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

/** Selectable shoe constructions fitted to the existing foot rigs. */
internal object FootwearTypes {
    private val ink = Color.rgb(83, 63, 56)
    private val cream = Color.rgb(251, 247, 239)
    private val shadow = Color.rgb(207, 194, 181)
    private val navy = Color.rgb(74, 103, 139)
    private val teal = Color.rgb(91, 142, 139)
    private val leather = Color.rgb(155, 104, 69)

    @Suppress("UNUSED_PARAMETER")
    fun apply(assetName: String, skin: Bitmap, clothing: Bitmap, type: Int) {
        require(type in 0..6)
        if (type == 0) return
        val canvas = Canvas(clothing)
        when (assetName) {
            "standing_body" -> {
                clear(canvas, 96f, 627f, 257f, 768f)
                clear(canvas, 260f, 627f, 421f, 768f)
                for (left in listOf(105f, 266f)) {
                    val save = canvas.save()
                    canvas.translate(left, 600f)
                    front(canvas, type)
                    canvas.restoreToCount(save)
                }
            }

            "side_shoe" -> {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                side(canvas, type)
            }
        }
    }

    private fun front(c: Canvas, type: Int) {
        if (type == 1 || type == 3 || type == 4 || type == 6) {
            shape(
                c,
                Path().apply {
                    moveTo(29f, 31f)
                    quadTo(75f, 42f, 122f, 31f)
                    lineTo(124f, 99f)
                    quadTo(74f, 113f, 26f, 99f)
                    close()
                },
                cream,
                Color.rgb(226, 218, 210),
                31f,
                104f,
                2.5f
            )
            for (x in 37..115 step 13) line(c, x.toFloat(), 40f, x - 2f, 68f, Color.rgb(219, 209, 200), 1.4f)
        }
        when (type) {
            1, 6 -> {
                // A low canvas collar, narrow sole and exposed ribbed sock.
                shape(
                    c,
                    Path().apply {
                        moveTo(29f, 91f)
                        quadTo(75f, 77f, 121f, 91f)
                        lineTo(138f, 128f)
                        quadTo(151f, 147f, 136f, 153f)
                        quadTo(73f, 165f, 13f, 153f)
                        quadTo(1f, 145f, 14f, 125f)
                        close()
                    },
                    if (type == 6) Color.rgb(248, 160, 146) else Color.rgb(194, 112, 101),
                    if (type == 6) Color.rgb(215, 117, 104) else Color.rgb(157, 79, 73),
                    83f,
                    155f
                )
                shape(
                    c,
                    Path().apply {
                        moveTo(22f, 123f)
                        quadTo(75f, 108f, 128f, 123f)
                        lineTo(138f, 145f)
                        quadTo(75f, 158f, 12f, 145f)
                        close()
                    },
                    cream,
                    Color.rgb(232, 224, 216),
                    117f,
                    153f,
                    2f
                )
                frontSole(c, 148f, 160f, cream, 2.5f)
                laces(c, 52f, 97f, 48f, 3, 8f)
            }

            2 -> {
                // Tall ankle quarters and a long separate tongue.
                shape(
                    c,
                    Path().apply {
                        moveTo(29f, 14f)
                        quadTo(75f, 3f, 120f, 14f)
                        lineTo(124f, 91f)
                        quadTo(145f, 113f, 145f, 147f)
                        quadTo(75f, 165f, 5f, 147f)
                        quadTo(6f, 113f, 25f, 91f)
                        close()
                    },
                    Color.rgb(109, 139, 173),
                    navy,
                    13f,
                    150f
                )
                shape(
                    c,
                    Path().apply {
                        moveTo(54f, 18f)
                        quadTo(75f, 11f, 97f, 18f)
                        lineTo(108f, 120f)
                        quadTo(75f, 132f, 42f, 120f)
                        close()
                    },
                    Color.rgb(157, 181, 200),
                    navy,
                    18f,
                    125f,
                    2f
                )
                line(c, 32f, 29f, 116f, 29f, cream, 4f)
                laces(c, 49f, 44f, 52f, 7, 11f)
                frontSole(c, 145f, 161f, cream, 3f)
            }

            3 -> {
                // Angular toe armour, layered side panels and a thick lugged sole.
                shape(
                    c,
                    Path().apply {
                        moveTo(24f, 83f)
                        lineTo(48f, 71f)
                        lineTo(105f, 71f)
                        lineTo(128f, 86f)
                        lineTo(143f, 124f)
                        lineTo(148f, 148f)
                        lineTo(126f, 160f)
                        lineTo(23f, 160f)
                        lineTo(2f, 147f)
                        lineTo(10f, 117f)
                        close()
                    },
                    Color.rgb(145, 180, 164),
                    Color.rgb(68, 117, 112),
                    76f,
                    154f
                )
                polygon(c, floatArrayOf(12f, 116f, 34f, 91f, 48f, 103f, 34f, 132f), Color.rgb(218, 157, 78))
                polygon(c, floatArrayOf(138f, 116f, 116f, 91f, 102f, 103f, 116f, 132f), Color.rgb(218, 157, 78))
                shape(
                    c,
                    Path().apply {
                        moveTo(17f, 126f)
                        lineTo(42f, 113f)
                        lineTo(108f, 113f)
                        lineTo(133f, 126f)
                        lineTo(141f, 145f)
                        lineTo(9f, 145f)
                        close()
                    },
                    Color.rgb(93, 112, 111),
                    Color.rgb(65, 81, 83),
                    119f,
                    145f,
                    2f
                )
                frontSole(c, 144f, 164f, Color.rgb(226, 217, 196), 3f)
                for (x in 15..135 step 24) line(c, x.toFloat(), 155f, x + 2f, 164f, ink, 5f)
                laces(c, 51f, 85f, 48f, 3, 10f)
            }

            4 -> {
                // Laceless curved vamp with elastic gores, deliberately no tongue.
                shape(
                    c,
                    Path().apply {
                        moveTo(28f, 94f)
                        quadTo(75f, 75f, 122f, 94f)
                        quadTo(137f, 112f, 144f, 145f)
                        quadTo(75f, 164f, 6f, 145f)
                        quadTo(13f, 112f, 28f, 94f)
                        close()
                    },
                    Color.rgb(220, 192, 150),
                    Color.rgb(181, 142, 96),
                    90f,
                    151f
                )
                shape(
                    c,
                    Path().apply {
                        moveTo(35f, 100f)
                        quadTo(75f, 84f, 115f, 100f)
                        quadTo(124f, 121f, 124f, 139f)
                        quadTo(75f, 149f, 26f, 139f)
                        quadTo(26f, 121f, 35f, 100f)
                        close()
                    },
                    Color.rgb(235, 213, 177),
                    Color.rgb(205, 175, 132),
                    97f,
                    144f,
                    1.8f
                )
                polygon(c, floatArrayOf(27f, 96f, 40f, 93f, 35f, 113f, 23f, 114f), Color.rgb(94, 85, 75))
                polygon(c, floatArrayOf(123f, 96f, 110f, 93f, 115f, 113f, 127f, 114f), Color.rgb(94, 85, 75))
                frontSole(c, 146f, 160f, cream, 2.5f)
            }

            5 -> {
                // High leather shaft, stitched toe cap and a dark substantial sole.
                shape(
                    c,
                    Path().apply {
                        moveTo(26f, 3f)
                        quadTo(75f, -1f, 124f, 3f)
                        lineTo(126f, 94f)
                        quadTo(147f, 113f, 146f, 149f)
                        quadTo(75f, 163f, 4f, 149f)
                        quadTo(3f, 113f, 24f, 94f)
                        close()
                    },
                    Color.rgb(181, 134, 91),
                    leather,
                    4f,
                    150f
                )
                shape(
                    c,
                    Path().apply {
                        moveTo(26f, 4f)
                        quadTo(75f, 0f, 124f, 4f)
                        lineTo(123f, 24f)
                        quadTo(75f, 31f, 27f, 24f)
                        close()
                    },
                    Color.rgb(115, 80, 58),
                    Color.rgb(89, 62, 49),
                    2f,
                    28f,
                    2f
                )
                line(c, 44f, 35f, 39f, 114f, Color.rgb(223, 182, 128), 2f)
                line(c, 106f, 35f, 111f, 114f, Color.rgb(223, 182, 128), 2f)
                laces(c, 51f, 43f, 48f, 6, 12f)
                curve(c, 16f, 129f, 75f, 110f, 134f, 129f, Color.rgb(224, 182, 128), 2f)
                frontSole(c, 145f, 165f, Color.rgb(78, 65, 56), 3f)
                for (x in 14..136 step 23) line(c, x.toFloat(), 156f, x.toFloat(), 164f, Color.rgb(46, 39, 35), 4f)
            }
        }
    }

