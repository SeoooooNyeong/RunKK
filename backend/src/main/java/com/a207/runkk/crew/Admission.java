package com.a207.runkk.crew;

/** 가입 방식 판정 결과(srs.md 6.3 5.3.1 "가입 방식 판정" 표, Crew.admissionFor). */
public enum Admission {
    /** 정원 10명. */
    FULL,
    /** 초대 코드 또는 자유 가입: 바로 소속. */
    IMMEDIATE,
    /** 승인제: 가입 신청. */
    REQUEST,
    /** 초대 코드가 틀리거나 만료, 또는 크루 ID로 비공개 크루를 찾음. */
    INVALID_CODE
}
