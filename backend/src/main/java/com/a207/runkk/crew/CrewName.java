package com.a207.runkk.crew;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;

/**
 * 크루명(설계 6.1.7). 2~20자. 다른 크루와의 중복은 CrewService가 저장소로 확인한다.
 *
 * @param value 크루명
 */
public record CrewName(String value) {

    static final int MIN_LENGTH = 2;
    static final int MAX_LENGTH = 20;

    public CrewName {
        if (value == null
                || value.isBlank()
                || value.length() < MIN_LENGTH
                || value.length() > MAX_LENGTH) {
            throw new BusinessRuleException(ReasonCode.CREW_NAME_INVALID);
        }
    }

    /** 위반이면 BusinessRuleException(CREW_NAME_INVALID). */
    public static CrewName of(String value) {
        return new CrewName(value == null ? null : value.strip());
    }
}