    private fun side(c: Canvas, type: Int) {
        if (type == 1 || type == 3 || type == 4 || type == 6) {
            shape(
                c,
                Path().apply {
                    moveTo(28f, 2f)
                    quadTo(76f, 12f, 129f, 2f)
                    lineTo(132f, 66f)
                    quadTo(77f, 79f, 24f, 65f)
                    close()
                },
                cream,
                Color.rgb(228, 220, 211),
                2f,
                70f,
                2.5f
            )
        }
        val upper = when (type) {
            1, 6 -> Path().apply {
                moveTo(20f, 70f)
                lineTo(92f, 68f)
                quadTo(119f, 45f, 143f, 57f)
                lineTo(198f, 94f)
                quadTo(242f, 99f, 248f, 132f)
                lineTo(242f, 144f)
                lineTo(9f, 143f)
                lineTo(7f, 105f)
                close()
            }

            2 -> Path().apply {
                moveTo(28f, 2f)
                lineTo(128f, 2f)
                lineTo(132f, 60f)
                lineTo(195f, 97f)
                quadTo(243f, 99f, 249f, 132f)
                lineTo(242f, 145f)
                lineTo(9f, 144f)
                lineTo(10f, 91f)
                lineTo(25f, 69f)
                close()
            }

            3 -> Path().apply {
                moveTo(16f, 69f)
                lineTo(92f, 61f)
                lineTo(123f, 39f)
                lineTo(151f, 48f)
                lineTo(198f, 84f)
                lineTo(234f, 98f)
                lineTo(251f, 128f)
                lineTo(244f, 145f)
                lineTo(5f, 145f)
                lineTo(4f, 104f)
                close()
            }

            4 -> Path().apply {
                moveTo(18f, 78f)
                quadTo(71f, 91f, 112f, 65f)
                quadTo(146f, 57f, 177f, 87f)
                quadTo(238f, 93f, 248f, 132f)
                lineTo(240f, 145f)
                lineTo(9f, 143f)
                lineTo(8f, 106f)
                close()
            }

            else -> Path().apply {
                moveTo(24f, 0f)
                lineTo(132f, 0f)
                lineTo(135f, 78f)
                quadTo(160f, 86f, 188f, 98f)
                quadTo(242f, 98f, 250f, 131f)
                lineTo(244f, 146f)
                lineTo(8f, 144f)
                lineTo(8f, 96f)
                lineTo(23f, 78f)
                close()
            }
        }
        val top = when (type) {
            1 -> Color.rgb(194, 112, 101)
            2 -> Color.rgb(126, 155, 186)
            3 -> Color.rgb(145, 180, 164)
            4 -> Color.rgb(229, 203, 161)
            6 -> Color.rgb(248, 160, 146)
            else -> Color.rgb(183, 136, 91)
        }
        val low = when (type) {
            1 -> Color.rgb(157, 79, 73)
            2 -> navy
            3 -> teal
            4 -> Color.rgb(180, 143, 97)
            6 -> Color.rgb(215, 117, 104)
            else -> leather
        }
        shape(c, upper, top, low, 40f, 142f)
        when (type) {
            1, 6 -> {
                curve(c, 16f, 88f, 68f, 78f, 90f, 123f, cream, 3f)
                shape(
                    c,
                    Path().apply {
                        moveTo(184f, 99f)
                        quadTo(235f, 99f, 246f, 133f)
                        lineTo(235f, 143f)
                        lineTo(169f, 140f)
                        close()
                    },
                    cream,
                    Color.rgb(225, 216, 206),
                    100f,
                    144f,
                    2f
                )
                sideLaces(c, 113f, 69f, 4)
            }

            2 -> {
                line(c, 31f, 16f, 122f, 16f, cream, 4f)
                shape(
                    c,
                    Path().apply {
                        moveTo(92f, 24f)
                        lineTo(123f, 24f)
                        lineTo(131f, 60f)
                        lineTo(177f, 96f)
                        lineTo(161f, 111f)
                        lineTo(96f, 67f)
                        close()
                    },
                    Color.rgb(171, 191, 207),
                    navy,
                    25f,
                    110f,
                    2f
                )
                for (y in 32..64 step 11) line(c, 100f, y.toFloat(), 122f, y + 3f, cream, 3.5f)
                sideLaces(c, 127f, 75f, 3)
                c.drawCircle(58f, 61f, 16f, paint(cream))
                c.drawCircle(58f, 61f, 8f, paint(navy))
            }

            3 -> {
                polygon(c, floatArrayOf(17f, 84f, 65f, 79f, 93f, 119f, 44f, 132f), Color.rgb(216, 157, 78))
                polygon(
                    c,
                    floatArrayOf(96f, 78f, 123f, 64f, 178f, 101f, 156f, 129f, 113f, 119f),
                    Color.rgb(73, 112, 113)
                )
                polygon(c, floatArrayOf(194f, 101f, 233f, 110f, 246f, 132f, 176f, 136f), Color.rgb(70, 85, 87))
                sideLaces(c, 125f, 66f, 4)
            }

            4 -> {
                curve(c, 17f, 91f, 71f, 106f, 115f, 77f, Color.rgb(119, 93, 69), 2.5f)
                polygon(c, floatArrayOf(100f, 79f, 116f, 70f, 125f, 88f, 108f, 99f), Color.rgb(87, 80, 69))
                curve(c, 140f, 96f, 174f, 107f, 179f, 137f, Color.rgb(246, 226, 192), 2f)
            }

            5 -> {
                shape(
                    c,
                    Path().apply {
                        moveTo(24f, 1f)
                        lineTo(132f, 1f)
                        lineTo(132f, 21f)
                        quadTo(78f, 29f, 24f, 20f)
                        close()
                    },
                    Color.rgb(112, 80, 59),
                    Color.rgb(86, 61, 47),
                    0f,
                    25f,
                    2f
                )
                line(c, 43f, 34f, 42f, 118f, Color.rgb(226, 184, 131), 2f)
                line(c, 111f, 31f, 114f, 91f, Color.rgb(226, 184, 131), 2f)
                for (y in 38..83 step 12) line(c, 79f, y.toFloat(), 108f, y + 1f, cream, 3f)
                curve(c, 180f, 112f, 203f, 118f, 210f, 138f, Color.rgb(225, 182, 128), 2f)
            }
        }
        val soleColor = if (type == 5) {
            Color.rgb(78, 65, 56)
        } else if (type == 3) {
            Color.rgb(225, 216, 195)
        } else {
            cream
        }
        shape(
            c,
            Path().apply {
                moveTo(8f, 132f)
                quadTo(125f, 144f, 247f, 132f)
                lineTo(247f, 150f)
                quadTo(126f, 165f, 8f, 152f)
                close()
            },
            soleColor,
            if (type ==
                5
            ) {
                Color.rgb(51, 44, 40)
            } else {
                shadow
            },
            132f,
            158f,
            2.7f
        )
        if (type == 3 || type == 5) for (x in 18..235 step 27) line(c, x.toFloat(), 149f, x + 1f, 157f, ink, 5f)
    }

