package com.a207.runkk.app.core.avatar

/**
 * 합성 요청([AvatarToneRequest])을 한 곳에서 만든다. Rive 뷰(로더가 잡은 원본)와 미리 준비(riv 내장 이미지,
 * [PreparedRiveAssets.embeddedImages])가 같은 원본으로 같은 요청을 만들어야 공용 합성 캐시([SharedToneCache])의 결과를 서로 쓸 수 있다.
 */
internal object AvatarToneRequests {
    /** 정면 몸 원본: standing_body, 남자 내장 기본 옷 몸(남자만), 체형 0 볼 덮개. */
    class FrontOriginals(val body: ByteArray, val selectedBody: ByteArray?, val fullness: ByteArray)

    /** 달리기 원본: 피부·의상 자리 일곱(이름 → PNG), 체형 0 옆볼 덮개. */
    class RunOriginals(val skin: Map<String, ByteArray>, val fullness: ByteArray)

    fun build(
        version: Int,
        identity: Int,
        gender: AvatarGender,
        look: AvatarLookKey,
        variant: AvatarVariantAssets,
        legacyStandingBody: ByteArray,
        front: FrontOriginals?,
        run: RunOriginals?,
        generatedSkinColor: Int?
    ): AvatarToneRequest {
        val stage = look.shape.stage
        return AvatarToneRequest(
            version = version,
            identity = identity,
            gender = gender,
            amount = look.amount,
            outfit = look.outfit,
            shape = look.shape,
            face = front?.let { variant.front.getValue(FACE) },
            hair = front?.let { variant.front.getValue(HAIR) },
            frontFullness = front?.let { if (stage == 0) it.fullness else variant.frontFullness.getValue(stage) },
            runFullness = run?.let { if (stage == 0) it.fullness else variant.sideFullness.getValue(stage) },
            sideLayers = run?.let { variant.sideLayers },
            skinAssets = buildMap {
                front?.let {
                    put(BodySkinLayers.STANDING_BODY, it.body)
                    put(BodySkinLayers.STANDING_BODY_LEGACY, legacyStandingBody)
                    it.selectedBody?.let { bytes -> put(MaleSelectedBody.ASSET, bytes) }
                }
                run?.let { putAll(it.skin) }
            },
            generatedSkinColor = generatedSkinColor
        )
    }

    /** 서기 riv 내장 이미지에서 정면 원본을 고른다. 승인 자리가 없으면 IllegalStateException. */
    fun frontOf(embedded: Map<String, ByteArray>): FrontOriginals = FrontOriginals(
        body = checkNotNull(embedded[BodySkinLayers.STANDING_BODY]) { "embedded standing body missing" },
        selectedBody = embedded[MaleSelectedBody.ASSET],
        fullness = checkNotNull(embedded[FrontAvatarAssetLoader.FULLNESS]) { "embedded fullness overlay missing" }
    )

    /** 달리기 riv 내장 이미지에서 달리기 원본을 고른다. 승인 자리가 없으면 IllegalStateException. */
    fun runOf(embedded: Map<String, ByteArray>): RunOriginals {
        check(embedded.keys.containsAll(RunAvatarAssetLoader.SLOTS)) { "embedded run skin slots missing" }
        return RunOriginals(
            skin = embedded.filterKeys { it in RunAvatarAssetLoader.SLOTS },
            fullness = checkNotNull(embedded[RunAvatarAssetLoader.FULLNESS]) {
                "embedded side fullness overlay missing"
            }
        )
    }

    private const val FACE = "face_layer"
    private const val HAIR = "hair_layer"
}
