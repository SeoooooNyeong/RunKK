package com.a207.runkk.app.ui.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkButton
import com.a207.runkk.app.core.designsystem.RunkkButtonSize
import com.a207.runkk.app.core.designsystem.RunkkButtonStyle
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkDialog
import com.a207.runkk.app.core.designsystem.RunkkDialogButtons
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.Shop

/** 상점(피그마 H-4, FR-160): 부위 칩, 아이템 2열, 칸을 골라 아래 구매 바에서 산다. 구매는 확인(H-5)을 거친다. */
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
            onBuyRequest = viewModel::onBuyRequest,
            onBuy = viewModel::onBuy,
            onDismiss = viewModel::onDismiss,
            onRetry = viewModel::load,
            onMessageShown = viewModel::onMessageShown
        )
    )
}

/** 상점 사용자 행동: 부위, 칸 고르기, 구매 바의 사기(확인 열기), 확인의 구매·취소. */
data class ShopActions(
    val onBack: () -> Unit = {},
    val onSlot: (ItemSlot?) -> Unit = {},
    val onPick: (Shop.Item) -> Unit = {},
    val onBuyRequest: () -> Unit = {},
    val onBuy: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onMessageShown: () -> Unit = {}
)

@Composable
fun ShopScreen(state: ShopUiState, actions: ShopActions, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    AvatarMessageEffect(state.message, snackbar, actions.onMessageShown)
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(Modifier.fillMaxSize()) {
            RunkkTopBar(
                title = stringResource(R.string.avatar_shop_title),
                onBack = actions.onBack,
                containerColor = RunkkColors.Surface,
                hasDivider = true
            ) {
                state.shop?.let { shop ->
                    val description = stringResource(R.string.avatar_shop_balance_description, shop.balance)
                    BalancePill(formatReward(shop.balance), Modifier.semantics { contentDescription = description })
                }
            }
            SlotChips(state.slot, actions.onSlot)
            ShopContent(state, actions, Modifier.weight(1f))
        }
        val selected = state.selected
        if (selected != null) {
            BuyBar(
                selected,
                state.shop?.balance,
                actions.onBuyRequest,
                Modifier.align(Alignment.BottomCenter).padding(14.dp)
            )
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
    state.confirming?.let { item ->
        BuyConfirmDialog(item, state.shop?.balance ?: 0, actions.onBuy, actions.onDismiss)
    }
}

/** 부위 필터 칩 줄(피그마 H-4: 높이 40, 모서리 10, 좌우 16, 간격 8, 고른 칩 primary 채움). 상점은 "모자" 그대로다. */
@Composable
private fun SlotChips(slot: ItemSlot?, onSlot: (ItemSlot?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ItemSlot.entries.forEach { entry ->
            val isSelected = entry == slot
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSlot(entry) }),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .height(40.dp)
                        .clip(shape)
                        .background(if (isSelected) RunkkColors.LimeFill else RunkkColors.Surface)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(entry.labelRes),
                        style = RunkkTextStyles.Body,
                        color = if (isSelected) RunkkColors.OnLimeFill else RunkkColors.TextPrimary
                    )
                }
            }
        }
    }
}

