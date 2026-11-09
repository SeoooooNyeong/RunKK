package com.a207.runkk.app.core.avatar

import android.content.Context
import android.os.Handler
import android.os.Looper
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
 * - 피부톤·의상·체형 텍스처는 단일 합성 스레드에서 만든다. 계산은 한 번에 하나, 대기는 가장 최근 요청 하나만 둔다.
 *   요청 버전([toneVersion])과 정체 버전([identity])이 지금과 다르면 결과를 버린다.
 * - 새로 읽은 파일의 기본 그림이 보이지 않도록 그 동작의 텍스처와 body_shape를 처음 넣은 뒤에야 보인다.
 * - 화면이 RESUMED가 아니면 재생하지 않고 결과도 RESUMED 때 넣는다.
 */
internal class RiveAvatarView(context: Context) : FrameLayout(context) {
    var onError: (Throwable) -> Unit = {}

    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val legacyStandingBody: ByteArray by lazy {
        resources.openRawResource(R.raw.avatar_legacy_standing_body).use { it.readBytes() }
    }
    private val builtIn = EnumMap<AvatarGender, AvatarVariantAssets>(AvatarGender::class.java)
    private val players = EnumMap<AvatarMotion, RiveAnimationView>(AvatarMotion::class.java)
    private val applied = EnumSet.noneOf(AvatarMotion::class.java)
    private var frontLoader: FrontAvatarAssetLoader? = null
    private var runLoader: RunAvatarAssetLoader? = null
    private var state: AvatarRenderState? = null
    private var sources: AvatarVariantAssets? = null
    private var identity = 0
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

