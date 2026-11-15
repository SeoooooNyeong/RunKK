package com.a207.runkk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import com.a207.runkk.avatar.AvatarQuery;
import com.a207.runkk.avatar.RunnerService;
import com.a207.runkk.battle.ActiveBattleView;
import com.a207.runkk.battle.Battle;
import com.a207.runkk.battle.BattleHistoryService;
import com.a207.runkk.battle.BattleMessage;
import com.a207.runkk.battle.BattleMessenger;
import com.a207.runkk.battle.BattleRepository;
import com.a207.runkk.battle.BattleResultService;
import com.a207.runkk.battle.BattleType;
import com.a207.runkk.battle.MatchingService;
import com.a207.runkk.battle.Matchmaker;
import com.a207.runkk.battle.RecordBattleService;
import com.a207.runkk.battle.RecordBattleStart;
import com.a207.runkk.battle.live.MatchFound;
import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.AvatarLook;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberRepository;
import com.a207.runkk.running.AvatarLookSource;
import com.a207.runkk.running.RunFixtures;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunUploadCommand;
import com.a207.runkk.running.RunUploadService;
import com.a207.runkk.running.UploadResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 아바타 모습 읽기가 실패해도 업무가 실패하지 않는다(S15P31A207-306 BE-2, PM 결정: 못 읽으면 기본 모습 = null). 실 PostgreSQL 위에서 모습
 * 읽기 도중 SQL 오류를 일으킨다. PostgreSQL은 한 문장이 실패하면 그 트랜잭션 전체가 못 쓰게 되므로, 모습 읽기가 부르는 쪽 트랜잭션에 참여하면 예외를 삼켜도
 * 업무가 실패한다(rollback-only). 읽기가 따로 떨어진 트랜잭션에서 도는지를 여기서 확인한다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@DisplayName("모습 읽기 실패에도 대결·대기실·기록 저장이 진행된다(통합)")
class AvatarLookFailureIntegrationTest {

    @Autowired MatchingService matching;
    @Autowired Matchmaker matchmaker;
    @Autowired BattleHistoryService history;
    @Autowired BattleResultService results;
    @Autowired RecordBattleService recordBattles;
    @Autowired BattleRepository battles;
    @Autowired AvatarQuery avatarQuery;
    @Autowired AvatarLookSource lookSource;
    @Autowired RunUploadService uploads;
    @Autowired MemberRepository members;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @MockitoSpyBean RunnerService runner;
    @MockitoSpyBean RunQueryService runQueries;
    @MockitoSpyBean BattleMessenger messenger;
    @MockitoSpyBean MemberQueryService memberQueries;

    private final AtomicBoolean ownGenderDown = new AtomicBoolean();

    /** 켜면 지금 모습 읽기(AvatarLookReader) 도중 SQL 오류가 난다. */
    private final AtomicBoolean currentLookDown = new AtomicBoolean();

    /** 켜면 기록의 그날 모습 읽기(RunQueryService.lookOf) 도중 SQL 오류가 난다. */
    private final AtomicBoolean recordLookDown = new AtomicBoolean();

    @BeforeEach
    void clean() throws Throwable {
        currentLookDown.set(false);
        recordLookDown.set(false);
        ownGenderDown.set(false);
        doAnswer(
                        inv -> {
                            if (ownGenderDown.get()) {
                                failingSql();
                            }
                            return inv.callRealMethod();
                        })
                .when(memberQueries)
                .ownGenderOf(anyLong());
        doAnswer(
                        inv -> {
                            if (currentLookDown.get()) {
                                failingSql();
                            }
                            return inv.callRealMethod();
                        })
                .when(runner)
                .runner(anyLong());
        doAnswer(
                        inv -> {
                            if (recordLookDown.get()) {
                                failingSql();
                            }
                            return inv.callRealMethod();
                        })
                .when(runQueries)
                .lookOf(anyString());
        for (String table :
                List.of(
                        "battle_disconnection",
                        "battle_participant",
                        "battle",
                        "celebrity_record",
                        "course_verdict",
                        "runimal_profile",
                        "notification",
                        "personal_best",
                        "personal_record_book",
                        "run_record",
                        "member_consent",
                        "member",
                        "audit_record")) {
            jdbc.update("delete from " + table);
        }
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushAll();
    }

