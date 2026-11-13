package com.a207.runkk.app.ui.crew

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.MetaItem
import com.a207.runkk.app.core.designsystem.MetaRow
import com.a207.runkk.app.core.designsystem.RunkkButton
import com.a207.runkk.app.core.designsystem.RunkkButtonStyle
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkSectionHeader
import com.a207.runkk.app.core.designsystem.RunkkSectionHeaderSize
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkStatTile
import com.a207.runkk.app.core.designsystem.RunkkStatTileDefaults
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.StatTileValue
import com.a207.runkk.app.domain.crew.CrewDetail
import com.a207.runkk.app.domain.crew.CrewList
import com.a207.runkk.app.domain.crew.JoinPolicy
import com.a207.runkk.app.domain.crew.MyCrew
import com.a207.runkk.app.domain.crew.RecentCrewRun
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 크루 정보 화면(피그마 F-02·F-02b·F-02c): 가운데 제목 "크루 정보"와 뒤로, 상세 내용, 아래 가입 단추. 시스템 뒤로 가기도 닫는다.
 * 크루 찾기와 크루 검색이 함께 쓴다.
 */
@Composable
internal fun CrewDetailPage(
    crew: CrewList.Item,
    info: CrewDetail?,
    pending: MyCrew.PendingJoin?,
    joinRemaining: Duration?,
    isJoining: Boolean,
    onBack: () -> Unit,
    onJoin: () -> Unit,
    onCancel: (MyCrew.PendingJoin) -> Unit
) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(RunkkColors.Background)) {
        RunkkTopBar(title = stringResource(R.string.crew_detail_title), onBack = onBack)
        CrewDetailContent(
            crew,
            isJoining = isJoining,
            onJoin = onJoin,
            info = info?.takeIf { it.crewId == crew.crewId },
            pending = pending,
            joinRemaining = joinRemaining,
            onCancel = onCancel,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 크루 상세(크루 밖 열람 범위, CR-163·결정 C9): 프로필 줄(이미지·크루명·개설일·인원), 소개, 태그, 통계 2칸(지난 주 달린 거리·누적 크루런
 * 횟수), 최근 크루런. 통계·최근 크루런·개설일은 크루 정보(GET /crews/{id}, [info])를 읽은 뒤에 채운다. 아래는 가입 신청 대기 안내와
 * 취소, 24시간 가입 제한 카운트다운(회색, [joinRemaining]), 정원 참, 가입 단추 중 하나다.
 */
@Composable
internal fun CrewDetailContent(
    crew: CrewList.Item,
    isJoining: Boolean,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
    info: CrewDetail? = null,
    pending: MyCrew.PendingJoin? = null,
    joinRemaining: Duration? = null,
    onCancel: (MyCrew.PendingJoin) -> Unit = {}
) {
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CrewProfileRow(
                crew.name,
                info?.logoUrl ?: crew.logoUrl,
                info?.createdAt,
                info?.headcount ?: crew.headcount
            )
            crew.intro?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = RunkkTextStyles.text(16, lineHeight = 22),
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.padding(horizontal = 5.dp)
                )
            }
            CrewTagPills(crew.tags)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailStatTile(
                    stringResource(R.string.crew_detail_last_week),
                    info?.let { kmLabel(it.lastWeekDistance) },
                    Modifier.weight(1f)
                )
                DetailStatTile(
                    stringResource(R.string.crew_detail_crew_run_count),
                    info?.let { stringResource(R.string.crew_detail_crew_run_count_value, it.crewRunCount) },
                    Modifier.weight(1f)
                )
            }
            if (info != null) RecentCrewRuns(info.recentCrewRuns)
        }
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp)) {
            when {
                pending != null -> PendingNotice(pending, isBusy = isJoining, onCancel = { onCancel(pending) })
                joinRemaining != null -> CooldownButton(joinRemaining)
                else -> JoinButton(info?.headcount ?: crew.headcount, crew.joinPolicy, isJoining, onJoin)
            }
        }
    }
}

/** 통계 칸(피그마 F-02 2칸 그리드): 테두리 통계 칸(이름 12/18, 값 20/24 Bold). 값을 모르면 빈 값. */
@Composable
private fun DetailStatTile(label: String, value: String?, modifier: Modifier = Modifier) {
    RunkkStatTile(label, StatTileValue(value ?: RunkkFormat.EMPTY), modifier, style = RunkkStatTileDefaults.Outlined)
}

