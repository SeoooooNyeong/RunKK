package com.a207.runkk.app

import java.io.File
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Android 15 이상 edge-to-edge(Play 권장 조치): 앱 테마가 시스템 막대 색을 지정하지 않는다. 막대 뒤 그림·아이콘 색은 MainActivity의
 * enableEdgeToEdge가 맡는다.
 */
class EdgeToEdgeThemeTest {
    @Test
    fun `테마 두 벌에 상태 막대·내비게이션 막대 색 항목이 없다`() {
        val themes = listOf("src/main/res/values/themes.xml", "src/main/res/values-v31/themes.xml").map(::File)
        themes.forEach { file ->
            assertTrue(file.isFile, "${file.path} 이 있어야 한다")
            val text = file.readText()
            listOf("statusBarColor", "navigationBarColor").forEach { item ->
                assertTrue(item !in text, "${file.path}에 $item 항목이 남아 있다")
            }
        }
    }
}