    fun render(next: AvatarRenderState) {
        if (isReleased) return
        val previous = state
        state = next
        try {
            RiveRuntime.ensureInitialized(context)
        } catch (error: LinkageError) {
            // 네이티브 Rive 라이브러리를 읽지 못했다(UnsatisfiedLinkError 등). 앱을 죽이지 않고 알린다.
            state = null
            fail(error)
            return
        }
        try {
            val isNewIdentity =
                previous == null || previous.gender != next.gender || previous.headLayers !== next.headLayers
            if (isNewIdentity) changeIdentity(next)
            val isNewPlayer = ensurePlayer(next.motion, next.gender)
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

    fun release() {
        if (isReleased) return
        isReleased = true
        io.shutdownNow()
        main.removeCallbacksAndMessages(null)
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
    private fun changeIdentity(next: AvatarRenderState) {
        val nextSources = next.headLayers?.variants?.getValue(next.gender)
            ?: builtIn.getOrPut(next.gender) { AvatarVariantAssets.loadBuiltIn(context.assets, next.gender) }
        identity++
        invalidateTone()
        cache = AvatarToneCache()
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
            loadResource(view, motion, next.gender)
            checkLoaded(motion)
        }
        playing = null
        frontLoader?.setVariantSources(nextSources.front)
    }

    /** [motion]의 뷰가 없으면 만든다. 새로 만들었으면 true. */
    private fun ensurePlayer(motion: AvatarMotion, gender: AvatarGender): Boolean {
        if (motion in players) return false
        val loader = when (motion) {
            AvatarMotion.STAND -> FrontAvatarAssetLoader(legacyStandingBody).also { frontLoader = it }
            AvatarMotion.RUN -> RunAvatarAssetLoader().also { runLoader = it }
        }
        val view = RiveAnimationView.Builder(context)
            .setResource(RiveAvatarScene.rawResource(gender, motion))
            .setArtboardName(RiveAvatarScene.artboard(motion))
            .setStateMachineName(RiveAvatarScene.stateMachine(motion))
            .setShouldLoadCDNAssets(false)
            .setRendererType(RendererType.Rive)
            .setFit(Fit.CONTAIN)
            .setAutoplay(false)
            .setAssetLoader(loader)
            .build()
        view.visibility = INVISIBLE
        // Builder는 창에 붙을 때 읽는다. 지금 한 번 읽어 자산 자리와 상태 입력을 바로 쓸 수 있게 한다
        // (onAttachedToWindow는 이미 파일이 있으면 다시 읽지 않는다).
        loadResource(view, motion, gender)
        players[motion] = view
        addView(view, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        checkLoaded(motion)
        if (motion == AvatarMotion.STAND) frontLoader?.setVariantSources(checkNotNull(sources).front)
        return true
    }

    private fun loadResource(view: RiveAnimationView, motion: AvatarMotion, gender: AvatarGender) {
        val machine = RiveAvatarScene.stateMachine(motion)
        view.setRiveResource(
            RiveAvatarScene.rawResource(gender, motion),
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
        val front = frontLoader?.takeIf { AvatarMotion.STAND in players }
        val run = runLoader?.takeIf { AvatarMotion.RUN in players }
        if (front == null && run == null) return
        val stage = current.shape.stage
        pendingResult = null
        pendingTone = AvatarToneRequest(
            version = ++toneVersion,
            identity = identity,
            gender = current.gender,
            amount = current.runningAmount,
            outfit = current.outfit,
            shape = current.shape,
            face = front?.originalFaceBytes(),
            hair = front?.originalHairBytes(),
            frontFullness = front?.let {
                if (stage ==
                    0
                ) {
                    it.originalFullnessBytes()
                } else {
                    variant.frontFullness.getValue(stage)
                }
            },
            runFullness = run?.let {
                if (stage ==
                    0
                ) {
                    it.originalFullnessBytes()
                } else {
                    variant.sideFullness.getValue(stage)
                }
            },
            sideLayers = run?.let { variant.sideLayers },
            skinAssets = buildMap {
                front?.let {
                    put(BodySkinLayers.STANDING_BODY, it.originalBodyBytes())
                    put(BodySkinLayers.STANDING_BODY_LEGACY, legacyStandingBody)
                    it.originalSelectedBodyBytes()?.let { bytes -> put(MaleSelectedBody.ASSET, bytes) }
                }
                run?.let { putAll(it.originalSkinBytes()) }
            },
            generatedSkinColor = current.headLayers?.skinColor
        )
        startNextTone()
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

    /** 최신 요청의 텍스처와 body_shape를 함께 넣는다(몸과 볼 레이어가 같이 바뀐다). */
    private fun apply(prepared: PreparedAvatarTone) {
        val request = prepared.request
        if (!isCurrent(request)) return
        try {
            val textures = prepared.textures
            players[AvatarMotion.STAND]?.let { view ->
                val loader = checkNotNull(frontLoader)
                prepared.face?.let { loader.replaceFace(it.bytes, checkNotNull(prepared.hair)) }
                textures[BodySkinLayers.STANDING_BODY]?.let(loader::replaceBody)
                textures[MaleSelectedBody.ASSET]?.let(loader::replaceSelectedBody)
                loader.replaceFullnessOverlay(prepared.frontOverlay?.bytes)
                val machine = RiveAvatarScene.stateMachine(AvatarMotion.STAND)
                view.setNumberState(
                    machine,
                    RiveAvatarScene.INPUT_BODY_SHAPE,
                    RiveAvatarScene.bodyShapeValue(request.shape)
                )
                if (loader.hasSelectedBody()) {
                    val outfit = request.outfit
                    view.setNumberState(
                        machine,
                        RiveAvatarScene.INPUT_SELECTED_TOP,
                        RiveAvatarScene.topSurface(request.gender, outfit.top)
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
            players[AvatarMotion.RUN]?.let { view ->
                val loader = checkNotNull(runLoader)
                loader.applySkin(
                    textures.filterKeys { it != BodySkinLayers.STANDING_BODY && it != MaleSelectedBody.ASSET }
                )
                loader.replaceFullnessOverlay(checkNotNull(prepared.runOverlay).bytes)
                view.setNumberState(
                    RiveAvatarScene.stateMachine(AvatarMotion.RUN),
                    RiveAvatarScene.INPUT_BODY_SHAPE,
                    RiveAvatarScene.bodyShapeValue(request.shape)
                )
                applied += AvatarMotion.RUN
            }
            updateMotion()
        } catch (error: RuntimeException) {
            fail(error)
        }
    }

    private fun updateMotion() {
        val current = state ?: return
        players.forEach { (motion, view) ->
            view.visibility = when {
                motion != current.motion -> GONE
                motion in applied -> VISIBLE
                else -> INVISIBLE
            }
        }
        val wanted = current.motion.takeIf { isResumed && it in applied }
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
}
