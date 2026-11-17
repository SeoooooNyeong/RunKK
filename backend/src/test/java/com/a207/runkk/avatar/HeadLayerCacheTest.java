package com.a207.runkk.avatar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * 층별 머리 캐시(S15P31A207-300 레이어 합성): 머리 하나를 ai에서 한 번만 받아 층·크기를 만들고, 크기 상한 안에서 담으며, 동시 요청도 한 번만 받는다.
 * 축소는 무손실 PNG(알파 가중 2×2 평균)라 앱이 알파 경계로 재는 구도가 원본과 같다.
 */
class HeadLayerCacheTest {
    private static final int FACE_ARGB = 0xFFC89678;

    private final FakeGateway gateway = new FakeGateway();

    @Test
    void firstLayerFetchesHeadOnceAndOtherLayersSizesAndMetaReuseIt() {
        HeadLayerCache cache = new HeadLayerCache(gateway, HeadLayerCache.DEFAULT_MAX_BYTES);

        cache.layer(7, "job-1", HeadLayer.FACE, 256);
        cache.layer(7, "job-1", HeadLayer.EYES, 256);
        cache.layer(7, "job-1", HeadLayer.HAIR, 512);
        cache.layer(7, "job-1", HeadLayer.RIGHT_HAIR, 1024);
        cache.layer(7, "job-1", HeadLayer.FACE, 256);
        cache.meta(7, "job-1");

        assertThat(gateway.calls.get()).isEqualTo(1);
        cache.layer(8, "job-1", HeadLayer.FACE, 256);
        assertThat(gateway.calls.get()).as("다른 머리는 새로 받는다").isEqualTo(2);
    }

