package com.a207.runkk.app.core.avatar

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.RendererType
import java.util.EnumMap
import java.util.EnumSet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 네이티브 Rive 아바타 뷰(PoC MainActivity의 Rive 파일 선택·FileAssetLoader·비동기 합성·캐시 버전 관리를 화면과 떼어 낸 것).
 *
 * - 동작마다 Rive 뷰 하나를 처음 필요할 때 만든다(서기 Idle, 달리기 Runner). 남녀나 머리가 바뀌면 같은 뷰에 새 파일을 다시 읽는다.
 * - Rive 파일·내장 머리·앞 단계 몸 바이트는 [RiveAvatarAssets]가 IO에서 미리 읽은 것만 쓴다(주 스레드에서 자산을 읽지 않는다).
 * - 피부톤·의상·체형 텍스처는 단일 합성 스레드에서 만든다. 계산은 한 번에 하나, 대기는 가장 최근 요청 하나만 둔다.
 *   요청 버전([toneVersion])과 정체 버전([identity])이 지금과 다르면 결과를 버린다.
 * - 새로 읽은 파일의 기본 그림이 보이지 않도록 그 동작의 텍스처와 body_shape를 처음 넣은 뒤에야 보인다. 보이게 된 뒤 첫 판이 그려진 때 [onReady].
 * - 합성 결과는 프로세스 공용 캐시([SharedToneCache])에도 넣는다. 합성하기 전에 그 캐시를 먼저 보고, 같은 머리·모습이 있으면 합성
 *   스레드를 거치지 않고 바로 넣는다(미리 준비 [prepareRiveAvatarLook], DR-10). 같은 머리면 몸 분리 결과도 다시 쓴다.
 * - 좌우 뒤집기는 달리기 플레이어만 `scaleX = -1`이다([RivePlayerRules.scaleX], D-762).
 * - 화면이 RESUMED가 아니면 재생하지 않고 결과도 RESUMED 때 넣는다.
 */
internal class RiveAvatarView(context: Context) : FrameLayout(context) {
    var onError: (Throwable) -> Unit = {}

    /**
     * 아바타는 누르기에 반응하지 않는다. Rive 플레이어가 상태 기계 입력으로 누르기를 소비하면 칸을 감싼 Compose 클릭(홈 아바타 → 내 러너)이
     * 받지 못한다(2026-11-11 실기기 3차). 누르기를 넘겨 부모가 받게 한다.
     */
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean = false

    /**
     * 지금 동작의 플레이어가 첫 텍스처를 받아 보이게 된 뒤 첫 판이 실제로 그려진 때(주 스레드). 판이 [READY_FRAME_TIMEOUT_MS] 안에
     * 오지 않아도 온다. 정체·동작이 바뀌면 다시 온다.
     */
    var onReady: () -> Unit = {}

    private val readyNotice = FirstFrameNotice { if (!isReleased) onReady() }
    private val readyTimeout = Runnable { readyNotice.timedOut() }

    private val tones = SharedToneCache.shared

