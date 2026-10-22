package com.a207.runkk.avatar;

/**
 * 아바타 얼굴 결과(자료사전 아바타얼굴결과: 생성완료 | 변환실패 + 실패사유). 거부(축소 운영·미동의·하루 한도·진행 중)는 BusinessRuleException이다.
 *
 * @param created 얼굴을 만들었는가
 * @param failure 변환 실패 사유, 성공이면 null. 실패면 기본 캐릭터를 고르도록 안내한다(FR-155)
 * @param view 처리 뒤 아바타 정보
 */
public record AvatarResult(boolean created, FailureReason failure, AvatarView view) {

    static AvatarResult created(AvatarView view) {
        return new AvatarResult(true, null, view);
    }

    static AvatarResult failed(FailureReason reason, AvatarView view) {
        return new AvatarResult(false, reason, view);
    }
}
