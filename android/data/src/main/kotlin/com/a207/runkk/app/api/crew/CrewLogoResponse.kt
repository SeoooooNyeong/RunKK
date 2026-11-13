package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

/** 크루 로고 업로드 결과(서버 CrewDtos.CrewLogoResponse, contract-W4 6절). logoUrl을 그대로 크루 만들기에 싣는다. */
@Serializable
internal data class CrewLogoResponse(val logoId: Long, val logoUrl: String)
