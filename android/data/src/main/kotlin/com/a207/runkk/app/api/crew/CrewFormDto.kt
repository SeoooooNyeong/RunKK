package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

@Serializable
internal data class CrewFormDto(
    val name: String,
    val intro: String?,
    val tags: Set<String>,
    val visibility: String,
    val joinPolicy: String
)
