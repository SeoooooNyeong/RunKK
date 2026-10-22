package com.a207.runkk.app.ui.me

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunimalTypeLabel
import com.a207.runkk.app.core.designsystem.RunkkAvatarFace
import com.a207.runkk.app.core.designsystem.RunkkBadge
import com.a207.runkk.app.core.designsystem.RunkkBadgeTone
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIconButton
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkMenuRow
import com.a207.runkk.app.core.designsystem.RunkkPillButton
import com.a207.runkk.app.core.designsystem.RunkkSectionTitle
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTags
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.account.Gender
import com.a207.runkk.app.domain.account.Profile

/** 마이 탭(프로필 요약 + 기록·설정 메뉴 + 로그아웃·탈퇴). */
@Composable
fun MeRoute(viewModel: MeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { if (it == MeEvent.LoggedOut) navigator.restart(Routes.LOGIN) }
    }
    MeScreen(
        state = state,
        onNavigate = navigator::navigate,
        onRetry = viewModel::refresh,
        onLogout = viewModel::onLogoutConfirmed
    )
}

@Composable
fun MeScreen(
    state: MeUiState,
    onNavigate: (String) -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmLogout by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxSize().background(
            RunkkColors.Background
        ).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        MeTopRow(onNavigate)
        when {
            state.isLoading && state.profile == null -> Box(Modifier.fillMaxWidth().padding(vertical = 32.dp)) {
                RunkkLoading()
            }

            state.profile != null -> ProfileCard(
                state.profile,
                state.faceLayers,
                onEdit = { onNavigate(Routes.PROFILE_EDIT) }
            )

            state.error != null -> RunkkErrorState(state.error.asString(), onRetry = onRetry)
        }
        MenuSections(onNavigate)
        RunkkCard(contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.Top) {
            RunkkMenuRow(stringResource(R.string.me_menu_logout), onClick = { confirmLogout = true })
            HorizontalDivider(color = RunkkColors.Border)
            RunkkMenuRow(
                stringResource(R.string.me_menu_withdraw),
                onClick = { onNavigate(Routes.WITHDRAW) },
                isAccent = true
            )
        }
    }
    if (confirmLogout) {
        LogoutDialog(
            onConfirm = {
                confirmLogout = false
                onLogout()
            },
            onDismiss = { confirmLogout = false }
        )
    }
}

/** 로그아웃 확인 대화상자. */
@Composable
private fun LogoutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    RunkkConfirmDialog(
        title = stringResource(R.string.me_logout_title),
        message = stringResource(R.string.me_logout_message),
        confirmLabel = stringResource(R.string.me_logout_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        isDestructive = true
    )
}

/** 마이 제목과 알림함·설정 아이콘. */
@Composable
private fun MeTopRow(onNavigate: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.me_main_title),
            style = RunkkTextStyles.text(24, androidx.compose.ui.text.font.FontWeight.Black),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.weight(1f).semantics {
                heading()
            }
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

/** 내 러닝·캐릭터·설정 메뉴 묶음. */
@Composable
private fun MenuSections(onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        MenuSection(
            R.string.me_menu_section_running,
            listOf(
                R.string.me_menu_records to Routes.RECORDS,
                R.string.me_menu_growth to Routes.GROWTH,
                R.string.me_menu_report to Routes.REPORT,
                R.string.me_menu_ranking to Routes.RANKING,
                R.string.me_menu_coach to Routes.COACH,
                R.string.me_menu_battle_history to Routes.BATTLE_HISTORY
            ),
            onNavigate
        )
        MenuSection(
            R.string.me_menu_section_character,
            listOf(
                R.string.me_menu_runimal to Routes.RUNIMAL,
                R.string.me_menu_avatar to Routes.AVATAR,
                R.string.me_menu_shop to Routes.SHOP
            ),
            onNavigate
        )
        MenuSection(
            R.string.me_menu_section_settings,
            listOf(
                R.string.me_settings_page_free_run to Routes.FREE_RUN_SETTINGS,
                R.string.me_settings_page_notifications to Routes.NOTIFICATION_SETTINGS,
                R.string.me_settings_page_consents to Routes.CONSENTS,
                R.string.me_settings_page_app to Routes.SETTINGS
            ),
            onNavigate
        )
    }
}

/** 입력한 나이대·성별(FR-006). "밝히지 않음"·미입력은 빼고, 둘 다 없으면 null. */
internal fun aboutLine(res: Resources, profile: Profile): String? = listOfNotNull(
    profile.ageBand?.takeIf { it.isNotBlank() },
    profile.gender?.takeIf { it != Gender.UNDISCLOSED }?.let { res.getString(genderLabelRes(it)) }
).takeIf { it.isNotEmpty() }?.joinToString(" · ")

