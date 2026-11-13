package com.a207.runkk.aimodel;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;

public record AiModel(
        String modelId,
        String baseUrl,
        String note,
        Boolean active,
        OffsetDateTime registeredAt,
        OffsetDateTime activatedAt) {
    public AiModel {
        if (modelId == null
                || modelId.isBlank()
                || modelId.codePointCount(0, modelId.length()) > 64
                || baseUrl == null
                || baseUrl.isBlank()
                || active == null
                || registeredAt == null) {
            throw new IllegalArgumentException("불완전한 writer 모델 응답");
        }
    }

    AiModel withoutUrlCredentials() {
        String endpoint;
        try {
            URI url = URI.create(baseUrl);
            if (url.getHost() == null
                    || !("http".equals(url.getScheme()) || "https".equals(url.getScheme()))) {
                endpoint = "[주소 숨김]";
            } else {
                endpoint =
                        url.getScheme()
                                + "://"
                                + url.getHost()
                                + (url.getPort() == -1 ? "" : ":" + url.getPort())
                                + url.getRawPath();
            }
        } catch (IllegalArgumentException failure) {
            endpoint = "[주소 숨김]";
        }
        return new AiModel(modelId, endpoint, note, active, registeredAt, activatedAt);
    }

    public record Catalog(List<AiModel> models, String active) {
        public Catalog {
            models = List.copyOf(models);
            List<String> activeIds =
                    models.stream().filter(AiModel::active).map(AiModel::modelId).toList();
            if (!activeIds.equals(active == null ? List.of() : List.of(active))) {
                throw new IllegalArgumentException("일치하지 않는 활성 writer 모델");
            }
        }
    }
}
