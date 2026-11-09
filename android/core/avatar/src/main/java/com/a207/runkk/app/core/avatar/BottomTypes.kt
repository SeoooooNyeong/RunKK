package com.a207.runkk.app.core.avatar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader

/** Garment silhouettes in the approved front-body and running-leg texture coordinates. */
internal object BottomTypes {
    fun apply(assetName: String, skin: Bitmap, clothing: Bitmap, type: Int) {
        require(type in 0..6)
        if (type == 0) return
        if ((type == 1 || type == 6) && assetName == "standing_body") restoreThighs(skin, false)
        if ((type == 1 || type == 6) && assetName == "side_leg_skin") restoreThighs(skin, true)
        if (assetName == "standing_body") {
            // Remove only the old blue-grey shorts fringe, never the warm hand outline.
            for (y in 310 until 425) {
                for (x in 95 until 418) {
                    val pixel = clothing.getPixel(x, y)
                    if (Color.alpha(pixel) > 0 && Color.blue(pixel) > Color.red(pixel) &&
                        Color.blue(pixel) >= Color.green(pixel)
                    ) {
                        clothing.setPixel(x, y, Color.TRANSPARENT)
                    }
                }
            }
        }
        when (assetName) {
            "standing_body" -> front(Canvas(skin), Canvas(clothing), type)
            "side_leg_skin" -> side(Canvas(skin), Canvas(clothing), type)
        }
    }

    private val clear = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private val ink = Color.rgb(71, 57, 54)
    private val colors = intArrayOf(
        0,
        Color.rgb(55, 76, 112),
        Color.rgb(65, 57, 83),
        Color.rgb(122, 143, 128),
        Color.rgb(192, 173, 139),
        Color.rgb(116, 123, 83),
        Color.rgb(76, 76, 99)
    )

    private fun path(block: Path.() -> Unit) = Path().apply(block)
    private fun garment(canvas: Canvas, shape: Path, color: Int, width: Float) {
        val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                width,
                0f,
                intArrayOf(tone(color, .78f), tone(color, 1.09f), color, tone(color, .82f)),
                floatArrayOf(0f, .37f, .68f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(shape, shade)
        canvas.drawPath(
            shape,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                strokeJoin = Paint.Join.ROUND
                this.color = ink
            }
        )
    }

    private fun tone(color: Int, factor: Float) = Color.rgb(
        (Color.red(color) * factor).toInt().coerceIn(0, 255),
        (Color.green(color) * factor).toInt().coerceIn(0, 255),
        (Color.blue(color) * factor).toInt().coerceIn(0, 255)
    )

    private fun line(canvas: Canvas, shape: Path, color: Int = ink, width: Float = 2f) {
        canvas.drawPath(
            shape,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = width
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
        )
    }

    internal fun restoreThighs(bitmap: Bitmap, side: Boolean) {
        // Extend the actual underlay's column colours, then cross-fade into its
        // original painted thigh. This remains consistent under every skin filter.
        val start = if (side) 175 else 365
        val blendStart = if (side) 282 else 425
        val end = if (side) 330 else 469
        val sampleY = if (side) 310 else 445
        val ranges = if (side) listOf(18..231) else listOf(122..248, 267..389)
        val rows = ranges.map { range ->
            range.map { x ->
                bitmap.getPixel(
                    x.coerceIn(
                        if (side) {
                            34
                        } else if (x < 256) {
                            138
                        } else {
                            279
                        },
                        if (side) {
                            212
                        } else if (x < 256) {
                            235
                        } else {
                            378
                        }
                    ),
                    sampleY
                )
            }
        }
        ranges.forEachIndexed { index, range ->
            for (y in start until end) {
                for (x in range) {
                    val original = bitmap.getPixel(x, y)
                    val sample = rows[index][x - range.first]
                    val fade = ((end - y).toFloat() / (end - blendStart)).coerceIn(0f, 1f)
                    if (Color.alpha(original) == 0 && y > blendStart) continue
                    val weight = if (Color.alpha(original) == 0) 1f else fade
                    fun blend(channel: (Int) -> Int) =
                        (channel(sample) * weight + channel(original) * (1f - weight)).toInt()
                    bitmap.setPixel(x, y, Color.argb(255, blend(Color::red), blend(Color::green), blend(Color::blue)))
                }
            }
        }
    }

