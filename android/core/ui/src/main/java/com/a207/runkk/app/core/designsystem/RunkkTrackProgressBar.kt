package com.a207.runkk.app.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.model.RunViewMode

/**
 * 진행 막대의 점 하나([임시:D-성-268]): 목표 거리에서의 자리 [fraction](0~1), 내 점인가 [isMine](라임, 아니면 보라), 그 사람의 얼굴 키
 * [faceKey](지금은 색 점만 그리고 얼굴은 그리지 않는다. 시안이 색 점이다).
 */
@Immutable
data class ProgressDot(val fraction: Float, val isMine: Boolean, val faceKey: String? = null)

/**
 * 트랙·지도 카드 위 진행 막대([임시:D-성-268], [임시:FR-성-27]): 윗줄 왼쪽 [leading](「2.34 / 5 km」)·오른쪽 [trailing](「남은 거리
 * 2.66 km」), 그 아래 막대 위에 [dots](대결은 나와 상대 둘, 크루런은 참가자 모두)와 오른쪽 끝 깃발. 내 점은 다른 점 위에 그린다.
 * 단일 값 막대 [RunkkProgressBar]와 달리 점이 여럿이다. 카드 위에 겹쳐 놓으므로 표면색 반투명 바탕을 깐다.
 */
@Composable
fun RunkkTrackProgressBar(
    dots: List<ProgressDot>,
    modifier: Modifier = Modifier,
    leading: String? = null,
    trailing: String? = null
) {
    Column(
        modifier = modifier
            .clip(RunkkShapes.Medium)
            .background(RunkkColors.Surface.copy(alpha = BAR_BACKGROUND_ALPHA))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (leading != null || trailing != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    leading.orEmpty(),
                    style = RunkkTextStyles.text(12, FontWeight.Bold),
                    color = RunkkColors.TextPrimary,
                    maxLines = 1
                )
                Text(
                    trailing.orEmpty(),
                    style = RunkkTextStyles.text(12),
                    color = RunkkColors.TextSecondary,
                    maxLines = 1
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BoxWithConstraints(Modifier.weight(1f).height(DOT_SIZE), contentAlignment = Alignment.CenterStart) {
                val span = maxWidth - DOT_SIZE
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RunkkShapes.Pill).background(RunkkColors.Border))
                // 내 점이 맨 위에 오게 나중에 그린다.
                dots.sortedBy { it.isMine }.forEach { dot ->
                    val reduceMotion = LocalReduceMotion.current
                    val at by animateFloatAsState(
                        targetValue = dot.fraction.coerceIn(0f, 1f),
                        animationSpec = if (reduceMotion) snap() else tween(DOT_MOVE_MILLIS),
                        label = "trackDot"
                    )
                    Box(
                        Modifier
                            .offset(x = span * at)
                            .size(DOT_SIZE)
                            .clip(CircleShape)
                            .background(if (dot.isMine) RunkkColors.Lime else RunkkColors.Lavender)
                            .border(1.dp, RunkkColors.Surface, CircleShape)
                            .testTag(if (dot.isMine) TRACK_DOT_MINE_TAG else TRACK_DOT_OTHER_TAG)
                    )
                }
            }
            Icon(
                RunkkIcons.Flag,
                contentDescription = null,
                tint = RunkkColors.TextPrimary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * 「트랙 | 지도」 보기 전환([임시:D-성-313], [임시:FR-성-28]): 고른 칸은 진한 알약이다. 무엇을 그릴지는 화면이 [selected]로 고르고,
 * 지도를 띄울 수 없으면 화면이 이 전환을 그리지 않는다.
 */
@Composable
fun RunkkViewModeToggle(selected: RunViewMode, onSelect: (RunViewMode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RunkkShapes.Pill)
            .background(RunkkColors.Surface.copy(alpha = BAR_BACKGROUND_ALPHA))
            .padding(2.dp)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RunViewMode.entries.forEach { mode ->
            val isSelected = mode == selected
            val label = stringResource(
                if (mode ==
                    RunViewMode.TRACK
                ) {
                    R.string.track_view_track
                } else {
                    R.string.track_view_map
                }
            )
            Box(
                modifier = Modifier
                    .testTag(viewModeTag(mode))
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(mode) })
                    .padding(vertical = (TOGGLE_TOUCH - TOGGLE_PILL) / 2),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = RunkkTextStyles.text(12, if (isSelected) FontWeight.Bold else FontWeight.Normal),
                    color = if (isSelected) RunkkColors.Background else RunkkColors.TextPrimary,
                    maxLines = 1,
                    modifier = Modifier
                        .height(TOGGLE_PILL)
                        .clip(RunkkShapes.Pill)
                        .background(if (isSelected) RunkkColors.TextPrimary else Color.Transparent)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/** 보기 전환 한 칸의 시험 표지. */
fun viewModeTag(mode: RunViewMode): String = "view-mode-${mode.name}"

const val TRACK_DOT_MINE_TAG = "track-dot-mine"
const val TRACK_DOT_OTHER_TAG = "track-dot-other"

private const val BAR_BACKGROUND_ALPHA = 0.88f
private const val DOT_MOVE_MILLIS = 300
private val DOT_SIZE = 12.dp
private val TOGGLE_PILL = 28.dp
private val TOGGLE_TOUCH = 48.dp
