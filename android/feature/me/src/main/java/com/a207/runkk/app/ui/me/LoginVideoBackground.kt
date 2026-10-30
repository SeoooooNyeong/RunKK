package com.a207.runkk.app.ui.me

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.viewinterop.AndroidView
import com.a207.runkk.app.core.designsystem.LocalReduceMotion

/**
 * 로그인 배경 영상(피그마 A-1 메모: 무음, 1초부터 끝까지 반복). 첫 장면 그림을 먼저 깔고 그 위에 영상을 화면에 꽉 차게(잘라서) 튼다.
 * 움직임 줄이기 설정이나 미리보기에서는 그림만 보인다. 장식이므로 읽기 정보가 없다.
 */
@Composable
internal fun LoginVideoBackground(@RawRes video: Int, @DrawableRes poster: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clearAndSetSemantics {}) {
        Image(
            painterResource(poster),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (!LocalInspectionMode.current && !LocalReduceMotion.current) {
            AndroidView(
                factory = { context -> LoopingVideoView(context, video) },
                onRelease = LoopingVideoView::release,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** 화면 크기에 맞춰 영상을 가운데 기준으로 잘라 그리고, 끝나면 [START_MS]로 돌아가 다시 튼다. 표면이 생길 때 재생기를 만든다. */
private class LoopingVideoView(context: Context, @RawRes private val video: Int) :
    TextureView(context),
    TextureView.SurfaceTextureListener {

    private var player: MediaPlayer? = null
    private var surface: Surface? = null

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        val target = Surface(texture)
        surface = target
        player = MediaPlayer().apply {
            setDataSource(context, Uri.parse("android.resource://${context.packageName}/$video"))
            setSurface(target)
            setVolume(0f, 0f)
            setOnPreparedListener { prepared ->
                cropToFill(prepared.videoWidth, prepared.videoHeight)
                prepared.seekTo(START_MS)
                prepared.start()
            }
            setOnCompletionListener { done ->
                done.seekTo(START_MS)
                done.start()
            }
            prepareAsync()
        }
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        player?.let { cropToFill(it.videoWidth, it.videoHeight) }
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        release()
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    fun release() {
        player?.release()
        player = null
        surface?.release()
        surface = null
    }

    private fun cropToFill(videoWidth: Int, videoHeight: Int) {
        if (videoWidth <= 0 || videoHeight <= 0 || width <= 0 || height <= 0) return
        val scale = maxOf(width.toFloat() / videoWidth, height.toFloat() / videoHeight)
        val matrix = Matrix()
        matrix.setScale(scale * videoWidth / width, scale * videoHeight / height, width / 2f, height / 2f)
        setTransform(matrix)
    }

    private companion object {
        /** 반복 구간 시작(피그마 메모 "1초부터 끝까지"). */
        const val START_MS = 1_000
    }
}
