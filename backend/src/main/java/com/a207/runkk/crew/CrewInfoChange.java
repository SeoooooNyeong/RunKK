package com.a207.runkk.crew;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.TagOwner;
import com.a207.runkk.common.TagSet;

/**
 * 크루 정보 변경(자료사전 크루정보변경). null인 항목은 바꾸지 않는다.
 *
 * @param name 크루명
 * @param intro 한줄소개
 * @param tags 성향 태그(TagOwner.CREW)
 * @param visibility 공개 여부
 * @param joinPolicy 가입 방식
 * @param logoUrl 로고 이미지 주소
 */
public record CrewInfoChange(
        CrewName name,
        String intro,
        TagSet tags,
        CrewVisibility visibility,
        JoinPolicy joinPolicy,
        String logoUrl) {

    public CrewInfoChange {
        if (tags != null && (tags.owner() != TagOwner.CREW || tags.isEmpty())) {
            throw new BusinessRuleException(ReasonCode.TAG_INVALID);
        }
        CrewProfile.requireIntro(intro);
    }
}
