package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

/** POST /crews/invite-preview 본문(서버 CrewDtos.InvitePreviewRequest, contract-W4 4절). 코드를 URL에 싣지 않는다. */
@Serializable
internal data class InvitePreviewRequest(val inviteCode: String)
