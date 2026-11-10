package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance

/** 크루런 모집글 수정(PATCH /crew-runs/{id}, 피그마 F-13d). 일시는 바꿀 수 없다. [distance]가 null이면 제한 없음이다. */
data class CrewRunEdit(val title: String, val body: String?, val distance: Distance?, val repeat: CrewRunRepeat)
