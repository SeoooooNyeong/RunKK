package com.a207.runkk.member;

/** member 응용 서비스가 함께 쓰는 회원 읽기(패키지 전용 보조). 토큰으로 정한 회원 ID라 없으면 입력 오류다. */
final class MemberLoader {

    private MemberLoader() {}

    static Member load(MemberRepository repository, long memberId) {
        return repository
                .findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원 없음: " + memberId));
    }
}
