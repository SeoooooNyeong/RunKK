package com.a207.runkk.app.ui.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RUNKK_AVATAR_FACE_TAG
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.RunkkUnderlineTabs
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.avatar.Avatar
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.Shop
import java.util.Locale

/**
 * 아바타 꾸미기(피그마 제안 A안 카드 무대 + 3열 격자). [startOnBackground]면 배경 탭으로 연다(알림 AVATAR_BACKGROUND, 내 러너 「배경」, FR-202).
 * 칸을 누르면 무대에 바로 입혀 보이고 「저장」이 서버에 보낸 뒤 닫는다. 배경 고르기는 누르면 바로 보낸다(거부는 바로 안내).
 */
@Composable
fun AvatarRoute(
    startOnBackground: Boolean = false,
    viewModel: AvatarViewModel = hiltViewModel(),
    backgroundViewModel: AvatarBackgroundViewModel = hiltViewModel()
) {
    // 상점에서 돌아오면 다시 읽는다.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }
    var isOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(startOnBackground) {
        if (startOnBackground && !isOpened) viewModel.onBackgroundTab()
        isOpened = true
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val background by backgroundViewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(state.isDone) { if (state.isDone) navigator.back() }
    AvatarScreen(
        state = state,
        background = background,
        actions = AvatarScreenActions(
            onBack = navigator::back,
            onShop = { navigator.navigate(Routes.SHOP) },
            onSlot = viewModel::onSlot,
            onBackgroundTab = viewModel::onBackgroundTab,
            onToggle = viewModel::onToggle,
            onUndo = viewModel::onUndo,
            onChooseBackground = backgroundViewModel::onChoose,
            onChooseDefaultBackground = backgroundViewModel::onChooseDefault,
            onRetry = {
                viewModel.load()
                backgroundViewModel.load()
            },
            onMessageShown = viewModel::onMessageShown,
            onBackgroundMessageShown = backgroundViewModel::onMessageShown,
            onSave = viewModel::onSave
        )
    )
}

/** 안내 문구가 생기면 스낵바로 한 번 보이고 지운다. */
@Composable
internal fun AvatarMessageEffect(message: UiText?, snackbar: SnackbarHostState, onMessageShown: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it.asString(context))
            onMessageShown()
        }
    }
}

/**
 * 꾸미기 A안(62337:1835): 상단 바(제목, 리워드 칩), 무대 카드 358×340(모서리 28, 오른쪽 위 되돌리기), 밑줄 탭 5개(상의·하의·신발·
 * 악세서리·배경) + 구분선, 3열 격자(칸 112, 마지막 칸 상점 바로가기), 아래 상점(회색)·저장(라임). 체형·피부색 칩과 빈 목록 문장은 두지
 * 않는다([임시:D-성-128]). 꾸미기 탭의 모자 부위는 「악세서리」다(사용자 결정 A2).
 */
