package com.a207.runkk.app.core.designsystem

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * 위치 동의 안내(A-6, D-267, FR-031). 자유런·랜덤 매칭·친구 초대·참가 전에 위치 수집 동의가 없으면 부르는 화면이 띄운다.
 * "동의하고 시작"([onAgree])은 부르는 화면의 ViewModel이 LocationConsentUseCase.give로 이어 가고, "닫기"([onClose])는 시작하지 않는다.
 */
@Composable
fun LocationConsentSheet(onAgree: () -> Unit, onClose: () -> Unit) {
    RunkkActionDialog(
        title = stringResource(R.string.core_location_consent_title),
        confirmLabel = stringResource(R.string.core_location_consent_agree),
        onConfirm = onAgree,
        onDismissRequest = onClose,
        dismissLabel = stringResource(R.string.core_location_consent_close)
    ) {
        Text(
            stringResource(R.string.core_location_consent_message),
            style = RunkkTextStyles.Body,
            color = RunkkColors.TextSecondary
        )
    }
}
