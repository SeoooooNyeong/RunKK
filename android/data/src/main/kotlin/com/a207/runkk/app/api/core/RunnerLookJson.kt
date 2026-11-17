package com.a207.runkk.app.api.core

import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.RunnerLook
import kotlinx.serialization.Serializable

/**
 * 서버 `common.AvatarLook`([임시:D-성-246]). REST 응답(대결 조회·결과·기록 대결 시작·후보·크루런 대기실)과 실시간 MatchFound가 같이 쓴다.
 * [equipped]의 키는 TOP·BOTTOM·SHOES이고 안 입은 부위는 키가 없다. [gender]는 표시 성별(MALE·FEMALE)이다.
 */
@Serializable
internal data class RunnerLookJson(
    val gender: String,
    val equipped: Map<String, Long> = emptyMap(),
    val bodyVariant: Int = 0,
    val runAmount: Int = 0,
    val faceKey: String? = null
) {
    /** 모르는 성별 이름이면 모습 없음(null)으로 본다: 화면이 성별 기본 모습으로 그린다([임시:D-성-278]). */
    fun toDomain(): RunnerLook? {
        val shown = enumByName<Gender>(gender) ?: return null
        return RunnerLook(
            gender = shown,
            topItemId = equipped[TOP],
            bottomItemId = equipped[BOTTOM],
            shoesItemId = equipped[SHOES],
            bodyVariant = bodyVariant,
            runAmount = runAmount,
            faceKey = faceKey?.takeIf { it.isNotBlank() }
        )
    }
}

private const val TOP = "TOP"
private const val BOTTOM = "BOTTOM"
private const val SHOES = "SHOES"
