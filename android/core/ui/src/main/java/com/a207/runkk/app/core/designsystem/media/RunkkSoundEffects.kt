package com.a207.runkk.app.core.designsystem.media

import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * 짧은 효과음 묶음(사용자 요청 2026-10-31: 홈 아바타를 누르면 뛰어오르며 나는 소리). [play]는 기다리지 않고 바로 돌아오며,
 * 아직 읽는 중이거나 열지 못했으면 소리 없이 넘어간다.
 */
@Stable
class RunkkSoundEffects internal constructor(
    private val pool: SoundPool?,
    private val loaded: Map<Int, Int>,
    private val volume: RunkkSoundVolume? = null
) {
    /** 설정의 효과음 크기(RunkkSoundVolume)로 튼다. */
    fun play(@RawRes sound: Int) {
        val id = loaded[sound] ?: return
        val level = volume?.effects ?: 1f
        pool?.play(id, level, level, 1, 0, 1f)
    }

    internal fun release() {
        pool?.release()
    }

    companion object {
        /** 소리가 나지 않는 묶음(미리보기·시험). */
        val SILENT = RunkkSoundEffects(null, emptyMap())
    }
}

/** [sounds]를 미리 읽어 둔 효과음 묶음. 화면을 떠나면 놓는다. 미리보기나 효과음 장치를 열 수 없는 환경(시험)에서는 소리가 없다. */
@Composable
fun rememberSoundEffects(@RawRes vararg sounds: Int): RunkkSoundEffects {
    if (LocalInspectionMode.current) return RunkkSoundEffects.SILENT
    val context = LocalContext.current.applicationContext
    val volume = rememberSoundVolume()
    val key = sounds.toList()
    val effects = remember(key) {
        try {
            val pool = SoundPool.Builder().setMaxStreams(MAX_EFFECT_STREAMS).setAudioAttributes(EFFECT).build()
            RunkkSoundEffects(pool, key.associateWith { pool.load(context, it, 1) }, volume)
        } catch (e: Exception) {
            Log.w(TAG, "효과음을 읽지 못했다", e)
            RunkkSoundEffects.SILENT
        }
    }
    DisposableEffect(effects) { onDispose { effects.release() } }
    return effects
}

private val EFFECT: AudioAttributes =
    AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

private const val MAX_EFFECT_STREAMS = 2
private const val TAG = "RunkkSoundEffects"
