package com.a207.runkk.app.ui.me

import com.a207.runkk.app.core.designsystem.UiText

/** 동의 화면 하나(가입·게스트 공용, FR-003·FR-197) 상태. 필수 동의 3종 + 만 14세 확인, 선택 동의 2종(기본 꺼짐). */
data class SignUpUiState(
    val consents: Map<String, Boolean> = SignUpConsents.items.associate { it.item to false },
    val isSubmitting: Boolean = false,
    val error: UiText? = null,
    /** 가입·게스트 시작이 거부·실패한 안내. 스낵바와 달리 다음 시도까지 화면에 남는다. */
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

    /** 필수 동의를 모두 했는가(FR-003 "빠지면 거부하고 빠진 항목을 표시한다"). */
    val canSubmit: Boolean
        get() = !isSubmitting && SignUpConsents.items.filter { it.isRequired }.all { consents[it.item] == true }
}
