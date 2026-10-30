package com.a207.runkk.app.ui.me

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.RunimalArt
import com.a207.runkk.app.core.designsystem.RunkkAvatar
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIconButton
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkMenuRow
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.designsystem.tierName
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.account.Gender
import com.a207.runkk.app.domain.account.Profile

/**
 * 마이 탭(G-1, D-287): 알림 종·설정, 아바타·닉네임·"Lv.[n] · [티어]"·"편집"·"[Runimal] ›", 누적 거리·최장 거리·러닝 횟수,
 * 메뉴 "러닝 기록"·"성장 분석"·"런바디"·"목표·러닝 코치". 소개·나이대·성별은 보이지 않는다. 로그아웃·탈퇴는 설정에 있다.
 */
@Composable
fun MeRoute(viewModel: MeViewModel = hiltViewModel()) {
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }
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

            state.profile != null -> {
                ProfileHeader(
                    state.profile,
                    state.faceLayers,
                    onEdit = { onNavigate(Routes.PROFILE_EDIT) },
                    onRunimal = { onNavigate(Routes.RUNIMAL) }
                )
                StatTiles(state.profile)
            }

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

/** 입력한 나이대·성별(FR-006). "밝히지 않음"·미입력은 빼고, 둘 다 없으면 null. 다른 러너 프로필이 쓴다. */
internal fun aboutLine(res: Resources, profile: Profile): String? = listOfNotNull(
    profile.ageBand?.takeIf { it.isNotBlank() },
    profile.gender?.takeIf { it != Gender.UNDISCLOSED }?.let { res.getString(genderLabelRes(it)) }
).takeIf { it.isNotEmpty() }?.joinToString(", ")

/** 아바타(기본 아바타, 꾸미기가 열리면 얼굴 그림), 닉네임·"Lv.[n] · [티어]", "편집", "[Runimal] ›". */
@Composable
private fun ProfileHeader(profile: Profile, faceLayers: List<FaceLayer>, onEdit: () -> Unit, onRunimal: () -> Unit) {
    val tier = tierName(profile.tier) ?: stringResource(R.string.me_profile_default_tier)
    val levelTier = stringResource(R.string.me_profile_level_tier, profile.level ?: RunkkFormat.EMPTY, tier)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(RunkkColors.SurfacePressed)
                .border(1.dp, RunkkColors.Border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            RunkkAvatar(
                profile.faceKey,
                AvatarExpression.FACE,
                modifier = Modifier.size(64.dp).testTag(PROFILE_FACE_TAG),
                faceLayers = faceLayers
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    profile.displayName,
                    style = RunkkTextStyles.text(18, FontWeight.Black),
                    color = RunkkColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(levelTier, style = RunkkTextStyles.Label, color = RunkkColors.TextSecondary)
            }
            RunimalLink(profile.runimalType, onRunimal)
        }
        val editDescription = stringResource(R.string.me_main_edit_description)
        TextButton(onClick = onEdit, modifier = Modifier.semantics { contentDescription = editDescription }) {
            Text(
                stringResource(R.string.me_main_edit),
                style = RunkkTextStyles.BodyStrong,
                color = RunkkColors.TextPrimary
            )
        }
    }
}

/** "[Runimal] ›": 유형 이름(없으면 "판정 전")을 누르면 MY Runimal로 간다. */
@Composable
private fun RunimalLink(type: String?, onClick: () -> Unit) {
    val name = if (type.isNullOrBlank()) {
        stringResource(R.string.me_profile_runimal_unknown)
    } else {
        stringResource(RunimalArt.of(type).nameRes)
    }
    // 글자 줄 높이(약 30dp)만으로는 누르기 어려워 최소 터치 크기(48dp)를 둔다(AN8-17).
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.me_profile_runimal),
                onClick = onClick
            )
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, style = RunkkTextStyles.text(15, FontWeight.Bold), color = RunkkColors.TextPrimary)
        Icon(
            RunkkIcons.ChevronRight,
            contentDescription = null,
            tint = RunkkColors.TextPrimary,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** 누적 거리·최장 거리·러닝 횟수 칸 세 개. 모르는 값은 "-". */
@Composable
private fun StatTiles(profile: Profile) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(kmValue(profile.totalDistance), stringResource(R.string.me_profile_total), Modifier.weight(1f))
        StatTile(kmValue(profile.longestDistance), stringResource(R.string.me_profile_longest), Modifier.weight(1f))
        StatTile(
            profile.runCount?.let { stringResource(R.string.me_profile_runs_value, it) } ?: RunkkFormat.EMPTY,
            stringResource(R.string.me_profile_runs),
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun kmValue(distance: Distance?): String =
    distance?.let { stringResource(R.string.me_profile_km_value, RunkkFormat.kmShort(it)) } ?: RunkkFormat.EMPTY

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, shape)
            .padding(vertical = 14.dp, horizontal = 4.dp)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(value, style = RunkkTextStyles.text(17, FontWeight.Black), color = RunkkColors.TextPrimary, maxLines = 1)
        Text(label, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
    }
}

/** 메뉴 네 줄(구분선). */
@Composable
private fun MenuList(onNavigate: (String) -> Unit) {
    Column {
        MENU.forEach { (label, route) ->
            RunkkMenuRow(stringResource(label), onClick = { onNavigate(route) })
            HorizontalDivider(color = RunkkColors.Border)
        }
    }
}

/** 마이 메뉴(G-1). 런바디는 기존 리포트 화면이다(CR-32). 랭킹은 하단 탭, 대결 이력은 기록 화면의 대결 탭이 맡는다. */
private val MENU: List<Pair<Int, String>> = listOf(
    R.string.me_menu_records to Routes.RECORDS,
    R.string.me_menu_growth to Routes.GROWTH,
    R.string.me_menu_report to Routes.REPORT,
    R.string.me_menu_coach to Routes.COACH
)

@Preview(name = "G-1 마이", widthDp = 390, heightDp = 844)
@Composable
private fun MeScreenPreview() {
    RunkkTheme(reduceMotion = true) {
        MeScreen(
            state = MeUiState(
                isLoading = false,
                profile = Profile(
                    7,
                    "달리는곰",
                    "SELF",
                    "골드",
                    3,
                    "퇴근 뒤 한강",
                    "MORNING_SPARROW",
                    null,
                    "새벽크루",
                    Distance(21_097),
                    totalDistance = Distance(128_400),
                    runCount = 42
                )
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
