package com.a207.runkk.app.core.designsystem.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.annotation.RawRes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.ExoPlayer

/**
 * 원본 자원 [raw]를 끝없이 반복하는 재생기를 만든다(배경 음악·반복 영상 공통, 리뷰 AN11-8). 다음 반복을 미리 읽어 끝과 처음이
 * 이음매 없이 이어진다. 소리 속성·음량을 정한 뒤 부르는 쪽이 prepare한다.
 */
@OptIn(UnstableApi::class)
internal fun openLoopingPlayer(context: Context, @RawRes raw: Int): ExoPlayer =
    ExoPlayer.Builder(context).build().apply {
        setMediaItem(MediaItem.fromUri(RawResourceDataSource.buildRawResourceUri(raw)))
        repeatMode = Player.REPEAT_MODE_ONE
    }
