package com.a207.runkk.app.ui.record

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.AvatarBackdrop
import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.LocalAvatarCharacter
import com.a207.runkk.app.core.designsystem.LocalRunkkColors
import com.a207.runkk.app.core.designsystem.LocalRunkkTierColors
import com.a207.runkk.app.core.designsystem.RunkkAvatar
import com.a207.runkk.app.core.designsystem.RunkkAvatarCircle
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkChipTone
import com.a207.runkk.app.core.designsystem.RunkkChoiceChip
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkPersonRow
import com.a207.runkk.app.core.designsystem.RunkkProgressBar
import com.a207.runkk.app.core.designsystem.RunkkRowDivider
import com.a207.runkk.app.core.designsystem.RunkkSegmentRow
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.TierBadge
import com.a207.runkk.app.core.designsystem.TierBadgeSize
import com.a207.runkk.app.core.designsystem.tierName
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.TierDisplay
import com.a207.runkk.app.core.navigation.AvatarStillSlot
import com.a207.runkk.app.core.navigation.AvatarStillSubject
import com.a207.runkk.app.domain.record.AggregationPeriod
import com.a207.runkk.app.domain.record.Ranking
import java.time.Duration
import java.time.LocalDate

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

/** 강조 글자색(마감 시간·라벨 아이콘): 라이트는 바이올렛, 다크는 어두운 바탕에서 읽히는 라벤더. */
@Composable
private fun rankingAccent(): Color = if (LocalRunkkColors.current.isDark) RunkkColors.Lavender else RunkkColors.Violet

/**
 * 상단 바 오른쪽 「개인 | 크루」 분절(랭킹 v3, 피그마 M3 Segmented button, 디자인 시스템 RunkkSegmentRow). 고른 칸은 라임 바탕에 체크이고, 크루가 닫혀 있으면
 * 크루 칸은 자물쇠와 함께 잠겨 누를 수 없다(「크루, 잠김」으로 읽는다).
 */
@Composable
internal fun RankingScopeToggle(scope: RankingScope, isCrewOpen: Boolean, onScope: (RankingScope) -> Unit) {
    val scopes = RankingScope.entries
    RunkkSegmentRow(
        options = scopes.map { stringResource(it.labelRes) },
        selectedIndex = scopes.indexOf(scope),
        onSelect = { onScope(scopes[it]) },
        modifier = Modifier.width(SEGMENT_WIDTH * scopes.size),
        lockedIndices = if (isCrewOpen) emptySet() else setOf(scopes.indexOf(RankingScope.CREW)),
        optionHeight = SEGMENT_HEIGHT
    )
}

/**
 * 기간 칩 「주간」「월간」(라임 고르기 칩, 높이 32 알약)과 오른쪽 집계 기간·마감까지 남은 시간(랭킹 v3). 고른 칩은 라임, 안 고른 칩은
 * 옅은 회색 바탕에 보조 글자색이다. 기간은 [window]가 있을 때만 보인다.
 */
@Composable
internal fun RankingPeriodRow(
    periods: List<Pair<AggregationPeriod, String>>,
    selected: AggregationPeriod,
    window: RankingWindow?,
    onPeriod: (AggregationPeriod) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // 기간 칩만 라디오 묶음으로 읽는다(「2개 중 1번째」, PL1-A5)
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            periods.forEach { (value, label) ->
                RunkkChoiceChip(
                    label = label,
                    isSelected = value == selected,
                    onSelectedChange = { onPeriod(value) },
                    tone = RunkkChipTone.Lime,
                    selectionRole = Role.RadioButton
                )
            }
        }
        Spacer(Modifier.weight(1f))
        window?.let { WindowText(it) }
    }
}

@Composable
private fun WindowText(window: RankingWindow) {
    val accent = rankingAccent()
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            stringResource(R.string.record_ranking_range, shortDate(window.start), shortDate(window.end)),
            style = RunkkTextStyles.text(11, FontWeight.Medium),
            color = RunkkColors.TextSecondary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(RunkkIcons.Clock, null, tint = accent, modifier = Modifier.size(12.dp))
            Text(deadlineText(window.remaining), style = RunkkTextStyles.text(11, FontWeight.Bold), color = accent)
        }
    }
}

