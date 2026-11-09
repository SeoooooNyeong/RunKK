package com.a207.runkk.app.core.avatar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader

/** Garment shapes in the approved texture coordinates; the Rive rig stays unchanged. */
internal object TopTypes {
    private val ink = Color.rgb(85, 61, 55)
    private val colors = intArrayOf(
        0,
        0xff5ca99d.toInt(),
        0xff537399.toInt(),
        0xffd69c76.toInt(),
        0xffa38abd.toInt(),
        0xff667d68.toInt(),
        0xffec9488.toInt()
    )

    fun apply(assetName: String, skin: Bitmap, clothing: Bitmap, type: Int) {
        require(type in 0..6)
        if (type == 0) return
        when (assetName) {
            "standing_body" -> front(Canvas(skin), Canvas(clothing), type)
            "continuous_arm" -> arm(Canvas(skin), Canvas(clothing), type)
            "side_body_coral", "side_body_cream", "side_body_blue" -> side(Canvas(clothing), type)
        }
    }

    private fun path(block: Path.() -> Unit) = Path().apply(block)
    private fun line(canvas: Canvas, shape: Path, color: Int = ink, width: Float = 3f) {
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
    private fun garment(canvas: Canvas, shape: Path, color: Int, height: Float, width: Float = 3f) {
        fun shade(f: Float) = Color.rgb(
            (Color.red(color) * f).toInt().coerceAtMost(255),
            (Color.green(color) * f).toInt().coerceAtMost(255),
            (Color.blue(color) * f).toInt().coerceAtMost(255)
        )
        canvas.drawPath(
            shape,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    260f,
                    height,
                    intArrayOf(shade(1.16f), color, shade(.82f)),
                    floatArrayOf(0f, .55f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        )
        line(canvas, shape, ink, width)
    }
    private fun clear(canvas: Canvas, shape: Path) {
        canvas.drawPath(
            shape,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                style = Paint.Style.FILL_AND_STROKE
                strokeWidth = 7f
            }
        )
    }

    /** Keep the original arm shading in front and fill only the uncovered shoulder. */
    private fun skinBacking(canvas: Canvas, shape: Path, outline: Path, height: Float) {
        canvas.drawPath(
            shape,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    20f,
                    0f,
                    190f,
                    height,
                    intArrayOf(Color.rgb(251, 214, 196), Color.rgb(255, 229, 215), Color.rgb(252, 220, 205)),
                    floatArrayOf(0f, .5f, 1f),
                    Shader.TileMode.CLAMP
                )
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
            }
        )
        // Open contour: the join to the original forearm must never be outlined.
        line(canvas, outline, ink, 3f)
    }
    private fun clearRect(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) = canvas.drawRect(
        left,
        top,
        right,
        bottom,
        Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
    )

    private fun originalFront() = path {
        moveTo(211f, 1f)
        quadTo(256f, 57f, 305f, 1f)
        cubicTo(369f, 15f, 398f, 47f, 447f, 144f)
        quadTo(427f, 172f, 370f, 182f)
        lineTo(398f, 289f)
        quadTo(258f, 329f, 115f, 289f)
        lineTo(143f, 182f)
        quadTo(88f, 174f, 63f, 145f)
        cubicTo(107f, 58f, 140f, 17f, 211f, 1f)
        close()
    }
    private fun torsoFront() = path {
        moveTo(211f, 1f)
        quadTo(256f, 54f, 305f, 1f)
        lineTo(365f, 26f)
        quadTo(391f, 103f, 375f, 178f)
        lineTo(398f, 294f)
        quadTo(256f, 326f, 114f, 294f)
        lineTo(137f, 178f)
        quadTo(122f, 103f, 147f, 26f)
        close()
    }
    private fun front(skin: Canvas, cloth: Canvas, type: Int) {
        clear(cloth, originalFront())
        when (type) {
            1 -> drawSleeveless(skin, cloth)
            2 -> drawPolo(cloth)
            3 -> drawSweater(cloth)
            4 -> drawHoodie(cloth)
            5 -> drawZipJacket(cloth)
            6 -> drawHoodie(cloth, 6)
        }
    }
    private fun mirror(canvas: Canvas, draw: () -> Unit) {
        draw()
        canvas.save()
        canvas.translate(512f, 0f)
        canvas.scale(-1f, 1f)
        draw()
        canvas.restore()
    }
    private fun drawSleeveless(skin: Canvas, cloth: Canvas) {
        val shoulder = path {
            moveTo(181f, 24f)
            cubicTo(140f, 28f, 105f, 76f, 81f, 129f)
            cubicTo(63f, 173f, 45f, 223f, 33f, 277f)
            lineTo(95f, 296f)
            cubicTo(115f, 255f, 135f, 208f, 145f, 166f)
            lineTo(183f, 88f)
            quadTo(196f, 49f, 181f, 24f)
            close()
        }
        mirror(skin) {
            skinBacking(
                skin,
                shoulder,
                path {
                    moveTo(181f, 24f)
                    cubicTo(140f, 28f, 105f, 76f, 81f, 129f)
                    quadTo(71f, 151f, 64f, 174f)
                },
                310f
            )
        }
        drawTank(cloth)
    }

    /** Replace the old sleeve shadow, then blend into the untouched forearm above the hand. */
    fun maleSleeveless(skin: Bitmap, clothing: Bitmap) {
        require(
            skin.width == 512 && skin.height == 768 &&
                clothing.width == 512 && clothing.height == 768
        )
        val replacement = Bitmap.createBitmap(512, 280, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(replacement)
            val shoulder = path {
                moveTo(181f, 24f)
                cubicTo(140f, 28f, 113f, 75f, 94f, 125f)
                cubicTo(86f, 148f, 80f, 168f, 71f, 188f)
                cubicTo(58f, 215f, 49f, 240f, 39f, 270f)
                lineTo(111f, 270f)
                cubicTo(124f, 240f, 135f, 208f, 142f, 178f)
                lineTo(183f, 88f)
                quadTo(196f, 49f, 181f, 24f)
                close()
            }
            mirror(canvas) {
                skinBacking(
                    canvas,
                    shoulder,
                    path {
                        moveTo(181f, 24f)
                        cubicTo(140f, 28f, 113f, 75f, 94f, 125f)
                        cubicTo(86f, 148f, 80f, 168f, 71f, 188f)
                        cubicTo(58f, 215f, 49f, 240f, 39f, 270f)
                        moveTo(111f, 270f)
                        cubicTo(124f, 240f, 135f, 208f, 142f, 178f)
                    },
                    310f
                )
            }
            val original = IntArray(512 * 280)
            val clean = IntArray(original.size)
            skin.getPixels(original, 0, 512, 0, 0, 512, 280)
            replacement.getPixels(clean, 0, 512, 0, 0, 512, 280)
            for (y in 0 until 270) {
                val t = ((y - 210f) / 60f).coerceIn(0f, 1f)
                val keep = t * t * (3f - 2f * t)
                for (x in 0 until 512) {
                    if (x in 194..317) continue
                    val i = y * 512 + x
                    val a = clean[i]
                    val b = original[i]
                    val aa = Color.alpha(a) * (1f - keep)
                    val ab = Color.alpha(b) * keep
                    val alpha = aa + ab
                    fun channel(ca: Int, cb: Int) = if (alpha == 0f) 0 else ((ca * aa + cb * ab) / alpha).toInt()
                    original[i] = Color.argb(
                        alpha.toInt(),
                        channel(Color.red(a), Color.red(b)),
                        channel(Color.green(a), Color.green(b)),
                        channel(Color.blue(a), Color.blue(b))
                    )
                }
            }
            skin.setPixels(original, 0, 512, 0, 0, 512, 280)
        } finally {
            replacement.recycle()
        }
        val cloth = Canvas(clothing)
        clear(cloth, originalFront())
        // The old cuff's dark edge may be in the clothing partition, not skin.
        clearRect(cloth, 0f, 140f, 159f, 220f)
        clearRect(cloth, 353f, 140f, 512f, 220f)
        MaleOutfitStyles.drawTank(cloth)
    }

    private fun drawTank(cloth: Canvas) {
        val tank = path {
            moveTo(194f, 10f)
            lineTo(215f, 3f)
            quadTo(255f, 70f, 296f, 3f)
            lineTo(318f, 11f)
            quadTo(322f, 81f, 353f, 111f)
            lineTo(391f, 291f)
            quadTo(256f, 319f, 121f, 291f)
            lineTo(159f, 111f)
            quadTo(190f, 81f, 194f, 10f)
            close()
        }
        garment(cloth, tank, colors[1], 310f)
        line(
            cloth,
            path {
                moveTo(204f, 8f)
                quadTo(255f, 88f, 307f, 8f)
            },
            0xffd7f2e9.toInt(),
            5f
        )
        line(
            cloth,
            path {
                moveTo(130f, 281f)
                quadTo(256f, 307f, 382f, 281f)
            },
            0xff397e76.toInt(),
            3f
        )
    }
    private fun drawPolo(cloth: Canvas) {
        garment(cloth, originalFront(), colors[2], 310f)
        garment(
            cloth,
            path {
                moveTo(209f, 4f)
                lineTo(253f, 47f)
                lineTo(229f, 86f)
                lineTo(185f, 27f)
                close()
            },
            0xffe7e6df.toInt(),
            90f
        )
        garment(
            cloth,
            path {
                moveTo(303f, 4f)
                lineTo(259f, 47f)
                lineTo(283f, 86f)
                lineTo(327f, 27f)
                close()
            },
            0xffe7e6df.toInt(),
            90f
        )
        line(
            cloth,
            path {
                moveTo(256f, 49f)
                lineTo(256f, 126f)
            },
            0xffe9e6df.toInt(),
            10f
        )
        for (y in listOf(68f, 91f, 114f)) {
            cloth.drawCircle(
                256f,
                y,
                3f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = ink
                }
            )
        }
        line(
            cloth,
            path {
                moveTo(74f, 145f)
                quadTo(106f, 169f, 141f, 174f)
            },
            0xffe5e7e6.toInt(),
            5f
        )
        line(
            cloth,
            path {
                moveTo(438f, 145f)
                quadTo(406f, 169f, 371f, 174f)
            },
            0xffe5e7e6.toInt(),
            5f
        )
    }
    private fun longSleeves(cloth: Canvas, type: Int) {
        val sleeve = path {
            moveTo(150f, 29f)
            cubicTo(108f, 55f, 83f, 104f, 65f, 159f)
            lineTo(24f, 279f)
            quadTo(17f, 296f, 14f, 309f)
            lineTo(89f, 332f)
            quadTo(109f, 285f, 123f, 240f)
            lineTo(153f, 160f)
            lineTo(171f, 57f)
            close()
        }
        mirror(cloth) {
            garment(cloth, sleeve, colors[type], 340f)
            garment(
                cloth,
                path {
                    moveTo(20f, 292f)
                    lineTo(95f, 315f)
                    lineTo(88f, 334f)
                    lineTo(14f, 312f)
                    close()
                },
                colors[type],
                390f
            )
            for (i in 0..7) {
                val x = 23f + i * 8f
                line(
                    cloth,
                    path {
                        moveTo(x, 297f + i * 2.5f)
                        lineTo(x - 5f, 310f + i * 2.5f)
                    },
                    0x55704d47,
                    1.5f
                )
            }
        }
    }
    private fun hem(cloth: Canvas, color: Int) {
        garment(
            cloth,
            path {
                moveTo(117f, 279f)
                quadTo(256f, 308f, 395f, 279f)
                lineTo(398f, 300f)
                quadTo(256f, 328f, 114f, 300f)
                close()
            },
            color,
            365f
        )
        for (x in 125..388 step 10) {
            line(
                cloth,
                path {
                    moveTo(x.toFloat(), 293f)
                    lineTo(x.toFloat(), 305f)
                },
                0x44634a42,
                1f
            )
        }
    }
    private fun drawSweater(cloth: Canvas) {
        longSleeves(cloth, 3)
        garment(cloth, torsoFront(), colors[3], 320f)
        hem(cloth, colors[3])
        line(
            cloth,
            path {
                moveTo(209f, 8f)
                quadTo(256f, 68f, 306f, 8f)
            },
            0xffa66c52.toInt(),
            10f
        )
        line(
            cloth,
            path {
                moveTo(166f, 167f)
                quadTo(156f, 236f, 141f, 267f)
            },
            0x55805848,
            3f
        )
    }
    private fun drawHoodie(cloth: Canvas, type: Int = 4) {
        val coral = type == 6
        longSleeves(cloth, type)
        garment(cloth, torsoFront(), if (coral) 0xfffce6dc.toInt() else colors[type], 320f)
        hem(cloth, colors[type])
        garment(
            cloth,
            path {
                moveTo(177f, 29f)
                quadTo(182f, 0f, 213f, 3f)
                quadTo(255f, 49f, 300f, 3f)
                quadTo(331f, 0f, 337f, 29f)
                lineTo(346f, 77f)
                quadTo(256f, 50f, 167f, 77f)
                close()
            },
            if (coral) 0xfffce6dc.toInt() else 0xffbaa6cf.toInt(),
            110f
        )
        line(
            cloth,
            path {
                moveTo(195f, 22f)
                quadTo(256f, 91f, 319f, 22f)
            },
            if (coral) colors[6] else 0xff766386.toInt(),
            5f
        )
        for (x in listOf(225f, 286f)) {
            line(
                cloth,
                path {
                    moveTo(x, 66f)
                    quadTo(x + 4f, 118f, x, 158f)
                },
                0xffeee6e1.toInt(),
                4f
            )
            line(
                cloth,
                path {
                    moveTo(x, 151f)
                    lineTo(x, 163f)
                },
                ink,
                5f
            )
        }
        garment(
            cloth,
            path {
                moveTo(188f, 213f)
                lineTo(324f, 213f)
                lineTo(345f, 266f)
                quadTo(256f, 283f, 167f, 266f)
                close()
            },
            colors[type],
            360f
        )
        line(
            cloth,
            path {
                moveTo(188f, 219f)
                lineTo(174f, 255f)
                moveTo(324f, 219f)
                lineTo(338f, 255f)
            },
            0xff786184.toInt(),
            3f
        )
        if (coral) {
            line(
                cloth,
                path {
                    moveTo(256f, 90f)
                    lineTo(256f, 309f)
                },
                0xffa96659.toInt(),
                4f
            )
        }
    }
    private fun drawZipJacket(cloth: Canvas) {
        longSleeves(cloth, 5)
        garment(cloth, torsoFront(), colors[5], 320f)
        hem(cloth, colors[5])
        garment(
            cloth,
            path {
                moveTo(210f, 1f)
                lineTo(250f, 43f)
                lineTo(226f, 68f)
                lineTo(195f, 23f)
                close()
            },
            0xff8e9b83.toInt(),
            100f
        )
        garment(
            cloth,
            path {
                moveTo(303f, 1f)
                lineTo(262f, 43f)
                lineTo(286f, 68f)
                lineTo(317f, 23f)
                close()
            },
            0xff8e9b83.toInt(),
            100f
        )
        line(
            cloth,
            path {
                moveTo(256f, 43f)
                lineTo(256f, 307f)
            },
            0xffd5d6bf.toInt(),
            7f
        )
        line(
            cloth,
            path {
                moveTo(256f, 45f)
                lineTo(256f, 307f)
            },
            0xff46594d.toInt(),
            2f
        )
        garment(
            cloth,
            path {
                moveTo(250f, 64f)
                lineTo(262f, 64f)
                lineTo(261f, 81f)
                lineTo(251f, 81f)
                close()
            },
            0xffd9d5c5.toInt(),
            100f,
            1f
        )
        line(
            cloth,
            path {
                moveTo(161f, 217f)
                lineTo(205f, 257f)
                moveTo(351f, 217f)
                lineTo(307f, 257f)
            },
            0xffc6cbb8.toInt(),
            4f
        )
    }

    private fun side(cloth: Canvas, type: Int) {
        clearRect(cloth, 0f, 0f, 256f, 384f)
        val body = path {
            moveTo(106f, 4f)
            quadTo(145f, 25f, 183f, 20f)
            cubicTo(217f, 53f, 235f, 178f, 247f, 270f)
            lineTo(252f, 365f)
            quadTo(134f, 396f, 4f, 365f)
            quadTo(11f, 313f, 24f, 267f)
            cubicTo(32f, 207f, -11f, 107f, 106f, 4f)
            close()
        }
        garment(cloth, body, if (type == 6) 0xfffce6dc.toInt() else colors[type], 384f)
        if (type == 6) {
            garment(
                cloth,
                path {
                    moveTo(105f, 5f)
                    quadTo(32f, 44f, 24f, 141f)
                    lineTo(25f, 264f)
                    lineTo(4f, 365f)
                    lineTo(81f, 376f)
                    quadTo(107f, 268f, 102f, 181f)
                    lineTo(120f, 35f)
                    close()
                },
                colors[6],
                384f,
                2f
            )
        }
        if (type == 1) {
            line(
                cloth,
                path {
                    moveTo(107f, 10f)
                    quadTo(151f, 49f, 190f, 37f)
                },
                0xffd7f2e9.toInt(),
                6f
            )
            line(
                cloth,
                path {
                    moveTo(85f, 39f)
                    cubicTo(40f, 85f, 35f, 149f, 64f, 183f)
                },
                0xffd7f2e9.toInt(),
                7f
            )
        }
        if (type == 2) {
            garment(
                cloth,
                path {
                    moveTo(107f, 5f)
                    lineTo(156f, 30f)
                    lineTo(139f, 69f)
                    lineTo(91f, 28f)
                    close()
                },
                0xffe7e6df.toInt(),
                90f
            )
            garment(
                cloth,
                path {
                    moveTo(181f, 18f)
                    lineTo(195f, 37f)
                    lineTo(183f, 74f)
                    lineTo(158f, 31f)
                    close()
                },
                0xffe7e6df.toInt(),
                90f
            )
            line(
                cloth,
                path {
                    moveTo(184f, 66f)
                    lineTo(200f, 128f)
                },
                0xffe5e4de.toInt(),
                8f
            )
            for (i in 0..2) {
                cloth.drawCircle(
                    187f + i * 5f,
                    78f + i * 20f,
                    2.5f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color =
                            ink
                    }
                )
            }
        }
        if (type >= 3) {
            line(
                cloth,
                path {
                    moveTo(11f, 351f)
                    quadTo(138f, 379f, 247f, 351f)
                },
                colors[type],
                18f
            )
            line(
                cloth,
                path {
                    moveTo(13f, 355f)
                    quadTo(138f, 383f, 247f, 355f)
                },
                0x66634a42,
                2f
            )
        }
        if (type == 3) {
            line(
                cloth,
                path {
                    moveTo(106f, 7f)
                    quadTo(145f, 40f, 188f, 34f)
                },
                0xffa66c52.toInt(),
                9f
            )
        }
        if (type == 4 || type == 6) {
            garment(
                cloth,
                path {
                    moveTo(78f, 20f)
                    quadTo(84f, -2f, 111f, 5f)
                    quadTo(146f, 31f, 180f, 20f)
                    lineTo(205f, 67f)
                    quadTo(126f, 54f, 69f, 79f)
                    close()
                },
                if (type ==
                    6
                ) {
                    0xfffce6dc.toInt()
                } else {
                    0xffbaa6cf.toInt()
                },
                100f
            )
            line(
                cloth,
                path {
                    moveTo(92f, 19f)
                    quadTo(142f, 66f, 185f, 40f)
                },
                if (type ==
                    6
                ) {
                    colors[6]
                } else {
                    0xff766386.toInt()
                },
                4f
            )
            line(
                cloth,
                path {
                    moveTo(180f, 59f)
                    lineTo(188f, 161f)
                },
                0xffeee6e1.toInt(),
                4f
            )
            garment(
                cloth,
                path {
                    moveTo(152f, 236f)
                    lineTo(222f, 230f)
                    lineTo(236f, 315f)
                    lineTo(160f, 321f)
                    lineTo(137f, 286f)
                    close()
                },
                colors[type],
                380f
            )
            if (type == 6) {
                line(
                    cloth,
                    path {
                        moveTo(195f, 87f)
                        cubicTo(215f, 176f, 230f, 278f, 238f, 367f)
                    },
                    0xffa96659.toInt(),
                    5f
                )
                garment(
                    cloth,
                    path {
                        moveTo(189f, 83f)
                        lineTo(201f, 83f)
                        lineTo(203f, 101f)
                        lineTo(191f, 101f)
                        close()
                    },
                    0xfffce6dc.toInt(),
                    110f,
                    1.5f
                )
            }
        }
        if (type == 5) {
            garment(
                cloth,
                path {
                    moveTo(105f, 3f)
                    lineTo(155f, 27f)
                    lineTo(140f, 64f)
                    lineTo(96f, 25f)
                    close()
                },
                0xff8e9b83.toInt(),
                100f
            )
            line(
                cloth,
                path {
                    moveTo(190f, 40f)
                    cubicTo(213f, 139f, 230f, 270f, 238f, 367f)
                },
                0xffd5d6bf.toInt(),
                6f
            )
            line(
                cloth,
                path {
                    moveTo(166f, 242f)
                    lineTo(200f, 285f)
                },
                0xffc6cbb8.toInt(),
                4f
            )
        }
    }

    private fun arm(skin: Canvas, cloth: Canvas, type: Int) {
        clearRect(cloth, 0f, 0f, 256f, if (type >= 3) 860f else 326f)
        if (type == 1) {
            skinBacking(
                skin,
                path {
                    moveTo(175f, 20f)
                    cubicTo(211f, 20f, 221f, 56f, 218f, 122f)
                    lineTo(215f, 350f)
                    quadTo(136f, 355f, 52f, 340f)
                    lineTo(66f, 118f)
                    quadTo(87f, 39f, 175f, 20f)
                    close()
                },
                path {
                    moveTo(57f, 286f)
                    lineTo(66f, 118f)
                    quadTo(87f, 39f, 175f, 20f)
                    cubicTo(211f, 20f, 221f, 56f, 218f, 122f)
                    lineTo(215f, 310f)
                },
                360f
            )
            return
        }
        if (type == 2) {
            garment(
                cloth,
                path {
                    moveTo(179f, 3f)
                    cubicTo(226f, 5f, 237f, 47f, 239f, 132f)
                    lineTo(249f, 298f)
                    quadTo(241f, 323f, 214f, 315f)
                    quadTo(139f, 291f, 55f, 281f)
                    lineTo(49f, 307f)
                    quadTo(1f, 298f, 7f, 273f)
                    lineTo(55f, 80f)
                    quadTo(82f, 27f, 179f, 3f)
                    close()
                },
                colors[2],
                320f
            )
            line(
                cloth,
                path {
                    moveTo(12f, 272f)
                    quadTo(66f, 257f, 242f, 301f)
                },
                0xffe7e6df.toInt(),
                12f
            )
            return
        }
        garment(
            cloth,
            path {
                moveTo(179f, 3f)
                cubicTo(225f, 5f, 238f, 43f, 239f, 132f)
                cubicTo(243f, 190f, 250f, 246f, 244f, 289f)
                cubicTo(231f, 379f, 218f, 450f, 221f, 523f)
                cubicTo(230f, 629f, 217f, 745f, 219f, 834f)
                lineTo(78f, 854f)
                cubicTo(57f, 762f, 43f, 653f, 40f, 555f)
                cubicTo(34f, 508f, 42f, 462f, 42f, 416f)
                cubicTo(30f, 315f, 29f, 196f, 56f, 78f)
                quadTo(83f, 25f, 179f, 3f)
                close()
            },
            colors[type],
            860f,
            4f
        )
        garment(
            cloth,
            path {
                moveTo(73f, 815f)
                lineTo(219f, 799f)
                lineTo(219f, 848f)
                lineTo(82f, 868f)
                close()
            },
            colors[type],
            1000f,
            3f
        )
        for (x in 86..212 step 13) {
            line(
                cloth,
                path {
                    moveTo(x.toFloat(), 824f)
                    lineTo(x.toFloat() + 3f, 851f)
                },
                0x66704d47,
                2f
            )
        }
        line(
            cloth,
            path {
                moveTo(87f, 420f)
                quadTo(69f, 463f, 79f, 498f)
            },
            0x44704d47,
            3f
        )
        if (type == 6) {
            line(
                cloth,
                path {
                    moveTo(43f, 237f)
                    quadTo(138f, 253f, 244f, 278f)
                },
                0xfffce6dc.toInt(),
                18f
            )
            line(
                cloth,
                path {
                    moveTo(42f, 255f)
                    quadTo(138f, 271f, 243f, 296f)
                },
                0xff55546b.toInt(),
                16f
            )
        }
    }
}