@Composable
fun AvatarScreen(
    state: AvatarUiState,
    actions: AvatarScreenActions,
    modifier: Modifier = Modifier,
    background: AvatarBackgroundUiState = AvatarBackgroundUiState(isLoading = false)
) {
    val snackbar = remember { SnackbarHostState() }
    AvatarMessageEffect(state.message, snackbar, actions.onMessageShown)
    AvatarMessageEffect(background.message, snackbar, actions.onBackgroundMessageShown)
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(Modifier.fillMaxSize()) {
            RunkkTopBar(title = stringResource(R.string.avatar_decorate_title), onBack = actions.onBack) {
                BalanceChip(state.avatar?.balance)
            }
            val avatar = state.avatar
            if (avatar == null && !state.isLoading) {
                RunkkErrorState(
                    state.error?.asString() ?: stringResource(R.string.avatar_decorate_load_failed),
                    onRetry = actions.onRetry,
                    modifier = Modifier.weight(1f)
                )
            } else {
                DecorateBody(state, background, avatar, actions, Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AvatarPillButton(
                    stringResource(R.string.avatar_shop_title),
                    onClick = actions.onShop,
                    icon = painterResource(R.drawable.avatar_ic_store),
                    isPrimary = false,
                    modifier = Modifier.weight(1f)
                )
                AvatarPillButton(
                    stringResource(R.string.avatar_decorate_save),
                    onClick = actions.onSave,
                    // 바뀐 것이 없으면 비활성 단추(피그마 A 62337:1814, 실기기 DV3-7). 닫기는 뒤로.
                    isEnabled = avatar != null && state.isDirty && !state.isSaving,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
}

@Composable
private fun DecorateBody(
    state: AvatarUiState,
    background: AvatarBackgroundUiState,
    avatar: Avatar?,
    actions: AvatarScreenActions,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 16.dp)
    ) {
        // 배경을 읽었으면 지금 고른 배경으로 그린다(배경 탭에서 고르면 바로 바뀐다, FR-202).
        val backgroundKey = if (background.backgrounds != null) background.selectedKey else avatar?.backgroundKey
        AvatarLookStage(
            look = if (avatar == null) null else state.look,
            backgroundKey = backgroundKey,
            faceKey = avatar?.renderKey,
            faceLayers = state.faceLayers,
            modifier = Modifier
                .fillMaxWidth()
                .height(STAGE_HEIGHT)
                .clip(RoundedCornerShape(STAGE_RADIUS))
                .background(RunkkColors.SurfaceHigh)
                .semantics { role = Role.Image },
            description = avatar?.let { shown -> { isFallback -> figureDescription(shown, state, isFallback) } }
        ) {
            GlassUndoButton(
                stringResource(R.string.avatar_decorate_undo),
                isEnabled = state.isDirty,
                onClick = actions.onUndo,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        UnderlineTabs(state, actions)
        HorizontalDivider(color = RunkkColors.Border)
        Spacer(Modifier.height(14.dp))
        if (state.isBackgroundTab) {
            AvatarBackgroundTab(background, actions)
        } else {
            ItemGrid(state, actions)
        }
    }
}

/** 밑줄 탭(디자인 시스템 RunkkUnderlineTabs: 글자 15 Bold textPrimary / 15 Medium textSecondary, 밑줄 40×2, 아래 선은 따로 긋는다). */
@Composable
private fun UnderlineTabs(state: AvatarUiState, actions: AvatarScreenActions) {
    val labels = VISIBLE_ITEM_SLOTS.map { stringResource(it.decorateLabelRes) } +
        stringResource(R.string.avatar_tab_background)
    val backgroundIndex = VISIBLE_ITEM_SLOTS.size
    RunkkUnderlineTabs(
        options = labels,
        selectedIndex = if (state.isBackgroundTab) backgroundIndex else VISIBLE_ITEM_SLOTS.indexOf(state.slot),
        onSelect = { index ->
            if (index == backgroundIndex) actions.onBackgroundTab() else actions.onSlot(VISIBLE_ITEM_SLOTS[index])
        },
        indicatorWidth = 40.dp,
        indicatorHeight = 2.dp,
        hasBaseline = false
    )
}

/** 3열 격자(칸 112, 간격 11·10): 가진 아이템(입는 중 = 라임 테두리·체크), 잠긴 아이템(흐림·조건), 마지막 칸 「상점에서 더 보기」. */
@Composable
private fun ItemGrid(state: AvatarUiState, actions: AvatarScreenActions) {
    val wearing = stringResource(R.string.avatar_item_wearing)
    val locked = stringResource(R.string.avatar_shop_locked)
    val cells: List<Shop.Item?> = state.cellsInSlot + null
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cells.chunked(COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(GRID_GAP)) {
                row.forEach { item ->
                    val cellModifier = Modifier.weight(1f)
                    if (item == null) {
                        ShopMoreCell(actions.onShop, cellModifier)
                    } else {
                        val isLocked = item.isLocked && !item.isOwned
                        val isEquipped = state.isEquipped(item)
                        ItemCell(
                            item,
                            look = when {
                                isLocked -> ItemCellLook.LOCKED
                                isEquipped -> ItemCellLook.WEARING
                                else -> ItemCellLook.PLAIN
                            },
                            onClick = { actions.onToggle(item) },
                            modifier = cellModifier,
                            stateText = when {
                                isLocked -> listOfNotNull(locked, item.unlockLabel).joinToString(", ")
                                isEquipped -> wearing
                                else -> null
                            }
                        )
                    }
                }
                repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * 그림 하나로 읽어 주는 설명: 입어 보는 아이템(체형 이름은 넣지 않는다, [임시:D-성-128]). 얼굴 이름(기본 캐릭터·셀카)은 Rive를 그리지 못해
 * 기본 그림을 보일 때([isFallback])만 넣는다. Rive 아바타는 「내 아바타」다(실기기 DV3-10).
 */
@Composable
internal fun figureDescription(avatar: Avatar, state: AvatarUiState, isFallback: Boolean): String {
    val base = if (isFallback) {
        stringResource(R.string.avatar_figure_description, faceLabel(avatar))
    } else {
        stringResource(R.string.avatar_figure_rive)
    }
    val slotLabels = VISIBLE_ITEM_SLOTS.associateWith { stringResource(it.decorateLabelRes) }
    val equipped = stringResource(R.string.avatar_figure_equipped_format)
    return buildString {
        append(base)
        // 숨긴 부위(모자)는 그림에 없으므로 읽지 않는다([임시:D-성-183]).
        state.draft.filterKeys { it in slotLabels }.forEach { (slot, id) ->
            state.items.firstOrNull { it.itemId == id }?.let {
                append(String.format(equipped, slotLabels.getValue(slot), it.name))
            }
        }
    }
}

/** 얼굴 문구: 기본 캐릭터(faceKey "default:…", 어느 값이든)면 「기본 얼굴」([임시:D-성-187]), 셀카 얼굴("selfie:…", "head:…")이면 셀카, 아니면 없음. */
@Composable
private fun faceLabel(avatar: Avatar): String = when {
    avatar.isDefaultFace -> stringResource(R.string.avatar_face_default)
    avatar.isSelfieFace -> stringResource(R.string.avatar_face_selfie)
    else -> stringResource(R.string.avatar_face_none)
}

internal fun formatReward(value: Int?): String = value?.let { String.format(Locale.US, "%,d", it) } ?: "-"

/** 셀카 얼굴 그림(시험이 찾는 표지, 공용 아바타 얼굴 칸의 표지). */
internal const val FACE_TAG = RUNKK_AVATAR_FACE_TAG

private const val COLUMNS = 3
private val STAGE_HEIGHT = 340.dp
private val STAGE_RADIUS = 28.dp

@Preview(name = "H-3 꾸미기 A안", widthDp = 390, heightDp = 844)
@Composable
private fun AvatarPreview() {
    RunkkTheme(reduceMotion = true) {
        AvatarScreen(
            AvatarUiState(
                isLoading = false,
                avatar = Avatar(null, "FOX", "STAGE3", "EVENING", emptyMap(), 1_240, true, emptyMap())
            ),
            AvatarScreenActions()
        )
    }
}
