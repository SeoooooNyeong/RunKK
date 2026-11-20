package com.a207.runkk.app.core.avatar

import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Rive 파일 가져오기(S15P31A207-335 6회차): 가져오기는 주 스레드 밖(worker)에서 하고 결과는 주 스레드(main)로 돌려준다. 출발 전
 * 미리 가져온 것([RivImports.hold])은 같은 키를 청하는 칸이 다시 가져오지 않고 바로(또는 가져오는 중이면 끝나는 대로) 받는다.
 * 아무도 가져가지 않은 것은 붙잡기를 놓은 뒤 [RivImports.LINGER_MS] 지나 버린다.
 */
class RivImportsTest {
    private val worker = Queue()
    private val prefetcher = Queue()
    private val main = Queue()
    private val delayed = mutableListOf<Pair<Long, Runnable>>()
    private val imported = mutableListOf<String>()
    private val disposed = mutableListOf<Item>()
    private val imports = RivImports<String, Item>(
        import = { key -> Item(key, imported.size).also { imported += key } },
        dispose = { disposed += it },
        worker = worker,
        main = main,
        later = { ms, task -> delayed += ms to task },
        prefetcher = prefetcher
    )

    @Test
    fun `청하면 worker에서 가져와 main에서 돌려준다`() {
        val got = mutableListOf<Item>()
        imports.request("m-run") { got += it.getOrThrow() }

        assertTrue(imported.isEmpty(), "청한 자리(주 스레드)에서 가져오지 않는다")
        runImports()
        assertTrue(got.isEmpty(), "결과는 main으로 넘어가 돌려준다")
        main.runAll()

        assertEquals(listOf("m-run"), imported)
        assertEquals("m-run", got.single().key)
    }

    @Test
    fun `미리 가져와 둔 것은 다시 가져오지 않고 청하는 자리에서 바로 준다`() {
        imports.hold("m-run")
        runImports()
        main.runAll()

        val got = mutableListOf<Item>()
        imports.request("m-run") { got += it.getOrThrow() }

        assertEquals(1, got.size, "준비된 것은 같은 호출 안에서 바로 준다(첫 구성 프레임에 플레이어를 만든다)")
        assertEquals(listOf("m-run"), imported, "다시 가져오지 않는다")
    }

    @Test
    fun `미리 가져오는 중이면 그것을 기다려 받고 두 번 가져오지 않는다`() {
        imports.hold("m-run")
        val got = mutableListOf<Item>()
        imports.request("m-run") { got += it.getOrThrow() }

        runImports()
        main.runAll()

        assertEquals(listOf("m-run"), imported)
        assertEquals(1, got.size)
    }

    @Test
    fun `다른 키는 미리 가져온 것을 가져가지 않는다`() {
        imports.hold("m-run")
        runImports()
        main.runAll()

        val got = mutableListOf<Item>()
        imports.request("m-stand") { got += it.getOrThrow() }
        runImports()
        main.runAll()

        assertEquals(listOf("m-run", "m-stand"), imported)
        assertEquals("m-stand", got.single().key)
    }

    @Test
    fun `붙잡기 둘이면 두 칸이 하나씩 받고 세 번째는 새로 가져온다`() {
        imports.hold("m-run")
        imports.hold("m-run")
        runImports()
        main.runAll()

        val got = mutableListOf<Item>()
        repeat(3) { imports.request("m-run") { got += it.getOrThrow() } }
        runImports()
        main.runAll()

        assertEquals(3, got.size)
        assertEquals(3, got.map { it.serial }.toSet().size, "같은 것을 두 칸에 주지 않는다")
        assertEquals(3, imported.size)
    }

    @Test
    fun `놓은 뒤 아무도 가져가지 않은 것은 잠시 뒤 버리고 가져간 것은 두다`() {
        val taken = imports.hold("m-run")
        val left = imports.hold("f-run")
        runImports()
        main.runAll()
        val got = mutableListOf<Item>()
        imports.request("m-run") { got += it.getOrThrow() }

        taken.release()
        left.release()
        assertTrue(disposed.isEmpty(), "놓자마자 버리지 않는다(달리기 화면이 곧 가져간다)")
        assertTrue(delayed.all { it.first == RivImports.LINGER_MS })
        delayed.forEach { it.second.run() }

        assertEquals(listOf("f-run"), disposed.map { it.key }, "가져간 m-run은 칸이 쓰므로 버리지 않는다")
        assertEquals(1, got.size)
    }

    @Test
    fun `놓은 뒤 늦게 끝난 것도 버린다`() {
        imports.hold("m-run").release()
        delayed.forEach { it.second.run() }
        runImports()
        main.runAll()

        assertEquals(listOf("m-run"), disposed.map { it.key })
        val got = mutableListOf<Item>()
        imports.request("m-run") { got += it.getOrThrow() }
        runImports()
        main.runAll()
        assertEquals(2, imported.size, "버린 것은 주지 않는다")
    }

    @Test
    fun `가져오기 실패는 실패로 돌려준다`() {
        val failing = RivImports<String, Item>(
            import = { error("broken riv") },
            dispose = {},
            worker = worker,
            main = main,
            later = { _, _ -> }
        )
        var failure: Throwable? = null
        failing.request("m-run") { failure = it.exceptionOrNull() }
        runImports()
        main.runAll()

        assertEquals("broken riv", failure?.message)
    }

    @Test
    fun `칸이 청한 가져오기는 미리 가져오기와 다른 줄에서 먼저 돈다`() {
        imports.hold("m-run")
        imports.request("m-stand") {}

        worker.runAll()

        assertEquals(listOf("m-stand"), imported, "카운트다운 VS 칸 같은 지금 그릴 칸이 데우기 뒤에 줄 서지 않는다")
        prefetcher.runAll()
        assertEquals(listOf("m-stand", "m-run"), imported)
    }

    private fun runImports() {
        worker.runAll()
        prefetcher.runAll()
    }

    data class Item(val key: String, val serial: Int)

    private class Queue : Executor {
        private val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) {
            tasks += command
        }

        fun runAll() {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
        }
    }
}
