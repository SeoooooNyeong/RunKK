package com.a207.runkk.app.core.designsystem

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** 시작 로딩 화면 진행 막대의 시험 표식. */
const val START_LOADING_BAR_TAG = "start_loading_bar"

/** 시작 로딩 화면 상태 문구의 시험 표식(live region). */
const val START_LOADING_MESSAGE_TAG = "start_loading_message"

/**
 * 시작 로딩 화면(S15P31A207-345): 창 배경 위 가운데 "런크크" 워드마크([RunkkHoppingWordmark], 한 번 뛰고 [onIntroDone]), 그 아래 상태
 * 문구와 얇은 진행 막대·작은 퍼센트 글자. 아래 칸은 워드마크 아래 고정 자리에 겹쳐 그려 나타나도 워드마크가 움직이지 않는다.
 *
 * - [message]: 지금 보일 상태 문구(고르기·유지 시간은 부르는 쪽, [rememberStartLoadingMessage]). null이면 문구 없음. 바뀌면 짧게
 *   겹쳐 바뀐다(움직임 줄이기면 바로). 화면 읽기 프로그램에는 정중한(polite) 알림 영역으로 알린다.
 * - [isFirstInstall]: 처음 설치 때만 상태 문구 아래 작고 옅게 「처음 실행을 준비하고 있어요」를 보인다.
 * - [percent]: 받는 중이면 0~100, 받을 것을 아직 모르거나 없으면 null(막대 없음).
 * - [isFailed]: 자동 재시도까지 받지 못했다. 막대·문구 대신 「준비를 마치지 못했어요」 안내와 「다시 시도」([onRetry])를 보인다.
 */
@Composable
fun RunkkStartLoading(
    percent: Int?,
    isFailed: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    message: StartLoadingMessage? = null,
    isFirstInstall: Boolean = false,
    onIntroDone: () -> Unit = {}
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(RunkkColors.Background),
        contentAlignment = Alignment.Center
    ) {
        RunkkHoppingWordmark(width = WORDMARK_WIDTH, onFinished = onIntroDone)
        // 아래 칸의 윗변을 화면 가운데(워드마크 가운데)에서 [BELOW_TOP] 아래에 둔다(안내가 길어도 워드마크와 겹치지 않는다).
        val below = Modifier.align(Alignment.TopCenter).padding(top = maxHeight / 2 + BELOW_TOP)
        when {
            isFailed -> Failed(onRetry, below)
            percent != null || message != null -> Progress(percent, message, isFirstInstall, below)
        }
    }
}

@Composable
private fun Progress(percent: Int?, message: StartLoadingMessage?, isFirstInstall: Boolean, modifier: Modifier) {
    Column(
        modifier = modifier.width(PROGRESS_WIDTH),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Status(message, isFirstInstall)
        if (percent != null) {
            Spacer(Modifier.height(16.dp))
            RunkkProgressBar(
                progress = percent.coerceIn(0, FULL) / FULL.toFloat(),
                modifier = Modifier.width(WORDMARK_WIDTH).testTag(START_LOADING_BAR_TAG),
                height = 4.dp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.core_start_loading_percent, percent.coerceIn(0, FULL)),
                style = RunkkTextStyles.text(12, FontWeight.Medium, lineHeight = 18),
                color = RunkkColors.TextSecondary
            )
        }
    }
}

/** 상태 문구(정중한 알림 영역)와 처음 설치 안내. 문구가 없으면 자리를 비운다. */
@Composable
private fun Status(message: StartLoadingMessage?, isFirstInstall: Boolean) {
    val fade = if (LocalReduceMotion.current) snap() else tween<Float>(FADE_MILLIS)
    Crossfade(targetState = message, animationSpec = fade, label = "startLoadingMessage") { shown ->
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            if (shown != null) {
                Text(
                    text = stringResource(shown.text),
                    style = RunkkTextStyles.text(14, FontWeight.Medium, lineHeight = 20),
                    color = RunkkColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .testTag(START_LOADING_MESSAGE_TAG)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                )
                if (isFirstInstall) {
                    Text(
                        text = stringResource(R.string.core_start_loading_first_install_hint),
                        style = RunkkTextStyles.text(12, lineHeight = 18),
                        color = RunkkColors.TextSecondary.copy(alpha = HINT_ALPHA),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
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
            text = stringResource(R.string.core_start_loading_failed_title),
            style = RunkkTextStyles.text(15, FontWeight.Bold, lineHeight = 22),
            color = RunkkColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Text(
            text = stringResource(R.string.core_start_loading_failed_body),
            style = RunkkTextStyles.text(13, lineHeight = 20),
            color = RunkkColors.TextSecondary,
            textAlign = TextAlign.Center
        )
        RunkkPrimaryButton(
            text = stringResource(R.string.core_start_loading_failed_button),
            onClick = onRetry,
            size = RunkkButtonSize.Medium
        )
    }
}

private val WORDMARK_WIDTH = 200.dp

/** 상태 문구 칸 너비(가장 긴 문구 「기록, 크루, 친구를 불러오고 있어요」가 한 줄에 든다). */
private val PROGRESS_WIDTH = 280.dp

/** 아래 칸 윗변을 화면 가운데에서 내리는 거리(워드마크 높이 절반 약 48 + 간격 40). */
private val BELOW_TOP = 88.dp
private val FAILED_WIDTH = 260.dp
private const val FULL = 100

/** 문구가 바뀔 때 겹쳐 바뀌는 시간. */
private const val FADE_MILLIS = 200

/** 처음 설치 안내의 옅은 정도. */
private const val HINT_ALPHA = 0.7f
