package com.a207.runkk.app.core.designsystem

import androidx.compose.runtime.staticCompositionLocalOf
import com.a207.runkk.app.core.model.Gender

/** 기본 캐릭터 남녀(프로필 성별이 여성이면 여자, 그 밖은 남자, 사용자 요청 2026-10-30). */
enum class AvatarCharacter {
    MALE,
    FEMALE;

    companion object {
        /** 성별 → 기본 캐릭터. 여성이면 여자, 남성·비공개·모름(null)은 남자다. 내 캐릭터와 대결 상대 그림이 같이 쓴다. */
        fun of(gender: Gender?): AvatarCharacter = if (gender == Gender.FEMALE) FEMALE else MALE
    }
}

/** 지금 회원의 기본 캐릭터. 앱 맨 위에서 내 프로필 성별로 넣는다. */
val LocalAvatarCharacter = staticCompositionLocalOf { AvatarCharacter.MALE }