/** "11.3"처럼 월.일(앞자리 0 없음). */
private fun shortDate(date: LocalDate): String = "${date.monthValue}.${date.dayOfMonth}"

/** 마감까지 남은 시간: 하루 이상이면 「n일 n시간」, 한 시간 이상이면 「n시간」, 그보다 적으면 「곧 마감」. */
@Composable
private fun deadlineText(remaining: Duration): String {
    val hours = remaining.toHours()
    return when {
        hours >= HOURS_IN_DAY -> stringResource(
            R.string.record_ranking_deadline_days,
            (hours / HOURS_IN_DAY).toInt(),
            (hours % HOURS_IN_DAY).toInt()
        )

        hours >= 1 -> stringResource(R.string.record_ranking_deadline_hours, hours.toInt())

        else -> stringResource(R.string.record_ranking_deadline_soon)
    }
}

/**
 * TOP3 시상대 카드(랭킹 v3, 피그마 RunKK / 랭킹 / TOP3 카드): 위는 홈과 같은 트랙 배경 위에 2·1·3위 아바타가 금·은·동 받침에
 * 서는 장면, 아래 띠는 이름·아주 작은 공개 티어 배지·거리(1위 칸만 금빛 바탕). 받침 숫자는 행의 rank 그대로라 동순위도 그대로
 * 보인다(FR-070). 빈 자리는 숫자 없는 받침만 둔다.
 */
