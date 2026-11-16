package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RouteLine
import com.a207.runkk.app.core.model.RunViewMode
import com.a207.runkk.app.core.model.RunnerLook
import com.a207.runkk.app.domain.account.MyAvatar
import com.a207.runkk.app.domain.live.BattleGuidance
import java.time.Duration
import java.time.LocalDate

data class BattleRunningUiState(
    val battleId: Long = 0,
    val opponentName: String = "",
    val distance: Distance = Distance(DEFAULT_METERS),
    val kind: OpponentKind = OpponentKind.RANDOM,
    val phase: BattlePhase = BattlePhase.STARTING,
    /** 측정 시작까지 남은 초(COUNTDOWN). */
    val countdown: Long? = null,
    /** 나 − 상대(m), 앞서면 양수. 첫 진행값 전이면 null. */
    val gapMeters: Int? = null,
    val opponentDistance: Distance? = null,
    /** 내 누적 거리·경과·페이스(조정자가 옮긴 측정 진행값). */
    val myDistance: Distance = Distance.ZERO,
    val elapsed: Duration = Duration.ZERO,
    val currentPace: Pace? = null,
    val averagePace: Pace? = null,
    /** 랜덤 대결 상대 연결 상태 문구(FR-088). 기록 상대·첫 상대 진행값 전이면 null. */
    val connectionLabel: UiText? = null,
    /** 내 실시간 연결이 끊겨 다시 잇는 중(배틀 QA 5번). 상대 진행값이 낡았을 수 있다. */
    val isLiveLost: Boolean = false,
    /** 방금 바뀐 앞뒤 순서 안내(잠깐 보인다, FR-188). */
    val guidance: BattleGuidance.Kind? = null,
    /** 이 화면에서 본 추월 횟수. 공식 횟수는 결과 비교표(서버)다. */
    val overtakes: Int = 0,
    /** 먼저 도착한 사람(종료 카운트다운, FR-107). */
    val arrivedName: String? = null,
    val closingSeconds: Long? = null,
    /** 종료 카운트다운이 시작될 때의 남은 초(보조 기술에 한 번 알리는 제한 시간, 매초 읽지 않게 고정). */
    val closingLimitSeconds: Long? = null,
    val isConfirmingForfeit: Boolean = false,
    /** 셀프런·셀럽런 취소 확인 창(기록 없이 끝내기, 사용자 요청 2026-10-30). */
    val isConfirmingCancel: Boolean = false,
    val hint: UiText? = null,
    val error: UiText? = null,
    /** 내 경로선(측정 중, D-261). 상대 마커는 이 경로 위 "내 거리 + 격차" 지점이다(D-262). */
    val route: RouteLine = RouteLine.EMPTY,
    /** 상대 기록(셀프런 과거의 나, 셀럽): 카운트다운 "5km 기록 30:12"(D-264). */
    val opponentRecord: Duration? = null,
    /** 과거의 나 기록 날짜("10월 2일의 나"). */
    val opponentRecordedOn: LocalDate? = null,
    /** 셀럽런 카운트다운의 그 거리 내 최고 기록(없으면 "첫 도전"). */
    val myBest: Duration? = null,
    /** 상대 성별(지도 위 상대 그림). 셀럽·과거의 나·모르면 null(남자 그림). */
    val opponentGender: Gender? = null,
    /** 셀럽 그림 키(카운트다운·지도 위 셀럽 그림). 없으면 공통 그림. */
    val celebrityFigure: String? = null,
    /** 배치고사 판 번호(Routes.BattleRunning.placementRound). 있으면 지도 위 상대를 점선 원 「?」로 그린다(FR-276, J24-3). */
    val placementRound: Int? = null,
    /** 상대 아바타 모습(트랙 보기, [임시:D-성-265]). 배치고사·고스트·셀럽이거나 받지 못했으면 null(성별 기본 모습)이다. */
    val opponentLook: RunnerLook? = null,
    /** 고른 보기(기기에 저장한 값, 처음은 트랙, [임시:D-성-264]). 지도를 띄울 수 없으면 화면이 트랙으로 그린다. */
    val viewMode: RunViewMode = RunViewMode.TRACK,
    /** 내 아바타(트랙 배경 키, 이름표와 지도 위 내 얼굴). 읽기 전이거나 못 읽으면 null(기본 배경·기본 얼굴)이다. */
    val myAvatar: MyAvatar? = null
) {
    /** 측정 중인가(진행·종료 카운트다운). */
    val isMeasuring: Boolean get() = phase == BattlePhase.RUNNING || phase == BattlePhase.CLOSING

    /** 상대 그림을 숨기는가(배치고사 상대, MatchUiState.isOpponentHidden과 같다). */
    val isOpponentHidden: Boolean get() = placementRound != null

    private companion object {
        const val DEFAULT_METERS = 5_000L
    }
}
