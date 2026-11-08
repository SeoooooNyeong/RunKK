package com.a207.runkk.app.domain.runbody

/** 기록 하나의 분석 상태(서버 RunBodySessionView). [result]는 [status]가 DONE일 때만 있다. */
data class RunBodySession(val recordId: String, val status: RunBodyStatus, val result: RunBodyResult?)
