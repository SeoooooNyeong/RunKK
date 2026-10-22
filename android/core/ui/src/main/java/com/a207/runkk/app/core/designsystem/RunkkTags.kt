package com.a207.runkk.app.core.designsystem

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * 성향 태그 고정 목록(SRS 3.5.1, W-13: 7분류 19개)의 화면 문구. 가입·프로필 편집(ui.me)과 크루(ui.crew)가 같이 쓴다.
 * [RunkkTag.id]는 서버 PersonalityTag 상수 이름이다(서버로 가는 값). 계약 시험(ContractEnumsTest)이 서버 이름 목록과 대조한다.
 * 고르기 규칙(분류마다 1개, 모두 1~5개)은 서버가 판정한다. 화면은 [RunkkTags.toggle]로 분류마다 1개만 남긴다.
 */
data class RunkkTag(val id: String, @StringRes val labelRes: Int)

data class RunkkTagCategory(@StringRes val nameRes: Int, val tags: List<RunkkTag>, val isCrewOnly: Boolean = false)

object RunkkTags {
    /** 러너가 고르는 상한(FR-007). */
    const val MAX_SELECTED = 5

    val categories: List<RunkkTagCategory> = listOf(
        RunkkTagCategory(
            R.string.core_tag_category_companion,
            listOf(
                RunkkTag("SOLO_RUN", R.string.core_tag_solo_run),
                RunkkTag("SOCIAL_RUN", R.string.core_tag_social_run)
            )
        ),
        RunkkTagCategory(
            R.string.core_tag_category_distance,
            listOf(
                RunkkTag("SHORT_DISTANCE", R.string.core_tag_short_distance),
                RunkkTag("LONG_DISTANCE", R.string.core_tag_long_distance)
            )
        ),
        RunkkTagCategory(
            R.string.core_tag_category_course,
            listOf(
                RunkkTag("FAMILIAR_ROUTE", R.string.core_tag_familiar_route),
                RunkkTag("EXPLORER_ROUTE", R.string.core_tag_explorer_route)
            )
        ),
        RunkkTagCategory(
            R.string.core_tag_category_habit,
            listOf(
                RunkkTag("ROUTINE_RUN", R.string.core_tag_routine_run),
                RunkkTag("IMPROMPTU_RUN", R.string.core_tag_impromptu_run)
            )
        ),
        RunkkTagCategory(
            R.string.core_tag_category_time,
            listOf(
                RunkkTag("DAWN", R.string.core_tag_dawn),
                RunkkTag("MORNING", R.string.core_tag_morning),
                RunkkTag("LUNCH", R.string.core_tag_lunch),
                RunkkTag("EVENING", R.string.core_tag_evening),
                RunkkTag("NIGHT", R.string.core_tag_night)
            )
        ),
        RunkkTagCategory(
            R.string.core_tag_category_goal,
            listOf(
                RunkkTag("PERSONAL_BEST", R.string.core_tag_personal_best),
                RunkkTag("RACE_PREP", R.string.core_tag_race_prep),
                RunkkTag("DIET", R.string.core_tag_diet),
                RunkkTag("STRESS_RELIEF", R.string.core_tag_stress_relief),
                RunkkTag("CONSISTENCY", R.string.core_tag_consistency)
            )
        ),
        RunkkTagCategory(
            R.string.core_tag_category_crew_mood,
            listOf(RunkkTag("BEGINNER_WELCOME", R.string.core_tag_beginner_welcome)),
            isCrewOnly = true
        )
    )

    /** 러너용 6분류. */
    val runnerCategories: List<RunkkTagCategory> = categories.filterNot { it.isCrewOnly }

    private val byId: Map<String, RunkkTag> = categories.flatMap { it.tags }.associateBy { it.id }

    /** id → 화면 문구. 모르는 id는 그대로 보인다. */
    fun label(res: Resources, id: String): String = byId[id]?.let { res.getString(it.labelRes) } ?: id

    /** [label]을 화면에서 쓴다. */
    @Composable
    fun label(id: String): String = label(LocalContext.current.resources, id)

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
