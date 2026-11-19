package com.a207.runkk.app.domain.sync

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
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

/**
 * 시작 로딩 화면의 에셋 미리 받기(S15P31A207-345). 앱이 로그인 상태를 알면 [start]를 부르고, 로딩 화면은 [state]가
 * [PreloadState.Ready]가 될 때까지 다음 화면으로 가지 않는다(시간 상한 없음, 지휘자 7회차 결정).
 *
 * - 목록: 로그인 전이면 공용 에셋만, 로그인 뒤면 내 아바타 에셋까지([AssetPreloadSource.memberAssets]).
 * - 캐시 적중: 유효한 캐시에 있는 에셋은 받지 않는다. 다 있으면 막대 없이(total 0) 바로 Ready다.
 * - 진행: 받을 에셋의 파일 수 합이 전체이고 파일 하나를 얻을 때마다 오른다. 다시 받을 때도 막대는 뒤로 가지 않는다.
 * - 실패: 연결 실패(Network)·서버 일시 실패(Server)는 [retryDelaysMillis] 간격으로 자동으로 다시 받는다. 그래도 안 되면 Failed이고
 *   [retry]가 남은 것부터 이어 받는다(끝난 에셋은 적중, 덜 받은 에셋의 얻은 파일은 출처가 다시 받지 않는다). 서버 거부(없는 머리
 *   등)·인증 실패는 다시 받아도 같으므로 건너뛰고 준비를 막지 않는다(화면이 지금처럼 기본 머리를 그리고 뒤에서 다시 찾는다).
 * - 표식: 출처의 [AssetPreloadSource.mark]로 "AssetPreload start/hit/fetch/retry/skip/failed/done" 한 줄씩 남긴다.
 *
 * 같은 로그인 상태로 다시 [start]하면(화면 재생성) 아무것도 하지 않는다. 받기는 [scope](앱 범위)에서 해 화면이 떠나도 끝까지 받는다.
 */
