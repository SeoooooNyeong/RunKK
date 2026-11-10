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
import com.a207.runkk.app.domain.avatar.SelfieFailure
import com.a207.runkk.app.domain.avatar.SelfieJob
import com.a207.runkk.app.domain.avatar.Shop
import com.a207.runkk.app.domain.notification.Inbox
import com.a207.runkk.app.domain.runimal.RunimalCard
import com.a207.runkk.app.domain.runimal.RunimalStatus
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

    /** 남긴 셀카 작업 ID를 메모리에 두는 저장소. */
    private val pending = object : PendingSelfieStore {
        var jobId: String? = null
        var failed: SelfieFailure? = null

        override fun load(): String? = jobId

        override fun save(jobId: String) {
            this.jobId = jobId
        }

        override fun clear() {
            jobId = null
        }

        override fun failure(): SelfieFailure? = failed

        override fun saveFailure(failure: SelfieFailure) {
            failed = failure
        }

        override fun clearFailure() {
            failed = null
        }
    }

    private fun repository() = AvatarRepositoryImpl(api.retrofit.create(), api.faces, pending)

    @Test
    fun `아바타 DTO를 부위별 맵으로 바꾸고 모르는 부위는 건너뛴다`() = runTest {
        api.reply(
            """{"faceKey":null,"defaultCharacterId":"bear","bodyShape":"NORMAL","skinTone":"DAY","equipped":{"TOP":11,"CAPE":99},
               |"balance":340,"canCreateSelfieToday":true,"rejected":{"SHOES":"NOT_OWNED"}}
            """.trimMargin()
        )

        val avatar = repository().avatar().value()

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
    fun `셀카는 JPEG multipart로 작업 접수 경로에 보내고 착용 변경은 부위 이름으로 보낸다`() = runTest {
        api.reply("""{"jobId":"job-1","status":"QUEUED"}""", code = 202)
        api.reply("""{"bodyShape":"NORMAL","skinTone":"DAY"}""")
        val repository = repository()

        assertEquals(
            SelfieJob("job-1", SelfieJob.Status.QUEUED),
            repository.submitSelfie(byteArrayOf(0xFF.toByte(), 0xD8.toByte())).value()
        )
        repository.equip(mapOf(ItemSlot.HAT to null, ItemSlot.TOP to 12L))

        val selfie = api.take()
        assertEquals("/api/v1/avatar/selfie/jobs", selfie.target)
        assertTrue(selfie.headers["Content-Type"]!!.startsWith("multipart/form-data"))
        val part = selfie.body!!.utf8()
        assertTrue(part.contains("name=\"selfie\"") && part.contains("Content-Type: image/jpeg"), part)
        assertEquals("""{"changes":{"HAT":null,"TOP":12}}""", api.take().body!!.utf8())
        assertEquals("job-1", repository.pendingSelfieJobId(), "접수한 작업은 이어 조회하려고 남긴다")
    }

    @Test
    fun `작업이 실패로 끝나면 사유를 돌려주고 남긴 작업을 지운다`() = runTest {
        api.reply("""{"jobId":"job-1","status":"QUEUED"}""", code = 202)
        api.reply("""{"jobId":"job-1","status":"RUNNING"}""")
        api.reply("""{"jobId":"job-1","status":"FAILED","error":{"code":"SELFIE_QUALITY_REJECTED","message":"흐림"}}""")
        val repository = repository()
        repository.submitSelfie(byteArrayOf(1))

        assertEquals(SelfieJob.Status.RUNNING, repository.selfieJob("job-1").value().status)
        assertEquals("job-1", repository.pendingSelfieJobId())
        assertEquals(
            SelfieJob("job-1", SelfieJob.Status.FAILED, "SELFIE_QUALITY_REJECTED"),
            repository.selfieJob("job-1").value()
        )
        api.replyEmpty(204)
        assertNull(repository.pendingSelfieJobId(), "서버에도 진행 중 작업이 없다")
        api.take()
        assertEquals("/api/v1/avatar/selfie/jobs/job-1", api.take().target)
    }

    @Test
    fun `작업이 실패로 끝나면 실패를 남기고, 새로 접수하거나 결과를 받으면 지운다(DV-성-10)`() = runTest {
        api.reply("""{"jobId":"job-1","status":"QUEUED"}""", code = 202)
        api.reply(
            """{"jobId":"job-1","status":"FAILED","error":{"code":"GENERATION_OUTCOME_UNKNOWN","message":"불명확"}}"""
        )
        api.reply("""{"jobId":"job-1","status":"SUCCEEDED","headId":"h-1"}""")
        api.reply("""{"headId":"h-1","faceImage":"data:image/png;base64,AAAA"}""")
        api.reply("""{"faceKey":"head:7:job-1","bodyShape":"STAGE1","skinTone":"DAYTIME"}""")
        api.reply("""{"jobId":"job-2","status":"QUEUED"}""", code = 202)
        val repository = repository()
        repository.submitSelfie(byteArrayOf(1))
        assertNull(repository.lastSelfieFailure())

        repository.selfieJob("job-1")
        assertEquals(SelfieFailure("job-1", "GENERATION_OUTCOME_UNKNOWN"), repository.lastSelfieFailure())
        assertEquals(SelfieJob.Status.SUCCEEDED, repository.selfieJob("job-1").value().status)
        assertEquals(
            SelfieFailure("job-1", "GENERATION_OUTCOME_UNKNOWN"),
            repository.lastSelfieFailure(),
            "회수된 작업도 결과를 받기 전까지는 남긴다"
        )
        repository.finishSelfie("job-1")
        assertNull(repository.lastSelfieFailure())
        pending.failed = SelfieFailure("job-1", "UPSTREAM_TIMEOUT")
        repository.submitSelfie(byteArrayOf(2))
        assertNull(repository.lastSelfieFailure())
    }

    @Test
    fun `서버가 작업을 잃었으면(404 reason) 거부로 돌려주고 남긴 작업을 지운다`() = runTest {
        api.reply("""{"jobId":"job-1","status":"QUEUED"}""", code = 202)
        api.reply("""{"reason":"JOB_NOT_FOUND"}""", code = 404)
        val repository = repository()
        repository.submitSelfie(byteArrayOf(1))

        assertEquals(Outcome.Failure(AppError.Rejected("JOB_NOT_FOUND")), repository.selfieJob("job-1"))
        api.replyEmpty(204)
        assertNull(repository.pendingSelfieJobId(), "서버에도 진행 중 작업이 없다")
    }

    @Test
    fun `조회가 잠깐 실패하면 남긴 작업을 그대로 둔다`() = runTest {
        api.reply("""{"jobId":"job-1","status":"QUEUED"}""", code = 202)
        api.reply("""{"reason":"UPSTREAM_FAILED"}""", code = 503)
        val repository = repository()
        repository.submitSelfie(byteArrayOf(1))

        assertEquals(Outcome.Failure(AppError.Server(503)), repository.selfieJob("job-1"))
        assertEquals("job-1", repository.pendingSelfieJobId())
    }

    @Test
    fun `동의가 없으면 접수 거부이고 작업을 남기지 않는다`() = runTest {
        api.reply("""{"code":"FACE_CONSENT_REQUIRED","message":"전송 동의 필요"}""", code = 409)
        val repository = repository()

        assertEquals(
            Outcome.Failure(AppError.Rejected("FACE_CONSENT_REQUIRED")),
            repository.submitSelfie(byteArrayOf(1))
        )
        api.replyEmpty(204)
        assertNull(repository.pendingSelfieJobId(), "서버에도 진행 중 작업이 없다")
    }

    @Test
    fun `끝난 작업은 결과를 받아 얼굴을 바꾸고 바뀐 아바타를 읽는다`() = runTest {
        api.reply("""{"jobId":"job-1","status":"QUEUED"}""", code = 202)
        api.reply("""{"headId":"h-1","faceImage":"data:image/png;base64,AAAA"}""")
        api.reply(
            """{"faceKey":"head:7:job-1","bodyShape":"STAGE1","skinTone":"DAYTIME","canCreateSelfieToday":false}"""
        )
        val repository = repository()
        repository.submitSelfie(byteArrayOf(1))

        val avatar = repository.finishSelfie("job-1").value()

        assertEquals("head:7:job-1", avatar.faceKey)
        assertEquals(false, avatar.canCreateSelfieToday)
        api.replyEmpty(204)
        assertNull(repository.pendingSelfieJobId(), "서버에도 진행 중 작업이 없다")
        api.take()
        assertEquals("/api/v1/avatar/selfie/jobs/job-1/result", api.take().target)
        assertEquals("/api/v1/avatar", api.take().target)
    }

    @Test
    fun `기기에 남긴 작업이 없으면 서버의 진행 중 작업을 이어 조회하려고 남긴다`() = runTest {
        api.reply("""{"jobId":"job-7","status":"SUCCEEDED","headId":"h-7"}""")
        val repository = repository()

        assertEquals("job-7", repository.pendingSelfieJobId())
        assertEquals("/api/v1/avatar/selfie/jobs/current", api.take().target)
        assertEquals("job-7", repository.pendingSelfieJobId(), "남긴 뒤에는 서버에 다시 묻지 않는다")
        assertEquals(1, api.server.requestCount)
    }

    @Test
    fun `상점과 구매 잔액을 읽는다`() = runTest {
        api.reply(
            """{"items":[{"itemId":12,"slot":"TOP","name":"러닝 티","price":100,"owned":true,"onSale":true}],"balance":340}"""
        )
        api.reply("""{"balance":240}""")
        val repository = repository()

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

    @Test
    fun `운영자 공지는 서버 제목과 링크 대상을 그대로 쓰고 제목이 없으면 종류 제목`() = runTest {
        api.reply(
            """{"items":[{"id":7,"type":"NOTICE","title":"코치님 이벤트 당첨","content":"구글 폼을 열어 주세요",
               |"occurredAt":"2026-11-17T10:00:00","read":false,
               |"target":{"screen":"EXTERNAL_LINK","targetId":"https://forms.gle/AbCdEf123"}},
               |{"id":8,"type":"NOTICE","content":"제목 없음","occurredAt":"2026-11-17T10:00:00","read":true,
               |"target":{"screen":"EXTERNAL_LINK"}}]}
            """.trimMargin()
        )

        val items = NotificationRepositoryImpl(
            api.retrofit.create(),
            RuntimeEnvironment.getApplication()
        ).inbox().value().items

        assertEquals(listOf("코치님 이벤트 당첨", "안내"), items.map { it.title })
        assertEquals("https://forms.gle/AbCdEf123", items[0].targetId)
        assertEquals("EXTERNAL_LINK", items[1].screen)
        assertEquals(null, items[1].targetId)
    }
}
