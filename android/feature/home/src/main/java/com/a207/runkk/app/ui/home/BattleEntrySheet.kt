package com.a207.runkk.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.LocalAvatarCharacter
import com.a207.runkk.app.core.designsystem.RunkkAvatar
import com.a207.runkk.app.core.designsystem.RunkkBottomSheet
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.TierBadge
import com.a207.runkk.app.core.designsystem.TierBadgeSize
import com.a207.runkk.app.core.designsystem.tierName
import com.a207.runkk.app.core.model.TierDisplay
import com.a207.runkk.app.core.navigation.AvatarStillSlot
import com.a207.runkk.app.core.navigation.AvatarStillSubject
import com.a207.runkk.app.domain.account.PlacementStatus

/**
 * 배틀런 진입 시트(FR-284, D-499, 피그마 61355:3402): 홈 배틀런 카드에서 홈 위로 연다(경로가 아니다). 디자인 시스템 시트에 손잡이를 달고
 * 닫기 ✕ 없이 제목만 둔다(덮개 45%, 표면 위쪽 둥근 28).
 * 랜덤 카드는 배치 중이면 칩 「배치고사 n/3」과 「3판을 뛰면 티어가 정해져요」, 확정 티어면 확정 배지와 「비슷한 페이스의 러너와 대결해요」,
 * 진행을 못 읽었으면 「랜덤 대결」만 보인다. 친구 카드는 「카톡으로 친구를 불러 함께」.
 */
@Composable
fun BattleEntrySheet(state: BattleEntrySheetState, onRandom: () -> Unit, onFriend: () -> Unit, onDismiss: () -> Unit) {
    RunkkBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.home_entry_title),
        hasDragHandle = true,
        hasCloseButton = false
    ) {
        BattleEntrySheetContent(state, onRandom, onFriend)
    }
}

/** 시트 안쪽 두 카드(사이 12). */
@Composable
internal fun BattleEntrySheetContent(
    state: BattleEntrySheetState,
    onRandom: () -> Unit,
    onFriend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RandomCard(state, onRandom)
        FriendCard(onFriend)
    }
}

