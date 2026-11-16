package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

/** 서버 CrewDtos.ReadyRequest(PUT /crew-runs/{id}/lobby/ready, 설계 05-32 5.1). */
@Serializable
internal data class ReadyRequest(val ready: Boolean)
