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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
import com.a207.runkk.app.core.designsystem.RunkkAvatarFace
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
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.FaceLayer
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
        RunkkTopBar(title = stringResource(R.string.avatar_setup_title), onBack = onBack)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                stringResource(R.string.avatar_setup_headline),
                style = RunkkTextStyles.Headline,
                color = RunkkColors.TextPrimary
            )
            SelfieCard(state.canCreateSelfie, state.working, onSelfie)
            RunkkSectionTitle(stringResource(R.string.avatar_setup_pick_character))
            CharacterGrid(state.selected, onCharacter)
            if (state.error != null) {
                Text(
                    state.error.asString(),
                    style = RunkkTextStyles.text(13, FontWeight.Bold),
                    color = RunkkColors.Lavender,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
        HorizontalDivider(color = RunkkColors.Border)
        SetupFooter(state.selected, state.working, onConfirm, onLater)
    }
}

/** 셀카로 SD 얼굴 만들기 카드(하루 한 번). */
@Composable
private fun SelfieCard(canCreateSelfie: Boolean, working: Boolean, onSelfie: () -> Unit) {
    RunkkCard(borderColor = if (canCreateSelfie) RunkkColors.Lime else RunkkColors.Border) {
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
                    stringResource(R.string.avatar_setup_selfie_title),
                    style = RunkkTextStyles.CardTitle,
                    color = RunkkColors.TextPrimary
                )
                Text(
                    stringResource(
                        if (canCreateSelfie) R.string.avatar_setup_selfie_ok else R.string.avatar_setup_selfie_blocked
                    ),
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.TextSecondary
                )
            }
        }
        RunkkSecondaryButton(
            stringResource(R.string.avatar_setup_take_selfie),
            onClick = onSelfie,
            enabled = canCreateSelfie && !working,
            leadingIcon = RunkkIcons.Camera,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 기본 캐릭터 4종 고르기. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CharacterGrid(selected: DefaultCharacter?, onCharacter: (DefaultCharacter) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2
    ) {
        DefaultCharacter.entries.forEach { character ->
            CharacterOption(
                character,
                selected = character == selected,
                onClick = { onCharacter(character) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 시작하기 버튼과 나중에 하기 링크. */
@Composable
private fun SetupFooter(selected: DefaultCharacter?, working: Boolean, onConfirm: () -> Unit, onLater: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RunkkPrimaryButton(
            selected?.let { stringResource(R.string.avatar_setup_start_with, stringResource(it.labelRes)) }
                ?: stringResource(R.string.avatar_setup_pick_prompt),
            onClick = onConfirm,
            enabled = selected != null,
            loading = working,
            modifier = Modifier.fillMaxWidth()
        )
        RunkkTextLink(stringResource(R.string.avatar_setup_later), onClick = onLater)
    }
}

@Composable
private fun CharacterOption(
    character: DefaultCharacter,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val name = stringResource(character.labelRes)
    RunkkCard(
        modifier = modifier.semantics { this.selected = selected },
        onClick = onClick,
        onClickLabel = stringResource(R.string.avatar_setup_pick_label, name),
        borderColor = if (selected) RunkkColors.Lime else RunkkColors.Border,
        background = if (selected) RunkkColors.LimeContainer else RunkkColors.Surface,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.align(Alignment.CenterHorizontally).size(64.dp).clip(CircleShape).background(RunkkColors.Border),
            contentAlignment = Alignment.Center
        ) {
            Text(name, style = RunkkTextStyles.text(14, FontWeight.Black), color = RunkkColors.TextPrimary)
        }
        Text(
            name,
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

/** 안내 문구가 생기면 스낵바로 한 번 보이고 지운다. */
@Composable
private fun MessageEffect(message: UiText?, snackbar: SnackbarHostState, onMessageShown: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it.asString(context))
            onMessageShown()
        }
    }
}

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
    MessageEffect(state.message, snackbar, onMessageShown)
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(Modifier.fillMaxSize()) {
            RunkkTopBar(title = stringResource(R.string.avatar_decorate_title), onBack = onBack) {
                Balance(state.avatar?.balance, onShop)
            }
            val avatar = state.avatar
            when {
                avatar == null && state.loading -> RunkkLoading(Modifier.weight(1f))

                avatar == null -> RunkkErrorState(
                    state.error?.asString() ?: stringResource(R.string.avatar_decorate_load_failed),
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f)
                )

                else -> AvatarBody(state, avatar, onFace, onShop, onSlot, onToggle, Modifier.weight(1f))
            }
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp))
    }
}

