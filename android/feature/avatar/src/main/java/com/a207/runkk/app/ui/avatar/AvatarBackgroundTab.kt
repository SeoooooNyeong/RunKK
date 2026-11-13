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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.AvatarStageBackdrop
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.avatar.AvatarBackgrounds

/**
 * 아바타 꾸미기의 배경 탭(J3, FR-201, FR-202): "랜드마크 배경"과 해금 수, 기본 배경과 랜드마크 배경 3열 격자.
 * 잠긴 배경은 흐리게 자물쇠를 얹고 해금 거리를 적는다. 누르면 해금 거리를 안내한다(AvatarBackgroundViewModel).
 */
@Composable
internal fun AvatarBackgroundTab(state: AvatarBackgroundUiState, actions: AvatarScreenActions) {
    val backgrounds = state.backgrounds
    when {
        backgrounds == null && state.isLoading -> RunkkLoading()

        backgrounds == null -> RunkkErrorState(
            state.error?.asString() ?: stringResource(R.string.avatar_background_load_failed)
        )

        else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.avatar_background_landmarks),
                    style = RunkkTextStyles.Label,
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(
                        R.string.avatar_background_unlocked_count,
                        backgrounds.items.count { it.isUnlocked },
                        backgrounds.items.size
                    ),
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.TextSecondary
                )
            }
            BackgroundGrid(state, backgrounds, actions)
        }
    }
}

/** 기본 배경 + 랜드마크 배경, 3열. 라디오 묶음으로 알린다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackgroundGrid(
    state: AvatarBackgroundUiState,
    backgrounds: AvatarBackgrounds,
    actions: AvatarScreenActions
) {
    val tiles = buildList {
        add(
            BackgroundTileState(
                key = null,
                stage = null,
                name = stringResource(R.string.avatar_background_default),
                isSelected = backgrounds.selectedKey == null,
                lockedDistance = null,
                isChoosing = state.choosingKey == AvatarBackgroundUiState.DEFAULT_KEY
            ) to actions.onChooseDefaultBackground
        )
        backgrounds.items.forEach { item ->
            add(
                BackgroundTileState(
                    key = item.key,
                    stage = item.stage,
                    name = item.name,
                    isSelected = backgrounds.selectedKey == item.key,
                    lockedDistance = item.remaining.takeUnless { item.isUnlocked },
                    isChoosing = state.choosingKey == item.key
                ) to { actions.onChooseBackground(item) }
            )
        }
    }
    FlowRow(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = COLUMNS
    ) {
        tiles.forEach { (tile, onClick) -> BackgroundTile(tile, onClick, Modifier.weight(1f)) }
        // 마지막 줄 칸 너비를 맞춘다.
        repeat((COLUMNS - tiles.size % COLUMNS) % COLUMNS) { Spacer(Modifier.weight(1f)) }
    }
}

/**
 * 배경 한 칸: 그림과 "이름" 또는 "이름, 사용 중". 잠긴 배경은 "이름, n km 더 달리면 해금"(남은 거리, W8).
 * 보조 기술에는 라디오 단추로 알리고 상태(사용 중·잠김)를 읽어 준다.
 */
@Composable
private fun BackgroundTile(tile: BackgroundTileState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (key, stage, name, isSelected, lockedDistance, isChoosing) = tile
    val isLocked = lockedDistance != null
    val label = when {
        isSelected -> stringResource(R.string.avatar_background_selected, name)

        lockedDistance != null && lockedDistance.meters <= 0 -> stringResource(
            R.string.avatar_background_locked_soon,
            name
        )

        lockedDistance != null -> stringResource(R.string.avatar_background_locked, name, remainingKm(lockedDistance))

        else -> name
    }
    Column(
        modifier = modifier
            .clip(RunkkShapes.Medium)
            .background(if (isLocked) RunkkColors.SurfaceHigh else RunkkColors.Surface)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) RunkkColors.LimeFill else RunkkColors.Border,
                shape = RunkkShapes.Medium
            )
            .selectable(selected = isSelected, enabled = !isChoosing, role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = name
                stateDescription = label
            }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 피그마 H-3: 세로 그림 95x126(모서리 12). 잠긴 배경도 그림을 불투명하게 그대로 보이고 가운데에 자물쇠 배지를 얹는다
        // (2026-11-12 PM 요구: 무엇이 열릴지 보이게).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(BACKDROP_RATIO)
                .clip(RunkkShapes.Small)
                .background(RunkkColors.SurfaceHigh),
            contentAlignment = Alignment.Center
        ) {
            AvatarStageBackdrop(key, modifier = Modifier.matchParentSize(), stage)
            if (isLocked) {
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(RunkkColors.Scrim),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        RunkkIcons.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Text(
            label,
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary,
            textAlign = TextAlign.Center,
            // 칸의 이름·상태로 읽으므로 보이는 글자는 두 번 읽지 않는다.
            modifier = Modifier.clearAndSetSemantics {}
        )
    }
}

private const val COLUMNS = 3

private const val BACKDROP_RATIO = 95f / 126f
private const val METERS_PER_KM = 1_000L

/** 남은 거리를 km로 올림한다(0.2km 남아도 "1km 더"). */
internal fun remainingKm(distance: Distance): Long = (distance.meters + METERS_PER_KM - 1) / METERS_PER_KM
