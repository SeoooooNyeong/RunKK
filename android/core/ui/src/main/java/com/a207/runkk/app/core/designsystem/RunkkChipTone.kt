package com.a207.runkk.app.core.designsystem

/**
 * 고르기 칩([RunkkChoiceChip]) 색 한 벌. 고른 칩은 채움에 굵은 글자다.
 * - [Neutral] 기본(피그마 시간대 칩): 고름 respChip 채움, 안 고름 divider 테두리에 본문색
 * - [Lime] 랭킹 기간 칩: 고름 라임 채움, 안 고름 눌린 표면 채움에 보조색 Medium
 * - [Violet] 반응 탭·대결 거리: 고름 보라 채움 흰 글자, 안 고름 divider 테두리에 본문색 Medium
 * - [Ink] 상점 부위 칩: 고름 본문색 채움에 표면색 글자, 안 고름 surfaceHigh 채움에 보조색(모두 Bold)
 */
enum class RunkkChipTone { Neutral, Lime, Violet, Ink }
