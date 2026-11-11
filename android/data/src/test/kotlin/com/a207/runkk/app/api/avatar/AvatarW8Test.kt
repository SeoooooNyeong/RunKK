package com.a207.runkk.app.api.avatar

import com.a207.runkk.app.api.core.ApiTestServer
import com.a207.runkk.app.api.core.value
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.avatar.AvatarRunner
import com.a207.runkk.app.domain.avatar.ItemSlot
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import retrofit2.create

/** W8(S15P31A207-299, contract-W8.md 2·4·6절): 내 러너, 배경 남은 거리, 상점 그림 키·잠김, 셀카 머리 JSON. */
class AvatarW8Test {
    private val api = ApiTestServer()
    private val store = object : PendingSelfieStore {
        override fun load(): String? = null

        override fun save(jobId: String, submittedAtMillis: Long?) = Unit

        override fun clear() = Unit
    }
    private val repository = AvatarRepositoryImpl(
        api.retrofit.create(),
        api.faces,
        store,
        SelfieProgressHub(
            store,
            com.a207.runkk.app.api.core.SelfMember(TestTokens(com.a207.runkk.app.api.core.jwtOf("7")))
        ),
        MemoryPhoto(),
        java.time.Clock.systemUTC()
    )

    @AfterTest
    fun tearDown() = api.close()

    @Test
    fun `내 러너를 GET avatar runner로 읽어 도메인으로 바꾼다`() = runTest {
        api.reply(
            """{"bodyShape":"STAGE3","bodyVariant":1,"skinTone":"MORNING","typicalStartHour":7,"recentRunCount":3,
               |"recentDistanceMeters":15400,"runAmount":51,"restDaysBeforeDecline":4}
            """.trimMargin()
        )

        val runner = repository.runner().value()

        assertEquals("/api/v1/avatar/runner", api.take().url.encodedPath)
        assertEquals(AvatarRunner("STAGE3", 1, "MORNING", 7, 3, Distance(15_400), 51, 4), runner)
    }

    @Test
    fun `대표 시각이 null이면 null로 읽는다`() = runTest {
        api.reply(
            """{"bodyShape":"STAGE1","bodyVariant":0,"skinTone":"DAYTIME","typicalStartHour":null,"recentRunCount":0,
               |"recentDistanceMeters":0,"runAmount":0,"restDaysBeforeDecline":4}
            """.trimMargin()
        )

        assertNull(repository.runner().value().typicalStartHour)
    }

    @Test
    fun `배경 목록의 누적 거리와 남은 거리를 읽는다`() = runTest {
        api.reply(
            """{"selectedKey":null,"totalMeters":287000,"backgrounds":[
               |{"key":"DAEJEON","name":"대전","stage":"DOMESTIC","distanceMeters":280000,"unlocked":true,
               |"unlockedAt":"2026-11-09T07:40:00+09:00","remainingMeters":0},
               |{"key":"JEONJU","name":"전주","stage":"DOMESTIC","distanceMeters":400000,"unlocked":false,
               |"unlockedAt":null,"remainingMeters":113000}]}
            """.trimMargin()
        )

        val backgrounds = repository.backgrounds().value()

        assertEquals(Distance(287_000), backgrounds.totalDistance)
        assertEquals(Distance(0), backgrounds.items[0].remaining)
        assertEquals(Distance(113_000), backgrounds.items[1].remaining)
    }

    @Test
    fun `상점 아이템의 그림 키, 잠김, 해금 조건 문구를 읽는다`() = runTest {
        api.reply(
            """{"balance":1240,"items":[
               |{"itemId":1,"slot":"TOP","name":"라임 싱글렛","price":300,"owned":true,"onSale":true,
               |"assetKey":"top_lime_singlet","locked":false,"unlockCondition":null},
               |{"itemId":6,"slot":"TOP","name":"완주 기념 티","price":300,"owned":false,"onSale":true,
               |"assetKey":"top_finisher_tee","locked":true,"unlockCondition":"하프 완주하면 열려요"}]}
            """.trimMargin()
        )

        val items = repository.shop(ItemSlot.TOP).value().items

        assertEquals("top_lime_singlet", items[0].imageKey)
        assertEquals(false, items[0].isLocked)
        assertNull(items[0].unlockLabel)
        assertEquals(true, items[1].isLocked)
        assertEquals("하프 완주하면 열려요", items[1].unlockLabel)
    }

    @Test
    fun `셀카 머리 얼굴이면 GET avatar heads 응답 JSON을 그대로 돌려주고 아니면 null`() = runTest {
        val body =
            """{"headId":"h","faceImage":"data:image/png;base64,AA",""" +
                """"rightFaceImage":"data:image/png;base64,AA"}"""
        api.reply(body)

        val json = repository.selfieHead("head:7:job-1").value()?.json

        assertEquals("/api/v1/avatar/heads/7/job-1", api.take().url.encodedPath)
        assertEquals(body, json)
        assertNull(repository.selfieHead("default:FOX").value())
        assertNull(repository.selfieHead(null).value())
    }
}
