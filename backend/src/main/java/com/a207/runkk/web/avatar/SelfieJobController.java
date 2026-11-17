package com.a207.runkk.web.avatar;

import com.a207.runkk.avatar.AvatarBody;
import com.a207.runkk.avatar.FaceJob;
import com.a207.runkk.avatar.FaceJobRejectedException;
import com.a207.runkk.avatar.GeneratedHead;
import com.a207.runkk.avatar.HeadLayer;
import com.a207.runkk.avatar.HeadLayerCache;
import com.a207.runkk.avatar.HeadLayerService;
import com.a207.runkk.avatar.HeadMeta;
import com.a207.runkk.avatar.SelfieJobService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/avatar")
public class SelfieJobController {
    /** 머리 응답 형식 버전. v2: frontImage·rightImage·mobile.renderFaceImage를 뺐다. */
    static final String HEAD_FORMAT = "v2";

    /** 층·메타 응답: 작업이 끝난 머리는 바뀌지 않으므로 1년 두고 다시 묻지 않는다(공유 캐시 금지). */
    private static final CacheControl FOREVER =
            CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable();

    private final SelfieJobService service;
    private final HeadLayerService layers;

    public SelfieJobController(SelfieJobService service, HeadLayerService layers) {
        this.service = service;
        this.layers = layers;
    }

    @PostMapping(
            value = {"/selfie", "/selfie/jobs"},
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<FaceJob> submit(
            @RequestAttribute("memberId") long memberId,
            @RequestPart("selfie") MultipartFile selfie)
            throws IOException {
        if (selfie.getSize() > 1024 * 1024) throw new FaceJobRejectedException(413, "TOO_LARGE");
        byte[] image = selfie.getBytes();
        if (!AvatarController.SelfieFormat.isJpegOrPng(image)) {
            throw new FaceJobRejectedException(415, "UNSUPPORTED_IMAGE");
        }
        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .body(service.submit(memberId, image));
    }

    /** 내 진행 중 셀카 작업(앱이 작업 ID를 잃었을 때 이어 조회한다). 없으면 204. */
    @GetMapping("/selfie/jobs/current")
    ResponseEntity<FaceJob> current(@RequestAttribute("memberId") long memberId) {
        return service.current(memberId)
                .map(job -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(job))
                .orElseGet(
                        () ->
                                ResponseEntity.noContent()
                                        .cacheControl(CacheControl.noStore())
                                        .build());
    }

    @GetMapping("/selfie/jobs/{jobId}")
    ResponseEntity<FaceJob> status(
            @RequestAttribute("memberId") long memberId, @PathVariable String jobId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.status(memberId, jobId));
    }

