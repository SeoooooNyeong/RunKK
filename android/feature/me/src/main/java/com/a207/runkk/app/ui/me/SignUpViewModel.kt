package com.a207.runkk.app.ui.me

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.RunkkTags
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.Gender
import com.a207.runkk.app.domain.account.SignUpForm
import com.a207.runkk.app.domain.account.SignUpUseCase
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
 * 가입 단계(FR-003, 와이어프레임 A2~A4): 1 약관·동의, 2·3 프로필·공개 범위(한 화면), 4 성향 태그, 5 아바타.
 * [number]는 상단 "n / 5" 표시.
 */
enum class SignUpStep(val number: Int) {
    CONSENT(1),
    PROFILE(2),
    TAGS(4),
    AVATAR(5);

    companion object {
        const val TOTAL = 5
    }
}

/** 가입 동의 항목(서버 ConsentItem 이름). [legalType]은 서버 DocumentType 이름이고, 있으면 "보기"로 고지문서를 연다(FR-018). 서버에 AI 코멘트용 전송만의 문서가 없어 개인정보 처리방침을 연다. */
data class ConsentLine(
    val item: String,
    @StringRes val labelRes: Int,
    val isRequired: Boolean,
    val legalType: String? = null
)

object SignUpConsents {
    val items: List<ConsentLine> = listOf(
        ConsentLine("AGE_14_PLUS", R.string.me_signup_consent_age, isRequired = true),
        ConsentLine("TERMS", R.string.me_legal_terms, isRequired = true, legalType = LegalTypes.TERMS),
        ConsentLine(
            "PRIVACY",
            R.string.me_consents_privacy_collect,
            isRequired = true,
            legalType = LegalTypes.PRIVACY_POLICY
        ),
        ConsentLine(
            "LOCATION",
            R.string.me_consents_location_title,
            isRequired = true,
            legalType = LegalTypes.LOCATION_TERMS
        ),
        ConsentLine("MARKETING", R.string.me_consents_marketing_title, isRequired = false),
        ConsentLine(
            "LLM_TRANSFER",
            R.string.me_consents_llm_title,
            isRequired = false,
            legalType = LegalTypes.PRIVACY_POLICY
        )
    )
}

/** 기록 공개 범위(FR-010·FR-028, 서버 RecordVisibility 이름). 기본 전체공개. */
object RecordVisibilityOptions {
    val values: List<Pair<String, Int>> = listOf(
        "PUBLIC" to R.string.me_choice_record_public,
        "CREW" to R.string.me_choice_record_crew,
        "PRIVATE" to R.string.me_choice_private
    )
}

/** 고지문서 본문 보기. */
data class LegalView(val title: UiText, val body: String)

data class SignUpUiState(
    val step: SignUpStep = SignUpStep.CONSENT,
    val consents: Map<String, Boolean> = SignUpConsents.items.associate { it.item to false },
    val nickname: String = "",
    val birthYear: Int? = null,
    val gender: Gender? = null,
    val intro: String = "",
    val recordVisibility: String = "PUBLIC",
    val tags: Set<String> = emptySet(),
    val isSubmitting: Boolean = false,
    val error: UiText? = null,
    /**
     * 가입 요청이 거부·실패했을 때의 안내(RunkkErrorText). 스낵바와 달리 다음 시도·단계 이동까지 화면에 남는다.
     * 5단계에서 실패해도 사용자가 멈춘 것으로 보지 않게 한다.
     */
    val submitError: UiText? = null,
    /** [submitError]가 있을 때 같은 입력으로 다시 보낼 수 있는가(가입 시간이 지났거나 이미 가입한 계정이면 로그인부터 다시). */
    val canRetrySubmit: Boolean = true,
    /** 마지막으로 가입을 시도한 쪽(아바타 만들기로 이어지는가). 다시 시도가 같은 쪽으로 보낸다. */
    val shouldLastCreateAvatar: Boolean = false,
    val legal: LegalView? = null
) {
    val isAllAgreed: Boolean get() = consents.values.all { it }

    /** 이 단계의 필수 값을 채웠는가(FR-003 "단계마다 필수 값을 채우기 전에는 다음 단계로 넘어가지 않는다"). */
    val canProceed: Boolean
        get() = when (step) {
            SignUpStep.CONSENT -> SignUpConsents.items.filter { it.isRequired }.all { consents[it.item] == true }
            SignUpStep.PROFILE -> nickname.isBlank() || nickname.trim().length in NICKNAME_LENGTH
            SignUpStep.TAGS -> tags.isNotEmpty()
            SignUpStep.AVATAR -> !isSubmitting
        }

    companion object {
        val NICKNAME_LENGTH = 2..20
        const val INTRO_MAX = 40
    }
}

sealed interface SignUpEvent {
    /** 가입 완료. [shouldCreateAvatar]면 아바타 만들기로, 아니면 홈으로. */
    data class Completed(val shouldCreateAvatar: Boolean) : SignUpEvent

    /** 첫 단계에서 뒤로: 로그인으로. */
    data object Exit : SignUpEvent

    /** 고지문서가 주소면 밖(브라우저)에서 연다. */
    data class OpenUrl(val url: String) : SignUpEvent
}

/**
 * 단계별 가입(FR-003, FR-004, FR-028). 입력은 단계마다 모으고 마지막에 SignUpUseCase(가입 → 세션 저장 → 설정 사본)으로 보낸다.
 * 거부 사유(서버 6.3 1.2 의사결정표)에 맞는 단계로 돌아가 문구를 보인다.
 */
