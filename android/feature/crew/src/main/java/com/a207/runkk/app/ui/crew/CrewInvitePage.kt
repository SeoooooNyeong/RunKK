package com.a207.runkk.app.ui.crew

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.MetaItem
import com.a207.runkk.app.core.designsystem.MetaRow
import com.a207.runkk.app.core.designsystem.RunkkBottomSheet
import com.a207.runkk.app.core.designsystem.RunkkButton
import com.a207.runkk.app.core.designsystem.RunkkButtonSize
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.domain.crew.CrewPreview
import com.a207.runkk.app.domain.crew.CrewVisibility
import java.time.Instant

/**
 * 초대 코드로 가입 바텀시트(피그마 F-03·F-03b·F-03c, contract-W4 4절): 제목·✕, 여섯 칸 코드, 단계별 아래쪽.
 * 입력 중은 "확인"(6자 전에는 회색), 맞는 크루가 없으면 칸을 오류로 칠하고 안내 문구, 찾으면 크루 미리보기 카드와 "가입하기".
 * 공개 크루 미리보기는 카드를 눌러 크루 상세로 간다(비공개는 크루 밖에서 상세를 못 보므로 ›를 숨긴다).
 */
@Composable
internal fun CrewInviteSheet(invite: InviteSheetState, actions: CrewFindActions) {
    RunkkBottomSheet(onDismissRequest = actions.onInviteClose, title = stringResource(R.string.crew_invite_title)) {
        InviteSheetContent(invite, actions)
    }
}

/** 시트 안쪽(스냅샷 시험은 시트 없이 이 내용을 그린다). */
@Composable
internal fun InviteSheetContent(invite: InviteSheetState, actions: CrewFindActions) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        val errorRes = when (invite.step) {
            InviteStep.NOT_FOUND -> R.string.crew_invite_not_found
            InviteStep.LIMITED -> R.string.crew_error_too_many_attempts
            else -> null
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InviteCodeField(invite.code, errorRes, actions.onInviteCode, actions.onInviteConfirm)
            if (errorRes != null) {
                Text(
                    stringResource(errorRes),
                    style = RunkkTextStyles.text(13, lineHeight = 16),
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
        val preview = invite.preview
        if (invite.step == InviteStep.FOUND && preview != null) {
            InvitePreviewCard(preview, onOpen = { actions.onInvitePreviewOpen(preview) })
            RunkkButton(
                stringResource(R.string.crew_invite_submit),
                onClick = actions.onInviteSubmit,
                size = RunkkButtonSize.Medium,
                isLoading = invite.isBusy,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            RunkkButton(
                stringResource(R.string.crew_invite_confirm),
                onClick = actions.onInviteConfirm,
                size = RunkkButtonSize.Medium,
                isEnabled = invite.isCodeComplete,
                isLoading = invite.isBusy,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 입력칸 하나를 여섯 칸(44x56, 간격 8, 모서리 12)으로 그린다. 보조 기술에는 "초대 코드" 입력칸 하나로 읽힌다. */
@Composable
private fun InviteCodeField(code: String, @StringRes errorRes: Int?, onCode: (String) -> Unit, onDone: () -> Unit) {
    val label = stringResource(R.string.crew_invite_field)
    val isError = errorRes != null
    val errorText = errorRes?.let { stringResource(it) }.orEmpty()
    val spoken = stringResource(R.string.crew_invite_field_state, code.toList().joinToString(" "), code.length)
    BasicTextField(
        value = code,
        onValueChange = onCode,
        singleLine = true,
        // 실제 입력 글자와 커서는 투명하게 두고 칸 그림으로 보인다.
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { if (code.length == INVITE_CODE_LENGTH) onDone() }),
        modifier = Modifier.semantics {
            contentDescription = label
            stateDescription = spoken
            if (isError) error(errorText)
        },
        decorationBox = { inner ->
            Box {
                inner()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(INVITE_CODE_LENGTH) { index -> CodeCell(code.getOrNull(index), isError) }
                }
            }
        }
    )
}

/** 코드 한 칸: 글자 22 Bold. 보통은 divider 테두리, 오류면 surfaceHigh 채움에 진한 테두리 1.5(피그마 F-03b). */
@Composable
private fun CodeCell(char: Char?, isError: Boolean) {
    val shape = RunkkShapes.Small
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 56.dp)
            .clip(shape)
            .background(if (isError) RunkkColors.SurfaceHigh else RunkkColors.Surface)
            .border(
                if (isError) 1.5.dp else 1.dp,
                if (isError) RunkkColors.TextSecondary else RunkkColors.Border,
                shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            char?.toString().orEmpty(),
            style = RunkkTextStyles.text(22, FontWeight.Bold, lineHeight = 26),
            color = RunkkColors.TextPrimary
        )
    }
}

/** 크루 미리보기 카드(피그마 F-03c): divider 테두리, 모서리 16, 안쪽 16. 이미지 74 + 크루명·개설일·인원, 소개, 태그, 오른쪽 ›. */
@Composable
private fun InvitePreviewCard(preview: CrewPreview, onOpen: () -> Unit) {
    val shape = RunkkShapes.Medium
    val canOpen = preview.visibility == CrewVisibility.PUBLIC
    val openLabel = stringResource(R.string.crew_find_open_detail, preview.name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, RunkkColors.Border, shape)
            .then(
                if (canOpen) {
                    Modifier.clickable(
                        onClickLabel = openLabel,
                        role = Role.Button,
                        onClick = onOpen
                    )
                } else {
                    Modifier
                }
            )
            .padding(16.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CrewProfileRow(preview.name, preview.logoUrl, preview.createdAt, preview.headcount)
            preview.intro?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = RunkkTextStyles.text(14, lineHeight = 20), color = RunkkColors.TextPrimary)
            }
            CrewTagPills(preview.tags)
        }
        if (canOpen) {
            Icon(
                RunkkIcons.ChevronRight,
                contentDescription = null,
                tint = RunkkColors.TextSecondary,
                modifier = Modifier.padding(start = 8.dp).size(22.dp)
            )
        }
    }
}

/**
 * 크루 프로필 줄(피그마 F-02·F-03c): 크루 이미지 74(모서리 24) + 14 + [크루명 19/23 Bold, 6, 개설일·인원 메타 줄]. 개설일이
 * 없으면(옛 응답) 인원만 보인다.
 */
@Composable
internal fun CrewProfileRow(name: String, logoUrl: String?, createdAt: Instant?, headcount: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        CrewImage(logoUrl, size = 74.dp, corner = 24.dp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                name,
                style = RunkkTextStyles.TopBarTitle,
                color = RunkkColors.TextPrimary
            )
            val created = createdAt?.let { createdDateLabel(it) }
            MetaRow(
                listOfNotNull(
                    created?.let {
                        MetaItem(
                            RunkkIcons.CalendarToday,
                            it,
                            spoken = stringResource(R.string.crew_detail_created_spoken, it)
                        )
                    },
                    MetaItem(RunkkIcons.Group, stringResource(R.string.crew_detail_headcount, headcount))
                )
            )
        }
    }
}
