package com.a207.runkk.app

import android.content.Intent

/**
 * 셀카 얼굴 결과 알림을 눌러 연 실행에서 작업 ID를 꺼낸다(design-oo 05-30 [임시:D-성-120], [임시:D-성-146]). 알림(data.push
 * SelfieNoticeTracker)이 앱 실행 인텐트에 [EXTRA_JOB_ID]를 싣는다. 읽은 값은 [consumed]로 비워 같은 인텐트로 두 번 열지 않는다.
 * app은 domain.avatar를 모르므로 키는 domain.avatar SelfieResultNotifier.EXTRA_JOB_ID와 같은 값을 둔다(시험이 같음을 본다).
 */
internal object SelfieNoticeReader {
    const val EXTRA_JOB_ID = "com.a207.runkk.extra.SELFIE_JOB_ID"

    /** 작업 ID. 없거나 비었으면 null. */
    fun read(intent: Intent?): String? = intent?.getStringExtra(EXTRA_JOB_ID)?.takeIf { it.isNotBlank() }

    /** 작업 ID를 뺀 인텐트(원본은 바꾸지 않는다). */
    fun consumed(intent: Intent): Intent = Intent(intent).apply { removeExtra(EXTRA_JOB_ID) }
}
