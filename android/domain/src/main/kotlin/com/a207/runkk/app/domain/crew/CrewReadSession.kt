package com.a207.runkk.app.domain.crew

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 보이는 한 대상의 다시 읽기를 합친다(설계 05-32 CrewReadSession, [임시:D-성-880]). 신호·당겨서 새로고침·재진입이 같은 [read]를 부른다.
 * 읽는 중에 다시 요청이 오면 끝난 뒤 한 번만 더 읽는다(동시 요청 1개, 후속 1회). 시간 주기 읽기는 없다(U-32).
 * 같은 스레드(화면 ViewModel의 Main)에서만 부른다.
 */
class CrewReadSession(private val scope: CoroutineScope, private val read: suspend () -> Unit) {
    private var job: Job? = null
    private var isDirty = false

    /** 읽는 중인가. */
    val isReading: Boolean get() = job?.isActive == true

    /** 다시 읽기를 요청한다. 읽는 중이면 후속 한 번으로 합친다. */
    fun refresh() {
        if (isReading) {
            isDirty = true
            return
        }
        job = scope.launch {
            do {
                isDirty = false
                read()
            } while (isDirty)
        }
    }

    /** 숨김·폐기: 읽기를 취소하고 남은 후속 요청을 버린다. */
    fun cancel() {
        job?.cancel()
        job = null
        isDirty = false
    }
}
