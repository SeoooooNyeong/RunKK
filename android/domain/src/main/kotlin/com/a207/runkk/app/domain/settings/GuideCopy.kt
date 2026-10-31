package com.a207.runkk.app.domain.settings

/** 첫 사용 가이드의 기기 사본(D-277). 서버에 알리지 못한 완료(SEEN_UNSENT)는 다음 시작 때 DeviceCopySync.syncAccount가 다시 보낸다(D-332). */
enum class GuideCopy {
    /** 아직 끝내지 않았다(또는 사본이 없다). */
    NONE,

    /** 기기에서는 끝냈지만 PUT /me/guide가 아직 성공하지 않았다. */
    SEEN_UNSENT,

    /** 끝냈고 서버도 안다. */
    SEEN
}
