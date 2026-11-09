package com.a207.runkk.app.core.avatar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

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
 *
 * 남녀나 머리가 바뀌면 파일을 다시 읽고, 의상·체형·러닝량이 바뀌면 백그라운드에서 텍스처만 다시 만든다. 새 그림이 준비될 때까지
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
    onError: (Throwable) -> Unit = {}
) {
    val amount = runningAmount.coerceIn(MIN_RUNNING_AMOUNT, MAX_RUNNING_AMOUNT)
    val currentOnError by rememberUpdatedState(onError)
    AndroidView(
        factory = { context -> RiveAvatarView(context).apply { this.onError = { currentOnError(it) } } },
        modifier = modifier,
        onRelease = RiveAvatarView::release,
        update = { view ->
            view.render(AvatarRenderState(gender, motion, outfit, bodyShape, amount, headLayers))
        }
    )
}
