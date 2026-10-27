package com.a207.runkk.app.ui.me

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.AvatarBackdrop
import com.a207.runkk.app.core.designsystem.RunimalArt
import com.a207.runkk.app.core.designsystem.RunkkAvatarArt
import com.a207.runkk.app.core.designsystem.RunkkAvatarPortrait
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIconButton
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkMenuRow
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.account.Gender
import com.a207.runkk.app.domain.account.Profile

/** 마이 탭(F2): 프로필 요약, 통계, MY Runimal, 기록·아바타 메뉴. 로그아웃·탈퇴는 설정(F4)에 있다. */
@Composable
fun MeRoute(viewModel: MeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    MeScreen(state = state, onNavigate = navigator::navigate, onRetry = viewModel::refresh)
}

@Composable
fun MeScreen(state: MeUiState, onNavigate: (String) -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RunkkColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MeTopRow(onNavigate)
        when {
            state.isLoading && state.profile == null -> Box(Modifier.fillMaxWidth().padding(vertical = 32.dp)) {
                RunkkLoading()
            }

            state.profile != null -> ProfileSummary(
                state.profile,
                state.faceLayers,
                onEdit = { onNavigate(Routes.PROFILE_EDIT) },
                onRunimal = { onNavigate(Routes.RUNIMAL) }
            )

            state.error != null -> RunkkErrorState(state.error.asString(), onRetry = onRetry)
        }
        MenuList(onNavigate)
    }
}

/** 마이 제목과 알림함·설정 아이콘. */
@Composable
private fun MeTopRow(onNavigate: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.me_main_title),
            style = RunkkTextStyles.text(24, FontWeight.Black),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        RunkkIconButton(
            RunkkIcons.Bell,
            contentDescription = stringResource(R.string.me_main_notifications),
            onClick = { onNavigate(Routes.NOTIFICATIONS) }
        )
        RunkkIconButton(
            RunkkIcons.Settings,
            contentDescription = stringResource(R.string.me_main_settings_description),
            onClick = { onNavigate(Routes.SETTINGS) }
        )
    }
}

/** 입력한 나이대·성별(FR-006). "밝히지 않음"·미입력은 빼고, 둘 다 없으면 null. */
internal fun aboutLine(res: Resources, profile: Profile): String? = listOfNotNull(
    profile.ageBand?.takeIf { it.isNotBlank() },
    profile.gender?.takeIf { it != Gender.UNDISCLOSED }?.let { res.getString(genderLabelRes(it)) }
).takeIf { it.isNotEmpty() }?.joinToString(", ")

/** 프로필 머리, "프로필 편집", 통계, MY Runimal 카드. */
@Composable
private fun ProfileSummary(profile: Profile, faceLayers: List<FaceLayer>, onEdit: () -> Unit, onRunimal: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ProfileHeader(profile, faceLayers)
        RunkkSecondaryButton(
            stringResource(R.string.me_profile_edit),
            onClick = onEdit,
            height = 40.dp,
            modifier = Modifier.fillMaxWidth()
        )
        StatTiles(profile)
        RunimalCard(profile.runimalType, onRunimal)
    }
}

/** 고른 랜드마크 배경 위 아바타 얼굴(FR-202), 닉네임, "Lv.n, 티어", 나이대·성별, 한줄 소개. */
@Composable
private fun ProfileHeader(profile: Profile, faceLayers: List<FaceLayer>) {
    val defaultTier = stringResource(R.string.me_profile_default_tier)
    val level = profile.level?.let { stringResource(R.string.me_profile_level, it) }
    val about = aboutLine(LocalContext.current.resources, profile)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(RunkkShapes.Large)
                .border(2.dp, RunkkColors.Lime, RunkkShapes.Large),
            contentAlignment = Alignment.Center
        ) {
            // 배경은 장식이다.
            AvatarBackdrop(profile.avatarBackgroundKey, modifier = Modifier.matchParentSize())
            RunkkAvatarPortrait(
                // 얼굴을 고르기 전이면 기본 캐릭터 얼굴 1번(FR-155·FR-158).
                profile.faceKey ?: RunkkAvatarArt.DEFAULT_FACE_KEY,
                faceLayers,
                modifier = Modifier.size(52.dp).clip(CircleShape).testTag(PROFILE_FACE_TAG)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                profile.displayName,
                style = RunkkTextStyles.text(18, FontWeight.Black),
                color = RunkkColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(level, profile.tier ?: defaultTier).joinToString(", "),
                style = RunkkTextStyles.Label,
                color = RunkkColors.TextSecondary
            )
            about?.let { Text(it, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary) }
            profile.intro?.let { Text(it, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary) }
        }
    }
}