@HiltViewModel
class SignUpViewModel @Inject constructor(private val signUp: SignUpUseCase, private val account: AccountRepository) :
    ViewModel() {
    private val _state = MutableStateFlow(SignUpUiState())
    val state: StateFlow<SignUpUiState> = _state.asStateFlow()

    private val _events = Channel<SignUpEvent>(Channel.BUFFERED)
    val events: Flow<SignUpEvent> = _events.receiveAsFlow()

    fun onAllAgreedChange(isAgreed: Boolean) = _state.update { s ->
        s.copy(consents = s.consents.mapValues { isAgreed })
    }

    fun onConsentChange(item: String, isAgreed: Boolean) = _state.update { s ->
        s.copy(
            consents =
                s.consents + (item to isAgreed)
        )
    }

    fun onNicknameChange(value: String) = _state.update {
        it.copy(nickname = value.take(SignUpUiState.NICKNAME_LENGTH.last))
    }

    fun onBirthYearChange(year: Int?) = _state.update { it.copy(birthYear = year) }

    fun onGenderChange(gender: Gender) = _state.update { it.copy(gender = gender) }

    fun onIntroChange(value: String) = _state.update { it.copy(intro = value.take(SignUpUiState.INTRO_MAX)) }

    fun onVisibilityChange(value: String) = _state.update { it.copy(recordVisibility = value) }

    fun onTagToggled(id: String) = _state.update { it.copy(tags = RunkkTags.toggle(it.tags, id)) }

    fun onErrorShown() = _state.update { it.copy(error = null) }

    /** 가입 거부 안내의 "다시 시도": 마지막과 같은 쪽(아바타 만들기 여부)으로 다시 보낸다. */
    fun onRetrySubmit() = onSubmit(_state.value.shouldLastCreateAvatar)

    /** 가입 시간이 지났거나 이미 가입한 계정이면 로그인 화면으로 돌아간다. */
    fun onExit() {
        _events.trySend(SignUpEvent.Exit)
    }

    fun onNext() {
        val current = _state.value
        if (!current.canProceed) return
        val next = when (current.step) {
            SignUpStep.CONSENT -> SignUpStep.PROFILE
            SignUpStep.PROFILE -> SignUpStep.TAGS
            SignUpStep.TAGS -> SignUpStep.AVATAR
            SignUpStep.AVATAR -> return
        }
        _state.update { it.copy(step = next, error = null, submitError = null) }
    }

    fun onBack() {
        val previous = when (_state.value.step) {
            SignUpStep.CONSENT -> {
                _events.trySend(SignUpEvent.Exit)
                return
            }

            SignUpStep.PROFILE -> SignUpStep.CONSENT

            SignUpStep.TAGS -> SignUpStep.PROFILE

            SignUpStep.AVATAR -> SignUpStep.TAGS
        }
        _state.update { it.copy(step = previous, error = null, submitError = null) }
    }

    fun onViewLegal(item: String) {
        val line = SignUpConsents.items.firstOrNull { it.item == item } ?: return
        val type = line.legalType ?: return
        viewModelScope.launch {
            when (val doc = account.legalDocument(type)) {
                is Outcome.Success -> {
                    val body = doc.value.trim()
                    if (body.startsWith("http://") || body.startsWith("https://")) {
                        _events.send(SignUpEvent.OpenUrl(body))
                    } else {
                        _state.update { it.copy(legal = LegalView(UiText.Res(line.labelRes), body)) }
                    }
                }

                is Outcome.Failure -> _state.update { it.copy(error = RunkkErrorText.of(doc.error)) }
            }
        }
    }

    fun onLegalDismissed() = _state.update { it.copy(legal = null) }

    fun onSubmit(shouldCreateAvatar: Boolean) {
        val s = _state.value
        if (s.isSubmitting) return
        _state.update {
            it.copy(isSubmitting = true, error = null, submitError = null, shouldLastCreateAvatar = shouldCreateAvatar)
        }
        val form = SignUpForm(
            nickname = s.nickname.trim().ifEmpty { null },
            birthYear = s.birthYear,
            gender = s.gender ?: Gender.UNDISCLOSED,
            intro = s.intro.trim().ifEmpty { null },
            consents = s.consents,
            recordVisibility = s.recordVisibility,
            tags = s.tags
        )
        viewModelScope.launch {
            when (val result = signUp(form)) {
                is Outcome.Success -> {
                    _state.update { it.copy(isSubmitting = false) }
                    _events.send(SignUpEvent.Completed(shouldCreateAvatar))
                }

                is Outcome.Failure -> _state.update {
                    it.copy(
                        isSubmitting = false,
                        step = stepFor(result.error) ?: it.step,
                        submitError = RunkkErrorText.of(result.error),
                        canRetrySubmit = (result.error as? AppError.Rejected)?.code !in LOGIN_AGAIN
                    )
                }
            }
        }
    }

    /** 거부 사유에 맞는 단계. 모르는 사유면 그대로 머문다. */
    private fun stepFor(error: AppError): SignUpStep? = when ((error as? AppError.Rejected)?.code) {
        "CONSENT_MISSING", "UNDER_14" -> SignUpStep.CONSENT
        "NICKNAME_INVALID", "INTRO_TOO_LONG", "VALUE_INVALID" -> SignUpStep.PROFILE
        "TAG_INVALID" -> SignUpStep.TAGS
        else -> null
    }
}

/** 같은 입력으로 다시 보내도 소용없는 사유: 가입 티켓(15분)이 끝났거나 이미 가입한 계정. 로그인부터 다시 한다. */
private val LOGIN_AGAIN = setOf("SIGN_UP_TICKET_INVALID", "ALREADY_MEMBER", AppError.Rejected.SIGN_UP_REQUIRED)
