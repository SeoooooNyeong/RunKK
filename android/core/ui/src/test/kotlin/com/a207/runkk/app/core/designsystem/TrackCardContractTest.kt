package com.a207.runkk.app.core.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import org.junit.Test

class TrackCardContractTest {
    @Test
    fun `주자는 크기와 배율과 그림자를 묶어 필수 인자가 네 개다`() {
        val parameters = parametersOf("Runner").filterNot { it == Modifier::class.java }
        assertEquals(4, parameters.size)
        assertEquals("TrackRunnerGeometry", parameters.first().simpleName)
    }

    @Test
    fun `이름표는 표시 내용과 얼굴과 배율의 세 인자다`() {
        val parameters = parametersOf("NameTag")
        assertEquals(3, parameters.size)
        assertEquals("TrackRunnerLabel", parameters.first().simpleName)
    }

    @Test
    fun `발밑 그림자는 내 주자 61x11, 상대 주자 51x9 그대로다`() {
        assertEquals(61.dp to 11.dp, TrackRunnerShadow.MINE.width to TrackRunnerShadow.MINE.height)
        assertEquals(51.dp to 9.dp, TrackRunnerShadow.OTHER.width to TrackRunnerShadow.OTHER.height)
    }

    private fun parametersOf(name: String): List<Class<*>> =
        Class.forName("com.a207.runkk.app.core.designsystem.RunkkTrackCardKt")
            .declaredMethods.single { it.name == name || it.name.startsWith("$name-") }
            .parameterTypes.filterNot { it.name == "androidx.compose.runtime.Composer" || it == Integer.TYPE }
}
