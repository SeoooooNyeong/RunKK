package com.a207.runkk.app.core.avatar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import kotlin.math.roundToInt

/**
 * 셀카 생성 결과를 남녀 몸체용 머리 레이어로 바꾼다(PoC GeneratedAvatarLayers). 새 인물마다 얼굴·눈·헤어에 같은 닮음 변환
 * ([HeadPlacement])을 적용해 세 층이 어긋나지 않는다. 특정 인물에만 맞는 파츠별 좌표나 오프셋은 쓰지 않는다.
 *
 * 정면: 목 중심·턱은 mobile.neckMask, 없으면 얼굴 알파([GeneratedHeadGeometry])에서 찾고 턱 경계를 밝기 대비로 다듬는다.
 * 눈 밑은 피부색으로 받쳐 깜박일 때 비지 않게 하고, 반쯤·완전히 감은 눈을 만든다. 측면: 아래쪽 목 폭이 넓어지는 줄을 턱으로 보고
 * 목을 줄여 몸체 옷깃에 잇는다. 체형 1·2단계 볼 레이어는 턱 위 띠를 가로로 넓혀 만든다.
 */
internal object GeneratedAvatarLayers {
    private const val SIZE = HeadResultPolicy.CANVAS
    private const val OPAQUE = 128
    private const val EYE_ALPHA = 48
    private const val BACKED_ALPHA = 230
    private const val SKIN_RED_OVER_BLUE = 20
    private val filter get() = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun prepare(input: GeneratedHeadInput): GeneratedHeadLayers {
        val bitmaps = mutableListOf<Bitmap>()
        fun decode(bytes: ByteArray) = decodeLayer(bytes).also(bitmaps::add)
        try {
            val face = decode(input.face)
            val eyes = decode(input.eyes)
            val hair = decode(input.hair)
            val sideFace = decode(input.rightFace)
            val sideEyes = decode(input.rightEyes)
            val sideHair = decode(input.rightHair)
            val facePixels = pixels(face)
            val reported = input.neckMask
            val detected = if (reported == null) GeneratedHeadGeometry.detect(facePixels, SIZE, SIZE) else null
            val centerX = (detected?.centerX ?: checkNotNull(reported).centerX).coerceIn(150f, 850f)
            val estimatedChin = (detected?.chinY ?: checkNotNull(reported).chinY).coerceIn(450f, 950f)
            val chin = chinEdge(face, centerX, estimatedChin)
            val crown = alphaBounds(hair).top.toFloat()
            require(chin - crown in 300f..980f) { "head placement is out of range" }
            val skin = input.skinColor ?: GeneratedHeadGeometry.skinColor(facePixels, SIZE, SIZE, centerX, chin)
            val eyeRects = eyeBounds(eyes, centerX)
            val backed = fillEyeBacking(face, eyes, eyeRects, skin).also(bitmaps::add)
            val half = blink(eyes, eyeRects, closed = false).also(bitmaps::add)
            val closed = blink(eyes, eyeRects, closed = true).also(bitmaps::add)

            val sideBounds = alphaBounds(sideFace)
            val bottom = rowBounds(sideFace, (sideBounds.bottom - 12).coerceIn(0, SIZE - 1))
            val sideCenterX = ((bottom.first + bottom.second) / 2f).coerceIn(100f, 950f)
            val sideChin = detectSideChin(sideFace, sideCenterX, sideBounds.bottom)
            val sideCrown = alphaBounds(sideHair).top.toFloat()
            val shortenedSide = shortenNeck(
                sideFace,
                sideChin,
                sideBounds.bottom.toFloat(),
                sideCenterX
            ).also(bitmaps::add)

            val variants = AvatarGender.entries.associateWith { gender ->
                val frontTarget = HeadTarget.front(gender)
                val sideTarget = HeadTarget.side(gender)
                val front = HeadPlacement.fit(centerX, chin, crown, frontTarget).matrix()
                val side = HeadPlacement.fit(sideCenterX, sideChin, sideCrown, sideTarget).matrix()
                val normalizedFace = transform(backed, front).also(bitmaps::add)
                val sideSkin = transform(shortenedSide, side).also(bitmaps::add)
                try {
                    AvatarVariantAssets(
                        front = mapOf(
                            "face_layer" to AvatarImages.encode(normalizedFace),
                            "hair_layer" to transformed(hair, front),
                            "eyes_open" to transformed(eyes, front),
                            "eyes_half" to transformed(half, front),
                            "eyes_closed" to transformed(closed, front)
                        ),
                        sideLayers = SideHeadLayers(
                            AvatarImages.encode(sideSkin),
                            transformed(sideEyes, side),
                            transformed(sideHair, side)
                        ),
                        frontFullness = (1..2).associateWith {
                            fullness(normalizedFace, frontTarget.centerX, frontTarget.chinY, it)
                        },
                        sideFullness = (1..2).associateWith {
                            fullness(sideSkin, sideTarget.centerX, sideTarget.chinY, it)
                        }
                    )
                } finally {
                    // 성공하면 바로 놓고, 실패하면 바깥 finally가 놓는다(recycle은 두 번 불러도 된다).
                    normalizedFace.recycle()
                    sideSkin.recycle()
                }
            }
            return GeneratedHeadLayers(variants, skin)
        } finally {
            bitmaps.forEach(Bitmap::recycle)
        }
    }

