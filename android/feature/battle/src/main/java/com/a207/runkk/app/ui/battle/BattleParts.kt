package com.a207.runkk.app.ui.battle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.CelebrityFigure
import com.a207.runkk.app.core.designsystem.RivalAvatar
import com.a207.runkk.app.core.designsystem.RunkkAvatar
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkLiveDot
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.navigation.AvatarStillSlot
import com.a207.runkk.app.core.navigation.AvatarStillSubject
import com.a207.runkk.app.domain.account.MyAvatar
import java.time.Duration
import java.time.LocalDate

/**
 * 상대 그림 종류(D-254): 기본 아바타, 반투명 기본 아바타(과거의 나), 셀럽 그림(전용 캐릭터, 키가 없으면 공통 SD), 배치고사 상대(점선 원
 * 「?」, FR-276 J24-3).
 */
internal enum class Figure { AVATAR, RIVAL, PAST_SELF, CELEBRITY, UNKNOWN }

/**
 * 한쪽 대결자 카드: 그림 칸, 이름, 티어 칩(있으면), 기록 줄(있으면). 그림은 장식이라 읽기 정보는 이름·줄이 맡는다.
 * [character]는 상대 그림(RIVAL)의 남녀(상대 성별)이고 [celebrityFigure]는 셀럽 그림(CELEBRITY)의 키다.
 */
@Composable
internal fun Contender(
    name: String,
    modifier: Modifier = Modifier,
    figure: Figure = Figure.AVATAR,
    tier: String? = null,
    caption: String? = null,
    character: AvatarCharacter = AvatarCharacter.MALE,
    celebrityFigure: String? = null
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FigureCard(figure, character, celebrityFigure)
        Text(
            name,
            style = RunkkTextStyles.text(16, FontWeight.Black),
            color = RunkkColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        if (tier != null) {
            Text(
                tier,
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextPrimary,
                modifier = Modifier
                    .clip(RunkkShapes.ExtraLarge)
                    .background(RunkkColors.Surface)
                    .border(1.dp, RunkkColors.Border, RunkkShapes.ExtraLarge)
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            )
        }
        if (caption != null) Text(caption, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
    }
}

@Composable
private fun FigureCard(figure: Figure, character: AvatarCharacter, celebrityFigure: String?) {
    // 피그마 C-3, D-2, E-2: 그림 뒤에 연두색 카드를 두지 않는다
    Box(
        Modifier
            .size(width = 104.dp, height = 112.dp)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        OpponentFigure(figure, size = 96.dp, character = character, celebrityFigure = celebrityFigure)
    }
}

/**
 * 대결자 그림 하나. 나는 내 아바타([myAvatar]가 있으면 홈과 같은 얼굴, 없으면 기본 아바타), 배틀런 상대는 상대 성별([character])의 상대 그림, 과거의 나는 반투명 기본 아바타, 셀럽은
 * 키([celebrityFigure])의 전용 캐릭터이고 키가 없으면 공통 흐린 그림이다(D-254, D73·D74, 사용자 결정 2026-10-31).
 */
@Composable
internal fun OpponentFigure(
    figure: Figure,
    size: Dp,
    expression: AvatarExpression = AvatarExpression.STAND,
    colorFilter: ColorFilter? = null,
    character: AvatarCharacter = AvatarCharacter.MALE,
    celebrityFigure: String? = null,
    myAvatar: MyAvatar? = null
) {
    when (figure) {
        Figure.AVATAR -> StillOr(MY_STILL, size, colorFilter) {
            RunkkAvatar(
                myAvatar?.faceKey,
                expression,
                Modifier.size(size),
                myAvatar?.faceLayers.orEmpty(),
                colorFilter = colorFilter
            )
        }

        Figure.RIVAL -> StillOr(
            AvatarStillSubject(isMine = false, isFemale = character == AvatarCharacter.FEMALE),
            size,
            colorFilter
        ) {
            RivalAvatar(Modifier.size(size), colorFilter, character)
        }

        Figure.PAST_SELF -> RunkkAvatar(
            null,
            AvatarExpression.STAND,
            Modifier.size(size).alpha(PAST_SELF_ALPHA),
            colorFilter = colorFilter
        )

        Figure.CELEBRITY -> CelebrityFigure(size = size, figureKey = celebrityFigure, colorFilter = colorFilter)

        Figure.UNKNOWN -> QuestionCircle(size = size * UNKNOWN_CIRCLE_RATIO, color = RunkkColors.TextSecondary)
    }
}

/**
 * 대결 화면의 전신 정지 그림 칸(2026-11-12 PM 요구: 배틀런 친구대결·랜덤대결도 얼굴 대신 아바타 몸체). 색 거르개를 쓰는 곳(흐림·흑백)은
 * 정지 그림에 거르개를 줄 수 없어 지금 그림 그대로 둔다.
 */
@Composable
internal fun StillOr(
    subject: AvatarStillSubject,
    size: Dp,
    colorFilter: ColorFilter?,
    fallback: @Composable () -> Unit
) {
    if (colorFilter != null) {
        fallback()
    } else {
        AvatarStillSlot(subject, Modifier.size(size), fallback)
    }
}

/** 내 전신 정지 그림(의상·체형·러닝량·셀카 머리 그대로). */
internal val MY_STILL = AvatarStillSubject(isMine = true)

/** 나 VS 상대 한 줄. */
@Composable
internal fun Versus(mine: @Composable (Modifier) -> Unit, theirs: @Composable (Modifier) -> Unit) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        mine(Modifier.weight(1f))
        Text(
            "VS",
            style = RunkkTextStyles.display(32),
            color = RunkkColors.TextSecondary,
            modifier = Modifier.padding(top = 40.dp).clearAndSetSemantics {}
        )
        theirs(Modifier.weight(1f))
    }
}