    @GetMapping("/selfie/jobs/{jobId}/result")
    ResponseEntity<GeneratedHead> result(
            @RequestAttribute("memberId") long memberId, @PathVariable String jobId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.result(memberId, jobId));
    }

    @GetMapping("/selfie/latest")
    ResponseEntity<GeneratedHead> latest(@RequestAttribute("memberId") long memberId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.latest(memberId));
    }

    /**
     * 다른 러너의 머리. 작업이 끝난 머리는 바뀌지 않으므로 작업마다 강한 ETag를 주고 매번 재검증하게 한다(공유 캐시 금지). If-None-Match가 맞으면 공개
     * 규칙만 다시 확인하고 ai를 부르지 않은 채 304를 돌려준다(S15P31A207-300 핫픽스).
     */
    @GetMapping("/heads/{ownerId}/{jobId}")
    ResponseEntity<GeneratedHead> head(
            @RequestAttribute("memberId") long memberId,
            @PathVariable long ownerId,
            @PathVariable String jobId,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false)
                    String ifNoneMatch) {
        String etag = headEtag(ownerId, jobId);
        CacheControl revalidate = CacheControl.noCache().cachePrivate();
        if (matches(ifNoneMatch, etag)) {
            service.requireVisibleHead(memberId, ownerId, jobId);
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .cacheControl(revalidate)
                    .build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(revalidate)
                .body(service.head(memberId, ownerId, jobId));
    }

    /**
     * 머리 응답의 강한 ETag. 작업(소유자·작업 ID)과 응답 형식 버전으로 만든다. 형식을 바꾸면 {@link #HEAD_FORMAT}을 올려 예전 사본을 다시 받게
     * 한다.
     */
    static String headEtag(long ownerId, String jobId) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest((ownerId + ":" + jobId).getBytes(StandardCharsets.UTF_8));
            return "\"head-" + HEAD_FORMAT + "-" + HexFormat.of().formatHex(digest, 0, 16) + "\"";
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256를 사용할 수 없다", error);
        }
    }

    /** If-None-Match 비교(RFC 9110 13.1.2: 약한 비교, 목록·*). */
    private static boolean matches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) return false;
        for (String candidate : ifNoneMatch.split(",")) {
            String tag = candidate.trim();
            if (tag.startsWith("W/")) tag = tag.substring(2);
            if (tag.equals("*") || tag.equals(etag)) return true;
        }
        return false;
    }

    /**
     * 머리 메타데이터(이미지 없음, S15P31A207-300 레이어 합성). 권한은 기존 머리와 같고 매 요청 확인한다. 앱은 이것과 층({@link
     * #headLayer})으로 머리를 합성한다. 기존 GET /heads/{회원}/{작업}(통짜 응답)은 v0.13.x 앱을 위해 그대로 둔다.
     */
    @GetMapping("/heads/{ownerId}/{jobId}/meta")
    ResponseEntity<HeadMeta> headMeta(
            @RequestAttribute("memberId") long memberId,
            @PathVariable long ownerId,
            @PathVariable String jobId,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false)
                    String ifNoneMatch) {
        String etag = metaEtag(ownerId, jobId);
        if (matches(ifNoneMatch, etag)) {
            layers.requireVisible(memberId, ownerId, jobId);
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .cacheControl(FOREVER)
                    .build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(FOREVER)
                .body(layers.meta(memberId, ownerId, jobId));
    }

    /**
     * 머리 층 그림 하나(face·eyes·hair·rightFace·rightEyes·rightHair). 1024 공통 캔버스를 size(256·512·1024, 기본
     * 1024)로 비율 그대로 줄인 무손실 PNG다. 바뀌지 않으므로 1년·immutable로 두고, 강한 ETag가 맞으면 공개 규칙만 확인하고 304를 준다.
     */
    @GetMapping("/heads/{ownerId}/{jobId}/layers/{layer}")
    ResponseEntity<byte[]> headLayer(
            @RequestAttribute("memberId") long memberId,
            @PathVariable long ownerId,
            @PathVariable String jobId,
            @PathVariable String layer,
            @RequestParam(defaultValue = "1024") int size,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false)
                    String ifNoneMatch) {
        HeadLayer parsed = HeadLayer.fromName(layer);
        HeadLayerService.requireSize(size);
        String etag = layerEtag(ownerId, jobId, parsed, size);
        if (matches(ifNoneMatch, etag)) {
            layers.requireVisible(memberId, ownerId, jobId);
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .cacheControl(FOREVER)
                    .build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(FOREVER)
                .contentType(MediaType.IMAGE_PNG)
                .body(layers.layer(memberId, ownerId, jobId, parsed, size));
    }

    /** 층 응답의 강한 ETag: 소유자·작업·층·크기·층 형식 버전({@link HeadLayerCache#FORMAT}). */
    static String layerEtag(long ownerId, String jobId, HeadLayer layer, int size) {
        String format = HeadLayerCache.FORMAT;
        return "\"layer-"
                + format
                + "-"
                + hash(ownerId + ":" + jobId + ":" + layer.pathName() + ":" + size + ":" + format)
                + "\"";
    }

    /** 메타 응답의 강한 ETag: 소유자·작업·층 형식 버전. */
    static String metaEtag(long ownerId, String jobId) {
        String format = HeadLayerCache.FORMAT;
        return "\"meta-" + format + "-" + hash(ownerId + ":" + jobId + ":meta:" + format) + "\"";
    }

    private static String hash(String text) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256를 사용할 수 없다", error);
        }
    }

    @GetMapping("/bodies/{bodyId}")
    AvatarBody body(@PathVariable String bodyId) {
        return service.body(bodyId);
    }

    @ExceptionHandler(FaceJobRejectedException.class)
    ResponseEntity<Map<String, String>> rejected(FaceJobRejectedException error) {
        return ResponseEntity.status(error.status())
                .cacheControl(CacheControl.noStore())
                .body(Map.of("reason", error.reason()));
    }
}