/** 꾸미기 본문: 그림·체형·부위 탭·보유 아이템. */
@Composable
private fun AvatarBody(
    state: AvatarUiState,
    avatar: Avatar,
    onFace: () -> Unit,
    onShop: () -> Unit,
    onSlot: (ItemSlot) -> Unit,
    onToggle: (Shop.Item) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AvatarHeader(state, avatar, onFace)
        BodyCard(avatar)
        RunkkSectionTitle(stringResource(R.string.avatar_decorate_dress_title))
        RunkkSegmentedTabs(
            options = ItemSlot.entries.map { stringResource(it.labelRes) },
            selectedIndex = state.slot.ordinal,
            onSelect = { onSlot(ItemSlot.entries[it]) },
            compact = true
        )
        OwnedItems(state, onShop, onToggle)
    }
}

/** 아바타 그림과 얼굴 문구·얼굴 바꾸기 버튼. */
@Composable
private fun AvatarHeader(state: AvatarUiState, avatar: Avatar, onFace: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarFigure(
            avatar,
            equippedNames = ItemSlot.entries.associateWith { state.itemName(avatar.equipped[it]) },
            faceLayers = state.faceLayers
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(faceLabel(avatar), style = RunkkTextStyles.CardTitle, color = RunkkColors.TextPrimary)
            if (avatar.faceKey == null && avatar.defaultCharacterId == null) {
                Text(
                    stringResource(R.string.avatar_decorate_pick_default_hint),
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.Lavender
                )
            }
            RunkkPillButton(
                stringResource(R.string.avatar_decorate_change_face),
                onClick = onFace,
                leadingIcon = RunkkIcons.Camera
            )
        }
    }
}

/** 부위에 맞는 보유 아이템 목록(없으면 상점 안내). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OwnedItems(state: AvatarUiState, onShop: () -> Unit, onToggle: (Shop.Item) -> Unit) {
    val owned = state.ownedInSlot
    if (owned.isEmpty()) {
        RunkkEmptyState(
            title = stringResource(R.string.avatar_decorate_no_items, stringResource(state.slot.labelRes)),
            message = stringResource(R.string.avatar_decorate_no_items_hint),
            actionLabel = stringResource(R.string.avatar_decorate_go_shop),
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

@Composable
private fun faceLabel(avatar: Avatar): String = when {
    avatar.faceKey != null -> stringResource(R.string.avatar_face_selfie)

    else -> DefaultCharacter.of(avatar.defaultCharacterId)
        ?.let { stringResource(R.string.avatar_face_default, stringResource(it.labelRes)) }
        ?: stringResource(R.string.avatar_face_none)
}

private fun formatReward(value: Int?): String = value?.let { String.format(Locale.US, "%,d", it) } ?: "-"

/** 리워드 잔액(누르면 상점). */
@Composable
private fun Balance(balance: Int?, onShop: () -> Unit) {
    val description = stringResource(R.string.avatar_balance_open_shop, formatReward(balance))
    RunkkPillButton(
        formatReward(balance),
        onClick = onShop,
        leadingIcon = RunkkIcons.Bolt,
        modifier = Modifier.semantics { contentDescription = description }
    )
}

/** 체형 게이지(FR-163)·피부톤 근거(FR-164). */
@Composable
private fun BodyCard(avatar: Avatar) {
    val stage = bodyStage(avatar.bodyShape)
    RunkkCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.avatar_body_title),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(
                    R.string.avatar_body_stage,
                    stage?.toString() ?: "-",
                    BODY_STAGES,
                    stringResource(bodyLabelRes(stage))
                ),
                style = RunkkTextStyles.BodyStrong,
                color = RunkkColors.TextPrimary
            )
        }
        RunkkProgressBar((stage ?: 0).toFloat() / BODY_STAGES)
        Text(
            stringResource(R.string.avatar_body_rest_hint),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
        HorizontalDivider(color = RunkkColors.Border)
        Text(
            stringResource(R.string.avatar_body_skin_hint),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
        Text(skinLabel(avatar.skinTone).asString(), style = RunkkTextStyles.Body, color = RunkkColors.TextPrimary)
    }
}

