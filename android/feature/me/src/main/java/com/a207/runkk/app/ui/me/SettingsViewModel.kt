package com.a207.runkk.app.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.ChangeSettingUseCase
import com.a207.runkk.app.domain.account.LinkSocialAccountUseCase
import com.a207.runkk.app.domain.account.LogoutUseCase
import com.a207.runkk.app.domain.account.MemberSettings
import com.a207.runkk.app.domain.account.ProviderToken
import com.a207.runkk.app.domain.account.ProviderType
import com.a207.runkk.app.domain.account.ResendRejectedRunsUseCase
import com.a207.runkk.app.domain.account.SessionRepository
import com.a207.runkk.app.domain.account.SettingChangeRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 설정·자유런 설정·알림 설정·동의 관리·소셜 계정 연동·로그아웃(FR-002, FR-008~FR-019, FR-025, FR-048, FR-198).
 * 화면마다 이 ViewModel을 하나씩 둔다.
 * 값은 AccountRepository.settings로 읽고 바꿀 때 ChangeSettingUseCase(서버 변경 → 설정 사본 갱신)를 부른다.
 * 바꾼 값은 화면에 바로 보이고, 실패하면 이전 값으로 되돌려 알린다.
 * 서버가 거부한 폰 기록 수를 따라 보이고 "다시 보내기"로 폰과 연결된 워치의 거부 기록을 다시 보낸다(W40, ResendRejectedRunsUseCase).
 */
