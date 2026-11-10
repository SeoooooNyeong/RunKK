package com.a207.runkk.app.ui.record.runbody

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.domain.record.RunSummary
import com.a207.runkk.app.domain.runbody.RunBodyAxis
import com.a207.runkk.app.domain.runbody.RunBodyResult
import com.a207.runkk.app.ui.record.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 러닝별 탭(G-13·G-17·G-19·G-21·G-31, FR-266): 고른 기록 칸과 그 기록의 분석 상태. 분석 요청은 러닝 저장 때 서버가 자동으로 하므로
 * 이 탭은 기록을 고르고(G-30) 상태를 읽는다. 결과가 있으면 "구간과 피드백 자세히 보기"로 상세(G-20)를 연다.
 */
@Composable
internal fun RunTab(state: RunBodyUiState, actions: RunBodyActions) {
    RunBodyLoadable(
        isLoading = state.isRunsLoading && state.runs.isEmpty(),
        error = state.runsError?.asString(),
        onRetry = actions.onRetryRuns
    ) {
        val run = state.selectedRun
        if (run == null) {
            RunkkEmptyState(stringResource(R.string.record_runbody_runs_empty))
            return@RunBodyLoadable
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SelectedRunCard(run, state.zone, actions.onOpenPick)
            SessionSection(state.session, run, state.zone, actions)
        }
    }
}

/**
 * 고른 기록 칸(피그마 모서리 12, 높이 64, 안쪽 16x8): 시각(12 Medium) + "5.2km  기록 선택 ▾"(16 Regular). 피그마의 가운뎃점은 띄어
 * 쓰기로 바꾼다([임시:D-성-10]). 누르면 러닝 선택(G-30).
 */
@Composable
private fun SelectedRunCard(run: RunSummary, zone: ZoneId, onOpenPick: () -> Unit) {
    val pickLabel = stringResource(R.string.record_runbody_pick_open)
    val body = RunkkTextStyles.text(16)
    RunkkCard(
        Modifier.fillMaxWidth().heightIn(min = SELECTED_RUN_HEIGHT),
        shape = RunkkShapes.Small,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        onClick = onOpenPick,
        onClickLabel = pickLabel,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
    ) {
        Text(
            runTime(run.startedAt, zone),
            style = RunkkTextStyles.text(12, FontWeight.Medium, letterSpacingEm = 0.04f),
            color = RunkkColors.TextSecondary
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.record_km_value, RunkkFormat.kmShort(run.distance)),
                style = body,
                color = RunkkColors.TextPrimary
            )
            Spacer(Modifier.width(8.dp))
            Text(pickLabel, style = body, color = RunkkColors.TextPrimary)
            Spacer(Modifier.width(4.dp))
            DropTriangle()
        }
    }
}

/** 피그마 "▾": 아래로 향한 작은 채운 삼각형(8x5). 장식이다. */
@Composable
private fun DropTriangle() {
    val color = RunkkColors.TextPrimary
    Canvas(Modifier.size(width = 8.dp, height = 5.dp).clearAndSetSemantics {}) {
        drawPath(
            Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2, size.height)
                close()
            },
            color
        )
    }
}

