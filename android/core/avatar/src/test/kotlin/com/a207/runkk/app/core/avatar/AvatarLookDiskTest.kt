package com.a207.runkk.app.core.avatar

import android.graphics.Bitmap
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/**
 * 아바타 합성 결과 기기 칸(S15P31A207-345 9회차): 머리 레이어·톤·정지 그림을 바이트 그대로 남기고 읽는다. 회원마다 칸이 따로라 다른
 * 계정이 앞 계정 사본을 보지 못하고, 다른 계정이 써도(로그아웃·계정 바뀜) 앞 계정 사본은 지우지 않는다. 깨진 사본은 없는 것으로 본다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AvatarLookDiskTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `생성 머리 레이어를 남기고 새로 읽어도 같은 바이트이고 같은 머리 키·층이면 같은 열쇠다`() {
        val disk = AvatarLookDisk.of(dir("head"), STAMP, keep = 1)
        val key = disk.headKey("head:7:job-1", META, LAYERS)
        assertEquals(key, disk.headKey("head:7:job-1", META, LAYERS.map { it.copyOf() }), "같은 입력이면 같은 열쇠")
        assertNotEquals(key, disk.headKey("head:7:job-2", META, LAYERS), "머리 작업이 바뀌면 다른 열쇠")
        assertNotEquals(key, disk.headKey("head:7:job-1", META, LAYERS.reversed()), "층이 바뀌면 다른 열쇠")

        disk.writeHead(key, head())
        val read = assertNotNull(disk.readHead(key))

        assertEquals(SKIN, read.skinColor)
        assertEquals(key, read.sourceKey, "톤 사본이 이 머리를 가르는 열쇠")
        AvatarGender.entries.forEach { gender ->
            val variant = read.variants.getValue(gender)
            assertContentEquals(byteArrayOf(1, gender.ordinal.toByte()), variant.front.getValue("face_layer"))
            assertContentEquals(byteArrayOf(3), variant.sideLayers.eyes)
            assertNull(variant.sideLayers.neckRegistration)
            assertEquals(SKIN, variant.sideLayers.neckUnderlay?.skin, "측면 목 층은 피부색·성별로 다시 만든다")
            assertSame(BodyNeck.side(gender), variant.sideLayers.neckUnderlay?.neck)
            assertContentEquals(byteArrayOf(9), variant.sideFullness.getValue(2))
        }
    }

    @Test
    fun `톤 사본은 머리를 사본 열쇠로 가를 수 없으면 남기지 않는다`() {
        val disk = AvatarLookDisk.of(dir("tone"), STAMP, keep = 1)
        val look = AvatarLookKey(AvatarOutfit(), AvatarBodyShape.BASE, 0)
        assertNotNull(disk.toneKey(AvatarGender.MALE, AvatarMotion.STAND, look, null), "내장 머리")
        assertNotNull(disk.toneKey(AvatarGender.MALE, AvatarMotion.STAND, look, head(sourceKey = "k")))
        assertNull(disk.toneKey(AvatarGender.MALE, AvatarMotion.STAND, look, head(sourceKey = null)), "사본 없는 생성 머리")
        assertNotEquals(
            disk.toneKey(AvatarGender.MALE, AvatarMotion.STAND, look, head(sourceKey = "a")),
            disk.toneKey(AvatarGender.MALE, AvatarMotion.STAND, look, head(sourceKey = "b")),
            "머리가 다르면 다른 열쇠(셀카를 다시 찍으면 다시 합성)"
        )
    }

    @Test
    fun `계정마다 칸이 따로라 서로 보지 못하고 다른 계정이 써도 앞 계정 사본은 남는다`() {
        val a = AvatarLookDisk.member(context, 7)
        val b = AvatarLookDisk.member(context, 8)
        assertNotEquals(a.dir, b.dir)
        assertEquals(File(context.filesDir, "avatar-look/m7"), a.dir)
        assertEquals(File(context.filesDir, "avatar-look/shared"), AvatarLookDisk.shared(context).dir)
        a.dir.deleteRecursively()
        b.dir.deleteRecursively()

        val look = AvatarLookKey(AvatarOutfit(), AvatarBodyShape.BASE, 20)
        val key = assertNotNull(a.toneKey(AvatarGender.FEMALE, AvatarMotion.STAND, look, null))
        a.writeTone(key, AvatarGender.FEMALE, AvatarMotion.STAND, tone())
        assertNull(b.readTone(key, AvatarGender.FEMALE, AvatarMotion.STAND), "다른 계정 칸에는 없다")

        // 다른 계정(로그아웃 뒤 로그인)이 자기 칸에 같은 묶음을 써도 앞 계정 칸은 그대로다
        b.writeTone(key, AvatarGender.FEMALE, AvatarMotion.STAND, tone())
        val back = assertNotNull(a.readTone(key, AvatarGender.FEMALE, AvatarMotion.STAND), "앞 계정으로 돌아오면 그대로 있다")
        assertContentEquals(byteArrayOf(5, 5), back.face?.bytes)
        assertContentEquals(byteArrayOf(6), back.face?.hair)
        assertEquals(SkinRenderKey(20, top = 1, shape = 2), back.skinRenders.getValue("standing_body").key)
        assertEquals(AvatarBodyShape.CHANGE_1, back.frontOverlay?.shape)
        assertNull(back.runOverlay)
    }

    @Test
    fun `깨진 사본은 없는 것으로 보고 지운다`() {
        val dir = dir("broken").also { it.mkdirs() }
        val disk = AvatarLookDisk.of(dir, STAMP, keep = 1)
        val file = File(dir, "head-abc.bin").also { it.writeBytes(byteArrayOf(1, 2, 3)) }
        assertNull(disk.readHead("abc"))
        assertFalse(file.exists())
    }

    @Test
    fun `정지 그림 사본은 모습과 칸 크기로 남고 공용 칸에서 읽어 캐시에 올린다`() {
        val disk = AvatarLookDisk.of(dir("still"), STAMP, keep = 4)
        val look = RiveAvatarLook(AvatarGender.MALE, AvatarOutfit(top = 2), AvatarBodyShape.BASE, 30, null)
        val key = assertNotNull(disk.stillKey(look))
        assertNull(disk.stillKey(look.copy(headLayers = head(sourceKey = null))), "사본 없는 생성 머리는 남기지 않는다")
        val still = Bitmap.createBitmap(12, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF336699.toInt()) }
        disk.writeStill(key, 120, 200, still)

        assertEquals(1, AvatarStillDisk.load(disk, look))
        val cached = assertNotNull(AvatarStillCache.get(AvatarStillKey.of(look, 120, 200)), "같은 칸 크기로 적중")
        assertEquals(12, cached.width)
        assertTrue(disk.readStills(assertNotNull(disk.stillKey(look.copy(runningAmount = 31)))).isEmpty())
    }

    private fun tone() = AvatarToneCache(
        skinBases = mapOf("standing_body" to byteArrayOf(4)),
        skinRenders = mapOf(
            "standing_body" to AvatarToneCache.RenderedSkin(SkinRenderKey(20, top = 1, shape = 2), byteArrayOf(7, 7))
        ),
        face = AvatarToneCache.RenderedFace(20, byteArrayOf(5, 5), byteArrayOf(6)),
        frontOverlay = AvatarToneCache.RenderedOverlay(AvatarBodyShape.CHANGE_1, 20, byteArrayOf(8))
    )

    private fun head(sourceKey: String? = "k") = GeneratedHeadLayers(
        AvatarGender.entries.associateWith { gender ->
            AvatarVariantAssets(
                front = mapOf("face_layer" to byteArrayOf(1, gender.ordinal.toByte()), "hair_layer" to byteArrayOf(2)),
                sideLayers = SideHeadLayers(
                    byteArrayOf(2),
                    byteArrayOf(3),
                    byteArrayOf(4),
                    neckUnderlay = NeckUnderlay(BodyNeck.side(gender), SKIN)
                ),
                frontFullness = mapOf(1 to byteArrayOf(5), 2 to byteArrayOf(6)),
                sideFullness = mapOf(1 to byteArrayOf(8), 2 to byteArrayOf(9))
            )
        },
        SKIN,
        sourceKey
    )

    private fun dir(name: String) = File(context.filesDir, "avatar-look-disk-test/$name").also {
        it.deleteRecursively()
    }

    private companion object {
        const val STAMP = "test-1"
        const val SKIN = 0xFFC08060.toInt()
        const val META = """{"mobile":{}}"""
        val LAYERS = List(6) { byteArrayOf(it.toByte(), 1) }
    }
}
