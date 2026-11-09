package com.a207.runkk.app.core.avatar

/** 러닝량 하한(가장 밝은 피부). */
const val MIN_RUNNING_AMOUNT = 0

/** 러닝량 상한(가장 어두운 피부). */
const val MAX_RUNNING_AMOUNT = 100

/**
 * 서버 피부색 톤 순번 → 렌더러 러닝량(0..100) 대응 자리. 러닝량이 낮으면 밝고 높으면 어둡다.
 *
 * 지금은 [toneIndex](0부터)를 [toneCount]개 톤에 고르게 나눈 값만 돌려준다(5톤이면 0, 25, 50, 75, 100). 실제 기록 연동
 * (FR-164 SkinTone 5톤의 순서와 FR-163 체형 갱신 규칙을 러닝량으로 옮기는 일)은 아바타 화면 작업(W8)이 정해 이 함수를 고친다.
 * PoC의 러닝량은 슬라이더 값이었다.
 */
fun skinToneAmountFrom(toneIndex: Int, toneCount: Int): Int {
    require(toneCount > 0 && toneIndex in 0 until toneCount) { "tone index $toneIndex out of $toneCount" }
    if (toneCount == 1) return MIN_RUNNING_AMOUNT
    return MIN_RUNNING_AMOUNT + (MAX_RUNNING_AMOUNT - MIN_RUNNING_AMOUNT) * toneIndex / (toneCount - 1)
}