@Composable
private fun SessionSection(session: RunBodySessionState, run: RunSummary, zone: ZoneId, actions: RunBodyActions) {
    when (session) {
        RunBodySessionState.Loading -> RunBodyCard(null) {
            Column(Modifier.fillMaxWidth().padding(vertical = 32.dp)) { RunkkLoading() }
        }

        RunBodySessionState.Pending -> RunBodyCard(
            stringResource(R.string.record_runbody_pending_title),
            titleSize = 16
        ) {
            val start = run.startedAt.atZone(zone)
            val day = DateTimeFormatter.ofPattern(stringResource(R.string.record_runbody_day_pattern), Locale.KOREAN)
            RunBodyCaption(
                stringResource(
                    R.string.record_runbody_pending_run,
                    day.format(start),
                    stringResource(timeOfDayRes(start.hour)),
                    RunkkFormat.kmShort(run.distance)
                )
            )
            RunBodyCaption(stringResource(R.string.record_runbody_pending_message))
            RecheckButton(actions.onRetrySession)
        }

        RunBodySessionState.Analyzing -> AnalyzingSection(actions.onRetrySession)

        RunBodySessionState.Failed -> RunBodyCard(
            stringResource(R.string.record_runbody_failed_title),
            titleSize = 16
        ) {
            RunBodyCaption(stringResource(R.string.record_runbody_failed_message))
            RunkkPrimaryButton(
                stringResource(R.string.record_runbody_failed_retry),
                onClick = actions.onRetrySession,
                modifier = Modifier.fillMaxWidth()
            )
        }

        RunBodySessionState.Unavailable -> RunBodyCard(
            stringResource(R.string.record_runbody_unavailable_title),
            titleSize = 16
        ) {
            RunBodyCaption(stringResource(R.string.record_runbody_unavailable_message))
            RunkkPrimaryButton(
                stringResource(R.string.record_runbody_unavailable_retry),
                onClick = actions.onRetrySession,
                modifier = Modifier.fillMaxWidth()
            )
        }

        RunBodySessionState.NotAnalyzable -> EmptyProfile(onOpenDetail = null)

        RunBodySessionState.NeedsBirthYear -> RunBodyCard(
            stringResource(R.string.record_runbody_birth_year_title),
            titleSize = 16
        ) {
            RunBodyCaption(stringResource(R.string.record_runbody_birth_year_message))
            RunkkPrimaryButton(
                stringResource(R.string.record_runbody_birth_year_edit),
                onClick = actions.onEditProfile,
                modifier = Modifier.fillMaxWidth()
            )
        }

        is RunBodySessionState.Done -> DoneSection(session.result, actions.onOpenDetail)
    }
}

