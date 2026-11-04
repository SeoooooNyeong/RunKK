package com.a207.runkk.app.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.LocalAvatarCharacter
import com.a207.runkk.app.core.designsystem.LocalRunkkTierColors
import com.a207.runkk.app.core.designsystem.RunkkAvatar
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.TierBadge
import com.a207.runkk.app.core.designsystem.TierBadgeSize
import com.a207.runkk.app.core.designsystem.tierName
import com.a207.runkk.app.core.model.TierDisplay
import com.a207.runkk.app.domain.record.AggregationPeriod
import com.a207.runkk.app.domain.record.Ranking

/** 랭킹 행의 공개 티어 배지(FR-290, D-486): 공개 티어가 없으면(탈퇴·크루 줄·옛 서버) 배지를 그리지 않는다. */
internal fun rankingTier(entry: Ranking.Entry): TierDisplay? = TierDisplay.ofPublic(entry.publicTier)

/** 랭킹 한 줄 읽기: "2위 달리는곰, 41.20킬로미터, 나, 골드 티어". 배지가 없으면 티어를 읽지 않는다. */
@Composable
internal fun rankingSpoken(entry: Ranking.Entry, isMine: Boolean): String = listOfNotNull(
    stringResource(R.string.record_ranking_spoken_entry, entry.rank, entry.name),
    stringResource(R.string.record_distance_spoken, RunkkFormat.km(entry.distance)),
    if (isMine) stringResource(R.string.record_ranking_spoken_me) else null,
    rankingTier(entry)?.let { tierSpoken(it) }
).joinToString(", ")

@Composable
private fun tierSpoken(display: TierDisplay): String = when (display) {
    is TierDisplay.Confirmed -> stringResource(
        R.string.record_ranking_tier_spoken,
        tierName(display.tier) ?: display.tier
    )

    is TierDisplay.Provisional -> stringResource(
        R.string.record_ranking_tier_spoken,
        tierName(display.tier) ?: display.tier
    )

    TierDisplay.Placing -> stringResource(R.string.record_ranking_placing_spoken)
}

/**
 * 기간 칩 「주간」「월간」(높이 32, r8, px16, 13 Medium)과 오른쪽 「누적 유효 거리」. 고른 칩은 SelectedFill, 안 고른 칩은 티어 벌의
 * periodChip이다(피그마는 두 칩 바탕이 같아 고른 상태가 보이지 않는다, 8절 7).
 */
@Composable
internal fun RankingPeriodRow(
    periods: List<Pair<AggregationPeriod, String>>,
    selected: AggregationPeriod,
    onPeriod: (AggregationPeriod) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // 기간 칩만 라디오 묶음으로 읽는다(「2개 중 1번째」, PL1-A5)
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            periods.forEach { (value, label) ->
                val isSelected = value == selected
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .heightIn(min = 32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) RunkkColors.SelectedFill else LocalRunkkTierColors.current.periodChip
                        )
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onPeriod(value) })
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = RunkkTextStyles.text(13, FontWeight.Medium),
                        color = if (isSelected) RunkkColors.OnSelectedFill else RunkkColors.TextPrimary
                    )
                }
            }
        }
        Text(
            stringResource(R.string.record_ranking_metric),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * TOP3 시상대(FR-290, 피그마 61296:3779, 350×230): 왼쪽 2위·가운데 1위·오른쪽 3위, 서 있는 아바타, 이름 옆 아주 작은 공개 티어 배지와
 * 거리, 받침(높이 56·76·40, 고정 색)과 받침 숫자(행의 rank). 금 배지는 없다. 빈 자리는 숫자 없는 받침만 둔다.
 */
@Composable
internal fun RankingPodiumView(podium: RankingPodium, mine: Ranking.Entry?) {
    val tiers = LocalRunkkTierColors.current
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = PODIUM_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        PodiumColumn(podium.second, mine, 56.dp, 90.dp, tiers.podiumSecond, 22, Modifier.weight(1f))
        PodiumColumn(podium.first, mine, 76.dp, 112.dp, tiers.podiumFirst, 26, Modifier.weight(1f))
        PodiumColumn(podium.third, mine, 40.dp, 90.dp, tiers.podiumThird, 22, Modifier.weight(1f))
    }
}

