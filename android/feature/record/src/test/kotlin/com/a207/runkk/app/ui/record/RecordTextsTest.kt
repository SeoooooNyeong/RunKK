package com.a207.runkk.app.ui.record

import androidx.test.core.app.ApplicationProvider
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.record.RecordDetail
import com.a207.runkk.app.ui.share.CardSide
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 기록 화면의 순수 계산: 불안정 구간 → 경로 포인트 번호, 카드 대체 텍스트, 기록 화면 오류 문구. */
@RunWith(RobolectricTestRunner::class)
class RecordTextsTest {
    private val resources get() = ApplicationProvider.getApplicationContext<android.content.Context>().resources

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
    fun `Kakao 지도는 키가 있고 arm 기기일 때만 띄운다(없으면 Canvas 경로선)`() {
        assertEquals(false, kakaoMapEnabled("", listOf("arm64-v8a")))
        assertEquals(false, kakaoMapEnabled("key", listOf("x86_64")))
        assertEquals(true, kakaoMapEnabled("key", listOf("x86_64", "arm64-v8a")))
    }

    @Test
    fun `지도 경로선은 안정·불안정 구간 줄로 나누고 이웃한 줄은 끝점을 나눈다`() {
        assertEquals(
            listOf(0..1 to false, 1..2 to true, 2..4 to false),
            routeRuns(pointCount = 5, unstable = listOf(1..2))
        )
        assertEquals(listOf(0..2 to false), routeRuns(3, emptyList()))
        assertEquals(emptyList(), routeRuns(1, emptyList()))
    }

    @Test
    fun `카드 이미지 대체 텍스트에 종류·거리·시간·페이스를 담는다`() {
        assertEquals(
            "러닝 카드 앞면, 자유런, 5.02킬로미터, 27분 48초, 1킬로미터당 5분 32초",
            cardDescription(resources, runCard(), CardSide.FRONT)
        )
        assertEquals(
            "러닝 카드 뒷면, 코스 지도, 자유런, 5.02킬로미터, 27분 48초, 1킬로미터당 5분 32초",
            cardDescription(resources, runCard(), CardSide.BACK)
        )
    }

    @Test
    fun `남의 기록 카드는 본인 기록만 만들 수 있다고 알린다`() {
        assertEquals(
            UiText.Res(R.string.record_error_own_record_only),
            RecordErrorText.of(AppError.Rejected("PERMISSION_DENIED"))
        )
        assertEquals(RunkkErrorText.rejected("RECORD_PRIVATE"), RecordErrorText.of(AppError.Rejected("RECORD_PRIVATE")))
        assertEquals(
            RunkkErrorText.rejected("RECORD_CREW_ONLY"),
            RecordErrorText.of(AppError.Rejected("RECORD_CREW_ONLY"))
        )
    }

    @Test
    fun `거리별 최고 기록의 기준 거리(서버 StandardDistance 이름)는 km·하프·풀로 보인다`() {
        assertEquals("1km", bestDistanceLabel(resources, "KM1"))
        assertEquals("5km", bestDistanceLabel(resources, "KM5"))
        assertEquals("10km", bestDistanceLabel(resources, "KM10"))
        assertEquals("하프", bestDistanceLabel(resources, "HALF"))
        assertEquals("풀", bestDistanceLabel(resources, "FULL"))
        assertEquals("UNKNOWN", bestDistanceLabel(resources, "UNKNOWN"))
    }
}
