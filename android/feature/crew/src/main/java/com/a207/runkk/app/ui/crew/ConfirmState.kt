package com.a207.runkk.app.ui.crew

/**
 * 결과 「확인」의 단계(설계 05-32 2.3): 누르기 전, 내 기록 저장·업로드를 기다림, 다시 보낼 수 있는 내 전송 실패, 다시 보내도 안 되는 실패
 * (서버 거부·기기 저장 실패, 한 번 더 누르면 복귀 값 없이 나간다).
 */
enum class ConfirmState { IDLE, WAITING, RETRYABLE, BLOCKED }
