package com.a207.runkk.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.AvatarBackdrop
import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.GuideStep
import com.a207.runkk.app.core.designsystem.HoldSplashUntil
import com.a207.runkk.app.core.designsystem.LocalAvatarCharacter
import com.a207.runkk.app.core.designsystem.LocalReduceMotion
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.LocalRunkkColors
import com.a207.runkk.app.core.designsystem.LocationConsentSheet
import com.a207.runkk.app.core.designsystem.RUNKK_BOTTOM_BAR_HEIGHT
import com.a207.runkk.app.core.designsystem.RUNKK_BOTTOM_BAR_RISE
import com.a207.runkk.app.core.designsystem.RunkkAvatar
import com.a207.runkk.app.core.designsystem.RunkkAvatarCircle
import com.a207.runkk.app.core.designsystem.RunkkBanner
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIconButton
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLimePill
import com.a207.runkk.app.core.designsystem.RunkkProgressBar
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.TierBadge
import com.a207.runkk.app.core.designsystem.TierBadgeSize
import com.a207.runkk.app.core.designsystem.UnreleasedFeature
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.designsystem.guideAnchor
import com.a207.runkk.app.core.designsystem.media.rememberSoundEffects
import com.a207.runkk.app.core.designsystem.sceneAvatarFilter
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.LocalSlots
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.SlotKey
import com.a207.runkk.app.core.navigation.isSlotFilled
import java.util.Locale
import kotlinx.coroutines.launch

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
    var isBatteryPromptShown by rememberSaveable { mutableStateOf(false) }
    var isBatteryPromptVisible by rememberSaveable { mutableStateOf(false) }
    val startRun = {
        if (LOCATION_PERMISSIONS.any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            navigator.navigate(Routes.RUN)
        } else {
            permission.launch(LOCATION_PERMISSIONS)
        }
    }
    LaunchedEffect(viewModel, navigator, context) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.Navigate -> navigator.navigate(event.key)

                HomeEvent.StartRun -> {
                    if (!isBatteryPromptShown && shouldOfferRunBatterySettings(context)) {
                        isBatteryPromptShown = true
                        isBatteryPromptVisible = true
                    } else {
                        startRun()
                    }
                }
            }
        }
    }
    if (isBatteryPromptVisible) {
        RunkkConfirmDialog(
            title = stringResource(R.string.home_run_battery_title),
            message = stringResource(R.string.home_run_battery_message),
            confirmLabel = stringResource(R.string.home_run_battery_open_settings),
            dismissLabel = stringResource(R.string.home_run_battery_skip),
            onConfirm = {
                isBatteryPromptVisible = false
                if (!openRunBatterySettings(context)) startRun()
            },
            onDismiss = {
                isBatteryPromptVisible = false
                startRun()
            },
            onDismissRequest = { isBatteryPromptVisible = false }
        )
    }
    HomeScreen(
        state = state,
        actions = HomeActions(
            onNavigate = navigator::navigate,
            onFreeRun = viewModel::onFreeRunTapped,
            onRetry = viewModel::refresh,
            onConsentAgree = viewModel::onConsentAgreed,
            onConsentClose = viewModel::onConsentClosed,
            onMessageShown = viewModel::onMessageShown,
            onSummaryToggle = viewModel::onSummaryToggle,
            onLatestRun = viewModel::onLatestRunTapped,
            onBattleCard = viewModel::onBattleCardTapped,
            onRandom = viewModel::onRandomChosen,
            onFriend = viewModel::onFriendChosen,
            onBattleEntryDismiss = viewModel::onBattleEntryDismissed
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
    val onMessageShown: () -> Unit = {},
    /** 「내 기록 요약 ⌄」·「접기 ⌃」(FR-287). */
    val onSummaryToggle: () -> Unit = {},
    /** 최근 러닝 한 줄(기록 ID). */
    val onLatestRun: (String) -> Unit = {},
    /** 배틀런 카드: 진입 시트를 연다(D-499). */
    val onBattleCard: () -> Unit = {},
    val onRandom: () -> Unit = {},
    val onFriend: () -> Unit = {},
    val onBattleEntryDismiss: () -> Unit = {}
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
    // 앱을 처음 열 때는 첫 읽기(닉네임·성별)가 끝날 때까지 스플래시 아래에 미리 그려 둔다(남자→여자로 바뀌어 보이지 않게)
    HoldSplashUntil(isReady = !state.isLoading)
    // 배경 장면 카드는 하단 탭 바 뒤로 화면 아래 끝까지 이어진다(R2-06, D86). 홈 자리 아래(탭 바·내비게이션 바)만큼 넘겨 그린다.
    val bleed = RUNKK_BOTTOM_BAR_HEIGHT + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val isAvatarOpen = LocalReleaseScope.current.isOpen(UnreleasedFeature.AVATAR_CUSTOMIZE)
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        // 피그마 A-4: 배경 사진이 로고·헤더 뒤부터 탭 바 뒤까지 화면 전체에 깔린다(흰 바탕 없음)
        // 사진(1:2)은 바닥에 맞춰 자른다: 폴드처럼 넓은 화면에서 가운데로 자르면 트랙이 화면 아래로 밀려 아바타가 떠 보인다
        AvatarBackdrop(
            if (isAvatarOpen) state.backgroundKey else null,
            modifier = Modifier.fillMaxSize().extendBottom(bleed),
            alignment = Alignment.BottomCenter,
            // 사진을 아래 가운데 기준으로 키워 트랙이 아바타 발 밑까지 오게 한다(2026-11-12 PM: 발이 지평선 근처에 떠 보였다)
            zoom = HOME_BACKDROP_ZOOM
        )
        // 점선 방 바깥은 사진 위에 반투명 막을 덮는다(라이트 흰 막, 다크 검은 막). 방 안 장면은 그대로 둔다
        var roomBounds by remember { mutableStateOf<Rect?>(null) }
        var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
        val scrim = if (LocalRunkkColors.current.isDark) Color.Black else Color.White
        Canvas(
            Modifier
                .fillMaxSize()
                .extendBottom(bleed)
                .onGloballyPositioned { overlayOrigin = it.positionInRoot() }
        ) {
            val outside = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                roomBounds?.let { room ->
                    val corner = ROOM_CORNER.toPx()
                    addRoundRect(RoundRect(room.translate(-overlayOrigin), CornerRadius(corner, corner)))
                }
            }
            drawPath(outside, scrim.copy(alpha = OUTSIDE_SCRIM_ALPHA))
        }
        BoxWithConstraints(modifier = Modifier.fillMaxSize().extendBottom(bleed)) {
            val viewport = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = ROOM_BOTTOM_GAP)
            ) {
                FillRemaining(
                    minHeight = viewport - 16.dp - ROOM_BOTTOM_GAP,
                    fillMinHeight = ROOM_MIN_HEIGHT,
                    gap = 12.dp,
                    // 상단 RRun 로고는 두지 않는다(사용자 요청 2026-10-30).
                    top = { Top(state, actions.onNavigate) },
                    fill = {
                        Room(
                            state,
                            actions,
                            bottomSpace = bleed + RUNKK_BOTTOM_BAR_RISE,
                            modifier = Modifier.onGloballyPositioned { roomBounds = it.boundsInRoot() }
                        )
                    }
                )
            }
        }
        RunkkSnackbarHost(
            snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp + RUNKK_BOTTOM_BAR_RISE)
        )
    }
    if (state.isConsentSheetVisible) {
        LocationConsentSheet(onAgree = actions.onConsentAgree, onClose = actions.onConsentClose)
    }
    if (state.isBattleEntryVisible) {
        BattleEntrySheet(
            BattleEntrySheetState(state.placement),
            onRandom = actions.onRandom,
            onFriend = actions.onFriend,
            onDismiss = actions.onBattleEntryDismiss
        )
    }
}

