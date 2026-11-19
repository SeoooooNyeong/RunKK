package com.a207.runkk.app.domain.sync

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 시작 로딩 화면의 에셋 미리 받기(S15P31A207-345). 앱이 로그인 상태를 알면 [start]를 부르고, 로딩 화면은 [state]가
 * [PreloadState.Ready]가 될 때까지 다음 화면으로 가지 않는다(시간 상한 없음, 지휘자 7회차 결정).
 *
 * - 목록: 출처([sources], 종류마다 하나)마다 로그인 전이면 공용 에셋만, 로그인 뒤면 회원 에셋까지([AssetPreloadSource.memberAssets]).
 *   출처들의 목록은 함께 묻는다.
 * - 캐시 적중: 유효한 캐시에 있는 에셋은 받지 않는다. 다 있으면 막대 없이(total 0) 바로 Ready다.
 * - 진행: 받을 에셋의 파일 수 합이 전체이고 파일 하나를 얻을 때마다 오른다. 다시 받을 때도 막대는 뒤로 가지 않는다. 단계마다 남은
 *   파일 수([PreloadState.Loading.remaining])를 함께 내 로딩 문구를 고르게 한다.
 * - 동시 요청: 한 번에 [maxParallel]개 에셋만 받는다(기본 [MAX_PARALLEL]).
 * - 실패: 연결 실패(Network)·서버 일시 실패(Server)는 [retryDelaysMillis] 간격으로 자동으로 다시 받는다(기다리는 동안 isRetrying).
 *   그래도 안 되면 Failed이고 [retry]가 남은 것부터 이어 받는다(끝난 에셋은 적중, 덜 받은 에셋의 얻은 파일은 출처가 다시 받지 않는다).
 *   서버 거부(없는 머리 등)·인증 실패는 다시 받아도 같으므로 건너뛰고 준비를 막지 않는다. 앞당기기 에셋([PreloadAsset.isOptional])은
 *   한 번만 하고 실패해도 건너뛴다.
 * - 표식: [journal]에 "AssetPreload <종류> start/hit/fetch/retry/skip/failed/done" 한 줄씩 남긴다(종류는 에셋 키 앞부분, 내 머리는
 *   avatar, 전체는 all).
 *
 * 같은 로그인 상태로 다시 [start]하면(화면 재생성) 아무것도 하지 않는다. 받기는 [scope](앱 범위)에서 해 화면이 떠나도 끝까지 받는다.
 */
