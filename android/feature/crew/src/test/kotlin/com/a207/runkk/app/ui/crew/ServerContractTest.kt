package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.testing.ContractEnums
import com.a207.runkk.app.domain.crew.CrewVisibility
import com.a207.runkk.app.domain.crew.JoinPolicy
import com.a207.runkk.app.domain.crew.VoteAnswer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 크루 값(공개 여부·가입 방식·투표 응답·크루런 거리·사유 코드)을 서버 enum·표준 거리 목록과 대조한다. */
class ServerContractTest {
    @Test
    fun `크루 공개 여부·가입 방식·투표 응답은 서버 enum과 같다`() {
        assertEquals(ContractEnums.names("CrewVisibility"), CrewVisibility.entries.map { it.name }.toSet())
        assertEquals(ContractEnums.names("JoinPolicy"), JoinPolicy.entries.map { it.name }.toSet())
        assertEquals(ContractEnums.names("VoteAnswer"), VoteAnswer.entries.map { it.name }.toSet())
    }

    @Test
    fun `크루런 거리는 서버가 받는 크루런 표준 거리와 같다`() {
        val server = ContractEnums.lines("StandardDistance.txt")
            .map { it.split(' ') }
            .filter { "CREW_RUN" in it[2].split(',') }
            .map { it[1].toLong() }
            .toSet()
        assertEquals(server, CREW_RUN_DISTANCES.map { it.meters }.toSet())
    }

    @Test
    fun `크루 오류 문구의 사유 코드는 서버 ReasonCode이거나 알려진 앱 코드다`() {
        val unknown = CrewErrorText.REJECTED.keys - ContractEnums.knownReasonCodes
        assertTrue(unknown.isEmpty(), "서버에 없는 사유 코드: $unknown")
    }

    @Test
    fun `크루 만들기 태그는 서버 PersonalityTag이고 크루 분위기를 포함한다`() {
        val ids = CREW_TAG_CATEGORIES.flatMap { it.tags }.map { it.id }.toSet()
        assertEquals(ContractEnums.names("PersonalityTag"), ids)
    }
}
