package com.a207.runkk.app.ui.runimal

import androidx.annotation.StringRes

/** 공유 대상 3종(C-012): 기기 저장, 다른 앱, 스토리. */
enum class RunimalShareKind(@StringRes val labelRes: Int) {
    SAVE(R.string.runimal_share_save),
    SYSTEM(R.string.runimal_share_system),
    STORY(R.string.runimal_share_story)
}
