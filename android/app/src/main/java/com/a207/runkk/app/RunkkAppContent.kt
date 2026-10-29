package com.a207.runkk.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkLogo
import com.a207.runkk.app.core.designsystem.RunkkLogoKind
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.Routes

/**
 * 앱 화면의 시작점. 로그인 상태(SessionRepository.loggedIn)를 처음 알게 되면 시작 경로를 한 번 정한다:
 * 로그인했으면 홈, 아니면 로그인. 아직 모르면([loggedIn] = null) 스플래시를 유지한다.
 * 시작 뒤 로그인 상태가 풀리면 RunkkNavHost가 로그인으로 돌린다.
 */
@Composable
fun RunkkAppContent(
    featureEntries: Set<FeatureEntry>,
    loggedIn: Boolean?,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    pendingInvite: String? = null,
    onInviteOpened: () -> Unit = {}
) {
    var startRoute by remember { mutableStateOf<String?>(null) }
    if (startRoute == null && loggedIn != null) startRoute = startRouteFor(loggedIn)
    val start = startRoute
    if (start == null) {
        RunkkSplash(modifier)
    } else {
        RunkkNavHost(
            featureEntries = featureEntries,
            startRoute = start,
            onExit = onExit,
            modifier = modifier,
            loggedIn =
                loggedIn ?: false,
            pendingInvite = pendingInvite,
            onInviteOpened = onInviteOpened
        )
    }
}

/** 로그인 상태 → 시작 경로. */
internal fun startRouteFor(loggedIn: Boolean): String = if (loggedIn) Routes.HOME else Routes.LOGIN

/** 스플래시: 창 배경과 같은 색 위에 RR 아이콘. */
@Composable
internal fun RunkkSplash(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.app_splash_description)
    Box(
        modifier = modifier.fillMaxSize().background(RunkkColors.Background).semantics {
            contentDescription = description
        },
        contentAlignment = Alignment.Center
    ) {
        RunkkLogo(RunkkLogoKind.Icon, height = 48.dp, contentDescription = null)
    }
}
