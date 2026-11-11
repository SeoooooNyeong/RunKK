package com.a207.runkk.app.core.avatar

import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** 리뷰 M2: Rive 파일·내장 머리·앞 단계 서기 몸 바이트를 주 스레드가 아닌 IO에서 남녀마다 한 번 읽어 프로세스에 둔다. */
@RunWith(RobolectricTestRunner::class)
class RiveAvatarAssetsTest {
    private val context = RuntimeEnvironment.getApplication()
    private val io = CountingDispatcher()
    private val assets = RiveAvatarAssets(io)

    @Test
    fun `남녀 자산을 주입한 IO에서 한 번 읽어 두고 다음에는 읽지 않는다`() = runBlocking {
        assertNull(assets.cached(AvatarGender.MALE), "읽기 전에는 없다")

        val male = assets.load(context, AvatarGender.MALE)
        val reads = io.dispatches.get()
        assertTrue(reads > 0, "IO 디스패처에서 읽는다")

        assertSame(male, assets.load(context, AvatarGender.MALE))
        assertSame(male, assets.cached(AvatarGender.MALE))
        assertEquals(reads, io.dispatches.get(), "두 번째는 읽지 않는다")
        assertNull(assets.cached(AvatarGender.FEMALE), "남녀를 따로 둔다")
    }

    @Test
    fun `준비한 바이트는 4개 매핑 Rive 파일(검수 바탕·내장 그림자 투명, 달리기 엉덩이 잇기 칸 옮김), 내장 머리 12장, 앞 단계 서기 몸이다`() = runBlocking {
        for (gender in AvatarGender.entries) {
            val prepared = assets.load(context, gender)
            assertEquals(gender, prepared.gender)
            for (motion in AvatarMotion.entries) {
                assertContentEquals(
                    RivGroundShadow.hidden(
                        RivReviewBackground.hidden(raw(RiveAvatarScene.rawResource(gender, motion)))
                    ).also(RivHipConnection::retargetInPlace),
                    prepared.riv(motion),
                    "$gender $motion"
                )
            }
            assertContentEquals(raw(R.raw.avatar_legacy_standing_body), prepared.legacyStandingBody)
            assertEquals(AvatarVariantAssets.FRONT_SLOTS, prepared.builtIn.front.keys)
        }
    }

    @Test
    fun `내장 이미지는 동작마다 처음 물을 때 한 번 읽어 둔다`() = runBlocking {
        val prepared = assets.load(context, AvatarGender.FEMALE)
        val stand = prepared.embeddedImages(AvatarMotion.STAND)
        assertSame(stand, prepared.embeddedImages(AvatarMotion.STAND), "두 번째는 같은 목록")
        assertTrue(BodySkinLayers.STANDING_BODY in stand)
        assertTrue("side_head" in prepared.embeddedImages(AvatarMotion.RUN))
    }

    @Test
    fun `공개 preloadRiveAvatar는 공용 자산을 남녀마다 읽어 둔다`() = runBlocking {
        preloadRiveAvatar(context, AvatarGender.MALE)
        assertEquals(AvatarGender.MALE, RiveAvatarAssets.shared.cached(AvatarGender.MALE)?.gender)
    }

    private fun raw(id: Int): ByteArray = context.resources.openRawResource(id).use { it.readBytes() }

    private class CountingDispatcher : CoroutineDispatcher() {
        val dispatches = AtomicInteger()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatches.incrementAndGet()
            Dispatchers.IO.dispatch(context, block)
        }
    }
}
