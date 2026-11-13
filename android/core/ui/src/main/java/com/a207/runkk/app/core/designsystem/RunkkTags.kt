package com.a207.runkk.app.core.designsystem

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** 크루 태그 고정 목록(서버 common.CrewTag): 평일·주말 × 아침·점심·저녁·새벽 8개, 크루마다 최대 3개(피그마 F-01b·F-04). */
object RunkkTags {
    /** 크루가 고르는 상한(피그마 F-04 "태그 최대 3개"). 크루 찾기 조건도 같은 상한을 쓴다. */
    const val MAX_SELECTED = 3

    val categories: List<RunkkTagCategory> = listOf(
        RunkkTagCategory(
            R.string.core_tag_category_weekday,
            listOf(
                RunkkTag("WEEKDAY_MORNING", R.string.core_tag_weekday_morning, R.string.core_tag_slot_morning),
                RunkkTag("WEEKDAY_LUNCH", R.string.core_tag_weekday_lunch, R.string.core_tag_slot_lunch),
                RunkkTag("WEEKDAY_EVENING", R.string.core_tag_weekday_evening, R.string.core_tag_slot_evening),
                RunkkTag("WEEKDAY_DAWN", R.string.core_tag_weekday_dawn, R.string.core_tag_slot_dawn)
            )
        ),
        RunkkTagCategory(
            R.string.core_tag_category_weekend,
            listOf(
                RunkkTag("WEEKEND_MORNING", R.string.core_tag_weekend_morning, R.string.core_tag_slot_morning),
                RunkkTag("WEEKEND_LUNCH", R.string.core_tag_weekend_lunch, R.string.core_tag_slot_lunch),
                RunkkTag("WEEKEND_EVENING", R.string.core_tag_weekend_evening, R.string.core_tag_slot_evening),
                RunkkTag("WEEKEND_DAWN", R.string.core_tag_weekend_dawn, R.string.core_tag_slot_dawn)
            )
        )
    )

    private val all: List<RunkkTag> = categories.flatMap { it.tags }
    private val byId: Map<String, RunkkTag> = all.associateBy { it.id }

    /** id의 태그(모르는 id면 null). */
    fun of(id: String): RunkkTag? = byId[id]

    /** 고정 목록 순서(평일 아침 … 주말 새벽)로 늘어놓는다. 모르는 id는 뒤에 둔다. */
    fun sorted(ids: Collection<String>): List<String> =
        ids.sortedBy { id -> all.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: Int.MAX_VALUE }

    /** id → 화면 문구("평일 저녁"). 모르는 id는 그대로 보인다. */
    fun label(res: Resources, id: String): String = byId[id]?.let { res.getString(it.labelRes) } ?: id

    /** [label]을 화면에서 쓴다. */
    @Composable
    fun label(id: String): String = label(LocalContext.current.resources, id)

    /** 태그 하나를 켜거나 끈다. 켜면 [max]개를 넘길 때 바꾸지 않는다. */
    fun toggle(selected: Set<String>, id: String, max: Int = MAX_SELECTED): Set<String> = when {
        id in selected -> selected - id
        id !in byId || selected.size >= max -> selected
        else -> selected + id
    }
}
