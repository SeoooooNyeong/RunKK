package com.a207.runkk.app.core.avatar

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 그 성별의 Rive 파일(서기·달리기, 최대 5MB)과 내장 머리 PNG를 IO에서 읽어 프로세스에 둔다(D-736). 이미 읽었으면 바로
 * 끝난다. 화면을 그리기 전에(로그인된 앱 시작, 로그인·가입 뒤 홈 진입) 부르면 칸이 처음 그려질 때 파일을 기다리지 않는다.
 *
 * 읽지 못하면 그 예외(IOException, 형식이 틀린 내장 PNG면 IllegalArgumentException)를 던진다. 네이티브 Rive 초기화는 하지 않는다
 * (첫 render에서 한다, D-716).
 */
suspend fun preloadRiveAvatar(context: Context, gender: AvatarGender) {
    RiveAvatarAssets.shared.load(context, gender)
}

/**
 * 그 모습의 합성 텍스처(피부톤·의상·체형·머리)를 Rive 뷰 없이 미리 만들어 프로세스 공용 캐시에 넣는다(D-737,
 * DR-10). 뒤에 같은 모습으로 [RunkkRiveAvatar]·[RiveAvatarStage]를 그리면 합성을 다시 하지 않고 첫 텍스처를 바로 넣는다.
 *
 * - 기본은 서기([motion] = STAND)만 준비한다. 공용 캐시는 남녀 × 동작마다 마지막 모습 하나다(상한 4묶음).
 * - [preloadRiveAvatar]를 먼저 부르지 않았으면 여기서 읽는다. 합성은 [Dispatchers.Default]에서 하고(수백 ms) 주 스레드를 쓰지 않는다.
 * - 같은 모습이 이미 들어 있으면 바로 끝난다. 호출이 겹치면 차례로 한다.
 * - [headLayers]는 [RunkkRiveAvatar]에 넘길 것과 같은 인스턴스를 넘긴다(참조로 비교한다).
 * - 실패하면 그 예외(IOException, IllegalArgumentException, IllegalStateException, OutOfMemoryError)를 던진다. 부르는 쪽은
 *   실패를 준비 실패로 다룬다(그려도 뷰가 다시 합성한다).
 */
suspend fun prepareRiveAvatarLook(
    context: Context,
    gender: AvatarGender,
    outfit: AvatarOutfit,
    bodyShape: AvatarBodyShape,
    runningAmount: Int,
    headLayers: GeneratedHeadLayers?,
    motion: AvatarMotion = AvatarMotion.STAND
) {
    AvatarLookPreparer.shared.prepare(context, gender, outfit, bodyShape, runningAmount, headLayers, motion)
}

/** [prepareRiveAvatarLook]의 구현. 시험이 자산·캐시·합성 함수를 넣는다. */
internal class AvatarLookPreparer(
    private val assets: RiveAvatarAssets,
    private val tones: SharedToneCache,
    private val worker: CoroutineDispatcher,
    private val compose: (AvatarToneRequest, AvatarToneCache) -> PreparedAvatarTone = AvatarTextureComposer::compose
) {
    private val mutex = Mutex()

    suspend fun prepare(
        context: Context,
        gender: AvatarGender,
        outfit: AvatarOutfit,
        shape: AvatarBodyShape,
        runningAmount: Int,
        head: GeneratedHeadLayers?,
        motion: AvatarMotion
    ) {
        val look = AvatarLookKey(outfit, shape, runningAmount.coerceIn(MIN_RUNNING_AMOUNT, MAX_RUNNING_AMOUNT))
        val prepared = assets.load(context, gender)
        mutex.withLock {
            if (tones.hit(gender, setOf(motion), head, look) != null) return
            withContext(worker) {
                val embedded = prepared.embeddedImages(motion)
                val request = AvatarToneRequests.build(
                    version = 0,
                    identity = 0,
                    gender = gender,
                    look = look,
                    variant = head?.variants?.getValue(gender) ?: prepared.builtIn,
                    front = if (motion == AvatarMotion.STAND) AvatarToneRequests.frontOf(embedded) else null,
                    run = if (motion == AvatarMotion.RUN) AvatarToneRequests.runOf(embedded) else null,
                    generatedSkinColor = head?.skinColor
                )
                val seed = tones.seed(gender, head)
                val result = compose(request, seed)
                ensureActive()
                tones.put(gender, motion, head, look, result.toCache(seed))
            }
        }
    }

    companion object {
        val shared = AvatarLookPreparer(RiveAvatarAssets.shared, SharedToneCache.shared, Dispatchers.Default)
    }
}
