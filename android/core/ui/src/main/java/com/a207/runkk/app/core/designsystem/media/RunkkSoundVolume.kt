package com.a207.runkk.app.core.designsystem.media

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * 앱 소리 크기(사용자 요청 2026-10-31: 설정의 볼륨 조절). 배경 음악과 효과음을 따로 0~1로 둔다. 기기에만 저장하고(서버 설정 아님)
 * 앱 하나에 하나만 둔다: 설정에서 바꾸면 틀고 있는 배경 음악에 바로 반영된다. 기기 음량에 곱해진다.
 */
@Stable
class RunkkSoundVolume internal constructor(private val prefs: SharedPreferences?) {
    var music by mutableFloatStateOf(prefs?.getFloat(MUSIC, FULL) ?: FULL)
        private set
    var effects by mutableFloatStateOf(prefs?.getFloat(EFFECTS, FULL) ?: FULL)
        private set

    /** 끌어 옮기는 동안은 화면 값만 바꾸고, [save]로 기기에 남긴다. */
    fun changeMusic(level: Float) {
        music = level.coerceIn(0f, FULL)
    }

    fun changeEffects(level: Float) {
        effects = level.coerceIn(0f, FULL)
    }

    fun save() {
        prefs?.edit()
            ?.putFloat(MUSIC, music)
            ?.putFloat(EFFECTS, effects)
            ?.putFloat(MUSIC_BEFORE_MUTE, musicBeforeMute)
            ?.putFloat(EFFECTS_BEFORE_MUTE, effectsBeforeMute)
            ?.apply()
    }

    /** 끄기 전 크기. 아이콘으로 다시 켜면 이 크기로 돌아간다(0이었으면 최대). */
    private var musicBeforeMute = prefs?.getFloat(MUSIC_BEFORE_MUTE, FULL) ?: FULL
    private var effectsBeforeMute = prefs?.getFloat(EFFECTS_BEFORE_MUTE, FULL) ?: FULL

    /** 배경 음악 아이콘: 켜져 있으면 끄고(크기를 기억), 꺼져 있으면 기억한 크기로 켠다. 바로 저장한다. */
    fun toggleMusic() {
        if (music > 0f) {
            musicBeforeMute = music
            music = 0f
        } else {
            music = musicBeforeMute.takeIf { it > 0f } ?: FULL
        }
        save()
    }

    fun toggleEffects() {
        if (effects > 0f) {
            effectsBeforeMute = effects
            effects = 0f
        } else {
            effects = effectsBeforeMute.takeIf { it > 0f } ?: FULL
        }
        save()
    }

    companion object {
        private const val FILE = "runkk_sound"
        private const val MUSIC = "music"
        private const val EFFECTS = "effects"
        private const val MUSIC_BEFORE_MUTE = "music_before_mute"
        private const val EFFECTS_BEFORE_MUTE = "effects_before_mute"
        private const val FULL = 1f

        @Volatile
        private var shared: RunkkSoundVolume? = null

        fun of(context: Context): RunkkSoundVolume = shared ?: synchronized(this) {
            shared ?: RunkkSoundVolume(context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE))
                .also { shared = it }
        }
    }
}

/** 앱 소리 크기. 미리보기에서는 저장하지 않는 기본값(최대)이다. */
@Composable
fun rememberSoundVolume(): RunkkSoundVolume =
    if (LocalInspectionMode.current) RunkkSoundVolume(null) else RunkkSoundVolume.of(LocalContext.current)
