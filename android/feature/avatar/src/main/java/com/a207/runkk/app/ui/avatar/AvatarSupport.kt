package com.a207.runkk.app.ui.avatar

import android.graphics.Bitmap
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.domain.avatar.ItemSlot
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** 기본 캐릭터 얼굴 4종(FR-158). id는 서버 DefaultCharacter 이름이다. */
enum class DefaultCharacter(val label: String) {
    RABBIT("토끼"),
    BEAR("곰"),
    FOX("여우"),
    CAT("고양이");

    companion object {
        fun of(id: String?): DefaultCharacter? = entries.firstOrNull { it.name == id }
    }
}

/** 부위 문구(FR-161). */
internal val ItemSlot.label: String
    get() = when (this) {
        ItemSlot.TOP -> "상의"
        ItemSlot.BOTTOM -> "하의"
        ItemSlot.SHOES -> "신발"
        ItemSlot.HAT -> "모자"
    }

/** 체형 단계(서버 BodyShape STAGE1~4, FR-163). 모르는 값이면 null. */
internal fun bodyStage(bodyShape: String): Int? = bodyShape.filter(Char::isDigit).toIntOrNull()?.takeIf {
    it in
        1..BODY_STAGES
}

internal fun bodyLabel(stage: Int?): String = when (stage) {
    1 -> "배가 볼록"
    2 -> "조금 말랑"
    3 -> "탄탄해지는 중"
    4 -> "근육 탄탄"
    else -> "체형 확인 중"
}

internal const val BODY_STAGES = 4

/** 피부톤(서버 SkinTone, FR-164): 이름과 근거 문구. */
internal fun skinLabel(skinTone: String): String = when (skinTone) {
    "DAWN" -> "새벽 톤 · 주로 새벽(04~07시)에 달려요"
    "MORNING" -> "아침 톤 · 주로 아침(07~11시)에 달려요"
    "DAYTIME" -> "낮 톤 · 주로 낮(11~17시)에 달려요"
    "EVENING" -> "저녁 톤 · 주로 저녁(17~21시)에 달려요"
    "NIGHT" -> "밤 톤 · 주로 밤(21~04시)에 달려요"
    else -> skinTone
}

/** 아바타·상점 거부 사유 문구(서버 ReasonCode). 모르는 코드는 공용 문구. */
internal object AvatarErrorText {
    fun of(error: AppError): String {
        val code = (error as? AppError.Rejected)?.code
        return code?.let(REJECTED::get) ?: RunkkErrorText.of(error)
    }

    /** 착용 거부 사유(Avatar.rejected 값, 서버 ReasonCode 이름)의 문구. 모르는 코드는 공용 문구다. */
    fun ofCode(code: String): String = REJECTED[code] ?: RunkkErrorText.rejected(code)

    internal val REJECTED = mapOf(
        // 셀카 변환 실패(서버 avatar.FailureReason, 200 + created=false라 앱이 Rejected로 바꾼다)
        "NO_FACE" to "얼굴을 찾지 못했어요. 얼굴이 잘 보이게 다시 찍거나 기본 캐릭터를 골라 주세요",
        "MULTIPLE_FACES" to "한 사람만 나오게 찍어 주세요. 기본 캐릭터를 골라도 돼요",
        "UNSUPPORTED_IMAGE" to "지원하지 않는 사진 형식이에요. 다시 찍거나 기본 캐릭터를 골라 주세요",
        "TOO_LARGE" to "사진이 너무 커요. 다시 찍거나 기본 캐릭터를 골라 주세요",
        "UPSTREAM_FAILED" to "지금은 얼굴을 변환할 수 없어요. 잠시 뒤 다시 시도하거나 기본 캐릭터를 골라 주세요",
        "TIMEOUT" to "변환 시간이 초과됐어요. 다시 시도하거나 기본 캐릭터를 골라 주세요",
        "SELFIE_FAILED" to "얼굴을 만들지 못했어요. 다시 시도하거나 기본 캐릭터를 골라 주세요",
        "SELFIE_LIMIT" to "오늘은 이미 셀카 아바타를 만들었어요. 내일 다시 만들 수 있어요. 기본 캐릭터는 지금 고를 수 있어요",
        "SELFIE_DISABLED" to "지금은 셀카 아바타를 만들 수 없어요. 기본 캐릭터를 골라 주세요",
        "FACE_CONSENT_REQUIRED" to "얼굴 변환 전송 동의가 필요해요. 마이 > 동의 관리에서 켜거나 기본 캐릭터를 골라 주세요",
        "SELFIE_IN_PROGRESS" to "얼굴을 만들고 있어요. 잠시 뒤 다시 확인해 주세요",
        "ITEM_NOT_OWNED" to "가지고 있지 않은 아이템이에요",
        "ITEM_NOT_FOUND" to "없는 아이템이에요",
        "ITEM_NOT_ON_SALE" to "지금은 팔지 않는 아이템이에요",
        "ALREADY_OWNED" to "이미 가진 아이템이에요",
        "INSUFFICIENT_REWARD" to "리워드가 모자라요. 달리고 대결에서 이기면 모여요"
    )
}

/**
 * 셀카를 보내기 전에 줄인다(D-23, D-59): 긴 변 1024px, JPEG, 1MB 이하. 원본은 어디에도 저장하지 않는다(FR-156).
 * 화질을 낮춰도 1MB를 넘으면 마지막 결과를 돌려주고 서버가 거부한다.
 */
internal object SelfieEncoder {
    const val MAX_SIDE = 1_024
    const val MAX_BYTES = 1_000_000

    fun encode(source: Bitmap): ByteArray {
        val longest = max(source.width, source.height)
        val scaled = if (longest > MAX_SIDE) {
            val ratio = MAX_SIDE.toFloat() / longest
            Bitmap.createScaledBitmap(
                source,
                (source.width * ratio).roundToInt().coerceAtLeast(1),
                (source.height * ratio).roundToInt().coerceAtLeast(1),
                true
            )
        } else {
            source
        }
        var quality = START_QUALITY
        var bytes: ByteArray
        do {
            bytes = ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
            quality -= QUALITY_STEP
        } while (bytes.size > MAX_BYTES && quality >= MIN_QUALITY)
        if (scaled !== source) scaled.recycle()
        return bytes
    }

    private const val START_QUALITY = 90
    private const val QUALITY_STEP = 10
    private const val MIN_QUALITY = 40
}
