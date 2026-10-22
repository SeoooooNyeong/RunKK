package com.a207.runkk.crew;

import static com.a207.runkk.crew.CrewFixtures.LEADER;
import static com.a207.runkk.crew.CrewFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.PersonalityTag;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.TagOwner;
import com.a207.runkk.common.TagSet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 14장 순서 1: crew 값 객체(CrewName, InviteCode, JoinEligibility)와 엔티티(Crew, CrewMembership,
 * JoinRequest, CrewPost).
 */
@DisplayName("crew 값 객체와 엔티티")
class CrewDomainTest {

    private static ReasonCode reasonOf(Throwable e) {
        return ((BusinessRuleException) e).reason();
    }

    @Nested
    @DisplayName("CrewName·InviteCode")
    class Values {

        @ParameterizedTest
        @ValueSource(strings = {"", "가", "123456789012345678901"})
        @DisplayName("크루명은 2~20자")
        void crewName_length(String name) {
            assertThatThrownBy(() -> CrewName.of(name))
                    .satisfies(
                            e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.CREW_NAME_INVALID));
        }

        @Test
        @DisplayName("크루명 2자·20자는 된다, null은 거부")
        void crewName_bounds() {
            assertThat(CrewName.of("새벽").value()).isEqualTo("새벽");
            assertThat(CrewName.of("12345678901234567890").value()).hasSize(20);
            assertThatThrownBy(() -> CrewName.of(null)).isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("초대 코드: 6자리 영문·숫자, 발급 + 7일 전까지 유효, 대소문자 구분 없이 맞춘다")
        void inviteCode() {
            InviteCode code = InviteCode.issue("AB12CD", NOW);
            assertThat(code.expiresAt()).isEqualTo(NOW.plusDays(7));
            assertThat(code.isValidAt(NOW.plusDays(7).minusSeconds(1))).isTrue();
            assertThat(code.isValidAt(NOW.plusDays(7))).isFalse();
            assertThat(code.matches("ab12cd")).isTrue();
            assertThat(code.matches("AB12CE")).isFalse();
            assertThat(code.matches(null)).isFalse();
            assertThatThrownBy(() -> InviteCode.issue("AB12C", NOW))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> InviteCode.issue("AB12C!", NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("InviteCodeGenerator: 겹치면 다시 만들고 5번 모두 겹치면 실패")
        void inviteCodeGenerator() {
            List<String> seen = new java.util.ArrayList<>();
            InviteCodeGenerator unique = new InviteCodeGenerator(code -> !seen.add(code) || false);
            assertThat(unique.issue(NOW).code()).matches("[A-Z0-9]{6}");

            int[] tries = {0};
            InviteCodeGenerator taken =
                    new InviteCodeGenerator(
                            code -> {
                                tries[0]++;
                                return true;
                            });
            assertThatThrownBy(() -> taken.issue(NOW)).isInstanceOf(IllegalStateException.class);
            assertThat(tries[0]).isEqualTo(InviteCodeGenerator.MAX_ATTEMPTS);
        }
    }

    @Nested
    @DisplayName("JoinEligibility(5.3.1 자격 판정)")
    class Eligibility {

        @Test
        @DisplayName("소속 중이면 ALREADY_IN_CREW")
        void active() {
            Crew crew = CrewFixtures.crew("소속크루", 1);
            JoinEligibility e = JoinEligibility.evaluate(crew.membershipsOf(1), NOW);
            assertThat(e.isEligible()).isFalse();
            assertThatThrownBy(e::require)
                    .satisfies(x -> assertThat(reasonOf(x)).isEqualTo(ReasonCode.ALREADY_IN_CREW));
        }

        @Test
        @DisplayName("탈퇴·강제 퇴장 뒤 24시간은 COOLDOWN과 가능 시각, 지나면 ELIGIBLE")
        void cooldown() {
            Crew crew = CrewFixtures.crew("쿨크루", 1, 2);
            crew.release(1, LeaveReason.WITHDREW, NOW);
            crew.release(2, LeaveReason.KICKED, NOW.minusHours(25));

            JoinEligibility left =
                    JoinEligibility.evaluate(crew.membershipsOf(1), NOW.plusHours(23));
            assertThat(left.isEligible()).isFalse();
            assertThat(left.availableAt()).contains(NOW.plusHours(24));
            assertThatThrownBy(left::require)
                    .satisfies(x -> assertThat(reasonOf(x)).isEqualTo(ReasonCode.COOLDOWN));
            assertThat(JoinEligibility.evaluate(crew.membershipsOf(2), NOW).isEligible()).isTrue();
            assertThat(JoinEligibility.evaluate(List.of(), NOW).isEligible()).isTrue();
        }
    }

    @Nested
    @DisplayName("Crew")
    class CrewRules {

        @Test
        @DisplayName("생성자가 크루장, 정원 10명(크루장 포함), 11번째 admit은 CREW_FULL")
        void createAndCapacity() {
            Crew crew = CrewFixtures.crew("정원크루", 1, 2, 3, 4, 5, 6, 7, 8);
            assertThat(crew.leaderId()).isEqualTo(LEADER);
            assertThat(crew.relationOf(LEADER)).isEqualTo(CrewRelation.LEADER);
            assertThat(crew.relationOf(1)).isEqualTo(CrewRelation.MEMBER);
            assertThat(crew.relationOf(99)).isEqualTo(CrewRelation.OUTSIDER);
            crew.admit(9, NOW);
            assertThat(crew.headcount()).isEqualTo(Crew.CAPACITY);
            assertThat(crew.isFull()).isTrue();
            assertThatThrownBy(() -> crew.admit(10, NOW))
                    .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.CREW_FULL));
        }

        @Test
        @DisplayName(
                "가입 방식 판정: 코드·자유 IMMEDIATE, 승인제 REQUEST, 정원 FULL, 틀린 코드·비공개 크루 ID는 INVALID_CODE")
        void admission() {
            Crew free = CrewFixtures.crew("자유크루");
            assertThat(free.admissionFor(JoinTarget.byCrew(1L), NOW))
                    .isEqualTo(Admission.IMMEDIATE);
            assertThat(free.admissionFor(JoinTarget.byCode("abc123"), NOW))
                    .isEqualTo(Admission.IMMEDIATE);
            assertThat(free.admissionFor(JoinTarget.byCode("ABC124"), NOW))
                    .isEqualTo(Admission.INVALID_CODE);
            assertThat(free.admissionFor(JoinTarget.byCode("ABC123"), NOW.plusDays(8)))
                    .isEqualTo(Admission.INVALID_CODE);

            Crew approval =
                    Crew.create(
                            LEADER,
                            CrewFixtures.profile(
                                    "승인크루", CrewVisibility.PRIVATE, JoinPolicy.APPROVAL),
                            CrewFixtures.code("XYZ999"),
                            NOW);
            assertThat(approval.admissionFor(JoinTarget.byCrew(1L), NOW))
                    .isEqualTo(Admission.INVALID_CODE);
            assertThat(approval.admissionFor(JoinTarget.byCode("XYZ999"), NOW))
                    .as("승인제도 코드로 오면 승인 생략(Q-41)")
                    .isEqualTo(Admission.IMMEDIATE);
            approval.changeInfo(
                    new CrewInfoChange(null, null, null, CrewVisibility.PUBLIC, null, null));
            assertThat(approval.admissionFor(JoinTarget.byCrew(1L), NOW))
                    .isEqualTo(Admission.REQUEST);

            Crew full = CrewFixtures.crew("만원크루", 1, 2, 3, 4, 5, 6, 7, 8, 9);
            assertThat(full.admissionFor(JoinTarget.byCode("ABC123"), NOW))
                    .isEqualTo(Admission.FULL);
        }

        @Test
        @DisplayName("탈퇴·강제 퇴장: 크루장 탈퇴는 양도 필요(혼자면 삭제 필요), 크루장 강제 퇴장은 대상 오류, 소속 아니면 거부")
        void release() {
            Crew crew = CrewFixtures.crew("퇴장크루", 1, 2);
            assertThatThrownBy(() -> crew.release(LEADER, LeaveReason.WITHDREW, NOW))
                    .satisfies(
                            e ->
                                    assertThat(reasonOf(e))
                                            .isEqualTo(ReasonCode.LEADER_MUST_TRANSFER));
            assertThatThrownBy(() -> crew.release(LEADER, LeaveReason.KICKED, NOW))
                    .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.TARGET_INVALID));
            assertThatThrownBy(() -> crew.release(99, LeaveReason.KICKED, NOW))
                    .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.TARGET_INVALID));
            assertThatThrownBy(() -> crew.release(99, LeaveReason.WITHDREW, NOW))
                    .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.NOT_CREW_MEMBER));

            crew.release(1, LeaveReason.WITHDREW, NOW);
            crew.release(2, LeaveReason.KICKED, NOW);
            assertThat(crew.headcount()).isEqualTo(1);
            assertThat(crew.relationOf(1)).isEqualTo(CrewRelation.OUTSIDER);
            assertThatThrownBy(() -> crew.release(LEADER, LeaveReason.WITHDREW, NOW))
                    .satisfies(
                            e ->
                                    assertThat(reasonOf(e))
                                            .isEqualTo(ReasonCode.CREW_DELETE_REQUIRED));
        }

        @Test
        @DisplayName("크루장 양도는 두 역할을 함께 바꾸고, 대상이 소속 크루원이 아니면 TARGET_INVALID")
        void transferLeadership() {
            Crew crew = CrewFixtures.crew("양도크루", 1);
            assertThatThrownBy(() -> crew.transferLeadership(LEADER, 99))
                    .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.TARGET_INVALID));
            assertThatThrownBy(() -> crew.transferLeadership(LEADER, LEADER))
                    .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.TARGET_INVALID));

            crew.transferLeadership(LEADER, 1);

            assertThat(crew.leaderId()).isEqualTo(1);
            assertThat(crew.relationOf(LEADER)).isEqualTo(CrewRelation.MEMBER);
        }

        @Test
        @DisplayName("초대 코드 재발급은 이전 코드를 바로 무효로 한다")
        void reissue() {
            Crew crew = CrewFixtures.crew("코드크루");
            crew.reissueInviteCode(InviteCode.issue("NEW777", NOW));
            assertThat(crew.acceptsInviteCode("ABC123", NOW)).isFalse();
            assertThat(crew.acceptsInviteCode("new777", NOW)).isTrue();
        }

        @Test
        @DisplayName("크루 내 표시 정보: 40자 초과 INTRO_TOO_LONG, 퇴장한 행은 다시 퇴장할 수 없다")
        void membershipProfile() {
            Crew crew = CrewFixtures.crew("소개크루", 1);
            CrewMembership m = crew.membershipOf(1).orElseThrow();
            m.editCrewProfile("반가워요", TagSet.of(Set.of(PersonalityTag.SOLO_RUN), TagOwner.RUNNER));
            assertThat(m.crewIntro()).isEqualTo("반가워요");
            assertThatThrownBy(() -> m.editCrewProfile("가".repeat(41), null))
                    .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.INTRO_TOO_LONG));
            m.leave(LeaveReason.WITHDREW, NOW);
            assertThat(m.cooldownUntil()).contains(NOW.plusHours(24));
            assertThatThrownBy(() -> m.leave(LeaveReason.WITHDREW, NOW))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("JoinRequest(상태도 2.4.4)")
    class JoinRequests {

        private JoinRequest pending() {
            return new JoinRequest(1, 7, NOW);
        }

        @Test
        @DisplayName("PENDING → APPROVED·REJECTED·DISSOLVED·CANCELLED·EXPIRED")
        void transitions() {
            JoinRequest a = pending();
            a.approve(NOW);
            assertThat(a.status()).isEqualTo(JoinRequestStatus.APPROVED);

            JoinRequest r = pending();
            r.reject(NOW);
            assertThat(r.status()).isEqualTo(JoinRequestStatus.REJECTED);

            JoinRequest d = pending();
            d.dissolve("정원 초과", NOW);
            assertThat(d.status()).isEqualTo(JoinRequestStatus.DISSOLVED);
            assertThat(d.reason()).isEqualTo("정원 초과");

            JoinRequest c = pending();
            c.cancel(7, NOW);
            assertThat(c.status()).isEqualTo(JoinRequestStatus.CANCELLED);

            JoinRequest x = pending();
            assertThat(x.expire(NOW.plusDays(7).minusSeconds(1))).isFalse();
            assertThat(x.isPending()).isTrue();
            assertThat(x.expire(NOW.plusDays(7))).isTrue();
            assertThat(x.status()).isEqualTo(JoinRequestStatus.EXPIRED);
        }

        @Test
        @DisplayName(
                "금지 전이: 처리된 신청의 승인·거절·소멸은 IllegalStateException, 남의 신청·처리된 신청 취소는 NOT_CANCELLABLE")
        void forbidden() {
            JoinRequest done = pending();
            done.reject(NOW);
            assertThatThrownBy(() -> done.approve(NOW)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> done.reject(NOW)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> done.dissolve("x", NOW))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(done.expire(NOW.plusDays(30))).isFalse();
            assertThatThrownBy(() -> done.cancel(7, NOW))
                    .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.NOT_CANCELLABLE));
            assertThatThrownBy(() -> pending().cancel(8, NOW))
                    .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.NOT_CANCELLABLE));
        }

        @Test
        @DisplayName("L12: 7일이 지난 신청은 만료 작업 전이라도 승인할 수 없다(REQUEST_DECIDED), 직전에는 승인된다")
        void approve_rechecksSevenDays() {
            JoinRequest due = pending();
            assertThat(due.isDueToExpire(NOW.plusDays(7))).isTrue();
            assertThatThrownBy(() -> due.approve(NOW.plusDays(7)))
                    .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.REQUEST_DECIDED));
            assertThat(due.isPending()).isTrue();

            JoinRequest inTime = pending();
            assertThat(inTime.isDueToExpire(NOW.plusDays(7).minusSeconds(1))).isFalse();
            inTime.approve(NOW.plusDays(7).minusSeconds(1));
            assertThat(inTime.status()).isEqualTo(JoinRequestStatus.APPROVED);
        }
    }

    @Nested
    @DisplayName("CrewPost·CrewPermissionPolicy")
    class Posts {

        @Test
        @DisplayName("본문 200자 초과 BODY_TOO_LONG")
        void bodyLength() {
            assertThat(new CheerPost(1, LEADER, "가".repeat(200), NOW).isVisible()).isTrue();
            assertThatThrownBy(() -> new CheerPost(1, LEADER, "가".repeat(201), NOW))
                    .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.BODY_TOO_LONG));
        }

        @Test
        @DisplayName("삭제: 작성자 SELF, 크루장 FORCED, 그 밖은 권한 없음")
        void delete() {
            assertThat(new CheerPost(1, 7, "응원", NOW).delete(7, CrewRelation.MEMBER))
                    .isEqualTo(DeletionKind.SELF);
            CheerPost other = new CheerPost(1, 7, "응원", NOW);
            assertThatThrownBy(() -> other.delete(8, CrewRelation.MEMBER))
                    .isInstanceOf(PermissionDeniedException.class);
            assertThatThrownBy(() -> other.delete(7, CrewRelation.OUTSIDER))
                    .isInstanceOf(PermissionDeniedException.class);
            assertThat(other.delete(LEADER, CrewRelation.LEADER)).isEqualTo(DeletionKind.FORCED);
            assertThat(other.isVisible()).isFalse();
        }

        @Test
        @DisplayName("탈퇴한 작성자는 익명이 되고 목록에 작성자 ID가 없다")
        void anonymize() {
            CheerPost post = new CheerPost(1, 7, "응원", NOW);
            post.anonymizeAuthor();
            assertThat(post.summary(7).authorId()).isNull();
            assertThat(post.summary(7).mine()).isFalse();
        }

        @Test
        @DisplayName("권한표 12행: 크루장 전용 5개, 크루원 공용 6개, 공개 정보는 모두")
        void permissionTable() {
            CrewPermissionPolicy policy = new CrewPermissionPolicy();
            for (CrewAction action : CrewAction.values()) {
                assertThat(policy.isAllowed(CrewRelation.LEADER, action))
                        .as(action.name())
                        .isTrue();
            }
            assertThat(
                            List.of(CrewAction.values()).stream()
                                    .filter(a -> policy.isAllowed(CrewRelation.MEMBER, a))
                                    .toList())
                    .containsExactlyInAnyOrder(
                            CrewAction.VIEW_PUBLIC_INFO,
                            CrewAction.WRITE_POST,
                            CrewAction.VOTE_OR_JOIN_CREW_RUN,
                            CrewAction.DELETE_OWN_POST,
                            CrewAction.VIEW_CREW_INTERNAL,
                            CrewAction.EDIT_OWN_CREW_PROFILE,
                            CrewAction.LEAVE);
            assertThat(
                            List.of(CrewAction.values()).stream()
                                    .filter(a -> policy.isAllowed(CrewRelation.OUTSIDER, a))
                                    .toList())
                    .containsExactly(CrewAction.VIEW_PUBLIC_INFO);
            assertThatThrownBy(() -> policy.check(CrewRelation.MEMBER, CrewAction.KICK))
                    .isInstanceOf(PermissionDeniedException.class)
                    .hasMessage("권한 없음");
        }
    }

    @Test
    @DisplayName("CrewMembership 쿨타임: 소속 중이면 없음")
    void activeMembership_hasNoCooldown() {
        Crew crew = CrewFixtures.crew("쿨없음", 1);
        assertThat(crew.membershipOf(1).orElseThrow().cooldownUntil()).isEmpty();
        assertThat(crew.membershipOf(1).orElseThrow().isActive()).isTrue();
        LocalDateTime joined = crew.membershipOf(1).orElseThrow().joinedAt();
        assertThat(joined).isEqualTo(NOW);
    }
}
