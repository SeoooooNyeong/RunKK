package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.battle.BattleRepository
import com.a207.runkk.app.domain.battle.BattleRunCoordinator
import com.a207.runkk.app.domain.battle.RecordBattleStart
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.update

/** 셀럽 탭: 유명인 공개 기록 대결(FR-097·FR-100). 거리로 거르고, 셀프런처럼 카드를 골라 "대결하기"로 시작한다. */
@HiltViewModel
class CelebBattleViewModel @Inject constructor(battles: BattleRepository, coordinator: BattleRunCoordinator) :
    RecordBattleViewModel(battles, coordinator, OpponentKind.CELEBRITY) {
    init {
        load()
    }

    override suspend fun fetch() = battles.celebrityCandidates(CELEB_FILTERS[mutableState.value.filterIndex].second)

    override suspend fun cached() =
        battles.cachedCelebrityCandidates(CELEB_FILTERS[mutableState.value.filterIndex].second)

    override suspend fun begin(candidate: CandidateUi): Outcome<RecordBattleStart> {
        val id = candidate.celebrityId ?: return Outcome.Failure(AppError.Rejected("CELEBRITY_REMOVED"))
        return battles.startCelebrityBattle(id)
    }

    /** 거리를 바꾸면 그 거리에 없는 선택은 바로 푼다. 남은 선택은 다시 불러온 목록에 있을 때만 지킨다(load). */
    fun onFilter(index: Int) {
        if (index == mutableState.value.filterIndex || index !in CELEB_FILTERS.indices) return
        val distance = CELEB_FILTERS[index].second
        mutableState.update { s ->
            s.copy(filterIndex = index, selected = s.selected?.takeIf { distance == null || it.distance == distance })
        }
        load()
    }
}

/** 유명인 거리 거르기(FR-097): 라벨 → 거리(전체는 null). */
val CELEB_FILTERS: List<Pair<UiText, Distance?>> = listOf(
    UiText.Res(R.string.battle_filter_all) to null,
    UiText.Plain(RunkkFormat.targetLabel(Distance(5_000))) to Distance(5_000),
    UiText.Plain(RunkkFormat.targetLabel(Distance(10_000))) to Distance(10_000),
    UiText.Res(R.string.battle_distance_half) to Distance(HALF_MARATHON_METERS),
    UiText.Res(R.string.battle_distance_full) to Distance(FULL_MARATHON_METERS)
)
