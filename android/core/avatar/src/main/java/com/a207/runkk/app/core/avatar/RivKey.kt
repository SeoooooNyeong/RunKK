package com.a207.runkk.app.core.avatar

/** 가져오기 키: 몸체 바이트(남녀)·동작·트랙 칸용인가. 트랙 칸용은 데우기가 미리 가져온 것만 가져간다. */
internal data class RivKey(val prepared: PreparedRiveAssets, val motion: AvatarMotion, val forTrack: Boolean)
