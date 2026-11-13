package com.a207.runkk.app.core.designsystem

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * 프로필 얼굴 그림은 그릴 크기에 맞춰 줄여 읽는다(랭킹·크루 목록 최적화): 목록의 작은 얼굴은 원본(셀카 1024, 기본 머리 512) 대신 화면
 * 크기 바로 위의 2의 거듭제곱 배로 줄인 비트맵을 쓰고, 같은 그림·같은 단계는 한 번만 읽어 함께 쓴다. 줄인 그림 1px이 화면 1px을 넘지
 * 않아 선명함은 같다.
 */
@RunWith(RobolectricTestRunner::class)
class PortraitBitmapSizeTest {
    private val resources = RuntimeEnvironment.getApplication().resources

    @Test
    fun `기본 머리 줄일 배수는 화면 크기 이상을 지키는 가장 큰 2의 거듭제곱이다`() {
        assertEquals(4, DefaultHeadBitmaps.sampleFor(512, 120), "40dp(3배 밀도) 목록 얼굴 → 128px")
        assertEquals(2, DefaultHeadBitmaps.sampleFor(512, 216), "72dp 마이 얼굴 → 256px")
        assertEquals(1, DefaultHeadBitmaps.sampleFor(512, 400))
        assertEquals(8, DefaultHeadBitmaps.sampleFor(512, 30))
    }

    @Test
    fun `목록 크기 기본 머리는 줄여 읽고 같은 크기 단계면 같은 비트맵을 함께 쓴다`() {
        val id = RunkkAvatarArt.defaultHeadOf(AvatarCharacter.MALE)
        val small = assertNotNull(DefaultHeadBitmaps.of(resources, id, 120))
        assertEquals(128, small.width)
        assertSame(small, DefaultHeadBitmaps.of(resources, id, 110), "같은 단계")
        val large = assertNotNull(DefaultHeadBitmaps.of(resources, id, 600))
        assertEquals(512, large.width, "크게 그리면 원본")
    }

    @Test
    fun `셀카 머리는 그릴 크기에 맞춰 줄여 읽고 구도도 그 크기로 옮긴다`() {
        val layers = SelfieHeadFixture.layers
        val frames = assertNotNull(SelfieHeadArt.framesOf(layers))
        // 픽스처 머리는 캔버스 가운데에 작게 있어 크게 늘려 그리므로 목록 크기보다 작은 칸(60px)에서 4배로 줄인다.
        val small = assertNotNull(SelfieHeadArt.of(layers, 60))
        val large = assertNotNull(SelfieHeadArt.of(layers, 2000))
        assertTrue(small.layers.all { it.width <= 256 }, "목록 얼굴은 256px 이하: ${small.layers.map { it.width }}")
        assertEquals(frames.width, large.layers.first().width, "크게 그리면 원본")
        assertSame(small, SelfieHeadArt.of(layers, 58), "같은 크기 단계는 다시 읽지 않는다")
        val full = assertNotNull(frames.frame)
        val scaled = assertNotNull(small.frame)
        val factor = frames.width.toFloat() / small.layers.first().width
        // 줄인 그림의 같은 점이 상자의 같은 자리에 놓인다.
        assertEquals(full.mapY(600f), scaled.mapY(600f / factor), 1e-3f)
        assertEquals(full.mapX(512f), scaled.mapX(512f / factor), 1e-3f)
    }
}
