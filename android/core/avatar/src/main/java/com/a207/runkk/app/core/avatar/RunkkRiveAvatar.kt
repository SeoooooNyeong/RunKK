package com.a207.runkk.app.core.avatar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.coroutines.cancellation.CancellationException

/**
 * 런크크 Rive 아바타(아바타 PoC의 남녀 서기·달리기 몸체). 네이티브 Rive 뷰를 Compose에 붙인다.
 *
 * - [gender]·[motion]: 4개 화면 매핑 Rive 파일 가운데 하나를 그린다(정면 서기, 옆모습 달리기).
 * - [outfit]: 상의·하의·신발 0..6. 0이 기본 의상이다.
 * - [bodyShape]: Rive body_shape 0·50·100.
 * - [runningAmount]: 0..100(밖이면 잘라 쓴다). 높을수록 피부가 어둡다. 피부에만 적용하고 눈·헤어·안경·옷은 원래 색이다.
 *   서버 피부색 값에서 바꾸는 자리는 [skinToneAmountFrom]이다.
 * - [headLayers]: 셀카 생성 머리([GeneratedHeadLayers.fromHeadJson]). null이면 내장 기본 머리다. 참조로 비교하므로
 *   결과 하나당 한 번 만들어 상태로 들고 있는다(매번 새로 만들면 파일을 다시 읽는다).
 * - [onError]: Rive 파일·자산을 읽지 못했거나 합성에 실패했을 때. 실패해도 앞 그림은 그대로 둔다.
 * - [mirrored]: 좌우로 뒤집는다. 달리기([AvatarMotion.RUN]) 플레이어에만 걸리고(네이티브 뷰 `scaleX = -1`, 축은 뷰 가운데 = 아트보드
 *   가운데) 서기는 늘 정면 그대로다(D-762). 진행 방향 흔들림은 [rememberRunFacingLeft]로 거른 값을 넘긴다.
 * - [onReady]: 지금 동작의 첫 텍스처가 들어가 보이게 된 뒤 첫 판이 실제로 그려진 때(주 스레드, 판이 0.4초 안에 오지 않아도 온다).
 *   남녀·머리·동작이 바뀌어 다시 그리면 다시 온다.
 *   로딩 표시를 거둘 시점이다([AvatarLoadingPlaceholder], D-738). 끝에 둔 매개변수라 뒤붙은 람다는 이것으로 묶인다.
 * - [warmAllMotions]: 서기·달리기 플레이어를 처음부터 함께 만들어 두 동작을 한 번에 합성한다(트랙 칸, S15P31A207-335). 자세가 바뀌는
 *   동안에는 새 동작 플레이어가 첫 판을 그릴 때까지 앞 동작을 보인다.
 *
 * 같은 모습을 [prepareRiveAvatarLook]으로 미리 준비했거나 다른 뷰가 그렸으면 합성을 다시 하지 않는다(공용 합성 캐시).
 *
 * Rive 파일·내장 그림 바이트는 IO에서 남녀마다 한 번 읽어 프로세스에 둔다([RiveAvatarAssets]). 남녀나 머리가 바뀌면 파일을 다시 읽고, 의상·체형·러닝량이 바뀌면 백그라운드에서 텍스처만 다시 만든다. 새 그림이 준비될 때까지
 * 그 동작의 기본 그림을 보이지 않는다. 화면이 멈추면(ON_PAUSE) 재생도 멈춘다.
 */
@Composable
fun RunkkRiveAvatar(
    gender: AvatarGender,
    motion: AvatarMotion,
    outfit: AvatarOutfit,
    bodyShape: AvatarBodyShape,
    runningAmount: Int,
    headLayers: GeneratedHeadLayers?,
    modifier: Modifier = Modifier,
    onError: (Throwable) -> Unit = {},
    mirrored: Boolean = false,
    onReady: () -> Unit = {},
    runSpeed: Float = 1f,
    warmAllMotions: Boolean = false
) {
    val amount = runningAmount.coerceIn(MIN_RUNNING_AMOUNT, MAX_RUNNING_AMOUNT)
    val currentOnError by rememberUpdatedState(onError)
    val currentOnReady by rememberUpdatedState(onReady)
    val context = LocalContext.current
    // Rive 파일(최대 5MB)·내장 PNG는 IO에서 남녀마다 한 번 읽어 둔다. 읽는 동안 뷰는 그리지 않고 기다린다.
    val prepared by produceState(RiveAvatarAssets.shared.cached(gender), gender) {
        value = try {
            RiveAvatarAssets.shared.load(context, gender)
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
    AndroidView(
        factory = { viewContext ->
            RiveAvatarView(viewContext).apply {
                this.onError = { currentOnError(it) }
                this.onReady = { currentOnReady() }
            }
        },
        modifier = modifier,
        onRelease = RiveAvatarView::release,
        update = { view ->
            view.runSpeed = runSpeed
            view.warmAllMotions = warmAllMotions
            view.render(AvatarRenderState(gender, motion, outfit, bodyShape, amount, headLayers, mirrored), prepared)
        }
    )
}
