package com.a207.runkk.infra.integration.ai;

import com.a207.runkk.avatar.AvatarBody;
import com.a207.runkk.avatar.FaceJob;
import com.a207.runkk.avatar.FaceJobGateway;
import com.a207.runkk.avatar.FaceJobRejectedException;
import com.a207.runkk.avatar.GeneratedHead;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 각 요청은 접수·조회만 수행한다. 생성 완료를 기다리며 HTTP 요청을 붙잡지 않는다. */
@Component
public class AiServerFaceJobGateway implements FaceJobGateway {
    private static final int MAX_RESPONSE_BYTES = 32 * 1024 * 1024;
    private static final String DATA_PNG = "data:image/png;base64,";
    private static final Set<String> REASONS =
            Set.of(
                    "UNSUPPORTED_IMAGE",
                    "TOO_LARGE",
                    "UPSTREAM_FAILED",
                    "TIMEOUT",
                    "JOB_NOT_FOUND",
                    "JOB_NOT_READY",
                    "HEAD_NOT_FOUND",
                    "BODY_NOT_FOUND");
    private static final Set<String> BODIES = Set.of("daily", "hoodie", "windbreaker", "singlet");
    private final AiServerClient client;
    private final ObjectMapper json;

    public AiServerFaceJobGateway(AiServerClient client, ObjectMapper json) {
        this.client = client;
        this.json = json;
    }

    @Override
    public FaceJob submit(long memberId, byte[] image) {
        requireMember(memberId);
        FaceJob job =
                exchange(
                        client.faces()
                                .post()
                                .uri("/users/{id}/faces/jobs", memberId)
                                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                .body(image),
                        202,
                        FaceJob.class);
        return job;
    }

    @Override
    public FaceJob status(long memberId, String jobId) {
        requireMember(memberId);
        requireId(jobId);
        FaceJob job =
                exchange(
                        client.faces().get().uri("/users/{id}/faces/jobs/{job}", memberId, jobId),
                        200,
                        FaceJob.class);
        if (!jobId.equals(job.jobId())) throw upstreamFailed();
        return job;
    }

    @Override
    public GeneratedHead result(long memberId, String jobId) {
        requireMember(memberId);
        requireId(jobId);
        return validateHead(
                memberId,
                exchange(
                        client.faces()
                                .get()
                                .uri("/users/{id}/faces/jobs/{job}/result", memberId, jobId),
                        200,
                        GeneratedHead.class));
    }

    @Override
    public GeneratedHead latest(long memberId) {
        requireMember(memberId);
        return validateHead(
                memberId,
                exchange(
                        client.faces().get().uri("/users/{id}/faces/latest", memberId),
                        200,
                        GeneratedHead.class));
    }

    @Override
    public AvatarBody body(String bodyId) {
        if (!BODIES.contains(bodyId == null ? "" : bodyId)) {
            throw new IllegalArgumentException("몸 프리셋 이름 오류");
        }
        AvatarBody body =
                exchange(
                        client.faces().get().uri("/avatar/bodies/{id}", bodyId),
                        200,
                        AvatarBody.class);
        if (body.pack() == null || body.pack().isEmpty()) throw upstreamFailed();
        return body;
    }

    private <T> T exchange(RestClient.RequestHeadersSpec<?> request, int expected, Class<T> type) {
        try {
            return request.exchange(
                    (sent, response) -> {
                        byte[] bytes = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                        if (bytes.length > MAX_RESPONSE_BYTES) throw upstreamFailed();
                        JsonNode body = json.readTree(bytes);
                        int status = response.getStatusCode().value();
                        if (status != expected) throw rejected(status, body);
                        try {
                            T value =
                                    json.readerFor(type)
                                            .without(
                                                    DeserializationFeature
                                                            .FAIL_ON_UNKNOWN_PROPERTIES)
                                            .readValue(body.traverse(json));
                            if (value == null) throw upstreamFailed();
                            return value;
                        } catch (IOException | IllegalArgumentException error) {
                            throw upstreamFailed();
                        }
                    },
                    true);
        } catch (RestClientException error) {
            throw timedOut(error) ? new FaceJobRejectedException(504, "TIMEOUT") : upstreamFailed();
        }
    }

    private static FaceJobRejectedException rejected(int status, JsonNode body) {
        String reason = body == null ? "" : body.path("reason").asText();
        if (!REASONS.contains(reason)
                || !Set.of(404, 409, 413, 415, 422, 503, 504).contains(status)) {
            return upstreamFailed();
        }
        return new FaceJobRejectedException(status, reason);
    }

    private static GeneratedHead validateHead(long memberId, GeneratedHead head) {
        if (head.headId() == null
                || !head.headId().matches("[A-Za-z0-9_.:-]{1,64}")
                || (head.userId() != null && !head.userId().equals(Long.toString(memberId)))) {
            throw upstreamFailed();
        }
        validatePng(head.faceImage());
        validatePng(head.eyesImage());
        validatePng(head.hairImage());
        if (head.mobile() != null && head.mobile().containsKey("renderFaceImage")) {
            Object render = head.mobile().get("renderFaceImage");
            validatePng(render instanceof String image ? image : null);
        }
        return head;
    }

    private static void validatePng(String data) {
        if (data == null || !data.startsWith(DATA_PNG)) throw upstreamFailed();
        try {
            byte[] image = Base64.getDecoder().decode(data.substring(DATA_PNG.length()));
            byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
            if (image.length < 24) throw upstreamFailed();
            for (int index = 0; index < signature.length; index++) {
                if (image[index] != signature[index]) throw upstreamFailed();
            }
            ByteBuffer header = ByteBuffer.wrap(image);
            if (header.getInt(8) != 13 || header.getInt(12) != 0x49484452) throw upstreamFailed();
            int width = header.getInt(16);
            int height = header.getInt(20);
            // 표본은 1픽셀 PNG다. 실제 1024픽셀 계약의 상한을 검사해 압축 해제 크기를 제한한다.
            if (width <= 0 || width > 1024 || height <= 0 || height > 1024) throw upstreamFailed();
        } catch (IllegalArgumentException error) {
            throw upstreamFailed();
        }
    }

    private static void requireMember(long memberId) {
        if (memberId <= 0) throw new IllegalArgumentException("회원 ID 오류");
    }

    private static void requireId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_.:-]{1,64}")) {
            throw new IllegalArgumentException("작업 ID 오류");
        }
    }

    private static boolean timedOut(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpConnectTimeoutException) return false;
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException)
                return true;
        }
        return false;
    }

    private static FaceJobRejectedException upstreamFailed() {
        return new FaceJobRejectedException(503, "UPSTREAM_FAILED");
    }
}
