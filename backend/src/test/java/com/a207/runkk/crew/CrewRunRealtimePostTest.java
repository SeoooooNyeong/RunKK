package com.a207.runkk.crew;

import static com.a207.runkk.crew.CrewFixtures.LEADER;
import static com.a207.runkk.crew.CrewFixtures.NOW;
import static com.a207.runkk.crew.CrewFixtures.RULE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.ReasonCode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 설계 05-32(크루런 실시간 반영) 엔티티 규칙: 전원 준비·모집자·예약 출발의 후보 고정과 3초 출발(U-25, U-33), 중간 참여(U-26), 멱등 준비, 단계와
 * 순번. 시각은 매개변수로 준다.
 */
@DisplayName("CrewRunPost 출발 후보 고정·중간 참여(05-32)")
class CrewRunRealtimePostTest {

    private static final LocalDateTime AT = NOW.plusDays(1);
    private static final LocalDateTime LOBBY_TIME = AT.minusMinutes(10);
    private static final long A = 1;
    private static final long B = 2;
    private static final long C = 3;
    private static final long D = 4;

    private static ReasonCode reasonOf(Throwable e) {
        return ((BusinessRuleException) e).reason();
    }

    /** 대기실을 연 모집글(ID 101). members는 입장만 한다. */
    private static CrewRunPost lobby(long... members) {
        CrewRunPost post = CrewFixtures.crewRun(9, AT);
        ReflectionTestUtils.setField(post, "id", 101L);
        assertThat(post.openLobby(AT.minusMinutes(30), RULE)).isTrue();
        for (long m : members) {
            post.enterLobby(m, LOBBY_TIME, RULE);
        }
        return post;
    }

    @Test
    @DisplayName(
            "[임시:TC-성-11022] 입장 2명이 모두 준비하면 후보 고정 + 3초 뒤 출발, 참석만 한 미입장 3명은 분모에서 빠진다(TC-11043"
                    + " startAt = 예약 + 3초 그대로)")
    void allReady_plansWithEntrantsOnly() {
        CrewRunPost post = lobby(LEADER, A);
        for (long absent : new long[] {B, C, D}) {
            post.answer(absent, AttendanceAnswer.GOING, NOW);
        }
        post.setReady(LEADER, true);
        assertThat(post.planIfAllReady(LOBBY_TIME, RULE)).as("A 미준비").isFalse();
        post.setReady(A, true);

        assertThat(post.planIfAllReady(LOBBY_TIME, RULE)).isTrue();
        assertThat(post.phase()).isEqualTo(CrewRunPhase.COUNTDOWN);
        assertThat(post.status()).as("상태 enum은 그대로").isEqualTo(CrewRunStatus.LOBBY_OPEN);
        assertThat(post.startReason()).contains(StartReason.ALL_READY);
        assertThat(post.startAt()).contains(LOBBY_TIME.plusSeconds(3));
        assertThat(post.countdownParticipantIds()).containsExactly(LEADER, A);
        assertThat(post.countdownToken()).hasValueSatisfying(t -> assertThat(t).startsWith("101-"));
        assertThat(post.planIfAllReady(LOBBY_TIME, RULE)).as("한 번만 예약").isFalse();

        assertThat(post.depart(LOBBY_TIME.plusSeconds(2), RULE).kind())
                .isEqualTo(DepartureOutcome.Kind.NONE);
        DepartureOutcome started = post.depart(LOBBY_TIME.plusSeconds(3), RULE);
        assertThat(started.kind()).isEqualTo(DepartureOutcome.Kind.STARTED);
        assertThat(started.startAt()).as("앱은 다시 3초를 더하지 않는다").isEqualTo(LOBBY_TIME.plusSeconds(3));
        assertThat(started.memberIds()).containsExactly(LEADER, A);
        assertThat(post.status()).isEqualTo(CrewRunStatus.RUNNING);
        assertThat(post.departedAt()).contains(LOBBY_TIME.plusSeconds(3));
        assertThat(post.participationOf(A).orElseThrow().joinedAt())
                .contains(LOBBY_TIME.plusSeconds(3));
        assertThat(post.participationOf(A).orElseThrow().measurementRecordId())
                .hasValueSatisfying(id -> assertThat(id).hasSize(36));
    }

    @Test
    @DisplayName("[임시:TC-성-11023] 준비 1명뿐이거나 입장 3명 중 2명만 준비면 전원 준비 예약이 없다")
    void allReady_requiresEveryEntrantAndTwo() {
        CrewRunPost alone = lobby(LEADER);
        alone.setReady(LEADER, true);
        assertThat(alone.planIfAllReady(LOBBY_TIME, RULE)).isFalse();

        CrewRunPost three = lobby(LEADER, A, B);
        three.setReady(LEADER, true);
        three.setReady(A, true);
        assertThat(three.planIfAllReady(LOBBY_TIME, RULE)).isFalse();
        assertThat(three.phase()).isEqualTo(CrewRunPhase.LOBBY);
        assertThat(three.countdownToken()).isEmpty();
    }

