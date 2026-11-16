package com.a207.runkk.app.core.designsystem

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * 무대 배경 그림 파일 검사([임시:D-성-304], [임시:D-성-308], [임시:NFR-성-8]): 결과 무대([ResultStageLayout])는 모든 무대 그림이
 * 1080×1200이고 트랙 윗선이 아래에서 18%라는 전제로 배경을 놓는다. 낮 그림 26장(도시 22, 단계 3, 기본 무대)과 같은 이름의 밤 그림
 * 26장을 모두 본다(홈 전체 배경용 720×1440 `avatar_backdrop_default`는 무대 그림이 아니다).
 */
@RunWith(RobolectricTestRunner::class)
class ResultStageBackdropFilesTest {
    private val resources = RuntimeEnvironment.getApplication().resources
    private val packageName = RuntimeEnvironment.getApplication().packageName

    private fun idOf(name: String): Int = resources.getIdentifier(name, "drawable", packageName)

    private fun nameOf(id: Int): String = resources.getResourceEntryName(id)

    @Test
    fun `무대 그림은 낮 26장과 밤 26장이 모두 1080×1200이다`() {
        assertEquals(26, DAY.size)
        (DAY + NIGHT).forEach { name ->
            val id = idOf(name)
            assertNotEquals(0, id, "$name 그림이 없다")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resources.openRawResource(id).use { BitmapFactory.decodeStream(it, null, bounds) }
            assertEquals(1080 to 1200, bounds.outWidth to bounds.outHeight, name)
        }
    }

    @Test
    fun `각 그림의 트랙 윗선은 아래에서 18퍼센트다(±1·5퍼센트, 알려진 낮은 트랙 그림은 ±3퍼센트)`() {
        val measured = (DAY + NIGHT).associateWith { trackTopFromBottom(it) }
        val outside = measured.filter { (name, share) ->
            val tolerance = if (name in LOW_TRACK) LOW_TRACK_TOLERANCE else TOLERANCE
            abs(share - ResultStageLayout.TRACK_FROM_BOTTOM) > tolerance + ROUNDING
        }
        assertEquals(emptyMap(), outside, "트랙 윗선이 허용 범위 밖: $measured")
        // 알려진 낮은 트랙 그림이 고쳐지면 목록에서 뺀다(목록이 낡지 않게 한다).
        val healed = LOW_TRACK.filter {
            abs(measured.getValue(it) - ResultStageLayout.TRACK_FROM_BOTTOM) <=
                TOLERANCE + ROUNDING
        }
        assertEquals(emptyList(), healed, "이제 ±1.5% 안이다. LOW_TRACK에서 뺀다")
    }

    @Test
    fun `도시 키 22개와 단계 3개가 모두 무대 그림에 연결된다`() {
        CITY_KEYS.forEach { key ->
            assertEquals("avatar_backdrop_${key.lowercase()}", nameOf(avatarStageBackdropRes(key, isDark = false)))
        }
        STAGES.forEach { stage ->
            assertEquals(
                "avatar_backdrop_${stage.lowercase()}",
                nameOf(avatarStageBackdropRes("LM_99", stage, isDark = false))
            )
        }
        assertEquals("avatar_stage_default", nameOf(avatarStageBackdropRes(null, isDark = false)))
        assertEquals("avatar_stage_default", nameOf(avatarStageBackdropRes("LM_99", isDark = false)))
    }

    @Test
    fun `다크는 모든 키에서 밤 그림이다`() {
        CITY_KEYS.forEach { key ->
            assertEquals("avatar_backdrop_${key.lowercase()}_night", nameOf(avatarStageBackdropRes(key, isDark = true)))
        }
        STAGES.forEach { stage ->
            assertEquals(
                "avatar_backdrop_${stage.lowercase()}_night",
                nameOf(avatarStageBackdropRes("LM_99", stage, isDark = true))
            )
        }
        assertEquals("avatar_stage_default_night", nameOf(avatarStageBackdropRes(null, isDark = true)))
        assertEquals("avatar_stage_default_night", nameOf(avatarStageBackdropRes("LM_99", isDark = true)))
    }

