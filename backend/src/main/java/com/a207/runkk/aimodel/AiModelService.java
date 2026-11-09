package com.a207.runkk.aimodel;

import com.a207.runkk.audit.ActorRole;
import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditEntry;
import com.a207.runkk.audit.AuditService;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiModelService {
    private final AiModelGateway gateway;
    private final AuditService audit;
    private final Set<String> allowedUrls;

    public AiModelService(
            AiModelGateway gateway,
            AuditService audit,
            @Value("${runkk.ai.writer-model-urls}") String allowedUrls) {
        this.gateway = gateway;
        this.audit = audit;
        this.allowedUrls =
                Arrays.stream(allowedUrls.split(","))
                        .map(String::strip)
                        .filter(url -> !url.isEmpty())
                        .map(AiModelService::writerUrl)
                        .collect(Collectors.toUnmodifiableSet());
    }

    public AiModel.Catalog list() {
        AiModel.Catalog catalog = gateway.list();
        return new AiModel.Catalog(
                catalog.models().stream().map(AiModel::withoutUrlCredentials).toList(),
                catalog.active());
    }

    public AiModel register(String actorId, String id, String url, String note) {
        validateId(id);
        String endpoint = writerUrl(url);
        if (!allowedUrls.contains(endpoint)
                || (note != null && note.codePointCount(0, note.length()) > 500)) {
            throw new IllegalArgumentException("허용되지 않는 writer 등록 요청");
        }
        return change(
                actorId,
                AuditAction.WRITER_MODEL_REGISTER,
                id,
                () -> gateway.register(id, endpoint, note));
    }

    public AiModel activate(String actorId, String id) {
        validateId(id);
        return change(actorId, AuditAction.WRITER_MODEL_ACTIVATE, id, () -> gateway.activate(id));
    }

    public AiModel rollback(String actorId) {
        return change(
                actorId, AuditAction.WRITER_MODEL_ROLLBACK, "WRITER_REGISTRY", gateway::rollback);
    }

    private synchronized AiModel change(
            String actorId, AuditAction action, String target, Supplier<AiModel> request) {
        // AI가 응답 전에 변경했을 수 있으므로 요청을 먼저 감사에 남긴다.
        audit.record(
                new AuditEntry(actorId, ActorRole.OPERATOR, action, target, null, "REQUESTED"));
        AiModel model = request.get();
        audit.record(
                new AuditEntry(
                        actorId, ActorRole.OPERATOR, action, model.modelId(), null, "SUCCEEDED"));
        return model.withoutUrlCredentials();
    }

    private static void validateId(String id) {
        if (id == null
                || id.isBlank()
                || id.codePointCount(0, id.length()) > 64
                || id.equals(".")
                || id.equals("..")
                || id.contains("/")
                || id.contains("\\")
                || id.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("잘못된 writer 모델 ID");
        }
    }

    private static String writerUrl(String value) {
        if (value == null || value.length() > 200) {
            throw new IllegalArgumentException("잘못된 writer 주소");
        }
        String endpoint = value.replaceFirst("/+$", "");
        URI url = URI.create(endpoint);
        if (!("http".equals(url.getScheme()) || "https".equals(url.getScheme()))
                || url.getHost() == null
                || url.getRawUserInfo() != null
                || url.getRawQuery() != null
                || url.getRawFragment() != null
                || !url.normalize().equals(url)) {
            throw new IllegalArgumentException("잘못된 writer 주소");
        }
        return endpoint;
    }
}
