package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

/** PUT·DELETE /crews/{id}/managers/{memberId} 응답(서버 CrewDtos.ManagerResponse): 지정이면 MANAGER, 해제면 MEMBER. */
@Serializable
internal data class ManagerResponse(val memberId: Long, val role: String)
