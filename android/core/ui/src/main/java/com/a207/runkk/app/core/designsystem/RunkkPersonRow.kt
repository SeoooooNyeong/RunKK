package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 사람 한 줄(크루원·참석자·반응한 사람·랭킹): 앞 [avatar](기본 36 원 자리) + 이름(14, [isMe]면 Bold) + [badge](크루장 등) + 아래
 * [supporting](13 보조색) + 오른쪽 [trailing](거리·아이콘). 높이 [minHeight](기본 52) 이상, 사이 12. 아래 선은 [divider]다.
 * 줄은 한 번에 읽히고, [onClick]이 있으면 눌러 [onClickLabel] 행동을 한다.
 *
 * ```
 * RunkkPersonRow(name, isMe = member.isMe, badge = { RunkkTagPill(leader) }, trailing = { Text(km) }, onClick = onOpen)
 * ```
 */
@Composable
fun RunkkPersonRow(
    name: String,
    modifier: Modifier = Modifier,
    avatar: @Composable () -> Unit = { RunkkAvatarCircle(PERSON_AVATAR) },
    isMe: Boolean = false,
    badge: (@Composable () -> Unit)? = null,
    supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    divider: RunkkRowDivider = RunkkRowDivider.Solid,
    minHeight: Dp = 52.dp,
    nameStyle: TextStyle = RunkkTextStyles.text(14, if (isMe) FontWeight.Bold else FontWeight.Normal)
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
                    } else {
                        Modifier
                    }
                )
                .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            avatar()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        name,
                        style = nameStyle,
                        color = RunkkColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    badge?.invoke()
                }
                if (supporting != null) {
                    Text(supporting, style = RunkkTextStyles.Label, color = RunkkColors.TextSecondary, maxLines = 1)
                }
            }
            trailing?.invoke()
        }
        RowDivider(divider)
    }
}

/** 줄 아래 구분선(실선·점선, divider 1). */
@Composable
private fun RowDivider(divider: RunkkRowDivider) {
    when (divider) {
        RunkkRowDivider.None -> Unit

        RunkkRowDivider.Solid -> HorizontalDivider(color = RunkkColors.Border, thickness = 1.dp)

        RunkkRowDivider.Dashed -> {
            val color = RunkkColors.Border
            Box(
                Modifier
                    .testTag(DASHED_DIVIDER_TAG)
                    .fillMaxWidth()
                    .height(1.dp)
                    .drawBehind {
                        val y = size.height / 2
                        drawLine(
                            color = color,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = size.height,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                        )
                    }
            )
        }
    }
}

/** 점선 구분선의 시험 표지. */
internal const val DASHED_DIVIDER_TAG = "runkk-dashed-divider"

/** 사람 줄 기본 아바타 지름(크루원 목록 36). */
private val PERSON_AVATAR = 36.dp
