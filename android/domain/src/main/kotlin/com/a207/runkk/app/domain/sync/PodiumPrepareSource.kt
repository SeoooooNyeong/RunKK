package com.a207.runkk.app.domain.sync

import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.avatar.PodiumAvatarPreparation
import javax.inject.Inject

/**
 * 시작 에셋 미리 받기의 랭킹 시상대 아바타 출처(S15P31A207-345 9회차, [PodiumAvatarPreparation]). 시상대 1~3위의 머리와 전신 정지 그림을
 * 로딩 화면에서 올려 랭킹 탭 첫 판부터 보이게 한다. 단계는 랭킹(로딩 문구 「랭킹」)이다. 랭킹을 받지 못하면 다시 받을 만한 실패라 자동으로
 * 다시 한다.
 */
class PodiumPrepareSource @Inject constructor(private val preparation: PodiumAvatarPreparation) : AssetPreloadSource {
    override suspend fun publicAssets(): Outcome<List<PreloadAsset>> = Outcome.Success(emptyList())

    override suspend fun memberAssets(): Outcome<List<PreloadAsset>> = Outcome.Success(listOf(ASSET))

    override fun isCached(asset: PreloadAsset): Boolean = preparation.isPrepared()

    override suspend fun fetch(asset: PreloadAsset, onFile: () -> Unit): Outcome<Unit> = preparation.prepare(onFile)

    internal companion object {
        /** 시상대 자리 셋. */
        val ASSET =
            PreloadAsset("ranking:podium", files = PodiumAvatarPreparation.PODIUM_SIZE, stage = PreloadStage.RANKING)
    }
}
