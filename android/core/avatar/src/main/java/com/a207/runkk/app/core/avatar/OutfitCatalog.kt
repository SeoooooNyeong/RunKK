package com.a207.runkk.app.core.avatar

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 의상 목록(assets/outfits/index.json, D-성-110·115). 묶음이 없는 키는 그 부위 기본 묶음(base_*)으로 그린다.
 * 지금 앱의 의상 번호(AvatarOutfit 0..6)는 [LEGACY_KEYS]의 같은 자리 키로 찾는다(5.7 대응표).
 */
internal class OutfitCatalog(bundles: List<OutfitBundle>, private val bodyShared: Map<String, String> = emptyMap()) {
    private val byKey = bundles.associateBy { it.assetKey }

    /** 맨몸 바탕 경로. 신발 슬롯은 피부가 없어 바탕이 없다. */
    fun bodyFile(gender: AvatarGender, slot: OutfitSlot): String? =
        if (slot == OutfitSlot.RUN_SHOE) null else body("${gender.fileName}_${slot.fileName}")

    /**
     * 상의 자르기 칸(맨몸 바탕에서 상의가 덮어야 하는 곳, 알파 255). 이 칸 안에서 상의 알파 밖의 맨몸 픽셀은 지운다([OutfitLayerComposer]).
     * 손·목·가슴 위는 칸 밖이라 보존한다. 상의가 없는 슬롯은 null.
     */
    fun cutFile(gender: AvatarGender, slot: OutfitSlot): String? =
        if (OutfitPart.TOP in slot.parts) body("${gender.fileName}_${slot.fileName}_cut") else null

    private fun body(name: String) = path(BODY, bodyShared[name] ?: name)

    val keys: Set<String> get() = byKey.keys

    /** [key]의 묶음. 없거나 부위가 다르면 그 부위 base_*. base_*도 없으면 null. */
    fun bundle(part: OutfitPart, key: String?): OutfitBundle? =
        key?.let(byKey::get)?.takeIf { it.part == part } ?: byKey[baseKey(part)]

    fun bundle(part: OutfitPart, id: Int): OutfitBundle? = bundle(part, keyOf(part, id))

    companion object {
        const val ROOT = "outfits"
        const val BODY = "body"
        const val INDEX = "$ROOT/index.json"
        const val EXTENSION = "webp"

        fun path(folder: String, name: String): String = "$ROOT/$folder/$name.$EXTENSION"

        private fun JsonObject.sharedMap(): Map<String, String> =
            this["shared"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content }.orEmpty()

        /** index.json을 읽는다. 형식이 틀리면 IllegalArgumentException(SerializationException 포함). */
        fun parse(text: String): OutfitCatalog {
            val root = Json.parseToJsonElement(text).jsonObject
            val outfits = root.getValue("outfits").jsonArray.map { element ->
                val entry = element.jsonObject
                OutfitBundle(
                    assetKey = entry.getValue("assetKey").jsonPrimitive.content,
                    part = OutfitPart.valueOf(entry.getValue("part").jsonPrimitive.content),
                    maleTopSurface = entry["maleTopSurface"]?.jsonPrimitive?.intOrNull ?: 1,
                    bodyOverrides = entry["bodyOverrides"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
                    shared = entry.sharedMap()
                )
            }
            return OutfitCatalog(outfits, root["body"]?.jsonObject?.sharedMap().orEmpty())
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
