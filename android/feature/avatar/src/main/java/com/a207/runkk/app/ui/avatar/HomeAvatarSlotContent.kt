package com.a207.runkk.app.ui.avatar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.avatar.SelfieProgress

/**
 * 홈 아바타 칸 내용(SlotKey.HOME_AVATAR, design-oo 05-30 2절): 칸(390×420, 홈이 고정)을 채워 내 아바타를 서 있게 그린다. 배경 사진은
 * 홈이 화면 전체에 깔므로 그리지 않고 배경 규격으로 바닥선만 잡는다([StageFloorPainter]). 준비 중이면 로딩 표시, 실패면 성별 기본 그림.
 *
 * 칸이 처음 그려질 때 저장소를 데우고(스플래시 아래에서 홈이 먼저 그려지면 Overlay보다 빠르다), 준비가 끝난 뒤 다시 들어오면(꾸미기·
 * 상점에서 돌아옴) 입력을 다시 읽는다. 다시 읽는 동안에는 앞 그림을 그대로 둔다.
 *
 * 셀카 얼굴을 만드는 중이면 칸 위에 「얼굴 만드는 중」 배지를 띄우고, 누르면 얼굴 만들기로 간다(피그마 AV-7 4, D-779). 칸이
 * 보일 때마다 남은 작업을 직접 확인해 워커가 돌지 못해도 끝난 작업의 배지가 남지 않는다(D-820).
 */
@Composable
internal fun HomeAvatarSlotContent(store: MyAvatarStore) {
    LaunchedEffect(store) {
        store.warm()
        store.revalidate()
    }
    // 칸이 보일 때마다(앱이 다시 앞으로 온 때 포함) 남은 셀카 작업을 직접 확인한다(D-820).
    LifecycleStartEffect(store) {
        store.checkSelfie()
        onStopOrDispose {}
    }
    val state by store.state.collectAsStateWithLifecycle()
    val selfie by store.selfieProgress.collectAsStateWithLifecycle(SelfieProgress.None)
    val navigator = LocalRunkkNavigator.current
    Box(Modifier.fillMaxSize()) {
        AvatarStage(state, Modifier.fillMaxSize(), backdrop = StageFloorPainter.BACKDROP_SPEC)
        if (selfie is SelfieProgress.Working) {
            SelfieWorkingBadge(
                onClick = { navigator.navigate(Routes.AVATAR_SETUP) },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
            )
        }
    }
}
