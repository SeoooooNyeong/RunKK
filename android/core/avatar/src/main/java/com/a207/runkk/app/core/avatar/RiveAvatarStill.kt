package com.a207.runkk.app.core.avatar

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 전신 정지 그림(읽어 둔 그림)의 시험 표지. */
const val AVATAR_STILL_TAG = "avatar_still"

/**
 * 서 있는 전신 아바타 정지 그림(랭킹 TOP3 시상대·기록런 VS 칸처럼 여러 사람을 한 화면에 세우는 칸, S15P31A207-300).
 * 칸마다 Rive를 계속 돌리지 않고, 처음 한 번만 서기(STAND)를 보이지 않게 그려 그 판을 읽은 뒤 몸만 잘라 칸 크기(px) 안으로 줄여
 * 프로세스 캐시([AvatarStillCache])에 둔다. 같은 모습·같은 칸 크기면 다음부터는 캐시 그림을 바로 그린다(Rive 없음).
 *
 * - [look]: 그릴 모습. 다른 러너처럼 꾸민 모습을 모르면 성별 기본 모습(기본 의상·BASE·러닝량 0·기본 머리)을 넘긴다.
 * - 그림은 칸 안에 비율을 지켜 맞추고 발을 칸 바닥에 붙인다(`ContentScale.Fit`, `Alignment.BottomCenter`).
 * - 읽는 동안(대개 1초 안) 칸은 비어 있다. Rive를 그리지 못하거나 [CAPTURE_TRIES]번 읽어도 몸이 없으면 [onError]다. 부르는 쪽이
 *   성별 기본 그림으로 바꾼다.
 */
@Composable
fun RiveAvatarStill(look: RiveAvatarLook, modifier: Modifier = Modifier, onError: (Throwable) -> Unit = {}) {
    BoxWithConstraints(modifier) {
        if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight) return@BoxWithConstraints
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        if (width <= 0 || height <= 0) return@BoxWithConstraints
        val key = remember(look, width, height) { AvatarStillKey.of(look, width, height) }
        var still by remember(key) { mutableStateOf(AvatarStillCache.get(key)) }
        val shown = still
        if (shown != null) {
            Image(
                bitmap = shown,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().testTag(AVATAR_STILL_TAG),
                alignment = Alignment.BottomCenter,
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.Medium
            )
        } else {
            StillCapture(
                look = look,
                width = width,
                height = height,
                onCaptured = { bitmap ->
                    AvatarStillCache.put(key, bitmap)
                    still = bitmap.asImageBitmap()
                },
                onError = onError
            )
        }
    }
}

/**
 * 서기 Rive를 아트보드 비율 상자에 거의 투명하게 그려(텍스처 뷰는 그려져야 판이 생긴다) 첫 텍스처가 들어간 뒤 판을 읽는다.
 * 몸이 칸 높이보다 크게 담기도록 아트보드를 칸 높이 / [FIGURE_OF_ARTBOARD]로 잡고, 자른 뒤 칸 크기로 줄인다(흐려지지 않게).
 * 읽고 나면 부르는 쪽이 캐시 그림으로 바꿔 이 뷰는 놓인다.
 */
@Composable
private fun StillCapture(
    look: RiveAvatarLook,
    width: Int,
    height: Int,
    onCaptured: (Bitmap) -> Unit,
    onError: (Throwable) -> Unit
) {
    val context = LocalContext.current
    val currentOnError by rememberUpdatedState(onError)
    val currentOnCaptured by rememberUpdatedState(onCaptured)
    val prepared by produceState(RiveAvatarAssets.shared.cached(look.gender), look.gender) {
        value = try {
            RiveAvatarAssets.shared.load(context, look.gender)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentOnError(e)
            null
        } catch (e: OutOfMemoryError) {
            currentOnError(e)
            null
        }
    }
    val artboardHeight = (height / FIGURE_OF_ARTBOARD).roundToInt()
    val artboardWidth = (artboardHeight * RiveAvatarGeometry.ASPECT).roundToInt()
    val density = LocalDensity.current
    var view by remember { mutableStateOf<RiveAvatarView?>(null) }
    var isReady by remember(look) { mutableStateOf(false) }
    val amount = look.runningAmount.coerceIn(MIN_RUNNING_AMOUNT, MAX_RUNNING_AMOUNT)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AndroidView(
            factory = { viewContext ->
                RiveAvatarView(viewContext).apply {
                    this.onError = { currentOnError(it) }
                    this.onReady = { isReady = true }
                }.also { view = it }
            },
            modifier = with(density) {
                Modifier
                    .requiredSize(artboardWidth.toDp(), artboardHeight.toDp())
                    .graphicsLayer { alpha = CAPTURE_ALPHA }
            },
            onRelease = {
                view = null
                it.release()
            },
            update = { riveView ->
                riveView.render(
                    AvatarRenderState(
                        look.gender,
                        AvatarMotion.STAND,
                        look.outfit,
                        look.bodyShape,
                        amount,
                        look.headLayers
                    ),
                    prepared
                )
            }
        )
    }
    LaunchedEffect(isReady, artboardWidth, artboardHeight) {
        if (!isReady) return@LaunchedEffect
        repeat(CAPTURE_TRIES) {
            delay(CAPTURE_DELAY_MS)
            val frame = view?.snapshot(artboardWidth, artboardHeight) ?: return@repeat
            val still = withContext(Dispatchers.Default) { AvatarStillFrame.cut(frame, width, height) }
            if (still != null) {
                currentOnCaptured(still)
                return@LaunchedEffect
            }
        }
        currentOnError(IllegalStateException("avatar still frame stayed empty"))
    }
}

/** 아트보드 높이 대비 몸 높이를 낮게 잡은 값. 아트보드를 칸 높이 / 이 값으로 그려 자른 몸이 칸보다 크게 담기게 한다. */
private const val FIGURE_OF_ARTBOARD = 0.6f

/** 읽는 동안 그리는 Rive 뷰의 불투명도(텍스처 뷰가 그려지되 눈에는 보이지 않게). 읽은 판에는 영향이 없다. */
private const val CAPTURE_ALPHA = 0.01f

/** 첫 텍스처가 들어간 뒤 판을 읽기까지 기다리는 시간과 다시 읽는 횟수(합 약 2초). */
private const val CAPTURE_DELAY_MS = 150L
private const val CAPTURE_TRIES = 12
