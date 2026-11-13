package com.a207.runkk.app.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.ProfileEdit
import com.a207.runkk.app.domain.account.SignUpForm
import com.a207.runkk.app.domain.account.SignUpUseCase
import com.a207.runkk.app.domain.account.StartAsGuestUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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
 * 동의 화면 하나(가입·게스트 공용, FR-003·FR-004·FR-028·FR-197, D-90). 같은 동의로 가입(SignUpUseCase)하거나
 * 게스트로 시작(StartAsGuestUseCase)한다. 닉네임은 보내지 않고 서버가 정한다(D-143). 거부는 화면에 남겨 보인다.
 * 게스트로 시작하면 홈으로 가기 전에 자료 유실 고지를 보인다(FR-199).
 * 동의 다음에 성별(남·여) 단계를 두고, 그 단계의 시작하기에서 계정을 만든 뒤 고른 성별을 프로필 성별로 저장한다(S15P31A207-300).
 */
@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val signUp: SignUpUseCase,
    private val startAsGuest: StartAsGuestUseCase,
    private val account: AccountRepository
) : ViewModel() {
    private val _state = MutableStateFlow(SignUpUiState())
    val state: StateFlow<SignUpUiState> = _state.asStateFlow()

    private val _events = Channel<SignUpEvent>(Channel.BUFFERED)
    val events: Flow<SignUpEvent> = _events.receiveAsFlow()

    fun onAllAgreedChange(isAgreed: Boolean) = _state.update { s ->
        s.copy(consents = s.consents.mapValues { isAgreed })
    }

    fun onConsentChange(item: String, isAgreed: Boolean) = _state.update { s ->
        s.copy(consents = s.consents + (item to isAgreed))
    }

    fun onErrorShown() = _state.update { it.copy(error = null) }

    /** "로그인으로 돌아가기"와 동의 단계의 뒤로. */
    fun onExit() {
        _events.trySend(SignUpEvent.Exit)
    }

    fun onViewLegal(item: String) {
        val line = SignUpConsents.items.firstOrNull { it.item == item } ?: return
        val type = line.legalType ?: return
        viewModelScope.launch {
            when (val doc = account.legalDocument(type)) {
                is Outcome.Success -> {
                    val link = legalLinkOrNull(doc.value)
                    if (link != null) {
                        _events.send(SignUpEvent.OpenUrl(link))
                    } else {
                        _state.update { it.copy(legal = LegalView(UiText.Res(line.labelRes), doc.value.trim())) }
                    }
                }

                is Outcome.Failure -> _state.update { it.copy(error = RunkkErrorText.of(doc.error)) }
            }
        }
    }

    fun onLegalDismissed() = _state.update { it.copy(legal = null) }

    /** 브라우저가 없거나 막혀 고지 문서 주소를 열지 못했다(FR-018 필수 고지라 안내한다). */
    fun onLegalOpenFailed() = _state.update { it.copy(error = UiText.Res(R.string.me_legal_open_failed)) }

    /** 뒤로: 성별 단계면 동의로(고른 동의 그대로), 동의 단계면 로그인으로. 계정을 만든 뒤 성별 단계에서는 머문다(앞으로만 간다). */
    fun onBack() {
        val s = _state.value
        when {
            s.step == SignUpStep.CONSENT -> onExit()
            s.canGoBackToConsent -> _state.update { it.copy(step = SignUpStep.CONSENT, submitError = null) }
        }
    }

    /** 동의 단계의 시작하기: 아직 보내지 않고 성별 단계로 간다(S15P31A207-300). 필수 동의가 빠지면 넘어가지 않는다. */
    fun onConsentNext() {
        if (_state.value.step != SignUpStep.CONSENT || !_state.value.canSubmit) return
        _state.update { it.copy(step = SignUpStep.GENDER, submitError = null) }
    }

    /** 성별 고르기. 남자·여자만 받는다("밝히지 않음" 없음). */
    fun onGenderSelect(gender: Gender) {
        if (gender !in SELECTABLE_GENDERS || _state.value.isSubmitting) return
        _state.update { it.copy(gender = gender) }
    }

    /**
     * 성별 단계의 시작하기: [isGuest]면 게스트로, 아니면 소셜 계정 가입으로 계정을 만들고(토큰 저장까지), 고른 성별을 프로필 성별로
     * 저장한 뒤 홈으로(게스트는 자료 유실 고지 뒤) 간다. 가입 요청에는 성별이 없어(서버 SignUpRequest) 따로 PATCH /me/profile로 저장한다.
     * 성별 저장이 실패하면 단계에 남아 안내하고, 다시 누르면 계정은 다시 만들지 않고 성별만 저장한다.
     */
    fun onStart(isGuest: Boolean) {
        val s = _state.value
        val gender = s.gender ?: return
        if (!s.canStart) return
        _state.update { it.copy(isSubmitting = true, error = null, submitError = null) }
        viewModelScope.launch {
            if (!s.isAccountCreated) {
                val result = if (isGuest) startAsGuest(s.consents) else signUp(SignUpForm(nickname = null, s.consents))
                if (result is Outcome.Failure) {
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            submitError = submitErrorText(result.error),
                            canRetrySubmit = (result.error as? AppError.Rejected)?.code !in LOGIN_AGAIN
                        )
                    }
                    return@launch
                }
                _state.update { it.copy(isAccountCreated = true) }
            }
            saveGender(gender, isGuest)
        }
    }

    /**
     * 고른 성별을 프로필 성별로 저장한다(PATCH /me/profile). 닉네임·소개는 null(그대로 둠)이고, 함께 보내야 하는 나이대·성별 공개는
     * 본인 프로필에서 읽은 값을 그대로 보낸다. 읽지 못하면 새 계정의 서버 기본(비공개)이다. 저장이 성공하면 저장소가 내 성별(myGender)을
     * 바로 바꿔 홈·꾸미기·대결의 기본 몸이 이 성별이 된다. 게스트는 같은 프로필 응답의 닉네임을 고지에 쓴다.
     */
    private suspend fun saveGender(gender: Gender, isGuest: Boolean) {
        val profile = account.profile(AccountRepository.SELF).getOrNull()
        if (isGuest && profile != null) _state.update { it.copy(guestNickname = profile.displayName) }
        val edit = ProfileEdit(
            nickname = null,
            intro = null,
            isAgePublic = profile?.isAgePublic ?: false,
            isGenderPublic = profile?.isGenderPublic ?: false,
            gender = gender
        )
        when (val saved = account.editProfile(edit)) {
            is Outcome.Success -> {
                _state.update { it.copy(isSubmitting = false, isGuestNoticeVisible = isGuest) }
                if (!isGuest) _events.send(SignUpEvent.Completed)
            }

            is Outcome.Failure -> _state.update {
                it.copy(isSubmitting = false, submitError = RunkkErrorText.of(saved.error), canRetrySubmit = true)
            }
        }
    }

    /** 게스트 안내 "나중에": 홈으로. */
    fun onGuestNoticeConfirmed() = leaveGuestNotice(SignUpEvent.Completed)

    /** 게스트 안내 "지금 연동하기": 소셜 계정 연동(F9)으로. */
    fun onGuestNoticeLinkTapped() = leaveGuestNotice(SignUpEvent.CompletedToLink)

    private fun leaveGuestNotice(event: SignUpEvent) {
        if (!_state.value.isGuestNoticeVisible) return
        _state.update { it.copy(isGuestNoticeVisible = false) }
        _events.trySend(event)
    }

    /** 게스트 생성 접속지 한도(429, FR-197)는 서버 일시 오류가 아니라 한도 안내로 보인다. */
    private fun submitErrorText(error: AppError): UiText = if (error ==
        AppError.Server(TOO_MANY_REQUESTS)
    ) {
        UiText.Res(R.string.me_guest_too_many)
    } else {
        RunkkErrorText.of(error)
    }

    private companion object {
        const val TOO_MANY_REQUESTS = 429

        /** 같은 입력으로 다시 보내도 소용없는 사유: 가입 티켓(15분)이 끝났거나 이미 가입한 계정. 로그인부터 다시 한다. */
        val LOGIN_AGAIN = setOf("SIGN_UP_TICKET_INVALID", "ALREADY_MEMBER", AppError.Rejected.SIGN_UP_REQUIRED)

        /** 성별 단계에서 고를 수 있는 성별. "밝히지 않음"은 없다(S15P31A207-300). */
        val SELECTABLE_GENDERS = setOf(Gender.MALE, Gender.FEMALE)
    }
}
