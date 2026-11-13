package com.a207.runkk.infra.integration.ai;

import com.a207.runkk.aiprivacy.AiDataEraser;
import com.a207.runkk.aiprivacy.AiUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class AiServerPrivacyGateway implements AiDataEraser {
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final Set<String> REASONS =
            Set.of("STORAGE_UNAVAILABLE", "STORAGE_TIMEOUT", "INVALID_REQUEST");
    private final AiServerClient client;
    private final ObjectMapper json;

    public AiServerPrivacyGateway(AiServerClient client, ObjectMapper json) {
        this.client = client;
        this.json = json;
    }

    @Override
    public void erase(long memberId) {
        if (memberId <= 0) throw new IllegalArgumentException("회원 ID 오류");
        try {
            client.privacy()
                    .delete()
                    .uri("/users/{id}", Long.toString(memberId))
                    .exchange(
                            (request, response) -> {
                                byte[] bytes =
                                        response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                                if (bytes.length > MAX_RESPONSE_BYTES) throw unavailable();
                                JsonNode body = json.readTree(bytes);
                                if (response.getStatusCode().value() != 200) {
                                    String reason =
                                            body == null ? "" : body.path("reason").asText();
                                    throw REASONS.contains(reason)
                                            ? new AiUnavailableException(reason)
                                            : unavailable();
                                }
                                if (!count(body, "sessionResults") || !count(body, "coachMessages"))
                                    throw unavailable();
                                if (body.has("avatarImages")
                                        && (!count(body, "avatarImages")
                                                || body.path("avatarImages").asLong() > 1))
                                    throw unavailable();
                                return null;
                            },
                            true);
        } catch (RestClientException error) {
            throw unavailable();
        }
    }

    private static boolean count(JsonNode body, String name) {
        return body != null
                && body.path(name).isIntegralNumber()
                && body.path(name).canConvertToLong()
                && body.path(name).asLong() >= 0;
    }

    private static AiUnavailableException unavailable() {
        return new AiUnavailableException("UPSTREAM_FAILED");
    }
}
