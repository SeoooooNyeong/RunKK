package com.a207.runkk.app.core.avatar

/**
 * 피부톤·의상·체형 합성 요청 한 건(PoC MainActivity.ToneRequest). 주 스레드에서 원본 바이트를 모아 만들고 합성 스레드가 읽기만 한다.
 * [version]·[identity]가 지금 값과 다르면 결과를 버린다(가장 최근 요청만 화면에 남는다).
 */
internal class AvatarToneRequest(
    val version: Int,
    val identity: Int,
    val gender: AvatarGender,
    val amount: Int,
    val outfit: AvatarOutfit,
    val shape: AvatarBodyShape,
    /** 정면 피부 얼굴 원본. 정면 플레이어가 없으면 null. */
    val face: ByteArray?,
    /** 정면 헤어 원본. 톤을 거치지 않고 그대로 넣는다. */
    val hair: ByteArray?,
    /** 지금 체형의 정면 볼 레이어 원본. 정면 플레이어가 없으면 null. */
    val frontFullness: ByteArray?,
    /** 지금 체형의 측면 볼 레이어 원본. 달리기 플레이어가 없으면 null. */
    val runFullness: ByteArray?,
    /** 측면 머리 세 층. 달리기 플레이어가 없으면 null. */
    val sideLayers: SideHeadLayers?,
    /** Rive 파일에 들어 있던 몸 텍스처 원본(이름 → PNG). */
    val skinAssets: Map<String, ByteArray>,
    /** 생성 머리의 얼굴 피부색. 내장 머리면 null. */
    val generatedSkinColor: Int?
)
