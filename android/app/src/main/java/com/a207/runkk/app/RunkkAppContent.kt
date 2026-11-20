package com.a207.runkk.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.a207.runkk.app.core.designsystem.FirstScreenGate
import com.a207.runkk.app.core.designsystem.LocalCoveredBySplash
import com.a207.runkk.app.core.designsystem.LocalFirstScreenGate
import com.a207.runkk.app.core.designsystem.RunkkStartLoading
import com.a207.runkk.app.core.designsystem.rememberStartLoadingMessage
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.sync.PreloadProgress
import com.a207.runkk.app.domain.sync.PreloadState

/**
 * 앱 화면의 시작점. 로그인 상태(SessionRepository.loggedIn)를 처음 알게 되면 시작 경로를 한 번 정한다:
 * 로그인했으면 홈, 아니면 로그인. 아직 모르면([loggedIn] = null) 스플래시를 유지한다.
 * 워드마크가 다 뛰고 나면 첫 화면을 스플래시 아래에서 미리 그려 두고, 첫 화면이 다 읽을 때까지(HoldSplashUntil) 스플래시를 걷지
 * 않는다(사용자 요청 2026-10-31: 남자 캐릭터가 보였다가 여자로 바뀌지 않게). 시간 상한은 없다(S15P31A207-345 9회차, 사용자 결정 "다
 * 받을 때까지 기다린다"): 첫 화면은 시작 로딩이 받아 둔 사본으로 바로 그리고, 받지 못해도 첫 화면이 오류 상태로 붙잡기를 놓는다.
 * 첫 화면을 워드마크가 뛰는 동안 같이 그리면 첫 화면 구성(글꼴·아바타 그림 읽기)이 메인 스레드를 잡아 글자 뜀 프레임이 통째로
 * 빠진다. 그래서 "런"·"크"·"크"가 차례로 뛰는 릴리스(v0.11.x) 모습이 보이도록 뜀이 끝난 뒤에 첫 화면을 그린다.
 * 시작 뒤 로그인 상태가 풀리면 RunkkNavHost가 로그인으로 돌린다.
 *
 * 시작 에셋 미리 받기(S15P31A207-345): [preload]가 Ready가 될 때까지 첫 화면을 그리지 않고 로딩 화면(워드마크 아래 진행 막대·퍼센트)에
 * 둔다. 시간 상한은 없다. 받지 못했으면(Failed) 「에셋을 받지 못했어요」와 「다시 시도」([onRetryPreload], 남은 것부터 이어 받기)를
 * 보이고 홈으로 넘어가지 않는다. 한 번 Ready가 되면 뒤에 다시 받기 시작해도(로그인 전환) 첫 화면을 다시 덮지 않는다.
 */
