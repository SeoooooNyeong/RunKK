package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkTags
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewForm
import com.a207.runkk.app.domain.crew.CrewList
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.CrewVisibility
import com.a207.runkk.app.domain.crew.JoinPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------- 크루 찾기·상세 ----------

data class CrewFindUiState(
    val loading: Boolean = true,
    val keyword: String = "",
    val tags: Set<String> = emptySet(),
    /** 추천(FR-118). 태그가 없으면 서버가 빈 목록을 준다(FR-119). */
    val recommendations: List<CrewList.Item> = emptyList(),
    val crews: List<CrewList.Item> = emptyList(),
    /** 상세(바텀 시트)로 연 크루. */
    val detail: CrewList.Item? = null,
    val joining: Boolean = false,
    val inviteOpen: Boolean = false,
    val inviteCode: String = "",
    val message: String? = null,
    val error: String? = null
)

sealed interface CrewEvent {
    data class Open(val key: Routes.CrewHome) : CrewEvent
}

/**
 * 크루 탭: 크루 찾기(FR-117~FR-122, FR-124, FR-143). 공개 크루 목록(태그 AND·키워드)과 추천을 보이고,
 * 상세 시트에서 바로 가입(자유)·가입 신청(승인제), 초대 코드 가입을 한다. 비공개 크루는 초대 코드로만 들어온다(Q-38).
 */
@HiltViewModel
class CrewFindViewModel @Inject constructor(private val crews: CrewRepository) : ViewModel() {
    private val _state = MutableStateFlow(CrewFindUiState())
    val state: StateFlow<CrewFindUiState> = _state.asStateFlow()

    private val _events = Channel<CrewEvent>(Channel.BUFFERED)
    val events: Flow<CrewEvent> = _events.receiveAsFlow()
    private var job: Job? = null

    init {
        load()
    }

    fun load() {
        job?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        val s = _state.value
        job = viewModelScope.launch {
            val recommended = async { crews.recommend() }
            when (val found = crews.search(s.keyword.trim().ifBlank { null }, s.tags)) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        loading = false,
                        crews = found.value.crews,
                        recommendations = recommended.await().getOrNull()?.crews.orEmpty()
                    )
                }

                is Outcome.Failure -> {
                    recommended.cancel()
                    _state.update { it.copy(loading = false, error = CrewErrorText.of(found.error)) }
                }
            }
        }
    }

    fun onKeyword(text: String) = _state.update { it.copy(keyword = text.take(KEYWORD_MAX)) }

    fun onTagToggle(id: String) {
        _state.update { it.copy(tags = RunkkTags.toggle(it.tags, id)) }
        load()
    }

    fun onOpen(item: CrewList.Item) = _state.update { it.copy(detail = item) }

    fun onCloseDetail() = _state.update { it.copy(detail = null) }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    /** 자유 가입이면 바로 크루 홈, 승인제면 신청 접수 안내(FR-120·FR-121). */
    fun onJoin(item: CrewList.Item) {
        if (_state.value.joining) return
        _state.update { it.copy(joining = true) }
        viewModelScope.launch {
            when (val result = crews.apply(item.crewId)) {
                is Outcome.Success -> {
                    _state.update { it.copy(joining = false, detail = null) }
                    if (item.joinPolicy == JoinPolicy.FREE) {
                        _events.trySend(CrewEvent.Open(Routes.CrewHome(item.crewId, item.name)))
                    } else {
                        _state.update { it.copy(message = "가입 신청했어요. 크루장이 결정하면 알려 드려요") }
                    }
                }

                is Outcome.Failure -> _state.update {
                    it.copy(joining = false, message = CrewErrorText.of(result.error))
                }
            }
        }
    }

    fun onInviteOpen() = _state.update { it.copy(inviteOpen = true, inviteCode = "") }

    fun onInviteClose() = _state.update { it.copy(inviteOpen = false) }

    /** 초대 코드는 6자리 영문·숫자(FR-112). 대문자로 맞춘다. */
    fun onInviteCode(text: String) = _state.update {
        it.copy(inviteCode = text.filter(Char::isLetterOrDigit).take(INVITE_LENGTH).uppercase())
    }

    fun onInviteSubmit() {
        val code = _state.value.inviteCode
        if (code.length != INVITE_LENGTH || _state.value.joining) return
        _state.update { it.copy(joining = true) }
        viewModelScope.launch {
            when (val result = crews.applyWithInviteCode(code)) {
                is Outcome.Success -> _state.update {
                    it.copy(joining = false, inviteOpen = false, message = "크루에 가입했어요. 홈의 크루 카드에서 볼 수 있어요")
                }

                is Outcome.Failure -> _state.update {
                    it.copy(joining = false, message = CrewErrorText.of(result.error))
                }
            }
        }
    }

    private companion object {
        const val KEYWORD_MAX = 20
        const val INVITE_LENGTH = 6
    }
}

// ---------- 크루 만들기 ----------

data class CrewFormState(
    val name: String = "",
    val intro: String = "",
    val tags: Set<String> = emptySet(),
    val visibility: CrewVisibility = CrewVisibility.PUBLIC,
    val joinPolicy: JoinPolicy = JoinPolicy.APPROVAL
) {
    /** 크루명 2~20자, 태그 1~5개(FR-110). 최종 판정은 서버가 한다. */
    val valid: Boolean get() = name.trim().length in NAME_MIN..NAME_MAX && tags.size in 1..RunkkTags.MAX_SELECTED

    fun toForm() = CrewForm(name.trim(), intro.trim().ifBlank { null }, tags, visibility, joinPolicy)

    companion object {
        const val NAME_MIN = 2
        const val NAME_MAX = 20
        const val INTRO_MAX = 40
    }
}

data class CrewCreateUiState(
    val form: CrewFormState = CrewFormState(),
    val saving: Boolean = false,
    val error: String? = null
)

/** 크루 만들기(FR-110): 만든 러너가 크루장이 된다. 만들면 크루 홈으로. */
@HiltViewModel
class CrewCreateViewModel @Inject constructor(private val crews: CrewRepository) : ViewModel() {
    private val _state = MutableStateFlow(CrewCreateUiState())
    val state: StateFlow<CrewCreateUiState> = _state.asStateFlow()

    private val _events = Channel<CrewEvent>(Channel.BUFFERED)
    val events: Flow<CrewEvent> = _events.receiveAsFlow()

    fun onForm(form: CrewFormState) = _state.update { it.copy(form = form, error = null) }

    fun onSubmit() {
        val s = _state.value
        if (!s.form.valid || s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val result = crews.create(s.form.toForm())) {
                is Outcome.Success -> {
                    _state.update { it.copy(saving = false) }
                    _events.trySend(CrewEvent.Open(Routes.CrewHome(result.value, s.form.name.trim())))
                }

                is Outcome.Failure -> _state.update { it.copy(saving = false, error = CrewErrorText.of(result.error)) }
            }
        }
    }
}