    /** 지금 트랜잭션의 연결로 실패하는 문장을 보낸다(0으로 나누기). 참여 중인 트랜잭션이면 PostgreSQL이 그 트랜잭션을 못 쓰게 만든다. */
    private void failingSql() {
        jdbc.queryForObject("select 1/0", Integer.class);
    }

    /** 유효 러닝 3회(5km 25분)를 뛰고 골드가 확정된 회원. */
    private long ranked(String nickname) {
        long id =
                tx.execute(
                        s ->
                                members.save(
                                                MemberFixtures.member(
                                                        nickname,
                                                        LocalDateTime.now().minusDays(30)))
                                        .getId());
        LocalDateTime start = LocalDateTime.now().minusDays(3).withNano(0);
        for (int i = 0; i < 3; i++) {
            uploads.upload(id, run(start.plusHours(i)));
        }
        tx.executeWithoutResult(
                s -> {
                    var member = members.findById(id).orElseThrow();
                    member.confirmPlacement(Tier.GOLD);
                    members.save(member);
                });
        return id;
    }

    private static RunUploadCommand run(LocalDateTime start) {
        return new RunUploadCommand(
                RunFixtures.upload(UUID.randomUUID().toString(), start, 5_000, 1_500));
    }

    private Battle battleOf(long memberId) {
        Long id =
                jdbc.queryForObject(
                        "select max(b.id) from battle b join battle_participant p on p.battle_id ="
                                + " b.id where p.member_id = ?",
                        Long.class,
                        memberId);
        assertThat(id).as("대결이 저장됐다").isNotNull();
        return tx.execute(s -> battles.findById(id).orElseThrow());
    }

