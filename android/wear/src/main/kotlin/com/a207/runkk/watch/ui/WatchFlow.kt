package com.a207.runkk.watch.ui

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.domain.run.RunProgress
import com.a207.runkk.watch.battle.RecordOpponent
import com.a207.runkk.watch.battle.WatchResultHeadline
import java.time.Duration

/**
 * 지금 워치가 보이는 화면과 그 화면의 값(3.5, D-374, 상태도 2.4.25). 상태는 14개이고 [WatchFlowViewModel]이 바꾼다.
 * 화면 함수는 상태 하나를 그리기만 한다.
 */
sealed interface WatchFlow {
    /** 로그인 정보 없음(FR-242). 러닝을 시작할 수 없다. */
    data object NotLoggedIn : WatchFlow

    /**
     * 메인 4안 캐러셀(FR-243). [gender]는 내 기본 캐릭터 성별(FR-162, 모르면 null → 남자 그림). [gps]는 GPS 예열 상태(W53, 예열하지
     * 않으면 null)다.
     */
    data class Main(
        val mode: WatchMode = WatchMode.FREE,
        val gender: Gender? = null,
        val notice: Notice? = null,
        val gps: GpsStatus? = null
    ) : WatchFlow

    /** 거리 카드를 고르는 화면 넷의 공통 값. [selected]는 [choices]에서 가운데 카드 위치다. */
    sealed interface Picker : WatchFlow {
        val choices: List<Choice>
        val selected: Int
        val notice: Notice?

        /** 가운데 카드. */
        val current: Choice get() = choices[selected.coerceIn(0, choices.lastIndex)]
    }

    /** 자유런 목표 거리(FR-244): 무제한·3·5·10km. */
    data class FreeGoal(
        override val choices: List<Choice>,
        override val selected: Int = 0,
        override val notice: Notice? = null
    ) : Picker

    /**
     * 배틀런 대결 거리(FR-257): 1·3·5·10km. 서버에 닿지 않으면 [isOnline]이 거짓이고 매칭 시작을 막는다(FR-251).
     * [runsLeft]가 있으면 랜덤 대결이 잠겨 있다(언랭, FR-080, 폰 RandomBattleGate와 같은 규칙): 1 이상은 남은 유효 러닝 수, 0은 남은 수를
     * 모르는 잠금이다. 잠겨 있으면 매칭 시작을 막고 "러닝을 n번 더 뛰면 배틀런이 가능해요!"를 보인다.
     */
    data class BattleDistance(
        override val choices: List<Choice>,
        override val selected: Int = 0,
        val isOnline: Boolean = true,
        override val notice: Notice? = null,
        val runsLeft: Int? = null
    ) : Picker

    /** 셀프런 거리(FR-258): 1·3·5·10km·하프. 카드에 내 최고 기록 시간, 기록 없는 거리는 누를 수 없다(W10). */
    data class SelfDistance(
        override val choices: List<Choice>,
        override val selected: Int = 0,
        override val notice: Notice? = null
    ) : Picker

    /** 셀럽런 거리(FR-259): 5·10km·하프·풀. 카드에 셀럽 기록 수, 기록 없는 거리는 누를 수 없다. */
    data class CelebDistance(
        override val choices: List<Choice>,
        override val selected: Int = 0,
        override val notice: Notice? = null
    ) : Picker

    /** 고른 거리의 셀럽 기록 목록(FR-259, W09). */
    data class CelebList(
        val distance: Distance,
        val celebrities: List<RecordOpponent>,
        val selected: Int = 0,
        val notice: Notice? = null,
        val fetching: RecordOpponent? = null
    ) : WatchFlow

    /** 배틀런 매칭 중(BMA, FR-257): 내 티어, 대결 거리, 기다린 초. */
    data class Matching(val tier: String?, val distance: Distance, val waitedSeconds: Long = 0) : WatchFlow

    /** 매칭 완료(BMB, FR-086): 나와 상대 캐릭터, 티어, 거리. */
    data class Matched(
        val tier: String?,
        val distance: Distance,
        val opponentName: String,
        val myGender: Gender?,
        val opponentGender: Gender?
    ) : WatchFlow

    /**
     * 출발 3·2·1·GO(FR-180, FR-087). [secondsLeft]가 0이면 GO다. 자유런 무제한은 [distance]가 null이다.
     * [isConnecting]이면 셀프·셀럽런이 서버 대결을 여는 중이라 숫자 대신 "연결 중"을 보인다(서버가 답하면 3부터 센다).
     */
    data class Countdown(
        val mode: WatchMode,
        val distance: Distance?,
        val secondsLeft: Int,
        val isConnecting: Boolean = false
    ) : WatchFlow

