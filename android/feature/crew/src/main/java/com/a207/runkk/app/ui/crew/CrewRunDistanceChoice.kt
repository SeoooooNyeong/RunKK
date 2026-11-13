package com.a207.runkk.app.ui.crew

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.model.Distance
import java.time.Duration
import java.time.Instant

/**
 * 크루런 거리 선택지(피그마 F-12e, 사용자 결정 C4, 계약 contract-W6.md R2): 제한 없음·3·5·10km·하프. 1km는 새로 쓸 수 없다.
 * [distance]가 null이면 제한 없음이다. 하프는 서버 StandardDistance.HALF(21,097m)다.
 */
enum class CrewRunDistanceChoice(val distance: Distance?) {
    NO_LIMIT(null),
    KM3(Distance(3_000)),
    KM5(Distance(5_000)),
    KM10(Distance(10_000)),
    HALF(Distance(HALF_METERS));

    companion object {
        /** 서버 거리 → 선택지. 선택지 밖(기존 1km 글)이면 null. */
        fun of(distance: Distance?): CrewRunDistanceChoice? = entries.firstOrNull { it.distance == distance }
    }
}

/** 하프 거리(서버 StandardDistance.HALF). */
internal const val HALF_METERS = 21_097L

/** 거리 문구: null은 "제한 없음", 하프는 "하프", 나머지는 "5km"(기존 1km 글도 "1km"). */
internal fun crewRunDistanceLabel(resources: Resources, distance: Distance?): String = when {
    distance == null -> resources.getString(R.string.crew_run_distance_unlimited)
    distance.meters == HALF_METERS -> resources.getString(R.string.crew_run_distance_half)
    else -> RunkkFormat.targetLabel(distance)
}

/** 화면에서 쓰는 [crewRunDistanceLabel]. */
@Composable
internal fun crewRunDistanceLabel(distance: Distance?): String =
    crewRunDistanceLabel(LocalContext.current.resources, distance)

/** 보조 기술용 거리 문구("5킬로미터", "제한 없음", "하프"). */
@Composable
internal fun crewRunDistanceSpoken(distance: Distance?): String =
    RunkkFormat.spokenLabel(crewRunDistanceLabel(distance))

/** 작성 경과 문구(피그마 F-13 "2시간 전"): 1분 안은 "방금 전", 1시간 안은 분, 하루 안은 시간, 그 뒤는 일. */
internal fun agoLabel(resources: Resources, at: Instant, now: Instant): String {
    val elapsed = Duration.between(at, now).coerceAtLeast(Duration.ZERO)

    @StringRes val res: Int
    val value: Long
    when {
        elapsed.toMinutes() < 1 -> return resources.getString(R.string.crew_run_ago_now)

        elapsed.toHours() < 1 -> {
            res = R.string.crew_run_ago_minutes
            value = elapsed.toMinutes()
        }

        elapsed.toDays() < 1 -> {
            res = R.string.crew_run_ago_hours
            value = elapsed.toHours()
        }

        else -> {
            res = R.string.crew_run_ago_days
            value = elapsed.toDays()
        }
    }
    return resources.getString(res, value.toInt())
}
