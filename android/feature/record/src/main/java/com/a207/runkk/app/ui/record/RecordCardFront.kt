package com.a207.runkk.app.ui.record

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.ResultCardStage
import com.a207.runkk.app.core.designsystem.ResultStageDefaultAvatar
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.splitPace
import com.a207.runkk.app.core.navigation.AvatarStillSlot
import com.a207.runkk.app.core.navigation.AvatarStillSubject
import com.a207.runkk.app.domain.record.CardWeather
import com.a207.runkk.app.domain.record.RunCard
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 기록 카드 앞면(B-7·K-1, FR-068, [임시:FR-성-34]): 날짜·시각과 날씨, 무대 배경 위에 선 내 전신 아바타(그림 칸은 카드 높이의 0.60),
 * 큰 거리와 "신기록", 운동 시간·평균 페이스·평균 심박. 종류 라벨과 서명 줄은 없다(서명은 사용자 요청 2026-10-30로 뺐다). 크기는
 * 뒷면([RecordCardBack])과 같은 334×383 비율이다(피그마 K-1). 공유 단계도 이 앞면을 그대로 그려 찍은 이미지를 보낸다.
 *
 * 앞면은 334×383 시안을 카드 너비에 맞춰 통째로 늘리거나 줄여 그린다(글자와 간격이 카드와 같은 비율로 변한다). 그림 칸이 카드 높이의
 * 0.60을 차지해 글자 줄에 남는 높이가 빠듯하므로, 좁은 화면의 작은 카드에서도 글자 줄이 잘리지 않게 하려는 것이다.
 * [backgroundKey]는 무대 배경 키(null이면 기본 무대), [onAvatarReady]는 전신 아바타가 보이면 한 번 불린다(공유 단계가 기다린다).
 */
@Composable
fun RecordCardFront(
    card: RunCard,
    modifier: Modifier = Modifier,
    backgroundKey: String? = null,
    onAvatarReady: () -> Unit = {}
) {
    BoxWithConstraints(modifier.aspectRatio(RECORD_CARD_RATIO)) {
        val density = LocalDensity.current
        val scale = maxWidth / CARD_DESIGN_WIDTH
        CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
            RecordCardShell(card, Modifier.fillMaxSize(), FRONT_ROW_GAP, FRONT_VERTICAL_PADDING) {
                ResultCardStage(
                    backgroundKey,
                    Modifier
                        .fillMaxWidth()
                        .cardPictureHeight()
                        .clip(RoundedCornerShape(24.dp))
                        .testTag(RECORD_CARD_PICTURE_TAG)
                ) { slot ->
                    AvatarStillSlot(AvatarStillSubject.MINE, slot, onReady = onAvatarReady) {
                        ResultStageDefaultAvatar(Modifier.fillMaxSize())
                    }
                }
                DistanceRow(card)
                StatsRow(card)
            }
        }
    }
}

/**
 * 그림 칸 높이를 카드 높이의 [RECORD_CARD_PICTURE_SHARE]로 맞춘다([임시:D-성-289]). 칸 너비는 카드 너비에서 양쪽 안쪽 여백을 뺀 값이므로
 * 카드 높이 = (칸 너비 + 2 × 여백) ÷ 카드 비율이다.
 */
private fun Modifier.cardPictureHeight(): Modifier = layout { measurable, constraints ->
    val cardHeight = (constraints.maxWidth + 2 * CARD_PADDING.toPx()) / RECORD_CARD_RATIO
    val height = (RECORD_CARD_PICTURE_SHARE * cardHeight).roundToInt()
    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, height))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/**
 * 카드 앞·뒷면 공통 틀(피그마 K 기록 카드): 표면색 둥근 네모(334×383 비율, 안쪽 여백 좌우 18·위아래 [verticalPadding], 줄 간격 [rowGap]) 위쪽에
 * 날짜·시각과 날씨 줄,
 * 그 아래 [content]. 크기가 정해져 있어 가운데 칸은 남은 높이를 weight로 채운다.
 */
@Composable
internal fun RecordCardShell(
    card: RunCard,
    modifier: Modifier,
    rowGap: Dp = 12.dp,
    verticalPadding: Dp = CARD_PADDING,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .aspectRatio(RECORD_CARD_RATIO)
            .clip(RoundedCornerShape(28.dp))
            .background(RunkkColors.Surface)
            .padding(horizontal = CARD_PADDING, vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(rowGap)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                cardDateLabel(card),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary,
                modifier = Modifier.weight(1f)
            )
            card.weather?.let { weatherLabel(it) }?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
            }
        }
        content()
    }
}

