package com.a207.runkk.app.core.navigation

/**
 * 달리는 아바타 칸(SlotKey.RUN_AVATAR)에 그릴 사람([임시:D-성-259]). [isMine]이면 내 아바타 그대로다. 다른 러너는 [look](받은 모습)으로
 * 그리고, 모습을 받지 못했으면(null) 표시 성별 [isFemale](모르면 null: 남자)의 기본 모습으로 그린다([임시:D-성-253]).
 */
data class RunAvatarSubject(val isMine: Boolean, val look: SlotRunnerLook?, val isFemale: Boolean?) {
    companion object {
        /** 내 아바타(기본 대상). */
        val Mine = RunAvatarSubject(isMine = true, look = null, isFemale = null)

        /** 다른 러너: 받은 모습 [look], 없으면 표시 성별 [isFemale]의 기본 모습. */
        fun other(look: SlotRunnerLook?, isFemale: Boolean?): RunAvatarSubject =
            RunAvatarSubject(isMine = false, look = look, isFemale = look?.isFemale ?: isFemale)
    }
}
