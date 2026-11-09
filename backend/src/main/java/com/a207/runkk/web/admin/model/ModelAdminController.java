package com.a207.runkk.web.admin.model;

import com.a207.runkk.aimodel.AiModel;
import com.a207.runkk.aimodel.AiModelService;
import com.a207.runkk.aimodel.ModelRejectedException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/models")
public class ModelAdminController {
    private static final String ACTOR_ID = "actorId";
    private final AiModelService models;

    public ModelAdminController(AiModelService models) {
        this.models = models;
    }

    @GetMapping
    ResponseEntity<AiModel.Catalog> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(models.list());
    }

    @PostMapping
    ResponseEntity<AiModel> register(
            @RequestAttribute(ACTOR_ID) String actorId,
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        models.register(
                                actorId, request.modelId(), request.baseUrl(), request.note()));
    }

    @PostMapping("/{id}/activate")
    AiModel activate(@RequestAttribute(ACTOR_ID) String actorId, @PathVariable String id) {
        return models.activate(actorId, id);
    }

    @PostMapping("/rollback")
    AiModel rollback(@RequestAttribute(ACTOR_ID) String actorId) {
        return models.rollback(actorId);
    }

    @ExceptionHandler(ModelRejectedException.class)
    ResponseEntity<Map<String, String>> reject(ModelRejectedException failure) {
        HttpStatus status =
                switch (failure.reason()) {
                    case MODEL_NOT_FOUND -> HttpStatus.NOT_FOUND;
                    case NO_PREVIOUS_MODEL -> HttpStatus.CONFLICT;
                    case MODEL_REGISTRY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
                };
        String message =
                switch (failure.reason()) {
                    case MODEL_NOT_FOUND -> "등록된 모델이 없어요";
                    case NO_PREVIOUS_MODEL -> "되돌릴 모델이 없어요";
                    case MODEL_REGISTRY_UNAVAILABLE -> "모델 관리 서버의 응답을 확인하지 못했어요";
                };
        return ResponseEntity.status(status)
                .body(Map.of("code", failure.reason().name(), "message", message));
    }

    record RegisterRequest(@NotBlank String modelId, @NotBlank String baseUrl, String note) {}
}
