package com.a207.runkk.app.ui.share

import android.graphics.Bitmap

/** 화면에 그린 카드를 그대로 찍은 이미지와 그때의 테마(어두운 벌인가). [ShareImageFramer]가 같은 테마 바탕에 워드마크를 붙인다. */
class CapturedCard(val bitmap: Bitmap, val isDark: Boolean)
