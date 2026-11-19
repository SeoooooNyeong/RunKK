package com.a207.runkk.app.domain.sync

import com.a207.runkk.app.core.model.AppError

/**
 * 시작 에셋 미리 받기 상태(S15P31A207-345). 시작 로딩 화면은 [Ready]가 되어야 다음 화면(홈·로그인)으로 간다. 시간 상한은 없다.
 */
sealed interface PreloadState {
    val progress: PreloadProgress

    /**
     * 목록을 얻는 중(막대 없음)이거나 받는 중. [remaining]은 단계마다 아직 받지 않은 파일 수(로딩 문구 고르기), [isRetrying]은 연결·서버
     * 일시 실패로 자동 재시도를 기다리는 중, [isFirstRun]은 이 기기에서 회원 에셋을 다 받아 본 적이 없음(처음 설치 안내)이다.
     */
    data class Loading(
        override val progress: PreloadProgress,
        val remaining: Map<PreloadStage, Int> = emptyMap(),
        val isRetrying: Boolean = false,
        val isFirstRun: Boolean = false
    ) : PreloadState

    /** 자동 재시도까지 다 실패했다(오프라인 등). 화면이 「다시 시도」를 보이고 [AssetPreloader.retry]로 이어 받는다. */
    data class Failed(override val progress: PreloadProgress, val error: AppError) : PreloadState

    /** 다 받았다(캐시에 다 있었으면 막대 없이 바로). */
    data class Ready(override val progress: PreloadProgress) : PreloadState
}
