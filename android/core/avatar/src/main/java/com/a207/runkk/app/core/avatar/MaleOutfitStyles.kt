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

/** Male cuts in the same atlas registration; source skin, hands and the rig stay separate. */
internal object MaleOutfitStyles {
    private val ink = Color.rgb(85, 61, 55)
    private val trim = 0xffeee8df.toInt()
    private val tops = intArrayOf(
        0,
        0xff5ca99d.toInt(),
        0xff537399.toInt(),
        0xffd69c76.toInt(),
        0xffa38abd.toInt(),
        0xff667d68.toInt(),
        0xffec9488.toInt()
    )
    private val bottoms = intArrayOf(
        0,
        0xff374c70.toInt(),
        0xff413953.toInt(),
        0xff7a8f80.toInt(),
        0xffc0ad8b.toInt(),
        0xff747b53.toInt(),
        0xff4c4c63.toInt()
    )

    fun apply(asset: String, skin: Bitmap, clothing: Bitmap, selection: AvatarOutfit) {
        if (selection.isDefault || asset == "side_head") return
        FootwearTypes.apply(asset, skin, clothing, selection.shoes)
        if (selection.bottom != 0) bottom(asset, skin, clothing, selection.bottom)
        if (selection.top == 0) return
        when (asset) {
            "standing_body" -> if (selection.top == 1) {
                TopTypes.maleSleeveless(skin, clothing)
            } else {
                if (selection.top in 3..6) {
                    // The long-sleeve silhouette replaces the old bare arms. Keep
                    // the approved hands (source y296 onward) and neck intact.
                    val skinCanvas = Canvas(skin)
                    clearRect(skinCanvas, 0f, 0f, 180f, 296f)
                    clearRect(skinCanvas, 332f, 0f, 512f, 296f)
                }
                frontTop(Canvas(clothing), selection.top)
            }

            "continuous_arm" -> arm(skin, clothing, selection.top)

            "side_body_coral", "side_body_cream", "side_body_blue" -> sideTop(Canvas(clothing), selection.top)
        }
    }

