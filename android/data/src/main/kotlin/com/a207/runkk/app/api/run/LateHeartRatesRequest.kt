package com.a207.runkk.app.api.run

import kotlinx.serialization.Serializable

/** POST /runs/{id}/heart-rates 요청(서버 LateHeartRateCommand). */
@Serializable
internal data class LateHeartRatesRequest(val samples: List<RunUploadRequest.HeartRate>)