    private fun front(skin: Canvas, clothes: Canvas, type: Int) {
        // Keep the curved shirt hem above this exact boundary, including base top 0.
        val oldGarment = path {
            moveTo(112f, 291f)
            quadTo(255f, 330f, 401f, 293f)
            lineTo(420f, 426f)
            lineTo(94f, 426f)
            close()
        }
        clothes.drawPath(oldGarment, clear)
        if (type in 2..4) clothes.drawRect(108f, 418f, 404f, 633f, clear)
        // The old shorts conceal the upper thighs. Restore only the newly exposed
        // skin under shorter hems; the existing painted knee and calf stay intact.
        val shape = when (type) {
            1 -> path {
                moveTo(119f, 294f)
                quadTo(255f, 328f, 395f, 294f)
                lineTo(402f, 368f)
                lineTo(388f, 354f)
                lineTo(391f, 388f)
                quadTo(328f, 402f, 267f, 389f)
                lineTo(258f, 357f)
                lineTo(248f, 389f)
                quadTo(183f, 402f, 120f, 387f)
                lineTo(121f, 355f)
                lineTo(106f, 368f)
                close()
            }

            2 -> path {
                moveTo(119f, 294f)
                quadTo(255f, 328f, 395f, 294f)
                quadTo(405f, 363f, 391f, 415f)
                cubicTo(376f, 475f, 386f, 548f, 379f, 631f)
                quadTo(330f, 643f, 281f, 631f)
                cubicTo(270f, 546f, 275f, 470f, 260f, 404f)
                cubicTo(241f, 472f, 241f, 548f, 232f, 631f)
                quadTo(185f, 642f, 137f, 631f)
                cubicTo(121f, 546f, 135f, 477f, 120f, 418f)
                quadTo(106f, 359f, 119f, 294f)
                close()
            }

            3 -> path {
                moveTo(119f, 294f)
                quadTo(255f, 328f, 395f, 294f)
                cubicTo(416f, 382f, 407f, 454f, 400f, 505f)
                quadTo(406f, 573f, 379f, 618f)
                lineTo(282f, 618f)
                quadTo(270f, 555f, 274f, 502f)
                lineTo(258f, 414f)
                lineTo(242f, 502f)
                quadTo(248f, 564f, 232f, 618f)
                lineTo(138f, 618f)
                quadTo(112f, 560f, 116f, 510f)
                cubicTo(104f, 450f, 99f, 381f, 119f, 294f)
                close()
            }

            4 -> path {
                moveTo(119f, 294f)
                quadTo(255f, 328f, 395f, 294f)
                quadTo(410f, 356f, 408f, 421f)
                lineTo(407f, 649f)
                quadTo(339f, 658f, 268f, 649f)
                lineTo(260f, 417f)
                lineTo(247f, 649f)
                quadTo(177f, 658f, 108f, 649f)
                lineTo(108f, 421f)
                quadTo(104f, 357f, 119f, 294f)
                close()
            }

            6 -> path {
                moveTo(119f, 294f)
                quadTo(255f, 328f, 395f, 294f)
                lineTo(410f, 401f)
                quadTo(337f, 423f, 268f, 407f)
                lineTo(258f, 374f)
                lineTo(248f, 407f)
                quadTo(177f, 423f, 105f, 401f)
                close()
            }

            else -> path {
                moveTo(119f, 294f)
                quadTo(255f, 328f, 395f, 294f)
                quadTo(411f, 385f, 404f, 484f)
                quadTo(334f, 494f, 271f, 482f)
                lineTo(257f, 408f)
                lineTo(243f, 482f)
                quadTo(174f, 494f, 107f, 483f)
                quadTo(102f, 382f, 119f, 294f)
                close()
            }
        }
        garment(clothes, shape, colors[type], 512f)
        line(
            clothes,
            path {
                moveTo(256f, 313f)
                quadTo(257f, 348f, 258f, 379f)
            },
            tone(colors[type], .64f)
        )
        if (type == 1) {
            val trim = Color.rgb(235, 235, 226)
            line(
                clothes,
                path {
                    moveTo(116f, 372f)
                    lineTo(122f, 384f)
                    quadTo(184f, 397f, 245f, 386f)
                },
                trim,
                4f
            )
            line(
                clothes,
                path {
                    moveTo(270f, 386f)
                    quadTo(329f, 397f, 389f, 385f)
                    lineTo(396f, 373f)
                },
                trim,
                4f
            )
        }
        if (type == 2) {
            line(
                clothes,
                path {
                    moveTo(136f, 321f)
                    quadTo(155f, 402f, 146f, 469f)
                    lineTo(151f, 620f)
                },
                tone(colors[type], 1.24f),
                2.5f
            )
            line(
                clothes,
                path {
                    moveTo(378f, 321f)
                    quadTo(359f, 402f, 367f, 469f)
                    lineTo(365f, 620f)
                },
                tone(colors[type], 1.24f),
                2.5f
            )
        }
        if (type == 3) {
            cuff(clothes, 137f, 611f, 233f, 641f, colors[type])
            cuff(clothes, 282f, 611f, 380f, 641f, colors[type])
            line(
                clothes,
                path {
                    moveTo(251f, 321f)
                    lineTo(238f, 349f)
                    moveTo(263f, 321f)
                    lineTo(274f, 349f)
                },
                Color.rgb(226, 223, 207),
                3f
            )
            line(
                clothes,
                path {
                    moveTo(121f, 504f)
                    quadTo(150f, 519f, 163f, 516f)
                    moveTo(397f, 503f)
                    quadTo(374f, 520f, 357f, 515f)
                },
                tone(colors[type], .79f)
            )
        }
        if (type == 4) {
            line(
                clothes,
                path {
                    moveTo(177f, 334f)
                    lineTo(177f, 638f)
                    moveTo(336f, 334f)
                    lineTo(337f, 638f)
                },
                tone(colors[type], .85f)
            )
            line(
                clothes,
                path {
                    moveTo(112f, 637f)
                    quadTo(179f, 647f, 244f, 637f)
                    moveTo(271f, 637f)
                    quadTo(339f, 647f, 403f, 637f)
                },
                tone(colors[type], .7f)
            )
        }
        if (type == 5) {
            pocket(clothes, 111f, 379f, 68f, 79f, colors[type])
            pocket(clothes, 337f, 379f, 65f, 79f, colors[type])
            line(
                clothes,
                path {
                    moveTo(111f, 472f)
                    quadTo(177f, 483f, 239f, 472f)
                    moveTo(276f, 472f)
                    quadTo(338f, 483f, 400f, 472f)
                },
                tone(colors[type], .72f)
            )
        }
        if (type == 6) {
            line(
                clothes,
                path {
                    moveTo(111f, 391f)
                    quadTo(179f, 412f, 245f, 398f)
                    moveTo(271f, 398f)
                    quadTo(338f, 412f, 403f, 391f)
                },
                Color.rgb(250, 233, 221),
                7f
            )
            line(
                clothes,
                path {
                    moveTo(111f, 397f)
                    quadTo(179f, 418f, 245f, 404f)
                    moveTo(271f, 404f)
                    quadTo(338f, 418f, 403f, 397f)
                },
                Color.rgb(230, 142, 129),
                3f
            )
        }
    }

