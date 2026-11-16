package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 이 앱 실행에서 본 내 기록의 저장·전송 결과를 기록ID별로 모은다(설계 05-32 OwnRunUploadPort의 상태 출처). RunArchive가 저장 결과를,
 * UploadPendingRunsUseCase가 서버 응답을 알린다. 메모리에만 두고 앱을 다시 켜면 비어 있다(그때는 보관함과 서버 원본으로 판단한다).
 */
@Singleton
class OwnRunUploads @Inject constructor() {
    private val _states = MutableStateFlow<Map<String, OwnUploadState>>(emptyMap())
    val states: StateFlow<Map<String, OwnUploadState>> = _states.asStateFlow()

    /** 확정한 기록의 측정 거리(Stored에 싣는다). */
    private val distances = MutableStateFlow<Map<String, Distance>>(emptyMap())

    fun onSaving(run: CompletedRun) {
        distances.update { it + (run.recordId to run.distance) }
        set(run.recordId, OwnUploadState.Saving)
    }

    fun onSaved(recordId: String) = set(recordId, OwnUploadState.Pending)

    fun onSaveFailed(recordId: String) = set(recordId, OwnUploadState.SaveFailed)

    /** 서버가 받았다. 늦은 심박을 붙이거나 보관함에서 지우는 일이 남아 있어도 서버 저장은 끝났다. */
    fun onStored(recordId: String, isValid: Boolean) =
        set(recordId, OwnUploadState.Stored(isValid, distances.value[recordId]))

    /** 전송 실패. 거부(Rejected)면 사유와 함께 멈춘 상태, 그 밖은 다시 보낼 실패다. 이미 받은 기록은 바꾸지 않는다. */
    fun onFailed(recordId: String, error: AppError) {
        val failed =
            (error as? AppError.Rejected)?.let { OwnUploadState.Rejected(it.code) } ?: OwnUploadState.Failed(error)
        _states.update { if (it[recordId] is OwnUploadState.Stored) it else it + (recordId to failed) }
    }

    /** 다시 보내기를 요청했다: 실패 표시를 지우고 전송 대기로 돌린다. */
    fun onRetry(recordId: String) {
        _states.update { states ->
            if (states[recordId] is OwnUploadState.Failed) states + (recordId to OwnUploadState.Pending) else states
        }
    }

    private fun set(recordId: String, state: OwnUploadState) {
        _states.update { states ->
            // 서버가 받은 뒤에는 늦게 온 저장·실패 알림으로 되돌리지 않는다.
            if (states[recordId] is OwnUploadState.Stored &&
                state !is OwnUploadState.Stored
            ) {
                states
            } else {
                states + (recordId to state)
            }
        }
    }
}
