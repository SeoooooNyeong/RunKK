package com.a207.runkk.app.core.avatar

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 의상 부위(assets/outfits/index.json의 part). 겹침 순서는 신발 → 하의 → 상의(OUTFIT_SPEC.md 5.3). */
internal enum class OutfitPart { TOP, BOTTOM, SHOES }

/**
 * 의상 묶음 하나(assets/outfits/<assetKey>/, D-성-110). [maleTopSurface]는 상의만: 남자 서기 riv 입력 selected_top 값
 * (1 반팔·민소매, 2 긴소매, DR-성-1). [bodyOverrides]는 체형 1·2 전용 파일 이름(확장자 없이, D-성-111).
 */
internal data class OutfitBundle(
    val assetKey: String,
    val part: OutfitPart,
    val maleTopSurface: Int = 1,
    val bodyOverrides: List<String> = emptyList()
) {
    /** 이 묶음에서 [gender]·[slot]·[shape]에 쓸 파일 경로(assets 기준). 체형 전용 파일이 있으면 그것을 쓴다. */
    fun file(gender: AvatarGender, slot: OutfitSlot, shape: AvatarBodyShape): String {
        val base = "${gender.fileName}_${slot.fileName}"
        val override = if (shape.stage > 0) "${base}_b${shape.stage}" else null
        val name = override?.takeIf { it in bodyOverrides } ?: base
        return "${OutfitCatalog.ROOT}/$assetKey/$name.png"
    }
}

/** 의상 PNG가 대응하는 riv 이미지 슬롯(OUTFIT_SPEC.md 5.2). */
internal enum class OutfitSlot(val fileName: String, val width: Int, val height: Int, val parts: Set<OutfitPart>) {
    STAND("stand", 512, 768, setOf(OutfitPart.TOP, OutfitPart.BOTTOM, OutfitPart.SHOES)),
    RUN_BODY("run_body", 256, 384, setOf(OutfitPart.TOP)),
    RUN_ARM("run_arm", 256, 1024, setOf(OutfitPart.TOP)),
    RUN_LEG("run_leg", 256, 768, setOf(OutfitPart.BOTTOM)),
    RUN_SHOE("run_shoe", 256, 160, setOf(OutfitPart.SHOES));

    /** 맨몸 바탕 경로. 신발 슬롯은 피부가 없어 바탕이 없다. */
    fun bodyFile(gender: AvatarGender): String? =
        if (this == RUN_SHOE) null else "${OutfitCatalog.ROOT}/${OutfitCatalog.BODY}/${gender.fileName}_$fileName.png"

    companion object {
        /** riv 이미지 자산 이름 → 슬롯. 달리기 몸통 세 슬롯은 같은 그림을 쓴다(DR-성-4). 의상 슬롯이 아니면 null. */
        fun of(assetName: String): OutfitSlot? = when (assetName) {
            BodySkinLayers.STANDING_BODY -> STAND
            "side_body_coral", "side_body_cream", "side_body_blue" -> RUN_BODY
            "continuous_arm" -> RUN_ARM
            "side_leg_skin" -> RUN_LEG
            "side_shoe" -> RUN_SHOE
            else -> null
        }
    }
}

private val AvatarGender.fileName: String get() = if (this == AvatarGender.FEMALE) "female" else "male"

/**
 * 의상 목록(assets/outfits/index.json, D-성-110·115). 묶음이 없는 키는 그 부위 기본 묶음(base_*)으로 그린다.
 * 지금 앱의 의상 번호(AvatarOutfit 0..6)는 [LEGACY_KEYS]의 같은 자리 키로 찾는다(5.7 대응표).
 */
internal class OutfitCatalog(bundles: List<OutfitBundle>) {
    private val byKey = bundles.associateBy { it.assetKey }

    val keys: Set<String> get() = byKey.keys

    /** [key]의 묶음. 없거나 부위가 다르면 그 부위 base_*. base_*도 없으면 null. */
    fun bundle(part: OutfitPart, key: String?): OutfitBundle? =
        key?.let(byKey::get)?.takeIf { it.part == part } ?: byKey[baseKey(part)]

    fun bundle(part: OutfitPart, id: Int): OutfitBundle? = bundle(part, keyOf(part, id))

    companion object {
        const val ROOT = "outfits"
        const val BODY = "body"
        const val INDEX = "$ROOT/index.json"

        /** index.json을 읽는다. 형식이 틀리면 IllegalArgumentException(SerializationException 포함). */
        fun parse(text: String): OutfitCatalog {
            val root = Json.parseToJsonElement(text).jsonObject
            val outfits = root.getValue("outfits").jsonArray.map { element ->
                val entry = element.jsonObject
                OutfitBundle(
                    assetKey = entry.getValue("assetKey").jsonPrimitive.content,
                    part = OutfitPart.valueOf(entry.getValue("part").jsonPrimitive.content),
                    maleTopSurface = entry["maleTopSurface"]?.jsonPrimitive?.intOrNull ?: 1,
                    bodyOverrides = entry["bodyOverrides"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
                )
            }
            return OutfitCatalog(outfits)
        }

        /** 부위별 번호 0..6 → 묶음 폴더(design-oo 05-30 5.7). */
        val LEGACY_KEYS: Map<OutfitPart, List<String>> = mapOf(
            OutfitPart.TOP to listOf(
                "base_top",
                "top_lime_singlet",
                "top_crew_tee",
                "top_finisher_tee",
                "top_winter_hoodie",
                "top_dawn_windbreaker",
                "top_cherry_vest"
            ),
            OutfitPart.BOTTOM to listOf(
                "base_bottom",
                "bottom_running_shorts",
                "bottom_leggings",
                "bottom_jogger_pants",
                "bottom_wide_pants",
                "bottom_cargo_shorts",
                "bottom_line_shorts"
            ),
            OutfitPart.SHOES to listOf(
                "base_shoes",
                "shoes_basic_runner",
                "shoes_high_top",
                "shoes_carbon_racer",
                "shoes_slip_on",
                "shoes_ankle_boots",
                "shoes_lime_runner"
            )
        )

        fun baseKey(part: OutfitPart): String = LEGACY_KEYS.getValue(part).first()

        fun keyOf(part: OutfitPart, id: Int): String = LEGACY_KEYS.getValue(part).getOrElse(id) { baseKey(part) }
    }
}
