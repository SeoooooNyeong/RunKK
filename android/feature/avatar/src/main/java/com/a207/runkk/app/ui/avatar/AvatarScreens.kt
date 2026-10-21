package com.a207.runkk.app.ui.avatar

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkBadge
import com.a207.runkk.app.core.designsystem.RunkkBadgeTone
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkPillButton
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkProgressBar
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkSectionTitle
import com.a207.runkk.app.core.designsystem.RunkkSegmentedTabs
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextLink
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.avatar.Avatar
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.Shop
import java.util.Locale

// ---------- 아바타 만들기 ----------

/** 아바타 만들기(가입 5단계·얼굴 바꾸기). 셀카는 시스템 카메라로 찍어 줄인 뒤 보낸다. 끝나면 이전 화면(홈·꾸미기)으로. */
@Composable
fun AvatarSetupRoute(viewModel: AvatarSetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                AvatarSetupEvent.Done -> navigator.back()
            }
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap == null) {
            viewModel.onSelfieUnavailable()
        } else {
            viewModel.onSelfieCaptured(bitmap)
        }
    }
    AvatarSetupScreen(
        state = state,
        onBack = navigator::back,
        onSelfie = { camera.launch(null) },
        onCharacter = viewModel::onCharacterSelected,
        onConfirm = viewModel::onConfirmCharacter,
        onLater = navigator::back
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AvatarSetupScreen(
    state: AvatarSetupUiState,
    onBack: () -> Unit,
    onSelfie: () -> Unit,
    onCharacter: (DefaultCharacter) -> Unit,
    onConfirm: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        RunkkTopBar(title = "아바타 만들기", onBack = onBack)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text("나만의 아바타를 만들어요", style = RunkkTextStyles.Headline, color = RunkkColors.TextPrimary)
            RunkkCard(borderColor = if (state.canCreateSelfie) RunkkColors.Lime else RunkkColors.Border) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(RunkkColors.LimeContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            RunkkIcons.Camera,
                            contentDescription = null,
                            tint = RunkkColors.Lime,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "셀카로 SD 얼굴 만들기",
                            style = RunkkTextStyles.CardTitle,
                            color = RunkkColors.TextPrimary
                        )
                        Text(
                            if (state.canCreateSelfie) SELFIE_OK else SELFIE_BLOCKED_TODAY,
                            style = RunkkTextStyles.Caption,
                            color = RunkkColors.TextSecondary
                        )
                    }
                }
                RunkkSecondaryButton(
                    "셀카 찍기",
                    onClick = onSelfie,
                    enabled = state.canCreateSelfie && !state.working,
                    leadingIcon = RunkkIcons.Camera,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            RunkkSectionTitle("기본 캐릭터 고르기")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 2
            ) {
                DefaultCharacter.entries.forEach { character ->
                    CharacterOption(
                        character,
                        selected = character == state.selected,
                        onClick = { onCharacter(character) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (state.error != null) {
                Text(
                    state.error,
                    style = RunkkTextStyles.text(13, FontWeight.Bold),
                    color = RunkkColors.Lavender,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
        HorizontalDivider(color = RunkkColors.Border)
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RunkkPrimaryButton(
                state.selected?.let { "${it.label}로 시작하기" } ?: "캐릭터를 골라 주세요",
                onClick = onConfirm,
                enabled = state.selected != null,
                loading = state.working,
                modifier = Modifier.fillMaxWidth()
            )
            RunkkTextLink("나중에 할게요", onClick = onLater)
        }
    }
}

private const val SELFIE_OK = "하루 한 번 만들 수 있어요. 원본은 변환 뒤 바로 지워요"
private const val SELFIE_BLOCKED_TODAY = "오늘은 만들 수 없어요. 내일 다시 만들 수 있어요"

@Composable
private fun CharacterOption(
    character: DefaultCharacter,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    RunkkCard(
        modifier = modifier.semantics { this.selected = selected },
        onClick = onClick,
        onClickLabel = "${character.label} 고르기",
        borderColor = if (selected) RunkkColors.Lime else RunkkColors.Border,
        background = if (selected) RunkkColors.LimeContainer else RunkkColors.Surface,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.align(Alignment.CenterHorizontally).size(64.dp).clip(CircleShape).background(RunkkColors.Border),
            contentAlignment = Alignment.Center
        ) {
            Text(character.label, style = RunkkTextStyles.text(14, FontWeight.Black), color = RunkkColors.TextPrimary)
        }
        Text(
            character.label,
            style = RunkkTextStyles.Label,
            color = if (selected) RunkkColors.Lime else RunkkColors.TextSecondary,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

// ---------- 아바타 꾸미기 ----------

/** 아바타 꾸미기. 상점·얼굴 바꾸기로 간다. */
@Composable
fun AvatarRoute(viewModel: AvatarViewModel = hiltViewModel()) {
    // 상점·얼굴 바꾸기에서 돌아오면 다시 읽는다.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    AvatarScreen(
        state = state,
        onBack = navigator::back,
        onShop = { navigator.navigate(Routes.SHOP) },
        onFace = { navigator.navigate(Routes.AVATAR_SETUP) },
        onSlot = viewModel::onSlot,
        onToggle = viewModel::onToggle,
        onRetry = viewModel::load,
        onMessageShown = viewModel::onMessageShown
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AvatarScreen(
    state: AvatarUiState,
    onBack: () -> Unit,
    onShop: () -> Unit,
    onFace: () -> Unit,
    onSlot: (ItemSlot) -> Unit,
    onToggle: (Shop.Item) -> Unit,
    onRetry: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(Modifier.fillMaxSize()) {
            RunkkTopBar(title = "아바타 꾸미기", onBack = onBack) {
                Balance(state.avatar?.balance, onShop)
            }
            val avatar = state.avatar
            when {
                avatar == null && state.loading -> RunkkLoading(Modifier.weight(1f))

                avatar == null -> RunkkErrorState(
                    state.error ?: "아바타를 불러오지 못했어요",
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f)
                )

                else -> Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarFigure(
                            avatar,
                            equippedNames = ItemSlot.entries.associateWith {
                                state.itemName(avatar.equipped[it])
                            }
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                faceLabel(avatar),
                                style = RunkkTextStyles.CardTitle,
                                color = RunkkColors.TextPrimary
                            )
                            if (avatar.faceKey == null && avatar.defaultCharacterId == null) {
                                Text("기본 캐릭터를 고를 수 있어요", style = RunkkTextStyles.Caption, color = RunkkColors.Lavender)
                            }
                            RunkkPillButton("얼굴 바꾸기", onClick = onFace, leadingIcon = RunkkIcons.Camera)
                        }
                    }
                    BodyCard(avatar)
                    RunkkSectionTitle("내 아이템 입히기")
                    RunkkSegmentedTabs(
                        options = ItemSlot.entries.map { it.label },
                        selectedIndex = state.slot.ordinal,
                        onSelect = { onSlot(ItemSlot.entries[it]) },
                        compact = true
                    )
                    val owned = state.ownedInSlot
                    if (owned.isEmpty()) {
                        RunkkEmptyState(
                            title = "${state.slot.label} 아이템이 없어요",
                            message = "상점에서 리워드로 살 수 있어요",
                            actionLabel = "상점 가기",
                            onAction = onShop
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            maxItemsInEachRow = 2
                        ) {
                            owned.forEach { item ->
                                ItemTile(
                                    item,
                                    equipped = state.isEquipped(item),
                                    onClick = { onToggle(item) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp))
    }
}

private fun faceLabel(avatar: Avatar): String = when {
    avatar.faceKey != null -> "셀카 SD 얼굴"
    else -> DefaultCharacter.of(avatar.defaultCharacterId)?.let { "기본 캐릭터 · ${it.label}" } ?: "얼굴 없음"
}

/** 리워드 잔액(누르면 상점). */
@Composable
private fun Balance(balance: Int?, onShop: () -> Unit) {
    RunkkPillButton(
        balance?.let { String.format(Locale.US, "%,d", it) } ?: "-",
        onClick = onShop,
        leadingIcon = RunkkIcons.Bolt,
        modifier = Modifier.semantics {
            contentDescription = "리워드 잔액 ${balance?.let { String.format(Locale.US, "%,d", it) } ?: "-"}, 상점 열기"
        }
    )
}

/** 체형 게이지(FR-163)·피부톤 근거(FR-164). */
@Composable
private fun BodyCard(avatar: Avatar) {
    val stage = bodyStage(avatar.bodyShape)
    RunkkCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "체형",
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${stage ?: "-"}/${BODY_STAGES}단계 · ${bodyLabel(stage)}",
                style = RunkkTextStyles.BodyStrong,
                color = RunkkColors.TextPrimary
            )
        }
        RunkkProgressBar((stage ?: 0).toFloat() / BODY_STAGES)
        Text("4일 넘게 쉬면 배가 나오기 시작해요", style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
        HorizontalDivider(color = RunkkColors.Border)
        Text("피부톤은 달리는 시간을 따라가요", style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
        Text(skinLabel(avatar.skinTone), style = RunkkTextStyles.Body, color = RunkkColors.TextPrimary)
    }
}

@Composable
private fun ItemTile(item: Shop.Item, equipped: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    RunkkCard(
        modifier = modifier.semantics {
            selected = equipped
            stateDescription = if (equipped) "입는 중" else "벗은 상태"
        },
        onClick = onClick,
        onClickLabel = if (equipped) "벗기" else "입기",
        borderColor = if (equipped) RunkkColors.Lime else RunkkColors.Border,
        background = if (equipped) RunkkColors.LimeContainer else RunkkColors.Surface
    ) {
        Text(item.name, style = RunkkTextStyles.BodyStrong, color = RunkkColors.TextPrimary)
        RunkkBadge(
            if (equipped) "입는 중" else item.slot.label,
            tone = if (equipped) RunkkBadgeTone.Lime else RunkkBadgeTone.Muted
        )
    }
}

/**
 * 아바타 자리 그림: 피부톤 색 머리 + 얼굴(셀카·기본 캐릭터) + 체형 단계 폭의 몸 + 착용 부위 표시.
 * 그림 자원(SD 몸체·아이템)은 2단계에 넣는다.
 */
@Composable
internal fun AvatarFigure(avatar: Avatar, equippedNames: Map<ItemSlot, String?>, modifier: Modifier = Modifier) {
    val stage = bodyStage(avatar.bodyShape) ?: 1
    val description = buildString {
        append("아바타, ").append(faceLabel(avatar)).append(", 체형 ").append(bodyLabel(stage))
        equippedNames.filterValues { it != null }.forEach { (slot, name) -> append(", ${slot.label} $name") }
    }
    Column(
        modifier = modifier
            .size(width = 120.dp, height = 168.dp)
            .clip(RunkkShapes.ExtraLarge)
            .background(RunkkColors.Surface)
            .border(2.dp, RunkkColors.Lime, RunkkShapes.ExtraLarge)
            .padding(12.dp)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Image
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(skinColor(avatar.skinTone))
                .then(
                    if (equippedNames[ItemSlot.HAT] !=
                        null
                    ) {
                        Modifier.border(3.dp, RunkkColors.Lavender, CircleShape)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (avatar.faceKey != null) "SD" else DefaultCharacter.of(avatar.defaultCharacterId)?.label ?: "?",
                style = RunkkTextStyles.text(12, FontWeight.Black),
                color = RunkkColors.OnLime
            )
        }
        Box(
            Modifier
                .width((BODY_BASE + (BODY_STAGES - stage) * BODY_STEP).dp)
                .height(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (equippedNames[ItemSlot.TOP] != null) RunkkColors.Lime else RunkkColors.BorderStrong)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(2) {
                Box(
                    Modifier
                        .size(width = 14.dp, height = 20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (equippedNames[ItemSlot.BOTTOM] !=
                                null
                            ) {
                                RunkkColors.Lavender
                            } else {
                                RunkkColors.BorderStrong
                            }
                        )
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .size(width = 48.dp, height = 6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (equippedNames[ItemSlot.SHOES] != null) RunkkColors.Lime else RunkkColors.Border)
        )
    }
}

private fun skinColor(tone: String): Color = when (tone) {
    "DAWN" -> Color(0xFFF1D7C4)
    "MORNING" -> Color(0xFFEFC9A6)
    "DAYTIME" -> Color(0xFFE0B48F)
    "EVENING" -> Color(0xFFC99470)
    "NIGHT" -> Color(0xFFA77B5C)
    else -> Color(0xFFE0B48F)
}

private const val BODY_BASE = 48
private const val BODY_STEP = 8

// ---------- 상점 ----------

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

private val SHOP_SLOTS: List<ItemSlot?> = listOf(null) + ItemSlot.entries

@OptIn(ExperimentalLayoutApi::class)
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
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(Modifier.fillMaxSize()) {
            RunkkTopBar(title = "상점", onBack = onBack) {
                Text(
                    state.shop?.balance?.let { String.format(Locale.US, "%,d", it) } ?: "-",
                    style = RunkkTextStyles.display(22),
                    color = RunkkColors.Lime,
                    modifier = Modifier.semantics { contentDescription = "리워드 잔액 ${state.shop?.balance ?: 0}" }
                )
            }
            RunkkSegmentedTabs(
                options = SHOP_SLOTS.map { it?.label ?: "전체" },
                selectedIndex = SHOP_SLOTS.indexOf(state.slot),
                onSelect = { onSlot(SHOP_SLOTS[it]) },
                compact = true,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            val shop = state.shop
            when {
                shop == null && state.loading -> RunkkLoading(Modifier.weight(1f))

                shop == null -> RunkkErrorState(
                    state.error ?: "상점을 불러오지 못했어요",
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f)
                )

                shop.items.isEmpty() -> RunkkEmptyState("판매 중인 아이템이 없어요", modifier = Modifier.weight(1f))

                else -> Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "달린 거리 1km마다 10, 대결 승리마다 20 리워드가 쌓여요(하루 200까지)",
                        style = RunkkTextStyles.Caption,
                        color = RunkkColors.TextSecondary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 2
                    ) {
                        shop.items.forEach { item ->
                            ShopTile(item, onClick = { onPick(item) }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp))
    }
    state.confirming?.let { item ->
        RunkkConfirmDialog(
            title = "${item.name}을(를) 살까요?",
            message = "${item.price} 리워드를 써요. 지금 잔액 ${state.shop?.balance ?: 0}",
            confirmLabel = "사기",
            onConfirm = onBuy,
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun ShopTile(item: Shop.Item, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val buyable = !item.owned && item.onSale
    RunkkCard(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = listOf(
                item.name,
                item.slot.label,
                when {
                    item.owned -> "보유"
                    !item.onSale -> "판매 종료"
                    else -> "${item.price} 리워드"
                }
            ).joinToString(", ")
        },
        onClick = if (buyable) onClick else null,
        onClickLabel = "${item.name} 사기"
    ) {
        Box(
            Modifier.fillMaxWidth().height(72.dp).clip(RunkkShapes.Medium).background(RunkkColors.Border),
            contentAlignment = Alignment.Center
        ) {
            Text(item.slot.label, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
        }
        Text(item.name, style = RunkkTextStyles.BodyStrong, color = RunkkColors.TextPrimary)
        when {
            item.owned -> RunkkBadge("보유", tone = RunkkBadgeTone.Lime)

            !item.onSale -> RunkkBadge("판매 종료", tone = RunkkBadgeTone.Muted)

            else -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    RunkkIcons.Bolt,
                    contentDescription = null,
                    tint = RunkkColors.Lime,
                    modifier = Modifier.size(14.dp)
                )
                Text(item.price.toString(), style = RunkkTextStyles.display(20), color = RunkkColors.Lime)
            }
        }
    }
}

@Preview(name = "아바타 만들기", widthDp = 390, heightDp = 844)
@Composable
private fun AvatarSetupPreview() {
    RunkkTheme(reduceMotion = true) {
        AvatarSetupScreen(AvatarSetupUiState(loading = false, selected = DefaultCharacter.FOX), {}, {}, {}, {}, {})
    }
}
