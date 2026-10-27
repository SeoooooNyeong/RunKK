package com.a207.runkk.app.core.designsystem

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 실시간 점(8dp): 측정 중·실시간 대결 표시. 1.6초 주기로 바깥 고리가 퍼진다. 동작 줄이기면 멈춘 점만 그린다.
 */
@Composable
fun RunkkLiveDot(modifier: Modifier = Modifier, color: Color = RunkkColors.Lime, size: Dp = 8.dp) {
    val reduce = LocalReduceMotion.current
    val ring = if (reduce) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "livePulse")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = InfiniteRepeatableSpec(tween(PULSE_MILLIS, easing = LinearEasing), RepeatMode.Restart),
            label = "livePulseRing"
        )
        value
    }
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                if (ring > 0f && ring < PULSE_VISIBLE_UNTIL) {
                    val progress = ring / PULSE_VISIBLE_UNTIL
                    drawCircle(
                        color = color.copy(alpha = (1f - progress) * PULSE_ALPHA),
                        radius =
                            this.size.minDimension / 2 + 8.dp.toPx() * progress
                    )
                }
            }
            .clip(CircleShape)
            .background(color)
    )
}

/** 로고 종류. 다크 배경용 라임 버전만 둔다. */
enum class RunkkLogoKind(internal val aspect: Float) {
    /** 한글 워드마크(로그인, 너비 240dp). */
    WordmarkKo(1539f / 735f),

    /** RRun 워드마크(홈 상단, 높이 24dp). */
    WordmarkRrun(1817f / 596f),

    /** RR 아이콘(러닝 결과, 높이 24dp). */
    Icon(1052f / 712f)
}

/** 로고 이미지. 크기는 [width] 또는 [height] 하나로 정하고 비율은 원본을 따른다. */
@Composable
fun RunkkLogo(
    kind: RunkkLogoKind,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp? = null,
    contentDescription: String? = stringResource(R.string.core_feedback_logo)
) {
    val res = when (kind) {
        RunkkLogoKind.WordmarkKo -> R.drawable.logo_wordmark_ko_lime
        RunkkLogoKind.WordmarkRrun -> R.drawable.logo_wordmark_rrun_lime
        RunkkLogoKind.Icon -> R.drawable.logo_icon_rr_lime
    }
    val sized = when {
        width != null -> Modifier.width(width).height(width / kind.aspect)
        height != null -> Modifier.height(height).width(height * kind.aspect)
        else -> Modifier.height(24.dp).width(24.dp * kind.aspect)
    }
    // 로고 그림은 라임 한 색이다. 라이트 벌에서는 흰 바탕 대비가 낮아 지금 벌의 포인트 색으로 칠한다(FR-203).
    val tint = if (LocalRunkkColors.current.isDark) null else ColorFilter.tint(RunkkColors.Lime)
    Image(
        painter = painterResource(res),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        colorFilter = tint,
        modifier = modifier.then(sized)
    )
}

/** 불러오는 중(가운데 라임 원). 보조 기술에 [label]을 알린다. */
@Composable
fun RunkkLoading(modifier: Modifier = Modifier, label: String = stringResource(R.string.core_feedback_loading)) {
    Box(
        modifier = modifier.fillMaxSize().semantics {
            contentDescription = label
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        },
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = RunkkColors.Lime, trackColor = RunkkColors.Border, strokeWidth = 3.dp)
    }
}

/** 빈 상태: 제목 + 설명 + 행동(선택). */
@Composable
fun RunkkEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = RunkkTextStyles.CardTitle,
            color = RunkkColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        if (message !=
            null
        ) {
            Text(
                text = message,
                style = RunkkTextStyles.Body,
                color = RunkkColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        }
        if (actionLabel != null && onAction != null) {
            RunkkPillButton(text = actionLabel, onClick = onAction, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** 오류 상태: 아이콘 + 문구 + "다시 시도"(선택). 문구가 바뀌면 보조 기술이 읽는다. */
@Composable
fun RunkkErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = stringResource(R.string.core_feedback_retry)
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(RunkkIcons.Alert, contentDescription = null, tint = RunkkColors.Lavender, modifier = Modifier.size(32.dp))
        // 알림 영역은 문구에만 건다: 컨테이너에 걸면 텍스트 없는 노드라 공지가 비고, 합치면 다시 시도 버튼이 묻힌다.
        Text(
            text = message,
            style = RunkkTextStyles.Body,
            color = RunkkColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        if (onRetry != null) RunkkPillButton(text = retryLabel, onClick = onRetry)
    }
}

/** 화면 위 짧은 안내 줄(라벤더 테두리): "길게 눌러 종료" 같은 힌트. 보조 기술이 바로 읽는다. */
@Composable
fun RunkkHint(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RunkkShapes.Medium)
            .background(RunkkColors.Surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = RunkkTextStyles.text(13, FontWeight.Bold),
            color = RunkkColors.Lavender,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

private const val PULSE_MILLIS = 1_600
private const val PULSE_VISIBLE_UNTIL = 0.7f
private const val PULSE_ALPHA = 0.7f

@Preview(name = "피드백", backgroundColor = 0xFF09090B, showBackground = true, widthDp = 390)
@Composable
private fun RunkkFeedbackPreview() {
    RunkkTheme(reduceMotion = true) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RunkkLiveDot()
                Text("RUNNING · 5km", style = RunkkTextStyles.Overline, color = RunkkColors.Lime)
            }
            RunkkLogo(RunkkLogoKind.WordmarkKo, width = 240.dp)
            RunkkLogo(RunkkLogoKind.WordmarkRrun, height = 24.dp)
            RunkkLogo(RunkkLogoKind.Icon, height = 24.dp)
            Box(Modifier.height(80.dp)) { RunkkLoading() }
            RunkkEmptyState("아직 기록이 없어요", message = "첫 러닝을 시작해 보세요", actionLabel = "자유런 시작", onAction = {})
            RunkkErrorState("네트워크에 연결할 수 없어요", onRetry = {})
            RunkkHint("길게 눌러 종료")
        }
    }
}
