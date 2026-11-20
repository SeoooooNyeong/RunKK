package com.a207.runkk.app.core.avatar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/**
 * 목·깃 맞춤(몸 쪽 목 층). Rive 그림은 Robolectric에 나오지 않으므로 riv와 같은 순서(머리 → 그 위에 몸 텍스처)로 1024 머리 캔버스에
 * 직접 합성한 캡처를 본다. 몸 텍스처는 앱 합성([OutfitLayerComposer], 상의 자르기 포함)으로 만들고 riv 셋업 자세의 몸 메시 → 머리 캔버스
 * 변환([BodyNeck.atlasToHead], 측면은 선형 근사)으로 옮긴다.
 *
 * 남녀 × 정면·측면 × 반팔·후디·바람막이, 기본 머리와 셀카 머리(목이 짧고 가는 도형) 각각:
 * - 깃 둘레(정면: 옷 깃 구멍 안, 측면: 목 층 자리)에 피부도 옷도 아닌 투명 화소가 없다.
 * - 목 폭이 몸 목 폭과 ±5% 안이다(정면: 보이는 목 = 깃 구멍 폭, 측면: 머리 레이어의 턱 아래 목 = 목 층 폭).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NeckCollarFitTest {
    private val root = File("src/main/assets")
    private val tops = linkedMapOf("반팔" to 0, "후디" to 4, "바람막이" to 5)

    @BeforeTest
    fun install() = OutfitLibrary.install(OutfitFiles { File(root, it).takeIf(File::isFile)?.readBytes() })

    private val catalog get() = checkNotNull(OutfitLibrary.catalog)

    private fun heads(gender: AvatarGender): Map<String, AvatarVariantAssets> = linkedMapOf(
        "기본 머리" to AvatarVariantAssets.loadBuiltIn(RuntimeEnvironment.getApplication().assets, gender),
        "셀카 머리" to selfie.variants.getValue(gender)
    )

    @Test
    fun `정면 - 옷 깃 구멍 안에 투명 화소가 없고 보이는 목 폭이 깃 구멍 폭과 5퍼센트 안이다`() {
        val failures = mutableListOf<String>()
        for (gender in AvatarGender.entries) {
            val neck = BodyNeck.front(gender)
            heads(gender).forEach { (headName, variant) ->
                val head = decode(variant.front.getValue("face_layer"))
                // 바람막이처럼 깃이 높아 목이 거의 보이지 않는 옷은 잴 행이 없을 수 있다. 머리마다 세 옷 합쳐 한 행은 잰다.
                var measured = 0
                tops.forEach { (topName, top) ->
                    val body = placed(atlas(gender, OutfitSlot.STAND, top), matrix(neck))
                    val shot = composite(head, body)
                    val label = "$gender 정면 $headName $topName"
                    val cx = neck.target.centerX.roundToInt()
                    val from = (neck.target.chinY + 4).roundToInt()
                    val to = (neck.outline.filterIndexed { i, _ -> i % 2 == 1 }.max()).roundToInt()
                    var gaps = 0
                    var widthRows = 0
                    for (y in from..to) {
                        val opening = run(body, cx, y) { it < OPAQUE } ?: continue
                        if (opening.first == 0 || opening.second == SIZE - 1) continue
                        gaps += (opening.first..opening.second).count { Color.alpha(shot.getPixel(it, y)) < OPAQUE }
                        val width = opening.second - opening.first + 1
                        if (width < MIN_MEASURED) continue
                        // 보이는 목: 깃 구멍 안에서 머리 레이어(얼굴·목 층)가 채운 화소
                        val visible = (opening.first..opening.second).count { x ->
                            Color.alpha(shot.getPixel(x, y)) >= OPAQUE
                        }
                        widthRows++
                        val diff = abs(visible - width) / width.toFloat()
                        if (diff > TOLERANCE) failures += "$label y$y 목 폭 $visible / 깃 구멍 $width"
                    }
                    if (gaps > 0) failures += "$label 깃 둘레 투명 화소 $gaps"
                    measured += widthRows
                    listOf(body, shot).forEach(Bitmap::recycle)
                }
                if (measured == 0) failures += "$gender 정면 $headName 목 폭을 잴 깃 구멍 행이 없다"
                head.recycle()
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `측면 - 목 층 자리에 투명 화소가 없고 턱 아래 목 폭이 몸 목 폭과 5퍼센트 안이다`() {
        val failures = mutableListOf<String>()
        for (gender in AvatarGender.entries) {
            val neck = BodyNeck.side(gender)
            heads(gender).forEach { (headName, variant) ->
                val blank = png { }
                val layers = variant.sideLayers
                val head = decode(
                    AvatarImages.composeSideHead(
                        SideHeadLayers(layers.face, blank, blank, layers.neckRegistration, layers.neckUnderlay),
                        MIN_RUNNING_AMOUNT
                    )
                )
                tops.forEach { (topName, top) ->
                    val body = placed(atlas(gender, OutfitSlot.RUN_BODY, top), matrix(neck))
                    val shot = composite(head, body)
                    val label = "$gender 측면 $headName $topName"
                    val from = (neck.target.chinY + 4).roundToInt()
                    val to = neck.outline.filterIndexed { i, _ -> i % 2 == 1 }.max().roundToInt() - 1
                    var gaps = 0
                    for (y in from..to) {
                        val (left, right) = neck.spanAt(y + .5f) ?: continue
                        gaps += (left.roundToInt() + 1 until right.roundToInt()).count {
                            Color.alpha(shot.getPixel(it, y)) < OPAQUE
                        }
                    }
                    if (gaps > 0) failures += "$label 깃 둘레 투명 화소 $gaps"
                    listOf(body, shot).forEach(Bitmap::recycle)
                }
                for (y in (neck.target.chinY + 8).roundToInt() until neck.target.chinY.roundToInt() + 24) {
                    val (left, right) = neck.spanAt(y + .5f) ?: continue
                    val width = right - left
                    val seen = run(head, ((left + right) / 2).roundToInt(), y) { it >= OPAQUE }
                    val visible = seen?.let { it.second - it.first + 1 } ?: 0
                    if (abs(visible - width) / width > TOLERANCE) {
                        failures += "$gender 측면 $headName y$y 목 폭 $visible / 몸 목 폭 ${width.roundToInt()}"
                    }
                }
                head.recycle()
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /** 슬롯 텍스처(러닝량 0, 기본 하의·신발): 앱 합성과 같다. */
    private fun atlas(gender: AvatarGender, slot: OutfitSlot, top: Int): Bitmap {
        val outfit = AvatarOutfit(top = top)
        val layers = OutfitLayerComposer.layers(catalog, slot, gender, AvatarBodyShape.BASE, outfit) {
            OutfitLibrary.exists(it)
        }
        val bytes = OutfitLayerComposer.compose(
            catalog.bodyFile(gender, slot)?.let(OutfitLibrary::read),
            layers.map { it.part to checkNotNull(OutfitLibrary.read(it.path)) },
            MIN_RUNNING_AMOUNT,
            slot.width,
            slot.height,
            cut = catalog.cutFile(gender, slot)?.let(OutfitLibrary::read)
        )
        return decode(bytes)
    }

    /** 몸 텍스처 → 머리 캔버스(riv 셋업 자세의 몸 메시에 맞춘 값, [BodyNeck.atlasToHead]). */
    private fun matrix(neck: BodyNeck) = Matrix().apply { setValues(neck.atlasToHead + floatArrayOf(0f, 0f, 1f)) }

    private fun placed(atlas: Bitmap, matrix: Matrix): Bitmap =
        Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawBitmap(atlas, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
            atlas.recycle()
        }

    /** riv 순서: 머리 위에 몸. */
    private fun composite(head: Bitmap, body: Bitmap): Bitmap =
        Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888).also {
            val canvas = Canvas(it)
            canvas.drawBitmap(head, 0f, 0f, null)
            canvas.drawBitmap(body, 0f, 0f, null)
        }

    /** [y] 행에서 [x]를 품고 [accept](알파)를 만족하는 이어진 구간. [x]부터 아니면 null. */
    private fun run(bitmap: Bitmap, x: Int, y: Int, accept: (Int) -> Boolean): Pair<Int, Int>? {
        if (!accept(Color.alpha(bitmap.getPixel(x, y)))) return null
        var left = x
        while (left > 0 && accept(Color.alpha(bitmap.getPixel(left - 1, y)))) left--
        var right = x
        while (right < SIZE - 1 && accept(Color.alpha(bitmap.getPixel(right + 1, y)))) right++
        return left to right
    }

    private companion object {
        const val SIZE = 1024
        const val OPAQUE = 128
        const val TOLERANCE = .05f

        /** 이보다 좁은 깃 구멍 행(구멍 아래 끝)은 폭 비율을 재지 않는다. */
        const val MIN_MEASURED = 24

        val SKIN = Color.rgb(236, 196, 170)
        val EYE = Color.rgb(40, 30, 30)
        val HAIR = Color.rgb(60, 40, 30)

        /** 셀카 머리 도형: 목이 짧고 가늘다(정면 목 x 492..532, 턱 750 아래 12px, 측면 목 x 630..660, 턱 760 아래 30px). */
        val selfie: GeneratedHeadLayers by lazy {
            val images = mapOf(
                "faceImage" to png {
                    fill(it, 312..712, 300 until 750, SKIN)
                    fill(it, 492..532, 750 until 762, SKIN)
                },
                "eyesImage" to png {
                    fill(it, 400..440, 500..520, EYE)
                    fill(it, 584..624, 500..520, EYE)
                },
                "hairImage" to png { fill(it, 300..724, 150..330, HAIR) },
                "rightFaceImage" to
                    png {
                        fill(it, 450..850, 300 until 760, SKIN)
                        fill(it, 630..660, 760 until 790, SKIN)
                    },
                "rightEyesImage" to png { fill(it, 760..790, 500..520, EYE) },
                "rightHairImage" to png { fill(it, 440..860, 160..330, HAIR) }
            )
            val fields = images.map { (name, bytes) ->
                "\"$name\":\"data:image/png;base64,${Base64.getEncoder().encodeToString(bytes)}\""
            }
            GeneratedHeadLayers.fromHeadJson(
                (fields + "\"mobile\":{\"neckMask\":{\"centerX\":512,\"chinY\":750}}").joinToString(",", "{", "}")
            )
        }

        fun png(draw: (Bitmap) -> Unit): ByteArray {
            val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            return try {
                draw(bitmap)
                ByteArrayOutputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                    it.toByteArray()
                }
            } finally {
                bitmap.recycle()
            }
        }

        fun fill(bitmap: Bitmap, xs: IntRange, ys: IntRange, color: Int) {
            val width = xs.last - xs.first + 1
            val row = IntArray(width) { color }
            for (y in ys) bitmap.setPixels(row, 0, width, xs.first, y, width, 1)
        }

        fun decode(bytes: ByteArray): Bitmap = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    }
}
