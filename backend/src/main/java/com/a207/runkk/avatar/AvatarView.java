package com.a207.runkk.avatar;

import com.a207.runkk.common.ItemSlot;
import com.a207.runkk.common.ReasonCode;
import java.util.Map;

/**
 * 아바타 정보(자료사전 아바타정보, srs.md 6.3 6.6). 얼굴 키는 Face.renderKey이고 얼굴이 없으면 null과 안내문이다.
 *
 * @param faceKey 얼굴 키, 없으면 null
 * @param guide 얼굴이 없을 때 안내문, 있으면 null
 * @param bodyShape 체형
 * @param skinTone 피부색
 * @param backgroundKey 고른 랜드마크 배경 키, null이면 기본 배경(FR-162, FR-202)
 * @param equipped 부위별 착용 아이템 ID
 * @param balance 리워드 잔액
 * @param canCreateSelfieToday 오늘 셀카로 다시 만들 수 있는가(FR-157)
 * @param rejected 착용 요청에서 거부된 부위와 사유
 */
public record AvatarView(
        String faceKey,
        String guide,
        BodyShape bodyShape,
        SkinTone skinTone,
        String backgroundKey,
        Map<ItemSlot, Long> equipped,
        int balance,
        boolean canCreateSelfieToday,
        Map<ItemSlot, ReasonCode> rejected) {

    public AvatarView {
        equipped = Map.copyOf(equipped);
        rejected = Map.copyOf(rejected);
    }

    AvatarView withSelfieToday(boolean available) {
        return new AvatarView(
                faceKey,
                guide,
                bodyShape,
                skinTone,
                backgroundKey,
                equipped,
                balance,
                available,
                rejected);
    }

    AvatarView withRejected(Map<ItemSlot, ReasonCode> reasons) {
        return new AvatarView(
                faceKey,
                guide,
                bodyShape,
                skinTone,
                backgroundKey,
                equipped,
                balance,
                canCreateSelfieToday,
                reasons);
    }
}
