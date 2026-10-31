package com.a207.runkk.battle;

import java.time.LocalDateTime;

/**
 * 셀럽 실명 등록의 코치 동의 기록(FR-170, v0.14 D-240). 둘 다 있어야 실명을 등록한다.
 *
 * @param consentedAt 동의 일시
 * @param basis 동의 근거(문서·연락 기록 등)
 */
public record RealNameConsent(LocalDateTime consentedAt, String basis) {

    /** 동의 일시와 근거가 모두 있는가. */
    public boolean isComplete() {
        return consentedAt != null && basis != null && !basis.isBlank();
    }
}
