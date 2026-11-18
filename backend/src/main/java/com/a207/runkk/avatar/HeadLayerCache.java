package com.a207.runkk.avatar;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 층별 머리 캐시(S15P31A207-300). 머리 하나(소유자·작업)를 ai에서 한 번 받아 원본 층 PNG(1024)와 메타데이터를 담고, 줄인 크기(512·256)는
 * 처음 요청될 때 그 원본에서 한 번 만들어 담는다. 같은 머리의 다른 층·크기 요청은 ai를 다시 부르지 않는다.
 *
 * <ul>
 *   <li>상한: 담은 바이트 합이 [maxBytes](기본 64MiB, 운영 api 힙 약 0.9GiB의 7%)를 넘으면 가장 오래 안 쓴 것부터 버린다. 원본 머리 하나는
 *       약 4~5MB(층 6장), 얼굴 원용 512 층은 장당 수십~수백 KB다. 버린 머리는 다음 요청에 다시 받는다.
 *   <li>동시 요청: 같은 키(머리 원본, 또는 층·크기)를 여러 요청이 함께 찾으면 하나만 받거나 만들고 나머지는 그 결과를 기다린다. 실패는 담지 않고 기다린 요청에도
 *       같은 오류를 돌려준다(다음 요청이 다시 받는다).
 *   <li>첫 적재 제한(v0.14 S15P31A207-316): ai 결과 받기·풀기는 동시에 [loadPermits](기본 4)개까지다. 머리 하나 적재가 약 30~40MB를
 *       잠깐 쓰므로 힙을 지킨다. 자리를 [loadWait](기본 10초) 안에 못 얻으면 503 {@link #HEAD_BUSY}다(앱은 한 번 다시 시도). 같은
 *       머리를 기다리는 요청은 자리를 쓰지 않고 앞 요청의 결과를 기다린다.
 *   <li>권한: 이 캐시는 권한을 보지 않는다. 부르는 쪽({@link HeadLayerService})이 매 요청 공개 규칙을 확인한다.
 *   <li>줄인 크기를 첫 요청 때 모두 만들지 않고 처음 요청된 층·크기만 만든다. 얼굴 원은 정면 세 층만, 큰 아바타는 원본(1024)만 쓰므로 쓰지 않는 크기를 만드는
 *       시간(층마다 수십 ms)으로 첫 응답을 늦추지 않는다.
 * </ul>
 *
 * <p>형식 판은 {@link HeadFormat#VERSION} 하나다(메타의 format과 층 주소가 그 값을 쓴다).
 */
@Component
public class HeadLayerCache {
    /** 첫 적재 자리를 제때 얻지 못했을 때의 사유(503, D-성-953). */
    public static final String HEAD_BUSY = "HEAD_BUSY";

    /** 동시에 받을 수 있는 머리 원본 수(D-성-953). */
    public static final int DEFAULT_LOAD_PERMITS = 4;

    /** 첫 적재 자리를 기다리는 최대 시간(D-성-953). */
    public static final Duration DEFAULT_LOAD_WAIT = Duration.ofSeconds(10);

    /** 층 원본의 공통 캔버스 한 변. */
    public static final int CANVAS = 1024;

    /** 내주는 크기(공통 캔버스 한 변 기준). */
    public static final Set<Integer> SIZES = Set.of(256, 512, CANVAS);

    public static final long DEFAULT_MAX_BYTES = 64L * 1024 * 1024;

    private static final String DATA_PNG = "data:image/png;base64,";

    /** 메타데이터 한 개를 바이트 상한에 셀 때 쓰는 어림값. */
    private static final long META_WEIGHT = 4 * 1024;

    private final FaceJobGateway gateway;
    private final long maxBytes;
    private final Semaphore loadPermits;
    private final Duration loadWait;
    private final LinkedHashMap<Object, Object> entries = new LinkedHashMap<>(64, 0.75f, true);
    private final Map<Object, CompletableFuture<Object>> loading = new ConcurrentHashMap<>();
    private long weight;

    @Autowired
    public HeadLayerCache(
            FaceJobGateway gateway,
            @Value("${runkk.avatar.head-layer-cache-bytes:67108864}") long maxBytes) {
        this(gateway, maxBytes, DEFAULT_LOAD_PERMITS, DEFAULT_LOAD_WAIT);
    }

    /** 첫 적재 자리 수와 기다림 상한을 정하는 생성자(시험용). */
    HeadLayerCache(FaceJobGateway gateway, long maxBytes, int loadPermits, Duration loadWait) {
        if (maxBytes <= 0) throw new IllegalArgumentException("캐시 상한 오류");
        if (loadPermits <= 0 || loadWait.isNegative()) {
            throw new IllegalArgumentException("머리 적재 제한 오류");
        }
        this.gateway = gateway;
        this.maxBytes = maxBytes;
        this.loadPermits = new Semaphore(loadPermits, true);
        this.loadWait = loadWait;
    }

    /** 이미지 없는 머리 메타데이터. */
    public HeadMeta meta(long ownerId, String jobId) {
        return source(ownerId, jobId).meta();
    }

    /**
     * 층 하나를 key.size(공통 캔버스 한 변 기준)로 줄인 PNG. 1024는 ai 원본 그대로다. 이 머리에 없는 층은 404 LAYER_NOT_FOUND다(크기는
     * {@link HeadLayerKey}가 이미 확인했다).
     */
    public byte[] layer(HeadLayerKey key) {
        if (key.size() == CANVAS) return source(key.ownerId(), key.jobId()).original(key.layer());
        return (byte[])
                load(
                        key,
                        () ->
                                HeadLayerImages.downscale(
                                        source(key.ownerId(), key.jobId()).original(key.layer()),
                                        key.size(),
                                        CANVAS));
    }

    /** 지금 담은 바이트 합(시험용). */
    synchronized long weight() {
        return weight;
    }

    private HeadSource source(long ownerId, String jobId) {
        return (HeadSource)
                load(
                        new SourceKey(ownerId, jobId),
                        () ->
                                withLoadPermit(
                                        () ->
                                                HeadSource.of(
                                                        ownerId,
                                                        jobId,
                                                        gateway.result(ownerId, jobId))));
    }

    /** 첫 적재 자리를 얻어 [loader]를 부른다. [loadWait] 안에 못 얻으면 503 HEAD_BUSY. 성공·실패 모두 자리를 돌려준다. */
    private Object withLoadPermit(Supplier<Object> loader) {
        boolean acquired;
        try {
            acquired = loadPermits.tryAcquire(loadWait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new FaceJobRejectedException(503, HEAD_BUSY);
        }
        if (!acquired) throw new FaceJobRejectedException(503, HEAD_BUSY);
        try {
            return loader.get();
        } finally {
            loadPermits.release();
        }
    }

    /** 담은 값이 있으면 그것을, 없으면 한 요청만 [loader]로 만들어 담는다. 나머지는 그 결과를 기다린다. */
    private Object load(Object key, Supplier<Object> loader) {
        Object cached = cached(key);
        if (cached != null) return cached;
        CompletableFuture<Object> mine = new CompletableFuture<>();
        CompletableFuture<Object> other = loading.putIfAbsent(key, mine);
        if (other != null) return await(other);
        try {
            Object value = cached(key);
            if (value == null) {
                value = loader.get();
                store(key, value);
            }
            mine.complete(value);
            return value;
        } catch (RuntimeException | Error error) {
            mine.completeExceptionally(error);
            throw error;
        } finally {
            loading.remove(key, mine);
        }
    }

    private synchronized Object cached(Object key) {
        return entries.get(key);
    }

    private synchronized void store(Object key, Object value) {
        Object before = entries.put(key, value);
        if (before != null) weight -= weigh(before);
        weight += weigh(value);
        // 방금 담은 것은 남기고 가장 오래 안 쓴 것부터 버린다.
        Iterator<Map.Entry<Object, Object>> eldest = entries.entrySet().iterator();
        while (weight > maxBytes && eldest.hasNext()) {
            Map.Entry<Object, Object> entry = eldest.next();
            if (entry.getKey().equals(key)) continue;
            weight -= weigh(entry.getValue());
            eldest.remove();
        }
    }

    private static long weigh(Object value) {
        if (value instanceof byte[] bytes) return bytes.length;
        if (value instanceof HeadSource source) return source.weight();
        return 0;
    }

    private static Object await(CompletableFuture<Object> future) {
        try {
            return future.get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new FaceJobRejectedException(503, "UPSTREAM_FAILED");
        } catch (ExecutionException | CompletionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error fatal) throw fatal;
            throw new FaceJobRejectedException(503, "UPSTREAM_FAILED");
        }
    }

    private record SourceKey(long ownerId, String jobId) {}

    /** ai에서 받은 머리 하나: 원본 층 PNG와 이미지 없는 메타데이터. */
    private record HeadSource(HeadMeta meta, Map<HeadLayer, byte[]> originals) {
        static HeadSource of(long ownerId, String jobId, GeneratedHead head) {
            Map<HeadLayer, byte[]> originals = new EnumMap<>(HeadLayer.class);
            put(originals, HeadLayer.FACE, head.faceImage());
            put(originals, HeadLayer.EYES, head.eyesImage());
            put(originals, HeadLayer.HAIR, head.hairImage());
            put(originals, HeadLayer.RIGHT_FACE, head.rightFaceImage());
            put(originals, HeadLayer.RIGHT_EYES, head.rightEyesImage());
            put(originals, HeadLayer.RIGHT_HAIR, head.rightHairImage());
            for (HeadLayer required : List.of(HeadLayer.FACE, HeadLayer.EYES, HeadLayer.HAIR)) {
                if (!originals.containsKey(required)) {
                    throw new FaceJobRejectedException(503, "UPSTREAM_FAILED");
                }
            }
            List<String> names = new ArrayList<>();
            originals.keySet().forEach(layer -> names.add(layer.pathName()));
            HeadMeta meta =
                    new HeadMeta(
                            HeadFormat.VERSION,
                            head.headId(),
                            CANVAS,
                            head.traits(),
                            head.hairLength(),
                            head.alignment(),
                            head.rightLayerLayout(),
                            withoutImages(head.mobile()),
                            Collections.unmodifiableList(names),
                            head.createdAt(),
                            layerUrls(ownerId, jobId, originals.keySet()));
            return new HeadSource(meta, Collections.unmodifiableMap(originals));
        }

        byte[] original(HeadLayer layer) {
            byte[] png = originals.get(layer);
            if (png == null) throw new FaceJobRejectedException(404, "LAYER_NOT_FOUND");
            return png;
        }

        long weight() {
            return META_WEIGHT + originals.values().stream().mapToLong(png -> png.length).sum();
        }

        /** 크기(작은 것부터) → 층 이름(층 순서) → 층 주소. 이 머리에 있는 층만. */
        private static Map<String, Map<String, String>> layerUrls(
                long ownerId, String jobId, Set<HeadLayer> present) {
            Map<String, Map<String, String>> bySize = new LinkedHashMap<>();
            for (int size : SIZES.stream().sorted().toList()) {
                Map<String, String> urls = new LinkedHashMap<>();
                for (HeadLayer layer : present) {
                    urls.put(
                            layer.pathName(),
                            HeadFormat.layerUrl(new HeadLayerKey(ownerId, jobId, layer, size)));
                }
                bySize.put(Integer.toString(size), Collections.unmodifiableMap(urls));
            }
            return Collections.unmodifiableMap(bySize);
        }

        private static void put(Map<HeadLayer, byte[]> originals, HeadLayer layer, String data) {
            if (data == null) return;
            if (!data.startsWith(DATA_PNG))
                throw new FaceJobRejectedException(503, "UPSTREAM_FAILED");
            try {
                originals.put(layer, Base64.getDecoder().decode(data.substring(DATA_PNG.length())));
            } catch (IllegalArgumentException error) {
                throw new FaceJobRejectedException(503, "UPSTREAM_FAILED");
            }
        }

        /** 합성 정보에서 이미지 값(Data URL, 예: renderFaceImage)을 뺀다. 나머지는 ai가 준 그대로다. */
        private static Map<String, Object> withoutImages(Map<String, Object> mobile) {
            if (mobile == null) return null;
            Map<String, Object> slim = new LinkedHashMap<>();
            mobile.forEach(
                    (key, value) -> {
                        if (!(value instanceof String text && text.startsWith("data:"))) {
                            slim.put(key, value);
                        }
                    });
            return slim;
        }
    }
}