@HiltViewModel
class SettingsViewModel internal constructor(
    private val account: AccountRepository,
    private val changeSetting: ChangeSettingUseCase,
    private val session: SessionRepository,
    private val linkSocialAccount: LinkSocialAccountUseCase,
    private val clients: Set<@JvmSuppressWildcards SocialLoginClient>,
    private val logout: LogoutUseCase,
    private val resendRejectedRuns: ResendRejectedRunsUseCase,
    /** 게스트 자격 증명(암호화 저장소) 읽기 디스패처(A4: 메인에서 디스크를 읽지 않는다). */
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {
    @Inject
    constructor(
        account: AccountRepository,
        changeSetting: ChangeSettingUseCase,
        session: SessionRepository,
        linkSocialAccount: LinkSocialAccountUseCase,
        clients: Set<@JvmSuppressWildcards SocialLoginClient>,
        logout: LogoutUseCase,
        resendRejectedRuns: ResendRejectedRunsUseCase
    ) : this(account, changeSetting, session, linkSocialAccount, clients, logout, resendRejectedRuns, Dispatchers.IO)

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    init {
        load()
        viewModelScope.launch {
            resendRejectedRuns.observeRejectedCount().collect { count ->
                _state.update { it.copy(rejectedRunCount = count) }
            }
        }
    }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val isGuest = readIsGuest()
            _state.update { it.copy(isGuest = isGuest) }
        }
        viewModelScope.launch {
            val nickname = account.profile(AccountRepository.SELF).getOrNull()?.displayName
            _state.update { it.copy(nickname = nickname) }
        }
        viewModelScope.launch {
            when (val result = account.settings()) {
                is Outcome.Success -> _state.update { it.copy(isLoading = false, settings = result.value) }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, error = RunkkErrorText.of(result.error))
                }
            }
        }
    }

    fun setValue(key: String, value: String) {
        val current = _state.value.settings ?: return
        if (_state.value.value(key) == value) return
        val previous = current.values[key]
        _state.update { s -> s.copy(settings = s.settings?.let { it.copy(values = it.values + (key to value)) }) }
        send(SettingChangeRequest.Change(key, value)) { settings ->
            // 실패하면 이 항목만 되돌린다(그사이 바꾼 다른 항목은 그대로). 그사이 같은 항목을 또 바꿨으면 건드리지 않는다.
            when {
                settings.values[key] != value -> settings
                previous == null -> settings.copy(values = settings.values - key)
                else -> settings.copy(values = settings.values + (key to previous))
            }
        }
    }

    fun setConsent(item: String, isAgreed: Boolean) {
        val current = _state.value.settings ?: return
        val previous = current.consents[item]
        _state.update { s ->
            s.copy(settings = s.settings?.let { it.copy(consents = it.consents + (item to isAgreed)) })
        }
        val request = if (isAgreed) {
            SettingChangeRequest.GiveConsent(
                item
            )
        } else {
            SettingChangeRequest.WithdrawConsent(item)
        }
        send(request) { settings ->
            when {
                settings.consents[item] != isAgreed -> settings
                previous == null -> settings.copy(consents = settings.consents - item)
                else -> settings.copy(consents = settings.consents + (item to previous))
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    /**
     * 게스트 계정에 소셜 계정 연동 시작(FR-198): 고른 제공자의 SDK 창구를 돌려준다. 이미 연동 중이거나 창구가 없으면 null.
     * SDK 로그인 화면은 화면(SettingsRoute)이 자기 코루틴에서 Activity로 띄운다. ViewModel 코루틴이 Activity Context를 잡아
     * 화면이 사라진 뒤에도 붙들지 않게 한다(A4). 받은 토큰은 [onLinkToken], 화면이 사라져 끊기면 [onLinkCancelled]로 넘긴다.
     */
    fun onLinkStart(provider: ProviderType): SocialLoginClient? {
        if (_state.value.linkingProvider != null) return null
        val client = clients.firstOrNull { it.provider == provider } ?: return null
        _state.update { it.copy(linkingProvider = provider, linkError = null) }
        return client
    }

    /**
     * 제공자 토큰(실패 포함)을 받아 LinkSocialAccountUseCase로 연동한다. 다른 회원과 연동된 계정(ALREADY_MEMBER)은 화면 안에 거부를 보이고
     * 게스트 상태를 그대로 둔다. 이미 연동했으면(ALREADY_LINKED) 연동 회원으로 보인다.
     */
    fun onLinkToken(token: Outcome<ProviderToken>) {
        viewModelScope.launch {
            val result = when (token) {
                is Outcome.Failure -> token
                is Outcome.Success -> linkSocialAccount(token.value)
            }
            val isGuest = readIsGuest()
            _state.update {
                when (result) {
                    is Outcome.Success -> it.copy(
                        linkingProvider = null,
                        isGuest = isGuest,
                        message = UiText.Res(R.string.me_link_done)
                    )

                    is Outcome.Failure -> it.copy(
                        linkingProvider = null,
                        isGuest = isGuest,
                        linkError = linkErrorText(result.error)
                    )
                }
            }
        }
    }

    /** 화면이 사라져 SDK 로그인이 끊겼다: 진행 표시만 푼다(연동하지 않았다). */
    fun onLinkCancelled() = _state.update { it.copy(linkingProvider = null) }

    /** 로그아웃 확인 뒤(FR-002, FR-199). 연동하지 않은 게스트는 기기의 미전송 기록도 지운다(LogoutUseCase). */
    fun onLogoutConfirmed() {
        if (_state.value.isLoggingOut) return
        _state.update { it.copy(isLoggingOut = true) }
        viewModelScope.launch {
            val result = logout()
            _state.update { it.copy(isLoggingOut = false) }
            when (result) {
                is Outcome.Success -> _events.send(SettingsEvent.LoggedOut)
                is Outcome.Failure -> _state.update { it.copy(message = RunkkErrorText.of(result.error)) }
            }
        }
    }

    /**
     * 못 올린 기록 다시 보내기(W40): 폰의 거부 기록을 미전송으로 되돌려 전송을 깨우고 워치에도 요청한다. 건수는 보관함을 따라 바뀐다.
     * 다시 보내도 서버가 거부하면 다시 못 올린 기록으로 남는다.
     */
    fun onResendRejected() {
        if (_state.value.isResending) return
        _state.update { it.copy(isResending = true) }
        viewModelScope.launch {
            val result = resendRejectedRuns()
            _state.update {
                it.copy(
                    isResending = false,
                    message = when (result) {
                        is Outcome.Success -> UiText.Res(R.string.me_settings_resend_started)
                        is Outcome.Failure -> RunkkErrorText.of(result.error)
                    }
                )
            }
        }
    }

    private suspend fun readIsGuest(): Boolean = withContext(ioDispatcher) { session.hasGuestCredential() }

    /** 고지문서 보기(FR-018). 주소면 밖에서 열고(urlToOpen), 본문이면 대화상자로 보인다. */
    fun onViewLegal(type: String, title: UiText) {
        viewModelScope.launch {
            when (val doc = account.legalDocument(type)) {
                is Outcome.Success -> {
                    val link = legalLinkOrNull(doc.value)
                    if (link != null) {
                        _state.update { it.copy(urlToOpen = link) }
                    } else {
                        _state.update { it.copy(legal = LegalView(title, doc.value.trim())) }
                    }
                }

                is Outcome.Failure -> _state.update { it.copy(message = RunkkErrorText.of(doc.error)) }
            }
        }
    }

    fun onLegalDismissed() = _state.update { it.copy(legal = null) }

    fun onUrlOpened() = _state.update { it.copy(urlToOpen = null) }

    /** 브라우저가 없거나 막혀 고지 문서 주소를 열지 못했다(FR-018 필수 고지라 안내한다). */
    fun onUrlOpenFailed() = _state.update {
        it.copy(urlToOpen = null, message = UiText.Res(R.string.me_legal_open_failed))
    }

    /** 바꾸기 요청은 하나씩 차례로 보낸다(앞 요청의 사본 갱신과 뒤 요청이 엇갈리지 않게). */
    private val changes = Mutex()

    private fun send(request: SettingChangeRequest, rollback: (MemberSettings) -> MemberSettings) {
        viewModelScope.launch {
            val result = changes.withLock { changeSetting(request) }
            if (result is Outcome.Failure) {
                _state.update { s ->
                    s.copy(settings = s.settings?.let(rollback), message = RunkkErrorText.of(result.error))
                }
            }
        }
    }
}

/** 연동 거부 문구(FR-198): 이미 가입된(다른 회원과 연동된) 소셜 계정, 이미 연동한 회원. 그 밖은 공통 문구. */
private fun linkErrorText(error: AppError): UiText = when ((error as? AppError.Rejected)?.code) {
    "ALREADY_MEMBER" -> UiText.Res(R.string.me_link_already_member)
    "ALREADY_LINKED" -> UiText.Res(R.string.me_link_already_linked)
    else -> RunkkErrorText.of(error)
}
