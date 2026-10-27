package com.a207.runkk.app.api.avatar

import com.a207.runkk.app.api.core.ApiTestServer
import com.a207.runkk.app.api.core.value
import com.a207.runkk.app.api.notification.NotificationRepositoryImpl
import com.a207.runkk.app.api.notification.PushTokenRemoteImpl
import com.a207.runkk.app.api.runimal.RunimalRepositoryImpl
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.avatar.Avatar
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.Shop
import com.a207.runkk.app.domain.notification.Inbox
import com.a207.runkk.app.domain.runimal.RunimalCard
import com.a207.runkk.app.domain.runimal.RunimalStatus
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import retrofit2.create

/** api.avatar·api.runimal·api.notification 매퍼(14장 단위(앱 데이터)). 알림함 제목이 문자열 리소스라 Robolectric으로 돌린다. */
@RunWith(RobolectricTestRunner::class)
class AvatarAndRunimalAndNotificationTest {
    private val api = ApiTestServer()

    @AfterTest
    fun tearDown() = api.close()

    @Test
    fun `아바타 DTO를 부위별 맵으로 바꾸고 모르는 부위는 건너뛴다`() = runTest {
        api.reply(
            """{"faceKey":null,"defaultCharacterId":"bear","bodyShape":"NORMAL","skinTone":"DAY","equipped":{"TOP":11,"CAPE":99},
               |"balance":340,"canCreateSelfieToday":true,"rejected":{"SHOES":"NOT_OWNED"}}
            """.trimMargin()
        )

        val avatar = AvatarRepositoryImpl(api.retrofit.create(), api.faces).avatar().value()

        assertEquals(
            Avatar(
                null,
                "bear",
                "NORMAL",
                "DAY",
                mapOf(ItemSlot.TOP to 11L),
                340,
                true,
                mapOf(ItemSlot.SHOES to "NOT_OWNED")
            ),
            avatar
        )
    }

    @Test
    fun `셀카는 JPEG multipart로 보내고 착용 변경은 부위 이름으로 보낸다`() = runTest {
        val body = """{"bodyShape":"NORMAL","skinTone":"DAY"}"""
        api.reply(body)
        api.reply(body)
        val repository = AvatarRepositoryImpl(api.retrofit.create(), api.faces)

        repository.createFromSelfie(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        repository.equip(mapOf(ItemSlot.HAT to null, ItemSlot.TOP to 12L))

        val selfie = api.take()
        assertTrue(selfie.headers["Content-Type"]!!.startsWith("multipart/form-data"))
        val part = selfie.body!!.utf8()
        assertTrue(part.contains("name=\"selfie\"") && part.contains("Content-Type: image/jpeg"), part)
        assertEquals("""{"changes":{"HAT":null,"TOP":12}}""", api.take().body!!.utf8())
    }

    @Test
    fun `셀카 변환 실패는 200이어도 실패 사유로 돌려준다`() = runTest {
        api.reply(
            """{"faceKey":null,"defaultCharacterId":null,"guide":"기본 캐릭터를 고를 수 있어요","bodyShape":"STAGE1","skinTone":"DAYTIME",
               |"equipped":{},"balance":0,"canCreateSelfieToday":true,"rejected":{},"created":false,
               |"failureReason":"NO_FACE","failureMessage":"얼굴을 찾지 못했어요"}
            """.trimMargin()
        )

        val result = AvatarRepositoryImpl(api.retrofit.create(), api.faces).createFromSelfie(byteArrayOf(1))

        assertEquals(Outcome.Failure(AppError.Rejected("NO_FACE")), result)
    }

    @Test
    fun `셀카 변환이 성공하면 아바타를 돌려준다`() = runTest {
        api.reply(
            """{"faceKey":"face-1","defaultCharacterId":null,"guide":null,"bodyShape":"STAGE1","skinTone":"DAYTIME","equipped":{},
               |"balance":0,"canCreateSelfieToday":false,"rejected":{},"created":true,"failureReason":null,"failureMessage":null}
            """.trimMargin()
        )

        val avatar = AvatarRepositoryImpl(api.retrofit.create(), api.faces).createFromSelfie(byteArrayOf(1)).value()

        assertEquals("face-1", avatar.faceKey)
        assertEquals(false, avatar.canCreateSelfieToday)
    }

    @Test
    fun `상점과 구매 잔액을 읽는다`() = runTest {
        api.reply(
            """{"items":[{"itemId":12,"slot":"TOP","name":"러닝 티","price":100,"owned":true,"onSale":true}],"balance":340}"""
        )
        api.reply("""{"balance":240}""")
        val repository = AvatarRepositoryImpl(api.retrofit.create(), api.faces)

        assertEquals(
            Shop(listOf(Shop.Item(12, ItemSlot.TOP, "러닝 티", 100, true, true)), 340),
            repository.shop(ItemSlot.TOP).value()
        )
        assertEquals(Outcome.Success(240), repository.purchase(12))
        assertEquals("/api/v1/shop?slot=TOP", api.take().target)
    }

    @Test
    fun `Runimal 상태와 유형 없음 거부를 바꾼다`() = runTest {
        api.reply(
            """{"type":"ENFP","provisional":true,"remainingRuns":7,
               |"card":{"type":"ENFP","title":"자유로운 여우","description":"…","axisRatios":{"PACE":0.7}},"allTypes":["ENFP","ISTJ"]}
            """.trimMargin()
        )
        api.reply("""{"code":"NO_TYPE"}""", code = 409)
        val repository = RunimalRepositoryImpl(api.retrofit.create())

        assertEquals(
            RunimalStatus(
                "ENFP",
                true,
                7,
                RunimalCard("ENFP", "자유로운 여우", "…", mapOf("PACE" to 0.7)),
                listOf("ENFP", "ISTJ")
            ),
            repository.status().value()
        )
        assertEquals(Outcome.Failure(AppError.Rejected("NO_TYPE")), repository.card())
    }

    @Test
    fun `알림함을 바꾸고 푸시 토큰을 등록한다`() = runTest {
        api.reply(
            """{"items":[{"id":5,"type":"CREW_RUN_SOON","content":"10분 뒤 출발","occurredAt":"2026-10-20T05:50:00",
               |"read":false,"target":{"screen":"CREW_RUN_RECRUIT","targetId":"9"}}]}
            """.trimMargin()
        )
        api.replyEmpty()

        val inbox = NotificationRepositoryImpl(
            api.retrofit.create(),
            RuntimeEnvironment.getApplication()
        ).inbox().value()
        val registered = PushTokenRemoteImpl(api.retrofit.create()).register("fcm-token")

        assertEquals(
            Inbox(
                listOf(
                    Inbox.Item(
                        5,
                        "CREW_RUN_SOON",
                        "크루런 시작 전",
                        "10분 뒤 출발",
                        Instant.parse("2026-10-19T20:50:00Z"),
                        false,
                        "CREW_RUN_RECRUIT",
                        "9"
                    )
                )
            ),
            inbox
        )
        assertEquals(Outcome.Success(Unit), registered)
        api.take()
        val push = api.take()
        assertEquals("PUT /api/v1/me/push-token", "${push.method} ${push.url.encodedPath}")
        assertEquals("""{"token":"fcm-token"}""", push.body!!.utf8())
    }
}
