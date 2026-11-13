package com.a207.runkk.app.ui.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkButtonSize
import com.a207.runkk.app.core.designsystem.RunkkChipSize
import com.a207.runkk.app.core.designsystem.RunkkChipTone
import com.a207.runkk.app.core.designsystem.RunkkChoiceChip
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkDialog
import com.a207.runkk.app.core.designsystem.RunkkDialogButtons
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.Shop

/** 상점(피그마 제안 A안 입어보기 무대 + 3열 격자, FR-160): 칸을 골라 위 무대에 입혀 보고, 아래 단추로 사거나(확인 H-5) 입는다. */
@Composable
fun ShopRoute(viewModel: ShopViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    ShopScreen(
        state = state,
        actions = ShopActions(
            onBack = navigator::back,
            onSlot = viewModel::onSlot,
            onPick = viewModel::onPick,
            onClearTryOn = viewModel::onClearTryOn,
            onBuyRequest = viewModel::onBuyRequest,
            onBuy = viewModel::onBuy,
            onEquip = viewModel::onEquip,
            onDismiss = viewModel::onDismiss,
            onRetry = viewModel::load,
            onMessageShown = viewModel::onMessageShown
        )
    )
}

/** 상점 사용자 행동: 부위, 칸 고르기(입어 보기), 벗기기, 구매 단추(확인 열기), 확인의 구매·취소, 입기. */
data class ShopActions(
    val onBack: () -> Unit = {},
    val onSlot: (ItemSlot) -> Unit = {},
    val onPick: (Shop.Item) -> Unit = {},
    val onClearTryOn: () -> Unit = {},
    val onBuyRequest: () -> Unit = {},
    val onBuy: () -> Unit = {},
    val onEquip: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onMessageShown: () -> Unit = {}
)

/**
 * 상점 A안(62338:1907): 상단 바(제목, 리워드 칩), 무대 카드 358×300(모서리 28, 오른쪽 위 벗기기), 부위 칩 4개, 3열 격자(칸 112: 입어 봄
 * 보라 테두리·체크, 입는 중 라임, 보유 칩, 잠김 흐림·조건), 아래 고정 단추(사기: 이름 + 코인 가격, 가진 것: 입기).
 */
@Composable
fun ShopScreen(state: ShopUiState, actions: ShopActions, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    AvatarMessageEffect(state.message, snackbar, actions.onMessageShown)
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(Modifier.fillMaxSize()) {
            RunkkTopBar(title = stringResource(R.string.avatar_shop_title), onBack = actions.onBack) {
                BalanceChip(state.shop?.balance ?: state.avatar?.balance)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 16.dp)
            ) {
                ShopStage(state, actions)
                Spacer(Modifier.height(16.dp))
                SlotChips(state.slot, actions.onSlot)
                Spacer(Modifier.height(12.dp))
                ShopContent(state, actions)
            }
            BottomAction(state, actions)
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
    state.confirming?.let { item ->
        BuyConfirmDialog(item, state.shop?.balance ?: 0, actions.onBuy, actions.onDismiss)
    }
}

/** 입어보기 무대(358×300): 지금 착용 위에 고른 칸을 입혀 본다. 오른쪽 위 벗기기는 입어 보는 칸이 있을 때만 누른다. */
@Composable
private fun ShopStage(state: ShopUiState, actions: ShopActions) {
    val avatar = state.avatar
    val figure = stringResource(R.string.avatar_shop_try_on_figure)
    AvatarLookStage(
        look = if (avatar == null) null else state.look,
        backgroundKey = avatar?.backgroundKey,
        faceKey = avatar?.renderKey,
        faceLayers = state.faceLayers,
        modifier = Modifier
            .fillMaxWidth()
            .height(STAGE_HEIGHT)
            .clip(RoundedCornerShape(STAGE_RADIUS))
            .background(RunkkColors.SurfaceHigh)
            .semantics {
                contentDescription = figure
                role = Role.Image
            }
    ) {
        GlassUndoButton(
            stringResource(R.string.avatar_shop_take_off_try),
            isEnabled = state.selected != null,
            onClick = actions.onClearTryOn,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
        )
    }
}

