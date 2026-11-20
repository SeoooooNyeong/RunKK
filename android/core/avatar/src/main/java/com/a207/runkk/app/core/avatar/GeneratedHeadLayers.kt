package com.a207.runkk.app.core.avatar

import androidx.annotation.WorkerThread

/**
 * 셀카로 생성한 머리를 남녀 Rive 몸체에 붙일 수 있게 바꾼 결과. [RunkkRiveAvatar]의 headLayers로 넘긴다(null이면 내장 기본 머리).
 *
 * 같은 인스턴스를 다시 넘기면 다시 합성하지 않는다(참조로 비교한다). 화면은 결과 하나당 한 번만 만들어 상태로 들고 있는다.
 */
class GeneratedHeadLayers internal constructor(
    internal val variants: Map<AvatarGender, AvatarVariantAssets>,
    /** 생성 얼굴의 피부색(불투명 ARGB). 몸 피부 바탕을 이 색에 맞춘다. */
    internal val skinColor: Int,
    /**
     * 기기 사본 열쇠(합성 입력의 해시, 디스크 칸을 넘겨 [fromLayers]로 만든 머리만). 이 머리로 합성한 톤을 기기에 남길 때 머리를 가르는
     * 값이다(S15P31A207-345 9회차). null이면 이 머리로 합성한 것은 기기에 남기지 않는다.
     */
    internal val sourceKey: String? = null
) {
    companion object {
        /**
         * 셀카 생성 결과 JSON(faceImage·eyesImage·hairImage·rightFaceImage·rightEyesImage·rightHairImage, 선택 frontImage·
         * rightImage, 선택 mobile.neckMask·mobile.skinColor)을 바꾼다. 모든 PNG는 1024×1024 Data URL이다.
         *
         * 그림을 여러 장 풀고 다시 인코딩하므로(수백 ms~수 초) 주 스레드에서 부르지 않는다. 형식이 맞지 않거나 목·눈을 찾지
         * 못하면 IllegalArgumentException이다. 화면은 실패하면 기본 머리(null)를 그린다.
         */
        @WorkerThread
        fun fromHeadJson(json: String): GeneratedHeadLayers =
            GeneratedAvatarLayers.prepare(GeneratedHeadInput.parse(json))

        /**
         * 층별로 받은 셀카 머리(S15P31A207-300 레이어 합성: 서버 …/layers/{층}?size=1024 PNG 여섯 장과 이미지 없는 메타 JSON의
         * mobile.neckMask·mobile.skinColor)를 바꾼다. 같은 그림·메타면 [fromHeadJson]과 같은 결과다. 층이 1024×1024 PNG가 아니거나
         * 메타가 JSON 객체가 아니면 IllegalArgumentException이다. 주 스레드에서 부르지 않는다.
         */
        @WorkerThread
        fun fromLayers(
            face: ByteArray,
            eyes: ByteArray,
            hair: ByteArray,
            rightFace: ByteArray,
            rightEyes: ByteArray,
            rightHair: ByteArray,
            metaJson: String
        ): GeneratedHeadLayers = GeneratedAvatarLayers.prepare(
            GeneratedHeadInput.of(face, eyes, hair, rightFace, rightEyes, rightHair, metaJson)
        )

        /**
         * [fromLayers]에 기기 사본을 더한 것(S15P31A207-345 9회차). 열쇠는 머리 키 [faceKey]·층·메타·앱 판의 해시다. [disk]에 같은 열쇠의
         * 사본이 있으면 그림을 풀지 않고 그것을 읽고, 없으면 합성한 뒤 남긴다(같은 칸의 옛 머리 사본은 새 사본을 남긴 뒤에 지운다).
         * [disk]가 null이면 위 [fromLayers]와 같다. 주 스레드에서 부르지 않는다.
         */
        @WorkerThread
        fun fromLayers(
            disk: AvatarLookDisk?,
            faceKey: String,
            face: ByteArray,
            eyes: ByteArray,
            hair: ByteArray,
            rightFace: ByteArray,
            rightEyes: ByteArray,
            rightHair: ByteArray,
            metaJson: String
        ): GeneratedHeadLayers {
            if (disk == null) return fromLayers(face, eyes, hair, rightFace, rightEyes, rightHair, metaJson)
            val startedAt = AvatarPrepareTrace.now()
            val key = disk.headKey(faceKey, metaJson, listOf(face, eyes, hair, rightFace, rightEyes, rightHair))
            disk.readHead(key)?.let { saved ->
                AvatarPrepareTrace.mark("head-layers", startedAt, "disk=hit")
                return saved
            }
            val made = fromLayers(face, eyes, hair, rightFace, rightEyes, rightHair, metaJson)
            val result = GeneratedHeadLayers(made.variants, made.skinColor, key)
            disk.writeHead(key, result)
            AvatarPrepareTrace.mark("head-layers", startedAt, "disk=miss")
            return result
        }
    }
}
