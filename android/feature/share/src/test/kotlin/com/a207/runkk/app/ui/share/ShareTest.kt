package com.a207.runkk.app.ui.share

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.record.RunCard
import com.a207.runkk.app.domain.runimal.RunimalCard
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

private class FakeSettings(var shared: Boolean) : SettingsRepository {
    override suspend fun refresh(): Outcome<Unit> = Outcome.Success(Unit)

    override fun locationConsented() = true

    override fun heartRateConsented() = false

    override fun watchLinked() = false

    override fun courseMapShared() = shared

    override fun birthYear(): Int? = null

    override fun guidanceSettings() = GuidanceSettings.DEFAULT

    override fun changes(): Flow<SettingsSnapshot> = flowOf(SettingsSnapshot.SAFE)

    override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
}

private class RecordingTarget : ShareTarget {
    var received: Uri? = null

    override fun share(context: Context, image: Uri): ShareOutcome {
        received = image
        return ShareOutcome.Shared
    }
}

/** 시험에서는 FileProvider 대신 파일 주소를 쓴다(FileProvider의 경로 비교가 Windows 경로 구분자를 모른다). */
private fun fileUri(context: Context, file: java.io.File): Uri = Uri.fromFile(file)

@RunWith(RobolectricTestRunner::class)
class ShareTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val card = RunCard(
        recordId = "rec-1",
        cardKind = CardKind.FREE_RUN,
        startedAt = Instant.parse("2026-10-20T10:00:00Z"),
        distance = Distance(5_020),
        elapsed = Duration.ofSeconds(1_668),
        averagePace = Pace(332),
        route = listOf(GeoPoint(37.5, 127.0), GeoPoint(37.51, 127.01), GeoPoint(37.505, 127.02)),
        courseMapShared = true
    )

    @Test
    fun `러닝 카드는 앞·뒷면 모두 같은 크기로 그린다(FR-068)`() {
        val renderer = RunCardRenderer()
        val front = renderer.render(card, CardSide.FRONT)
        val back = renderer.render(card, CardSide.BACK)
        assertEquals(RunCardRenderer.WIDTH, front.width)
        assertEquals(RunCardRenderer.HEIGHT, back.height)
    }

    @Test
    fun `Runimal 카드는 앞면 하나를 그린다(FR-168)`() {
        val bitmap = RunimalCardRenderer().render(RunimalCard("치타", "질주하는 치타", "짧고 빠르게", mapOf("거리" to 0.7)))
        assertEquals(RunCardRenderer.WIDTH, bitmap.width)
    }

    @Test
    fun `코스 지도 공유가 꺼져 있으면 뒷면 공유를 거부한다(FR-069)`() = runTest {
        val target = RecordingTarget()
        val sharer = CardSharer(context, FakeSettings(shared = false), ::fileUri)
        val outcome = sharer.share(RunCardRenderer().render(card, CardSide.BACK), CardSide.BACK, target)
        assertIs<ShareOutcome.Failed>(outcome)
        assertEquals(CardSharer.COURSE_MAP_NOT_SHARED, outcome.code)
        assertNull(target.received)
    }

    @Test
    fun `앞면은 이미지 파일 주소를 공유 대상에 넘긴다`() = runTest {
        val target = RecordingTarget()
        val sharer = CardSharer(context, FakeSettings(shared = false), ::fileUri)
        val outcome = sharer.share(RunCardRenderer().render(card, CardSide.FRONT), CardSide.FRONT, target)
        assertEquals(ShareOutcome.Shared, outcome)
        val uri = assertNotNull(target.received)
        assertTrue(java.io.File(requireNotNull(uri.path)).length() > 0)
    }

    @Test
    fun `다른 앱 공유는 공유 시트를 연다`() {
        val outcome = SystemShareTarget().share(context, Uri.parse("content://x/card.png"))
        assertEquals(ShareOutcome.Shared, outcome)
        val started = shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, started.action)
    }

    @Test
    fun `인스타그램이 없으면 스토리 대신 기기 저장을 안내한다`() {
        val outcome = StoryShareTarget(DeviceSaveTarget()).share(context, Uri.parse("content://x/card.png"))
        assertIs<ShareOutcome.Fallback>(outcome)
    }
}
