package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.codePointLength

/**
 * 게시판 글쓰기(FR-211, D-100, D-165) 상태. [photos]는 고른 사진의 기기 주소(content://)이고 0~5장이다. 본문은 1,000자(코드 포인트, D-143)까지이고
 * 본문과 사진이 둘 다 비면 올리지 않는다. 사진은 올릴 때 api.crew가 장마다 1MB 이하 JPEG로 줄인다.
 */
data class CrewBoardWriteUiState(
    val body: String = "",
    val photos: List<String> = emptyList(),
    val isSaving: Boolean = false,
    /** 올렸다: 화면을 닫는다. */
    val isPosted: Boolean = false,
    /** 올리기 실패 이유(압축 거부 TOO_LARGE·UNSUPPORTED_IMAGE 포함). */
    val error: UiText? = null,
    /** 사진을 고를 때의 안내(5장 넘음, 걸러진 사진). */
    val message: UiText? = null
) {
    val canAddPhoto: Boolean get() = photos.size < PHOTO_MAX && !isSaving

    val canSubmit: Boolean get() = (body.isNotBlank() || photos.isNotEmpty()) && body.codePointLength() <= BODY_MAX &&
        !isSaving

    companion object {
        const val BODY_MAX = 1_000
        const val PHOTO_MAX = 5

        /** 고를 수 있는 원본 상한(FR-211 제안 값 10MB). */
        const val PHOTO_BYTES_MAX = 10L * 1024 * 1024
    }
}
