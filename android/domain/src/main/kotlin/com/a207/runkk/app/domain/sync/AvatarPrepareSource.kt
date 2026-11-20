package com.a207.runkk.app.domain.sync

import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.avatar.MyAvatarPreparation
import javax.inject.Inject

/**
 * 시작 에셋 미리 받기의 내 아바타 합성 출처(S15P31A207-345 2차): 홈 큰 아바타의 입력 모으기·서기 합성·Rive 첫 판 준비를 로딩 화면에서
 * 끝낸다([MyAvatarPreparation], 홈 칸과 같은 캐시).
 *
 * 9회차: 합성 결과는 기기에 남아(계정별 칸) 두 번째 실행부터는 다시 합성하지 않고 바로 끝난다. 앞당기기가 아니다: 시간 상한 없이 끝날
 * 때까지 기다리고, 끝나지 못하면(입력을 받지 못함, 합성 예외) 다시 받을 만한 실패라 자동으로 다시 한 뒤 실패 화면·「다시 시도」로 간다.
 * 로그아웃 상태(인증 실패)처럼 다시 해도 같은 실패만 건너뛴다.
 */
class AvatarPrepareSource @Inject constructor(private val preparation: MyAvatarPreparation) : AssetPreloadSource {
    override suspend fun publicAssets(): Outcome<List<PreloadAsset>> = Outcome.Success(emptyList())

    override suspend fun memberAssets(): Outcome<List<PreloadAsset>> = Outcome.Success(listOf(ASSET))

    override fun isCached(asset: PreloadAsset): Boolean = preparation.isPrepared()

    override suspend fun fetch(asset: PreloadAsset, onFile: () -> Unit): Outcome<Unit> = preparation.prepare(onFile)

    internal companion object {
        /** 입력 모으기·합성 1 + Rive 첫 판 1. */
        val ASSET = PreloadAsset("avatar:prepare", files = 2, stage = PreloadStage.MY_AVATAR_PREPARE)
    }
}
