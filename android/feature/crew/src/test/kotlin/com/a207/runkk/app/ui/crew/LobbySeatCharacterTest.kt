package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.domain.crew.Lobby
import org.junit.Assert.assertEquals
import org.junit.Test

class LobbySeatCharacterTest {
    @Test
    fun `내 자리는 표시 성별이 달라도 내 성별로 그린다`() {
        val me = Lobby.Participant("나", false, 1, isMe = true, shownGender = Gender.FEMALE)
        assertEquals(AvatarCharacter.MALE, lobbySeatCharacter(me, AvatarCharacter.MALE))
    }

    @Test
    fun `다른 사람 자리는 표시 성별 그대로다`() {
        val other = Lobby.Participant("달빛", false, 2, shownGender = Gender.FEMALE)
        assertEquals(AvatarCharacter.FEMALE, lobbySeatCharacter(other, AvatarCharacter.MALE))
    }
}
