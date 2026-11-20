package com.a207.runkk.app.ui.record.runbody

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.record.RunBodyPreview
import com.a207.runkk.app.domain.record.RunBodyPreviewSource
import com.a207.runkk.app.domain.runbody.RunBodyAxis
import com.a207.runkk.app.domain.runbody.RunBodyRepository
import com.a207.runkk.app.domain.runbody.RunBodySession
import com.a207.runkk.app.domain.runbody.RunBodyStatus
import javax.inject.Inject

/**
 * 기록 상세의 런바디 진입 카드 상태를 런바디 분석(GET /runbody/sessions/{recordId})으로 정한다. 결과가 있으면 운동 강도·지구력·페이스
 * 안정성, 대기·분석 중이면 분석 중, 기능이 꺼졌으면(FEATURE_CLOSED) 숨김, 그 밖(실패·데이터 부족·일시 장애)은 볼 수 없음이다.
 */
internal class RunBodyPreviewReader @Inject constructor(private val runBody: RunBodyRepository) : RunBodyPreviewSource {
    override suspend fun preview(recordId: String): RunBodyPreview = when (val result = runBody.session(recordId)) {
        is Outcome.Success -> previewOf(result.value)
        is Outcome.Failure -> previewOf(result.error)
    }

    private fun previewOf(session: RunBodySession): RunBodyPreview {
        val result = session.result
        return when {
            session.status.isWaiting -> RunBodyPreview.Analyzing

            session.status == RunBodyStatus.DONE && result != null -> RunBodyPreview.Ready(
                intensity = result.axes[RunBodyAxis.INTENSITY]?.score,
                endurance = result.axes[RunBodyAxis.ENDURANCE]?.score,
                stability = result.axes[RunBodyAxis.STABILITY]?.score
            )

            else -> RunBodyPreview.Unavailable
        }
    }

    private fun previewOf(error: AppError): RunBodyPreview =
        if ((error as? AppError.Rejected)?.code == RunBodyRepository.FEATURE_CLOSED) {
            RunBodyPreview.Hidden
        } else {
            RunBodyPreview.Unavailable
        }
}
