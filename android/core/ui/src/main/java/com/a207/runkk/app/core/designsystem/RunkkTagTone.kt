package com.a207.runkk.app.core.designsystem

/**
 * 꼬리표 알약([RunkkTagPill]) 색 한 벌.
 * - [Outline] 바탕 없이 divider 테두리에 본문색(크루 카드 태그, 기록 태그, 크루장 배지)
 * - [Neutral] surfaceHigh 채움(주간 배지, 주 요약 칩)
 * - [Lime] 라임 채움(러니멀 카드 강조 태그, 페이스 변화)
 * - [LimeTint]·[PurpleTint] 라임·보라 틴트(크루 주말·평일 태그)
 * - [Ink] 본문색 채움에 표면색 글자(사진 위 잠김·코너 칩)
 * - [Glass] 사진 위 반투명 표면(표면 0.92, 아바타 레벨 칩)
 */
enum class RunkkTagTone { Outline, Neutral, Lime, LimeTint, PurpleTint, Ink, Glass }
