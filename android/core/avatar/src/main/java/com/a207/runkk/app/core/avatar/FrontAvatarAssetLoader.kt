package com.a207.runkk.app.core.avatar

import app.rive.runtime.kotlin.core.FileAsset
import app.rive.runtime.kotlin.core.FileAssetLoader
import app.rive.runtime.kotlin.core.ImageAsset

/**
 * 정면(서기) Rive 파일의 이미지 자리를 잡아 두고 바꾼다(PoC MainActivity.HeadLoader). 머리 다섯 자리, 몸 standing_body,
 * 남자 선택 몸 male_selected_body, 볼 fullness_overlay를 쓴다. Rive 파일에 들어 있던 원본은 읽기 전용 사본으로 남긴다.
 * standing_body는 처음부터 기본 의상 몸([defaultStandingBody])으로 시작한다.
 */
internal class FrontAvatarAssetLoader(private val defaultStandingBody: ByteArray) : FileAssetLoader() {
    private val assets = mutableMapOf<String, ImageAsset>()
    private val originals = mutableMapOf<String, ByteArray>()
    private var sources: Map<String, ByteArray>? = null
    private var fullnessAsset: ImageAsset? = null
    private var originalFullness: ByteArray? = null
    private var hasPreparedHead = false

    override fun loadContents(asset: FileAsset, inBandBytes: ByteArray): Boolean {
        if (asset !is ImageAsset) return false
        val name = asset.name.substringBeforeLast('.')
        val isHeadOrBody = name in AvatarVariantAssets.FRONT_SLOTS || name == BodySkinLayers.STANDING_BODY ||
            name == MaleSelectedBody.ASSET
        if (isHeadOrBody) {
            check(inBandBytes.isNotEmpty()) { "embedded image missing: $name" }
            assets[name] = asset
            originals[name] = inBandBytes.copyOf()
            if (name == BodySkinLayers.STANDING_BODY) {
                asset.assignPng(defaultStandingBody)
                return true
            }
            if (name == MaleSelectedBody.ASSET) {
                // 내장 기본 옷 몸은 그리지 않는다(D-747): 투명으로 바꿔 아틀라스 표면 빈 곳으로 비치지 않게 한다
                asset.assignPng(MaleSelectedBody.transparentPng)
                return true
            }
        } else if (name == FULLNESS) {
            check(inBandBytes.isNotEmpty()) { "embedded fullness overlay missing" }
            fullnessAsset = asset
            originalFullness = inBandBytes.copyOf()
        }
        return false
    }

    fun hasApprovedAssets(): Boolean = assets.keys.containsAll(AvatarVariantAssets.FRONT_SLOTS) &&
        BodySkinLayers.STANDING_BODY in assets &&
        fullnessAsset != null &&
        originalFullness != null

    /** 이 몸체에 넣을 머리 원본(내장 또는 생성). 다음 [replaceFace]가 다섯 자리를 모두 다시 넣는다. */
    fun setVariantSources(front: Map<String, ByteArray>) {
        require(front.keys.containsAll(AvatarVariantAssets.FRONT_SLOTS)) { "front head layers are incomplete" }
        sources = front
        hasPreparedHead = false
    }

    fun originalFaceBytes(): ByteArray = source("face_layer")

    fun originalHairBytes(): ByteArray = source("hair_layer")

    fun originalBodyBytes(): ByteArray {
        check(hasApprovedAssets()) { "standing body slot missing" }
        return originals.getValue(BodySkinLayers.STANDING_BODY)
    }

    /** 남자 서기 riv(내장 기본 옷 몸 자리와 selected_* 입력이 있다)인가. 그 자리는 바꾸지 않는다. */
    fun hasSelectedBody(): Boolean = MaleSelectedBody.ASSET in assets

    fun originalFullnessBytes(): ByteArray = checkNotNull(originalFullness) { "fullness overlay slot missing" }

    fun replaceBody(bytes: ByteArray) = assets.getValue(BodySkinLayers.STANDING_BODY).assignPng(bytes)

    /** 볼 레이어. null이면 비운다. */
    fun replaceFullnessOverlay(bytes: ByteArray?) =
        checkNotNull(fullnessAsset) { "fullness overlay slot missing" }.assignPng(bytes ?: TRANSPARENT_PNG)

    /**
     * 톤을 적용한 피부 얼굴과 그 얼굴에 맞춘 헤어(고유색 그대로, 피부 섞인 경계만 톤, DV-5)를 넣는다. 머리 원본을 바꾼 뒤 처음이면
     * 눈 세 장(원래 색)도 함께 넣어, 눈이 톤을 거치지 않고 피부 얼굴 위에 합성되게 한다.
     */
    fun replaceFace(face: ByteArray, hair: ByteArray) {
        check(hasApprovedAssets()) { "front head slots missing" }
        if (hasPreparedHead) {
            assets.getValue("face_layer").assignPng(face)
            assets.getValue("hair_layer").assignPng(hair)
            return
        }
        AvatarVariantAssets.FRONT_SLOTS.forEach { slot ->
            val bytes = when (slot) {
                "face_layer" -> face
                "hair_layer" -> hair
                else -> source(slot)
            }
            assets.getValue(slot).assignPng(bytes)
        }
        hasPreparedHead = true
    }

    fun clearHandles() {
        assets.clear()
        originals.clear()
        sources = null
        fullnessAsset = null
        originalFullness = null
        hasPreparedHead = false
    }

    private fun source(slot: String): ByteArray {
        check(hasApprovedAssets()) { "front head slots missing" }
        return (sources ?: originals).getValue(slot)
    }

    companion object {
        /** 정면 볼 덮개 자리 이름. */
        const val FULLNESS = "fullness_overlay"
    }
}