@Composable
private fun ItemTile(item: Shop.Item, equipped: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val wearing = stringResource(R.string.avatar_item_wearing)
    val state = if (equipped) wearing else stringResource(R.string.avatar_item_off)
    RunkkCard(
        modifier = modifier.semantics {
            selected = equipped
            stateDescription = state
        },
        onClick = onClick,
        onClickLabel = stringResource(if (equipped) R.string.avatar_item_take_off else R.string.avatar_item_put_on),
        borderColor = if (equipped) RunkkColors.Lime else RunkkColors.Border,
        background = if (equipped) RunkkColors.LimeContainer else RunkkColors.Surface
    ) {
        Text(item.name, style = RunkkTextStyles.BodyStrong, color = RunkkColors.TextPrimary)
        RunkkBadge(
            if (equipped) wearing else stringResource(item.slot.labelRes),
            tone = if (equipped) RunkkBadgeTone.Lime else RunkkBadgeTone.Muted
        )
    }
}

/**
 * 아바타 그림: 피부톤 색 머리 + 얼굴(셀카면 서버 부위 PNG를 겹친 SD 얼굴, 받지 못했거나 기본 캐릭터면 글자 자리 그림)
 * + 체형 단계 폭의 몸 + 착용 부위 표시. 그림 자원(SD 몸체·아이템)은 2단계에 넣는다.
 */
@Composable
internal fun AvatarFigure(
    avatar: Avatar,
    equippedNames: Map<ItemSlot, String?>,
    modifier: Modifier = Modifier,
    faceLayers: List<FaceLayer> = emptyList()
) {
    val stage = bodyStage(avatar.bodyShape) ?: 1
    val description = figureDescription(avatar, equippedNames, stage)
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
        FigureHead(avatar, equippedNames[ItemSlot.HAT] != null, faceLayers)
        Box(
            Modifier
                .width((BODY_BASE + (BODY_STAGES - stage) * BODY_STEP).dp)
                .height(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (equippedNames[ItemSlot.TOP] != null) RunkkColors.Lime else RunkkColors.BorderStrong)
        )
        FigureLegs(equippedNames[ItemSlot.BOTTOM] != null)
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .size(width = 48.dp, height = 6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (equippedNames[ItemSlot.SHOES] != null) RunkkColors.Lime else RunkkColors.Border)
        )
    }
}

/** 그림 하나로 읽어 주는 설명: 얼굴·체형·입은 아이템. */
@Composable
private fun figureDescription(avatar: Avatar, equippedNames: Map<ItemSlot, String?>, stage: Int): String {
    val base =
        stringResource(R.string.avatar_figure_description, faceLabel(avatar), stringResource(bodyLabelRes(stage)))
    val slotLabels = ItemSlot.entries.associateWith { stringResource(it.labelRes) }
    val equipped = stringResource(R.string.avatar_figure_equipped_format)
    return buildString {
        append(base)
        equippedNames.filterValues { it != null }.forEach { (slot, name) ->
            append(String.format(equipped, slotLabels.getValue(slot), name))
        }
    }
}

/** 머리: 피부톤 원 + 얼굴(셀카 부위 그림 또는 자리 글자). 모자를 쓰면 테두리를 두른다. */
@Composable
private fun FigureHead(avatar: Avatar, hasHat: Boolean, faceLayers: List<FaceLayer>) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(skinColor(avatar.skinTone))
            .then(if (hasHat) Modifier.border(3.dp, RunkkColors.Lavender, CircleShape) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        val placeholderText = if (avatar.faceKey != null) {
            "SD"
        } else {
            DefaultCharacter.of(avatar.defaultCharacterId)?.let { stringResource(it.labelRes) } ?: "?"
        }
        val placeholder: @Composable () -> Unit = {
            Text(
                placeholderText,
                style = RunkkTextStyles.text(12, FontWeight.Black),
                color = RunkkColors.OnLime
            )
        }
        if (faceLayers.isEmpty()) {
            placeholder()
        } else {
            RunkkAvatarFace(faceLayers, Modifier.fillMaxSize().testTag(FACE_TAG)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { placeholder() }
            }
        }
    }
}

