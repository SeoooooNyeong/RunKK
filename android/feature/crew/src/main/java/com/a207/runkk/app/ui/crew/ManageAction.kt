package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.domain.crew.CrewMembers

/** 확인을 받는 관리 행동. */
sealed interface ManageAction {
    data class Kick(val member: CrewMembers.Member) : ManageAction

    data class Transfer(val member: CrewMembers.Member) : ManageAction

    data object Delete : ManageAction

    /** 운영진 지정(FR-204, 와이어프레임 M9). [managerCount]는 지금 운영진 수다. */
    data class Appoint(val member: CrewMembers.Member, val managerCount: Int) : ManageAction
}
