package com.a207.runkk.watch.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text

/**
 * 미로그인 안내(FR-242): 로고와 "폰에서 런크크에 로그인해 주세요"만 둔다. 워치에서는 로그인·러닝 시작을 받지 않고, 로그인 정보가
 * 오면 메인으로 바뀐다(WatchFlowViewModel).
 */
@Composable
fun WatchNotLoggedInScreen(modifier: Modifier = Modifier) {
    TrackBackground(isDimmed = true, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
        ) {
            Image(
                painter = painterResource(R.drawable.watch_logo),
                contentDescription = stringResource(R.string.logo_description),
                modifier = Modifier.width(56.dp)
            )
            Text(
                text = stringResource(R.string.not_logged_in),
                style = labelStyle(color = WatchColors.OnSurface, size = 14.sp),
                textAlign = TextAlign.Center
            )
        }
    }
}
