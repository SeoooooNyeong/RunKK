package com.a207.runkk.app.ui.avatar

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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
import com.a207.runkk.app.core.designsystem.AvatarBackdrop
import com.a207.runkk.app.core.designsystem.RUNKK_AVATAR_FACE_TAG
import com.a207.runkk.app.core.designsystem.RunkkAvatarPortrait
import com.a207.runkk.app.core.designsystem.RunkkBadge
import com.a207.runkk.app.core.designsystem.RunkkBadgeTone
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkInfoChip
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkPillButton
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkSegmentedTabs
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
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

/**
 * 아바타 꾸미기(J3). 상점·얼굴 만들기(J7)로 간다. [startOnBackground]면 배경 탭으로 연다(알림 AVATAR_BACKGROUND, FR-202).
 * 입기·벗기와 배경 고르기는 누르면 바로 서버에 보내고(거부는 바로 안내), "저장"은 꾸미기를 마치고 닫는다.
 */
@Composable
fun AvatarRoute(
    startOnBackground: Boolean = false,
    viewModel: AvatarViewModel = hiltViewModel(),
    backgroundViewModel: AvatarBackgroundViewModel = hiltViewModel()
) {
    // 상점·얼굴 바꾸기에서 돌아오면 다시 읽는다.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }
    var isOpened by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(startOnBackground) {
        if (startOnBackground && !isOpened) viewModel.onBackgroundTab()
        isOpened = true
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val background by backgroundViewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    AvatarScreen(
        state = state,
        background = background,
        actions = AvatarScreenActions(
            onBack = navigator::back,
            onShop = { navigator.navigate(Routes.SHOP) },
            onFace = { navigator.navigate(Routes.AVATAR_SETUP) },
            onSlot = viewModel::onSlot,
            onBackgroundTab = viewModel::onBackgroundTab,
            onToggle = viewModel::onToggle,
            onChooseBackground = backgroundViewModel::onChoose,
            onChooseDefaultBackground = backgroundViewModel::onChooseDefault,
            onRetry = {
                viewModel.load()
                backgroundViewModel.load()
            },
            onMessageShown = viewModel::onMessageShown,
            onBackgroundMessageShown = backgroundViewModel::onMessageShown,
            onSave = navigator::back
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
                Balance(state.avatar?.balance)
            }
            val avatar = state.avatar
            when {
                avatar == null && state.isLoading -> RunkkLoading(Modifier.weight(1f))

                avatar == null -> RunkkErrorState(
                    state.error?.asString() ?: stringResource(R.string.avatar_decorate_load_failed),
                    onRetry = actions.onRetry,
                    modifier = Modifier.weight(1f)
                )

                else -> AvatarBody(state, background, avatar, actions, Modifier.weight(1f))
            }
            HorizontalDivider(color = RunkkColors.Border)
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RunkkSecondaryButton(
                    stringResource(R.string.avatar_decorate_go_shop),
                    onClick = actions.onShop,
                    modifier = Modifier.weight(1f)
                )
                RunkkPrimaryButton(
                    stringResource(R.string.avatar_decorate_save),
                    onClick = actions.onSave,
                    isEnabled = !state.isSaving,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 112.dp))
    }
}

/** 꾸미기 본문: 큰 아바타, 체형·피부색 칩과 얼굴 바꾸기, 부위·배경 탭, 탭 내용. */
@Composable
private fun AvatarBody(
    state: AvatarUiState,
    background: AvatarBackgroundUiState,
    avatar: Avatar,
    actions: AvatarScreenActions,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 배경을 읽었으면 지금 고른 배경으로 그린다(배경 탭에서 고르면 바로 바뀐다).
        val backgroundKey = if (background.backgrounds != null) background.selectedKey else avatar.backgroundKey
        AvatarFigure(
            avatar.copy(backgroundKey = backgroundKey),
            equippedNames = ItemSlot.entries.associateWith { state.itemName(avatar.equipped[it]) },
            faceLayers = state.faceLayers,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        AvatarTraits(avatar, actions.onFace)
        val tabs = ItemSlot.entries.map { stringResource(it.labelRes) } + stringResource(R.string.avatar_tab_background)
        RunkkSegmentedTabs(
            options = tabs,
            selectedIndex = if (state.isBackgroundTab) ItemSlot.entries.size else state.slot.ordinal,
            onSelect = { index ->
                if (index ==
                    ItemSlot.entries.size
                ) {
                    actions.onBackgroundTab()
                } else {
                    actions.onSlot(ItemSlot.entries[index])
                }
            }
        )
        if (state.isBackgroundTab) {
            AvatarBackgroundTab(background, actions)
        } else {
            OwnedItems(state, actions.onToggle)
        }
    }
}

