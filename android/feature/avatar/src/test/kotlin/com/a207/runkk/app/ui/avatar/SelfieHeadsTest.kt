package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.avatar.SelfieHead
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** 셀카 머리 캐시(리뷰 M1): 머리 JSON은 한 번만 받아 두 화면이 나눠 쓰고, 받기 실패만 다시 받는다. */
class SelfieHeadsTest {
    private val repo = FakeAvatars()
    private val heads = headsOf(repo)

    @Test
    fun `셀카 머리는 본문을 한 번만 받아 대체 그림과 머리 레이어를 같은 본문에서 만들고 두 화면이 같은 값을 쓴다`() = runTest {
        repo.headResult = Outcome.Success(SelfieHead(faceLayers))

        val runner = heads.look("head:7:job-1")
        val decorate = heads.look("head:7:job-1")

        assertEquals(listOf("head:head:7:job-1"), repo.calls, "부위 그림(faceLayers)을 따로 받지 않고 머리도 한 번")
        assertEquals(faceLayers, runner.faceLayers)
        assertSame(runner, decorate, "렌더러는 참조로 비교하므로 같은 값을 준다")
    }

    @Test
    fun `형식 실패는 다시 받지 않고 기본 머리로 둔다`() = runTest {
        repo.headResult = Outcome.Success(SelfieHead(faceLayers, List(3) { byteArrayOf(1) }, "not json"))

        val first = heads.look("head:7:job-1")
        heads.look("head:7:job-1")

        assertNull(first.headLayers)
        assertEquals(1, repo.calls.count { it.startsWith("head:") })
    }

    @Test
    fun `받기 실패는 두지 않고 다음에 다시 받는다`() = runTest {
        repo.headResult = Outcome.Failure(AppError.Network)
        assertNull(heads.look("head:7:job-1").headLayers)
        repo.headResult = Outcome.Success(SelfieHead(faceLayers))
        assertEquals(faceLayers, heads.look("head:7:job-1").faceLayers)
        assertEquals(2, repo.calls.count { it.startsWith("head:") })
    }

    @Test
    fun `셀카 부위 얼굴은 부위 그림을 받아 두고, 얼굴이 없으면 받지 않는다`() = runTest {
        assertTrue(heads.look(null).faceLayers.isEmpty())
        heads.look("selfie:face/7/s1")
        heads.look("selfie:face/7/s1")
        assertEquals(listOf("face:selfie:face/7/s1"), repo.calls)
    }

    @Test
    fun `최근 얼굴 몇 개만 두고 오래된 것은 다시 받는다`() = runTest {
        repo.headResult = Outcome.Success(SelfieHead(faceLayers))
        listOf("a", "b", "c", "d", "e", "f", "g").forEach { heads.look("head:7:$it") }
        heads.look("head:7:a")
        assertEquals(2, repo.calls.count { it == "head:head:7:a" })
    }

    @Test
    fun `내 얼굴과 랭킹 시상대 세 러너, 막 바꾼 얼굴까지는 다시 받지 않는다(S15P31A207-300)`() = runTest {
        repo.headResult = Outcome.Success(SelfieHead(faceLayers))
        val keys = listOf("head:7:me", "head:1:a", "head:2:b", "head:3:c", "head:7:new")
        keys.forEach { heads.look(it) }
        keys.forEach { heads.look(it) }
        assertEquals(keys.map { "head:$it" }, repo.calls)
    }

    @Test
    fun `서로 다른 얼굴은 한 머리를 받는 동안에도 함께 받는다(AR-89)`() = runTest {
        repo.headResult = Outcome.Success(SelfieHead(faceLayers))
        val slow = CompletableDeferred<Unit>()
        repo.headGates["head:1:a"] = slow

        val first = async { heads.look("head:1:a") }
        val second = async { heads.look("head:2:b") }
        runCurrent()

        assertEquals(listOf("head:head:1:a", "head:head:2:b"), repo.calls, "첫 머리가 막혀도 둘째를 받기 시작한다")
        assertTrue(second.isCompleted, "둘째는 첫째를 기다리지 않는다")
        assertTrue(!first.isCompleted)
        slow.complete(Unit)
        assertEquals(faceLayers, first.await().faceLayers)
    }

    @Test
    fun `같은 얼굴을 동시에 찾으면 한 번만 받고 기다린 쪽도 같은 값을 쓴다(AR-89)`() = runTest {
        repo.headResult = Outcome.Success(SelfieHead(faceLayers))
        val gate = CompletableDeferred<Unit>()
        repo.headGates["head:1:a"] = gate

        val runner = async { heads.look("head:1:a") }
        val podium = async { heads.look("head:1:a") }
        runCurrent()
        gate.complete(Unit)

        assertSame(runner.await(), podium.await())
        assertEquals(listOf("head:head:1:a"), repo.calls)
    }

    @Test
    fun `동시에 기다리던 받기가 실패하면 둘 다 기본 얼굴이고 다음에 다시 받는다(AR-89)`() = runTest {
        repo.headResult = Outcome.Failure(AppError.Network)
        val gate = CompletableDeferred<Unit>()
        repo.headGates["head:1:a"] = gate

        val one = async { heads.look("head:1:a") }
        val two = async { heads.look("head:1:a") }
        runCurrent()
        gate.complete(Unit)

        assertSame(FaceLook.NONE, one.await())
        assertSame(FaceLook.NONE, two.await())
        repo.headResult = Outcome.Success(SelfieHead(faceLayers))
        assertEquals(faceLayers, heads.look("head:1:a").faceLayers)
        assertEquals(2, repo.calls.size)
    }
}
