package com.a207.runkk.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.AvatarBackdrop
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.GuideStep
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.LocationConsentSheet
import com.a207.runkk.app.core.designsystem.RunkkAvatar
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLogo
import com.a207.runkk.app.core.designsystem.RunkkLogoKind
import com.a207.runkk.app.core.designsystem.RunkkProgressBar
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.UnreleasedFeature
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.designsystem.guideAnchor
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import java.util.Locale

/** 홈(A-4): 상단(프로필 원·닉네임·티어·Lv, 리워드 잔액, 알림함), 이번 주 막대·연속·러닝 설정, 자유런·배틀런 카드, 가운데 아바타. */
@Composable
fun HomeRoute(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 러닝 결과·다른 화면에서 돌아오면 다시 읽는다(처음 표시는 init이 읽는다).
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }
    val navigator = LocalRunkkNavigator.current
    val context = LocalContext.current
    // 위치 동의가 끝났는데 OS 위치 권한이 없으면 권한 창을 먼저 띄우고, 답과 상관없이 러닝 화면으로 간다(거절은 러닝 화면이 안내한다).
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        navigator.navigate(Routes.RUN)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                HomeEvent.StartRun -> if (LOCATION_PERMISSIONS.any {
                        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
                    }
                ) {
                    navigator.navigate(Routes.RUN)
                } else {
                    permission.launch(LOCATION_PERMISSIONS)
                }
            }
        }
    }
    HomeScreen(
        state = state,
        actions = HomeActions(
            onNavigate = navigator::navigate,
            onFreeRun = viewModel::onFreeRunTapped,
            onRetry = viewModel::refresh,
            onConsentAgree = viewModel::onConsentAgreed,
            onConsentClose = viewModel::onConsentClosed,
            onMessageShown = viewModel::onMessageShown
        )
    )
}

private val LOCATION_PERMISSIONS =
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/** 홈의 사용자 행동. */
data class HomeActions(
    val onNavigate: (String) -> Unit = {},
    val onFreeRun: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onConsentAgree: () -> Unit = {},
    val onConsentClose: () -> Unit = {},
    val onMessageShown: () -> Unit = {}
)

@Composable
fun HomeScreen(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.asString()
    LaunchedEffect(state.message) {
        message?.let {
            snackbar.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RunkkLogo(
                RunkkLogoKind.WordmarkRrun,
                height = 28.dp,
                contentDescription = stringResource(R.string.home_logo_description)
            )
            Header(state, actions.onNavigate)
            Room(state, actions)
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }
    if (state.isConsentSheetVisible) {
        LocationConsentSheet(onAgree = actions.onConsentAgree, onClose = actions.onConsentClose)
    }
}

/** 상단: 프로필 원(기본 아바타), 닉네임·"[티어], Lv.[n]", 리워드 잔액, 알림함. */
@Composable
private fun Header(state: HomeUiState, onNavigate: (String) -> Unit) {
    val isAvatarOpen = LocalReleaseScope.current.isOpen(UnreleasedFeature.AVATAR_CUSTOMIZE)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(RunkkColors.SurfacePressed),
            contentAlignment = Alignment.Center
        ) {
            RunkkAvatar(state.faceKey, AvatarExpression.FACE, Modifier.size(44.dp), state.faceLayers)
        }
        HeaderIdentity(state, modifier = Modifier.weight(1f))
        // 상점은 아바타 꾸미기와 함께 연다. 닫혀 있으면 잔액만 보인다(D-252, TC-9700).
        BalancePill(
            balance = state.header.balance,
            onClick = if (isAvatarOpen) ({ onNavigate(Routes.SHOP) }) else null
        )
        CircleIconButton(
            icon = RunkkIcons.Bell,
            description = stringResource(R.string.home_header_notifications),
            onClick = { onNavigate(Routes.NOTIFICATIONS) }
        )
    }
}

/** 닉네임과 티어·레벨 줄(불러오는 동안은 회색 막대). */
@Composable
private fun HeaderIdentity(state: HomeUiState, modifier: Modifier = Modifier) {
    val header = state.header
    val tierLevel = stringResource(
        R.string.home_header_tier_level,
        header.tier ?: stringResource(R.string.home_header_default_tier),
        header.level ?: RunkkFormat.EMPTY
    )
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
        },
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (state.isHeaderLoading) {
            SkeletonBar(width = 96, height = 16)
            SkeletonBar(width = 72, height = 12)
        } else {
            Text(
                text = header.nickname ?: stringResource(R.string.home_header_default_nickname),
                style = RunkkTextStyles.text(16, FontWeight.Black),
                color = RunkkColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )
            Text(text = tierLevel, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
        }
    }
}