/** 상단: 재전송 안내 띠(필요할 때만, W48)와 헤더. */
@Composable
private fun Top(state: HomeUiState, onNavigate: (String) -> Unit) {
    if (!state.isResendReminderVisible) {
        Header(state, onNavigate)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RunkkBanner(stringResource(R.string.home_resend_reminder))
        Header(state, onNavigate)
    }
}

/** 상단: 프로필 원(기본 아바타), 닉네임·"[티어], Lv.[n]", 리워드 잔액, 알림함. */
@Composable
private fun Header(state: HomeUiState, onNavigate: (String) -> Unit) {
    val isAvatarOpen = LocalReleaseScope.current.isOpen(UnreleasedFeature.AVATAR_CUSTOMIZE)
    val editProfile = stringResource(R.string.home_open_profile_edit)
    // 머리 줄 사이는 8dp다. 알림 단추는 터치 48dp 안에 40dp 원이라 잔액과의 보이는 간격은 12dp다. 좁은 기기·큰 글꼴에서
    // 4·5자 닉네임과 「Lv.n」이 한 줄에 다 들어가도록 알약 안팎 여백을 줄였다(실기기 DV3-9).
    Row(horizontalArrangement = Arrangement.spacedBy(HEADER_GAP), verticalAlignment = Alignment.CenterVertically) {
        // 프로필(얼굴과 닉네임)을 누르면 프로필 편집으로 간다. 얼굴과 닉네임은 둥근 바탕(라이트 흰색, 다크 검은색) 위에 둔다.
        // 남는 폭은 바깥 칸이 다 차지해 잔액·알림이 늘 오른쪽 끝에 있고, 알약은 그 안에서 내용 폭만큼만 넓어진다(남는 폭이
        // 모자랄 때만 닉네임이 줄어든다).
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(RunkkColors.Surface)
                    .clickable(role = Role.Button, onClickLabel = editProfile) { onNavigate(Routes.PROFILE_EDIT) }
                    .padding(start = 4.dp, top = 4.dp, bottom = 4.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RunkkAvatarCircle(48.dp, background = RunkkColors.SurfacePressed) {
                    // 성별을 읽기 전에는 기본 머리를 그리지 않는다(남자 얼굴이 보였다가 바뀌지 않게, TF-V12-02). 셀카 얼굴 층이 있으면
                    // (기기에 남긴 마지막 내 얼굴) 성별과 상관없으므로 첫 그림부터 그린다.
                    if (state.isAvatarReady || (isAvatarOpen && state.faceLayers.isNotEmpty())) {
                        RunkkAvatar(state.faceKey, AvatarExpression.FACE, Modifier.fillMaxSize(), state.faceLayers)
                    }
                }
                HeaderIdentity(state, modifier = Modifier.weight(1f, fill = false))
            }
        }
        // 잔액과 알림함을 묶어 첫 사용 가이드 포인트 단계(J-8)가 함께 강조한다(S15P31A207-250)
        Row(
            modifier = Modifier.guideAnchor(GuideStep.POINT),
            horizontalArrangement = Arrangement.spacedBy(HEADER_GAP),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 상점은 아바타 꾸미기와 함께 연다. 닫혀 있으면 잔액만 보인다(D-252, TC-9700).
            BalancePill(
                balance = state.header.balance,
                onClick = if (isAvatarOpen) ({ onNavigate(Routes.SHOP) }) else null
            )
            // 안 본 진행 중 이벤트가 있으면 ui.notification이 칸에 알림 점을 넣는다(피그마 A-4 62273:7142). 점 중심이 40dp 원의
            // 오른쪽 위 가장자리에서 4dp 안쪽이다(48dp 터치 칸 기준 위 2, 오른쪽 2).
            Box {
                CircleIconButton(
                    icon = RunkkIcons.Bell,
                    description = stringResource(R.string.home_header_notifications),
                    onClick = { onNavigate(Routes.NOTIFICATIONS) }
                )
                Box(Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp)) {
                    LocalSlots.current[SlotKey.HOME_INBOX_DOT].orEmpty().forEach { it() }
                }
            }
        }
    }
}