@Composable
internal fun RankingTop3Card(podium: RankingPodium, mine: Ranking.Entry?, period: AggregationPeriod) {
    val tiers = LocalRunkkTierColors.current
    RunkkCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RunkkShapes.Dialog,
        contentPadding = PaddingValues(0.dp),
        borderColor = RunkkColors.Border,
        verticalArrangement = Arrangement.Top
    ) {
        Box(Modifier.fillMaxWidth().height(SCENE_HEIGHT)) {
            AvatarBackdrop(backgroundKey = null, modifier = Modifier.matchParentSize(), alignment = SCENE_ALIGNMENT)
            Sparkles(Modifier.matchParentSize())
            Top3Label(period, Modifier.align(Alignment.TopStart).padding(12.dp))
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                val second = tiers.onPodium.copy(alpha = SECOND_NUMBER_ALPHA)
                PodiumStand(
                    podium.second,
                    mine,
                    Stand(54.dp, 108.dp, tiers.podiumSecond, second, 28),
                    Modifier.weight(1f)
                )
                PodiumStand(
                    podium.first,
                    mine,
                    Stand(76.dp, 128.dp, tiers.podiumFirst, Color.White, 34),
                    Modifier.weight(1f)
                )
                PodiumStand(
                    podium.third,
                    mine,
                    Stand(42.dp, 100.dp, tiers.podiumThird, Color.White, 26),
                    Modifier.weight(1f)
                )
            }
        }
        // 큰 글자에서도 거리가 잘리지 않게 높이는 내용만큼 늘고, 세 칸(1위 강조 배경)은 가장 높은 칸에 맞춘다
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val gold = tiers.podiumFirst.copy(alpha = FIRST_HIGHLIGHT_ALPHA)
            PodiumInfo(podium.second, mine, highlight = null, Modifier.weight(1f).fillMaxHeight())
            PodiumInfo(podium.first, mine, highlight = gold, Modifier.weight(1f).fillMaxHeight())
            PodiumInfo(podium.third, mine, highlight = null, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** 받침 한 칸의 모양: 받침 높이, 아바타 키, 받침 색, 받침 숫자 색·크기. */
private class Stand(val block: Dp, val avatar: Dp, val color: Color, val number: Color, val numberSize: Int)

@Composable
private fun Top3Label(period: AggregationPeriod, modifier: Modifier = Modifier) {
    val label = stringResource(
        if (period ==
            AggregationPeriod.MONTHLY
        ) {
            R.string.record_ranking_top3_monthly
        } else {
            R.string.record_ranking_top3_weekly
        }
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(RunkkColors.Surface.copy(alpha = LABEL_ALPHA))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(RunkkIcons.Trophy, null, tint = rankingAccent(), modifier = Modifier.size(12.dp))
        Text(label, style = RunkkTextStyles.text(11, FontWeight.Bold), color = RunkkColors.TextPrimary)
    }
}

/**
 * 받침(위가 밝은 그라데이션)과 그 위에 서 있는 아바타·발밑 그림자. 아바타는 머리·몸·의상이 다 보이는 전신 정지 그림이다
 * (SlotKey.AVATAR_STILL, S15P31A207-300). 읽기는 아래 정보 띠가 맡는다.
 */
@Composable
private fun PodiumStand(entry: Ranking.Entry?, mine: Ranking.Entry?, stand: Stand, modifier: Modifier = Modifier) {
    Column(modifier.clearAndSetSemantics {}, horizontalAlignment = Alignment.CenterHorizontally) {
        if (entry != null) {
            val isMine = entry == mine
            Box(
                modifier = Modifier.drawBehind {
                    val w = SHADOW_WIDTH.toPx()
                    val h = SHADOW_HEIGHT.toPx()
                    drawOval(
                        Color.Black.copy(alpha = SHADOW_ALPHA),
                        topLeft = Offset((size.width - w) / 2, size.height - h),
                        size = Size(w, h)
                    )
                }
            ) {
                // 꾸미기가 열려 있으면 전신 정지 그림(나는 내 모습, 다른 러너는 표시 성별의 기본 모습), 아니면 지금 서기 그림
                val figure = Modifier.height(stand.avatar).aspectRatio(AVATAR_ASPECT)
                AvatarStillSlot(AvatarStillSubject(isMine, entry.shownGender?.let { it == Gender.FEMALE }), figure) {
                    RunkkAvatar(
                        faceKey = null,
                        expression = AvatarExpression.STAND,
                        modifier = figure,
                        character = if (isMine) LocalAvatarCharacter.current else AvatarCharacter.of(entry.shownGender)
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(stand.block)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(
                    Brush.verticalGradient(listOf(lerp(stand.color, Color.White, BLOCK_HIGHLIGHT), stand.color))
                ),
            contentAlignment = Alignment.Center
        ) {
            entry?.let {
                Text(
                    "${it.rank}",
                    style = RunkkTextStyles.text(stand.numberSize, FontWeight.Black),
                    color = stand.number
                )
            }
        }
    }
}

/** 시상대 아래 띠 한 칸: 이름, 아주 작은 공개 티어 배지, 거리. 한 칸을 한 문장으로 읽는다. */
@Composable
private fun PodiumInfo(entry: Ranking.Entry?, mine: Ranking.Entry?, highlight: Color?, modifier: Modifier = Modifier) {
    val spoken = entry?.let { rankingSpoken(it, it == mine) }
    Column(
        modifier = modifier
            .heightIn(min = INFO_HEIGHT)
            .then(if (highlight != null && entry != null) Modifier.background(highlight) else Modifier)
            .clearAndSetSemantics { spoken?.let { contentDescription = it } }
            .padding(top = 10.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (entry == null) return@Column
        Text(
            entry.name,
            style = RunkkTextStyles.text(13, FontWeight.Bold),
            color = RunkkColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        rankingTier(entry)?.let { TierBadge(it, TierBadgeSize.XSMALL) }
        KmText(entry.distance, if (highlight != null) 20 else 18, RunkkColors.TextPrimary, RunkkColors.TextSecondary)
    }
}

/** 장면 위 반짝임 세 개(장식). */
@Composable
private fun Sparkles(modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        sparkle(Offset(size.width * 0.30f, 66.dp.toPx()), 6.dp.toPx())
        sparkle(Offset(size.width * 0.73f, 48.dp.toPx()), 4.5.dp.toPx())
        sparkle(Offset(size.width * 0.62f, 22.dp.toPx()), 3.5.dp.toPx())
    }
}

private fun DrawScope.sparkle(center: Offset, radius: Float) {
    val inner = radius * SPARKLE_WAIST
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + inner, center.y - inner)
        lineTo(center.x + radius, center.y)
        lineTo(center.x + inner, center.y + inner)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - inner, center.y + inner)
        lineTo(center.x - radius, center.y)
        lineTo(center.x - inner, center.y - inner)
        close()
    }
    drawPath(path, Color.White)
}

/** 4위부터 목록 카드(둥근 24, 테두리): 행 사이에만 안쪽 구분선을 긋는다. */
@Composable
internal fun RankingListCard(rest: List<Ranking.Entry>, mine: Ranking.Entry?) {
    if (rest.isEmpty()) return
    RunkkCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RunkkShapes.ExtraLarge,
        contentPadding = PaddingValues(vertical = 2.dp),
        borderColor = RunkkColors.Border,
        verticalArrangement = Arrangement.Top
    ) {
        rest.forEachIndexed { index, entry ->
            RankingListRow(entry, isMine = entry == mine, showDivider = index < rest.lastIndex)
        }
    }
}

/** 4위부터 한 줄(최소 높이 62): 순위·아바타 40·닉네임 + 배지·거리. 순위 변동은 서버 값이 생길 때까지 그리지 않는다. */
@Composable
private fun RankingListRow(entry: Ranking.Entry, isMine: Boolean, showDivider: Boolean) {
    val spoken = rankingSpoken(entry, isMine)
    RunkkPersonRow(
        name = entry.name,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        avatar = {
            Text(
                "${entry.rank}",
                style = RunkkTextStyles.text(18, FontWeight.Bold),
                color = RunkkColors.TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.width(30.dp)
            )
            RunkkAvatarCircle(
                faceKey = null,
                size = 40.dp,
                character = if (isMine) LocalAvatarCharacter.current else AvatarCharacter.of(entry.shownGender),
                background = RunkkColors.RunimalTile
            )
        },
        badge = rankingTier(entry)?.let { tier -> { TierBadge(tier, TierBadgeSize.XSMALL) } },
        trailing = { KmText(entry.distance, 18, RunkkColors.TextPrimary, RunkkColors.TextSecondary) },
        divider = if (showDivider) RunkkRowDivider.Solid else RunkkRowDivider.None,
        minHeight = 62.dp,
        nameStyle = RunkkTextStyles.text(15, FontWeight.Bold)
    )
}

/**
 * 내 순위(하단 탭 바로 위에 고정, 랭킹 v3): 바이올렛 카드에 순위·얼굴·「나」와 배지·바로 위 순위까지 남은 거리 막대·내 거리.
 * 1위이거나 남은 거리를 모르면 막대 줄을 그리지 않는다. 범위·기간을 바꿔 순위가 바뀌면 알린다.
 */
@Composable
internal fun RankingMineCard(mine: Ranking.Entry, gapToNext: Distance?, rankAbove: Int, modifier: Modifier = Modifier) {
    val me = stringResource(R.string.record_ranking_me)
    val gap = gapToNext?.takeIf { mine.rank > 1 }
    val gapText = gap?.let { stringResource(R.string.record_ranking_gap, rankAbove, RunkkFormat.kmShort(it)) }
    val spoken = listOfNotNull(rankingSpoken(mine.copy(name = me), isMine = true), gapText).joinToString(", ")
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, shape, ambientColor = RunkkColors.Violet, spotColor = RunkkColors.Violet)
            .clip(shape)
            .background(RunkkColors.Violet)
            .clearAndSetSemantics {
                contentDescription = spoken
                liveRegion = LiveRegionMode.Polite
            }
            .padding(start = 16.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${mine.rank}",
            style = RunkkTextStyles.text(22, FontWeight.Black),
            color = RunkkColors.OnSecondary,
            maxLines = 1
        )
        RunkkAvatar(
            faceKey = null,
            expression = AvatarExpression.FACE,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(RunkkColors.RunimalTile)
                .border(2.dp, RunkkColors.OnSecondary, CircleShape),
            character = LocalAvatarCharacter.current
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(me, style = RunkkTextStyles.text(15, FontWeight.Bold), color = RunkkColors.OnSecondary)
                rankingTier(mine)?.let { TierBadge(it, TierBadgeSize.XSMALL) }
            }
            if (gap != null && gapText != null) {
                val total = mine.distance.meters.toFloat() + gap.meters.toFloat()
                val fraction = if (total > 0f) (mine.distance.meters.toFloat() / total).coerceIn(0f, 1f) else 0f
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RunkkProgressBar(
                        progress = fraction,
                        modifier = Modifier.weight(1f),
                        height = 6.dp,
                        color = RunkkColors.LimeFill,
                        trackColor = RunkkColors.OnSecondary.copy(alpha = TRACK_ALPHA)
                    )
                    Text(gapText, style = RunkkTextStyles.text(11, FontWeight.Medium), color = RunkkColors.OnSecondary)
                }
            }
        }
        KmText(mine.distance, 20, RunkkColors.OnSecondary, RunkkColors.OnSecondary.copy(alpha = UNIT_ALPHA))
    }
}

