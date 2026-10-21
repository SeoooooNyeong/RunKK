package com.a207.runkk.web.crew;

import com.a207.runkk.common.PersonalityTag;
import com.a207.runkk.common.Tier;
import com.a207.runkk.crew.CrewMembersView;
import com.a207.runkk.crew.CrewQueryService;
import com.a207.runkk.crew.PendingRequestsView;
import com.a207.runkk.runimal.RunimalQuery;
import com.a207.runkk.runimal.RunimalType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 크루원 목록 응답을 조합한다(설계 6.1.13 web.crew, D-46). CrewQueryService.members에
 * RunimalQuery.typesOf(disclosedMemberIds)를 붙인다. 업무 규칙 없이 두 조회 결과만 붙이고, 공개 수준은 crew가 member의 판정으로
 * 정한 disclosedMemberIds만 따른다(D-52). 가입 대기 목록의 Runimal 유형(FR-136)도 같은 방식으로 붙인다.
 */
@Component
class CrewMembersAssembler {

    private final CrewQueryService crewQueryService;
    private final RunimalQuery runimalQuery;

    CrewMembersAssembler(CrewQueryService crewQueryService, RunimalQuery runimalQuery) {
        this.crewQueryService = crewQueryService;
        this.runimalQuery = runimalQuery;
    }

    CrewMembersResponse members(long viewerId, long crewId) {
        CrewMembersView view = crewQueryService.members(viewerId, crewId);
        Map<Long, RunimalType> types = runimalQuery.typesOf(view.disclosedMemberIds());
        return new CrewMembersResponse(
                view.members().stream()
                        .map(
                                m ->
                                        new CrewMembersResponse.Member(
                                                m.memberId(),
                                                CrewDtos.displayOf(m.name()),
                                                m.role().name(),
                                                m.crewIntro(),
                                                m.crewTags() == null ? null : m.crewTags().tags(),
                                                m.tier(),
                                                types.get(m.memberId())))
                        .toList());
    }

    PendingRequestsResponse pendingRequests(long requesterId, long crewId) {
        PendingRequestsView view = crewQueryService.pendingRequests(requesterId, crewId);
        Map<Long, RunimalType> types =
                runimalQuery.typesOf(
                        view.requests().stream().map(PendingRequestsView.Line::memberId).toList());
        return new PendingRequestsResponse(
                view.requests().stream()
                        .map(
                                r ->
                                        new PendingRequestsResponse.Request(
                                                r.requestId(),
                                                r.memberId(),
                                                CrewDtos.displayOf(r.name()),
                                                r.tier(),
                                                types.get(r.memberId()),
                                                r.requestedAt()))
                        .toList());
    }

    /**
     * 크루원 목록. 비공개 크루원은 표시명·역할·크루 내 소개만 있다.
     *
     * @param members 크루원
     */
    record CrewMembersResponse(List<Member> members) {

        /**
         * 크루원 한 명.
         *
         * @param memberId 회원 ID
         * @param displayName 표시명
         * @param role LEADER | MEMBER
         * @param crewIntro 크루 내 소개
         * @param tags 크루 내 성향 태그(공개만)
         * @param tier 티어(공개만)
         * @param runimalType Runimal 유형(공개만)
         */
        record Member(
                long memberId,
                String displayName,
                String role,
                String crewIntro,
                Set<PersonalityTag> tags,
                Tier tier,
                RunimalType runimalType) {}
    }

    /**
     * 가입 대기 목록.
     *
     * @param requests 대기 중 신청
     */
    record PendingRequestsResponse(List<Request> requests) {

        /**
         * 신청 한 건.
         *
         * @param requestId 가입 신청 ID
         * @param memberId 신청자
         * @param displayName 표시명
         * @param tier 티어
         * @param runimalType Runimal 유형
         * @param requestedAt 신청 일시
         */
        record Request(
                long requestId,
                long memberId,
                String displayName,
                Tier tier,
                RunimalType runimalType,
                LocalDateTime requestedAt) {}
    }
}