/**
 * 머리 알약의 이름 칸(FR-288, 불러오는 동안은 회색 막대): 위 줄 닉네임 + 「Lv.n」(사이 6dp), 아래 줄 티어 배지(아주 작게). 진행을 못
 * 읽었으면 배지가 없다. 「Lv.n」과 배지 글자는 늘 고유 폭을 다 쓰고 닉네임만 말줄임한다(실기기 DV3-9). 읽기는 닉네임 → 레벨 → 티어다.
 */
@Composable
private fun HeaderIdentity(state: HomeUiState, modifier: Modifier = Modifier) {
    val header = state.header
    val level = stringResource(R.string.home_header_level, header.level ?: RunkkFormat.EMPTY)
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
        },
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (state.isHeaderLoading) {
            SkeletonBar(width = 96, height = 16)
            SkeletonBar(width = 72, height = 12)
        } else {
            // Row는 무게 없는 Lv를 먼저 재고 닉네임에 나머지를 준다(닉네임만 말줄임).
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = header.nickname ?: stringResource(R.string.home_header_default_nickname),
                    style = RunkkTextStyles.text(16, FontWeight.Black),
                    color = RunkkColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .alignByBaseline()
                        .semantics { heading() }
                )
                Text(
                    text = level,
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.TextSecondary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.alignByBaseline()
                )
            }
            state.tierDisplay?.let { TierBadge(it, TierBadgeSize.XSMALL) }
        }
    }
}

