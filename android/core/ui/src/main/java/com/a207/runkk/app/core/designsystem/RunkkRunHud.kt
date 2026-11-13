package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 측정 화면 큰 거리(「2.14」 96 + 오른쪽 아래 「km」). 보조 기술에는 [spoken]("2.14킬로미터") 하나로 읽힌다. 단위 글자·색·아래 띄움은
 * [tone]이 정한다([RunkkHudTone.Run] 18 보조색·아래 12, [RunkkHudTone.Crew] 20/24 본문색). 크루런 칸 최소 높이(82)·위 8은 부르는 쪽이
 * [modifier]로 둔다.
 */
@Composable
fun RunkkRunDistance(
    distance: String,
    unit: String,
    spoken: String,
    modifier: Modifier = Modifier,
    tone: RunkkHudTone = RunkkHudTone.Run
) {
    val isRun = tone == RunkkHudTone.Run
    Row(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(distance, style = RunkkTextStyles.DisplayHuge, color = RunkkColors.TextPrimary)
        Text(
            unit,
            style = if (isRun) RunkkTextStyles.text(18) else RunkkTextStyles.text(20, lineHeight = 24),
            color = if (isRun) RunkkColors.TextSecondary else RunkkColors.TextPrimary,
            modifier = Modifier.padding(start = 4.dp, bottom = if (isRun) 12.dp else 0.dp)
        )
    }
}

/**
 * 측정 화면 지표 줄(시간·페이스·심박). [RunkkHudTone.Run]은 값 28·라벨 12/18 보조색을 고르게 띄워 놓고(SpaceEvenly),
 * [RunkkHudTone.Crew]는 3등분 칸에 값 26·라벨 12/14 본문색(사이 2)이다. 칸마다 "라벨 읽을 값"으로 한 번에 읽힌다.
 */
@Composable
fun RunkkRunStats(items: List<RunkkRunStat>, modifier: Modifier = Modifier, tone: RunkkHudTone = RunkkHudTone.Run) {
    if (tone == RunkkHudTone.Run) {
        Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            items.forEach { item ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        item.spoken?.let { contentDescription = "${item.label} $it" }
                    }
                ) {
                    Text(item.value, style = RunkkTextStyles.display(28), color = RunkkColors.TextPrimary)
                    Text(item.label, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
                }
            }
        }
        return
    }
    Row(modifier = modifier.fillMaxWidth()) {
        items.forEach { item ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = "${item.label} ${item.spoken ?: item.value}" }
            ) {
                Text(
                    item.value,
                    style = RunkkTextStyles.display(26, lineHeightRatio = CREW_STAT_LINE_RATIO),
                    color = RunkkColors.TextPrimary
                )
                Text(item.label, style = RunkkTextStyles.text(12, lineHeight = 14), color = RunkkColors.TextPrimary)
            }
        }
    }
}

/**
 * 측정 화면 둥근 단추(취소 ✕ 48·대결 취소 56·일시정지 88): 지름 [size]의 [shape](기본 원, 자유런 일시정지는
 * `CutCornerShape(percent = 30)`) [background] 칸 가운데 [icon]([iconSize], 기본 지름의 1/3). [caption]이 있으면 아래 8에 12/18
 * 글자를 둔다(보조 기술은 [contentDescription]으로 읽는다). [isEnabled]가 거짓이면 흐리게 두고 누를 수 없다.
 */
@Composable
fun RunkkRoundControl(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    iconSize: Dp = size / ICON_RATIO,
    background: Color = RunkkColors.Surface,
    tint: Color = RunkkColors.TextPrimary,
    shape: Shape = CircleShape,
    borderColor: Color = Color.Transparent,
    borderWidth: Dp = 0.dp,
    caption: String? = null,
    captionColor: Color = RunkkColors.TextSecondary,
    isEnabled: Boolean = true
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(size)
                .then(if (isEnabled) Modifier else Modifier.alpha(RunkkAlpha.Disabled))
                .clip(shape)
                .background(background)
                .then(if (borderWidth > 0.dp) Modifier.border(borderWidth, borderColor, shape) else Modifier)
                .clickable(
                    enabled = isEnabled,
                    role = Role.Button,
                    onClickLabel = contentDescription,
                    onClick = onClick
                )
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
        }
        if (caption != null) {
            Text(
                caption,
                style = RunkkTextStyles.Caption,
                color = captionColor,
                modifier = Modifier.padding(top = 8.dp).clearAndSetSemantics {}
            )
        }
    }
}

/**
 * 길게 눌러 종료 단추([RunkkLongPressButton]) 안의 정지 네모: 자유런 32·모서리 8 본문색(기본), 대결 28·8, 크루런 28·7
 * [RunkkColors.OnLimeFill].
 */
@Composable
fun RunkkStopSquare(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    corner: Dp = 8.dp,
    color: Color = RunkkColors.TextPrimary
) {
    Box(modifier.size(size).clip(RoundedCornerShape(corner)).background(color))
}

/**
 * 일시정지 덮개: 화면 가득 [RunkkColors.Scrim](검정 0.45) 위 아래 가운데에 [content](종료·재개 단추 줄). 보조 기술에는 [paneTitle]
 * ("일시정지") 화면으로 알린다. 안쪽 여백은 [tone]이 정한다(자유런 좌우 16·아래 32, 크루런 좌우 24·아래 36).
 */
@Composable
fun RunkkPauseScrim(
    paneTitle: String,
    modifier: Modifier = Modifier,
    tone: RunkkHudTone = RunkkHudTone.Run,
    content: @Composable BoxScope.() -> Unit
) {
    val padding = if (tone == RunkkHudTone.Run) {
        PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)
    } else {
        PaddingValues(start = 24.dp, end = 24.dp, bottom = 36.dp)
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(RunkkColors.Scrim)
            .semantics { this.paneTitle = paneTitle }
            .padding(padding),
        contentAlignment = Alignment.BottomCenter,
        content = content
    )
}

/** 크루런 지표 값 줄 높이(피그마 31/26). */
private const val CREW_STAT_LINE_RATIO = 31f / 26f

/** 둥근 단추 아이콘 = 지름 / 3. */
private const val ICON_RATIO = 3
