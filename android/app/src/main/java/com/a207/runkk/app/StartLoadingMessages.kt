package com.a207.runkk.app

import com.a207.runkk.app.core.designsystem.StartLoadingMessage
import com.a207.runkk.app.domain.sync.PreloadStage
import com.a207.runkk.app.domain.sync.PreloadState

/**
 * 시작 로딩 문구 고르기 규칙(S15P31A207-345 2차, 순수 함수). 보이는 시간(첫 등장 지연·최소 600ms 유지)은 StartLoadingMessageHold가 정한다.
 *
 * 1. 준비가 끝났거나(Ready) 실패 화면(Failed)이면 문구 없음.
 * 2. 자동 재시도를 기다리는 중이면 「연결이 느려 다시 시도하고 있어요」.
 * 3. 받을 목록을 아직 모르면(막대 없음) 「시작할 준비를 하고 있어요」.
 * 4. 90% 이상이면 「준비가 거의 끝났어요」.
 * 5. 그 밖에는 지금 받고 있는 일([PreloadState.Loading.active]) 가운데 남은 파일이 가장 많은 단계의 문구(같으면 내 아바타 → 랭킹 →
 *    셀럽런 → 그 밖 순서). 차례를 기다리는 단계는 남은 파일이 많아도 고르지 않는다(S15P31A207-345 9회차). 받는 일이 없으면 남은 파일
 *    기준, 그것도 없으면 「거의 끝났어요」.
 */
internal fun startLoadingMessageOf(state: PreloadState): StartLoadingMessage? {
    val loading = state as? PreloadState.Loading ?: return null
    if (loading.isRetrying) return StartLoadingMessage.RETRYING
    if (!loading.progress.hasBar) return StartLoadingMessage.START
    if (loading.progress.percent >= ALMOST_DONE_PERCENT) return StartLoadingMessage.ALMOST_DONE
    val counts = loading.active.takeIf { it.values.any { left -> left > 0 } } ?: loading.remaining
    val busiest = PreloadStage.entries
        .map { it to (counts[it] ?: 0) }
        .filter { (_, left) -> left > 0 }
        .maxByOrNull { (_, left) -> left }
        ?.first
        ?: return StartLoadingMessage.ALMOST_DONE
    return when (busiest) {
        PreloadStage.MY_AVATAR_FETCH -> StartLoadingMessage.MY_AVATAR_FETCH
        PreloadStage.MY_AVATAR_PREPARE -> StartLoadingMessage.MY_AVATAR_PREPARE
        PreloadStage.RANKING -> StartLoadingMessage.RANKING
        PreloadStage.CELEB -> StartLoadingMessage.CELEB
        PreloadStage.OTHERS -> StartLoadingMessage.RECORDS_CREW_FRIENDS
    }
}

/** 「준비가 거의 끝났어요」로 바꾸는 진행률(%). */
private const val ALMOST_DONE_PERCENT = 90
