package com.a207.runkk.app.ui.share

import androidx.annotation.StringRes

/** 공유 결과. 화면이 문구로 바꿔 보인다. */
sealed interface ShareOutcome {
    /** 다른 앱(공유 시트·스토리)으로 넘겼다. */
    data object Shared : ShareOutcome

    /** 기기 사진첩에 저장했다. */
    data object Saved : ShareOutcome

    /** 원래 대상 대신 다른 방법을 썼거나 안내가 필요하다(인스타그램 없음). */
    data class Fallback(@StringRes val messageRes: Int) : ShareOutcome

    /** 공유하지 못했다. [code]는 사유 코드(COURSE_MAP_NOT_SHARED 등). */
    data class Failed(val code: String, @StringRes val messageRes: Int) : ShareOutcome
}
