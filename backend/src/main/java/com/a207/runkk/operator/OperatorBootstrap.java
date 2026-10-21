package com.a207.runkk.operator;

import com.a207.runkk.member.ProviderType;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.policy.OperationSettingService;
import com.a207.runkk.policy.SettingKey;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 첫 운영자 넣기(code-review 01-backend U10, FR-173 "화이트리스트는 팀이 직접 등록"). OPERATOR_WHITELIST의 기본값이 빈 목록이고
 * 변경은 확인된 운영자만 할 수 있어 관리툴로는 첫 운영자를 만들 수 없다. 그래서 환경 변수
 * RUNKK_BOOTSTRAP_OPERATORS("KAKAO:계정ID,GOOGLE:계정ID")가 있고 화이트리스트가 비었을 때만 시작 때 한 번
 * OperationSettingService.change로 넣는다. 그 경로가 감사 기록(SETTING_CHANGE, 행위자 {@value #ACTOR_ID})과 캐시 무효화를
 * 함께 한다. 화이트리스트가 이미 있으면 아무것도 하지 않으므로 운영자가 관리툴에서 바꾼 목록을 덮지 않는다. 시작 작업을 그 작업을 가진 패키지가 하는 방식(D-55)을
 * 따른다.
 */
@Component
public class OperatorBootstrap {

    /** 감사 기록에 남는 시작 시드 행위자. */
    static final String ACTOR_ID = "bootstrap:RUNKK_BOOTSTRAP_OPERATORS";

    private static final Logger log = LoggerFactory.getLogger(OperatorBootstrap.class);

    private final OperationPolicy policy;
    private final OperationSettingService settingService;
    private final ObjectMapper objectMapper;
    private final String operators;

    public OperatorBootstrap(
            OperationPolicy policy,
            OperationSettingService settingService,
            ObjectMapper objectMapper,
            @Value("${runkk.operator.bootstrap-operators:}") String operators) {
        this.policy = policy;
        this.settingService = settingService;
        this.objectMapper = objectMapper;
        this.operators = operators;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        seedIfEmpty();
    }

    /** 시드를 넣었으면 true. 형식 오류면 기동은 계속하고 운영 로그만 남긴다. */
    public boolean seedIfEmpty() {
        if (operators == null || operators.isBlank()) {
            return false;
        }
        if (!policy.operatorWhitelist().accounts().isEmpty()) {
            log.info("operator_bootstrap skipped=whitelist_present");
            return false;
        }
        try {
            settingService.change(ACTOR_ID, SettingKey.OPERATOR_WHITELIST, whitelistJson());
            log.info("operator_bootstrap seeded=true");
            return true;
        } catch (RuntimeException | JsonProcessingException e) {
            log.error("operator_bootstrap_failed error={}", e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * "KAKAO:1,GOOGLE:a" → {"accounts":{"GOOGLE":["a"],"KAKAO":["1"]}}. 제공자는 ProviderType 이름만 받는다.
     */
    private String whitelistJson() throws JsonProcessingException {
        Map<String, Set<String>> accounts = new TreeMap<>();
        for (String entry : operators.split(",")) {
            String[] parts = entry.trim().split(":", 2);
            if (parts.length != 2 || parts[1].isBlank()) {
                throw new IllegalArgumentException("RUNKK_BOOTSTRAP_OPERATORS 형식 오류");
            }
            String provider = ProviderType.valueOf(parts[0].trim().toUpperCase(Locale.ROOT)).name();
            accounts.computeIfAbsent(provider, p -> new TreeSet<>()).add(parts[1].trim());
        }
        return objectMapper.writeValueAsString(Map.of("accounts", accounts));
    }
}
