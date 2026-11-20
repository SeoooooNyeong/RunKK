package com.a207.runkk.app.core.avatar

import kotlin.math.roundToInt
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull

/**
 * 셀카 생성 결과(AI 서버 head-jobs result, 아바타 PoC 온보딩 5절)를 그림 처리 전에 읽은 값. 모든 PNG는 1024×1024 공통 좌표다.
 *
 * - 합성 레이어 여섯 장(faceImage·eyesImage·hairImage, rightFaceImage·rightEyesImage·rightHairImage)은 반드시 있어야 한다.
 * - 완성 원본 frontImage·rightImage는 합성에 쓰지 않아 없어도 된다(미리보기용으로만 보관).
 * - mobile.neckMask·mobile.skinColor는 null일 수 있다. 목 값 둘 중 하나라도 없거나 숫자가 아니면 [neckMask]가 null이라
 *   얼굴 알파에서 목을 찾고, 피부색이 세 값이 아니면 [skinColor]가 null이라 턱 위 피부 중앙값을 쓴다.
 */
internal class GeneratedHeadInput(
    val face: ByteArray,
    val eyes: ByteArray,
    val hair: ByteArray,
    val rightFace: ByteArray,
    val rightEyes: ByteArray,
    val rightHair: ByteArray,
    val frontImage: ByteArray?,
    val rightImage: ByteArray?,
    val neckMask: ReportedNeck?,
    val skinColor: Int?
) {
    /** 서버가 알려 준 목 연결 기준점(턱 중심 x, 턱 y). */
    data class ReportedNeck(val centerX: Float, val chinY: Float)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private const val CHANNEL_MAX = 255

        fun parse(text: String): GeneratedHeadInput {
            val root = objectOf(text)

            fun required(field: String) = HeadResultPolicy.decodePng(
                field,
                root.string(field) ?: throw IllegalArgumentException("$field is missing")
            )

            fun optional(field: String) = root.string(field)?.let { HeadResultPolicy.decodePng(field, it) }
            val mobile = root["mobile"] as? JsonObject
            return GeneratedHeadInput(
                face = required("faceImage"),
                eyes = required("eyesImage"),
                hair = required("hairImage"),
                rightFace = required("rightFaceImage"),
                rightEyes = required("rightEyesImage"),
                rightHair = required("rightHairImage"),
                frontImage = optional("frontImage"),
                rightImage = optional("rightImage"),
                neckMask = (mobile?.get("neckMask") as? JsonObject)?.let(::neck),
                skinColor = (mobile?.get("skinColor") as? JsonArray)?.let(::skin)
            )
        }

        /**
         * 층별로 받은 PNG 여섯 장과 이미지 없는 메타 JSON(서버 …/meta, 또는 mobile만 남긴 통짜 응답)으로 만든다(S15P31A207-300).
         * 층마다 1024×1024 PNG인지 본다. 완성 원본(frontImage·rightImage)은 받지 않으므로 null이다.
         */
        fun of(
            face: ByteArray,
            eyes: ByteArray,
            hair: ByteArray,
            rightFace: ByteArray,
            rightEyes: ByteArray,
            rightHair: ByteArray,
            metaJson: String
        ): GeneratedHeadInput {
            val mobile = objectOf(metaJson)["mobile"] as? JsonObject
            return GeneratedHeadInput(
                face = HeadResultPolicy.checkPng("face", face),
                eyes = HeadResultPolicy.checkPng("eyes", eyes),
                hair = HeadResultPolicy.checkPng("hair", hair),
                rightFace = HeadResultPolicy.checkPng("rightFace", rightFace),
                rightEyes = HeadResultPolicy.checkPng("rightEyes", rightEyes),
                rightHair = HeadResultPolicy.checkPng("rightHair", rightHair),
                frontImage = null,
                rightImage = null,
                neckMask = (mobile?.get("neckMask") as? JsonObject)?.let(::neck),
                skinColor = (mobile?.get("skinColor") as? JsonArray)?.let(::skin)
            )
        }

        private fun objectOf(text: String): JsonObject {
            HeadResultPolicy.checkResultLength(text.length)
            return try {
                json.parseToJsonElement(text) as? JsonObject
            } catch (error: SerializationException) {
                throw IllegalArgumentException("avatar result is not JSON", error)
            } ?: throw IllegalArgumentException("avatar result is not a JSON object")
        }

        private fun JsonObject.string(field: String): String? = (this[field] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.contentOrNull

        private fun JsonElement?.finiteFloat(): Float? = (this as? JsonPrimitive)
            ?.takeIf { !it.isString && it != JsonNull }
            ?.floatOrNull
            ?.takeIf { it.isFinite() }

        private fun neck(mask: JsonObject): ReportedNeck? {
            val centerX = mask["centerX"].finiteFloat() ?: return null
            val chinY = mask["chinY"].finiteFloat() ?: return null
            return ReportedNeck(centerX, chinY)
        }

        private fun skin(values: JsonArray): Int? {
            if (values.size != 3) return null
            val channels = values.map { it.finiteFloat()?.roundToInt()?.coerceIn(0, CHANNEL_MAX) ?: return null }
            return (CHANNEL_MAX shl 24) or (channels[0] shl 16) or (channels[1] shl 8) or channels[2]
        }
    }
}
