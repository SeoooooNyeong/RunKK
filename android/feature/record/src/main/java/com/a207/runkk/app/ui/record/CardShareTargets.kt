package com.a207.runkk.app.ui.record

import com.a207.runkk.app.ui.share.DeviceSaveTarget
import com.a207.runkk.app.ui.share.ShareTarget
import com.a207.runkk.app.ui.share.StoryShareTarget
import com.a207.runkk.app.ui.share.SystemShareTarget
import javax.inject.Inject

/** ui.share의 공유 대상 묶음. 시험에서는 가짜 대상을 넣는다. */
class CardShareTargets(val save: ShareTarget, val system: ShareTarget, val story: ShareTarget) {
    @Inject
    constructor(save: DeviceSaveTarget, system: SystemShareTarget, story: StoryShareTarget) :
        this(save as ShareTarget, system as ShareTarget, story as ShareTarget)

    fun of(kind: CardShareKind): ShareTarget = when (kind) {
        CardShareKind.SAVE -> save
        CardShareKind.SYSTEM -> system
        CardShareKind.STORY -> story
    }
}
