package com.a207.runkk.app.domain.run

/** 서버가 한 기록에 받는 움직임 표본 수 상한(RunController MAX_MOTIONS = 위치 상한 43,200 × 2). */
const val MAX_UPLOAD_MOTIONS = 86_400

private const val FIRST_BUCKET_MILLIS = 1_000L

/**
 * 시간순 표본이 [max]개를 넘으면 같은 시간 칸(처음 1초, 그래도 넘치면 두 배씩 넓힌다)끼리 합쳐 [max]개 안으로 줄인다(MR !91 AN-5). 칸의 시각은 칸 첫
 * 표본의 시각이고 속도·케이던스는 그 칸에서 잰 값의 평균이다. 상한 안이면 그대로 돌려준다. 상한을 넘겨 기록 전체가 거부되는 것보다 표본 해상도를 낮추는
 * 편이 낫다.
 */
fun List<MotionSample>.thinnedTo(max: Int = MAX_UPLOAD_MOTIONS): List<MotionSample> {
    if (size <= max) return this
    var bucket = FIRST_BUCKET_MILLIS
    var merged = mergedInto(bucket)
    while (merged.size > max) {
        bucket *= 2
        merged = mergedInto(bucket)
    }
    return merged
}

private fun List<MotionSample>.mergedInto(bucketMillis: Long): List<MotionSample> {
    val origin = first().at.toEpochMilli()
    return groupBy { (it.at.toEpochMilli() - origin) / bucketMillis }.values.map { group ->
        MotionSample(
            at = group.first().at,
            speedMps = group.mapNotNull { it.speedMps }.averageOrNull(),
            cadenceSpm = group.mapNotNull { it.cadenceSpm }.averageOrNull()
        )
    }
}

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
