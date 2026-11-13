package com.a207.runkk.app.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.LocalAvatarCharacter
import com.a207.runkk.app.core.designsystem.LocationConsentSheet
import com.a207.runkk.app.core.designsystem.RUNKK_BOTTOM_BAR_RISE
import com.a207.runkk.app.core.designsystem.RunkkAvatar
import com.a207.runkk.app.core.designsystem.RunkkAvatarCircle
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkElevation
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkRadioMark
import com.a207.runkk.app.core.designsystem.RunkkSectionHeader
import com.a207.runkk.app.core.designsystem.RunkkSectionHeaderSize
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.AvatarStillSlot
import com.a207.runkk.app.core.navigation.AvatarStillSubject
import java.time.LocalDate

/** 친구 대결(FR-286): 「카카오톡으로 초대하기」는 위치 권한을 확인한 뒤 초대 대기로 간다. */
@Composable
fun FriendBattleRoute(viewModel: FriendBattleViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val open = rememberBattleOpener()
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is FriendBattleEvent.Open -> open(event.key)
            }
        }
    }
    FriendBattleScreen(
        state = state,
        actions = FriendBattleActions(
            onFriend = viewModel::onFriendSelected,
            onDistanceMenu = viewModel::onDistanceMenuToggle,
            onDistance = viewModel::onDistanceSelected,
            onInvite = viewModel::onInviteTapped,
            onConsentAgree = viewModel::onConsentAgreed,
            onConsentClose = viewModel::onConsentClosed,
            onMessageShown = viewModel::onMessageShown
        )
    )
}

/**
 * 친구 대결(FR-286, 피그마 61312:14822 닫힘·61312:14940 펼침): 「친구 대결」, 라임 히어로(「누가 더 빠를지」「친구와 대결해 볼까요?」, 두 아바타),
 * 「누구랑 달릴까요?」「최근 함께 달린 친구」 라디오 목록(비면 「아직 함께 달린 친구가 없어요」), 거리 상자(누르면 1·3·5·10km 펼침),
 * 「카카오톡으로 초대하기」. 하단 탭이 보이고 뒤로 버튼은 없다(8절 5).
 */
@Composable
fun FriendBattleScreen(state: FriendBattleUiState, actions: FriendBattleActions, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it.asString(context))
            actions.onMessageShown()
        }
    }
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(Modifier.fillMaxSize()) {
            RunkkTopBar(title = stringResource(R.string.friend_battle_title))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FriendHero()
                RunkkSectionHeader(stringResource(R.string.friend_battle_who), size = RunkkSectionHeaderSize.Card) {
                    Text(
                        stringResource(R.string.friend_battle_recent),
                        style = RunkkTextStyles.text(12, FontWeight.Normal),
                        color = RunkkColors.TextSecondary
                    )
                }
                FriendList(state.friends, state.selectedFriendId, actions.onFriend)
                DistanceSelect(state.distance, state.isDistanceMenuOpen, actions.onDistanceMenu, actions.onDistance)
            }
            RunkkPrimaryButton(
                text = stringResource(R.string.friend_battle_invite),
                onClick = actions.onInvite,
                // 솟은 탭 원 위에 16을 둔다(D100).
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp + RUNKK_BOTTOM_BAR_RISE)
            )
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
    if (state.isConsentSheetVisible) {
        LocationConsentSheet(onAgree = actions.onConsentAgree, onClose = actions.onConsentClose)
    }
}

/** 라임 히어로(피그마 r24 px18 py16): 왼쪽 글 세 줄, 오른쪽 두 아바타(나와 반대 성별 친구, 진입 시트와 같다). */
@Composable
private fun FriendHero() {
    val me = LocalAvatarCharacter.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.ExtraLarge)
            .background(RunkkColors.LimeFill)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier.weight(1f).semantics(mergeDescendants = true) {
            },
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(R.string.friend_battle_hero_line1, R.string.friend_battle_hero_line2).forEach {
                Text(
                    stringResource(it),
                    style = RunkkTextStyles.text(17, FontWeight.Bold),
                    color = RunkkColors.OnLimeFill
                )
            }
            Text(
                stringResource(R.string.friend_battle_hero_caption),
                style = RunkkTextStyles.text(11, FontWeight.Normal),
                color = RunkkColors.OnLimeFill
            )
        }
        Box(Modifier.size(width = HERO_ART_WIDTH, height = HERO_ART_HEIGHT).clearAndSetSemantics {}) {
            val friend = if (me == AvatarCharacter.MALE) AvatarCharacter.FEMALE else AvatarCharacter.MALE
            AvatarStillSlot(
                AvatarStillSubject(isMine = false, isFemale = friend == AvatarCharacter.FEMALE),
                Modifier.size(width = 62.dp, height = HERO_ART_HEIGHT)
            ) {
                RunkkAvatar(
                    null,
                    AvatarExpression.STAND,
                    Modifier.size(width = 62.dp, height = HERO_ART_HEIGHT),
                    character = friend
                )
            }
            AvatarStillSlot(MY_STILL, Modifier.offset(x = 56.dp).size(width = 62.dp, height = HERO_ART_HEIGHT)) {
                RunkkAvatar(null, AvatarExpression.STAND, Modifier.size(width = 62.dp, height = HERO_ART_HEIGHT))
            }
        }
    }
}