    private fun frontSole(c: Canvas, y: Float, bottom: Float, color: Int, width: Float) = shape(
        c,
        Path().apply {
            moveTo(7f, y)
            quadTo(75f, y + 11f, 143f, y)
            lineTo(143f, bottom - 3f)
            quadTo(
                75f,
                bottom + 4f,
                7f,
                bottom - 3f
            )
            close()
        },
        color,
        if (color == cream) shadow else color,
        y,
        bottom,
        width
    )

    private fun laces(c: Canvas, x: Float, y: Float, width: Float, count: Int, step: Float) {
        for (i in 0 until count) {
            val py = y + i * step
            line(c, x, py, x + width, py + 3f, ink, 6f)
            line(c, x, py, x + width, py + 3f, cream, 3.5f)
        }
    }
    private fun sideLaces(c: Canvas, x: Float, y: Float, count: Int) {
        for (i in 0 until count) line(c, x + i * 13f, y + i * 9f, x + 27f + i * 13f, y - 3f + i * 9f, cream, 4f)
    }
    private fun polygon(c: Canvas, points: FloatArray, color: Int) {
        shape(
            c,
            Path().apply {
                moveTo(points[0], points[1])
                for (i in 2 until points.size step 2) {
                    lineTo(
                        points[i],
                        points[
                            i +
                                1
                        ]
                    )
                }
                close()
            },
            color,
            color,
            0f,
            1f,
            2f
        )
    }
    private fun curve(
        c: Canvas,
        x: Float,
        y: Float,
        cx: Float,
        cy: Float,
        ex: Float,
        ey: Float,
        color: Int,
        width: Float
    ) {
        c.drawPath(
            Path().apply {
                moveTo(x, y)
                quadTo(cx, cy, ex, ey)
            },
            paint(color, Paint.Style.STROKE, width)
        )
    }
    private fun line(c: Canvas, x: Float, y: Float, ex: Float, ey: Float, color: Int, width: Float) =
        c.drawLine(x, y, ex, ey, paint(color, Paint.Style.STROKE, width))
    private fun shape(c: Canvas, path: Path, top: Int, bottom: Int, y: Float, endY: Float, width: Float = 3f) {
        val fill = paint(top).apply { shader = LinearGradient(0f, y, 0f, endY, top, bottom, Shader.TileMode.CLAMP) }
        c.drawPath(path, fill)
        c.drawPath(path, paint(ink, Paint.Style.STROKE, width))
    }
    private fun paint(color: Int, style: Paint.Style = Paint.Style.FILL, width: Float = 1f) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.style = style
            strokeWidth = width
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
    private fun clear(c: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        c.drawRect(
            RectF(left, top, right, bottom),
            Paint().apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
        )
    }
}
