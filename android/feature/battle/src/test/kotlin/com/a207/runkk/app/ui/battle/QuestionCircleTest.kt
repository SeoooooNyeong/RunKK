package com.a207.runkk.app.ui.battle

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.assertEquals
import org.junit.Test

/** 점선 원 「?」 글자는 원과 같이 dp를 따르고 글자 크기 설정으로 커지지 않는다(PL2-A6). */
class QuestionCircleTest {
    @Test
    fun `물음표 글자는 글자 배율과 상관없이 원 지름의 0_52배 dp다(PL2-A6)`() {
        val normal = Density(density = 2f, fontScale = 1f)
        val large = Density(density = 2f, fontScale = 2f)
        assertEquals(52.sp, questionTextSize(100.dp, normal))
        // 글자 배율 변환은 비선형일 수 있으므로(Android 14 FontScaleConverter) sp 값이 아니라 그린 크기(px)로 본다.
        with(large) { assertEquals(52.dp.toPx(), questionTextSize(100.dp, large).toPx(), 0.5f, "2배 글자에서도 52dp") }
    }
}
