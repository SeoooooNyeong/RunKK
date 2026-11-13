package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 꺾쇠 링크(마이 "러니멀 보기 ›", 크루 "크루런 제목 ›"): 글자([style] 기본 15 Bold, [color]) 뒤에 ChevronRight([chevronSize] 기본 16,
 * [chevronColor] 기본 글자색). 누르는 칸은 48dp이고 보조 기술에는 단추로 읽힌다([onClickLabel]이 있으면 그 행동으로 알린다).
 */
@Composable
fun RunkkChevronLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = CHEVRON_LINK_STYLE,
    color: Color = RunkkColors.TextPrimary,
    chevronSize: Dp = 16.dp,
    chevronColor: Color = color,
    onClickLabel: String? = null
) {
    Row(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = style, color = color)
        Icon(
            RunkkIcons.ChevronRight,
            contentDescription = null,
            tint = chevronColor,
            modifier = Modifier.size(chevronSize)
        )
    }
}

/** 꺾쇠 링크 기본 글자(15 Bold). */
private val CHEVRON_LINK_STYLE = RunkkTextStyles.text(15, FontWeight.Bold)
