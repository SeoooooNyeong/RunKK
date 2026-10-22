package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** 안내 출력 포트(TTS·진동·화면 배너). 구현 data.device.AndroidGuidanceOutput. */
interface GuidanceOutput {
    fun play(g: Guidance)
}
