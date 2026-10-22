package com.a207.runkk.app.ui.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.record.RunCard
import com.a207.runkk.app.domain.runimal.RunimalCard
import java.time.Duration
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 공유 카드 이미지(러닝 카드 앞·뒷면, Runimal 카드) 스크린샷. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class CardScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val card = RunCard(
        recordId = "rec-1",
        cardKind = CardKind.FREE_RUN,
        startedAt = Instant.parse("2026-10-20T10:00:00Z"),
        distance = Distance(5_020),
        elapsed = Duration.ofSeconds(1_668),
        averagePace = Pace(332),
        route = listOf(
            GeoPoint(37.50, 127.00),
            GeoPoint(37.506, 127.008),
            GeoPoint(37.502, 127.016),
            GeoPoint(37.51, 127.024),
            GeoPoint(37.512, 127.03)
        ),
        isCourseMapShared = true
    )

    @Test
    fun runCardFront() = compose.captureRunkkScreen("share_run_card_front") {
        Image(
            RunCardRenderer(RuntimeEnvironment.getApplication()).render(card, CardSide.FRONT).asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }

    @Test
    fun runCardBack() = compose.captureRunkkScreen("share_run_card_back") {
        Image(
            RunCardRenderer(RuntimeEnvironment.getApplication()).render(card, CardSide.BACK).asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }

    @Test
    fun runimalCard() = compose.captureRunkkScreen("share_runimal_card") {
        val runimal = RunimalCard(
            "MORNING_SPARROW",
            "질주하는 치타",
            "짧고 빠르게, 늘 가던 길을 정해진 시간에 달려요",
            mapOf(
                "COMPANION" to 0.7,
                "DISTANCE" to 0.8,
                "COURSE" to 0.6,
                "HABIT" to 0.7
            )
        )
        Image(
            RunimalCardRenderer(RuntimeEnvironment.getApplication()).render(runimal).asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}