/** 양옆으로 [extra]만큼 넓게 그린다(방 안쪽 여백을 넘어 화면 가로 여백까지). 자리 폭은 그대로다. */
private fun Modifier.bleedHorizontally(extra: Dp): Modifier = layout { measurable, constraints ->
    val extraPx = extra.roundToPx()
    val width = constraints.maxWidth + extraPx * 2
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extraPx, 0) }
}

/** 방 안쪽 여백. */
private val ROOM_PADDING = 12.dp

/** 아래로 [extra]만큼 더 크게 그린다. 자리 크기는 그대로라 넘친 부분은 아래(하단 탭 바 뒤)에 겹친다. */
private fun Modifier.extendBottom(extra: Dp): Modifier = layout { measurable, constraints ->
    val extraPx = extra.roundToPx()
    val height = if (constraints.hasBoundedHeight) constraints.maxHeight + extraPx else constraints.maxHeight
    val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
    layout(placeable.width, constraints.maxHeight.coerceAtMost(placeable.height)) { placeable.place(0, 0) }
}

/** [top]을 위에 두고 그 아래 [fill]이 남은 높이(전체 [minHeight]까지, 적어도 [fillMinHeight])를 채운다. 사이는 [gap]이다. */
@Composable
private fun FillRemaining(
    minHeight: Dp,
    fillMinHeight: Dp,
    gap: Dp,
    top: @Composable () -> Unit,
    fill: @Composable () -> Unit
) {
    Layout(contents = listOf(top, fill)) { (tops, fills), constraints ->
        val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val topPlaceable = tops.single().measure(loose)
        val gapPx = gap.roundToPx()
        val used = topPlaceable.height + gapPx
        val fillHeight = maxOf(fillMinHeight.roundToPx(), minHeight.roundToPx() - used)
        val fillPlaceable = fills.single().measure(loose.copy(minHeight = fillHeight))
        layout(constraints.maxWidth, used + fillPlaceable.height) {
            topPlaceable.place(0, 0)
            fillPlaceable.place(0, used)
        }
    }
}

/** 머리 줄(프로필 알약·잔액·알림) 사이 간격. */
private val HEADER_GAP = 8.dp