    /**
     * 맨몸 바탕(assets/outfits/body)의 피부를 생성 얼굴 피부색에 맞춘다(채널별 중앙값 비율). 중앙값은 피부색 픽셀(붉은 기가 파란 기보다
     * 20 넘게 큰 불투명 픽셀)로만 재서 속옷 회색이 섞이지 않게 한다. 의상은 바탕 위에 따로 얹으므로 바뀌지 않는다.
     */
    fun matchBody(body: ByteArray, color: Int): ByteArray {
        val decoded = decodeLayer(body, requireCanvas = false)
        val skin = try {
            decoded.copy(Bitmap.Config.ARGB_8888, true) ?: throw IllegalStateException("skin layer copy failed")
        } finally {
            decoded.recycle()
        }
        try {
            val pixels = IntArray(skin.width * skin.height)
            skin.getPixels(pixels, 0, skin.width, 0, 0, skin.width, skin.height)
            val channels = Array(3) { ArrayList<Int>() }
            for (index in pixels.indices step 7) {
                val pixel = pixels[index]
                if (Color.alpha(pixel) > 220 && Color.red(pixel) - Color.blue(pixel) > SKIN_RED_OVER_BLUE) {
                    channels[0].add(Color.red(pixel))
                    channels[1].add(Color.green(pixel))
                    channels[2].add(Color.blue(pixel))
                }
            }
            if (channels[0].isEmpty()) return body
            val target = intArrayOf(Color.red(color), Color.green(color), Color.blue(color))
            val ratios = channels.mapIndexed { index, values ->
                values.sort()
                target[index].toFloat() / values[values.size / 2].coerceAtLeast(1)
            }
            for (index in pixels.indices) {
                val pixel = pixels[index]
                if (Color.alpha(pixel) > 0) {
                    pixels[index] = Color.argb(
                        Color.alpha(pixel),
                        (Color.red(pixel) * ratios[0]).roundToInt().coerceIn(0, 255),
                        (Color.green(pixel) * ratios[1]).roundToInt().coerceIn(0, 255),
                        (Color.blue(pixel) * ratios[2]).roundToInt().coerceIn(0, 255)
                    )
                }
            }
            skin.setPixels(pixels, 0, skin.width, 0, 0, skin.width, skin.height)
            return AvatarImages.encode(skin)
        } finally {
            skin.recycle()
        }
    }

    private fun HeadPlacement.matrix() = Matrix().apply {
        setScale(scale, scale)
        postTranslate(dx, dy)
    }

