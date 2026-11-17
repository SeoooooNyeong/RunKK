package com.a207.runkk.app.ui.record

import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.record.Ranking

/**
 * 랭킹 얼굴 원 하나에 그릴 얼굴: 얼굴 키, 셀카 층, 읽는(받는) 중. 다른 러너 줄은 줄 모습의 얼굴 키와 받은 층([ofRow]), 「나」(내 순위 카드·
 * 목록의 내 줄)는 홈 아바타·마이와 같은 서버 아바타 얼굴([myRankingFace], PM 2026-11-15)이다. [isLoading]이면 기본 머리 대신 빈 자리다.
 */
data class RankingFace(
    val faceKey: String?,
    val faceLayers: List<FaceLayer> = emptyList(),
    val isLoading: Boolean = false
) {
    companion object {
        /** 내 얼굴을 읽는 중(기본 머리가 보였다가 셀카로 바뀌지 않게 빈 자리, D-846). */
        val LOADING = RankingFace(null, isLoading = true)

        /** 줄 모습의 얼굴: 받은 층([faces]), 받을 키인데 아직 없으면 받는 중([rankingFaceLoading]). 모습이 없으면 얼굴 없음. */
        internal fun ofRow(entry: Ranking.Entry, faces: Map<String, List<FaceLayer>>): RankingFace {
            val faceKey = entry.look?.faceKey
            return RankingFace(faceKey, faceKey?.let(faces::get).orEmpty(), rankingFaceLoading(faceKey, faces))
        }
    }
}

/**
 * 랭킹의 내 얼굴(PM 2026-11-15): 내 아바타(`AvatarRepository.avatar`, 홈 아바타·내 얼굴 톤과 같은 출처)의 얼굴 키와 그 셀카 층이다.
 * 셀카 키가 아니면(기본 캐릭터·얼굴 없음) 받을 층이 없고, 층을 받지 못하면 빈 층(기본 머리)이다. 아바타를 읽지 못하면 null(모름).
 */
internal suspend fun AvatarRepository.myRankingFace(): RankingFace? {
    val faceKey = (avatar().getOrNull() ?: return null).faceKey
    val layers = if (isFetchableFace(faceKey)) faceLayers(faceKey).getOrNull().orEmpty() else emptyList()
    return RankingFace(faceKey, layers)
}