    private fun path(block: Path.() -> Unit) = Path().apply(block)
    private fun tone(color: Int, scale: Float) = Color.rgb(
        (Color.red(color) * scale).toInt().coerceIn(0, 255),
        (Color.green(color) * scale).toInt().coerceIn(0, 255),
        (Color.blue(color) * scale).toInt().coerceIn(0, 255)
    )
    private fun line(c: Canvas, p: Path, color: Int = ink, width: Float = 3f) {
        c.drawPath(
            p,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = width
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun garment(c: Canvas, p: Path, color: Int, width: Float = 512f) {
        val bounds = RectF().also { p.computeBounds(it, true) }
        c.drawPath(
            p,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    bounds.left,
                    bounds.top,
                    bounds.right,
                    bounds.top,
                    intArrayOf(tone(color, .86f), tone(color, 1.08f), tone(color, 1.1f), color, tone(color, .83f)),
                    floatArrayOf(0f, .23f, .45f, .7f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        )
        c.drawPath(
            p,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    bounds.top,
                    0f,
                    bounds.bottom,
                    intArrayOf(0x12ffffff, 0x00ffffff, 0x17704d47),
                    floatArrayOf(0f, .4f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        )
        line(c, p)
    }
    private fun fold(c: Canvas, p: Path, alpha: Int = 35) {
        val bounds = RectF().also { p.computeBounds(it, true) }
        c.drawPath(
            p,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    bounds.left,
                    bounds.top,
                    bounds.right,
                    bounds.bottom,
                    Color.argb(alpha, 70, 49, 45),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
            }
        )
    }
    private fun clear(c: Canvas, p: Path) = c.drawPath(
        p,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = 7f
        }
    )
    private fun clearRect(c: Canvas, l: Float, t: Float, r: Float, b: Float) =
        c.drawRect(l, t, r, b, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })
    private fun mirror(c: Canvas, draw: () -> Unit) {
        draw()
        val save = c.save()
        c.translate(512f, 0f)
        c.scale(-1f, 1f)
        draw()
        c.restoreToCount(save)
    }

    internal fun drawTank(c: Canvas) {
        garment(
            c,
            path {
                moveTo(194f, 10f)
                lineTo(214f, 3f)
                quadTo(256f, 58f, 298f, 3f)
                lineTo(318f, 10f)
                quadTo(326f, 71f, 355f, 105f)
                lineTo(380f, 292f)
                quadTo(256f, 312f, 132f, 292f)
                lineTo(157f, 105f)
                quadTo(186f, 71f, 194f, 10f)
                close()
            },
            tops[1]
        )
        line(
            c,
            path {
                moveTo(204f, 8f)
                quadTo(256f, 73f, 308f, 8f)
            },
            0xffd7f2e9.toInt(),
            4f
        )
        line(
            c,
            path {
                moveTo(142f, 282f)
                quadTo(256f, 299f, 370f, 282f)
            },
            tone(tops[1], .8f),
            2f
        )
    }
    private fun originalTop() = path {
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
    private fun body() = path {
        moveTo(211f, 2f)
        quadTo(256f, 51f, 301f, 2f)
        lineTo(366f, 28f)
        lineTo(375f, 127f)
        lineTo(386f, 294f)
        quadTo(256f, 315f, 126f, 294f)
        lineTo(137f, 127f)
        lineTo(146f, 28f)
        close()
    }
    private fun sleeves(c: Canvas, type: Int) {
        val color = tops[type]
        mirror(c) {
            garment(
                c,
                path {
                    moveTo(157f, 25f)
                    cubicTo(115f, 40f, 94f, 88f, 75f, 145f)
                    lineTo(24f, 290f)
                    lineTo(18f, 309f)
                    lineTo(88f, 329f)
                    lineTo(111f, 259f)
                    lineTo(143f, 163f)
                    lineTo(174f, 51f)
                    close()
                },
                color
            )
            garment(
                c,
                path {
                    moveTo(24f, 292f)
                    lineTo(94f, 312f)
                    lineTo(88f, 330f)
                    lineTo(18f, 310f)
                    close()
                },
                tone(color, .89f)
            )
            line(
                c,
                path {
                    moveTo(84f, 166f)
                    lineTo(62f, 226f)
                },
                tone(color, .86f),
                2f
            )
            fold(
                c,
                path {
                    moveTo(137f, 124f)
                    quadTo(113f, 227f, 86f, 304f)
                    lineTo(74f, 299f)
                    quadTo(111f, 206f, 137f, 124f)
                    close()
                }
            )
        }
    }
    private fun frontTop(c: Canvas, type: Int) {
        clear(c, originalTop())
        if (type == 2) {
            garment(
                c,
                path {
                    moveTo(211f, 2f)
                    quadTo(256f, 51f, 301f, 2f)
                    cubicTo(361f, 12f, 397f, 66f, 447f, 144f)
                    quadTo(427f, 172f, 370f, 182f)
                    lineTo(386f, 294f)
                    quadTo(256f, 315f, 126f, 294f)
                    lineTo(143f, 182f)
                    quadTo(88f, 174f, 63f, 145f)
                    cubicTo(115f, 66f, 151f, 12f, 211f, 2f)
                    close()
                },
                tops[type]
            )
            garment(
                c,
                path {
                    moveTo(210f, 5f)
                    lineTo(253f, 39f)
                    lineTo(234f, 66f)
                    lineTo(192f, 24f)
                    close()
                },
                trim
            )
            garment(
                c,
                path {
                    moveTo(302f, 5f)
                    lineTo(259f, 39f)
                    lineTo(278f, 66f)
                    lineTo(320f, 24f)
                    close()
                },
                trim
            )
            line(
                c,
                path {
                    moveTo(256f, 43f)
                    lineTo(256f, 106f)
                },
                trim,
                7f
            )
            for (y in listOf(62f, 87f)) c.drawCircle(256f, y, 2.3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink })
            line(
                c,
                path {
                    moveTo(80f, 142f)
                    quadTo(105f, 158f, 137f, 165f)
                    moveTo(432f, 142f)
                    quadTo(407f, 158f, 375f, 165f)
                },
                trim,
                4f
            )
        } else {
            sleeves(c, type)
            garment(c, body(), if (type == 6) 0xfffce6dc.toInt() else tops[type])
            garment(
                c,
                path {
                    moveTo(128f, 278f)
                    quadTo(256f, 297f, 384f, 278f)
                    lineTo(386f, 299f)
                    quadTo(256f, 319f, 126f, 299f)
                    close()
                },
                tops[type]
            )
            if (type == 3) {
                line(
                    c,
                    path {
                        moveTo(211f, 8f)
                        quadTo(256f, 57f, 301f, 8f)
                    },
                    tone(tops[type], .78f),
                    8f
                )
            }
            if (type == 4 || type == 6) {
                garment(
                    c,
                    path {
                        moveTo(211f, 8f)
                        cubicTo(190f, 8f, 178f, 23f, 175f, 45f)
                        quadTo(192f, 66f, 227f, 58f)
                        quadTo(244f, 56f, 256f, 49f)
                        quadTo(268f, 56f, 285f, 58f)
                        quadTo(320f, 66f, 337f, 45f)
                        cubicTo(334f, 23f, 322f, 8f, 301f, 8f)
                        quadTo(256f, 45f, 211f, 8f)
                        close()
                    },
                    if (type == 6) 0xfffce6dc.toInt() else tone(tops[type], 1.13f)
                )
                line(
                    c,
                    path {
                        moveTo(202f, 20f)
                        quadTo(224f, 43f, 256f, 49f)
                        quadTo(288f, 43f, 310f, 20f)
                    },
                    tone(tops[type], .74f),
                    3f
                )
                fold(
                    c,
                    path {
                        moveTo(180f, 44f)
                        quadTo(208f, 52f, 247f, 50f)
                        quadTo(204f, 65f, 180f, 44f)
                        close()
                    },
                    42
                )
                fold(
                    c,
                    path {
                        moveTo(332f, 44f)
                        quadTo(304f, 52f, 265f, 50f)
                        quadTo(308f, 65f, 332f, 44f)
                        close()
                    },
                    42
                )
                for (x in listOf(228f, 284f)) {
                    line(
                        c,
                        path {
                            moveTo(x, 58f)
                            quadTo(x - 2f, 95f, x, 137f)
                        },
                        trim,
                        3.5f
                    )
                }
                garment(
                    c,
                    path {
                        moveTo(188f, 213f)
                        lineTo(324f, 213f)
                        lineTo(338f, 262f)
                        lineTo(174f, 262f)
                        close()
                    },
                    tops[type]
                )
                fold(
                    c,
                    path {
                        moveTo(188f, 217f)
                        lineTo(324f, 217f)
                        lineTo(332f, 251f)
                        quadTo(260f, 234f, 180f, 252f)
                        close()
                    },
                    26
                )
            }
            if (type == 5) {
                garment(
                    c,
                    path {
                        moveTo(212f, 4f)
                        lineTo(253f, 37f)
                        lineTo(233f, 57f)
                        lineTo(198f, 22f)
                        close()
                    },
                    tone(tops[type], 1.2f)
                )
                garment(
                    c,
                    path {
                        moveTo(300f, 4f)
                        lineTo(259f, 37f)
                        lineTo(279f, 57f)
                        lineTo(314f, 22f)
                        close()
                    },
                    tone(tops[type], 1.2f)
                )
            }
            if (type == 5 || type == 6) {
                line(
                    c,
                    path {
                        moveTo(256f, 68f)
                        lineTo(256f, 302f)
                    },
                    trim,
                    5f
                )
                line(
                    c,
                    path {
                        moveTo(256f, 69f)
                        lineTo(256f, 301f)
                    },
                    tone(tops[type], .68f),
                    1.8f
                )
                line(
                    c,
                    path {
                        moveTo(165f, 216f)
                        lineTo(206f, 250f)
                        moveTo(347f, 216f)
                        lineTo(306f, 250f)
                    },
                    tone(tops[type], .7f),
                    2f
                )
            }
        }
        fold(
            c,
            path {
                moveTo(145f, 137f)
                cubicTo(144f, 192f, 149f, 249f, 158f, 276f)
                lineTo(181f, 286f)
                cubicTo(162f, 237f, 160f, 180f, 145f, 137f)
                close()
            },
            32
        )
        fold(
            c,
            path {
                moveTo(366f, 145f)
                cubicTo(363f, 208f, 357f, 254f, 346f, 278f)
                lineTo(375f, 285f)
                cubicTo(367f, 235f, 369f, 185f, 366f, 145f)
                close()
            },
            25
        )
        fold(
            c,
            path {
                moveTo(162f, 266f)
                quadTo(248f, 291f, 353f, 267f)
                quadTo(278f, 300f, 162f, 280f)
                close()
            },
            26
        )
        line(
            c,
            path {
                moveTo(146f, 209f)
                quadTo(151f, 251f, 160f, 266f)
            },
            0x33704d47,
            2f
        )
    }

    private fun sideTop(c: Canvas, type: Int) {
        clearRect(c, 0f, 0f, 256f, 384f)
        garment(
            c,
            path {
                moveTo(106f, 4f)
                quadTo(145f, 25f, 183f, 20f)
                cubicTo(208f, 51f, 216f, 133f, 225f, 222f)
                lineTo(239f, 365f)
                quadTo(134f, 386f, 12f, 365f)
                lineTo(22f, 260f)
                cubicTo(32f, 183f, 1f, 96f, 106f, 4f)
                close()
            },
            if (type == 6) 0xfffce6dc.toInt() else tops[type],
            256f
        )
        if (type == 1) {
            line(
                c,
                path {
                    moveTo(108f, 10f)
                    quadTo(148f, 42f, 184f, 34f)
                },
                0xffd7f2e9.toInt(),
                5f
            )
            line(
                c,
                path {
                    moveTo(86f, 38f)
                    cubicTo(47f, 77f, 40f, 135f, 65f, 170f)
                },
                0xffd7f2e9.toInt(),
                5f
            )
        }
        if (type == 2 || type == 5) {
            garment(
                c,
                path {
                    moveTo(108f, 6f)
                    lineTo(151f, 28f)
                    lineTo(137f, 56f)
                    lineTo(96f, 25f)
                    close()
                },
                if (type ==
                    2
                ) {
                    trim
                } else {
                    tone(tops[type], 1.2f)
                },
                256f
            )
            garment(
                c,
                path {
                    moveTo(178f, 20f)
                    lineTo(189f, 35f)
                    lineTo(178f, 59f)
                    lineTo(154f, 30f)
                    close()
                },
                if (type ==
                    2
                ) {
                    trim
                } else {
                    tone(tops[type], 1.2f)
                },
                256f
            )
            if (type == 2) {
                line(
                    c,
                    path {
                        moveTo(183f, 57f)
                        lineTo(192f, 106f)
                    },
                    trim,
                    6f
                )
            }
        }
        if (type == 3) {
            line(
                c,
                path {
                    moveTo(108f, 9f)
                    quadTo(147f, 38f, 184f, 31f)
                },
                tone(tops[type], .78f),
                8f
            )
        }
        if (type == 4 || type == 6) {
            garment(
                c,
                path {
                    moveTo(109f, 8f)
                    cubicTo(91f, 7f, 78f, 23f, 77f, 44f)
                    quadTo(91f, 66f, 125f, 56f)
                    quadTo(159f, 49f, 193f, 54f)
                    quadTo(191f, 34f, 179f, 21f)
                    quadTo(146f, 31f, 109f, 8f)
                    close()
                },
                if (type ==
                    6
                ) {
                    0xfffce6dc.toInt()
                } else {
                    tone(tops[type], 1.13f)
                },
                256f
            )
            line(
                c,
                path {
                    moveTo(97f, 22f)
                    quadTo(137f, 54f, 181f, 37f)
                },
                tone(tops[type], .74f),
                3f
            )
            line(
                c,
                path {
                    moveTo(177f, 55f)
                    lineTo(183f, 133f)
                },
                trim,
                3f
            )
            garment(
                c,
                path {
                    moveTo(148f, 231f)
                    lineTo(210f, 228f)
                    lineTo(221f, 310f)
                    lineTo(146f, 314f)
                    lineTo(132f, 278f)
                    close()
                },
                tops[type],
                256f
            )
        }
        if (type >= 3) {
            line(
                c,
                path {
                    moveTo(17f, 354f)
                    quadTo(132f, 373f, 233f, 355f)
                },
                tops[type],
                15f
            )
        }
        if (type == 5 || type == 6) {
            line(
                c,
                path {
                    moveTo(185f, 69f)
                    cubicTo(202f, 154f, 216f, 271f, 228f, 364f)
                },
                trim,
                4f
            )
            line(
                c,
                path {
                    moveTo(185f, 69f)
                    cubicTo(202f, 154f, 216f, 271f, 228f, 364f)
                },
                tone(tops[type], .7f),
                1.5f
            )
        }
        fold(
            c,
            path {
                moveTo(41f, 161f)
                quadTo(37f, 260f, 43f, 336f)
                lineTo(72f, 354f)
                quadTo(47f, 264f, 41f, 161f)
                close()
            },
            35
        )
        fold(
            c,
            path {
                moveTo(56f, 331f)
                quadTo(138f, 353f, 216f, 330f)
                quadTo(143f, 376f, 56f, 343f)
                close()
            },
            27
        )
    }

    private fun arm(skin: Bitmap, clothing: Bitmap, type: Int) {
        val c = Canvas(clothing)
        clearRect(c, 0f, 0f, 256f, if (type >= 3) 870f else 340f)
        if (type == 1) {
            val s = Canvas(skin)
            // Extend skin behind the source shoulder; no cuff is drawn on bare skin.
            val p =
                path {
                    moveTo(175f, 20f)
                    cubicTo(207f, 23f, 215f, 63f, 211f, 130f)
                    lineTo(210f, 350f)
                    quadTo(140f, 355f, 56f, 340f)
                    lineTo(69f, 120f)
                    quadTo(89f, 42f, 175f, 20f)
                    close()
                }
            s.drawPath(
                p,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader =
                        LinearGradient(
                            30f,
                            0f,
                            200f,
                            350f,
                            0xfffbd6c4.toInt(),
                            0xffffe5d7.toInt(),
                            Shader.TileMode.CLAMP
                        )
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
                }
            )
            line(
                s,
                path {
                    moveTo(59f, 291f)
                    lineTo(69f, 120f)
                    quadTo(89f, 42f, 175f, 20f)
                    cubicTo(207f, 23f, 215f, 63f, 211f, 130f)
                    lineTo(210f, 306f)
                }
            )
            return
        }
        if (type == 2) {
            garment(
                c,
                path {
                    moveTo(177f, 4f)
                    cubicTo(219f, 7f, 231f, 52f, 232f, 130f)
                    lineTo(239f, 292f)
                    quadTo(231f, 308f, 212f, 303f)
                    quadTo(132f, 282f, 55f, 273f)
                    lineTo(49f, 292f)
                    quadTo(14f, 286f, 17f, 269f)
                    lineTo(62f, 83f)
                    quadTo(88f, 28f, 177f, 4f)
                    close()
                },
                tops[type],
                256f
            )
            line(
                c,
                path {
                    moveTo(24f, 269f)
                    quadTo(123f, 270f, 234f, 294f)
                },
                trim,
                8f
            )
            return
        }
        garment(
            c,
            path {
                moveTo(177f, 4f)
                cubicTo(217f, 8f, 230f, 52f, 231f, 130f)
                cubicTo(235f, 210f, 234f, 257f, 227f, 295f)
                cubicTo(218f, 380f, 211f, 454f, 214f, 526f)
                cubicTo(221f, 631f, 215f, 746f, 216f, 837f)
                lineTo(81f, 857f)
                cubicTo(63f, 763f, 51f, 652f, 48f, 555f)
                cubicTo(43f, 508f, 49f, 463f, 49f, 418f)
                cubicTo(40f, 314f, 40f, 198f, 62f, 83f)
                quadTo(88f, 28f, 177f, 4f)
                close()
            },
            tops[type],
            256f
        )
        garment(
            c,
            path {
                moveTo(78f, 817f)
                lineTo(216f, 801f)
                lineTo(216f, 849f)
                lineTo(84f, 867f)
                close()
            },
            tone(tops[type], .89f),
            256f
        )
        line(
            c,
            path {
                moveTo(90f, 424f)
                quadTo(77f, 465f, 86f, 496f)
            },
            0x44704d47,
            3f
        )
        fold(
            c,
            path {
                moveTo(216f, 294f)
                cubicTo(192f, 458f, 196f, 653f, 211f, 801f)
                lineTo(187f, 806f)
                cubicTo(174f, 602f, 181f, 421f, 216f, 294f)
                close()
            },
            38
        )
        if (type == 6) {
            line(
                c,
                path {
                    moveTo(48f, 234f)
                    quadTo(137f, 249f, 230f, 271f)
                },
                0xfffce6dc.toInt(),
                16f
            )
            line(
                c,
                path {
                    moveTo(48f, 252f)
                    quadTo(137f, 267f, 229f, 289f)
                },
                bottoms[6],
                12f
            )
        }
    }

