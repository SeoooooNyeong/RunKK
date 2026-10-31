package com.a207.runkk.watch.link

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.live.BattleStart
import com.a207.runkk.app.domain.live.LiveChannel
import com.a207.runkk.app.domain.live.LiveChannelProvider
import com.a207.runkk.app.domain.live.MatchFailed
import com.a207.runkk.app.domain.live.MatchFound
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

/**
 * 워치 배틀런 매칭 대기의 서버 실시간 연결(FR-257, D-376). 폰 MatchViewModel과 같은 순서로 대기 채널을 연다: 메시지를 먼저 구독하고
 * 연결한 뒤에 매칭 요청을 보낸다(실시간 채널은 지난 메시지를 다시 주지 않는다, D-274). 성사(MatchFound)·실패(MatchFailed)·끊김 가운데
 * 첫 사건을 [End]로 돌려준다. 서버는 MatchFound 바로 뒤에 BattleStart를 보내므로 성사 뒤에도 대기 임대를 쥐고 그 대결의 출발 예약을
 * 넘긴다([forwardStarts]). 조정자가 측정을 시작하거나 대결이 끝나면 [close]로 임대를 반납한다(D-196).
 *
 * watch.ui가 domain.live를 import하지 않게(설계 5절 watch.ui Fan-Out 7) 실시간 메시지 형식은 이 클래스에서만 다룬다.
 */
@Singleton
class MatchWaiter @Inject constructor(private val channels: LiveChannelProvider, private val scope: CoroutineScope) {
    /** 대기가 끝난 사건. */
    sealed interface End {
        /** 매칭 성사. [match]는 BattleRunCoordinator.onMatched에 넘긴다. */
        data class Found(val match: MatchFound) : End

        /** 서버가 매칭을 끝냈다(사유 코드). 서버가 대기열에서 이미 뺐으므로 취소를 보내지 않는다. */
        data class Failed(val code: String) : End

        /** 채널을 열지 못했거나 끊겼다. */
        data class Lost(val error: AppError?) : End
    }

    private val lock = Any()
    private var channel: LiveChannel? = null
    private var listening: Job? = null
    private var starts: Job? = null
    private var ends = Channel<End>(Channel.CONFLATED)
    private var earlyStart: BattleStart? = null

    /** 대기 채널을 열어 끝 메시지·출발 예약을 구독하고 연결한다. 이전 대기가 남아 있으면 닫는다. 연결하지 못하면 Failure. */
    suspend fun open(): Outcome<Unit> {
        close()
        val opened = channels.open()
        val endChannel = Channel<End>(Channel.CONFLATED)
        synchronized(lock) {
            channel = opened
            ends = endChannel
            earlyStart = null
            listening = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                opened.messages
                    .onCompletion { cause -> if (cause == null) endChannel.trySend(End.Lost(null)) }
                    .collect { message ->
                        when (message) {
                            is MatchFound -> endChannel.trySend(End.Found(message))
                            is MatchFailed -> endChannel.trySend(End.Failed(message.code))
                            is BattleStart -> synchronized(lock) { earlyStart = message }
                            else -> Unit
                        }
                    }
            }
        }
        // 연결 중에 취소되거나 실패하면 이 임대를 반납한다(취소가 open보다 먼저 닫기를 불러 놓친 경우도 여기서 닫는다).
        val connected = try {
            opened.connect()
        } catch (e: CancellationException) {
            closeIfCurrent(opened)
            throw e
        }
        if (connected is Outcome.Failure) {
            closeIfCurrent(opened)
            return connected
        }
        return Outcome.Success(Unit)
    }

    /** 대기가 끝나는 첫 사건을 기다린다. [open] 전에 부르면 Lost다. */
    suspend fun awaitEnd(): End {
        val endChannel = synchronized(lock) { ends.takeIf { channel != null } } ?: return End.Lost(null)
        return endChannel.receive()
    }

    /**
     * 성사된 [battleId] 대결의 출발 예약을 [onStart]로 넘긴다. 대기 중에 먼저 받은 것도 넘긴다. 조정자가 직접 받은 것과 겹쳐도
     * 조정자가 한 번만 시작한다(BattleRun.schedule).
     */
    fun forwardStarts(battleId: Long, onStart: (BattleStart) -> Unit) {
        val early = synchronized(lock) {
            val opened = channel ?: return
            starts?.cancel()
            starts = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                opened.messages.collect { message ->
                    if (message is BattleStart && message.battleId == battleId) onStart(message)
                }
            }
            earlyStart
        }
        early?.takeIf { it.battleId == battleId }?.let(onStart)
    }

    /** [opened]가 아직 지금 대기 채널이면 닫는다. 바뀌었으면 바꾼 쪽(새 open의 close)이 이미 반납했다. */
    private fun closeIfCurrent(opened: LiveChannel) {
        val isCurrent = synchronized(lock) { channel === opened }
        if (isCurrent) close()
    }

    /** 대기 임대를 반납한다(서버 대기열 정리는 하지 않는다). 거듭 불러도 된다. */
    fun close() {
        val closing = synchronized(lock) {
            val current = channel
            channel = null
            listening?.cancel()
            listening = null
            starts?.cancel()
            starts = null
            earlyStart = null
            current
        }
        closing?.close()
    }
}