    private fun decodeLayer(bytes: ByteArray, requireCanvas: Boolean = true): Bitmap {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalArgumentException("unreadable avatar layer")
        if (requireCanvas && (bitmap.width != SIZE || bitmap.height != SIZE)) {
            bitmap.recycle()
            throw IllegalArgumentException("avatar layer must be ${SIZE}x$SIZE")
        }
        return bitmap
    }

    private fun pixels(bitmap: Bitmap) = IntArray(SIZE * SIZE).also { bitmap.getPixels(it, 0, SIZE, 0, 0, SIZE, SIZE) }

    private fun alphaBounds(bitmap: Bitmap): Rect {
        val pixels = pixels(bitmap)
        var left = SIZE
        var top = SIZE
        var right = 0
        var bottom = 0
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                if (Color.alpha(pixels[y * SIZE + x]) >= OPAQUE) {
                    left = minOf(left, x)
                    right = maxOf(right, x + 1)
                    top = minOf(top, y)
                    bottom = maxOf(bottom, y + 1)
                }
            }
        }
        require(left < right && top < bottom) { "empty avatar layer" }
        return Rect(left, top, right, bottom)
    }

    private fun rowBounds(bitmap: Bitmap, y: Int): Pair<Int, Int> {
        val row = IntArray(SIZE).also { bitmap.getPixels(it, 0, SIZE, 0, y, SIZE, 1) }
        var left = SIZE
        var right = 0
        for (x in 0 until SIZE) {
            if (Color.alpha(row[x]) >= OPAQUE) {
                left = minOf(left, x)
                right = maxOf(right, x)
            }
        }
        require(left < right) { "neck row not found" }
        return left to right
    }

    /** 추정 턱 둘레에서 위·아래보다 어두운 줄(턱 선)을 찾는다. 대비가 약하면 추정값 그대로다. */
    private fun chinEdge(face: Bitmap, center: Float, estimate: Float): Float {
        fun light(x: Int, y: Int): Float {
            val pixel = face.getPixel(x, y)
            return Color.red(pixel) * .2126f + Color.green(pixel) * .7152f + Color.blue(pixel) * .0722f
        }
        var best = estimate
        var score = 8f
        val cx = center.roundToInt()
        for (y in (estimate.toInt() - 14).coerceAtLeast(6)..(estimate.toInt() + 30).coerceAtMost(SIZE - 7)) {
            var contrast = 0f
            for (dx in -15..15 step 3) {
                val x = (cx + dx).coerceIn(0, SIZE - 1)
                contrast += (light(x, y - 5) + light(x, y + 5)) / 2 - light(x, y)
            }
            contrast /= 11
            if (contrast > score) {
                score = contrast
                best = y.toFloat()
            }
        }
        return best
    }

    private fun detectSideChin(face: Bitmap, center: Float, base: Int): Float {
        val reference = rowBounds(face, (base - 18).coerceIn(0, SIZE - 1))
        val width = reference.second - reference.first
        var estimate = (base - 75).toFloat()
        for (y in (base - 20).coerceAtMost(SIZE - 1) downTo (base - 180).coerceAtLeast(300)) {
            val row = runCatching { rowBounds(face, y) }.getOrNull() ?: continue
            if (row.second - row.first > width * 1.35) {
                estimate = y.toFloat()
                break
            }
        }
        return chinEdge(face, center, estimate).coerceIn((base - 170).toFloat(), (base - 25).toFloat())
    }

    private fun transform(source: Bitmap, matrix: Matrix): Bitmap =
        Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888).also { Canvas(it).drawBitmap(source, matrix, filter) }

    private fun transformed(source: Bitmap, matrix: Matrix): ByteArray {
        val bitmap = transform(source, matrix)
        return try {
            AvatarImages.encode(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    /** 목 중심 왼쪽·오른쪽 눈의 상자(y 200..850). 둘 중 하나라도 없으면 실패한다. */
    private fun eyeBounds(eyes: Bitmap, center: Float): List<Rect> {
        val pixels = pixels(eyes)
        return listOf(0 until center.toInt(), center.toInt() until SIZE).map { range ->
            var left = SIZE
            var top = SIZE
            var right = 0
            var bottom = 0
            for (y in 200 until 850) {
                for (x in range) {
                    if (Color.alpha(pixels[y * SIZE + x]) > EYE_ALPHA) {
                        left = minOf(left, x)
                        right = maxOf(right, x + 1)
                        top = minOf(top, y)
                        bottom = maxOf(bottom, y + 1)
                    }
                }
            }
            require(left < right && top < bottom) { "eye layer not found" }
            Rect(left, top, right, bottom)
        }
    }

    /** 눈 상자 안에서 얼굴이 덜 불투명하고 눈이 있는 픽셀을 피부색으로 채운다(깜박일 때 뒤가 비지 않게). */
    private fun fillEyeBacking(face: Bitmap, eyes: Bitmap, bounds: List<Rect>, skin: Int): Bitmap {
        val result = face.copy(Bitmap.Config.ARGB_8888, true)
        for (box in bounds) {
            for (y in box.top.coerceAtLeast(0) until box.bottom.coerceAtMost(SIZE)) {
                for (x in box.left.coerceAtLeast(0) until box.right.coerceAtMost(SIZE)) {
                    if (Color.alpha(result.getPixel(x, y)) < BACKED_ALPHA && Color.alpha(eyes.getPixel(x, y)) > 0) {
                        result.setPixel(x, y, skin)
                    }
                }
            }
        }
        return result
    }

    /** 반쯤 감은 눈(아래 절반 + 속눈썹 선) 또는 감은 눈(선만). */
    private fun blink(eyes: Bitmap, bounds: List<Rect>, closed: Boolean): Bitmap {
        val result = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val lash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(57, 40, 32)
            style = Paint.Style.STROKE
            strokeWidth = 4.5f
            strokeCap = Paint.Cap.ROUND
        }
        for (box in bounds) {
            val y = box.exactCenterY()
            val path = Path().apply {
                moveTo(box.left.toFloat(), y - 3)
                quadTo(box.exactCenterX(), y + 14, box.right.toFloat(), y - 3)
            }
            if (!closed) {
                val clip = Path(path).apply {
                    lineTo(box.right.toFloat(), box.bottom.toFloat())
                    lineTo(box.left.toFloat(), box.bottom.toFloat())
                    close()
                }
                val save = canvas.save()
                canvas.clipPath(clip)
                canvas.drawBitmap(eyes, 0f, 0f, null)
                canvas.restoreToCount(save)
            }
            canvas.drawPath(path, lash)
        }
        return result
    }

    /** 턱 아래 목을 0.45배 줄이고 아래로 갈수록 좁혀 몸체 옷깃 안으로 잇는다(측면). */
    private fun shortenNeck(face: Bitmap, chin: Float, base: Float, centerX: Float): Bitmap {
        val span = (base - chin).coerceAtLeast(1f)
        val vertices = FloatArray((SIZE + 1) * 4)
        for (row in 0..SIZE) {
            val y = row.toFloat()
            val t = ((y - chin) / span).coerceIn(0f, 1f)
            val smooth = t * t * (3 - 2 * t)
            val mappedY = y - (y - chin).coerceIn(0f, span) * .45f
            vertices[row * 4] = centerX * .45f * smooth
            vertices[row * 4 + 1] = mappedY
            vertices[row * 4 + 2] = SIZE - (SIZE - centerX) * .45f * smooth
            vertices[row * 4 + 3] = mappedY
        }
        return Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawBitmapMesh(face, 1, SIZE, vertices, 0, null, 0, filter)
        }
    }

    /** 체형 [stage]의 볼 레이어: 턱 위 145px 띠를 턱 중심 기준으로 가로 3.5%씩 넓힌다. */
    private fun fullness(face: Bitmap, centerX: Float, chin: Float, stage: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.clipRect(0f, chin - 145f, SIZE.toFloat(), chin)
            canvas.scale(1 + stage * .035f, 1f, centerX, chin)
            canvas.drawBitmap(face, 0f, 0f, filter)
            return AvatarImages.encode(bitmap)
        } finally {
            bitmap.recycle()
        }
    }
}
