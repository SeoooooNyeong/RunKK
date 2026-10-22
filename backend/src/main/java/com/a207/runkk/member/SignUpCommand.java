package com.a207.runkk.member;

/**
 * 가입 요청(SQ-17 SignUpCommand(ticket, 동의, 프로필, 공개 범위, 태그)).
 *
 * @param signUpTicket 로그인 때 받은 가입 티켓
 * @param form 가입 입력
 */
public record SignUpCommand(String signUpTicket, SignUpForm form) {

    public SignUpCommand {
        if (signUpTicket == null || signUpTicket.isBlank() || form == null) {
            throw new IllegalArgumentException("가입 티켓과 입력이 필요하다");
        }
    }
}
