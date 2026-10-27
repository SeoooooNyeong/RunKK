package com.a207.runkk.crew;

import static com.a207.runkk.crew.CrewFixtures.LEADER;
import static com.a207.runkk.crew.CrewFixtures.NOW;
import static com.a207.runkk.crew.CrewFixtures.RULE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import java.time.LocalDateTime;
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
    @DisplayName("작성: VOTING, 작성자는 GOING. 지난 시각·크루런 밖 거리·희망일시 뒤 마감은 CREW_RUN_INVALID")
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
                                        new CrewRunDraft("하프", AT, StandardDistance.HALF, null),
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
    @DisplayName("진행: 도착은 처음만, 그만두기는 순위 제외, 러닝 연결은 한 참여에 1건, 모두 끝나면 종료 조건")
    void running_arrivalQuitLink() {
        CrewRunPost post = running(A, B);
        LocalDateTime start = AT.plusSeconds(3);
        assertThat(post.shouldClose(start.plusMinutes(10), RULE)).isFalse();

        post.recordArrival(A, start.plusMinutes(25));
        post.recordArrival(A, start.plusMinutes(40));
        post.recordArrival(LEADER, start.plusMinutes(28));
        assertThat(post.linkRun(A, "rec-a")).isTrue();
        assertThat(post.linkRun(A, "rec-a2")).isFalse();
        assertThat(post.linkRun(C, "rec-c")).as("참가자가 아님").isFalse();
        post.quit(B, start.plusMinutes(12));
        assertThatThrownBy(() -> post.quit(C, start))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.NOT_PARTICIPANT));

        assertThat(post.shouldClose(start.plusMinutes(30), RULE)).isTrue();
        post.close(start.plusMinutes(30));

        assertThat(post.status()).isEqualTo(CrewRunStatus.FINISHED);
        assertThat(post.participationOf(A).orElseThrow().rank()).isEqualTo(1);
        assertThat(post.participationOf(LEADER).orElseThrow().rank()).isEqualTo(2);
        assertThat(post.participationOf(B).orElseThrow().rank()).isNull();
        assertThat(post.summary(A).crewRun().finisherCount()).isEqualTo(2);
        assertThat(post.linkedRecordIds()).containsExactly("rec-a");
    }

    @Test
    @DisplayName("종료 기준 시간(출발 + 거리 × 15분)이 지나면 미도착은 미완주로 끝난다")
    void close_byTimeLimit() {
        CrewRunPost post = running(A);
        LocalDateTime start = AT.plusSeconds(3);
        post.recordArrival(A, start.plusMinutes(30));
        assertThat(post.shouldClose(start.plusMinutes(75).minusSeconds(1), RULE)).isFalse();
        assertThat(post.shouldClose(start.plusMinutes(75), RULE)).isTrue();
        post.close(start.plusMinutes(75));
        assertThat(post.participationOf(LEADER).orElseThrow().rank()).isNull();
        assertThatThrownBy(() -> post.close(start)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("러닝 연결은 진행 뒤(종료 포함)에도 받고, 출발 전에는 받지 않는다")
    void linkRun_onlyAfterDeparture() {
        CrewRunPost voting = post();
        assertThat(voting.linkRun(LEADER, "early")).isFalse();
        CrewRunPost post = running(A);
        post.close(AT.plusHours(3));
        assertThat(post.linkRun(A, "late")).isTrue();
    }
}
