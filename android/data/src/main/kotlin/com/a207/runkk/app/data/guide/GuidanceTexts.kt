package com.a207.runkk.app.data.guide

import android.content.res.Resources
import androidx.annotation.StringRes
import com.a207.runkk.app.api.core.R
import com.a207.runkk.app.domain.run.Guidance
import com.a207.runkk.app.domain.run.GuidanceMessage

/**
 * 도메인 안내 키를 문자열 리소스로 바꾼다(2차 리뷰 M-3). 폰(data.device AndroidGuidanceOutput)과 워치(watch.guide)가 같은 표를
 * 쓰도록 공용 :data의 data.guide에 둔다(D-370, D-387 R66). Guidance·GuidanceMessage(domain.run)만 받는다(core.model 없음, M81).
 */
object GuidanceTexts {
    /** 안내 한 건의 문구. 인자가 없으면 서식 없이 읽는다("10%" 같은 글자를 그대로 둔다). */
    fun text(guidance: Guidance, resources: Resources): String {
        val id = textRes(guidance.message)
        return if (guidance.args.isEmpty()) {
            resources.getString(id)
        } else {
            resources.getString(id, *guidance.args.toTypedArray())
        }
    }

    @StringRes
    fun textRes(message: GuidanceMessage): Int = TEXTS.getValue(message)

    /**
     * 안내 키 → 문구 리소스 대응표(3차 리뷰: 15갈래 when을 표 조회로). 빠진 키는 GuidanceTextsTest가
     * 모든 GuidanceMessage를 읽어 잡는다.
     */
    private val TEXTS: Map<GuidanceMessage, Int> = mapOf(
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
}
