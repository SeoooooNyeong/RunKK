package com.a207.runkk.infra.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.TestcontainersConfig;
import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.PersonalityTag;
import com.a207.runkk.common.RecordVisibility;
import com.a207.runkk.common.Tier;
import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.Member;
import com.a207.runkk.member.MemberStatus;
import com.a207.runkk.member.MemberSummary;
import com.a207.runkk.member.Nickname;
import com.a207.runkk.member.ProviderType;
import com.a207.runkk.member.SettingChange;
import com.a207.runkk.member.SettingItem;
import com.a207.runkk.member.SignUpForm;
import com.a207.runkk.member.SocialAccount;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 14장 순서 2: infra.member를 실제 PostgreSQL(Flyway V3)로 확인한다. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
class JpaMemberRepositoryIntegrationTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 10, 1, 9, 0);

    @Autowired JpaMemberRepository repository;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from member_consent");
        jdbc.update("delete from member");
    }

    private long save(String nickname) {
        return tx.execute(
                s -> {
                    SignUpForm form =
                            new SignUpForm(
                                    nickname,
                                    1990,
                                    null,
                                    null,
                                    null,
                                    Map.of(
                                            ConsentItem.AGE_14_PLUS, true,
                                            ConsentItem.TERMS, true,
                                            ConsentItem.PRIVACY, true,
                                            ConsentItem.LOCATION, true,
                                            ConsentItem.LLM_TRANSFER, true),
                                    RecordVisibility.PUBLIC,
                                    Set.of(PersonalityTag.SOLO_RUN));
                    return repository
                            .save(
                                    Member.signUp(
                                            new SocialAccount(ProviderType.KAKAO, "k-" + nickname),
                                            form,
                                            Nickname.of(nickname),
                                            T))
                            .getId();
                });
    }

    @Test
    void saveAndQueries() {
        long a = save("곰A");
        long b = save("곰B");

        assertThat(repository.findByAccount(new SocialAccount(ProviderType.KAKAO, "k-곰A")))
                .map(Member::getId)
                .contains(a);
        assertThat(repository.existsByNickname("곰B")).isTrue();
        assertThat(repository.existsByNickname("곰Z")).isFalse();
        assertThat(repository.findAllById(List.of(a, b, 999L))).hasSize(2);
        assertThat(repository.rankedMemberIds()).containsExactlyInAnyOrder(a, b);
        assertThat(repository.ghostOwners(Set.of(Tier.UNRANKED), a))
                .singleElement()
                .satisfies(o -> assertThat(o.displayName()).isEqualTo(DisplayName.of("곰B")));

        Map<Long, MemberSummary> summaries = repository.summaries(List.of(a, b));
        assertThat(summaries.get(a).tags().tags()).containsExactly(PersonalityTag.SOLO_RUN);

        Integer consents =
                jdbc.queryForObject(
                        "select count(*) from member_consent where member_id = ?",
                        Integer.class,
                        a);
        assertThat(consents).isEqualTo(5);
    }

    @Test
    void settingsConsentsAndWithdrawal_roundTrip() {
        long a = save("곰A");
        tx.executeWithoutResult(
                s -> {
                    Member m = repository.findById(a).orElseThrow();
                    m.changeSetting(new SettingChange(SettingItem.ALERT, "false", "KICKED"));
                    m.changeSetting(
                            new SettingChange(
                                    SettingItem.RUN_SETTINGS,
                                    "{\"target\":\"KM5\",\"voice\":false,\"vibration\":true,\"followCoachPlan\":true}",
                                    null));
                    m.withdrawConsent(ConsentItem.LOCATION, T.plusDays(1));
                });
        tx.executeWithoutResult(
                s -> {
                    Member m = repository.findById(a).orElseThrow();
                    assertThat(m.getSettings().notificationPrefs()).containsEntry("KICKED", false);
                    assertThat(m.getSettings().runSettings().followCoachPlan()).isTrue();
                    assertThat(m.hasConsent(ConsentItem.LOCATION)).isFalse();
                    assertThat(m.hasConsent(ConsentItem.LLM_TRANSFER)).isTrue();
                    m.withdraw(T.plusDays(2));
                });

        assertThat(repository.findWithdrawnBefore(T.plusDays(3)))
                .singleElement()
                .satisfies(m -> assertThat(m.getStatus()).isEqualTo(MemberStatus.WITHDRAWN));
        assertThat(repository.summaries(List.of(a)).get(a).displayName())
                .isEqualTo(DisplayName.WITHDRAWN);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from member_consent where member_id = ?",
                                Integer.class,
                                a))
                .isZero();
        // 탈퇴 뒤 같은 소셜 계정·닉네임으로 다시 가입할 수 있다(식별정보 삭제)
        save("곰A");
    }

    @Test
    void duplicateNickname_isRejectedByUniqueIndex() {
        save("곰A");
        assertThatThrownBy(
                        () ->
                                tx.executeWithoutResult(
                                        s ->
                                                repository.save(
                                                        Member.signUp(
                                                                new SocialAccount(
                                                                        ProviderType.GOOGLE, "g"),
                                                                new SignUpForm(
                                                                        "곰A",
                                                                        null,
                                                                        null,
                                                                        null,
                                                                        null,
                                                                        Map.of(),
                                                                        RecordVisibility.PUBLIC,
                                                                        Set.of()),
                                                                Nickname.of("곰A"),
                                                                T))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
