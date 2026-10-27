package com.a207.runkk.app.domain.run

import java.time.Duration

/**
 * 워치 근접 알림 한 번(FR-196, D-96): 격차 절댓값(m), 비프·진동 간격, 앞섬 여부. ProximityAlertRule.evaluate가 만들고
 * RunWatchBridge가 ProximityMessage로 보낸다.
 */
data class ProximityAlert(val gapMeters: Int, val interval: Duration, val isAhead: Boolean)
