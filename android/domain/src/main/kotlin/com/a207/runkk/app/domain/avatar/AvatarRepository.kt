package com.a207.runkk.app.domain.avatar

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.Outcome

/**
 * 아바타·상점 포트(5.7.1 /avatar/…, /shop/…). 구현 api.avatar.
 * 셀카는 앱이 긴 변 1024px·JPEG·1MB 이하로 줄여 보내고 어디에도 저장하지 않는다(D-23, D-59). 얼굴은 서버가 비동기로 만든다.
 */
interface AvatarRepository {
    suspend fun avatar(): Outcome<Avatar>

    /**
     * 셀카 접수(POST /avatar/selfie/jobs → 202, S15P31A207-276). 접수한 작업 ID는 기기에 남겨 화면을 떠나거나 앱을 다시 켜도
     * [pendingSelfieJobId]로 이어 조회한다. 동의 없음·하루 한도·재시도 한도·진행 중은 Rejected(사유 코드)다.
     */
    suspend fun submitSelfie(jpeg: ByteArray): Outcome<SelfieJob>

    /** 작업 상태(GET /avatar/selfie/jobs/{작업ID}). 실패로 끝났거나 서버가 작업을 잃었으면(JOB_NOT_FOUND) 남긴 작업 ID를 지운다. */
    suspend fun selfieJob(jobId: String): Outcome<SelfieJob>

    /**
     * 끝난 작업의 결과를 받아(GET /avatar/selfie/jobs/{작업ID}/result) 서버가 내 얼굴을 셀카 머리로 바꾸게 하고, 바뀐 아바타를 읽는다.
     * 남긴 작업 ID를 지운다.
     */
    suspend fun finishSelfie(jobId: String): Outcome<Avatar>

    /**
     * 이어 조회할 셀카 작업 ID(로그인한 회원이 접수하고 아직 끝내지 않은 것). 기기에 남긴 것이 없으면 서버의 진행 중 작업
     * (GET /avatar/selfie/jobs/current)을 쓴다. 없으면 null.
     */
    suspend fun pendingSelfieJobId(): String?

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

    /** 내 러너(GET /avatar/runner, W8 H-2). 구현이 없으면 실패다. */
    suspend fun runner(): Outcome<AvatarRunner> = Outcome.Failure(AppError.Server(HTTP_NOT_IMPLEMENTED))

    /**
     * 셀카 머리 결과(GET /avatar/heads/{회원ID}/{작업ID}, W9 Rive 렌더러 입력, contract-W8.md 6.2). 본문을 한 번 받아 원문 JSON과
     * 대체 그림(정면 세 층)을 함께 준다. [faceKey]가 셀카 머리("head:…")가 아니면 null이다. 받지 못하면 실패이고, 받았지만 층 형식이
     * 틀렸으면 대체 그림이 빈 성공이다. 구현이 없으면 null이다.
     */
    suspend fun selfieHead(faceKey: String?): Outcome<SelfieHead?> = Outcome.Success(null)
}

/** 구현이 없는 기본 메소드의 실패 상태(HTTP 501과 같은 뜻). */
private const val HTTP_NOT_IMPLEMENTED = 501
