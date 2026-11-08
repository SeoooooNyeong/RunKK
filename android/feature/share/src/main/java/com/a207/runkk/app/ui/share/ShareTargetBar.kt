package com.a207.runkk.app.ui.share

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles

/**
 * 공유 대상 막대(피그마 K-0 "공유 대상", K-2·K-3): 위 모서리가 둥근 표면 위에 기기에 저장, 인스타그램 스토리, 더 보기 세 칸.
 * 칸은 테두리 있는 원 아이콘과 이름이다. 기록 카드 공유와 Runimal 공유가 함께 쓴다. 공유 중에는 누를 수 없다.
 */
@Composable
fun ShareTargetBar(
    saveLabel: String,
    storyLabel: String,
    moreLabel: String,
    isSharing: Boolean,
    onSave: () -> Unit,
    onStory: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(BAR_SHAPE)
            .background(RunkkColors.Surface)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ShareTargetButton(RunkkIcons.Plus, saveLabel, isSharing, onSave)
        ShareTargetButton(RunkkIcons.Camera, storyLabel, isSharing, onStory)
        ShareTargetButton(RunkkIcons.Share, moreLabel, isSharing, onMore)
    }
}

@Composable
private fun ShareTargetButton(icon: ImageVector, label: String, isSharing: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RunkkShapes.Medium)
            .clickable(enabled = !isSharing, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(RunkkColors.Background)
                .border(1.dp, RunkkColors.Border, CircleShape)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = RunkkColors.TextPrimary, modifier = Modifier.size(24.dp))
        }
        Text(
            label,
            style = RunkkTextStyles.text(12, FontWeight.Medium),
            color = RunkkColors.TextPrimary,
            textAlign = TextAlign.Center
        )
    }
}

private val BAR_SHAPE = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