    @Test
    @DisplayName(
            "[임시:TC-성-11024] 카운트다운 중 준비 취소·퇴장으로 후보가 1명·0명이 돼도 token·startAt 그대로, 늦은 입장·재준비는 후보에 들지"
                    + " 않고 출발 뒤 join")
    void countdown_onlyShrinks() {
        CrewRunPost post = lobby(LEADER, A, B);
        post.setReady(LEADER, true);
        post.setReady(A, true);
        post.setReady(B, true);
        assertThat(post.planIfAllReady(LOBBY_TIME, RULE)).isTrue();
        String token = post.countdownToken().orElseThrow();
        LocalDateTime startAt = post.startAt().orElseThrow();

        assertThat(post.setReady(A, false)).isTrue();
        post.leaveLobby(B);
        assertThat(post.countdownParticipantIds()).containsExactly(LEADER);
        assertThat(post.setReady(A, true)).as("다시 준비").isTrue();
        post.enterLobby(C, LOBBY_TIME.plusSeconds(1), RULE);
        post.setReady(C, true);
        assertThat(post.countdownParticipantIds()).as("재준비·늦은 입장은 추가 안 함").containsExactly(LEADER);
        assertThat(post.planIfAllReady(LOBBY_TIME.plusSeconds(1), RULE)).isFalse();
        post.toggleReady(LEADER); // 옛 토글로 마지막 후보도 취소
        assertThat(post.countdownParticipantIds()).isEmpty();
        assertThat(post.countdownToken()).contains(token);
        assertThat(post.startAt()).contains(startAt);

        DepartureOutcome started = post.depart(startAt, RULE);
        assertThat(started.kind()).as("0명이어도 같은 시각에 출발").isEqualTo(DepartureOutcome.Kind.STARTED);
        assertThat(started.memberIds()).isEmpty();
        assertThat(started.notifiedIds())
                .as("출발 사실은 입장자 전원")
                .containsExactlyInAnyOrder(LEADER, A, C);
        assertThat(post.shouldClose(startAt.plusSeconds(1), RULE))
                .as("진행 참가자 0명을 전원 종료로 보지 않는다")
                .isFalse();
        assertThat(post.join(C, startAt.plusSeconds(5), RULE)).isTrue();
        assertThat(post.runners()).containsExactly(C);
        assertThat(post.countdownParticipantIds()).isEmpty();
    }

    @Test
    @DisplayName("카운트다운 중 후보가 옛 앱으로 대기실에 다시 들어와도(enter 재호출) 준비와 출발 후보가 그대로다")
    void countdown_reEnterKeepsCandidate() {
        CrewRunPost post = lobby(LEADER, A);
        post.setReady(LEADER, true);
        post.setReady(A, true);
        assertThat(post.planIfAllReady(LOBBY_TIME, RULE)).isTrue();

        post.enterLobby(A, LOBBY_TIME.plusSeconds(1), RULE);

        assertThat(post.participationOf(A).orElseThrow().readiness()).isEqualTo(Readiness.READY);
        assertThat(post.countdownParticipantIds()).containsExactly(LEADER, A);
        assertThat(post.depart(post.startAt().orElseThrow(), RULE).memberIds())
                .containsExactly(LEADER, A);
    }

