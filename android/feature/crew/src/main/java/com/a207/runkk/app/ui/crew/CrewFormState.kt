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
    val joinPolicy: JoinPolicy = JoinPolicy.APPROVAL
) {
    /** 크루명 2~20자, 태그 1~5개(FR-110). 최종 판정은 서버가 한다. */
    val isValid: Boolean get() = name.trim().length in NAME_MIN..NAME_MAX && tags.size in 1..RunkkTags.MAX_SELECTED

    fun toForm() = CrewForm(name.trim(), intro.trim().ifBlank { null }, tags, visibility, joinPolicy)

    companion object {
        const val NAME_MIN = 2
        const val NAME_MAX = 20
        const val INTRO_MAX = 40
    }
}

/** 크루 정보 → 정보 수정 칸. */
internal fun CrewDetail.toFormState(): CrewFormState =
    CrewFormState(name = name, intro = intro.orEmpty(), tags = tags, visibility = visibility, joinPolicy = joinPolicy)
