package com.a207.runkk.app.api.avatar

import com.a207.runkk.app.domain.avatar.SelfieProgress
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 셀카 작업 진행의 프로세스 사본(design-oo 05-30 [임시:D-성-117], [임시:DR-성-13]). 원본은 [PendingSelfieStore](작업 ID·접수 시각·
 * 마지막 실패)이고, 저장소 구현과 워커가 그 값을 고친 뒤 [sync]로 다시 읽어 같은 프로세스의 화면이 바로 받게 한다.
 * 성공은 원본에 남기지 않으므로(결과를 받으면 작업 ID를 지운다) [succeeded]로 다음 접수·확인까지 이 사본에만 둔다.
 */
@Singleton
internal class SelfieProgressHub
@Inject
constructor(private val store: PendingSelfieStore) {
    private val state = MutableStateFlow<SelfieProgress?>(null)

    /** 처음 읽을 때 원본에서 채운다. */
    val progress: StateFlow<SelfieProgress?> = state.asStateFlow()

    /** 원본을 다시 읽어 진행을 정한다. 남은 작업이 있으면 만드는 중, 없고 실패가 남았으면 실패, 둘 다 없으면 앞 성공을 두거나 없음이다. */
    fun sync() {
        val jobId = store.load()
        val failure = store.failure()
        state.value = when {
            jobId != null -> SelfieProgress.Working(jobId, store.submittedAt())
            failure != null -> SelfieProgress.Failed(failure.jobId, failure.code)
            else -> state.value as? SelfieProgress.Succeeded ?: SelfieProgress.None
        }
    }

    /** 처음 듣는 쪽을 위해 아직 읽지 않았으면 원본에서 채운다. */
    fun ensureLoaded() {
        if (state.value == null) sync()
    }

    /** 결과를 받아 내 얼굴을 바꿨다([임시:D-성-118]). */
    fun succeeded(jobId: String, faceKey: String?) {
        state.value = SelfieProgress.Succeeded(jobId, faceKey)
    }

    /** 성공·실패를 본 뒤(확인, 기본 캐릭터 고름) 없음으로 둔다. 남은 작업이 있으면 그대로 만드는 중이다. */
    fun reset() {
        state.value = null
        sync()
    }
}
