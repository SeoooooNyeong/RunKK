package com.a207.runkk.app.ui.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.AvatarStageBackdrop
import com.a207.runkk.app.core.designsystem.RunkkButtonStyle
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkDialog
import com.a207.runkk.app.core.designsystem.RunkkDialogButtons
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.domain.avatar.AvatarBackgrounds
import com.a207.runkk.app.domain.avatar.BackgroundUnlockSeenStore
import java.time.Instant

/**
 * 새로 연 배경(피그마 H-6). [items]는 해금 거리 내림차순(같으면 해금 일시 최신, 그다음 키)이고 첫 배경이 제목이다
 * (contract-W8.md 5.2 H-6).
 */
data class BackgroundUnlock(val items: List<AvatarBackgrounds.Item>) {
    init {
        require(items.isNotEmpty()) { "열린 배경이 없다" }
    }

    val title: AvatarBackgrounds.Item get() = items.first()

    val keys: Set<String> get() = items.map { it.key }.toSet()
}

/**
 * 기기 기록([seen])과 배경 목록으로 새로 연 배경을 고른다. 기록이 없으면(설치·로그인 직후) 지금 해금된 배경을 모두 본 것으로 채우고
 * 팝업을 띄우지 않는다. 기록이 있으면 기록에 없고 기록 시작([BackgroundUnlockSeenStore.Seen.since]) 뒤에 해금한 배경이 새 배경이다.
 * 돌려주는 기록은 그대로 저장한다(처음이면 새 기록, 아니면 받은 기록).
 */
internal fun findNewUnlocks(
    backgrounds: AvatarBackgrounds,
    seen: BackgroundUnlockSeenStore.Seen?,
    now: Instant
): Pair<BackgroundUnlock?, BackgroundUnlockSeenStore.Seen> {
    val unlocked = backgrounds.items.filter { it.isUnlocked }
    if (seen == null) return null to BackgroundUnlockSeenStore.Seen(unlocked.map { it.key }.toSet(), now)
    val fresh = unlocked
        .filter { it.key !in seen.keys && it.unlockedAt?.isAfter(seen.since) == true }
        .sortedWith(
            compareByDescending<AvatarBackgrounds.Item> { it.distance.meters }
                .thenByDescending { it.unlockedAt }
                .thenBy { it.key }
        )
    return fresh.takeIf { it.isNotEmpty() }?.let(::BackgroundUnlock) to seen
}

/**
 * 배경 해금 팝업(피그마 H-6): 세로 배경 미리보기, "[이름] 배경을 열었어요", 둘 이상이면 "이번 러닝으로 함께 연 배경" 상자, 닫기·배경 바꾸기.
 */
@Composable
internal fun BackgroundUnlockDialog(unlock: BackgroundUnlock, onClose: () -> Unit, onChange: () -> Unit) {
    RunkkDialog(title = stringResource(R.string.avatar_unlock_title, unlock.title.name), onDismissRequest = onClose) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(PREVIEW_RATIO)
                .clip(RunkkShapes.Medium)
                .background(RunkkColors.SurfaceHigh)
                .clearAndSetSemantics {}
        ) {
            AvatarStageBackdrop(unlock.title.key, Modifier.matchParentSize(), unlock.title.stage)
        }
        if (unlock.items.size > 1) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RunkkShapes.Medium)
                    .background(RunkkColors.SurfaceHigh)
                    .border(1.dp, RunkkColors.Border, RunkkShapes.Medium)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    stringResource(R.string.avatar_unlock_together),
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.TextSecondary
                )
                unlock.items.forEach {
                    Text(it.name, style = RunkkTextStyles.Body, color = RunkkColors.TextPrimary)
                }
            }
        }
        RunkkDialogButtons(
            confirmLabel = stringResource(R.string.avatar_unlock_change),
            onConfirm = onChange,
            dismissLabel = stringResource(R.string.avatar_unlock_close),
            onDismiss = onClose,
            confirmStyle = RunkkButtonStyle.Lime
        )
    }
}

/** 피그마 미리보기 280x500 비. */
private const val PREVIEW_RATIO = 280f / 500f
