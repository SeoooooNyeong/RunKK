package com.a207.runkk.app.domain.sync

import com.a207.runkk.app.core.model.AppError

/**
 * 시작 에셋 미리 받기 상태(S15P31A207-345). 시작 로딩 화면은 [Ready]가 되어야 다음 화면(홈·로그인)으로 간다. 시간 상한은 없다.
 */
sealed interface PreloadState {
    val progress: PreloadProgress

    /** 목록을 얻는 중(막대 없음)이거나 받는 중. */
    data class Loading(override val progress: PreloadProgress) : PreloadState

    /** 자동 재시도까지 다 실패했다(오프라인 등). 화면이 「다시 시도」를 보이고 [AssetPreloader.retry]로 이어 받는다. */
    data class Failed(override val progress: PreloadProgress, val error: AppError) : PreloadState

    /** 다 받았다(캐시에 다 있었으면 막대 없이 바로). */
    data class Ready(override val progress: PreloadProgress) : PreloadState
}