    private fun side(skin: Canvas, clothes: Canvas, type: Int) {
        clothes.drawRect(0f, 36f, 256f, if (type in 2..4) 700f else 272f, clear)
        val shape = when (type) {
            1 -> path {
                moveTo(64f, 40f)
                quadTo(163f, 39f, 245f, 66f)
                lineTo(242f, 181f)
                lineTo(226f, 172f)
                lineTo(233f, 202f)
                quadTo(128f, 223f, 8f, 199f)
                quadTo(2f, 115f, 64f, 40f)
                close()
            }

            2 -> path {
                moveTo(64f, 40f)
                quadTo(163f, 39f, 245f, 66f)
                cubicTo(246f, 213f, 220f, 344f, 216f, 421f)
                cubicTo(212f, 463f, 182f, 499f, 179f, 539f)
                lineTo(169f, 695f)
                quadTo(105f, 712f, 39f, 695f)
                cubicTo(22f, 620f, 30f, 564f, 25f, 520f)
                quadTo(40f, 460f, 28f, 401f)
                lineTo(9f, 243f)
                quadTo(-4f, 132f, 64f, 40f)
                close()
            }

            3 -> path {
                moveTo(64f, 40f)
                quadTo(163f, 39f, 245f, 66f)
                cubicTo(253f, 225f, 235f, 357f, 226f, 429f)
                quadTo(221f, 492f, 191f, 533f)
                lineTo(178f, 675f)
                quadTo(109f, 698f, 36f, 674f)
                quadTo(19f, 589f, 19f, 532f)
                quadTo(31f, 466f, 18f, 415f)
                lineTo(3f, 239f)
                quadTo(-5f, 132f, 64f, 40f)
                close()
            }

            4 -> path {
                moveTo(64f, 40f)
                quadTo(163f, 39f, 245f, 66f)
                lineTo(241f, 266f)
                lineTo(218f, 701f)
                quadTo(118f, 718f, 22f, 700f)
                lineTo(13f, 268f)
                quadTo(-7f, 129f, 64f, 40f)
                close()
            }

            6 -> path {
                moveTo(64f, 40f)
                quadTo(163f, 39f, 245f, 66f)
                lineTo(243f, 256f)
                quadTo(129f, 280f, 10f, 256f)
                lineTo(4f, 230f)
                quadTo(-4f, 130f, 64f, 40f)
                close()
            }

            else -> path {
                moveTo(64f, 40f)
                quadTo(163f, 39f, 245f, 66f)
                lineTo(239f, 352f)
                quadTo(127f, 375f, 12f, 350f)
                lineTo(4f, 233f)
                quadTo(-4f, 130f, 64f, 40f)
                close()
            }
        }
        garment(clothes, shape, colors[type], 256f)
        line(
            clothes,
            path {
                moveTo(82f, 48f)
                quadTo(60f, 118f, 42f, 188f)
            },
            tone(colors[type], .7f)
        )
        if (type ==
            1
        ) {
            line(
                clothes,
                path {
                    moveTo(11f, 194f)
                    quadTo(120f, 217f, 230f, 198f)
                    lineTo(235f, 187f)
                },
                Color.rgb(235, 235, 226),
                4f
            )
        }
        if (type ==
            2
        ) {
            line(
                clothes,
                path {
                    moveTo(225f, 81f)
                    quadTo(221f, 333f, 198f, 436f)
                    quadTo(163f, 521f, 155f, 684f)
                },
                tone(colors[type], 1.25f),
                3f
            )
        }
        if (type == 3) {
            cuff(clothes, 37f, 669f, 177f, 703f, colors[type])
            line(
                clothes,
                path {
                    moveTo(23f, 438f)
                    quadTo(79f, 459f, 111f, 445f)
                    moveTo(220f, 479f)
                    lineTo(181f, 504f)
                },
                tone(colors[type], .74f),
                3f
            )
        }
        if (type == 4) {
            line(
                clothes,
                path {
                    moveTo(159f, 83f)
                    lineTo(141f, 693f)
                },
                tone(colors[type], .8f),
                3f
            )
            line(
                clothes,
                path {
                    moveTo(26f, 687f)
                    quadTo(124f, 704f, 214f, 688f)
                },
                tone(colors[type], .7f)
            )
        }
        if (type == 5) {
            pocket(clothes, 112f, 182f, 109f, 130f, colors[type])
            line(
                clothes,
                path {
                    moveTo(17f, 339f)
                    quadTo(123f, 363f, 234f, 341f)
                },
                tone(colors[type], .7f),
                3f
            )
        }
        if (type == 6) {
            line(
                clothes,
                path {
                    moveTo(11f, 244f)
                    quadTo(129f, 268f, 241f, 244f)
                },
                Color.rgb(250, 233, 221),
                8f
            )
            line(
                clothes,
                path {
                    moveTo(11f, 252f)
                    quadTo(129f, 276f, 241f, 252f)
                },
                Color.rgb(230, 142, 129),
                4f
            )
        }
    }

