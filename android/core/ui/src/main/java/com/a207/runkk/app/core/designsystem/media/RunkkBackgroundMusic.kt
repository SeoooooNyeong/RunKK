package com.a207.runkk.app.core.designsystem.media

import android.content.Context
import android.media.AudioManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.a207.runkk.app.core.designsystem.LocalCoveredBySplash

/**
 * 배경 음악(사용자 요청 2026-10-31: 로그인 화면, 로그인한 뒤 모든 화면). 앱이 보이는 동안(재개 상태, 스플래시에 덮이지 않음,
 * [isPlaying] 참) 끝없이 반복하고, 앱이 뒤로 가거나 [isPlaying]이 거짓이면 그 자리에서 멈췄다가 다시 이어 튼다. 부르는 자리를 떠나면 놓는다.
 * 다른 앱 음악이 이미 나오고 있으면 틀지 않는다(오디오 포커스를 가져가지 않는다).
 * 미리보기나 재생기를 만들 수 없는 환경(시험)에서는 아무것도 하지 않는다.
 */
@Composable
fun RunkkBackgroundMusic(@RawRes music: Int, volume: Float = 1f, isPlaying: Boolean = true) {
    if (LocalInspectionMode.current) return
    val context = LocalContext.current.applicationContext
    val isCovered = LocalCoveredBySplash.current
    // 설정의 배경 음악 크기(RunkkSoundVolume)를 곱한다. 바꾸면 틀고 있는 음악에 바로 반영된다
    val level = rememberSoundVolume().music
    val player = remember(music) { LoopingMusic.open(context, music) } ?: return
    DisposableEffect(player) { onDispose { player.release() } }
    SideEffect { player.setVolume(volume * level) }
    LifecycleResumeEffect(player, isCovered, isPlaying) {
        if (!isCovered && isPlaying) player.play()
        onPauseOrDispose { player.pause() }
    }
}

/**
 * 반복 재생기 하나. ExoPlayer가 다음 반복을 미리 읽어 두어 끝과 처음이 이음매 없이 이어진다(MediaPlayer 반복은 처음으로 되감는 사이
 * 소리가 잠깐 끊긴다). 처음 틀 때만 다른 앱 음악이 나오는지 본다(멈췄다 다시 틀 때는 우리 음악을 이어 간다). 화면이 바뀌는 사이
 * 앞 화면의 우리 음악이 아직 나오고 있을 수 있어, 우리 재생기가 하나라도 틀고 있으면 다른 앱 음악으로 보지 않는다.
 */
private class LoopingMusic(private val player: ExoPlayer, private val audio: AudioManager?) {
    private var hasPlayed = false
    private var isCounted = false

    fun play() {
        if (player.playWhenReady) return
        if (!mayStartMusic(hasPlayed, audio?.isMusicActive == true, ownPlaying)) return
        hasPlayed = true
        player.play()
        count(true)
    }

    fun pause() {
        player.pause()
        count(false)
    }

    fun release() {
        count(false)
        player.release()
    }

    fun setVolume(level: Float) {
        player.volume = level
    }

    private fun count(isOn: Boolean) {
        if (isCounted == isOn) return
        isCounted = isOn
        ownPlaying += if (isOn) 1 else -1
    }

    companion object {
        /** 지금 틀고 있는 우리 재생기 수(화면 스레드에서만 바꾼다). */
        private var ownPlaying = 0

        @OptIn(UnstableApi::class)
        fun open(context: Context, @RawRes music: Int): LoopingMusic? = try {
            val player = openLoopingPlayer(context, music).apply {
                setAudioAttributes(MUSIC, false)
                prepare()
            }
            LoopingMusic(player, context.getSystemService(AudioManager::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "배경 음악을 열지 못했다", e)
            null
        }
    }
}

private val MUSIC: AudioAttributes =
    AudioAttributes.Builder()
        .setUsage(C.USAGE_GAME)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

private const val TAG = "RunkkBackgroundMusic"

/**
 * 배경 음악을 틀어도 되는가(D-346, FR-235): 이미 한 번 틀었으면(멈췄다 다시 틀기) 늘 된다. 처음이면 다른 앱 음악이 나오는 중일 때만
 * 양보한다. 기기에서 음악이 나오더라도 우리 재생기([ownPlaying] > 0, 앞 화면 음악)가 내는 것이면 다른 앱 음악으로 보지 않는다.
 */
internal fun mayStartMusic(hasPlayed: Boolean, isMusicActive: Boolean, ownPlaying: Int): Boolean =
    hasPlayed || !isMusicActive || ownPlaying > 0
