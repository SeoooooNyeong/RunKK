package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.domain.crew.CrewRunResult
import com.a207.runkk.app.domain.crew.MyRunState
import com.a207.runkk.app.domain.crew.PostList
import java.time.Duration
import java.time.Instant

/**
 * 달리는 크루런 강조 카드(피그마 F-11e·f·g)의 내 상태별 모양. 큰 숫자 줄과 흰 단추를 내 참여 상태([MyRunState])로 고른다:
 * 끝냈으면 내 거리와 「완주했어요」·「결과 보기」, 측정 중이면 지난 시간 「째 달리는 중」·「이어 달리기」, 그 밖이면 지난 시간
 * 「째 진행 중」·「중간 참여하기」.
 */
internal enum class RunningHero(val buttonRes: Int) {
    JOIN(R.string.crew_home_run_join) {
        override fun open(post: PostList.Item, actions: CrewHomeActions) = actions.onJoinRun(post)

        @Composable
        override fun Headline(scheduledAt: Instant, result: CrewRunResult?, now: Instant) =
            Elapsed(elapsed(scheduledAt, result, now), stringResource(R.string.crew_schedule_elapsed_live))
    },
    RESUME(R.string.crew_home_run_resume) {
        override fun open(post: PostList.Item, actions: CrewHomeActions) = actions.onResumeRun(post)

        @Composable
        override fun Headline(scheduledAt: Instant, result: CrewRunResult?, now: Instant) =
            Elapsed(elapsed(scheduledAt, result, now), stringResource(R.string.crew_schedule_elapsed_running))
    },
    RESULT(R.string.crew_home_run_result) {
        override fun open(post: PostList.Item, actions: CrewHomeActions) = actions.onResult(post)

        /** 내 거리(결과를 아직 못 읽었으면 숫자 없이 「완주했어요」만). */
        @Composable
        override fun Headline(scheduledAt: Instant, result: CrewRunResult?, now: Instant) {
            val mine = result?.participants?.firstOrNull { it.isMe }?.distance
            val big = mine?.let { stringResource(R.string.crew_contribution_km, RunkkFormat.km(it)) }
            BigRow(big, stringResource(R.string.crew_schedule_finished))
        }
    };

    /** 단추를 눌렀을 때 갈 곳. */
    abstract fun open(post: PostList.Item, actions: CrewHomeActions)

    /** 큰 숫자 줄. */
    @Composable
    abstract fun Headline(scheduledAt: Instant, result: CrewRunResult?, now: Instant)

    companion object {
        fun of(myRun: MyRunState): RunningHero = when (myRun) {
            MyRunState.ENDED -> RESULT
            MyRunState.RUNNING -> RESUME
            MyRunState.NONE -> JOIN
        }
    }
}

/** 출발에서 지난 시간(결과의 출발 시각, 없으면 모집 일시부터). 음수는 0. */
private fun elapsed(scheduledAt: Instant, result: CrewRunResult?, now: Instant): Duration =
    Duration.between(result?.startedAt ?: scheduledAt, now).coerceAtLeast(Duration.ZERO)

@Composable
private fun Elapsed(elapsed: Duration, label: String) = BigRow(RunkkFormat.duration(elapsed), label)

/** 큰 숫자(Barlow 28 Bold Italic)와 옆 13 글자(피그마 Timer 줄: 아래 맞춤, 간격 6). */
@Composable
private fun BigRow(big: String?, label: String) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {}
    ) {
        big?.let {
            Text(
                it,
                style = RunkkTextStyles.display(28, FontWeight.Bold, lineHeightRatio = 34f / 28f),
                color = RunkkColors.OnSecondary
            )
        }
        Text(
            label,
            style = RunkkTextStyles.text(13, lineHeight = 16),
            color = RunkkColors.OnSecondary,
            modifier = Modifier.padding(bottom = if (big == null) 0.dp else 4.dp)
        )
    }
}
