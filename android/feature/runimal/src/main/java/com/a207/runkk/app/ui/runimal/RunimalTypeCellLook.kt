package com.a207.runkk.app.ui.runimal

/**
 * 16유형 격자(G-5) 칸 한 개의 모양(D94). 내 유형은 진한 테두리(RunimalTileBorder)와 굵은 11.5 글자, 나머지는 옅은 테두리와 보통
 * 10.5 글자다. 확정받은 적 없는 유형은 칸 전체를 0.3으로 흐린다.
 */
internal data class RunimalTypeCellLook(
    val alpha: Float,
    val isStrongBorder: Boolean,
    val isBold: Boolean,
    val fontSizeSp: Float
) {
    companion object {
        fun of(isMine: Boolean, isDimmed: Boolean): RunimalTypeCellLook = RunimalTypeCellLook(
            alpha = if (isDimmed) DIMMED_ALPHA else 1f,
            isStrongBorder = isMine,
            isBold = isMine,
            fontSizeSp = if (isMine) MINE_FONT_SP else OTHER_FONT_SP
        )

        private const val DIMMED_ALPHA = 0.3f
        private const val MINE_FONT_SP = 11.5f
        private const val OTHER_FONT_SP = 10.5f
    }
}