    /**
     * 트랙 윗선의 아래에서부터 비율: 트랙 한가운데 줄([REFERENCE_ROW])의 평균색과 가까운 줄이 위로 어디까지 이어지는지 본다.
     * 트랙과 잔디 사이는 몇 px에 걸쳐 섞이므로 채널 차이 [TRACK_COLOR_DISTANCE] 미만인 줄까지를 트랙으로 본다.
     */
    private fun trackTopFromBottom(name: String): Float {
        val options = BitmapFactory.Options().apply { inScaled = false }
        val bitmap = assertNotNull(BitmapFactory.decodeResource(resources, idOf(name), options), name)
        try {
            assertEquals(1200, bitmap.height, name)
            val reference = rowColor(bitmap, REFERENCE_ROW)
            var top = REFERENCE_ROW
            while (top > 0 && distance(rowColor(bitmap, top - 1), reference) < TRACK_COLOR_DISTANCE) top--
            assertTrue(top < REFERENCE_ROW, "$name: 기준 줄 위가 트랙이 아니다")
            return (bitmap.height - top).toFloat() / bitmap.height
        } finally {
            bitmap.recycle()
        }
    }

    /** 줄 [y]의 평균색(가로 4px마다 한 점). */
    private fun rowColor(bitmap: Bitmap, y: Int): IntArray {
        val pixels = IntArray(bitmap.width).also { bitmap.getPixels(it, 0, bitmap.width, 0, y, bitmap.width, 1) }
        val sum = IntArray(3)
        var count = 0
        for (x in pixels.indices step 4) {
            val p = pixels[x]
            sum[0] += p shr 16 and 0xFF
            sum[1] += p shr 8 and 0xFF
            sum[2] += p and 0xFF
            count++
        }
        return IntArray(3) { sum[it] / count }
    }

    private fun distance(a: IntArray, b: IntArray): Int = (0..2).maxOf { abs(a[it] - b[it]) }

    private companion object {
        val CITY_KEYS = listOf(
            "INCHEON", "CHUNCHEON", "DAEJEON", "JEONJU", "YEOSU", "BUSAN", "JEJU", "BEIJING", "TOKYO", "TAIPEI",
            "HONG_KONG", "HANOI", "BANGKOK", "KUALA_LUMPUR", "HONOLULU", "SYDNEY", "LONDON", "PARIS", "LOS_ANGELES",
            "NEW_YORK", "SANTIAGO", "EARTH_LAP"
        )
        val STAGES = listOf("DOMESTIC", "EAST_ASIA", "INTERCONTINENTAL")
        val DAY = (CITY_KEYS + STAGES).map { "avatar_backdrop_${it.lowercase()}" } + "avatar_stage_default"
        val NIGHT = DAY.map { "${it}_night" }

        /** 1200px 그림에서 아래 100px(트랙 한가운데) 줄. */
        const val REFERENCE_ROW = 1100
        const val TRACK_COLOR_DISTANCE = 40
        const val TOLERANCE = 0.015f
        const val LOW_TRACK_TOLERANCE = 0.03f

        /** 경계값(16.5%, 1002번째 줄)이 Float 반올림으로 밖이 되지 않게 하는 여유(1px = 0.00083보다 작다). */
        const val ROUNDING = 0.0001f

        /**
         * 트랙 윗선이 18%−1.5%보다 낮게 그려진 그림 목록. 2026-11-14에 낮은 그림 8장(국내, 지구 한 바퀴, 대륙 간의 낮과 밤, 동아시아 밤,
         * 기본 무대 밤)을 18%로 다시 맞춰 지금은 비어 있다. 새 그림이 낮게 들어오면 그림을 고치고, 고치기 전까지만 여기에 적는다.
         */
        val LOW_TRACK = emptySet<String>()
    }
}