/** 리워드 잔액 알약 "n,nnn P". [onClick]이 없으면 누를 수 없는 표시다. 잔액을 모르면 "-". */
@Composable
private fun BalancePill(balance: Int?, onClick: (() -> Unit)?) {
    // 잔액을 모르면(아직 적립이 없거나 못 읽음) 0으로 보인다(사용자 요청 2026-10-30)
    val amount = String.format(Locale.US, "%,d", balance ?: 0)
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
        // 디자인 시스템 라임 알약: 기기 글자 크기에 맞춰 커진다(고정 높이 40에서 글자 아래가 잘렸다, 사용자 확인 2026-10-31)
        RunkkLimePill(
            stringResource(R.string.home_balance_value, amount),
            textStyle = RunkkTextStyles.text(15, FontWeight.Bold),
            horizontalPadding = 12.dp
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
    RunkkIconButton(
        icon = icon,
        contentDescription = description,
        onClick = onClick,
        borderColor = Color.Transparent,
        containerColor = RunkkColors.Violet,
        iconTint = RunkkColors.OnSecondary
    )
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
 * 방은 하단 탭 바 뒤까지 이어지므로 내용 아래에 [bottomSpace]를 비워 둔다(내용이 탭 바와 솟은 탭 원에 가리지 않게, D100).
 */
@Composable
private fun Room(state: HomeUiState, actions: HomeActions, bottomSpace: Dp, modifier: Modifier = Modifier) {
    val isAvatarOpen = LocalReleaseScope.current.isOpen(UnreleasedFeature.AVATAR_CUSTOMIZE)
    // 사진은 화면 전체 배경에 있고, 방은 테두리 없이 바깥 반투명 막으로만 구분한다
    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag(HOME_ROOM_TAG)
    ) {
        Column(
            modifier = Modifier.matchParentSize().padding(ROOM_PADDING),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WeekRow(
                state,
                onSettings = { actions.onNavigate(Routes.FREE_RUN_SETTINGS) },
                onGoal = { actions.onNavigate(Routes.COACH) }
            )
            RunCards(state, actions)
            // 대시보드를 못 읽었으면 막대 아래에 다시 읽기를 둔다(지난 값은 그대로 보인다).
            state.error?.let { error ->
                RunkkErrorState(
                    error.asString(),
                    onRetry = actions.onRetry,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(RunkkColors.Surface)
                )
            }
            // 「내 기록 요약」은 아바타 위에 겹친다(블러 없음). 펼치면 알약 자리에 화면 가로 여백 16 안쪽 전체 폭 패널이 뜬다(8절 2).
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // 아바타 칸(ui.avatar의 Rive 아바타, design-oo 05-30 2절)이 있으면 칸을, 비면(미리보기·화면 시험) 지금 그림을 그린다
                if (isSlotFilled(SlotKey.HOME_AVATAR)) {
                    HomeAvatarSlot(
                        isAvatarOpen,
                        onAvatar = { actions.onNavigate(Routes.AVATAR_RUNNER) },
                        Modifier.matchParentSize()
                    )
                } else {
                    RoomAvatar(
                        state,
                        isAvatarOpen,
                        onAvatar = { actions.onNavigate(Routes.AVATAR_RUNNER) },
                        Modifier.matchParentSize()
                    )
                }
                if (state.isSummaryExpanded) {
                    SummaryPanel(
                        state.summary,
                        onCollapse = actions.onSummaryToggle,
                        onLatestRun = actions.onLatestRun,
                        onRecords = { actions.onNavigate(Routes.RECORDS) },
                        modifier = Modifier.bleedHorizontally(ROOM_PADDING)
                    )
                } else {
                    SummaryToggle(actions.onSummaryToggle, Modifier.align(Alignment.TopEnd))
                }
            }
            Spacer(Modifier.height(bottomSpace))
        }
    }
}