/** 통계 칸. 앱 프로필에 있는 값(최장 거리)만 보인다(누적 거리·러닝 횟수는 프로필 응답에 없다). */
@Composable
private fun StatTiles(profile: Profile) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(
            profile.longestDistance?.let { stringResource(R.string.me_profile_longest_value, RunkkFormat.km(it)) }
                ?: RunkkFormat.EMPTY,
            stringResource(R.string.me_profile_longest),
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RunkkShapes.Medium)
            .background(RunkkColors.Surface)
            .padding(vertical = 12.dp, horizontal = 4.dp)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(value, style = RunkkTextStyles.text(17, FontWeight.Black), color = RunkkColors.TextPrimary)
        Text(label, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
    }
}

/** MY Runimal 카드(누르면 J1): 유형 그림과 이름. 아직 유형이 없으면 "판정 전". */
@Composable
private fun RunimalCard(type: String?, onClick: () -> Unit) {
    RunkkCard(onClick = onClick, onClickLabel = stringResource(R.string.me_profile_runimal)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val art = RunimalArt.of(type)
            Image(
                painterResource(art.drawableRes),
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(CircleShape).background(RunkkColors.Background).padding(4.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.me_profile_runimal),
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.TextSecondary
                )
                Text(
                    if (type.isNullOrBlank()) {
                        stringResource(R.string.me_profile_runimal_unknown)
                    } else {
                        stringResource(art.nameRes)
                    },
                    style = RunkkTextStyles.text(15, FontWeight.Bold),
                    color = RunkkColors.TextPrimary
                )
            }
            Icon(
                RunkkIcons.ChevronRight,
                contentDescription = null,
                tint = RunkkColors.TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** 기록·아바타 메뉴 한 목록. */
@Composable
private fun MenuList(onNavigate: (String) -> Unit) {
    RunkkCard(contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.Top) {
        MENU.forEachIndexed { index, (label, route) ->
            RunkkMenuRow(stringResource(label), onClick = { onNavigate(route) })
            if (index < MENU.lastIndex) HorizontalDivider(color = RunkkColors.Border)
        }
    }
}

private val MENU: List<Pair<Int, String>> = listOf(
    R.string.me_menu_records to Routes.RECORDS,
    R.string.me_menu_growth to Routes.GROWTH,
    R.string.me_menu_report to Routes.REPORT,
    R.string.me_menu_coach to Routes.COACH,
    R.string.me_menu_ranking to Routes.RANKING,
    R.string.me_menu_battle_history to Routes.BATTLE_HISTORY,
    R.string.me_menu_avatar to Routes.AVATAR
)

@Preview(name = "F2 마이", widthDp = 390, heightDp = 1000)
@Composable
private fun MeScreenPreview() {
    RunkkTheme(reduceMotion = true) {
        MeScreen(
            state = MeUiState(
                isLoading = false,
                profile = Profile(7, "달리는곰", "SELF", "골드", 3, "퇴근 뒤 한강", "치타", null, "새벽크루", null)
            ),
            onNavigate = {},
            onRetry = {}
        )
    }
}

@StringRes
internal fun genderLabelRes(gender: Gender): Int = when (gender) {
    Gender.FEMALE -> R.string.me_gender_female
    Gender.MALE -> R.string.me_gender_male
    Gender.UNDISCLOSED -> R.string.me_gender_undisclosed
}

/** 프로필 원(시험이 찾는 표지). */
internal const val PROFILE_FACE_TAG = "profile-avatar-face"