/** 큰 거리 "5.02 km"와 오른쪽 "신기록" 표지. */
@Composable
private fun DistanceRow(card: RunCard) {
    val spoken = stringResource(R.string.record_distance_spoken, RunkkFormat.km(card.distance))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = spoken }
        ) {
            Text(
                RunkkFormat.km(card.distance),
                style = RunkkTextStyles.display(DISTANCE_SIZE),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.testTag(RECORD_CARD_DISTANCE_TAG)
            )
            Text(
                stringResource(R.string.record_card_km),
                style = RunkkTextStyles.CardTitle,
                color = RunkkColors.TextPrimary,
                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
            )
        }
        if (card.newRecords.isNotEmpty()) {
            Text(
                stringResource(R.string.record_card_new_record),
                style = RunkkTextStyles.text(12, FontWeight.Bold),
                color = RunkkColors.TextPrimary,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(RunkkColors.LimeContainer)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

/** 운동 시간·평균 페이스(5'43")·평균 심박 세 칸. */
@Composable
private fun StatsRow(card: RunCard) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CardStat(
            RunkkFormat.duration(card.elapsed),
            stringResource(R.string.record_card_stat_time),
            Modifier.weight(1f),
            spoken = RunkkFormat.durationSpoken(card.elapsed)
        )
        CardStat(
            card.averagePace?.let(::splitPace) ?: RunkkFormat.EMPTY,
            stringResource(R.string.record_card_stat_pace),
            Modifier.weight(1f),
            spoken = RunkkFormat.paceSpoken(card.averagePace)
        )
        CardStat(
            card.averageHeartRate?.toString() ?: RunkkFormat.EMPTY,
            stringResource(R.string.record_card_stat_heart),
            Modifier.weight(1f)
        )
    }
}

@Composable
internal fun CardStat(value: String, label: String, modifier: Modifier, spoken: String? = null) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            spoken?.let { contentDescription = "$label $it" }
        }
    ) {
        Text(value, style = RunkkTextStyles.text(16, FontWeight.Black), color = RunkkColors.TextPrimary)
        Text(label, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
    }
}

/** "2026.10.21 (수) 오후 7:12". */
@Composable
private fun cardDateLabel(card: RunCard): String = DateTimeFormatter
    .ofPattern(stringResource(R.string.record_card_date_pattern), Locale.KOREAN)
    .format(card.startedAt.atZone(ZoneId.systemDefault()))

/** "맑음 14°". 하늘 상태나 기온 중 아는 것만 쓴다. */
@Composable
private fun weatherLabel(weather: CardWeather): String = listOfNotNull(
    weather.sky?.let { stringResource(skyLabelRes(it)) },
    weather.temperatureC?.let { stringResource(R.string.record_card_temperature, it) }
).joinToString(" ")

@StringRes
private fun skyLabelRes(sky: CardWeather.Sky): Int = when (sky) {
    CardWeather.Sky.CLEAR -> R.string.record_card_sky_clear
    CardWeather.Sky.CLOUDY -> R.string.record_card_sky_cloudy
    CardWeather.Sky.OVERCAST -> R.string.record_card_sky_overcast
    CardWeather.Sky.RAIN -> R.string.record_card_sky_rain
    CardWeather.Sky.SNOW -> R.string.record_card_sky_snow
}

/** 기록 카드 가로:세로(피그마 B-7·K 시안 334×383, 사용자 결정 2026-11-09). */
internal const val RECORD_CARD_RATIO = 334f / 383f

/** 앞면 그림 칸 높이 ÷ 카드 높이([임시:D-성-289], 허용오차 ±0.005). */
internal const val RECORD_CARD_PICTURE_SHARE = 0.60f

/** 앞면 그림 칸과 큰 거리 글자의 시험 표지. */
internal const val RECORD_CARD_PICTURE_TAG = "record-card-picture"
internal const val RECORD_CARD_DISTANCE_TAG = "record-card-distance"

/** 카드 시안 너비(334×383)와 안쪽 여백. */
private val CARD_DESIGN_WIDTH = 334.dp
private val CARD_PADDING = 18.dp

/**
 * 앞면 줄 간격, 위아래 안쪽 여백, 큰 거리 글자 크기: 그림 칸이 카드 높이의 0.60이라 글자 줄에 남는 높이가 모자라 줄 간격 12, 여백 18,
 * 글자 44에서 줄였다(설계 05-31 C 묶음 위험 ④). 시안 334×383에서 날짜 17 + 그림 230 + 거리 48 + 통계 41 + 간격 15 = 351 ≤ 355.
 */
private val FRONT_ROW_GAP = 5.dp
private val FRONT_VERTICAL_PADDING = 14.dp
private const val DISTANCE_SIZE = 40
