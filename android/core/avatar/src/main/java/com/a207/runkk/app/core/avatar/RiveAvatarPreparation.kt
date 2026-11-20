package com.a207.runkk.app.core.avatar

import android.content.Context
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

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
 * - [disk](S15P31A207-345 9회차): 기기 칸을 넘기면 같은 입력의 기기 사본이 있을 때 합성하지 않고 그것을 공용 캐시에 올리고, 없으면
 *   합성한 뒤 그 칸에 남긴다(앱을 다시 켜도 다시 합성하지 않는다). 머리가 기기 사본으로 가를 수 없는 생성 머리면 남기지 않는다.
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
    motion: AvatarMotion = AvatarMotion.STAND,
    disk: AvatarLookDisk? = null
) {
    AvatarLookPreparer.shared.prepare(context, gender, outfit, bodyShape, runningAmount, headLayers, motion, disk)
}

/**
 * 기기 공용 칸에 남긴 그 모습의 전신 정지 그림([RiveAvatarStill]이 남긴 것)을 프로세스 캐시에 올린다(S15P31A207-345 9회차, 랭킹 시상대).
 * 같은 칸 크기로 [RiveAvatarStill]을 그리면 Rive 없이 첫 판부터 그림이 보인다. 올린 그림 수(없으면 0). [look]의 머리는 칸이 그릴 것과
 * 같은 인스턴스를 넘긴다(참조로 비교한다).
 */
suspend fun preloadRiveAvatarStills(context: Context, look: RiveAvatarLook): Int =
    withContext(Dispatchers.IO) { AvatarStillDisk.load(AvatarLookDisk.shared(context), look) }

/**
 * 트랙 주자 칸 미리 데우기(출발 전 카운트다운·크루런 대기실, S15P31A207-335 6회차). Rive 칸을 만들지 않고 그 모습의 서기·달리기 Rive 파일을
 * 가져오기 스레드에서 미리 가져와 붙잡고([RivFiles]), 두 동작 텍스처를 공용 캐시에 미리 합성한다. 출발하면 트랙 칸이 붙잡아 둔 파일과
 * 텍스처를 바로 받아 주 스레드에서 파일을 읽거나 합성을 기다리지 않는다.
 *
 * 취소될 때까지 붙잡는다(주 스레드 코루틴, 예: LaunchedEffect). 취소되면 놓고, 놓은 뒤 잠시 안에 트랙 칸이 가져가지 않은 파일은
 * 버린다. 준비 실패는 던지지 않는다(트랙 칸이 다시 읽고 합성한다).
 */
suspend fun holdTrackRiveAvatar(context: Context, look: RiveAvatarLook) {
    val holds = try {
        val prepared = RiveAvatarAssets.shared.load(context, look.gender)
        RiveRuntime.ensureInitialized(context)
        AvatarMotion.entries.map { RivFiles.shared.hold(RivKey(prepared, it, forTrack = true)) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "track avatar warmup skipped")
        emptyList()
    } catch (e: LinkageError) {
        Timber.w(e, "track avatar warmup skipped")
        emptyList()
    }
    try {
        AvatarMotion.entries.forEach { motion ->
            try {
                prepareRiveAvatarLook(
                    context,
                    look.gender,
                    look.outfit,
                    look.bodyShape,
                    look.runningAmount,
                    look.headLayers,
                    motion
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "track avatar tone warmup skipped")
            } catch (e: OutOfMemoryError) {
                Timber.w(e, "track avatar tone warmup skipped")
            }
        }
        awaitCancellation()
    } finally {
        holds.forEach { it.release() }
    }
}

/**
 * [prepareRiveAvatarLook]의 구현. 시험이 자산·캐시·합성 함수를 넣는다. 구간 표식(AvatarPrepare tone-…)을 남긴다: 차례 기다리기(tone-wait),
 * 기기 사본 읽기·쓰기(tone-disk-read·tone-disk-write), Rive 파일 읽기(riv-read), 합성 스레드 기다리기(tone-thread), 내장 그림
 * 풀기(tone-embedded), 합성(tone-compose).
 */
internal class AvatarLookPreparer(
    private val assets: RiveAvatarAssets,
    private val tones: SharedToneCache,
    private val worker: CoroutineDispatcher,
    private val io: CoroutineDispatcher = Dispatchers.IO,
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
        motion: AvatarMotion,
        disk: AvatarLookDisk? = null
    ) {
        val look = AvatarLookKey(outfit, shape, runningAmount.coerceIn(MIN_RUNNING_AMOUNT, MAX_RUNNING_AMOUNT))
        val waitAt = AvatarPrepareTrace.now()
        mutex.withLock {
            AvatarPrepareTrace.mark("tone-wait", waitAt, "motion=$motion")
            if (tones.hit(gender, setOf(motion), head, look) != null) return
            val key = disk?.toneKey(gender, motion, look, head)
            if (disk != null && key != null) {
                val readAt = AvatarPrepareTrace.now()
                val saved = withContext(io) { disk.readTone(key, gender, motion) }
                AvatarPrepareTrace.mark("tone-disk-read", readAt, "motion=$motion hit=${saved != null}")
                if (saved != null) {
                    // 앱을 다시 켜도 같은 입력이면 합성하지 않는다: 기기 사본을 공용 캐시에 올려 홈 칸이 바로 적중한다.
                    tones.put(gender, motion, head, look, saved)
                    return
                }
            }
            val loadAt = AvatarPrepareTrace.now()
            val prepared = assets.load(context, gender)
            AvatarPrepareTrace.mark("riv-read", loadAt, "gender=$gender")
            val queuedAt = AvatarPrepareTrace.now()
            val made = withContext(worker) {
                AvatarPrepareTrace.mark("tone-thread", queuedAt, "motion=$motion")
                val embeddedAt = AvatarPrepareTrace.now()
                val embedded = prepared.embeddedImages(motion)
                AvatarPrepareTrace.mark("tone-embedded", embeddedAt, "motion=$motion")
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
                val composeAt = AvatarPrepareTrace.now()
                val result = compose(request, seed)
                AvatarPrepareTrace.mark("tone-compose", composeAt, "motion=$motion")
                ensureActive()
                result.toCache(seed).also { tones.put(gender, motion, head, look, it) }
            }
            if (disk != null && key != null) {
                val writeAt = AvatarPrepareTrace.now()
                withContext(io) { disk.writeTone(key, gender, motion, made.only(motion)) }
                AvatarPrepareTrace.mark("tone-disk-write", writeAt, "motion=$motion")
            }
        }
    }

    companion object {
        val shared = AvatarLookPreparer(RiveAvatarAssets.shared, SharedToneCache.shared, Dispatchers.Default)
    }
}
