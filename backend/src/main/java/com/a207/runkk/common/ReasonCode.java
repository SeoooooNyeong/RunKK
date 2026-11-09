package com.a207.runkk.common;

/**
 * 업무 규칙 거부 사유 코드(설계 6.1, 13.1 "BusinessRuleException(사유 코드 enum)"). 상수는 srs.md 6.3의 거부 사유 문구와 1:1로
 * 대응하고 ApiExceptionHandler가 문구로 바꾼다. 상수는 그 사유를 처음 쓰는 패키지를 구현할 때 더한다.
 */
public enum ReasonCode {
    /** 성향 태그 개수·분류 규칙 위반(srs.md 6.3 1.2, Q-82). */
    TAG_INVALID("태그 오류"),
    /** 운영 기준값 형식·범위 위반(srs.md 6.3 7.2, FR-174). */
    VALUE_INVALID("값 오류"),

    // member (srs.md 6.3 1.1~1.4)
    /** 필수 동의 3종 누락(srs.md 6.3 1.2 규칙 1). */
    CONSENT_MISSING("필수동의누락"),
    /** 만 14세 미만(srs.md 6.3 1.2 규칙 2, FR-004). */
    UNDER_14("만 14세 이상만 가입할 수 있음"),
    /** 닉네임 길이 위반(srs.md 6.3 1.2 규칙 3 "입력오류(닉네임)", 1.3 "닉네임 오류"). */
    NICKNAME_INVALID("닉네임 오류"),
    /** 다른 회원·가상 닉네임과 겹침(srs.md 6.3 1.3 "닉네임 오류" 중 중복, 앱이 "이미 쓰는 닉네임"을 따로 보이게 나눴다). */
    NICKNAME_TAKEN("이미 쓰는 닉네임"),
    /** 프로필 소개 40자 초과(srs.md 6.3 1.3). */
    INTRO_TOO_LONG("소개 길이 초과"),
    /** 설정 값이 설정항목의 허용 값이 아님(srs.md 6.3 1.4 "설정항목 + 거부"). */
    SETTING_INVALID("설정 값 오류"),
    /** 동의 항목을 설정 변경으로 바꾸려 함. 동의는 동의 주기·철회로만 바꾼다(D-36). */
    CONSENT_ITEM_NOT_SETTING("동의 항목은 동의 변경으로 바꿔야 함"),
    /** 소셜 로그인 제공자 무응답(srs.md 6.3 1.1, FR-001). */
    PROVIDER_UNAVAILABLE("로그인 제공자 연결 실패"),
    /** 소셜 로그인 인증 실패(srs.md 6.3 1.1 "로그인실패 + 실패사유"). */
    AUTH_FAILED("인증 실패"),
    /** 가입 티켓이 없거나 15분이 지남(D-19). */
    SIGN_UP_TICKET_INVALID("가입 시간 초과, 다시 로그인해 주세요"),
    /** 이미 가입한 소셜 계정으로 다시 가입하거나 다른 회원과 연동된 소셜 계정을 연동함(FR-003, FR-198). 문구는 FR-198(D-155 (3)). */
    ALREADY_MEMBER("이미 가입된 계정"),

    // running (srs.md 6.3 2.7, 3.2)
    /** 러닝 기록이 없음. */
    RECORD_NOT_FOUND("기록 없음"),
    /** 위치 경로가 파기된 기록(FR-026). */
    ROUTE_PURGED("경로 파기된 기록"),
    /** 유효 기록이거나 재판정 대기 기록이라 삭제할 수 없음(FR-225, D-231). */
    RECORD_VALID_NOT_DELETABLE("삭제할 수 없는 기록"),
    /** 파기 지시가 없음(FR-026, FR-029). */
    DISPOSAL_ORDER_NOT_FOUND("파기 지시 없음"),

    // analysis (srs.md 6.3 3.2, FR-072, FR-073)
    /** 비공개 기록을 다른 러너가 조회함(3.2 규칙 2, FR-060). */
    RECORD_PRIVATE("비공개 기록"),
    /** 크루 공개 기록을 같은 크루가 아닌 러너가 조회함(3.2 규칙 3, FR-060). */
    RECORD_CREW_ONLY("크루 공개 기록"),
    /** 주간 목표 횟수가 1~7이 아니거나 거리가 0 이하(FR-072). */
    GOAL_INVALID("목표 오류"),
    /** 계획 거리가 0 이하(FR-073). */
    PLAN_INVALID("계획 오류"),
    /** 오늘보다 이전 날짜의 계획(FR-073). */
    PAST_DATE("지난 날짜"),

    // notification (FR-178)
    /** 알림이 없음. */
    NOTIFICATION_NOT_FOUND("알림 없음"),

