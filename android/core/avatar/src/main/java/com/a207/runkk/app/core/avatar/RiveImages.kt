package com.a207.runkk.app.core.avatar

import android.graphics.Bitmap
import app.rive.runtime.kotlin.core.ImageAsset
import app.rive.runtime.kotlin.core.RendererType
import app.rive.runtime.kotlin.core.RiveRenderImage
import java.io.ByteArrayOutputStream

/** Rive 이미지 자리에 PNG를 넣는다. 만든 렌더 이미지는 자리에 넣은 뒤 바로 놓는다(자리가 참조를 갖는다). */
internal fun ImageAsset.assignPng(bytes: ByteArray) {
    val image = RiveRenderImage.fromEncoded(bytes, RendererType.Rive)
    try {
        this.image = image
    } finally {
        image.release()
    }
}

/** 이미지 자리를 비울 때 쓰는 1×1 투명 PNG. */
internal val TRANSPARENT_PNG: ByteArray by lazy {
    val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    try {
        ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "PNG encoding failed" }
            output.toByteArray()
        }
    } finally {
        bitmap.recycle()
    }
}
