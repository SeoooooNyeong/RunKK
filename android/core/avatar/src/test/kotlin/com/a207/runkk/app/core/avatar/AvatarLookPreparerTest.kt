package com.a207.runkk.app.core.avatar

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/**
 * 미리 준비([임시:D-성-104], [임시:D-성-105], [임시:DR-성-10]): `prepareRiveAvatarLook`은 Rive 뷰 없이 그 모습의 서기 합성 텍스처를
 * 프로세스 공용 캐시에 넣는다. 같은 모습이면 두 번째는 합성하지 않는다. 요청은 뷰가 만드는 것과 같은 원본(riv 내장 이미지, 내장 머리,
 * 앞 단계 서기 몸)으로 만든다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AvatarLookPreparerTest {
    private val context = RuntimeEnvironment.getApplication()
    private val assets = RiveAvatarAssets(Dispatchers.IO)
    private val tones = SharedToneCache()
    private val requests = mutableListOf<AvatarToneRequest>()
    private val preparer = AvatarLookPreparer(assets, tones, Dispatchers.Default) { request, cache ->
        requests += request
        AvatarTextureComposer.compose(request, cache)
    }

    @Test
    fun `서기 모습을 합성해 공용 캐시에 넣고 같은 모습이면 다시 합성하지 않는다`() = runBlocking {
        val outfit = AvatarOutfit(top = 2, bottom = 1, shoes = 3)
        preparer.prepare(context, AvatarGender.FEMALE, outfit, AvatarBodyShape.CHANGE_1, 40, null, AvatarMotion.STAND)

        val entry = assertNotNull(tones.entry(AvatarGender.FEMALE, AvatarMotion.STAND))
        assertEquals(AvatarLookKey(outfit, AvatarBodyShape.CHANGE_1, 40), entry.look)
        assertNull(entry.head)
        assertTrue(BodySkinLayers.STANDING_BODY in entry.cache.skinRenders)
        assertEquals(40, entry.cache.face?.amount)
        assertEquals(AvatarBodyShape.CHANGE_1, entry.cache.frontOverlay?.shape)
        assertNull(tones.entry(AvatarGender.FEMALE, AvatarMotion.RUN), "서기만 준비")
        assertEquals(1, requests.size)

        preparer.prepare(context, AvatarGender.FEMALE, outfit, AvatarBodyShape.CHANGE_1, 40, null, AvatarMotion.STAND)
        assertEquals(1, requests.size, "같은 모습이면 합성하지 않는다")
        assertSame(entry, tones.entry(AvatarGender.FEMALE, AvatarMotion.STAND))

        preparer.prepare(context, AvatarGender.FEMALE, outfit, AvatarBodyShape.CHANGE_1, 70, null, AvatarMotion.STAND)
        assertEquals(2, requests.size, "러닝량이 바뀌면 다시 합성")
        assertEquals(70, tones.entry(AvatarGender.FEMALE, AvatarMotion.STAND)?.look?.amount)
        assertSame(
            entry.cache.skinBases[BodySkinLayers.STANDING_BODY],
            tones.entry(AvatarGender.FEMALE, AvatarMotion.STAND)?.cache?.skinBases?.get(BodySkinLayers.STANDING_BODY),
            "같은 머리면 몸 분리 결과는 다시 쓴다"
        )
    }

    @Test
    fun `서기 요청은 뷰와 같은 원본으로 만든다`() = runBlocking {
        val prepared = assets.load(context, AvatarGender.MALE)
        val embedded = prepared.embeddedImages(AvatarMotion.STAND)
        preparer.prepare(
            context,
            AvatarGender.MALE,
            AvatarOutfit(),
            AvatarBodyShape.BASE,
            150,
            null,
            AvatarMotion.STAND
        )

        val request = requests.single()
        assertEquals(100, request.amount, "러닝량은 0..100으로 자른다")
        assertContentEquals(prepared.builtIn.front.getValue("face_layer"), request.face)
        assertContentEquals(prepared.builtIn.front.getValue("hair_layer"), request.hair)
        assertContentEquals(embedded.getValue("fullness_overlay"), request.frontFullness, "체형 0은 riv 내장 볼 덮개")
        assertContentEquals(
            embedded.getValue(BodySkinLayers.STANDING_BODY),
            request.skinAssets[BodySkinLayers.STANDING_BODY]
        )
        assertEquals(setOf(BodySkinLayers.STANDING_BODY), request.skinAssets.keys, "남자 내장 기본 옷 몸·앞 단계 몸은 합성하지 않는다")
        assertNull(request.runFullness)
        assertNull(request.sideLayers)
        assertNull(request.generatedSkinColor)
    }

    @Test
    fun `달리기를 고르면 달리기 원본으로 만들고 체형 1·2는 내장 머리의 옆볼 덮개를 쓴다`() = runBlocking {
        val prepared = assets.load(context, AvatarGender.FEMALE)
        preparer.prepare(
            context,
            AvatarGender.FEMALE,
            AvatarOutfit(),
            AvatarBodyShape.CHANGE_2,
            0,
            null,
            AvatarMotion.RUN
        )

        val request = requests.single()
        assertNull(request.face)
        assertNull(request.frontFullness)
        assertContentEquals(prepared.builtIn.sideFullness.getValue(2), request.runFullness)
        assertSame(prepared.builtIn.sideLayers, request.sideLayers)
        assertEquals(RunAvatarAssetLoader.SLOTS, request.skinAssets.keys)
        assertTrue(tones.entry(AvatarGender.FEMALE, AvatarMotion.RUN) != null, "달리기 자리에 넣는다")
    }

    @Test
    fun `공개 prepareRiveAvatarLook은 프로세스 공용 캐시를 채운다`() = runBlocking {
        prepareRiveAvatarLook(context, AvatarGender.FEMALE, AvatarOutfit(), AvatarBodyShape.BASE, 10, null)
        val entry = assertNotNull(SharedToneCache.shared.entry(AvatarGender.FEMALE, AvatarMotion.STAND))
        assertEquals(AvatarLookKey(AvatarOutfit(), AvatarBodyShape.BASE, 10), entry.look)
    }
}
