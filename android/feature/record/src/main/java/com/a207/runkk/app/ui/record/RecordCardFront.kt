package com.a207.runkk.app.ui.record

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.RunkkAvatar
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.splitPace
import com.a207.runkk.app.domain.record.CardWeather
import com.a207.runkk.app.domain.record.RunCard
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 기록 카드 앞면(B-7, FR-068): 날짜·시각과 날씨, 기본 아바타, 큰 거리와 "신기록", 운동 시간·평균 페이스·평균 심박,
 * "[닉네임], 런크크" 서명. 종류 라벨은 없다. 뒷면(코스)은 CARD_BACK이 열릴 때만 있다(D-252, D93).
 */
@Composable
fun RecordCardFront(card: RunCard, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(RunkkColors.Surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(AVATAR_BOX_RATIO)
                .clip(RoundedCornerShape(24.dp))
                .background(RunkkColors.LimeContainer),
            contentAlignment = Alignment.Center
        ) {
            RunkkAvatar(faceKey = null, expression = AvatarExpression.JOY, modifier = Modifier.size(120.dp))
        }
        DistanceRow(card)
        StatsRow(card)
        Text(signature(card.ownerName), style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
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
            Text(RunkkFormat.km(card.distance), style = RunkkTextStyles.display(44), color = RunkkColors.TextPrimary)
            Text(
                stringResource(R.string.record_card_km),
                style = RunkkTextStyles.text(16, FontWeight.Bold),
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
private fun CardStat(value: String, label: String, modifier: Modifier, spoken: String? = null) {
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

/** "[닉네임], 런크크". 닉네임을 모르면(서버가 아직 주지 않음) "런크크"만. */
@Composable
private fun signature(ownerName: String?): String = ownerName?.let {
    stringResource(R.string.record_card_signature, it)
} ?: stringResource(R.string.record_card_brand)

private const val AVATAR_BOX_RATIO = 1.75f
