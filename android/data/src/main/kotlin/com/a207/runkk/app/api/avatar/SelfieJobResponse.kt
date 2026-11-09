package com.a207.runkk.app.api.avatar

import kotlinx.serialization.Serializable

/** 서버 avatar.FaceJob(셀카 얼굴 작업). FAILED도 200이고 사유는 error.code다. */
@Serializable
internal data class SelfieJobResponse(
    val jobId: String,
    val status: String,
    val headId: String? = null,
    val error: Error? = null
) {
    @Serializable
    data class Error(val code: String, val message: String? = null)
}