    private fun cuff(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, color: Int) {
        val shape = path { addRoundRect(RectF(left, top, right, bottom), 5f, 5f, Path.Direction.CW) }
        garment(canvas, shape, tone(color, .79f), right)
        var x = left + 7f
        while (x < right - 3f) {
            line(
                canvas,
                path {
                    moveTo(x, top + 3f)
                    lineTo(x, bottom - 3f)
                },
                tone(color, .96f),
                1.3f
            )
            x += 8f
        }
    }

    private fun pocket(canvas: Canvas, left: Float, top: Float, width: Float, height: Float, color: Int) {
        val panel =
            path {
                moveTo(left, top)
                lineTo(left + width, top)
                lineTo(left + width - 4f, top + height)
                quadTo(left + width / 2f, top + height + 6f, left + 3f, top + height)
                close()
            }
        garment(canvas, panel, tone(color, .93f), left + width)
        val flap =
            path {
                moveTo(left - 1f, top)
                lineTo(left + width + 1f, top)
                lineTo(left + width - 2f, top + 21f)
                lineTo(left + width / 2f, top + 27f)
                lineTo(left + 2f, top + 21f)
                close()
            }
        garment(canvas, flap, tone(color, 1.06f), left + width)
        canvas.drawCircle(left + width / 2f, top + 14f, 2.3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = ink })
    }
}
