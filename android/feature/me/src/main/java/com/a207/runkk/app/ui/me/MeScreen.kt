package com.a207.runkk.app.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "마이",
                style = RunkkTextStyles.text(24, androidx.compose.ui.text.font.FontWeight.Black),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.weight(1f).semantics {
                    heading()
                }
            )
            RunkkIconButton(RunkkIcons.Bell, contentDescription = "알림함", onClick = { onNavigate(Routes.NOTIFICATIONS) })
            RunkkIconButton(RunkkIcons.Settings, contentDescription = "설정", onClick = { onNavigate(Routes.SETTINGS) })
        }
        when {
            state.loading && state.profile == null -> Box(Modifier.fillMaxWidth().padding(vertical = 32.dp)) {
                RunkkLoading()
            }

            state.profile != null -> ProfileCard(state.profile, onEdit = { onNavigate(Routes.PROFILE_EDIT) })

            state.error != null -> RunkkErrorState(state.error, onRetry = onRetry)
        }
        MenuSection(
            "내 러닝",
            listOf(
                "기록 목록·달력" to Routes.RECORDS,
                "성장 분석" to Routes.GROWTH,
                "리포트" to Routes.REPORT,
                "랭킹" to Routes.RANKING,
                "목표·러닝 코치" to Routes.COACH,
                "함께 달린 기록" to Routes.BATTLE_HISTORY
            ),
            onNavigate
        )
        MenuSection(
            "캐릭터",
            listOf("MY Runimal" to Routes.RUNIMAL, "아바타 꾸미기" to Routes.AVATAR, "상점" to Routes.SHOP),
            onNavigate
        )
        MenuSection(
            "설정",
            listOf(
                "자유런 설정" to Routes.FREE_RUN_SETTINGS,
                "알림 설정" to Routes.NOTIFICATION_SETTINGS,
                "동의 관리" to Routes.CONSENTS,
                "앱 설정" to Routes.SETTINGS
            ),
            onNavigate
        )
        RunkkCard(contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.Top) {
            RunkkMenuRow("로그아웃", onClick = { confirmLogout = true })
            HorizontalDivider(color = RunkkColors.Border)
            RunkkMenuRow("회원 탈퇴", onClick = { onNavigate(Routes.WITHDRAW) }, accent = true)
        }
    }
    if (confirmLogout) {
        RunkkConfirmDialog(
            title = "로그아웃할까요?",
            message = "기기에 남은 미전송 기록은 지우지 않고 다음 로그인 때 보내요.",
            confirmLabel = "로그아웃",
            onConfirm = {
                confirmLogout = false
                onLogout()
            },
            onDismiss = { confirmLogout = false },
            destructive = true
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileCard(profile: Profile, onEdit: () -> Unit) {
    RunkkCard(shape = RunkkShapes.ExtraLarge, verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    profile.displayName,
                    style = RunkkTextStyles.text(18, androidx.compose.ui.text.font.FontWeight.Black),
                    color = RunkkColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        profile.tier ?: "언랭",
                        profile.level?.let {
                            "Lv.$it"
                        },
                        profile.crewName
                    ).joinToString(" · "),
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.TextSecondary
                )
            }
            RunkkPillButton("편집", onClick = onEdit)
        }
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
            MiniStat("Runimal", profile.runimalType ?: "판정 전", Modifier.weight(1f))
            MiniStat(
                "최장 거리",
                profile.longestDistance?.let {
                    "${RunkkFormat.km(it)} km"
                } ?: RunkkFormat.EMPTY,
                Modifier.weight(1f)
            )
        }
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

@Composable
private fun MenuSection(title: String, items: List<Pair<String, String>>, onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkSectionTitle(title, modifier = Modifier.padding(horizontal = 8.dp))
        RunkkCard(contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.Top) {
            items.forEachIndexed { index, (label, route) ->
                RunkkMenuRow(label, onClick = { onNavigate(route) })
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
                loading = false,
                profile = Profile(
                    7, "달리는곰", "SELF", "골드", 3,
                    setOf(
                        "SOCIAL",
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
