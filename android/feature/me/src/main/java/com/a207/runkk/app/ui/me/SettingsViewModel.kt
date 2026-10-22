package com.a207.runkk.app.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.ChangeSettingUseCase
import com.a207.runkk.app.domain.account.MemberSettings
import com.a207.runkk.app.domain.account.SettingChangeRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 설정 항목 키(서버 SettingItem 이름, 러닝·대결 설정은 하위 항목, 알림은 "ALERT.알림종류"). MemberSettings.values의 키이자
 * ChangeSettingUseCase(Change(item, value))의 item이다. 10/21 API 계약에서 이름이 다르면 api.account 매퍼가 맞춘다.
 */
object SettingKeys {
    const val UNIT = "UNIT"
    const val RECORD_VISIBILITY = "RECORD_VISIBILITY"
    const val COURSE_MAP_SHARED = "COURSE_MAP_SHARED"
    const val PROFILE_VISIBILITY = "PROFILE_VISIBILITY"
    const val WATCH_LINKED = "WATCH_LINK"
    const val RUN_TARGET = "RUN_SETTINGS.target"
    const val RUN_VOICE = "RUN_SETTINGS.voice"
    const val RUN_VIBRATION = "RUN_SETTINGS.vibration"
    const val RUN_FOLLOW_COACH_PLAN = "RUN_SETTINGS.followCoachPlan"
    const val BATTLE_VOICE = "BATTLE_SETTINGS.voice"
    const val BATTLE_VIBRATION = "BATTLE_SETTINGS.vibration"
    const val BATTLE_GAP_ALERT = "BATTLE_SETTINGS.gapAlertInterval"

    /** 알림 종류(자료사전 알림종류 12개) 수신 여부 키. */
    fun alert(type: String): String = "ALERT.$type"

    /** 서버에 사본이 없을 때 쓰는 기본값(srs 기본값: 단위 km, 전체공개, 코스 지도 꺼짐, 개인정보 비공개, 음성·진동 켬, 알림 켬). */
    val DEFAULTS: Map<String, String> = mapOf(
        UNIT to "KM",
        RECORD_VISIBILITY to "PUBLIC",
        COURSE_MAP_SHARED to "false",
        PROFILE_VISIBILITY to "PRIVATE",
        WATCH_LINKED to "false",
        RUN_TARGET to "FREE",
        RUN_VOICE to "true",
        RUN_VIBRATION to "true",
        RUN_FOLLOW_COACH_PLAN to "false",
        BATTLE_VOICE to "true",
        BATTLE_VIBRATION to "true",
        BATTLE_GAP_ALERT to "KM1"
    )
}

/** 동의 항목 키(서버 ConsentItem 이름). 동의 주기·철회(PUT·DELETE /me/consents/{item})와 MemberSettings.consents의 키다. */
object ConsentKeys {
    const val LOCATION = "LOCATION"
    const val HEART_RATE = "HEART_RATE"
    const val MARKETING = "MARKETING"
    const val LLM_TRANSFER = "LLM_TRANSFER"
    const val FACE_TRANSFER = "FACE_TRANSFER"
}

/** 설정 화면 선택지: 서버 enum 이름(서버로 가는 값) → 화면 문구 리소스. 문구는 화면에서 푼다. */
object SettingChoices {
    /** 서버 DistanceUnit. */
    val unit: List<Pair<String, Int>> = listOf("KM" to R.string.me_choice_unit_km, "MI" to R.string.me_choice_unit_mile)

    /** 서버 ProfileVisibility. */
    val profileVisibility: List<Pair<String, Int>> =
        listOf("PUBLIC" to R.string.me_choice_public, "PRIVATE" to R.string.me_choice_private)
}

/** 고지문서 종류(서버 DocumentType 이름, GET /legal/{type}). */
object LegalTypes {
    const val TERMS = "TERMS"
    const val PRIVACY_POLICY = "PRIVACY_POLICY"
    const val LOCATION_TERMS = "LOCATION_TERMS"
    const val HELP = "HELP"
}

