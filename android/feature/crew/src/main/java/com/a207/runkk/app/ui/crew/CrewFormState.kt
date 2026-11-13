package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.RunkkTags
import com.a207.runkk.app.domain.crew.CrewDetail
import com.a207.runkk.app.domain.crew.CrewForm
import com.a207.runkk.app.domain.crew.CrewVisibility
import com.a207.runkk.app.domain.crew.JoinPolicy

data class CrewFormState(
    val name: String = "",
    val intro: String = "",
    val tags: Set<String> = emptySet(),
    val visibility: CrewVisibility = CrewVisibility.PUBLIC,
    val joinPolicy: JoinPolicy = JoinPolicy.APPROVAL,
    /** 로고 주소(POST /crew-logos 결과). 없으면 기본 이미지(contract-W4 6·8절). */
    val logoUrl: String? = null,
    /** 정보 수정이면 지금 크루명. 이름을 그대로 두면 2~12자 규칙 전에 만든 긴 이름도 통과한다(결정 C1). */
    val originalName: String? = null
) {
    /** 크루명 2~12자(바꾸지 않은 기존 이름은 통과), 태그 1~3개(FR-110, 피그마 F-04, 결정 C1). 최종 판정은 서버가 한다. */
    val isValid: Boolean
        get() {
            val trimmed = name.trim()
            val nameOk = trimmed.length in NAME_MIN..NAME_MAX || (trimmed.isNotEmpty() && trimmed == originalName)
            return nameOk && tags.size in 1..RunkkTags.MAX_SELECTED
        }

    fun toForm() = CrewForm(name.trim(), intro.trim().ifBlank { null }, tags, visibility, joinPolicy, logoUrl)

    companion object {
        const val NAME_MIN = 2

        /** 새로 정하는 크루명 상한(결정 C1, 서버 CrewName.MAX_LENGTH). */
        const val NAME_MAX = 12
        const val INTRO_MAX = 40
    }
}

/** 크루 정보 → 정보 수정 칸. */
internal fun CrewDetail.toFormState(): CrewFormState = CrewFormState(
    name = name,
    intro = intro.orEmpty(),
    tags = tags,
    visibility = visibility,
    joinPolicy = joinPolicy,
    logoUrl = logoUrl,
    originalName = name
)
