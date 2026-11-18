package com.a207.runkk.app.ui.record.runbody

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.record.RunBodyPreview
import com.a207.runkk.app.domain.runbody.RunBodyRepository
import com.a207.runkk.app.domain.runbody.RunBodyStatus
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** 기록 상세의 런바디 진입 카드 상태(피그마 G-9·G-9b)를 런바디 분석 결과로 정한다. */
class RunBodyPreviewReaderTest {
    private val runBody = FakeRunBodyRepository()
    private val reader = RunBodyPreviewReader(runBody)

    @Test
    fun `분석이 끝났으면 세 지표(운동 강도·지구력·페이스 안정성)를 담는다`() = runTest {
        assertEquals(RunBodyPreview.Ready(intensity = 78, endurance = 84, stability = 72), reader.preview("r1"))
        assertEquals(listOf("r1"), runBody.sessionCalls)
    }

    @Test
    fun `대기·분석 중이면 Analyzing, 실패·데이터 부족·일시 장애면 Unavailable`() = runTest {
        runBody.reply("r1", waiting("r1", RunBodyStatus.ANALYZING))
        assertEquals(RunBodyPreview.Analyzing, reader.preview("r1"))
        runBody.reply("r1", waiting("r1", RunBodyStatus.PENDING))
        assertEquals(RunBodyPreview.Analyzing, reader.preview("r1"))
        runBody.reply("r1", waiting("r1", RunBodyStatus.FAILED))
        assertEquals(RunBodyPreview.Unavailable, reader.preview("r1"))
        runBody.reply("r1", Outcome.Failure(AppError.Rejected(RunBodyRepository.NOT_FOUND)))
        assertEquals(RunBodyPreview.Unavailable, reader.preview("r1"))
        runBody.reply("r1", Outcome.Failure(AppError.Server(503)))
        assertEquals(RunBodyPreview.Unavailable, reader.preview("r1"))
    }

    @Test
    fun `런바디 기능이 꺼졌으면(FEATURE_CLOSED) 숨긴다`() = runTest {
        runBody.reply("r1", Outcome.Failure(AppError.Rejected(RunBodyRepository.FEATURE_CLOSED)))
        assertEquals(RunBodyPreview.Hidden, reader.preview("r1"))
    }
}
