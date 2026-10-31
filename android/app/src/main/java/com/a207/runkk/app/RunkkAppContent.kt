package com.a207.runkk.app

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.FirstScreenGate
import com.a207.runkk.app.core.designsystem.LocalCoveredBySplash
import com.a207.runkk.app.core.designsystem.LocalFirstScreenGate
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkHoppingWordmark
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.Routes
import kotlinx.coroutines.delay

/**
 * 앱 화면의 시작점. 로그인 상태(SessionRepository.loggedIn)를 처음 알게 되면 시작 경로를 한 번 정한다:
 * 로그인했으면 홈, 아니면 로그인. 아직 모르면([loggedIn] = null) 스플래시를 유지한다.
 * 시작 경로를 알면 첫 화면을 스플래시 아래에서 미리 그려 두고, 워드마크가 다 뛰고 첫 화면이 다 읽을 때까지(HoldSplashUntil,
 * 길어도 [FIRST_SCREEN_MAX_WAIT_MS]) 스플래시를 걷지 않는다(사용자 요청 2026-10-31: 남자 캐릭터가 보였다가 여자로 바뀌지 않게).
 * 시작 뒤 로그인 상태가 풀리면 RunkkNavHost가 로그인으로 돌린다.
 */
@Composable
fun RunkkAppContent(
    featureEntries: Set<FeatureEntry>,
    loggedIn: Boolean?,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    pendingInvite: PendingInvite? = null,
    onInviteDone: () -> Unit = {}
) {
    var startRoute by remember { mutableStateOf<String?>(null) }
    // 스플래시 워드마크가 한 번 다 뛸 때까지는 시작 경로를 알아도 스플래시를 둔다.
    var isIntroDone by remember { mutableStateOf(false) }
    var isRevealed by remember { mutableStateOf(false) }
    val gate = remember { FirstScreenGate() }
    if (startRoute == null && loggedIn != null) startRoute = startRouteFor(loggedIn)
    val start = startRoute
    val canReveal = start != null && isIntroDone && !gate.isHeld
    LaunchedEffect(canReveal) {
        if (!canReveal) return@LaunchedEffect
        // 첫 화면이 처음 그려진 프레임의 붙잡기(HoldSplashUntil)가 돈 뒤에 걷는다: 그 사이 붙잡으면 이 효과는 취소된다
        withFrameNanos {}
        isRevealed = true
    }
    LaunchedEffect(start != null && isIntroDone) {
        if (start == null || !isIntroDone) return@LaunchedEffect
        delay(FIRST_SCREEN_MAX_WAIT_MS)
        isRevealed = true
    }
    Box(modifier = modifier.fillMaxSize()) {
        if (start != null) {
            CompositionLocalProvider(
                LocalFirstScreenGate provides gate,
                LocalCoveredBySplash provides !isRevealed
            ) {
                RunkkNavHost(
                    featureEntries = featureEntries,
                    startRoute = start,
                    onExit = onExit,
                    modifier = Modifier.fillMaxSize(),
                    loggedIn =
                        loggedIn ?: false,
                    pendingInvite = pendingInvite,
                    onInviteDone = onInviteDone
                )
            }
        }
        if (!isRevealed) {
            RunkkSplash(onIntroDone = { isIntroDone = true })
        }
    }
}

/** 첫 화면이 다 읽기를 기다리는 가장 긴 시간(네트워크가 느려도 스플래시에 갇히지 않게). */
private const val FIRST_SCREEN_MAX_WAIT_MS = 4_000L

/** 로그인 상태 → 시작 경로. */
internal fun startRouteFor(loggedIn: Boolean): String = if (loggedIn) Routes.HOME else Routes.LOGIN

/**
 * 스플래시: 창 배경과 같은 색 위에 "런크크" 워드마크(RR 아이콘은 쓰지 않는다, 사용자 요청 2026-10-30).
 * 글자가 차례로 한 번 뛰어오르고 끝나면 [onIntroDone]을 부른다.
 */
@Composable
internal fun RunkkSplash(modifier: Modifier = Modifier, onIntroDone: () -> Unit = {}) {
    val description = stringResource(R.string.app_splash_description)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(RunkkColors.Background)
            // 아래에 미리 그려 둔 첫 화면이 눌리지 않게 손가락을 모두 받는다
            .pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        RunkkHoppingWordmark(width = 200.dp, onFinished = onIntroDone)
    }
}
