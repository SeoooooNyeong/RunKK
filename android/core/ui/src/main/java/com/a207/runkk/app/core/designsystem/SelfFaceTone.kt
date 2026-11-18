package com.a207.runkk.app.core.designsystem

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.a207.runkk.app.core.model.avatar.SkinTone

/**
 * 내 셀카 얼굴의 그을림([LocalSelfFaceTone]). 몸(Rive)과 같은 규칙으로 얼굴 원의 피부 층도 그을린다: 러닝량 0~100,
 * 100이어도 가장 어두운 톤의 40%까지, 곱만 쓴다(core.avatar SkinToneFilter와 같은 core.model [SkinTone] 값).
 * 눈·헤어 층에는 쓰지 않는다. [faceKey]가 같은 얼굴에만 쓴다(다른 러너 얼굴은 그대로). 랭킹 줄처럼 다른 러너 얼굴을 그을릴 때는
 * 그 얼굴 원을 그 러너의 얼굴 키·러닝량 값으로 감싸 내려 준다(S15P31A207-300).
 * 기본 캐릭터 얼굴("default:…")도 같은 거르개를 기본 머리의 피부 층에만 씌운다(PM 2026-11-14). 다만 기본 캐릭터 키는 여러 러너가 같은
 * 값을 쓰므로 키만으로는 주인을 가를 수 없다: 한 러너의 얼굴 원만 감싸 내려 준 톤([includesDefaultFace])일 때만 쓰고, 앱 전체에 내려 준
 * 내 톤은 기본 얼굴을 그을리지 않는다(같은 기본 캐릭터를 쓰는 다른 러너 얼굴까지 내 러닝량으로 그을리지 않게).
 *
 * @property faceKey 그을릴 얼굴 키("head:…", 보통 내 얼굴), 모르면 null
 * @property runningAmount 그 얼굴 주인의 러닝량 0~100
 * @property includesDefaultFace 한 러너의 얼굴 원만 감싼 톤이라 기본 캐릭터 키 얼굴에도 쓰는가
 */
data class SelfFaceTone(
    val faceKey: String? = null,
    val runningAmount: Int = 0,
    val includesDefaultFace: Boolean = false
) {
    /**
     * [key] 얼굴의 피부 층에 씌울 색 거르개. 내 얼굴이 아니거나 러닝량이 0이면 null(원본 그대로). 기본 캐릭터 키는
     * [includesDefaultFace]일 때만이다.
     */
    fun skinFilterFor(key: String?): ColorFilter? {
        if (key == null || key != faceKey || runningAmount <= 0) return null
        if (RunkkAvatarArt.isDefaultKey(key) && !includesDefaultFace) return null
        val (red, green, blue) = SkinTone.channelScales(runningAmount)
        return ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    red, 0f, 0f, 0f, 0f,
                    0f, green, 0f, 0f, 0f,
                    0f, 0f, blue, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }
}
