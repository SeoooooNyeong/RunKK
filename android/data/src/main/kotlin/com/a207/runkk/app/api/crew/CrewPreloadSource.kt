package com.a207.runkk.app.api.crew

import com.a207.runkk.app.api.core.ScreenCopyStore
import com.a207.runkk.app.api.core.SelfMember
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.sync.AssetPreloadSource
import com.a207.runkk.app.domain.sync.PreloadAsset
import com.a207.runkk.app.domain.sync.PreloadStage
import javax.inject.Inject

/**
 * 시작 에셋 미리 받기 출처: 크루 탭 첫 화면(S15P31A207-345 2차, 포트 domain.sync.AssetPreloadSource).
 *
 * crew:mine — 내 크루 응답(GET /crews/me, 크루 정보 포함) 1 + 내 크루 로고 1. 응답은 [crews]가 내 계정 칸 사본에, 로고는 일반 API 디스크
 * 캐시(계정 칸)에 남아 크루 탭·크루 홈 머리가 다시 받지 않는다. 크루가 없거나 로고가 없으면 남은 칸은 다 받은 것으로 채운다. 레이스·크루런
 * 현황·대기실은 실시간이라 받지 않는다. 적중: 내 계정 칸에 사본이 있으면 받지 않는다.
 */
internal class CrewPreloadSource @Inject constructor(
    private val crews: CrewRepository,
    private val copies: ScreenCopyStore,
    private val self: SelfMember
) : AssetPreloadSource {
    override suspend fun publicAssets(): Outcome<List<PreloadAsset>> = Outcome.Success(emptyList())

    override suspend fun memberAssets(): Outcome<List<PreloadAsset>> {
        if (self.memberId() == null) return Outcome.Failure(AppError.Unauthorized)
        return Outcome.Success(listOf(ASSET))
    }

    override fun isCached(asset: PreloadAsset): Boolean {
        val member = self.memberId() ?: return false
        return copies.paramOf(member, CrewRepositoryImpl.MY_CREW) != null
    }

    override suspend fun fetch(asset: PreloadAsset, onFile: () -> Unit): Outcome<Unit> {
        val mine = when (val result = crews.myCrew()) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> return result
        }
        onFile()
        val logo = mine.crew?.logoUrl ?: return Outcome.Success(Unit)
        val read = crews.logo(logo)
        // 로고를 못 받아도(옛 주소·거부) 크루 탭은 기본 그림을 그린다. 연결이 끊긴 것만 다시 받는다.
        if (read is Outcome.Failure && (read.error is AppError.Network || read.error is AppError.Server)) return read
        onFile()
        return Outcome.Success(Unit)
    }

    private companion object {
        val ASSET = PreloadAsset("crew:mine", 2, PreloadStage.OTHERS)
    }
}
