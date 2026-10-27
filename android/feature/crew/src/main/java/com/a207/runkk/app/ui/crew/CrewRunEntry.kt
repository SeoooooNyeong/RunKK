package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.domain.crew.CrewRunStatus
import com.a207.runkk.app.domain.crew.LobbyAvailability

/**
 * 모집글 상태로 고른 단추 표시: 투표(갈래요·못 가요), 대기실 단추([lobby], 끝·취소된 글이면 null), 지난 결과.
 * 상태를 모르면 투표·결과를 보이고 대기실은 시각으로만 판단한다.
 */
internal data class CrewRunEntry(val canVote: Boolean, val lobby: LobbyAvailability?, val isFinished: Boolean) {
    companion object {
        fun of(status: CrewRunStatus?, lobby: LobbyAvailability?): CrewRunEntry = CrewRunEntry(
            canVote = status == null || status == CrewRunStatus.VOTING,
            lobby = lobby.takeUnless { status == CrewRunStatus.FINISHED || status == CrewRunStatus.CANCELLED },
            isFinished = status == null || status == CrewRunStatus.FINISHED
        )
    }
}
