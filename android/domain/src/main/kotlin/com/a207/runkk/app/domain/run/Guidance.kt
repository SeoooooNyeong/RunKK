package com.a207.runkk.app.domain.run

import com.a207.runkk.app.domain.settings.GuidanceSettings

/** 안내 하나(안내 종류, 문구, 방식). GuidanceOutput이 소리·진동·화면으로 낸다. */
data class Guidance(val kind: Kind, val message: String, val voice: Boolean, val vibration: Boolean) {
    enum class Kind {
        FINISH_HINT,
        UNLOCK_HINT,
        SPLIT,
        TARGET_REACHED,
        PACE,
        BREATH,
        GAP,
        RANK,
        OVERTOOK,
        OVERTAKEN,

        /** 측정 결과를 기기 보관함에 저장하지 못함. */
        SAVE_FAILED,

        /** 대결 시작·그만두기를 서버에 알리지 못함(다시 보내도 실패, 리뷰 F-3). */
        REPORT_FAILED
    }

    companion object {
        /** 화면에만 보이는 안내(소리·진동 없음). */
        fun screenOnly(kind: Kind, message: String): Guidance =
            Guidance(kind, message, voice = false, vibration = false)
    }
}