/** 최근 함께 달린 친구 라디오 목록(표면 r16, 행 px14 py12, 둘째 행부터 위 구분선). 비면 안내 한 줄. */
@Composable
private fun FriendList(friends: List<RecentFriend>, selectedId: Long?, onSelect: (Long) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.Medium)
            .background(RunkkColors.Surface)
            .selectableGroup()
    ) {
        if (friends.isEmpty()) {
            Text(
                stringResource(R.string.friend_battle_empty),
                style = RunkkTextStyles.text(14, FontWeight.Normal),
                color = RunkkColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 20.dp)
            )
        }
        friends.forEachIndexed { index, friend ->
            if (index > 0) HorizontalDivider(color = RunkkColors.Border)
            FriendRow(friend, isSelected = friend.memberId == selectedId, onClick = { onSelect(friend.memberId) })
        }
    }
}

@Composable
private fun FriendRow(friend: RecentFriend, isSelected: Boolean, onClick: () -> Unit) {
    val ranOn = ranOnLabel(friend.lastRunOn)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RunkkAvatarCircle(40.dp, background = RunkkColors.RunimalTile)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                friend.name,
                style = RunkkTextStyles.text(14, FontWeight.Medium),
                color = RunkkColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(ranOn, style = RunkkTextStyles.text(11, FontWeight.Normal), color = RunkkColors.TextSecondary)
        }
        RunkkRadioMark(isSelected)
    }
}

@Composable
private fun ranOnLabel(date: LocalDate): String =
    stringResource(R.string.friend_battle_ran_on, date.monthValue, date.dayOfMonth)

/**
 * 거리 상자(피그마 표면 r12 pl16 pr10 py12, 높이 48): 「거리」와 값·꺾쇠. 닫힘은 1 BorderStrong 테두리(컨트롤 경계, PL2-A7), 펼침은 1.5
 * 글자색 테두리와 아래 목록(표면 r12, 행 44). 목록 글자는 앱 글꼴 14 Medium(8절 5).
 */
@Composable
private fun DistanceSelect(selected: Distance, isOpen: Boolean, onToggle: () -> Unit, onSelect: (Distance) -> Unit) {
    val shape = RunkkShapes.Small
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DistanceBox(selected, isOpen, onToggle)
        if (isOpen) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        RunkkElevation.High,
                        shape,
                        ambientColor = RunkkColors.Shadow,
                        spotColor = RunkkColors.Shadow
                    )
                    .clip(shape)
                    .background(RunkkColors.Surface)
                    .padding(vertical = 6.dp)
                    .selectableGroup()
            ) {
                MatchUiState.DISTANCES.forEach { distance ->
                    DistanceOption(distance, isSelected = distance == selected) { onSelect(distance) }
                }
            }
        }
    }
}

/** 거리 상자 머리(「거리」, 값, 꺾쇠). 펼침 목록 상자로 읽고 펼침·접힘 상태를 알린다. */
@Composable
private fun DistanceBox(selected: Distance, isOpen: Boolean, onToggle: () -> Unit) {
    val shape = RunkkShapes.Small
    val label = stringResource(R.string.friend_battle_distance)
    val value = RunkkFormat.targetLabel(selected)
    val state =
        stringResource(
            if (isOpen) R.string.friend_battle_distance_expanded else R.string.friend_battle_distance_collapsed
        )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(RunkkColors.Surface)
            .border(
                if (isOpen) 1.5.dp else 1.dp,
                if (isOpen) RunkkColors.TextPrimary else RunkkColors.BorderStrong,
                shape
            )
            .clickable(onClick = onToggle)
            .clearAndSetSemantics {
                contentDescription = "$label $value"
                stateDescription = state
                role = Role.DropdownList
            }
            .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = RunkkTextStyles.text(14, FontWeight.Medium),
            color = RunkkColors.TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(value, style = RunkkTextStyles.BodyStrong, color = RunkkColors.TextPrimary)
        Icon(
            if (isOpen) RunkkIcons.ChevronUp else RunkkIcons.ChevronDown,
            contentDescription = null,
            tint = RunkkColors.TextPrimary,
            modifier = Modifier.padding(start = 2.dp).size(22.dp)
        )
    }
}

/**
 * 거리 목록 한 행(라디오). 고른 행은 라임 바탕에 굵은 글자와 오른쪽 체크를 더해 색만으로 구분하지 않는다(PL2-A7: 라이트에서 라임 바탕과
 * 표면 대비가 1.37:1).
 */
@Composable
private fun DistanceOption(distance: Distance, isSelected: Boolean, onSelect: () -> Unit) {
    val content = if (isSelected) RunkkColors.OnLimeFill else RunkkColors.TextPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(OPTION_HEIGHT)
            .background(if (isSelected) RunkkColors.LimeFill else RunkkColors.Surface)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            RunkkFormat.targetLabel(distance),
            style = RunkkTextStyles.text(14, if (isSelected) FontWeight.Bold else FontWeight.Medium),
            color = content,
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            Icon(
                RunkkIcons.Check,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private val HERO_ART_WIDTH = 120.dp
private val HERO_ART_HEIGHT = 120.dp

/** 거리 목록 한 행 높이(피그마 44, 터치 48). */
private val OPTION_HEIGHT = 48.dp

@Preview(name = "C-9 친구 대결", widthDp = 390, heightDp = 844)
@Composable
private fun FriendBattlePreview() {
    RunkkTheme(reduceMotion = true) {
        FriendBattleScreen(
            FriendBattleUiState(
                friends = listOf(
                    RecentFriend(1, "바람처럼", LocalDate.of(2026, 10, 12)),
                    RecentFriend(2, "달빛", LocalDate.of(2026, 9, 30))
                ),
                selectedFriendId = 1
            ),
            FriendBattleActions()
        )
    }
}
