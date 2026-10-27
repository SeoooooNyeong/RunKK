package com.a207.runkk.crew;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 설계 v0.11 crew enum(F.5.1, 06-04, D-98, D-99). TC-6203. */
@DisplayName("v0.11 crew enum")
class CrewEnumsV011Test {

    @Test
    @DisplayName("게시 종류는 6개이고 이름은 post_kind 칼럼(10자) 안이다")
    void postKinds() {
        assertThat(PostKind.values())
                .containsExactly(
                        PostKind.CHEER,
                        PostKind.CREW_RUN,
                        PostKind.MEETUP,
                        PostKind.NOTICE,
                        PostKind.POLL,
                        PostKind.BOARD);
        assertThat(PostKind.values()).allMatch(k -> k.name().length() <= 10);
    }

    @Test
    @DisplayName("참석 응답은 GOING·NOT_GOING·UNDECIDED이고 VoteAnswer는 없다(D-99)")
    void attendanceAnswer_replacesVoteAnswer() {
        assertThat(AttendanceAnswer.values())
                .containsExactly(
                        AttendanceAnswer.GOING,
                        AttendanceAnswer.NOT_GOING,
                        AttendanceAnswer.UNDECIDED);
        assertThatThrownBy(() -> Class.forName("com.a207.runkk.crew.VoteAnswer"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    @DisplayName("투표 방식, 크루 역할·관계에 운영진(MANAGER)")
    void pollMethodAndRoles() {
        assertThat(PollMethod.values()).containsExactly(PollMethod.YES_NO, PollMethod.CHOICES);
        assertThat(CrewRole.values())
                .containsExactly(CrewRole.LEADER, CrewRole.MANAGER, CrewRole.MEMBER);
        assertThat(CrewRelation.values())
                .containsExactly(
                        CrewRelation.LEADER,
                        CrewRelation.MANAGER,
                        CrewRelation.MEMBER,
                        CrewRelation.OUTSIDER);
    }
}
