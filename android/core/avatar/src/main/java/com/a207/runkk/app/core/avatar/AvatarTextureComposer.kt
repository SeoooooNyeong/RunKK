package com.a207.runkk.app.core.avatar

/**
 * 합성 스레드에서 도는 텍스처 계산(PoC MainActivity.startNextTone의 계산 부분). 순서는 디자인 규칙을 따른다:
 * 몸은 맨몸 바탕(assets/outfits/body)에 러닝량 톤을 준 뒤 의상 PNG(신발 → 하의 → 상의)를 원래 색으로 얹고(D-742·D-744),
 * 정면 얼굴은 피부 레이어에만 톤을 적용하며 눈은 원본, 헤어는 고유색 그대로 피부와 맞닿아 피부가 섞인 경계 픽셀만 섞인 몫만큼
 * 톤을 준다(DV-5). 측면 머리는 톤을 적용한 얼굴 위에 눈·헤어를 얹는다.
 *
 * 남자 서기의 male_selected_body(riv 내장 기본 옷 몸)는 더 그리지 않는다. 남자도 모든 묶음에서 standing_body 아틀라스 표면
 * (selected_top = maleTopSurface, 하의·신발 1)을 쓰기 때문이다(D-747, [RiveAvatarScene.topSurface]).
 */
internal object AvatarTextureComposer {
    private const val SIDE_HEAD = "side_head"

    fun compose(request: AvatarToneRequest, cache: AvatarToneCache): PreparedAvatarTone {
        val skinBases = mutableMapOf<String, ByteArray>()
        val skinRenders = renderSlots(request, cache, skinBases)
        request.sideLayers?.let { layers ->
            val key = SkinRenderKey(request.amount)
            skinRenders[SIDE_HEAD] = cache.skinRenders[SIDE_HEAD]?.takeIf { it.key == key }
                ?: AvatarToneCache.RenderedSkin(key, AvatarImages.composeSideHead(layers, request.amount))
        }
        val face = request.face?.let { original -> face(original, request, cache) }
        return PreparedAvatarTone(
            request = request,
            face = face,
            // 헤어 고유색은 그대로, 피부 얼굴과 맞닿은 피부 섞인 경계만 얼굴과 같은 러닝량 톤(DV-5).
            hair = face?.hair ?: request.hair,
            skinBases = skinBases,
            skinRenders = skinRenders,
            frontOverlay = request.frontFullness?.let { overlay(it, cache.frontOverlay, request) },
            runOverlay = request.runFullness?.let { overlay(it, cache.runOverlay, request) }
        )
    }

    /**
     * 의상 자리마다 합성한 몸 텍스처(riv 자리 이름 → 결과). 같은 슬롯·같은 키(달리기 몸통 세 자리)는 한 번만 합성해 같은 결과를
     * 나눠 쓴다. 캐시 키는 riv 자리 이름이다(정면·달리기 캐시 나눔 [SharedToneCache]과 같은 이름).
     */
    private fun renderSlots(
        request: AvatarToneRequest,
        cache: AvatarToneCache,
        skinBases: MutableMap<String, ByteArray>
    ): MutableMap<String, AvatarToneCache.RenderedSkin> {
        // 의상 자리가 있을 때만 목록이 필요하다(정면 얼굴만 합성하는 요청은 목록 없이 돈다).
        val catalog by lazy { checkNotNull(OutfitLibrary.catalog) { "outfit index missing" } }
        val bySlot = mutableMapOf<Pair<OutfitSlot, SkinRenderKey>, Pair<ByteArray?, AvatarToneCache.RenderedSkin>>()
        val renders = mutableMapOf<String, AvatarToneCache.RenderedSkin>()
        for (name in request.skinAssets.keys) {
            val slot = OutfitSlot.of(name) ?: continue
            val key = SkinRenderKey.of(name, request.amount, request.outfit, request.shape)
            val (base, rendered) = bySlot.getOrPut(slot to key) {
                cache.skinRenders[name]?.takeIf { it.key == key }?.let { null to it } ?: run {
                    val base = catalog.bodyFile(request.gender, slot)?.let { path ->
                        cache.skinBases[name] ?: bodyBase(path, request.generatedSkinColor)
                    }
                    base to renderSlot(catalog, slot, key, base, request)
                }
            }
            base?.let { skinBases[name] = it }
            renders[name] = rendered
        }
        return renders
    }

    private fun renderSlot(
        catalog: OutfitCatalog,
        slot: OutfitSlot,
        key: SkinRenderKey,
        body: ByteArray?,
        request: AvatarToneRequest
    ): AvatarToneCache.RenderedSkin {
        val layers = OutfitLayerComposer.layers(catalog, slot, request.gender, request.shape, request.outfit) {
            OutfitLibrary.exists(it)
        }
        val bytes = OutfitLayerComposer.compose(
            body,
            layers.map { it.part to checkNotNull(OutfitLibrary.read(it.path)) { "outfit layer missing: ${it.path}" } },
            request.amount,
            slot.width,
            slot.height,
            cut = catalog.cutFile(request.gender, slot)?.let(OutfitLibrary::read)
        ) { part ->
            catalog.bundle(part, OutfitCatalog.baseKey(part))
                ?.file(request.gender, slot, request.shape)
                ?.let(OutfitLibrary::read)
        }
        return AvatarToneCache.RenderedSkin(key, bytes)
    }

    private fun face(
        original: ByteArray,
        request: AvatarToneRequest,
        cache: AvatarToneCache
    ): AvatarToneCache.RenderedFace = cache.face?.takeIf { it.amount == request.amount }
        ?: AvatarToneCache.RenderedFace(
            request.amount,
            AvatarImages.faceForRunningAmount(original, request.amount),
            request.hair?.let { AvatarImages.hairForRunningAmount(it, original, request.amount) }
        )

    /** 맨몸 바탕. 생성 머리면 몸 피부를 얼굴 피부색에 맞춘다. */
    private fun bodyBase(path: String, generatedSkinColor: Int?): ByteArray {
        val original = checkNotNull(OutfitLibrary.read(path)) { "body base missing: $path" }
        return generatedSkinColor?.let { GeneratedAvatarLayers.matchBody(original, it) } ?: original
    }

    private fun overlay(
        original: ByteArray,
        cached: AvatarToneCache.RenderedOverlay?,
        request: AvatarToneRequest
    ): AvatarToneCache.RenderedOverlay = cached?.takeIf { it.shape == request.shape && it.amount == request.amount }
        ?: AvatarToneCache.RenderedOverlay(
            request.shape,
            request.amount,
            AvatarImages.faceForRunningAmount(original, request.amount)
        )
}
