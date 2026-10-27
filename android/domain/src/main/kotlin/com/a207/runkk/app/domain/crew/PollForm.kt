package com.a207.runkk.app.domain.crew

import java.time.Instant

/** 투표 쓰기 입력(FR-206). [options]는 CHOICES일 때의 선택지이고 YES_NO면 보내지 않는다(서버가 찬성·반대를 만든다). */
data class PollForm(val question: String, val method: PollMethod, val options: List<String>, val closesAt: Instant)