    // battle (srs.md 6.3 4.1·4.6·7.1)
    /**
     * 랜덤 대결 거리가 1·3·5·10km가 아니거나 나와의 싸움 거리가 3·5·10km·하프가 아님(4.1 규칙 1). 유명인 기록 거리가 5·10km·하프·풀이
     * 아님(배틀런 QA 결정).
     */
    BATTLE_DISTANCE_INVALID("대결 거리 오류"),
    /** 위치 수집 동의 없이 대결을 요청함(4.1 규칙 1, Q-74). */
    LOCATION_CONSENT_REQUIRED("위치 수집 동의 필요"),
    /** 언랭 회원의 랜덤 대결 요청(4.1 규칙 2). 앱은 나와의 싸움을 안내한다. */
    RANKED_ONLY("유효 러닝 3회 뒤 랜덤 대결 가능"),
    /** 이미 대기 중이거나 진행 중인 대결이 있음(4.1 규칙 3, FR-080). */
    ALREADY_IN_BATTLE("이미 대기 중이거나 대결 중"),
    /** 배정된 뒤의 매칭 취소(FR-085). */
    ALREADY_MATCHED("이미 매칭됨"),
    /** 대기열 저장소(Redis)를 쓸 수 없음(5.5 장애: Redis). */
    QUEUE_UNAVAILABLE("잠시 후 다시 시도"),
    /** 나와의 싸움 거리의 본인 최고 기록이 없음(4.6, FR-096). */
    NO_RECORD("아직 기록이 없어요"),
    /** 고른 유명인 기록이 지워짐(4.6, FR-172). */
    CELEBRITY_REMOVED("고를 수 없는 기록"),
    /** 랜덤·고스트 대결을 만들지 못해 대기가 풀림(실시간 MatchFailed). */
    MATCH_FAILED("대결을 만들지 못했어요. 다시 시도해 주세요"),
    /** 대결이 없음. */
    BATTLE_NOT_FOUND("대결 없음"),
    /** 유명인 기록 출처명이 비었음(7.1). */
    SOURCE_REQUIRED("출처 필요"),
    /** 유명인 익명 닉네임이 비었음(7.1, FR-171). */
    ANONYMOUS_NAME_REQUIRED("익명 닉네임 필요"),
    /** 유명인 구간 기록의 누적 거리가 줄어듦(7.1). */
    SPLITS_INVALID("구간 오류"),
    /** 셀럽 실명 등록에 코치 동의 일시·근거가 함께 있지 않음(FR-170, D-240). */
    REAL_NAME_CONSENT_REQUIRED("실명 등록 동의 기록 필요"),

    // battle 친구 대결 (FR-227~FR-229, v0.14 D-227)
    /** 초대 토큰·ID가 없음. */
    INVITE_NOT_FOUND("초대 없음"),
    /** 이미 기한이 남은 대기 초대가 있음(D-220). */
    INVITE_ALREADY_PENDING("이미 보낸 초대가 있음"),
    /** 본인이 보낸 초대에 참가·거절함. */
    INVITE_OWN("내가 보낸 초대"),
    /** 초대 만료(생성 + 5분, D-221). */
    INVITE_EXPIRED("만료된 초대"),
    /** 초대한 사람이 취소했거나 받는 사람이 거절한 초대. */
    INVITE_CANCELLED("취소된 초대"),
    /** 이미 다른 러너가 참가한 초대. */
    INVITE_ALREADY_JOINED("이미 참가한 초대"),

    // runimal (FR-168)
    /** 판정된 Runimal 유형이 없어 카드를 만들 수 없음. */
    NO_TYPE("유형 없음"),

    // reward (srs.md 6.3 6.5)
    /** 상점 목록에 없는 아이템(6.5). */
    ITEM_NOT_FOUND("아이템 없음"),
    /** 판매 중지된 아이템(D-44). */
    ITEM_NOT_ON_SALE("판매 중지된 아이템"),
    /** 이미 보유한 아이템(6.5). */
    ALREADY_OWNED("이미 보유"),
    /** 잔액이 가격보다 적음(6.5). */
    INSUFFICIENT_REWARD("리워드 부족"),