/** 리워드 잔액 알약 "n,nnn P". [onClick]이 없으면 누를 수 없는 표시다. 잔액을 모르면 "-". */
@Composable
private fun BalancePill(balance: Int?, onClick: (() -> Unit)?) {
    val amount = balance?.let { String.format(Locale.US, "%,d", it) } ?: RunkkFormat.EMPTY
    val description = stringResource(R.string.home_balance_description, amount)
    val openShop = stringResource(R.string.home_balance_open_shop)
    val clickable = if (onClick != null) {
        Modifier.minimumInteractiveComponentSize().clickable(
            role = Role.Button,
            onClickLabel = openShop,
            onClick = onClick
        )
    } else {
        Modifier
    }
    Box(
        modifier = clickable.clearAndSetSemantics {
            contentDescription = description
            if (onClick != null) role = Role.Button
        },
        contentAlignment = Alignment.Center
    ) {
        Text(
            stringResource(R.string.home_balance_value, amount),
            style = RunkkTextStyles.text(15, FontWeight.Bold),
            color = RunkkColors.OnLime,
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(RunkkColors.Lime)
                .padding(horizontal = 16.dp, vertical = 9.dp)
        )
    }
}

/** 바이올렛 원 아이콘 버튼(알림함·러닝 설정). 보이는 크기 40dp, 터치 48dp. */
@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(RunkkColors.Violet),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

/** 불러오는 중 자리(회색 막대). */
@Composable
private fun SkeletonBar(width: Int, height: Int) {
    Box(
        Modifier
            .width(width.dp)
            .height(height.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(RunkkColors.Border)
    )
}

/**
 * 기본 배경 위 방(A-4): 이번 주 막대 줄, 자유런·배틀런 카드, 가운데 아바타. 배경은 아바타 꾸미기가 닫혀 있으면 기본 배경이다(D-254).
 */
@Composable
private fun Room(state: HomeUiState, actions: HomeActions) {
    val isAvatarOpen = LocalReleaseScope.current.isOpen(UnreleasedFeature.AVATAR_CUSTOMIZE)
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROOM_MIN_HEIGHT)
            .clip(shape)
            .border(1.dp, RunkkColors.Border, shape)
    ) {
        AvatarBackdrop(if (isAvatarOpen) state.backgroundKey else null, modifier = Modifier.matchParentSize())
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WeekRow(state, onSettings = { actions.onNavigate(Routes.FREE_RUN_SETTINGS) })
            RunCards(state, actions)
            // 대시보드를 못 읽었으면 막대 아래에 다시 읽기를 둔다(지난 값은 그대로 보인다).
            state.error?.let { error ->
                RunkkErrorState(
                    error.asString(),
                    onRetry = actions.onRetry,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(RunkkColors.Surface)
                )
            }
            RoomAvatar(state, isAvatarOpen, onAvatar = { actions.onNavigate(Routes.AVATAR) })
        }
    }
}

/** "이번 주 [n] / [n]km" 막대와 "[n]일 연속"(흰 알약, 가이드 앵커 WEEKLY_GOAL), 러닝 설정(해 모양) 버튼. */
@Composable
private fun WeekRow(state: HomeUiState, onSettings: () -> Unit) {
    val actual = compactKm(state.weeklyDistance)
    val goal = state.weeklyGoal?.let(::compactKm)
    val spoken = goal?.let { stringResource(R.string.home_week_spoken, actual, it) }
        ?: stringResource(R.string.home_week_spoken_no_goal, actual)
    val streak = stringResource(R.string.home_streak, state.streakDays)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .guideAnchor(GuideStep.WEEKLY_GOAL)
                .clip(RoundedCornerShape(24.dp))
                .background(RunkkColors.Surface)
                .clearAndSetSemantics { contentDescription = "$spoken, $streak" }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                buildAnnotatedString {
                    append(stringResource(R.string.home_week_label))
                    append(" ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Black, fontSize = RunkkTextStyles.CardTitle.fontSize)) {
                        append(actual)
                    }
                    append(" / ")
                    append(stringResource(R.string.home_week_goal, goal ?: RunkkFormat.EMPTY))
                },
                style = RunkkTextStyles.Label,
                color = RunkkColors.TextPrimary,
                maxLines = 1
            )
            RunkkProgressBar(progress = state.weeklyProgress, height = 6.dp, modifier = Modifier.weight(1f))
            Text(streak, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary, maxLines = 1)
        }
        CircleIconButton(
            icon = RunkkIcons.Sun,
            description = stringResource(R.string.home_run_settings),
            onClick = onSettings
        )
    }
}

