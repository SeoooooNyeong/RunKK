package com.a207.runkk.app.ui.share

import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.RunResultDetailState
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.record.RecordRepository
import javax.inject.Inject

/**
 * 기록 상세(GET /runs/{id})를 결과 상세 한 장으로 바꿔 읽는다(대결 결과 2쪽 toDetailState와 같은 항목). 날짜 줄은 비워 두고 부르는
 * 화면이 채운다.
 */
class RecordResultDetailReader @Inject constructor(private val records: RecordRepository) {
    suspend fun read(recordId: String): Outcome<RecordResultDetail> = when (val read = records.run(recordId)) {
        is Outcome.Success -> with(read.value) {
            Outcome.Success(
                RecordResultDetail(
                    summary.startedAt,
                    RunResultDetailState(
                        distance = summary.distance,
                        dateLine = "",
                        elapsed = summary.elapsed,
                        rest = paused.takeIf { !it.isZero },
                        averagePace = summary.averagePace,
                        averageHeartRate = averageHeartRate,
                        splitPaces = splitPaces,
                        heartRateZones = heartRateZones.toList(),
                        newRecords = newRecords,
                        rewardPoints = rewardPoints,
                        expression = AvatarExpression.JUMP
                    )
                )
            )
        }

        is Outcome.Failure -> read
    }
}
