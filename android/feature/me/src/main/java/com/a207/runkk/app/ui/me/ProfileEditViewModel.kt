package com.a207.runkk.app.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.takeCodePoints
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.ProfileEdit
import com.a207.runkk.app.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Year
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 프로필 편집(G-2, D-287, FR-007·FR-012): 닉네임·소개와 나이대·성별 공개 스위치를 한 번에 PATCH /me/profile로 보낸다
 * (AccountRepository.editProfile(ProfileEdit)). 검증(중복·길이)은 서버가 하고 거부 사유를 보인다.
 * 공개 여부는 본인 프로필 응답의 ageBandVisibility·genderVisibility로 채우고, 옛 서버라 없으면 비공개로 둔다(D-289).
 * 출생연도는 설정 사본에서 채우고 함께 보낸다(만 14세 이하면 서버가 UNDER_14로 거부, 2026-10-30).
 */
@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    private val account: AccountRepository,
    private val settings: SettingsRepository
) : ViewModel() {
    // 기기에 남긴 마지막 내 얼굴을 첫 값으로 그린다(프로필을 읽는 동안 중립 자리·기본 머리를 거치지 않게).
    private val _state = MutableStateFlow(
        account.lastMyAvatar()?.let { ProfileEditUiState(faceKey = it.faceKey, faceLayers = it.faceLayers) }
            ?: ProfileEditUiState()
    )
    val state: StateFlow<ProfileEditUiState> = _state.asStateFlow()

    private val _events = Channel<ProfileEditEvent>(Channel.BUFFERED)
    val events: Flow<ProfileEditEvent> = _events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            // 출생연도는 본인 프로필 응답에 없고 설정 사본에 있다. 새로 읽지 못하면 이전 사본을 쓴다.
            settings.refresh()
            val birthYear = settings.birthYear()
            when (val result = account.profile(AccountRepository.SELF)) {
                is Outcome.Success -> _state.update {
                    val me = result.value
                    // 받아 둔 그림이 있으면 바로 그리고, 같은 얼굴이면 지금 그림을 둔다.
                    val cached = account.cachedFaceLayers(me.faceKey)
                    val isSameFace = it.faceKey == me.faceKey
                    val layers = cached ?: it.faceLayers.takeIf { isSameFace }.orEmpty()
                    it.copy(
                        isLoading = false,
                        faceKey = me.faceKey,
                        faceLayers = layers,
                        // 얼굴 층을 받을 때까지 기본 머리를 보이지 않는다([임시:D-성-190]).
                        isFaceLoading = me.faceKey != null && cached == null && layers.isEmpty(),
                        nickname = me.displayName,
                        intro = me.intro.orEmpty(),
                        ageBand = me.ageBand,
                        birthYear = birthYear,
                        gender = me.gender,
                        isAgePublic = me.isAgePublic ?: false,
                        isGenderPublic = me.isGenderPublic ?: false
                    )
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, error = RunkkErrorText.of(result.error))
                }
            }
            // 아바타 꾸미기가 열렸을 때 쓰는 얼굴 층. 못 읽으면 기본 얼굴이다.
            val faceKey = _state.value.faceKey ?: return@launch
            if (account.cachedFaceLayers(faceKey) != null) return@launch
            val face = account.faceLayers(faceKey).getOrNull()
            _state.update { it.copy(faceLayers = face ?: it.faceLayers, isFaceLoading = false) }
        }
    }

    fun onNicknameChange(value: String) = _state.update {
        it.copy(nickname = value.takeCodePoints(ProfileEditUiState.NICKNAME_LENGTH.last))
    }

    fun onIntroChange(value: String) = _state.update { it.copy(intro = value.take(ProfileEditUiState.INTRO_MAX)) }

    /** 출생연도를 고르면 나이대 표시도 서버 규칙(MemberQueryService.profileOf: 올해 − 출생연도를 10 단위로)대로 바로 바꾼다. */
    fun onBirthYearChange(year: Int?) = _state.update {
        it.copy(birthYear = year, ageBand = year?.let { y -> "${(Year.now().value - y) / AGE_BAND * AGE_BAND}대" })
    }

    fun onAgePublicChange(isPublic: Boolean) = _state.update { it.copy(isAgePublic = isPublic) }

    fun onGenderPublicChange(isPublic: Boolean) = _state.update { it.copy(isGenderPublic = isPublic) }

    /** 성별을 고른다. 여성이면 기본 캐릭터가 여자로 바뀐다(저장 뒤). */
    fun onGenderChange(gender: Gender) = _state.update { it.copy(gender = gender) }

    fun onErrorShown() = _state.update { it.copy(error = null) }

    private companion object {
        const val AGE_BAND = 10
    }

    fun onSave() {
        val s = _state.value
        if (s.isSaving) return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val edit = ProfileEdit(
                nickname = s.nickname.trim(),
                intro = s.intro.trim().ifEmpty { null },
                isAgePublic = s.isAgePublic,
                isGenderPublic = s.isGenderPublic,
                gender = s.gender,
                birthYear = s.birthYear
            )
            when (val saved = account.editProfile(edit)) {
                is Outcome.Success -> {
                    // 호흡 안내 심박 기준이 출생연도를 쓰므로 설정 사본을 새로 읽는다.
                    if (edit.birthYear != null) settings.refresh()
                    // 저장 중 상태를 풀지 않는다: 화면이 닫히는 전환 사이 저장을 또 누르면 한 번 더 뒤로 가 홈에서 앱이 닫혔다
                    // (사용자 확인 2026-10-31). 이 화면은 곧 닫힌다.
                    _events.send(ProfileEditEvent.Saved)
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isSaving = false, error = RunkkErrorText.of(saved.error))
                }
            }
        }
    }
}
