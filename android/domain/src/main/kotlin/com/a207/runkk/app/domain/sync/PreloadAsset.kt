package com.a207.runkk.app.domain.sync

/**
 * 시작 로딩 화면이 미리 받는 에셋 하나(S15P31A207-345). [key]는 캐시 열쇠(예: 내 셀카 머리 얼굴 키 "head:{회원ID}:{작업ID}"),
 * [files]는 받을 파일 수(진행 막대 칸 수, 예: 머리 메타 1 + 층 6).
 */
data class PreloadAsset(val key: String, val files: Int = 1)
