package com.a207.runkk.app.ui.avatar

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkBadge
import com.a207.runkk.app.core.designsystem.RunkkBadgeTone
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkLimePillButton
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkSegmentedTabs
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.Shop

/** 상점(J4, FR-160): 부위 탭, 아이템 그림·가격·구매. 구매는 확인(J5)을 거친다. */
@Composable
fun ShopRoute(viewModel: ShopViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    ShopScreen(
        state = state,
        onBack = navigator::back,
        onSlot = viewModel::onSlot,
        onPick = viewModel::onPick,
        onBuy = viewModel::onBuy,
        onDismiss = viewModel::onDismiss,
        onRetry = viewModel::load,
        onMessageShown = viewModel::onMessageShown
    )
}

@Composable
fun ShopScreen(
    state: ShopUiState,
    onBack: () -> Unit,
    onSlot: (ItemSlot?) -> Unit,
    onPick: (Shop.Item) -> Unit,
    onBuy: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbar = remember { SnackbarHostState() }
    AvatarMessageEffect(state.message, snackbar, onMessageShown)
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(Modifier.fillMaxSize()) {
            RunkkTopBar(title = stringResource(R.string.avatar_shop_title), onBack = onBack) {
                Balance(state.shop?.balance)
            }
            RunkkSegmentedTabs(
                options = ItemSlot.entries.map { stringResource(it.labelRes) },
                selectedIndex = state.slot?.ordinal ?: -1,
                onSelect = { onSlot(ItemSlot.entries[it]) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            ShopContent(state, onRetry, onPick, Modifier.weight(1f))
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp))
    }
    state.confirming?.let { item -> BuyConfirmDialog(item, state.shop?.balance ?: 0, onBuy, onDismiss) }
}

/** 구매 확인(J5): "[아이템]을 살까요?", 그림, "n P, 구매 후 잔액 m P", 취소/구매. */
@Composable
private fun BuyConfirmDialog(item: Shop.Item, balance: Int, onBuy: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RunkkColors.Surface,
        titleContentColor = RunkkColors.TextPrimary,
        textContentColor = RunkkColors.TextSecondary,
        title = {
            Text(stringResource(R.string.avatar_shop_confirm_title, item.name), style = RunkkTextStyles.CardTitle)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ItemPicture(item.slot)
                Text(
                    stringResource(
                        R.string.avatar_shop_confirm_message,
                        formatReward(item.price),
                        formatReward(balance - item.price)
                    ),
                    style = RunkkTextStyles.Body
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onBuy) {
                Text(
                    stringResource(R.string.avatar_shop_buy),
                    style = RunkkTextStyles.BodyStrong,
                    color = RunkkColors.Lime
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.avatar_shop_cancel),
                    style = RunkkTextStyles.BodyStrong,
                    color = RunkkColors.TextSecondary
                )
            }
        }
    )
}

/** 아이템 그림 자리(아이템 그림 자원은 2단계에 넣는다): 부위 이름. */
@Composable
private fun ItemPicture(slot: ItemSlot) {
    Box(
        Modifier.fillMaxWidth().height(72.dp).clip(RunkkShapes.Medium).background(RunkkColors.Border),
        contentAlignment = Alignment.Center
    ) {
        Text(stringResource(slot.labelRes), style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
    }
}

/** 상점 본문: 불러오는 중·오류·빈 목록·아이템 2열. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShopContent(
    state: ShopUiState,
    onRetry: () -> Unit,
    onPick: (Shop.Item) -> Unit,
    modifier: Modifier = Modifier
) {
    val shop = state.shop
    when {
        shop == null && state.isLoading -> RunkkLoading(modifier)

        shop == null -> RunkkErrorState(
            state.error?.asString() ?: stringResource(R.string.avatar_shop_load_failed),
            onRetry = onRetry,
            modifier = modifier
        )

        shop.items.isEmpty() -> RunkkEmptyState(stringResource(R.string.avatar_shop_empty), modifier = modifier)

        else -> FlowRow(
            modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 2
        ) {
            shop.items.forEach { item -> ShopTile(item, onClick = { onPick(item) }, modifier = Modifier.weight(1f)) }
            if (shop.items.size % 2 == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** 아이템 칸: 그림, 이름, "n P" + 구매(보유면 "보유 중", 판매 종료면 그 표시). */
@Composable
private fun ShopTile(item: Shop.Item, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val slotLabel = stringResource(item.slot.labelRes)
    val price = stringResource(R.string.avatar_points, formatReward(item.price))
    val statusText = when {
        item.isOwned -> stringResource(R.string.avatar_shop_owned)
        !item.isOnSale -> stringResource(R.string.avatar_shop_ended)
        else -> price
    }
    RunkkCard(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = listOf(item.name, slotLabel, statusText).joinToString(", ")
        }
    ) {
        ItemPicture(item.slot)
        Text(item.name, style = RunkkTextStyles.BodyStrong, color = RunkkColors.TextPrimary)
        when {
            item.isOwned -> RunkkBadge(stringResource(R.string.avatar_shop_owned), tone = RunkkBadgeTone.Lime)

            !item.isOnSale -> RunkkBadge(stringResource(R.string.avatar_shop_ended), tone = RunkkBadgeTone.Muted)

            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    price,
                    style = RunkkTextStyles.text(14, FontWeight.Bold),
                    color = RunkkColors.Lime,
                    modifier = Modifier.weight(1f)
                )
                RunkkLimePillButton(stringResource(R.string.avatar_shop_buy), onClick = onClick)
            }
        }
    }
}

private val PREVIEW_ITEMS = listOf(
    Shop.Item(1, ItemSlot.TOP, "러닝 티셔츠", 300, isOwned = false, isOnSale = true),
    Shop.Item(2, ItemSlot.TOP, "바람막이", 500, isOwned = true, isOnSale = true)
)

@Preview(name = "J4 상점", widthDp = 390, heightDp = 844)
@Composable
private fun ShopPreview() {
    RunkkTheme(reduceMotion = true) {
        ShopScreen(ShopUiState(isLoading = false, shop = Shop(PREVIEW_ITEMS, 1_240)), {}, {}, {}, {}, {}, {}, {})
    }
}
