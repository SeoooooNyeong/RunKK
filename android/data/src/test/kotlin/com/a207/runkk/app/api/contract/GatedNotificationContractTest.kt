package com.a207.runkk.app.api.contract

import com.a207.runkk.app.domain.notification.GatedFeature
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 닫힌 기능 알림 분류(GatedFeature)가 서버와 같은지 대조한다(D138, 설계 재검토 H-47). 서버는 이 분류로 1차 배포에서 크루·아바타 알림의 FCM
 * 푸시를 막고(NotificationService), 앱은 같은 분류로 알림함·알림 설정·시스템 푸시에서 숨긴다. `python android/contract/sync_from_server.py`가
 * 서버 notification.NotificationType의 GatedFeature 인자를 contract/policy/GatedNotification.txt로 뽑는다("종류 기능", 없으면 "-").
 */
class GatedNotificationContractTest {
    private val contract =
        File(requireNotNull(System.getProperty("runkk.contract.dir")) { "runkk.contract.dir 속성이 없다" })

    @Test
    fun `알림 종류마다 앱 GatedFeature 분류가 서버 분류와 같다`() {
        val server =
            File(contract, "policy/GatedNotification.txt")
                .readLines()
                .filter(String::isNotBlank)
                .associate { line -> line.substringBefore(' ') to line.substringAfter(' ').trim() }
        val types =
            File(contract, "enums/NotificationType.txt")
                .readLines()
                .map(String::trim)
                .filter(String::isNotEmpty)
        assertEquals(types.toSet(), server.keys, "GatedNotification.txt가 서버 알림 종류 전부를 담아야 한다")
        val app = types.associateWith { GatedFeature.of(it)?.name ?: "-" }
        assertEquals(server, app)
    }
}
