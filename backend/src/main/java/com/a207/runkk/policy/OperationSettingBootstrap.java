package com.a207.runkk.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 운영 기준값 처음 넣기(S15P31A207-222). 관리 API는 운영자 소셜 토큰이 있어야 해서 개발 서버(develop)에서 2차 배포 기능 공개 스위치
 * (CREW_RELEASED·AVATAR_RELEASED·RUNBODY_ENABLED)를 배포만으로 켤 길이 없다. 그래서 환경 변수
 * RUNKK_BOOTSTRAP_SETTINGS("KEY=값,KEY=값", 값은 관리툴과 같은 JSON)가 있으면 시작 때 그 키의 행이 없을 때만
 * OperationSettingService.change로 넣는다. 그 경로가 감사 기록(SETTING_CHANGE, 행위자 {@value #ACTOR_ID})과 커밋 뒤 캐시
 * 무효화를 함께 한다. 행이 이미 있으면 관리툴로 바꾼 값이므로 덮지 않는다. 모르는 키·형식 오류는 WARN만 남기고 건너뛰어 기동을 막지 않는다. 운영자
 * 시드(operator.OperatorBootstrap)와 같은 방식이고, 시작 작업은 그 작업을 가진 패키지가 한다(D-55).
 */
@Component
public class OperationSettingBootstrap {

    /** 감사 기록에 남는 시작 시드 행위자. */
    static final String ACTOR_ID = "bootstrap:RUNKK_BOOTSTRAP_SETTINGS";

    /** 다음 항목 이름(대문자 KEY 뒤 "=", 쉼표, 끝) 앞의 쉼표에서만 나눈다. JSON 값 안의 쉼표는 그대로 둔다. */
    private static final Pattern ENTRY_SEPARATOR =
            Pattern.compile(",(?=\\s*[A-Z][A-Z0-9_]*\\s*(?:=|,|$))");

    private static final Logger log = LoggerFactory.getLogger(OperationSettingBootstrap.class);

    private final OperationSettingRepository repository;
    private final OperationSettingService settingService;
    private final String settings;

    @Autowired
    public OperationSettingBootstrap(
            OperationSettingRepository repository,
            OperationSettingService settingService,
            Seed seed) {
        this(repository, settingService, seed.settings());
    }

    OperationSettingBootstrap(
            OperationSettingRepository repository,
            OperationSettingService settingService,
            String settings) {
        this.repository = repository;
        this.settingService = settingService;
        this.settings = settings;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        seedMissing();
    }

    /**
     * 넣은 키를 적은 순서대로 돌려준다. 값이 없거나(null) 빈 문자열이면 로그 없이 빈 목록이다. compose가 .env에 없는 변수를 빈 문자열로
     * 넘기므로(${RUNKK_BOOTSTRAP_SETTINGS:-}) prod처럼 쓰지 않는 환경에서 매 기동 경고가 남지 않게 한다. 항목마다 따로 처리해 한 항목이
     * 틀려도 나머지는 넣고, 예외를 던지지 않는다.
     */
    public List<SettingKey> seedMissing() {
        if (settings == null || settings.isBlank()) {
            return List.of();
        }
        List<SettingKey> seeded = new ArrayList<>();
        for (String entry : ENTRY_SEPARATOR.split(settings.trim())) {
            seedOne(entry.trim()).ifPresent(seeded::add);
        }
        return seeded;
    }

    private Optional<SettingKey> seedOne(String entry) {
        int eq = entry.indexOf('=');
        if (eq <= 0) {
            log.warn("setting_bootstrap_skipped reason=format");
            return Optional.empty();
        }
        String name = entry.substring(0, eq).trim();
        String value = entry.substring(eq + 1).trim();
        SettingKey key;
        try {
            key = SettingKey.valueOf(name);
        } catch (IllegalArgumentException e) {
            log.warn("setting_bootstrap_skipped reason=unknown_key key={}", name);
            return Optional.empty();
        }
        if (!key.validate(value).valid()) {
            log.warn("setting_bootstrap_skipped reason=invalid_value key={}", key);
            return Optional.empty();
        }
        try {
            if (repository.find(key).isPresent()) {
                log.info("setting_bootstrap skipped=row_present key={}", key);
                return Optional.empty();
            }
            settingService.change(ACTOR_ID, key, value);
        } catch (RuntimeException e) {
            log.warn("setting_bootstrap_failed key={} error={}", key, e.getClass().getSimpleName());
            return Optional.empty();
        }
        log.info("setting_bootstrap seeded key={}", key);
        return Optional.of(key);
    }

    /**
     * 운영 기준값 시드. 환경 변수 RUNKK_BOOTSTRAP_SETTINGS가 이 값(runkk.bootstrap.settings)에 그대로 묶인다. 변수가 없으면
     * null(시드 없음, 로그 없음)이라 application.yml에 빈 기본값을 두지 않는다.
     *
     * @param settings "CREW_RELEASED=true,AVATAR_RELEASED=true"(없으면 넣지 않는다)
     */
    @ConfigurationProperties("runkk.bootstrap")
    public record Seed(String settings) {}
}