/** 거리 "62.4" + 작은 "km"(글자 바닥선을 맞춘다). */
@Composable
private fun KmText(distance: Distance, size: Int, color: Color, unitColor: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            RunkkFormat.kmShort(distance),
            style = RunkkTextStyles.text(size, FontWeight.Bold),
            color = color,
            modifier = Modifier.alignByBaseline()
        )
        Text(
            stringResource(R.string.record_ranking_unit_km),
            style = RunkkTextStyles.text(11, FontWeight.Medium),
            color = unitColor,
            modifier = Modifier.alignByBaseline()
        )
    }
}

private val SEGMENT_HEIGHT = 36.dp
private val SEGMENT_WIDTH = 84.dp

/** 시상대 장면 높이와 배경 사진에서 보일 자리(트랙 지평선이 받침 뒤에 오게 아래쪽으로). */
private val SCENE_HEIGHT = 216.dp
private val SCENE_ALIGNMENT = BiasAlignment(0f, 0.57f)
private val INFO_HEIGHT = 84.dp

/** 서 있는 그림 칸 가로세로 비(홈 아바타와 같다). */
private const val AVATAR_ASPECT = 0.6f
private val SHADOW_WIDTH = 56.dp
private val SHADOW_HEIGHT = 8.dp
private const val SHADOW_ALPHA = 0.18f

