package com.a207.runkk.web.avatar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.a207.runkk.avatar.*;
import com.a207.runkk.web.WebTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class SelfieJobControllerTest extends WebTestSupport {
    @MockitoBean FaceJobGateway gateway;
    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1};

    private MockMultipartFile image(byte[] bytes) {
        return new MockMultipartFile("selfie", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, bytes);
    }

    @Test
    void authenticatedAsyncFlowAppliesOnlyOnce() throws Exception {
        Session user = signUp();
        var queued = new FaceJob("http-job-1", FaceJob.Status.QUEUED, null, null);
        when(gateway.submit(eq(user.memberId()), any(), anyInt())).thenReturn(queued);
        mvc.perform(as(user, multipart("/api/v1/avatar/selfie/jobs").file(image(JPEG))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FACE_CONSENT_REQUIRED"));
        mvc.perform(as(user, put("/api/v1/me/consents/FACE_TRANSFER"))).andExpect(status().isOk());
        mvc.perform(as(user, multipart("/api/v1/avatar/selfie/jobs").file(image(JPEG))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"));
        when(gateway.status(user.memberId(), "http-job-1"))
                .thenReturn(new FaceJob("http-job-1", FaceJob.Status.SUCCEEDED, "head-1", null));
        mvc.perform(as(user, get("/api/v1/avatar/selfie/jobs/http-job-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        verify(gateway, never()).result(anyLong(), anyString());
        var head =
                new GeneratedHead(
                        "head-1",
                        Long.toString(user.memberId()),
                        "face-png",
                        "eyes-png",
                        "hair-png",
                        Map.of("hair", "short"),
                        "short",
                        Map.of("pivotX", 0.5),
                        Map.of("anchor", new double[] {0.5, 0.88}),
                        "2026-11-04T00:00:00Z");
        when(gateway.result(user.memberId(), "http-job-1")).thenReturn(head);
        for (int index = 0; index < 2; index++) {
            mvc.perform(as(user, get("/api/v1/avatar/selfie/jobs/http-job-1/result")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.faceImage").value("face-png"))
                    .andExpect(jsonPath("$.mobile.anchor[1]").value(0.88));
        }
        mvc.perform(as(user, get("/api/v1/avatar")))
                .andExpect(jsonPath("$.faceKey").value("head:" + user.memberId() + ":http-job-1"))
                .andExpect(jsonPath("$.canCreateSelfieToday").value(false));
        Session other = signUp();
        mvc.perform(as(other, get("/api/v1/avatar/selfie/jobs/http-job-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("JOB_NOT_FOUND"));
        mvc.perform(as(other, get("/api/v1/avatar/heads/" + user.memberId() + "/http-job-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headId").value("head-1"));
        mvc.perform(get("/api/v1/avatar/selfie/jobs/http-job-1"))
                .andExpect(status().isUnauthorized());
    }

    /** 셀카 작업 하나를 끝까지 돌려 얼굴에 반영한다. */
    private void finishSelfie(Session user, String jobId, GeneratedHead head) throws Exception {
        mvc.perform(as(user, put("/api/v1/me/consents/FACE_TRANSFER"))).andExpect(status().isOk());
        when(gateway.submit(eq(user.memberId()), any(), anyInt()))
                .thenReturn(new FaceJob(jobId, FaceJob.Status.QUEUED, null, null));
        mvc.perform(as(user, multipart("/api/v1/avatar/selfie/jobs").file(image(JPEG))))
                .andExpect(status().isAccepted());
        when(gateway.status(user.memberId(), jobId))
                .thenReturn(new FaceJob(jobId, FaceJob.Status.SUCCEEDED, head.headId(), null));
        mvc.perform(as(user, get("/api/v1/avatar/selfie/jobs/" + jobId)))
                .andExpect(status().isOk());
        when(gateway.result(user.memberId(), jobId)).thenReturn(head);
        mvc.perform(as(user, get("/api/v1/avatar/selfie/jobs/" + jobId + "/result")))
                .andExpect(status().isOk());
    }

    private static GeneratedHead w9Head(Session owner) {
        return new GeneratedHead(
                "head-9",
                Long.toString(owner.memberId()),
                "face-png",
                "eyes-png",
                "hair-png",
                Map.of("hair", "short"),
                "short",
                Map.of("pivotX", 0.5),
                Map.of(
                        "renderFaceImage",
                        "render-png",
                        "neckMask",
                        "neck-png",
                        "skinColor",
                        "#f0c8a0"),
                "2026-11-17T00:00:00Z",
                "front-png",
                "right-png",
                "right-face-png",
                "right-eyes-png",
                "right-hair-png",
                Map.of("scale", 1.0));
    }

    @Test
    void headOmitsUnusedOriginalsAndRevalidatesWithEtag() throws Exception {
        Session owner = signUp();
        finishSelfie(owner, "etag-job-1", w9Head(owner));
        // 셀카 작업 결과(본인 조회)는 그대로 둔다
        mvc.perform(as(owner, get("/api/v1/avatar/selfie/jobs/etag-job-1/result")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frontImage").value("front-png"))
                .andExpect(jsonPath("$.mobile.renderFaceImage").value("render-png"));
        Session viewer = signUp();
        String path = "/api/v1/avatar/heads/" + owner.memberId() + "/etag-job-1";
        String etag =
                mvc.perform(as(viewer, get(path)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.frontImage").doesNotExist())
                        .andExpect(jsonPath("$.rightImage").doesNotExist())
                        .andExpect(jsonPath("$.mobile.renderFaceImage").doesNotExist())
                        .andExpect(jsonPath("$.mobile.neckMask").value("neck-png"))
                        .andExpect(jsonPath("$.mobile.skinColor").value("#f0c8a0"))
                        .andExpect(jsonPath("$.headId").value("head-9"))
                        .andExpect(jsonPath("$.faceImage").value("face-png"))
                        .andExpect(jsonPath("$.eyesImage").value("eyes-png"))
                        .andExpect(jsonPath("$.hairImage").value("hair-png"))
                        .andExpect(jsonPath("$.rightFaceImage").value("right-face-png"))
                        .andExpect(jsonPath("$.rightEyesImage").value("right-eyes-png"))
                        .andExpect(jsonPath("$.rightHairImage").value("right-hair-png"))
                        .andExpect(jsonPath("$.rightLayerLayout.scale").value(1.0))
                        .andExpect(jsonPath("$.traits.hair").value("short"))
                        .andExpect(jsonPath("$.hairLength").value("short"))
                        .andExpect(jsonPath("$.alignment.pivotX").value(0.5))
                        .andExpect(jsonPath("$.createdAt").value("2026-11-17T00:00:00Z"))
                        .andExpect(
                                header().string(
                                                "ETag",
                                                matchesPattern(
                                                        "\"head-"
                                                                + HeadFormat.VERSION
                                                                + "-[0-9a-f]{32}\"")))
                        .andExpect(header().string("Cache-Control", containsString("private")))
                        .andExpect(header().string("Cache-Control", containsString("no-cache")))
                        .andExpect(
                                header().string("Cache-Control", not(containsString("no-store"))))
                        .andReturn()
                        .getResponse()
                        .getHeader("ETag");
        assertThat(etag).isEqualTo(SelfieJobController.headEtag(owner.memberId(), "etag-job-1"));

        clearInvocations(gateway);
        for (String condition : List.of(etag, "W/" + etag, "\"stale\", " + etag, "*")) {
            mvc.perform(as(viewer, get(path).header("If-None-Match", condition)))
                    .andExpect(status().isNotModified())
                    .andExpect(header().string("ETag", etag))
                    .andExpect(header().string("Cache-Control", containsString("private")))
                    .andExpect(header().string("Cache-Control", containsString("no-cache")))
                    .andExpect(content().string(""));
        }
        verify(gateway, never()).result(anyLong(), anyString());

        // 형식 버전이 다른(예전) 태그는 맞지 않아 새 응답을 받는다
        mvc.perform(as(viewer, get(path).header("If-None-Match", "\"head-v1-old\"")))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", etag))
                .andExpect(jsonPath("$.faceImage").value("face-png"));
    }

    @Test
    void matchingEtagStillChecksVisibility() throws Exception {
        Session owner = signUp();
        finishSelfie(owner, "etag-job-2", w9Head(owner));
        Session viewer = signUp();
        String path = "/api/v1/avatar/heads/" + owner.memberId() + "/etag-job-2";
        String etag = SelfieJobController.headEtag(owner.memberId(), "etag-job-2");
        mvc.perform(get(path).header("If-None-Match", etag)).andExpect(status().isUnauthorized());
        // 없는 작업은 태그가 맞아도 지금과 같은 404
        String missingEtag = SelfieJobController.headEtag(owner.memberId(), "etag-job-x");
        mvc.perform(
                        as(
                                viewer,
                                get("/api/v1/avatar/heads/" + owner.memberId() + "/etag-job-x")
                                        .header("If-None-Match", missingEtag)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("JOB_NOT_FOUND"));
        // 탈퇴한 회원의 얼굴은 태그가 맞아도 지금과 같은 403
        mvc.perform(as(owner, delete("/api/v1/me"))).andExpect(status().isOk());
        clearInvocations(gateway);
        mvc.perform(as(viewer, get(path).header("If-None-Match", etag)))
                .andExpect(status().isForbidden());
        verify(gateway, never()).result(anyLong(), anyString());
    }

    @Test
    void differentJobsGetDifferentEtags() {
        assertThat(SelfieJobController.headEtag(1, "job-a"))
                .isNotEqualTo(SelfieJobController.headEtag(1, "job-b"))
                .isNotEqualTo(SelfieJobController.headEtag(2, "job-a"))
                .isEqualTo(SelfieJobController.headEtag(1, "job-a"));
    }

    @Test
    void failedJobIs200AndNotReadyResultIs409() throws Exception {
        Session user = signUp();
        mvc.perform(as(user, put("/api/v1/me/consents/FACE_TRANSFER"))).andExpect(status().isOk());
        when(gateway.submit(eq(user.memberId()), any(), anyInt()))
                .thenReturn(new FaceJob("http-job-2", FaceJob.Status.RUNNING, null, null));
        mvc.perform(as(user, multipart("/api/v1/avatar/selfie").file(image(JPEG))))
                .andExpect(status().isAccepted());
        when(gateway.result(user.memberId(), "http-job-2"))
                .thenThrow(new FaceJobRejectedException(409, "JOB_NOT_READY"));
        mvc.perform(as(user, get("/api/v1/avatar/selfie/jobs/http-job-2/result")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("JOB_NOT_READY"));
        when(gateway.status(user.memberId(), "http-job-2"))
                .thenReturn(
                        new FaceJob(
                                "http-job-2",
                                FaceJob.Status.FAILED,
                                null,
                                new FaceJob.Error("NO_FACE_DETECTED", "얼굴 없음")));
        mvc.perform(as(user, get("/api/v1/avatar/selfie/jobs/http-job-2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value("NO_FACE_DETECTED"));
        mvc.perform(as(user, get("/api/v1/avatar")))
                .andExpect(jsonPath("$.canCreateSelfieToday").value(true));
    }

    @Test
    void currentJobLetsAppResumeWithoutLocalJobId() throws Exception {
        Session user = signUp();
        mvc.perform(as(user, get("/api/v1/avatar/selfie/jobs/current")))
                .andExpect(status().isNoContent());
        mvc.perform(as(user, put("/api/v1/me/consents/FACE_TRANSFER"))).andExpect(status().isOk());
        when(gateway.submit(eq(user.memberId()), any(), anyInt()))
                .thenReturn(new FaceJob("http-job-3", FaceJob.Status.QUEUED, null, null));
        mvc.perform(as(user, multipart("/api/v1/avatar/selfie/jobs").file(image(JPEG))))
                .andExpect(status().isAccepted());
        mvc.perform(as(user, get("/api/v1/avatar/selfie/jobs/current")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("http-job-3"))
                .andExpect(jsonPath("$.status").value("QUEUED"));
        verify(gateway, never()).status(anyLong(), anyString());
        Session other = signUp();
        mvc.perform(as(other, get("/api/v1/avatar/selfie/jobs/current")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/avatar/selfie/jobs/current")).andExpect(status().isUnauthorized());
    }

    @Test
    void imageLimitsAndBodyPresetAreExposed() throws Exception {
        Session user = signUp();
        mvc.perform(
                        as(
                                user,
                                multipart("/api/v1/avatar/selfie/jobs")
                                        .file(image(new byte[1024 * 1024 + 1]))))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(
                        as(
                                user,
                                multipart("/api/v1/avatar/selfie/jobs")
                                        .file(image(new byte[] {1, 2}))))
                .andExpect(status().isUnsupportedMediaType());
        when(gateway.body("daily"))
                .thenReturn(
                        new AvatarBody(
                                Map.of("mobile", Map.of("anchor", new double[] {0.5, 0.8}))));
        mvc.perform(as(user, get("/api/v1/avatar/bodies/daily")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pack.mobile.anchor[0]").value(0.5));
        when(gateway.latest(user.memberId()))
                .thenThrow(new FaceJobRejectedException(404, "HEAD_NOT_FOUND"));
        mvc.perform(as(user, get("/api/v1/avatar/selfie/latest")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("HEAD_NOT_FOUND"));
    }
}