@Singleton
class AssetPreloader internal constructor(
    private val source: AssetPreloadSource,
    private val scope: CoroutineScope,
    private val retryDelaysMillis: List<Long>
) {
    @Inject
    constructor(source: AssetPreloadSource, scope: CoroutineScope) : this(source, scope, RETRY_DELAYS_MILLIS)

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
        _state.value = PreloadState.Loading(PreloadProgress.UNKNOWN)
        job = scope.launch { load(isLoggedIn) }
    }

    /** 실패 화면의 「다시 시도」: 남은 것부터 이어 받는다. 실패 상태가 아니면 아무것도 하지 않는다. */
    fun retry() = synchronized(lock) {
        val current = isLoggedInTarget ?: return@synchronized
        if (_state.value !is PreloadState.Failed) return@synchronized
        _state.value = PreloadState.Loading(_state.value.progress)
        job = scope.launch { load(current) }
    }

    private suspend fun load(isLoggedIn: Boolean) {
        source.mark("AssetPreload start member=$isLoggedIn")
        val planned = when (val result = withRetry("plan") { plan(isLoggedIn) }) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> return fail(_state.value.progress, result.error)
        }
        val (hits, missing) = planned.partition(source::isCached)
        hits.forEach { source.mark("AssetPreload hit ${it.key}") }
        val earlier = synchronized(lock) { hits.mapNotNull { finished[it.key] } }.sum()
        val total = earlier + missing.sumOf { it.files }
        val done = AtomicInteger(earlier + synchronized(lock) { missing.sumOf { partial[it.key] ?: 0 } })
        // 여러 스레드가 알려도 막대가 뒤로 가지 않게 더 큰 값만 싣는다.
        val publish = {
            val now = done.get()
            _state.update { current ->
                val shown = (current as? PreloadState.Loading)?.progress
                if (shown?.total == total &&
                    shown.done >= now
                ) {
                    current
                } else {
                    PreloadState.Loading(PreloadProgress(now, total))
                }
            }
        }
        publish()
        val failures = coroutineScope {
            missing.map { asset -> async { fetch(asset, done, publish) } }.awaitAll().filterNotNull()
        }
        val progress = PreloadProgress(done.get(), total)
        if (failures.isNotEmpty()) return fail(progress, failures.first())
        source.mark("AssetPreload done fetched=${missing.size} hit=${hits.size} files=$total")
        _state.value = PreloadState.Ready(progress)
    }

    private suspend fun plan(isLoggedIn: Boolean): Outcome<List<PreloadAsset>> {
        val public = orSkipped("public", source.publicAssets())
        if (public !is Outcome.Success || !isLoggedIn) return public
        return when (val member = orSkipped("member", source.memberAssets())) {
            is Outcome.Success -> Outcome.Success(public.value + member.value)
            is Outcome.Failure -> member
        }
    }

    /** 목록을 얻다 다시 해도 같은 실패(인증 만료 등)를 만나면 그 목록은 건너뛴다(준비를 막지 않는다, 로그인 화면이 따로 처리한다). */
    private fun orSkipped(what: String, result: Outcome<List<PreloadAsset>>): Outcome<List<PreloadAsset>> {
        if (result !is Outcome.Failure || isRetryable(result.error)) return result
        source.mark("AssetPreload skip $what ${result.error}")
        return Outcome.Success(emptyList())
    }

    /** 에셋 하나(자동 재시도 포함). 다시 받아도 안 되는 실패면 그 오류, 끝났거나 건너뛰었으면 null. */
    private suspend fun fetch(asset: PreloadAsset, done: AtomicInteger, publish: () -> Unit): AppError? {
        val counted = AtomicInteger(synchronized(lock) { partial[asset.key] ?: 0 })
        val result = withRetry(asset.key) {
            source.mark("AssetPreload fetch ${asset.key}")
            val seen = AtomicInteger()
            source.fetch(asset) {
                val now = seen.incrementAndGet().coerceAtMost(asset.files)
                val before = counted.getAndAccumulate(now, ::maxOf)
                if (now > before) {
                    done.addAndGet(now - before)
                    synchronized(lock) { partial[asset.key] = counted.get() }
                    publish()
                }
            }
        }
        val error = (result as? Outcome.Failure)?.error
        if (error != null && isRetryable(error)) return error
        if (error != null) source.mark("AssetPreload skip ${asset.key} $error")
        // 끝났거나 건너뛴 에셋은 남은 칸을 채운다(막대가 100까지 간다).
        done.addAndGet(asset.files - counted.getAndSet(asset.files))
        synchronized(lock) {
            partial.remove(asset.key)
            finished[asset.key] = asset.files
        }
        publish()
        return null
    }

    /** [block]을 하고 다시 받을 만한 실패면 [retryDelaysMillis] 간격으로 다시 한다. 예외는 연결 실패로 본다. */
    private suspend fun <T> withRetry(what: String, block: suspend () -> Outcome<T>): Outcome<T> {
        var attempt = 0
        while (true) {
            val result = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Outcome.Failure(AppError.Network)
            }
            if (result !is Outcome.Failure || !isRetryable(result.error) || attempt >= retryDelaysMillis.size) {
                return result
            }
            source.mark("AssetPreload retry $what #${attempt + 1} ${result.error}")
            delay(retryDelaysMillis[attempt])
            attempt++
        }
    }

    private fun fail(progress: PreloadProgress, error: AppError) {
        source.mark("AssetPreload failed ${progress.done}/${progress.total} $error")
        _state.value = PreloadState.Failed(progress, error)
    }

    /** 잠시 뒤 다시 하면 될 수 있는 실패(연결·서버 일시 실패). 거부·인증 실패·저장 실패는 다시 받아도 같다. */
    private fun isRetryable(error: AppError): Boolean = error is AppError.Network || error is AppError.Server

    internal companion object {
        /**
         * 자동 재시도 간격 1·2·4초(세 번, 처음까지 네 번 시도). 망 전환·잠깐 끊김은 몇 초 안에 돌아오고, 서버 머리 첫 적재 혼잡
         * (503 HEAD_BUSY, 서버가 이미 최대 10초 기다린 뒤)도 몇 초 뒤면 풀린다. 오프라인이면 약 7초 뒤 「다시 시도」를 보인다.
         */
        val RETRY_DELAYS_MILLIS = listOf(1_000L, 2_000L, 4_000L)
    }
}
