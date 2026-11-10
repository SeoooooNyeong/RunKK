package com.a207.runkk.app.core.avatar

/**
 * 합성 스레드에서 도는 텍스처 계산(PoC MainActivity.startNextTone의 계산 부분). 순서는 디자인 규칙을 따른다:
 * 몸은 피부에 톤을 적용한 뒤 옷을 얹고, 정면 얼굴은 피부 레이어에만 톤을 적용하며 눈은 원본, 헤어는 고유색 그대로 피부와 맞닿아
 * 피부가 섞인 경계 픽셀만 섞인 몫만큼 톤을 준다(DV-성-5).
 * 측면 머리는 톤을 적용한 얼굴 위에 눈·헤어를 얹는다.
 */
internal object AvatarTextureComposer {
    private const val SIDE_HEAD = "side_head"

    fun compose(request: AvatarToneRequest, cache: AvatarToneCache): PreparedAvatarTone {
        val male = request.gender == AvatarGender.MALE
        // 두 내장 캐릭터와 생성 머리 모두 측면 레이어를 따로 가지므로 Rive에 들어 있던 완성 측면 머리에서 피부를 추정하지 않는다.
        val skinBases = request.skinAssets.filterKeys { it != SIDE_HEAD }.mapValues { (name, bytes) ->
            cache.skinBases[name] ?: BodySkinLayers.prepare(name, bytes).let { prepared ->
                request.generatedSkinColor?.let { GeneratedAvatarLayers.matchBody(prepared, it) } ?: prepared
            }
        }
        val skinRenders = skinBases.filterKeys { it != BodySkinLayers.STANDING_BODY_LEGACY }
            .mapValues { (name, layers) ->
                val key = SkinRenderKey.of(name, request.amount, request.outfit)
                cache.skinRenders[name]?.takeIf { it.key == key } ?: AvatarToneCache.RenderedSkin(
                    key,
                    if (name == BodySkinLayers.STANDING_BODY) {
                        BodySkinLayers.renderStanding(
                            layers,
                            skinBases.getValue(BodySkinLayers.STANDING_BODY_LEGACY),
                            request.amount,
                            request.outfit,
                            maleOutfit = male
                        )
                    } else {
                        BodySkinLayers.render(layers, request.amount, request.outfit, maleOutfit = male)
                    }
                )
            }.toMutableMap()
        request.sideLayers?.let { layers ->
            val key = SkinRenderKey(request.amount)
            skinRenders[SIDE_HEAD] = cache.skinRenders[SIDE_HEAD]?.takeIf { it.key == key }
                ?: AvatarToneCache.RenderedSkin(key, AvatarImages.composeSideHead(layers, request.amount))
        }
        val face = request.face?.let { original ->
            cache.face?.takeIf { it.amount == request.amount }
                ?: AvatarToneCache.RenderedFace(
                    request.amount,
                    AvatarImages.faceForRunningAmount(original, request.amount),
                    request.hair?.let { AvatarImages.hairForRunningAmount(it, original, request.amount) }
                )
        }
        return PreparedAvatarTone(
            request = request,
            face = face,
            // 헤어 고유색은 그대로, 피부 얼굴과 맞닿은 피부 섞인 경계만 얼굴과 같은 러닝량 톤(DV-성-5).
            hair = face?.hair ?: request.hair,
            skinBases = skinBases,
            skinRenders = skinRenders,
            frontOverlay = request.frontFullness?.let { overlay(it, cache.frontOverlay, request) },
            runOverlay = request.runFullness?.let { overlay(it, cache.runOverlay, request) }
        )
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
