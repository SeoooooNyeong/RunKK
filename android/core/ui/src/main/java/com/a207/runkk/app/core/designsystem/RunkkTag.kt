package com.a207.runkk.app.core.designsystem

import androidx.annotation.StringRes

/**
 * 크루 태그(요일 묶음 × 시간대, 피그마 F-01b·F-04)의 화면 문구. [id]는 서버 CrewTag 상수 이름이다(서버로 가는 값). 계약 시험
 * (ServerContractTest)이 서버 이름 목록과 대조한다. [labelRes]는 목록·배지에 쓰는 전체 이름("평일 저녁"), [slotRes]는 고르는 칸에
 * 쓰는 시간대 이름("저녁")이다. 개수 상한(3개)은 서버가 판정하고 화면은 [RunkkTags.toggle]로 넘기지 않는다.
 */
data class RunkkTag(val id: String, @StringRes val labelRes: Int, @StringRes val slotRes: Int) {
    /** 주말 태그인가(배지 색을 가른다). */
    val isWeekend: Boolean get() = id.startsWith(WEEKEND_PREFIX)

    private companion object {
        const val WEEKEND_PREFIX = "WEEKEND_"
    }
}
