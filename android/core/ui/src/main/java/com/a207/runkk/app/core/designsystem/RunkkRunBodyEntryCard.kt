package com.a207.runkk.app.core.designsystem

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 런바디 진입 카드(피그마 디자인 시스템 「RunKK / 런바디 진입 카드」 62613:3367): 기록 상세 지표 카드 아래에서 같은 러닝의 런바디 상세로
 * 간다. 왼쪽 원 아이콘(준비됨은 primary 채움, 분석 중은 background 회색), 제목 15 Bold, 한 줄 설명 12, 오른쪽 13 Medium 꼬리 글자.
 * 바탕 surface, 테두리 divider, 모서리 20, 안쪽 16·14. 문구는 부르는 화면이 정한다.
 */
@Composable
fun RunkkRunBodyEntryCard(
    title: String,
    description: String,
    trailing: String,
    isReady: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (isReady) RunkkColors.LimeFill else RunkkColors.Background),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                RunkkIcons.RunBody,
                contentDescription = null,
                tint = if (isReady) RunkkColors.OnLimeFill else RunkkColors.TextPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = RunkkTextStyles.text(15, FontWeight.Bold), color = RunkkColors.TextPrimary)
            Text(
                description,
                style = RunkkTextStyles.text(12),
                color = RunkkColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            trailing,
            style = RunkkTextStyles.text(13, FontWeight.Medium),
            color = if (isReady) RunkkColors.TextPrimary else RunkkColors.TextSecondary
        )
    }
}
