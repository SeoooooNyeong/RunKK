package com.a207.runkk.member;

/** 가상 닉네임처럼 회원이 쓸 수 없는 닉네임인지 묻는 포트(설계 6.1.2, FR-003, FR-007). 구현은 battle.VirtualNicknameChecker. */
public interface ReservedNicknameChecker {

    boolean isReserved(String nickname);
}
