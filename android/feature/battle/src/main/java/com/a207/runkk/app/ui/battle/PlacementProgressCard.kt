package com.a207.runkk.app.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.TierBadge
import com.a207.runkk.app.core.designsystem.TierBadgeSize
import com.a207.runkk.app.core.model.TierDisplay
import com.a207.runkk.app.domain.account.PlacementProgress
import com.a207.runkk.app.domain.battle.RandomBattleGate

/**
 * 판 전 진행 카드(FR-279, 피그마 61354:2971 「1판 전」·「2판 전」, 8절 8 「3판 전」 연장): 「배치고사」, 「n」「/3판」(n = 이번 판),
 * 세 칸 막대와 칸 이름(센 판 「n판 완료」, 이번 판 「n판 이번 판」, 남은 판 「n판」), 1판을 센 뒤에는 임시 티어 배지. 안내 줄은 숨긴다.
 */
@Composable
fun PlacementProgressBeforeRound(progress: PlacementProgress, modifier: Modifier = Modifier) {
    val current = (progress.countedRounds + 1).coerceAtMost(RandomBattleGate.PLACEMENT_RUNS)
    ProgressCard(
        progress = progress,
        big = current,
        tail = stringResource(R.string.placement_card_of, RandomBattleGate.PLACEMENT_RUNS),
        spoken = stringResource(R.string.placement_card_desc, current, RandomBattleGate.PLACEMENT_RUNS),
        current = current,
        modifier = modifier
    )
}

/** 판 뒤 진행 카드(FR-280, 피그마 61354:2950 「1판 완료」): 「n」「/3판 완료」(n = 센 판 수), 세 칸 막대, 임시 티어 배지. 이번 판 칸은 없다. */
@Composable
fun PlacementProgressAfterRound(progress: PlacementProgress, modifier: Modifier = Modifier) {
    ProgressCard(
        progress = progress,
        big = progress.countedRounds,
        tail = stringResource(R.string.placement_card_of_done, RandomBattleGate.PLACEMENT_RUNS),
        spoken = stringResource(
            R.string.placement_card_desc_done,
            progress.countedRounds,
            RandomBattleGate.PLACEMENT_RUNS
        ),
        current = null,
        modifier = modifier
    )
}

/**
 * 두 진행 카드의 틀(표면 r18 p16 사이 12, 그림자). 머리 숫자는 「배치고사 3판 중 n판째」 한 줄로 읽고, 칸 이름은 머리와 겹치므로 읽지
 * 않는다(PL2-A5: 「…1판 완료, 1판 완료, 2판, 3판」처럼 겹쳐 읽히지 않게).
 */
@Composable
private fun ProgressCard(
    progress: PlacementProgress,
    big: Int,
    tail: String,
    spoken: String,
    current: Int?,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    val provisional = TierDisplay.ofSelf(progress.tier, progress.provisionalTier) as? TierDisplay.Provisional
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, shape, ambientColor = BAR_SHADOW, spotColor = BAR_SHADOW)
            .clip(shape)
            .background(RunkkColors.Surface)
            .padding(16.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = spoken },
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    stringResource(R.string.placement_card_label),
                    style = RunkkTextStyles.text(12, FontWeight.Medium),
                    color = RunkkColors.TextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        big.toString(),
                        style = RunkkTextStyles.text(26, FontWeight.Bold),
                        color = RunkkColors.TextPrimary,
                        modifier = Modifier.alignByBaseline()
                    )
                    Text(
                        tail,
                        style = RunkkTextStyles.text(14, FontWeight.Medium),
                        color = RunkkColors.TextSecondary,
                        modifier = Modifier.alignByBaseline()
                    )
                }
            }
            provisional?.let { TierBadge(it, TierBadgeSize.SMALL) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.clearAndSetSemantics {}) {
            (1..RandomBattleGate.PLACEMENT_RUNS).forEach { round ->
                Step(round, isDone = round <= progress.countedRounds, isCurrent = round == current, Modifier.weight(1f))
            }
        }
    }
}

/** 칸 하나: 막대 높이 6(완료 보라, 이번 판 보라 35%, 남음 Border)과 이름 11(완료·이번 판 Medium, 남음 Regular). */
@Composable
private fun Step(round: Int, isDone: Boolean, isCurrent: Boolean, modifier: Modifier = Modifier) {
    val color = when {
        isDone -> RunkkColors.Violet
        isCurrent -> RunkkColors.Violet.copy(alpha = CURRENT_ALPHA)
        else -> RunkkColors.Border
    }
    val label = when {
        isDone -> stringResource(R.string.placement_step_done, round)
        isCurrent -> stringResource(R.string.placement_step_current, round)
        else -> stringResource(R.string.placement_step_left, round)
    }
    val isMarked = isDone || isCurrent
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(
            label,
            style = RunkkTextStyles.text(11, if (isMarked) FontWeight.Medium else FontWeight.Normal),
            color = if (isMarked) RunkkColors.TextPrimary else RunkkColors.TextSecondary
        )
    }
}

/** 이번 판 막대 불투명도(피그마 secondary 35%). */
private const val CURRENT_ALPHA = 0.35f
