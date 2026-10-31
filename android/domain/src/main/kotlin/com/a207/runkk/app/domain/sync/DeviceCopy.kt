package com.a207.runkk.app.domain.sync

/** DeviceCopySync가 서버와 맞추는 기기 사본(D-332). 실패를 알릴 때 어느 사본인지 가리킨다. */
enum class DeviceCopy {
    /** 서버 파기 지시를 보관함에 적용(SyncDisposalsUseCase). */
    DISPOSALS,

    /** 설정 사본 갱신·삭제(SettingsRepository). */
    SETTINGS,

    /** 오늘 계획 사본(TodayPlanCache, FR-042). */
    TODAY_PLAN,

    /** 서버에 알리지 못한 첫 사용 가이드 끝냄(FirstRunGuide.resendCompletion, D-277). */
    GUIDE_COMPLETION
}
