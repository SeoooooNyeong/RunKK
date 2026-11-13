package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText

/**
 * 크루 만들기 화면 상태(피그마 F-04).
 *
 * @property logo 고른 로고 그림(JPEG, 미리보기). 업로드 결과 주소는 [CrewFormState.logoUrl]이다.
 * @property isUploadingLogo 로고를 올리는 중(그동안 만들기를 막는다)
 */
data class CrewCreateUiState(
    val form: CrewFormState = CrewFormState(),
    val isSaving: Boolean = false,
    val error: UiText? = null,
    val logo: ByteArray? = null,
    val isUploadingLogo: Boolean = false
) {
    /** 만들기 단추를 누를 수 있는가(입력이 맞고 로고를 올리는 중이 아님). */
    val canSubmit: Boolean get() = form.isValid && !isUploadingLogo

    override fun equals(other: Any?): Boolean = other is CrewCreateUiState &&
        form == other.form &&
        isSaving == other.isSaving &&
        error == other.error &&
        isUploadingLogo == other.isUploadingLogo &&
        (logo?.contentEquals(other.logo) ?: (other.logo == null))

    override fun hashCode(): Int {
        var result = form.hashCode()
        result = HASH * result + isSaving.hashCode()
        result = HASH * result + (error?.hashCode() ?: 0)
        result = HASH * result + (logo?.contentHashCode() ?: 0)
        result = HASH * result + isUploadingLogo.hashCode()
        return result
    }

    private companion object {
        const val HASH = 31
    }
}
