package com.a207.runkk.app.ui.crew

/** 탭 하나의 삭제 확인: 보일지와 확인·닫기 콜백(AN5-D-4). */
internal data class TabDeleteConfirm(val isShown: Boolean, val onConfirm: () -> Unit, val onDismiss: () -> Unit)
