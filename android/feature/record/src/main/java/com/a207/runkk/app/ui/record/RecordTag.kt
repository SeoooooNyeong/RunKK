package com.a207.runkk.app.ui.record

import androidx.annotation.StringRes
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.record.NewRecordMark
import com.a207.runkk.app.domain.record.RecordBattleType
import com.a207.runkk.app.domain.record.RecordVerdict
import com.a207.runkk.app.domain.record.RunSummary

/**
 * 기록 목록·달력 줄의 태그(D-279). 자유런이 아니면 종류 태그 하나(배틀런·친구 대결은 승패가 정해졌을 때 승패를 붙인다,
 * 보류면 종류만), 대결이 아닌 신기록이면 신기록 종류 문구("최장 거리", "10km 최고")를, 종류가 없으면 "신기록"만 붙인다.
 * 유효·집계 제외는 태그로 보이지 않는다(D29).
 */
enum class RecordTag(@StringRes val labelRes: Int) {
    BATTLE(R.string.record_tag_battle),
    BATTLE_WIN(R.string.record_tag_battle_win),
    BATTLE_LOSE(R.string.record_tag_battle_lose),
    BATTLE_DRAW(R.string.record_tag_battle_draw),
    FRIEND(R.string.record_tag_friend),
    FRIEND_WIN(R.string.record_tag_friend_win),
    FRIEND_LOSE(R.string.record_tag_friend_lose),
    FRIEND_DRAW(R.string.record_tag_friend_draw),
    SELF(R.string.record_tag_self),
    CELEB(R.string.record_tag_celeb),
    CREW(R.string.record_tag_crew),
    NEW_RECORD(R.string.record_tag_new_record);

    companion object {
        /** 종류 태그. 자유런은 null. 서버 battleType이 없으면(옛 서버) 카드 종류로 정한다(D-289). */
        fun kindOf(run: RunSummary): RecordTag? {
            val verdict = run.verdict.takeUnless { run.isPending }
            return when (run.battleType ?: battleTypeOf(run.cardKind)) {
                RecordBattleType.RANDOM -> verdict.pick(BATTLE, BATTLE_WIN, BATTLE_LOSE, BATTLE_DRAW)
                RecordBattleType.FRIEND -> verdict.pick(FRIEND, FRIEND_WIN, FRIEND_LOSE, FRIEND_DRAW)
                RecordBattleType.SELF -> SELF
                RecordBattleType.CELEBRITY -> CELEB
                null -> if (run.cardKind == CardKind.CREW_RUN) CREW else null
            }
        }

        /** 행에 붙일 태그 문구 전부(종류, 신기록). */
        fun textsOf(run: RunSummary): List<UiText> {
            val kind = kindOf(run)
            val isBattle = kind != null && kind != CREW
            val records = if (run.isNewRecord && !isBattle) newRecordTexts(run.newRecords) else emptyList()
            return listOfNotNull(kind?.let { UiText.Res(it.labelRes) }) + records
        }

        private fun battleTypeOf(kind: CardKind): RecordBattleType? = when (kind) {
            CardKind.BATTLE_RUN -> RecordBattleType.RANDOM
            CardKind.SELF_RUN -> RecordBattleType.SELF
            CardKind.CELEB_RUN -> RecordBattleType.CELEBRITY
            CardKind.FREE_RUN, CardKind.CREW_RUN -> null
        }

        private fun RecordVerdict?.pick(none: RecordTag, win: RecordTag, lose: RecordTag, draw: RecordTag) =
            when (this) {
                RecordVerdict.WIN -> win
                RecordVerdict.LOSE -> lose
                RecordVerdict.DRAW -> draw
                null -> none
            }

        private fun newRecordTexts(marks: List<NewRecordMark>): List<UiText> {
            val texts = marks.mapNotNull { mark ->
                when (mark.kind) {
                    NewRecordMark.Kind.LONGEST -> UiText.Res(R.string.record_tag_longest)

                    NewRecordMark.Kind.BASE_DISTANCE ->
                        baseDistanceLabel(mark.distance)?.let { UiText.Res(R.string.record_tag_best_at, listOf(it)) }
                }
            }
            return texts.ifEmpty { listOf(UiText.Res(NEW_RECORD.labelRes)) }
        }

        /** 서버 StandardDistance 이름 → "5km"·"하프"·"풀". 모르는 이름은 null. */
        private fun baseDistanceLabel(name: String?): UiText? = when (name) {
            "HALF" -> UiText.Res(R.string.record_distance_half)
            "FULL" -> UiText.Res(R.string.record_distance_full)
            else -> BASE_KM[name]?.let { UiText.Plain(RunkkFormat.targetLabel(Distance(it))) }
        }

        private val BASE_KM: Map<String?, Long> =
            mapOf("KM1" to 1_000L, "KM3" to 3_000L, "KM5" to 5_000L, "KM10" to 10_000L)
    }
}