    @Test
    void concurrentRequestsForOneHeadCallGatewayOnce() throws Exception {
        HeadLayerCache cache = new HeadLayerCache(gateway, HeadLayerCache.DEFAULT_MAX_BYTES);
        gateway.gate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<byte[]>> results = new ArrayList<>();
            HeadLayer[] layers = HeadLayer.values();
            for (int index = 0; index < 8; index++) {
                HeadLayer layer = layers[index % layers.length];
                int size = index % 2 == 0 ? 256 : 1024;
                results.add(pool.submit(() -> cache.layer(7, "job-1", layer, size)));
            }
            Thread.sleep(200);
            gateway.gate.countDown();
            for (Future<byte[]> result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS)).isNotEmpty();
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(gateway.calls.get()).isEqualTo(1);
    }

    @Test
    void fullSizeIsTheOriginalPngAndSmallerSizesKeepRatioAndTransparency() throws IOException {
        HeadLayerCache cache = new HeadLayerCache(gateway, HeadLayerCache.DEFAULT_MAX_BYTES);

        assertThat(cache.layer(7, "job-1", HeadLayer.FACE, 1024))
                .as("1024는 ai 원본 그대로(다시 인코딩하지 않는다)")
                .isEqualTo(gateway.face);
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(gateway.face));
        for (int size : new int[] {512, 256}) {
            byte[] png = cache.layer(7, "job-1", HeadLayer.FACE, size);
            assertThat(png).startsWith(0x89, 'P', 'N', 'G');
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            assertThat(image.getWidth()).isEqualTo(size);
            assertThat(image.getHeight()).isEqualTo(size);
            assertThat(image.getColorModel().hasAlpha()).isTrue();
            int factor = 1024 / size;
            // 무손실: 축소 그림의 모든 픽셀이 원본 factor×factor 칸의 알파 가중 평균과 같다(품질 판단 근거).
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    assertThat(image.getRGB(x, y))
                            .as("(%d,%d) @%d", x, y, size)
                            .isEqualTo(expectedAverage(original, x, y, factor));
                }
            }
            assertThat(image.getRGB(0, 0) >>> 24).as("투명 바탕").isZero();
            assertThat(image.getRGB(size / 2, size / 2)).as("불투명 얼굴").isEqualTo(FACE_ARGB);
        }
    }

    @Test
    void cacheIsBoundedByBytesAndEvictedHeadsAreFetchedAgain() {
        // 머리 원본(6층) 하나만 겨우 담는 상한: 두 번째 머리가 첫 머리를 밀어낸다.
        long oneHead = gateway.headBytes() + 64 * 1024;
        HeadLayerCache cache = new HeadLayerCache(gateway, oneHead);
        cache.layer(7, "job-1", HeadLayer.FACE, 1024);
        cache.layer(8, "job-2", HeadLayer.FACE, 1024);
        cache.layer(7, "job-1", HeadLayer.FACE, 1024);

        assertThat(gateway.calls.get()).isEqualTo(3);
        assertThat(cache.weight()).isLessThanOrEqualTo(oneHead);
    }

    @Test
    void metaCarriesNoImageDataAndListsPresentLayers() {
        gateway.withRight = false;
        HeadLayerCache cache = new HeadLayerCache(gateway, HeadLayerCache.DEFAULT_MAX_BYTES);

        HeadMeta meta = cache.meta(7, "job-1");

        assertThat(meta.format()).isEqualTo(HeadLayerCache.FORMAT);
        assertThat(meta.headId()).isEqualTo("head-1");
        assertThat(meta.canvas()).isEqualTo(1024);
        assertThat(meta.layers()).containsExactly("face", "eyes", "hair");
        assertThat(meta.mobile())
                .containsKeys("neckMask", "skinColor")
                .doesNotContainKey("renderFaceImage");
        assertThat(meta.traits()).containsEntry("hair", "short");
        assertThat(meta.hairLength()).isEqualTo("short");
        assertThat(meta.toString()).doesNotContain("data:image");
        assertThatThrownBy(() -> cache.layer(7, "job-1", HeadLayer.RIGHT_FACE, 1024))
                .isInstanceOfSatisfying(
                        FaceJobRejectedException.class,
                        error -> {
                            assertThat(error.status()).isEqualTo(404);
                            assertThat(error.reason()).isEqualTo("LAYER_NOT_FOUND");
                        });
    }

    @Test
    void failedFetchIsNotCachedAndUnsupportedSizeIsRejected() {
        HeadLayerCache cache = new HeadLayerCache(gateway, HeadLayerCache.DEFAULT_MAX_BYTES);
        gateway.failure = new FaceJobRejectedException(503, "UPSTREAM_FAILED");
        assertThatThrownBy(() -> cache.layer(7, "job-1", HeadLayer.FACE, 256))
                .isInstanceOf(FaceJobRejectedException.class);
        gateway.failure = null;
        assertThat(cache.layer(7, "job-1", HeadLayer.FACE, 256)).isNotEmpty();
        assertThat(gateway.calls.get()).isEqualTo(2);

        assertThatThrownBy(() -> cache.layer(7, "job-1", HeadLayer.FACE, 300))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HeadLayer.fromName("frontImage"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(HeadLayer.fromName("rightEyes")).isEqualTo(HeadLayer.RIGHT_EYES);
    }

    /** 원본 factor×factor 칸의 알파 가중 평균(반올림). */
    private static int expectedAverage(BufferedImage source, int x, int y, int factor) {
        long alpha = 0;
        long red = 0;
        long green = 0;
        long blue = 0;
        for (int dy = 0; dy < factor; dy++) {
            for (int dx = 0; dx < factor; dx++) {
                int argb = source.getRGB(x * factor + dx, y * factor + dy);
                int a = argb >>> 24;
                alpha += a;
                red += (long) ((argb >> 16) & 0xff) * a;
                green += (long) ((argb >> 8) & 0xff) * a;
                blue += (long) (argb & 0xff) * a;
            }
        }
        // 2×2를 거듭하므로 단계마다 반올림한 값과 같게 거듭해 계산한다.
        if (factor > 2) {
            BufferedImage half = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
            int step = factor / 2;
            for (int dy = 0; dy < 2; dy++) {
                for (int dx = 0; dx < 2; dx++) {
                    half.setRGB(dx, dy, expectedAverage(source, x * 2 + dx, y * 2 + dy, step));
                }
            }
            return expectedAverage(half, 0, 0, 2);
        }
        if (alpha == 0) return 0;
        int count = factor * factor;
        int a = (int) ((alpha + count / 2) / count);
        int r = (int) ((red + alpha / 2) / alpha);
        int g = (int) ((green + alpha / 2) / alpha);
        int b = (int) ((blue + alpha / 2) / alpha);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** ai 머리 결과를 흉내 낸다: 1024 투명 바탕에 원(얼굴)과 반투명 띠(가장자리)를 그린 PNG 여섯 장. */
    static final class FakeGateway implements FaceJobGateway {
        final AtomicInteger calls = new AtomicInteger();
        final byte[] face = png(FACE_ARGB, 300);
        final byte[] other = png(0xFF302010, 200);
        volatile CountDownLatch gate;
        volatile RuntimeException failure;
        volatile boolean withRight = true;

        long headBytes() {
            return face.length + 5L * other.length;
        }

        @Override
        public GeneratedHead result(long memberId, String jobId) {
            calls.incrementAndGet();
            CountDownLatch waiting = gate;
            if (waiting != null) {
                try {
                    waiting.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
            }
            if (failure != null) throw failure;
            Map<String, Object> mobile = new LinkedHashMap<>();
            mobile.put("neckMask", Map.of("centerX", 512, "chinY", 750));
            mobile.put("skinColor", List.of(200, 150, 120));
            mobile.put("renderFaceImage", data(other));
            return new GeneratedHead(
                    "head-1",
                    Long.toString(memberId),
                    data(face),
                    data(other),
                    data(other),
                    Map.of("hair", "short"),
                    "short",
                    Map.of("pivotX", 0.5),
                    mobile,
                    "2026-11-17T00:00:00Z",
                    data(other),
                    data(other),
                    withRight ? data(other) : null,
                    withRight ? data(other) : null,
                    withRight ? data(other) : null,
                    withRight ? Map.of("scale", 1.0) : null);
        }

        @Override
        public FaceJob submit(long memberId, byte[] image, int attempt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FaceJob status(long memberId, String jobId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GeneratedHead latest(long memberId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AvatarBody body(String bodyId) {
            throw new UnsupportedOperationException();
        }

        static String data(byte[] png) {
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
        }

        /** 원과 그 둘레 반투명 띠(알파 120, 색이 섞이는 가장자리). */
        static byte[] png(int argb, int radius) {
            BufferedImage image = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 1024; y++) {
                for (int x = 0; x < 1024; x++) {
                    double distance = Math.hypot(x - 511.5, y - 511.5);
                    if (distance <= radius) {
                        image.setRGB(x, y, argb);
                    } else if (distance <= radius + 7) {
                        int shade = (x * 7 + y * 13) & 0xff;
                        image.setRGB(
                                x, y, (120 << 24) | (shade << 16) | (0x40 << 8) | (255 - shade));
                    }
                }
            }
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                ImageIO.write(image, "png", out);
                return out.toByteArray();
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
        }
    }
}
