package com.a207.runkk.infra.integration.ai;

import com.a207.runkk.avatar.FacePart;
import com.a207.runkk.avatar.FaceTransformGateway;
import com.a207.runkk.avatar.FaceTransformOutcome;
import com.a207.runkk.avatar.FailureReason;
import com.a207.runkk.avatar.SdFacePartImages;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * ai 서버 POST /faces를 부른다(설계 6.1.13, SQ-11, D-23, D-59). 이미지를 application/octet-stream 본문으로
 * 보내고(multipart 아님) 회원 식별자를 보내지 않는다. 200이면 FaceOut의 부위별 base64 PNG를 SdFacePartImages로, 200이 아니면 본문의
 * reason을 FailureReason으로 바꾼다. reason이 없거나 모르는 값이면 UPSTREAM_FAILED(설계 공백 U15). 401은 내부 토큰 설정 오류라 경고
 * 로그를 남기고 UPSTREAM_FAILED다. 읽기 시간 초과는 TIMEOUT이다. 이미지·응답 본문을 로그에 남기지 않고 응답 뒤 참조를 버린다.
 */
@Component
public class AiServerFaceGateway implements FaceTransformGateway {

    static final String PATH = "/faces";

    private static final Logger log = LoggerFactory.getLogger(AiServerFaceGateway.class);

    private final AiServerClient client;
    private final ObjectMapper objectMapper;

    public AiServerFaceGateway(AiServerClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public FaceTransformOutcome transform(byte[] image) {
        try {
            return client.faces()
                    .post()
                    .uri(PATH)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(image)
                    .exchange(
                            (request, response) ->
                                    translate(response.getStatusCode().value(), response.getBody()),
                            true);
        } catch (RestClientException e) {
            FailureReason reason =
                    isReadTimeout(e) ? FailureReason.TIMEOUT : FailureReason.UPSTREAM_FAILED;
            log.warn("ai_face_failed reason={} error={}", reason, e.getClass().getSimpleName());
            return FaceTransformOutcome.failure(reason);
        }
    }

    private FaceTransformOutcome translate(int status, InputStream body) {
        JsonNode json = readJson(body).orElse(null);
        if (status == HttpStatus.OK.value()) {
            return partsOf(json)
                    .map(FaceTransformOutcome::success)
                    .orElseGet(() -> failed(status, FailureReason.UPSTREAM_FAILED));
        }
        if (status == HttpStatus.UNAUTHORIZED.value()) {
            log.warn("ai_face_unauthorized status=401 hint=RUNKK_INTERNAL_TOKEN 불일치");
            return FaceTransformOutcome.failure(FailureReason.UPSTREAM_FAILED);
        }
        return failed(status, reasonOf(json));
    }

    private static FaceTransformOutcome failed(int status, FailureReason reason) {
        log.info("ai_face_rejected status={} reason={}", status, reason);
        return FaceTransformOutcome.failure(reason);
    }

    private Optional<JsonNode> readJson(InputStream body) {
        try {
            return Optional.ofNullable(objectMapper.readTree(body));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** FaceOut.parts의 5개 부위가 모두 base64 PNG면 이미지 묶음. */
    private static Optional<SdFacePartImages> partsOf(JsonNode json) {
        if (json == null || !json.path("parts").isObject()) {
            return Optional.empty();
        }
        Map<FacePart, byte[]> images = new EnumMap<>(FacePart.class);
        try {
            for (FacePart part : FacePart.values()) {
                JsonNode encoded = json.path("parts").path(part.name());
                if (!encoded.isTextual()) {
                    return Optional.empty();
                }
                images.put(part, Base64.getDecoder().decode(encoded.asText()));
            }
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return Optional.of(new SdFacePartImages(images));
    }

    private static FailureReason reasonOf(JsonNode json) {
        String reason = json == null ? "" : json.path("reason").asText("");
        return Arrays.stream(FailureReason.values())
                .filter(r -> r.name().equals(reason))
                .findFirst()
                .orElse(FailureReason.UPSTREAM_FAILED);
    }

    /** 읽기 시간 초과만 TIMEOUT. 연결 시간 초과(ai 컨테이너 없음)는 UPSTREAM_FAILED다. */
    private static boolean isReadTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpConnectTimeoutException) {
                return false;
            }
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
