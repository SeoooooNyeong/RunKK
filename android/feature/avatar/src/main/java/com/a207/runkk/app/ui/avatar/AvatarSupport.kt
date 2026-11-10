package com.a207.runkk.app.ui.avatar

import androidx.annotation.StringRes
import com.a207.runkk.app.core.designsystem.RunkkAvatarArt
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.avatar.Avatar
import com.a207.runkk.app.domain.avatar.ItemSlot

/**
 * 그릴 얼굴 키(서버 Face.renderKey). 서버는 기본 캐릭터면 faceKey "default:<이름>"과 defaultCharacterId를 함께 준다.
 * faceKey가 비었으면 defaultCharacterId로 같은 형식의 키를 만들고, 얼굴을 고르기 전이면 기본 캐릭터 얼굴 1번이다(FR-155·FR-158).
 */
internal val Avatar.renderKey: String
    get() = faceKey ?: defaultCharacterId?.let(RunkkAvatarArt::defaultKey) ?: RunkkAvatarArt.DEFAULT_FACE_KEY

/** 기본 캐릭터 얼굴이면 그 캐릭터, 셀카 얼굴·얼굴 없음·모르는 값이면 null. */
internal val Avatar.character: DefaultCharacter?
    get() = DefaultCharacter.ofFaceKey(renderKey)

/** 셀카 얼굴인지(서버 SelfieFace.renderKey "selfie:…", 비동기 셀카 머리 "head:{회원ID}:{작업ID}"). */
internal val Avatar.isSelfieFace: Boolean
    get() = faceKey?.let { it.startsWith(SELFIE_PREFIX) || it.startsWith(HEAD_PREFIX) } == true

private const val SELFIE_PREFIX = "selfie:"
private const val HEAD_PREFIX = "head:"

/** 부위 문구(FR-161). */
@get:StringRes
internal val ItemSlot.labelRes: Int
    get() = when (this) {
        ItemSlot.TOP -> R.string.avatar_slot_top
        ItemSlot.BOTTOM -> R.string.avatar_slot_bottom
        ItemSlot.SHOES -> R.string.avatar_slot_shoes
        ItemSlot.HAT -> R.string.avatar_slot_hat
    }

/** 꾸미기 탭 부위 문구: 모자 부위는 "악세서리"(사용자 결정 A2, FR-161 표시 이름), 나머지는 [labelRes]와 같다. */
@get:StringRes
internal val ItemSlot.decorateLabelRes: Int
    get() = if (this == ItemSlot.HAT) R.string.avatar_slot_accessory else labelRes

/** 서버 체형 4단계를 화면·렌더러 3단계(0 기본, 1 변화 1, 2 변화 2)로(서버 BodyShape.variant와 같다). 모르면 0. */
internal fun bodyVariantOf(bodyShape: String): Int = when (bodyStage(bodyShape)) {
    2, 3 -> 1
    4 -> 2
    else -> 0
}

/** 체형 단계(서버 BodyShape STAGE1~4, FR-163). 모르는 값이면 null. */
internal fun bodyStage(bodyShape: String): Int? = bodyShape.filter(Char::isDigit).toIntOrNull()?.takeIf {
    it in
        1..BODY_STAGES
}

/**
 * 체형 이름(DV-성-12): 꾸미기와 내 러너가 같은 3단계 표를 쓴다. 서버 4단계를 [bodyVariantOf]로 접은 단계(D-성-58)의 이름이다.
 * 모르는 체형 값이면 「체형 확인 중」.
 */
@StringRes
internal fun bodyLabelRes(bodyShape: String): Int =
    if (bodyStage(bodyShape) == null) R.string.avatar_body_unknown else bodyVariantLabelRes(bodyVariantOf(bodyShape))

/** 3단계 체형(0 기본, 1 변화 1, 2 변화 2)의 이름. 범위 밖은 가까운 끝으로. */
@StringRes
internal fun bodyVariantLabelRes(variant: Int): Int =
    BODY_VARIANT_LABELS[variant.coerceIn(0, BODY_VARIANT_LABELS.lastIndex)]

private val BODY_VARIANT_LABELS = listOf(R.string.avatar_body_low, R.string.avatar_body_mid, R.string.avatar_body_high)

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