/** "이번 주 [n] / [n]km" 막대와 "[n]주 연속"(흰 알약, 가이드 앵커 WEEKLY_GOAL), 러닝 설정(톱니바퀴, 2026-11-04 핫픽스) 버튼. */
@Composable
private fun WeekRow(state: HomeUiState, onSettings: () -> Unit, onGoal: () -> Unit) {
    val openGoal = stringResource(R.string.home_open_goal_coach)
    val actual = compactKm(state.weeklyDistance)
    val goal = state.weeklyGoal?.let(::compactKm)
    val spoken = goal?.let { stringResource(R.string.home_week_spoken, actual, it) }
        ?: stringResource(R.string.home_week_spoken_no_goal, actual)
    val streak = stringResource(R.string.home_streak, state.streakWeeks)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .guideAnchor(GuideStep.WEEKLY_GOAL)
                .clip(RoundedCornerShape(24.dp))
                .background(RunkkColors.Surface)
                // 이번 주 막대를 누르면 주간 목표를 정하는 목표·러닝 코치로 간다
                .clickable(role = Role.Button, onClickLabel = openGoal, onClick = onGoal)
                .clearAndSetSemantics {
                    contentDescription = "$spoken, $streak"
                    role = Role.Button
                    onClick(label = openGoal) {
                        onGoal()
                        true
                    }
                }
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
            icon = RunkkIcons.Settings,
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
            container = RunkkColors.LimeFill,
            content = RunkkColors.OnLimeFill,
            onClick = actions.onFreeRun,
            modifier = Modifier.weight(1f).guideAnchor(GuideStep.FREE_RUN)
        )
        val battleCaption = stringResource(R.string.home_battle_caption)
        RunCard(
            title = stringResource(R.string.home_battle_title),
            caption = battleCaption,
            spokenCaption = battleCaption,
            container = RunkkColors.Violet,
            content = RunkkColors.OnSecondary,
            onClick = actions.onBattleCard,
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

/**
 * 아바타. 카드 아래 남은 자리의 바닥(트랙)에 발을 붙여 세운다(피그마 A-4). 키는 남은 높이에 맞추되 [AVATAR_MAX_HEIGHT]를 넘지 않는다.
 * 아바타 꾸미기가 열려 있을 때만 눌러 꾸미기로 간다(1차 배포는 눌러도 반응 없음, D-252).
 * 본인 요약(성별)을 처음 읽기 전에는 그리지 않는다(자리만 둔다): 로그인 직후 남자 그림이 잠깐 보였다가 바뀌지 않게.
 */
@Composable
private fun RoomAvatar(state: HomeUiState, isAvatarOpen: Boolean, onAvatar: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.home_avatar_description)
    val editLabel = stringResource(R.string.home_house_edit_avatar)
    val reduceMotion = LocalReduceMotion.current
    val hop = remember { Animatable(0f) }
    var isCheering by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sounds = rememberSoundEffects(R.raw.home_jump_female, R.raw.home_jump_male)
    val jumpSound = if (LocalAvatarCharacter.current == AvatarCharacter.FEMALE) {
        R.raw.home_jump_female
    } else {
        R.raw.home_jump_male
    }
    // 1차 배포는 눌러도 이동하지 않고, 기쁜 자세로 바뀌며 소리와 함께 한 번 뛰었다 내려온다(장식이라 읽기 동작은 두지 않는다).
    // 열린 배포에서 눌러 이동할 때는 물결을 그리지 않는다(D-814).
    val interaction = remember { MutableInteractionSource() }
    val press = if (isAvatarOpen) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            role = Role.Button,
            onClickLabel = editLabel,
            onClick = onAvatar
        )
    } else {
        Modifier.pointerInput(reduceMotion, jumpSound) {
            detectTapGestures {
                if (reduceMotion || hop.isRunning) return@detectTapGestures
                sounds.play(jumpSound)
                scope.launch {
                    isCheering = true
                    hop.animateTo(1f, tween(durationMillis = HOP_UP_MS, easing = FastOutSlowInEasing))
                    hop.animateTo(0f, spring(dampingRatio = HOP_DAMPING, stiffness = Spring.StiffnessMediumLow))
                    isCheering = false
                }
            }
        }
    }
    BoxWithConstraints(
        // 발을 아래로 [AVATAR_GROUND_SINK]만큼 더 내려 트랙에 세운다(솟은 홈 탭 원은 두 발 사이에 온다)
        modifier = modifier.fillMaxWidth().offset(y = AVATAR_GROUND_SINK),
        contentAlignment = Alignment.BottomCenter
    ) {
        val height = maxHeight.coerceIn(AVATAR_MIN_HEIGHT, AVATAR_MAX_HEIGHT)
        // 발밑 그림자(납작한 동그라미, 장식). 뛰어오르면 바닥에 남아 작고 옅어진다
        Box(
            Modifier
                .offset(y = SHADOW_HEIGHT / 2)
                .size(width = height * SHADOW_WIDTH_RATIO, height = SHADOW_HEIGHT)
                .graphicsLayer {
                    scaleX = 1f - SHADOW_SHRINK * hop.value
                    scaleY = 1f - SHADOW_SHRINK * hop.value
                    alpha = 1f - SHADOW_FADE * hop.value
                }
                .drawBehind { drawOval(Color.Black.copy(alpha = SHADOW_ALPHA)) }
                .clearAndSetSemantics {}
        )
        // 뛰어오를 때 칸째로 옮긴다(칸 안에서 그림만 옮기면 둥근 모서리 자르기에 머리가 잘린다)
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationY = -hop.value * HOP_HEIGHT.toPx()
                    alpha = if (state.isAvatarReady) 1f else 0f
                }
                .clip(RoundedCornerShape(32.dp))
                // 숨긴 동안은 누르지도 읽지도 않는다(리뷰 AN11-5)
                .then(if (state.isAvatarReady) press.semantics { contentDescription = description } else Modifier)
        ) {
            RunkkAvatar(
                state.faceKey,
                if (isCheering) AvatarExpression.JOY else AvatarExpression.STAND,
                Modifier
                    .height(height)
                    .aspectRatio(AVATAR_ASPECT),
                state.faceLayers,
                colorFilter = sceneAvatarFilter()
            )
        }
    }
}

