package com.a207.runkk.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.LocalAvatarCharacter
import com.a207.runkk.app.core.designsystem.RunkkButton
import com.a207.runkk.app.core.designsystem.RunkkButtonSize
import com.a207.runkk.app.core.designsystem.RunkkButtonStyle
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.defaultAvatarOf

/**
 * 첫 사용 가이드 시작 카드([임시:FR-성-35], 피그마 62431:1918 ⑦): 홈을 어둡게 덮고 가운데 카드에 서 있는 아바타, 제목 「런크크가
 * 처음이신가요?」, 본문(단계 수 [stepCount]가 들어간다), 라임색 큰 단추 「둘러보기」, 글자 단추 「건너뛰기」를 둔다. 카드 아래에는 흰 글자로
 * 설정에서 다시 볼 수 있다고 알린다. 막은 아래 홈으로 가는 누름을 모두 받아 버리고, 뒤로 가기는 「건너뛰기」와 같다(AN8-15).
 */
@Composable
fun FirstRunGuideStartCard(stepCount: Int, onStart: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onSkip)
    val title = stringResource(R.string.home_guide_start_title)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = DIM_ALPHA))
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag(GUIDE_START_CARD_TAG)
            // 보조 기술이 아래 홈보다 카드를 먼저 읽게 한다.
            .semantics {
                isTraversalGroup = true
                traversalIndex = -1f
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp).widthIn(max = CARD_MAX_WIDTH),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(RunkkColors.Surface)
                    .semantics { paneTitle = title }
                    .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 장식 그림(성별 기본 서기)
                Image(
                    painterResource(defaultAvatarOf(AvatarExpression.STAND, LocalAvatarCharacter.current)),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(AVATAR_HEIGHT)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    title,
                    style = RunkkTextStyles.text(20, FontWeight.Bold, lineHeight = 28),
                    color = RunkkColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.home_guide_start_body, stepCount),
                    style = RunkkTextStyles.text(14, lineHeight = 22),
                    color = RunkkColors.TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                RunkkButton(
                    stringResource(R.string.home_guide_start_tour),
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                RunkkButton(
                    stringResource(R.string.home_guide_skip),
                    onClick = onSkip,
                    style = RunkkButtonStyle.Text,
                    size = RunkkButtonSize.Small
                )
            }
            Text(
                stringResource(R.string.home_guide_start_hint),
                style = RunkkTextStyles.text(12, lineHeight = 18),
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** 시작 카드 막의 시험 표지. */
internal const val GUIDE_START_CARD_TAG = "first-run-guide-start-card"

private const val DIM_ALPHA = 0.6f
private val CARD_MAX_WIDTH = 334.dp
private val AVATAR_HEIGHT = 120.dp