/** 다리 두 개(하의를 입으면 라벤더). */
@Composable
private fun FigureLegs(hasBottom: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(2) {
            Box(
                Modifier
                    .size(width = 14.dp, height = 20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (hasBottom) RunkkColors.Lavender else RunkkColors.BorderStrong)
            )
        }
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

/** 셀카 얼굴 그림(시험이 찾는 표지). */
internal const val FACE_TAG = "avatar-face"

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
    MessageEffect(state.message, snackbar, onMessageShown)
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(Modifier.fillMaxSize()) {
            ShopHeader(state, onBack, onSlot)
            ShopContent(state, onRetry, onPick, Modifier.weight(1f))
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp))
    }
    state.confirming?.let { item ->
        RunkkConfirmDialog(
            title = stringResource(R.string.avatar_shop_confirm_title, item.name),
            message = stringResource(R.string.avatar_shop_confirm_message, item.price, state.shop?.balance ?: 0),
            confirmLabel = stringResource(R.string.avatar_shop_buy),
            onConfirm = onBuy,
            onDismiss = onDismiss
        )
    }
}

/** 상점 상단: 제목·잔액과 부위 탭. */
@Composable
private fun ShopHeader(state: ShopUiState, onBack: () -> Unit, onSlot: (ItemSlot?) -> Unit) {
    val balanceDescription = stringResource(R.string.avatar_shop_balance_description, state.shop?.balance ?: 0)
    RunkkTopBar(title = stringResource(R.string.avatar_shop_title), onBack = onBack) {
        Text(
            formatReward(state.shop?.balance),
            style = RunkkTextStyles.display(22),
            color = RunkkColors.Lime,
            modifier = Modifier.semantics { contentDescription = balanceDescription }
        )
    }
    val all = stringResource(R.string.avatar_shop_all)
    RunkkSegmentedTabs(
        options = SHOP_SLOTS.map { slot -> slot?.let { stringResource(it.labelRes) } ?: all },
        selectedIndex = SHOP_SLOTS.indexOf(state.slot),
        onSelect = { onSlot(SHOP_SLOTS[it]) },
        compact = true,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

/** 상점 본문: 불러오는 중·오류·빈 목록·아이템 목록. */
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
        shop == null && state.loading -> RunkkLoading(modifier)

        shop == null -> RunkkErrorState(
            state.error?.asString() ?: stringResource(R.string.avatar_shop_load_failed),
            onRetry = onRetry,
            modifier = modifier
        )

        shop.items.isEmpty() -> RunkkEmptyState(stringResource(R.string.avatar_shop_empty), modifier = modifier)

        else -> Column(
            modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.avatar_shop_reward_hint),
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

@Composable
private fun ShopTile(item: Shop.Item, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val buyable = !item.owned && item.onSale
    val slotLabel = stringResource(item.slot.labelRes)
    val statusText = when {
        item.owned -> stringResource(R.string.avatar_shop_owned)
        !item.onSale -> stringResource(R.string.avatar_shop_ended)
        else -> stringResource(R.string.avatar_shop_price_description, item.price)
    }
    val description = listOf(item.name, slotLabel, statusText).joinToString(", ")
    RunkkCard(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        onClick = if (buyable) onClick else null,
        onClickLabel = stringResource(R.string.avatar_shop_buy_label, item.name)
    ) {
        Box(
            Modifier.fillMaxWidth().height(72.dp).clip(RunkkShapes.Medium).background(RunkkColors.Border),
            contentAlignment = Alignment.Center
        ) {
            Text(slotLabel, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
        }
        Text(item.name, style = RunkkTextStyles.BodyStrong, color = RunkkColors.TextPrimary)
        when {
            item.owned -> RunkkBadge(stringResource(R.string.avatar_shop_owned), tone = RunkkBadgeTone.Lime)

            !item.onSale -> RunkkBadge(stringResource(R.string.avatar_shop_ended), tone = RunkkBadgeTone.Muted)

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
