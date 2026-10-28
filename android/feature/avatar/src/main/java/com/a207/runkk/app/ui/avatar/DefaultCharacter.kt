package com.a207.runkk.app.ui.avatar

import androidx.annotation.StringRes
import com.a207.runkk.app.core.designsystem.RunkkAvatarArt

/** 기본 캐릭터 얼굴 4종(FR-158). id는 서버 DefaultCharacter 이름이다. */
enum class DefaultCharacter(@StringRes val labelRes: Int) {
    RABBIT(R.string.avatar_character_rabbit),
    BEAR(R.string.avatar_character_bear),
    FOX(R.string.avatar_character_fox),
    CAT(R.string.avatar_character_cat);

    companion object {
        fun of(id: String?): DefaultCharacter? = entries.firstOrNull { it.name == id }

        /** 서버 얼굴 키("default:<이름>")의 캐릭터. 셀카 얼굴·모르는 값이면 null. */
        fun ofFaceKey(faceKey: String?): DefaultCharacter? =
            entries.firstOrNull { RunkkAvatarArt.defaultKey(it.name) == faceKey }
    }
}
