package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance

/** 공동 목표 달성 축하 카드(FR-141): 크루 이름, 이번 주 합계, 목표, 참여 인원. ui.share가 이미지로 그린다. */
data class CelebrationCard(val crewName: String, val total: Distance, val goal: Distance, val participantCount: Int)
