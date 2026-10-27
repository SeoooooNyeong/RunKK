package com.a207.runkk.app.core.designsystem

import androidx.annotation.StringRes

/**
 * 성향 태그 고정 목록(SRS 3.5.1, W-13: 7분류 19개)의 화면 문구. 가입·프로필 편집(ui.me)과 크루(ui.crew)가 같이 쓴다.
 * [RunkkTag.id]는 서버 PersonalityTag 상수 이름이다(서버로 가는 값). 계약 시험(ContractEnumsTest)이 서버 이름 목록과 대조한다.
 * 고르기 규칙(분류마다 1개, 모두 1~5개)은 서버가 판정한다. 화면은 [RunkkTags.toggle]로 분류마다 1개만 남긴다.
 */
data class RunkkTag(val id: String, @StringRes val labelRes: Int)
