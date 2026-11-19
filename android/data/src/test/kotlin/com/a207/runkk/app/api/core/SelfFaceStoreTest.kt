package com.a207.runkk.app.api.core

import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.FacePart
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SelfFaceStore 형식 판(S15P31A207-345): 남긴 내 머리는 남길 때의 앱 형식 판과 함께 둔다. 판이 바뀌면(앱 갱신) 남긴 것을 내주지
 * 않아 다시 받고, 새로 받은 것은 같은 얼굴 키여도 덮어쓴다. 판이 없는 옛 사본도 다시 받는다. [SelfFaceStore.hasHead]는 파일을 읽지
 * 않고 다 남았는지만 본다(시작 미리 받기의 캐시 적중 판단).
 */
class SelfFaceStoreTest {
    private val dir: File = kotlin.io.path.createTempDirectory("my-face").toFile()

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `같은 형식 판이면 남긴 머리를 주고 다 남았다고 본다`() {
        SelfFaceStore(dir, format = "v3").saveHead(MEMBER, KEY, head(1))

        val restarted = SelfFaceStore(dir, format = "v3")
        assertTrue(restarted.hasHead(MEMBER, KEY))
        assertNotNull(restarted.head(MEMBER, KEY))
        assertFalse(restarted.hasHead(OTHER, KEY), "다른 회원 것은 없는 것")
        assertFalse(restarted.hasHead(MEMBER, "head:12:job-2"), "다른 얼굴은 없는 것")
    }

    @Test
    fun `형식 판이 바뀌면 남긴 것을 내주지 않고 같은 키로 다시 받은 것을 덮어쓴다`() {
        SelfFaceStore(dir, format = "v3").saveHead(MEMBER, KEY, head(1))

        val upgraded = SelfFaceStore(dir, format = "v4")
        assertFalse(upgraded.hasHead(MEMBER, KEY))
        assertNull(upgraded.head(MEMBER, KEY))
        assertNull(upgraded.layers(MEMBER, KEY), "정면 층도 옛 판이면 쓰지 않는다")

        upgraded.saveHead(MEMBER, KEY, head(9))
        val again = SelfFaceStore(dir, format = "v4")
        assertTrue(again.hasHead(MEMBER, KEY))
        assertEquals(listOf<Byte>(9, 9, 9), again.head(MEMBER, KEY)?.faceLayers?.map { it.png.single() })
    }

    @Test
    fun `정면만 새 판으로 다시 남기면 옛 판 옆모습은 짝으로 보지 않는다`() {
        SelfFaceStore(dir, format = "v3").saveHead(MEMBER, KEY, head(1))
        val upgraded = SelfFaceStore(dir, format = "v4")
        upgraded.saveLayers(MEMBER, KEY, head(5).faceLayers)

        assertEquals(listOf<Byte>(5, 5, 5), upgraded.layers(MEMBER, KEY)?.map { it.png.single() })
        assertNull(upgraded.head(MEMBER, KEY), "옆모습·메타는 옛 판이라 다시 받는다")
        assertFalse(upgraded.hasHead(MEMBER, KEY))
    }

    @Test
    fun `형식 판을 적지 않은 옛 사본은 다시 받는다`() {
        SelfFaceStore(dir, format = "v3").saveHead(MEMBER, KEY, head(1))
        val meta = File(dir, "m$MEMBER/meta.properties")
        meta.writeText(meta.readLines().filterNot { it.startsWith("format=") }.joinToString("\n"))

        assertFalse(SelfFaceStore(dir, format = "v3").hasHead(MEMBER, KEY))
    }

    @Test
    fun `계정마다 칸을 나눠 다른 계정이 남겨도 앞 계정 것을 지우지 않고 돌아오면 바로 준다`() {
        val store = SelfFaceStore(dir, format = "v3")
        store.saveKeys(MEMBER, SelfFaceStore.Keys(KEY, "bg-a"))
        store.saveGender(MEMBER, "FEMALE")
        store.saveHead(MEMBER, KEY, head(1))

        store.saveKeys(OTHER, SelfFaceStore.Keys(OTHER_KEY, "bg-b"))
        store.saveGender(OTHER, "MALE")
        store.saveHead(OTHER, OTHER_KEY, head(2))
        assertNull(store.head(OTHER, KEY), "다른 계정은 앞 계정 머리를 받지 않는다")
        assertNull(store.keys(null), "로그아웃이면 아무 칸도 내주지 않는다")

        // 새 실행에서도 칸마다 그대로 남는다.
        val reopened = SelfFaceStore(dir, format = "v3")
        assertEquals(SelfFaceStore.Keys(KEY, "bg-a"), reopened.keys(MEMBER))
        assertEquals("FEMALE", reopened.gender(MEMBER))
        assertTrue(reopened.hasHead(MEMBER, KEY))
        assertEquals(1, reopened.head(MEMBER, KEY)?.faceLayers?.first()?.png?.single()?.toInt())
        assertEquals(SelfFaceStore.Keys(OTHER_KEY, "bg-b"), reopened.keys(OTHER))
        assertEquals(2, reopened.head(OTHER, OTHER_KEY)?.faceLayers?.first()?.png?.single()?.toInt())
    }

    @Test
    fun `예전 한 칸 사본은 그 회원 칸으로 옮겨 다시 받지 않는다`() {
        dir.mkdirs()
        listOf("EARS", "EYES", "HAIR").forEach { File(dir, "$it.png").writeBytes(byteArrayOf(5)) }
        repeat(3) { File(dir, "side$it.png").writeBytes(byteArrayOf(6)) }
        File(dir, "head-meta.json").writeText("""{"format":"v3"}""")
        File(dir, "meta.properties").writeText(
            listOf(
                "member=12",
                "hasKeys=1",
                "faceKey=$KEY",
                "layersKey=$KEY",
                "headKey=$KEY",
                "sideCount=3",
                "format=v3",
                "gender=MALE"
            )
                .joinToString(System.lineSeparator())
        )

        val store = SelfFaceStore(dir, format = "v3")
        assertTrue(store.hasHead(MEMBER, KEY))
        assertEquals(5, store.head(MEMBER, KEY)?.faceLayers?.first()?.png?.single()?.toInt())
        assertEquals("MALE", store.gender(MEMBER))
        assertNull(store.keys(OTHER))
    }

    private fun head(byte: Int) = SelfFaceStore.Head(
        listOf(FacePart.EARS, FacePart.EYES, FacePart.HAIR).map { FaceLayer(it, byteArrayOf(byte.toByte())) },
        List(3) { byteArrayOf(byte.toByte()) },
        """{"format":"v3"}"""
    )

    private companion object {
        const val MEMBER = 12L
        const val OTHER = 99L
        const val KEY = "head:12:job-1"
        const val OTHER_KEY = "head:99:job-5"
    }
}
