package com.a207.runkk.app.core.designsystem.media

import android.content.Context
import android.graphics.Matrix
import android.util.Log
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.ExoPlayer

/**
 * 소리 없는 반복 영상(로그인 배경, 사용자 요청 2026-10-31: 반복하는 곳에서 버벅이지 않게). ExoPlayer가 다음 반복을 미리 읽어 두어
 * 끝 장면에서 첫 장면으로 멈춤 없이 넘어간다. 화면 크기에 맞춰 가운데 기준으로 잘라 꽉 채우고, 앱이 뒤로 가면 멈췄다 이어 튼다.
 * 재생기를 만들 수 없으면(시험) 아무것도 그리지 않는다. 장식이므로 읽기 정보가 없다.
 */
@Composable
fun RunkkLoopingVideo(@RawRes video: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(video) { openLoopingVideo(context.applicationContext, video) } ?: return
    DisposableEffect(player) { onDispose { player.release() } }
    LifecycleResumeEffect(player) {
        player.play()
        onPauseOrDispose { player.pause() }
    }
    AndroidView(
        factory = { CroppedVideoView(it, player) },
        onRelease = CroppedVideoView::detach,
        modifier = modifier
    )
}

@OptIn(UnstableApi::class)
private fun openLoopingVideo(context: Context, @RawRes video: Int): ExoPlayer? = try {
    openLoopingPlayer(context, video).apply {
        volume = 0f
        prepare()
    }
} catch (e: Exception) {
    Log.w("RunkkLoopingVideo", "반복 영상을 열지 못했다", e)
    null
}

/** 영상을 화면 크기에 맞춰 가운데 기준으로 잘라 그리는 표면. 영상 크기를 알거나 화면 크기가 바뀌면 다시 맞춘다. */
private class CroppedVideoView(context: Context, private val player: ExoPlayer) :
    TextureView(context),
    Player.Listener {
    init {
        isOpaque = false
        player.setVideoTextureView(this)
        player.addListener(this)
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) = cropToFill()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cropToFill()
    }

    fun detach() {
        player.removeListener(this)
        player.clearVideoTextureView(this)
    }

    private fun cropToFill() {
        val videoWidth = player.videoSize.width
        val videoHeight = player.videoSize.height
        if (videoWidth <= 0 || videoHeight <= 0 || width <= 0 || height <= 0) return
        val scale = maxOf(width.toFloat() / videoWidth, height.toFloat() / videoHeight)
        val matrix = Matrix()
        matrix.setScale(scale * videoWidth / width, scale * videoHeight / height, width / 2f, height / 2f)
        setTransform(matrix)
    }
}
