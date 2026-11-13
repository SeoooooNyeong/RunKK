package com.a207.runkk.app.core.avatar

import app.rive.runtime.kotlin.core.FileAsset
import app.rive.runtime.kotlin.core.FileAssetLoader
import app.rive.runtime.kotlin.core.ImageAsset

/**
 * 달리기(측면) Rive 파일의 피부·의상 이미지 자리를 잡아 두고 바꾼다(PoC MainActivity.RunSkinLoader). 측면 머리 side_head,
 * 몸통 세 벌, 다리, 이어진 팔, 신발, 옆볼 fullness_overlay_side를 쓴다.
 */
internal class RunAvatarAssetLoader : FileAssetLoader() {
    private val assets = mutableMapOf<String, ImageAsset>()
    private val originals = mutableMapOf<String, ByteArray>()
    private var fullnessAsset: ImageAsset? = null
    private var originalFullness: ByteArray? = null

    override fun loadContents(asset: FileAsset, inBandBytes: ByteArray): Boolean {
        if (asset !is ImageAsset) return false
        val name = asset.name.substringBeforeLast('.')
        if (name in SLOTS) {
            check(inBandBytes.isNotEmpty()) { "embedded image missing: $name" }
            assets[name] = asset
            originals[name] = inBandBytes.copyOf()
        } else if (name == FULLNESS) {
            check(inBandBytes.isNotEmpty()) { "embedded side fullness overlay missing" }
            fullnessAsset = asset
            originalFullness = inBandBytes.copyOf()
        }
        return false
    }

    fun hasApprovedAssets(): Boolean =
        assets.keys.containsAll(SLOTS) && fullnessAsset != null && originalFullness != null

    fun originalFullnessBytes(): ByteArray = checkNotNull(originalFullness) { "side fullness overlay slot missing" }

    fun replaceFullnessOverlay(bytes: ByteArray) =
        checkNotNull(fullnessAsset) { "side fullness overlay slot missing" }.assignPng(bytes)

    fun originalSkinBytes(): Map<String, ByteArray> {
        check(hasApprovedAssets()) { "run skin slots missing" }
        return originals.toMap()
    }

    fun applySkin(textures: Map<String, ByteArray>) {
        check(hasApprovedAssets() && textures.keys.containsAll(SLOTS)) { "run skin textures missing" }
        SLOTS.forEach { assets.getValue(it).assignPng(textures.getValue(it)) }
    }

    fun clearHandles() {
        assets.clear()
        originals.clear()
        fullnessAsset = null
        originalFullness = null
    }

    companion object {
        /** 옆볼 덮개 자리 이름. */
        const val FULLNESS = "fullness_overlay_side"

        /** 달리기 피부·의상 자리 이름(미리 준비가 riv 내장 이미지에서 같은 이름을 고른다). */
        val SLOTS = setOf(
            "side_head",
            "side_body_coral",
            "side_body_cream",
            "side_body_blue",
            "side_leg_skin",
            "continuous_arm",
            "side_shoe"
        )
    }
}
