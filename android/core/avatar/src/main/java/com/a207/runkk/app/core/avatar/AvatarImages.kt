package com.a207.runkk.app.core.avatar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import java.io.ByteArrayOutputStream

/**
 * Rive 이미지 자리에 넣을 머리 텍스처(PoC AvatarImages 가운데 렌더러가 쓰는 부분). 원본 PNG는 바꾸지 않고, 톤은 매번 원본에서
 * 다시 계산해 누적되지 않는다. 피부 얼굴에만 톤을 쓰고 눈·헤어는 원래 색으로 그 위에 얹는다.
 */
internal object AvatarImages {
    private const val SIZE = 1024

    /** 피부 얼굴 레이어에 러닝량 톤을 적용한다. 러닝량 0이면 원본 바이트를 그대로 돌려준다. */
    fun faceForRunningAmount(bytes: ByteArray, amount: Int): ByteArray {
        val matrix = SkinToneFilter.matrix(amount)
        if (amount == MIN_RUNNING_AMOUNT) return bytes
        val source = decode(bytes)
        try {
            val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            try {
                Canvas(result).drawBitmap(
                    source,
                    0f,
                    0f,
                    Paint().apply {
                        colorFilter = ColorMatrixColorFilter(matrix)
                    }
                )
                return encode(result)
            } finally {
                result.recycle()
            }
        } finally {
            source.recycle()
        }
    }

    /**
     * 헤어 레이어: 고유색은 그대로 두고 얼굴과 맞닿은 경계의 피부 섞인 색만 섞인 몫만큼 러닝량 톤을 준다([HairSkinFringe], DV-5).
     * 러닝량 0이거나 얼굴과 크기가 다르면 원본 바이트를 그대로 돌려준다.
     */
    fun hairForRunningAmount(hair: ByteArray, face: ByteArray, amount: Int): ByteArray {
        val matrix = SkinToneFilter.matrix(amount)
        if (amount == MIN_RUNNING_AMOUNT) return hair
        val hairBitmap = decode(hair)
        try {
            val faceBitmap = decode(face)
            try {
                if (hairBitmap.width != faceBitmap.width || hairBitmap.height != faceBitmap.height) return hair
                val toned = tonedHair(hairBitmap, faceBitmap, matrix)
                try {
                    return encode(toned)
                } finally {
                    toned.recycle()
                }
            } finally {
                faceBitmap.recycle()
            }
        } finally {
            hairBitmap.recycle()
        }
    }

    /** [hair] 경계를 [face]에 맞춰 톤을 준 새 비트맵. 부른 쪽이 recycle한다. */
    private fun tonedHair(hair: Bitmap, face: Bitmap, matrix: FloatArray): Bitmap {
        val width = hair.width
        val height = hair.height
        val hairPixels = IntArray(width * height).also { hair.getPixels(it, 0, width, 0, 0, width, height) }
        val facePixels = IntArray(width * height).also { face.getPixels(it, 0, width, 0, 0, width, height) }
        val toned = HairSkinFringe.tone(hairPixels, facePixels, width, height, matrix)
        return Bitmap.createBitmap(toned, width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * 측면 머리: 몸 쪽 목 층(있으면, 얼굴과 같은 톤) 위에 톤을 적용한 얼굴(내장 샘플은 목 등록 메시, 턱 아래는 목 층 폭으로 자름), 눈,
     * 헤어 순서로 얹는다. 헤어는 피부 섞인 경계만 톤을 받는다.
     */
    fun composeSideHead(layers: SideHeadLayers, amount: Int): ByteArray {
        val face = decode(faceForRunningAmount(layers.face, amount))
        try {
            val result = Bitmap.createBitmap(face.width, face.height, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(result)
                val skin = Bitmap.createBitmap(face.width, face.height, Bitmap.Config.ARGB_8888)
                try {
                    val skinCanvas = Canvas(skin)
                    val neck = layers.neckRegistration
                    if (neck == null) {
                        skinCanvas.drawBitmap(face, 0f, 0f, null)
                    } else {
                        drawRegisteredFace(skinCanvas, face, neck)
                    }
                    layers.neckUnderlay?.let { underlay ->
                        underlay.trim(skin)
                        underlay.draw(canvas, amount)
                    }
                    canvas.drawBitmap(skin, 0f, 0f, null)
                } finally {
                    skin.recycle()
                }
                drawOverlay(canvas, layers.eyes, face)
                drawOverlay(
                    canvas,
                    hairForRunningAmount(layers.hair, layers.face, amount),
                    face
                )
                return encode(result)
            } finally {
                result.recycle()
            }
        } finally {
            face.recycle()
        }
    }

    private fun drawRegisteredFace(canvas: Canvas, face: Bitmap, neck: SideNeckRegistration) {
        require(face.width == SIZE && face.height == SIZE) { "side face must be ${SIZE}x$SIZE" }
        val pixels = IntArray(SIZE * SIZE)
        face.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        val clean = Bitmap.createBitmap(neck.cleanNeckPixels(pixels), SIZE, SIZE, Bitmap.Config.ARGB_8888)
        try {
            canvas.drawBitmapMesh(
                clean,
                1,
                SIZE,
                neck.meshVertices(),
                0,
                null,
                0,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
        } finally {
            clean.recycle()
        }
    }

    private fun drawOverlay(canvas: Canvas, bytes: ByteArray, face: Bitmap) {
        val overlay = decode(bytes)
        try {
            require(overlay.width == face.width && overlay.height == face.height) { "side layers differ in size" }
            canvas.drawBitmap(overlay, 0f, 0f, null)
        } finally {
            overlay.recycle()
        }
    }

    private fun decode(bytes: ByteArray): Bitmap =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw IllegalArgumentException("unreadable PNG layer")

    fun encode(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use {
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "PNG encoding failed" }
        it.toByteArray()
    }
}