/** 자유런 목표 거리(서버 FreeRunTarget). */
object FreeRunTargets {
    val values: List<Pair<String, Int>> = listOf(
        "FREE" to R.string.me_choice_target_free,
        "KM3" to R.string.me_choice_target_km3,
        "KM5" to R.string.me_choice_target_km5,
        "KM10" to R.string.me_choice_target_km10
    )
}

/** 대결 중간 알림 간격(서버 GapAlertInterval, FR-189). */
object GapAlertIntervals {
    val values: List<Pair<String, Int>> =
        listOf("M500" to R.string.me_choice_gap_m500, "KM1" to R.string.me_choice_gap_km1)
}

/** 알림 종류(자료사전 알림종류, FR-008). 키는 서버 이름, 값은 화면 문구 리소스. */
object NotificationTypes {
    val values: List<Pair<String, Int>> = listOf(
        "PLAN_MISSED" to R.string.me_alert_plan_missed,
        "BODY_SHAPE_CHANGED" to R.string.me_alert_body_shape_changed,
        "JOIN_REQUESTED" to R.string.me_alert_join_requested,
        "JOIN_DECIDED" to R.string.me_alert_join_decided,
        "JOIN_EXPIRED" to R.string.me_alert_join_expired,
        "LEADER_TRANSFERRED" to R.string.me_alert_leader_transferred,
        "CREW_DELETED" to R.string.me_alert_crew_deleted,
        "KICKED" to R.string.me_alert_kicked,
        "CREW_RUN_RECRUIT" to R.string.me_alert_crew_run_recruit,
        "CREW_RUN_SOON" to R.string.me_alert_crew_run_soon,
        "OPPONENT_ARRIVED" to R.string.me_alert_opponent_arrived,
        "BATTLE_CORRECTED" to R.string.me_alert_battle_corrected
    )
}

data class SettingsUiState(
    val isLoading: Boolean = true,
    val settings: MemberSettings? = null,
    val error: UiText? = null,
    val message: UiText? = null,
    val legal: LegalView? = null,
    val urlToOpen: String? = null
) {
    /** 항목 값. 서버 값이 없으면 기본값. */
    fun value(key: String): String = settings?.values?.get(key) ?: SettingKeys.DEFAULTS[key] ?: "true"

    fun flag(key: String): Boolean = value(key).toBoolean()

    /** 동의 최근 값. 이력이 없으면 거짓(서버 ConsentHistory.isAgreed). */
    fun consent(item: String): Boolean = settings?.consents?.get(item) == true
}

/**
 * 설정·자유런 설정·알림 설정·동의 관리(FR-008~FR-019, FR-025, FR-048). 화면마다 이 ViewModel을 하나씩 둔다.
 * 값은 AccountRepository.settings로 읽고 바꿀 때 ChangeSettingUseCase(서버 변경 → 설정 사본 갱신)를 부른다.
 * 바꾼 값은 화면에 바로 보이고, 실패하면 이전 값으로 되돌려 알린다.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val account: AccountRepository,
    private val changeSetting: ChangeSettingUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
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

    /** 고지문서 보기(FR-018). 주소면 밖에서 열고(urlToOpen), 본문이면 대화상자로 보인다. */
    fun onViewLegal(type: String, title: UiText) {
        viewModelScope.launch {
            when (val doc = account.legalDocument(type)) {
                is Outcome.Success -> {
                    val body = doc.value.trim()
                    if (body.startsWith("http://") || body.startsWith("https://")) {
                        _state.update { it.copy(urlToOpen = body) }
                    } else {
                        _state.update { it.copy(legal = LegalView(title, body)) }
                    }
                }

                is Outcome.Failure -> _state.update { it.copy(message = RunkkErrorText.of(doc.error)) }
            }
        }
    }

    fun onLegalDismissed() = _state.update { it.copy(legal = null) }

    fun onUrlOpened() = _state.update { it.copy(urlToOpen = null) }

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