@Singleton
class AssetPreloader internal constructor(
    private val sources: List<AssetPreloadSource>,
    private val journal: PreloadJournal,
    private val scope: CoroutineScope,
    private val retryDelaysMillis: List<Long> = RETRY_DELAYS_MILLIS,
    private val maxParallel: Int = MAX_PARALLEL
) {
    @Inject
    constructor(
        sources: Set<@JvmSuppressWildcards AssetPreloadSource>,
        journal: PreloadJournal,
        scope: CoroutineScope
    ) : this(sources.toList(), journal, scope)

    private val _state = MutableStateFlow<PreloadState>(PreloadState.Loading(PreloadProgress.UNKNOWN))
    val state: StateFlow<PreloadState> = _state.asStateFlow()

    private val lock = Any()
    private var isLoggedInTarget: Boolean? = null
    private var job: Job? = null

    /** 이 프로세스에서 다 받은 에셋(다시 시도 때 막대에 받은 것으로 남긴다). */
    private val finished = HashMap<String, Int>()

    /** 덜 받은 에셋에서 얻은 파일 수(시도마다 가장 큰 값). */
    private val partial = HashMap<String, Int>()

    /** 로그인 상태([isLoggedIn])에 맞는 목록을 받기 시작한다. 같은 상태로 이미 시작했으면 아무것도 하지 않는다. */
    fun start(isLoggedIn: Boolean) = synchronized(lock) {
        if (isLoggedInTarget == isLoggedIn) return@synchronized
        isLoggedInTarget = isLoggedIn
        job?.cancel()
        _state.value = PreloadState.Loading(PreloadProgress.UNKNOWN, isFirstRun = isFirstRun(isLoggedIn))
        job = scope.launch { load(isLoggedIn) }
    }

    /** 실패 화면의 「다시 시도」: 남은 것부터 이어 받는다. 실패 상태가 아니면 아무것도 하지 않는다. */
    fun retry() = synchronized(lock) {
        val current = isLoggedInTarget ?: return@synchronized
        val failed = _state.value as? PreloadState.Failed ?: return@synchronized
        _state.value = PreloadState.Loading(failed.progress, isFirstRun = isFirstRun(current))
        job = scope.launch { load(current) }
    }

    private fun isFirstRun(isLoggedIn: Boolean): Boolean = isLoggedIn && !journal.hasFinishedBefore()

    private suspend fun load(isLoggedIn: Boolean) {
        journal.mark("AssetPreload all start member=$isLoggedIn")
        val run = Run(isFirstRun(isLoggedIn))
        val planned = when (val result = withRetry("all:plan", run) { plan(isLoggedIn) }) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> return fail(_state.value.progress, result.error)
        }
        val (hits, missing) = planned.partition { (asset, source) -> source.isCached(asset) }
        hits.forEach { (asset, _) -> mark("hit", asset) }
        val earlier = synchronized(lock) { hits.mapNotNull { finished[it.first.key] } }.sum()
        run.begin(missing.map { it.first }, earlier)
        run.publish()
        val gate = Semaphore(maxParallel)
        val failures = coroutineScope {
            missing.map { (asset, source) -> async { fetch(asset, source, run, gate) } }.awaitAll().filterNotNull()
        }
        val progress = run.progress()
        if (failures.isNotEmpty()) return fail(progress, failures.first())
        journal.mark("AssetPreload all done fetched=${missing.size} hit=${hits.size} files=${progress.total}")
        if (isLoggedIn) journal.markFinished()
        _state.value = PreloadState.Ready(progress)
    }

    /** 출처마다 목록을 함께 묻는다. 다시 받을 만한 실패가 하나라도 있으면 그 실패(목록 전체를 다시 묻는다). */
    private suspend fun plan(isLoggedIn: Boolean): Outcome<List<Pair<PreloadAsset, AssetPreloadSource>>> =
        coroutineScope {
            val results = sources.map { source -> async { planOf(source, isLoggedIn) } }.awaitAll()
            results.filterIsInstance<Outcome.Failure>().firstOrNull()
                ?: Outcome.Success(results.flatMap { (it as Outcome.Success).value })
        }

    private suspend fun planOf(
        source: AssetPreloadSource,
        isLoggedIn: Boolean
    ): Outcome<List<Pair<PreloadAsset, AssetPreloadSource>>> {
        val public = orSkipped("public", guarded { source.publicAssets() })
        if (public !is Outcome.Success || !isLoggedIn) return public.pairedWith(source)
        return when (val member = orSkipped("member", guarded { source.memberAssets() })) {
            is Outcome.Success -> Outcome.Success((public.value + member.value).map { it to source })
            is Outcome.Failure -> member
        }
    }

    private fun Outcome<List<PreloadAsset>>.pairedWith(
        source: AssetPreloadSource
    ): Outcome<List<Pair<PreloadAsset, AssetPreloadSource>>> = when (this) {
        is Outcome.Success -> Outcome.Success(value.map { it to source })
        is Outcome.Failure -> this
    }

    /** 목록을 얻다 다시 해도 같은 실패(인증 만료 등)를 만나면 그 목록은 건너뛴다(준비를 막지 않는다, 로그인 화면이 따로 처리한다). */
    private fun orSkipped(what: String, result: Outcome<List<PreloadAsset>>): Outcome<List<PreloadAsset>> {
        if (result !is Outcome.Failure || isRetryable(result.error)) return result
        journal.mark("AssetPreload all skip $what ${result.error}")
        return Outcome.Success(emptyList())
    }

    /** 에셋 하나(자동 재시도 포함). 다시 받아도 안 되는 실패면 그 오류, 끝났거나 건너뛰었으면 null. */
    private suspend fun fetch(asset: PreloadAsset, source: AssetPreloadSource, run: Run, gate: Semaphore): AppError? {
        val counted = AtomicInteger(synchronized(lock) { partial[asset.key] ?: 0 })
        val attempt: suspend () -> Outcome<Unit> = {
            gate.withPermit {
                mark("fetch", asset)
                val seen = AtomicInteger()
                guarded {
                    source.fetch(asset) {
                        val now = seen.incrementAndGet().coerceAtMost(asset.files)
                        val before = counted.getAndAccumulate(now, ::maxOf)
                        if (now > before) {
                            run.add(asset, now - before)
                            synchronized(lock) { partial[asset.key] = counted.get() }
                            run.publish()
                        }
                    }
                }
            }
        }
        val result = if (asset.isOptional) attempt() else withRetry(asset.key, run, attempt)
        val error = (result as? Outcome.Failure)?.error
        if (error != null && isRetryable(error) && !asset.isOptional) return error
        if (error != null) mark("skip", asset, " $error")
        // 끝났거나 건너뛴 에셋은 남은 칸을 채운다(막대가 100까지 간다).
        run.add(asset, asset.files - counted.getAndSet(asset.files))
        synchronized(lock) {
            partial.remove(asset.key)
            finished[asset.key] = asset.files
        }
        run.publish()
        return null
    }

    /** [block]을 하고 다시 받을 만한 실패면 [retryDelaysMillis] 간격으로 다시 한다. 기다리는 동안 [run]이 재시도 중을 낸다. */
    private suspend fun <T> withRetry(what: String, run: Run, block: suspend () -> Outcome<T>): Outcome<T> {
        var attempt = 0
        while (true) {
            val result = guarded(block)
            if (result !is Outcome.Failure || !isRetryable(result.error) || attempt >= retryDelaysMillis.size) {
                return result
            }
            journal.mark("AssetPreload ${kindOf(what)} retry $what #${attempt + 1} ${result.error}")
            run.retrying(+1)
            try {
                delay(retryDelaysMillis[attempt])
            } finally {
                run.retrying(-1)
            }
            attempt++
        }
    }

    /** 출처가 던진 예외는 연결 실패로 본다(취소는 그대로 넘긴다). */
    private suspend fun <T> guarded(block: suspend () -> Outcome<T>): Outcome<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Outcome.Failure(AppError.Network)
    }

    private fun mark(what: String, asset: PreloadAsset, suffix: String = "") =
        journal.mark("AssetPreload ${kindOf(asset.key)} $what ${asset.key}$suffix")

    private fun fail(progress: PreloadProgress, error: AppError) {
        journal.mark("AssetPreload all failed ${progress.done}/${progress.total} $error")
        _state.value = PreloadState.Failed(progress, error)
    }

    /** 잠시 뒤 다시 하면 될 수 있는 실패(연결·서버 일시 실패). 거부·인증 실패·저장 실패는 다시 받아도 같다. */
    private fun isRetryable(error: AppError): Boolean = error is AppError.Network || error is AppError.Server

    /** 한 번의 받기(시도): 막대·단계별 남은 수·재시도 중을 모아 상태로 낸다. 여러 스레드가 함께 쓴다. */
    private inner class Run(private val isFirstRun: Boolean) {
        private val done = AtomicInteger()
        private val waiting = AtomicInteger()
        private val left = ConcurrentHashMap<String, Pair<PreloadStage, AtomicInteger>>()

        @Volatile
        private var total = 0

        fun begin(missing: List<PreloadAsset>, earlier: Int) {
            total = earlier + missing.sumOf { it.files }
            val got = synchronized(lock) { missing.associate { it.key to (partial[it.key] ?: 0) } }
            missing.forEach { asset ->
                left[asset.key] = asset.stage to AtomicInteger(asset.files - got.getValue(asset.key))
            }
            done.set(earlier + got.values.sum())
        }

        fun add(asset: PreloadAsset, files: Int) {
            if (files <= 0) return
            done.addAndGet(files)
            left[asset.key]?.second?.addAndGet(-files)
        }

        fun retrying(delta: Int) {
            waiting.addAndGet(delta)
            publish()
        }

        fun progress(): PreloadProgress = PreloadProgress(done.get(), total)

        /** 여러 스레드가 알려도 막대가 뒤로 가지 않게 더 큰 값을 싣는다. */
        fun publish() {
            val now = done.get()
            val isRetrying = waiting.get() > 0
            val remaining = left.values
                .groupBy({ it.first }, { it.second.get().coerceAtLeast(0) })
                .mapValues { (_, counts) -> counts.sum() }
            _state.update { current ->
                val shown = (current as? PreloadState.Loading)?.progress?.takeIf { it.total == total }?.done ?: 0
                val next = PreloadState.Loading(
                    PreloadProgress(maxOf(now, shown), total),
                    remaining,
                    isRetrying,
                    isFirstRun
                )
                if (current is PreloadState.Loading && current.progress.done > now && current.progress.total == total) {
                    // 늦게 온 작은 값: 막대는 그대로 두고 단계·재시도만 바꾼다.
                    next.copy(progress = current.progress)
                } else {
                    next
                }
            }
        }
    }

    internal companion object {
        /**
         * 자동 재시도 간격 1·2·4초(세 번, 처음까지 네 번 시도). 망 전환·잠깐 끊김은 몇 초 안에 돌아오고, 서버 머리 첫 적재 혼잡
         * (503 HEAD_BUSY, 서버가 이미 최대 10초 기다린 뒤)도 몇 초 뒤면 풀린다. 오프라인이면 약 7초 뒤 「다시 시도」를 보인다.
         */
        val RETRY_DELAYS_MILLIS = listOf(1_000L, 2_000L, 4_000L)

        /**
         * 한 번에 받는 에셋 수 4. OkHttp는 한 호스트에 동시 요청 5개까지 보내므로(Dispatcher.maxRequestsPerHost 기본값) 에셋 4개를
         * 받는 동안에도 화면·토큰 갱신 요청이 한 자리를 쓸 수 있다.
         */
        const val MAX_PARALLEL = 4

        /** 표식의 종류: 에셋 키 앞부분(내 셀카 머리 "head:"는 avatar). */
        fun kindOf(key: String): String = key.substringBefore(':').let { if (it == "head") "avatar" else it }
    }
}
