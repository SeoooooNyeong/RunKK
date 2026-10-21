package com.a207.runkk.web.admin.setting;

import com.a207.runkk.policy.OperationSettingService;
import com.a207.runkk.policy.SettingChangeResult;
import com.a207.runkk.policy.SettingKey;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영 기준값 조회·변경(설계 5.7.1 PUT /admin/settings/{key} → OperationSettingService.change, FR-174). 본문은 그
 * 항목의 값 JSON 그대로다. 형식·범위·상점 아이템 전후 검사에 걸리면 409 + VALUE_INVALID(D-44, D-62). 운영자는 web.admin이 붙인 요청
 * 속성 actorId로 받고 operator·web.admin을 import하지 않는다(D-50). GET /admin/settings(지금 값 보기)는 5.7.1 밖 추가다.
 */
@RestController
@RequestMapping("/api/v1/admin/settings")
public class SettingAdminController {

    private static final String ACTOR_ID = "actorId";

    private final OperationSettingService operationSettingService;

    public SettingAdminController(OperationSettingService operationSettingService) {
        this.operationSettingService = operationSettingService;
    }

    @GetMapping
    Map<SettingKey, String> current(@RequestAttribute(ACTOR_ID) String actorId) {
        return operationSettingService.currentValues();
    }

    @PutMapping("/{key}")
    SettingChangeResult change(
            @RequestAttribute(ACTOR_ID) String actorId,
            @PathVariable SettingKey key,
            @RequestBody JsonNode value) {
        return operationSettingService.change(actorId, key, value.toString());
    }
}
