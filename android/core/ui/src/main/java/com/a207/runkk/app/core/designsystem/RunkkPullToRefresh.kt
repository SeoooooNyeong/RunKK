package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 당겨서 새로고침(설계 05-32 [임시:FR-성-366]): Material3 PullToRefreshBox에 앱 색(표면 위 라임 원)을 입혔다. [content] 안의 세로 스크롤
 * (LazyColumn·verticalScroll)을 위로 더 당기면 [onRefresh]를 부르고, [isRefreshing]인 동안 위에 도는 원을 보인다. 읽기 실패는 부르는
 * 화면이 지금 값을 두고 안내한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunkkPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = RunkkColors.Surface,
                color = RunkkColors.Violet
            )
        },
        content = content
    )
}
