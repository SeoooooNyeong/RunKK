package com.a207.runkk.app.data.device

import android.content.res.Resources
import androidx.annotation.StringRes
import com.a207.runkk.app.api.core.R
import com.a207.runkk.app.domain.run.Guidance
import com.a207.runkk.app.domain.run.GuidanceMessage

/** 도메인 안내 키를 문자열 리소스로 바꾼다(2차 리뷰 M-3). 인자가 없으면 서식 없이 읽는다("10%" 같은 글자를 그대로 둔다). */
internal fun Guidance.text(resources: Resources): String {
    val id = message.textRes()
    return if (args.isEmpty()) resources.getString(id) else resources.getString(id, *args.toTypedArray())
}

@StringRes
internal fun GuidanceMessage.textRes(): Int = when (this) {
    GuidanceMessage.FINISH_HINT -> R.string.guidance_finish_hint
    GuidanceMessage.UNLOCK_HINT -> R.string.guidance_unlock_hint
    GuidanceMessage.SPLIT -> R.string.guidance_split
    GuidanceMessage.TARGET_REACHED -> R.string.guidance_target_reached
    GuidanceMessage.PACE_SLOW -> R.string.guidance_pace_slow
    GuidanceMessage.BREATH_HIGH -> R.string.guidance_breath_high
    GuidanceMessage.GAP_AHEAD -> R.string.guidance_gap_ahead
    GuidanceMessage.GAP_BEHIND -> R.string.guidance_gap_behind
    GuidanceMessage.GAP_AHEAD_REMAINING -> R.string.guidance_gap_ahead_remaining
    GuidanceMessage.GAP_BEHIND_REMAINING -> R.string.guidance_gap_behind_remaining
    GuidanceMessage.RANK -> R.string.guidance_rank
    GuidanceMessage.OVERTOOK -> R.string.guidance_overtook
    GuidanceMessage.OVERTAKEN -> R.string.guidance_overtaken
    GuidanceMessage.SAVE_FAILED -> R.string.guidance_save_failed
    GuidanceMessage.REPORT_FAILED -> R.string.guidance_report_failed
}
