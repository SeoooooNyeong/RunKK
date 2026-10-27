package com.a207.runkk.app.api.run

import kotlinx.serialization.Serializable

/** GET /me/disposals 응답 한 줄(서버 DisposalOrderView: id·kind·dueAt·status). kind는 ROUTE / HEART_RATE. */
@Serializable
internal data class DisposalOrderResponse(val id: Long, val kind: String)
