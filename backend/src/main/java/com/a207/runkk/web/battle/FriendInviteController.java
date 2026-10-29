package com.a207.runkk.web.battle;

import com.a207.runkk.battle.FriendInviteQuery;
import com.a207.runkk.battle.FriendInviteService;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import jakarta.validation.Valid;
import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 친구 대결 초대 엔드포인트(설계 5.6.3, SQ-31·SQ-32, FR-227~FR-229). 업무 거부는 409 {code, message}, 남의 초대 취소는 403
 * PERMISSION_DENIED다(D-227). 끝난 초대 조회는 오류가 아니라 200 + status·blockedReason이고 없는 토큰만 409
 * INVITE_NOT_FOUND다(D-224).
 */
@RestController
@RequestMapping("/api/v1/battles/friend-invites")
public class FriendInviteController {

    private static final String MEMBER_ID = "memberId";

    private final FriendInviteService friendInviteService;
    private final FriendInviteQuery friendInviteQuery;
    private final FriendInviteLinks friendInviteLinks;

    FriendInviteController(
            FriendInviteService friendInviteService,
            FriendInviteQuery friendInviteQuery,
            FriendInviteLinks friendInviteLinks) {
        this.friendInviteService = friendInviteService;
        this.friendInviteQuery = friendInviteQuery;
        this.friendInviteLinks = friendInviteLinks;
    }

    /** 초대 만들기(FR-227). 201. 거리가 1·3·5·10km가 아니면 409 BATTLE_DISTANCE_INVALID. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    FriendInviteDtos.InviteResponse invite(
            @RequestAttribute(MEMBER_ID) long memberId,
            @Valid @RequestBody BattleDtos.DistanceRequest request) {
        return FriendInviteDtos.InviteResponse.of(
                friendInviteService.invite(memberId, standard(request.distanceMeters())),
                friendInviteLinks);
    }

    /** 초대한 쪽 복구용 열린 초대(D-224). 없으면 204. */
    @GetMapping("/mine")
    ResponseEntity<FriendInviteDtos.InviteResponse> mine(
            @RequestAttribute(MEMBER_ID) long memberId) {
        return friendInviteQuery
                .mine(memberId)
                .map(issue -> FriendInviteDtos.InviteResponse.of(issue, friendInviteLinks))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** 초대한 사람의 취소(FR-228). 끝난 초대도 204(멱등). */
    @DeleteMapping("/{inviteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@RequestAttribute(MEMBER_ID) long memberId, @PathVariable long inviteId) {
        friendInviteService.cancel(memberId, inviteId);
    }

    /** 링크를 연 러너의 참가 확인 정보(FR-229). */
    @GetMapping("/by-token/{token}")
    FriendInviteDtos.InviteViewResponse view(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable String token) {
        return FriendInviteDtos.InviteViewResponse.of(friendInviteQuery.view(memberId, token));
    }

    /** 참가(FR-229). 200 {battleId}, 뒤이어 두 사람에게 MatchFound·BattleStart. */
    @PostMapping("/by-token/{token}/accept")
    FriendInviteDtos.AcceptResponse accept(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable String token) {
        return new FriendInviteDtos.AcceptResponse(friendInviteService.accept(memberId, token));
    }

    /** 거절(FR-229). 204. 초대한 앱에 FriendInviteDeclined. */
    @PostMapping("/by-token/{token}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void decline(@RequestAttribute(MEMBER_ID) long memberId, @PathVariable String token) {
        friendInviteService.decline(memberId, token);
    }

    /** 기준 거리가 아니면 BATTLE_DISTANCE_INVALID(서비스가 1·3·5·10km를 다시 본다). */
    private static StandardDistance standard(long meters) {
        return Arrays.stream(StandardDistance.values())
                .filter(d -> d.distance().meters() == meters)
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(ReasonCode.BATTLE_DISTANCE_INVALID));
    }
}