/**
 * 부위 칩(디자인 시스템 RunkkChoiceChip Ink, 높이 36 알약, 글자 14 Bold, 간격 6). 고른 칩은 textPrimary 바탕에 surface 글자다. 칩은 상의·하의·신발
 * 셋이다(모자 부위는 숨긴다, [임시:D-성-183]).
 */
@Composable
private fun SlotChips(slot: ItemSlot, onSlot: (ItemSlot) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        VISIBLE_ITEM_SLOTS.forEach { entry ->
            RunkkChoiceChip(
                label = stringResource(entry.decorateLabelRes),
                isSelected = entry == slot,
                onSelectedChange = { onSlot(entry) },
                tone = RunkkChipTone.Ink,
                size = RunkkChipSize.Medium,
                selectionRole = Role.Tab
            )
        }
    }
}

/** 구매 확인(피그마 H-5): 제목, 112 그림, 가운데 두 줄 "n P," / "구매 후 잔액 m P", 취소(흰)·구매(보라). */
@Composable
private fun BuyConfirmDialog(item: Shop.Item, balance: Int, onBuy: () -> Unit, onDismiss: () -> Unit) {
    RunkkDialog(
        title = stringResource(R.string.avatar_shop_confirm_title, item.name, objectParticle(item.name)),
        onDismissRequest = onDismiss
    ) {
        ItemPicture(item, Modifier.align(Alignment.CenterHorizontally).size(112.dp))
        Text(
            stringResource(
                R.string.avatar_shop_confirm_message,
                formatReward(item.price),
                formatReward(balance - item.price)
            ),
            style = RunkkTextStyles.Body,
            color = RunkkColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        RunkkDialogButtons(
            confirmLabel = stringResource(R.string.avatar_shop_buy),
            onConfirm = onBuy,
            dismissLabel = stringResource(R.string.avatar_shop_cancel),
            onDismiss = onDismiss
        )
    }
}

/** 상점 본문: 불러오는 중(무대 아래 빈 자리)·오류·빈 목록·3열 격자(간격 11·12). */
@Composable
private fun ShopContent(state: ShopUiState, actions: ShopActions) {
    val shop = state.shop
    when {
        shop == null && state.isLoading -> Spacer(Modifier.height(THUMB_ROW))

        shop == null -> RunkkErrorState(
            state.error?.asString() ?: stringResource(R.string.avatar_shop_load_failed),
            onRetry = actions.onRetry
        )

        state.itemsInSlot.isEmpty() -> RunkkEmptyState(stringResource(R.string.avatar_shop_empty))

        else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.itemsInSlot.chunked(COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(GRID_GAP)) {
                    row.forEach { item -> ShopCell(state, item, actions, Modifier.weight(1f)) }
                    repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ShopCell(state: ShopUiState, item: Shop.Item, actions: ShopActions, modifier: Modifier = Modifier) {
    val isLocked = item.isLocked && !item.isOwned
    val isEquipped = state.isEquipped(item)
    val isTrying = state.selected?.itemId == item.itemId
    val wearing = stringResource(R.string.avatar_item_wearing)
    val owned = stringResource(R.string.avatar_item_owned_chip)
    val ended = stringResource(R.string.avatar_shop_ended)
    val price = stringResource(R.string.avatar_shop_price_description, item.price)
    val trying = stringResource(R.string.avatar_shop_selected)
    val lockedText = stringResource(R.string.avatar_shop_locked)
    val status = when {
        isLocked -> listOfNotNull(lockedText, item.unlockLabel).joinToString(", ")
        isEquipped -> wearing
        item.isOwned -> owned
        !item.isOnSale -> ended
        else -> price
    }
    ItemCell(
        item,
        look = when {
            isLocked -> ItemCellLook.LOCKED
            isTrying -> ItemCellLook.TRYING
            isEquipped -> ItemCellLook.WEARING
            item.isOwned -> ItemCellLook.OWNED
            else -> ItemCellLook.PLAIN
        },
        onClick = { actions.onPick(item) },
        modifier = modifier,
        stateText = if (isTrying) "$status, $trying" else status,
        priceLine = when {
            isLocked -> null
            isEquipped -> ({ PriceNote(wearing) })
            item.isOwned -> ({ PriceNote(owned) })
            !item.isOnSale -> ({ PriceNote(ended) })
            else -> ({ PriceLine(item.price) })
        }
    )
}

/**
 * 아래 고정 단추(높이 52, 라임): 가지지 않은 칸이면 가방 + 이름 + 코인 가격(누르면 구매 확인), 가진 칸이면 「입기」(입는 중이면 끔).
 * 고른 칸이 없으면 자리를 비워 둔다.
 */
@Composable
private fun BottomAction(state: ShopUiState, actions: ShopActions) {
    val item = state.selected
    Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp).heightIn(min = 52.dp)) {
        when {
            item == null -> Unit

            item.isOwned -> AvatarPillButton(
                stringResource(
                    if (state.isEquipped(item)) R.string.avatar_item_wearing else R.string.avatar_item_put_on
                ),
                onClick = actions.onEquip,
                icon = painterResource(R.drawable.avatar_ic_hanger),
                isEnabled = !state.isEquipped(item) && !state.isEquipping,
                modifier = Modifier.fillMaxWidth()
            )

            else -> {
                val label = stringResource(R.string.avatar_shop_buy_description, item.name, item.price)
                AvatarPillButton(
                    item.name,
                    onClick = actions.onBuyRequest,
                    icon = painterResource(R.drawable.avatar_ic_bag),
                    isEnabled = !state.isBuying,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }
                ) {
                    Spacer(Modifier.size(2.dp))
                    Icon(
                        painterResource(R.drawable.avatar_ic_coin),
                        null,
                        Modifier.size(16.dp),
                        tint = RunkkColors.OnLimeFill
                    )
                    Text(
                        formatReward(item.price),
                        style = RunkkButtonSize.Large.textStyle,
                        color = RunkkColors.OnLimeFill
                    )
                }
            }
        }
    }
}

private const val COLUMNS = 3
private val STAGE_HEIGHT = 300.dp
private val STAGE_RADIUS = 28.dp
private val THUMB_ROW = 160.dp

private val PREVIEW_ITEMS = listOf(
    Shop.Item(5, ItemSlot.TOP, "코랄 집업 후드", 400, false, true, imageKey = "top_cherry_vest"),
    Shop.Item(1, ItemSlot.TOP, "민트 싱글렛", 300, isOwned = true, isOnSale = true, imageKey = "top_lime_singlet"),
    Shop.Item(3, ItemSlot.TOP, "크루 카라티", 300, false, true, imageKey = "top_crew_tee"),
    Shop.Item(4, ItemSlot.TOP, "겨울 후디", 700, false, true, imageKey = "top_winter_hoodie"),
    Shop.Item(2, ItemSlot.TOP, "새벽 바람막이", 500, false, true, imageKey = "top_dawn_windbreaker"),
    Shop.Item(
        6,
        ItemSlot.TOP,
        "완주 기념 맨투맨",
        300,
        false,
        true,
        imageKey = "top_finisher_tee",
        isLocked = true,
        unlockLabel = "하프 완주하면 열려요"
    )
)

@Preview(name = "H-4 상점 A안", widthDp = 390, heightDp = 844)
@Composable
private fun ShopPreview() {
    RunkkTheme(reduceMotion = true) {
        ShopScreen(
            ShopUiState(isLoading = false, shop = Shop(PREVIEW_ITEMS, 1_240), selected = PREVIEW_ITEMS[0]),
            ShopActions()
        )
    }
}
