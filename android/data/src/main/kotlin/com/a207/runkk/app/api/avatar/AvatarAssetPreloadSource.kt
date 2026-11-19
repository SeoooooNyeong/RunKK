package com.a207.runkk.app.api.avatar

import com.a207.runkk.app.api.core.ApiErrorMapper
import com.a207.runkk.app.api.core.FaceImageSource
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.map
import com.a207.runkk.app.domain.sync.AssetPreloadSource
import com.a207.runkk.app.domain.sync.PreloadAsset
import javax.inject.Inject
import timber.log.Timber

/**
 * 시작 에셋 미리 받기 출처(S15P31A207-345, 포트 domain.sync.AssetPreloadSource).
 *
 * - 공용 에셋: 없다. 로그인과 상관없는 그림(아바타 riv 4개, 옷 그림·index.json, 무대 배경 26종 낮·밤, 기본 머리)은 모두 앱에 들어 있다.
 * - 회원 에셋: 내 셀카 머리(얼굴 키 "head:…")의 메타와 1024 층 여섯 장(정면 셋·옆모습 셋). 홈 큰 아바타·얼굴 원·러닝·대결 카운트다운
 *   ·트랙이 같은 [FaceImageSource.selfieHead]와 내 얼굴 저장소(files/my-face)를 쓰므로, 여기서 받아 두면 화면은 다시 받지 않는다.
 *   얼굴 키는 기기에 남긴 프로필 키를 쓰고(네트워크 없음), 모르면(새로 설치·첫 로그인) GET /avatar로 묻는다. 옛 셀카 부위 얼굴
 *   ("selfie:…")과 기본 캐릭터는 받을 것이 없다.
 * - 적중: 내 얼굴 저장소에 지금 형식 판으로 머리 전체가 있으면 받지 않는다.
 * - 표식: Timber.d 한 줄("AssetPreload …").
 */
internal class AvatarAssetPreloadSource(
    private val api: AvatarApi,
    private val faces: FaceImageSource,
    private val log: (String) -> Unit
) : AssetPreloadSource {
    @Inject
    constructor(api: AvatarApi, faces: FaceImageSource) : this(api, faces, { line -> Timber.d(line) })

    override suspend fun publicAssets(): Outcome<List<PreloadAsset>> = Outcome.Success(emptyList())

    override suspend fun memberAssets(): Outcome<List<PreloadAsset>> {
        val known = faces.lastSelf()
        val faceKey = if (known != null) {
            known.faceKey
        } else {
            when (val avatar = ApiErrorMapper.call { api.avatar() }) {
                is Outcome.Success -> avatar.value.faceKey
                is Outcome.Failure -> return avatar
            }
        }
        val head = faceKey?.takeIf { it.startsWith(HEAD_PREFIX) } ?: return Outcome.Success(emptyList())
        return Outcome.Success(listOf(PreloadAsset(head, HEAD_FILES)))
    }

    override fun isCached(asset: PreloadAsset): Boolean = faces.hasSelfHead(asset.key)

    override suspend fun fetch(asset: PreloadAsset, onFile: () -> Unit): Outcome<Unit> =
        faces.selfieHead(asset.key, onFile).map { }

    override fun mark(line: String) = log(line)

    private companion object {
        const val HEAD_PREFIX = "head:"

        /** 머리 메타 1 + 1024 층 여섯 장. */
        const val HEAD_FILES = 7
    }
}
