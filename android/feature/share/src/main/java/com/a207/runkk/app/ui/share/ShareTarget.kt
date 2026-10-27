package com.a207.runkk.app.ui.share

import android.content.Context
import android.net.Uri

/** 카드 이미지 하나를 한 곳에 공유한다(6.1.14, C-012). [image]는 FileProvider content 주소. */
interface ShareTarget {
    fun share(context: Context, image: Uri): ShareOutcome
}
