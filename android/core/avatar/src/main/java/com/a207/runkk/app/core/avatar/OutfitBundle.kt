package com.a207.runkk.app.core.avatar

/**
 * 의상 묶음 하나(assets/outfits/<assetKey>/, D-성-110). [maleTopSurface]는 상의만: 남자 서기 riv 입력 selected_top 값
 * (1 반팔·민소매, 2 긴소매, DR-성-1). [bodyOverrides]는 체형 1·2 전용 파일 이름(확장자 없이, D-성-111).
 * [shared]는 같은 내용이라 한 장을 함께 쓰는 파일(이름 → 실제 파일 이름, 확장자 없이. 예: male_run_arm → female_run_arm).
 */
internal data class OutfitBundle(
    val assetKey: String,
    val part: OutfitPart,
    val maleTopSurface: Int = 1,
    val bodyOverrides: List<String> = emptyList(),
    val shared: Map<String, String> = emptyMap()
) {
    /** 이 묶음에서 [gender]·[slot]·[shape]에 쓸 파일 경로(assets 기준). 체형 전용 파일이 있으면 그것을 쓴다. */
    fun file(gender: AvatarGender, slot: OutfitSlot, shape: AvatarBodyShape): String {
        val base = "${gender.fileName}_${slot.fileName}"
        val override = if (shape.stage > 0) "${base}_b${shape.stage}" else null
        val name = override?.takeIf { it in bodyOverrides } ?: base
        return OutfitCatalog.path(assetKey, shared[name] ?: name)
    }
}
