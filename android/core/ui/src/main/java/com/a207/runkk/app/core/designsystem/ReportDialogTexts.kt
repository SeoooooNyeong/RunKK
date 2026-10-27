package com.a207.runkk.app.core.designsystem

import androidx.compose.runtime.Immutable

/**
 * 신고 시트 문구 묶음(D-186). 화면마다 다른 것은 제목, 대상 한 줄([subtitle], 없으면 null), 상세 칸 라벨이고, 나머지(사유 묶음 이름·
 * 보내기·취소)는 core.ui 공용 문구다. [reportDialogTexts]로 만든다.
 */
@Immutable
data class ReportDialogTexts(
    val title: String,
    val detailLabel: String,
    val reasonsLabel: String,
    val submitLabel: String,
    val dismissLabel: String,
    val subtitle: String? = null
)
