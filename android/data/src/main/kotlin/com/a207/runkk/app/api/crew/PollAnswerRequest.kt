package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

@Serializable
internal data class PollAnswerRequest(val optionNo: Int)
