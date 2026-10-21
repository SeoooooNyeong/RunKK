package com.a207.runkk.app.core.designsystem.testing

import java.io.File

/**
 * 서버 값 계약 자료(android/contract/enums). 서버 enum 상수 이름을 `backend/`에서 뽑아 고정한 목록이다
 * (갱신: `python android/contract/sync_from_server.py`). 앱 상수(서버로 가는 문자열)가 이 목록 안에 있는지 시험이 확인한다.
 * 경로는 Gradle 시험 작업이 시스템 속성 runkk.contract.dir로 넘긴다(build-logic AndroidLibraryConventionPlugin).
 */
object ContractEnums {
    private val dir: File =
        File(requireNotNull(System.getProperty("runkk.contract.dir")) { "runkk.contract.dir 속성이 없다" }, "enums")

    /** 서버 enum 이름 목록(한 줄에 하나). */
    fun names(enumName: String): Set<String> = lines("$enumName.txt").map { it.substringBefore(' ') }.toSet()

    /** 한 줄에 여러 값이 있는 목록(예: "KM1 1000 RANDOM_BATTLE,CREW_RUN")의 원본 줄. */
    fun lines(fileName: String): List<String> = File(dir, fileName).readLines().filter { it.isNotBlank() }

    /** 사유 코드로 쓸 수 있는 이름: 서버 ReasonCode + 오류 응답의 일반 코드(BAD_REQUEST 등) + 앱 안에서만 만드는 코드. */
    val knownReasonCodes: Set<String> by lazy {
        names("ReasonCode") + names("ErrorCode.http") + APP_LOCAL_CODES
    }

    /** 서버에 없고 앱이 만드는 사유 코드(AppError.Rejected 상수·로그인·탈퇴 변환·공유). */
    val APP_LOCAL_CODES: Set<String> = setOf(
        "LOCATION_CONSENT",
        "RUN_IN_PROGRESS",
        "SIGN_UP_REQUIRED",
        "PROVIDER_SDK_NOT_READY",
        "LOGIN_CANCELLED",
        "BATTLE_IN_PROGRESS",
        "WITHDRAWAL_REJECTED",
        "NOT_FOUND",
        "COURSE_MAP_NOT_SHARED",
        "SELFIE_FAILED"
    )
}