/**
 * 아바타 칸(SlotKey.HOME_AVATAR, design-oo 05-30 2절, D-732, D-766, 피그마 62341:1878): 390×420 칸에 ui.avatar가
 * 바닥선·그림자·Rive 서기·로딩 표시를 그린다. 배경 사진은 홈이 화면 전체에 깔아 두므로 칸은 배경을 다시 그리지 않고 같은 바닥 규칙
 * (칸 위에서 342dp, 아래에서 [HOME_AVATAR_FLOOR_FROM_BOTTOM])만 쓴다. 칸 바닥선이 남은 자리 아래보다
 * [HOME_AVATAR_FLOOR_ABOVE_ROOM] 위에 오게 칸을 내리고, 남은 자리가 좁으면 칸 높이를 줄인다(바닥선은 칸 아래에서 같은 거리다).
 * 아바타 꾸미기가 열려 있으면 누르면 내 러너로 간다(물결 없음, D-814). 뛰어오르기·효과음은 없다(FR-236 2차 배포 뒤 규칙).
 */
@Composable
private fun HomeAvatarSlot(isAvatarOpen: Boolean, onAvatar: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.home_avatar_description)
    val editLabel = stringResource(R.string.home_house_edit_avatar)
    val slot = LocalSlots.current[SlotKey.HOME_AVATAR].orEmpty()
    val interaction = remember { MutableInteractionSource() }
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        val drop = HOME_AVATAR_FLOOR_FROM_BOTTOM - HOME_AVATAR_FLOOR_ABOVE_ROOM
        val height = (maxHeight + drop).coerceIn(HOME_AVATAR_SLOT_MIN_HEIGHT, HOME_AVATAR_SLOT_HEIGHT)
        // 아바타 그림을 누르면 물결(반투명 눌림)을 그리지 않는다. 단추 안내·누름 영역은 그대로다(D-814).
        val press = if (isAvatarOpen) {
            Modifier.clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = editLabel,
                onClick = onAvatar
            )
        } else {
            Modifier
        }
        Box(
            Modifier
                .offset(y = drop)
                .requiredSize(HOME_AVATAR_SLOT_WIDTH, height)
                .testTag(HOME_AVATAR_SLOT_TAG)
                .then(press)
                .semantics { contentDescription = description }
        ) { slot.forEach { it() } }
    }
}

