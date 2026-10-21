package com.a207.runkk.battle;

/**
 * 대결의 한쪽: 참가자 회원 또는 기록 상대(설계 6.1.6 Side, sealed: MemberSide(long), OpponentSide). 판정 표(Judgement)의
 * 열쇠다.
 */
public sealed interface Side permits Side.MemberSide, Side.OpponentSide {

    /** 기록 상대(고스트·나와의 싸움·유명인). */
    Side OPPONENT = new OpponentSide();

    static Side member(long memberId) {
        return new MemberSide(memberId);
    }

    /**
     * 참가자 회원.
     *
     * @param memberId 회원 ID
     */
    record MemberSide(long memberId) implements Side {}

    /** 기록 상대. 값이 하나뿐이다. */
    record OpponentSide() implements Side {}
}
