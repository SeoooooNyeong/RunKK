package com.a207.runkk.web.crew;

import com.a207.runkk.crew.CrewResult;
import com.a207.runkk.crew.CrewService;
import com.a207.runkk.crew.DecisionResult;
import com.a207.runkk.crew.MembershipService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 크루장 전용 엔드포인트(설계 5.7.1, 권한표 3.0.3): 정보 변경, 초대 코드 재발급, 크루장 양도, 크루 삭제, 가입 승인·거절, 강제 퇴장. 권한 검사는 서비스가
 * 권한표로 한다(FR-128). 삭제 확인은 앱 화면이 받는다(D-36).
 */
@RestController
@RequestMapping("/api/v1")
public class CrewAdminController {

    private static final String MEMBER_ID = "memberId";

    private final CrewService crewService;
    private final MembershipService membershipService;

    CrewAdminController(CrewService crewService, MembershipService membershipService) {
        this.crewService = crewService;
        this.membershipService = membershipService;
    }

    @PatchMapping("/crews/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changeInfo(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable("id") long crewId,
            @RequestBody CrewDtos.CrewFormRequest body) {
        crewService.changeInfo(memberId, crewId, body.toChange());
    }

    @PostMapping("/crews/{id}/invite-code")
    CrewDtos.InviteCodeResponse reissueInviteCode(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") long crewId) {
        CrewResult result = crewService.reissueInviteCode(memberId, crewId);
        return new CrewDtos.InviteCodeResponse(result.inviteCode(), result.inviteCodeExpiresAt());
    }

    @PostMapping("/crews/{id}/leader")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void transferLeadership(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable("id") long crewId,
            @Valid @RequestBody CrewDtos.LeaderRequest body) {
        crewService.transferLeadership(memberId, crewId, body.memberId());
    }

    @DeleteMapping("/crews/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") long crewId) {
        crewService.delete(memberId, crewId);
    }

    @PostMapping("/join-requests/{id}/approve")
    CrewDtos.DecisionResponse approveJoin(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") long requestId) {
        return decision(membershipService.approveJoin(memberId, requestId));
    }

    @PostMapping("/join-requests/{id}/reject")
    CrewDtos.DecisionResponse rejectJoin(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") long requestId) {
        return decision(membershipService.rejectJoin(memberId, requestId));
    }

    private static CrewDtos.DecisionResponse decision(DecisionResult result) {
        return new CrewDtos.DecisionResponse(
                result.requestId(), result.status().name(), result.reason());
    }

    @PostMapping("/crews/{id}/members/{mid}/kick")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void kick(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable("id") long crewId,
            @PathVariable("mid") long targetId) {
        membershipService.kick(memberId, crewId, targetId);
    }
}