/** 자유런(라임)·배틀런(바이올렛) 카드 두 장. 가이드 앵커 FREE_RUN·BATTLE_RUN. */
@Composable
private fun RunCards(state: HomeUiState, actions: HomeActions) {
    val on = stringResource(R.string.home_free_on)
    val off = stringResource(R.string.home_free_off)
    val spokenOn = stringResource(R.string.home_free_spoken_on)
    val spokenOff = stringResource(R.string.home_free_spoken_off)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RunCard(
            title = stringResource(R.string.home_free_title),
            caption = stringResource(
                R.string.home_free_options,
                if (state.isRunVoiceOn) on else off,
                if (state.isRunVibrationOn) on else off
            ),
            spokenCaption = stringResource(
                R.string.home_free_spoken,
                if (state.isRunVoiceOn) spokenOn else spokenOff,
                if (state.isRunVibrationOn) spokenOn else spokenOff
            ),
            container = RunkkColors.Lime,
            content = RunkkColors.OnLime,
            onClick = actions.onFreeRun,
            modifier = Modifier.weight(1f).guideAnchor(GuideStep.FREE_RUN)
        )
        val battleCaption = stringResource(R.string.home_battle_caption)
        RunCard(
            title = stringResource(R.string.home_battle_title),
            caption = battleCaption,
            spokenCaption = battleCaption,
            container = RunkkColors.Violet,
            content = Color.White,
            onClick = { actions.onNavigate(Routes.BATTLE_MODE) },
            modifier = Modifier.weight(1f).guideAnchor(GuideStep.BATTLE_RUN)
        )
    }
}

@Composable
private fun RunCard(
    title: String,
    caption: String,
    spokenCaption: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .heightIn(min = 80.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(container)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "$title, $spokenCaption"
                role = Role.Button
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, style = RunkkTextStyles.text(18, FontWeight.Black), color = content)
        Text(caption, style = RunkkTextStyles.Caption, color = content)
    }
}

/** 가운데 아바타. 아바타 꾸미기가 열려 있을 때만 눌러 꾸미기로 간다(1차 배포는 눌러도 반응 없음, D-252). */
@Composable
private fun RoomAvatar(state: HomeUiState, isAvatarOpen: Boolean, onAvatar: () -> Unit) {
    val description = stringResource(R.string.home_avatar_description)
    val editLabel = stringResource(R.string.home_house_edit_avatar)
    val press = if (isAvatarOpen) {
        Modifier.clickable(role = Role.Button, onClickLabel = editLabel, onClick = onAvatar)
    } else {
        Modifier
    }
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(32.dp))
                .then(press)
                .semantics { contentDescription = description }
        ) {
            RunkkAvatar(state.faceKey, AvatarExpression.STAND, Modifier.size(AVATAR_SIZE), state.faceLayers)
        }
    }
}

/** 주간 막대의 km 표기: 정수면 정수, 아니면 소수 한 자리("12", "12.5"). */
internal fun compactKm(distance: Distance): String {
    val text = RunkkFormat.kmShort(distance)
    return text.removeSuffix(".0")
}

private val AVATAR_SIZE = 240.dp
private val ROOM_MIN_HEIGHT = 560.dp

@Preview(name = "A-4 홈", widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenPreview() {
    RunkkTheme(reduceMotion = true) {
        HomeScreen(
            state = HomeUiState(
                isLoading = false,
                header = HomeHeader("달리는곰", "골드", 3, 1_250),
                isHeaderLoading = false,
                weeklyDistance = Distance(12_000),
                weeklyGoal = Distance(20_000),
                streakDays = 4
            ),
            actions = HomeActions()
        )
    }
}