    private MatchFound matchFoundTo(long memberId) {
        ArgumentCaptor<BattleMessage> sent = ArgumentCaptor.forClass(BattleMessage.class);
        verify(messenger, atLeastOnce()).send(eq(memberId), sent.capture());
        return sent.getAllValues().stream()
                .filter(MatchFound.class::isInstance)
                .map(MatchFound.class::cast)
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("모습 읽기가 실패하지 않으면 랜덤 대결 성사 알림에 상대 모습이 실린다(대조)")
    void lookIsSentWhenReadWorks() {
        long alice = ranked("모습곰");
        long bob = ranked("모습여우");
        matching.requestMatch(alice, StandardDistance.KM5);
        matching.requestMatch(bob, StandardDistance.KM5);
        matchmaker.tick(LocalDateTime.now());

        assertThat(matchFoundTo(alice).opponentLook()).isNotNull();
    }

    @Test
    @DisplayName("모습 읽기가 실패해도 매칭이 성사돼 알림이 나가고 opponentLook은 null이다. 대결 조회와 결과도 정상 응답한다")
    void matchSucceedsWithoutLook() {
        long alice = ranked("실패곰");
        long bob = ranked("실패여우");
        currentLookDown.set(true);
        matching.requestMatch(alice, StandardDistance.KM5);
        matching.requestMatch(bob, StandardDistance.KM5);
        matchmaker.tick(LocalDateTime.now());

        Battle battle = battleOf(alice);
        assertThat(battle.getType()).isEqualTo(BattleType.RANDOM);
        assertThat(battle.getStartAt()).as("출발 예약이 커밋됐다").isPresent();
        MatchFound toAlice = matchFoundTo(alice);
        assertThat(toAlice.opponentLook()).isNull();
        assertThat(toAlice.opponentGender()).isNotNull();
        assertThat(matchFoundTo(bob).opponentLook()).isNull();

        ActiveBattleView active = history.active(alice).orElseThrow();
        assertThat(active.battleId()).isEqualTo(battle.getId());
        assertThat(active.opponentLook()).isNull();
        assertThat(results.result(bob, battle.getId()).opponentLook()).isNull();
    }

    @Test
    @DisplayName("그날 모습도 지금 모습도 읽지 못해도 기록 대결(나와의 싸움)이 시작되고 저장된다")
    void selfBattleStartsWithoutLook() {
        long alice = ranked("셀프곰");
        currentLookDown.set(true);
        recordLookDown.set(true);

        RecordBattleStart start = recordBattles.startSelfBattle(alice, StandardDistance.KM5);

        assertThat(start.opponentLook()).isNull();
        assertThat(battleOf(alice).getId()).isEqualTo(start.battleId());
        assertThat(recordBattles.selfCandidates(alice).candidates())
                .as("후보 조회도 정상 응답한다")
                .isNotEmpty()
                .allSatisfy(c -> assertThat(c.look()).isNull());
    }

    @Test
    @DisplayName("본인 성별 SQL만 실패해도 후보와 진행 조회는 저장된 모습을 유지하고 바깥 쓰기를 커밋한다")
    void ownGenderFailureLeavesSelfBattleAndCallerUsable() {
        long alice = ranked("성별곰");
        recordBattles.startSelfBattle(alice, StandardDistance.KM5);
        String recordId = battleOf(alice).getOpponent().orElseThrow().recordId();
        AvatarLook stored = runQueries.lookOf(recordId).orElseThrow();
        assertThat(stored).isNotNull();
        ownGenderDown.set(true);

        tx.executeWithoutResult(
                status -> {
                    assertThat(recordBattles.selfCandidates(alice).candidates())
                            .filteredOn(c -> c.look() != null)
                            .isNotEmpty()
                            .allSatisfy(c -> assertThat(c.look()).isEqualTo(stored));
                    assertThat(history.active(alice).orElseThrow().opponentLook())
                            .isEqualTo(stored);
                    assertThat(status.isRollbackOnly()).isFalse();
                    jdbc.update("update member set intro = ? where id = ?", "격리 성공", alice);
                });

        assertThat(
                        jdbc.queryForObject(
                                "select intro from member where id = ?", String.class, alice))
                .isEqualTo("격리 성공");
    }

    @Test
    @DisplayName("대기실 모습 읽기(AvatarQuery.looksOf)가 실패하면 그 참가자만 빠지고, 부른 쪽 트랜잭션은 rollback-only가 아니다")
    void looksOfLeavesCallerTransactionUsable() {
        long alice = ranked("대기곰");
        long bob = ranked("대기여우");
        long carol =
                tx.execute(
                        s ->
                                members.save(
                                                MemberFixtures.member(
                                                        "대기토끼", LocalDateTime.now().minusDays(30)))
                                        .getId());
        currentLookDown.set(true);

        assertThat(avatarQuery.looksOf(List.of(alice, bob))).isEmpty();
        Long count =
                tx.execute(
                        status -> {
                            Map<Long, AvatarLook> looks = avatarQuery.looksOf(List.of(alice, bob));
                            Optional<AvatarLook> one = lookSource.currentLookOf(alice);
                            assertThat(looks).isEmpty();
                            assertThat(one).isEmpty();
                            assertThat(status.isRollbackOnly()).isFalse();
                            // 부른 쪽 트랜잭션의 연결이 살아 있다(PostgreSQL "current transaction is aborted"가
                            // 아니다)
                            var member = members.findById(carol).orElseThrow();
                            member.confirmPlacement(Tier.SILVER);
                            members.save(member);
                            return jdbc.queryForObject("select count(*) from member", Long.class);
                        });

        assertThat(count).isEqualTo(3);
        assertThat(members.findById(carol).orElseThrow().getTier())
                .as("부른 쪽 트랜잭션이 커밋됐다")
                .isEqualTo(Tier.SILVER);
        currentLookDown.set(false);
        assertThat(avatarQuery.looksOf(List.of(alice, bob))).containsOnlyKeys(alice, bob);
    }

    @Test
    @DisplayName("모습 읽기가 실패해도 러닝 기록은 저장된다(모습만 비어 있다)")
    void runIsStoredWithoutLook() {
        long alice = ranked("기록곰");
        currentLookDown.set(true);
        RunUploadCommand command = run(LocalDateTime.now().minusDays(1).withNano(0));

        UploadResult result = uploads.upload(alice, command);

        currentLookDown.set(false);
        assertThat(result.recordId()).isNotNull();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from run_record where record_id = ?",
                                Integer.class,
                                result.recordId()))
                .isEqualTo(1);
        assertThat(runQueries.lookOf(result.recordId())).isEmpty();
    }
}
