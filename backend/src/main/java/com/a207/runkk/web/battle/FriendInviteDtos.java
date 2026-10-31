package com.a207.runkk.web.battle;

import com.a207.runkk.battle.FriendInviteIssue;
import com.a207.runkk.battle.FriendInviteView;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 친구 대결 초대 전송 형식(설계 5.6.3, D-219·D-224). 생성 요청은 BattleDtos.DistanceRequest를 다시 쓴다. 시각은 web.battle
 * 관례대로 오프셋 없는 LocalDateTime이다(D-213).
 */
final class FriendInviteDtos {

    private FriendInviteDtos() {}

    /**
     * 만든(또는 열려 있는) 초대.
     *
     * @param inviteId 초대 ID
     * @param token 링크 토큰(22자)
     * @param inviteUrl 카카오톡으로 보낼 링크
     * @param distanceMeters 대결 거리
     * @param expiresAt 만료 시각(생성 + 5분)
     */
    record InviteResponse(
            long inviteId,
            String token,
            String inviteUrl,
            long distanceMeters,
            LocalDateTime expiresAt) {

        static InviteResponse of(FriendInviteIssue issue, FriendInviteLinks links) {
            return new InviteResponse(
                    issue.inviteId(),
                    issue.token(),
                    links.urlOf(issue.token()),
                    issue.distance().distance().meters(),
                    issue.expiresAt());
        }
    }

    /**
     * 링크를 연 러너에게 보일 초대(D-224). 끝난 초대도 200이고 status·blockedReason으로 알린다.
     *
     * @param inviteId 초대 ID
     * @param status PENDING·ACCEPTED·DECLINED·CANCELLED·EXPIRED
     * @param distanceMeters 대결 거리
     * @param expiresAt 만료 시각
     * @param inviter 초대한 러너
     * @param me 조회한 러너
     * @param blockedReason 참가할 수 없는
     *     사유(INVITE_OWN·INVITE_EXPIRED·INVITE_CANCELLED·INVITE_ALREADY_JOINED·ALREADY_IN_BATTLE),
     *     없으면 null
     */
    record InviteViewResponse(
            long inviteId,
            String status,
            long distanceMeters,
            LocalDateTime expiresAt,
            Inviter inviter,
            Me me,
            String blockedReason) {

        static InviteViewResponse of(FriendInviteView v) {
            return new InviteViewResponse(
                    v.inviteId(),
                    v.status().name(),
                    v.distance().distance().meters(),
                    v.expiresAt(),
                    new Inviter(
                            v.inviterName().value(),
                            v.inviterTier().name(),
                            seconds(v.inviterBest())),
                    new Me(v.myTier().name(), seconds(v.myBest())),
                    v.blockedReason() == null ? null : v.blockedReason().name());
        }
    }

    /**
     * 초대한 러너.
     *
     * @param name 표시명
     * @param tier 티어
     * @param bestSeconds 그 거리 최고 기록(초), 없으면 null
     */
    record Inviter(String name, String tier, Long bestSeconds) {}

    /**
     * 조회한 러너 본인.
     *
     * @param tier 티어
     * @param bestSeconds 그 거리 최고 기록(초), 없으면 null
     */
    record Me(String tier, Long bestSeconds) {}

    /**
     * 참가 결과. 뒤이어 두 사람에게 실시간 MatchFound·BattleStart가 간다.
     *
     * @param battleId 대결 ID
     */
    record AcceptResponse(long battleId) {}

    private static Long seconds(Duration d) {
        return d == null ? null : d.toSeconds();
    }
}
