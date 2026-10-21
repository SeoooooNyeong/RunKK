package com.a207.runkk.app.ui.record

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.record.RecordDetail
import com.a207.runkk.app.ui.share.CardSide
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

/** 기록 화면의 순수 계산: 불안정 구간 → 경로 포인트 번호, 카드 대체 텍스트, 기록 화면 오류 문구. */
class RecordTextsTest {
    private fun unstable(from: Long, to: Long) =
        RecordDetail.Unstable(Duration.ofSeconds(from), Duration.ofSeconds(to), Distance(30))

    @Test
    fun `불안정 구간의 경과 시간을 전체 시간 비율로 경로 포인트 번호에 옮긴다`() {
        assertEquals(
            listOf(1..2),
            unstableIndexRanges(listOf(unstable(10, 20)), Duration.ofSeconds(40), pointCount = 5)
        )
    }

    @Test
    fun `경로 끝을 넘는 구간은 마지막 포인트로 자르고 시간이 없거나 포인트가 모자라면 비운다`() {
        assertEquals(listOf(3..4), unstableIndexRanges(listOf(unstable(30, 90)), Duration.ofSeconds(40), 5))
        assertEquals(emptyList(), unstableIndexRanges(listOf(unstable(1, 2)), Duration.ZERO, 5))
        assertEquals(emptyList(), unstableIndexRanges(listOf(unstable(1, 2)), Duration.ofSeconds(40), 1))
    }

    @Test
    fun `카드 이미지 대체 텍스트에 종류·거리·시간·페이스를 담는다`() {
        assertEquals(
            "러닝 카드 앞면, 자유런, 5.02킬로미터, 27분 48초, 1킬로미터당 5분 32초",
            cardDescription(runCard(), CardSide.FRONT)
        )
        assertEquals(
            "러닝 카드 뒷면, 코스 지도, 자유런, 5.02킬로미터, 27분 48초, 1킬로미터당 5분 32초",
            cardDescription(runCard(), CardSide.BACK)
        )
    }

    @Test
    fun `남의 기록 카드는 본인 기록만 만들 수 있다고 알린다`() {
        assertEquals("본인 기록만 카드로 만들 수 있어요", RecordErrorText.of(AppError.Rejected("PERMISSION_DENIED")))
        assertEquals("비공개 기록이라 볼 수 없어요", RecordErrorText.of(AppError.Rejected("RECORD_PRIVATE")))
        assertEquals("같은 크루원만 볼 수 있는 기록이에요", RecordErrorText.of(AppError.Rejected("RECORD_CREW_ONLY")))
    }
}
