package com.a207.runkk.app.ui.crew

/**
 * 초대 코드 바텀시트의 단계(피그마 F-03·F-03b·F-03c, contract-W4 4절).
 * [ENTERING] 코드 입력 중, [NOT_FOUND] 맞는 크루 없음(칸 오류 표시), [FOUND] 크루 미리보기를 보이고 "가입하기"를 기다림.
 */
enum class InviteStep { ENTERING, NOT_FOUND, FOUND }
