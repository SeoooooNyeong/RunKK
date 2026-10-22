package com.a207.runkk.infra.battle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.TestcontainersConfig;
import com.a207.runkk.battle.BattleStartService;
import com.a207.runkk.battle.CelebrityInput;
import com.a207.runkk.battle.CelebrityRecordService;
import com.a207.runkk.battle.RecordBattleService;
import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 활성 대결 1개 불변식(V15, PostgreSQL): battle_participant.active_member_id 고유 인덱스가 같은 회원의 동시 기록 대결 생성에도
 * 활성 대결을 하나로 지키고, 버려진 기록 대결 정리가 CANCELLED로 칸을 비워 다음 대결을 막지 않는다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@DisplayName("활성 대결 1개 불변식(PostgreSQL)")
class ActiveBattleSlotIntegrationTest {

    private static final int RACERS = 6;

    @Autowired RecordBattleService records;
    @Autowired CelebrityRecordService celebrities;
    @Autowired BattleStartService starts;
    @Autowired MemberRepository members;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        for (String table :
                List.of(
                        "battle_disconnection",
                        "battle_participant",
                        "battle",
                        "celebrity_record",
                        "member_consent",
                        "member",
                        "audit_record")) {
            jdbc.update("delete from " + table);
        }
    }

    private long runner() {
        return tx.execute(
                s ->
                        members.save(
                                        MemberFixtures.member(
                                                "슬롯러너", LocalDateTime.now().minusDays(30)))
                                .getId());
    }

    private long celebrity() {
        return celebrities
                .register(
                        "KAKAO:admin",
                        new CelebrityInput(
                                "전설",
                                StandardDistance.KM5.distance(),
                                Duration.ofMinutes(15),
                                List.of(),
                                "대회 공식 기록",
                                null))
                .celebrityId();
    }

    private int activeSlots(long memberId) {
        return jdbc.queryForObject(
                "select count(*) from battle_participant where active_member_id = ?",
                Integer.class,
                memberId);
    }

    @Test
    @DisplayName("같은 회원이 기록 대결을 동시에 6번 만들어도 활성 대결은 하나이고 나머지는 ALREADY_IN_BATTLE 또는 대신됨(CANCELLED)")
    void concurrentCreates_leaveOneActiveBattle() throws Exception {
        long member = runner();
        long celebrityId = celebrity();
        ExecutorService pool = Executors.newFixedThreadPool(RACERS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<ReasonCode>> results = new ArrayList<>();
        for (int i = 0; i < RACERS; i++) {
            results.add(
                    pool.submit(
                            () -> {
                                go.await();
                                try {
                                    records.startCelebrityBattle(member, celebrityId);
                                    return null;
                                } catch (BusinessRuleException e) {
                                    return e.reason();
                                }
                            }));
        }
        go.countDown();
        pool.shutdown();
        for (Future<ReasonCode> f : results) {
            ReasonCode reason = f.get();
            assertThat(reason).isIn(null, ReasonCode.ALREADY_IN_BATTLE);
        }
        assertThat(activeSlots(member)).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from battle where status in"
                                        + " ('WAITING_START', 'IN_PROGRESS', 'COUNTDOWN')",
                                Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("고유 인덱스는 같은 회원의 두 번째 활성 칸을 거부하고 비어 있는(NULL) 칸은 여럿 허용한다")
    void uniqueIndex_rejectsSecondActiveSlot() {
        long member = runner();
        records.startCelebrityBattle(member, celebrity());
        Long battleId = jdbc.queryForObject("select id from battle", Long.class);
        jdbc.update(
                "insert into battle_participant (battle_id, seq, member_id, overtakes, forfeited,"
                        + " new_record) values (?, 1, 999, 0, false, false)",
                battleId);
        jdbc.update(
                "insert into battle_participant (battle_id, seq, member_id, overtakes, forfeited,"
                        + " new_record) values (?, 2, 998, 0, false, false)",
                battleId);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "update battle_participant set active_member_id = ?"
                                                + " where member_id = 999",
                                        member))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("시작 보고 없이 10분 지난 기록 대결은 정리 배치가 CANCELLED로 끝내 칸을 비우고, 그 뒤 새 대결을 만들 수 있다")
    void abandonedBattle_isCancelled_andFreesTheSlot() {
        long member = runner();
        long celebrityId = celebrity();
        long first = records.startCelebrityBattle(member, celebrityId).battleId();

        LocalDateTime later = LocalDateTime.now().plusMinutes(10).plusSeconds(1);
        assertThat(starts.cancelAbandoned(later)).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select status from battle where id = ?", String.class, first))
                .isEqualTo("CANCELLED");
        assertThat(activeSlots(member)).isZero();

        long second = records.startCelebrityBattle(member, celebrityId).battleId();
        records.markStarted(member, second);
        assertThat(activeSlots(member)).isEqualTo(1);
        assertThatThrownBy(() -> records.startCelebrityBattle(member, celebrityId))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.ALREADY_IN_BATTLE);
        assertThatThrownBy(() -> records.markStarted(member, first))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.BATTLE_NOT_FOUND);
    }
}
