package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.model.GeoPoint

/** 지도 위 크루원 한 명(F-15): 내 경로 위 자리, 이름표(멈췄을 때만 「이름 멈춤」, 아니면 없음), 움직이는가, 캐릭터(그 크루원의 표시 성별, D-327). */
internal data class MateMarker(
    val point: GeoPoint,
    val label: String?,
    val isMoving: Boolean,
    val character: AvatarCharacter = AvatarCharacter.MALE
)