/** 대기·분석 중 "다시 확인"(MR !91 e2e 4). 자동 다시 읽기가 멈춘 뒤에도 상태를 읽을 수 있다. */
@Composable
private fun RecheckButton(onClick: () -> Unit) {
    RunkkSecondaryButton(
        stringResource(R.string.record_runbody_recheck),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * G-13 분석 중: 같은 칸에 빈 오각형과 "분석 중이에요", 값은 흐리게, 상세보기는 꺼 둔다(회색). "다시 확인"은 피그마에 없지만 MR !91 e2e 4
 * 결정으로 둔다.
 */
@Composable
private fun AnalyzingSection(onRecheck: () -> Unit) {
    val analyzing = stringResource(R.string.record_runbody_analyzing_short)
    ProfileCard(stringResource(R.string.record_runbody_profile_title)) {
        RunBodyRadar(emptyMap(), centerText = stringResource(R.string.record_runbody_analyzing))
        ProfileCaption(stringResource(R.string.record_runbody_analyzing_message))
        RunBodyIconRow(RunkkIcons.Bolt, stringResource(R.string.record_runbody_best), analyzing, isMuted = true)
        RunBodyIconRow(
            RunkkIcons.CalendarToday,
            stringResource(R.string.record_runbody_check),
            analyzing,
            isMuted = true
        )
        RecheckButton(onRecheck)
    }
    RunkkPrimaryButton(
        stringResource(R.string.record_runbody_detail_wait),
        onClick = {},
        isEnabled = false,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * 결과(G-13·G-21·G-31): 점수가 있는 축이 둘 이상이면 "이번 러닝 프로필"과 가장 높은·낮은 축, 하나면 "분석 가능한 러닝 프로필",
 * 없으면 "분석할 데이터가 부족해요"다.
 */
@Composable
private fun DoneSection(result: RunBodyResult, onOpenDetail: () -> Unit) {
    val scored = result.scoredAxes
    when {
        scored.isEmpty() -> EmptyProfile(onOpenDetail)

        scored.size == 1 -> {
            val axis = scored.single()
            ProfileCard(stringResource(R.string.record_runbody_profile_partial_title)) {
                RunBodyRadar(scoresOf(result))
                ProfileCaption(
                    stringResource(R.string.record_runbody_profile_partial, stringResource(axis.labelRes()), 1)
                )
            }
            DetailButton(onOpenDetail)
        }

        else -> {
            ProfileCard(stringResource(R.string.record_runbody_profile_title)) {
                RunBodyRadar(scoresOf(result))
                ProfileCaption(profileCaption(result))
                val best = scored.maxBy { result.axes.getValue(it).score ?: 0 }
                val check = scored.minBy { result.axes.getValue(it).score ?: 0 }
                RunBodyIconRow(RunkkIcons.Bolt, stringResource(R.string.record_runbody_best), axisScore(result, best))
                RunBodyIconRow(
                    RunkkIcons.CalendarToday,
                    stringResource(R.string.record_runbody_check),
                    axisScore(result, check)
                )
            }
            DetailButton(onOpenDetail)
        }
    }
}

/** G-31: 모든 축 점수가 없다. 분석 작업이 없는 기록(404)은 상세가 없어 버튼을 두지 않는다. */
@Composable
private fun EmptyProfile(onOpenDetail: (() -> Unit)?) {
    ProfileCard(stringResource(R.string.record_runbody_profile_empty_title)) {
        RunBodyRadar(emptyMap())
        ProfileCaption(stringResource(R.string.record_runbody_profile_empty_message))
    }
    onOpenDetail?.let { DetailButton(it) }
}

/** 프로필 카드(G-13·G-21·G-31): 제목 20 Bold, 안쪽 위·좌우 20·아래 16, 요소 간격 8(피드백 줄 42 사이). */
@Composable
internal fun ProfileCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    RunBodyCard(
        title,
        titleSize = PROFILE_TITLE_SIZE,
        spacing = 8.dp,
        contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 16.dp),
        content = content
    )
}

@Composable
private fun DetailButton(onOpenDetail: () -> Unit) {
    RunkkPrimaryButton(
        stringResource(R.string.record_runbody_detail_open),
        onClick = onOpenDetail,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun ProfileCaption(text: String) {
    Text(
        text,
        style = RunkkTextStyles.Caption,
        color = RunkkColors.TextSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

/** "4개 항목 분석 완료, 러닝 폼 데이터 없음". 다섯 축이 다 있으면 "5개 항목 분석 완료". */
@Composable
internal fun profileCaption(result: RunBodyResult): String {
    val scored = result.scoredAxes
    val missing = RunBodyAxis.entries.filter { it !in scored }
    if (missing.isEmpty()) return stringResource(R.string.record_runbody_profile_all, scored.size)
    val names = missing.map { stringResource(it.labelRes()) }.joinToString(", ")
    return stringResource(R.string.record_runbody_profile_missing, scored.size, names)
}

@Composable
private fun axisScore(result: RunBodyResult, axis: RunBodyAxis): String = stringResource(
    R.string.record_runbody_axis_score,
    stringResource(axis.labelRes()),
    result.axes.getValue(axis).score ?: 0
)

internal fun scoresOf(result: RunBodyResult): Map<RunBodyAxis, Int?> = result.axes.mapValues { it.value.score }

/** 기록 시각 "11월 2일 오전 7:10". */
@Composable
internal fun runTime(startedAt: Instant, zone: ZoneId): String {
    val pattern = stringResource(R.string.record_runbody_run_time_pattern)
    return DateTimeFormatter.ofPattern(pattern, Locale.KOREAN).format(startedAt.atZone(zone))
}

internal const val PROFILE_TITLE_SIZE = 20
private val SELECTED_RUN_HEIGHT = 64.dp