/** 체형·피부색 칩(FR-163, FR-164)과 얼굴 바꾸기(J7). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AvatarTraits(avatar: Avatar, onFace: () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        RunkkInfoChip(
            stringResource(R.string.avatar_trait_body, stringResource(bodyLabelRes(bodyStage(avatar.bodyShape))))
        )
        RunkkInfoChip(stringResource(R.string.avatar_trait_skin, skinLabel(avatar.skinTone).asString()))
        RunkkPillButton(
            stringResource(R.string.avatar_decorate_change_face),
            onClick = onFace,
            leadingIcon = RunkkIcons.Camera
        )
    }
}

/** 부위에 맞는 보유 아이템(없으면 빈 상태). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OwnedItems(state: AvatarUiState, onToggle: (Shop.Item) -> Unit) {
    val owned = state.ownedInSlot
    if (owned.isEmpty()) {
        RunkkEmptyState(title = stringResource(R.string.avatar_decorate_no_items, stringResource(state.slot.labelRes)))
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 2
        ) {
            owned.forEach { item ->
                ItemTile(
                    item,
                    isEquipped = state.isEquipped(item),
                    onClick = { onToggle(item) },
                    modifier = Modifier.weight(1f)
                )
            }
            if (owned.size % 2 == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** 얼굴 문구: 기본 캐릭터(faceKey "default:…")면 그 캐릭터, 셀카 얼굴("selfie:…")이면 셀카, 아니면 없음. */
@Composable
private fun faceLabel(avatar: Avatar): String {
    val character = avatar.character
    return when {
        character != null -> stringResource(R.string.avatar_face_default, stringResource(character.labelRes))
        avatar.isSelfieFace -> stringResource(R.string.avatar_face_selfie)
        else -> stringResource(R.string.avatar_face_none)
    }
}

internal fun formatReward(value: Int?): String = value?.let { String.format(Locale.US, "%,d", it) } ?: "-"

/** 리워드 잔액 "1,240 P". */
@Composable
internal fun Balance(balance: Int?) {
    val description = stringResource(R.string.avatar_shop_balance_description, balance ?: 0)
    Text(
        stringResource(R.string.avatar_points, formatReward(balance)),
        style = RunkkTextStyles.text(14, FontWeight.Bold),
        color = RunkkColors.Lime,
        modifier = Modifier.padding(end = 8.dp).semantics { contentDescription = description }
    )
}

@Composable
private fun ItemTile(item: Shop.Item, isEquipped: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val wearing = stringResource(R.string.avatar_item_wearing)
    val state = if (isEquipped) wearing else stringResource(R.string.avatar_item_off)
    RunkkCard(
        modifier = modifier.semantics {
            selected = isEquipped
            stateDescription = state
        },
        onClick = onClick,
        onClickLabel = stringResource(if (isEquipped) R.string.avatar_item_take_off else R.string.avatar_item_put_on),
        borderColor = if (isEquipped) RunkkColors.Lime else RunkkColors.Border,
        background = if (isEquipped) RunkkColors.LimeContainer else RunkkColors.Surface
    ) {
        Text(item.name, style = RunkkTextStyles.BodyStrong, color = RunkkColors.TextPrimary)
        RunkkBadge(
            if (isEquipped) wearing else stringResource(item.slot.labelRes),
            tone = if (isEquipped) RunkkBadgeTone.Lime else RunkkBadgeTone.Muted
        )
    }
}

/**
 * 아바타 그림: 피부톤 색 머리 + 얼굴(기본 캐릭터면 앱의 캐릭터 그림, 셀카면 서버 부위 PNG를 겹친 SD 얼굴, 받지 못했으면 글자 자리 그림)
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
    // 고른 랜드마크 배경 위에 아바타를 그린다(FR-202). 배경은 장식이라 설명에 넣지 않는다.
    Box(
        modifier = modifier
            .size(width = 150.dp, height = 210.dp)
            .clip(RunkkShapes.ExtraLarge)
            .border(2.dp, RunkkColors.Lime, RunkkShapes.ExtraLarge)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Image
            }
    ) {
        AvatarBackdrop(avatar.backgroundKey, modifier = Modifier.matchParentSize())
        AvatarFigureBody(avatar, equippedNames, faceLayers, stage)
    }
}

@Composable
private fun AvatarFigureBody(
    avatar: Avatar,
    equippedNames: Map<ItemSlot, String?>,
    faceLayers: List<FaceLayer>,
    stage: Int
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
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

/** 머리: 피부톤 원 + 얼굴(기본 캐릭터 그림, 셀카 부위 그림, 받지 못했으면 자리 글자). 모자를 쓰면 테두리를 두른다. */
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
        val placeholderText = if (avatar.isSelfieFace) "SD" else "?"
        RunkkAvatarPortrait(avatar.renderKey, faceLayers, Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    placeholderText,
                    style = RunkkTextStyles.text(12, FontWeight.Black),
                    // 피부톤 바탕은 테마와 무관하게 밝으므로 글자도 테마와 무관한 어두운 색이다(라이트 OnLime은 흰색).
                    color = PLACEHOLDER_ON_SKIN
                )
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

private val PLACEHOLDER_ON_SKIN = Color(0xFF18181B)

private fun skinColor(tone: String): Color = when (tone) {
    "DAWN" -> Color(0xFFF1D7C4)
    "MORNING" -> Color(0xFFEFC9A6)
    "DAYTIME" -> Color(0xFFE0B48F)
    "EVENING" -> Color(0xFFC99470)
    "NIGHT" -> Color(0xFFA77B5C)
    else -> Color(0xFFE0B48F)
}

/** 셀카 얼굴 그림(시험이 찾는 표지, 공용 아바타 얼굴 칸의 표지). */
internal const val FACE_TAG = RUNKK_AVATAR_FACE_TAG

private const val BODY_BASE = 48
private const val BODY_STEP = 8

@Preview(name = "J3 아바타 꾸미기", widthDp = 390, heightDp = 844)
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
