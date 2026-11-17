package com.a207.runkk.web.avatar;

import com.a207.runkk.avatar.AvatarBody;
import com.a207.runkk.avatar.FaceJob;
import com.a207.runkk.avatar.FaceJobRejectedException;
import com.a207.runkk.avatar.GeneratedHead;
import com.a207.runkk.avatar.SelfieJobService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    /**
     * 머리 응답 형식 버전. v2: frontImage·rightImage·mobile.renderFaceImage를 뺐다. v3: avatar-ai가 저장된 머리
     * 6레이어를 팔레트 PNG로 다시 줄였다(S15P31A207-300). 압축 전 사본을 가진 앱이 한 번 다시 받는다.
     */
    static final String HEAD_FORMAT = "v3";

    private final SelfieJobService service;

    public SelfieJobController(SelfieJobService service) {
        this.service = service;
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
