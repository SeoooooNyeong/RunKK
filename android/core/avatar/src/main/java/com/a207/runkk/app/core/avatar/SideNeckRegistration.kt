package com.a207.runkk.app.core.avatar

/**
 * 내장 샘플 남녀 옆얼굴 전용 목 등록값(PoC SideNeckRegistration). 피부 얼굴 비트맵에만 쓰고 눈·헤어는 이 변환을 거치지 않는다.
 *
 * **새 셀카 얼굴에 재사용하지 않는다.** 이 좌표는 내장 샘플 두 장의 목 길이에 맞춘 값이라 다른 인물에 쓰면 목이 끊기거나 검은 틈이
 * 생긴다. 생성 머리는 [GeneratedAvatarLayers]가 알파에서 턱을 찾아 목을 줄인다.
 */
internal enum class SideNeckRegistration(
    val gender: AvatarGender,
    val startY: Float,
    val endY: Float,
    val centerX: Float
) {
    MALE(AvatarGender.MALE, 858f, 930f, 582f),
    FEMALE(AvatarGender.FEMALE, 792f, 864f, 651f);

    val offsetY: Float get() = (endY - startY) * SHRINK

    /** 이어진 목과 그 가장자리 한 픽셀만 남기고 목 아래 떨어진 얼룩을 지운다. */
    fun cleanNeckPixels(pixels: IntArray): IntArray {
        require(pixels.size == SIZE * SIZE) { "side face must be ${SIZE}x$SIZE" }
        val start = startY.toInt() * SIZE
        val connected = BooleanArray(pixels.size)
        val queue = IntArray(pixels.size - start)
        var tail = 0
        fun enqueue(index: Int) {
            if (index < start || index >= pixels.size || connected[index] || pixels[index] ushr 24 < 128) return
            connected[index] = true
            queue[tail++] = index
        }
        for (x in 0 until SIZE) enqueue(start + x)
        var head = 0
        while (head < tail) {
            val index = queue[head++]
            if (index % SIZE > 0) enqueue(index - 1)
            if (index % SIZE < SIZE - 1) enqueue(index + 1)
            enqueue(index - SIZE)
            enqueue(index + SIZE)
        }
        return pixels.copyOf().also { result ->
            for (index in start until pixels.size) {
                if (!isNearConnected(connected, index)) result[index] = 0
            }
        }
    }

    /**
     * drawBitmapMesh 꼭짓점(1×1024 격자). 원본 한 줄마다 한 꼭짓점 줄이라 정수 목 경계 위 얼굴은 그대로이고 목 구간만
     * [SHRINK]만큼 짧아지며 아래로 갈수록 폭이 0.55배로 좁아진다.
     */
    fun meshVertices(): FloatArray = (0..SIZE).flatMap { row ->
        val y = row.toFloat()
        val t = ((y - startY) / (endY - startY)).coerceIn(0f, 1f)
        val smooth = t * t * (3f - 2f * t)
        val mappedY = y - (y - startY).coerceIn(0f, endY - startY) * SHRINK
        listOf(centerX * SHRINK * smooth, mappedY, SIZE - (SIZE - centerX) * SHRINK * smooth, mappedY)
    }.toFloatArray()

    private fun isNearConnected(connected: BooleanArray, index: Int): Boolean {
        val x = index % SIZE
        val y = index / SIZE
        for (dy in -1..1) {
            for (dx in -1..1) {
                if (x + dx in 0 until SIZE && y + dy in startY.toInt() until SIZE &&
                    connected[(y + dy) * SIZE + x + dx]
                ) {
                    return true
                }
            }
        }
        return false
    }

    companion object {
        private const val SIZE = 1024
        private const val SHRINK = .45f

        /** 내장 샘플 옆얼굴의 등록값. 생성 머리에는 쓰지 않는다. */
        fun builtInFor(gender: AvatarGender): SideNeckRegistration = entries.first { it.gender == gender }
    }
}
