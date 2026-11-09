package com.a207.runkk;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 시험 문맥의 Clock 빈을 Mockito 목으로 바꾸지 않는다(S15P31A207-275). 목은 문맥이 뜰 때와 각 시험이 끝나 초기화된 뒤
 * getZone()·instant()가 null이라, 같은 문맥에서 도는 주기 작업(PollReminderJob 1분 등)이 NPE를 내고 ERROR 로그를 남긴다. 시간을
 * 움직여야 하면 시간대를 늘 가진 Clock 하위 클래스를 {@code @TestBean}으로 넣는다(AiPrivacyWithdrawalIntegrationTest).
 */
@DisplayName("시험 문맥 Clock 규칙")
class TestClockRuleTest {

    @Test
    @DisplayName("@MockitoBean Clock 필드가 없다")
    void noMockitoBeanClock() {
        JavaClasses tests =
                new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                        .importPackages("com.a207.runkk");
        noFields()
                .that()
                .haveRawType(Clock.class)
                .should()
                .beAnnotatedWith(MockitoBean.class)
                .because("목 Clock은 시간대가 없어 시험 문맥의 주기 작업이 NPE를 낸다")
                .check(tests);
    }
}
