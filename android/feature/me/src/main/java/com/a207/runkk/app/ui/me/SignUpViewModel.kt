package com.a207.runkk.app.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.RunkkTags
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.Gender
import com.a207.runkk.app.domain.account.LoginUseCase
import com.a207.runkk.app.domain.account.SignUpForm
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

/** 가입 동의 항목(서버 ConsentItem 이름). [legalType]이 있으면 "보기"로 고지문서를 연다(FR-018). */
data class ConsentLine(val item: String, val label: String, val required: Boolean, val legalType: String? = null)

object SignUpConsents {
    val items: List<ConsentLine> = listOf(
        ConsentLine("AGE_14_PLUS", "만 14세 이상이에요", required = true),
        ConsentLine("TERMS", "서비스 이용약관", required = true, legalType = "TERMS"),
        ConsentLine("PRIVACY", "개인정보 수집·이용", required = true, legalType = "PRIVACY"),
        ConsentLine("LOCATION", "위치 정보 수집", required = true, legalType = "LOCATION"),
        ConsentLine("MARKETING", "마케팅 정보 수신", required = false),
        ConsentLine("LLM_TRANSFER", "AI 코멘트용 집계값 외부 전송", required = false, legalType = "LLM_TRANSFER")
    )
}

/** 기록 공개 범위(FR-010·FR-028, 서버 RecordVisibility 이름). 기본 전체공개. */
object RecordVisibilityOptions {
    val values: List<Pair<String, String>> = listOf("PUBLIC" to "전체공개", "CREW" to "크루공개", "PRIVATE" to "비공개")
}

/** 고지문서 본문 보기. */
data class LegalView(val title: String, val body: String)

data class SignUpUiState(
    val step: SignUpStep = SignUpStep.CONSENT,
    val consents: Map<String, Boolean> = SignUpConsents.items.associate { it.item to false },
    val nickname: String = "",
    val birthYear: Int? = null,
    val gender: Gender? = null,
    val intro: String = "",
    val recordVisibility: String = "PUBLIC",
    val tags: Set<String> = emptySet(),
    val submitting: Boolean = false,
    val error: String? = null,
    val legal: LegalView? = null
) {
    val allAgreed: Boolean get() = consents.values.all { it }

    /** 이 단계의 필수 값을 채웠는가(FR-003 "단계마다 필수 값을 채우기 전에는 다음 단계로 넘어가지 않는다"). */
    val canProceed: Boolean
        get() = when (step) {
            SignUpStep.CONSENT -> SignUpConsents.items.filter { it.required }.all { consents[it.item] == true }
            SignUpStep.PROFILE -> nickname.isBlank() || nickname.trim().length in NICKNAME_LENGTH
            SignUpStep.TAGS -> tags.isNotEmpty()
            SignUpStep.AVATAR -> !submitting
        }

    companion object {
        val NICKNAME_LENGTH = 2..20
        const val INTRO_MAX = 40
    }
}

sealed interface SignUpEvent {
    /** 가입 완료. [createAvatar]면 아바타 만들기로, 아니면 홈으로. */
    data class Completed(val createAvatar: Boolean) : SignUpEvent

    /** 첫 단계에서 뒤로: 로그인으로. */
    data object Exit : SignUpEvent

    /** 고지문서가 주소면 밖(브라우저)에서 연다. */
    data class OpenUrl(val url: String) : SignUpEvent
}

/**
 * 단계별 가입(FR-003, FR-004, FR-028). 입력은 단계마다 모으고 마지막에 LoginUseCase.signUp(가입 → 세션 저장 → 설정 사본)으로 보낸다.
 * 거부 사유(서버 6.3 1.2 의사결정표)에 맞는 단계로 돌아가 문구를 보인다.
 */
@HiltViewModel
class SignUpViewModel @Inject constructor(private val login: LoginUseCase, private val account: AccountRepository) :
    ViewModel() {
    private val _state = MutableStateFlow(SignUpUiState())
    val state: StateFlow<SignUpUiState> = _state.asStateFlow()

    private val _events = Channel<SignUpEvent>(Channel.BUFFERED)
    val events: Flow<SignUpEvent> = _events.receiveAsFlow()

    fun onAllAgreedChange(agreed: Boolean) = _state.update { s -> s.copy(consents = s.consents.mapValues { agreed }) }

    fun onConsentChange(item: String, agreed: Boolean) = _state.update { s ->
        s.copy(
            consents =
                s.consents + (item to agreed)
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

    fun onNext() {
        val current = _state.value
        if (!current.canProceed) return
        val next = when (current.step) {
            SignUpStep.CONSENT -> SignUpStep.PROFILE
            SignUpStep.PROFILE -> SignUpStep.TAGS
            SignUpStep.TAGS -> SignUpStep.AVATAR
            SignUpStep.AVATAR -> return
        }
        _state.update { it.copy(step = next, error = null) }
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
        _state.update { it.copy(step = previous, error = null) }
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
                        _state.update { it.copy(legal = LegalView(line.label, body)) }
                    }
                }

                is Outcome.Failure -> _state.update { it.copy(error = RunkkErrorText.of(doc.error)) }
            }
        }
    }

    fun onLegalDismissed() = _state.update { it.copy(legal = null) }

    fun onSubmit(createAvatar: Boolean) {
        val s = _state.value
        if (s.submitting) return
        _state.update { it.copy(submitting = true, error = null) }
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
            when (val result = login.signUp(form)) {
                is Outcome.Success -> {
                    _state.update { it.copy(submitting = false) }
                    _events.send(SignUpEvent.Completed(createAvatar))
                }

                is Outcome.Failure -> _state.update {
                    it.copy(
                        submitting = false,
                        step = stepFor(result.error) ?: it.step,
                        error = RunkkErrorText.of(result.error)
                    )
                }
            }
        }
    }

    /** 거부 사유에 맞는 단계. 모르는 사유면 그대로 머문다. */
    private fun stepFor(error: AppError): SignUpStep? = when ((error as? AppError.Rejected)?.code) {
        "CONSENT_MISSING", "UNDER_14" -> SignUpStep.CONSENT
        "NICKNAME_INVALID", "NICKNAME_TAKEN", "INTRO_TOO_LONG" -> SignUpStep.PROFILE
        "TAG_INVALID" -> SignUpStep.TAGS
        else -> null
    }
}
