package com.a207.runkk.app.domain.avatar

import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.Outcome

/**
 * 아바타·상점 포트(5.7.1 /avatar/…, /shop/…). 구현 api.avatar.
 * 셀카는 앱이 긴 변 1024px·JPEG·1MB 이하로 줄여 보내고 어디에도 저장하지 않는다(D-23, D-59).
 */
interface AvatarRepository {
    suspend fun avatar(): Outcome<Avatar>

    suspend fun createFromSelfie(jpeg: ByteArray): Outcome<Avatar>

    suspend fun chooseDefault(characterId: String): Outcome<Avatar>

    /** 착용·해제 변경. 거부된 항목은 Avatar.rejected에 사유로 모인다. */
    suspend fun equip(changes: Map<ItemSlot, Long?>): Outcome<Avatar>

    suspend fun shop(slot: ItemSlot?): Outcome<Shop>

    suspend fun purchase(itemId: Long): Outcome<Int>

    /** 배경 목록과 고른 배경(GET /avatar/backgrounds, FR-201, FR-202). */
    suspend fun backgrounds(): Outcome<AvatarBackgrounds>

    /** 배경 고르기(PUT /avatar/background, FR-202). null이면 기본 배경. 해금하지 않은 배경은 BACKGROUND_LOCKED. */
    suspend fun chooseBackground(key: String?): Outcome<Unit>

    /** 러닝 카드 배경: 고른 키와 그 단계(GET /avatar/backgrounds, ui.record가 읽는다, D-140, D-185). 기본 배경이면 null. */
    suspend fun backgroundChoice(): Outcome<BackgroundChoice?>

    /**
     * 셀카 아바타 얼굴 부위 그림(GET /avatar/parts/face/…, FR-155). [faceKey]가 셀카 얼굴이 아니면 빈 목록이다.
     * 받지 못하면 실패이고 화면은 지금의 자리 그림을 그린다. 구현이 없으면 빈 목록이다.
     */
    suspend fun faceLayers(faceKey: String?): Outcome<List<FaceLayer>> = Outcome.Success(emptyList())
}
