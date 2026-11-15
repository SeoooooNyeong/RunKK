package com.a207.runkk.infra.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.TestcontainersConfig;
import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Tier;
import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.GuestCredential;
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
@org.junit.jupiter.api.DisplayName("회원 저장소(PostgreSQL)")
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
                    SignUpForm form = new SignUpForm(nickname, consents());
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

    private static Map<ConsentItem, Boolean> consents() {
        return Map.of(
                ConsentItem.AGE_14_PLUS, true,
                ConsentItem.TERMS, true,
                ConsentItem.PRIVACY, true,
                ConsentItem.LOCATION, true,
                ConsentItem.LLM_TRANSFER, true);
    }

    private long saveGuest(String nickname, LocalDateTime lastAccess) {
        return tx.execute(
                s -> {
                    Member g =
                            repository.save(
                                    Member.guest(Nickname.of(nickname), consents(), lastAccess));
                    g.attachGuestCredential(GuestCredential.hashOf("secret-" + nickname));
                    return g.getId();
                });
    }

    @Test
    @org.junit.jupiter.api.DisplayName("가입과 같은 시계 눈금 안에서 동의를 바꿔도 거부되지 않는다(DB는 마이크로초로 반올림해 저장)")
    void consentInSameClockTickAsSignUp_isAccepted() {
        // Windows 시계는 약 1~15.6ms마다 바뀌고 100ns 자리까지 준다. 가입과 다음 요청이 한 눈금 안이면 now가 같은데,
        // DB(timestamp(6))가 생성 시각을 마이크로초로 반올림해 올리면 읽은 생성 시각이 now보다 늦어진다.
        LocalDateTime tick = LocalDateTime.of(2026, 11, 15, 0, 35, 47, 123_456_700);
        long id =
                tx.execute(
                        s ->
                                repository
                                        .save(
                                                Member.signUp(
                                                        new SocialAccount(
                                                                ProviderType.KAKAO, "k-눈금곰"),
                                                        new SignUpForm("눈금곰", consents()),
                                                        Nickname.of("눈금곰"),
                                                        tick))
                                        .getId());
        String count = "select count(*) from member_consent where member_id = ?";
        int before = jdbc.queryForObject(count, Integer.class, id);

        tx.executeWithoutResult(
                s ->
                        repository
                                .findById(id)
                                .orElseThrow()
                                .giveConsent(ConsentItem.LLM_TRANSFER, tick));

        assertThat(jdbc.queryForObject(count, Integer.class, id)).isEqualTo(before + 1);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("V20: 게스트 저장·해시·미이용 조회(정각 포함)·연동·탈퇴가 제약을 지킨다")
    void guestLifecycle() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 22, 3, 0);
        long idle = saveGuest("러너0001", now.minusDays(365));
        long fresh = saveGuest("러너0002", now.minusDays(365).plusSeconds(1));
        save("연동곰");

        assertThat(repository.findIdleGuests(now.minusDays(365)))
                .extracting(Member::getId)
                .containsExactly(idle);
        assertThat(
                        jdbc.queryForObject(
                                "select guest_credential_hash from member where id = ?",
                                String.class,
                                idle))
                .isEqualTo(GuestCredential.hashOf("secret-러너0001"));

        tx.executeWithoutResult(
                s -> {
                    Member m = repository.findById(fresh).orElseThrow();
                    m.linkSocialAccount(new SocialAccount(ProviderType.GOOGLE, "g-linked"));
                    m.changeSetting(new SettingChange(SettingItem.THEME, "DARK", null));
                });
        tx.executeWithoutResult(s -> repository.findById(idle).orElseThrow().withdraw(now));

        Map<String, Object> linked =
                jdbc.queryForMap(
                        "select account_type, guest_credential_hash, provider, screen_theme"
                                + " from member where id = ?",
                        fresh);
        assertThat(linked.get("account_type")).isEqualTo("LINKED");
        assertThat(linked.get("guest_credential_hash")).isNull();
        assertThat(linked.get("provider")).isEqualTo("GOOGLE");
        assertThat(linked.get("screen_theme")).isEqualTo("DARK");
        assertThat(repository.findIdleGuests(now.plusYears(5))).isEmpty();
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
        assertThat(summaries.get(a).tier()).isEqualTo(Tier.UNRANKED);

        Integer consents =
                jdbc.queryForObject(
                        "select count(*) from member_consent where member_id = ?",
                        Integer.class,
                        a);
        assertThat(consents).isEqualTo(6);
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
                                                                new SignUpForm("곰A", Map.of()),
                                                                Nickname.of("곰A"),
                                                                T))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
