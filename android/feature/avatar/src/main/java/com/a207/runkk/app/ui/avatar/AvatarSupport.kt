package com.a207.runkk.app.ui.avatar

import android.graphics.Bitmap
import androidx.annotation.StringRes
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.domain.avatar.ItemSlot
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** 기본 캐릭터 얼굴 4종(FR-158). id는 서버 DefaultCharacter 이름이다. */
enum class DefaultCharacter(@StringRes val labelRes: Int) {
    RABBIT(R.string.avatar_character_rabbit),
    BEAR(R.string.avatar_character_bear),
    FOX(R.string.avatar_character_fox),
    CAT(R.string.avatar_character_cat);

    companion object {
        fun of(id: String?): DefaultCharacter? = entries.firstOrNull { it.name == id }
    }
}

/** 부위 문구(FR-161). */
@get:StringRes
internal val ItemSlot.labelRes: Int
    get() = when (this) {
        ItemSlot.TOP -> R.string.avatar_slot_top
        ItemSlot.BOTTOM -> R.string.avatar_slot_bottom
        ItemSlot.SHOES -> R.string.avatar_slot_shoes
        ItemSlot.HAT -> R.string.avatar_slot_hat
    }

/** 체형 단계(서버 BodyShape STAGE1~4, FR-163). 모르는 값이면 null. */
internal fun bodyStage(bodyShape: String): Int? = bodyShape.filter(Char::isDigit).toIntOrNull()?.takeIf {
    it in
        1..BODY_STAGES
}

@StringRes
internal fun bodyLabelRes(stage: Int?): Int = when (stage) {
    1 -> R.string.avatar_body_1
    2 -> R.string.avatar_body_2
    3 -> R.string.avatar_body_3
    4 -> R.string.avatar_body_4
    else -> R.string.avatar_body_unknown
}

internal const val BODY_STAGES = 4

/** 피부톤(서버 SkinTone, FR-164): 이름과 근거 문구. */
internal fun skinLabel(skinTone: String): UiText = when (skinTone) {
    "DAWN" -> UiText.Res(R.string.avatar_skin_dawn)
    "MORNING" -> UiText.Res(R.string.avatar_skin_morning)
    "DAYTIME" -> UiText.Res(R.string.avatar_skin_daytime)
    "EVENING" -> UiText.Res(R.string.avatar_skin_evening)
    "NIGHT" -> UiText.Res(R.string.avatar_skin_night)
    else -> UiText.Plain(skinTone)
}

/** 아바타·상점 거부 사유 문구(서버 ReasonCode). 모르는 코드는 공용 문구. */
internal object AvatarErrorText {
    fun of(error: AppError): UiText {
        val code = (error as? AppError.Rejected)?.code
        return code?.let(REJECTED::get)?.let { UiText.Res(it) } ?: RunkkErrorText.of(error)
    }

    /** 착용 거부 사유(Avatar.rejected 값, 서버 ReasonCode 이름)의 문구. 모르는 코드는 공용 문구다. */
    fun ofCode(code: String): UiText = REJECTED[code]?.let { UiText.Res(it) } ?: RunkkErrorText.rejected(code)

    internal val REJECTED: Map<String, Int> = mapOf(
        // 셀카 변환 실패(서버 avatar.FailureReason, 200 + created=false라 앱이 Rejected로 바꾼다)
        "NO_FACE" to R.string.avatar_error_no_face,
        "MULTIPLE_FACES" to R.string.avatar_error_multiple_faces,
        "UNSUPPORTED_IMAGE" to R.string.avatar_error_unsupported_image,
        "TOO_LARGE" to R.string.avatar_error_too_large,
        "UPSTREAM_FAILED" to R.string.avatar_error_upstream_failed,
        "TIMEOUT" to R.string.avatar_error_timeout,
        "SELFIE_FAILED" to R.string.avatar_error_selfie_failed,
        "SELFIE_LIMIT" to R.string.avatar_error_selfie_limit,
        "SELFIE_DISABLED" to R.string.avatar_error_selfie_disabled,
        "FACE_CONSENT_REQUIRED" to R.string.avatar_error_face_consent_required,
        "SELFIE_RETRY_LIMIT" to R.string.avatar_error_selfie_retry_limit,
        "FACE_IMAGE_NOT_FOUND" to R.string.avatar_error_face_image_not_found,
        "SELFIE_IN_PROGRESS" to R.string.avatar_error_selfie_in_progress,
        "ITEM_NOT_OWNED" to R.string.avatar_error_item_not_owned,
        "ITEM_NOT_FOUND" to R.string.avatar_error_item_not_found,
        "ITEM_NOT_ON_SALE" to R.string.avatar_error_item_not_on_sale,
        "ALREADY_OWNED" to R.string.avatar_error_already_owned,
        "INSUFFICIENT_REWARD" to R.string.avatar_error_insufficient_reward
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
