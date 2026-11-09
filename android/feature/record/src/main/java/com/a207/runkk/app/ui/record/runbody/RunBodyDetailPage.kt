package com.a207.runkk.app.ui.record.runbody

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.a207.runkk.app.core.designsystem.MetaItem
import com.a207.runkk.app.core.designsystem.MetaRow
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.domain.runbody.RunBodyResult
import com.a207.runkk.app.ui.record.R

/**
 * 런바디 상세(G-20, FR-266): 능력 오각형, 러닝 요약, 구간 기록, 피드백, 코칭, 이전 러닝과 비교. 값이 없는 줄과 직전 러닝이 없는 비교,
 * 플래그가 없는 코칭 카드는 그리지 않는다.
 */
@Composable
internal fun RunBodyDetailPage(state: RunBodyUiState, actions: RunBodyActions) {
    val result = (state.session as? RunBodySessionState.Done)?.result ?: return
    RunBodyFrame(
        stringResource(R.string.record_runbody_detail_title),
        onBack = actions.onBack,
        onCoach = actions.onCoach
    ) {
        // 피그마 "11월 2일 오전 7:10 · 5.2km"의 가운뎃점은 메타 줄로 그린다([임시:D-성-10])
        MetaRow(
            listOf(
                MetaItem(RunkkIcons.CalendarToday, runTime(result.summary.startedAt, state.zone)),
                MetaItem(
                    RunkkIcons.DirectionsRun,
                    stringResource(R.string.record_km_value, RunkkFormat.kmShort(result.summary.distance))
                )
            )
        )
        RunBodyCard(stringResource(R.string.record_runbody_detail_profile), titleSize = PROFILE_TITLE_SIZE) {
            RunBodyRadar(scoresOf(result))
            ProfileCaption(profileCaption(result))
        }
        SummaryCard(result.summary)
        if (result.laps.isNotEmpty()) LapsCard(result.laps)
        FeedbackCard(result.feedback)
        if (result.coaching.isNotEmpty()) CoachingCard(result.coaching, result.split)
        result.previous?.let { CompareCard(result.summary, it) }
    }
}

@Composable
private fun SummaryCard(summary: RunBodyResult.Summary) {
    RunBodyCard(stringResource(R.string.record_runbody_detail_summary)) {
        RunBodyValueRow(
            stringResource(R.string.record_runbody_distance),
            stringResource(R.string.record_km_value, RunkkFormat.kmShort(summary.distance))
        )
        RunBodyValueRow(stringResource(R.string.record_runbody_active_time), runBodyClock(summary.active))
        summary.pace?.let {
            RunBodyValueRow(
                stringResource(R.string.record_stat_avg_pace),
                stringResource(R.string.record_runbody_pace_per_km, runBodyPace(it))
            )
        }
        summary.averageHeartRate?.let {
            RunBodyValueRow(
                stringResource(R.string.record_runbody_avg_heart),
                stringResource(R.string.record_runbody_bpm, it)
            )
        }
        summary.maxHeartRate?.let {
            RunBodyValueRow(
                stringResource(R.string.record_runbody_max_heart),
                stringResource(R.string.record_runbody_bpm, it)
            )
        }
    }
}

/** 구간 기록: 머리 문구(구간, 페이스, 평균 심박) + 1km마다 한 줄("1km" 왼쪽, "5'42"/km, 148 bpm" 오른쪽), 남은 구간은 "마지막 0.2 km". */
@Composable
private fun LapsCard(laps: List<RunBodyResult.Lap>) {
    RunBodyCard(stringResource(R.string.record_runbody_laps)) {
        RunBodyCaption(stringResource(R.string.record_runbody_lap_header))
        laps.forEach { lap ->
            val pace = stringResource(R.string.record_runbody_pace_per_km, runBodyPace(lap.pace))
            RunBodyValueRow(
                if (lap.isPartial) {
                    stringResource(R.string.record_runbody_lap_last, runBodyKm(lap.km))
                } else {
                    stringResource(R.string.record_runbody_lap_km, lap.km.toInt().toString())
                },
                lap.averageHeartRate?.let {
                    stringResource(
                        R.string.record_runbody_lap_value,
                        pace,
                        stringResource(R.string.record_runbody_bpm, it)
                    )
                } ?: pace
            )
        }
    }
}

/** 피드백: 판정 한 줄 + 좋았던 점·보완할 점·다음 러닝(라벨과 본문을 한 줄에). 모두 14 Regular 보조색(피그마 G-20). */
@Composable
private fun FeedbackCard(feedback: RunBodyResult.Feedback) {
    RunBodyCard(stringResource(R.string.record_runbody_feedback)) {
        Text(feedback.headline, style = DETAIL_LINE, color = RunkkColors.TextSecondary)
        LabeledText(stringResource(R.string.record_runbody_feedback_good), feedback.good)
        LabeledText(stringResource(R.string.record_runbody_feedback_fix), feedback.fix)
        LabeledText(stringResource(R.string.record_runbody_feedback_next), feedback.next)
    }
}

/**
 * 코칭(바탕 accent container #EBE7FC): 플래그마다 제목 한 줄(14 Regular 보조색). 후반이 느려진 러닝은 확인 근거(전반 → 후반 페이스)를
 * 덧붙인다.
 */
@Composable
private fun CoachingCard(coaching: List<RunBodyResult.Coaching>, split: RunBodyResult.Split?) {
    RunBodyCard(stringResource(R.string.record_runbody_coaching), background = RunkkColors.AccentContainer) {
        coaching.forEach { flag ->
            Text(stringResource(flag.titleRes()), style = DETAIL_LINE, color = RunkkColors.TextSecondary)
            if (flag == RunBodyResult.Coaching.POSITIVE_SPLIT && split != null) {
                LabeledText(
                    stringResource(R.string.record_runbody_coaching_evidence),
                    stringResource(
                        R.string.record_runbody_coaching_split,
                        runBodyPace(split.firstHalf),
                        runBodyPace(split.secondHalf)
                    )
                )
            }
        }
    }
}

@Composable
private fun CompareCard(summary: RunBodyResult.Summary, previous: RunBodyResult.Previous) {
    RunBodyCard(stringResource(R.string.record_runbody_compare)) {
        RunBodyCaption(stringResource(R.string.record_runbody_compare_legend))
        RunBodyValueRow(
            stringResource(R.string.record_runbody_distance),
            stringResource(
                R.string.record_runbody_compare_value,
                stringResource(R.string.record_runbody_km_spaced, RunkkFormat.kmShort(summary.distance)),
                stringResource(R.string.record_runbody_km_spaced, RunkkFormat.kmShort(previous.distance))
            )
        )
        RunBodyValueRow(
            stringResource(R.string.record_stat_avg_pace),
            stringResource(R.string.record_runbody_compare_value, runBodyPace(summary.pace), runBodyPace(previous.pace))
        )
    }
}

/**
 * 라벨과 본문을 한 줄(문단)에: "좋았던 점 전반과 후반 페이스 차이가 작아요."(G-20). 피그마는 한 줄 전체가 14 Regular 보조색이고 라벨 뒤를
 * 가운뎃점으로 띄운다. 가운뎃점 대신 라벨만 Medium으로 구분한다([임시:D-성-10]).
 */
@Composable
private fun LabeledText(label: String, text: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(label) }
            append(' ')
            append(text)
        },
        style = DETAIL_LINE,
        color = RunkkColors.TextSecondary,
        modifier = Modifier.fillMaxWidth()
    )
}

private val DETAIL_LINE = RunkkTextStyles.text(14, lineHeight = 21)