@Composable
private fun PodiumColumn(
    entry: Ranking.Entry?,
    mine: Ranking.Entry?,
    blockHeight: Dp,
    avatarHeight: Dp,
    color: Color,
    numberSize: Int,
    modifier: Modifier = Modifier
) {
    val isMine = entry != null && entry == mine
    val spoken = entry?.let { rankingSpoken(it, isMine) }
    Column(
        modifier = modifier.clearAndSetSemantics { spoken?.let { contentDescription = it } },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        if (entry != null) {
            RunkkAvatar(
                faceKey = null,
                expression = AvatarExpression.STAND,
                modifier = Modifier.height(avatarHeight).aspectRatio(AVATAR_ASPECT),
                character = if (isMine) LocalAvatarCharacter.current else AvatarCharacter.of(entry.shownGender)
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    entry.name,
                    style = RunkkTextStyles.text(12, FontWeight.Medium),
                    color = RunkkColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                rankingTier(entry)?.let { TierBadge(it, TierBadgeSize.XSMALL) }
            }
            Text(
                stringResource(R.string.record_ranking_km, RunkkFormat.kmShort(entry.distance)),
                style = RunkkTextStyles.text(13, FontWeight.Bold),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(blockHeight)
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(color),
            contentAlignment = Alignment.TopCenter
        ) {
            entry?.let {
                Text(
                    "${it.rank}",
                    style = RunkkTextStyles.text(numberSize, FontWeight.Bold),
                    color = LocalRunkkTierColors.current.onPodium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/** 내 순위 줄(피그마): 배경, 2 RunimalTileBorder 테두리, r24, p14 — 순위·아바타 40·「나」·배지·거리. 순위가 바뀌면 알린다. */
@Composable
internal fun RankingMineRow(mine: Ranking.Entry) {
    val me = stringResource(R.string.record_ranking_me)
    val shown = mine.copy(name = me)
    val spoken = rankingSpoken(shown, isMine = true)
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(RunkkColors.Background)
            .border(2.dp, RunkkColors.RunimalTileBorder, shape)
            .clearAndSetSemantics {
                contentDescription = spoken
                liveRegion = LiveRegionMode.Polite
            }
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RankText(mine.rank, 16)
        RowAvatar(LocalAvatarCharacter.current, 40.dp)
        NameWithBadge(shown, 16, FontWeight.Bold, Modifier.weight(1f))
        DistanceText(mine, 16)
    }
}

/** 4위부터 목록 한 줄(최소 높이 49, 아래 1 RunimalTileBorder 선): 순위·아바타 36·닉네임 + 배지·거리. */
@Composable
internal fun RankingListRow(entry: Ranking.Entry, isMine: Boolean) {
    val spoken = rankingSpoken(entry, isMine)
    val line = RunkkColors.RunimalTileBorder
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 49.dp)
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RankText(entry.rank, 15)
        RowAvatar(if (isMine) LocalAvatarCharacter.current else AvatarCharacter.of(entry.shownGender), 36.dp)
        NameWithBadge(entry, 15, if (isMine) FontWeight.Bold else FontWeight.Normal, Modifier.weight(1f))
        DistanceText(entry, 15)
    }
}

@Composable
private fun RankText(rank: Int, size: Int) {
    Text(
        "$rank",
        style = RunkkTextStyles.text(size, FontWeight.Bold),
        color = RunkkColors.TextPrimary,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier.width(36.dp)
    )
}

@Composable
private fun RowAvatar(character: AvatarCharacter, size: Dp) {
    RunkkAvatar(
        faceKey = null,
        expression = AvatarExpression.FACE,
        modifier = Modifier.size(size).clip(CircleShape).background(RunkkColors.RunimalTile),
        character = character
    )
}

@Composable
private fun NameWithBadge(entry: Ranking.Entry, size: Int, weight: FontWeight, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            entry.name,
            style = RunkkTextStyles.text(size, weight),
            color = RunkkColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        rankingTier(entry)?.let { TierBadge(it, TierBadgeSize.XSMALL) }
    }
}

@Composable
private fun DistanceText(entry: Ranking.Entry, size: Int) {
    Text(
        stringResource(R.string.record_ranking_km, RunkkFormat.kmShort(entry.distance)),
        style = RunkkTextStyles.text(size, FontWeight.Bold),
        color = RunkkColors.TextPrimary
    )
}

private val PODIUM_HEIGHT = 230.dp

/** 서 있는 그림 칸 가로세로 비(홈 아바타와 같다). */
private const val AVATAR_ASPECT = 0.6f
