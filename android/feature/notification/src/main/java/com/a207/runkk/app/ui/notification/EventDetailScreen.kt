package com.a207.runkk.app.ui.notification

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.LocalRunkkColors
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicReference
import timber.log.Timber

/**
 * 이벤트 상세 웹 페이지(이벤트 계약 4절, 5절): 상단 바(뒤로, 제목) 아래 웹뷰가 상세 주소에 지금 앱 테마(?theme=light|dark)를 붙여 연다.
 *
 * 보안
 * - JS는 켜고 파일·콘텐츠 접근과 혼합 콘텐츠는 끈다.
 * - 주 문서는 API origin 안에서만 옮긴다. 다른 origin 링크는 바깥 브라우저로 넘기고(http·https만) 웹뷰는 그대로 둔다.
 * - 다른 origin 자원 요청(iframe·스크립트·그림 등)은 막는다: 다른 origin 코드가 RunkkApp 브리지에 닿지 않게.
 * - 브리지(RunkkAppBridge)는 매 호출마다 주 문서 origin을 다시 확인한다.
 * local 플레이버(http://10.0.2.2:8080)도 같은 규칙이다(같은 origin의 http는 혼합 콘텐츠가 아니다).
 */
@Composable
fun EventDetailRoute(key: Routes.EventDetail, viewModel: EventDetailViewModel = hiltViewModel()) {
    val navigator = LocalRunkkNavigator.current
    val context = LocalContext.current
    val isReady by viewModel.isReady.collectAsStateWithLifecycle()
    LaunchedEffect(key.slug) { viewModel.start(key.slug) }
    // open("run"): 홈 자유런 카드처럼 OS 위치 권한이 없으면 권한 창을 먼저 띄우고, 답과 상관없이 자유런 화면으로 간다.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        navigator.navigate(Routes.RUN)
    }
    val onOpen: (EventPageTarget) -> Unit = { target ->
        when (target) {
            EventPageTarget.CELEBRITY -> navigator.navigate(Routes.RECORD_RUN_CELEB)

            EventPageTarget.EVENTS -> navigator.navigate(Routes.EVENTS)

            EventPageTarget.RUN -> if (LOCATION_PERMISSIONS.any {
                    context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
                }
            ) {
                navigator.navigate(Routes.RUN)
            } else {
                permission.launch(LOCATION_PERMISSIONS)
            }
        }
    }
    EventDetailScreen(
        title = key.title,
        url = key.url,
        origin = viewModel.webOrigin,
        isReady = isReady,
        accessToken = viewModel::accessToken,
        onOpen = onOpen,
        onClose = navigator::back,
        onShare = { text -> shareText(context, text) }
    )
}

@Composable
internal fun EventDetailScreen(
    title: String,
    url: String,
    origin: String,
    isReady: Boolean,
    accessToken: () -> String?,
    onOpen: (EventPageTarget) -> Unit,
    onClose: () -> Unit,
    onShare: (String) -> Unit
) {
    val isDark = LocalRunkkColors.current.isDark
    val pageUrl = remember(url, isDark) { withTheme(url, isDark) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    val latestOpen by rememberUpdatedState(onOpen)
    val latestClose by rememberUpdatedState(onClose)
    val latestShare by rememberUpdatedState(onShare)
    val back = {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onClose()
    }
    BackHandler(onBack = back)
    Column(modifier = Modifier.fillMaxSize().background(RunkkColors.Background)) {
        RunkkTopBar(title = title, onBack = back)
        Box(modifier = Modifier.weight(1f)) {
            if (isReady) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        createEventWebView(
                            context = context,
                            origin = origin,
                            accessToken = accessToken,
                            onOpen = { latestOpen(it) },
                            onClose = { latestClose() },
                            onShare = { latestShare(it) },
                            onLoading = { isLoading = it },
                            onError = { hasError = true }
                        ).also {
                            webView = it
                            it.loadUrl(pageUrl)
                        }
                    },
                    onRelease = { view ->
                        view.stopLoading()
                        view.removeJavascriptInterface(RunkkAppBridge.NAME)
                        view.destroy()
                        webView = null
                    }
                )
            }
            when {
                hasError -> RunkkErrorState(
                    message = RunkkErrorText.of(AppError.Network).asString(),
                    modifier = Modifier.fillMaxSize().background(RunkkColors.Background),
                    onRetry = {
                        hasError = false
                        webView?.reload()
                    }
                )

                !isReady || isLoading -> RunkkLoading()
            }
        }
    }
}

/** 웹뷰를 만든다: 설정, 이동·자원 origin 막기, 브리지. */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
private fun createEventWebView(
    context: Context,
    origin: String,
    accessToken: () -> String?,
    onOpen: (EventPageTarget) -> Unit,
    onClose: () -> Unit,
    onShare: (String) -> Unit,
    onLoading: (Boolean) -> Unit,
    onError: () -> Unit
): WebView {
    val main = Handler(Looper.getMainLooper())
    val view = WebView(context)
    // 주 문서 주소. JavaBridge 스레드가 읽으므로 웹뷰 getUrl()(주 스레드 전용) 대신 페이지 시작·기록 갱신 때 남긴다.
    val current = AtomicReference<String?>(null)
    view.settings.apply {
        javaScriptEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        setGeolocationEnabled(false)
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
    }
    view.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val target = request.url.toString()
            if (isSameOrigin(target, origin)) return false
            if (request.isForMainFrame && isExternalWebLink(target)) openExternal(context, target)
            return true
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val target = request.url.toString()
            return if (isExternalWebLink(target) && !isSameOrigin(target, origin)) blocked() else null
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            current.set(url)
            onLoading(true)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            current.set(url)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            onLoading(false)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) onError()
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse
        ) {
            if (request.isForMainFrame) onError()
        }
    }
    view.addJavascriptInterface(
        RunkkAppBridge(
            allowedOrigin = origin,
            currentUrl = current::get,
            accessToken = accessToken,
            runOnMain = { main.post(it) },
            onOpen = onOpen,
            onClose = onClose,
            onShare = onShare
        ),
        RunkkAppBridge.NAME
    )
    return view
}

/** 다른 origin 자원 요청에 돌려주는 빈 403 응답. */
private fun blocked(): WebResourceResponse =
    WebResourceResponse("text/plain", "utf-8", FORBIDDEN, "Forbidden", emptyMap(), ByteArrayInputStream(ByteArray(0)))

private fun openExternal(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "바깥 브라우저 없음")
    }
}

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    try {
        context.startActivity(Intent.createChooser(send, null))
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "공유 앱 없음")
    }
}

private val LOCATION_PERMISSIONS =
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

private const val FORBIDDEN = 403
