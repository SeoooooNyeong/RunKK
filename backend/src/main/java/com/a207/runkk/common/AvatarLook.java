package com.a207.runkk.common;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 전신 아바타를 그리는 값 묶음(설계 5.31 B, [임시:D-성-246]). 러닝 기록에 그 시점 모습으로 남고(running), 대결 상대·기록 대결 후보·크루런 대기실
 * 참가자 응답에 실린다(battle, web.battle, web.crew). 항목 모양은 아바타 응답의 equipped, 내 러너 응답의
 * bodyVariant·runAmount와 같다. JSON 항목 이름은 구성요소 이름이다.
 *
 * @param gender 캐릭터 성별 이름("MALE" | "FEMALE")
 * @param equipped 부위별 착용 아이템 ID(TOP·BOTTOM·SHOES만). 안 입은 부위는 키가 없다
 * @param bodyVariant 3단계 체형(0 기본, 1 변화 1, 2 변화 2)
 * @param runAmount 피부톤 입력 러닝량 0~100
 * @param faceKey 얼굴 키(Face.renderKey), 얼굴을 고르지 않았으면 null
 */
public record AvatarLook(
        String gender,
        Map<ItemSlot, Long> equipped,
        int bodyVariant,
        int runAmount,
        String faceKey) {

    public AvatarLook {
        Map<ItemSlot, Long> ordered = new EnumMap<>(ItemSlot.class);
        ordered.putAll(equipped);
        equipped = Collections.unmodifiableMap(ordered);
    }

    /** 성별만 바꾼 사본(대결에 저장된 표시 성별로 덮어쓸 때). */
    public AvatarLook withGender(String g) {
        return new AvatarLook(g, equipped, bodyVariant, runAmount, faceKey);
    }
}
