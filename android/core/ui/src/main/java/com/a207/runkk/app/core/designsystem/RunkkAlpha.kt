package com.a207.runkk.app.core.designsystem

/**
 * 투명도 토큰. 화면마다 다르던 꺼짐 값(0.3·0.38·0.4)과 유리 표면 값(0.92)을 이름으로 묶는다.
 * `Modifier.alpha(RunkkAlpha.Disabled)`, `RunkkColors.Surface.copy(alpha = RunkkAlpha.Glass)`처럼 쓴다.
 */
object RunkkAlpha {
    /** 꺼진 단추·잠긴 탭(0.4): 아이콘 단추, 하단 탭 잠김, 결과 하단 바 글자 단추. */
    val Disabled: Float = 0.4f

    /** 꺼진 단추 안의 글자·아이콘(0.38, Material 꺼짐 내용): 코치 보내기 단추. */
    val DisabledContent: Float = 0.38f

    /** 더 넘길 수 없는 화살표(0.3): 달·주 넘기기([RunkkPeriodNavigator]). */
    val DisabledFaint: Float = 0.3f

    /** 사진 위 유리 표면(0.92): 아바타 화면 칩·되돌리기 단추. */
    val Glass: Float = 0.92f
}