@Composable
private fun RandomCard(state: BattleEntrySheetState, onClick: () -> Unit) {
    val placement = state.placement
    val title = stringResource(R.string.home_entry_random)
    val isPlacing = placement?.status == PlacementStatus.IN_PROGRESS
    val chip = if (isPlacing) {
        stringResource(
            R.string.home_entry_placement_chip,
            placement?.countedRounds ?: 0
        )
    } else {
        null
    }
    val confirmed = placement?.takeIf { !isPlacing }?.let { TierDisplay.ofSelf(it.tier, it.provisionalTier) }
    val caption = when {
        isPlacing -> stringResource(R.string.home_entry_random_placing)
        placement != null -> stringResource(R.string.home_entry_random_ranked)
        else -> null
    }
    val tierSpoken = when (confirmed) {
        is TierDisplay.Confirmed -> stringResource(
            R.string.home_entry_tier_spoken,
            tierName(confirmed.tier) ?: confirmed.tier
        )

        is TierDisplay.Provisional -> stringResource(
            R.string.home_entry_tier_spoken,
            tierName(confirmed.tier) ?: confirmed.tier
        )

        TierDisplay.Placing -> tierName(TierDisplay.UNRANKED)

        null -> null
    }
    EntryCard(
        spoken = listOfNotNull(title, chip, tierSpoken, caption).joinToString(", "),
        container = RunkkColors.Violet,
        onClick = onClick,
        art = {
            // 나(전신 정지 그림) VS 모르는 상대(물음표)
            Box(Modifier.size(width = ART_WIDTH, height = ART_HEIGHT)) {
                AvatarStillSlot(MY_STILL, Modifier.offset(x = 4.dp).size(width = 48.dp, height = ART_HEIGHT)) {}
                Box(Modifier.offset(x = 54.dp, y = 21.dp).size(48.dp)) { QuestionMark(48.dp) }
            }
        },
        chevron = RunkkColors.OnSecondary
    ) {
        chip?.let {
            Text(
                it,
                style = RunkkTextStyles.text(11, FontWeight.Bold),
                color = RunkkColors.Violet,
                modifier = Modifier.clip(
                    CircleShape
                ).background(RunkkColors.OnSecondary).padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        confirmed?.let { TierBadge(it, TierBadgeSize.SMALL) }
        Text(title, style = RunkkTextStyles.text(19, FontWeight.Bold), color = RunkkColors.OnSecondary)
        caption?.let { Text(it, style = RunkkTextStyles.Caption, color = RunkkColors.OnSecondary) }
    }
}

@Composable
private fun FriendCard(onClick: () -> Unit) {
    val title = stringResource(R.string.home_entry_friend)
    val caption = stringResource(R.string.home_entry_friend_caption)
    val me = LocalAvatarCharacter.current
    EntryCard(
        spoken = "$title, $caption",
        container = RunkkColors.LimeFill,
        onClick = onClick,
        art = {
            Box(Modifier.size(width = ART_WIDTH, height = ART_HEIGHT)) {
                val friend = if (me == AvatarCharacter.MALE) AvatarCharacter.FEMALE else AvatarCharacter.MALE
                // 2026-11-12 PM 요구: 대결 고르기 카드도 얼굴 대신 아바타 몸체(전신 정지 그림)
                AvatarStillSlot(MY_STILL, Modifier.offset(x = 14.dp).size(width = 48.dp, height = ART_HEIGHT)) {
                    RunkkAvatar(
                        faceKey = null,
                        expression = AvatarExpression.STAND,
                        modifier = Modifier.size(width = 48.dp, height = ART_HEIGHT)
                    )
                }
                AvatarStillSlot(
                    AvatarStillSubject(
                        isMine = false,
                        isFemale = friend == AvatarCharacter.FEMALE
                    ),
                    Modifier.offset(x = 56.dp).size(width = 48.dp, height = ART_HEIGHT)
                ) {
                    RunkkAvatar(
                        faceKey = null,
                        expression = AvatarExpression.STAND,
                        modifier = Modifier.size(width = 48.dp, height = ART_HEIGHT),
                        character = friend
                    )
                }
            }
        },
        chevron = RunkkColors.OnLimeFill
    ) {
        Text(title, style = RunkkTextStyles.text(19, FontWeight.Bold), color = RunkkColors.OnLimeFill)
        Text(caption, style = RunkkTextStyles.Caption, color = RunkkColors.OnLimeFill)
    }
}

/** 대결 카드 틀: r20, pl18 pr12 py10, 글(세로 4) + 그림 110×90 + 꺾쇠. 카드 전체가 버튼 하나로 읽힌다. */
@Composable
private fun EntryCard(
    spoken: String,
    container: Color,
    onClick: () -> Unit,
    art: @Composable () -> Unit,
    chevron: Color,
    text: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
            }
            .padding(start = 18.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { text() }
        art()
        Icon(RunkkIcons.ChevronRight, contentDescription = null, tint = chevron, modifier = Modifier.size(20.dp))
    }
}

/** 고스트 자리: 흰 점선 원 [size] 안 「?」(지름 64면 30 Bold). 랜덤 대결 카드에서 내 전신 그림 오른쪽에 둔다. */
@Composable
private fun QuestionMark(size: Dp) {
    val ring = RunkkColors.OnSecondary
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 2.dp.toPx()
            drawCircle(
                color = ring,
                radius = this.size.minDimension / 2 - stroke / 2,
                style = Stroke(
                    width = stroke,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                )
            )
        }
        Text(
            QUESTION,
            style = RunkkTextStyles.text((30 * size.value / 64).toInt(), FontWeight.Bold),
            color = RunkkColors.OnSecondary
        )
    }
}

private val MY_STILL = AvatarStillSubject(isMine = true, isLightSkin = true)

private const val QUESTION = "?"
private val ART_WIDTH = 110.dp
private val ART_HEIGHT = 90.dp