    // avatar (srs.md 6.3 6.3·6.6)
    /** 축소 운영으로 셀카 생성을 껐음(6.3 규칙 1). */
    SELFIE_DISABLED("셀카 생성 중단"),
    /** 얼굴 변환 전송 동의가 없음(6.3 규칙 2). */
    FACE_CONSENT_REQUIRED("전송 동의 필요"),
    /** 오늘 이미 셀카 아바타를 만듦(6.3 규칙 3, FR-157). */
    SELFIE_LIMIT("내일 다시 만들 수 있어요"),
    /** 오늘 셀카 변환 실패가 상한에 닿음(ai 반복 호출 방지). 기본 캐릭터는 고를 수 있다. */
    SELFIE_RETRY_LIMIT("오늘은 더 시도할 수 없어요. 기본 캐릭터를 고르거나 내일 다시 시도해 주세요"),
    /** 같은 회원의 셀카 변환이 이미 진행 중(5.3, D-59). */
    SELFIE_IN_PROGRESS("이미 변환 중"),
    /** 셀카 부위 이미지가 없음(다시 만들어 이전 묶음이 지워졌거나 잘못된 키, GET /avatar/parts). */
    FACE_IMAGE_NOT_FOUND("얼굴 이미지 없음"),
    /** 보유하지 않았거나 부위가 맞지 않는 아이템 착용(6.6). */
    ITEM_NOT_OWNED("보유하지 않은 아이템"),

    // crew (srs.md 6.3 5.1~5.7)
    /** 크루가 없음. */
    CREW_NOT_FOUND("크루 없음"),
    /** 초대 코드가 없거나 만료(5.3.1 자격 판정 규칙 1). */
    INVALID_CODE("크루 없음 또는 초대 코드 오류·만료"),
    /** 틀린 초대 코드를 짧은 시간에 너무 많이 넣음(무차별 대입 제한, 429). */
    TOO_MANY_ATTEMPTS("시도가 너무 많아요. 잠시 뒤 다시 시도해 주세요"),
    /** 이미 크루에 소속(1인 1크루, Q-34). */
    ALREADY_IN_CREW("이미 크루 소속"),
    /** 퇴장 뒤 24시간 안(FR-124). */
    COOLDOWN("퇴장 뒤 24시간 동안 가입할 수 없음"),
    /** 크루명이 2~20자가 아님(5.1.1). */
    CREW_NAME_INVALID("크루명 오류"),
    /** 크루명이 다른 크루와 같음(5.1.1 "크루명 오류" 중 중복, 앱이 따로 보이게 나눴다). */
    CREW_NAME_TAKEN("이미 있는 크루명"),
    /** 크루 인원이 10명(FR-111). */
    CREW_FULL("정원 초과"),
    /** 같은 크루에 대기 중 신청이 있음(FR-121). */
    ALREADY_REQUESTED("이미 신청함"),
    /** 가입 신청이 없음. */
    JOIN_REQUEST_NOT_FOUND("가입 신청 없음"),
    /** 본인 신청이 아니거나 대기가 아님(FR-143). */
    NOT_CANCELLABLE("취소할 수 없는 신청"),
    /** 이미 처리된 신청의 승인·거절(5.3.2 규칙 1). */
    REQUEST_DECIDED("처리된 신청"),
    /** 진행 중이거나 매칭 대기 중인 대결이 있어 회원 탈퇴를 할 수 없음(srs.md 6.3 1.5, FR-024). */
    BATTLE_IN_PROGRESS("진행 중인 대결 종료 필요"),
    /** 크루원이 남은 크루의 크루장 탈퇴(FR-126), 크루원이 남은 크루장의 회원 탈퇴(FR-024). */
    LEADER_MUST_TRANSFER("크루장 양도 필요"),
    /** 혼자인 크루장의 탈퇴(5.3.3). */
    CREW_DELETE_REQUIRED("크루 삭제 필요"),
    /** 양도·강제 퇴장 대상이 본인이거나 소속 크루원이 아님(5.1.3, 5.3.4). */
    TARGET_INVALID("대상 오류"),
    /** 그 크루에 소속 중이 아님(5.3.3, 5.5.3). */
    NOT_CREW_MEMBER("소속 아님"),
    /** 게시글 본문 200자 초과(FR-129). */
    BODY_TOO_LONG("본문 길이 초과"),
    /** 모집글 입력 규칙 위반(5.4 규칙 3). */
    CREW_RUN_INVALID("입력 오류"),
    /** 게시글이 없음. */
    POST_NOT_FOUND("게시글 없음"),
    /** 투표 마감 뒤 또는 삭제된 모집글(5.4 참여 투표 규칙 2). */
    VOTE_CLOSED("투표가 끝난 크루런"),
    /** 대기실이 아직 열리지 않음(5.7.1 규칙 2). */
    LOBBY_NOT_OPEN("아직 대기실이 열리지 않음"),
    /** 이미 시작했거나 취소된 크루런(5.7.1 규칙 3). */
    ALREADY_STARTED("이미 시작했거나 취소된 크루런"),
    /** 크루런 참가자가 아니거나 진행 중이 아님(FR-192). */
    NOT_PARTICIPANT("크루런 참가자 아님"),