    private fun bottom(asset: String, skin: Bitmap, clothing: Bitmap, type: Int) {
        if (asset != "standing_body" && asset != "side_leg_skin") return
        val side = asset == "side_leg_skin"
        if (type == 1 || type == 6) BottomTypes.restoreThighs(skin, side)
        val c = Canvas(clothing)
        if (side) {
            sideBottom(c, type)
            return
        }
        // Preserve the warm hand outlines beside the shorts.
        for (y in 310 until 425) {
            for (x in 95 until 418) {
                val px = clothing.getPixel(x, y)
                if (Color.alpha(px) > 0 && Color.blue(px) > Color.red(px) &&
                    Color.blue(px) >= Color.green(px)
                ) {
                    clothing.setPixel(x, y, 0)
                }
            }
        }
        clear(
            c,
            path {
                moveTo(112f, 291f)
                quadTo(255f, 330f, 401f, 293f)
                lineTo(420f, 426f)
                lineTo(94f, 426f)
                close()
            }
        )
        if (type in 2..4) clearRect(c, 108f, 418f, 404f, 650f)
        if (type == 1 || type == 6) restoreShortLegEdges(skin, c)
        val hem = when (type) {
            1 -> 397f
            5 -> 471f
            6 -> 411f
            4 -> 641f
            else -> 625f
        }
        val left = when (type) {
            2 -> 139f
            3 -> 137f
            4 -> 112f
            5 -> 112f
            else -> 116f
        }
        val inner = if (type == 4) {
            245f
        } else if (type in 2..3) {
            233f
        } else {
            247f
        }
        val sideY = minOf(411f, hem - 12f)
        val crotch = when (type) {
            1 -> 366f
            6 -> 380f
            else -> 405f
        }
        val pantsShape = path {
            moveTo(121f, 297f)
            quadTo(256f, 320f, 391f, 297f)
            quadTo(404f, 350f, 397f, sideY)
            lineTo(512f - left, hem)
            quadTo(329f, hem + 6f, 512f - inner, hem)
            lineTo(259f, crotch)
            lineTo(inner, hem)
            quadTo(183f, hem + 6f, left, hem)
            lineTo(115f, sideY)
            quadTo(108f, 350f, 121f, 297f)
            close()
        }
        garment(c, pantsShape, bottoms[type])
        line(
            c,
            path {
                moveTo(256f, 314f)
                lineTo(256f, 385f)
            },
            tone(bottoms[type], .67f),
            2f
        )
        line(
            c,
            path {
                moveTo(138f, 320f)
                lineTo(171f, 352f)
                moveTo(374f, 320f)
                lineTo(341f, 352f)
            },
            tone(bottoms[type], .72f),
            2f
        )
        val foldClip = c.save()
        c.clipPath(pantsShape)
        fold(
            c,
            path {
                moveTo(126f, 331f)
                quadTo(139f, 355f, 155f, hem - 9f)
                lineTo(125f, hem - 5f)
                close()
            },
            31
        )
        fold(
            c,
            path {
                moveTo(386f, 331f)
                quadTo(373f, 355f, 357f, hem - 9f)
                lineTo(387f, hem - 5f)
                close()
            },
            25
        )
        c.restoreToCount(foldClip)
        if (type == 1 || type == 6) {
            line(
                c,
                path {
                    moveTo(left + 5f, hem - 10f)
                    quadTo(184f, hem - 4f, inner - 5f, hem - 10f)
                    moveTo(517f - inner, hem - 10f)
                    quadTo(328f, hem - 4f, 507f - left, hem - 10f)
                },
                trim,
                if (type ==
                    6
                ) {
                    7f
                } else {
                    4f
                }
            )
            if (type ==
                6
            ) {
                line(
                    c,
                    path {
                        moveTo(left + 5f, hem - 4f)
                        lineTo(inner - 5f, hem - 4f)
                        moveTo(
                            517f - inner,
                            hem - 4f
                        )
                        lineTo(507f - left, hem - 4f)
                    },
                    tops[6],
                    3f
                )
            }
        }
        if (type ==
            2
        ) {
            line(
                c,
                path {
                    moveTo(145f, 355f)
                    lineTo(154f, 613f)
                    moveTo(367f, 355f)
                    lineTo(358f, 613f)
                },
                tone(bottoms[type], 1.2f),
                2f
            )
        }
        if (type == 3) {
            for (x in listOf(137f, 279f)) {
                garment(
                    c,
                    path {
                        moveTo(x, 608f)
                        lineTo(x + 96f, 608f)
                        lineTo(x + 96f, 635f)
                        lineTo(x, 635f)
                        close()
                    },
                    tone(bottoms[type], .84f)
                )
            }
            line(
                c,
                path {
                    moveTo(250f, 320f)
                    lineTo(240f, 348f)
                    moveTo(262f, 320f)
                    lineTo(272f, 348f)
                },
                trim,
                3f
            )
        }
        if (type ==
            4
        ) {
            line(
                c,
                path {
                    moveTo(175f, 344f)
                    lineTo(175f, 628f)
                    moveTo(337f, 344f)
                    lineTo(337f, 628f)
                },
                tone(bottoms[type], .85f),
                2f
            )
        }
        if (type == 5) {
            pocket(c, 119f, 373f, 65f, 73f, bottoms[type])
            pocket(c, 328f, 373f, 65f, 73f, bottoms[type])
        }
    }
    private fun restoreShortLegEdges(skin: Bitmap, cloth: Canvas) {
        val underlay = Canvas(skin)
        // The old shorts erasure also removes the original thigh outline. The
        // new shorts are shorter, so join the newly exposed edges to y430.
        mirror(underlay) {
            underlay.drawPath(
                path {
                    moveTo(119f, 384f)
                    lineTo(249f, 384f)
                    cubicTo(250f, 406f, 249f, 425f, 246f, 444f)
                    lineTo(125f, 444f)
                    cubicTo(121f, 426f, 119f, 408f, 119f, 384f)
                    close()
                },
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        119f,
                        0f,
                        249f,
                        0f,
                        intArrayOf(0xfff2c5af.toInt(), 0xffffe3d1.toInt(), 0xfff8d2bd.toInt()),
                        floatArrayOf(0f, .5f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
                }
            )
        }
        mirror(cloth) {
            line(
                cloth,
                path {
                    moveTo(119f, 386f)
                    cubicTo(119f, 403f, 119f, 417f, 122f, 430f)
                },
                ink,
                4f
            )
            line(
                cloth,
                path {
                    moveTo(249f, 386f)
                    cubicTo(250f, 404f, 249f, 418f, 248f, 430f)
                },
                ink,
                4f
            )
        }
    }
    private fun sideBottom(c: Canvas, type: Int) {
        clearRect(c, 0f, 36f, 256f, if (type in 2..4) 710f else 278f)
        val hem = when (type) {
            1 -> 218f
            5 -> 342f
            6 -> 264f
            4 -> 697f
            else -> 682f
        }
        val left = if (type == 4) {
            24f
        } else if (type in 2..3) {
            39f
        } else {
            16f
        }
        val right = if (type == 4) {
            216f
        } else if (type in 2..3) {
            177f
        } else {
            234f
        }
        garment(
            c,
            path {
                moveTo(64f, 41f)
                quadTo(160f, 43f, 240f, 67f)
                if (type in 2..4) {
                    cubicTo(241f, 215f, 222f, 356f, 215f, 427f)
                    quadTo(201f, 490f, right + 4f, 536f)
                    lineTo(right, hem)
                    quadTo(112f, hem + 12f, left, hem)
                    lineTo(left - 10f, 529f)
                    quadTo(35f, 463f, 25f, 410f)
                    lineTo(10f, 244f)
                } else {
                    lineTo(right, hem)
                    quadTo(127f, hem + 13f, left, hem)
                    lineTo(7f, minOf(234f, hem - 25f))
                }
                quadTo(-1f, 129f, 64f, 41f)
                close()
            },
            bottoms[type],
            256f
        )
        line(
            c,
            path {
                moveTo(82f, 52f)
                quadTo(63f, 105f, 48f, 166f)
            },
            tone(bottoms[type], .7f),
            2f
        )
        if (type == 1 || type == 6) {
            line(
                c,
                path {
                    moveTo(left + 5f, hem - 10f)
                    quadTo(126f, hem + 2f, right - 5f, hem - 10f)
                },
                trim,
                if (type ==
                    6
                ) {
                    7f
                } else {
                    4f
                }
            )
            if (type ==
                6
            ) {
                line(
                    c,
                    path {
                        moveTo(left + 5f, hem - 3f)
                        quadTo(126f, hem + 9f, right - 5f, hem - 3f)
                    },
                    tops[6],
                    3f
                )
            }
        }
        if (type ==
            2
        ) {
            line(
                c,
                path {
                    moveTo(220f, 89f)
                    quadTo(213f, 330f, 188f, 434f)
                    lineTo(158f, 668f)
                },
                tone(bottoms[type], 1.2f),
                2f
            )
        }
        if (type ==
            3
        ) {
            garment(
                c,
                path {
                    moveTo(39f, 662f)
                    lineTo(177f, 662f)
                    lineTo(177f, 698f)
                    lineTo(39f, 698f)
                    close()
                },
                tone(bottoms[type], .84f),
                256f
            )
        }
        if (type == 4) {
            line(
                c,
                path {
                    moveTo(156f, 92f)
                    lineTo(142f, 681f)
                },
                tone(bottoms[type], .85f),
                2f
            )
        }
        if (type == 5) pocket(c, 112f, 180f, 102f, 125f, bottoms[type])
    }
    private fun pocket(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int) {
        garment(
            c,
            path {
                moveTo(x, y)
                lineTo(x + w, y)
                lineTo(x + w - 3f, y + h)
                lineTo(x + 3f, y + h)
                close()
            },
            tone(color, .95f)
        )
        garment(
            c,
            path {
                moveTo(x, y)
                lineTo(x + w, y)
                lineTo(x + w, y + 20f)
                lineTo(x + w / 2f, y + 25f)
                lineTo(
                    x,
                    y + 20f
                )
                close()
            },
            tone(color, 1.08f)
        )
        fold(
            c,
            path {
                moveTo(x + 3f, y + 24f)
                lineTo(x + w - 3f, y + 24f)
                lineTo(x + w - 7f, y + 36f)
                lineTo(
                    x + 7f,
                    y + 36f
                )
                close()
            },
            34
        )
    }
}
