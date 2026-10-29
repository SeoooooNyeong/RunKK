package com.a207.runkk.app.ui.record

/** 기록 상세의 삭제 행동 묶음(D-280): "기록 삭제" 누름, 확인, 취소, 안내 문구를 보인 뒤. */
class RecordDeleteActions(
    val onDelete: () -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onMessageShown: () -> Unit = {}
)
