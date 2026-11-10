package com.a207.runkk.app.api.avatar

import com.a207.runkk.app.api.core.ApiTestServer
import com.a207.runkk.app.api.core.value
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.avatar.AvatarBackgrounds
import com.a207.runkk.app.domain.avatar.BackgroundChoice
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import retrofit2.create

/** api.avatar 랜드마크 배경(FR-201, FR-202, D-107, D-140): 목록·선택·러닝 카드 배경 키. */
class AvatarBackgroundTest {
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
        SelfieProgressHub(store),
        java.time.Clock.systemUTC()
    )

    @AfterTest
    fun tearDown() = api.close()

    @Test
    fun `배경 목록을 도메인으로 바꾸고 해금 일시는 오프셋 시각으로 읽는다`() = runTest {
        api.reply(
            """{"selectedKey":"SEOUL_TOWER","backgrounds":[
               |{"key":"SEOUL_TOWER","name":"남산타워","stage":"DOMESTIC","distanceMeters":10000,"unlocked":true,
               |"unlockedAt":"2026-10-20T19:00:00+09:00"},
               |{"key":"FUJI","name":"후지산","stage":"EAST_ASIA","distanceMeters":500000,"unlocked":false,"unlockedAt":null}]}
            """.trimMargin()
        )

        val backgrounds = repository.backgrounds().value()

        assertEquals(
            AvatarBackgrounds(
                "SEOUL_TOWER",
                listOf(
                    AvatarBackgrounds.Item(
                        "SEOUL_TOWER",
                        "남산타워",
                        "DOMESTIC",
                        Distance(10_000),
                        true,
                        Instant.parse("2026-10-20T10:00:00Z")
                    ),
                    AvatarBackgrounds.Item("FUJI", "후지산", "EAST_ASIA", Distance(500_000), false, null)
                )
            ),
            backgrounds
        )
        assertEquals("GET /api/v1/avatar/backgrounds", api.take().let { "${it.method} ${it.url.encodedPath}" })
    }

    @Test
    fun `배경 고르기는 PUT으로 키를 보내고 잠긴 배경은 BACKGROUND_LOCKED로 거부된다`() = runTest {
        api.reply("""{"selectedKey":"SEOUL_TOWER","backgrounds":[]}""")
        api.reply("""{"code":"BACKGROUND_LOCKED","message":"아직 해금하지 않은 배경"}""", code = 409)
        api.reply("""{"selectedKey":null,"backgrounds":[]}""")

        assertEquals(Outcome.Success(Unit), repository.chooseBackground("SEOUL_TOWER"))
        assertEquals(Outcome.Failure(AppError.Rejected("BACKGROUND_LOCKED")), repository.chooseBackground("FUJI"))
        assertEquals(Outcome.Success(Unit), repository.chooseBackground(null))

        val first = api.take()
        assertEquals("PUT /api/v1/avatar/background", "${first.method} ${first.url.encodedPath}")
        assertEquals(Json.parseToJsonElement("""{"key":"SEOUL_TOWER"}"""), Json.parseToJsonElement(first.body!!.utf8()))
        api.take()
        val reset = Json.parseToJsonElement(api.take().body!!.utf8())
        assertEquals(Json.parseToJsonElement("{}"), reset, "기본 배경은 key 없음(null)")
    }

    @Test
    fun `러닝 카드 배경은 배경 목록의 고른 키와 그 단계로 읽는다 - 운영이 더한 키도 단계 그림을 찾는다(AN5-D-14, D-185)`() = runTest {
        api.reply(
            """{"selectedKey":"LM_7","backgrounds":[
               |{"key":"SEOUL_TOWER","name":"남산타워","stage":"DOMESTIC","distanceMeters":10000,"unlocked":true,"unlockedAt":null},
               |{"key":"LM_7","name":"새 명소","stage":"EAST_ASIA","distanceMeters":400000,"unlocked":true,"unlockedAt":null}]}
            """.trimMargin()
        )
        api.reply("""{"selectedKey":null,"backgrounds":[]}""")

        assertEquals(Outcome.Success(BackgroundChoice("LM_7", "EAST_ASIA")), repository.backgroundChoice())
        assertEquals(Outcome.Success(null), repository.backgroundChoice(), "기본 배경이면 null")
        assertEquals("GET /api/v1/avatar/backgrounds", api.take().let { "${it.method} ${it.url.encodedPath}" })
    }

    @Test
    fun `아바타 화면에도 고른 배경 키를 싣는다`() = runTest {
        api.reply("""{"bodyShape":"NORMAL","skinTone":"DAY","backgroundKey":"FUJI"}""")
        assertEquals("FUJI", repository.avatar().value().backgroundKey)
    }
}