/** 받침 위쪽을 흰색 쪽으로 섞는 비율, 2위 받침 숫자의 진하기, 1위 칸 금빛 바탕 진하기. */
private const val BLOCK_HIGHLIGHT = 0.45f
private const val SECOND_NUMBER_ALPHA = 0.55f
private const val FIRST_HIGHLIGHT_ALPHA = 0.28f
private const val LABEL_ALPHA = 0.88f
private const val SPARKLE_WAIST = 0.22f
private const val TRACK_ALPHA = 0.22f
private const val UNIT_ALPHA = 0.8f
private const val HOURS_IN_DAY = 24L

/**
 * 내 바로 위 순위 번호(랭킹 v3 「n위까지」, PL5-A2). 같은 거리는 같은 순위이고 다음 순위는 사람 수만큼 건너뛰므로(FR-070, 1·2·2·4위)
 * 목록이 내 순위까지 이어지면 목록에서 나보다 앞선 가장 큰 순위를 쓴다. 목록이 내 순위 전에 끝나면 알 수 없어 [mine] 순위 − 1로 둔다.
 */
internal fun rankAbove(entries: List<Ranking.Entry>, mine: Ranking.Entry): Int {
    val coversMine = entries.any { it.rank >= mine.rank }
    val above = entries.filter { it.rank < mine.rank }.maxOfOrNull { it.rank }
    return if (coversMine && above != null) above else mine.rank - 1
}
