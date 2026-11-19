package com.a207.runkk.app.domain.sync

import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.avatar.MyAvatarPreparation
import javax.inject.Inject

/**
 * 시작 에셋 미리 받기의 내 아바타 합성 출처(S15P31A207-345 2차): 홈 큰 아바타의 입력 모으기·서기 합성·Rive 첫 판 준비를 로딩 화면에서
 * 끝낸다([MyAvatarPreparation], 홈 칸과 같은 캐시). 프로세스 메모리 캐시라 앱을 켤 때마다 한다. 앞당기기라 한 번만 하고 실패해도
 * 건너뛴다(홈 칸이 스스로 다시 준비한다, 오프라인 시작을 막지 않는다).
 */
class AvatarPrepareSource @Inject constructor(private val preparation: MyAvatarPreparation) : AssetPreloadSource {
    override suspend fun publicAssets(): Outcome<List<PreloadAsset>> = Outcome.Success(emptyList())

    override suspend fun memberAssets(): Outcome<List<PreloadAsset>> = Outcome.Success(listOf(ASSET))

    override fun isCached(asset: PreloadAsset): Boolean = preparation.isPrepared()

    override suspend fun fetch(asset: PreloadAsset, onFile: () -> Unit): Outcome<Unit> = preparation.prepare(onFile)

    internal companion object {
        /** 입력 모으기 1 + 합성·첫 판 1. */
        val ASSET = PreloadAsset("avatar:prepare", files = 2, stage = PreloadStage.MY_AVATAR_PREPARE, isOptional = true)
    }
}
