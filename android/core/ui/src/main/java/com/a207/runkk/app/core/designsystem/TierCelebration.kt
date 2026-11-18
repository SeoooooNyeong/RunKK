package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.model.TierDisplay
import com.a207.runkk.app.core.navigation.AvatarStillSlot
import com.a207.runkk.app.core.navigation.AvatarStillSubject

/**
 * 티어 축하 화면 틀(배치고사 완료 C-16, 티어 재평가 G-11 v2): 아바타 배경(내용이 있을 때), 왼쪽 위 뒤로 + 가운데 [title] 상단 바, 스크롤 본문,
 * 아래 고정 [bottomBar]. 하단 탭은 숨긴다. 본문 문구는 부르는 화면이 정한다.
 */
@Composable
fun TierCelebrationFrame(
    title: String,
    onBack: () -> Unit,
    hasBackdrop: Boolean,
    bottomBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        if (hasBackdrop) AvatarBackdrop(backgroundKey = null, modifier = Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize()) {
            RunkkTopBar(title = title, onBack = onBack, isTitleCentered = true)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
            bottomBar()
        }
    }
}

/**
 * 축하 히어로와 제목: 흰 원 빛 위 큰 티어 배지([tier]), 기뻐하는 내 전신 아바타, 축하 조각 10개, 아래 24 Bold [title]과 [subtitle](있을 때).
 * 부품은 받은 글자만 그린다(승급·유지 문장은 부르는 화면이 고른다).
 */
@Composable
fun TierCelebration(title: String, subtitle: String?, tier: String) {
    TierCelebrationHero(tier)
    Text(
        title,
        style = RunkkTextStyles.text(24, FontWeight.Bold),
        color = RunkkColors.TextPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() }
    )
    subtitle?.let {
        Text(it, style = RunkkTextStyles.Label, color = RunkkColors.TextSecondary, textAlign = TextAlign.Center)
    }
}

/**
 * 축하 히어로 350×212(피그마 61361 축하 5장): 흰 원 188(75%) 위 티어 배지 크게(엠블럼 112 + 이름), 왼쪽 내 전신 아바타 110×176, 축하 조각 10개.
 * 조각 위치·크기·각도는 피그마 7-5 그대로, 색은 라임·보라·금(podiumFirst #EBC66A). 그림은 장식이라 배지 낭독만 남긴다.
 */
@Composable
private fun TierCelebrationHero(tier: String) {
    val gold = LocalRunkkTierColors.current.podiumFirst
    Box(Modifier.size(width = HERO_WIDTH, height = HERO_HEIGHT)) {
        Box(
            Modifier
                .offset(x = 126.dp, y = 12.dp)
                .size(188.dp)
                .alpha(GLOW_ALPHA)
                .clip(CircleShape)
                // 라이트는 흰 빛, 다크는 표면색 빛(흰 빛 위 밝은 금색 티어 글자는 대비가 낮다). 둘 다 표면색이다.
                .background(RunkkColors.Surface)
        )
        CONFETTI.forEach { piece ->
            val color = when (piece.tone) {
                Confetti.Tone.LIME -> RunkkColors.LimeFill
                Confetti.Tone.VIOLET -> RunkkColors.Violet
                Confetti.Tone.GOLD -> gold
            }
            Box(
                Modifier
                    .offset(x = piece.x, y = piece.y)
                    .size(width = piece.width, height = piece.width * CONFETTI_RATIO)
                    .rotate(piece.degrees)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
        // 축하 아바타는 내 전신 정지 그림. 칸이 비면 기쁜 기본 아바타다.
        Box(Modifier.offset(x = 30.dp, y = 30.dp).clearAndSetSemantics {}) {
            AvatarStillSlot(AvatarStillSubject.MINE, Modifier.size(width = 110.dp, height = 176.dp)) {
                RunkkAvatar(null, AvatarExpression.JOY, Modifier.size(width = 110.dp, height = 176.dp))
            }
        }
        TierBadgeLarge(TierDisplay.Confirmed(tier), Modifier.offset(x = 152.dp, y = 50.dp).width(136.dp))
    }
}

/** 축하 화면 요약 카드(표면 r18 px16 py6, 그림자). 머리는 [TierCelebrationCardHeader], 행은 부르는 화면이 넣는다. */
@Composable
fun TierCelebrationCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(RunkkElevation.Medium, shape, ambientColor = RunkkColors.Shadow, spotColor = RunkkColors.Shadow)
            .clip(shape)
            .background(RunkkColors.Surface)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        content = content
    )
}

