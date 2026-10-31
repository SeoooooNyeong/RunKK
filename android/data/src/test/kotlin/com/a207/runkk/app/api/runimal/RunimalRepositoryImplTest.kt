package com.a207.runkk.app.api.runimal

import com.a207.runkk.app.api.core.ApiTestServer
import com.a207.runkk.app.api.core.value
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.runimal.AxisLean
import com.a207.runkk.app.domain.runimal.RunimalCard
import com.a207.runkk.app.domain.runimal.RunimalTypeBoard
import com.a207.runkk.app.domain.runimal.RunimalTypeDetail
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import retrofit2.create

/** api.runimal v0.14 매퍼(D-285, D-289): 16유형 목록·유형 상세·카드 축 막대. */
class RunimalRepositoryImplTest {
    private val api = ApiTestServer()
    private val repository = RunimalRepositoryImpl(api.retrofit.create())

    @AfterTest
    fun tearDown() = api.close()

    @Test
    fun `TC-9738 16유형 목록은 지금 유형과 유형마다 확정받은 적 있는지를 읽는다`() = runTest {
        api.reply(
            """{"current":"COMMUTER_TURTLE","types":[
               |{"type":"COMMUTER_TURTLE","displayName":"출근도장 거북이","icon":"turtle","description":"같은 길을",
               |"axes":{"COMPANION":"A","DISTANCE":"B","COURSE":"A","HABIT":"A"},"everConfirmed":true},
               |{"type":"WILD_HORSE","displayName":"들판의 야생마","icon":"horse","description":"…",
               |"axes":{"COMPANION":"A","DISTANCE":"B","COURSE":"B","HABIT":"NEW"},"everConfirmed":false}]}
            """.trimMargin()
        )

        val board = repository.types().value()

        assertEquals(
            RunimalTypeBoard(
                "COMMUTER_TURTLE",
                listOf(
                    RunimalTypeBoard.Entry(
                        "COMMUTER_TURTLE",
                        "출근도장 거북이",
                        "turtle",
                        "같은 길을",
                        mapOf(
                            "COMPANION" to AxisLean.A,
                            "DISTANCE" to AxisLean.B,
                            "COURSE" to AxisLean.A,
                            "HABIT" to AxisLean.A
                        ),
                        isEverConfirmed = true
                    ),
                    RunimalTypeBoard.Entry(
                        "WILD_HORSE",
                        "들판의 야생마",
                        "horse",
                        "…",
                        mapOf("COMPANION" to AxisLean.A, "DISTANCE" to AxisLean.B, "COURSE" to AxisLean.B),
                        isEverConfirmed = false
                    )
                )
            ),
            board
        )
        assertEquals("/api/v1/runimal/types", api.take().url.encodedPath)
    }

    @Test
    fun `TC-9738 유형 상세는 특징 3줄과 비율을 읽고 비율이 없으면 null이다`() = runTest {
        api.reply(
            """{"type":"COMMUTER_TURTLE","displayName":"출근도장 거북이","icon":"turtle","description":"같은 길을",
               |"axes":{"COMPANION":"A"},"traits":["하나","둘","셋"],"sharePercent":33,"computedAt":"2026-10-29T00:00:00+09:00"}
            """.trimMargin()
        )
        api.reply(
            """{"type":"WILD_HORSE","displayName":"들판의 야생마","icon":null,"description":"…","axes":{},
               |"traits":[],"sharePercent":null,"computedAt":null}
            """.trimMargin()
        )
        api.reply("""{"code":"BAD_REQUEST"}""", code = 400)

        assertEquals(
            RunimalTypeDetail(
                "COMMUTER_TURTLE",
                "출근도장 거북이",
                "turtle",
                "같은 길을",
                mapOf("COMPANION" to AxisLean.A),
                listOf("하나", "둘", "셋"),
                33,
                Instant.parse("2026-10-28T15:00:00Z")
            ),
            repository.type("COMMUTER_TURTLE").value()
        )
        assertEquals("/api/v1/runimal/types/COMMUTER_TURTLE", api.take().url.encodedPath)
        val empty = repository.type("WILD_HORSE").value()
        assertEquals(null, empty.sharePercent)
        assertEquals(null, empty.computedAt)
        assertEquals(Outcome.Failure(AppError.Rejected("BAD_REQUEST")), repository.type("NOPE"))
    }

    @Test
    fun `lean이 없는 옛 서버 카드 축 막대는 성향 이름으로 기운 쪽을 정한다`() = runTest {
        api.reply(
            """{"type":"COMMUTER_TURTLE","title":"출근도장 거북이","description":"…","axisRatios":{"COMPANION":0.78},
               |"axes":[{"axis":"COMPANION","label":"솔로런","percent":78},{"axis":"DISTANCE","label":"장거리","percent":64},
               |{"axis":"COURSE","label":"단골길","percent":71},{"axis":"HABIT","label":"즉흥런","percent":58},
               |{"axis":"NEW","label":"모름","percent":50}]}
            """.trimMargin()
        )

        assertEquals(
            listOf(
                RunimalCard.AxisBar("COMPANION", AxisLean.A, 78),
                RunimalCard.AxisBar("DISTANCE", AxisLean.B, 64),
                RunimalCard.AxisBar("COURSE", AxisLean.A, 71),
                RunimalCard.AxisBar("HABIT", AxisLean.B, 58)
            ),
            repository.card().value().axes
        )
    }

    @Test
    fun `카드 축 막대는 서버 lean이 있으면 성향 이름보다 lean을 따른다`() = runTest {
        api.reply(
            """{"type":"COMMUTER_TURTLE","title":"출근도장 거북이","description":"…",
               |"axes":[{"axis":"COMPANION","label":"솔로런","percent":78,"lean":"B"},
               |{"axis":"DISTANCE","label":"새 이름","percent":64,"lean":"A"},
               |{"axis":"COURSE","label":"단골길","percent":71,"lean":"X"}]}
            """.trimMargin()
        )

        assertEquals(
            listOf(
                RunimalCard.AxisBar("COMPANION", AxisLean.B, 78),
                RunimalCard.AxisBar("DISTANCE", AxisLean.A, 64),
                RunimalCard.AxisBar("COURSE", AxisLean.A, 71)
            ),
            repository.card().value().axes
        )
    }

    @Test
    fun `TC-9743 옛 서버 카드는 축 막대 없이 해독된다`() = runTest {
        api.reply("""{"type":"T","title":"이름","description":"…"}""")

        assertEquals(emptyList(), repository.card().value().axes)
    }
}
