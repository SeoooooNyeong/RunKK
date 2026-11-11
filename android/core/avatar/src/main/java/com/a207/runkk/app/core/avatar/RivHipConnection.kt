package com.a207.runkk.app.core.avatar

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 달리기 엉덩이 잇기 메시(Image 「Cloth textured hip connection」, 타원 윤곽 꼭짓점 17개)가 하의 달리기 다리 텍스처(run_leg)의 어느 칸을
 * 쓸지 바꾼다(판정 round1 C). 승인 riv는 천 가운데 조각(v 약 0.209~0.271 = y 160~208)을 써서 엉덩이 뒷면에 외곽선이 없고, 같은 칸을
 * 앞 다리 메시도 허벅지에 써서 거기에 외곽선을 그릴 수 없었다. 꼭짓점 v만 위로 옮겨 다리 메시가 화면에 쓰지 않는 칸을 쓰게 한다:
 * 남녀 리그가 이 메시를 다르게 휘어 칸을 나눈다(여자 y 0~47 [FEMALE_V_SHIFT], 남자 y 48~95 [MALE_V_SHIFT]). 하의 레이어는 그 칸에
 * 원래 조각을 옮기고 등 쪽을 깎아 둔다(파이프라인 `outfit_pipeline/hip.py`).
 * 파일은 승인본(SHA 시험) 그대로 두고 읽은 바이트에서 바꾼다([RivGroundShadow]와 같은 방식). 위치 표는 파이프라인
 * `vertex_v_offsets`가 riv에서 찾은 값이고(파이썬 시험이 이 표와 맞는지 본다), 바꾸기 전 값이 승인 범위(0.2~0.28)가 아니면 그 파일은
 * 그대로 둔다.
 */
internal object RivHipConnection {
    const val FEMALE_V_SHIFT = 0.20833333333333334f
    const val MALE_V_SHIFT = 0.14583333333333334f

    /** avatar_run_approved.riv(여자 달리기) 꼭짓점 v 위치(바이트, float32 리틀엔디언). */
    internal val FEMALE_OFFSETS = intArrayOf(
        2251489, 2251511, 2251538, 2251565, 2251592, 2251619,
        2251646, 2251673, 2251700, 2251727, 2251754, 2251781,
        2251808, 2251835, 2251862, 2251889, 2251916
    )

    /** avatar_male_run.riv(남자 달리기) 꼭짓점 v 위치. */
    internal val MALE_OFFSETS = intArrayOf(
        2177924, 2177946, 2177973, 2178000, 2178027, 2178054,
        2178081, 2178108, 2178135, 2178162, 2178189, 2178216,
        2178243, 2178270, 2178297, 2178324, 2178351
    )

    /** [riv]가 승인 달리기 riv면 엉덩이 잇기 꼭짓점 v를 옮긴다(바로 고친다). 아니면 그대로 둔다. */
    fun retargetInPlace(riv: ByteArray) {
        val (offsets, shift) = listOf(FEMALE_OFFSETS to FEMALE_V_SHIFT, MALE_OFFSETS to MALE_V_SHIFT)
            .firstOrNull { matches(riv, it.first) } ?: return
        val buffer = ByteBuffer.wrap(riv).order(ByteOrder.LITTLE_ENDIAN)
        offsets.forEach { buffer.putFloat(it, buffer.getFloat(it) - shift) }
    }

    private fun matches(riv: ByteArray, offsets: IntArray): Boolean {
        if (offsets.last() + 4 > riv.size) return false
        val buffer = ByteBuffer.wrap(riv).order(ByteOrder.LITTLE_ENDIAN)
        return offsets.all { buffer.getFloat(it) in 0.2f..0.28f }
    }
}
