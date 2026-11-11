package com.a207.runkk.app.core.avatar

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import timber.log.Timber

/**
 * 달리기 엉덩이 잇기 메시(Image 「Cloth textured hip connection」, 타원 윤곽 꼭짓점 17개)가 하의 달리기 다리 텍스처(run_leg)의 어느 칸을
 * 쓸지 바꾼다(판정 round1 C). 승인 riv는 천 가운데 조각(v 약 0.209~0.271 = y 160~208)을 써서 엉덩이 뒷면에 외곽선이 없고, 같은 칸을
 * 앞 다리 메시도 허벅지에 써서 거기에 외곽선을 그릴 수 없었다. 꼭짓점 v만 위로 옮겨 다리 메시가 화면에 쓰지 않는 칸을 쓰게 한다:
 * 남녀 리그가 이 메시를 다르게 휘어 칸을 나눈다(여자 y 0~47 [FEMALE_V_SHIFT], 남자 y 48~95 [MALE_V_SHIFT]). 하의 레이어는 그 칸에
 * 원래 조각을 옮기고 등 쪽을 깎아 둔다(파이프라인 `outfit_pipeline/hip.py`).
 * 파일은 승인본(SHA 시험) 그대로 두고 읽은 바이트에서 바꾼다([RivGroundShadow]와 같은 방식). 위치 표는 파이프라인
 * `vertex_v_offsets`가 riv에서 찾은 값이고(파이썬 시험이 이 표와 맞는지 본다). 고치기 전에 승인 파일 지문([FINGERPRINTS]: 파일 길이,
 * 꼭짓점 둘레 바이트의 SHA-256)과 바꾸기 전 v가 승인 범위(0.2~0.28)인지 함께 본다. riv를 다시 내보내 위치가 밀리면 범위 검사만으로는
 * 엉뚱한 값을 고치거나 말없이 건너뛸 수 있어서다. 지문이 다르면 고치지 않고 경고를 한 번 남긴다. 파일 전체 SHA가 아닌 것은 이 함수가
 * 검수 바탕·바닥 그림자를 먼저 숨긴 바이트를 받아서다([PreparedRiveAssets.hideBuiltInShapes]). 지문은 v 4바이트를 0으로 두고 재므로,
 * 이미 옮긴 바이트를 다시 받으면 지문은 맞고 v가 범위 밖이라 그대로 둔다(두 번 불러도 한 번과 같다).
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

    /** 승인 riv 지문: 파일 길이와 [offsets] 꼭짓점 둘레([WINDOW]바이트 여유, v 4바이트는 0으로 둠)의 SHA-256. */
    internal class Fingerprint(val offsets: IntArray, val size: Int, val sha256: String, val shift: Float)

    internal val FINGERPRINTS = listOf(
        Fingerprint(
            FEMALE_OFFSETS,
            2_543_627,
            "c1573d023070b500bd40c78488aceaefd687ad3566529ac317f6ccfb115de526",
            FEMALE_V_SHIFT
        ),
        Fingerprint(
            MALE_OFFSETS,
            2_471_104,
            "6f53bf0686c8f00947621e10cde781dfed97d6274c765d5fed470f4ef3b723aa",
            MALE_V_SHIFT
        )
    )

    private const val WINDOW = 64
    private val warned = AtomicBoolean(false)

    /** [riv]가 승인 달리기 riv면 엉덩이 잇기 꼭짓점 v를 옮긴다(바로 고친다). 아니면 그대로 둔다. */
    fun retargetInPlace(riv: ByteArray) {
        val print = FINGERPRINTS.firstOrNull { riv.size == it.size && windowSha256(riv, it.offsets) == it.sha256 }
        if (print == null) {
            // 길이가 같거나 v가 승인 범위인데 지문이 다르면 다시 내보낸 riv다(서기 riv는 둘 다 아니라 말없이 넘긴다)
            if (FINGERPRINTS.any { riv.size == it.size || inRange(riv, it.offsets) } &&
                warned.compareAndSet(false, true)
            ) {
                Timber.w("RivHipConnection: 달리기 riv 지문이 승인본과 달라 엉덩이 잇기 칸을 옮기지 않는다(size=%d)", riv.size)
            }
            return
        }
        if (!inRange(riv, print.offsets)) return // 이미 옮긴 바이트
        val buffer = ByteBuffer.wrap(riv).order(ByteOrder.LITTLE_ENDIAN)
        print.offsets.forEach { buffer.putFloat(it, buffer.getFloat(it) - print.shift) }
    }

    /** [offsets] 꼭짓점 둘레 바이트(v 값은 0으로 둔다)의 SHA-256(소문자 16진수). 범위를 벗어나면 빈 문자열. */
    internal fun windowSha256(riv: ByteArray, offsets: IntArray): String {
        val start = offsets.first() - WINDOW
        val end = offsets.last() + 4 + WINDOW
        if (start < 0 || end > riv.size) return ""
        val window = riv.copyOfRange(start, end)
        offsets.forEach { offset -> (0 until 4).forEach { window[offset - start + it] = 0 } }
        return MessageDigest.getInstance("SHA-256").digest(window).joinToString("") { "%02x".format(it) }
    }

    /** 시험용: 한 번만 남기는 경고를 다시 남길 수 있게 한다. */
    internal fun resetWarning() = warned.set(false)

    private fun inRange(riv: ByteArray, offsets: IntArray): Boolean {
        if (offsets.last() + 4 > riv.size) return false
        val buffer = ByteBuffer.wrap(riv).order(ByteOrder.LITTLE_ENDIAN)
        return offsets.all { buffer.getFloat(it) in 0.2f..0.28f }
    }
}
