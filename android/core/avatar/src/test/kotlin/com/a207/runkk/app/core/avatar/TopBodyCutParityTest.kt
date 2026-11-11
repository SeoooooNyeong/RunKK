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

    @Test
    fun `띠 판정이 한 줄 걸러 지워 남긴 1px 줄도 지운다(파이프라인 close_slits와 같다)`() {
        val skin = 0xFFFAD2C0.toInt()
        val before = IntArray(7 * 7) { i -> if (i % 7 in 1..5 && i / 7 in 1..5) skin else 0 }
        val after = before.copyOf()
        for (x in 1..5) {
            after[2 * 7 + x] = 0 // 빗살
            after[4 * 7 + x] = 0
        }
        TopBodyCut.closeSlits(before, after, 7, 7)
        assertEquals((1..5).map { 0 }, (1..5).map { after[3 * 7 + it] })
        assertEquals((1..5).map { skin }, (1..5).map { after[1 * 7 + it] })

        // 맨몸 안의 1px 줄 틈(위·아래가 두꺼운 맨몸)은 되살린다
        val slit = before.copyOf()
        for (x in 1..5) slit[3 * 7 + x] = 0
        TopBodyCut.closeSlits(before, slit, 7, 7)
        assertEquals((1..5).map { skin }, (1..5).map { slit[3 * 7 + it] })
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
