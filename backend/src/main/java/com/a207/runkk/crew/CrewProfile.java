package com.a207.runkk.crew;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.TagOwner;
import com.a207.runkk.common.TagSet;

/**
 * 크루 생성 입력(자료사전 크루생성요청, srs.md 6.3 5.1.1).
 *
 * @param name 크루명
 * @param intro 한줄소개, 40자 이하
 * @param tags 성향 태그(TagOwner.CREW)
 * @param visibility 공개 여부
 * @param joinPolicy 가입 방식
 * @param logoUrl 로고 이미지 주소, 없을 수 있음
 */
public record CrewProfile(
        CrewName name,
        String intro,
        TagSet tags,
        CrewVisibility visibility,
        JoinPolicy joinPolicy,
        String logoUrl) {

    /** 한줄소개 상한(설계 6.1.7). */
    static final int MAX_INTRO = 40;

    public CrewProfile {
        if (name == null || tags == null || visibility == null || joinPolicy == null) {
            throw new IllegalArgumentException("크루 생성 입력이 비었다");
        }
        if (tags.owner() != TagOwner.CREW || tags.isEmpty()) {
            throw new BusinessRuleException(ReasonCode.TAG_INVALID);
        }
        requireIntro(intro);
    }

    static void requireIntro(String intro) {
        if (intro != null && intro.length() > MAX_INTRO) {
            throw new BusinessRuleException(ReasonCode.INTRO_TOO_LONG);
        }
    }
}
