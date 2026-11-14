package com.a207.runkk.app.domain.record

/**
 * 랭킹 줄 회원의 아바타 모습(서버 랭킹 Entry.look, S15P31A207-300). 다른 러너도 그 사람의 얼굴·의상·체형·러닝량으로 그린다.
 *
 * @property faceKey 얼굴 키(GET /avatar faceKey와 같은 값). 얼굴을 안 골랐으면 null
 * @property topKey 상의 상점 아이템 그림 키(Shop.Item.imageKey와 같은 값). 안 입었으면 null. 모자 등 그리지 않는 부위는 담지 않는다
 * @property bottomKey 하의 그림 키
 * @property shoesKey 신발 그림 키
 * @property bodyVariant 체형 0..2(/avatar/runner bodyVariant와 같은 규칙)
 * @property runAmount 러닝량 0..100(/avatar/runner runAmount와 같은 규칙)
 */
data class RunnerLook(
    val faceKey: String?,
    val topKey: String?,
    val bottomKey: String?,
    val shoesKey: String?,
    val bodyVariant: Int,
    val runAmount: Int
)
