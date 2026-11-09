package com.a207.runkk.app.core.avatar

/**
 * Rive 몸체 체형 3단계(기본, 변화 1, 변화 2). Rive 입력 body_shape 값은 [stage]×50(0, 50, 100)이다.
 * 얼굴 원본은 바꾸지 않고 레이어·메시로만 바뀐다. 서버 체형(FR-163, BodyShape 1~4단계)을 이 세 단계로 옮기는 대응은 화면(W8)이 정한다.
 */
enum class AvatarBodyShape(val stage: Int) {
    BASE(0),
    CHANGE_1(1),
    CHANGE_2(2)
}
