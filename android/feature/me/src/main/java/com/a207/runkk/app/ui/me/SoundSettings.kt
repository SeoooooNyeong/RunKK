package com.a207.runkk.app.ui.me

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkIconButton
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.rememberSoundVolume
import kotlin.math.roundToInt

/**
 * 소리 묶음(사용자 요청 2026-10-31): 배경 음악과 효과음 크기. 기기에만 저장하는 값(RunkkSoundVolume)이라 서버 설정을 거치지 않고,
 * 끄는 동안 틀고 있는 배경 음악에 바로 들린다. 손을 떼면 저장한다. 앞의 스피커 아이콘을 누르면 소리를 끄고, 다시 누르면 끄기 전 크기로 켠다.
 */
@Composable
internal fun SoundSection() {
    val volume = rememberSoundVolume()
    Section(R.string.me_settings_section_sound) {
        VolumeRow(
            R.string.me_settings_music_volume,
            volume.music,
            VolumeActions(volume::changeMusic, volume::save, volume::toggleMusic),
            MUSIC_VOLUME_TAG
        )
        SettingsDivider()
        VolumeRow(
            R.string.me_settings_effect_volume,
            volume.effects,
            VolumeActions(volume::changeEffects, volume::save, volume::toggleEffects),
            EFFECT_VOLUME_TAG
        )
    }
}

/** 크기 한 줄의 행동: 끌기, 손 떼기(저장), 스피커 아이콘(끄기·켜기). */
private class VolumeActions(val onChange: (Float) -> Unit, val onDone: () -> Unit, val onToggle: () -> Unit)

/** 스피커 아이콘(끄기·켜기) + 제목 + 끌어 정하는 크기 막대 한 줄. 막대 읽기 정보는 "제목, n퍼센트"다. */
@Composable
private fun VolumeRow(@StringRes title: Int, level: Float, actions: VolumeActions, tag: String) {
    val titleText = stringResource(title)
    val percent = (level * PERCENT).roundToInt()
    val spoken = stringResource(R.string.me_settings_volume_spoken, titleText, percent)
    val isMuted = level <= 0f
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).padding(start = 8.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RunkkIconButton(
            if (isMuted) RunkkIcons.VolumeOff else RunkkIcons.Volume,
            contentDescription = stringResource(
                if (isMuted) R.string.me_settings_volume_unmute else R.string.me_settings_volume_mute,
                titleText
            ),
            onClick = actions.onToggle,
            iconTint = if (isMuted) RunkkColors.TextSecondary else RunkkColors.TextPrimary,
            modifier = Modifier.testTag("$tag-toggle")
        )
        Text(
            titleText,
            style = RunkkTextStyles.text(15, FontWeight.Medium),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.width(72.dp)
        )
        Slider(
            value = level,
            onValueChange = actions.onChange,
            onValueChangeFinished = actions.onDone,
            colors = SliderDefaults.colors(
                thumbColor = RunkkColors.Lime,
                activeTrackColor = RunkkColors.Lime,
                inactiveTrackColor = RunkkColors.Border
            ),
            modifier = Modifier.weight(1f).testTag(tag).semantics { contentDescription = spoken }
        )
    }
}

private const val PERCENT = 100

internal const val MUSIC_VOLUME_TAG = "settings-music-volume"
internal const val EFFECT_VOLUME_TAG = "settings-effect-volume"
