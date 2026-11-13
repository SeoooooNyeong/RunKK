package com.a207.runkk.app.core.avatar

import android.content.Context
import app.rive.runtime.kotlin.core.RendererType
import app.rive.runtime.kotlin.core.Rive

/** Rive 네이티브 런타임을 앱에서 한 번만 Rive 렌더러로 초기화한다(PoC MainActivity.onCreate). */
internal object RiveRuntime {
    @Volatile
    private var isInitialized = false

    fun ensureInitialized(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (!isInitialized) {
                Rive.init(context.applicationContext, RendererType.Rive)
                isInitialized = true
            }
        }
    }
}
