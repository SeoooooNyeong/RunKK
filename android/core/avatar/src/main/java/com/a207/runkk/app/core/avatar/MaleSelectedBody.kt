package com.a207.runkk.app.core.avatar

/** 남자 선택 몸 원본(1122×1402, Rive 파일 내장) 좌표 전용 피부 영역. 선택한 체형을 보존하려고 몸 범위 밖은 칠하지 않는다. */
internal object MaleSelectedBody {
    const val ASSET = "male_selected_body"
    const val WIDTH = 1122
    const val HEIGHT = 1402

    fun isSkin(x: Int, y: Int, pixel: Int): Boolean {
        if (pixel ushr 24 == 0 || x !in 0 until WIDTH || y !in 708..1179) return false
        val r = pixel ushr 16 and 255
        val g = pixel ushr 8 and 255
        val b = pixel and 255
        // The RGB source includes a warm cream background. Its near-neutral
        // chroma must not be classified as skin at the clipped silhouette edge.
        if (r < 175 || g < 95 || b < 65 || r - g < 12 || g - b < 3 || r - g < g - b + 3) return false
        return when (y) {
            in 708..734 -> inside(x, y, neck)
            in 831..934 -> inside(x, y, leftArm) || inside(x, y, rightArm)
            in 1041..1179 -> inside(x, y, leftLeg) || inside(x, y, rightLeg)
            else -> false
        }
    }

    private val neck = intArrayOf(
        527, 709, 607, 708, 603, 721, 589, 731, 569, 734, 546, 729, 532, 721
    )
    private val leftArm = intArrayOf(
        424, 831, 477, 845, 476, 855, 474, 865, 472, 875, 469, 885,
        464, 895, 459, 905, 453, 915, 446, 934,
        386, 934, 391, 914, 400, 880, 414, 847
    )

    // Follow the inner forearm curves rather than cutting across them with a
    // straight chord. A color-only expansion would also tint pink shirt folds.
    // One source-pixel margin covers edge samples between the traced points;
    // the unchanged chroma test still excludes the dark outline and background.
    private val rightArm = intArrayOf(
        654, 845, 706, 832, 719, 855, 731, 889, 746, 934, 688, 934,
        679, 915, 672, 905, 667, 895, 663, 885, 660, 875, 657, 865, 655, 855
    )
    private val leftLeg = intArrayOf(
        459, 1041, 557, 1047, 550, 1092, 548, 1148, 544, 1179, 465, 1179, 463, 1135, 464, 1095
    )
    private val rightLeg = intArrayOf(
        575, 1046, 666, 1042, 665, 1082, 669, 1130, 664, 1179, 584, 1179, 580, 1130
    )

    private fun inside(x: Int, y: Int, polygon: IntArray): Boolean {
        var included = false
        var previous = polygon.size - 2
        for (current in polygon.indices step 2) {
            val xi = polygon[current]
            val yi = polygon[current + 1]
            val xj = polygon[previous]
            val yj = polygon[previous + 1]
            if ((yi > y) != (yj > y) &&
                x < (xj - xi).toDouble() * (y - yi) / (yj - yi) + xi
            ) {
                included = !included
            }
            previous = current
        }
        return included
    }
}
