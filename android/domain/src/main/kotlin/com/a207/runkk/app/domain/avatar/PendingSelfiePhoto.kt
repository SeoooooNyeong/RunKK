package com.a207.runkk.app.domain.avatar

/**
 * 얼굴을 만드는 동안 보일 보낸 사진 한 장(이미 줄인 JPEG, design-oo 05-30 [임시:D-성-119], FR-156 예외). 작업이 끝나거나 서버에
 * 진행 중 작업이 없다고 확인되면 지운다. 구현은 앱 전용 파일 한 장이고, 로그인한 회원 것만 돌려준다.
 */
interface PendingSelfiePhoto {
    suspend fun save(jpeg: ByteArray)

    /** 남긴 사진. 없거나 다른 회원이 남긴 것이면 null이다. */
    suspend fun load(): ByteArray?

    suspend fun clear()
}
