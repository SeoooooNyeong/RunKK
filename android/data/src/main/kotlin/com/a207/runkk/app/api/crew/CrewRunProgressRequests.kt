package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

/** 내 크루런 끝내기 본문(POST /crew-runs/{id}/quit). 모르는 값은 null로 보내 서버가 마지막 중계 값을 쓴다. */
@Serializable
internal data class CrewRunQuitRequest(val distanceMeters: Long? = null, val elapsedSeconds: Long? = null)

/** 일시정지 본문(POST /crew-runs/{id}/pause). 정지 때 경과보다 큰 경과의 진행값이 오면 서버가 재개로 본다. */
@Serializable
internal data class CrewRunPauseRequest(val elapsedSeconds: Long)
