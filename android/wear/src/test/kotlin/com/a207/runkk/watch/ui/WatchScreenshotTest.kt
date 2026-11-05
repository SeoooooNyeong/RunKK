package com.a207.runkk.watch.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.RunProgress
import com.a207.runkk.watch.battle.RecordOpponent
import com.a207.runkk.watch.battle.WatchResultHeadline
import com.a207.runkk.watch.cache.CachedTrack
import com.a207.runkk.watch.testing.celebrityListItem
import com.a207.runkk.watch.testing.celebrityTrack
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.Duration
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 워치 화면 스크린샷. 작은 원형 워치(192dp, 384px)에 원으로 잘라 찍는다. 앱 프로세스(Hilt RunkkWatchApp) 대신 빈 Application으로 띄운다.
 * 이름 머리는 피그마 프레임(Q·F·B·S·C 흐름, 결과)과 K2·K4·K5(폰 러닝 동행 표시, W35부터 측정 화면과 같은 모양)다. 기록: recordRoborazziLocalDebug,
 * 검증: verifyRoborazziLocalDebug.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = WATCH_SMALL_ROUND, application = Application::class)
class WatchScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val companion = WatchUiState(
        isPhoneConnected = true,
        isRunning = true,
        elapsedSeconds = 1_421,
        distanceMeters = 4_121,
        paceSecondsPerKm = 342,
        heartRateBpm = 152
    )

    private fun progress(meters: Long, seconds: Long, paused: Boolean = false) = RunProgress(
        elapsed = Duration.ofSeconds(seconds),
        distance = Distance(meters),
        currentPace = Pace(392),
        averagePace = Pace(390),
        signal = null,
        heartRate = HeartRateSample(Instant.EPOCH, 151),
        isPaused = paused
    )

    private val summary = RunSummaryView(Distance(3_000), Duration.ofSeconds(1_170), Pace(390), 154)

    private fun capture(
        name: String,
        flow: WatchFlow,
        companionState: WatchUiState = companion,
        isAmbient: Boolean = false
    ) {
        compose.setContent {
            WatchTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(WatchColors.Background)
                ) {
                    WatchRoot(
                        flow = flow,
                        companion = companionState,
                        actions = WatchActions.NONE,
                        isAmbient = isAmbient,
                        timeText = { TimeText(timeSource = FixedTimeSource) }
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    private fun choices(vararg meters: Long?) = meters.map { WatchFlow.Choice(it?.let(::Distance)) }

    @Test
    fun notLoggedIn() = capture("N0_watch_not_logged_in", WatchFlow.NotLoggedIn)

    @Test
    fun mainFree() = capture("Q1_watch_main_free", WatchFlow.Main(WatchMode.FREE, Gender.FEMALE))

    @Test
    fun mainBattle() = capture("Q2_watch_main_battle", WatchFlow.Main(WatchMode.BATTLE, Gender.FEMALE))

    @Test
    fun mainSelf() = capture("Q3_watch_main_self", WatchFlow.Main(WatchMode.SELF, Gender.MALE))

    @Test
    fun mainCeleb() = capture("Q4_watch_main_celeb", WatchFlow.Main(WatchMode.CELEB, null))

    /** 거리 0으로 끝내 저장하지 않았다는 안내를 메인 위에 띄운다(R13 D4). */
    @Test
    fun mainNotSaved() = capture(
        "Q1_watch_main_not_saved",
        WatchFlow.Main(WatchMode.FREE, Gender.FEMALE, notice = WatchFlow.Notice.NOT_SAVED)
    )

    /** GPS 예열 중 찾는 중(W53). */
    @Test
    fun mainGpsSearching() = capture(
        "Q1_watch_main_gps_searching",
        WatchFlow.Main(WatchMode.FREE, Gender.FEMALE, gps = WatchFlow.GpsStatus.SEARCHING)
    )

    /** GPS 예열 준비됨(W53). */
    @Test
    fun mainGpsReady() = capture(
        "Q1_watch_main_gps_ready",
        WatchFlow.Main(WatchMode.FREE, Gender.FEMALE, gps = WatchFlow.GpsStatus.READY)
    )

    /** 워치 위치 꺼짐 안내(W52·W53). 누르면 위치 설정을 연다. */
    @Test
    fun mainLocationOff() = capture(
        "Q1_watch_main_location_off",
        WatchFlow.Main(WatchMode.FREE, Gender.FEMALE, gps = WatchFlow.GpsStatus.LOCATION_OFF)
    )

    /** 자유런 거리 선택의 워치 위치 꺼짐 안내(W52). */
    @Test
    fun freeLocationOff() = capture(
        "F1_watch_free_location_off",
        WatchFlow.FreeGoal(choices(null, 3_000, 5_000, 10_000), notice = WatchFlow.Notice.LOCATION_OFF)
    )

    /** 측정 중 위치를 받지 못함(W53): 안내 띠 자리에 "GPS 찾는 중". */
    @Test
    fun runGpsSearching() = capture(
        "FRinf_watch_run_gps_searching",
        WatchFlow.Running(WatchMode.FREE, progress(0, 12), goal = null, isPausable = true, isGpsSearching = true)
    )

    /** 자유런에서 배틀런으로 넘어가는 중간 프레임(Smart Animate 320ms 가운데쯤, 시계 고정). 카드가 반쯤 미끄러지며 커지고 작아진다. */
    @Test
    fun mainTransition() {
        compose.setContent {
            WatchTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(WatchColors.Background)
                ) {
                    WatchModeCarousel(WatchFlow.Main(WatchMode.FREE, Gender.FEMALE), onModeSelected = {})
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("배틀런").performClick()
        compose.mainClock.advanceTimeBy(TRANSITION_MIDDLE_MS)
        compose.onRoot().captureRoboImage("src/test/snapshots/Q1-Q2_watch_main_transition.png")
    }

    @Test
    fun freeUnlimited() = capture("F1_watch_free_unlimited", WatchFlow.FreeGoal(choices(null, 3_000, 5_000, 10_000)))

    @Test
    fun freeThree() =
        capture("F1-3_watch_free_3km", WatchFlow.FreeGoal(choices(null, 3_000, 5_000, 10_000), selected = 1))

    /** WR16-2: 가장 넓은 숫자 "10"(피그마 F1-10). */
    @Test
    fun freeTen() =
        capture("F1-10_watch_free_10km", WatchFlow.FreeGoal(choices(null, 3_000, 5_000, 10_000), selected = 3))

    @Test
    fun freeConsent() = capture(
        "F1-3_watch_free_consent",
        WatchFlow.FreeGoal(
            choices(null, 3_000, 5_000, 10_000),
            selected = 1,
            notice = WatchFlow.Notice.LOCATION_CONSENT
        )
    )

    @Test
    fun battleOne() = capture("B1-1_watch_battle_1km", WatchFlow.BattleDistance(choices(1_000, 3_000, 5_000, 10_000)))

    /** WR16-2: 배틀런 끝 카드 "10". */
    @Test
    fun battleTen() = capture(
        "B1-10_watch_battle_10km",
        WatchFlow.BattleDistance(choices(1_000, 3_000, 5_000, 10_000), selected = 3)
    )

    @Test
    fun battleOffline() = capture(
        "B1-1_watch_battle_offline",
        WatchFlow.BattleDistance(choices(1_000, 3_000, 5_000, 10_000), isOnline = false)
    )

    /** TC-5868 배치 중 잠금(J24-12, D-500): 「폰에서 배치고사를 마치면 열려요」를 보이고 매칭 시작을 막는다. */
    @Test
    fun battleLocked() = capture(
        "B1-1_watch_battle_locked",
        WatchFlow.BattleDistance(choices(1_000, 3_000, 5_000, 10_000), selected = 2, isRandomLocked = true)
    )

    @Test
    fun selfDistance() = capture(
        "S1-3_watch_self_3km",
        WatchFlow.SelfDistance(
            listOf(
                WatchFlow.Choice(Distance(1_000), isEnabled = false),
                WatchFlow.Choice(Distance(3_000), record = Duration.ofSeconds(1_182)),
                WatchFlow.Choice(Distance(5_000), record = Duration.ofSeconds(1_990)),
                WatchFlow.Choice(Distance(10_000), record = Duration.ofSeconds(4_105)),
                WatchFlow.Choice(Distance(21_097), isEnabled = false)
            ),
            selected = 1
        )
    )

    @Test
    fun selfDistanceOffline() = capture(
        "S1-3_watch_self_offline",
        WatchFlow.SelfDistance(
            listOf(1_000L, 3_000L, 5_000L, 10_000L, 21_097L).map {
                WatchFlow.Choice(Distance(it), isEnabled = false, isOfflineLocked = true)
            }
        )
    )

    @Test
    fun celebDistance() = capture(
        "C1-5_watch_celeb_distance",
        WatchFlow.CelebDistance(
            listOf(
                WatchFlow.Choice(Distance(5_000), count = 3),
                WatchFlow.Choice(Distance(10_000), count = 2),
                WatchFlow.Choice(Distance(21_097), count = 0, isEnabled = false),
                WatchFlow.Choice(Distance(42_195), count = 1)
            )
        )
    )

    @Test
    fun celebList() = capture(
        "C2-5_watch_celeb_list",
        WatchFlow.CelebList(
            Distance(5_000),
            listOf(
                RecordOpponent(CachedTrack(celebrityTrack(1, 5_000, 1_250), Instant.EPOCH)),
                RecordOpponent(CachedTrack(celebrityTrack(2, 5_000, 1_420), Instant.EPOCH)),
                RecordOpponent(CachedTrack(celebrityTrack(3, 5_000, 1_610), Instant.EPOCH))
            )
        )
    )

    /** 고른 셀럽의 구간 기록을 받는 중(W31): 그 항목은 완주 시간 대신 "받는 중"이다. */
    @Test
    fun celebListFetching() {
        val celebrities = listOf(
            RecordOpponent(CachedTrack(celebrityListItem(1, 5_000, 1_250), Instant.EPOCH)),
            RecordOpponent(CachedTrack(celebrityListItem(2, 5_000, 1_420), Instant.EPOCH))
        )
        capture(
            "C2-5_watch_celeb_list_fetching",
            WatchFlow.CelebList(Distance(5_000), celebrities, selected = 1, fetching = celebrities[1])
        )
    }

    @Test
    fun matching() = capture("BMA_watch_matching", WatchFlow.Matching("SILVER", Distance(3_000), waitedSeconds = 7))

    @Test
    fun matched() = capture(
        "BMB_watch_matched",
        WatchFlow.Matched("SILVER", Distance(3_000), "민준", myGender = Gender.FEMALE, opponentGender = Gender.MALE)
    )

    @Test
    fun countdownFree() =
        capture("FC3_watch_countdown_free_3km", WatchFlow.Countdown(WatchMode.FREE, Distance(3_000), 3))

    @Test
    fun countdownConnecting() = capture(
        "SC3_watch_countdown_connecting",
        WatchFlow.Countdown(WatchMode.SELF, Distance(3_000), 3, isConnecting = true)
    )

    @Test
    fun countdownGo() = capture("BC1_watch_countdown_go", WatchFlow.Countdown(WatchMode.BATTLE, Distance(1_000), 0))

    @Test
    fun runFreeGoal() = capture(
        "FR3_watch_run_free_3km",
        WatchFlow.Running(WatchMode.FREE, progress(1_500, 585), Distance(3_000), isPausable = true)
    )

    @Test
    fun runFreeUnlimited() = capture(
        "FRinf_watch_run_free_unlimited",
        WatchFlow.Running(WatchMode.FREE, progress(520, 203), goal = null, banner = "이번 구간 6분 32초", isPausable = true)
    )

    @Test
    fun runBattle() = capture(
        "BR1_watch_run_battle",
        WatchFlow.Running(WatchMode.BATTLE, progress(500, 195), Distance(1_000), gapMeters = -6)
    )

    @Test
    fun runSelfLeadChanged() = capture(
        "SR3_watch_run_self_lead",
        WatchFlow.Running(WatchMode.SELF, progress(1_500, 585), Distance(3_000), gapMeters = 5, isLeadChanged = true)
    )

    @Test
    fun runAmbient() = capture(
        "FR3_watch_run_ambient",
        WatchFlow.Running(WatchMode.FREE, progress(1_500, 585), Distance(3_000), isPausable = true),
        isAmbient = true
    )

    @Test
    fun paused() = capture("FP3_watch_paused", WatchFlow.Paused(progress(1_500, 585, paused = true), Distance(3_000)))

    @Test
    fun resultFinish() = capture("FFinf_watch_result_finish", WatchFlow.Result(WatchResultHeadline.Finish, summary))

    @Test
    fun resultGoal() = capture("FG3_watch_result_goal", WatchFlow.Result(WatchResultHeadline.Goal, summary))

    @Test
    fun resultStop() = capture("FS3_watch_result_stop", WatchFlow.Result(WatchResultHeadline.Stop, summary))

    @Test
    fun resultWin() = capture(
        "BW1_watch_result_win",
        WatchFlow.Result(WatchResultHeadline.Win("민준", gapMeters = 12), summary)
    )

    @Test
    fun resultLose() = capture("BL1_watch_result_lose", WatchFlow.Result(WatchResultHeadline.Lose("민준", 8), summary))

    @Test
    fun resultBest() = capture("SW3_watch_result_best", WatchFlow.Result(WatchResultHeadline.Best(12), summary))

    @Test
    fun resultGood() = capture("SL3_watch_result_good", WatchFlow.Result(WatchResultHeadline.Good(5), summary))

    @Test
    fun resultPending() = capture("BW1_watch_result_pending", WatchFlow.Result(WatchResultHeadline.Pending, summary))

    /** 평균 심박이 없으면 그 칸을 비운다(FR-249, R13 D5). */
    @Test
    fun resultNoHeartRate() = capture(
        "SL3_watch_result_good_no_heart_rate",
        WatchFlow.Result(WatchResultHeadline.Good(42), summary.copy(averageHeartRate = null))
    )

    /** 폰 자유런 동행 표시(계획 5km): 워치 단독 FR3과 같은 모양(W35). */
    @Test
    fun companionRun() =
        capture("K2_watch_run", WatchFlow.Companion, companion.copy(isPausable = true, planDistanceMeters = 5_000))

    /** 폰 대결 중 폰 연결이 끊겼을 때. 근접 값이 없어 거리 차 칸이 비고 "폰 연결 끊김"을 띄운다. */
    @Test
    fun companionLinkLost() =
        capture("K2_watch_run_link_lost", WatchFlow.Companion, companion.copy(isLinkLost = true, heartRateBpm = null))

    @Test
    fun companionAmbient() = capture(
        "K2_watch_run_ambient",
        WatchFlow.Companion,
        companion.copy(isPausable = true, planDistanceMeters = 5_000),
        isAmbient = true
    )

    /** 폰 자유런 일시정지: 워치 단독 FP3과 같은 모양(계속·종료는 폰에 요청). */
    @Test
    fun companionPaused() = capture(
        "K4_watch_paused",
        WatchFlow.Companion,
        companion.copy(isPaused = true, isPausable = true, paceSecondsPerKm = null, planDistanceMeters = 5_000)
    )

    @Test
    fun companionBattleAhead() = capture(
        "K5_watch_battle_ahead",
        WatchFlow.Companion,
        companion.copy(
            elapsedSeconds = 940,
            distanceMeters = 2_840,
            planDistanceMeters = 3_000,
            proximity = WatchProximity(gapMeters = 18, isAhead = true, intervalMillis = 600)
        )
    )

    @Test
    fun companionBattleBehind() = capture(
        "K5_watch_battle_behind",
        WatchFlow.Companion,
        companion.copy(
            elapsedSeconds = 940,
            distanceMeters = 2_820,
            planDistanceMeters = 3_000,
            proximity = WatchProximity(gapMeters = 72, isAhead = false, intervalMillis = 1_510)
        )
    )

    /** 폰 대결인데 100m 밖이라 근접 값이 없다: 거리 차 칸을 비운다. */
    @Test
    fun companionBattleFar() = capture(
        "K5_watch_battle_far",
        WatchFlow.Companion,
        companion.copy(elapsedSeconds = 940, distanceMeters = 2_610, planDistanceMeters = 3_000)
    )

    @Test
    fun companionBattleLeadChanged() = capture(
        "K5_watch_battle_lead",
        WatchFlow.Companion,
        companion.copy(
            elapsedSeconds = 940,
            distanceMeters = 2_840,
            planDistanceMeters = 3_000,
            proximity = WatchProximity(gapMeters = 2, isAhead = true, intervalMillis = 300),
            leadKind = LeadKind.OVERTOOK
        )
    )
}

/** 찍을 때마다 바뀌지 않게 고정한 시각. */
private object FixedTimeSource : TimeSource {
    @Composable
    override fun currentTime(): String = "10:09"
}

/** 전환 중간 프레임을 찍는 시각(첫 프레임이 시작 시각을 잡는다). */
private const val TRANSITION_MIDDLE_MS = 64L

/** 작은 원형 워치(Wear OS small round, 192dp = 384px). */
private const val WATCH_SMALL_ROUND = "w192dp-h192dp-round-xhdpi"
