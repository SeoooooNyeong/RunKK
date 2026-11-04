package com.a207.runkk.battle;

import static com.a207.runkk.battle.FriendInviteTest.CREATED;
import static com.a207.runkk.battle.FriendInviteTest.INVITER;
import static com.a207.runkk.battle.FriendInviteTest.JOINER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.battle.live.FriendInviteDeclined;
import com.a207.runkk.battle.live.MatchFound;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.Gender;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberSettingService;
import com.a207.runkk.member.MemberSummary;
import com.a207.runkk.running.BestEntry;
import com.a207.runkk.running.RunQueryService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * FriendInviteService·RunnerAvailability·FriendInviteQuery(설계 5.6.2, SQ-31·SQ-32, D-220~D-224,
 * test.md 12.2.2 TC-9235~TC-9266, 기초 경로 TC-9501~TC-9506, 결정표 TC-9551~TC-9559). 인메모리 저장소·가짜 메신저·조절
 * 시계.
 */
@DisplayName("친구 대결 초대 서비스")
class FriendInviteServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /** 시험이 옮기는 시계. */
    static final class MovingClock extends Clock {
        private Instant now;

        MovingClock(LocalDateTime at) {
            set(at);
        }

        void set(LocalDateTime at) {
            now = at.atZone(SEOUL).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return SEOUL;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final InMemoryBattleStores.Battles battles = new InMemoryBattleStores.Battles();
    private final InMemoryBattleStores.Queue queue = new InMemoryBattleStores.Queue();
    private final InMemoryBattleStores.Invites invites = new InMemoryBattleStores.Invites();
    private final InMemoryBattleStores.Sessions sessions = new InMemoryBattleStores.Sessions();
    private final InMemoryBattleStores.Messenger messenger = new InMemoryBattleStores.Messenger();
    private final MemberQueryService members = mock(MemberQueryService.class);
    private final MemberSettingService settings = mock(MemberSettingService.class);
    private final RunQueryService runs = mock(RunQueryService.class);
    private final BattleTransactions tx = InMemoryBattleStores.transactions();
    private final MovingClock clock = new MovingClock(CREATED);
    private final Map<Long, Tier> tiers = new HashMap<>();

    private RunnerAvailability availability;
    private FriendInviteService service;
    private FriendInviteQuery query;
    private BattleStartService startService;

    @BeforeEach
    void wire() {
        when(members.hasConsent(anyLong(), eq(ConsentItem.LOCATION))).thenReturn(true);
        when(members.summaries(anyCollection()))
                .thenAnswer(
                        inv -> {
                            Map<Long, MemberSummary> map = new HashMap<>();
                            for (Object id : (java.util.Collection<?>) inv.getArgument(0)) {
                                long memberId = (Long) id;
                                map.put(
                                        memberId,
                                        BattleFixtures.summary(
                                                memberId,
                                                tiers.getOrDefault(memberId, Tier.UNRANKED)));
                            }
                            return map;
                        });
        availability = new RunnerAvailability(queue, battles, invites);
        startService =
                new BattleStartService(battles, members, runs, messenger, sessions, clock, tx);
        service =
                new FriendInviteService(
                        invites,
                        availability,
                        members,
                        settings,
                        startService,
                        messenger,
                        tx,
                        clock);
        query = new FriendInviteQuery(invites, availability, members, runs, clock);
    }

    private void now(int minutes, int seconds) {
        clock.set(CREATED.plusMinutes(minutes).plusSeconds(seconds));
    }

    private static void assertReason(Runnable call, ReasonCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(code);
    }

    private FriendInviteIssue invite() {
        return service.invite(INVITER, StandardDistance.KM5);
    }

    private void waiting(long memberId) {
        queue.enqueue(MatchTicket.open(memberId, Tier.GOLD, StandardDistance.KM5, CREATED));
    }

    private void inBattle(long memberId) {
        battles.save(
                Battle.againstRecord(
                        new RecordBattleSpec(
                                BattleType.SELF,
                                memberId,
                                StandardDistance.KM5.distance(),
                                BattleFixtures.spec(BattleType.SELF, Duration.ofMinutes(20))
                                        .opponent(),
                                BattleFixtures.spec(BattleType.SELF, Duration.ofMinutes(20))
                                        .track(),
                                ComparisonValues.undisclosed()),
                        CREATED));
    }

    @Test
    @DisplayName("TC-9235 언랭 회원도 초대를 만들고 거리를 기본 대결 거리로 기억한다(티어를 보지 않는다)")
    void tc9235_invite() {
        FriendInviteIssue issue = invite();
        assertThat(issue.distance()).isEqualTo(StandardDistance.KM5);
        assertThat(issue.expiresAt()).isEqualTo(CREATED.plusMinutes(5));
        assertThat(issue.token()).hasSize(22);
        assertThat(invites.all()).hasSize(1);
        assertThat(invites.all().get(0).getStatus()).isEqualTo(FriendInviteStatus.PENDING);
        verify(settings).rememberBattleDistance(INVITER, StandardDistance.KM5);
        verify(members, never()).summaries(anyCollection());
    }

    @ParameterizedTest(name = "TC-9236 {0}")
    @CsvSource({"HALF", "FULL"})
    @DisplayName("TC-9236 거리 오류는 BATTLE_DISTANCE_INVALID, 저장 0")
    void tc9236_invalidDistance(StandardDistance d) {
        assertReason(() -> service.invite(INVITER, d), ReasonCode.BATTLE_DISTANCE_INVALID);
        assertReason(() -> service.invite(INVITER, null), ReasonCode.BATTLE_DISTANCE_INVALID);
        assertThat(invites.all()).isEmpty();
        verify(settings, never()).rememberBattleDistance(anyLong(), eq(d));
    }

    @Test
    @DisplayName("TC-9237 위치 동의 없으면 LOCATION_CONSENT_REQUIRED")
    void tc9237_consent() {
        when(members.hasConsent(INVITER, ConsentItem.LOCATION)).thenReturn(false);
        assertReason(this::invite, ReasonCode.LOCATION_CONSENT_REQUIRED);
        assertThat(invites.all()).isEmpty();
    }

    @Test
    @DisplayName(
            "TC-9238·TC-9239 기한 남은 대기 초대는 INVITE_ALREADY_PENDING, 기한 지난 것은 EXPIRED로 바꾸고 새로 만든다")
    void tc9238_pendingInvite() {
        invite();
        now(4, 59);
        assertReason(this::invite, ReasonCode.INVITE_ALREADY_PENDING);
        assertThat(invites.all()).hasSize(1);

        now(5, 0);
        FriendInviteIssue second = invite();
        assertThat(invites.all()).hasSize(2);
        assertThat(invites.all().get(0).getStatus()).isEqualTo(FriendInviteStatus.EXPIRED);
        assertThat(invites.findById(second.inviteId()).orElseThrow().getStatus())
                .isEqualTo(FriendInviteStatus.PENDING);
        assertThat(messenger.sent()).as("옛 초대 만료는 새 초대를 만드는 본인에게 알리지 않는다").isEmpty();
    }

    @Test
    @DisplayName("TC-9240·TC-9241 매칭 대기·활성 대결이면 ALREADY_IN_BATTLE, 대기열 장애는 QUEUE_UNAVAILABLE")
    void tc9240_busy() {
        waiting(INVITER);
        assertReason(this::invite, ReasonCode.ALREADY_IN_BATTLE);
        queue.remove(INVITER);
        inBattle(INVITER);
        assertReason(this::invite, ReasonCode.ALREADY_IN_BATTLE);
        assertThat(invites.all()).isEmpty();

        RunnerAvailability broken = mock(RunnerAvailability.class);
        when(broken.isBusy(anyLong(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new DataAccessResourceFailureException("redis down"));
        FriendInviteService down =
                new FriendInviteService(
                        invites, broken, members, settings, startService, messenger, tx, clock);
        assertReason(() -> down.invite(9L, StandardDistance.KM3), ReasonCode.QUEUE_UNAVAILABLE);
    }

    @Test
    @DisplayName("TC-9557 초대 검사 순서: 거리 → 위치 동의 → 대기 초대 → 바쁨")
    void tc9557_inviteOrder() {
        when(members.hasConsent(INVITER, ConsentItem.LOCATION)).thenReturn(false);
        assertReason(
                () -> service.invite(INVITER, StandardDistance.HALF),
                ReasonCode.BATTLE_DISTANCE_INVALID);
        assertReason(this::invite, ReasonCode.LOCATION_CONSENT_REQUIRED);
        when(members.hasConsent(INVITER, ConsentItem.LOCATION)).thenReturn(true);
        invite();
        waiting(INVITER);
        assertReason(this::invite, ReasonCode.INVITE_ALREADY_PENDING);
    }

    @Test
    @DisplayName(
            "TC-9245·TC-9506 정상 수락: FRIEND·WAITING_START·참가자 7·8, linkBattle, MatchFound"
                    + " 둘·BattleStart(+13초)")
    void tc9245_accept() {
        tiers.put(INVITER, Tier.GOLD);
        when(runs.best(INVITER, StandardDistance.KM5))
                .thenReturn(Optional.of(best("r7", Duration.ofSeconds(1812))));
        FriendInviteIssue issue = invite();
        now(1, 0);

        long battleId = service.accept(JOINER, issue.token());

        Battle battle = battles.findById(battleId).orElseThrow();
        assertThat(battle.getType()).isEqualTo(BattleType.FRIEND);
        assertThat(battle.getStatus()).isEqualTo(BattleStatus.WAITING_START);
        assertThat(battle.memberIds()).containsExactly(INVITER, JOINER);
        assertThat(battle.getDistance()).isEqualTo(StandardDistance.KM5.distance());
        assertThat(battle.getStartAt()).contains(CREATED.plusMinutes(1).plusSeconds(13));
        FriendInvite invite = invites.findById(issue.inviteId()).orElseThrow();
        assertThat(invite.getStatus()).isEqualTo(FriendInviteStatus.ACCEPTED);
        assertThat(invite.getBattleId()).contains(battleId);
        assertThat(messenger.to(INVITER, "MatchFound")).hasSize(1);
        assertThat(messenger.to(JOINER, "MatchFound")).hasSize(1);
        MatchFound toJoiner = (MatchFound) messenger.to(JOINER, "MatchFound").get(0);
        assertThat(toJoiner.opponentTier()).isEqualTo(Tier.GOLD);
        assertThat(messenger.to(INVITER, "BattleStart")).hasSize(1);
        assertThat(messenger.to(JOINER, "BattleStart")).hasSize(1);
    }

    @Test
    @DisplayName("친구 대결 수락: 서로 상대(친구) 회원의 성별을 MatchFound에 싣는다(2026-10-31 결정)")
    void accept_sendsFriendGender() {
        when(members.shownGendersOf(anyCollection()))
                .thenReturn(Map.of(INVITER, Gender.FEMALE, JOINER, Gender.MALE));
        FriendInviteIssue issue = invite();
        now(1, 0);

        long battleId = service.accept(JOINER, issue.token());

        MatchFound toJoiner = (MatchFound) messenger.to(JOINER, "MatchFound").get(0);
        MatchFound toInviter = (MatchFound) messenger.to(INVITER, "MatchFound").get(0);
        assertThat(toJoiner.opponentGender()).isEqualTo("FEMALE");
        assertThat(toInviter.opponentGender()).isEqualTo("MALE");
        Battle battle = battles.findById(battleId).orElseThrow();
        assertThat(battle.opponentGenderFor(JOINER)).contains(Gender.FEMALE);
        assertThat(battle.opponentGenderFor(INVITER)).contains(Gender.MALE);
    }

    private static BestEntry best(String recordId, Duration time) {
        return new BestEntry(recordId, time.toMillis(), null, LocalDate.of(2026, 10, 1), false);
    }

    @Test
    @DisplayName("TC-9231 같은 초대를 두 번째로 수락하면 INVITE_ALREADY_JOINED, 대결은 하나")
    void tc9231_secondAcceptFails() {
        FriendInviteIssue issue = invite();
        service.accept(JOINER, issue.token());
        assertReason(() -> service.accept(9L, issue.token()), ReasonCode.INVITE_ALREADY_JOINED);
        assertThat(battles.all()).hasSize(1);
        assertThat(messenger.to(9L, "MatchFound")).isEmpty();
    }

    @Test
    @DisplayName("TC-9242·TC-9503 받는 쪽 위치 동의 없음 → LOCATION_CONSENT_REQUIRED, 초대 PENDING, 대결 0")
    void tc9242_joinerConsent() {
        FriendInviteIssue issue = invite();
        when(members.hasConsent(JOINER, ConsentItem.LOCATION)).thenReturn(false);
        assertReason(
                () -> service.accept(JOINER, issue.token()), ReasonCode.LOCATION_CONSENT_REQUIRED);
        assertThat(invites.all().get(0).getStatus()).isEqualTo(FriendInviteStatus.PENDING);
        assertThat(battles.all()).isEmpty();
    }

    @Test
    @DisplayName("TC-9243·TC-9504 받는 쪽이 매칭 대기·활성 대결·열린 초대 보유 → ALREADY_IN_BATTLE")
    void tc9243_joinerBusy() {
        FriendInviteIssue issue = invite();
        waiting(JOINER);
        assertReason(() -> service.accept(JOINER, issue.token()), ReasonCode.ALREADY_IN_BATTLE);
        queue.remove(JOINER);
        inBattle(JOINER);
        assertReason(() -> service.accept(JOINER, issue.token()), ReasonCode.ALREADY_IN_BATTLE);
    }

    @Test
    @DisplayName("TC-9243 받는 쪽이 열린 초대를 가졌으면 ALREADY_IN_BATTLE, 초대 PENDING 그대로")
    void tc9243_joinerHasOpenInvite() {
        FriendInviteIssue issue = invite();
        service.invite(9L, StandardDistance.KM3);
        assertReason(() -> service.accept(9L, issue.token()), ReasonCode.ALREADY_IN_BATTLE);
        assertThat(invites.findById(issue.inviteId()).orElseThrow().getStatus())
                .isEqualTo(FriendInviteStatus.PENDING);
        assertThat(battles.all()).isEmpty();
    }

    @Test
    @DisplayName("TC-9244·TC-9505 초대한 쪽이 그사이 매칭 대기·대결 중이면 초대를 CANCELLED로 저장하고 INVITE_CANCELLED")
    void tc9244_inviterBusy() {
        FriendInviteIssue issue = invite();
        waiting(INVITER);
        assertReason(() -> service.accept(JOINER, issue.token()), ReasonCode.INVITE_CANCELLED);
        assertThat(invites.findById(issue.inviteId()).orElseThrow().getStatus())
                .isEqualTo(FriendInviteStatus.CANCELLED);
        assertThat(battles.all()).isEmpty();
    }

    @Test
    @DisplayName("TC-9246·TC-9501·TC-9551 없는 토큰 → INVITE_NOT_FOUND")
    void tc9246_unknownToken() {
        assertReason(() -> service.accept(JOINER, "nope"), ReasonCode.INVITE_NOT_FOUND);
        assertReason(() -> service.decline(JOINER, "nope"), ReasonCode.INVITE_NOT_FOUND);
        assertReason(() -> query.view(JOINER, "nope"), ReasonCode.INVITE_NOT_FOUND);
    }

    @Test
    @DisplayName("TC-9502·TC-9552 만료 정각 수락 → INVITE_EXPIRED(정리 작업 전에도)")
    void tc9502_acceptAtExpiry() {
        FriendInviteIssue issue = invite();
        now(5, 0);
        assertReason(() -> service.accept(JOINER, issue.token()), ReasonCode.INVITE_EXPIRED);
        assertThat(invites.all().get(0).getStatus()).isEqualTo(FriendInviteStatus.PENDING);
    }

    @Test
    @DisplayName(
            "TC-9555 결정표 조합: 본인+만료 INVITE_OWN, 만료+바쁨 INVITE_EXPIRED, 동의 없음+바쁨"
                    + " LOCATION_CONSENT_REQUIRED")
    void tc9555_priority() {
        FriendInviteIssue issue = invite();
        now(6, 0);
        assertReason(() -> service.accept(INVITER, issue.token()), ReasonCode.INVITE_OWN);
        waiting(JOINER);
        assertReason(() -> service.accept(JOINER, issue.token()), ReasonCode.INVITE_EXPIRED);
        now(1, 0);
        when(members.hasConsent(JOINER, ConsentItem.LOCATION)).thenReturn(false);
        assertReason(
                () -> service.accept(JOINER, issue.token()), ReasonCode.LOCATION_CONSENT_REQUIRED);
    }

    @Test
    @DisplayName("TC-9247 거절: DECLINED, 초대한 쪽에 FriendInviteDeclined 1회")
    void tc9247_decline() {
        FriendInviteIssue issue = invite();
        service.decline(JOINER, issue.token());
        assertThat(invites.all().get(0).getStatus()).isEqualTo(FriendInviteStatus.DECLINED);
        assertThat(messenger.to(INVITER, "FriendInviteDeclined"))
                .containsExactly(new FriendInviteDeclined(issue.inviteId()));
    }

    @Test
    @DisplayName("TC-9248·TC-9559 거절 거부: 본인 INVITE_OWN, ACCEPTED INVITE_ALREADY_JOINED, 알림 0")
    void tc9248_declineRefused() {
        FriendInviteIssue issue = invite();
        assertReason(() -> service.decline(INVITER, issue.token()), ReasonCode.INVITE_OWN);
        service.accept(JOINER, issue.token());
        assertReason(() -> service.decline(9L, issue.token()), ReasonCode.INVITE_ALREADY_JOINED);
        assertThat(messenger.to(INVITER, "FriendInviteDeclined")).isEmpty();
    }

    @Test
    @DisplayName(
            "TC-9249·TC-9250·TC-9558 취소: 남의 초대 403, 두 번 취소 멱등, 없는 ID INVITE_NOT_FOUND, ACCEPTED"
                    + " INVITE_ALREADY_JOINED")
    void tc9249_cancel() {
        FriendInviteIssue issue = invite();
        assertThatThrownBy(() -> service.cancel(9L, issue.inviteId()))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(invites.all().get(0).getStatus()).isEqualTo(FriendInviteStatus.PENDING);

        service.cancel(INVITER, issue.inviteId());
        service.cancel(INVITER, issue.inviteId());
        assertThat(invites.all().get(0).getStatus()).isEqualTo(FriendInviteStatus.CANCELLED);
        assertReason(() -> service.cancel(INVITER, 999L), ReasonCode.INVITE_NOT_FOUND);

        FriendInviteIssue second = invite();
        service.accept(JOINER, second.token());
        assertReason(
                () -> service.cancel(INVITER, second.inviteId()), ReasonCode.INVITE_ALREADY_JOINED);
    }

    @Test
    @DisplayName("TC-9251 만료 정리: 기한 지난 둘은 EXPIRED·알림 각 1회, 기한 남은 하나는 PENDING")
    void tc9251_expireDue() {
        service.invite(1L, StandardDistance.KM1);
        now(1, 0);
        service.invite(2L, StandardDistance.KM3);
        now(4, 30);
        service.invite(3L, StandardDistance.KM5);

        int expired = service.expireDue(CREATED.plusMinutes(6));

        assertThat(expired).isEqualTo(2);
        assertThat(invites.findPending(3L)).isPresent();
        assertThat(invites.findPending(1L)).isEmpty();
        assertThat(messenger.to(1L, "FriendInviteExpired")).hasSize(1);
        assertThat(messenger.to(2L, "FriendInviteExpired")).hasSize(1);
        assertThat(messenger.to(3L, "FriendInviteExpired")).isEmpty();
    }

    @Test
    @DisplayName("TC-9252 한 번에 100건까지 만료하고 나머지는 다음 호출이 처리한다")
    void tc9252_expireBatch() {
        for (long id = 100; id < 250; id++) {
            service.invite(id, StandardDistance.KM1);
        }
        LocalDateTime later = CREATED.plusMinutes(10);
        assertThat(service.expireDue(later)).isEqualTo(FriendInviteService.EXPIRE_BATCH);
        assertThat(service.expireDue(later)).isEqualTo(50);
        assertThat(service.expireDue(later)).isZero();
    }

    @Test
    @DisplayName("TC-9253 셋 중 둘째 저장이 실패해도 나머지는 만료하고 알림은 성공한 둘만")
    void tc9253_partialFailure() {
        FriendInviteIssue a = service.invite(1L, StandardDistance.KM1);
        FriendInviteIssue b = service.invite(2L, StandardDistance.KM1);
        FriendInviteIssue c = service.invite(3L, StandardDistance.KM1);
        invites.failNextSaveOf(b.inviteId());

        assertThat(service.expireDue(CREATED.plusMinutes(6))).isEqualTo(2);
        assertThat(invites.findById(a.inviteId()).orElseThrow().getStatus())
                .isEqualTo(FriendInviteStatus.EXPIRED);
        assertThat(invites.findById(c.inviteId()).orElseThrow().getStatus())
                .isEqualTo(FriendInviteStatus.EXPIRED);
        assertThat(messenger.to(2L, "FriendInviteExpired")).isEmpty();
        // 실패한 건의 트랜잭션은 롤백되어 다음 주기에 다시 고른다(인메모리 저장소는 롤백을 흉내 내지 않아 여기서는 보지 않는다)
    }

    @Test
    @DisplayName("TC-9254 탈퇴 정리: PENDING이면 CANCELLED, 없으면 아무 일도 없다")
    void tc9254_cancelOpenOf() {
        FriendInviteIssue issue = invite();
        service.cancelOpenOf(INVITER);
        assertThat(invites.findById(issue.inviteId()).orElseThrow().getStatus())
                .isEqualTo(FriendInviteStatus.CANCELLED);
        service.cancelOpenOf(42L);
    }

    @Test
    @DisplayName("TC-9255~TC-9261 RunnerAvailability: 대기 표·활성 대결·열린 초대, 기한 정각은 열린 초대가 아니다")
    void tc9255_availability() {
        assertThat(availability.isBusy(INVITER, CREATED)).isFalse();
        assertThat(availability.isInBattleOrQueue(INVITER)).isFalse();
        waiting(INVITER);
        assertThat(availability.isBusy(INVITER, CREATED)).isTrue();
        queue.remove(INVITER);
        inBattle(INVITER);
        assertThat(availability.isBusy(INVITER, CREATED)).isTrue();
        assertThat(availability.isInBattleOrQueue(INVITER)).isTrue();
    }

    @Test
    @DisplayName(
            "TC-9257·TC-9258·TC-9260 열린 초대: 4:59 바쁨, 5:00 아님, 초대한 쪽 확인(isInBattleOrQueue)은 자기 초대를"
                    + " 세지 않는다")
    void tc9257_openInviteBoundary() {
        invite();
        assertThat(availability.isBusy(INVITER, CREATED.plusMinutes(4).plusSeconds(59))).isTrue();
        assertThat(availability.isBusy(INVITER, CREATED.plusMinutes(5))).isFalse();
        assertThat(availability.isInBattleOrQueue(INVITER)).isFalse();
    }

    @Test
    @DisplayName("TC-9261 대기열 장애는 RunnerAvailability가 그대로 올린다")
    void tc9261_queueFailurePropagates() {
        MatchQueue broken = mock(MatchQueue.class);
        when(broken.findWaiting(anyLong())).thenThrow(new DataAccessResourceFailureException("x"));
        RunnerAvailability down = new RunnerAvailability(broken, battles, invites);
        assertThatThrownBy(() -> down.isBusy(INVITER, CREATED))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    @DisplayName("TC-9262 열린 친구 초대가 있으면 랜덤 매칭 요청은 ALREADY_IN_BATTLE, 대기열 등록 0")
    void tc9262_matchBlockedByInvite() {
        tiers.put(INVITER, Tier.GOLD);
        when(settings.battleSettingsOf(anyLong()))
                .thenReturn(com.a207.runkk.member.BattleSettings.defaults());
        invite();
        MatchingService matching =
                new MatchingService(
                        members,
                        settings,
                        queue,
                        availability,
                        mock(PlacementMatcher.class),
                        clock);
        assertReason(
                () -> matching.requestMatch(INVITER, StandardDistance.KM5),
                ReasonCode.ALREADY_IN_BATTLE);
        assertThat(queue.findWaiting(INVITER)).isEmpty();
    }

    @Test
    @DisplayName("TC-9263 링크 조회는 끝난 초대도 오류 없이 status·blockedReason을 싣는다")
    void tc9263_viewStates() {
        FriendInviteIssue open = invite();
        assertThat(query.view(JOINER, open.token()).blockedReason()).isNull();
        assertThat(query.view(INVITER, open.token()).blockedReason())
                .isEqualTo(ReasonCode.INVITE_OWN);
        waiting(JOINER);
        assertThat(query.view(JOINER, open.token()).blockedReason())
                .isEqualTo(ReasonCode.ALREADY_IN_BATTLE);
        queue.remove(JOINER);

        now(5, 0);
        FriendInviteView expired = query.view(JOINER, open.token());
        assertThat(expired.status()).isEqualTo(FriendInviteStatus.EXPIRED);
        assertThat(expired.blockedReason()).isEqualTo(ReasonCode.INVITE_EXPIRED);

        FriendInviteIssue second = invite();
        service.decline(JOINER, second.token());
        assertThat(query.view(9L, second.token()).blockedReason())
                .isEqualTo(ReasonCode.INVITE_CANCELLED);

        now(6, 0);
        FriendInviteIssue third = service.invite(INVITER, StandardDistance.KM3);
        service.accept(JOINER, third.token());
        FriendInviteView joined = query.view(9L, third.token());
        assertThat(joined.status()).isEqualTo(FriendInviteStatus.ACCEPTED);
        assertThat(joined.blockedReason()).isEqualTo(ReasonCode.INVITE_ALREADY_JOINED);
    }

    @Test
    @DisplayName("TC-9265 조회: 초대한 쪽 5km 최고 1812초·티어, 본인 기록 없음 null")
    void tc9265_viewBests() {
        tiers.put(INVITER, Tier.SILVER);
        when(runs.best(INVITER, StandardDistance.KM5))
                .thenReturn(Optional.of(best("r7", Duration.ofSeconds(1812))));
        FriendInviteIssue issue = invite();
        FriendInviteView view = query.view(JOINER, issue.token());
        assertThat(view.inviterBest()).isEqualTo(Duration.ofSeconds(1812));
        assertThat(view.inviterTier()).isEqualTo(Tier.SILVER);
        assertThat(view.myBest()).isNull();
        assertThat(view.myTier()).isEqualTo(Tier.UNRANKED);
        assertThat(view.distance()).isEqualTo(StandardDistance.KM5);
    }

    @Test
    @DisplayName("TC-9266 mine: 열린 초대만, 기한 지난 PENDING은 비어 있다")
    void tc9266_mine() {
        assertThat(query.mine(INVITER)).isEmpty();
        FriendInviteIssue issue = invite();
        assertThat(query.mine(INVITER)).contains(issue);
        now(5, 0);
        assertThat(query.mine(INVITER)).isEmpty();
    }

    @Test
    @DisplayName("동시 초대(uq_friend_invite_pending 위반)는 INVITE_ALREADY_PENDING으로 바꾼다(TC-9804 단위)")
    void tc9804_uniqueViolationBecomesPending() {
        FriendInviteRepository racing = mock(FriendInviteRepository.class);
        when(racing.findPending(INVITER)).thenReturn(Optional.empty());
        when(racing.saveImmediately(org.mockito.ArgumentMatchers.any()))
                .thenThrow(integrityViolation("23505"));
        FriendInviteService raced =
                new FriendInviteService(
                        racing,
                        new RunnerAvailability(queue, battles, racing),
                        members,
                        settings,
                        startService,
                        messenger,
                        tx,
                        clock);
        assertReason(
                () -> raced.invite(INVITER, StandardDistance.KM5),
                ReasonCode.INVITE_ALREADY_PENDING);
        verify(settings, times(0)).rememberBattleDistance(INVITER, StandardDistance.KM5);
    }

    private static org.springframework.dao.DataIntegrityViolationException integrityViolation(
            String sqlState) {
        return new org.springframework.dao.DataIntegrityViolationException(
                "could not execute statement", new java.sql.SQLException("violation", sqlState));
    }

    @Test
    @DisplayName(
            "BE8-5 invite: 고유 제약이 아닌 무결성 위반(NOT NULL 23502·CHECK 23514)은 INVITE_ALREADY_PENDING으로"
                    + " 바꾸지 않고 그대로 올린다")
    void be8_5_otherIntegrityViolationsPropagate() {
        for (String sqlState : java.util.List.of("23502", "23514")) {
            FriendInviteRepository broken = mock(FriendInviteRepository.class);
            when(broken.findPending(INVITER)).thenReturn(Optional.empty());
            when(broken.saveImmediately(org.mockito.ArgumentMatchers.any()))
                    .thenThrow(integrityViolation(sqlState));
            FriendInviteService service =
                    new FriendInviteService(
                            broken,
                            new RunnerAvailability(queue, battles, broken),
                            members,
                            settings,
                            startService,
                            messenger,
                            tx,
                            clock);
            assertThatThrownBy(() -> service.invite(INVITER, StandardDistance.KM5))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
    }

    @Test
    @DisplayName("BE8-4 invite: 기한 지난 자기 초대를 정리하는 쪽과 겹쳐 낙관적 잠금이 한 번 충돌해도 다시 읽어 새 초대를 만든다")
    void be8_4_expiryRaceRetries() {
        FriendInviteIssue old = invite();
        now(5, 1);
        FriendInviteRepository racing = mock(FriendInviteRepository.class);
        FriendInvite stale = invites.findById(old.inviteId()).orElseThrow();
        when(racing.findPending(INVITER))
                .thenReturn(Optional.of(stale))
                .thenReturn(Optional.empty());
        when(racing.saveImmediately(stale))
                .thenThrow(
                        new org.springframework.orm.ObjectOptimisticLockingFailureException(
                                FriendInvite.class, old.inviteId()));
        when(racing.saveImmediately(org.mockito.ArgumentMatchers.argThat(i -> i != stale)))
                .thenAnswer(a -> invites.saveImmediately(a.getArgument(0)));
        FriendInviteService raced =
                new FriendInviteService(
                        racing,
                        new RunnerAvailability(queue, battles, racing),
                        members,
                        settings,
                        startService,
                        messenger,
                        tx,
                        clock);

        FriendInviteIssue fresh = raced.invite(INVITER, StandardDistance.KM5);

        assertThat(fresh.inviteId()).isNotEqualTo(old.inviteId());
    }

    @Test
    @DisplayName("탈퇴 파기: eraseOf는 그 회원이 보낸 초대와 참가한 초대를 지운다")
    void eraseOf_deletesInvitesOfMember() {
        FriendInviteIssue sent = invite();
        service.accept(JOINER, sent.token());
        long other = 77L;
        FriendInviteIssue unrelated = service.invite(other, StandardDistance.KM1);

        service.eraseOf(JOINER);

        assertThat(invites.findById(sent.inviteId())).isEmpty();
        assertThat(invites.findById(unrelated.inviteId())).isPresent();
    }

    @Test
    @DisplayName("TC-9805 만료 경계: 4:59 수락 성공, 5:00·5:01은 정리 작업 전이라도 INVITE_EXPIRED")
    void tc9805_expiryBoundary() {
        FriendInviteIssue early = invite();
        now(4, 59);
        assertThat(service.accept(JOINER, early.token())).isPositive();

        for (int seconds : new int[] {0, 1}) {
            // 새 초대 한 쌍씩: 초대한 쪽이 다른 대결 중이 아니도록 다른 회원으로 만든다
            long inviter = 500L + seconds;
            now(0, 0);
            FriendInviteIssue issue = service.invite(inviter, StandardDistance.KM3);
            now(5, seconds);
            assertReason(
                    () -> service.accept(JOINER + 10, issue.token()), ReasonCode.INVITE_EXPIRED);
            assertThat(invites.findById(issue.inviteId()).orElseThrow().getStatus())
                    .as("정리 작업 전이라 저장 상태는 PENDING 그대로")
                    .isEqualTo(FriendInviteStatus.PENDING);
        }
    }
}
