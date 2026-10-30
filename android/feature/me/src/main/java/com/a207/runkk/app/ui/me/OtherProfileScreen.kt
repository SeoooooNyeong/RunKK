package com.a207.runkk.app.ui.me

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.RunkkAvatar
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.UnreleasedFeature
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.designsystem.tierName
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.domain.account.Profile
import com.a207.runkk.app.domain.account.RecentRun
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 다른 러너 프로필(G-3, D-287): 아바타, 닉네임, 티어·레벨, 크루(2차 배포), 나이대·성별·소개, 최근 러닝. */
@Composable
fun OtherProfileRoute(memberId: Long, viewModel: OtherProfileViewModel = hiltViewModel()) {
    LaunchedEffect(memberId) { viewModel.load(memberId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    OtherProfileScreen(state, onBack = navigator::back, onRetry = viewModel::retry)
}

@Composable
fun OtherProfileScreen(
    state: OtherProfileUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        RunkkTopBar(title = stringResource(R.string.me_other_profile_title), onBack = onBack, isTitleCentered = true)
        val profile = state.profile
        when {
            profile == null && state.isLoading -> RunkkLoading(Modifier.weight(1f))

            profile == null -> RunkkErrorState(
                state.error?.asString() ?: stringResource(R.string.me_other_profile_load_failed),
                onRetry = onRetry,
                modifier = Modifier.weight(1f)
            )

            else -> OtherProfileBody(profile, state.faceLayers, Modifier.weight(1f))
        }
    }
}

/**
 * 다른 러너 프로필 본문(G-3, D-287): 아바타, 닉네임, "[티어], Lv.[n]", 크루명(크루 공개 때만, D-252), 공개된 나이대·성별과 한줄 소개 칸,
 * "최근 러닝" 최대 3줄(종류·날짜·거리, 서버가 기록 공개 범위를 통과할 때만 준다. 없으면 영역 없음).
 */
@Composable
private fun OtherProfileBody(profile: Profile, faceLayers: List<FaceLayer>, modifier: Modifier = Modifier) {
    val defaultTier = stringResource(R.string.me_profile_default_tier)
    val level = profile.level?.let { stringResource(R.string.me_profile_level, it) }
    val about = aboutLine(LocalContext.current.resources, profile)
    val isCrewOpen = LocalReleaseScope.current.isOpen(UnreleasedFeature.CREW)
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RunkkAvatar(profile.faceKey, AvatarExpression.RUN, Modifier.size(120.dp), faceLayers)
        Text(
            profile.displayName,
            style = RunkkTextStyles.text(20, FontWeight.Black),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            listOfNotNull(tierName(profile.tier) ?: defaultTier, level).joinToString(", "),
            style = RunkkTextStyles.Label,
            color = RunkkColors.TextSecondary
        )
        if (isCrewOpen) {
            profile.crewName?.let { Text(it, style = RunkkTextStyles.Body, color = RunkkColors.TextPrimary) }
        }
        if (about != null || !profile.intro.isNullOrBlank()) AboutCard(about, profile.intro)
        val runs = profile.recentRuns.take(RECENT_RUNS_MAX)
        if (runs.isNotEmpty()) RecentRuns(runs)
    }
}

/** 공개된 나이대·성별(작은 글자)과 한줄 소개를 담은 칸. */
@Composable
private fun AboutCard(about: String?, intro: String?) {
    val shape = RunkkShapes.Large
    Column(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, shape)
            .padding(16.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        about?.let { Text(it, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary) }
        intro?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = RunkkTextStyles.Body, color = RunkkColors.TextPrimary)
        }
    }
}

/** "최근 러닝" 줄들: 종류와 날짜("[10.20]") 왼쪽, 거리("[5.02]km") 오른쪽. */
@Composable
private fun RecentRuns(runs: List<RecentRun>) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            stringResource(R.string.me_other_recent_runs),
            style = RunkkTextStyles.text(15, FontWeight.Bold),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.padding(bottom = 8.dp).semantics { heading() }
        )
        runs.forEach { run ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(runKindLabelRes(run.kind)),
                        style = RunkkTextStyles.Body,
                        color = RunkkColors.TextPrimary
                    )
                    Text(
                        run.startedOn.format(RUN_DATE),
                        style = RunkkTextStyles.Caption,
                        color = RunkkColors.TextSecondary
                    )
                }
                Text(
                    stringResource(R.string.me_other_run_distance, RunkkFormat.km(run.distance)),
                    style = RunkkTextStyles.text(15, FontWeight.Bold),
                    color = RunkkColors.TextPrimary
                )
            }
            HorizontalDivider(color = RunkkColors.Border)
        }
    }
}

/** 최근 러닝 종류 표기(서버 CardKind). 모르면 "러닝". */
@StringRes
internal fun runKindLabelRes(kind: CardKind?): Int = when (kind) {
    CardKind.FREE_RUN -> R.string.me_run_kind_free
    CardKind.BATTLE_RUN -> R.string.me_run_kind_battle
    CardKind.SELF_RUN -> R.string.me_run_kind_self
    CardKind.CELEB_RUN -> R.string.me_run_kind_celeb
    CardKind.CREW_RUN -> R.string.me_run_kind_crew
    null -> R.string.me_run_kind_unknown
}

private const val RECENT_RUNS_MAX = 3
private val RUN_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MM.dd")

@Preview(name = "F8 다른 러너 프로필", widthDp = 390, heightDp = 844)
@Composable
private fun OtherProfilePreview() {
    RunkkTheme(reduceMotion = true) {
        OtherProfileScreen(
            OtherProfileUiState(
                memberId = 9,
                isLoading = false,
                profile = Profile(
                    9,
                    "새벽러너",
                    "PUBLIC",
                    "실버",
                    5,
                    "주말엔 한강",
                    null,
                    null,
                    "새벽크루",
                    null,
                    ageBand = "20대",
                    gender = Gender.FEMALE,
                    recentRuns = listOf(
                        RecentRun(CardKind.FREE_RUN, LocalDate.of(2026, 10, 20), Distance(5_020)),
                        RecentRun(CardKind.BATTLE_RUN, LocalDate.of(2026, 10, 18), Distance(5_000))
                    )
                )
            ),
            onBack = {},
            onRetry = {}
        )
    }
}
