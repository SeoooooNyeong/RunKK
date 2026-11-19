package com.a207.runkk.app.core.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * 달리기 화면 위에 겹쳐 띄우는 안내 띠(추월·역전·종료 카운트다운, S15P31A207-334). 바탕은 테마와 상관없이 흰색(라이트 표면색)이고 글자는
 * 진한 본문색, 모서리 16, 옅은 그림자(6)다. [tone]이 왼쪽 동그라미 아이콘과 테두리 색을 정한다(추월 라임, 역전·카운트다운 보라).
 * 나타나면 보조 기술이 바로 읽는다(Polite). [spoken]을 주면 보이는 글자(바뀌는 초) 대신 그 고정 문구를 즉시(Assertive) 한 번 알린다.
 */
@Composable
fun RunkkGuidanceToast(text: String, tone: RunkkGuidanceTone, modifier: Modifier = Modifier, spoken: String? = null) {
    val palette = RunkkColors.light
    val accent = if (tone == RunkkGuidanceTone.Lead) palette.limeFill else palette.violet
    val onAccent = if (tone == RunkkGuidanceTone.Lead) palette.onLimeFill else palette.onSecondary
    val icon = when (tone) {
        RunkkGuidanceTone.Lead -> RunkkIcons.ChevronUp
        RunkkGuidanceTone.Rival -> RunkkIcons.ChevronDown
        RunkkGuidanceTone.Closing -> RunkkIcons.Clock
    }
    val announce = if (spoken == null) {
        Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
    } else {
        Modifier.clearAndSetSemantics {
            contentDescription = spoken
            liveRegion = LiveRegionMode.Assertive
        }
    }
    Row(
        modifier = modifier
            .testTag(RUNKK_GUIDANCE_TOAST_TAG)
            .shadow(
                RunkkElevation.Medium,
                RunkkShapes.Medium,
                ambientColor = palette.shadow,
                spotColor = palette.shadow
            )
            .clip(RunkkShapes.Medium)
            .background(palette.surface)
            .border(GUIDANCE_BORDER, accent, RunkkShapes.Medium)
            .then(announce)
            .padding(start = 12.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = onAccent, modifier = Modifier.size(18.dp))
        }
        Text(text = text, style = RunkkTextStyles.text(15, FontWeight.Black), color = palette.textPrimary)
    }
}

/**
 * 안내 띠를 띄우는 겹침 층. [notice]가 있으면 [content]를 위 가운데에 그리고, 없으면 아무것도 그리지 않는다. 부르는 쪽이 이 층을
 * 다른 요소 위에 겹쳐 두므로(자리를 차지하지 않는 칸) 안내가 뜨고 사라져도 다른 요소의 자리·크기가 바뀌지 않는다. 나타날 때 위에서 반쯤
 * 내려오며 짙어지고(200ms), 사라질 때 거꾸로 간다(150ms). 움직임 줄이기면 바로 바뀐다. [contentKey]가 같으면(예: 초만 바뀐 종료
 * 카운트다운) 다시 움직이지 않는다.
 */
@Composable
fun <T : Any> RunkkGuidanceOverlay(
    notice: T?,
    modifier: Modifier = Modifier,
    contentKey: (T) -> Any = { it },
    content: @Composable (T) -> Unit
) {
    if (LocalReduceMotion.current) {
        Box(modifier, contentAlignment = Alignment.TopCenter) { notice?.let { content(it) } }
        return
    }
    AnimatedContent(
        targetState = notice,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(ENTER_MILLIS)) + slideInVertically(tween(ENTER_MILLIS)) { -it / 2 }) togetherWith
                (fadeOut(tween(EXIT_MILLIS)) + slideOutVertically(tween(EXIT_MILLIS)) { -it / 2 }) using
                SizeTransform(clip = false)
        },
        contentAlignment = Alignment.TopCenter,
        contentKey = { it?.let(contentKey) },
        label = "guidance"
    ) { shown -> shown?.let { content(it) } }
}

/** 안내 띠의 시험 표지. */
const val RUNKK_GUIDANCE_TOAST_TAG = "runkk-guidance-toast"

private val GUIDANCE_BORDER = 2.dp
private const val ENTER_MILLIS = 200
private const val EXIT_MILLIS = 150

/** 안내 띠 미리보기 배경(다크 화면). 띠는 다크에서도 흰색이다. */
private val PREVIEW_BACKGROUND = Color(0xFF09090B)

@Preview(name = "안내 띠", backgroundColor = 0xFF09090B, showBackground = true, widthDp = 390)
@Composable
private fun RunkkGuidanceToastPreview() {
    RunkkTheme(darkTheme = true, reduceMotion = true) {
        Column(
            Modifier.background(PREVIEW_BACKGROUND).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RunkkGuidanceToast("추월했어요!", RunkkGuidanceTone.Lead)
            RunkkGuidanceToast("역전당했어요", RunkkGuidanceTone.Rival)
            RunkkGuidanceToast("상대 도착! 27초 안에 도착하지 못하면 대결이 끝나요", RunkkGuidanceTone.Closing, spoken = "상대 도착")
        }
    }
}
