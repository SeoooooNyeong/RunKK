package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.getOrNull

/**
 * 내 아바타 모습: 본인 요약의 얼굴 키·배경 키와 그 얼굴의 부위 그림. 홈(HomeViewModel.refresh)과 같은 출처라 결과 화면(크루런 F-16,
 * 대결 결과)의 "내 아바타"가 홈과 같은 모습이다. 체형(남녀 기본 그림)은 앱 전체의 LocalAvatarCharacter가 정한다.
 */
data class MyAvatar(val faceKey: String?, val faceLayers: List<FaceLayer>, val backgroundKey: String?)

/**
 * 본인 요약을 읽어 [MyAvatar]로 옮긴다. 요약을 읽지 못하면 null이고, 부위 그림을 받지 못하면 빈 목록이다(홈과 같다). 받아 둔 그림이
 * 있으면 다시 받지 않는다([AccountRepository.cachedFaceLayers]).
 */
suspend fun AccountRepository.myAvatar(): MyAvatar? {
    val me = mySummary().getOrNull() ?: return null
    return MyAvatar(me.faceKey, faceLayersOf(me.faceKey), me.avatarBackgroundKey)
}

/** [faceKey]의 그림: 받아 둔 것이 있으면 바로, 없으면 받는다. 받지 못하면 빈 목록. */
suspend fun AccountRepository.faceLayersOf(faceKey: String?): List<FaceLayer> =
    cachedFaceLayers(faceKey) ?: faceKey?.let { faceLayers(it).getOrNull() }.orEmpty()
