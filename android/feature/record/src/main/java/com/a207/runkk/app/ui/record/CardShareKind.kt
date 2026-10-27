package com.a207.runkk.app.ui.record

import androidx.annotation.StringRes

/** 공유 대상 3종(C-012): 기기 저장, 다른 앱, 스토리. */
enum class CardShareKind(@StringRes val labelRes: Int) {
    SAVE(R.string.record_card_share_save),
    SYSTEM(R.string.record_card_share_system),
    STORY(R.string.record_card_share_story)
}
