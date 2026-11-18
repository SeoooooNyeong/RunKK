package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.model.RunViewMode
import java.io.File
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 트랙 카드, 진행 막대, 보기 전환([임시:D-성-313], [임시:D-성-268], [임시:D-성-306]). */
@RunWith(RobolectricTestRunner::class)
class RunkkTrackCardTest {
    @get:Rule
    val compose = createComposeRule()

    private fun card(
        layout: TrackLayout.Result,
        aheadChip: String? = null,
        behindChip: String? = null,
        rulerLabel: String? = null,
        height: Dp = 328.dp
    ) {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkTrackCard(
                    layout = layout,
                    myTag = "나",
                    otherTag = "바람처럼",
                    myAvatar = {},
                    otherAvatar = {},
                    modifier = Modifier.size(width = 320.dp, height = height),
                    myPace = "5'30\"",
                    aheadChip = aheadChip,
                    behindChip = behindChip,
                    rulerLabel = rulerLabel
                )
            }
        }
    }

    private fun centerX(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().let {
        (it.left + it.right) /
            2
    }

    @Test
    fun `나는 폭의 40퍼센트에 있고 앞선 상대는 거리만큼 오른쪽에 그린다`() {
        card(TrackLayout.of(aheadMeters = 12.0, remainingMeters = 2_000.0), rulerLabel = "12 m")

        // 홀수 크기 아바타를 정수 픽셀에 놓을 때 생기는 반 픽셀 반올림을 허용한다.
        assertEquals(128f, centerX(TRACK_ME_TAG).value, 0.5f)
        assertEquals(256.dp, centerX(TRACK_OTHER_TAG))
        compose.onNodeWithText("바람처럼").assertExists()
        compose.onNodeWithText("5'30\"").assertExists()
        compose.onNodeWithText("12 m").assertExists()
        compose.onAllNodesWithTag(TRACK_FINISH_TAG).assertCountEquals(0)
    }

    @Test
    fun `뒤선 상대는 내 왼쪽에 그린다`() {
        card(TrackLayout.of(aheadMeters = -6.0, remainingMeters = 2_000.0))

        assertTrue(centerX(TRACK_OTHER_TAG) < centerX(TRACK_ME_TAG))
    }

    @Test
    fun `주자 칸은 시안 크기로 각 레인 발 위치에 놓인다`() {
        card(TrackLayout.of(aheadMeters = -6.0, remainingMeters = 2_000.0))

        val mine = compose.onNodeWithTag(TRACK_ME_TAG).getUnclippedBoundsInRoot()
        val other = compose.onNodeWithTag(TRACK_OTHER_TAG).getUnclippedBoundsInRoot()
        assertEquals(105.dp, mine.right - mine.left)
        assertEquals(293.dp, mine.bottom)
        assertEquals(88.dp, other.right - other.left)
        assertEquals(225.dp, other.bottom)
        assertNameTags(mine.top, other.top)
    }

    @Test
    fun `280 카드에서는 주자를 줄여 이름표와 보기 전환이 겹치지 않는다`() {
        assertShortCard(280.dp, otherFoot = 177.dp)
    }

    @Test
    fun `240 카드에서도 이름표가 보기 전환 아래에 남고 발은 레인을 지킨다`() {
        assertShortCard(240.dp, otherFoot = 137.dp)
    }

    private fun assertShortCard(height: Dp, otherFoot: Dp) {
        card(TrackLayout.of(aheadMeters = -6.0, remainingMeters = 2_000.0), height = height)
        val other = compose.onNodeWithTag(TRACK_OTHER_TAG).getUnclippedBoundsInRoot()
        assertEquals(otherFoot, other.bottom)
        assertTrue(other.top >= 96.dp)
        assertTrue(other.right - other.left < 92.dp)
        assertNameTags(compose.onNodeWithTag(TRACK_ME_TAG).getUnclippedBoundsInRoot().top, other.top)
    }

    private fun assertNameTags(myHead: Dp, otherHead: Dp) {
        val mine = compose.onNodeWithTag(TRACK_ME_NAME_TAG).getUnclippedBoundsInRoot()
        val other = compose.onNodeWithTag(TRACK_OTHER_NAME_TAG).getUnclippedBoundsInRoot()
        // 작은 카드에서는 칸 높이와 위 좌표를 각각 정수 px로 반올림한다.
        assertEquals(myHead.value - 4f, mine.bottom.value, 1f)
        assertEquals(otherHead.value - 4f, other.bottom.value, 1f)
        assertTrue(other.bottom < mine.top, "가로로 겹쳐도 상대 이름표가 한 줄 위에 있다")
    }

    @Test
    fun `상대가 화면 밖이면 아바타 없이 그쪽 가장자리 칩을 그린다`() {
        card(
            TrackLayout.of(aheadMeters = 240.0, remainingMeters = 2_000.0),
            aheadChip = "바람처럼 +240 m",
            rulerLabel = "240 m"
        )

        compose.onAllNodesWithTag(TRACK_OTHER_TAG).assertCountEquals(0)
        compose.onNodeWithTag(TRACK_AHEAD_TAG).assertExists()
        compose.onNodeWithText("바람처럼 +240 m").assertExists()
        compose.onAllNodesWithTag(TRACK_BEHIND_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(TRACK_RULER_TAG).assertCountEquals(0)
    }

    @Test
    fun `결승선은 화면 안에 들어올 때만 그린다`() {
        card(TrackLayout.of(aheadMeters = 3.0, remainingMeters = 9.0))

        assertEquals(224.dp, centerX(TRACK_FINISH_TAG))
    }

    @Test
    fun `결승선이 화면 밖이면 그리지 않는다`() {
        card(TrackLayout.of(aheadMeters = 3.0, remainingMeters = 25.0))

        compose.onAllNodesWithTag(TRACK_FINISH_TAG).assertCountEquals(0)
    }

    @Test
    fun `격차를 모르면 상대와 거리 자 알약을 그리지 않는다`() {
        card(TrackLayout.of(aheadMeters = null, remainingMeters = 2_000.0), rulerLabel = null)

        compose.onNodeWithTag(TRACK_ME_TAG).assertExists()
        compose.onAllNodesWithTag(TRACK_OTHER_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(TRACK_RULER_TAG).assertCountEquals(0)
    }

    @Test
    fun `보기 전환은 고른 칸을 알리고 누르면 그 보기를 넘긴다`() {
        var mode by mutableStateOf(RunViewMode.TRACK)
        compose.setContent {
            RunkkTheme(reduceMotion = true) { RunkkViewModeToggle(selected = mode, onSelect = { mode = it }) }
        }

        compose.onNodeWithTag(viewModeTag(RunViewMode.TRACK)).assertIsSelected()
        compose.onNodeWithTag(viewModeTag(RunViewMode.MAP)).assertIsNotSelected()
        compose.onNodeWithText("지도").performClick()

        assertEquals(RunViewMode.MAP, mode)
        compose.onNodeWithTag(viewModeTag(RunViewMode.MAP)).assertIsSelected()
    }

    @Test
    fun `진행 막대는 참가자마다 점을 찍고 거리 글자를 적는다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                Box(Modifier.size(width = 320.dp, height = 80.dp)) {
                    RunkkTrackProgressBar(
                        dots = listOf(
                            ProgressDot(0.47f, isMine = true),
                            ProgressDot(0.5f, false),
                            ProgressDot(1.4f, false)
                        ),
                        leading = "2.34 / 5 km",
                        trailing = "남은 거리 2.66 km",
                        modifier = Modifier.testTag("track-progress")
                    )
                }
            }
        }

        compose.onAllNodesWithTag(TRACK_DOT_MINE_TAG).assertCountEquals(1)
        compose.onAllNodesWithTag(TRACK_DOT_OTHER_TAG).assertCountEquals(2)
        compose.onNodeWithTag("track-progress").assertHeightIsEqualTo(44.dp)
        compose.onNodeWithTag(TRACK_DOT_MINE_TAG).assertWidthIsEqualTo(13.dp)
        compose.onAllNodesWithTag(TRACK_DOT_OTHER_TAG)[0].assertWidthIsEqualTo(11.dp)
        compose.onNodeWithText("2.34 / 5 km").assertExists()
        compose.onNodeWithText("남은 거리 2.66 km").assertExists()
    }

    @Test
    fun `가장자리에서 트랙에 들어온 상대는 내 자리(가운데)가 아니라 가장자리 쪽에서 시작해 부드럽게 들어온다(S15P31A207-335)`() {
        var layout by mutableStateOf(TrackLayout.of(aheadMeters = 20.0, remainingMeters = 2_000.0))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            RunkkTheme(reduceMotion = false) {
                RunkkTrackCard(
                    layout = layout,
                    myTag = "나",
                    otherTag = "바람처럼",
                    myAvatar = {},
                    otherAvatar = {},
                    modifier = Modifier.size(width = 320.dp, height = 328.dp)
                )
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        Snapshot.withMutableSnapshot { layout = TrackLayout.of(aheadMeters = 15.0, remainingMeters = 2_000.0) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        // 20 m 앞(x 1.07) 가장자리 밖에서 오므라 들어오는 순간은 오른쪽 가장자리 근처다. 가운데(128)에서 나타나지 않는다.
        assertTrue(centerX(TRACK_OTHER_TAG) > 272.dp, "들어오는 순간 ${centerX(TRACK_OTHER_TAG)}")
        // 시간이 지나면 받은 자리(0.9 × 320 = 288)에 닿는다.
        compose.mainClock.advanceTimeBy(3_000)
        assertEquals(288f, centerX(TRACK_OTHER_TAG).value, 1f)
    }

    /** 몸 준비에 [readyAfterMillis]가 걸리는 상대 아바타(트랙 칸처럼 준비 중임을 카드에 알린다). */
    private fun slowAvatar(readyAfterMillis: Long): @Composable () -> Unit = {
        val report = LocalTrackAvatarLoading.current
        var isReady by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(readyAfterMillis)
            isReady = true
        }
        DisposableEffect(isReady, report) {
            report(!isReady)
            onDispose { report(false) }
        }
    }

    private fun movingCard(layout: () -> TrackLayout.Result, otherAvatar: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            RunkkTheme(reduceMotion = false) {
                RunkkTrackCard(
                    layout = layout(),
                    myTag = "나",
                    otherTag = "과거의 나",
                    myAvatar = {},
                    otherAvatar = otherAvatar,
                    modifier = Modifier.size(width = 320.dp, height = 328.dp)
                )
            }
        }
    }

    private fun isOtherShown() = compose.onAllNodesWithTag(TRACK_OTHER_TAG).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `출발 때 상대는 몸이 준비될 때까지 그림자·이름표도 숨고 준비되면 출발 쪽 트랙 밖에서 들어와 제자리로 간다(S15P31A207-335 2차)`() {
        // 셀프런 출발: 상대는 내 자리(격차 0)에서 시작하고 몸 준비에 0.7초가 걸린다.
        movingCard({ TrackLayout.of(aheadMeters = 0.0, remainingMeters = 5_000.0) }, slowAvatar(readyAfterMillis = 700))

        assertTrue(!isOtherShown(), "첫 프레임부터 그림자·이름표를 먼저 보이지 않는다")
        compose.onAllNodesWithTag(TRACK_OTHER_NAME_TAG).assertCountEquals(0)
        var t = 0L
        while (t < 680) {
            compose.mainClock.advanceTimeByFrame()
            t += FRAME_MILLIS
            assertTrue(!isOtherShown(), "몸 준비 전(${t}ms)에는 숨는다")
            compose.onAllNodesWithTag(TRACK_OTHER_NAME_TAG).assertCountEquals(0)
        }
        while (!isOtherShown()) {
            compose.mainClock.advanceTimeByFrame()
            t += FRAME_MILLIS
            assertTrue(t < 900, "준비되면 곧 보인다")
        }
        compose.onNodeWithTag(TRACK_OTHER_NAME_TAG).assertExists()
        // 보이는 순간 상대는 출발 쪽(왼쪽) 가장자리 밖에 있다(가운데 제자리에서 생기지 않는다).
        val first = centerX(TRACK_OTHER_TAG)
        assertTrue(first < 0.dp, "처음 보일 때 트랙 밖 왼쪽: $first")
        var previous = first
        repeat(90) {
            compose.mainClock.advanceTimeByFrame()
            val x = centerX(TRACK_OTHER_TAG)
            assertTrue(x >= previous - 0.5.dp, "들어오는 동안 되돌아가지 않는다: $previous → $x")
            previous = x
        }
        assertEquals(128f, centerX(TRACK_OTHER_TAG).value, 1f)
    }

    @Test
    @Config(qualifiers = "w411dp-h914dp-xxhdpi")
    fun `일정 속도 진행값이면 상대는 매 프레임 조금씩 오른쪽으로 가고 계단이 없으며 내 자리는 그대로다(S15P31A207-335 2차)`() {
        // 상대가 1초마다 3 m씩 앞선다(정수 m 수신). 몸은 바로 준비된다.
        var ahead by mutableStateOf(0.0)
        movingCard({ TrackLayout.of(aheadMeters = ahead, remainingMeters = 5_000.0) }, otherAvatar = {})
        val rows = mutableListOf<Triple<Long, Float?, Float>>()
        var t = 0L
        while (t <= 4_000) {
            // 1초마다(프레임은 16ms라 1초를 넘긴 첫 프레임에) 정수 m 값이 온다.
            val second = t / 1_000
            if (3.0 * second != ahead) Snapshot.withMutableSnapshot { ahead = 3.0 * second }
            compose.mainClock.advanceTimeByFrame()
            t += FRAME_MILLIS
            val other = if (isOtherShown()) centerX(TRACK_OTHER_TAG).value else null
            rows += Triple(t, other, centerX(TRACK_ME_TAG).value)
        }
        val table = rows.filter { it.first <= 3_000 }.joinToString("\n") { (ms, other, me) ->
            "$ms\t${other?.let { "%.2f".format(it) } ?: "-"}\t${"%.2f".format(me)}"
        }
        println("ms\tother_x_dp\tme_x_dp\n$table")
        File("build/reports/track-frames").apply { mkdirs() }.resolve("constant-3mps.tsv")
            .writeText("ms\tother_x_dp\tme_x_dp\n$table\n")

        assertEquals(1, rows.map { it.third }.distinct().size, "내 자리는 폭의 40%에 고정이다(트랙 점선이 움직인다)")
        val shown = rows.filter { it.second != null }.map { it.first to it.second!! }
        shown.zipWithNext { a, b -> assertTrue(b.second >= a.second - 0.34f, "되돌아가지 않는다 at ${b.first}ms") }
        // 1 m = 320/30 ≈ 10.7dp. 들어오는 동안도 한 프레임에 1 m를 넘게 뛰지 않는다.
        val steps = shown.zipWithNext { a, b -> b.first to (b.second - a.second) }
        assertTrue(steps.all { it.second < 10.6f }, "계단: ${steps.maxBy { it.second }}")
        // 들어온 뒤(1.5초~) 일정 속도 3 m/s = 프레임당 0.512dp. 한 프레임 이동이 그 언저리(0.34dp 픽셀 반올림 포함)에 머문다.
        val steady = steps.filter { it.first >= 1_500 }
        assertTrue(
            steady.all {
                it.second in -0.34f..1.6f
            },
            "고르지 않은 한 프레임: ${steady.filter { it.second !in -0.34f..1.6f }}"
        )
        val stalled = steady.windowed(4).count { window -> window.sumOf { it.second.toDouble() } <= 0.0 }
        assertEquals(0, stalled, "네 프레임 넘게 멈춰 있지 않는다")
    }

    /**
     * 등장·이동 시나리오(S15P31A207-335 4차). 1초마다 [gapAt]의 정수 m 격차를 넣으며 16ms 프레임마다 (ms, 상대 가운데 x dp, 그리는
     * 불투명도)를 기록한다. 상대 몸 준비는 [readyAfterMillis]이고 null이면 바로 준비된다. 기록은 build/reports/track-frames/[name].tsv.
     */
    private fun scenario(
        name: String,
        readyAfterMillis: Long?,
        untilMillis: Long,
        gapAt: (Long) -> Double
    ): List<Triple<Long, Float, Float>> {
        var ahead by mutableStateOf(gapAt(0))
        movingCard(
            { TrackLayout.of(aheadMeters = ahead, remainingMeters = 5_000.0) },
            otherAvatar = readyAfterMillis?.let(::slowAvatar) ?: {}
        )
        val rows = mutableListOf<Triple<Long, Float, Float>>()
        var t = 0L
        while (t <= untilMillis) {
            val next = gapAt(t)
            if (next != ahead) Snapshot.withMutableSnapshot { ahead = next }
            compose.mainClock.advanceTimeByFrame()
            t += FRAME_MILLIS
            val probe = compose.onNode(SemanticsMatcher.keyIsDefined(TrackOtherAlpha))
            val alpha = probe.fetchSemanticsNode().config[TrackOtherAlpha]
            val bounds = probe.getUnclippedBoundsInRoot()
            rows += Triple(t, ((bounds.left + bounds.right) / 2).value, alpha)
        }
        val table = rows.joinToString("\n") { (ms, x, alpha) -> "$ms\t${"%.2f".format(x)}\t${"%.3f".format(alpha)}" }
        File("build/reports/track-frames").apply { mkdirs() }.resolve("$name.tsv")
            .writeText("ms\tother_x_dp\talpha\n$table\n")
        return rows
    }

    /** 1초마다 바뀌는 정수 m 격차: [values]의 i번째 값이 i초부터다(마지막 값이 이어진다). */
    private fun perSecond(vararg values: Double): (Long) -> Double =
        { t -> values[(t / 1_000).toInt().coerceAtMost(values.lastIndex)] }

    private val Triple<Long, Float, Float>.isVisible get() = third > HIDDEN_ALPHA

    /** 보이는 두 프레임 사이에 상한(폭의 2.5%)을 넘게 옮긴 곳(순간 이동). */
    private fun teleports(rows: List<Triple<Long, Float, Float>>) = rows.zipWithNext()
        .filter { (a, b) -> a.isVisible && b.isVisible && abs(b.second - a.second) > STEP_CAP_DP + PIXEL_DP }
        .map { (a, b) -> "${b.first}ms ${a.second}→${b.second}" }

    /** 보이는 두 프레임 사이에 내 x(128dp)를 넘어간 곳(가로지름). */
    private fun crossings(rows: List<Triple<Long, Float, Float>>) = rows.zipWithNext()
        .filter { (a, b) ->
            a.isVisible && b.isVisible &&
                (a.second - MY_X_DP) * (b.second - MY_X_DP) < 0 &&
                maxOf(abs(a.second - MY_X_DP), abs(b.second - MY_X_DP)) > PIXEL_DP
        }
        .map { (a, b) -> "${b.first}ms ${a.second}→${b.second}" }

    /** 1초(62프레임) 안에 보이는 채로 왼쪽(진행 반대)으로 움직인 합의 최댓값(dp). */
    private fun maxBackwardSlide(rows: List<Triple<Long, Float, Float>>): Float {
        val steps = rows.zipWithNext { a, b -> if (a.isVisible && b.isVisible) minOf(0f, b.second - a.second) else 0f }
        return steps.windowed(62, partialWindows = true).maxOf { window -> -window.sum() }
    }

    @Test
    fun `(a) 출발 격차 0이면 몸이 준비될 때 출발 쪽 밖에서 들어오고 내 x를 넘지 않는다(S15P31A207-335 4차)`() {
        val rows = scenario("a-start-gap-0", readyAfterMillis = 700, untilMillis = 3_000, gapAt = { 0.0 })

        val visible = rows.filter { it.isVisible }
        assertTrue(visible.first().second < 0f, "출발 쪽(왼쪽) 트랙 밖에서 나타난다: ${visible.first()}")
        assertEquals(emptyList(), teleports(rows), "순간 이동")
        assertTrue(visible.all { it.second <= MY_X_DP + PIXEL_DP }, "내 x를 넘지 않는다: ${visible.maxOf { it.second }}")
        assertEquals(MY_X_DP, visible.last().second, PIXEL_DP)
    }

    @Test
    fun `(b) 몸 준비 때 상대가 이미 14 m 앞이면 그 자리에서 나타나고 나를 가로지르거나 왼쪽으로 미끄러지지 않는다(S15P31A207-335 4차)`() {
        // 에뮬레이터 셀프런: 내 거리는 0인데 과거 기록은 약 3.5 m/s로 앞서 나가고, 상대 몸은 4.07초에 준비된다.
        val rows = scenario(
            "b-ready-14m-ahead",
            readyAfterMillis = 4_070,
            untilMillis = 5_500,
            gapAt = perSecond(0.0, 4.0, 7.0, 11.0, 14.0, 18.0)
        )

        val visible = rows.filter { it.isVisible }
        assertTrue(visible.isNotEmpty() && visible.first().first <= 4_070 + 3 * FRAME_MILLIS, "준비되면 곧 나타난다")
        assertTrue(visible.first().second > MY_X_DP + 100f, "실제 자리(오른쪽)에서 나타난다: ${visible.first()}")
        assertTrue(visible.all { it.second > MY_X_DP }, "내 뒤(왼쪽)로 가지 않는다: ${visible.minOf { it.second }}")
        assertEquals(emptyList(), teleports(rows), "순간 이동")
        assertEquals(emptyList(), crossings(rows), "가로지름")
        assertTrue(maxBackwardSlide(rows) <= ONE_METER_DP, "진행 반대로 미끄러짐 ${maxBackwardSlide(rows)}dp")
    }

    @Test
    fun `(c) 몸 준비 때 상대가 8 m 뒤면 왼쪽 밖에서 들어오고 내 x를 넘지 않는다(S15P31A207-335 4차)`() {
        val rows = scenario(
            "c-ready-8m-behind",
            readyAfterMillis = 3_070,
            untilMillis = 6_000,
            gapAt = perSecond(0.0, -3.0, -6.0, -8.0)
        )

        val visible = rows.filter { it.isVisible }
        assertTrue(visible.first().second < 0f, "왼쪽 트랙 밖에서 들어온다: ${visible.first()}")
        assertTrue(visible.all { it.second < MY_X_DP }, "내 x를 넘지 않는다: ${visible.maxOf { it.second }}")
        assertEquals(emptyList(), teleports(rows), "순간 이동")
        assertEquals(emptyList(), crossings(rows), "가로지름")
        // 8 m 뒤 = (0.4 − 8/30) × 320 ≈ 42.7dp
        assertEquals(42.7f, visible.last().second, 1.5f)
    }

    @Test
    fun `(d) 실제 위치가 트랙 밖(가장자리 칩)이면 몸을 그리지 않는다(S15P31A207-335 4차)`() {
        // 10 m 뒤는 트랙 밖이다(가장자리 여백 0.08 → 9.6 m).
        val rows = scenario(
            "d-off-track-behind",
            readyAfterMillis = 3_070,
            untilMillis = 5_000,
            gapAt = perSecond(0.0, -4.0, -8.0, -10.0, -12.0)
        )

        assertEquals(emptyList(), rows.filter { it.first >= 3_000 && it.isVisible }, "트랙 밖인데 보인 프레임")
    }

    @Test
    fun `(d) 앞쪽 트랙 밖(20 m 앞)이어도 몸을 그리지 않는다(S15P31A207-335 4차)`() {
        val rows = scenario("d-off-track-ahead", readyAfterMillis = null, untilMillis = 2_000, gapAt = { 20.0 })

        assertEquals(emptyList(), rows.filter { it.isVisible }, "트랙 밖인데 보인 프레임")
    }

    @Test
    fun `(e) 달리는 중 격차가 크게 바뀌면 보이는 채로 뛰지 않고 사라졌다 새 자리에서 나타난다(S15P31A207-335 4차)`() {
        // 2 m 앞 → 8 m 뒤(10 m 변화) → 12 m 앞(20 m 변화) → 1 m/s로 15 m 앞까지(따라감).
        val rows = scenario(
            "e-big-gap-change",
            readyAfterMillis = null,
            untilMillis = 9_500,
            gapAt = perSecond(2.0, 2.0, -8.0, -8.0, 12.0, 13.0, 14.0, 15.0)
        )

        assertEquals(emptyList(), teleports(rows), "순간 이동")
        assertEquals(emptyList(), crossings(rows), "가로지름")
        val jumps = rows.filter { it.first in 1_500..5_000 }
        assertTrue(maxBackwardSlide(jumps) <= PIXEL_DP, "큰 변화 때 진행 반대로 미끄러짐 ${maxBackwardSlide(jumps)}dp")
        // 상대 속도가 멈추면(15 m에서 그대로) GapMotion 예측이 1.5초까지 앞서 간 만큼(1 m/s × 1.5초 이하) 되돌아온다. 크게(2 m) 넘지 않는다.
        assertTrue(maxBackwardSlide(rows) <= 2 * ONE_METER_DP, "진행 반대로 미끄러짐 ${maxBackwardSlide(rows)}dp")
        fun at(ms: Long) = rows.first { it.first >= ms }
        assertEquals(42.7f, at(3_900).second, 1.5f)
        assertTrue(at(3_900).isVisible)
        assertEquals(256f, at(4_900).second, 1.5f)
        assertEquals(288f, rows.last().second, 1.5f)
    }

    @Test
    fun `출발 때 내 아바타는 몸이 준비될 때까지 그림자·이름표도 숨어 몸 없는 그림자·이름표 프레임이 없다(S15P31A207-335 3차)`() {
        // 카운트다운 → 달리기 화면: 트랙의 내 칸은 새 플레이어라 첫 판까지 0.47초가 걸린다(에뮬레이터 녹화).
        var isBodyReady = false
        val myAvatar: @Composable () -> Unit = {
            val report = LocalTrackAvatarLoading.current
            var isReady by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(MY_FIRST_FRAME_MILLIS)
                isReady = true
            }
            DisposableEffect(isReady, report) {
                isBodyReady = isReady
                report(!isReady)
                onDispose { report(false) }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            RunkkTheme(reduceMotion = false) {
                RunkkTrackCard(
                    layout = TrackLayout.of(aheadMeters = 0.0, remainingMeters = 5_000.0),
                    myTag = "나",
                    otherTag = "과거의 나",
                    myAvatar = myAvatar,
                    otherAvatar = {},
                    modifier = Modifier.size(width = 320.dp, height = 328.dp)
                )
            }
        }
        fun isMineShown() = compose.onAllNodesWithTag(TRACK_ME_NAME_TAG).fetchSemanticsNodes().isNotEmpty()
        // (ms, 내 그림자·이름표 보임, 몸 준비)
        val rows = mutableListOf(Triple(0L, isMineShown(), isBodyReady))
        var t = 0L
        while (t < 1_000) {
            compose.mainClock.advanceTimeByFrame()
            t += FRAME_MILLIS
            rows += Triple(t, isMineShown(), isBodyReady)
        }
        val table = rows.joinToString(separator = "\n") { (ms, shown, ready) -> "$ms\t$shown\t$ready" }
        println("ms\tme_shown\tbody_ready\n$table")
        val bodyless = rows.filter { (_, shown, ready) -> shown && !ready }.map { it.first }
        assertEquals(emptyList(), bodyless, "몸 없이 그림자·이름표만 보인 프레임(ms)")
        val firstShown = rows.first { it.second }.first
        assertTrue(firstShown <= MY_FIRST_FRAME_MILLIS + 3 * FRAME_MILLIS, "몸이 준비되면 곧 보인다: $firstShown ms")
        assertTrue(rows.filter { it.first >= firstShown }.all { it.second }, "한 번 보인 뒤 다시 숨지 않는다")
    }

    private companion object {
        const val FRAME_MILLIS = 16L
        const val MY_FIRST_FRAME_MILLIS = 470L

        /** 이 불투명도 이하는 보이지 않는 미리 그리기다(카드의 준비용 0.01). */
        const val HIDDEN_ALPHA = 0.011f

        /** 카드 폭 320dp에서 내 x(폭의 40%). */
        const val MY_X_DP = 128f

        /** 보이는 동안 한 프레임 이동 상한: 폭의 2.5% = 8dp(320dp 카드). */
        const val STEP_CAP_DP = 8f

        /** 픽셀 반올림 허용(mdpi 1px). */
        const val PIXEL_DP = 1f

        /** 1 m = 320/30 dp. */
        const val ONE_METER_DP = 320f / 30f
    }
}
