package com.a207.runkk.app.api.battle

import com.a207.runkk.app.api.core.ScreenCopyStore
import com.a207.runkk.app.api.core.SelfMember
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.battle.BattleHistory
import com.a207.runkk.app.domain.battle.BattleRepository
import com.a207.runkk.app.domain.sync.AssetPreloadSource
import com.a207.runkk.app.domain.sync.PreloadAsset
import com.a207.runkk.app.domain.sync.PreloadStage
import javax.inject.Inject

/**
 * 시작 에셋 미리 받기 출처: 셀럽런·친구(S15P31A207-345 2차, 포트 domain.sync.AssetPreloadSource).
 *
 * - 셀럽런(celeb:candidates): 셀럽런 탭 첫 화면(전체 거리) 셀럽 목록과 각 셀럽 기록, 파일 1. 셀럽 모습은 앱에 든 그림(셀럽 그림 키)이라
 *   받을 그림이 없다. 대결 생성(POST)은 하지 않는다. 사본은 [battles]가 내 계정 칸(내 최고 기록 포함)과 공용 칸에 남긴다.
 * - 친구(friends:recent): 친구 대결 화면의 최근 친구(친구 대결 이력 첫 쪽), 파일 1. 내 계정 칸.
 * - 적중: 내 계정 칸에 사본이 있으면 받지 않는다(화면이 열 때 새로 받는다).
 */
internal class BattlePreloadSource @Inject constructor(
    private val battles: BattleRepository,
    private val copies: ScreenCopyStore,
    private val self: SelfMember
) : AssetPreloadSource {
    override suspend fun publicAssets(): Outcome<List<PreloadAsset>> = Outcome.Success(emptyList())

    override suspend fun memberAssets(): Outcome<List<PreloadAsset>> {
        if (self.memberId() == null) return Outcome.Failure(AppError.Unauthorized)
        return Outcome.Success(listOf(CELEB_ASSET, FRIENDS_ASSET))
    }

    override fun isCached(asset: PreloadAsset): Boolean {
        val member = self.memberId() ?: return false
        return when (asset.key) {
            CELEB_ASSET.key -> copies.paramOf(member, BattleRepositoryImpl.CELEB) != null
            FRIENDS_ASSET.key -> copies.paramOf(member, BattleRepositoryImpl.historyKey(FRIENDS)) != null
            else -> false
        }
    }

    override suspend fun fetch(asset: PreloadAsset, onFile: () -> Unit): Outcome<Unit> {
        val result = when (asset.key) {
            CELEB_ASSET.key -> battles.celebrityCandidates(null)
            FRIENDS_ASSET.key -> battles.history(FRIENDS)
            else -> return Outcome.Success(Unit)
        }
        return when (result) {
            is Outcome.Success -> Outcome.Success(Unit).also { onFile() }
            is Outcome.Failure -> result
        }
    }

    private companion object {
        val CELEB_ASSET = PreloadAsset("celeb:candidates", 1, PreloadStage.CELEB)
        val FRIENDS_ASSET = PreloadAsset("friends:recent", 1, PreloadStage.OTHERS)
        val FRIENDS = BattleHistory.Type.FRIEND
    }
}