/** 주간 막대의 km 표기: 정수면 정수, 아니면 소수 한 자리("12", "12.5"). */
internal fun compactKm(distance: Distance): String {
    val text = RunkkFormat.kmShort(distance)
    return text.removeSuffix(".0")
}

private val AVATAR_MIN_HEIGHT = 160.dp
private val AVATAR_MAX_HEIGHT = 360.dp

/** 발밑 그림자 크기와 진하기(키에 비례한 너비), 뛰어오를 때 줄어드는 정도. */
private const val SHADOW_WIDTH_RATIO = 0.46f
private val SHADOW_HEIGHT = 14.dp
private const val SHADOW_ALPHA = 0.28f
private const val SHADOW_SHRINK = 0.3f
private const val SHADOW_FADE = 0.5f

/** 눌렀을 때 뛰어오르는 높이와 박자. */
private val HOP_HEIGHT = 36.dp
private const val HOP_UP_MS = 180
private const val HOP_DAMPING = 0.45f

/** 발을 남은 자리 바닥보다 더 내리는 거리. 솟은 홈 탭 원(20dp)과 신발이 조금 겹친다(사용자 요청 2026-10-30). */
private val AVATAR_GROUND_SINK = 8.dp

/** 홈 아바타 칸 크기(피그마 62341:1878, D-766). 남은 자리가 좁으면 높이만 줄인다. */
private val HOME_AVATAR_SLOT_WIDTH = 390.dp
private val HOME_AVATAR_SLOT_HEIGHT = 420.dp
private val HOME_AVATAR_SLOT_MIN_HEIGHT = 240.dp

/**
 * 홈 칸 바닥선의 칸 아래 끝으로부터 거리(4.4 계산: 390dp 칸에 1080×1200 배경 규격이면 그려진 그림 높이 433.3dp × 0.18 = 78dp,
 * 칸 높이 433dp 이하에서 같다). ui.avatar 홈 칸 내용이 같은 규격으로 바닥선을 잡는다.
 */
internal val HOME_AVATAR_FLOOR_FROM_BOTTOM = 78.dp

/**
 * Rive 칸 바닥선을 방 아래 끝보다 올리는 거리. 신발이 솟은 홈 탭 원(방 아래 끝에서 약 18dp 위까지 솟음)과 8dp쯤만 겹친다
 * (사용자 요청 2026-10-30 「조금 겹친다」, 2026-11-11 실기기 3차에서 방 아래 + 8dp는 신발 대부분이 가려짐, D-805).
 */
internal val HOME_AVATAR_FLOOR_ABOVE_ROOM = 10.dp

/** 홈 아바타 칸의 시험 표지. */
internal const val HOME_AVATAR_SLOT_TAG = "home-avatar-slot"

/** 서 있는 그림 칸의 가로세로 비(그림은 칸 바닥에 붙어 그려지므로 칸이 조금 넓어도 된다). */
private const val AVATAR_ASPECT = 0.6f
private val ROOM_MIN_HEIGHT = 560.dp

/** 배경 장면 카드(방)의 시험 표지. */
internal const val HOME_ROOM_TAG = "home-room"

/** 방 카드 아래 끝과 화면 아래 끝 사이. */
private val ROOM_BOTTOM_GAP = 8.dp

@Preview(name = "A-4 홈", widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenPreview() {
    RunkkTheme(reduceMotion = true) {
        HomeScreen(
            state = HomeUiState(
                isLoading = false,
                header = HomeHeader("달리는곰", "골드", 3, 1_250),
                isHeaderLoading = false,
                isAvatarReady = true,
                weeklyDistance = Distance(12_000),
                weeklyGoal = Distance(20_000),
                streakWeeks = 4
            ),
            actions = HomeActions()
        )
    }
}

/** 점선 방 모서리와 바깥 막의 진하기. */
private val ROOM_CORNER = 28.dp
private const val OUTSIDE_SCRIM_ALPHA = 0.4f

/** 홈 배경 사진 확대 배율(아래 가운데 기준). */
private const val HOME_BACKDROP_ZOOM = 1.35f
