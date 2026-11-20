package com.a207.runkk.app.core.avatar

import java.util.concurrent.Executor

/**
 * Rive 파일 가져오기(.riv 읽기, 내장 그림 풀기)를 주 스레드 밖에서 하고, 출발 전에 미리 가져온 것을 칸에 나눠 준다(S15P31A207-335 6회차).
 *
 * - [request]: 그 키의 파일을 청한다. 미리 가져온 것이 준비돼 있으면 부른 자리에서 바로 주고(첫 구성 프레임에 플레이어를 만든다),
 *   가져오는 중이면 끝나는 대로 준다. 없으면 [worker]에서 새로 가져와 [main]에서 준다. 한 번 준 것은 다시 주지 않는다.
 * - [hold]: 그 키 하나를 미리 가져와 붙잡는다(카운트다운·크루런 대기실의 트랙 주자 데우기). 같은 키를 두 번 붙잡으면 둘을 가져온다.
 *   붙잡기를 놓으면 [LINGER_MS] 뒤 아무도 가져가지 않은 것은 버린다(곧 열리는 달리기 화면이 가져갈 틈). 미리 가져오기는 [prefetcher]
 *   줄에서 돌아 지금 그릴 칸(카운트다운 VS 칸 등)의 [request]보다 뒤에 선다.
 *
 * 상태는 주 스레드에서만 바꾼다([request]·[hold]·[Hold.release]는 주 스레드에서 부른다).
 */
internal class RivImports<K : Any, T : Any>(
    private val import: (K) -> T,
    private val dispose: (T) -> Unit,
    private val worker: Executor,
    private val main: Executor,
    private val later: (Long, Runnable) -> Unit,
    private val prefetcher: Executor = worker
) {
    /** 미리 가져온(또는 가져오는 중인) 것 하나. */
    private inner class Slot(val key: K) {
        var result: Result<T>? = null
        var waiter: ((Result<T>) -> Unit)? = null
        var isClaimed = false
        var isDropped = false
    }

    /** 아직 아무 칸도 가져가지 않은 미리 가져오기(키마다 먼저 시작한 것이 앞). */
    private val unclaimed = mutableMapOf<K, MutableList<Slot>>()

    fun request(key: K, onLoaded: (Result<T>) -> Unit) {
        val slot = unclaimed[key]?.let { slots -> slots.firstOrNull { it.result != null } ?: slots.firstOrNull() }
        if (slot == null) {
            start(key, worker, onLoaded)
            return
        }
        unclaim(slot)
        slot.isClaimed = true
        val result = slot.result
        if (result != null) onLoaded(result) else slot.waiter = onLoaded
    }

    fun hold(key: K): Hold {
        val slot = Slot(key)
        unclaimed.getOrPut(key) { mutableListOf() } += slot
        start(key, prefetcher) { result -> onPrefetched(slot, result) }
        return Hold { drop(slot) }
    }

    private fun start(key: K, on: Executor, onLoaded: (Result<T>) -> Unit) {
        on.execute {
            // 가져오기가 Error(OutOfMemoryError 등)로 끝나도 결과를 주 스레드로 돌려준다.
            @Suppress("TooGenericExceptionCaught")
            val result = try {
                Result.success(import(key))
            } catch (error: Throwable) {
                Result.failure(error)
            }
            main.execute { onLoaded(result) }
        }
    }

    private fun onPrefetched(slot: Slot, result: Result<T>) {
        if (slot.isDropped) {
            result.onSuccess(dispose)
            return
        }
        slot.result = result
        slot.waiter?.let { waiter ->
            slot.waiter = null
            waiter(result)
        }
    }

    private fun unclaim(slot: Slot) {
        val slots = unclaimed[slot.key] ?: return
        slots.remove(slot)
        if (slots.isEmpty()) unclaimed.remove(slot.key)
    }

    private fun drop(slot: Slot) {
        later(LINGER_MS) {
            if (slot.isClaimed) return@later
            unclaim(slot)
            slot.isDropped = true
            slot.result?.onSuccess(dispose)
        }
    }

    /** 미리 가져오기 하나를 붙잡은 것. 놓으면 [LINGER_MS] 뒤에도 아무도 가져가지 않았을 때 버린다. */
    class Hold internal constructor(private val onRelease: () -> Unit) {
        private var isReleased = false

        fun release() {
            if (isReleased) return
            isReleased = true
            onRelease()
        }
    }

    companion object {
        /** 붙잡기를 놓은 뒤 가져가기를 기다리는 시간. 카운트다운이 끝나 달리기 화면이 칸을 만들기까지(에뮬레이터 2초 안)보다 넉넉하다. */
        const val LINGER_MS = 10_000L
    }
}
