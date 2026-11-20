package com.a207.runkk.app.api.core

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

/**
 * 화면 첫 응답 사본(S15P31A207-345 2차): 계정별 칸과 공용 칸으로 나눠 남기고 지우지 않는다. 다른 계정은 앞 계정 칸을 보지 못하고, 앞
 * 계정으로 돌아오면 그 칸을 바로 준다. 앱을 새로 켜도(새 인스턴스) 기기에서 읽는다.
 */
class ScreenCopyStoreTest {
    private val dir: File = kotlin.io.path.createTempDirectory("screen-copy").toFile()
    private val json = ApiCoreBindings.json()

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Serializable
    data class Body(val name: String, val mine: Int? = null)

    @Test
    fun `바로 보기는 파일을 읽지 않고 메모리에 올린 사본만 준다(적중 확인·받기 뒤에는 있다)`() {
        ScreenCopyStore(dir, json).write(A, KEY, "WEEKLY", Body.serializer(), Body("a"))

        val reopened = ScreenCopyStore(dir, json)
        assertNull(reopened.peek(A, KEY, Body.serializer()), "새 실행에서 아직 읽지 않은 사본은 기다리지 않고 없다고 한다")
        assertEquals("WEEKLY", reopened.paramOf(A, KEY), "시작 로딩의 적중 확인이 읽는다")
        assertEquals("WEEKLY" to Body("a"), reopened.peek(A, KEY, Body.serializer()))
    }

    @Test
    fun `계정별 칸은 서로 보이지 않고 지우지 않으며 돌아오면 바로 준다`() {
        val store = ScreenCopyStore(dir, json)
        store.write(A, KEY, "WEEKLY", Body.serializer(), Body("a", mine = 3))
        store.write(B, KEY, "WEEKLY", Body.serializer(), Body("b", mine = 9))

        assertEquals("WEEKLY" to Body("a", 3), store.read(A, KEY, Body.serializer()))
        assertEquals("WEEKLY" to Body("b", 9), store.read(B, KEY, Body.serializer()))
        assertNull(store.read(C, KEY, Body.serializer()), "다른 계정 칸은 보이지 않는다")

        val reopened = ScreenCopyStore(dir, json)
        assertEquals(Body("a", 3), reopened.read(A, KEY, Body.serializer())?.second, "새 실행에서도 앞 계정 칸이 남는다")
        assertEquals("WEEKLY", reopened.paramOf(B, KEY))
    }

    @Test
    fun `공용 칸은 계정과 상관없이 같이 쓴다`() {
        val store = ScreenCopyStore(dir, json)
        store.write(null, KEY, "", Body.serializer(), Body("shared"))

        assertEquals(Body("shared"), ScreenCopyStore(dir, json).read(null, KEY, Body.serializer())?.second)
        assertNull(store.read(A, KEY, Body.serializer()), "공용 칸은 계정 칸과 따로다")
    }

    @Test
    fun `풀지 못하는 사본은 없는 것으로 본다`() {
        val store = ScreenCopyStore(dir, json)
        store.write(A, KEY, "x", String.serializer(), "text")
        assertNull(ScreenCopyStore(dir, json).read(A, KEY, Body.serializer()))
    }

    private companion object {
        const val A = 7L
        const val B = 8L
        const val C = 9L
        const val KEY = "ranking-personal-WEEKLY"
    }
}