/** 구매 확인(피그마 H-5): 제목, 112 그림, 가운데 두 줄 "n P," / "구매 후 잔액 m P", 취소(흰)·구매(보라). */
@Composable
private fun BuyConfirmDialog(item: Shop.Item, balance: Int, onBuy: () -> Unit, onDismiss: () -> Unit) {
    RunkkDialog(title = stringResource(R.string.avatar_shop_confirm_title, item.name), onDismissRequest = onDismiss) {
        ItemArt(item, isSelected = true, modifier = Modifier.align(Alignment.CenterHorizontally).size(112.dp))
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

/** 상점 본문: 불러오는 중·오류·빈 목록·아이템 2열(아래 구매 바 자리를 비운다). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShopContent(state: ShopUiState, actions: ShopActions, modifier: Modifier = Modifier) {
    val shop = state.shop
    when {
        shop == null && state.isLoading -> RunkkLoading(modifier)

        shop == null -> RunkkErrorState(
            state.error?.asString() ?: stringResource(R.string.avatar_shop_load_failed),
            onRetry = actions.onRetry,
            modifier = modifier
        )

        shop.items.isEmpty() -> RunkkEmptyState(stringResource(R.string.avatar_shop_empty), modifier = modifier)

        else -> FlowRow(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 100.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 2
        ) {
            shop.items.forEach { item ->
                ShopTile(
                    item,
                    isSelected = state.selected?.itemId == item.itemId,
                    onClick = { actions.onPick(item) },
                    modifier = Modifier.weight(1f)
                )
            }
            if (shop.items.size % 2 == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * 아이템 칸(피그마 H-4, 169x176): 그림 96, 이름, 가격 "300". 보유면 "소지 중", 잠겼으면 흐리게(0.45) 해금 조건, 판매 종료면 그 표시.
 * 고른 칸은 primary 2 테두리다.
 */
@Composable
private fun ShopTile(item: Shop.Item, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val slotLabel = stringResource(item.slot.labelRes)
    val statusText = when {
        item.isOwned -> stringResource(R.string.avatar_shop_owned)
        item.isLocked -> item.unlockLabel ?: stringResource(R.string.avatar_shop_locked)
        !item.isOnSale -> stringResource(R.string.avatar_shop_ended)
        else -> formatReward(item.price)
    }
    val selectedText = stringResource(R.string.avatar_shop_selected)
    Column(
        modifier = modifier
            .height(TILE_HEIGHT)
            .clip(RunkkShapes.Large)
            .background(RunkkColors.Surface)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) RunkkColors.LimeFill else RunkkColors.Border,
                shape = RunkkShapes.Large
            )
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = listOf(item.name, slotLabel, statusText).joinToString(", ")
                if (isSelected) stateDescription = selectedText
            }
            .alpha(if (item.isLocked) LOCKED_ALPHA else 1f)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ItemArt(item, isSelected, Modifier.fillMaxWidth().height(96.dp))
        Text(item.name, style = RunkkTextStyles.Body, color = RunkkColors.TextPrimary, maxLines = 1)
        Text(
            statusText,
            style = RunkkTextStyles.text(14, FontWeight.Medium),
            color = RunkkColors.TextPrimary,
            maxLines = 2
        )
    }
}

/**
 * 아래 떠 있는 구매 바(피그마 H-4): 이름과 "사면 n 남아요", 보라 "300에 사기". 잔액이 값보다 적으면 "n 부족해요"를 보이고 사기 단추를
 * 끈다(DV-성-4). 잔액을 모르면(null) 남는 금액을 보이지 않고 단추는 켠다(서버가 판정).
 */
@Composable
private fun BuyBar(item: Shop.Item, balance: Int?, onBuyRequest: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RunkkShapes.Large)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, RunkkShapes.Large)
            .padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.name, style = RunkkTextStyles.Label, color = RunkkColors.TextPrimary, maxLines = 1)
            balance?.let {
                val left = it - item.price
                Text(
                    if (left >= 0) {
                        stringResource(R.string.avatar_shop_left_after, formatReward(left))
                    } else {
                        stringResource(R.string.avatar_shop_short_by, formatReward(-left))
                    },
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.TextSecondary
                )
            }
        }
        RunkkButton(
            stringResource(R.string.avatar_shop_buy_for, formatReward(item.price)),
            onClick = onBuyRequest,
            style = RunkkButtonStyle.Purple,
            size = RunkkButtonSize.Medium,
            isEnabled = balance == null || balance >= item.price
        )
    }
}

private val TILE_HEIGHT = 176.dp
private const val LOCKED_ALPHA = 0.45f

private val PREVIEW_ITEMS = listOf(
    Shop.Item(1, ItemSlot.TOP, "라임 싱글렛", 300, isOwned = false, isOnSale = true),
    Shop.Item(2, ItemSlot.TOP, "새벽 바람막이", 500, isOwned = true, isOnSale = true),
    Shop.Item(6, ItemSlot.TOP, "완주 기념 티", 300, false, true, isLocked = true, unlockLabel = "하프 완주하면 열려요")
)

@Preview(name = "H-4 상점", widthDp = 390, heightDp = 844)
@Composable
private fun ShopPreview() {
    RunkkTheme(reduceMotion = true) {
        ShopScreen(
            ShopUiState(isLoading = false, shop = Shop(PREVIEW_ITEMS, 1_240), selected = PREVIEW_ITEMS[0]),
            ShopActions()
        )
    }
}
