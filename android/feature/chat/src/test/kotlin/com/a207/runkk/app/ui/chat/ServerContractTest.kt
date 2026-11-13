package com.a207.runkk.app.ui.chat

import com.a207.runkk.app.core.designsystem.testing.ContractEnums
import com.a207.runkk.app.domain.chat.ChatAuthorRole
import com.a207.runkk.app.domain.chat.ChatMessageKind
import com.a207.runkk.app.domain.chat.ReactionKind
import com.a207.runkk.app.domain.report.ReportReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 채팅 화면의 사유 코드·리액션·신고 사유를 서버 enum과 대조한다(F.2.3, F.2.4). */
class ServerContractTest {
    @Test
    fun `채팅 오류 문구의 사유 코드는 서버 ReasonCode이거나 알려진 앱 코드다`() {
        val unknown = ChatErrorText.REJECTED.keys - ContractEnums.knownReasonCodes
        assertTrue(unknown.isEmpty(), "서버에 없는 사유 코드: $unknown")
    }

    @Test
    fun `리액션 종류와 신고 사유는 서버 enum과 같다`() {
        assertEquals(ContractEnums.names("ReactionKind"), ReactionKind.entries.map { it.name }.toSet())
        assertEquals(ContractEnums.names("ReportReason"), ReportReason.entries.map { it.name }.toSet())
        assertEquals(ContractEnums.names("ChatMessageKind"), ChatMessageKind.entries.map { it.name }.toSet())
        assertEquals(ContractEnums.names("CrewRole"), ChatAuthorRole.entries.map { it.name }.toSet())
    }
}
