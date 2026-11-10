package com.a207.runkk.app.core.avatar

import android.graphics.BitmapFactory
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * 상의 자르기 앱([TopBodyCut])과 파이프라인(`tools/outfit-pipeline/outfit_pipeline/cut.py`, 검수 렌더)이 같은 입력에 같은 결과를 낸다.
 * 픽스처 src/test/resources/topcut은 `tests/make_topcut_fixture.py`가 cut.py로 만들고, 파이프라인 pytest도 같은 파일과 비교한다.
 * 몸통(255) 띠, 덮인 몫이 60% 이하인 팔 띠(200, 자르지 않음), 모두 지우는 칸(240)이 함께 들어 있다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TopBodyCutParityTest {
    private val root = File("src/test/resources/topcut")

    @Test
    fun `앱 상의 자르기는 파이프라인 cut_py와 같은 픽셀을 지운다`() {
        val body = pixels("body")
        val top = pixels("top")
        val area = pixels("area")
        val expected = pixels("expected")
        TopBodyCut.apply(body.first, top.first, area.first, body.second, body.third)
        val differs = body.first.indices.count { (body.first[it] ushr 24 != 0) != (expected.first[it] ushr 24 != 0) }
        assertEquals(0, differs, "남은 맨몸 픽셀이 cut.py와 다르다")
    }

    private fun pixels(name: String): Triple<IntArray, Int, Int> {
        val bitmap = BitmapFactory.decodeFile(File(root, "$name.png").path)
        val width = bitmap.width
        val height = bitmap.height
        return Triple(
            IntArray(width * height).also {
                bitmap.getPixels(it, 0, width, 0, 0, width, height)
            },
            width,
            height
        )
            .also { bitmap.recycle() }
    }
}
