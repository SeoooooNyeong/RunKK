package com.a207.runkk.policy;

import com.a207.runkk.common.Tier;
import java.time.Duration;

/**
 * 매칭 허용 티어 범위(설계 6.1.9).
 *
 * @param tierWindow 허용 티어 차이(0 = 같은 티어)
 * @param ghostAfter 이 시간이 지나면 고스트를 권한다
 */
public record MatchCondition(int tierWindow, Duration ghostAfter) {

    /** 두 티어가 허용 범위 안인가. 랭크 티어와 UNRANKED는 서로 맞추지 않는다. */
    public boolean accepts(Tier a, Tier b) {
        if (a.isRanked() != b.isRanked()) {
            return false;
        }
        return Math.abs(a.ordinal() - b.ordinal()) <= tierWindow;
    }
}