@Composable
private fun ProfileCard(profile: Profile, faceLayers: List<FaceLayer>, onEdit: () -> Unit) {
    RunkkCard(shape = RunkkShapes.ExtraLarge, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ProfileHeader(profile, faceLayers, onEdit)
        ProfileDetails(profile)
    }
}

/** 프로필 원(셀카 얼굴), 닉네임·티어 줄·나이대·성별, 편집 버튼. */
@Composable
private fun ProfileHeader(profile: Profile, faceLayers: List<FaceLayer>, onEdit: () -> Unit) {
    val defaultTier = stringResource(R.string.me_profile_default_tier)
    val level = profile.level?.let { stringResource(R.string.me_profile_level, it) }
    val about = aboutLine(LocalContext.current.resources, profile)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(
                56.dp
            ).clip(
                CircleShape
            ).border(
                2.dp,
                RunkkColors.Lime,
                CircleShape
            ).padding(4.dp).clip(CircleShape).background(RunkkColors.Border)
        ) {
            if (faceLayers.isNotEmpty()) {
                RunkkAvatarFace(faceLayers, Modifier.fillMaxSize().testTag(PROFILE_FACE_TAG))
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                profile.displayName,
                style = RunkkTextStyles.text(18, androidx.compose.ui.text.font.FontWeight.Black),
                color = RunkkColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(profile.tier ?: defaultTier, level, profile.crewName).joinToString(" · "),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
            about?.let { Text(it, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary) }
        }
        RunkkPillButton(stringResource(R.string.me_profile_edit), onClick = onEdit)
    }
}

/** 소개, 성향 태그, Runimal·최장 거리 칸. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.ProfileDetails(profile: Profile) {
    profile.intro?.let { Text(it, style = RunkkTextStyles.Body, color = RunkkColors.TextPrimary) }
    if (profile.tags.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            profile.tags.forEach { RunkkBadge(RunkkTags.label(it), tone = RunkkBadgeTone.Lime) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RunimalStat(profile.runimalType, Modifier.weight(1f))
        MiniStat(
            stringResource(R.string.me_profile_longest),
            profile.longestDistance?.let {
                stringResource(R.string.me_profile_longest_value, RunkkFormat.km(it))
            } ?: RunkkFormat.EMPTY,
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(
            RunkkShapes.Medium
        ).background(RunkkColors.Background).padding(12.dp).semantics(mergeDescendants = true) {
        },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, style = RunkkTextStyles.Micro, color = RunkkColors.TextSecondary)
        Text(value, style = RunkkTextStyles.BodyStrong, color = RunkkColors.TextPrimary)
    }
}

/** 러니멀 칸: 서버 유형 값을 한국어 이름과 작은 그림으로 보인다. 아직 유형이 없으면 "판정 전". */
@Composable
private fun RunimalStat(type: String?, modifier: Modifier = Modifier) {
    if (type.isNullOrBlank()) {
        MiniStat(
            stringResource(R.string.me_profile_runimal),
            stringResource(R.string.me_profile_runimal_unknown),
            modifier
        )
        return
    }
    Column(
        modifier = modifier.clip(RunkkShapes.Medium).background(RunkkColors.Background).padding(12.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            stringResource(R.string.me_profile_runimal),
            style = RunkkTextStyles.Micro,
            color = RunkkColors.TextSecondary
        )
        RunimalTypeLabel(
            type,
            artSize = 24.dp,
            textStyle = RunkkTextStyles.BodyStrong,
            color = RunkkColors.TextPrimary
        )
    }
}

@Composable
private fun MenuSection(@StringRes title: Int, items: List<Pair<Int, String>>, onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkSectionTitle(stringResource(title), modifier = Modifier.padding(horizontal = 8.dp))
        RunkkCard(contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.Top) {
            items.forEachIndexed { index, (label, route) ->
                RunkkMenuRow(stringResource(label), onClick = { onNavigate(route) })
                if (index < items.lastIndex) HorizontalDivider(color = RunkkColors.Border)
            }
        }
    }
}

@Preview(name = "마이", widthDp = 390, heightDp = 1200)
@Composable
private fun MeScreenPreview() {
    RunkkTheme(reduceMotion = true) {
        MeScreen(
            state = MeUiState(
                isLoading = false,
                profile = Profile(
                    7, "달리는곰", "SELF", "골드", 3,
                    setOf(
                        "SOCIAL_RUN",
                        "EVENING"
                    ),
                    "퇴근 뒤 한강", "치타", null, "새벽크루", null
                )
            ),
            onNavigate = {},
            onRetry = {},
            onLogout = {}
        )
    }
}

/** 프로필 원 안의 셀카 얼굴(시험이 찾는 표지). */
internal const val PROFILE_FACE_TAG = "profile-avatar-face"
