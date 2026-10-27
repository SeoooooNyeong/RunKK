package com.a207.runkk.app.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.AvatarBackdrop
import com.a207.runkk.app.core.designsystem.RunkkAvatarArt
import com.a207.runkk.app.core.designsystem.RunkkAvatarPortrait
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkInfoChip
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.domain.account.Gender
import com.a207.runkk.app.domain.account.Profile

/** 다른 러너 프로필(F8): 랜드마크 배경 위 아바타, 닉네임, 티어·레벨, 크루, 나이대·성별, 한줄 소개. */
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
        RunkkTopBar(title = stringResource(R.string.me_other_profile_title), onBack = onBack)
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

@Composable
private fun OtherProfileBody(profile: Profile, faceLayers: List<FaceLayer>, modifier: Modifier = Modifier) {
    val defaultTier = stringResource(R.string.me_profile_default_tier)
    val level = profile.level?.let { stringResource(R.string.me_profile_level, it) }
    val about = aboutLine(LocalContext.current.resources, profile)
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RunkkShapes.ExtraLarge)
                .border(1.dp, RunkkColors.Border, RunkkShapes.ExtraLarge),
            contentAlignment = Alignment.Center
        ) {
            // 배경은 장식이다.
            AvatarBackdrop(profile.avatarBackgroundKey, modifier = Modifier.matchParentSize())
            RunkkAvatarPortrait(
                profile.faceKey ?: RunkkAvatarArt.DEFAULT_FACE_KEY,
                faceLayers,
                modifier = Modifier.size(96.dp).clip(CircleShape)
            )
        }
        Text(
            profile.displayName,
            style = RunkkTextStyles.text(20, FontWeight.Black),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() }
        )
        Text(
            listOfNotNull(profile.tier ?: defaultTier, level).joinToString(", "),
            style = RunkkTextStyles.Label,
            color = RunkkColors.TextSecondary
        )
        profile.crewName?.let { RunkkInfoChip(it, isHighlighted = true) }
        about?.let { Text(it, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary) }
        profile.intro?.let {
            Text(it, style = RunkkTextStyles.Body, color = RunkkColors.TextPrimary, textAlign = TextAlign.Center)
        }
    }
}

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
                    gender = Gender.FEMALE
                )
            ),
            onBack = {},
            onRetry = {}
        )
    }
}
