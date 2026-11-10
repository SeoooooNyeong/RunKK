package com.a207.runkk.app.core.avatar

/** 합성 결과를 가르는 모습 값(러닝량은 0..100으로 자른 값). */
internal data class AvatarLookKey(val outfit: AvatarOutfit, val shape: AvatarBodyShape, val amount: Int)
