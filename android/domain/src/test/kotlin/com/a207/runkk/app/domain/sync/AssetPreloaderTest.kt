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
        assertTrue(source.marks.any { it == "AssetPreload avatar hit ${HEAD.key}" }, "${source.marks}")
        assertTrue(source.marks.any { it.startsWith("AssetPreload all done") }, "${source.marks}")
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
        assertEquals(PreloadProgress(0, 7), assertIs<PreloadState.Loading>(preloader.state.value).progress)
        advanceTimeBy(3_500)
        assertEquals(PreloadProgress(3, 7), assertIs<PreloadState.Loading>(preloader.state.value).progress)
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
        assertEquals(2, source.marks.count { it.startsWith("AssetPreload avatar retry") }, "${source.marks}")
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
        assertTrue(source.marks.any { it.startsWith("AssetPreload all failed") }, "${source.marks}")

        // 다시 시도: 끝난 에셋은 다시 받지 않고, 덜 받은 에셋은 이미 얻은 파일 다음부터 받는다.
        source.offline = emptySet()
        val fetchedBefore = source.fetched.size
        preloader.retry()
        assertEquals(
            PreloadProgress(5, 9),
            assertIs<PreloadState.Loading>(preloader.state.value).progress,
            "막대는 뒤로 가지 않는다"
        )
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
        assertTrue(source.marks.any { it.startsWith("AssetPreload avatar skip") }, "${source.marks}")
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
        assertTrue(source.marks.any { it.startsWith("AssetPreload all skip member") }, "${source.marks}")
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

    @Test
    fun `여러 종류 출처의 파일 수를 합쳐 막대를 내고 단계마다 남은 파일 수를 함께 낸다`() = runTest {
        val avatar =
            FakeSource(member = listOf(HEAD.copy(stage = PreloadStage.MY_AVATAR_FETCH)), fileDelayMillis = 1_000)
        val ranking = FakeSource(member = listOf(RANKING), fileDelayMillis = 10_000)
        val preloader = preloader(avatar, ranking)

        preloader.start(isLoggedIn = true)
        runCurrent()
        val first = assertIs<PreloadState.Loading>(preloader.state.value)
        assertEquals(PreloadProgress(0, 7 + 3), first.progress, "내 머리 7 + 랭킹 3")
        assertEquals(mapOf(PreloadStage.MY_AVATAR_FETCH to 7, PreloadStage.RANKING to 3), first.remaining)

        advanceTimeBy(7_500)
        val later = assertIs<PreloadState.Loading>(preloader.state.value)
        assertEquals(PreloadProgress(7, 10), later.progress)
        assertEquals(mapOf(PreloadStage.MY_AVATAR_FETCH to 0, PreloadStage.RANKING to 3), later.remaining)
        advanceUntilIdle()
        assertEquals(PreloadState.Ready(PreloadProgress(10, 10)), preloader.state.value)
        assertTrue(ranking.marks.isEmpty(), "표식은 기록 포트 하나로 모은다")
        assertTrue(avatar.marks.any { it == "AssetPreload ranking fetch ${RANKING.key}" }, "${avatar.marks}")
    }

    @Test
    fun `한 번에 받는 에셋은 동시 요청 상한(4)을 넘지 않는다`() = runTest {
        val many = List(10) { PreloadAsset("records:$it", files = 1) }
        val source = FakeSource(member = many, fileDelayMillis = 1_000)
        val preloader = preloader(source)

        preloader.start(isLoggedIn = true)
        advanceUntilIdle()

        assertEquals(PreloadState.Ready(PreloadProgress(10, 10)), preloader.state.value)
        assertEquals(AssetPreloader.MAX_PARALLEL, source.maxInFlight, "상한까지 함께 받고 넘지 않는다")
        assertEquals(4, AssetPreloader.MAX_PARALLEL)
    }

    @Test
    fun `앞당기기 에셋은 연결 실패여도 다시 받지 않고 건너뛰어 준비를 막지 않는다`() = runTest {
        // 지난 사본이 있는 기록 첫 화면처럼 앞당기기인 것(홈 아바타 합성은 9회차부터 앞당기기가 아니다)
        val optional = PreloadAsset("records:month", files = 2, stage = PreloadStage.OTHERS, isOptional = true)
        val source = FakeSource(member = listOf(optional), failures = mutableListOf(AppError.Network))
        val preloader = preloader(source)

        preloader.start(isLoggedIn = true)
        advanceUntilIdle()

        assertEquals(PreloadState.Ready(PreloadProgress(2, 2)), preloader.state.value)
        assertEquals(1, source.fetched.size, "자동 재시도 없이 한 번")
        assertTrue(source.marks.any { it.startsWith("AssetPreload records skip records:month") }, "${source.marks}")
    }

    @Test
    fun `자동 재시도를 기다리는 동안은 재시도 중으로 낸다`() = runTest {
        val source = FakeSource(member = listOf(HEAD), failures = mutableListOf(AppError.Network))
        val preloader = preloader(source)

        preloader.start(isLoggedIn = true)
        runCurrent()
        assertTrue(assertIs<PreloadState.Loading>(preloader.state.value).isRetrying)
        advanceTimeBy(1_500)
        runCurrent()
        assertIs<PreloadState.Ready>(preloader.state.value)
    }

    @Test
    fun `회원 에셋을 다 받아 본 적이 없을 때만 처음 실행으로 내고 다 받으면 기억한다`() = runTest {
        val source = FakeSource(member = listOf(HEAD), fileDelayMillis = 1_000)
        val first = preloader(source)
        first.start(isLoggedIn = true)
        runCurrent()
        assertTrue(assertIs<PreloadState.Loading>(first.state.value).isFirstRun)
        advanceUntilIdle()
        assertTrue(source.hasFinished)

        val again = preloader(FakeSource(member = listOf(HEAD), fileDelayMillis = 1_000).apply { hasFinished = true })
        again.start(isLoggedIn = true)
        runCurrent()
        assertFalse(assertIs<PreloadState.Loading>(again.state.value).isFirstRun)

        val loggedOut = FakeSource(public = listOf(PUBLIC), fileDelayMillis = 1_000)
        val beforeLogin = preloader(loggedOut)
        beforeLogin.start(isLoggedIn = false)
        runCurrent()
        assertFalse(assertIs<PreloadState.Loading>(beforeLogin.state.value).isFirstRun, "로그인 전은 처음 실행 안내 없음")
        advanceUntilIdle()
        assertFalse(loggedOut.hasFinished, "로그인 전 공용만 받은 것은 기억하지 않는다")
    }

    @Test
    fun `한 출처의 목록이 인증 만료여도 다른 출처는 받는다`() = runTest {
        val expired = FakeSource(member = listOf(HEAD), memberError = AppError.Unauthorized)
        val ranking = FakeSource(member = listOf(RANKING))
        val preloader = preloader(expired, ranking)

        preloader.start(isLoggedIn = true)
        advanceUntilIdle()

        assertEquals(PreloadState.Ready(PreloadProgress(3, 3)), preloader.state.value)
        assertEquals(listOf(RANKING.key), ranking.fetched)
    }

    @Test
    fun `네트워크가 돌아온 뒤 다시 시도를 한 번 누르면 바로 받기 시작한다`() = runTest {
        val source = FakeSource(member = listOf(HEAD), offline = setOf(HEAD.key))
        val preloader = preloader(source)
        preloader.start(isLoggedIn = true)
        advanceUntilIdle()
        assertIs<PreloadState.Failed>(preloader.state.value)

        source.offline = emptySet()
        preloader.retry()
        assertIs<PreloadState.Loading>(preloader.state.value, "첫 누름에 바로 받기 시작한다")
        advanceUntilIdle()
        assertIs<PreloadState.Ready>(preloader.state.value)
        preloader.retry()
        assertIs<PreloadState.Ready>(preloader.state.value, "준비가 끝난 뒤 누름은 아무것도 하지 않는다")
    }

    @Test
    fun `홈 아바타 합성은 건너뛰지 않는다 - 받지 못하면 실패 화면으로 가고 다시 시도로 다시 한다`() = runTest {
        val preparation = FakePreparation(
            results = MutableList(1 + AssetPreloader.RETRY_DELAYS_MILLIS.size) { Outcome.Failure(AppError.Network) }
        )
        val journal = FakeSource()
        val preloader = AssetPreloader(listOf(AvatarPrepareSource(preparation)), journal, this)

        preloader.start(isLoggedIn = true)
        advanceUntilIdle()

        assertIs<PreloadState.Failed>(preloader.state.value, "합성이 끝나지 않으면 홈으로 넘어가지 않는다")
        assertEquals(1 + AssetPreloader.RETRY_DELAYS_MILLIS.size, preparation.calls, "연결 실패는 자동으로 다시 한다")
        preparation.results.clear()
        preloader.retry()
        advanceUntilIdle()
        assertEquals(PreloadState.Ready(PreloadProgress(2, 2)), preloader.state.value)
    }

    @Test
    fun `홈 아바타 합성이 아주 오래 걸려도 시간으로 끊거나 건너뛰지 않고 끝날 때까지 기다린다`() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val preparation = FakePreparation(gate = gate)
        val journal = FakeSource()
        val preloader = AssetPreloader(listOf(AvatarPrepareSource(preparation)), journal, this)

        preloader.start(isLoggedIn = true)
        advanceTimeBy(ONE_DAY_MILLIS)
        runCurrent()
        assertIs<PreloadState.Loading>(preloader.state.value, "하루가 지나도 기다린다")
        assertFalse(journal.marks.any { "skip" in it || "failed" in it }, "${journal.marks}")

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(PreloadState.Ready(PreloadProgress(2, 2)), preloader.state.value)
    }

    @Test
    fun `단계 문구용 진행 중 단계는 지금 받고 있는 에셋만 센다(차례를 기다리는 에셋은 빼고)`() = runTest {
        val ranking = FakeSource(member = listOf(RANKING), fileDelayMillis = 1_000)
        val avatar =
            FakeSource(member = listOf(HEAD.copy(stage = PreloadStage.MY_AVATAR_FETCH)), fileDelayMillis = 1_000)
        val preloader = AssetPreloader(listOf(ranking, avatar), ranking, this, maxParallel = 1)

        preloader.start(isLoggedIn = true)
        runCurrent()
        val first = assertIs<PreloadState.Loading>(preloader.state.value)
        assertEquals(mapOf(PreloadStage.RANKING to 3, PreloadStage.MY_AVATAR_FETCH to 7), first.remaining)
        assertEquals(mapOf(PreloadStage.RANKING to 3), first.active, "랭킹만 받는 중")

        advanceTimeBy(3_500)
        val later = assertIs<PreloadState.Loading>(preloader.state.value)
        assertEquals(mapOf(PreloadStage.MY_AVATAR_FETCH to 7), later.active, "랭킹이 끝나면 내 아바타 받기")
        advanceUntilIdle()
        assertIs<PreloadState.Ready>(preloader.state.value)
    }

    /** 홈 아바타 미리 준비 포트 가짜: [results]를 차례로 돌려주고(없으면 성공), [gate]가 있으면 열릴 때까지 기다린다. */
    private class FakePreparation(
        val results: MutableList<Outcome<Unit>> = mutableListOf(),
        private val gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    ) : com.a207.runkk.app.domain.avatar.MyAvatarPreparation {
        var calls = 0

        override fun isPrepared(): Boolean = false

        override suspend fun prepare(onStep: () -> Unit): Outcome<Unit> {
            calls++
            gate?.await()
            results.removeFirstOrNull()?.let { return it }
            onStep()
            onStep()
            return Outcome.Success(Unit)
        }
    }

    private fun TestScope.preloader(source: FakeSource, vararg more: AssetPreloadSource) =
        AssetPreloader(listOf(source) + more, source, this)

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
    ) : AssetPreloadSource,
        PreloadJournal {
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

        var maxInFlight = 0
        private var inFlight = 0

        override suspend fun fetch(asset: PreloadAsset, onFile: () -> Unit): Outcome<Unit> {
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
            try {
                return serve(asset, onFile)
            } finally {
                inFlight--
            }
        }

        private suspend fun serve(asset: PreloadAsset, onFile: () -> Unit): Outcome<Unit> {
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

        var hasFinished = false

        override fun mark(line: String) {
            marks += line
        }

        override fun hasFinishedBefore(): Boolean = hasFinished

        override fun markFinished() {
            hasFinished = true
        }
    }

    private companion object {
        val HEAD = PreloadAsset("head:7:job-1", files = 7)
        val PUBLIC = PreloadAsset("public:stage", files = 1)
        val RANKING = PreloadAsset("ranking:personal", files = 3, stage = PreloadStage.RANKING)
        const val OFFLINE_FILES = 3
        const val ONE_DAY_MILLIS = 24L * 60 * 60 * 1_000
    }
}
