package com.a207.runkk.web.crew;

import com.a207.runkk.common.AggregationPeriod;
import com.a207.runkk.common.PersonalityTag;
import com.a207.runkk.crew.CrewCreateResult;
import com.a207.runkk.crew.CrewQueryService;
import com.a207.runkk.crew.CrewRankingView;
import com.a207.runkk.crew.CrewSearch;
import com.a207.runkk.crew.CrewService;
import com.a207.runkk.crew.JoinResult;
import com.a207.runkk.crew.JoinTarget;
import com.a207.runkk.crew.MembershipService;
import com.a207.runkk.crew.WeeklyRaceService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 크루 엔드포인트(설계 5.7.1): 생성, 목록·추천, 가입 신청·취소, 탈퇴, 크루 내 표시 정보, 크루원·가입 대기·랭킹·주간 현황 조회. 엔드포인트 하나를 서비스 연산
 * 하나에 잇고 요청·응답 형식만 바꾼다. 크루장 전용 변경은 CrewAdminController가 받는다.
 */
@RestController
@RequestMapping("/api/v1")
public class CrewController {

    private static final String MEMBER_ID = "memberId";

    private final CrewService crewService;
    private final CrewQueryService crewQueryService;
    private final MembershipService membershipService;
    private final WeeklyRaceService weeklyRaceService;
    private final CrewMembersAssembler crewMembersAssembler;

    CrewController(
            CrewService crewService,
            CrewQueryService crewQueryService,
            MembershipService membershipService,
            WeeklyRaceService weeklyRaceService,
            CrewMembersAssembler crewMembersAssembler) {
        this.crewService = crewService;
        this.crewQueryService = crewQueryService;
        this.membershipService = membershipService;
        this.weeklyRaceService = weeklyRaceService;
        this.crewMembersAssembler = crewMembersAssembler;
    }

    @PostMapping("/crews")
    @ResponseStatus(HttpStatus.CREATED)
    CrewDtos.CreatedResponse create(
            @RequestAttribute(MEMBER_ID) long memberId,
            @Valid @RequestBody CrewDtos.CrewFormRequest body) {
        CrewCreateResult result = crewService.create(memberId, body.toCommand());
        return new CrewDtos.CreatedResponse(
                result.crewId(), result.inviteCode(), result.inviteCodeExpiresAt());
    }

    @GetMapping("/crews")
    CrewDtos.CrewListResponse search(
            @RequestAttribute(MEMBER_ID) long memberId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Set<PersonalityTag> tags) {
        return CrewDtos.CrewListResponse.of(
                crewQueryService.search(memberId, new CrewSearch(tags, keyword)));
    }

    /** 본인 소속 크루와 대기 중인 본인 가입 신청(크루 탭 재진입). 설계 5.7.1에 없어 더한 조회다. */
    @GetMapping("/crews/me")
    CrewDtos.MyCrewResponse myCrew(@RequestAttribute(MEMBER_ID) long memberId) {
        return CrewDtos.MyCrewResponse.of(crewQueryService.myCrew(memberId));
    }

    /** 크루 한 곳(크루 홈·정보 수정 화면). 비공개 크루는 크루원만. 설계 5.7.1에 없어 더한 조회다. */
    @GetMapping("/crews/{id}")
    CrewDtos.CrewDetailResponse detail(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") long crewId) {
        return CrewDtos.CrewDetailResponse.of(crewQueryService.detail(memberId, crewId));
    }

    @GetMapping("/crews/recommendations")
    CrewDtos.RecommendationsResponse recommend(@RequestAttribute(MEMBER_ID) long memberId) {
        return CrewDtos.RecommendationsResponse.of(crewQueryService.recommend(memberId));
    }

    @PostMapping("/crews/join")
    CrewDtos.JoinResponse apply(
            @RequestAttribute(MEMBER_ID) long memberId,
            @RequestBody CrewDtos.JoinRequestBody body) {
        return joinResponse(
                membershipService.apply(
                        memberId, new JoinTarget(body.crewId(), body.inviteCode())));
    }

    @DeleteMapping("/join-requests/{id}")
    CrewDtos.JoinResponse cancel(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") long requestId) {
        return joinResponse(membershipService.cancel(memberId, requestId));
    }

    private static CrewDtos.JoinResponse joinResponse(JoinResult result) {
        return new CrewDtos.JoinResponse(
                result.status().name(), result.crewId(), result.requestId());
    }

    @PostMapping("/crews/{id}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void leave(@RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") long crewId) {
        membershipService.leave(memberId, crewId);
    }

    @PatchMapping("/crews/{id}/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void editCrewProfile(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable("id") long crewId,
            @RequestBody CrewDtos.CrewProfileRequest body) {
        membershipService.editCrewProfile(memberId, crewId, body.toEdit());
    }

    @GetMapping("/crews/{id}/members")
    CrewMembersAssembler.CrewMembersResponse members(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") long crewId) {
        return crewMembersAssembler.members(memberId, crewId);
    }

    @GetMapping("/crews/{id}/join-requests")
    CrewMembersAssembler.PendingRequestsResponse pendingRequests(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") long crewId) {
        return crewMembersAssembler.pendingRequests(memberId, crewId);
    }

    @GetMapping("/crews/{id}/ranking")
    RankingResponse crewRanking(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable("id") long crewId,
            @RequestParam(defaultValue = "WEEKLY") AggregationPeriod period) {
        CrewRankingView view = crewQueryService.crewRanking(memberId, crewId, period);
        return new RankingResponse(
                view.period().name(),
                view.entries().stream()
                        .map(
                                e ->
                                        new RankingResponse.Entry(
                                                e.rank(),
                                                e.memberId(),
                                                CrewDtos.displayOf(e.name()),
                                                e.distance().meters()))
                        .toList());
    }

    @GetMapping("/crews/{id}/race")
    CrewDtos.RaceStatusResponse race(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable("id") long crewId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate weekStart) {
        return CrewDtos.RaceStatusResponse.of(
                weeklyRaceService.status(memberId, crewId, Optional.ofNullable(weekStart)),
                memberId);
    }

    /**
     * 크루 내 랭킹.
     *
     * @param period WEEKLY | MONTHLY
     * @param entries 순위
     */
    record RankingResponse(String period, List<Entry> entries) {

        /**
         * 순위 한 줄(비공개 크루원도 순위·거리만).
         *
         * @param rank 순위
         * @param memberId 회원 ID
         * @param displayName 표시명
         * @param distanceMeters 누적 유효 거리
         */
        record Entry(int rank, long memberId, String displayName, long distanceMeters) {}
    }
}
