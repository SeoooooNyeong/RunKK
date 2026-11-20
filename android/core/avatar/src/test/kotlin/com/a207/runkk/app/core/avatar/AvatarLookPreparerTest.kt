package com.a207.runkk.app.core.avatar

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
 * 미리 준비(D-736, D-737, DR-10): `prepareRiveAvatarLook`은 Rive 뷰 없이 그 모습의 서기 합성 텍스처를
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
    fun `기기 사본이 있으면 새 프로세스에서도 합성기를 다시 부르지 않고 공용 캐시에 올린다`() = runBlocking {
        val disk = AvatarLookDisk.of(dir("m7"), STAMP, keep = 1)
        val outfit = AvatarOutfit(top = 1)
        preparer.prepare(context, AvatarGender.FEMALE, outfit, AvatarBodyShape.BASE, 30, null, AvatarMotion.STAND, disk)
        assertEquals(1, requests.size)
        val first = assertNotNull(tones.hit(AvatarGender.FEMALE, setOf(AvatarMotion.STAND), null, lookOf(outfit, 30)))

        // 새 프로세스 흉내: 빈 공용 캐시, 새 합성기
        val nextTones = SharedToneCache()
        val nextRequests = mutableListOf<AvatarToneRequest>()
        val next =
            AvatarLookPreparer(RiveAvatarAssets(Dispatchers.IO), nextTones, Dispatchers.Default) { request, cache ->
                nextRequests += request
                AvatarTextureComposer.compose(request, cache)
            }
        next.prepare(context, AvatarGender.FEMALE, outfit, AvatarBodyShape.BASE, 30, null, AvatarMotion.STAND, disk)

        assertTrue(nextRequests.isEmpty(), "같은 입력이면 합성하지 않는다")
        val restored = assertNotNull(
            nextTones.hit(AvatarGender.FEMALE, setOf(AvatarMotion.STAND), null, lookOf(outfit, 30)),
            "홈 칸이 찾는 열쇠(성별·동작·머리·모습)로 적중한다"
        )
        assertContentEquals(first.face?.bytes, restored.face?.bytes)
        assertContentEquals(
            first.skinRenders.getValue(BodySkinLayers.STANDING_BODY).bytes,
            restored.skinRenders.getValue(BodySkinLayers.STANDING_BODY).bytes
        )
        assertEquals(first.frontOverlay?.shape, restored.frontOverlay?.shape)
    }

    @Test
    fun `입력이 바뀌면 다시 합성해 새 사본을 남긴 뒤에만 옛 사본을 지운다`() = runBlocking {
        val dir = dir("m7")
        val disk = AvatarLookDisk.of(dir, STAMP, keep = 1)
        val old = assertNotNull(disk.toneKey(AvatarGender.MALE, AvatarMotion.STAND, lookOf(AvatarOutfit(), 10), null))
        val new = assertNotNull(disk.toneKey(AvatarGender.MALE, AvatarMotion.STAND, lookOf(AvatarOutfit(), 60), null))
        fun has(key: String) = dir.listFiles().orEmpty().any { it.name.endsWith("$key.bin") }

        preparer.prepare(
            context,
            AvatarGender.MALE,
            AvatarOutfit(),
            AvatarBodyShape.BASE,
            10,
            null,
            AvatarMotion.STAND,
            disk
        )
        assertTrue(has(old))

        // 새 사본을 남기지 못하면(쓰기 실패) 옛 사본은 그대로다
        File(dir, "tone-MALE-STAND-$new.bin.tmp").mkdirs()
        preparer.prepare(
            context,
            AvatarGender.MALE,
            AvatarOutfit(),
            AvatarBodyShape.BASE,
            60,
            null,
            AvatarMotion.STAND,
            disk
        )
        assertEquals(2, requests.size, "러닝량이 바뀌면 다시 합성")
        assertTrue(has(old), "새 사본이 없으니 옛 사본을 지우지 않는다")
        assertFalse(has(new))

        File(dir, "tone-MALE-STAND-$new.bin.tmp").delete()
        val again = AvatarLookPreparer(RiveAvatarAssets(Dispatchers.IO), SharedToneCache(), Dispatchers.Default)
        again.prepare(
            context,
            AvatarGender.MALE,
            AvatarOutfit(),
            AvatarBodyShape.BASE,
            60,
            null,
            AvatarMotion.STAND,
            disk
        )
        assertTrue(has(new), "새 사본을 남겼다")
        assertFalse(has(old), "새 사본을 남긴 뒤 옛 사본을 지운다")
    }

    @Test
    fun `앱이 갱신되면(판 표지가 다르면) 기기 사본을 쓰지 않고 다시 합성한다`() = runBlocking {
        val dir = dir("m7")
        preparer.prepare(
            context,
            AvatarGender.FEMALE,
            AvatarOutfit(),
            AvatarBodyShape.BASE,
            0,
            null,
            AvatarMotion.STAND,
            AvatarLookDisk.of(dir, STAMP, keep = 1)
        )
        val fresh =
            AvatarLookPreparer(RiveAvatarAssets(Dispatchers.IO), SharedToneCache(), Dispatchers.Default) {
                    request,
                    cache
                ->
                requests += request
                AvatarTextureComposer.compose(request, cache)
            }
        fresh.prepare(
            context,
            AvatarGender.FEMALE,
            AvatarOutfit(),
            AvatarBodyShape.BASE,
            0,
            null,
            AvatarMotion.STAND,
            AvatarLookDisk.of(dir, "$STAMP-next", keep = 1)
        )
        assertEquals(2, requests.size)
    }

    private fun lookOf(outfit: AvatarOutfit, amount: Int) = AvatarLookKey(outfit, AvatarBodyShape.BASE, amount)

    private fun dir(name: String) = File(context.filesDir, "avatar-look-test/$name").also { it.deleteRecursively() }

    @Test
    fun `공개 prepareRiveAvatarLook은 프로세스 공용 캐시를 채운다`() = runBlocking {
        prepareRiveAvatarLook(context, AvatarGender.FEMALE, AvatarOutfit(), AvatarBodyShape.BASE, 10, null)
        val entry = assertNotNull(SharedToneCache.shared.entry(AvatarGender.FEMALE, AvatarMotion.STAND))
        assertEquals(AvatarLookKey(AvatarOutfit(), AvatarBodyShape.BASE, 10), entry.look)
    }

    private companion object {
        const val STAMP = "test-1"
    }
}
