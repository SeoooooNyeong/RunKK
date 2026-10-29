package com.a207.runkk.app

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.navigation.Routes
import kotlin.test.assertEquals
import org.junit.Test

/** 백 스택 이동기: 평가 결과의 "유형 보기"처럼 맨 위 화면을 바꾸는 이동(리뷰 지적: back 뒤 navigate는 화면 하나일 때 앱을 닫는다). */
class BackStackNavigatorTest {
    private var exits = 0

    @Test
    fun `replace는 맨 위 화면만 바꾸고 아래 화면은 그대로 둔다`() {
        val stack = NavBackStack<NavKey>(Routes.Key(Routes.HOME), Routes.Evaluation("GOLD", 20, "COMMUTER_TURTLE"))
        BackStackNavigator(stack, onExit = { exits++ }).replace(Routes.RunimalType("COMMUTER_TURTLE"))

        assertEquals(listOf(Routes.Key(Routes.HOME), Routes.RunimalType("COMMUTER_TURTLE")), stack.toList())
        assertEquals(0, exits)
    }

    @Test
    fun `평가 화면 하나뿐이어도 replace는 앱을 닫지 않고 유형 상세를 연다`() {
        val stack = NavBackStack<NavKey>(Routes.Evaluation("SILVER", 3, "COMMUTER_TURTLE"))
        BackStackNavigator(stack, onExit = { exits++ }).replace(Routes.RunimalType("COMMUTER_TURTLE"))

        assertEquals(listOf<NavKey>(Routes.RunimalType("COMMUTER_TURTLE")), stack.toList())
        assertEquals(0, exits)
    }
}
