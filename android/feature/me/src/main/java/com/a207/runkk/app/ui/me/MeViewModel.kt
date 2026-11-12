package com.a207.runkk.app.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.TierDisplay
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.account.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 마이(F2, FR-005): 본인 프로필과 배치 진행(티어 배지, FR-282)을 읽는다. 프로필 편집 저장 뒤 돌아오면(화면 재개) 다시 읽는다(AN8-3).
 * 로그아웃은 설정(SettingsViewModel)이 한다.
 */
@HiltViewModel
class MeViewModel @Inject constructor(private val account: AccountRepository) : ViewModel() {
    private val _state = MutableStateFlow(MeUiState())
    val state: StateFlow<MeUiState> = _state.asStateFlow()

    private var refreshJob: Job? = null
    private var resumedOnce = false

    init {
        refresh()
    }

    /** 화면이 다시 보일 때(프로필 편집 저장 뒤 복귀 등) 다시 읽는다. 처음 표시는 init이 읽으므로 첫 재개는 건너뛴다. */
    fun onResumed() {
        if (resumedOnce) refresh()
        resumedOnce = true
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        // 늦게 끝난 앞 요청이 새 결과를 덮지 않게 취소한다.
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            when (val result = account.profile(AccountRepository.SELF)) {
                is Outcome.Success -> {
                    // 얼굴은 프로필과 같은 그림(첫 그림)부터 그린다: 받아 둔 그림(홈이 받은 내 얼굴, 기기에 남긴 마지막 모습)이 있으면
                    // 바로 쓰고, 같은 얼굴이면 지금 그림을 둔다. 둘 다 없을 때만 받는 동안 중립 자리다(기본 머리를 거치지 않는다).
                    val faceKey = result.value.faceKey
                    val cached = account.cachedFaceLayers(faceKey)
                    _state.update {
                        val isSameFace = it.profile?.faceKey == faceKey && it.faceLayers.isNotEmpty()
                        val kept = it.faceLayers.takeIf { isSameFace }
                        it.copy(
                            isLoading = false,
                            profile = result.value,
                            faceLayers = cached ?: kept.orEmpty(),
                            isFaceLoading = cached == null && kept == null && faceKey != null
                        )
                    }
                    // 얼굴 받기와 배치 진행 읽기를 함께 한다(얼굴이 배치 진행을 기다리지 않게).
                    val face = faceKey?.takeIf { cached == null }
                        ?.let { key -> async { account.faceLayers(key).getOrNull() } }
                    val placement = account.placement().getOrNull()
                    val tier = placement?.let { TierDisplay.ofSelf(it.tier, it.provisionalTier) }
                        ?: TierDisplay.ofPublic(result.value.tier)
                    _state.update { it.copy(tierDisplay = tier) }
                    if (face != null) {
                        val layers = face.await()
                        _state.update { it.copy(faceLayers = layers ?: it.faceLayers, isFaceLoading = false) }
                    }
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, error = RunkkErrorText.of(result.error))
                }
            }
        }
    }
}
