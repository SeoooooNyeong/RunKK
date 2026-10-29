package com.a207.runkk.app.ui.record

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.record.NewRecordMark
import com.a207.runkk.app.domain.record.RecordBattleType
import com.a207.runkk.app.domain.record.RecordVerdict
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import org.junit.Test

/** 기록 목록 태그(RecordTag, D-279)와 목록·상세 서식(묶음, 가장 빠른 1km, 페이스 표기). */
class RecordTagTest {
    private fun res(id: Int, vararg args: Any) = UiText.Res(id, args.toList())

    @Test
    fun `TC-9732 자유런은 종류 태그가 없고 배틀런·친구 대결은 승패를, 보류면 종류만 붙인다`() {
        val free = summary("free", 20, 5_000)
        val randomWin = summary("rw", 20, 5_000, kind = CardKind.BATTLE_RUN)
            .copy(battleType = RecordBattleType.RANDOM, verdict = RecordVerdict.WIN)
        val friendLose = summary("fl", 20, 5_000, kind = CardKind.BATTLE_RUN)
            .copy(battleType = RecordBattleType.FRIEND, verdict = RecordVerdict.LOSE)
        val randomPending = summary("rp", 20, 5_000, kind = CardKind.BATTLE_RUN)
            .copy(battleType = RecordBattleType.RANDOM, isPending = true)
        val draw = summary("d", 20, 5_000, kind = CardKind.BATTLE_RUN)
            .copy(battleType = RecordBattleType.RANDOM, verdict = RecordVerdict.DRAW)

        assertEquals(null, RecordTag.kindOf(free))
        assertEquals(RecordTag.BATTLE_WIN, RecordTag.kindOf(randomWin))
        assertEquals(RecordTag.FRIEND_LOSE, RecordTag.kindOf(friendLose))
        assertEquals(RecordTag.BATTLE, RecordTag.kindOf(randomPending))
        assertEquals(RecordTag.BATTLE_DRAW, RecordTag.kindOf(draw))
        assertEquals(emptyList(), RecordTag.textsOf(free))
        assertEquals(listOf(res(R.string.record_tag_battle_win)), RecordTag.textsOf(randomWin))
        assertEquals(listOf(res(R.string.record_tag_friend_lose)), RecordTag.textsOf(friendLose))
    }

    @Test
    fun `TC-9732 셀프런·셀럽런은 승패 없이 종류만, 크루런은 크루런이다`() {
        val self = summary("s", 20, 5_000, kind = CardKind.SELF_RUN)
            .copy(battleType = RecordBattleType.SELF, verdict = RecordVerdict.WIN)
        val celeb = summary("c", 20, 5_000, kind = CardKind.CELEB_RUN).copy(battleType = RecordBattleType.CELEBRITY)
        val crew = summary("cr", 20, 5_000, kind = CardKind.CREW_RUN)

        assertEquals(RecordTag.SELF, RecordTag.kindOf(self))
        assertEquals(RecordTag.CELEB, RecordTag.kindOf(celeb))
        assertEquals(RecordTag.CREW, RecordTag.kindOf(crew))
    }

    @Test
    fun `TC-9743 옛 서버라 대결 종류가 없으면 카드 종류로 정한다`() {
        assertEquals(RecordTag.BATTLE, RecordTag.kindOf(summary("b", 20, 5_000, kind = CardKind.BATTLE_RUN)))
        assertEquals(RecordTag.SELF, RecordTag.kindOf(summary("s", 20, 5_000, kind = CardKind.SELF_RUN)))
        assertEquals(RecordTag.CELEB, RecordTag.kindOf(summary("c", 20, 5_000, kind = CardKind.CELEB_RUN)))
    }

    @Test
    fun `TC-9732 대결이 아닌 신기록은 종류 문구를, 종류가 없으면 신기록만 붙인다`() {
        val marked = summary("n", 20, 10_000).copy(
            isNewRecord = true,
            newRecords = listOf(
                NewRecordMark(NewRecordMark.Kind.LONGEST, null),
                NewRecordMark(NewRecordMark.Kind.BASE_DISTANCE, "KM10"),
                NewRecordMark(NewRecordMark.Kind.BASE_DISTANCE, "HALF")
            )
        )
        val plain = summary("p", 20, 10_000).copy(isNewRecord = true)
        val battle = summary("b", 20, 5_000, kind = CardKind.BATTLE_RUN)
            .copy(battleType = RecordBattleType.RANDOM, verdict = RecordVerdict.WIN, isNewRecord = true)

        assertEquals(
            listOf(
                res(R.string.record_tag_longest),
                res(R.string.record_tag_best_at, UiText.Plain("10km")),
                res(R.string.record_tag_best_at, res(R.string.record_distance_half))
            ),
            RecordTag.textsOf(marked)
        )
        assertEquals(listOf(res(R.string.record_tag_new_record)), RecordTag.textsOf(plain))
        assertEquals(listOf(res(R.string.record_tag_battle_win)), RecordTag.textsOf(battle))
    }

    @Test
    fun `TC-9733 이번 주는 월요일부터 오늘까지이고 나머지는 이전이다`() {
        val zone = ZoneId.of("Asia/Seoul")
        val runs = listOf(summary("tue", 20, 1_000), summary("mon", 19, 1_000), summary("sun", 18, 1_000))
        val (thisWeek, earlier) = splitByWeek(runs, LocalDate.of(2026, 10, 20), zone)
        assertEquals(listOf("tue", "mon"), thisWeek.map { it.recordId })
        assertEquals(listOf("sun"), earlier.map { it.recordId })
    }

    @Test
    fun `TC-9734 가장 빠른 1km는 구간 페이스 최솟값 위치이고 같으면 앞 구간, 없으면 null이다`() {
        assertEquals(2, fastestSplit(listOf(Pace(370), Pace(318), Pace(318), Pace(360))))
        assertEquals(1, fastestSplit(listOf(Pace(300))))
        assertEquals(null, fastestSplit(emptyList()))
    }

    @Test
    fun `페이스는 분과 초 기호로 적는다`() {
        assertEquals("6'09\"", paceMarks(Pace(369)))
        assertEquals("-'--\"", paceMarks(null))
    }
}
