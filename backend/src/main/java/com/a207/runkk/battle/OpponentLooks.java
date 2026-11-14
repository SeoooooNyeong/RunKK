package com.a207.runkk.battle;

import com.a207.runkk.common.AvatarLook;
import com.a207.runkk.running.AvatarLookSource;
import com.a207.runkk.running.RunQueryService;
import org.springframework.stereotype.Component;

/**
 * 대결 상대의 아바타 모습(설계 5.31 B, [임시:D-성-251]~[임시:D-성-253]). 성사 알림(MatchFound), 대결 조회, 대결 결과, 기록 대결 후보와
 * 시작 응답이 같은 규칙을 쓰도록 한곳에 둔다.
 *
 * <ul>
 *   <li>랜덤·친구 대결: 상대 회원의 지금 모습에, 대결에 저장된 표시 성별(OpponentGenders.nameFor)을 덮어쓴다.
 *   <li>나와의 싸움: 상대 기록을 달린 날의 내 모습, 모습이 없는 옛 기록이면 지금 내 모습.
 *   <li>고스트(러너 기록·가상 기록), 배치고사, 유명인: 모습이 없다(null).
 * </ul>
 */
@Component
class OpponentLooks {

    private final AvatarLookSource avatarLookSource;
    private final RunQueryService runQueryService;

    OpponentLooks(AvatarLookSource avatarLookSource, RunQueryService runQueryService) {
        this.avatarLookSource = avatarLookSource;
        this.runQueryService = runQueryService;
    }

    /** 회원이 보는 상대 모습, 싣지 않으면 null. */
    AvatarLook of(Battle battle, long memberId) {
        if (battle.getType() == BattleType.SELF) {
            return battle.getOpponent().map(ref -> selfLook(memberId, ref.recordId())).orElse(null);
        }
        if (battle.getOpponent().isPresent() || !battle.getType().hasLiveRival()) {
            return null;
        }
        String shown = OpponentGenders.nameFor(battle, memberId);
        return battle.rivalOf(memberId)
                .flatMap(rival -> avatarLookSource.currentLookOf(rival.getMemberId()))
                .map(look -> shown == null ? look : look.withGender(shown))
                .orElse(null);
    }

    /** 나와의 싸움 상대(그 기록을 달린 날의 나). 기록에 모습이 없으면 지금 모습으로 대신한다. 둘 다 없으면 null. */
    AvatarLook selfLook(long memberId, String recordId) {
        return runQueryService
                .lookOf(recordId)
                .or(() -> avatarLookSource.currentLookOf(memberId))
                .orElse(null);
    }
}
