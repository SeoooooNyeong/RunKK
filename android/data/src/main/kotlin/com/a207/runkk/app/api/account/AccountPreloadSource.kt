package com.a207.runkk.app.api.account

import com.a207.runkk.app.api.core.ScreenCopyStore
import com.a207.runkk.app.api.core.SelfMember
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.sync.AssetPreloadSource
import com.a207.runkk.app.domain.sync.PreloadAsset
import com.a207.runkk.app.domain.sync.PreloadStage
import javax.inject.Inject

/**
 * 시작 에셋 미리 받기 출처: 홈 첫 화면의 내 요약(S15P31A207-345 2차, 포트 domain.sync.AssetPreloadSource).
 *
 * home:summary — 내 프로필 응답(GET /members/{내 ID}/profile) 1. [accounts]가 내 계정 칸 사본에 남기고 홈 머리(이름·티어·레벨)가 먼저
 * 그린다. 홈 대시보드는 기록 출처(records:month)의 오늘 대시보드와 같은 사본이다. 적중: 내 계정 칸에 사본이 있으면 받지 않는다.
 */
internal class AccountPreloadSource @Inject constructor(
    private val accounts: AccountRepository,
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
        return copies.paramOf(member, AccountRepositoryImpl.MY_PROFILE) != null
    }

    override suspend fun fetch(asset: PreloadAsset, onFile: () -> Unit): Outcome<Unit> =
        when (val result = accounts.mySummary()) {
            is Outcome.Success -> Outcome.Success(Unit).also { onFile() }
            is Outcome.Failure -> result
        }

    private companion object {
        val ASSET = PreloadAsset("home:summary", 1, PreloadStage.OTHERS)
    }
}
