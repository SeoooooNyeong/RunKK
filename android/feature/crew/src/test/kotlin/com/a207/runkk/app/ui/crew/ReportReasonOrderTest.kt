package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.ReportReasonLabels
import com.a207.runkk.app.domain.report.ReportReason
import kotlin.test.Test
import kotlin.test.assertEquals

/** 신고 사유 라벨은 core.ui가 인덱스로 찾으므로 domain 사유 순서가 같아야 한다(D-186, AN5-D-9). */
class ReportReasonOrderTest {
    @Test
    fun `domain 신고 사유 순서는 core·ui 사유 라벨 순서와 같다`() {
        assertEquals(ReportReasonLabels.ORDER, ReportReason.entries.map { it.name })
    }
}
