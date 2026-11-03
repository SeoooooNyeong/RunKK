package com.a207.runkk.app.domain.coach

/** D-450의 스트림 실패와 요청 전 동의·기능 닫힘 거부를 화면에 전달한다. */
enum class CoachFailure { RETRY, QUOTA, EXPIRED, TOO_LONG, CONSENT_REQUIRED, FEATURE_CLOSED }