/** 요약 카드 머리: 왼쪽 13 Medium 제목(머리글), 오른쪽 12 Medium 보조 글자(없으면 비운다). */
@Composable
fun TierCelebrationCardHeader(title: String, trailing: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = RunkkTextStyles.text(13, FontWeight.Medium),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        trailing?.let {
            Text(it, style = RunkkTextStyles.text(12, FontWeight.Medium), color = RunkkColors.TextSecondary)
        }
    }
}

/**
 * 평가 결과 행(디자인 시스템 「RunKK / 평가 결과 행」 62613:6348): 왼쪽 14 Medium 이름, 오른쪽 작은 티어 엠블럼 22(있을 때)와 15 Bold 값.
 * 위아래 10.
 */
@Composable
fun TierResultRow(label: String, value: String, tier: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = RunkkTextStyles.text(14, FontWeight.Medium),
            color = RunkkColors.TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            tier?.let { TierEmblem(it, TierEmblemSize.SMALL, Modifier.size(22.dp)) }
            Text(value, style = RunkkTextStyles.text(15, FontWeight.Bold), color = RunkkColors.TextPrimary)
        }
    }
}

/**
 * Runimal 확정 카드(피그마 G-11 v2·C-16 v2): 표면 r18 위 원 그림 56, 「Runimal 확정」 12, 유형 이름 18 Bold, 한 줄 설명 12(있을 때).
 * [type]은 서버 RunimalType 이름이다.
 */
@Composable
fun TierCelebrationRunimalCard(type: String, description: String?, modifier: Modifier = Modifier) {
    val art = RunimalArt.of(type)
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(RunkkElevation.Medium, shape, ambientColor = RunkkColors.Shadow, spotColor = RunkkColors.Shadow)
            .clip(shape)
            .background(RunkkColors.Surface)
            .padding(16.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(art.drawableRes),
            contentDescription = null,
            modifier = Modifier
                .size(RUNIMAL_TILE)
                .clip(CircleShape)
                .background(RunkkColors.RunimalTile)
                .border(1.dp, RunkkColors.RunimalTileBorder, CircleShape)
                .padding(RUNIMAL_TILE_PADDING)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.core_runimal_confirmed),
                style = RunkkTextStyles.text(12),
                color = RunkkColors.TextSecondary
            )
            Text(
                stringResource(art.nameRes),
                style = RunkkTextStyles.text(18, FontWeight.Bold),
                color = RunkkColors.TextPrimary
            )
            description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = RunkkTextStyles.text(12), color = RunkkColors.TextSecondary)
            }
        }
    }
}

/** 축하 조각 하나(피그마 7-5: 위치, 폭(높이 = 폭 × 1.6), 각도, 색). */
private data class Confetti(val x: Dp, val y: Dp, val width: Dp, val degrees: Float, val tone: Tone) {
    enum class Tone { LIME, VIOLET, GOLD }
}

private val CONFETTI = listOf(
    Confetti(60.dp, 15.3.dp, 8.dp, -20f, Confetti.Tone.LIME),
    Confetti(295.9.dp, 26.dp, 10.dp, 15f, Confetti.Tone.VIOLET),
    Confetti(326.dp, 93.dp, 6.dp, -30f, Confetti.Tone.GOLD),
    Confetti(22.dp, 115.dp, 7.dp, -45f, Confetti.Tone.VIOLET),
    Confetti(263.6.dp, 170.dp, 8.dp, 30f, Confetti.Tone.LIME),
    Confetti(96.dp, 189.dp, 6.dp, -10f, Confetti.Tone.GOLD),
    Confetti(248.dp, 2.8.dp, 6.dp, -60f, Confetti.Tone.GOLD),
    Confetti(14.7.dp, 60.dp, 6.dp, 20f, Confetti.Tone.LIME),
    Confetti(332.dp, 148.2.dp, 7.dp, -15f, Confetti.Tone.LIME),
    Confetti(150.dp, 2.6.dp, 6.dp, -35f, Confetti.Tone.VIOLET)
)

private const val CONFETTI_RATIO = 1.6f
private const val GLOW_ALPHA = 0.75f
private val HERO_WIDTH = 350.dp
private val HERO_HEIGHT = 212.dp
private val RUNIMAL_TILE = 56.dp
private val RUNIMAL_TILE_PADDING = 4.dp
