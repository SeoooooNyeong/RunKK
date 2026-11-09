package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 상태 칩 색: 보라(새 소식), 라임(참여 중), 회색(곧 열림, 종료). */
enum class RunkkStatusChipTone { Violet, Lime, Muted }

/**
 * 작은 상태 칩(피그마 RunKK / 이벤트 / 상태 칩 62268:2820): 11sp 굵게, 모서리 6, 안쪽 8×3. [RunkkBadge](높이 28, 12sp)보다 작아 카드
 * 제목 위에 얹는다. 보라 바탕은 보라 글자색을 옅게 깐다(라이트 피그마 #E3DFF9 근사).
 */
@Composable
fun RunkkStatusChip(label: String, tone: RunkkStatusChipTone, modifier: Modifier = Modifier) {
    val (background, content) = when (tone) {
        RunkkStatusChipTone.Violet -> RunkkColors.Lavender.copy(alpha = VIOLET_CHIP_ALPHA) to RunkkColors.Lavender
        RunkkStatusChipTone.Lime -> RunkkColors.LimeFill to RunkkColors.OnLimeFill
        RunkkStatusChipTone.Muted -> RunkkColors.Border to RunkkColors.TextSecondary
    }
    Text(
        label,
        style = RunkkTextStyles.text(11, FontWeight.Bold),
        color = content,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/**
 * D-day 배지(피그마 RunKK / 이벤트 / D-day 배지 62268:2823): 본문 글자색 바탕에 표면색 글자, 11sp 굵게, 모서리 4, 안쪽 6×1.
 * 다크에서는 밝은 바탕에 어두운 글자가 된다. 문구("D-8", "D-DAY")는 부르는 쪽이 만든다.
 */
@Composable
fun RunkkDdayBadge(label: String, modifier: Modifier = Modifier) {
    Text(
        label,
        style = RunkkTextStyles.text(11, FontWeight.Bold),
        color = RunkkColors.Surface,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(RunkkColors.TextPrimary)
            .padding(horizontal = 6.dp, vertical = 1.dp)
    )
}

/**
 * 밑줄 탭(피그마 RunKK / 이벤트 / 탭 62268:2840): 같은 너비 칸, 15sp, 고른 탭은 굵은 본문색 글자와 3dp 밑줄, 나머지는 보통 굵기 보조
 * 글자와 1dp 구분선. 칸 높이는 터치 영역 48이고 밑줄은 칸 바닥에 붙는다(피그마 44). 보조 기술에는 탭으로 보인다.
 * 채운 탭 전환은 [RunkkSegmentedTabs], 칸이 많으면 [RunkkTabRow]를 쓴다.
 */
@Composable
fun RunkkUnderlineTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth().selectableGroup()) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(index) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    label,
                    style = RunkkTextStyles.text(15, if (isSelected) FontWeight.Bold else FontWeight.Medium),
                    color = if (isSelected) RunkkColors.TextPrimary else RunkkColors.TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (isSelected) 3.dp else 1.dp)
                        .background(if (isSelected) RunkkColors.TextPrimary else RunkkColors.Border)
                )
            }
        }
    }
}

/** 보라 칩 바탕: 보라 글자색의 16%. */
private const val VIOLET_CHIP_ALPHA = 0.16f
