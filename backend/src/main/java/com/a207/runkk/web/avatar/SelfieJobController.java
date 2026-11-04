package com.a207.runkk.web.avatar;

import com.a207.runkk.avatar.AvatarBody;
import com.a207.runkk.avatar.FaceJob;
import com.a207.runkk.avatar.FaceJobRejectedException;
import com.a207.runkk.avatar.GeneratedHead;
import com.a207.runkk.avatar.SelfieJobService;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/avatar")
public class SelfieJobController {
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

    @GetMapping("/heads/{ownerId}/{jobId}")
    ResponseEntity<GeneratedHead> head(
            @RequestAttribute("memberId") long memberId,
            @PathVariable long ownerId,
            @PathVariable String jobId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.head(memberId, ownerId, jobId));
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
