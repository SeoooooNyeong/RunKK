package com.a207.runkk.app.ui.me

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.domain.account.ProviderType

/** 소셜 계정 연동(F9, FR-198): 내 닉네임과 연동 상태, 카카오·Google 연동 버튼, 거부 한 줄. 연동 회원이면 버튼을 뺀다. */
@Composable
internal fun AccountLinkContent(state: SettingsUiState, actions: SettingsActions) {
    RunkkCard(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        state.nickname?.let {
            Text(it, style = RunkkTextStyles.text(18, FontWeight.Black), color = RunkkColors.TextPrimary)
        }
        Text(
            stringResource(if (state.isGuest) R.string.me_link_status_guest else R.string.me_account_linked),
            style = RunkkTextStyles.Label,
            color = RunkkColors.TextSecondary
        )
    }
    if (!state.isGuest) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LinkButton(ProviderType.KAKAO, R.string.me_link_kakao, state, actions)
        LinkButton(ProviderType.GOOGLE, R.string.me_link_google, state, actions)
    }
    state.linkError?.let {
        Text(
            it.asString(),
            style = RunkkTextStyles.text(13, FontWeight.Bold),
            color = RunkkColors.Lavender,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

/** 카카오는 주 버튼, Google은 보조 버튼(로그인 화면과 같은 짝). */
@Composable
private fun LinkButton(
    provider: ProviderType,
    @StringRes label: Int,
    state: SettingsUiState,
    actions: SettingsActions
) {
    val isLinking = state.linkingProvider == provider
    val text = stringResource(if (isLinking) R.string.me_link_in_progress else label)
    if (provider == ProviderType.KAKAO) {
        RunkkPrimaryButton(
            text = text,
            onClick = { actions.onLink(provider) },
            isEnabled = state.linkingProvider == null,
            isLoading = isLinking,
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        RunkkSecondaryButton(
            text = text,
            onClick = { actions.onLink(provider) },
            isEnabled = state.linkingProvider == null,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
