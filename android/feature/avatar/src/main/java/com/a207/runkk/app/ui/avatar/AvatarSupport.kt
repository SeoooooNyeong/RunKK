package com.a207.runkk.app.ui.avatar

import androidx.annotation.StringRes
import com.a207.runkk.app.core.designsystem.RunkkAvatarArt
import com.a207.runkk.app.domain.avatar.Avatar
import com.a207.runkk.app.domain.avatar.ItemSlot

/**
 * 그릴 얼굴 키(서버 Face.renderKey). 서버는 기본 캐릭터면 faceKey "default:<이름>"과 defaultCharacterId를 함께 준다.
 * faceKey가 비었으면 defaultCharacterId로 같은 형식의 키를 만들고, 얼굴을 고르기 전이면 기본 캐릭터 얼굴 1번이다(FR-155·FR-158).
 */
internal val Avatar.renderKey: String
    get() = faceKey ?: defaultCharacterId?.let(RunkkAvatarArt::defaultKey) ?: RunkkAvatarArt.DEFAULT_FACE_KEY

/** 기본 캐릭터 얼굴인가(어느 캐릭터 값이든 앱은 기본 얼굴 하나로 그린다, [임시:D-성-187]). 얼굴을 고르기 전도 기본 얼굴이다. */
internal val Avatar.isDefaultFace: Boolean
    get() = RunkkAvatarArt.isDefaultKey(renderKey)

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

/**
 * 꾸미기 탭·상점 칩에 보이는 부위(한 곳의 목록, [임시:D-성-183]). 모자(「악세서리」) 부위는 숨긴다: Rive에 모자 층이 없어 입어도 그림이
 * 바뀌지 않는다. 서버·상점 API·저장된 모자 착용은 그대로다(저장은 바뀐 부위만 보내므로 모자 착용을 건드리지 않는다).
 */
internal val VISIBLE_ITEM_SLOTS: List<ItemSlot> = listOf(ItemSlot.TOP, ItemSlot.BOTTOM, ItemSlot.SHOES)

/** 꾸미기 탭 부위 문구: 모자 부위는 "악세서리"(사용자 결정 A2, FR-161 표시 이름), 나머지는 [labelRes]와 같다. */
@get:StringRes
internal val ItemSlot.decorateLabelRes: Int
    get() = if (this == ItemSlot.HAT) R.string.avatar_slot_accessory else labelRes

/**
 * 서버 체형 4단계를 화면·렌더러 3단계(0 기본, 1 변화 1, 2 변화 2)로 옮긴다. 서버 BodyShape.variant와 같다([임시:D-성-179]: 4단계가 기본
 * 몸, 2·3단계가 조금 통통, 1단계가 가장 통통). 모르면 0. 앞 판은 4단계를 2(가장 통통)로 뒤집어 옮겨, 상점·꾸미기 무대가 홈(내 러너
 * bodyVariant)과 다른 체형으로 그리며 셀카 눈 위에 볼 레이어를 얹었다([임시:D-성-191]).
 */
internal fun bodyVariantOf(bodyShape: String): Int = when (bodyStage(bodyShape)) {
    1 -> 2
    2, 3 -> 1
    else -> 0
}

/** 체형 단계(서버 BodyShape STAGE1~4, FR-163). 모르는 값이면 null. */
internal fun bodyStage(bodyShape: String): Int? = bodyShape.filter(Char::isDigit).toIntOrNull()?.takeIf {
    it in
        1..BODY_STAGES
}

internal const val BODY_STAGES = 4