/** 최근 크루런(피그마 F-02): 제목 14 Bold + 줄마다 날짜(12/18)와 인원·총 거리 메타 줄. 없으면 안내 한 줄. */
@Composable
private fun RecentCrewRuns(runs: List<RecentCrewRun>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkSectionHeader(stringResource(R.string.crew_detail_recent_title), size = RunkkSectionHeaderSize.Compact)
        if (runs.isEmpty()) {
            Text(
                stringResource(R.string.crew_detail_recent_empty),
                style = RunkkTextStyles.Meta,
                color = RunkkColors.TextSecondary
            )
        }
        runs.forEach { run ->
            RunkkCard(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                shape = RunkkShapes.ExtraLarge,
                contentPadding = PaddingValues(14.dp),
                borderColor = RunkkColors.Border,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    recentRunLabel(LocalContext.current.resources, run.scheduledAt),
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.TextPrimary
                )
                // 가운뎃점 대신 아이콘 메타 줄(결정 P3): "[n]명 참여", "총 [n]km"
                MetaRow(
                    listOf(
                        MetaItem(
                            RunkkIcons.Group,
                            stringResource(R.string.crew_detail_recent_participants, run.participantCount)
                        ),
                        MetaItem(
                            RunkkIcons.DirectionsRun,
                            stringResource(R.string.crew_detail_recent_total, RunkkFormat.km(run.totalDistance))
                        )
                    )
                )
            }
        }
    }
}

/** "10.21 (수) 저녁 7:30"(피그마 F-02 최근 크루런 일시, 기기 시간대): 새벽 0~5시, 오전 6~11시, 오후 12~17시, 저녁 18~23시. */
internal fun recentRunLabel(resources: Resources, at: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val time = at.atZone(zone)
    val period = resources.getString(
        when (time.hour) {
            in 0..5 -> R.string.crew_period_dawn
            in 6..11 -> R.string.crew_period_morning
            in 12..17 -> R.string.crew_period_afternoon
            else -> R.string.crew_period_evening
        }
    )
    val hour12 = (time.hour % HALF_DAY).let { if (it == 0) HALF_DAY else it }
    return resources.getString(
        R.string.crew_detail_recent_when,
        RECENT_DAY.format(time),
        period,
        hour12,
        String.format(Locale.ROOT, "%02d", time.minute)
    )
}

private const val HALF_DAY = 12

private val RECENT_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("M.d (E)", Locale.KOREAN)

/** 가입 단추(피그마 Lime Large 52): 정원이 차면 회색 "정원이 찼어요", 바로 가입이면 "가입하기", 승인제면 "가입 신청하기". */
@Composable
private fun JoinButton(headcount: Int, joinPolicy: JoinPolicy, isJoining: Boolean, onJoin: () -> Unit) {
    val full = headcount >= CREW_MAX
    RunkkButton(
        text = stringResource(
            when {
                full -> R.string.crew_detail_full
                joinPolicy == JoinPolicy.FREE -> R.string.crew_detail_join_now
                else -> R.string.crew_detail_join_apply
            }
        ),
        onClick = onJoin,
        isEnabled = !full,
        isLoading = isJoining,
        modifier = Modifier.fillMaxWidth()
    )
}

/** 24시간 가입 제한(피그마 F-02c): 회색 "[hh:mm:ss] 후에 가입할 수 있어요". 누를 수 없다. */
@Composable
private fun CooldownButton(remaining: Duration) {
    RunkkButton(
        text = stringResource(R.string.crew_detail_cooldown, countdownLabel(remaining)),
        onClick = {},
        style = RunkkButtonStyle.Gray,
        isEnabled = false,
        modifier = Modifier.fillMaxWidth()
    )
}

/** 가입 신청 대기: "가입 신청을 보냈어요", "M.d까지 결정돼요", 신청 취소(피그마에 없는 앱 상태, 유지). */
@Composable
private fun PendingNotice(pending: MyCrew.PendingJoin, isBusy: Boolean, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                stringResource(R.string.crew_detail_pending_title),
                style = RunkkTextStyles.BodyStrong,
                color = RunkkColors.TextPrimary
            )
            Text(
                stringResource(
                    R.string.crew_detail_pending_until,
                    shortDateLabel(pending.requestedAt.plus(Duration.ofDays(JOIN_REQUEST_DAYS)))
                ),
                style = RunkkTextStyles.Meta,
                color = RunkkColors.TextSecondary
            )
        }
        RunkkButton(
            stringResource(R.string.crew_find_cancel_confirm_action),
            onClick = onCancel,
            style = RunkkButtonStyle.Outline,
            isEnabled = !isBusy,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 가입 신청은 신청 뒤 7일 안에 결정이 없으면 만료된다(FR-121, Q-40). */
private const val JOIN_REQUEST_DAYS = 7L

/** 상세와 만들기 화면의 가입 방식 이름(와이어 I1, I2): "바로 가입", "승인 후 가입". 목록 배지는 짧은 이름([labelRes])이다. */
internal val JoinPolicy.longLabelRes: Int
    @StringRes get() = when (this) {
        JoinPolicy.APPROVAL -> R.string.crew_join_policy_approval_long
        JoinPolicy.FREE -> R.string.crew_join_policy_free
    }
