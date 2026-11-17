package com.a207.runkk.app.ui.crew

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkBottomSheet
import com.a207.runkk.app.core.designsystem.RunkkButton
import com.a207.runkk.app.core.designsystem.RunkkButtonSize
import com.a207.runkk.app.core.designsystem.RunkkButtonStyle
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.navigation.crewInviteUrl
import com.a207.runkk.app.domain.crew.CrewDetail
import com.a207.runkk.app.ui.share.CrewInviteMessage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 초대 창(피그마 62431:1918 ⑧, [임시:FR-성-37], [임시:D-성-241]): 아래에서 올라오는 창, 위 손잡이, 제목 「크루원 초대」. 크루장과
 * 크루원이 같이 쓴다. 코드 칸(만료일, 코드, 「복사」), 선택 줄 셋(카카오톡으로 보내기, 초대 링크 복사, 다른 앱으로 공유), 회색 「닫기」다.
 * 코드 재발급 단추는 없다(서버가 만료된 코드를 읽을 때 새로 발급한다). [crewName]은 복사한 글의 이름표로 쓴다.
 */
@Composable
internal fun CrewInviteSheet(code: String, expiresAt: Instant?, crewName: String, actions: CrewHomeActions) {
    RunkkBottomSheet(
        onDismissRequest = actions.onInviteClose,
        title = stringResource(R.string.crew_invite_sheet_title),
        hasDragHandle = true,
        hasCloseButton = false
    ) {
        CrewInviteSheetContent(code, expiresAt, crewName, actions)
    }
}

/** 시트 안쪽(스냅샷 시험은 시트 없이 이 내용을 그린다). */
@Composable
internal fun CrewInviteSheetContent(code: String, expiresAt: Instant?, crewName: String, actions: CrewHomeActions) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        InviteCodeBox(code, expiresAt) {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(crewName, code)))
                actions.onInviteCopied()
            }
        }
        Column {
            InviteOption(
                RunkkIcons.ChatBubble,
                stringResource(R.string.crew_invite_kakao),
                stringResource(R.string.crew_invite_kakao_hint),
                actions.onInviteKakao
            )
            InviteOption(
                RunkkIcons.OpenInNew,
                stringResource(R.string.crew_invite_link_copy),
                stringResource(R.string.crew_invite_link_copy_hint)
            ) {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(crewName, crewInviteUrl(code))))
                    actions.onInviteLinkCopy()
                }
            }
            InviteOption(
                RunkkIcons.Share,
                stringResource(R.string.crew_invite_system_share),
                stringResource(R.string.crew_invite_system_share_hint),
                actions.onInviteSystemShare
            )
        }
        RunkkButton(
            stringResource(R.string.crew_invite_sheet_close),
            onClick = actions.onInviteClose,
            style = RunkkButtonStyle.Gray,
            size = RunkkButtonSize.Medium,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 코드 칸(surfaceHigh, 모서리 14): 윗줄 작은 글자 「초대 코드, 11월 21일까지」(만료일을 모르면 「초대 코드」), 큰 글자 코드(24 Bold, 자간 4),
 * 오른쪽 「복사」. 만료일을 보이는 것은 결정 U-13이다.
 */
@Composable
private fun InviteCodeBox(code: String, expiresAt: Instant?, onCopy: () -> Unit) {
    val label = expiresAt?.let { stringResource(R.string.crew_invite_code_until, inviteExpiryLabel(it)) }
        ?: stringResource(R.string.crew_info_invite)
    val spoken = stringResource(R.string.crew_manage_invite_code_description, code.toList().joinToString(" "))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RunkkColors.SurfaceHigh)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = RunkkTextStyles.text(12, lineHeight = 14), color = RunkkColors.TextSecondary)
            Text(
                code,
                style = RunkkTextStyles.text(24, FontWeight.Bold, letterSpacingEm = CODE_LETTER_SPACING),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.semantics { contentDescription = spoken }
            )
        }
        RunkkButton(
            stringResource(R.string.crew_manage_invite_copy),
            onClick = onCopy,
            style = RunkkButtonStyle.Outline,
            size = RunkkButtonSize.Small
        )
    }
}

/** 선택 줄(높이 64 이상): 왼쪽 44 원 아이콘, 제목 15 Bold와 설명 13, 오른쪽 ›. 줄 전체가 단추다. */
@Composable
private fun InviteOption(icon: ImageVector, title: String, hint: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(RunkkColors.SurfaceHigh).clearAndSetSemantics {},
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = RunkkColors.TextPrimary, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = RunkkTextStyles.text(15, FontWeight.Bold, lineHeight = 20),
                color = RunkkColors.TextPrimary
            )
            Text(hint, style = RunkkTextStyles.text(13, lineHeight = 16), color = RunkkColors.TextSecondary)
        }
        Icon(
            RunkkIcons.ChevronRight,
            contentDescription = null,
            tint = RunkkColors.TextSecondary,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** 초대 코드 자간(피그마 4 / 글자 24). */
private const val CODE_LETTER_SPACING = 4f / 24f

/** 만료일 "11월 21일"(초대 창과 초대 메시지의 유효 기간). */
internal fun inviteExpiryLabel(resources: Resources, at: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    at.atZone(zone).format(
        DateTimeFormatter.ofPattern(resources.getString(R.string.crew_invite_expiry_pattern), Locale.KOREAN)
    )

/** 화면에서 쓰는 [inviteExpiryLabel]. */
@Composable
private fun inviteExpiryLabel(at: Instant): String = inviteExpiryLabel(LocalContext.current.resources, at)

/**
 * 이 크루의 지금 초대 코드로 초대 메시지를 만든다([임시:D-성-237]: 링크는 앱이 초대 코드로 만든다). 초대 코드를 받지 못했으면 null.
 */
internal fun crewInviteMessage(
    resources: Resources,
    detail: CrewDetail?,
    zone: ZoneId = ZoneId.systemDefault()
): CrewInviteMessage? {
    val code = detail?.inviteCode ?: return null
    return CrewInviteMessage(
        crewName = detail.name,
        code = code,
        expiresOn = detail.inviteCodeExpiresAt?.let { inviteExpiryLabel(resources, it, zone) }.orEmpty(),
        webUrl = crewInviteUrl(code)
    )
}

/** 다른 앱으로 보낼 초대 글: 크루 이름과 초대 문장, 유효 기간(알 때만), 초대 링크. */
internal fun crewInviteShareText(resources: Resources, invite: CrewInviteMessage): String =
    if (invite.expiresOn.isBlank()) {
        resources.getString(R.string.crew_invite_share_text, invite.crewName, invite.webUrl)
    } else {
        resources.getString(R.string.crew_invite_share_text_until, invite.crewName, invite.expiresOn, invite.webUrl)
    }

/** 다른 앱으로 공유: Android 공유 화면(ACTION_SEND, 글)을 연다. 열 앱이 없으면 거짓. */
internal fun openCrewInviteShare(context: Context, invite: CrewInviteMessage): Boolean {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, crewInviteShareText(context.resources, invite))
    }
    val chooser = Intent.createChooser(send, context.getString(R.string.crew_invite_sheet_title))
    return try {
        context.startActivity(chooser)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
