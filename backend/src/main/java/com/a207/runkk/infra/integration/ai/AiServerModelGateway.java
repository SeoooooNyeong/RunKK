package com.a207.runkk.infra.integration.ai;

import com.a207.runkk.aimodel.AiModel;
import com.a207.runkk.aimodel.AiModelGateway;
import com.a207.runkk.aimodel.ModelRejectedException;
import com.a207.runkk.aimodel.ModelRejectedException.Reason;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AiServerModelGateway implements AiModelGateway {
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final String MODELS = "/models";

    private final RestClient http;
    private final ObjectMapper json;
    private final ObjectReader responses;

    public AiServerModelGateway(AiServerClient client, ObjectMapper json) {
        this.http = client.models();
        this.json = json;
        this.responses = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @Override
    public AiModel.Catalog list() {
        return send(http.get().uri(MODELS), 200, AiModel.Catalog.class, null);
    }

    @Override
    public AiModel register(String id, String url, String note) {
        AiModel model =
                send(
                        http.post()
                                .uri(MODELS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(new Registration(id, url, note)),
                        201,
                        AiModel.class,
                        null);
        if (!id.equals(model.modelId()) || !url.equals(model.baseUrl())) {
            throw unavailable();
        }
        return model;
    }

    @Override
    public AiModel activate(String id) {
        AiModel model =
                send(
                        http.post().uri(MODELS + "/{id}/activate", id),
                        200,
                        AiModel.class,
                        Reason.MODEL_NOT_FOUND);
        if (!id.equals(model.modelId()) || !model.active()) {
            throw unavailable();
        }
        return model;
    }

    @Override
    public AiModel rollback() {
        AiModel model =
                send(
                        http.post().uri(MODELS + "/rollback"),
                        200,
                        AiModel.class,
                        Reason.NO_PREVIOUS_MODEL);
        if (!model.active()) {
            throw unavailable();
        }
        return model;
    }

    private <T> T send(
            RestClient.RequestHeadersSpec<?> request,
            int expectedStatus,
            Class<T> type,
            Reason rejection) {
        try {
            return request.accept(MediaType.APPLICATION_JSON)
                    .exchange(
                            (sent, response) -> {
                                int status = response.getStatusCode().value();
                                int rejectionStatus =
                                        rejection == Reason.MODEL_NOT_FOUND ? 404 : 409;
                                if (status != expectedStatus
                                        && (rejection == null || status != rejectionStatus)) {
                                    throw unavailable();
                                }
                                byte[] bytes =
                                        response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                                if (bytes.length > MAX_RESPONSE_BYTES) {
                                    throw unavailable();
                                }
                                JsonNode body = responses.readTree(bytes);
                                if (body == null || !body.isObject()) {
                                    throw unavailable();
                                }
                                if (status != expectedStatus) {
                                    throw rejection.name().equals(body.path("reason").asText())
                                            ? new ModelRejectedException(rejection)
                                            : unavailable();
                                }
                                return json.treeToValue(body, type);
                            });
        } catch (RestClientException failure) {
            throw unavailable();
        }
    }

    private static ModelRejectedException unavailable() {
        return new ModelRejectedException(Reason.MODEL_REGISTRY_UNAVAILABLE);
    }

    private record Registration(String modelId, String baseUrl, String note) {}
}
