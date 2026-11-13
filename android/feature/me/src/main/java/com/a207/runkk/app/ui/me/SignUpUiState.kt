package com.a207.runkk.app.ui.me

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Gender

/**
 * 가입·게스트 공용 화면(FR-003·FR-197) 상태. 동의 단계는 필수 동의 3종 + 만 14세 확인, 선택 동의 2종(기본 꺼짐)이고,
 * 성별 단계는 남자·여자 둘 중 하나만 고른다(처음에는 고르지 않음, "밝히지 않음" 없음, S15P31A207-300).
 */
data class SignUpUiState(
    val step: SignUpStep = SignUpStep.CONSENT,
    val consents: Map<String, Boolean> = SignUpConsents.items.associate { it.item to false },
    /** 성별 단계에서 고른 성별(MALE·FEMALE). 아직 고르지 않았으면 null. */
    val gender: Gender? = null,
    val isSubmitting: Boolean = false,
    /**
     * 가입·게스트 시작은 끝났고 성별 저장만 남았는가(성별 저장 실패 뒤). 이때 시작하기는 성별 저장만 다시 하고, 동의로 돌아가지 않는다
     * (계정은 이미 있어 동의를 다시 보내지 않는다).
     */
    val isAccountCreated: Boolean = false,
    val error: UiText? = null,
    /** 가입·게스트 시작·성별 저장이 거부·실패한 안내. 스낵바와 달리 다음 시도까지 화면에 남는다. */
    val submitError: UiText? = null,
    /** [submitError]가 있을 때 같은 입력으로 다시 보낼 수 있는가(가입 시간이 지났거나 이미 가입한 계정이면 로그인부터 다시). */
    val canRetrySubmit: Boolean = true,
    /** 게스트 시작 직후의 자료 유실 고지(FR-199). 확인하면 홈으로 간다. */
    val isGuestNoticeVisible: Boolean = false,
    /** 게스트로 시작한 뒤 서버가 붙인 닉네임(A8 안내에 보인다). 아직 못 읽었으면 null. */
    val guestNickname: String? = null,
    val legal: LegalView? = null
) {
    val isAllAgreed: Boolean get() = consents.values.all { it }

    /** 필수 동의를 모두 했는가(FR-003 "빠지면 거부하고 빠진 항목을 표시한다"). 동의 단계의 시작하기(다음)를 켠다. */
    val canSubmit: Boolean
        get() = !isSubmitting && SignUpConsents.items.filter { it.isRequired }.all { consents[it.item] == true }

    /** 성별 단계의 시작하기를 켠다: 성별을 골랐고, 계정이 아직 없으면 필수 동의도 했다. */
    val canStart: Boolean
        get() = step == SignUpStep.GENDER && gender != null && !isSubmitting && (isAccountCreated || canSubmit)

    /** 성별 단계에서 동의로 돌아갈 수 있는가. 계정을 만든 뒤에는 앞으로(성별 저장)만 간다. */
    val canGoBackToConsent: Boolean get() = !isAccountCreated && !isSubmitting
}