/**
 * 자동 출발 고리(C-3·D-2·E-2): 큰 원 안의 남은 초와 "초 뒤 자동으로 출발해요". 고리는 [total]초 가운데 남은 몫만큼 그린다.
 * 남은 초를 모르면(출발 예약 전) 안내 문구만 보인다.
 */
@Composable
internal fun StartRing(seconds: Long?, total: Long, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (seconds != null) {
            val sweep = (seconds.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)
            // 바깥 원은 채움이라 라이트에서도 라임이다(D85).
            val outer = RunkkColors.LimeFill
            val ring = RunkkColors.LimeContainer
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .drawBehind {
                        drawCircle(outer)
                        val inset = 12.dp.toPx()
                        drawArc(
                            color = ring,
                            startAngle = -90f,
                            sweepAngle = 360f * sweep,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - 2 * inset, size.height - 2 * inset),
                            style = Stroke(width = 6.dp.toPx())
                        )
                        drawCircle(ring, radius = size.minDimension / 2 - 24.dp.toPx())
                    }
                    .semantics { contentDescription = "$seconds" },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$seconds",
                    style = RunkkTextStyles.display(88, lineHeightRatio = 1f).copy(fontStyle = FontStyle.Italic),
                    color = RunkkColors.TextPrimary
                )
            }
            Text(
                stringResource(R.string.battle_match_starts_in_suffix),
                style = RunkkTextStyles.Body,
                color = RunkkColors.TextPrimary
            )
        } else {
            Text(
                stringResource(R.string.battle_match_starts_soon),
                style = RunkkTextStyles.BodyStrong,
                color = RunkkColors.TextPrimary
            )
        }
    }
}

/** "5km 최고 30:12"(기록이 없으면 null: 그 칸을 비운다, D-273). */
@Composable
internal fun bestLine(distanceLabel: String, best: Duration?): String? =
    best?.let { stringResource(R.string.battle_best_line, distanceLabel, RunkkFormat.duration(it)) }

/** "10월 2일"(배틀런 카드 날짜, 과거의 나 날짜). */
internal fun monthDay(date: LocalDate): String = "${date.monthValue}월 ${date.dayOfMonth}일"

/**
 * 이름 뒤 조사(과/와, 을/를 등): 마지막 글자가 받침 있는 한글이면 [withFinal], 아니면 [withoutFinal]. 한글이 아니면 받침 없음으로 본다.
 */
internal fun particle(name: String, withFinal: String, withoutFinal: String): String {
    val last = name.trimEnd().lastOrNull() ?: return withoutFinal
    val hasFinal = last in '가'..'힣' && (last - '가') % FINAL_CONSONANTS != 0
    return if (hasFinal) withFinal else withoutFinal
}

private const val FINAL_CONSONANTS = 28
private const val PAST_SELF_ALPHA = 0.5f

/** 배치고사 상대 원은 그림 칸보다 작게 둔다(성사 화면 96 칸 안 72 원). */
private const val UNKNOWN_CIRCLE_RATIO = 0.75f

/**
 * 아직 모르는 상대 자리: 점선 원 안 「?」(피그마 61355:2909 원 100·글자 52, 진입 시트 원 64·글자 30과 같은 비율). [color]는 선과 글자 색이다.
 * 장식이라 낭독하지 않는다(부모가 「나 대 아직 모르는 상대」로 읽는다).
 */
@Composable
internal fun QuestionCircle(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 2.dp.toPx()
            drawCircle(
                color = color,
                radius = this.size.minDimension / 2 - stroke / 2,
                style = Stroke(
                    width = stroke,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                )
            )
        }
        Text(
            QUESTION_MARK,
            style = RunkkTextStyles.text(
                0,
                FontWeight.Bold
            ).copy(fontSize = questionTextSize(size, LocalDensity.current)),
            color = color
        )
    }
}

/**
 * 원 [size] 안 「?」 글자 크기. 원은 dp라 글자 크기 설정으로 커지지 않으므로 글자도 dp로 맞춰(글자 배율을 나눠) 원에 닿지 않게 한다
 * (PL2-A6, TierEmblem의 「?」와 같은 방식). 글자 배율 1에서는 지금까지의 정수 sp와 같다.
 */
internal fun questionTextSize(size: Dp, density: Density): TextUnit =
    with(density) { (size.value * QUESTION_TEXT_RATIO).toInt().dp.toSp() }

private const val QUESTION_MARK = "?"

/** 원 지름 대비 「?」 글자 크기(피그마 100 → 52). */
private const val QUESTION_TEXT_RATIO = 0.52f
