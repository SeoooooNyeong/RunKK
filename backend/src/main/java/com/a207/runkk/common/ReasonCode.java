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
    /** 이미 가입한 소셜 계정으로 다시 가입함(FR-003). */
    ALREADY_MEMBER("이미 가입한 계정"),

    // running (srs.md 6.3 2.7, 3.2)
    /** 러닝 기록이 없음. */
    RECORD_NOT_FOUND("기록 없음"),
    /** 위치 경로가 파기된 기록(FR-026). */
    ROUTE_PURGED("경로 파기된 기록"),
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
    /** 랜덤 대결 거리가 1·3·5·10km가 아니거나 나와의 싸움 거리가 3·5·10km·하프가 아님(4.1 규칙 1). */
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
    NOT_PARTICIPANT("크루런 참가자 아님");

    private final String message;

    ReasonCode(String message) {
        this.message = message;
    }

    /** 화면 문구. */
    public String message() {
        return message;
    }
}
