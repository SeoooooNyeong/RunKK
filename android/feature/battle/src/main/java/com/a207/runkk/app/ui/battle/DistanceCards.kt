package com.a207.runkk.app.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.RunkkAvatar
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.runkkDashedBorder
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.AvatarStillSlot
import com.a207.runkk.app.core.navigation.AvatarStillSubject
import java.time.Duration
import java.time.LocalDate

// 기록런(셀프런) 화면이 쓰는 나 VS 상대 자리와 거리 카드. v0.14 배틀런 화면(BattleModeScreen, D-268)에서 옮겼다(배틀런 화면은 D-498로 지움).

/** 거리 카드 한 장(셀프런): 거리, 내 최고 기록과 날짜(없으면 "기록 없음"이고 고를 수 있다). */
data class DistanceCard(val distance: Distance, val best: Duration?, val recordedOn: LocalDate?)

/**
 * 나 VS 상대 자리(상대는 아직 모르므로 흐린 상대 그림). 나와 과거의 나는 머리·몸·의상이 다 보이는 내 전신 정지 그림이다
 * (SlotKey.AVATAR_STILL, S15P31A207-300). 꾸미기가 닫혀 있거나 칸이 비면 지금 서기 그림이다.
 */
@Composable
internal fun ModeVersus(opponent: String, opponentFigure: Figure = Figure.RIVAL) {
    val me = stringResource(R.string.battle_me)
    val description = stringResource(R.string.battle_versus_desc, me, opponent)
    Row(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        VersusSlot {
            AvatarStillSlot(MY_STILL, Modifier.size(VERSUS_FIGURE)) {
                RunkkAvatar(null, AvatarExpression.STAND, Modifier.size(VERSUS_FIGURE))
            }
        }
        Text(
            "VS",
            style = RunkkTextStyles.display(28),
            color = RunkkColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        VersusSlot(isFaded = true) {
            if (opponentFigure == Figure.PAST_SELF) {
                // 과거의 나도 내 전신 정지 그림(흐림은 칸이 준다)
                AvatarStillSlot(MY_STILL, Modifier.size(VERSUS_FIGURE)) {
                    OpponentFigure(opponentFigure, size = VERSUS_FIGURE)
                }
            } else {
                OpponentFigure(opponentFigure, size = VERSUS_FIGURE)
            }
        }
    }
}

@Composable
private fun VersusSlot(isFaded: Boolean = false, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 120.dp, height = 168.dp)
            .runkkDashedBorder()
            .alpha(if (isFaded) FADED else 1f),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** VS 칸 안 캐릭터 크기(칸 높이에 맞춰 크게). */
private val VERSUS_FIGURE = 148.dp

/** 거리 카드: 거리 칩, "내 최고 기록"과 기록(없으면 "기록 없음"), 날짜. 고르면 테두리가 짙어진다. */
@Composable
internal fun DistanceCardRow(
    card: DistanceCard,
    isSelected: Boolean,
    onClick: () -> Unit,
    isEnabled: Boolean = true,
    label: String = RunkkFormat.targetLabel(card.distance)
) {
    val best = card.best?.let(RunkkFormat::duration)
    val noRecord = stringResource(R.string.battle_no_record)
    val description = stringResource(
        R.string.battle_card_desc,
        label,
        best ?: noRecord,
        card.recordedOn?.let(::monthDay).orEmpty()
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.Large)
            .background(RunkkColors.Surface)
            .border(
                if (isSelected) 2.dp else 1.dp,
                if (isSelected) RunkkColors.TextSecondary else RunkkColors.Border,
                RunkkShapes.Large
            )
            .clickable(enabled = isEnabled, role = Role.RadioButton, onClick = onClick)
            .alpha(if (isEnabled) 1f else FADED)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                selected = isSelected
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = RunkkTextStyles.text(16, FontWeight.Black),
            color = RunkkColors.TextPrimary,
            modifier = Modifier
                .clip(RunkkShapes.ExtraLarge)
                .background(RunkkColors.Background)
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .width(52.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.battle_mode_best),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
            Spacer(Modifier.height(2.dp))
            Text(best ?: noRecord, style = RunkkTextStyles.text(16, FontWeight.Black), color = RunkkColors.TextPrimary)
        }
        card.recordedOn?.let { Text(monthDay(it), style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary) }
    }
}

internal const val FADED = 0.4f
