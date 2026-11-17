package com.a207.runkk.app

import android.content.Intent
import com.a207.runkk.app.core.model.SelfieNoticeIntent

/**
 * 셀카 얼굴 결과 알림을 눌러 연 실행인가(design-oo 05-30 D-752, D-792). 알림(data.push SelfieNoticeTracker)이 앱 안
 * 전용 동작 [SelfieNoticeIntent.ACTION_OPEN_RESULT]로 실행 화면을 연다. 동작만 보고 다른 값(엑스트라)은 믿지 않는다. 읽은 실행은
 * [consumed]로 바꿔 같은 인텐트로 두 번 열지 않게 한다.
 */
internal object SelfieNoticeReader {
    fun read(intent: Intent?): Boolean = intent?.action == SelfieNoticeIntent.ACTION_OPEN_RESULT

    /** 동작을 앱 실행(MAIN)으로 바꾼 인텐트(원본은 바꾸지 않는다). Activity.setIntent로 바꿔 둔다. */
    fun consumed(intent: Intent): Intent = Intent(intent).setAction(Intent.ACTION_MAIN)
}
