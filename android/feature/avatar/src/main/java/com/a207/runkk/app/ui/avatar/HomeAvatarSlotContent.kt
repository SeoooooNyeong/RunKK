package com.a207.runkk.app.ui.avatar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 홈 아바타 칸 내용(SlotKey.HOME_AVATAR, design-oo 05-30 2절): 칸(390×420, 홈이 고정)을 채워 내 아바타를 서 있게 그린다. 배경 사진은
 * 홈이 화면 전체에 깔므로 그리지 않고 배경 규격으로 바닥선만 잡는다([StageFloorPainter]). 준비 중이면 로딩 표시, 실패면 성별 기본 그림.
 *
 * 칸이 처음 그려질 때 저장소를 데우고(스플래시 아래에서 홈이 먼저 그려지면 Overlay보다 빠르다), 준비가 끝난 뒤 다시 들어오면(꾸미기·
 * 상점에서 돌아옴) 입력을 다시 읽는다. 다시 읽는 동안에는 앞 그림을 그대로 둔다.
 */
@Composable
internal fun HomeAvatarSlotContent(store: MyAvatarStore) {
    LaunchedEffect(store) {
        store.warm()
        store.revalidate()
    }
    val state by store.state.collectAsStateWithLifecycle()
    AvatarStage(state, Modifier.fillMaxSize(), backdrop = StageFloorPainter.BACKDROP_SPEC)
}
