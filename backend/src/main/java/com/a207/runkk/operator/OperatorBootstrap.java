package com.a207.runkk.operator;

import com.a207.runkk.member.ProviderType;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.policy.OperationSettingService;
import com.a207.runkk.policy.SettingKey;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;
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

    @Autowired
    public OperatorBootstrap(
            OperationPolicy policy,
            OperationSettingService settingService,
            ObjectMapper objectMapper,
            Seed seed) {
        this(policy, settingService, objectMapper, seed.operators());
    }

    OperatorBootstrap(
            OperationPolicy policy,
            OperationSettingService settingService,
            ObjectMapper objectMapper,
            String operators) {
        this.policy = policy;
        this.settingService = settingService;
        this.objectMapper = objectMapper;
        this.operators = operators;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        seedIfEmpty();
    }

    /**
     * 시드를 넣었으면 true(D-166, 부록 E K-5). 값이 없으면(null) 로그 없이 false, 빈 문자열(공백만 포함)이면 설정 실수일 수 있어 WARN 1줄
     * (operator_seed_empty) 뒤 false. 형식 오류·검사 실패(제공자는 대문자 KAKAO·GOOGLE만)도 저장 0·WARN 1줄이고 예외를 던지지 않아
     * 기동은 계속된다.
     */
    public boolean seedIfEmpty() {
        if (operators == null) {
            return false;
        }
        if (!policy.operatorWhitelist().accounts().isEmpty()) {
            log.info("operator_bootstrap skipped=whitelist_present");
            return false;
        }
        if (operators.isBlank()) {
            log.warn("operator_seed_empty");
            return false;
        }
        try {
            settingService.change(ACTOR_ID, SettingKey.OPERATOR_WHITELIST, whitelistJson());
            log.info("operator_bootstrap seeded=true");
            return true;
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("operator_bootstrap_failed error={}", e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * "KAKAO:1,GOOGLE:a" → {"accounts":{"GOOGLE":["a"],"KAKAO":["1"]}}. 제공자는 ProviderType 이름(대문자)만
     * 받는다. 관리툴 검사(SettingKey.OPERATOR_WHITELIST)와 같은 허용 값이다(D-87, D-166).
     */
    private String whitelistJson() throws JsonProcessingException {
        Map<String, Set<String>> accounts = new TreeMap<>();
        for (String entry : operators.split(",")) {
            String[] parts = entry.trim().split(":", 2);
            if (parts.length != 2 || parts[1].isBlank()) {
                throw new IllegalArgumentException("RUNKK_BOOTSTRAP_OPERATORS 형식 오류");
            }
            String provider = ProviderType.valueOf(parts[0].trim()).name();
            accounts.computeIfAbsent(provider, p -> new TreeSet<>()).add(parts[1].trim());
        }
        return objectMapper.writeValueAsString(Map.of("accounts", accounts));
    }

    /**
     * 첫 운영자 시드(code-review U10, 코드 리뷰 2차 L1). 환경 변수 RUNKK_BOOTSTRAP_OPERATORS가 이
     * 값(runkk.bootstrap.operators)에 그대로 묶인다. 변수가 없으면 null(시드 없음, 로그 없음)이고 빈 문자열이면 설정 실수로 보고 WARN
     * 1줄이다(D-166). application.yml에 빈 기본값을 두지 않는다.
     *
     * @param operators "KAKAO:계정ID,GOOGLE:계정ID"(없으면 넣지 않는다)
     */
    @ConfigurationProperties("runkk.bootstrap")
    public record Seed(String operators) {}
}
