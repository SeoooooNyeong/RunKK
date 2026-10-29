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

    /** 게스트로 시작(FR-197): 가입과 같은 동의 화면 하나를 게스트 갈래로 보인다. */
    const val GUEST_START = "signup/guest"

    // 하단 탭 다섯 개(크루·기록런·홈·랭킹·마이, v0.14 D-250)

    /** 크루 탭: 내 크루 또는 크루 찾기(ui.crew). 1차 배포에서는 잠긴 탭이다(D-253). */
    const val CREW = "crew"

    /** 기록런 탭: 셀프런 | 셀럽런 분절 하나(ui.battle RecordRunScreen, D-250). */
    const val RECORD_RUN = "battle/record"
    const val HOME = "home"

    /** 랭킹 탭: 개인 랭킹(ui.record RankingScreen, D-250, D-286). v0.14에서 값을 "records/ranking"에서 바꿨다. */
    const val RANKING = "ranking"
    const val ME = "me"

    /** 배틀런 화면(랜덤 | 친구 분절, ui.battle BattleModeScreen, D-268). 탭이 아니지만 하단 탭을 보이고 홈을 고른 상태로 둔다. */
    const val BATTLE_MODE = "battle/mode"

    /** 셀프 탭(v0.13): 나와의 싸움 선택(ui.battle). v0.14에서 탭에서 뺐다. 기록런 화면이 대신하면 지운다(D-250, A3). */
    const val SELF = "battle/self"

    /** 셀럽 탭(v0.13): 유명인 선택(ui.battle). v0.14에서 탭에서 뺐다. 기록런 화면이 대신하면 지운다(D-250, A3). */
    const val CELEB = "battle/celeb"

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

    /** 기록 화면을 "대결" 탭으로 연다(D-281). 대결 결과·기록런·알림함의 "함께 달린 기록"이 여기로 온다. */
    const val RECORDS_BATTLES = "records/battles"
    const val GROWTH = "records/growth"

    /** 런바디(v0.14에서 리포트 화면 제목을 바꿨다, CR-32). 경로 이름은 그대로다. */
    const val REPORT = "records/report"
    const val COACH = "records/coach"

    // 알림(ui.notification)
    const val NOTIFICATIONS = "notifications"

    // 대결(ui.battle), 크루(ui.crew), Runimal(ui.runimal), 아바타(ui.avatar): 화면은 A3b 단계가 등록한다.

    /**
     * 배틀런(랜덤 대결) 거리 고르기·매칭 대기(v0.13). v0.14는 배틀런 화면(BATTLE_MODE)이 대신한다. A3가 대체 화면을 만들면 지운다.
     */
    const val BATTLE_RANDOM = "battle/random"
    const val RUNIMAL = "runimal"

    /** Runimal 16유형(G-5, ui.runimal RunimalTypesScreen, D-285). */
    const val RUNIMAL_TYPES = "runimal/types"
    const val AVATAR = "avatar"

    /** 아바타 만들기(가입 5단계에서 이어진다, FR-155·FR-158). */
    const val AVATAR_SETUP = "avatar/setup"
    const val SHOP = "avatar/shop"

    /** 아바타 랜드마크 배경 고르기(FR-201, FR-202). 알림 Screen.AVATAR_BACKGROUND가 여기로 온다. */
    const val AVATAR_BACKGROUND = "avatar/background"

    /** 하단 탭 경로(왼쪽부터, D-250). */
    val TABS: List<String> = listOf(CREW, RECORD_RUN, HOME, RANKING, ME)

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

    /** 다른 러너 프로필(F8, FR-005). 회원 ID. ui.me가 그린다. 크루원 목록·랭킹 등에서 연다. */
    @Serializable
    data class MemberProfile(val memberId: Long) : NavKey

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

    /**
     * 홈의 배틀런 시작(FR-080): 홈 배틀런 탭에서 고른 대결 거리(1·3·5·10km, 처음엔 기본 대결 거리 FR-109)로 바로 매칭을 요청한다.
     * 거리는 홈이 고르고 매칭 화면(ui.battle)은 이 인자로 받는다(화면 모듈끼리 직접 의존하지 않는다).
     */
    @Serializable
    data class BattleQuickMatch(val distanceMeters: Long) : NavKey

    /** Runimal 유형 상세(G-6, D-285). [type]은 서버 RunimalType 이름. */
    @Serializable
    data class RunimalType(val type: String) : NavKey

    /**
     * 평가 결과(G-11, ui.runimal EvaluationScreen, D-284). 러닝·대결 결과 응답의 evaluation이 있을 때 한 번 연다.
     * [tier]는 서버 Tier 이름, [validRuns]는 evaluation.validRuns, [runimalType]은 확정 유형(첫 배치·미확정이면 null).
     */
    @Serializable
    data class Evaluation(val tier: String, val validRuns: Int, val runimalType: String?) : NavKey

    /** 대결 결과(비교표, FR-093·FR-108). */
    @Serializable
    data class BattleResult(val battleId: Long) : NavKey

    // 크루(ui.crew) 화면(A3b)

    /** 크루 만들기(FR-110). */
    const val CREW_CREATE = "crew/create"

    /** 내 크루 홈(레이스·크루런·응원보드·크루원 탭). 크루 이름·역할은 화면이 GET /crews/{id}로 읽는다. */
    @Serializable
    data class CrewHome(val crewId: Long) : NavKey

    /**
     * 크루 관리(크루장·운영진, FR-113~FR-116·FR-123·FR-127·FR-204). 보이는 기능은 내 역할(3.0.3)로 정한다.
     * 지금 크루 정보는 화면이 GET /crews/{id}로 읽는다.
     */
    @Serializable
    data class CrewManage(val crewId: Long) : NavKey

    /** 일정 쓰기(FR-132): 크루런 또는 대면 모임을 고른다. 이름은 v0.10 크루런 모집글 쓰기에서 이어 왔다. */
    @Serializable
    data class CrewRunWrite(val crewId: Long) : NavKey

    /** 크루런 대기실 → 카운트다운 → 진행 중 중계(한 화면, CrewRunCoordinator 상태). 크루런 거리는 대기실 응답이 준다. */
    @Serializable
    data class CrewRunLobby(val postId: Long) : NavKey

    /** 크루런 결과(FR-191). */
    @Serializable
    data class CrewRunResult(val postId: Long) : NavKey

    /** 투표 만들기(FR-206). */
    @Serializable
    data class CrewPollWrite(val crewId: Long) : NavKey

    /** 게시판 글쓰기(본문 + 사진 0~5장, FR-211). */
    @Serializable
    data class CrewBoardWrite(val crewId: Long) : NavKey

    /** 공지 쓰기(FR-205, 와이어프레임 M1). 크루장·운영진. */
    @Serializable
    data class CrewNoticeWrite(val crewId: Long) : NavKey

    /** 투표 응답과 결과(FR-206·FR-207, 와이어프레임 M3). 작성자·시각은 크루 투표 목록에서 찾는다. */
    @Serializable
    data class CrewPoll(val crewId: Long, val postId: Long) : NavKey

    /** 대면 모임 상세와 참석 응답(FR-145·FR-207, 와이어프레임 M7). */
    @Serializable
    data class CrewMeetupDetail(val crewId: Long, val postId: Long) : NavKey

    /** 주간 공동 목표 정하기(FR-138, 와이어프레임 M8). 크루장·운영진. */
    @Serializable
    data class CrewGoalSet(val crewId: Long) : NavKey

    // 크루 채팅(ui.chat, :feature:chat) 화면(A3b)

    /** 크루 채팅방(FR-208~FR-210, FR-213, FR-214). ui.chat의 ChatFeatureEntry가 등록한다(6.1.14). */
    @Serializable
    data class CrewChat(val crewId: Long) : NavKey

    // 알림 이동 화면(FR-178, D-150): 서버 notification.Screen 이름과 같은 v0.11 크루 화면 4종. 알림은 크루 ID를 경로로 넘기지 않으므로
    // 내 크루(GET /crews/me)의 해당 탭이나 채팅방을 연다. ui.crew가 등록한다(채팅은 내 크루를 확인한 뒤 CrewChat(crewId)으로 넘긴다).

    /** 내 크루 홈의 공지 탭(Screen CREW_NOTICE). */
    const val CREW_NOTICE = "crew/notice"

    /** 내 크루 홈의 투표 탭(Screen CREW_POLL). */
    const val CREW_POLL = "crew/poll"

    /** 내 크루 홈의 일정 탭(Screen CREW_SCHEDULE). */
    const val CREW_SCHEDULE = "crew/schedule"

    /** 내 크루 채팅방(Screen CREW_CHAT). */
    const val CREW_CHAT = "crew/chat"
}