@Composable
fun RunkkAppContent(
    featureEntries: Set<FeatureEntry>,
    loggedIn: Boolean?,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    pendingInvite: PendingInvite? = null,
    onInviteDone: () -> Unit = {},
    pendingBattleReturn: Long? = null,
    onBattleReturnDone: () -> Unit = {},
    pendingSelfieResult: Boolean = false,
    onSelfieResultDone: () -> Unit = {},
    preload: PreloadState = PreloadState.Ready(PreloadProgress.UNKNOWN),
    onRetryPreload: () -> Unit = {}
) {
    var startRoute by remember { mutableStateOf<String?>(null) }
    // 스플래시 워드마크가 한 번 다 뛸 때까지는 시작 경로를 알아도 스플래시를 둔다.
    var isIntroDone by remember { mutableStateOf(false) }
    var isRevealed by remember { mutableStateOf(false) }
    // 첫 화면이 한 프레임 그려졌는가: 그 프레임에 HoldSplashUntil이 붙잡은 뒤에야 걷기를 따진다
    var isFirstScreenMounted by remember { mutableStateOf(false) }
    // 에셋을 다 받았는가(한 번 참이면 그대로 둔다)
    var isPreloaded by remember { mutableStateOf(false) }
    if (!isPreloaded && preload is PreloadState.Ready) isPreloaded = true
    val gate = remember { FirstScreenGate() }
    if (startRoute == null && loggedIn != null) startRoute = startRouteFor(loggedIn)
    val start = startRoute
    val canShowFirst = start != null && isIntroDone && isPreloaded
    val canReveal = canShowFirst && isFirstScreenMounted && !gate.isHeld
    LaunchedEffect(canReveal) {
        if (!canReveal) return@LaunchedEffect
        // 첫 화면이 처음 그려진 프레임의 붙잡기(HoldSplashUntil)가 돈 뒤에 걷는다: 그 사이 붙잡으면 이 효과는 취소된다
        withFrameNanos {}
        isRevealed = true
    }
    Box(modifier = modifier.fillMaxSize()) {
        // 뜀이 끝나기 전과 에셋을 다 받기 전에는 첫 화면을 그리지 않는다(위 설명: 뜀 프레임이 빠지지 않게, 받은 에셋으로 그리게)
        if (canShowFirst && start != null) {
            LaunchedEffect(Unit) {
                withFrameNanos {}
                isFirstScreenMounted = true
            }
            CompositionLocalProvider(
                LocalFirstScreenGate provides gate,
                LocalCoveredBySplash provides !isRevealed
            ) {
                RunkkNavHost(
                    featureEntries = featureEntries,
                    startRoute = start,
                    onExit = onExit,
                    // 덮인 동안은 보조 기술에서도 숨긴다(TalkBack이 스플래시 아래 홈을 누르지 못하게, 리뷰 AN11-4)
                    modifier = Modifier.fillMaxSize().then(
                        if (isRevealed) {
                            Modifier
                        } else {
                            Modifier.clearAndSetSemantics {
                            }
                        }
                    ),
                    loggedIn =
                        loggedIn ?: false,
                    pendingInvite = pendingInvite,
                    onInviteDone = onInviteDone,
                    pendingBattleReturn = pendingBattleReturn,
                    onBattleReturnDone = onBattleReturnDone,
                    pendingSelfieResult = pendingSelfieResult,
                    onSelfieResultDone = onSelfieResultDone
                )
            }
        }
        if (!isRevealed) {
            RunkkSplash(
                onIntroDone = { isIntroDone = true },
                preload = preload.takeUnless { isPreloaded },
                onRetry = onRetryPreload
            )
        }
    }
}

/** 로그인 상태 → 시작 경로. */
internal fun startRouteFor(loggedIn: Boolean): String = if (loggedIn) Routes.HOME else Routes.LOGIN

/**
 * 스플래시(시작 로딩 화면): 창 배경과 같은 색 위에 "런크크" 워드마크(RR 아이콘은 쓰지 않는다, 사용자 요청 2026-10-30).
 * 글자가 차례로 한 번 뛰어오르고 끝나면 [onIntroDone]을 부른다. 에셋을 받는 중이면([preload]에 받을 파일이 있음) 워드마크 아래 상태
 * 문구(단계별, startLoadingMessageOf + 최소 유지)·처음 설치 안내와 진행 막대·퍼센트를, 받지 못했으면 안내와 「다시 시도」([onRetry])를
 * 보인다(S15P31A207-345).
 *
 * 아래에 미리 그려 둔 첫 화면이 눌리지 않게 손가락을 받되 소비하지는 않는다: 맨 위 형제(스플래시)가 손가락을 받으면 아래 형제는 받지
 * 못한다(Compose 형제 적중 규칙). 소비하면 「다시 시도」 단추의 누름이 누르는 사이 손가락이 조금만 움직여도 취소된다(Main 단계에서 부모가
 * 움직임을 소비하면 자식 탭 감지가 Final 단계에서 취소로 본다, 7회차 「첫 누름 무반응」).
 */
@Composable
internal fun RunkkSplash(
    modifier: Modifier = Modifier,
    onIntroDone: () -> Unit = {},
    preload: PreloadState? = null,
    onRetry: () -> Unit = {}
) {
    val description = stringResource(R.string.app_splash_description)
    val message = rememberStartLoadingMessage(preload?.let(::startLoadingMessageOf))
    RunkkStartLoading(
        percent = preload?.progress?.takeIf { it.hasBar && preload !is PreloadState.Ready }?.percent,
        isFailed = preload is PreloadState.Failed,
        onRetry = onRetry,
        onIntroDone = onIntroDone,
        message = message.takeUnless { preload is PreloadState.Failed },
        isFirstInstall = (preload as? PreloadState.Loading)?.isFirstRun == true,
        modifier = modifier
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .semantics { contentDescription = description }
    )
}
