package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.RunnerLook
import com.a207.runkk.app.core.navigation.SlotRunnerLook

/** 받은 모습을 칸·경로 값(원시값, core.navigation)으로 옮긴다([임시:D-성-259]). 표시 성별이 여자일 때만 여자 몸이다. */
internal fun RunnerLook.toSlot(): SlotRunnerLook = SlotRunnerLook(
    isFemale = gender == Gender.FEMALE,
    topItemId = topItemId,
    bottomItemId = bottomItemId,
    shoesItemId = shoesItemId,
    bodyVariant = bodyVariant,
    runAmount = runAmount,
    faceKey = faceKey
)

/** 경로 값으로 넘어온 모습을 화면 상태 값으로 되돌린다. */
internal fun SlotRunnerLook.toRunnerLook(): RunnerLook = RunnerLook(
    gender = if (isFemale) Gender.FEMALE else Gender.MALE,
    topItemId = topItemId,
    bottomItemId = bottomItemId,
    shoesItemId = shoesItemId,
    bodyVariant = bodyVariant,
    runAmount = runAmount,
    faceKey = faceKey
)
