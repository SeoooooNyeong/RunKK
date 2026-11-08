package com.a207.runkk.app.api.runbody

import kotlinx.serialization.Serializable

/** GET /runbody/sessions/{recordId}. [result]는 status가 DONE일 때만 있고 ai SessionResultOut 본문 그대로다. */
@Serializable
internal data class RunBodySessionResponse(val recordId: String, val status: String, val result: Result? = null) {
    /** 앱이 쓰지 않는 필드(metrics, qualityFlags, engineVersion 등)는 받지 않는다(ignoreUnknownKeys). */
    @Serializable
    data class Result(
        val summary: Summary,
        val axes: Map<String, Axis> = emptyMap(),
        val coaching: Coaching = Coaching(),
        val laps: List<Lap> = emptyList(),
        val prev: Prev? = null,
        val feedback: Feedback
    )

    @Serializable
    data class Summary(
        val distanceMeters: Long,
        val activeMs: Long,
        val paceSecondsPerKm: Int? = null,
        val avgHeartRate: Int? = null,
        val maxHeartRate: Int? = null,
        val startedAt: String
    )

    @Serializable
    data class Axis(val score: Int? = null, val label: String = "", val level: String = "none")

    /** detail.split은 {type, firstHalfPaceS, secondHalfPaceS, ratio}이고 나누지 못했으면 type만 null이다. */
    @Serializable
    data class Coaching(val flags: List<String> = emptyList(), val detail: Detail = Detail())

    @Serializable
    data class Detail(val split: Split? = null)

    @Serializable
    data class Split(val type: String? = null, val firstHalfPaceS: Int? = null, val secondHalfPaceS: Int? = null)

    @Serializable
    data class Lap(val km: Double, val paceSeconds: Int, val avgHeartRate: Int? = null, val partial: Boolean = false)

    @Serializable
    data class Prev(val distanceMeters: Long, val paceSecondsPerKm: Int? = null)

    @Serializable
    data class Feedback(val headline: String, val good: String, val fix: String, val next: String)
}
