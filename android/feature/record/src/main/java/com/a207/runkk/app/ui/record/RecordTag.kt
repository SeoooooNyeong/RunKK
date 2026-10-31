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
 * 보류면 종류만), 신기록이면 러닝 종류와 관계없이(셀프런·셀럽런·대결 포함, 사용자 결정 D52, U8-3) 신기록 종류 문구
 * ("최장 거리", "10km 최고")를, 종류가 없으면 "신기록"만 붙인다.
 * 집계 제외 기록은 맨 앞에 "집계 제외"를 붙인다(D29 번복, 사용자 요청 2026-10-30). 유효 기록에는 따로 붙이지 않는다.
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
    NEW_RECORD(R.string.record_tag_new_record),
    EXCLUDED(R.string.record_tag_excluded);

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

        /** 행에 붙일 태그 문구 전부(종류, 신기록). 크루가 닫혀 있으면([isCrewOpen] 거짓, 1차 배포) 크루런 태그는 뺀다(AN8-12). */
        fun textsOf(run: RunSummary, isCrewOpen: Boolean = true): List<UiText> {
            val kind = kindOf(run)?.takeUnless { it == CREW && !isCrewOpen }
            val records = if (run.isNewRecord) newRecordTexts(run.newRecords) else emptyList()
            val excluded = if (run.isValid) null else UiText.Res(EXCLUDED.labelRes)
            return listOfNotNull(excluded, kind?.let { UiText.Res(it.labelRes) }) + records
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
