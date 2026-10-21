package com.a207.runkk.app.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * 화면 경로 이름(5.1.2). 화면 사이 이동은 이 이름으로만 한다(RunkkNavigator.navigate).
 * 인자가 없는 화면은 [Key](경로 이름 하나)로, 기록 ID 같은 인자가 있는 화면은 아래 인자 키(RunResult 등)로 백 스택에 올린다.
 * 경로를 등록하는 쪽(FeatureEntry 구현)은 화면 모듈이고, 이 파일은 이름만 정한다. 등록되지 않은 경로는 app이 "준비 중"으로 보인다.
 */
object Routes {
    // 시작·회원(ui.me)
    const val LOGIN = "login"
    const val SIGN_UP = "signup"

    // 하단 탭 다섯 개(셀프·셀럽·홈·크루·마이, SRS 5.1)

    /** 셀프 탭: 나와의 싸움 선택(ui.battle). */
    const val SELF = "battle/self"

    /** 셀럽 탭: 유명인 선택(ui.battle). */
    const val CELEB = "battle/celeb"
    const val HOME = "home"

    /** 크루 탭: 내 크루 또는 크루 찾기(ui.crew). */
    const val CREW = "crew"
    const val ME = "me"

    // 러닝(ui.run)

    /** 자유런 카운트다운·측정·화면 잠금(한 화면, RunViewModel). */
    const val RUN = "run"

    // 마이(ui.me)
    const val PROFILE_EDIT = "me/profile"
    const val SETTINGS = "me/settings"

    /** 자유런 설정(FR-048): 설정 변경 흐름(ChangeSettingUseCase)이 ui.me에 있어 ui.me가 그린다. */
    const val FREE_RUN_SETTINGS = "me/settings/run"
    const val NOTIFICATION_SETTINGS = "me/settings/notifications"
    const val CONSENTS = "me/consents"
    const val WITHDRAW = "me/withdraw"

    // 기록(ui.record)
    const val RECORDS = "records"
    const val GROWTH = "records/growth"
    const val REPORT = "records/report"
    const val RANKING = "records/ranking"
    const val COACH = "records/coach"

    // 알림(ui.notification)
    const val NOTIFICATIONS = "notifications"

    // 대결(ui.battle), 크루(ui.crew), Runimal(ui.runimal), 아바타(ui.avatar): 화면은 A3b 단계가 등록한다.

    /** 배틀런(랜덤 대결) 거리 고르기·매칭 대기. */
    const val BATTLE_RANDOM = "battle/random"
    const val BATTLE_HISTORY = "battle/history"
    const val RUNIMAL = "runimal"
    const val AVATAR = "avatar"

    /** 아바타 만들기(가입 5단계에서 이어진다, FR-155·FR-158). */
    const val AVATAR_SETUP = "avatar/setup"
    const val SHOP = "avatar/shop"

    /** 하단 탭 경로(왼쪽부터). */
    val TABS: List<String> = listOf(SELF, CELEB, HOME, CREW, ME)

    /** Navigation 3 백 스택에 올리는 경로 키. 경로 이름 문자열 하나를 담는다. */
    @Serializable
    data class Key(val route: String) : NavKey

    /** 러닝 결과(B4). 측정이 끝난 기록 ID(앱이 만든 UUID). */
    @Serializable
    data class RunResult(val recordId: String) : NavKey

    /** 기록 상세(C2). */
    @Serializable
    data class RecordDetail(val recordId: String) : NavKey

    /** 기록 카드 팝업·공유(러닝 카드 앞뒤, FR-068·FR-069). ui.record가 그린다(ui.record → ui.share). */
    @Serializable
    data class RecordCard(val recordId: String) : NavKey

    // 대결(ui.battle) 인자 화면(A3b)

    /**
     * 대결 진행(랜덤·고스트·나와의 싸움·유명인). 상대 표시명·대결 거리는 매칭 성사(MatchFound)·기록 대결 시작 정보에서 넘긴다.
     * [opponentKind]는 RANDOM·SELF·CELEBRITY(고스트는 RANDOM으로 보인다, Q-02). [startAtEpochMillis]는 매칭 화면이 이미 받은
     * 시작 시각(측정 중에 화면을 넘겨받을 때 경과 시간 기준)이고 모르면 null이다.
     */
    @Serializable
    data class BattleRunning(
        val battleId: Long,
        val opponentName: String,
        val distanceMeters: Long,
        val opponentKind: String,
        val startAtEpochMillis: Long? = null
    ) : NavKey

    /** 대결 결과(비교표, FR-093·FR-108). */
    @Serializable
    data class BattleResult(val battleId: Long) : NavKey

    // 크루(ui.crew) 화면(A3b)

    /** 크루 만들기(FR-110). */
    const val CREW_CREATE = "crew/create"

    /** 내 크루 홈(레이스·크루런·응원보드·크루원 탭). 크루 이름을 돌려주는 조회가 없어 넘긴다. */
    @Serializable
    data class CrewHome(val crewId: Long, val crewName: String) : NavKey

    /** 크루 관리(크루장, FR-113~FR-116·FR-123·FR-127). */
    @Serializable
    data class CrewManage(val crewId: Long, val crewName: String) : NavKey

    /** 크루런 모집글 쓰기(FR-132). */
    @Serializable
    data class CrewRunWrite(val crewId: Long) : NavKey

    /**
     * 크루런 대기실 → 카운트다운 → 진행 중 중계(한 화면, CrewRunCoordinator 상태). [distanceMeters]는 크루런 거리이고
     * 모르면 null이다(게시판 목록에 거리 항목이 아직 없다).
     */
    @Serializable
    data class CrewRunLobby(val postId: Long, val distanceMeters: Long?) : NavKey

    /** 크루런 결과(FR-191). */
    @Serializable
    data class CrewRunResult(val postId: Long) : NavKey
}
