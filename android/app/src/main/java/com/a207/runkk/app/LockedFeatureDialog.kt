package com.a207.runkk.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.a207.runkk.app.core.designsystem.RunkkActionDialog

/**
 * 잠긴 기능 안내(D-253, FR-223): 제목 "추후 추가 예정"과 "확인"만 있다. 잠긴 하단 탭을 누르거나 잠긴 경로로 들어오면
 * RunkkNavHost가 띄운다. 이동도 서버 요청도 하지 않는다. "확인"·바깥 누름·뒤로가기 모두 [onDismiss]다.
 */
@Composable
internal fun LockedFeatureDialog(onDismiss: () -> Unit) {
    RunkkActionDialog(
        title = stringResource(R.string.app_locked_feature_title),
        confirmLabel = stringResource(R.string.app_locked_feature_confirm),
        onConfirm = onDismiss,
        onDismissRequest = onDismiss
    )
}
