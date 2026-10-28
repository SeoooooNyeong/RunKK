package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText

data class CrewCreateUiState(
    val form: CrewFormState = CrewFormState(),
    val isSaving: Boolean = false,
    val error: UiText? = null
)
