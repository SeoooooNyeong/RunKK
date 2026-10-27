package com.a207.runkk.app.ui.me

sealed interface ProfileEditEvent {
    data object Saved : ProfileEditEvent
}
