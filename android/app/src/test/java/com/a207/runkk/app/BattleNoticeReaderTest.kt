package com.a207.runkk.app

import android.content.Intent
import com.a207.runkk.app.core.model.RunNoticeExtras
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 대결 진행 알림을 눌러 연 실행(S15P31A207-278): 알림 인텐트의 대결 ID를 꺼내고, 읽은 뒤에는 비워 다시 열지 않는다. */
@RunWith(RobolectricTestRunner::class)
class BattleNoticeReaderTest {
    private val notice = Intent(Intent.ACTION_MAIN).putExtra(RunNoticeExtras.BATTLE_ID, 9L)

    @Test
    fun `S15P31A207-278 진행 알림 인텐트에서 대결 ID를 꺼내고 비운 인텐트에서는 꺼내지 않는다`() {
        assertEquals(9L, BattleNoticeReader.read(notice))
        assertNull(BattleNoticeReader.read(BattleNoticeReader.consumed(notice)))
        assertEquals(9L, BattleNoticeReader.read(notice), "원본 인텐트는 바꾸지 않는다")
    }

    @Test
    fun `S15P31A207-278 대결 ID가 없거나 0 이하인 실행은 앱만 연다`() {
        assertNull(BattleNoticeReader.read(Intent(Intent.ACTION_MAIN)))
        assertNull(BattleNoticeReader.read(Intent(Intent.ACTION_MAIN).putExtra(RunNoticeExtras.BATTLE_ID, 0L)))
        assertNull(BattleNoticeReader.read(null))
    }
}
