package com.a207.runkk.app

import android.content.Intent
import com.a207.runkk.app.core.model.RunNoticeExtras

/**
 * 대결 진행 알림을 눌러 연 실행에서 대결 ID를 꺼낸다(S15P31A207-278). 알림(data.device)이 앱 실행 인텐트에 [RunNoticeExtras.BATTLE_ID]를
 * 싣는다. 자유런 알림이나 앱 아이콘 실행에는 없다. 읽은 값은 [consumed]로 비워 같은 인텐트로 두 번 돌아가지 않게 한다.
 */
internal object BattleNoticeReader {
    /** 대결 ID. 없거나 0 이하이면 null. */
    fun read(intent: Intent?): Long? = intent?.getLongExtra(RunNoticeExtras.BATTLE_ID, 0L)?.takeIf { it > 0 }

    /** 대결 ID를 뺀 인텐트(원본은 바꾸지 않는다). Activity.setIntent로 바꿔 둔다. */
    fun consumed(intent: Intent): Intent = Intent(intent).apply { removeExtra(RunNoticeExtras.BATTLE_ID) }
}
