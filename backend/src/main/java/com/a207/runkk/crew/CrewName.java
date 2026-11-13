package com.a207.runkk.crew;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;

/**
 * 크루명(설계 6.1.7). 새로 정하는 이름(생성·이름 변경)은 2~12자다(피그마 F-04, 결정 C1, S15P31A207-295). 그 전에 2~20자로 만든 크루는
 * 이름을 그대로 두므로 저장 값은 2~20자(crew.name 열)까지 읽는다. 다른 크루와의 중복은 CrewService가 저장소로 확인한다.
 *
 * @param value 크루명
 */
public record CrewName(String value) {

    static final int MIN_LENGTH = 2;

    /** 새로 정하는 이름의 상한(결정 C1). */
    static final int MAX_LENGTH = 12;

    /** 저장된 이름의 상한(crew.name 열, 결정 C1 이전 규칙). */
    static final int STORED_MAX_LENGTH = 20;

    public CrewName {
        if (value == null
                || value.isBlank()
                || value.length() < MIN_LENGTH
                || value.length() > STORED_MAX_LENGTH) {
            throw new BusinessRuleException(ReasonCode.CREW_NAME_INVALID);
        }
    }

    /** 새로 정하는 이름(생성). 앞뒤 공백을 지우고 2~12자가 아니면 BusinessRuleException(CREW_NAME_INVALID). */
    public static CrewName of(String value) {
        return edited(value).requireInputLength();
    }

    /**
     * 정보 변경 요청의 이름. 앞뒤 공백만 지우고 저장 가능 범위(2~20자)만 본다. 지금 이름과 다를 때만 CrewService가 requireInputLength로
     * 2~12자를 확인한다(기존 긴 이름 크루도 다른 항목을 고칠 수 있게).
     */
    public static CrewName edited(String value) {
        return new CrewName(value == null ? null : value.strip());
    }

    /** 새로 정하는 이름 규칙(2~12자). 어기면 BusinessRuleException(CREW_NAME_INVALID). */
    public CrewName requireInputLength() {
        if (value.length() > MAX_LENGTH) {
            throw new BusinessRuleException(ReasonCode.CREW_NAME_INVALID);
        }
        return this;
    }
}