    // 합성 스레드가 프로세스 종료를 막지 않도록 데몬으로 둔다.
    private val io: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "runkk-avatar-tone").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())
    private val players = EnumMap<AvatarMotion, PacedRiveAnimationView>(AvatarMotion::class.java)

    /** 달리기 동작 재생 빠르기(1 = 원래, 러닝 중 페이스에 맞춘다). */
    var runSpeed: Float = 1f
        set(value) {
            field = value
            players[AvatarMotion.RUN]?.playbackSpeed = value
        }

    /**
     * 서기·달리기 플레이어를 처음부터 함께 만들어 둔다(트랙 칸, S15P31A207-335). 두 동작 텍스처를 한 번에 합성하므로 자세가 바뀌어도 새
     * 플레이어를 만들며 몸이 비지 않는다.
     */
    var warmAllMotions: Boolean = false

    private val applied = EnumSet.noneOf(AvatarMotion::class.java)
    private var frontLoader: FrontAvatarAssetLoader? = null
    private var runLoader: RunAvatarAssetLoader? = null
    private var state: AvatarRenderState? = null
    private var assets: PreparedRiveAssets? = null
    private var sources: AvatarVariantAssets? = null
    private var identity = 0

    /** 지금 정체의 생성 머리(공용 캐시 키, 내장 머리면 null). */
    private var head: GeneratedHeadLayers? = null
    private var toneVersion = 0
    private var isToneInFlight = false
    private var pendingTone: AvatarToneRequest? = null
    private var pendingResult: PreparedAvatarTone? = null
    private var cache = AvatarToneCache()
    private var playing: AvatarMotion? = null
    private var isResumed = false
    private var isReleased = false
    private var lifecycle: Lifecycle? = null
    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> onResumed()
            Lifecycle.Event.ON_PAUSE -> onPaused()
            else -> Unit
        }
    }

    /**
     * [next]를 그린다. [prepared]는 IO에서 미리 읽은 [next] 몸체의 바이트([RiveAvatarAssets])이고, 아직 읽는 중이면 null이라
     * 그리지 않고 기다린다(준비되면 다시 부른다). 네이티브 런타임 초기화는 프로세스에서 한 번이고, 실패를 바로 알려 화면이 앞 단계
     * 그림으로 바꾸게 한다.
     */
    fun render(next: AvatarRenderState, prepared: PreparedRiveAssets?) {
        if (isReleased) return
        try {
            RiveRuntime.ensureInitialized(context)
        } catch (error: LinkageError) {
            // 네이티브 Rive 라이브러리를 읽지 못했다(UnsatisfiedLinkError 등). 앱을 죽이지 않고 알린다.
            state = null
            fail(error)
            return
        }
        if (prepared == null || prepared.gender != next.gender) return
        val previous = state
        val previousAssets = assets
        state = next
        assets = prepared
        try {
            val isNewIdentity = previous == null || previousAssets !== prepared ||
                previous.gender != next.gender || previous.headLayers !== next.headLayers
            if (isNewIdentity) changeIdentity(next, prepared)
            val isNewPlayer = RivePlayerRules.motionsToCreate(next.motion, warmAllMotions)
                .map { ensurePlayer(it, prepared) }
                .any { it }
            val isNewLook = previous != null &&
                (
                    previous.runningAmount != next.runningAmount ||
                        previous.outfit != next.outfit ||
                        previous.shape != next.shape
                    )
            if (isNewIdentity || isNewPlayer || isNewLook) requestTone()
            updateMotion()
        } catch (error: RuntimeException) {
            // 다음 render가 정체(파일·머리 원본)부터 다시 읽게 한다. 앞 그림은 그대로 둔다.
            state = null
            fail(error)
        }
    }

    /**
     * 지금 동작 플레이어가 그린 판을 [width]×[height]로 읽는다(전신 정지 그림, [RiveAvatarStill]). 그 동작의 첫 텍스처가 아직 들어가지
     * 않았거나 그릴 면이 아직 없으면 null이다. 주 스레드에서 부른다.
     */
    fun snapshot(width: Int, height: Int): Bitmap? {
        if (isReleased || width <= 0 || height <= 0) return null
        val motion = state?.motion ?: return null
        if (motion !in applied) return null
        val player = players[motion]?.takeIf { it.isAvailable } ?: return null
        return player.getBitmap(width, height)
    }

    fun release() {
        if (isReleased) return
        isReleased = true
        io.shutdownNow()
        main.removeCallbacksAndMessages(null)
        readyNotice.cancel()
        lifecycle?.removeObserver(lifecycleObserver)
        lifecycle = null
        players.values.forEach { it.pause() }
        removeAllViews()
        players.clear()
        frontLoader?.clearHandles()
        runLoader?.clearHandles()
        // 로더는 Builder가 뷰의 의존 목록으로 놓는다. 여기서는 참조만 끊는다.
        frontLoader = null
        runLoader = null
        pendingTone = null
        pendingResult = null
        cache = AvatarToneCache()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val owner = findViewTreeLifecycleOwner()
        if (owner == null) {
            onResumed()
        } else {
            lifecycle = owner.lifecycle.also { it.addObserver(lifecycleObserver) }
        }
    }

    override fun onDetachedFromWindow() {
        lifecycle?.removeObserver(lifecycleObserver)
        lifecycle = null
        onPaused()
        super.onDetachedFromWindow()
    }

    /** 남녀나 머리가 바뀌었다. 전체 머리 원본을 먼저 읽어 남녀 자산이 섞인 채 남지 않게 한 뒤 열린 뷰에 새 파일을 읽는다. */
    private fun changeIdentity(next: AvatarRenderState, prepared: PreparedRiveAssets) {
        val nextSources = next.headLayers?.variants?.getValue(next.gender) ?: prepared.builtIn
        identity++
        invalidateTone()
        readyNotice.cancel()
        head = next.headLayers
        // 같은 머리를 다른 뷰나 미리 준비가 합성해 두었으면 몸 분리·텍스처를 다시 쓴다.
        cache = tones.seed(next.gender, next.headLayers)
        applied.clear()
        sources = nextSources
        players.forEach { (motion, view) ->
            view.pause()
            view.visibility = INVISIBLE
            when (motion) {
                AvatarMotion.STAND -> checkNotNull(frontLoader).clearHandles()
                AvatarMotion.RUN -> checkNotNull(runLoader).clearHandles()
            }
            // setRiveResource는 컨트롤러의 파일을 바꾸고 앞 장면을 SDK가 놓는다. Builder로 넣은 로더는 그대로 쓴다.
            loadResource(view, motion, prepared)
            checkLoaded(motion)
        }
        playing = null
        frontLoader?.setVariantSources(nextSources.front)
    }

    /** [motion]의 뷰가 없으면 만든다. 새로 만들었으면 true. */
    private fun ensurePlayer(motion: AvatarMotion, prepared: PreparedRiveAssets): Boolean {
        if (motion in players) return false
        val loader = when (motion) {
            AvatarMotion.STAND -> FrontAvatarAssetLoader(prepared.legacyStandingBody).also { frontLoader = it }
            AvatarMotion.RUN -> RunAvatarAssetLoader().also { runLoader = it }
        }
        val view = PacedRiveAnimationView(
            RiveAnimationView.Builder(context)
                .setResource(prepared.riv(motion))
                .setArtboardName(RiveAvatarScene.artboard(motion))
                .setStateMachineName(RiveAvatarScene.stateMachine(motion))
                .setShouldLoadCDNAssets(false)
                .setRendererType(RendererType.Rive)
                .setFit(Fit.CONTAIN)
                .setAutoplay(false)
                .setAssetLoader(loader)
        )
        if (motion == AvatarMotion.RUN) view.playbackSpeed = runSpeed
        // 지금 동작 플레이어가 그린 판만 준비 알림으로 친다.
        view.onFrameDrawn = { if (state?.motion == motion && view.visibility == VISIBLE) readyNotice.frameDrawn() }
        view.visibility = INVISIBLE
        // Builder는 창에 붙을 때 읽는다. 지금 한 번 읽어 자산 자리와 상태 입력을 바로 쓸 수 있게 한다
        // (onAttachedToWindow는 이미 파일이 있으면 다시 읽지 않는다).
        loadResource(view, motion, prepared)
        players[motion] = view
        addView(view, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        checkLoaded(motion)
        if (motion == AvatarMotion.STAND) frontLoader?.setVariantSources(checkNotNull(sources).front)
        return true
    }

    /** 미리 읽은 Rive 파일 바이트를 넣는다(주 스레드에서 res/raw를 읽지 않는다). */
    private fun loadResource(view: RiveAnimationView, motion: AvatarMotion, prepared: PreparedRiveAssets) {
        val machine = RiveAvatarScene.stateMachine(motion)
        view.setRiveBytes(
            prepared.riv(motion),
            artboardName = RiveAvatarScene.artboard(motion),
            stateMachineName = machine,
            autoplay = false,
            fit = Fit.CONTAIN
        )
        if (motion == AvatarMotion.RUN) {
            view.setBooleanState(machine, RiveAvatarScene.INPUT_IS_RUNNING, true)
            view.setNumberState(machine, RiveAvatarScene.INPUT_OUTFIT_ID, 0f)
        }
    }

    private fun checkLoaded(motion: AvatarMotion) = when (motion) {
        AvatarMotion.STAND -> check(frontLoader?.hasApprovedAssets() == true) { "approved front avatar slots missing" }
        AvatarMotion.RUN -> check(runLoader?.hasApprovedAssets() == true) { "approved run avatar slots missing" }
    }

    private fun invalidateTone() {
        toneVersion++
        pendingTone = null
        pendingResult = null
    }

    private fun requestTone() {
        val current = state ?: return
        val variant = sources ?: return
        if (assets == null) return
        val front = frontLoader?.takeIf { AvatarMotion.STAND in players }
        val run = runLoader?.takeIf { AvatarMotion.RUN in players }
        if (front == null && run == null) return
        pendingResult = null
        val request = AvatarToneRequests.build(
            version = ++toneVersion,
            identity = identity,
            gender = current.gender,
            look = current.look,
            variant = variant,
            front = front?.let {
                AvatarToneRequests.FrontOriginals(it.originalBodyBytes(), it.originalFullnessBytes())
            },
            run = run?.let { AvatarToneRequests.RunOriginals(it.originalSkinBytes(), it.originalFullnessBytes()) },
            generatedSkinColor = current.headLayers?.skinColor
        )
        val motions = buildSet {
            if (front != null) add(AvatarMotion.STAND)
            if (run != null) add(AvatarMotion.RUN)
        }
        val shared = tones.hit(current.gender, motions, head, current.look)
        if (shared == null) {
            pendingTone = request
            startNextTone()
            return
        }
        // 미리 준비했거나 다른 뷰가 같은 모습을 합성했다: 합성 스레드를 거치지 않고 바로 넣는다.
        pendingTone = null
        val prepared = PreparedAvatarTone(
            request = request,
            face = shared.face,
            hair = shared.face?.hair ?: request.hair,
            skinBases = shared.skinBases,
            skinRenders = shared.skinRenders,
            frontOverlay = shared.frontOverlay,
            runOverlay = shared.runOverlay
        )
        cache = prepared.toCache(cache)
        if (isResumed) apply(prepared) else pendingResult = prepared
    }

    private fun startNextTone() {
        if (isReleased || isToneInFlight) return
        val request = pendingTone ?: return
        pendingTone = null
        isToneInFlight = true
        val snapshot = cache
        io.execute {
            // OutOfMemoryError 같은 Error도 잡는다. 결과를 반드시 주 스레드로 돌려줘야 isToneInFlight가 풀린다.
            @Suppress("TooGenericExceptionCaught")
            val result = try {
                Result.success(AvatarTextureComposer.compose(request, snapshot))
            } catch (error: Throwable) {
                Result.failure(error)
            }
            main.post { onToneComputed(request, result) }
        }
    }

    private fun onToneComputed(request: AvatarToneRequest, result: Result<PreparedAvatarTone>) {
        isToneInFlight = false
        if (isReleased) return
        if (request.identity == identity) result.getOrNull()?.let { cache = it.toCache(cache) }
        if (isCurrent(request)) result.getOrNull()?.let(::share)
        if (isCurrent(request)) {
            result.fold(
                onSuccess = { prepared -> if (isResumed) apply(prepared) else pendingResult = prepared },
                onFailure = ::fail
            )
        }
        startNextTone()
    }

    private fun isCurrent(request: AvatarToneRequest): Boolean {
        val current = state ?: return false
        return !isReleased && request.version == toneVersion && request.identity == identity &&
            request.gender == current.gender && request.shape == current.shape
    }

    /** 지금 모습의 합성 결과를 공용 캐시에 넣는다(동작마다 그 요청에 든 것만). */
    private fun share(prepared: PreparedAvatarTone) {
        val request = prepared.request
        val look = AvatarLookKey(request.outfit, request.shape, request.amount)
        val result = prepared.toCache(AvatarToneCache())
        if (request.face != null) tones.put(request.gender, AvatarMotion.STAND, head, look, result)
        if (request.runFullness != null) tones.put(request.gender, AvatarMotion.RUN, head, look, result)
    }

    /** 최신 요청의 텍스처와 body_shape를 함께 넣는다(몸과 볼 레이어가 같이 바뀐다). */
    private fun apply(prepared: PreparedAvatarTone) {
        if (!isCurrent(prepared.request)) return
        try {
            players[AvatarMotion.STAND]?.let { applyStand(it, prepared) }
            players[AvatarMotion.RUN]?.let { applyRun(it, prepared) }
            updateMotion()
        } catch (error: RuntimeException) {
            fail(error)
        }
    }

    private fun applyStand(view: RiveAnimationView, prepared: PreparedAvatarTone) {
        val request = prepared.request
        val loader = checkNotNull(frontLoader)
        prepared.face?.let { loader.replaceFace(it.bytes, checkNotNull(prepared.hair)) }
        prepared.textures[BodySkinLayers.STANDING_BODY]?.let(loader::replaceBody)
        loader.replaceFullnessOverlay(prepared.frontOverlay?.bytes)
        val machine = RiveAvatarScene.stateMachine(AvatarMotion.STAND)
        view.setNumberState(machine, RiveAvatarScene.INPUT_BODY_SHAPE, RiveAvatarScene.bodyShapeValue(request.shape))
        if (loader.hasSelectedBody()) {
            // 남자 서기 riv만 표면 입력이 있다. 표면은 합성이 실제로 그린 묶음 값이다(D-747).
            val outfit = request.outfit
            view.setNumberState(
                machine,
                RiveAvatarScene.INPUT_SELECTED_TOP,
                RiveAvatarScene.topSurface(request.gender, outfit.top, request.shape)
            )
            view.setNumberState(
                machine,
                RiveAvatarScene.INPUT_SELECTED_BOTTOM,
                RiveAvatarScene.bottomSurface(outfit.bottom)
            )
            view.setNumberState(
                machine,
                RiveAvatarScene.INPUT_SELECTED_SHOES,
                RiveAvatarScene.shoesSurface(outfit.shoes)
            )
        }
        applied += AvatarMotion.STAND
    }

    private fun applyRun(view: RiveAnimationView, prepared: PreparedAvatarTone) {
        val loader = checkNotNull(runLoader)
        loader.applySkin(prepared.textures.filterKeys { it != BodySkinLayers.STANDING_BODY })
        loader.replaceFullnessOverlay(checkNotNull(prepared.runOverlay).bytes)
        view.setNumberState(
            RiveAvatarScene.stateMachine(AvatarMotion.RUN),
            RiveAvatarScene.INPUT_BODY_SHAPE,
            RiveAvatarScene.bodyShapeValue(prepared.request.shape)
        )
        applied += AvatarMotion.RUN
    }

    private fun updateMotion() {
        val current = state ?: return
        players.forEach { (motion, view) ->
            val visibility = RivePlayerRules.visibility(motion, current.motion, applied)
            val isShown = motion == current.motion && RivePlayerRules.becameVisible(view.visibility, visibility)
            view.visibility = visibility
            view.scaleX = RivePlayerRules.scaleX(motion, current.mirrored)
            if (isShown) {
                readyNotice.shown()
                main.removeCallbacks(readyTimeout)
                main.postDelayed(readyTimeout, READY_FRAME_TIMEOUT_MS)
            }
        }
        // 지금 동작이 준비 전이면 보이고 있는 앞 동작을 계속 재생한다(RivePlayerRules.visibility).
        val shown = players.keys.firstOrNull {
            RivePlayerRules.visibility(it, current.motion, applied) == VISIBLE
        }
        val wanted = shown.takeIf { isResumed }
        if (wanted == playing) return
        players.forEach { (motion, view) -> if (motion != wanted) view.pause() }
        wanted?.let { players[it]?.play(RiveAvatarScene.stateMachine(it), isStateMachine = true) }
        playing = wanted
    }

    private fun onResumed() {
        if (isReleased) return
        isResumed = true
        pendingResult?.let {
            pendingResult = null
            apply(it)
        }
        updateMotion()
    }

    private fun onPaused() {
        isResumed = false
        players.values.forEach { it.pause() }
        playing = null
    }

    private fun fail(error: Throwable) {
        if (!isReleased) onError(error)
    }

    private companion object {
        /** 보이게 된 뒤 첫 판을 기다리는 최대 시간. 넘으면 그냥 준비로 알린다(창에 붙지 않아 판이 없는 경우 등). */
        const val READY_FRAME_TIMEOUT_MS = 400L
    }
}