    // v0.11 (설계 F.2.3, 06-01 ReasonCode: TOO_LARGE 413·UNSUPPORTED_IMAGE 422 외 모두 409)
    /** 이미 소셜 계정을 연동한 회원의 연동 요청(FR-198). */
    ALREADY_LINKED("이미 연동한 계정"),
    /** 운영진 지정 상한 2명(FR-204, Crew.appointManager). */
    MANAGER_LIMIT("운영진은 2명까지 지정할 수 있음"),
    /** 고정 공지 상한 3개(FR-205). */
    PIN_LIMIT("공지는 3개까지 고정할 수 있음"),
    /** 대면 모임 일시가 지금 이전(FR-132. 길이·범위 위반은 400, D-155). */
    SCHEDULE_INVALID("일정 입력 오류"),
    /** 대면 모임 참석이 정원에 닿음(FR-145). */
    SCHEDULE_FULL("정원이 찼음"),
    /** 투표 마감이 지금 이전이거나 없는 선택지(FR-206, FR-207. 선택지 길이·개수는 400, D-155). */
    POLL_INVALID("투표 입력 오류"),
    /** 마감된 투표에 응답(FR-207). */
    POLL_CLOSED("마감된 투표"),
    /** 빈 본문이면서 사진 0장인 게시글(FR-211). */
    POST_EMPTY("내용이나 사진이 필요함"),
    /** 게시글 사진 6장 이상(FR-211). */
    PHOTO_LIMIT("사진은 5장까지"),
    /** 빈 채팅 메시지(FR-208). */
    CHAT_EMPTY("빈 메시지"),
    /** 채팅 메시지가 없거나 지워짐(FR-210, FR-213). */
    CHAT_MESSAGE_NOT_FOUND("메시지 없음"),
    /** 신고 대상이 없음·삭제·숨김·종류 불일치(FR-214). */
    REPORT_TARGET_NOT_FOUND("신고할 대상이 없음"),
    /** 같은 신고자의 같은 대상 재신고(FR-215, D-149). */
    ALREADY_REPORTED("이미 신고함"),
    /** 신고 사례가 없음(FR-216). */
    REPORT_NOT_FOUND("신고 없음"),
    /** 이미 숨김·기각한 신고 사례(FR-216, D-138). */
    REPORT_DECIDED("이미 처리한 신고"),
    /** 해금하지 않은 랜드마크 배경 선택(FR-202). */
    BACKGROUND_LOCKED("아직 해금하지 않은 배경"),
    /** 게시글 사진이 1MB를 넘음(FR-211, D-100). 413(STATUS_BY_REASON, 06-06). */
    TOO_LARGE("사진이 너무 커요"),
    /** 게시글 사진이 JPEG가 아니거나 깨짐(FR-211, D-100, D-142). 422(STATUS_BY_REASON, 06-06). */
    UNSUPPORTED_IMAGE("지원하지 않는 형식"),
    /** 크루 로고가 없음(GET /crew-logos/{logoId}, S15P31A207-295). 404(NotFoundRuleException). */
    CREW_LOGO_NOT_FOUND("크루 로고 없음"),

    // runbody (05-19 D-440·D-450, 앱 계약 runbody contract.md v1.0)
    /** 그 기록의 런바디 분석 작업이 없음(집계 제외 기록, 기능을 켜기 전 기록, 남의 기록). 404(NotFoundRuleException). */
    RUNBODY_NOT_FOUND("분석할 데이터가 부족해요"),
    /** 2차 배포 기능이 운영 기준값으로 닫혀 있음(RUNBODY_ENABLED false, D-440). 403(STATUS_BY_REASON). */
    FEATURE_CLOSED("아직 열리지 않은 기능"),
    /** ai 결과 읽기 실패·시간 초과(D-449 읽기 10초). 503(STATUS_BY_REASON). 같은 요청을 다시 보내면 된다. */
    RUNBODY_UNAVAILABLE("분석하지 못했어요"),
    /** 월이 yyyy-MM(2000~2099년) 형식이 아님. 400(STATUS_BY_REASON). */
    INVALID_YEAR_MONTH("월 형식 오류"),

    // web.auth (코드 리뷰 3차 B-L6)
    /** 서버 저장소 일시 장애로 지금 처리할 수 없음(갱신 토큰 폐기 목록 등). 소셜 제공자 문제가 아니다. 같은 요청을 다시 보내면 된다. */
    TEMPORARILY_UNAVAILABLE("잠시 후 다시 시도해 주세요"),

    // event (S15P31A207-275)
    /** 이벤트가 없거나 공개하지 않음(404). */
    EVENT_NOT_FOUND("이벤트 없음");

    private final String message;

    ReasonCode(String message) {
        this.message = message;
    }

    /** 화면 문구. */
    public String message() {
        return message;
    }
}
