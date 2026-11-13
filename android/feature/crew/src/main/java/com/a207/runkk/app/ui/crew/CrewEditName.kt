package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.RunkkTags

/**
 * 크루 정보 수정(F-26)의 크루명 규칙(결정 C1, 서버 S2·S3와 같다). 크루명 상한([CrewFormState.NAME_MAX])보다 긴 옛 이름이 남아 있을 수
 * 있어, 수정 칸은 옛 이름 길이만큼 입력을 받고(지워 줄일 수 있게) 이름을 그대로 두면 길이와 무관하게 통과한다. 이름을 바꿀 때만
 * NAME_MIN~NAME_MAX로 검사한다.
 */
internal fun editNameMaxLength(originalName: String): Int = maxOf(CrewFormState.NAME_MAX, originalName.trim().length)

/** 이름을 그대로 두었거나, 바꾼 이름이 NAME_MIN~NAME_MAX다. */
internal fun CrewFormState.isEditNameValid(originalName: String): Boolean {
    val trimmed = name.trim()
    return trimmed == originalName.trim() || trimmed.length in CrewFormState.NAME_MIN..CrewFormState.NAME_MAX
}

/** 수정 저장 가능: 이름 규칙과 태그 1~3개. */
internal fun CrewFormState.isEditValid(originalName: String): Boolean =
    isEditNameValid(originalName) && tags.size in 1..RunkkTags.MAX_SELECTED
