package com.a207.runkk.web.avatar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.a207.runkk.avatar.*;
import com.a207.runkk.web.WebTestSupport;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 층별 머리 엔드포인트(S15P31A207-300 레이어 합성): GET /avatar/heads/{회원}/{작업}/meta 와 …/layers/{층}?size=. 권한은 기존
 * 머리(requireVisibleHead)와 같고 매 요청 확인한다. 층은 이미지 바이트(PNG)로, 오래 두는 캐시 머리글과 강한 ETag를 준다.
 */
class HeadLayerEndpointTest extends WebTestSupport {
    @MockitoBean FaceJobGateway gateway;
    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1};
    private static final byte[] FACE = png(0xFFC89678);
    private static final byte[] OTHER = png(0xFF302010);

    @Test
    void metaHasNoImagesAndLayersAreImmutablePngsFromOneUpstreamCall() throws Exception {
        Session owner = signUp();
        finishSelfie(owner, "layer-job-1", head(owner, true));
        Session viewer = signUp();
        String base = "/api/v1/avatar/heads/" + owner.memberId() + "/layer-job-1";
        clearInvocations(gateway);

        String meta =
                mvc.perform(as(viewer, get(base + "/meta")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.format").value(HeadFormat.VERSION))
                        .andExpect(jsonPath("$.headId").value("head-l"))
                        .andExpect(jsonPath("$.canvas").value(1024))
                        .andExpect(jsonPath("$.traits.hair").value("short"))
                        .andExpect(jsonPath("$.hairLength").value("short"))
                        .andExpect(jsonPath("$.alignment.pivotX").value(0.5))
                        .andExpect(jsonPath("$.rightLayerLayout.scale").value(1.0))
                        .andExpect(jsonPath("$.mobile.neckMask.chinY").value(750))
                        .andExpect(jsonPath("$.mobile.skinColor[0]").value(200))
                        .andExpect(jsonPath("$.mobile.renderFaceImage").doesNotExist())
                        .andExpect(jsonPath("$.faceImage").doesNotExist())
                        .andExpect(
                                jsonPath(
                                        "$.layers",
                                        contains(
                                                "face",
                                                "eyes",
                                                "hair",
                                                "rightFace",
                                                "rightEyes",
                                                "rightHair")))
                        .andExpect(
                                jsonPath("$.layerUrls['256'].face")
                                        .value(
                                                base
                                                        + "/layers/face?size=256&f="
                                                        + HeadFormat.VERSION))
                        .andExpect(
                                jsonPath("$.layerUrls['1024'].rightHair")
                                        .value(
                                                base
                                                        + "/layers/rightHair?size=1024&f="
                                                        + HeadFormat.VERSION))
                        .andExpect(jsonPath("$.layerUrls.*", hasSize(3)))
                        .andExpect(jsonPath("$.layerUrls['512'].*", hasSize(6)))
                        .andExpect(
                                header().string(
                                                "ETag",
                                                matchesPattern(
                                                        "\"meta-"
                                                                + HeadFormat.VERSION
                                                                + "-[0-9a-f]{32}\"")))
                        .andExpect(header().string("Cache-Control", containsString("private")))
                        .andExpect(
                                header().string("Cache-Control", containsString("max-age=86400")))
                        .andExpect(
                                header().string("Cache-Control", not(containsString("immutable"))))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(meta).doesNotContain("data:image");

        for (String layer :
                List.of("face", "eyes", "hair", "rightFace", "rightEyes", "rightHair")) {
            for (int size : new int[] {256, 512, 1024}) {
                byte[] png =
                        layer(viewer, base, layer, size)
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                                .andExpect(immutable())
                                .andExpect(
                                        header().string(
                                                        "ETag",
                                                        SelfieJobController.layerEtag(
                                                                new HeadLayerKey(
                                                                        owner.memberId(),
                                                                        "layer-job-1",
                                                                        HeadLayer.fromName(layer),
                                                                        size))))
                                .andReturn()
                                .getResponse()
                                .getContentAsByteArray();
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
                assertThat(image.getWidth()).as(layer + "@" + size).isEqualTo(size);
                assertThat(image.getHeight()).isEqualTo(size);
            }
        }
        // 크기를 주지 않으면 1024(원본 그대로)
        byte[] full =
                mvc.perform(as(viewer, get(base + "/layers/face?f=" + HeadFormat.VERSION)))
                        .andExpect(status().isOk())
                        .andExpect(immutable())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        assertThat(full).isEqualTo(FACE);
        verify(gateway, times(1)).result(owner.memberId(), "layer-job-1");
    }

    @Test
    void matchingEtagIs304WithoutUpstreamButStillChecksVisibility() throws Exception {
        Session owner = signUp();
        finishSelfie(owner, "layer-job-2", head(owner, true));
        Session viewer = signUp();
        String base = "/api/v1/avatar/heads/" + owner.memberId() + "/layer-job-2";
        String etag =
                SelfieJobController.layerEtag(
                        new HeadLayerKey(owner.memberId(), "layer-job-2", HeadLayer.HAIR, 256));
        String hair256 = base + "/layers/hair?size=256&f=" + HeadFormat.VERSION;
        clearInvocations(gateway);

        for (String condition : List.of(etag, "W/" + etag, "\"stale\", " + etag)) {
            mvc.perform(as(viewer, get(hair256).header("If-None-Match", condition)))
                    .andExpect(status().isNotModified())
                    .andExpect(header().string("ETag", etag))
                    .andExpect(immutable())
                    .andExpect(content().string(""));
        }
        String metaEtag = SelfieJobController.metaEtag(owner.memberId(), "layer-job-2");
        mvc.perform(as(viewer, get(base + "/meta").header("If-None-Match", metaEtag)))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", metaEtag))
                .andExpect(header().string("Cache-Control", containsString("max-age=86400")));
        verify(gateway, never()).result(anyLong(), anyString());
        // 다른 크기의 태그는 맞지 않는다
        mvc.perform(
                        as(
                                viewer,
                                get(base + "/layers/hair?size=512&f=" + HeadFormat.VERSION)
                                        .header("If-None-Match", etag)))
                .andExpect(status().isOk());

        mvc.perform(get(hair256).header("If-None-Match", etag))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        as(
                                viewer,
                                get("/api/v1/avatar/heads/"
                                                + owner.memberId()
                                                + "/layer-job-x/layers/face?f="
                                                + HeadFormat.VERSION)
                                        .header(
                                                "If-None-Match",
                                                SelfieJobController.layerEtag(
                                                        new HeadLayerKey(
                                                                owner.memberId(),
                                                                "layer-job-x",
                                                                HeadLayer.FACE,
                                                                1024)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("JOB_NOT_FOUND"));
        mvc.perform(as(owner, delete("/api/v1/me"))).andExpect(status().isOk());
        mvc.perform(as(viewer, get(hair256).header("If-None-Match", etag)))
                .andExpect(status().isForbidden());
        mvc.perform(as(viewer, get(base + "/meta"))).andExpect(status().isForbidden());
    }

    @Test
    void unknownLayerOrSizeIs400AndMissingLayerIs404() throws Exception {
        Session owner = signUp();
        finishSelfie(owner, "layer-job-3", head(owner, false));
        Session viewer = signUp();
        String base = "/api/v1/avatar/heads/" + owner.memberId() + "/layer-job-3";

        layer(viewer, base, "frontImage", 256).andExpect(status().isBadRequest());
        layer(viewer, base, "face", 300).andExpect(status().isBadRequest());
        layer(viewer, base, "rightFace", 256)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("LAYER_NOT_FOUND"));
        mvc.perform(as(viewer, get(base + "/meta")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layers", contains("face", "eyes", "hair")))
                .andExpect(jsonPath("$.layerUrls['256'].*", hasSize(3)))
                .andExpect(jsonPath("$.layerUrls['256'].rightFace").doesNotExist())
                .andExpect(jsonPath("$.rightLayerLayout").doesNotExist());
        // 아직 끝나지 않은 남의 작업·없는 작업은 기존 머리와 같은 404
        mvc.perform(as(viewer, get("/api/v1/avatar/heads/" + owner.memberId() + "/nope/meta")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("JOB_NOT_FOUND"));
    }

    @Test
    void layerWithoutCurrentFormatServesCurrentContentButIsNotStored() throws Exception {
        Session owner = signUp();
        finishSelfie(owner, "layer-job-4", head(owner, true));
        Session viewer = signUp();
        String base = "/api/v1/avatar/heads/" + owner.memberId() + "/layer-job-4";

        for (String query : List.of("?size=1024", "?size=1024&f=png1", "?size=1024&f=")) {
            byte[] png =
                    mvc.perform(as(viewer, get(base + "/layers/face" + query)))
                            .andExpect(status().isOk())
                            .andExpect(content().contentType(MediaType.IMAGE_PNG))
                            .andExpect(header().string("Cache-Control", "no-store"))
                            .andExpect(header().doesNotExist("ETag"))
                            .andReturn()
                            .getResponse()
                            .getContentAsByteArray();
            assertThat(png).as(query).isEqualTo(FACE);
        }
        // 옛 판 URL은 태그가 맞아도 304가 아니라 지금 내용을 준다
        String etag =
                SelfieJobController.layerEtag(
                        new HeadLayerKey(owner.memberId(), "layer-job-4", HeadLayer.FACE, 1024));
        mvc.perform(
                        as(
                                viewer,
                                get(base + "/layers/face?size=1024&f=png1")
                                        .header("If-None-Match", etag)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        // 모르는 크기·층은 판과 관계없이 400
        mvc.perform(as(viewer, get(base + "/layers/face?size=300&f=png1")))
                .andExpect(status().isBadRequest());
        mvc.perform(as(viewer, get(base + "/layers/frontImage?f=png1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void busyFirstLoadIs503HeadBusy() throws Exception {
        Session owner = signUp();
        finishSelfie(owner, "layer-job-5", head(owner, true));
        Session viewer = signUp();
        String base = "/api/v1/avatar/heads/" + owner.memberId() + "/layer-job-5";
        when(gateway.result(owner.memberId(), "layer-job-5"))
                .thenThrow(new FaceJobRejectedException(503, HeadLayerCache.HEAD_BUSY));

        mvc.perform(as(viewer, get(base + "/meta")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.reason").value("HEAD_BUSY"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    private ResultActions layer(Session viewer, String base, String layer, int size)
            throws Exception {
        return mvc.perform(
                as(
                        viewer,
                        get(base
                                        + "/layers/"
                                        + layer
                                        + "?size="
                                        + size
                                        + "&f="
                                        + HeadFormat.VERSION)
                                .accept(MediaType.IMAGE_PNG, MediaType.APPLICATION_JSON)));
    }

    private static org.springframework.test.web.servlet.ResultMatcher immutable() {
        return result -> {
            header().string("Cache-Control", containsString("private")).match(result);
            header().string("Cache-Control", containsString("max-age=31536000")).match(result);
            header().string("Cache-Control", containsString("immutable")).match(result);
        };
    }

    /** 셀카 작업 하나를 끝까지 돌려 얼굴에 반영한다. */
    private void finishSelfie(Session user, String jobId, GeneratedHead head) throws Exception {
        mvc.perform(as(user, put("/api/v1/me/consents/FACE_TRANSFER"))).andExpect(status().isOk());
        when(gateway.submit(eq(user.memberId()), any(), anyInt()))
                .thenReturn(new FaceJob(jobId, FaceJob.Status.QUEUED, null, null));
        mvc.perform(
                        as(
                                user,
                                multipart("/api/v1/avatar/selfie/jobs")
                                        .file(
                                                new MockMultipartFile(
                                                        "selfie",
                                                        "photo.jpg",
                                                        MediaType.IMAGE_JPEG_VALUE,
                                                        JPEG))))
                .andExpect(status().isAccepted());
        when(gateway.status(user.memberId(), jobId))
                .thenReturn(new FaceJob(jobId, FaceJob.Status.SUCCEEDED, head.headId(), null));
        mvc.perform(as(user, get("/api/v1/avatar/selfie/jobs/" + jobId)))
                .andExpect(status().isOk());
        when(gateway.result(user.memberId(), jobId)).thenReturn(head);
        mvc.perform(as(user, get("/api/v1/avatar/selfie/jobs/" + jobId + "/result")))
                .andExpect(status().isOk());
    }

    private static GeneratedHead head(Session owner, boolean withRight) {
        String face = data(FACE);
        String other = data(OTHER);
        return new GeneratedHead(
                "head-l",
                Long.toString(owner.memberId()),
                face,
                other,
                other,
                Map.of("hair", "short"),
                "short",
                Map.of("pivotX", 0.5),
                Map.of(
                        "renderFaceImage",
                        other,
                        "neckMask",
                        Map.of("centerX", 512, "chinY", 750),
                        "skinColor",
                        List.of(200, 150, 120)),
                "2026-11-17T00:00:00Z",
                other,
                other,
                withRight ? other : null,
                withRight ? other : null,
                withRight ? other : null,
                withRight ? Map.of("scale", 1.0) : null);
    }

    private static String data(byte[] png) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
    }

    private static byte[] png(int argb) {
        BufferedImage image = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
        for (int y = 300; y < 724; y++) {
            for (int x = 300; x < 724; x++) image.setRGB(x, y, argb);
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (java.io.IOException error) {
            throw new java.io.UncheckedIOException(error);
        }
    }
}