    @Test
    @DisplayName(
            "[임시:TC-성-11025] 지금 시작: 모집자만(아니면 403), 최초 예약만 준비 2명 검사(부족 CREW_RUN_NOT_READY), 같은 3초와"
                    + " 후보 고정, 재요청은 같은 예약")
    void startNow_rules() {
        CrewRunPost post = lobby(LEADER, A, B);
        post.setReady(LEADER, true);
        assertThat(post.canStartNow(LEADER, RULE)).isFalse();
        assertThatThrownBy(() -> post.startNow(LEADER, LOBBY_TIME, RULE))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.CREW_RUN_NOT_READY));
        post.setReady(A, true);
        assertThat(post.canStartNow(LEADER, RULE)).isTrue();
        assertThat(post.canStartNow(A, RULE)).isFalse();
        assertThatThrownBy(() -> post.startNow(A, LOBBY_TIME, RULE))
                .isInstanceOf(PermissionDeniedException.class);

        assertThat(post.startNow(LEADER, LOBBY_TIME, RULE)).isTrue();
        String token = post.countdownToken().orElseThrow();
        assertThat(post.startReason()).contains(StartReason.HOST);
        assertThat(post.countdownParticipantIds()).containsExactly(LEADER, A);
        assertThat(post.startAt()).contains(LOBBY_TIME.plusSeconds(3));

        post.setReady(A, false);
        assertThat(post.startNow(LEADER, LOBBY_TIME.plusSeconds(1), RULE))
                .as("후보가 1명으로 줄어도 인원 재검사 없이 같은 예약")
                .isFalse();
        assertThat(post.countdownToken()).contains(token);
        assertThat(post.startAt()).contains(LOBBY_TIME.plusSeconds(3));
        assertThat(post.canStartNow(LEADER, RULE)).isFalse();

        CrewRunPost voting = CrewFixtures.crewRun(9, AT);
        assertThatThrownBy(() -> voting.startNow(LEADER, NOW, RULE))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.CREW_RUN_NOT_READY));
    }

    @Test
    @DisplayName(
            "[임시:TC-성-11026] 전원 준비·지금 시작·예약 시각이 겹쳐도 예약은 하나(같은 token·startAt), 예약 뒤 후보 감소로 취소·재예약"
                    + " 없음, 최초 예약 전 부족만 취소")
    void threePaths_singlePlan() {
        CrewRunPost post = lobby(LEADER, A);
        post.setReady(LEADER, true);
        post.setReady(A, true);
        assertThat(post.planIfAllReady(LOBBY_TIME, RULE)).isTrue();
        String token = post.countdownToken().orElseThrow();
        LocalDateTime startAt = post.startAt().orElseThrow();
        assertThat(post.startNow(LEADER, LOBBY_TIME.plusSeconds(1), RULE)).isFalse();
        post.setReady(A, false);
        post.leaveLobby(LEADER);
        assertThat(post.depart(AT, RULE).kind())
                .as("예약 시각이 지나도 후보 감소로 취소하지 않고 그대로 출발")
                .isEqualTo(DepartureOutcome.Kind.STARTED);
        assertThat(post.countdownToken()).contains(token);
        assertThat(post.departedAt()).contains(startAt);
        assertThat(post.startNow(LEADER, AT, RULE)).as("출발 뒤 재요청도 같은 예약").isFalse();

        CrewRunPost scheduled = lobby(LEADER, A, B);
        scheduled.setReady(LEADER, true);
        scheduled.setReady(A, true);
        DepartureOutcome planned = scheduled.depart(AT, RULE);
        assertThat(planned.kind()).isEqualTo(DepartureOutcome.Kind.PLANNED);
        assertThat(planned.startAt()).isEqualTo(AT.plusSeconds(3));
        assertThat(planned.memberIds()).containsExactly(LEADER, A);
        assertThat(scheduled.startReason()).contains(StartReason.SCHEDULED);
        assertThat(scheduled.depart(AT.plusSeconds(1), RULE).kind())
                .as("예약 뒤 다시 예약하지 않는다")
                .isEqualTo(DepartureOutcome.Kind.NONE);
        scheduled.setReady(A, false);
        assertThat(scheduled.depart(AT.plusSeconds(3), RULE).memberIds()).containsExactly(LEADER);

        CrewRunPost lonely = lobby(LEADER, A);
        lonely.setReady(LEADER, true);
        assertThat(lonely.depart(AT, RULE).kind())
                .as("최초 예약 전 부족만 취소")
                .isEqualTo(DepartureOutcome.Kind.CANCELLED);
        assertThat(lonely.status()).isEqualTo(CrewRunStatus.CANCELLED);
    }

    private static CrewRunPost running() {
        CrewRunPost post = lobby(LEADER, A, B);
        post.setReady(LEADER, true);
        post.setReady(A, true);
        post.startNow(LEADER, LOBBY_TIME, RULE);
        post.depart(LOBBY_TIME.plusSeconds(3), RULE);
        return post;
    }

    @Test
    @DisplayName(
            "[임시:TC-성-11027] 출발 사실은 미준비 입장자(B)를 포함한 입장자 전원, 진행 참가자(자동 측정)는 확정 후보만, B는 join으로 합류")
    void departure_notifiesEveryEntrant() {
        CrewRunPost post = lobby(LEADER, A, B);
        post.setReady(LEADER, true);
        post.setReady(A, true);
        post.startNow(LEADER, LOBBY_TIME, RULE);
        DepartureOutcome started = post.depart(LOBBY_TIME.plusSeconds(3), RULE);
        assertThat(started.notifiedIds()).containsExactlyInAnyOrder(LEADER, A, B);
        assertThat(started.memberIds()).containsExactly(LEADER, A);
        assertThat(post.participationOf(B).orElseThrow().isRunner()).isFalse();
        assertThat(post.canJoin(B, LOBBY_TIME.plusSeconds(4), RULE)).isTrue();
        assertThat(post.canJoin(A, LOBBY_TIME.plusSeconds(4), RULE)).isFalse();
    }

    @Test
    @DisplayName(
            "[임시:TC-성-11028] 5분 뒤 join한 C는 승인 시각부터, 불참 응답이어도 GOING으로 바뀌고 거리는 보정하지 않는다(실제 거리로 순위)")
    void join_measuresFromApproval() {
        CrewRunPost post = running();
        LocalDateTime later = LOBBY_TIME.plusSeconds(3).plusMinutes(5);
        post.answer(C, AttendanceAnswer.NOT_GOING, NOW);
        assertThat(post.join(C, later, RULE)).isTrue();
        CrewRunParticipation c = post.participationOf(C).orElseThrow();
        assertThat(c.answer()).isEqualTo(AttendanceAnswer.GOING);
        assertThat(c.joinedAt()).contains(later);
        assertThat(c.isRunner()).isTrue();
        assertThat(c.isCountdownCandidate()).isFalse();
        assertThat(post.runners()).containsExactly(LEADER, A, C);
        assertThat(post.end(C, Distance.ofMeters(800), Duration.ofMinutes(5))).isTrue();
        assertThat(c.finalDistance()).contains(Distance.ofMeters(800));
    }

    @Test
    @DisplayName(
            "[임시:TC-성-11029] join 10번 재요청에도 같은 joinedAt·측정 기록 ID, 출발 전·종료 기준 시각 뒤·종료·취소면 거부(409"
                    + " INVALID_STATE)")
    void join_idempotentAndClosed() {
        CrewRunPost voting = lobby(LEADER, A);
        assertThatThrownBy(() -> voting.join(C, LOBBY_TIME, RULE))
                .isInstanceOf(IllegalStateException.class);

        CrewRunPost post = running();
        LocalDateTime at = LOBBY_TIME.plusMinutes(1);
        assertThat(post.join(C, at, RULE)).isTrue();
        CrewRunParticipation c = post.participationOf(C).orElseThrow();
        String recordId = c.measurementRecordId().orElseThrow();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            assertThat(post.join(C, at.plusMinutes(i), RULE)).isFalse();
            ids.add(c.measurementRecordId().orElseThrow());
            assertThat(c.joinedAt()).contains(at);
        }
        assertThat(ids).containsExactly(recordId);
        assertThat(post.join(A, at, RULE)).as("출발 후보 재요청").isFalse();

        LocalDateTime closes = LOBBY_TIME.plusSeconds(3).plusMinutes(75); // 5km × 15분
        assertThatThrownBy(() -> post.join(D, closes, RULE))
                .as("상태가 RUNNING이어도 종료 기준 시각 뒤면 거부")
                .isInstanceOf(IllegalStateException.class);
        assertThat(post.canJoin(D, closes, RULE)).isFalse();
        post.close(closes, java.util.Map.of());
        assertThatThrownBy(() -> post.join(D, closes.minusMinutes(1), RULE))
                .isInstanceOf(IllegalStateException.class);
        assertThat(post.join(C, closes, RULE)).as("종료 뒤에도 기존 참가자는 같은 값").isFalse();
    }

    @Test
    @DisplayName("멱등 준비: 같은 값은 바뀐 것 없음, 입장 전은 NOT_PARTICIPANT, 대기실이 아니면 상태 위반")
    void setReady_idempotent() {
        CrewRunPost post = lobby(LEADER);
        assertThat(post.setReady(LEADER, true)).isTrue();
        assertThat(post.setReady(LEADER, true)).isFalse();
        assertThat(post.readinessOf(LEADER)).contains(Readiness.READY);
        assertThat(post.setReady(LEADER, false)).isTrue();
        assertThat(post.setReady(LEADER, false)).isFalse();
        assertThatThrownBy(() -> post.setReady(A, true))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.NOT_PARTICIPANT));
        CrewRunPost voting = CrewFixtures.crewRun(9, AT);
        assertThatThrownBy(() -> voting.setReady(LEADER, true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("참석 철회(불참 응답)는 출발 후보에서만 빼고 예약은 그대로, 단계는 투표 중도 LOBBY")
    void attendanceWithdrawal_dropsCandidate() {
        CrewRunPost voting = CrewFixtures.crewRun(9, AT);
        assertThat(voting.phase()).isEqualTo(CrewRunPhase.LOBBY);
        CrewRunPost post = lobby(LEADER, A);
        post.setReady(LEADER, true);
        post.setReady(A, true);
        post.planIfAllReady(LOBBY_TIME, RULE);
        post.answer(A, AttendanceAnswer.NOT_GOING, LOBBY_TIME);
        assertThat(post.countdownParticipantIds()).containsExactly(LEADER);
        assertThat(post.entrantIds()).containsExactly(LEADER);
        long before = post.revision();
        assertThat(post.markChanged()).isEqualTo(before + 1);
    }
}
