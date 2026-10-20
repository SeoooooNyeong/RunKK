package com.a207.runkk.app.core.designsystem

/**
 * 성향 태그 고정 목록(SRS 3.5.1, W-13: 7분류 19개)의 화면 문구. 가입·프로필 편집(ui.me)과 크루(ui.crew)가 같이 쓴다.
 * [RunkkTag.id]는 서버 PersonalityTag 상수 이름으로 쓰는 값이고, 10/21 API 계약에서 이름이 다르면 api.* 매퍼가 맞춘다.
 * 고르기 규칙(분류마다 1개, 모두 1~5개)은 서버가 판정한다. 화면은 [RunkkTags.toggle]로 분류마다 1개만 남긴다.
 */
data class RunkkTag(val id: String, val label: String)

data class RunkkTagCategory(val name: String, val tags: List<RunkkTag>, val crewOnly: Boolean = false)

object RunkkTags {
    /** 러너가 고르는 상한(FR-007). */
    const val MAX_SELECTED = 5

    val categories: List<RunkkTagCategory> = listOf(
        RunkkTagCategory("동행", listOf(RunkkTag("SOLO", "혼자가 편해요"), RunkkTag("SOCIAL", "함께가 좋아요"))),
        RunkkTagCategory("거리", listOf(RunkkTag("SHORT_DISTANCE", "짧게 자주"), RunkkTag("LONG_DISTANCE", "길게 천천히"))),
        RunkkTagCategory("코스", listOf(RunkkTag("FAMILIAR_ROUTE", "늘 가던 길"), RunkkTag("NEW_ROUTE", "새로운 길 탐험"))),
        RunkkTagCategory("습관", listOf(RunkkTag("ROUTINE", "정해진 루틴"), RunkkTag("SPONTANEOUS", "그날 기분대로"))),
        RunkkTagCategory(
            "주로 뛰는 시간",
            listOf(
                RunkkTag("DAWN", "새벽"),
                RunkkTag("MORNING", "아침"),
                RunkkTag("LUNCH", "점심"),
                RunkkTag("EVENING", "저녁"),
                RunkkTag("NIGHT", "밤")
            )
        ),
        RunkkTagCategory(
            "목표",
            listOf(
                RunkkTag("RECORD", "기록 단축"),
                RunkkTag("RACE", "대회 준비"),
                RunkkTag("DIET", "다이어트"),
                RunkkTag("STRESS", "스트레스 해소"),
                RunkkTag("STEADY", "꾸준히")
            )
        ),
        RunkkTagCategory("크루 분위기", listOf(RunkkTag("BEGINNER_WELCOME", "초보 환영")), crewOnly = true)
    )

    /** 러너용 6분류. */
    val runnerCategories: List<RunkkTagCategory> = categories.filterNot { it.crewOnly }

    private val byId: Map<String, RunkkTag> = categories.flatMap { it.tags }.associateBy { it.id }

    /** id → 화면 문구. 모르는 id는 그대로 보인다. */
    fun label(id: String): String = byId[id]?.label ?: id

    /**
     * 태그 하나를 켜거나 끈다. 켤 때는 같은 분류의 다른 태그를 끈다(분류마다 1개). 상한을 넘기면 바꾸지 않는다.
     */
    fun toggle(selected: Set<String>, id: String, max: Int = MAX_SELECTED): Set<String> {
        if (id in selected) return selected - id
        val category = categories.firstOrNull { c -> c.tags.any { it.id == id } } ?: return selected
        val withoutSameCategory = selected - category.tags.map { it.id }.toSet()
        if (withoutSameCategory.size >= max) return selected
        return withoutSameCategory + id
    }
}
