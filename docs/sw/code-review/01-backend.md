> [런크크 코드 리뷰 결과서](../code-review.md) 목차 · 리뷰 2026-10-20 (1차) · 다음: [01b-backend-2.1](01b-backend-2.1.md) · 2차: [리뷰 2026-10-21 (2차)](#리뷰-2026-10-21-2차) · 3차: [리뷰 2026-10-21 (3차)](01b-backend-2.1.md#리뷰-2026-10-21-3차) · 4차: [리뷰 2026-10-21 (4차)](01b-backend-2.1.md#리뷰-2026-10-21-4차)

## 1. 백엔드 1단계 (common·audit·policy, 스택 전환)

입력: S15P31A207-impl/backend (feature/runkk-impl 9f2af7e, 기준선 origin/develop d38b99f), docs/sw/design-oo.md v0.7(05-01, 05-02, 06-01, 06-05, 06-06 6.1.13, 06-07, 12, 13-14), docs/sw/design-review.md v0.7

### 리뷰 기준

| 항목 | 기준 | 출처 |
|------|------|------|
| 매개변수 수 | 4개 이하 (5개 이상은 중). 손으로 쓴 메소드·생성자에 적용하고, record 표준 생성자(구성요소가 설계서 속성 목록 그대로)는 세지 않는다 | 기본 규칙 |
| 중첩 깊이 | 3단계 이하 | 기본 규칙 |
| 함수 길이 | 50줄 이하 | 기본 규칙 |
| 순환 복잡도 | 10 이하 (약산: 조건문 + 반복문 + 1) | 기본 규칙 |
| 결합도 상한 / 응집도 하한 | 스탬프 / 통신적 (제어 결합은 검토). 변경이 드문 읽기 전용 기준 형식 패키지는 논리적이어도 조건부 허용 | design-review.md 0.1 |
| Fan-Out 상한 | 7 | design-review.md 0.1 |
| 의존 방향 | 12.2 의존 목록(D1~D74)과 13.1 ArchUnit 규칙 10개 | design-oo.md 12, 13.1 |

코딩 규약 문서: `GitHub/자율/자율프로젝트_코드컨벤션.md` v0.1(제안) 적용. 기본 규칙보다 우선한다. 다만 규약 2장의 버전(Java 25, Spring Boot 4.1, Security 7)과 패키지 배치(`global/`, `{도메인}/controller·service·repository`)는 설계서 D-01·5.1.1과 저장소 `AGENTS.md`(Java 21, Spring Boot 3.5, Security 6)와 충돌한다. 클래스 이름·패키지는 설계서가 기준이라(13장 끝 문장) 설계를 따른 코드는 결함으로 세지 않고, 규약 문서 갱신 요청으로 따로 적는다(8절).
보조 기준: ecc `java-coding-standards`(설정은 `@ConfigurationProperties`, 넓은 catch 금지, Optional 사용). 최종 판정은 sw-code-review 기준.
형상 관리 도구·브랜치 전략: Git(GitLab). 규칙은 `AGENTS.md` — 브랜치 `feature/{Jira 키}-{작업명}`, 커밋 `{타입}: {내용} [{Jira 키}]`, MR 리뷰 1명 뒤 병합.

### 언어 평가 (최초 1회)

언어: Java 21, Spring Boot 3.5.16 (Gradle Kotlin DSL, PostgreSQL 17, Redis 8)

| 기준 | 판정 | 근거 |
|------|------|------|
| 친숙성 | 적합 | plan.md 7.2 "Spring Boot 전원 보유" |
| 사용 능력 | 적합 | 트랜잭션(JPA), 사건(@TransactionalEventListener), WebSocket, 동시성 라이브러리가 표준 생태계에 있음 |
| 처리 효율성 | 적합 | NFR-001 핵심 API p95 500ms, 동시 50명(인스턴스 1개, 5.4) 대비 여유 |
| 과거 개발 실적 | 보통 | 연동할 기존 서버 시스템이 없다. 앱(Kotlin)·ai(Python)와는 HTTP 계약으로만 연결 |
| 문제 유형 | 적합 | 웹 API + 실시간 중계형, Java·Spring이 흔히 쓰이는 유형 |
| 자료 구조 | 적합 | 표준 컬렉션·record·enum 다형성으로 충분(이번 범위에서 확인) |
| 이식성 | 적합 | JVM + Docker compose(EC2 Linux), 로컬은 H2·내장 Redis로 설치 없이 실행 |
| 개발자 경험 | 보통 | Spring Security 6 람다 DSL·JPA 낙관적 잠금·비동기 실행기 수명 관리 경험 깊이는 확인 안 됨(아래 C1이 그 흔적) |
| 컴파일러·도구 | 적합 | Gradle, Spotless(google-java-format), ArchUnit, Testcontainers |

부적합 0건 → 재검토 불필요

### 대상

변경 범위: `git diff --stat origin/develop...HEAD -- backend` 119개 파일, +5,519 / −160. 본 리뷰는 `src/main` 74개 Java 파일(2,802줄)과 마이그레이션 2개, 설정 1개를 읽었다. 시험 코드 39개 파일(2,828줄)은 구조 시험(ArchitectureTest)과 규약 점검에만 썼다. `build/` 결과는 보지 않았다.

| 파일 (`src/main/java/com/a207/runkk/`) | 줄 수 | 대응 설계 모듈 | 마지막 커밋 |
|------|-------|----------------|-------------|
| common/AggregationPeriod.java | 26 | common.AggregationPeriod | b66ff24 |
| common/BusinessRuleException.java | 16 | common.BusinessRuleException | b66ff24 |
| common/DateRange.java | 48 | common.DateRange | b66ff24 |
| common/DisplayName.java | 26 | common.DisplayName | b66ff24 |
| common/Distance.java | 60 | common.Distance | b66ff24 |
| common/DistanceUnit.java | 28 | common.DistanceUnit | b66ff24 |
| common/DomainEvent.java | 9 | common.DomainEvent | b66ff24 |
| common/DomainEventPublisher.java | 10 | common.DomainEventPublisher | b66ff24 |
| common/GeoPoint.java | 50 | common.GeoPoint | b66ff24 |
| common/ItemSlot.java | 9 | common.ItemSlot | b66ff24 |
| common/Pace.java | 52 | common.Pace | b66ff24 |
| common/PermissionDeniedException.java | 9 | common.PermissionDeniedException | b66ff24 |
| common/PersonalityTag.java | 40 | common.PersonalityTag | b66ff24 |
| common/ProgressReport.java | 13 | common.ProgressReport | b66ff24 |
| common/ReasonCode.java | 23 | (설계서에 이름 없음, 13.1 "사유 코드 enum") | b66ff24 |
| common/RecordVisibility.java | 62 | common.RecordVisibility | b66ff24 |
| common/StandardDistance.java | 65 | common.StandardDistance | b66ff24 |
| common/TagCategory.java | 29 | common.TagCategory | b66ff24 |
| common/TagOwner.java | 17 | common.TagOwner | b66ff24 |
| common/TagSet.java | 87 | common.TagSet | b66ff24 |
| common/Tier.java | 44 | common.Tier | b66ff24 |
| common/Timestamps.java | 35 | common.Timestamps | b66ff24 |
| common/ViewerRelation.java | 8 | common.ViewerRelation | b66ff24 |
| common/Week.java | 39 | common.Week | b66ff24 |
| audit/ActorRole.java | 9 | audit.ActorRole | fabd0f2 |
| audit/AuditAction.java | 19 | audit.AuditAction | fabd0f2 |
| audit/AuditEntry.java | 32 | audit.AuditEntry | fabd0f2 |
| audit/AuditRecord.java | 69 | audit.AuditRecord | fabd0f2 |
| audit/AuditRecordView.java | 23 | audit.AuditRecordView | fabd0f2 |
| audit/AuditRepository.java | 17 | audit.AuditRepository | fabd0f2 |
| audit/AuditService.java | 43 | audit.AuditService | fabd0f2 |
| policy/OperationSetting.java | 74 | policy.OperationSetting | 6ee8c6d |
| policy/OperationSettingHistory.java | 77 | policy.OperationSettingHistory | 6ee8c6d |
| policy/OperationPolicy.java | 155 | policy.OperationPolicy | 6ee8c6d |
| policy/OperationSettingService.java | 78 | policy.OperationSettingService | 6ee8c6d |
| policy/OperationSettingChanged.java | 18 | policy.OperationSettingChanged | 6ee8c6d |
| policy/OperationSettingRepository.java | 16 | policy.OperationSettingRepository | 6ee8c6d |
| policy/SettingKey.java | 399 | policy.SettingKey | 6ee8c6d |
| policy/SettingChangeResult.java | 10 | policy.SettingChangeResult (R29) | 6ee8c6d |
| policy/ValidationResult.java | 20 | policy.ValidationResult (SettingKey 반환형) | 6ee8c6d |
| policy/TierBoundary.java | 50 | policy.TierBoundary | 6ee8c6d |
| policy/MatchCondition.java | 21 | policy.MatchCondition | 6ee8c6d |
| policy/RewardRule.java | 18 | policy.RewardRule | 6ee8c6d |
| policy/CoachingRule.java | 24 | policy.CoachingRule | 6ee8c6d |
| policy/RunimalRule.java | 38 | policy.RunimalRule | 6ee8c6d |
| policy/CourseSimilarityRule.java | 10 | policy.CourseSimilarityRule | 6ee8c6d |
| policy/LandmarkTable.java | 53 | policy.LandmarkTable | 6ee8c6d |
| policy/Landmark.java | 13 | policy.Landmark | 6ee8c6d |
| policy/LandmarkStage.java | 60 | policy.LandmarkStage | 6ee8c6d |
| policy/LandmarkProgress.java | 10 | policy.LandmarkProgress (A-18 반환형) | 6ee8c6d |
| policy/VirtualRecordRule.java | 24 | policy.VirtualRecordRule | 6ee8c6d |
| policy/ShopCatalog.java | 37 | policy.ShopCatalog | 6ee8c6d |
| policy/ShopItem.java | 27 | policy.ShopItem | 6ee8c6d |
| policy/FeatureToggles.java | 9 | policy.FeatureToggles | 6ee8c6d |
| policy/SelfieLimit.java | 8 | policy.SelfieLimit | 6ee8c6d |
| policy/LlmCallLimit.java | 9 | policy.LlmCallLimit | 6ee8c6d |
| policy/SupplementCap.java | 10 | policy.SupplementCap | 6ee8c6d |
| policy/NotificationRetention.java | 16 | policy.NotificationRetention | 6ee8c6d |
| policy/CalorieCoefficient.java | 16 | policy.CalorieCoefficient | 6ee8c6d |
| policy/BodyShapeRule.java | 27 | policy.BodyShapeRule | 6ee8c6d |
| policy/SkinToneRule.java | 30 | policy.SkinToneRule | 6ee8c6d |
| policy/CrewRunRule.java | 23 | policy.CrewRunRule | 6ee8c6d |
| policy/OperatorWhitelist.java | 21 | policy.OperatorWhitelist | 6ee8c6d |
| infra/audit/JpaAuditRepository.java | 55 | infra.audit.JpaAuditRepository | fabd0f2 |
| infra/policy/JpaOperationSettingRepository.java | 47 | infra.policy.JpaOperationSettingRepository | 6ee8c6d |
| bootstrap/RunkkApplication.java | 31 | bootstrap.RunkkApplication | 00d730e |
| bootstrap/AsyncConfig.java | 48 | bootstrap.AsyncConfig | 3769295 |
| bootstrap/DomainEventConfig.java | 16 | bootstrap.DomainEventConfig | 3769295 |
| bootstrap/SpringDomainEventPublisher.java | 23 | bootstrap.SpringDomainEventPublisher | 3769295 |
| bootstrap/EmbeddedRedisConfig.java | 61 | (설계서에 없음. develop의 config에서 옮김) | 00d730e |
| web/auth/SecurityConfig.java | 46 | web.auth.SecurityConfig | 00d730e |
| web/error/ApiExceptionHandler.java | 48 | web.error.ApiExceptionHandler | 3769295 |
| web/error/ErrorResponse.java | 9 | web.error.ErrorResponse | 3769295 |
| resources/application.yml | 134 | (설정) | 00d730e |
| resources/db/migration/V1__create_audit_record.sql | 34 | audit 소유 테이블 | fabd0f2 |
| resources/db/migration/V2__create_operation_setting.sql | 21 | policy 소유 테이블 | 6ee8c6d |

### 1. 설계 일치

#### 1.1 클래스 대조 (1단계 범위)

| 패키지 | 설계서 클래스 수 | 코드에 있음 | 빠짐 | 설계서에 없는 클래스 | 판정 |
|--------|------------------|-------------|------|----------------------|------|
| common (5.1.1, 6.1.1) | 23 | 23 | 0 | ReasonCode | 일치, 추가 1건은 갱신 요청 U3 |
| audit (6.1.11) | 7 | 7 | 0 | 없음 | 일치 |
| policy (6.1.9) | 29 | 29 | 0 | 없음(SettingChangeResult·ValidationResult·LandmarkProgress는 설계 시그니처의 반환형) | 일치 |
| infra.audit / infra.policy | 1 / 1 | 1 / 1 | 0 | 없음 | 일치 |
| bootstrap (6.1.13) | 4 | 4 | 0 | EmbeddedRedisConfig | 일치, 추가 1건은 갱신 요청 U8 |
| web.error | 2 | 2 | 0 | 없음 | 일치 |
| web.auth | 4 (AuthController, SignUpController, AuthFilter, SecurityConfig) | 1 (SecurityConfig) | — | 없음 | SecurityConfig만 1단계 범위. 나머지 3개는 미구현(다음 단계 예정) |

1단계 범위 안에서 설계서에 있는데 코드에 빠진 클래스는 0건이다.

미구현(다음 단계 예정, 결함으로 세지 않음): web.auth의 AuthController·SignUpController·AuthFilter 3개와 SecurityConfig의 경로 규칙(로그인·가입 공개, /admin/* 제외), 그리고 member, notification, running, runimal, battle, reward, avatar, crew, analysis, profile, operator 핵심 패키지 11개, web.* 12개, realtime, batch.* 8개, infra.<도메인> 9개, infra.integration.* 4개(5.1.1 53개 중 13개 패키지 착수, 그중 web.auth는 일부).

#### 1.2 이름·인터페이스 대조

| 설계서 항목 | 코드 | 일치 | 비고 |
|-------------|------|------|------|
| Distance ofMeters·ofKm·plus·minus·isAtLeast·km·format(DistanceUnit), Comparable | 같음 | 예 | |
| Pace of(Duration, Distance)·isSlowerThanBy·isWithin·speedKmh·format | 같음 | 예 | |
| GeoPoint distanceTo·coarsen(int) | 같음 | 예 | |
| Week of·contains·closesAt·previous / DateRange contains·days·lastWeeks·lastMonths | 같음 | 예 | |
| TagSet of(Set, TagOwner)·empty·overlapCount·containsAll·valueOf(TagCategory)·isEmpty | 같음 | 예 | 위반 시 BusinessRuleException(TAG_INVALID) 그대로 |
| RecordVisibility allows·includedInPersonalRanking·ghostEligible (상수별 메소드) | 같음 | 예 | 6.4 다형성 그대로 |
| StandardDistance distance·isRandomBattle·isSelfBattle·isCrewRun·isRecordBase·nearestAtMost | 같음 | 예 | |
| Tier isRanked·adjacent·searchLadder | 같음 | 예 | |
| DomainEvent.occurredAt / DomainEventPublisher.publish | 같음 | 예 | |
| Timestamps createdAt(now)·touched(now), @Embeddable | 같음 | 예 | |
| BusinessRuleException(사유 코드 enum) | BusinessRuleException(ReasonCode) | 예 | enum 이름·위치는 설계서에 없음 → U3 |
| AuditRecord.of(AuditEntry, LocalDateTime), 변경 메소드 없음 | 같음 + 패키지 전용 `view()` | 예 | `view()` 추가 → U7 |
| AuditService record·query(DateRange, Optional<AuditAction>)·purgeExpired(now) | 같음 | 예 | |
| AuditRepository append·find·deleteOlderThan, update 없음 | 같음 | 예 | DB 트리거로 UPDATE 거부까지 함 |
| OperationSetting.change(newValue, actorId, now) → OperationSettingHistory | 같음 + `static initial(key, now)` | 예 | `initial` 추가 → U4 |
| SettingKey validate·parse·validateTransition(SHOP_ITEMS·ITEM_PRICES만 재정의), "21개 상수" | 22개 상수, validate·parse·validateTransition·defaultValue | 아니오(수) | 설계서 표가 이미 22행이다. 본문 "21개"가 낡음 → U2 |
| SettingKey ITEM_PRICES·SHOP_ITEMS → ShopCatalog, CREW_RUN_LOBBY_OPEN·CREW_RUN_END_RULE → CrewRunRule | ITEM_PRICES·CREW_RUN_*는 Map을 parse, OperationPolicy가 두 키를 합침 | 아니오 | 한 키의 parse가 두 키 값을 만들 수 없어 설계 표가 실현 불가 → U2 |
| OperationPolicy 19개 조회 + invalidate(key), cache ConcurrentHashMap | 같음 | 예 | |
| OperationSettingService.change(actorId, key, value) → SettingChangeResult | 같음 | 예 | 이력 → 감사 → 커밋 뒤 invalidate → 사건 발행 순서도 같음 |
| OperationSettingRepository find·save·saveHistory·findAll | 같음 | 예 | |
| TierBoundary tierOf·midScoreOf / MatchCondition accepts / RewardRule amountForRun / CoachingRule thresholdFor / CalorieCoefficient estimate / NotificationRetention isExpired / CrewRunRule closesAt / ShopCatalog find·bySlot·slotOf·allowsTransitionFrom | 같음 | 예 | |
| RunimalRule `double threshold(RunimalAxis a)` | `double threshold(TagCategory axis)` | 아니오 | 의존 규칙 때문 → U1 |
| BodyShapeRule `BodyShape stageFor(int)` | `int stageFor(int)` | 아니오 | 의존 규칙 때문 → U1 |
| SkinToneRule `SkinTone toneOf(LocalTime)` | `int toneOf(LocalTime)` | 아니오 | 의존 규칙 때문 → U1 |
| OperatorWhitelist accounts(Set<SocialAccount>), `contains(SocialAccount)` | accounts(Map<String, Set<String>>), `contains(String provider, String accountId)` | 아니오 | 의존 규칙 때문 → U1 |
| LandmarkTable personalProgressOf·crewProgressOf + 비공개 progressAmong (A-18) | 같음 | 예 | |
| LandmarkStage.DOMESTIC "왕복 n회", n = ⌊total ÷ (거리 × 2)⌋ | n = ⌊total ÷ distance⌋, distance에 왕복 거리 저장 | 아니오(식) | 결과는 같음. 거리 의미가 다름 → U5 |
| ApiExceptionHandler: BusinessRule→409+사유 코드, PermissionDenied→403, IllegalArgument→400 | 3개 + IllegalStateException→409, MethodArgumentNotValidException→400 | 아니오(추가) | 13.1 "예외 4종을 ApiExceptionHandler가 바꾼다"와 맞춘 것 → U6 |
| SpringDomainEventPublisher implements common.DomainEventPublisher | 같음, DomainEventConfig가 빈 등록 | 예 | |
| SecurityConfig "로그인·가입 경로만 공개, /admin/* 제외" | health·docs·h2-console 공개, 나머지 인증 | 부분 | 경로 규칙은 다음 단계(B2·B5) 예정이라 미구현으로 센다 |

#### 1.3 의존 방향 대조

실제 import(`com.a207.runkk.*`만):

| From | To (import 수) | 12.2 목록 | 판정 |
|------|----------------|-----------|------|
| common | 없음 | — | 예 |
| audit | common(2) | D44 | 예 |
| policy | audit(4), common(18) | D41, D44 | 예 |
| infra.audit | audit(3), common(1) | D58류, D67 | 예 |
| infra.policy | policy(4) | D58류 | 예 |
| bootstrap | common(3) | D66 | 예 |
| web.error | common(2) | D53 | 예 |
| web.auth | 없음 | (D45는 다음 단계) | 예 |

설계에 없는 의존 화살표:

| From → To | 주고받는 것 | 결합도 | 등급 |
|-----------|-------------|--------|------|
| 없음 | — | — | — |

- 순환 0, 계층 역행 0. Fan-Out 최대는 policy 2(audit, common)로 상한 7 안이다.
- 구현자가 바꾼 policy 시그니처 4건(U1)은 설계서대로 만들면 policy → runimal·avatar·member 화살표가 새로 생기고, 이미 있는 runimal·avatar·member → policy(D7, D20, D1)와 **양방향(순환)** 이 된다. 이는 치명 결함 정의("설계서에 없는 패키지 간 양방향 의존")에 해당하므로, 코드가 12.2를 지킨 쪽이 옳다. 대가로 int 번호·문자열 계약이라는 숨은 의존이 생겼다(U1에 적음).
- ArchUnit(`src/test/.../ArchitectureTest.java`)은 13.1 규칙 10개를 그대로 옮겼다. 허용 목록(`allowedDependencies`)을 12.2와 행마다 대조했고 차이가 없다. 53개 패키지 slice, `*.live` 상위 합치기, 저장 포트 규칙(4), common 규칙(6)이 설계 문장과 같다. 음성 시험(`ArchitectureRuleNegativeTest`)과 위반 고정 클래스(ArchFixture…)로 규칙이 실제로 잡는지도 확인한다.

#### 1.4 응집도 회귀

| 패키지 | 설계 판정 | 코드 | 판정 |
|--------|-----------|------|------|
| common | 불변 기본 형식만(조건부 허용, D-48) | ReasonCode가 더해졌다. 상수 주석이 "사유를 처음 쓰는 패키지를 구현할 때 더한다"고 적어, 남은 패키지 11개를 구현할 때마다 common이 바뀐다. design-review가 사건 형식을 common에서 뺀 이유(H-12, D-31)와 같은 압력이다. 다만 ArchUnit 규칙 6이 common의 인터페이스를 막아 "공통 인터페이스 + 패키지별 enum" 대안도 쓸 수 없다. 설계 결정이 필요하다 | 갱신 요청 U3 (코드 결함 아님) |
| policy | 통신적(운영 기준값) | 값 객체·엔티티·서비스 모두 운영 기준값만 다룬다 | 회귀 없음 |
| audit | 기능적 | 회귀 없음 | 회귀 없음 |
| bootstrap | "컴포넌트 탐색 진입점 + 사건 발행기 구현만" | EmbeddedRedisConfig(local·test 프로필에서 내장 Redis 띄우기)가 더해져 "시작할 때 필요한 것" 묶음(시간적)으로 기울었다 | 갱신 요청 U8 |

설계서 갱신 요청은 8절에 모았다.

### 2. 코딩 스타일

| 파일:줄 | 항목 | 현재 | 교정 예시 | 등급 |
|---------|------|------|-----------|------|
| S1 · common/StandardDistance.java:22-27 | 매개변수 | `StandardDistance(long meters, boolean randomBattle, boolean selfBattle, boolean crewRun, boolean recordBase)` — 5개, 호출부 `KM1(1_000, true, false, true, true)`가 위치로만 읽힌다 | `KM1(1_000, EnumSet.of(Use.RANDOM_BATTLE, Use.CREW_RUN, Use.RECORD_BASE))` + `isRandomBattle() { return uses.contains(Use.RANDOM_BATTLE); }` | 중 |
| S2 · policy/OperationSettingService.java:25-30 | 매개변수 | 생성자 `(repository, policy, auditService, eventPublisher, clock)` 5개 | 감사·사건 발행을 묶은 협력 객체 `SettingChangeRecorder(AuditService, DomainEventPublisher)`를 주입해 4개로, 또는 13.1에 "생성자 주입 의존은 Fan-Out으로 본다" 예외를 정하면 해제(U9) | 중 |
| S3 · policy/OperationSettingHistory.java:45-50 | 매개변수 | `OperationSettingHistory(SettingKey key, String before, String after, LocalDateTime changedAt, String operatorId)` 5개, String 셋이 연달아 와서 순서 실수가 컴파일에 안 걸린다 | `OperationSettingHistory(SettingKey key, ValueChange change, String operatorId, LocalDateTime changedAt)` + `record ValueChange(String before, String after)` | 중 |
| S4 · policy/SettingKey.java:348-398 | 함수 길이 | `defaultLandmarks()` 51줄(자료 30행 + JSON 손조립) | 기본표를 `private static final List<Landmark> DEFAULT_LANDMARKS = List.of(new Landmark(DOMESTIC, "인천", Distance.ofKm(60), true), …)`로 두고 `MAPPER.writeValueAsString(new LandmarkTable(DEFAULT_LANDMARKS))` | 중 |
| S5 · policy/SettingKey.java:26,36,82,104,122,137,171,201,341 | 변수명 | `MatchCondition v = (MatchCondition) value;` 등 한 글자 `v` 9곳(같은 파일 5개 이상이라 1건으로 합산) | `MatchCondition condition = (MatchCondition) value;` | 중 |
| S6 · policy/SettingKey.java:29,106,141,222 | 매직 넘버 | `between(v.tierWindow(), 0, 2) && ghostSeconds >= 5 && ghostSeconds <= 60` 외 100·220, 0.5·1.0, 1·100, 10·200 | `private static final int MAX_TIER_WINDOW = 2;` 식으로 상수화(6.1.9 검사 열 이름 그대로) | 경 |
| S7 · bootstrap/AsyncConfig.java:27-32 | 매직 넘버·규약 | `executor.setCorePoolSize(2); … setQueueCapacity(500); … setAwaitTerminationSeconds(30);` | 규약 2장 "설정값은 application.yml + @ConfigurationProperties": `spring.task.execution.pool.core-size: 2` 등으로 옮기고 Boot 자동 구성 실행기 사용 | 경 |
| S8 · bootstrap/EmbeddedRedisConfig.java:30 | 규약 | `public EmbeddedRedisConfig(@Value("${runkk.embedded-redis.port}") int port)` — 규약 2장 `@Value` 금지 | `@ConfigurationProperties("runkk.embedded-redis") record EmbeddedRedisProperties(boolean enabled, int port)`를 생성자로 받음 | 경 |
| S9 · policy/OperationPolicy.java:118-124, policy/SettingKey.java:228,232,240 | 매직 문자열 중복 | `lobby.get("lobbyOpenBeforeMinutes")` — 같은 키 문자열이 두 파일에 따로 있다 | CREW_RUN_LOBBY_OPEN의 parse 형식을 `record CrewRunLobby(int lobbyOpenBeforeMinutes, int reminderBeforeMinutes, int minReady)`로 바꿔 `lobby.lobbyOpenBeforeMinutes()`로 읽음 | 경 |
| S10 · common/Pace.java:27, policy/CalorieCoefficient.java:14, policy/RewardRule.java:16, policy/CrewRunRule.java:20 | 매직 넘버 중복 | `kcalPerKm * d.meters() / 1000.0` 등 m→km 환산 1000이 4곳 | Distance에 `double exactKm()`(반올림 없는 환산)을 두고 `Math.round(kcalPerKm * d.exactKm())` | 경 |
| S11 · policy/SettingKey.java:282 | 예외 처리(규약 1장 "예외는 삼키지 않는다") | `} catch (RuntimeException e) { return ValidationResult.invalid("형식 오류"); }` — 원인 없이 버리고 프로그래밍 오류(NPE 등)까지 "형식 오류"로 숨긴다 | `} catch (IllegalArgumentException e) { log.debug("setting_invalid key={} cause={}", name(), e.getMessage()); return ValidationResult.invalid("형식 오류"); }` | 경 |
| S12 · src/test/.../policy/OperationSettingServiceTest.java:45 외 시험 전체 | 규약(시험 이름) | `void change_savesValueHistoryAuditAndPublishes()` — 규약 2장 `행위_조건_결과` + `@DisplayName` 한글 중 @DisplayName이 없다(policy·common·audit 시험 0건) | `@DisplayName("값을 바꾸면 이력·감사·사건이 남는다") void change_whenValid_savesHistoryAuditAndPublishes()` | 경 |

중첩 깊이: 최대 2(SettingKey.strictlyAscending, TagSet.of, LandmarkTable.progressAmong). 기준 3 이하로 위반 0.
들여쓰기·배치: Spotless가 정하므로 지적하지 않는다(AGENTS.md "리뷰에서 포맷을 지적하지 않는다").

행 머리의 S·C·O 번호는 통합 결과서에서 붙였다. 원문 본문의 C1~C3·O1·S9 참조와 같은 순서다. 통합 결과서의 전역 ID는 앞에 `BE-`를 붙인다(예: BE-S1).

#### 2.1 정확성·보안 (보조 기준: ecc java-coding-standards, Spring 수명 관리)

| 파일:줄 | 항목 | 현재 | 교정 예시 | 등급 |
|---------|------|------|-----------|------|
| C1 · bootstrap/AsyncConfig.java:25-34 | 실행기 수명 | `getAsyncExecutor()`가 빈이 아닌 `new ThreadPoolTaskExecutor()`를 돌려준다. 컨테이너가 관리하지 않아 종료 때 `shutdown()`이 불리지 않으므로 `setWaitForTasksToCompleteOnShutdown(true)`·`setAwaitTerminationSeconds(30)`가 효과 없다. 비동기 수신자(리포트·Runimal, D-03)가 배포 재시작 때 말없이 잘린다 | AsyncConfigurer는 예외 처리기만 두고 실행기는 `spring.task.execution.pool.*`·`spring.task.execution.shutdown.await-termination: true`로 Boot 자동 구성 빈(`applicationTaskExecutor`)을 쓴다 | 중 |
| C2 · policy/OperationPolicy.java:147-153 | 저장값 검사 | `return Optional.of(key.parse(json));` — 저장값을 범위 검사(validate) 없이 쓴다. 수동 SQL·옛 판 값이 범위 밖이면 그대로 쓰이고, CREW_RUN_* 키가 빠진 값이면 `crewRunRule()`(121행)에서 NPE | `if (!key.validate(json).valid()) { log.warn(...); return Optional.empty(); }` 뒤 parse | 경 |
| C3 · policy/SettingKey.java:85 | 검사 누락 | `&& v.bands().get(0) >= 0` — 첫 하한이 0이 아니면 `BodyShapeRule.stageFor(0)`이 0을 돌려 "단계 1~4" 계약이 깨진다 | `&& v.bands().get(0) == 0` | 경 |
| C4 · web/error/ApiExceptionHandler.java:19-47 | 동시 변경 응답 | OperationSetting의 `@Version` 충돌(`ObjectOptimisticLockingFailureException`)과 첫 저장 경합(PK 중복)이 매핑되지 않아 500이 된다 | `@ExceptionHandler(OptimisticLockingFailureException.class) ResponseEntity<ErrorResponse> handleConflict(...) { return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("CONCURRENT_UPDATE", "다시 시도해 주세요")); }` | 경 |
| C5 · web/auth/SecurityConfig.java:24 | 인증 방식 | `.httpBasic(Customizer.withDefaults())` — 사용자 저장소가 없어 Boot가 기본 사용자(`user` + 기동 로그의 생성 비밀번호)를 만들고, 모든 프로필에서 이 계정으로 인증된다 | AuthFilter 전까지 `.httpBasic(AbstractHttpConfigurer::disable)`로 끄고 `anyRequest().authenticated()`로 401 유지 | 경 |
| C6 · web/auth/SecurityConfig.java:26,40 | 프로필 범위 | `.headers(h -> h.frameOptions(f -> f.sameOrigin()))`, `.requestMatchers("/h2-console/**").permitAll()` — 주석은 "local only"인데 develop·prod에도 적용된다 | `@Profile("local")` 별도 `SecurityFilterChain`에 `securityMatcher("/h2-console/**")`로 분리 | 경 |

### 3. 구조적 프로그래밍

| 함수 | 조건문 | 반복문 | 약산 복잡도 | 위반 |
|------|--------|--------|-------------|------|
| common.TagSet.of | 2 (복합 조건 `\|\|` 4개) | 1 | 4 (조건식 단위 8) | 없음. 반복 안 `throw`는 규칙 거부이고 흐름 분기가 아니다 |
| common.Tier.adjacent / searchLadder | 1 / 1 | 0 | 2 / 2 | 없음 |
| common.GeoPoint 생성자 | 2 | 0 | 3 | 없음 |
| audit.AuditEntry 생성자 | 3 | 0 | 4 | 없음 |
| policy.OperationSetting.change | 2 | 0 | 3 | 없음 |
| policy.OperationSettingService.change / invalidateAfterCommit | 0 / 1 | 0 | 1 / 2 | 없음 |
| policy.OperationPolicy.load / parseStored | 0 / try-catch 1 | 0 | 1 / 2 | 없음 |
| policy.SettingKey.validate | 1 + try-catch 1 | 0 | 3 | 없음 (아래 주) |
| policy.SettingKey.ITEM_PRICES·SHOP_ITEMS.validateTransition | try-catch 1 | 0 | 2 | 없음 |
| policy.SettingKey.LANDMARKS.checkRange | 0 (`&&` 7개, 람다) | 0 | 1 (조건식 단위 8) | 없음 |
| policy.SettingKey.strictlyAscending | 1 | 1 | 3 | 반복 안 `return false` — 이 함수의 유일한 책임(검사)이라 허용 |
| policy.SettingKey.defaultLandmarks | 1 | 1 | 3 | 없음 (길이는 2절) |
| policy.TierBoundary 생성자 / tierOf / midScoreOf | 2 / 1 / 1 + 삼항 2 | 1 / 1 / 0 | 4 / 3 / 4 | 없음 |
| policy.LandmarkTable.progressAmong | 2 | 1 | 4 | 없음 |
| policy.LandmarkStage.CREW_TOTAL.label | 3 | 0 | 4 | 없음 |
| policy.LandmarkStage.INTERCONTINENTAL.label | 1 | 0 | 2 | 없음 |
| policy.SkinToneRule.toneOf | 1 | 1 | 3 | 없음 |
| policy.RunimalRule.threshold | switch 5갈래 | 0 | 5 | 없음 (OO 4절 O1) |
| policy.MatchCondition.accepts | 1 | 0 | 2 | 없음 |
| bootstrap.EmbeddedRedisConfig.afterPropertiesSet / isPortInUse | 1 / try-catch 1 | 0 | 2 / 2 | 없음 |
| infra.audit.JpaAuditRepository.find | 삼항 1 + ifPresent 1 | 0 | 3 | 없음 |

goto 등가물: 없음. 라벨 break·continue, switch fallthrough, `while(true)` 0건.
예외를 흐름 제어로 쓰는 곳 점검: SettingKey.validate·validateTransition, OperationPolicy.parseStored는 Jackson의 형식 오류 예외를 **한 단계 안에서** ValidationResult·Optional로 바꾼다. 이는 structured-programming.md 3절의 교정 방향(결과 객체로 분기)과 같고 호출 스택을 건너뛰지 않아 goto 등가물로 보지 않는다. 다만 넓은 catch는 2절 경 결함으로 적었다.
단일 출구: 반복문 한가운데 return은 strictlyAscending 1곳이며 허용 범위다.

### 4. 객체지향 구현

| 파일:줄 | 항목 | 현재 | 교정 예시 | 등급 |
|---------|------|------|-----------|------|
| 전체 | 캡슐화 | 엔티티·값 객체 필드 모두 private. public 필드는 `public static final` 상수(Distance.ZERO, DisplayName.WITHDRAWN)뿐. ArchUnit 규칙 10이 강제 | 통과 | 없음 |
| 전체 | 접근자 | setter 0개. 엔티티 조회 메소드(OperationSetting.key()·value(), OperationSettingHistory.*)는 패키지 전용 | 통과 | 없음 |
| AuditRecord·OperationSetting·OperationSettingHistory | 생성자 | JPA용 기본 생성자 `protected`, 정적 생성(`AuditRecord.of`, `OperationSetting.initial`)으로만 만든다(13.1 정적 생성 규칙) | 통과 | 없음 |
| 전체 | this | 생성자에서 `this.x = x`로 구분 | 통과 | 없음 |
| common/BusinessRuleException.java:4 외 | 상속 | RuntimeException 상속 2건(is-a), 깊이 1. BaseEntity 상속 없음(Timestamps 포함) | 통과 | 없음 |
| RecordVisibility, AggregationPeriod, LandmarkStage, SettingKey | 다형성 | 상수별 메소드로 분기 제거(6.4 표 그대로) | 통과 | 없음 |
| O1 · policy/RunimalRule.java:29-36 | 다형성 | `switch (axis) { case COMPANION -> …; default -> throw new IllegalArgumentException(…) }` — TagCategory 7개 중 4개만 유효해 잘못된 입력을 컴파일 시점에 막지 못한다 | 축 평가기(AxisEvaluator 4개)가 `rule.companionRatio()` 등 구성요소를 직접 읽게 하고 `threshold`를 없애거나, U1 결정대로 RunimalAxis를 받게 함 | 경 |
| O2 · policy/OperationPolicy.java:32-128 | 형 안전 | `return (TierBoundary) valueOf(SettingKey.TIER_BOUNDARY);` 등 다운캐스트 19곳 + unchecked 2곳(67, 118행) | `private <T> T valueOf(SettingKey key, Class<T> type) { return type.cast(cache.computeIfAbsent(key, this::load)); }` | 경 |
| 전체 | 절차적 스타일 | 모든 메소드가 static인 유틸 클래스 0개. 서비스는 Repository·Clock을 생성자 주입 | 통과 | 없음 |

참고(결함 아님): `ShopCatalog.bySlot(Optional<ItemSlot>)`, `AuditService.query(…, Optional<AuditAction>)`, `CoachingRule.thresholdFor(OptionalInt)`는 ecc java-coding-standards가 권하지 않는 "Optional 매개변수"지만 설계서 시그니처 그대로다. 바꾸려면 설계서부터 고친다.

### 5. 형상 관리

| 활동 | 판정 | 근거 |
|------|------|------|
| 형상 식별 | 예 | 패키지·클래스가 설계서 5.1.1·6.1 이름과 1:1이고 클래스 주석마다 설계 절(6.1.9, D-44 등)을 적었다. ArchitectureTest `mainClasses_resideInDesignPackages`가 53개 패키지 밖 클래스를 막는다 |
| 버전 제어 | 예 | Git 이력, `build.gradle.kts` version `0.0.1-SNAPSHOT`. 태그는 없다(1단계 끝에 `backend-v0.1.0` 같은 태그 권고) |
| 변경 통제 | 아니오 | 이번 작업은 Jira 키 없이 진행 중이다. 브랜치 `feature/runkk-impl`·`feature/runkk-impl-backend`에 키가 없고(규칙 `feature/{Jira 키}-{작업명}`), 커밋 6개 모두 `[{Jira 키}]`가 없다(예: `feat: policy 패키지와 infra.policy 운영 기준값 저장소 구현`). 통합 브랜치에는 MR 없이 `merge:` 커밋(5c98024)으로 들어왔다. 커밋이 설계 절은 가리키지만 승인된 변경 요청·요구 ID와는 이어지지 않는다 |
| 형상 감사 | 예 | 기준선 origin/develop d38b99f 대비 `git diff origin/develop...HEAD -- backend`로 119개 파일 변경을 확인할 수 있다. 이동(config → bootstrap·web.auth)도 rename으로 추적된다 |
| 상태 보고 | 예 | 커밋 제목이 유형 접두어 + 한국어 요약이고 본문에 바꾼 클래스·시험을 목록으로 적었다. 다만 `merge:`는 AGENTS.md 타입 목록(feat, fix, docs, style, refactor, test, chore) 밖이고 CHANGELOG는 없다 |

아니오 1건 → 조건부 요인.
교정: 1단계를 develop에 올리기 전에 Jira 이슈(예: "백엔드 1단계 common·audit·policy")를 만들고, MR 제목 `feat: 백엔드 1단계 common·audit·policy [S15P31A207-NN]`과 본문 `Closes S15P31A207-NN`으로 사후 연결한다. 이후 단계는 `feature/S15P31A207-NN-member` 식으로 시작한다. 형상 관리 대상에 개발 비용은 넣지 않는다.

### 6. 집계와 판정

| 등급 | 건수 |
|------|------|
| 치명 | 0 |
| 중 | 6 (매개변수 3: StandardDistance·OperationSettingService·OperationSettingHistory, 함수 길이 1: SettingKey.defaultLandmarks, 변수명 합산 1: SettingKey `v`, 실행기 수명 1: AsyncConfig) |
| 경 | 14 (스타일·규약 7, 정확성·보안 5, 객체지향 2) |

판정: **조건부**
근거: 치명 0, 중 6(4 이상 10 이하), 형상 관리 1항목 아니오(변경 통제). 설계 일치 점검은 수행했고 1단계 범위 안 설계 불일치 결함은 0건이다(이름·시그니처 차이 9건은 모두 설계서 갱신 요청으로 분류).
U9(생성자 주입 의존을 매개변수 기준에서 뺄지)를 받아들이면 중이 5건이 되어도 판정은 같다.

미구현 패키지(1.1)는 진척 항목이라 판정에 넣지 않는다.

조치 상태(2026-10-20): 중 6건·경 14건 모두 **수정 중(B2에 포함)**. MR !8 전체 판정은 [code-review.md](../code-review.md)의 집계와 판정을 따른다.

### 7. sw-test 전달: 우선 테스트 대상

복잡도 10 초과 함수는 없다. 분기·경계가 몰린 함수를 고른다.

| 함수 | 약산 복잡도 | 이유 |
|------|-------------|------|
| common.TagSet.of | 4 (조건식 8) | 개수 0·1·5·6, 분류 중복, 러너가 7분류(CREW_MOOD) 선택 — 경곗값·원인-효과 |
| policy.LandmarkStage.CREW_TOTAL.label | 4 | 380,000km·384,400km·768,800km 세 경계와 그 아래 지구 바퀴 문구 |
| policy.LandmarkTable.progressAmong | 4 | 넘은 랜드마크 없음(첫 랜드마크 %), 정확히 같은 거리, 개인용에서 CREW_TOTAL만 있는 표(빈 목록) |
| policy.TierBoundary.midScoreOf | 4 | 브론즈·마스터 열린 구간, UNRANKED 거부 |
| policy.OperationSetting.change | 3 | actorId 공백, validate 실패, validateTransition 실패(상점 ID 삭제·부위 변경) |
| policy.SettingKey.SHOP_ITEMS·ITEM_PRICES.validateTransition | 2 | 판매 중지 허용 / ID 삭제·부위 변경 거부(D-44, T-11) |
| policy.SettingKey.LLM_DAILY_LIMIT.checkRange | 1 (조건식 3) | 전체 200·201 경계(D-62, T-11) |
| policy.OperationPolicy.load·parseStored | 2 | 저장값 없음 → 기본값, 읽을 수 없는 저장값 → 기본값, 범위 밖 저장값(2.1 C2 회귀 시험) |
| policy.SkinToneRule.toneOf / BodyShapeRule.stageFor | 3 / 1 | 04:00 직전 밤으로 감기, 첫 하한 0 아닌 규칙(2.1 C3) |
| policy.OperationSettingService.change | 2 | 커밋 뒤 invalidate가 사건 수신자보다 먼저 도는지, 거부 시 이력·감사·사건 0(통합 시험 있음) |

### 8. 설계서 갱신 요청 (코드가 옳고 설계가 낡은 경우, `/sw-design-oo` 재호출 목록)

구현자가 남긴 결정의 판정: 7건 모두 **설계서 갱신 요청**이고 코드 결함이 아니다(U1~U7). 리뷰 중 찾은 것 3건(U8~U10)을 더했다.

| ID | 대상 절 | 내용 | 이유 |
|----|---------|------|------|
| U1 | 6.1.9 운영 기준 값 객체 | `RunimalRule.threshold(TagCategory)`, `BodyShapeRule.stageFor(int) → int(단계 번호 1~4)`, `SkinToneRule.toneOf(LocalTime) → int(시간대 번호 0~4)`, `OperatorWhitelist(Map<String, Set<String>>).contains(String provider, String accountId)`로 고친다 | 설계 시그니처대로면 policy → runimal·avatar·member 화살표가 생겨 D7·D20·D1과 순환한다. 대신 생긴 숨은 의존(번호 ↔ avatar.BodyShape·SkinTone 순서, "KAKAO"·"GOOGLE" 문자열 ↔ member 제공자 이름, TagCategory 1~4 ↔ RunimalAxis)을 design-review 숨은 의존 표에 올리고, 받는 쪽 패키지를 구현할 때 순서·이름 계약 시험을 14장에 넣는다. 대안(RunimalAxis·BodyShape·SkinTone을 common으로)도 함께 검토 |
| U2 | 6.1.9 SettingKey, 부록 C | "21개 상수"를 "22개(기준항목 21 + OPERATOR_WHITELIST)"로. ITEM_PRICES는 `Map<Long, Integer>`, CREW_RUN_LOBBY_OPEN·CREW_RUN_END_RULE은 각자 형식을 parse하고 OperationPolicy.shopCatalog()·crewRunRule()이 두 키를 합친다고 적는다 | 설계 표가 이미 22행이고, 한 키의 parse가 두 키 값으로 된 값 객체를 만들 수 없다. 형식은 2절 S9 교정(전용 record)으로 정하면 좋다 |
| U3 | 6.1 예외 규칙, 6.1.1 common, 13.1 예외 | 사유 코드 enum 이름 `common.ReasonCode`, 화면 문구(`message()`)를 enum이 갖는다는 것, 상수를 패키지 구현 때마다 더한다는 것을 적는다. common 유지 조건(D-48)과 충돌하므로 (가) 예외로 인정하고 이유를 남기거나 (나) ArchUnit 규칙 6에 "사유 코드 인터페이스" 예외를 두고 패키지별 enum으로 나누는 것 중 하나를 정한다 | 6.1.13은 "ApiExceptionHandler가 화면 문구로 바꾼다"인데 문구가 common에 있다. 남은 11개 패키지 구현마다 common이 바뀌어 design-review H-12(사건을 common에서 뺀 이유)와 같은 압력이 생긴다 |
| U4 | 6.1.9 OperationSetting | `static OperationSetting initial(SettingKey key, LocalDateTime now)`(값 = srs.md 예시값) 추가 | 행이 없는 항목의 첫 변경에 필요하다. 이력의 before가 기본값이 된다 |
| U5 | 6.1.9 LandmarkStage, A-18 | Landmark.distance를 "roundTrip이면 srs.md 3.3.1 표의 왕복 거리"로 정의하고 DOMESTIC 식을 n = ⌊total ÷ distance⌋로 고친다 | 코드 결과는 설계 식과 같고, 표 값을 그대로 쓰는 쪽이 오기 위험이 적다 |
| U6 | 6.1.13 ApiExceptionHandler | IllegalStateException → 409 "처리할 수 없는 상태", Bean Validation 실패(MethodArgumentNotValidException) → 400을 더한다. 2.1의 낙관적 잠금 충돌 → 409도 함께 정한다 | 13.1은 예외 4종을 ApiExceptionHandler가 바꾼다고 하는데 6.1.13 표에는 3종뿐이다. Bean Validation은 팀 규약 2장(DTO 검증)이 요구한다 |
| U7 | 6.1.11 AuditRecord | 패키지 전용 `AuditRecordView view()` 추가 | getter를 열지 않고 투영을 만드는 방법이라 13.1 "getter는 조회 줄의 것만"과 맞는다 |
| U8 | 5.1.1 bootstrap, 6.1.13 | EmbeddedRedisConfig(local·test 프로필에서 내장 Redis 실행)를 bootstrap 포함 클래스에 더하거나 놓을 패키지를 정한다. bootstrap 책임 한 문장도 고친다 | 설계서에 없는 클래스이고 bootstrap이 시간적 응집으로 기운다. 12.2 화살표는 바뀌지 않는다(외부 라이브러리만 import) |
| U9 | 13.1 서비스 의존 / 리뷰 기준 | 생성자 주입 매개변수를 매개변수 4개 기준에서 뺄지(Fan-Out 상한 7로 따로 본다) 정한다 | OperationSettingService는 6.1.9 "의존" 줄 5개를 그대로 받는다. 정하지 않으면 다음 단계 서비스마다 중 결함이 나온다 |
| U10 | 6.1.9 OPERATOR_WHITELIST, 6.1.12 operator | 첫 운영자를 넣는 절차(Flyway 시드 또는 환경 변수 → 기동 시 1회 저장)를 정한다 | 기본값이 빈 목록인데 validate는 1개 이상을 요구하고, 변경은 확인된 운영자(actorId)만 할 수 있어 관리툴로는 첫 운영자를 만들 수 없다 |

팀 규약 문서 갱신 요청(설계서 아님, `자율프로젝트_코드컨벤션.md`): 2장의 Java 25·Spring Boot 4.1·Security 7을 Java 21·Spring Boot 3.5·Security 6으로(AGENTS.md·D-01 기준), 패키지 예시(`global/`, `{도메인}/controller…`)를 설계서 5.1.1 계층 패키지로, 서비스 `@Transactional` 위치를 13.1(public 메소드 단위)과 맞춘다. 규약 4장 규칙대로 변경 이력 한 줄과 Jira 이슈를 건다.

### 9. 다음 행동

- [ ] 중 결함 6건 수정(2절 4건 + 매개변수 묶기 2건 또는 U9 결정) — 담당 백엔드 구현자, 2단계(member) 착수 전
- [ ] 2.1의 AsyncConfig 실행기 수명(C1)은 비동기 수신자(analysis·runimal)가 생기기 전에 고친다
- [ ] 설계서 갱신: `/sw-design-oo`로 U1~U10 반영 뒤 `/sw-design review`로 숨은 의존(U1)과 common 안정성(U3) 재확인
- [ ] 변경 통제: Jira 이슈 생성 → MR `[S15P31A207-NN]`·`Closes` 연결, 다음 단계부터 키 붙은 브랜치
- [ ] `/sw-test` 호출: 7절 10개 함수 우선

## 리뷰 2026-10-21 (2차)

대상 커밋: feature/runkk-impl f20a3f2 (1차 9f2af7e, 기준선 origin/develop d38b99f). 1차 이후 커밋 140개(`merge:` 12개).

입력: S15P31A207-impl/backend (feature/runkk-impl f20a3f2, 기준선 origin/develop d38b99f), docs/sw/design-oo.md v0.8~v0.10 본문(06-01~06-07, 12, 13-14), design-review.md, 팀 규약 v0.1.
방법: 설계 대조(클래스·시그니처·의존) 스크립트 + 정적 지표 스크립트 + 위험 순 정독(battle·reward·running·crew·realtime·security) + 보조 리뷰(ecc:java-reviewer, ecc:security-reviewer, sonnet). 최종 판정과 등급은 sw-code-review 기준으로 직접 재분류했다. 테스트는 실행하지 않았다(읽기 전용). `build/`는 보지 않았다. 보조 리뷰의 "미재현" 지적은 그렇게 표시했다.

### 리뷰 기준

| 항목 | 기준 | 출처 |
|------|------|------|
| 매개변수 수 | 4개 이하(5개 이상은 중). 생성자 주입 매개변수와 record 구성요소는 세지 않는다 | design-oo 13.1 D-73, 1차 기준 |
| 중첩 깊이 | 3단계 이하 | 기본 규칙 |
| 함수 길이 | 50줄 이하 | 기본 규칙 |
| 순환 복잡도 | 10 이하(약산: 조건 + 반복 + case + &&/\|\| + 삼항 + 1) | 기본 규칙 |
| 결합도 / 응집도 / Fan-Out | 스탬프 / 통신적 / 7 이하 | design-review.md 0.1 |
| 의존 방향 | 12.2 D1~D74, 13.1 ArchUnit 규칙 10개 | design-oo |

코딩 규약: `자율프로젝트_코드컨벤션.md` v0.1 (`@Value` 금지, 설정은 `@ConfigurationProperties` 등). 형상 관리: Git, `feature/{Jira 키}-{작업명}`, 커밋 `{타입}: {내용} [{Jira 키}]`(AGENTS.md).
보조 기준: ecc java-coding-standards·springboot-security·jpa-patterns의 점검 항목을 보조 리뷰 지시에 반영.

### 대상

main Java 585개 파일 35,563줄(+ 마이그레이션 V1~V11, application.yml), 시험 128개 파일 19,755줄. 기준선 대비 backend 803개 파일 +58,598/−208, 이 중 1차 이후 커밋 140개(`merge:` 12개). 파일별 줄 수 표는 1차 결과서 형식이 585행이라 패키지별 요약으로 대신한다.

| 패키지(파일 수) | 대응 설계 절 |
|-----------------|--------------|
| common 26, audit 7, policy 34, bootstrap 6 | 6.1.1, 6.1.11, 6.1.9, 6.1.13 |
| member 59, notification 13, running 46 | 6.1.2, 6.1.10, 6.1.3 |
| battle 70(+live), runimal 27, reward 11, avatar 29 | 6.1.6, 6.1.7 |
| crew 91(+live), analysis 51, profile 4, operator 5 | 6.1.7~6.1.12 |
| web.* 33, realtime 11, batch.* 20 | 6.1.13, 5.7 |
| infra.* 48 | 5.1.1 |

### 1차 지적 추적표 (중 6, 경 14)

| 1차 ID | 1차 지적 | 현재 상태 | 근거(현 코드) | 해소 |
|--------|----------|-----------|---------------|------|
| S1(중) | StandardDistance 매개변수 5 | 해소 | 생성자 `(long meters, Use... uses)`, `Set<Use>` | 예 |
| S2(중) | OperationSettingService 생성자 5 | 해소(기준 변경) | 설계 13.1 D-73이 생성자 주입 제외를 확정. 코드 그대로 5개 | 예 |
| S3(중) | OperationSettingHistory 매개변수 5 | 해소 | `(SettingChangeResult change, LocalDateTime changedAt, String operatorId)` | 예 |
| S4(중) | defaultLandmarks 51줄 | 해소 | SettingKey.Defaults.LANDMARKS 리스트 상수 + `landmarksJson()` | 예 |
| S5(중) | 한 글자 변수 `v` | 해소 | `condition`, `coaching`, `shape`, `course`로 바뀜 | 예 |
| C1(중) | AsyncConfig 실행기 수명 | 해소 | `applicationTaskExecutor` 주입, `spring.task.execution.shutdown.await-termination: true` | 예 |
| S6(경) | 매직 넘버 | 해소 | `MAX_TIER_WINDOW` 등 상수 | 예 |
| S7(경) | AsyncConfig 하드코딩 | 해소 | application.yml `spring.task.execution.pool.*` | 예 |
| S8(경) | EmbeddedRedisConfig `@Value` | 해소 | 해당 파일에 `@Value` 없음(다른 18개 파일의 `@Value`는 새 지적 L1) | 예 |
| S9(경) | 문자열 키 중복 | 해소 | `CrewRunLobby` record, `lobby.lobbyOpenBeforeMinutes()` | 예 |
| S10(경) | m→km 1000 중복 | 해소 | `Distance.exactKm()` 사용(CalorieCoefficient, CrewRunRule) | 예 |
| S11(경) | 넓은 catch | 해소 | `catch (IllegalArgumentException e)` + `log.debug` | 예 |
| S12(경) | @DisplayName | 부분 해소 | 클래스 수준은 101개 모두 있음, 메소드 수준은 대부분 없음(새 지적 L31로 이관) | 예(부분) |
| C2(경) | 저장값 범위 검사 없음 | 해소 | `parseStored`가 `key.validate(json).valid()` 먼저 검사 | 예 |
| C3(경) | bands 첫 하한 | 해소 | `shape.bands().get(0) == 0` | 예 |
| C4(경) | 낙관적 잠금 409 | 해소 | ApiExceptionHandler가 OptimisticLockingFailureException → 409 | 예 |
| C5(경) | httpBasic | 해소 | `.httpBasic(AbstractHttpConfigurer::disable)` | 예 |
| C6(경) | h2-console 프로필 | 해소 | `@Profile("local")` 별도 체인 | 예 |
| O1(경) | RunimalRule.threshold 형 안전 | 해소 | threshold 제거, 구성요소 직접 읽기 | 예 |
| O2(경) | OperationPolicy 다운캐스트 19곳 | 해소 | `valueOf(key, Class<T>)` 사용 | 예 |

해소 20/20(중 6, 경 14). 설계 이탈 U1~U10은 설계서가 v0.8~v0.10에 반영해 닫혔다(U1 순환 회피 시그니처, U4 initial, U8 EmbeddedRedis 제외).

집계: 해결 19, 일부 1(S12 메소드 수준 @DisplayName, 새 지적 L31로 이관), 미해결 0. 원자료의 "해소 20/20"은 S12를 부분 해소로 보고 해소에 센 값이다.

### 1. 설계 일치

#### 1.1 클래스·시그니처 대조

- 설계서 6.1.x `#####` 클래스 표제 185개 중 표제 아닌 항목(헤더 7개)을 뺀 클래스는 코드에 모두 있다. 빠진 클래스는 `policy.OperatorWhitelistSeeder` 1개이며 코드는 같은 일을 `operator.OperatorBootstrap`이 한다(아래 U2).
- 설계 표의 메소드명 371개를 코드 소스와 대조: 없는 것 5개 — `MatchTicket.release`(선언은 있으나 호출 0), `Matchmaker.pairUp`·`resolveUnpaired`(통합 구현, 아래 M1), `CrewRunLiveSession.relayFor`, `CrewRunService.endByDeletion`(다른 이름으로 구현). 나머지는 이름 일치.
- 패키지 53개 모두 존재하고 `mainClasses_resideInDesignPackages`가 53개 밖 클래스를 막는다.

| 설계서 항목 | 코드 | 일치 | 비고 |
|-------------|------|------|------|
| Matchmaker.tick = partnerOf + pairUp + resolveUnpaired (v0.10 D-64·D-79: tryAssign/release, 실패 시 WAITING 복귀, 30초에 MatchFailed) | tick 하나. 실패하면 두 표 `remove` 후 즉시 MatchFailed, 고스트는 `remove`(tryAssign 아님) | 아니오(코드가 나쁨) | 결함 M1 |
| Battle.judge: FINISHED 뒤 재판정은 결과가 바뀔 때만 정정 | 같은 결과도 `apply`를 다시 하고 Optional 반환 | 아니오(코드가 나쁨) | 결함 M2 |
| MembershipService.approve: "7일 경과 재확인 → crew 잠금" (06-04) | requirePending만 확인 | 아니오 | 경 L12 |
| OperatorWhitelistSeeder(policy, 행위자 SYSTEM) | operator.OperatorBootstrap(actor 고정값) | 아니오(위치) | 갱신 요청 U2 |
| 13.1 "public 메소드 @Transactional" | 낙관적 재시도가 필요한 곳은 `TransactionTemplate`(battle·crew·analysis·avatar·runimal) | 아니오(의도) | 일관됨, 갱신 요청 U5 |
| 예외 4종 + 낙관적 잠금 409 | ApiExceptionHandler가 낙관적 잠금과 DataIntegrityViolation을 409로 | 예 + 확장 | 경 L2 |

#### 1.2 의존 방향 대조

실제 import를 53개 slice(battle.live·crew.live는 상위로 합침)로 모아 12.2 D1~D74와 대조: 서로 다른 slice 사이 화살표 130쌍(common 포함). 목록에 없는 화살표 0, 순환 0, 계층 역행 0. 완전수식 이름 사용 `web.crew.CrewDtos`의 `com.a207.runkk.crew.PostKind`·`VoteAnswer`도 D50(web.crew → crew) 안이다.
Fan-Out(common 포함): analysis 7, battle 7, crew 7(상한), 그 밖 6 이하. 상한 7 이내.
설계에 없는 의존 화살표:

| From → To | 주고받는 것 | 결합도 | 등급 |
|-----------|-------------|--------|------|
| 없음 | — | — | — |

ArchUnit 표본 확인: 규칙 4(저장 포트), 9(setter)의 setter 규칙은 `set` + 같은 이름 필드만 잡는다. `analysis.RunningGoal.setWeeklyGoal`(설계 6.1.4 업무 동사)은 필드명이 달라 규칙 밖이며 설계 이름 그대로라 결함이 아니다.

#### 1.3 응집도 회귀

| 패키지 | 설계 판정 | 코드 | 판정 |
|--------|-----------|------|------|
| common | 불변 기본 형식(D-48), ReasonCode만 예외(D-72) | `AttemptLimiter`(가변 메모리 상태, 시도 제한)와 `PageCursor`(목록 자르기)가 더해졌다 | 경 L3, 갱신 요청 U3 |
| battle·crew | — | `AfterCommit`이 두 패키지에 거의 같은 사본, `*Transactions` 재시도 틀이 5개 패키지에 사본 | 경 L4 |
| bootstrap | 시작점 + 사건 발행기 | EmbeddedRedisConfig, ProductionSecretsCheck가 더해짐(시간적 응집) | 갱신 요청 U4 |

### 2. 코딩 스타일·정확성

