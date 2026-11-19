package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** 시작 로딩 화면 진행 막대의 시험 표식. */
const val START_LOADING_BAR_TAG = "start_loading_bar"

/**
 * 시작 로딩 화면(S15P31A207-345): 창 배경 위 가운데 "런크크" 워드마크([RunkkHoppingWordmark], 한 번 뛰고 [onIntroDone]), 그 아래
 * 얇은 진행 막대와 작은 퍼센트 글자. 막대·글자는 워드마크 아래 고정 자리에 겹쳐 그려 나타나도 워드마크가 움직이지 않는다.
 *
 * - [percent]: 받는 중이면 0~100, 받을 것을 아직 모르거나 없으면 null(막대 없음).
 * - [isFailed]: 자동 재시도까지 받지 못했다. 막대 대신 「에셋을 받지 못했어요」 안내와 「다시 시도」([onRetry])를 보인다.
 */
@Composable
fun RunkkStartLoading(
    percent: Int?,
    isFailed: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onIntroDone: () -> Unit = {}
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(RunkkColors.Background),
        contentAlignment = Alignment.Center
    ) {
        RunkkHoppingWordmark(width = WORDMARK_WIDTH, onFinished = onIntroDone)
        // 막대·안내의 윗변을 화면 가운데(워드마크 가운데)에서 [BELOW_TOP] 아래에 둔다(안내가 길어도 워드마크와 겹치지 않는다).
        val below = Modifier.align(Alignment.TopCenter).padding(top = maxHeight / 2 + BELOW_TOP)
        when {
            isFailed -> Failed(onRetry, below)
            percent != null -> Progress(percent, below)
        }
    }
}

@Composable
private fun Progress(percent: Int, modifier: Modifier) {
    Column(
        modifier = modifier.width(WORDMARK_WIDTH),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RunkkProgressBar(
            progress = percent.coerceIn(0, FULL) / FULL.toFloat(),
            modifier = Modifier.testTag(START_LOADING_BAR_TAG),
            height = 4.dp
        )
        Text(
            text = stringResource(R.string.core_start_loading_percent, percent.coerceIn(0, FULL)),
            style = RunkkTextStyles.text(12, FontWeight.Medium, lineHeight = 18),
            color = RunkkColors.TextSecondary
        )
    }
}

@Composable
private fun Failed(onRetry: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.width(FAILED_WIDTH),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.core_start_loading_failed),
            style = RunkkTextStyles.text(15, FontWeight.Bold, lineHeight = 22),
            color = RunkkColors.TextPrimary,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.core_start_loading_failed_hint),
            style = RunkkTextStyles.text(13, lineHeight = 20),
            color = RunkkColors.TextSecondary,
            textAlign = TextAlign.Center
        )
        RunkkPrimaryButton(
            text = stringResource(R.string.core_start_loading_retry),
            onClick = onRetry,
            size = RunkkButtonSize.Medium
        )
    }
}

private val WORDMARK_WIDTH = 200.dp

/** 막대·안내 칸 윗변을 화면 가운데에서 내리는 거리(워드마크 높이 절반 약 48 + 간격 40). */
private val BELOW_TOP = 88.dp
private val FAILED_WIDTH = 260.dp
private const val FULL = 100