    /**
     * 측정 중(FR-246). [goal]은 목표·대결 거리(자유런 무제한은 null), [gapMeters]는 대결 상대와의 거리 차(앞서면 양수, 모르면 null),
     * [banner]는 안내 문구(FR-254), [isLeadChanged]는 방금 앞서고 뒤처짐이 바뀌었다("역전!", FR-188), [leadKind]는 그 방향이다(있으면 띠 색을 정한다). [isPausable]은 자유런만 참이다.
     * [isGpsSearching]은 이 회에 위치를 아직 받지 못했거나 마지막 위치 뒤 10초가 지났다("GPS 찾는 중", W53)이다.
     */
    data class Running(
        val mode: WatchMode,
        val progress: RunProgress,
        val goal: Distance?,
        val gapMeters: Int? = null,
        val banner: String? = null,
        val isLeadChanged: Boolean = false,
        val leadKind: LeadKind? = null,
        val isPausable: Boolean = false,
        val notice: Notice? = null,
        val isGpsSearching: Boolean = false
    ) : WatchFlow

    /** 자유런 일시정지(FR-247): 그때까지의 누적 거리와 계속·종료. [banner]는 폰이 보낸 안내 문구다(동행 표시, FR-195). */
    data class Paused(
        val progress: RunProgress,
        val goal: Distance?,
        val notice: Notice? = null,
        val banner: String? = null
    ) : WatchFlow

    /** 결과(FR-249). */
    data class Result(val headline: WatchResultHeadline, val summary: RunSummaryView) : WatchFlow

    /**
     * 폰에서 시작한 러닝의 동행 표시(FR-042, D-380, W07). 값은 WatchViewModel(WatchUiState)이 내고, 화면은 그 값을 [Running]·[Paused]로
     * 옮겨 워치 단독 측정 화면과 같은 모양으로 그린다(W35).
     */
    data object Companion : WatchFlow

    /**
     * 거리 카드 하나. [distance]가 null이면 자유런 무제한이다. [record]는 셀프런 내 최고 기록 시간, [count]는 셀럽 기록 수다.
     * [isEnabled]가 거짓이면 누를 수 없다(기록 없음). [isOfflineLocked]는 셀프 기록을 받은 적 없이 오프라인이라 누를 수 없는 카드다(W32).
     */
    data class Choice(
        val distance: Distance?,
        val record: Duration? = null,
        val count: Int? = null,
        val isEnabled: Boolean = true,
        val isOfflineLocked: Boolean = false
    )

    /** 메인의 GPS 상태 표시(W53): 예열 중 찾는 중·준비됨, 워치 위치 꺼짐(누르면 위치 설정). */
    enum class GpsStatus {
        SEARCHING,
        READY,
        LOCATION_OFF
    }

    /** 화면에 잠깐 띄우는 안내. 문구는 화면이 문자열 리소스로 바꾼다. */
    enum class Notice {
        /** 서버 위치 수집 동의 없음(FR-245). */
        LOCATION_CONSENT,

        /** 워치 OS 위치 권한을 허용하지 않음(FR-245). */
        LOCATION_PERMISSION,

        /** 워치 시스템 위치가 꺼져 있어 측정을 시작하지 않음(W52). 누르면 위치 설정을 연다. */
        LOCATION_OFF,

        /** 폰이 측정 중이라 워치 시작 거부(FR-252). */
        PHONE_RUNNING,

        /** 서버에 닿지 않아 배틀런 매칭을 막음(FR-251). */
        BATTLE_OFFLINE,

        /** 매칭 거부·실패·연결 끊김. */
        MATCH_FAILED,

        /** 랜덤 대결 잠금(언랭, 서버 RANKED_ONLY, FR-080). 문구는 BattleDistance.runsLeft로 남은 횟수를 넣는다. */
        RANDOM_LOCKED,

        /** 이미 매칭을 기다리거나 대결 중(서버 ALREADY_IN_BATTLE·ALREADY_MATCHED). */
        ALREADY_IN_BATTLE,

        /** 서버 매칭 대기열을 쓸 수 없음(QUEUE_UNAVAILABLE). */
        QUEUE_UNAVAILABLE,

        /** 서버가 받지 않는 대결 거리(BATTLE_DISTANCE_INVALID). */
        DISTANCE_INVALID,

        /** 셀프런 서버 시작 거부: 그 거리 기록 없음(NO_RECORD). */
        NO_RECORD,

        /** 셀럽런 서버 시작 거부: 내려간 셀럽 기록(CELEBRITY_REMOVED). */
        CELEBRITY_REMOVED,

        /** 셀럽 구간 기록을 받아 두지 않았는데 연결이 없음(W31). */
        CELEB_OFFLINE,

        /** 서버가 잠시 답하지 못함(셀럽 구간 기록 받기 5xx 등, W31). */
        RETRY_LATER,

        /** 측정을 시작하지 못함(포그라운드 서비스 거부 등). */
        START_FAILED,

        /** 측정 서비스가 운동 세션을 열지 못했거나 밖에서 끝나 기록 없이 멈춤. */
        MEASUREMENT_STOPPED,

        /** 거리 0으로 길게 눌러 끝내 기록을 저장하지 않음(UML 5a, R13 D4). */
        NOT_SAVED,

        /** 종료를 짧게 눌렀다(FR-181, FR-248). */
        HOLD_TO_END,

        /** 동행 표시 중 폰과 연결이 끊겼다(6.1.15 "연결 끊김 표시"). */
        LINK_LOST,

        /** 동행 표시에서 보낸 요청을 폰이 연결되지 않아 보내지 못했다(FR-183). */
        PHONE_NOT_CONNECTED
    }
}
