package com.a207.runkk.member;

/**
 * 게스트 시작 결과(설계 6.1.2, FR-197). web.auth가 GuestStartResponse로 바꾼다. toString은 자격 증명을 감춘다.
 *
 * @param memberId 회원 ID
 * @param nickname 기본 닉네임("러너" + 숫자)
 * @param tokens 토큰 쌍
 * @param guestCredential 기기 자격 증명(한 번만 내준다, D-89)
 */
public record GuestStartResult(
        long memberId, String nickname, TokenPair tokens, String guestCredential) {

    @Override
    public String toString() {
        return "GuestStartResult[memberId=" + memberId + ", nickname=" + nickname + "]";
    }
}
