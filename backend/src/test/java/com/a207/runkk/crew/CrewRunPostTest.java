package com.a207.runkk.crew;

import static com.a207.runkk.crew.CrewFixtures.LEADER;
import static com.a207.runkk.crew.CrewFixtures.NOW;
import static com.a207.runkk.crew.CrewFixtures.RULE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 14장 순서 1 엔티티: CrewRunPost(상태도 2.4.5)·CrewRunParticipation(2.4.6)의 전이와 금지 전이, 6.2 A-13. */
@DisplayName("CrewRunPost 크루런 생애주기")
class CrewRunPostTest {

    private static final LocalDateTime AT = NOW.plusDays(1);
    private static final long A = 1;
    private static final long B = 2;
    private static final long C = 3;

    private static ReasonCode reasonOf(Throwable e) {
        return ((BusinessRuleException) e).reason();
    }

    private static CrewRunPost post() {
        return CrewFixtures.crewRun(9, AT);
    }

    /** 대기실을 연 모집글. */
    private static CrewRunPost lobby() {
        CrewRunPost post = post();
        assertThat(post.openLobby(AT.minusMinutes(30), RULE)).isTrue();
        return post;
    }

    @Test
    @DisplayName("작성: VOTING, 작성자는 GOING. 지난 시각·크루런 밖 거리(1km, W6)·희망일시 뒤 마감은 CREW_RUN_INVALID")
    void create() {
        CrewRunPost post = post();
        assertThat(post.status()).isEqualTo(CrewRunStatus.VOTING);
        assertThat(post.participantsToRemind()).containsExactly(LEADER);

        assertThatThrownBy(() -> CrewFixtures.crewRun(9, NOW.minusMinutes(1)))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.CREW_RUN_INVALID));
        assertThatThrownBy(
                        () ->
                                new CrewRunPost(
                                        9,
                                        LEADER,
                                        new CrewRunDraft("1km", AT, StandardDistance.KM1, null),
                                        NOW))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.CREW_RUN_INVALID));
        assertThatThrownBy(
                        () ->
                                new CrewRunPost(
                                        9,
                                        LEADER,
                                        new CrewRunDraft(
                                                "마감", AT, StandardDistance.KM3, AT.plusMinutes(1)),
                                        NOW))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.CREW_RUN_INVALID));
    }

    @Test
    @DisplayName("참석 응답: 마감 전 추가·변경, 마감 뒤 VOTE_CLOSED, 삭제된 글은 상태 위반(서비스가 먼저 POST_NOT_FOUND)")
    void answer() {
        CrewRunPost post = post();
        post.answer(A, AttendanceAnswer.GOING, NOW);
        post.answer(B, AttendanceAnswer.GOING, NOW);
        AttendanceView view = post.answer(B, AttendanceAnswer.NOT_GOING, NOW);

        assertThat(view.myAnswer()).isEqualTo(AttendanceAnswer.NOT_GOING);
        assertThat(view.goingCount()).isEqualTo(2);
        assertThat(view.notGoingCount()).isEqualTo(1);
        assertThat(post.participantsToRemind()).containsExactlyInAnyOrder(LEADER, A);
        assertThat(post.summary(B).crewRun().myAnswer()).isEqualTo(AttendanceAnswer.NOT_GOING);
        assertThat(post.summary(B).crewRun().counts()).isEqualTo(new AttendanceCounts(2, 1, 0));
        assertThatThrownBy(() -> post.answer(C, AttendanceAnswer.GOING, AT))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.VOTE_CLOSED));
        post.delete(LEADER, CrewRelation.LEADER);
        assertThatThrownBy(() -> post.answer(C, AttendanceAnswer.GOING, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName(
            "대기실: 30분 전에 열리고 이르면 LOBBY_NOT_OPEN, 시작 뒤면 ALREADY_STARTED, 입장은 응답 없거나 못 가요여도 GOING")
    void lobby_openEnterToggleLeave() {
        CrewRunPost post = post();
        assertThat(post.openLobby(AT.minusMinutes(31), RULE)).isFalse();
        assertThatThrownBy(() -> post.enterLobby(A, AT.minusMinutes(31), RULE))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.LOBBY_NOT_OPEN));
        assertThat(post.openLobby(AT.minusMinutes(30), RULE)).isTrue();
        assertThat(post.openLobby(AT.minusMinutes(29), RULE)).as("한 번만 연다").isFalse();

        post.answer(A, AttendanceAnswer.NOT_GOING, NOW);
        post.enterLobby(A, AT.minusMinutes(5), RULE);
        assertThat(post.readinessOf(A)).contains(Readiness.ENTERING);
        assertThat(post.participantsToRemind()).contains(A);
        assertThat(post.toggleReady(A)).isEqualTo(Readiness.READY);
        assertThat(post.toggleReady(A)).isEqualTo(Readiness.ENTERING);
        post.leaveLobby(A);
        assertThat(post.readinessOf(A)).contains(Readiness.NOT_ENTERED);
        assertThatThrownBy(() -> post.toggleReady(A)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> post.enterLobby(B, AT, RULE))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.ALREADY_STARTED));
    }

    @Test
    @DisplayName("출발: READY 2명 이상이면 RUNNING(희망일시 + 3초), 준비 안 한 참가자는 빠진다")
    void depart_started() {
        CrewRunPost post = lobby();
        post.enterLobby(LEADER, AT.minusMinutes(1), RULE);
        post.enterLobby(A, AT.minusMinutes(1), RULE);
        post.enterLobby(B, AT.minusMinutes(1), RULE);
        post.toggleReady(LEADER);
        post.toggleReady(A);

        assertThat(post.depart(AT.minusSeconds(1), RULE).kind())
                .isEqualTo(DepartureOutcome.Kind.NONE);
        DepartureOutcome outcome = post.depart(AT, RULE);

        assertThat(outcome.kind()).isEqualTo(DepartureOutcome.Kind.STARTED);
        assertThat(outcome.startAt()).isEqualTo(AT.plusSeconds(3));
        assertThat(outcome.memberIds()).containsExactlyInAnyOrder(LEADER, A);
        assertThat(post.status()).isEqualTo(CrewRunStatus.RUNNING);
        assertThat(post.runners()).containsExactlyInAnyOrder(LEADER, A);
        assertThat(post.depart(AT, RULE).kind()).isEqualTo(DepartureOutcome.Kind.NONE);
    }

    @Test
    @DisplayName("출발: READY가 2명 미만이면 CANCELLED, 갈래요 응답자에게 알린다")
    void depart_cancelled() {
        CrewRunPost post = lobby();
        post.answer(A, AttendanceAnswer.GOING, NOW);
        post.enterLobby(LEADER, AT.minusMinutes(1), RULE);
        post.toggleReady(LEADER);

        DepartureOutcome outcome = post.depart(AT, RULE);

        assertThat(outcome.kind()).isEqualTo(DepartureOutcome.Kind.CANCELLED);
        assertThat(outcome.memberIds()).containsExactlyInAnyOrder(LEADER, A);
        assertThat(post.status()).isEqualTo(CrewRunStatus.CANCELLED);
        assertThatThrownBy(() -> post.enterLobby(A, AT.minusMinutes(1), RULE))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.ALREADY_STARTED));
    }

    private static CrewRunPost running(long... others) {
        CrewRunPost post = lobby();
        post.enterLobby(LEADER, AT.minusMinutes(1), RULE);
        post.toggleReady(LEADER);
        for (long m : others) {
            post.enterLobby(m, AT.minusMinutes(1), RULE);
            post.toggleReady(m);
        }
        post.depart(AT, RULE);
        return post;
    }

    @Test
    @DisplayName("진행: 각자 끝내기(값 남김, 다시 불러도 그대로), 정지 확인, 러닝 연결은 한 참여에 1건, 모두 끝내면 종료 조건")
    void running_endPauseLink() {
        CrewRunPost post = running(A, B);
        LocalDateTime start = AT.plusSeconds(3);
        assertThat(post.shouldClose(start.plusMinutes(10), RULE)).isFalse();

        assertThat(post.linkRun(A, "rec-a")).isTrue();
        assertThat(post.linkRun(A, "rec-a2")).isFalse();
        assertThat(post.linkRun(C, "rec-c")).as("참가자가 아님").isFalse();
        post.checkPausable(B);
        assertThat(post.end(B, Distance.ofMeters(2_100), Duration.ofMinutes(12))).isTrue();
        assertThat(post.end(B, Distance.ofMeters(9_999), Duration.ofMinutes(99)))
                .as("이미 끝냄")
                .isFalse();
        assertThat(post.participationOf(B).orElseThrow().finalDistance())
                .contains(Distance.ofMeters(2_100));
        assertThatThrownBy(() -> post.checkPausable(B))
                .as("끝낸 뒤 정지")
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.NOT_PARTICIPANT));
        assertThatThrownBy(() -> post.end(C, null, null))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.NOT_PARTICIPANT));
        assertThatThrownBy(() -> post.checkPausable(C))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.NOT_PARTICIPANT));

        post.end(A, Distance.ofMeters(5_280), Duration.ofMinutes(28));
        assertThat(post.shouldClose(start.plusMinutes(30), RULE)).as("방장이 아직 달린다").isFalse();
        post.end(LEADER, null, null);
        assertThat(post.shouldClose(start.plusMinutes(30), RULE)).isTrue();
        post.close(start.plusMinutes(30), Map.of());

        assertThat(post.status()).isEqualTo(CrewRunStatus.FINISHED);
        assertThat(post.participations())
                .filteredOn(CrewRunParticipation::isRunner)
                .allMatch(CrewRunParticipation::hasEnded);
        assertThat(post.summary(A).crewRun().finisherCount()).as("진행 참가자 수").isEqualTo(3);
        assertThat(post.linkedRecordIds()).containsExactly("rec-a");
    }

    @Test
    @DisplayName("종료 기준 시간(출발 + 거리 × 15분)이 지나면 아직 달리는 사람은 마지막 중계 값으로 마감된다")
    void close_byTimeLimit() {
        CrewRunPost post = running(A);
        LocalDateTime start = AT.plusSeconds(3);
        post.end(A, Distance.ofMeters(4_000), Duration.ofMinutes(30));
        assertThat(post.shouldClose(start.plusMinutes(75).minusSeconds(1), RULE)).isFalse();
        assertThat(post.shouldClose(start.plusMinutes(75), RULE)).isTrue();
        post.close(
                start.plusMinutes(75),
                Map.of(
                        LEADER,
                        new LiveProgress(
                                Distance.ofMeters(6_100),
                                Duration.ofMinutes(74),
                                start.plusMinutes(75)),
                        A,
                        new LiveProgress(
                                Distance.ofMeters(9_000),
                                Duration.ofMinutes(74),
                                start.plusMinutes(75))));
        CrewRunParticipation leader = post.participationOf(LEADER).orElseThrow();
        assertThat(leader.hasEnded()).isTrue();
        assertThat(leader.finalDistance()).contains(Distance.ofMeters(6_100));
        assertThat(leader.finalElapsed()).contains(Duration.ofMinutes(74));
        assertThat(post.participationOf(A).orElseThrow().finalDistance())
                .as("먼저 끝낸 값은 그대로")
                .contains(Distance.ofMeters(4_000));
        assertThatThrownBy(() -> post.close(start, Map.of()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(post.end(LEADER, Distance.ofMeters(1), Duration.ZERO))
                .as("종료 뒤 끝내기는 이미 끝남")
                .isFalse();
    }

    @Test
    @DisplayName("러닝 연결은 진행 뒤(종료 포함)에도 받고, 출발 전에는 받지 않는다. 출발 전 끝내기·정지는 NOT_PARTICIPANT")
    void linkRun_onlyAfterDeparture() {
        CrewRunPost voting = post();
        assertThat(voting.linkRun(LEADER, "early")).isFalse();
        assertThatThrownBy(() -> voting.end(LEADER, null, null))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.NOT_PARTICIPANT));
        assertThatThrownBy(() -> voting.checkPausable(LEADER))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.NOT_PARTICIPANT));
        CrewRunPost post = running(A);
        post.close(AT.plusHours(3), Map.of());
        assertThat(post.linkRun(A, "late")).isTrue();
    }
}
