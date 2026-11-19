package com.a207.runkk.app.domain.sync

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * 시작 에셋 미리 받기(S15P31A207-345): 진행률 계산, 캐시 적중 건너뜀, 다 받기 전에는 준비 끝이 아님, 실패 자동 재시도, 재시도를 다
 * 실패하면 실패 상태와 「다시 시도」 이어 받기(받은 것은 다시 받지 않음), 로그인 전·후 목록 차이.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AssetPreloaderTest {
    @Test
    fun `진행률은 받은 파일 수 나누기 전체이고 0개면 바로 완료다`() {
        assertEquals(0, PreloadProgress(0, 7).percent)
        assertEquals(42, PreloadProgress(3, 7).percent, "버림: 3/7 = 42.8%")
        assertEquals(100, PreloadProgress(7, 7).percent)
        assertEquals(0.5f, PreloadProgress(2, 4).fraction)
        assertTrue(PreloadProgress(0, 0).isComplete, "받을 것이 없으면 바로 완료")
        assertEquals(100, PreloadProgress(0, 0).percent)
        assertFalse(PreloadProgress(0, 0).hasBar, "받을 것이 없으면 막대를 그리지 않는다")
        assertTrue(PreloadProgress(0, 7).hasBar)
        assertEquals(100, PreloadProgress(9, 7).percent, "넘쳐도 100이다")
    }

    @Test
    fun `캐시에 다 있으면 받지 않고 막대 없이 바로 준비 끝이다`() = runTest {
        val source = FakeSource(member = listOf(HEAD), cached = setOf(HEAD.key))
        val preloader = preloader(source)

        preloader.start(isLoggedIn = true)
        runCurrent()

        val state = assertIs<PreloadState.Ready>(preloader.state.value)
        assertFalse(state.progress.hasBar)
        assertEquals(emptyList(), source.fetched, "받지 않는다")
        assertTrue(source.marks.any { it.startsWith("AssetPreload hit ${HEAD.key}") }, "${source.marks}")
        assertTrue(source.marks.any { it.startsWith("AssetPreload done") }, "${source.marks}")
    }

    @Test
    fun `로그인 전에는 공용 에셋만, 로그인 뒤에는 내 아바타 에셋까지 받는다`() = runTest {
        val before = FakeSource(public = listOf(PUBLIC), member = listOf(HEAD))
        preloader(before).start(isLoggedIn = false)
        advanceUntilIdle()
        assertEquals(listOf(PUBLIC.key), before.fetched)
        assertEquals(0, before.memberAsked, "로그인 전에는 회원 목록을 묻지 않는다")

        val after = FakeSource(public = listOf(PUBLIC), member = listOf(HEAD))
        preloader(after).start(isLoggedIn = true)
        advanceUntilIdle()
        assertEquals(setOf(PUBLIC.key, HEAD.key), after.fetched.toSet())
    }

    @Test
    fun `다 받기 전에는 준비 끝이 아니고 받은 파일마다 막대가 오른다`() = runTest {
        val source = FakeSource(member = listOf(HEAD), fileDelayMillis = 1_000)
        val preloader = preloader(source)

        preloader.start(isLoggedIn = true)
        runCurrent()
        assertEquals(PreloadState.Loading(PreloadProgress(0, 7)), preloader.state.value)
        advanceTimeBy(3_500)
        assertEquals(PreloadState.Loading(PreloadProgress(3, 7)), preloader.state.value)
        // 시간 상한이 없다: 오래 걸려도 다 받을 때까지 준비 끝이 아니다.
        source.fileDelayMillis = 60_000
        advanceTimeBy(120_000)
        assertIs<PreloadState.Loading>(preloader.state.value)
        advanceUntilIdle()
        assertEquals(PreloadState.Ready(PreloadProgress(7, 7)), preloader.state.value)
    }

    @Test
    fun `네트워크 실패는 짧은 간격으로 자동으로 다시 받아 성공하면 준비 끝이다`() = runTest {
        val source = FakeSource(member = listOf(HEAD), failures = mutableListOf(AppError.Network, AppError.Server(503)))
        val preloader = preloader(source)

        preloader.start(isLoggedIn = true)
        advanceUntilIdle()

        assertIs<PreloadState.Ready>(preloader.state.value)
        assertEquals(3, source.fetched.size, "처음 + 자동 재시도 두 번")
        assertEquals(2, source.marks.count { it.startsWith("AssetPreload retry") }, "${source.marks}")
    }

    @Test
    fun `자동 재시도를 다 실패하면 실패 상태이고 다시 시도는 남은 것부터 이어 받는다`() = runTest {
        val first = PreloadAsset("public:a", files = 2)
        val second = PreloadAsset("head:7:job", files = 7)
        val source = FakeSource(public = listOf(first), member = listOf(second), offline = setOf(second.key))
        val preloader = preloader(source)

        preloader.start(isLoggedIn = true)
        advanceUntilIdle()

        val failed = assertIs<PreloadState.Failed>(preloader.state.value)
        assertEquals(AppError.Network, failed.error)
        assertEquals(1 + AssetPreloader.RETRY_DELAYS_MILLIS.size, source.fetched.count { it == second.key })
        assertEquals(PreloadProgress(2 + 3, 9), failed.progress, "받은 것은 막대에 남는다(앞 에셋 2 + 뒤 에셋 3파일)")
        assertTrue(source.marks.any { it.startsWith("AssetPreload failed") }, "${source.marks}")

        // 다시 시도: 끝난 에셋은 다시 받지 않고, 덜 받은 에셋은 이미 얻은 파일 다음부터 받는다.
        source.offline = emptySet()
        val fetchedBefore = source.fetched.size
        preloader.retry()
        assertEquals(PreloadState.Loading(PreloadProgress(5, 9)), preloader.state.value, "막대는 뒤로 가지 않는다")
        advanceUntilIdle()

        assertEquals(PreloadState.Ready(PreloadProgress(9, 9)), preloader.state.value)
        assertEquals(listOf(second.key), source.fetched.drop(fetchedBefore), "끝난 앞 에셋은 다시 받지 않는다")
        assertEquals(7, source.filesServed.getValue(second.key), "파일은 한 번씩만 받는다(3 + 남은 4)")
    }

    @Test
    fun `목록을 얻지 못해도 자동 재시도 뒤 실패 상태가 되고 다시 시도로 이어 간다`() = runTest {
        val source = FakeSource(member = listOf(HEAD), planFailures = 1 + AssetPreloader.RETRY_DELAYS_MILLIS.size)
        val preloader = preloader(source)

        preloader.start(isLoggedIn = true)
        advanceUntilIdle()
        assertIs<PreloadState.Failed>(preloader.state.value)

        preloader.retry()
        advanceUntilIdle()
        assertIs<PreloadState.Ready>(preloader.state.value)
        assertEquals(listOf(HEAD.key), source.fetched)
    }

    @Test
    fun `서버가 거부한 에셋(없는 머리 등)은 다시 받아도 소용없어 건너뛰고 준비를 막지 않는다`() = runTest {
        val source = FakeSource(member = listOf(HEAD), failures = mutableListOf(AppError.Rejected("JOB_NOT_FOUND")))
        val preloader = preloader(source)

        preloader.start(isLoggedIn = true)
        advanceUntilIdle()

        assertEquals(PreloadState.Ready(PreloadProgress(7, 7)), preloader.state.value)
        assertEquals(1, source.fetched.size, "거부는 다시 받지 않는다")
        assertTrue(source.marks.any { it.startsWith("AssetPreload skip") }, "${source.marks}")
    }

    @Test
    fun `목록을 얻다 인증이 만료되면(다시 해도 같음) 그 목록을 건너뛰고 준비를 막지 않는다`() = runTest {
        val source = FakeSource(member = listOf(HEAD), memberError = AppError.Unauthorized)
        val preloader = preloader(source)

        preloader.start(isLoggedIn = true)
        advanceUntilIdle()

        assertEquals(PreloadState.Ready(PreloadProgress.UNKNOWN), preloader.state.value)
        assertEquals(emptyList(), source.fetched)
        assertEquals(1, source.memberAsked, "다시 해도 같은 실패는 다시 묻지 않는다")
        assertTrue(source.marks.any { it.startsWith("AssetPreload skip member") }, "${source.marks}")
    }

    @Test
    fun `같은 로그인 상태로 다시 시작하면(화면 재생성) 처음부터 다시 받지 않는다`() = runTest {
        val source = FakeSource(member = listOf(HEAD))
        val preloader = preloader(source)
        preloader.start(isLoggedIn = true)
        advanceUntilIdle()
        preloader.start(isLoggedIn = true)
        advanceUntilIdle()
        assertEquals(1, source.fetched.size)
    }

    private fun TestScope.preloader(source: FakeSource) = AssetPreloader(source, this)

    /**
     * 가짜 출처. [offline]에 든 에셋은 앞 3파일만 주고 Network로 실패한다(이어 받기 시험). 파일은 받은 만큼 [filesServed]에 남고 다음
     * 받기는 그 다음 파일부터 준다(실제 출처가 받은 층을 메모리·기기에 남기는 것과 같다).
     */
    private class FakeSource(
        private val public: List<PreloadAsset> = emptyList(),
        private val member: List<PreloadAsset> = emptyList(),
        private val cached: Set<String> = emptySet(),
        private val failures: MutableList<AppError> = mutableListOf(),
        var offline: Set<String> = emptySet(),
        var fileDelayMillis: Long = 0,
        private var planFailures: Int = 0,
        private val memberError: AppError? = null
    ) : AssetPreloadSource {
        val fetched = mutableListOf<String>()
        val marks = mutableListOf<String>()
        val filesServed = mutableMapOf<String, Int>()
        var memberAsked = 0
        private val done = mutableSetOf<String>()

        override suspend fun publicAssets(): Outcome<List<PreloadAsset>> = plan(public)

        override suspend fun memberAssets(): Outcome<List<PreloadAsset>> {
            memberAsked++
            memberError?.let { return Outcome.Failure(it) }
            return plan(member)
        }

        private fun plan(list: List<PreloadAsset>): Outcome<List<PreloadAsset>> {
            if (planFailures > 0) {
                planFailures--
                return Outcome.Failure(AppError.Network)
            }
            return Outcome.Success(list)
        }

        override fun isCached(asset: PreloadAsset): Boolean = asset.key in cached || asset.key in done

        override suspend fun fetch(asset: PreloadAsset, onFile: () -> Unit): Outcome<Unit> {
            fetched += asset.key
            failures.removeFirstOrNull()?.let { return Outcome.Failure(it) }
            val served = filesServed[asset.key] ?: 0
            repeat(served) { onFile() }
            val limit = if (asset.key in offline) OFFLINE_FILES else asset.files
            for (file in served until limit) {
                if (fileDelayMillis > 0) kotlinx.coroutines.delay(fileDelayMillis)
                filesServed[asset.key] = file + 1
                onFile()
            }
            if (asset.key in offline) return Outcome.Failure(AppError.Network)
            done += asset.key
            return Outcome.Success(Unit)
        }

        override fun mark(line: String) {
            marks += line
        }
    }

    private companion object {
        val HEAD = PreloadAsset("head:7:job-1", files = 7)
        val PUBLIC = PreloadAsset("public:stage", files = 1)
        const val OFFLINE_FILES = 3
    }
}
