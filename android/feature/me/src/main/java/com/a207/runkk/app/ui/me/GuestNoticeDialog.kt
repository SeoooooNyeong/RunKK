package com.a207.runkk.app.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.RunkkActionDialog
import com.a207.runkk.app.core.designsystem.RunkkAvatar
import com.a207.runkk.app.core.designsystem.RunkkAvatarCircle
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkTextStyles

/**
 * 게스트 시작 직후 안내(A-3, D-278, FR-199): 화면 가운데 대화상자(R2-08)에 자동으로 붙은 닉네임(보기만)과 자료 유실 고지.
 * "나중에"([onConfirm])면 홈으로, "지금 연동하기"([onLink])면 소셜 계정 연동으로. 바깥을 눌러도 "나중에"와 같다.
 * 닫힌 뒤 홈에서 첫 사용 가이드가 뜬다(D-277).
 * [nickname]을 아직 못 읽었으면 닉네임 칸을 뺀다.
 */
@Composable
internal fun GuestNoticeDialog(nickname: String?, onConfirm: () -> Unit, onLink: () -> Unit) {
    RunkkActionDialog(
        title = stringResource(R.string.me_guest_notice_title),
        confirmLabel = stringResource(R.string.me_guest_notice_link),
        onConfirm = onLink,
        onDismissRequest = onConfirm,
        dismissLabel = stringResource(R.string.me_guest_notice_later)
    ) {
        nickname?.let { NicknameBox(it) }
        Text(
            stringResource(R.string.me_guest_notice_message),
            style = RunkkTextStyles.Body,
            color = RunkkColors.TextSecondary
        )
    }
}

/** "내 닉네임" 칸: 왼쪽 둥근 얼굴 아바타와 자동 닉네임. */
@Composable
private fun NicknameBox(nickname: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(RunkkColors.Background)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RunkkAvatarCircle(36.dp, background = RunkkColors.SurfacePressed) {
            RunkkAvatar(faceKey = null, expression = AvatarExpression.FACE, modifier = Modifier.fillMaxSize())
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.me_guest_notice_nickname),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
            Text(nickname, style = RunkkTextStyles.CardTitle, color = RunkkColors.TextPrimary)
        }
    }
}
