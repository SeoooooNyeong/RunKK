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
internal fun GuidanceMessage.textRes(): Int = GUIDANCE_TEXTS.getValue(this)

/**
 * 안내 키 → 문구 리소스 대응표(3차 리뷰: 15갈래 when을 표 조회로). 빠진 키는 GuidanceTextsTest가
 * 모든 GuidanceMessage를 읽어 잡는다.
 */
private val GUIDANCE_TEXTS: Map<GuidanceMessage, Int> = mapOf(
    GuidanceMessage.FINISH_HINT to R.string.guidance_finish_hint,
    GuidanceMessage.SPLIT to R.string.guidance_split,
    GuidanceMessage.TARGET_REACHED to R.string.guidance_target_reached,
    GuidanceMessage.PACE_SLOW to R.string.guidance_pace_slow,
    GuidanceMessage.BREATH_HIGH to R.string.guidance_breath_high,
    GuidanceMessage.GAP_AHEAD to R.string.guidance_gap_ahead,
    GuidanceMessage.GAP_BEHIND to R.string.guidance_gap_behind,
    GuidanceMessage.GAP_AHEAD_REMAINING to R.string.guidance_gap_ahead_remaining,
    GuidanceMessage.GAP_BEHIND_REMAINING to R.string.guidance_gap_behind_remaining,
    GuidanceMessage.RANK to R.string.guidance_rank,
    GuidanceMessage.OVERTOOK to R.string.guidance_overtook,
    GuidanceMessage.OVERTAKEN to R.string.guidance_overtaken,
    GuidanceMessage.SAVE_FAILED to R.string.guidance_save_failed,
    GuidanceMessage.REPORT_FAILED to R.string.guidance_report_failed,
    GuidanceMessage.PAUSED to R.string.guidance_paused,
    GuidanceMessage.RESUMED to R.string.guidance_resumed
)
