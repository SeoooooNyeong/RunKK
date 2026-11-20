package com.a207.runkk.app

import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.sync.PreloadAsset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * 대결 카운트다운 데우기 출처(S15P31A207-335 9회차): 시작 로딩이 로그인한 회원에게 카운트다운 첫 판의 클래스·글꼴을 한 번 미리 읽게 한다.
 * 앞당기기라 실패해도 준비를 막지 않고, 프로세스마다 한 번 한다.
 */
class CountdownWarmupSourceTest {
    @Test
    fun `로그인한 회원에게만 앞당기기 에셋 하나를 내고 데운 뒤에는 적중이다`() = runTest {
        var warmed = 0
        val source = CountdownWarmupSource { warmed++ }
        assertEquals(emptyList(), assertIs<Outcome.Success<List<PreloadAsset>>>(source.publicAssets()).value)
        val asset = assertIs<Outcome.Success<List<PreloadAsset>>>(source.memberAssets()).value.single()
        assertTrue(asset.isOptional, "앞당기기라 실패해도 준비를 막지 않는다")
        assertFalse(source.isCached(asset), "프로세스를 새로 띄우면 다시 데운다")

        var files = 0
        assertIs<Outcome.Success<Unit>>(source.fetch(asset) { files++ })
        assertEquals(1, warmed)
        assertEquals(asset.files, files, "진행 막대 칸을 채운다")
        assertTrue(source.isCached(asset))
    }

    @Test
    fun `미리 읽을 클래스 이름은 모두 있다(이름이 바뀌면 시험이 알린다)`() {
        val loader = javaClass.classLoader
        val missing = CountdownWarmupSource.CLASSES.filter { name ->
            runCatching { Class.forName(name, false, loader) }.isFailure
        }
        assertEquals(emptyList(), missing)
    }
}
