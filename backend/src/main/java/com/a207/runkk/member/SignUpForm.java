package com.a207.runkk.member;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.PersonalityTag;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.RecordVisibility;
import com.a207.runkk.common.TagOwner;
import com.a207.runkk.common.TagSet;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 가입 요청 값(설계 6.1.2 SignUpForm, srs.md 6.3 1.2 의사결정표).
 *
 * @param nickname 닉네임, 없으면 서비스가 정한다
 * @param birthYear 출생연도, 없을 수 있음
 * @param gender 성별, 없을 수 있음
 * @param photoUrl 프로필 사진 주소, 없을 수 있음
 * @param intro 프로필 소개, 없을 수 있음
 * @param consents 동의 항목 → 동의 여부
 * @param recordVisibility 가입 단계에서 고른 기록 공개 범위(FR-028)
 * @param tags 성향 태그(비었으면 고르지 않음)
 */
public record SignUpForm(
        String nickname,
        Integer birthYear,
        Gender gender,
        String photoUrl,
        String intro,
        Map<ConsentItem, Boolean> consents,
        RecordVisibility recordVisibility,
        Set<PersonalityTag> tags) {

    private static final int MIN_AGE = 14;
    private static final Set<ConsentItem> REQUIRED_DOCUMENTS =
            EnumSet.of(ConsentItem.TERMS, ConsentItem.PRIVACY, ConsentItem.LOCATION);

    public SignUpForm {
        consents =
                consents == null || consents.isEmpty()
                        ? Map.of()
                        : Collections.unmodifiableMap(new EnumMap<>(consents));
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        if (recordVisibility == null) {
            recordVisibility = RecordVisibility.PUBLIC;
        }
    }

    /** 약관·개인정보·위치 중 동의하지 않은 것. */
    public Set<ConsentItem> missingRequiredConsents() {
        Set<ConsentItem> missing = EnumSet.noneOf(ConsentItem.class);
        for (ConsentItem item : REQUIRED_DOCUMENTS) {
            if (!agreed(item)) {
                missing.add(item);
            }
        }
        return missing;
    }

    /** 만 14세 이상 확인에 동의하지 않았거나 출생연도로 만 14세 미만이면 true(FR-004). */
    public boolean isUnder14(int currentYear) {
        if (!agreed(ConsentItem.AGE_14_PLUS)) {
            return true;
        }
        return birthYear != null && currentYear - birthYear < MIN_AGE;
    }

    /** 의사결정표 규칙 1·2·3 순서로 거부한다. 태그는 비지 않았으면 TagSet 규칙으로 검사한다. */
    public void validate(int currentYear) {
        if (!missingRequiredConsents().isEmpty()) {
            throw new BusinessRuleException(ReasonCode.CONSENT_MISSING);
        }
        if (isUnder14(currentYear)) {
            throw new BusinessRuleException(ReasonCode.UNDER_14);
        }
        if (nickname != null) {
            Nickname.of(nickname);
        }
        tagSet();
    }

    TagSet tagSet() {
        return tags.isEmpty() ? TagSet.empty(TagOwner.RUNNER) : TagSet.of(tags, TagOwner.RUNNER);
    }

    private boolean agreed(ConsentItem item) {
        return Boolean.TRUE.equals(consents.get(item));
    }
}
