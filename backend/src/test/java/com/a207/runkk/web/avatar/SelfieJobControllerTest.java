package com.a207.runkk.web.avatar;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.a207.runkk.avatar.*;
import com.a207.runkk.web.WebTestSupport;
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
        when(gateway.submit(eq(user.memberId()), any())).thenReturn(queued);
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

    @Test
    void failedJobIs200AndNotReadyResultIs409() throws Exception {
        Session user = signUp();
        mvc.perform(as(user, put("/api/v1/me/consents/FACE_TRANSFER"))).andExpect(status().isOk());
        when(gateway.submit(eq(user.memberId()), any()))
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
        when(gateway.submit(eq(user.memberId()), any()))
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
