package com.a207.runkk.watch.cache

import com.a207.runkk.app.domain.battle.RecordTrack
import java.time.Instant

/** 받은 시각과 함께 워치에 둔 셀프·셀럽 상대 기록 하나(3.4, FR-260 "받은 시각을 함께 둔다"). */
data class CachedTrack(val record: RecordTrack, val fetchedAt: Instant)
