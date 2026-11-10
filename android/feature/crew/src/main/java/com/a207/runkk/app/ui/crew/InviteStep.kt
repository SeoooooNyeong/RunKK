package com.a207.runkk.app.ui.crew

/**
 * 초대 코드 바텀시트의 단계(피그마 F-03·F-03b·F-03c, contract-W4 4절).
 * [ENTERING] 코드 입력 중, [NOT_FOUND] 맞는 크루 없음(칸 오류 표시), [FOUND] 크루 미리보기를 보이고 "가입하기"를 기다림,
 * [LIMITED] 틀린 코드가 너무 많아 서버가 잠시 막음(429 TOO_MANY_ATTEMPTS, 칸 오류와 같은 자리에 기다리라는 안내).
 */
enum class InviteStep { ENTERING, NOT_FOUND, FOUND, LIMITED }
