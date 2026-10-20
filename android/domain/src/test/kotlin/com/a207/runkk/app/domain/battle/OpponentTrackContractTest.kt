package com.a207.runkk.app.domain.battle

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.live.TrackPoint
import com.a207.runkk.app.domain.live.TrackTable
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OpponentTrack 계약 시험(14장): 서버 battle.OpponentTrack과 같은 입력·출력 표(contract/opponent-track.csv)로 확인한다.
 */
class OpponentTrackContractTest {
    @Test
    fun `계약 표의 모든 줄이 맞는다`() {
        val lines =
            requireNotNull(javaClass.getResource("/contract/opponent-track.csv")) { "계약 표 없음" }
                .readText()
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
        var track = OpponentTrack.from(TrackTable(emptyList()))
        var checked = 0
        lines.forEach { line ->
            val cells = line.split(",")
            when (cells[0]) {
                "track" -> track = OpponentTrack.from(TrackTable(parsePoints(cells[1])))

                "uniform" -> {
                    val (meters, millis) = cells[1].split(":").map { it.toLong() }
                    track = OpponentTrack.uniform(Distance(meters), Duration.ofMillis(millis))
                }

                "empty" -> track = OpponentTrack.from(TrackTable(emptyList()))

                "cut" -> track = track.cut(Distance(cells[1].toLong()))

                "distanceAt" -> {
                    val actual = track.distanceAt(Duration.ofMillis(cells[1].toLong()))
                    assertEquals(Distance(cells[2].toLong()), actual, line)
                    checked++
                }

                "arrivalTime" -> {
                    val actual = track.arrivalTime(Distance(cells[1].toLong()))
                    val expected = cells[2].takeUnless { it == "NONE" }?.let { Duration.ofMillis(it.toLong()) }
                    assertEquals(expected, actual, line)
                    checked++
                }

                else -> error("모르는 명령: $line")
            }
        }
        assertTrue(checked >= 30, "확인한 줄 수: $checked")
    }

    private fun parsePoints(cell: String): List<TrackPoint> = cell.split(";").map { pair ->
        val (seconds, meters) = pair.split(":").map { it.toLong() }
        TrackPoint(elapsedSeconds = seconds, distanceMeters = meters)
    }
}
