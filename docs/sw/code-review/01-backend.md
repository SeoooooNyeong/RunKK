> [런크크 코드 리뷰 결과서](../code-review.md) 목차 · 리뷰 2026-10-20 (1차) · 다음: [02-android](02-android.md) · 2차: [리뷰 2026-10-21 (2차)](#리뷰-2026-10-21-2차) · 3차: [리뷰 2026-10-21 (3차)](#리뷰-2026-10-21-3차) · 4차: [리뷰 2026-10-21 (4차)](#리뷰-2026-10-21-4차)

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

#### 2.1 결함 (등급순)

| ID · 파일:줄 | 항목 | 현재 | 교정 예시 | 등급 |
|--------------|------|------|-----------|------|
| M1 · battle/Matchmaker.java:56-74, 85-110 | 상태 전이·설계 일치(D-64, D-79) | 고스트 경로 `else if (ticket.isExpired(now, cond.ghostAfter()) && matchQueue.remove(ticket.getMemberId())) { … transactions.once("ghost_match", …); if (!created) notifyFailed(...) }` — tryAssign 없이 표를 지운다. 지운 뒤 대결이 저장되기 전 짧은 구간에 `MatchingService.cancel`이 오면 `removeWaiting` false, `existsActive` false라 "대기해제"로 답하는데 고스트 대결이 뒤이어 만들어진다. 대결 저장 실패도 표를 되돌리지 않고(`MatchTicket.release` 호출 0) 첫 실패에 MatchFailed를 보낸다. 설계는 30초까지 다음 tick에 재시도 | `matchQueue.tryAssign(id)`(WAITING→ASSIGNED, 이미 배정·취소면 false)로 먼저 잡고, 실패하면 `matchQueue.release(id)`로 WAITING 복귀 + 로그, `isExpired(now, ghostAfter + GIVE_UP_MARGIN)`일 때만 MatchFailed. MatchQueue에 tryAssign·release 포트 추가 | 중 |
| M2 · battle/Battle.java:333-334, 451 | 상태 전이 | `boolean changed = status == BattleStatus.FINISHED && !verdicts.equals(currentVerdicts()); return Optional.of(apply(verdicts, now, changed));` → `corrected = isCorrection;` — 이미 FINISHED이고 결과가 같으면 `corrected`가 false로 덮이고 judgedAt이 바뀌며 `BattleResultService.announce`가 BattleJudged를 다시 발행한다(이중 적립은 reward 멱등이 막음) | `if (status == FINISHED && verdicts.equals(currentVerdicts())) return Optional.empty();` 그리고 `corrected = corrected \|\| isCorrection;` | 중 |
| M3 · battle/BattleResultService.java:103-109, battle/Battle.java:136 | 동시성(미재현) | 참가자 두 명의 RunRecordSaved가 동시에 처리되면 각 트랜잭션은 자식 `BattleParticipant`만 바꾸고(`judge`가 빈 값) 부모 `Battle` 행은 그대로라 `@Version`이 오르지 않아 충돌 없이 둘 다 커밋된다. 서로 상대 기록을 못 본 채 판정이 늦어져 카운트다운 종료·24시간 만료까지 RESULT_PENDING에 머문다 | `battle.verify(...)` 직후 `entityManager.lock(battle, LockModeType.OPTIMISTIC_FORCE_INCREMENT)` 또는 `battleRepository.findByIdForUpdate`. BattleThreadIntegrationTest에 양쪽 동시 업로드 시험 추가 | 중 |
| M4 · reward/RewardService.java:43,65 · crew/WeeklyRaceService.java:67 · crew/CrewRunService.java:139 | 사건 수신자 예외 | `public void onRunSaved(RunRecordSaved e) {` — AFTER_COMMIT 수신자가 예외를 그냥 던진다. 업로드 호출자에게 500이 가고 뒤따르는 수신자가 건너뛰어지며, 앱이 재전송하면 `alreadyStored`라 사건이 다시 안 나가 적립·주간 거리가 유실된다. 같은 사건의 BattleResultService·TierUpdateListener는 잡는다 | `try { accrue(...); } catch (RuntimeException ex) { log.error("reward_accrual_failed recordId={}", e.recordId(), ex); }` + 보정 배치(기록 ID 근거 재적립은 고유 제약이 멱등 보장) | 중 |
| M5 · web/auth/AuthController.java:41,61 · web/auth/SignUpController.java:41 | 보안(방어 누락) | `@PostMapping("/login")` — 로그인·갱신·가입 공개 경로에 속도 제한이 없다. 요청마다 카카오·구글 외부 호출이 나간다(관리툴 로그인과 초대 코드에만 AttemptLimiter) | 접속지 키 `AttemptLimiter`를 `/auth/*`에 적용하거나 nginx `limit_req` | 중 |
| M6 · infra/integration/auth/JwtTokenIssuer.java:55 | 보안(영속성) | `private final Map<String, Instant> revokedRefreshIds = new ConcurrentHashMap<>();` — 폐기한 갱신 토큰 목록이 메모리뿐이라 재시작하면 폐기한 14일 토큰이 되살아난다(탈취 + 재시작 조건) | `revoked_refresh(jti, expires_at)` 테이블 또는 Redis `SET jti 1 NX EX ttl`로 원자 폐기 | 중 |
| P1 · web/battle/BattleController.java:140 | 매개변수 6개 | `history(@RequestAttribute long memberId, @RequestParam … from, to, type, cursor, size)` | 요청 파라미터를 `record HistoryQuery(LocalDate from, LocalDate to, BattleType type, PageCursor page)`로 묶어 `@ModelAttribute`로 받음 | 중 |
| P2 · web/crew/CrewController.java:74 | 매개변수 5개 | `search(memberId, keyword, tags, cursor, size)` | `record CrewSearchQuery(String keyword, Set<String> tags, PageCursor page)` | 중 |
| P3 · web/crew/CrewPostController.java:35 | 매개변수 5개 | `list(memberId, crewId, kind, cursor, size)` | `PageCursor`를 바로 받아 4개로 | 중 |

M1~M6 중 M1·M2는 설계가 이미 정한 동작을 코드가 어겼거나(M1) 상태 전이 가드가 느슨한 경우(M2), M3은 동시성 가정(미재현)이다. M3·M4는 시험으로 먼저 재현하되, 재현 전이라도 코드 구조상 가능하다고 판단해 중으로 둔다.

| ID · 파일:줄 | 항목 | 현재 | 교정 예시 | 등급 |
|--------------|------|------|-----------|------|
| L1 · 18개 파일 34곳 (batch/*SchedulingConfig, analysis/AiCommentGenerator.java:30 등) | 규약(`@Value` 금지) | `@Value("${runkk.batch.ranking-rebuild-cron}") String rankingCron,` | `@ConfigurationProperties("runkk.batch") record BatchProperties(String rankingRebuildCron, …)` | 경 |
| L2 · web/error/ApiExceptionHandler.java:82-85 | 예외 처리 | `@ExceptionHandler({OptimisticLockingFailureException.class, DataIntegrityViolationException.class})` — NOT NULL·길이 위반 같은 프로그래밍 오류도 "CONCURRENT_UPDATE 다시 시도해 주세요"로 숨는다 | 낙관적 잠금만 409, 무결성 위반은 제약 이름으로 분기하고 나머지는 500 + 로그 | 경 |
| L3 · common/AttemptLimiter.java:17, common/PageCursor.java:14 | 응집도(common) | 가변 메모리 상태 클래스와 목록 자르기 도구가 common에 있다 | AttemptLimiter는 쓰는 패키지(crew·web.admin) 밖 공통이 없으면 `web.error`·`crew`로, 아니면 U3 결정 후 유지 | 경 |
| L4 · battle/AfterCommit.java, crew/AfterCommit.java; *Transactions 5개(battle 64줄, crew 69줄, analysis, avatar, runimal) | 중복 코드 | 두 AfterCommit의 차이는 try/catch 하나뿐, 재시도 틀 5개가 비슷하다 | common에 두기 어려우면(규칙 6) 패키지별 사본의 차이를 문서화하거나 infra 공용 빈 대신 한 곳으로 | 경 |
| L5 · notification/NotificationService.java:51,68 | 트랜잭션 안 외부 호출 | `@Transactional public void notify(…)` 안에서 `pushGateway.send(tokens, …)` | 저장은 트랜잭션, 푸시는 `AfterCommit.run(...)` | 경 |
| L6 · battle/BattleResultService.java:153, battle/LiveBattleService.java:109, crew/CrewRunAnnouncer.java:58 | 재시도 구간 안 알림 | `notificationService.notify(` 가 낙관적 재시도·행 잠금 구간 안이라 재시도 때 중복 알림 가능 | `AfterCommit.run(() -> notificationService.notify(...))` | 경 |
| L7 · battle/LiveBattleService.java:155,175, battle/BattleClockService.java:114, crew/CrewRunLiveService.java:74 | 잠금 객체 제거 경합 | `sessionLocks.remove(id)` ↔ `sessionLocks.computeIfAbsent(id, i -> new Object())` — 제거 직후 다른 스레드가 새 잠금을 만들어 같은 세션에 잠금이 둘이 될 수 있다 | 종료 때만 제거하거나 `Striped<Lock>` | 경 |
| L8 · battle/Matchmaker.java:107 | 로그 오류 | `log.warn("random_match_dropped tickets={}", LiveBattleSession.RUNNERS);` — 상수 2를 찍는다 | `log.warn("random_match_dropped members={},{}", first.getMemberId(), second.getMemberId());` | 경 |
| L9 · battle/Matchmaker.java:56-74 | 오류 격리 | for 루프 안 `tryAssignPair`/`remove`가 예외를 던지면 그 tick 전체가 멈춘다(BattleTickJob이 잡아 다음 tick) | 표마다 `try { … } catch (RuntimeException e) { log.warn(…); }` | 경 |
| L10 · battle/MatchingService.java:81-85, battle/RecordBattleService.java:100,130 | 활성 대결 확인 | `matchQueue.findWaiting(id).isPresent() \|\| battleRepository.existsActive(id)` 확인과 대기 등록·대결 생성이 원자적이지 않다(DB 제약 없음). 기록 대결과 대기 등록이 겹치면 한 회원이 두 대결에 있을 수 있다 | 대결 생성 때도 대기열·`existsActive`를 확인하거나 회원별 활성 대결 부분 고유 인덱스 | 경 |
| L11 · infra/battle/JdbcMatchQueue.java:89-103 | 정리 누락 | `update match_ticket set status = 'ASSIGNED' where member_id in (?, ?)` — 배정 직후 장애로 남은 행이 `on conflict do nothing` 때문에 재요청을 계속 막는다(Redis는 TTL 5분) | `assigned_at` 열 + 5분 넘은 ASSIGNED 행 삭제 | 경 |
| L12 · crew/MembershipService.java:145-153 | 설계 일치 | `requirePending(request);` — 7일 지난 신청을 승인할 수 있다(만료 작업이 도는 사이) | `if (request.requestedAt().plusDays(JoinRequest.EXPIRY_DAYS).isBefore(now)) throw new BusinessRuleException(ReasonCode.REQUEST_DECIDED);` | 경 |
| L13 · infra/crew/JpaCrewRepository.java:100-111 | 성능·정확성 | `query.getResultList().stream().filter(c -> c.tags().containsAll(cond.tags())).toList();` 전체 공개 크루를 읽고 메모리에서 거름. LIKE의 `%`·`_` 이스케이프 없음 | `escape '\'` + `setMaxResults` + 태그 조건을 SQL로 | 경 |
| L14 · reward/RewardWallet.java:66-70,102-106 | 성능 | `accruals.stream().filter(a -> a.isOn(req.at())).mapToInt(RewardAccrual::amount).sum();` 적립마다 전체 적립 내역을 읽는다 | `select sum(amount) … where accrued_at >= :today`, 중복은 `uq_reward_accrual_source`에 맡김 | 경 |
| L15 · battle/Battle.java:320-335 | 사건 누락 | 카운트다운 종료 전에 양쪽 기록이 오면 COUNTDOWN에서 곧장 FINISHED가 되어 `BattleEnded` 전송·세션 삭제를 건너뛴다(3초 뒤 detectTimeouts가 정리) | 판정 시점에 `AfterCommit.run(() -> messenger.send(..., BattleEnded))` | 경 |
| L16 · running/TierUpdateListener.java:36-62 | 유실 | `catch (RuntimeException failure) { log.error("tier_update_failed …"); return; }` 일시 오류면 한 판의 티어 점수가 재처리 없이 사라진다 | 실패 건을 보정 배치가 읽도록 기록하거나 재시도 후 로그 | 경 |
| L17 · crew/CrewWithdrawalListener.java:80-82 | 경합(저확률) | `if (leader) { … log.warn("crew_leader_withdrawn_with_members …"); return; }` 가드 검사와 AFTER_COMMIT 사이에 크루원이 들어오면 탈퇴한 크루장이 소속으로 남는다(코드 주석이 운영 확인 대상) | 크루 행 잠금 후 CrewLeaderGuard 재검사 또는 자동 위임 | 경 |
| L18 · running/RunRecord.java:153-185, running/ValidityRule.java | 입력 신뢰(설계 공백) | 설계 4조건 그대로이나 `upload.distance()`·`elapsed()`를 포인트 경로 길이·`endedAt-startedAt`과 대조하지 않는다. 포인트 2개 + 거리 42000m도 유효 기록 | 경로 길이·시간 허용 오차 검사를 ValidityRule 5번째 조건으로. 요구 공백이라 SRS·설계 갱신 요청 U6 | 경 |
| L19 · bootstrap/ProductionSecretsCheck.java:26, application.yml:6-9 | 설정(추정) | `if (environment.acceptsProfiles(Profiles.of("prod")))` — 프로필 미지정이면 local(H2, 인증 없는 h2-console, 임의 JWT 키)로 뜬다 | local 프로필에서 `server.address` 비루프백이면 기동 거부, 또는 `!local && !test`에서 검사 | 경 |
| L20 · web/me/MeController.java:133, web/auth/SignUpController.java:72, web/crew/CrewDtos.java:82 | 입력 검증 | `@Size(max = TEXT_MAX) String photoUrl,` 스킴·호스트 미검사 | `@Pattern(regexp = "^https://[^ ]{1,480}$")` | 경 |
| L21 · web/auth/AuthFilter.java:56 | 인증 | `memberId = tokenIssuer.verify(header.substring(BEARER.length()));` 서명·만료만 본다. 탈퇴 회원 접근 토큰이 최대 1시간 | 짧은 TTL 캐시로 ACTIVE 확인 | 경 |
| L22 · realtime/LivePollingController.java:46 | 입력 크기 | `@RequestBody String body)` 크기 상한 없음(WebSocket은 1024바이트) | `if (body.length() > MAX_MESSAGE_BYTES) return ResponseEntity.badRequest().build();` | 경 |
| L23 · web/auth/SecurityConfig.java:84-85 | 방어 한 겹 | `.requestMatchers(API + "/admin/**").permitAll()` 관리툴 인증이 OperatorCheckInterceptor 하나에 있다(현재 매핑 빈틈 없음) | `/admin/login`만 permitAll, 나머지 `hasRole("OPERATOR")` 이중화 | 경 |
| L24 · web/admin/setting/SettingAdminController.java:43 | 권한 상승 | `return operationSettingService.change(actorId, key, value.toString());` 운영자가 OPERATOR_WHITELIST를 바꿔 다른 운영자를 추가·전원 삭제 가능(감사에는 남음) | 화이트리스트 키는 최소 1명 유지 검증 | 경 |
| L25 · web/admin/AdminAuthController.java:81, operator/OperatorAuthorizer.java:63 | 감사·신뢰 | `return real.strip();` X-Real-IP를 사설망 출처면 믿고, 관리툴 로그인 성공·실패가 감사에 안 남는다 | 신뢰 프록시를 설정으로 지정, `AuditService`에 로그인 기록 | 경 |
| L26 · infra/integration/ai/AiServerClient.java:57, application.yml:111-112 | 내부 토큰 | `.defaultHeader(TOKEN_HEADER, token)` http면 평문 전송(기본 localhost). ai 서버의 상수 시간 비교는 이번 범위 밖이라 확인 불가 | ai 쪽 `hmac.compare_digest` 확인, 원격이면 TLS | 경 |
| L27 · application.yml:185,193-195,223-225 | 비밀 기본값 | `password: ${DB_PASSWORD:runkk}`, Redis 비밀번호 설정 없음(develop·prod) | 기본값 제거, `spring.data.redis.password: ${REDIS_PASSWORD}` | 경 |
| L28 · web/avatar/AvatarController.java:62-65 | 입력 검증 | `if (!SelfieFormat.isJpegOrPng(image)) {` 매직 넘버만 본다(크기 1MB, 하루 실패 5회 제한 있음) | ai 서버에서 최대 픽셀·디코드 실패 400 | 경 |
| L29 · realtime/TokenHandshakeInterceptor.java:41 | 세션 수명 | 접근 토큰을 핸드셰이크 때 한 번만 검증한다 | 세션 속성에 exp 저장, 만료 시 close | 경 |
| L30 · running/RunUploadService.java:61 | 정보 노출(영향 작음) | `if (runRecordRepository.exists(upload.recordId())) {` 타인 기록 ID도 alreadyStored | 존재하면 memberId 일치 확인, 다르면 409 | 경 |
| L31 · src/test/** (101개 시험 클래스) | 규약(시험 이름) | 클래스 수준 `@DisplayName`만 있고 메소드 261개 대부분에 없다 | 메소드에 `@DisplayName("…")` | 경 |
| L32 · 커밋 12개 `merge:` | 상태 보고 | `merge: 백엔드 빈 곳 메우기 …` — AGENTS.md 타입 목록 밖 | `chore:`로 쓰거나 목록에 추가 | 경 |

중첩 깊이: 4단계 이상 0. 중 3단계 10곳(VirtualRecordFactory.generate, PageCursor.slice, RankingCache.page, PersonalRecordBook.apply, DisposalService.executeDue 등)은 기준 이내.
함수 길이: 50줄 초과 메소드 0(스크립트가 record·compact 생성자 본문을 한 덩어리로 세어 128줄 등으로 나오는 FitnessSummary·SignUpForm은 손으로 확인한 결과 메소드 단위 50줄 이하). `SecurityConfig.apiChain` 45줄이 최장급.
1차 S6 형(매직 넘버): 이번 범위에서는 상수화되어 지적 없음.

### 3. 구조적 프로그래밍

goto 등가물(라벨 break·continue, switch fallthrough) 0건. `System.out`·`printStackTrace` 0건. 넓은 `catch (Exception|Throwable)` 0건, `catch (RuntimeException)` 33곳은 전부 로그를 남기고 경계(스케줄러·수신자·게이트웨이)에서만 쓴다. 가변 static 0.
복잡도(약산) 10 초과:

| 함수 | 조건 | 반복 | 약산 복잡도 | 위반 |
|------|------|------|-------------|------|
| running.PersonalRecordBook.apply | 다수(&&·삼항 포함) | 1 | 13 | 위반 아님, sw-test 대상 |
| running.RunRecord.receive | 8(+ 복합 조건) | 0 | 12 | 위반 아님 |
| infra.integration.auth.JwtOperatorTokenIssuer.verify | 다수 | 0 | 11 | 위반 아님 |
| running.RunTrack.curve | 다수 | 1 | 11 | 위반 아님 |

기타 10 이하 경계: RankingCache.page 10, GoogleLoginGateway.authenticate 10, JwtTokenIssuer.parse 10, TierBoundary 생성자 10. 반복 안 return이 있는 함수는 가드 절 범위로 허용.

### 4. 객체지향 구현

| 파일:줄 | 항목 | 현재 | 교정 예시 | 등급 |
|---------|------|------|-----------|------|
| 전체 | 캡슐화 | 엔티티 필드 private, setter는 업무 동사뿐, 다른 패키지 엔티티 import 0(ArchUnit 규칙 5·9·10) | 통과 | 없음 |
| battle/Battle.java:149-182 | 정적 생성·불변식 | `Battle.random`·`againstRecord` 정적 생성, 전이 메소드가 상태 검사 | 통과(단 M2) | 없음 |
| battle, reward, crew 상태 전이 | 상태 가드 | `requireStatus`, `forfeit`, `closeCountdown`, `JoinRequest.requirePending`, `CrewRunPost.depart` 가드 | 통과 | 없음 |
| common.StandardDistance 외 | 다형성 | 상수별 메소드·enum 다형성 유지. `instanceof` 11곳은 `Side.MemberSide` 패턴 매칭 등 sealed 성격 분기 | 통과 | 경 0 |
| 서비스 전반 | 절차적 스타일 | static 유틸 클래스 0, 서비스는 생성자 주입 | 통과 | 없음 |

### 5. 형상 관리

| 활동 | 판정 | 근거 |
|------|------|------|
| 형상 식별 | 예 | 패키지·클래스가 설계서와 1:1(위 1.1), ArchUnit이 53개 패키지 강제 |
| 버전 제어 | 예 | Git 이력 140커밋, 버전 0.0.1-SNAPSHOT. 태그 없음 |
| 변경 통제 | 아니오 | 커밋 140개 중 `[S15P31A207-…]` 키 0개, 브랜치 `feature/runkk-impl`에 키 없음. 사용자 결정으로 Jira 없이 진행(1차와 동일, 판정 요인으로만 기록) |
| 형상 감사 | 예 | `git diff origin/develop...HEAD -- backend`로 803개 파일 변경 확인 |
| 상태 보고 | 예 | 커밋 제목 유형 접두어 + 한국어 요약, 본문에 설계 절·결정 기록. CHANGELOG 없음, `merge:` 12개는 유형 목록 밖(L32) |

### 6. 집계와 판정

| 등급 | 건수 |
|------|------|
| 치명 | 0 |
| 중 | 9 (설계·상태 전이 M1·M2, 동시성 M3, 수신자 예외 M4, 보안 M5·M6, 매개변수 P1~P3) |
| 경 | 32 (L1~L32) |

판정: **조건부**
근거: 치명 0(양방향 의존·goto·내용·공통 결합 없음), 중 9(4 이상 10 이하), 형상 관리 1항목 아니오(변경 통제), 설계 일치 점검 수행. 중 P1~P3는 요청 매개변수 묶기로 한 번에 닫혀 실질 위험은 M1~M6에 몰려 있다. M1~M6을 고치고 P를 정리하면 중 0 → 합격 조건 중 변경 통제(Jira 사용 여부)만 남는다.
M3·M4는 미재현이므로 시험으로 재현 후 확정해도 되지만 구조상 가능하다.

설계가 정한 핵심 불변식은 대체로 코드가 지킨다(통과 근거): 리워드 이중 적립(지갑 행 PESSIMISTIC_WRITE + 도메인 중복 확인 + `uq_reward_accrual_source`), 잔액 음수(`CHECK balance >= 0`), 크루 정원(크루 행 잠금 후 `isFull`), 1인 1크루(`uq_crew_membership_active` 부분 고유), 가입 신청 중복(`uq_join_request_pending`), 유효 러닝 순번(장부 행 잠금 뒤 `countValid + 1`), 매칭 짝 배정(Lua `ASSIGN_PAIR` / JDBC `skip locked`), 호출 한도 카운터(Lua 한 번), AFTER_COMMIT 수신자의 새 트랜잭션(REQUIRES_NEW 또는 TransactionTemplate), 셀카 비저장(메모리 byte[]만, 임시 파일 임계 > 최대 크기, 로그 104건 전수 확인에서 개인정보 0), 인가(IDOR: 모든 회원 컨트롤러가 요청 속성의 memberId만 사용, 크루장 동작은 CrewAccess 권한표), JWT(HS256 고정·iss·type·만료, 관리툴 토큰 키 분리, 화이트리스트 요청마다 재확인).

조치 상태(2026-10-21): 중 9건(M1~M6, P1~P3)은 수정 완료, 통합 브랜치 feature/runkk-impl 67a5ffa에 반영(백엔드 수정은 12cd646까지). 경 32건(L1~L32)의 조치 결과는 다음 절에서 기록한다. 이 절의 집계·판정은 2차 리뷰 시점(f20a3f2) 기준이며 수정분의 재확인은 3차 리뷰에서 한다. MR 전체 판정은 [code-review.md](../code-review.md)의 2차 종합 절을 따른다.

### 7. sw-test 전달: 우선 테스트 대상

복잡도 10 초과 4개와 이번 리뷰의 결함 재현 시험을 우선한다.

| 함수 | 약산 복잡도 | 이유 |
|------|-------------|------|
| running.PersonalRecordBook.apply | 13 | 거리별 최고 기록 갱신·무효 기록·동률 경계 |
| running.RunRecord.receive | 12 | UUID·시각 순서·거리/시간 0·포인트 수 불일치·모드 일관성(원인-효과 표) |
| infra.integration.auth.JwtOperatorTokenIssuer.verify | 11 | 서명·iss·type·만료·형식 오류 경계 |
| running.RunTrack.curve | 11 | 거리 도달 경계·구간 보간·표본 간격 |
| battle.Battle.judge / decide | 8~9 (조건식 단위) | M2: FINISHED 재호출 시 corrected 유지, 규칙 1~4 1초 경계 |
| battle.Matchmaker.tick | 7 | M1: 대결 저장 실패 시 표 복귀, 취소와 고스트 경합, 30초 정각 |
| BattleResultService.onRunSaved | 3 | M3: 두 참가자 기록 동시 도착 |
| RewardService.onRunSaved / onBattleJudged | 2 | M4: 수신자 예외 시 유실, 재전달 멱등 |
| crew.MembershipService.approve | 5 | L12: 7일 경과 승인, 정원 경합 |
| common.PageCursor.slice | 9 | 커서 항목이 지워진 경우, 크기 경계 |

### 8. 설계서 갱신 요청 (코드가 옳거나 설계 공백, `/sw-design-oo` 재호출 목록)

구현자가 남긴 이탈과 이번에 찾은 설계에 없는 클래스를 "갱신 요청"으로 분류했다. 코드 결함으로 세지 않았다.

| ID | 대상 절 | 내용 | 이유 |
|----|---------|------|------|
| CR2-U1 | 6.1.3/6.1.4/6.1.6 등 신규 엔드포인트 | 구현자가 "설계 공백을 메운 최소 형태"로 더한 GET /battles/match(대기 상태·기본 거리), 내 크루·크루 단건, 아바타 부위 이미지(`/avatar/parts/face/{회원}/{묶음}/{부위}`), 기본 대결 거리, MemberQueryService.profileOf(MemberProfile)를 5.7.1·6.1에 올린다 | 코드가 설계 공백을 합리적으로 채움 |
| CR2-U2 | 6.1.9/6.1.12 | `OperatorWhitelistSeeder`(policy)를 `operator.OperatorBootstrap`으로 위치·이름 갱신(operator → policy → audit, 12.2 D40·D41 안) | 구현이 operator에 있음, 의존 방향 규칙 내 |
| CR2-U3 | 5.1.1 common, D-48 | `AttemptLimiter`, `PageCursor`를 common 허용 목록에 넣을지(예외로 인정) 또는 쓰는 패키지로 옮길지 결정 | common 불변 형식 원칙(D-48)과 충돌(L3) |
| CR2-U4 | 5.1.1 bootstrap, 6.1.13 | `EmbeddedRedisConfig`(local·test), `ProductionSecretsCheck`(prod 기동 검사)를 bootstrap 포함 클래스에 추가, 책임 문장 수정 | 설계에 없는 클래스 |
| CR2-U5 | 13.1 트랜잭션 | "낙관적 재시도가 필요한 쓰기는 `TransactionTemplate`(`*Transactions`)을 쓴다"와 `AfterCommit` 헬퍼 규칙 추가 | battle·crew·analysis·avatar·runimal 5곳에서 일관되게 쓰는 방식 |
| CR2-U6 | srs.md 유효 기록 4조건, 6.1.3 ValidityRule | 거리·시간과 경로 길이 일치 조건 추가 여부 결정(L18) | 요구·설계 공백 |
| CR2-U7 | 6.1.7 crew, 6.1.7 avatar, realtime, runimal, member, analysis | 설계 표에 없는 보조 클래스 약 35개(CrewAccess, CrewDeletion, CrewRunAnnouncer, CrewRunViews, CrewTags, CrewTransactions, MyCrewView, FaceImageService, SelfieConversion, SelfieFailureStore, SelfieLockStore, AvatarTransactions, BattleDelivery, BattleRandomConfig, OpponentNames, MessageMailbox, ProgressDispatcher, TokenHandshakeInterceptor, MemberLoader, RunSampleReader, RunimalTransactions, AnalysisTransactions, RankingPage, RecordRow, PlanPurgeJob, 각 web DTO 모음, infra Redis 저장소 등)를 6.1에 한 줄씩 반영 | 구현 중 생긴 협력 클래스. 의존 화살표는 12.2 안 |
| CR2-U8 | 6.1.6 Matchmaker | M1을 고치면 설계가 이미 맞으므로 갱신 불필요. 고치지 않고 현 구현(즉시 제거 + MatchFailed)을 유지하기로 하면 D-64·D-79를 개정해야 함 | 의사결정 필요(결함 M1 처리 방향) |

종합 결과서에서 이 표의 ID는 원래 ID 그대로(CR2-U1~CR2-U8) 쓴다.

### 9. 다음 행동

- [ ] M1~M6 수정(담당 백엔드 구현자): M1·M2·M3·M4는 도메인·시험 보강, M5·M6은 보안 보강. 각각 재현 시험 먼저.
- [ ] P1~P3: 요청 파라미터 record로 묶기.
- [ ] 경 32건은 L8·L12·L1(규약)부터 처리. L18·L19·L20·L27은 설정·검증 한두 줄.
- [ ] 설계서 갱신: `/sw-design-oo`로 CR2-U1~U7 반영 후 `/sw-design review`로 common 안정성(U3) 재확인.
- [ ] 변경 통제: Jira 사용 여부 결정(1차와 동일), 아니면 MR 본문에 결과서·U 번호 기록. 단계 태그 `backend-v0.2.0`.
- [ ] `/sw-test` 호출: 8절 우선 대상.

(위 체크 목록은 2차 리뷰 시점 원문이다. M1~M6·P1~P3 수정은 위 조치 상태를 따른다.)


## 리뷰 2026-10-21 (3차)

대상 커밋: feature/runkk-impl 0009d29 (2차 수정 병합 뒤 통합 브랜치, 2차 f20a3f2·수정 12cd646·통합 67a5ffa). 2차 이후 백엔드 변경은 `12cd646..0009d29 -- backend` 10커밋(86파일 +2,860 −316)이다.

입력: S15P31A207-impl/backend (feature/runkk-impl 0009d29), docs/sw/design-oo.md와 design-oo/ 하위 최신판(06-01~06-07, 12, 13-14), 2차 원자료와 위 2차 절.
방법: 읽기 전용. git log/diff/grep, 파일 읽기, 약산 지표 스크립트(매개변수·길이·순환 복잡도·중첩)를 썼다. gradle·docker·시험은 실행하지 않았다(코드 읽기 근거이며 작동 확인은 아님). ai·infra·CI는 같은 원자료로 함께 봤고 [03-ai](03-ai.md#리뷰-2026-10-21-3차)에 따로 적었다.

### 리뷰 기준

| 항목 | 기준 | 출처 |
|------|------|------|
| 매개변수 수 | 4개 이하(5개 이상 중). 생성자 주입·record 구성요소는 세지 않음 | design-oo 13.1 D-73, 2차 기준 |
| 중첩 깊이 | 제어 구조 3단계 이하(else 안의 for·if도 한 단계로 센다) | 기본 규칙 |
| 함수 길이 | 50줄 이하 | 기본 규칙 |
| 순환 복잡도 | 10 이하(약산: 조건·반복·case·catch·&&/||·삼항 + 1) | 기본 규칙 |
| 결합도·응집도·Fan-Out | 스탬프·통신적·7 이하 | design-review.md 0.1 |
| 의존 방향 | 12-의존도 D1~D74, ArchitectureTest 허용 표 | design-oo 12, 13.1 |
| 구조 | goto 등가(라벨 break·fallthrough), 예외로 정상 흐름 제어, 넓은 catch | sw-code-review |
| 객체지향 | 캡슐화, 내용 결합도(남의 상태 직접 조작), 전역 가변 상태 | sw-code-review |
| 형상 관리 | Git, `{타입}: {내용} [{Jira 키}]`, 타입 feat·fix·docs·style·refactor·test·chore | AGENTS.md |

등급: 치명(goto 등가, 내용·공통 결합, 설계에 없는 패키지 양방향 의존) / 중 / 경. 판정: 치명 0·중 3 이하 합격, 중 4~10 조건부, 치명 1+ 또는 중 11+ 불합격. 변경 통제(Jira 키)는 사용자 결정으로 판정 요인에서 뺐다(2차까지는 형상 관리 1항목 아니오를 조건부 요인으로 셌으나 3차부터 기록만 한다).
코딩 규약·형상 관리 도구는 2차와 같다.

### 대상

| 항목 | 내용 |
|------|------|
| 코드 | S15P31A207-impl/backend, 브랜치 feature/runkk-impl 0009d29 |
| 범위 | 2차 수정 12cd646 이후 변경 10커밋(86파일 +2,860 −316). 2차 지적 확인은 해당 줄을 직접 읽었고, 새 결함은 변경분 위주로 찾되 약산 지표는 전체를 다시 쟀다 |
| 설계서 | docs/sw/design-oo.md와 design-oo/ 하위 최신판 |
| 새로 들어온 것 | 보정 배치(reward·crew·battle), 갱신 토큰 폐기 목록(V14), 운영자 로그인 감사(V16), 활성 대결 1개 불변식(V15), 속도 제한(AuthAttemptGuard), `@ConfigurationProperties` 22개 |

### 2차 지적 추적표

#### 중 9건 (9/9 해결)

| ID | 2차 지적 | 근거(파일:줄) | 상태 |
|----|----------|---------------|------|
| M1 | Matchmaker 고스트 경로 tryAssign·release 없음, 첫 실패에 MatchFailed | battle/Matchmaker.java:155 tryAssign, :160 release, :150 ghostAfter + GIVE_UP_MARGIN(15초)일 때만 :151-153 MatchFailed, :120-139 짝 배정 실패 시 두 표 release | 해결 |
| M2 | Battle.judge 같은 결과 재판정이 정정·판정 시각을 덮음 | battle/Battle.java:379-383 같은 결과면 Optional.empty(), :507 corrected = corrected OR isCorrection | 해결 |
| M3 | 참가자 두 명 동시 기록 시 부모 @Version 안 오름 | infra/battle/JpaBattleRepository.java:52-54 OPTIMISTIC_FORCE_INCREMENT | 해결 |
| M4 | 커밋 뒤 수신자 예외·유실, 보정 경로 없음 | reward/RewardService.java:91-131 3회 재시도 후 reward_accrual_failed 로그만, crew/CrewRunService.java:143-160 transactions.afterCommit, crew/WeeklyRaceService.java:72, 보정 reward/RewardReconcileService.java:50, crew/CrewRunLinkReconcileService.java:33, batch/reward·batch/crew Job, V13 | 해결 |
| M5 | 공개 인증 경로 속도 제한 없음 | web/auth/AuthAttemptGuard.java:46-59(접속지 요청 수·접속지 실패·자격 실패 3종), web/auth/AuthController.java:49,83, web/auth/SignUpController.java:51 | 해결 |
| M6 | 갱신 토큰 폐기 목록이 메모리뿐 | infra/integration/auth/JdbcRefreshTokenRevocations.java:17-30 insert on conflict do nothing, V14, JwtTokenIssuer.java:114-145 DataAccessException이면 ProviderUnavailableException(fail-closed) | 해결 |
| P1 | BattleController.history 6개 | web/battle/BattleController.java:138-140 @ModelAttribute HistoryQuery(2개) | 해결 |
| P2 | CrewController.search 5개 | web/crew/CrewController.java:71-73 @ModelAttribute CrewSearchQuery(2개) | 해결 |
| P3 | CrewPostController.list 5개 | web/crew/CrewPostController.java:34-37 @ModelAttribute PostListQuery(3개) | 해결 |

#### 경 32건 (해결 19 / 일부 4 / 미해결 9)

| ID | 2차 지적 | 근거(파일:줄) | 상태 |
|----|----------|---------------|------|
| L1 | @Value 18파일 34곳 | main 전체 @Value 0건, @ConfigurationProperties 22개(AuthSecrets.java:13, AiServerClient.java:85 등), RunkkApplication @ConfigurationPropertiesScan | 해결 |
| L2 | 무결성 위반 전부 409 | web/error/ApiExceptionHandler.java:100-110 SQLSTATE 23505·23503만 409, 나머지 500 + 로그 | 해결 |
| L3 | common에 AttemptLimiter·PageCursor | common/AttemptLimiter.java:15, common/PageCursor.java 그대로(설계 결정 CR2-U3 대기) | 미해결 |
| L4 | AfterCommit 두 사본, *Transactions 5개 사본 | battle/AfterCommit.java, crew/AfterCommit.java(차이는 try/catch), analysis·avatar·battle·crew·runimal *Transactions 5개 그대로 | 미해결 |
| L5 | NotificationService 트랜잭션 안 푸시 | notification/NotificationService.java:69 afterCommit(() -> push(req, tokens)), :78-90 동기화 등록 | 해결 |
| L6 | 재시도 구간 안 알림 중복 | L5 수정으로 롤백된 시도의 푸시는 나가지 않음(NotificationService.java 주석). 호출처 battle/BattleResultService.java:169, LiveBattleService.java:114, crew/CrewRunAnnouncer.java:58은 REQUIRES_NEW 안 | 해결 |
| L7 | sessionLocks remove 경합 | battle/LiveBattleService.java:40-42,179 Object[64] stripe, crew/CrewRunLiveService.java:30,99 | 해결 |
| L8 | random_match_dropped 로그 상수 | battle/Matchmaker.java:138 random_match_failed members={},{} 회원 ID | 해결 |
| L9 | tick 한 표 예외가 전체 중단 | battle/Matchmaker.java:66-80 표마다 try/catch | 해결 |
| L10 | 활성 대결 확인 비원자 | V15 uq_battle_participant_active_member, battle/Battle.java:273-276 moveTo, battle/RecordBattleService.java:161-186, battle/Matchmaker.java:89-101 | 해결 |
| L11 | ASSIGNED 잔류 | infra/battle/JdbcMatchQueue.java:31-33,46 assigned_at 5분 초과 삭제, :109-131 | 해결 |
| L12 | approve 7일 경과 확인 없음 | crew/MembershipService.java:153-155 isDueToExpire이면 REQUEST_DECIDED | 해결 |
| L13 | 크루 검색 전체 읽기·LIKE 미이스케이프 | infra/crew/JpaCrewRepository.java:93-98,108-109 escape 처리는 해결. :118 태그 조건을 메모리에서 거르고 setMaxResults 없음은 그대로 | 일부 |
| L14 | RewardWallet 적립 합계 전체 읽기 | reward/RewardWallet.java:67-70,103-106 그대로. 보정 배치가 기록마다 같은 경로를 부른다(RewardReconcileService.java:90) | 미해결 |
| L15 | 조기 판정 시 BattleEnded 누락 | battle/BattleResultService.java:113,122-126 endedEarly AfterCommit 전송 | 해결 |
| L16 | TierUpdateListener 실패 유실 | running/TierUpdateListener.java:53-67 로그만, 보정 없음 | 미해결 |
| L17 | 크루장 탈퇴 경합 | crew/CrewWithdrawalListener.java:88-97 successorOf 자동 위임 | 해결 |
| L18 | 선언 거리·시간 미대조 | running/ValidityRule.java:48-51 isBackedBy, running/RunRecord.java:199 | 해결 |
| L19 | 프로필 미지정 시 local 개방 | application.yml:194-195 server.address: ${SERVER_ADDRESS:127.0.0.1}(local) | 해결 |
| L20 | photoUrl 스킴 미검사 | web/me/MeController.java:134, web/auth/SignUpController.java:90, web/crew/CrewDtos.java:85 @Pattern(https) | 해결 |
| L21 | 탈퇴 회원 접근 토큰 통과 | web/auth/AuthFilter.java:64 activeMembers.isActive, web/auth/ActiveMembers.java(30초 캐시 + 탈퇴 사건 즉시 무효화) | 해결 |
| L22 | 폴링 본문 크기 무제한 | realtime/LivePollingController.java:50 MAX_MESSAGE_BYTES 초과 400 | 해결 |
| L23 | /admin/** permitAll 한 겹 | web/auth/SecurityConfig.java:88 그대로 | 미해결 |
| L24 | 화이트리스트 전원 삭제 가능 | policy/SettingKey.java:248-254 OPERATOR_WHITELIST 범위 검사 1개 이상 | 해결 |
| L25 | X-Real-IP 신뢰 + 로그인 감사 없음 | 감사: operator/OperatorAuthorizer.java:61-100 성공·실패 기록, V16. 신뢰 프록시: common/ClientAddress.java:31-41 사설망이면 신뢰, 설정화 안 됨 | 일부 |
| L26 | ai 내부 토큰 평문·ai 비교 확인 | ai/runkk_ai/core/security.py:50 hmac.compare_digest 확인됨. AiServerClient http 평문은 그대로(compose 내부 망, application.yml:118) | 일부 |
| L27 | DB·Redis 비밀 기본값 | Redis: application.yml:216,247 password ${REDIS_PASSWORD:} + compose :? 필수. DB: application.yml:204 password ${DB_PASSWORD:runkk}(develop) 그대로 | 일부 |
| L28 | 셀카 매직 넘버만 검사 | web/avatar/AvatarController.java:65 그대로, ai에 픽셀 상한 없음 | 미해결 |
| L29 | 핸드셰이크 한 번만 토큰 검증 | realtime/TokenHandshakeInterceptor.java:40-44 그대로(exp 미보관) | 미해결 |
| L30 | 타인 기록 ID가 alreadyStored | running/RunUploadService.java:81-90 회원 불일치면 IllegalStateException(409) | 해결 |
| L31 | 시험 메소드 @DisplayName | src/test @Test·@ParameterizedTest 680개 중 243개에만 있음(36%) | 미해결 |
| L32 | merge: 커밋 | 범위 내 merge: 12개 + ci: 1개(AGENTS.md 타입 목록 밖), Jira 키 0/156 | 미해결 |

집계: 해결 19(L1,2,5,6,7,8,9,10,11,12,15,17,18,19,20,21,22,24,30), 일부 4(L13,25,26,27), 미해결 9(L3,4,14,16,23,28,29,31,32).

### 1. 설계 일치

설계에 없던 화살표: operator → audit(운영자 로그인 감사), batch.reward → reward, reward → member·running·battle의 메소드 호출형(기존 D13~D15는 사건 수신만), crew → running(SavedRun). 모두 한 방향이고 역방향·순환이 없어 치명이 아니다. 설계서 갱신 요청으로 올렸다(8절). Fan-Out은 crew 7(상한), 그 밖은 6 이하다. 의존 방향은 ArchitectureTest 허용 표로 강제된다.

설계가 정한 동작과 다른 곳은 1건이다(B-M5, 중). 설계 D-76(1)·6.1.12는 운영자 소셜 인증 실패를 `OperatorAuthFailedException` → 401로 정했는데 코드는 `PermissionDeniedException` → 403이고 해당 클래스가 없다. 갱신 요청 R-6에 "코드를 고칠지 D-76을 403으로 개정할지" 결정을 걸었다.

### 2. 코딩 스타일·정확성

#### 2.1 새 결함 (치명 0 / 중 5 / 경 8)

| ID | 파일:줄 | 항목 | 현재 코드 한 줄 | 교정 예시 한 줄 | 등급 |
|----|---------|------|-----------------|-----------------|------|
| B-M1 | reward/RewardService.java:91 | 매개변수 5개 | `int accrueWithRetry(long memberId, AccrualReason reason, String sourceId, ToIntFunction<RewardRule> amountOf, LocalDateTime at)` | `int accrueWithRetry(AccrualSource source, ToIntFunction<RewardRule> amountOf)` + `record AccrualSource(long memberId, AccrualReason reason, String sourceId, LocalDateTime at)` | 중 |
| B-M2 | reward/RewardReconcileService.java:80 | 매개변수 6개 | `private int restore(Map<Long, Boolean> active, long memberId, AccrualReason reason, String sourceId, ToIntFunction<RewardRule> amountOf, LocalDateTime at)` | B-M1의 AccrualSource 재사용: `restore(Map<Long, Boolean> active, AccrualSource source, ToIntFunction<RewardRule> amountOf)` | 중 |
| B-M3 | crew/CrewRunService.java:191 | 매개변수 5개 | `private boolean link(CrewRunPost post, long memberId, String recordId, LocalDateTime startedAt, Distance distance)` | `private boolean link(CrewRunPost post, SavedRun run)` + `SavedRun.of(RunRecordSaved e)` 정적 팩터리로 수신자(:152-158)와 보정(:173-179)이 같은 형식을 넘김 | 중 |
| B-M4 | common/PageCursor.java:39-53 | 중첩 4단계(2차는 3단계로 셌으나 `if (cursor)` → else → for → if로 재측정. 파일은 2차 이전부터 불변) | `if (idOf.applyAsLong(item) < cursor) {` (:52, if (cursor != null) 안 else 안 for 안) | 두 탐색을 `private int indexOf(List<T> l, ToLongFunction<T> idOf)`, `private List<T> olderThanCursor(List<T> l, ToLongFunction<T> idOf)`로 추출해 slice는 2단계 | 중 |
| B-M5 | operator/OperatorAuthorizer.java:75 | 설계 일치: D-76(1)·6.1.12는 소셜 인증 실패를 OperatorAuthFailedException → 401로 정함. 코드는 PermissionDeniedException → 403이고 OperatorAuthFailedException 클래스가 없다(시험 AdminControllerTest:176-178, OperatorTest:75-77도 403 기대). 2차 이전부터 있던 차이이나 이번 대조에서 확인 | `throw new PermissionDeniedException("운영자 인증 실패");` | `throw new OperatorAuthFailedException("운영자 인증 실패");` + AdminAuthController.login에서 이 예외를 401로 변환(또는 D-76을 403으로 개정, 8절 R-6) | 중 |
| B-L1 | V15__battle_active_slot.sql:13-22, battle/Battle.java:273-276 | 마이그레이션 안전: 기존 데이터에서 한 회원이 활성 대결을 둘 이상 가졌으면 최신 것만 칸을 채운다. 나머지 활성 대결은 칸이 NULL인 채 남고, 그 대결이 IN_PROGRESS → COUNTDOWN처럼 활성 → 활성으로 넘어가면 moveTo가 칸을 채워 최신 대결과 고유 제약 충돌이 난다. 조건: 이전 데이터에 중복 활성이 있을 때만 | `SELECT DISTINCT ON (bp.member_id) bp.id ... ORDER BY bp.member_id, b.id DESC` | 같은 마이그레이션에 `UPDATE battle SET status = 'CANCELLED' WHERE status IN ('WAITING_START','IN_PROGRESS','COUNTDOWN') AND id NOT IN (SELECT battle_id FROM battle_participant WHERE active_member_id IS NOT NULL);` 추가 | 경 |
| B-L2 | reward/RewardReconcileService.java:93 | 보정이 적립량 0인 기록을 매일 다시 시도: RewardWallet.accrue(RewardWallet.java:64-75)는 0 이하면 기록하지 않아 1km 미만 유효 러닝은 2일 창 동안 매일 지갑 행 잠금 + 전체 적립 내역 읽기를 반복 | `int amount = rewardService.accrueWithRetry(memberId, reason, sourceId, amountOf, at);` | 호출 전 `if (amountOf.applyAsInt(operationPolicy.rewardRule()) <= 0) return 0;` (reward → policy는 D16 안) | 경 |
| B-L3 | battle/RecordBattleService.java:177 | 무결성 위반 전부를 ALREADY_IN_BATTLE로 바꿈(2차 L2와 같은 위험). warn 로그에 원인은 남지만 NOT NULL·길이 위반도 409로 응답 | `} catch (DataIntegrityViolationException \| OptimisticLockingFailureException e) {` | 낙관적 잠금만 잡고, 무결성은 제약 이름이 uq_battle_participant_active_member일 때만 변환하고 아니면 `throw e` | 경 |
| B-L4 | battle/RecordBattleService.java:170 | Tell, Don't Ask: 서비스가 엔티티 상태를 묻고 분기한 뒤 취소(isAwaitingStartReport를 private에서 package-private으로 풀어 줌) | `if (!active.isAwaitingStartReport()) {` | `if (!active.cancelIfUnstarted(now)) { throw new BusinessRuleException(ReasonCode.ALREADY_IN_BATTLE); }` (Battle이 상태 검사·취소를 함께 소유) | 경 |
| B-L5 | infra/integration/auth/JwtTokenIssuer.java:57,149 | 시간대 상수 중복(Clock 빈이 이미 서울) | `private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");` | `revocations.purgeExpired(now.atZone(clock.getZone()).toInstant())` | 경 |
| B-L6 | web/auth/AuthController.java:71,90 | 폐기 목록 저장소 장애가 "로그인 제공자 연결 실패"(ReasonCode.java:29)로 나간다. 원인은 소셜 제공자가 아니라 서버 DB | `throw new BusinessRuleException(ReasonCode.PROVIDER_UNAVAILABLE);` | `throw new BusinessRuleException(ReasonCode.TEMPORARILY_UNAVAILABLE);` (문구 "잠시 후 다시 시도해 주세요", 앱 계약 목록에 추가) | 경 |
| B-L7 | member/LegalDocumentCatalog.java:19-28 | @Value → record 전환으로 필수 설정 누락이 기동 실패가 아니라 null URL 응답이 됨(yml 기본값이 있어 현재는 안전) | `urls.put(DocumentType.TERMS, termsUrl);` | `urls.put(DocumentType.TERMS, Objects.requireNonNull(termsUrl, "runkk.legal.terms-url"));` | 경 |
| B-L8 | battle/Matchmaker.java:93 | 매 tick(1초)마다 대기자 수만큼 활성 대결 조회(대기자 N명이면 초당 N쿼리) | `if (!battleRepository.existsActive(memberId)) {` | `Set<Long> busy = battleRepository.activeMemberIds(ids);` 한 번에 조회 후 `busy.contains(memberId)` | 경 |

#### 2.2 확인했으나 결함 아님(통과)

- 활성 대결 1개 불변식은 `battle_participant.active_member_id` 고유 인덱스(V15:24)와 `Battle.moveTo`(Battle.java:273-276)가 모든 상태 전이를 지난다(`status =` 직접 대입은 생성자 :152 하나뿐, 팩터리 :172·189에서 칸을 맞춤).
- 새 기록 대결이 이전 시작 전 대결을 대신할 때 saveImmediately(JpaBattleRepository.java:59-63)로 칸을 먼저 비운 뒤 새 행을 넣어 Hibernate의 삽입 우선 순서에 걸리지 않는다.
- CANCELLED는 결과·시작 보고·판정·이력에서 걸러진다(BattleResultService.java:96-99,284, RecordBattleService.java:202, Battle.java:373-375).
- 리워드 보정은 지갑 행 잠금 + accruedFor + 고유 제약으로 멱등이고 하루 상한은 saved_at·판정 시각 기준이라 수신자와 같다. 크루런 보정은 isUnlinked 확인 뒤 같은 link를 쓰고 참여당 러닝 1건이라 멱등이다.
- 폐기 목록은 PK 한 번 INSERT로 원자적이고(JdbcRefreshTokenRevocations.java:17-30) 만료 행은 서명 검사가 이미 거부한다. 장애 시 갱신은 토큰을 쓰지 않고 503이라 앱이 같은 토큰으로 재시도할 수 있다.
- 운영자 로그인 감사: 실패 감사 저장 오류가 원래 거부 사유를 가리지 않고(OperatorAuthorizer.java:90-107), 성공은 감사가 안 남으면 토큰을 주지 않는다. 429로 막힌 요청은 운영 로그만.
- 마이그레이션 V13~V16: 제약 이름(ck_battle_status, ck_audit_record_action)이 V6·V1과 일치하고 action 칼럼 길이 30 안에 새 값 22자가 들어간다. V13은 saved_at을 ended_at으로 채운 뒤 NOT NULL로 올려 순서가 안전하다. V14 엔티티(RefreshTokenRevocationRow)는 ddl validate와 타입이 맞는다.
- 비밀값: AuthSecrets·AiServerClient.Server·KmaWeatherGateway.Kma는 toString을 가렸고(def4fbc), 저장소의 .env 파일은 예시 두 개와 빈 값뿐, 새 로그에 토큰·비밀 출력 없음.

### 3. 구조적 프로그래밍

goto 등가, System.out, 넓은 catch(Exception)는 새 코드 0건이다. catch (RuntimeException)은 스케줄러·수신자·감사 같은 경계에서만 로그와 함께 쓴다. 예외 흐름 제어는 RecordBattleService.create의 무결성 예외 변환뿐이고 DB 제약이 판정자라 허용했으며 범위만 B-L3로 지적했다.

순환 복잡도 10 초과 함수:

| 단위 | 목록 |
|------|------|
| 백엔드 | 없음(0건). 2차의 4개는 모두 해소: running.RunRecord.receive 12 → 2(검증 helper 추출, RunRecord.java:157-167), running.PersonalRecordBook.apply 13 → 4(:77), infra.integration.auth.JwtOperatorTokenIssuer.verify 11 → 5(:90), running.RunTrack.curve 11 → 5(:145) |
| 경계 10(위반 아님) | infra.integration.auth.JwtTokenIssuer.parse(:185), infra.analysis.RankingCache.page(:53) |
| 9 | common.PageCursor.slice(:37, 복잡도는 9이나 중첩이 4라 B-M4), crew.WeeklyRaceService.reconcileMember(:188), infra.integration.weather.KmaWeatherGateway.parse(:99), infra.integration.auth.KakaoLoginGateway.authenticate(:42) |
| ai | 없음(0건). 경계 10: face.router.create(face/router.py:54, 2차 6에서 10으로 늘었으나 위반 아님. 가지를 더하기 전에 본문 읽기·검증을 helper로 추출 권장) |

함수 50줄 초과: 백엔드 메소드 0건(스크립트가 record 본문으로 잡은 LiveBattleSession.Runner 55줄, CrewDtos.PostListResponse 77줄은 메소드 단위 50줄 이하), ai 0건.

### 4. 객체지향 구현

| 점검 | 결과 |
|------|------|
| 캡슐화·내용 결합도 | 엔티티 필드 private, 활성 칸은 Battle이 소유(moveTo → BattleParticipant.syncActive는 패키지 내부). 서비스가 상태를 묻는 곳은 B-L4 |
| 전역 가변 상태 | AttemptLimiter를 가진 빈 3곳(AuthAttemptGuard, AdminAuthController, MembershipService)과 ActiveMembers 캐시는 인스턴스 1개 전제(5.4)의 메모리 상태. 결함은 아니고 설계 결정 L3와 묶음 |
| 동시성·트랜잭션·멱등 | 새 코드에서 결함이 나오지 않았다(2.2절) |

### 5. 형상 관리

| 활동 | 판정 | 근거 |
|------|------|------|
| 형상 식별 | 예 | 패키지·클래스가 설계서와 1:1, ArchUnit이 패키지를 강제. batch.reward가 54번째 패키지로 늘었다(갱신 요청 R-2) |
| 버전 제어 | 예 | Git 이력. 태그 없음 |
| 변경 통제 | 아니오 | Jira 키 0/156 커밋, 브랜치 feature/runkk-impl(사용자 결정, 2차와 같아 판정 요인으로만 기록). 타입 목록 밖 merge:·ci:(L32) |
| 형상 감사 | 예 | `git diff 12cd646..0009d29 -- backend`로 2차 이후 변경 86파일을 확인 |
| 상태 보고 | 예 | 커밋 제목 유형 접두어 + 한국어 요약. CHANGELOG 없음 |

### 6. 집계와 판정

| 구분 | 치명 | 중 | 경 | 비고 |
|------|------|----|----|------|
| 새 결함 | 0 | 5 (B-M1~B-M5) | 8 (B-L1~B-L8) | |
| 2차 잔여 | 0 | 0 | 13 (미해결 9 + 일부 4) | 중 9건은 전부 해결 |
| 합계 | 0 | 5 | 21 | |

판정: **조건부**
근거: 치명 0, 중 5(4 이상 10 이하). 2차 중 9건(M1~M6, P1~P3)은 모두 해결됐다. 새 중 5건은 매개변수 묶기 3건(B-M1·M2는 AccrualSource 하나로 동시 해소, B-M3), 중첩 하나(B-M4), 설계 결정 불일치 하나(B-M5)로 모두 작은 수정이다. 고치면 중 0으로 합격 수준이다. 동시성·트랜잭션 경계·멱등은 새 코드에서 결함이 나오지 않았다.

수정 우선순위: B-M5(코드·설계 중 어느 쪽을 바꿀지 결정 먼저) → B-M1~M3 → B-M4 → B-L1(V15는 이미 DB에 적용됐으면 V17로 따로) → 나머지 경. MR 전체 판정은 [code-review.md](../code-review.md)의 3차 종합 절을 따른다.

조치 상태(2026-10-21): 새 결함 중 5건·경 8건은 수정 후 통합 브랜치 d562d26에 반영됐고 [4차 절](#리뷰-2026-10-21-4차)에서 모두 해결로 확인했다.

### 7. sw-test 전달: 우선 테스트 대상

복잡도 10 초과 함수가 없으므로 이번 수정과 새 코드의 회귀 시험을 우선한다.

| 함수·경로 | 약산 복잡도 | 이유 |
|-----------|-------------|------|
| reward.RewardReconcileService (보정 전체) | — | 멱등(같은 기록 두 번 보정), 적립량 0인 기록 건너뜀(B-L2), 하루 상한 경계 |
| reward.RewardService.accrueWithRetry | — | 3회 재시도 뒤 실패 로그, AccrualSource 도입 뒤 회귀(B-M1) |
| battle.RecordBattleService.create | — | 활성 대결 중복(고유 제약 위반 변환), 낙관적 잠금 충돌, 그 밖 무결성 위반은 그대로 던짐(B-L3) |
| V15 마이그레이션 | — | 중복 활성 대결이 있던 데이터에서 칸이 빈 활성 대결(B-L1). 실제 PostgreSQL |
| common.PageCursor.slice | 9 | 커서 항목이 지워진 경우, 크기 경계, 중첩 정리(B-M4) 뒤 회귀 |
| JwtTokenIssuer.parse / RefreshTokenRevocations | 10 | 폐기 목록 장애 시 503(fail-closed), 만료 행 정리, 같은 토큰 재시도 |
| web.auth.AuthAttemptGuard | — | 접속지 요청 수·실패·자격 실패 3종 제한 경계, 429 |
| operator.OperatorAuthorizer | — | 소셜 인증 실패 401(B-M5), 화이트리스트 밖 403, 로그인 감사 성공·실패 |
| crew.CrewRunService.link / CrewRunLinkReconcileService | — | 수신자와 보정이 같은 link를 쓰는지, 참여당 러닝 1건 멱등(B-M3) |

### 8. 설계서 갱신 요청 (코드가 옳거나 설계 공백, `/sw-design-oo` 재호출 목록)

코드 결함으로 세지 않았다. 조각 파일에서는 3차 원래 ID(R-1~R-11)를 쓰고, 종합 결과서에서는 2차 CR2-* 이후 번호(CR34-01~CR34-11)로 모은다. 구현자가 먼저 보고한 항목과 겹치는 것은 비고에 표시해 한 줄로 묶었다.

| ID | 대상 절 | 내용 | 비고 |
|----|---------|------|------|
| R-1 | 2.4.2 상태도, 6.1.6 Battle·BattleStatus·BattleRepository·BattleStartService | CANCELLED 상태, ABANDON_AFTER 10분(SRS에 값 없음), cancelUnstarted·isAbandoned, battle_participant.active_member_id 활성 대결 1개 불변식, findActive·findAbandoned·saveImmediately·findMemberWinsJudgedBetween, BattleWin, BattleStartService.cancelAbandoned | 기보고(CANCELLED·결정) |
| R-2 | 5.1.1, 6.1.13 batch | batch.reward 패키지(54번째), RewardReconcileJob·CrewRunLinkReconcileJob·AbandonedBattleJob·RevocationPurgeJob, 0시 10분 보정 cron | 기보고(batch.reward) |
| R-3 | 6.1.8 reward, 6.1.3 running, 6.1.7 crew | RewardReconcileService, CrewRunLinkReconcileService, RunQueryService.savedBetween·SavedRun, RunRecord.savedAt(V13), 수신자는 로그만 남기고 보정이 채운다는 M4 결정 | 기보고(결정) |
| R-4 | 12-의존도 | operator → audit(운영자 로그인 감사), batch.reward → reward, reward → member·running·battle의 메소드 호출(기존 D13~D15는 사건 수신만), crew → running(SavedRun) | 기보고(의존 3개) |
| R-5 | 6.1.2 TokenIssuer, 5.5 | RefreshTokenRevocations(PostgreSQL, V14), fail-closed, purgeExpiredRevocations, AuthService.purgeExpiredRevocations. 5.5 "Redis 장애는 매칭·실시간만"과 같은 원칙 | 기보고(결정) |
| R-6 | 6.1.12 OperatorAuthorizer, 6.1.11 AuditAction, D-76 | authenticate(provider, providerToken, client) 세 번째 인수, OPERATOR_LOGIN_SUCCESS·FAILURE(V16). 단 소셜 인증 실패 401·OperatorAuthFailedException은 코드가 따르지 않음(B-M5): 코드를 고칠지 D-76을 403으로 개정할지 결정 필요 | 기보고(결정) + B-M5 결정 |
| R-7 | 13.1 코딩 규약 | @Value 대신 기능별 @ConfigurationProperties record 22개, 비밀 포함 record는 toString 가림 | 기보고(결정) |
| R-8 | 6.1.3 ValidityRule, srs 유효 기록 4조건 | 다섯째 조건 isBackedBy(선언 거리 ≤ 경로 × 2, 선언 시간 ≤ 종료 - 시작 + 1초) | CR2-U6 |
| R-9 | 6.1.13 web.auth, 5.7 | AuthAttemptGuard(접속지 요청 수·실패·자격 실패 제한), ActiveMembers(탈퇴 회원 접근 토큰 거부) | 신규 클래스 |
| R-10 | 5.4 배포 | 2차 U16~U18 아직 미반영(05-02:94 "환경 변수 파일 한 곳"). 추가: redis --requirepass(REDIS_PASSWORD), DB 필수 값 :?, postgres·redis 포트 고정, nginx resolver 재해석과 /actuator/ 차단, deploy.sh의 api·ai 헬스 대기, 롤백에 ai 이미지 | 2차 U16~U18 + 신규 |
| R-11 | 5.1.1 common, D-48 | AttemptLimiter·PageCursor를 common 허용 목록에 넣을지 결정 | CR2-U3 잔여 |

### 9. 다음 행동

- [x] B-M5: 운영자 소셜 인증 실패를 401로 고쳐 D-76과 맞췄다(4차에서 확인, 갱신 요청 R-6은 세 번째 인수와 감사 동작만 남음)
- [x] B-M1~B-M4, 경 새 결함 B-L1~B-L8: 4차에서 해결 확인
- [ ] 2차 잔여 경 중 설계 결정이 필요한 것(L3·L4·L14·L16·L23·L25·L26·L29)은 결정 대기로 분리(4차 절)
- [ ] 설계서 갱신: `/sw-design-oo`로 R-1~R-11 반영
- [x] 수정 뒤 `/sw-code-review` 4차: 아래 절

(위 체크 목록은 3차 리뷰 시점 원문을 4차 확인 결과로 갱신한 것이다.)


## 리뷰 2026-10-21 (4차)

대상 커밋: feature/runkk-impl d562d26 (3차 0009d29, 3차 수정 병합 뒤 통합 브랜치). 범위는 `git diff 0009d29..d562d26` 164파일 +1,966 −804, 커밋 32개(백엔드·ai·infra 18, 안드로이드 11, 계약 동기화 1, 병합 2)다.

입력: S15P31A207-impl `feature/runkk-impl` d562d26, docs/sw/design-oo.md와 design-oo/ 하위, 3차 원자료와 위 3차 절.
방법: 읽기 전용. git log/diff/grep과 파일 읽기, 줄 수·매개변수 약산 스크립트만 썼다. 리뷰 자체는 코드 읽기 기준이다. 같은 통합 브랜치에서 따로 돌린 실행 검증(backend 882 tests 통과 등)은 [code-review.md](../code-review.md)의 4차 종합 절에 적었다.

### 리뷰 기준

| 항목 | 기준 | 출처 |
|------|------|------|
| 설계 일치 | 설계와 다르면 불일치(코드가 낫다면 "설계서 갱신 요청"), 의존은 12-의존도 D1~D74·ArchitectureTest 허용 표 | design-oo 12, 13.1·13.2 |
| 매개변수 | 4개 이하(생성자 주입·record 구성요소는 제외, Composable은 modifier·콜백 제외) | D-73, 기본 규칙 |
| 중첩 / 함수 길이 | 제어 구조 3단계 이하 / 50줄 이하 | 기본 규칙 |
| 순환 복잡도 | 10 이하(약산). 3차와 같이 10 초과 안드로이드 함수는 참고로만 적고 집계에서 뺀다 | 3차 5절 |
| 구조 | goto 등가, 예외로 정상 흐름 제어, 넓은 catch, 반복 안 return | sw-code-review |
| 객체지향 | 캡슐화, 내용 결합도, 전역 가변 상태, Tell Dont Ask | sw-code-review |
| 이름 | Boolean은 is·has·can(안드로이드), 화면 문구 하드코딩 금지, Dispatchers 주입, 로그는 Timber | AGENTS.md, 팀 컨벤션 |
| 형상 관리 | 제목 형식 타입-내용-Jira 키, 타입 feat·fix·docs·style·refactor·test·chore, 커밋은 한 가지 일 | AGENTS.md |
| 등급 | 치명(goto 등가·내용/공통 결합·전역 가변 상태·설계에 없는 양방향 의존) / 중(데이터 손실·설계 불일치·규칙의 광범위 위반) / 경 | 요청 |
| 판정 | 치명 0·중 3 이하 합격 / 중 4~10 조건부 / 치명 1+ 또는 중 11+ 불합격. 종합은 단위 합계 | 요청 |

### 대상

| 항목 | 내용 |
|------|------|
| 코드 | S15P31A207-impl/backend, 브랜치 feature/runkk-impl d562d26 |
| 범위 | 3차 이후 변경(백엔드·ai·infra 커밋 18개). 새 코드 위주로 읽고 3차 지적 위치를 직접 확인했다 |
| 새로 들어온 것 | V17 마이그레이션(V15 backfill 잔여 정리), 크루 검색 SQL(키워드·태그 AND·cursor), 운영자 소셜 인증 401, SQLSTATE 23505 판정, AccrualSource·SavedRun 값 객체, ReasonCode TEMPORARILY_UNAVAILABLE(503) |

### 3차 지적 추적표

#### 3차 새 결함 13건 (중 5 + 경 8): 해결 13 / 일부 0 / 미해결 0

| ID | 3차 지적 | 근거(파일:줄) | 상태 |
|----|----------|---------------|------|
| B-M1 | accrueWithRetry 매개변수 5 | reward/RewardService.java:90 `accrueWithRetry(AccrualSource source, ToIntFunction<RewardRule> amountOf)`, reward/AccrualSource.java:21 record | 해결 |
| B-M2 | restore 매개변수 6 | reward/RewardReconcileService.java:94 `restore(Map active, AccrualSource source, ToIntFunction amountOf)` (3개) | 해결 |
| B-M3 | link 매개변수 5 | crew/CrewRunService.java:175 `link(CrewRunPost post, SavedRun run)`, running/SavedRun.java `of(RunRecordSaved)`, 호출 :151·:165 | 해결 |
| B-M4 | PageCursor.slice 중첩 4 | common/PageCursor.java:38 slice(2단계 이하), :47 afterCursor, :56 indexOfCursor, :66 olderThanCursor(각 for+if 2단계) | 해결 |
| B-M5 | 운영자 소셜 인증 실패가 403, D-76은 401 | operator/OperatorAuthorizer.java:75 `throw new OperatorAuthFailedException(...)`, operator/OperatorAuthFailedException.java, web/admin/AdminAuthController.java:71-74 401(AUTH_FAILED) 변환·실패 횟수 기록, 설계 D-76·6.1.12와 일치 | 해결 |
| B-L1 | V15 backfill이 남기는 칸 빈 활성 대결 | db/migration/V17__battle_active_slot_cleanup.sql:12-21 칸이 빈 활성 대결을 CANCELLED, :23-31 비활성 대결의 칸을 비움. 4.4절 점검 | 해결 |
| B-L2 | 보정이 적립량 0 근거를 매일 재시도 | reward/RewardReconcileService.java:63 `run.valid() && current.amountForRun(...) > 0`, :76 `current.battleWin() > 0`일 때만 승리 조회 | 해결 |
| B-L3 | 무결성 위반 전부 ALREADY_IN_BATTLE | battle/RecordBattleService.java:181-187 낙관적 잠금과 SQLSTATE 23505만 변환, 그 밖은 `throw e`, :203 isUniqueViolation. 시험 battle/AbandonedBattleTest.java:184-199(23505 변환, 23502 재던짐) | 해결 |
| B-L4 | Tell Dont Ask | battle/Battle.java:540 `cancelIfAwaitingStartReport`, :229 isAwaitingStartReport private, RecordBattleService.java:174 | 해결 |
| B-L5 | 시간대 상수 중복 | infra/integration/auth/JwtTokenIssuer.java:146 `now.atZone(clock.getZone())` | 해결 |
| B-L6 | 폐기 목록 장애를 PROVIDER_UNAVAILABLE로 응답 | common/ReasonCode.java TEMPORARILY_UNAVAILABLE, web/error/ApiExceptionHandler.java:48 503 매핑, web/auth/AuthController.java:72·:93, 계약 동기화(backend/src/test/resources/contract/app/enums/ReasonCode.txt, android/contract/enums/ReasonCode.txt, RunkkErrorText.kt:38) | 해결 |
| B-L7 | 법정 문서 주소 누락이 null 응답 | member/LegalDocumentCatalog.java:25-28·`required` (비면 IllegalStateException, 키 이름만 노출) | 해결 |
| B-L8 | tick마다 대기자 수만큼 활성 대결 조회 | battle/Matchmaker.java:92 `battleRepository.activeMemberIds(...)` 한 번, infra/battle/JpaBattleRepository.java:114, 시험 BattleServicesTest의 activeLookups 1회 | 해결 |

#### 2차 잔여 경 13건

| ID | 지적 | 근거 | 상태 |
|----|------|------|------|
| L3 | common에 AttemptLimiter·PageCursor | common/AttemptLimiter.java, common/PageCursor.java 그대로(crew/CrewRepository.java도 PageCursor를 import) | 결정 대기 |
| L4 | AfterCommit 두 사본, *Transactions 5개 사본 | battle/AfterCommit.java, crew/AfterCommit.java 그대로 | 결정 대기 |
| L13 | 크루 검색 전체 읽기 | 검색은 해결: infra/crew/JpaCrewRepository.java:109-138 키워드·태그 AND(`concat(쉼표, c.tags, 쉼표) like :tagN escape 느낌표`)·cursor(`c.id < :cursor`)·`setMaxResults(size + 1)`를 SQL로. 추천 경로는 그대로: crew/CrewQueryService.java:102 `crewRepository.search(CrewSearch.all(), PageCursor.all(), thisWeek())`가 공개 크루 전체와 주간 거리를 읽는다 | 일부(경) |
| L14 | RewardWallet 적립 합계 전체 읽기 | reward/RewardWallet.java:67-70·103-106 그대로 | 결정 대기 |
| L16 | TierUpdateListener 실패 유실 | running/TierUpdateListener.java:53-67 그대로 | 결정 대기 |
| L23 | /admin/** permitAll 한 겹 | web/auth/SecurityConfig.java:88 그대로. 인터셉터 경로 점검은 18c74a3(web/admin/AdminControllerTest.java)이 등록된 모든 관리 경로를 403으로 시험해 보강 | 일부, 결정 대기 |
| L25 | X-Real-IP 신뢰 | 감사는 해결(3차). common/ClientAddress.java:31-41 사설망·루프백이면 신뢰, 설정화 안 됨 | 일부, 결정 대기 |
| L26 | ai 내부 토큰 평문 http | AiServerClient http 그대로 | 일부, 결정 대기 |
| L27 | DB 비밀번호 기본값 | application.yml:205 `password: ${DB_PASSWORD}`(develop), :237 prod는 원래 필수, backend/compose.yaml:3-4 안내, infra/docker-compose.yml:37 필수(:?), 시험 컨테이너는 develop 프로필을 쓰지 않음 | 해결 |
| L28 | 셀카 매직 넘버만 검사 | ai/runkk_ai/face/router.py:88-98 `_rejection`(머리 정보 가로×세로 4096² 초과 422 TOO_LARGE, 깨진 머리 422 UNSUPPORTED_IMAGE), face/schemas.py:31-76 파서, core/config.py max_image_pixels, 시험 ai/tests/face/test_image_dimensions.py | 해결 |
| L29 | 핸드셰이크 한 번만 토큰 검증 | realtime/TokenHandshakeInterceptor.java:40-44 그대로 | 결정 대기 |
| L31 | 시험 메소드 @DisplayName | src/test @DisplayName 316 / @Test·@ParameterizedTest 667(3차와 같은 방법이 아닐 수 있어 비율만 참고, 47%) | 미해결(경) |
| L32 | 커밋 규약 | 범위 32커밋 중 Jira 키 0개, 병합 2개는 git 기본 문구 Merge branch(타입 목록 밖), 18c74a3은 시험만 바꾸는데 fix 타입 | 미해결(경) |

집계: 3차 새 결함 13건(중 5, 경 8)은 모두 해결. 2차 잔여 경 13건은 해결 2(L27·L28), 결정 대기 8(L3·L4·L14·L16·L23·L25·L26·L29), 일부 L13(검색은 해결, 추천 경로는 그대로), 미해결 L31·L32다. L23·L25·L26은 일부 해소 뒤 결정이 남아 결정 대기로 센다.

### 1. 설계 일치

새 화살표는 없다. crew가 common.PageCursor를 인터페이스에서도 import하는 것은 이미 있던 화살표의 확대이고, reward에서 policy(D16 안), infra.crew에서 common, web.admin에서 operator 예외(D52)는 모두 설계 안이다. 설계가 정한 동작을 어긴 곳은 없다(B-M5가 해결돼 운영자 소셜 인증 실패 401이 D-76·6.1.12와 맞는다). 설계서 갱신 요청은 8절에 있다.

### 2. 코딩 스타일·정확성

새 결함은 치명 0, 중 0, 경 1이다.


| ID | 파일:줄 | 항목 | 현재 코드 한 줄 | 교정 예시 한 줄 | 등급 |
|----|---------|------|-----------------|-----------------|------|
| B4-1 | running/HeartRateSeries.java:51 (3차에서 서버 쪽을 못 본 사항을 확인. 이번 diff 밖 기존 코드) | 늦은 심박 덧붙이기가 멱등이 아니다. 앱은 POST /runs가 이미 저장됐다는 응답(AlreadySaved)이면 보관 심박 전부를 다시 덧붙이고(domain/sync/UploadPendingRunsUseCase.kt:40-45), 항목 전송도 지우기 전에 끊기면 같은 심박을 다시 보낸다(:66-67). 서버는 같은 측정 시각을 걸러 내지 않아 목록이 중복된다. 응답이 유실된 재전송에서 전체 심박 열이 두 번 쌓이고 평균은 거의 같지만 심박 그래프·표본 수·5구간 계산 입력이 중복된다. 데이터 손실은 아니라 경 | `merged.addAll(s);` | 측정 시각을 키로 한 TreeMap에 기존 표본과 새 표본을 putIfAbsent로 넣고 값 목록으로 samples를 만든다(앱 merge의 distinctBy at과 같은 규칙) | 경 |

점검했으나 결함 아님(통과):

| 점검 요청 | 결과 |
|-----------|------|
| V17 CANCELLED 범위 | 조건은 활성 3종이면서 참가자 중 active_member_id가 NULL인 대결. battle_participant.member_id는 NOT NULL(V6)이라 상대가 기록·유명인인 대결(상대는 battle 칼럼)도 칸이 채워져 오취소가 없다. 활성 대결의 칸은 Battle.moveTo가 항상 채우므로 정상 대결은 건드리지 않는다. 일부 참가자만 칸이 빈 대결(상대의 최신 대결이 따로 있는 경우)은 같이 취소되고 두 번째 UPDATE가 남은 참가자의 칸을 비운다(파일 주석에 명시, 중복 활성이 있던 데이터에서만 발생) |
| V17 version·closed_at | version을 1 올려 낙관적 잠금으로 실행 중인 메모리 엔티티와 충돌시킨다. closed_at은 COALESCE로 기존 값을 보존하고 LOCALTIMESTAMP는 DB 시간대가 서울(infra/docker-compose.yml:96-97 TZ·PGTZ)이라 서비스 시각과 같다. CANCELLED는 판정·리워드·이력에서 걸러진다(3차 확인) |
| V17 재실행 안전 | 첫 UPDATE는 칸이 빈 활성 대결만 고르고 취소하면 조건에서 빠지므로 다시 돌려도 0건. 둘째 UPDATE도 같다. Flyway는 한 번만 돌리고 V15는 고치지 않았다. 신규 대결은 생성 때 칸을 채우는 팩터리라 새 행이 걸리지 않는다. 실제 PostgreSQL 통합 시험 BattleActiveSlotMigrationIntegrationTest 신규(158줄). 리뷰는 읽기만 했고, 통합 d562d26 검증에서 PostgreSQL Testcontainers로 실행해 2건 통과 |
| SQLSTATE 23505 판정 | RecordBattleService.java:203-210 원인 사슬에서 SQLState가 있는 첫 SQLException을 비교한다. Hibernate JDBCException은 SQLException이 아니라 건너뛰고 PSQLException·H2 예외에 닿는다. SQLState가 null이면 다음 원인으로 넘어간다. 새 대결 행이 닿는 고유 제약은 활성 칸뿐(참가자 유일 제약은 새 battle_id라 충돌 불가)이라 이름 대신 SQLSTATE로 보는 근거가 맞다 |
| 크루 검색 SQL | LIKE: 키워드와 태그 모두 escapeLike(느낌표·퍼센트·밑줄), escape 절을 쓴다. 태그 칸이 정렬된 쉼표 구분 이름(crew/CrewTags.java:14-18)이라 앞뒤에 쉼표를 붙여 찾으면 한 태그가 다른 태그의 접두어여도 부분 일치가 없다. 정렬: SQL order by c.id desc, c.id 가 cursor보다 작음, setMaxResults(size+1)한 뒤 page.slice가 같은 id 기준으로 자른다. 목록에 cursor 항목이 없으면 olderThanCursor가 전부 통과시켜 일관된다. PageCursor 생성자가 size 1~100·cursor 양수를 막아 size+1 오버플로가 없다. 질의 문자열은 상수 조각과 정수 인덱스만 이어 붙이고 값은 전부 바인드(주입 없음). search 메소드는 48줄(한계 근접, 질의 조립과 행 변환을 둘로 나누기를 다음 변경 때 권장) |
| 운영자 401 변환 | AdminAuthController.java:71-74가 OperatorAuthFailedException만 401(AUTH_FAILED)로 바꾸고 실패 횟수를 센다. 화이트리스트 밖(PermissionDeniedException)은 403 유지, 제공자 무응답은 세지 않음. 실패 감사는 OperatorAuthorizer가 예외 전에 남긴다(3차 확인). web.admin이 operator 예외를 import하는 것은 D52(web.admin에서 operator로) 안 |

### 3. 구조적 프로그래밍

goto 등가·넓은 catch·반복 안 return은 새 코드에서 찾지 못했다. PageCursor.slice 중첩은 2단계 이하로 줄었다(B-M4 해결). 크루 검색 `search` 메소드(JpaCrewRepository)는 48줄로 한계에 가깝다. 질의 조립과 행 변환을 둘로 나누기를 다음 변경 때 권장한다(결함 아님).

### 4. 객체지향 구현

Tell, Don't Ask는 `Battle.cancelIfAwaitingStartReport`로 해결됐다(B-L4). 새 결함은 없다. 의존·형상 점검:

| 점검 요청 | 결과 |
|-----------|------|
| 의존 방향 | 새 화살표 없음. crew가 common.PageCursor를 인터페이스에서도 import(이미 있던 화살표의 확대), reward에서 policy(D16 안), infra.crew에서 common, web.admin에서 operator 예외(D52). 안드로이드는 domain에 android·data import 0, 모듈 의존 변경 0 |

### 5. 형상 관리

| 활동 | 판정 | 근거 |
|------|------|------|
| 변경 통제 | 아니오 | 범위 32커밋 중 Jira 키 0개. 병합 2개는 git 기본 문구 Merge branch(타입 목록 밖), 18c74a3은 시험만 바꾸는데 fix 타입(L32). 사용자 결정으로 판정 요인에서 제외 |

나머지 4개 활동은 활동별 표를 따로 만들지 않았다. 범위 32커밋은 fix 14·refactor 13·test 1·chore 1·병합 2이고 병합과 18c74a3을 뺀 나머지는 한 가지 일씩이다.

### 6. 집계와 판정

| 구분 | 치명 | 중 | 경 | 구성 |
|------|------|----|----|------|
| 새 결함 | 0 | 0 | 1 | B4-1 |
| 2차 잔여 | 0 | 0 | 11 | 결정 대기 8(L3·L4·L14·L16·L23·L25·L26·L29) + L13(추천 경로 전체 읽기) + L31 + L32 |
| 합계 | 0 | 0 | 12 | 3차 중 5·경 8은 전부 해결 |

판정: **합격**
근거: 치명 0, 중 0. 3차 중 5건과 경 8건이 모두 해결됐고 새 코드(V17, 23505 판정, 크루 검색 SQL, 운영자 401)에서 결함이 나오지 않았다. 경 12 중 8은 설계 결정 대기다. 변경 통제(Jira 키)는 사용자 결정으로 판정 요인에서 뺐다.

조치 상태(2026-10-21): B4-1은 6af16a9에서 해결(같은 측정 시각은 한 번만, 다른 값이면 먼저 받은 값 유지, 시험 3건). 통합 928c829(검증: backend 884, ai 214, android 824 + 게이트, release 3종 시험 전용 코드 0건, compose, E2E 7/7). 결정 대기 8건은 [code-review.md](../code-review.md)의 4차 종합 절 "결정 대기 13건" 표에 모았다. MR 전체 판정은 종합 4차 절을 따른다.

### 7. sw-test 전달: 우선 테스트 대상

복잡도 10 초과 함수는 없다. 이번 수정의 회귀 시험을 우선한다.

| 함수·경로 | 약산 복잡도 | 이유 |
|-----------|-------------|------|
| running.HeartRateSeries.append | — | B4-1: 같은 측정 시각 심박을 두 번 덧붙이는 시험(응답 유실 재전송, 항목 전송 중 끊김) |
| V17 마이그레이션 (BattleActiveSlotMigrationIntegrationTest) | — | 칸이 빈 활성 대결 취소, 비활성 대결의 칸 비움, 재실행 0건. 통합 d562d26 검증에서 PostgreSQL Testcontainers로 실행해 2건 통과 |
| battle.RecordBattleService.create | — | SQLSTATE 23505만 ALREADY_IN_BATTLE, 23502 등은 재던짐(AbandonedBattleTest가 일부 포함) |
| infra.crew.JpaCrewRepository.search | — | 키워드·태그 AND·LIKE 이스케이프·cursor·size+1 경계. 추천 경로(CrewQueryService:102)의 읽기 상한 |
| web.admin.AdminAuthController / OperatorAuthorizer | — | 소셜 인증 실패 401과 실패 횟수 기록, 화이트리스트 밖 403, 제공자 무응답은 세지 않음 |
| reward.RewardReconcileService | — | 적립량 0 근거 건너뜀(B-L2), 승리 조회는 battleWin > 0일 때만 |
| web.auth.AuthController (갱신·로그아웃) | — | 폐기 목록 장애 시 503 TEMPORARILY_UNAVAILABLE |
| member.LegalDocumentCatalog | — | 주소가 비면 기동 실패(IllegalStateException) |

### 8. 설계서 갱신 요청 (코드가 옳거나 설계 공백, `/sw-design-oo` 재호출 목록)

3차 R-1~R-11과 겹치는 것은 묶었다. 조각 파일에서는 4차 원래 ID(U4-n)를 쓰고, 종합 결과서에서는 CR34-nn으로 이어 번호를 매긴다.

| ID | 대상 절 | 내용 | 비고 |
|----|---------|------|------|
| U4-1 | 6.1.8 reward, 6.1.4 running, 6.1.7 crew | reward.AccrualSource(memberId, reason, sourceId, at) record와 accrueWithRetry(source, amountOf)·restore(active, source, amountOf), running.SavedRun.of(RunRecordSaved), crew.CrewRunService.link(post, SavedRun), RewardReconcileService의 적립량 0 근거 건너뜀(OperationPolicy 조회) | 3차 R-3과 묶음 |
| U4-2 | 06-04 CrewRepository 행(:272), CrewQueryService.search | 설계는 search(CrewSearch). 코드는 search(CrewSearch, PageCursor, Week)이고 태그 AND·페이지를 저장소가 SQL로 거르며 size+1을 읽는다. 추천 경로는 PageCursor.all() | 낡음(코드가 옳다) |
| U4-3 | 6.1.6 battle | BattleRepository.activeMemberIds(Collection), Battle.cancelIfAwaitingStartReport(now), Matchmaker tick의 일괄 조회, V17 마이그레이션(V15 backfill 잔여 정리), RecordBattleService의 SQLSTATE 23505만 ALREADY_IN_BATTLE | 3차 R-1과 묶음 |
| U4-4 | 6.1.13 ApiExceptionHandler(06-06:13), ReasonCode 목록, 5.7.1 | ReasonCode.TEMPORARILY_UNAVAILABLE(503, 잠시 후 다시 시도해 주세요): 갱신·로그아웃의 폐기 목록 저장소 장애. PROVIDER_UNAVAILABLE은 소셜 제공자 무응답 전용. 앱 계약 목록 동기화 | 신규 |
| U4-5 | 6.1.16 FaceRouter(06-06:285), CommentRouter, 5.4 배포 | FaceRouter의 422 UNSUPPORTED_IMAGE(머리 정보 깨짐)·422 TOO_LARGE(가로x세로가 max_image_pixels, 기본 4096x4096 초과)는 변환기가 아니라 입력 관문이 내며 디코드하지 않고 머리만 읽는다. Settings.max_image_pixels, RUNKK_MAX_IMAGE_PIXELS 환경 변수. CommentRouter는 본문 4KiB 상한, 넘으면 413 TOO_LARGE(JSON 아님·정의 밖은 422 INVALID_REQUEST) | 3차 N5 해소 반영 |
| U4-6 | 5.4 배포(3차 R-10과 묶음) | develop 프로필 DB 비밀번호 기본값 제거, deploy.sh의 환경 값 읽기(env_value, 줄이 없으면 기본값) | 3차 R-10 확장 |
| U4-7 | 6.1.2 member LegalDocumentCatalog | 법정 문서 주소 4개가 비면 기동 실패(필수 설정) | 신규 |

### 9. 다음 행동

- [x] B4-1: HeartRateSeries.append에 측정 시각 기준 중복 제거와 같은 심박을 두 번 덧붙이는 시험(6af16a9)
- [ ] L13 잔여: 추천 경로의 공개 크루 전체 읽기에 상한이나 후보 쿼리(크루 수가 늘기 전에)
- [ ] 결정 대기 8건(L3·L4·L14·L16·L23·L25·L26·L29): 사용자·팀 결정 뒤 반영
- [ ] 설계서 갱신: `/sw-design-oo`로 U4-1~U4-7과 3차 R-1~R-11 반영
- [x] V17 통합 시험(PostgreSQL): 통합 d562d26 검증에서 PostgreSQL Testcontainers로 실행해 2건 통과

## 리뷰 2026-10-23 (5차, 백엔드 (가) 묶음)

대상 커밋: S15P31A207-v010 `feature/runkk-v010` bfe55e1c(기준 develop d7618cb). 범위는 `git diff d7618cb bfe55e1c -- backend infra` 중 (가) 묶음 140파일 +4,470 −768(전체 269파일 +11,107 −1,350 중 crew·crew.chat·report·report.target·web.crew·web.chat·web.report·web.admin.report와 그 infra를 뺀 나머지)이다. 범위 안 서버 커밋은 48개다(전체 115개 중 backend·infra를 건드린 것). 작업 트리 HEAD는 4ee91aef(bfe55e1c + develop 약관 페이지 병합)이고 이 병합은 `infra/nginx/static/legal/*`와 문서만 더해 (가) 코드는 bfe55e1c와 같다.

입력: 위 코드, docs/sw/design-oo.md v0.11과 design-oo/(06-01·06-02·06-03·06-05·06-06, 05-02 D-89~D-166, 05-03, 12장 64패키지·174화살표), design-review.md 17장, implementation.md 3.1~3.10(단계별 결정), 4차 결과서(위 4차 절)와 종합(code-review.md).
방법: 읽기 전용. git diff·grep과 파일 읽기, 줄 수·매개변수·복잡도 약산 스크립트, 실제 import 그래프를 뽑아 12.2 의존 목록과 대조한 것이 전부다. 빌드·시험은 돌리지 않았다(통합 브랜치의 push 전 검증 기록을 그대로 믿는다: backend build 통과). 구현 기록 3.x의 총괄 결정(7-3 tier_applied, 7-4 운동 시간 축 curve 등)은 설계서 갱신 요청으로 분류하고 코드 결함으로 세지 않았다.

### 리뷰 기준

| 항목 | 기준 | 출처 |
|------|------|------|
| 설계 일치 | 설계와 다르면 불일치(코드가 낫다면 "설계서 갱신 요청"), 의존은 12.2 D1~D77·N1~N17·V1~V9와 ArchitectureTest 허용 표(64패키지) | design-oo 12, 13.1 |
| 매개변수 | 4개 이하(생성자 주입·record 구성요소·설정 바인딩 형식은 제외. 도메인 객체의 private 생성자는 제외하지 않는다, 3차 B-M1과 같다) | 기본 규칙, D-73 |
| 중첩 / 함수 길이 | 제어 구조 3단계 이하 / 50줄 이하(DTO record 선언은 제외) | 기본 규칙 |
| 순환 복잡도 | 10 이하(조건문·반복문·`&&`·`\|\|`·삼항 + 1 약산). 초과는 sw-test로 | 기본 규칙 |
| 구조 | goto 등가, 예외로 정상 흐름 제어, 반복 안 return(재시도 루프는 4차까지의 기준대로 허용) | sw-code-review |
| 객체지향 | 캡슐화, setter, 생성자 유효성, 내용 결합도, 전역 가변 상태 | sw-code-review |
| 동시성·보안 | 게스트 자격 증명(해시·상수 시간·로그), 4401 만료, 시도 제한 | 요청 |
| 시험 품질 | 새 기능마다 시험이 있는가 | 요청 |
| 결합도·응집도 | 상한 스탬프, 하한 통신적, Fan-Out 7 | design-review 17장 |
| 형상 관리 | 제목 형식 타입-내용-Jira 키(AGENTS.md), 타입 feat·fix·docs·style·refactor·test·chore, 커밋은 한 가지 일. Jira 키 없음은 N-14 보류(U-04)라 판정 요인에서 뺀다 | AGENTS.md, implementation 1.1 |
| 등급 | 치명(goto 등가·내용/공통 결합·전역 가변 상태·설계에 없는 양방향 의존) / 중(데이터 손실·설계 불일치·규칙 위반) / 경 | 요청 |
| 판정 | 치명 0·중 3 이하 합격 / 중 4~10 조건부 / 치명 1+ 또는 중 11+ 불합격 | sw-code-review |

### 대상

| 패키지(묶음 안 분류) | 파일 | 줄 | 새로 들어온 것 |
|----------------------|------|----|----------------|
| member | 28 | +1,031 −245 | 게스트 시작·세션 재개·연동(AuthService·SignUpService·GuestCredential·Member), 미이용 게스트 파기(WithdrawalService), 설정 THEME·근접 알림, 닉네임 코드 포인트, TierLevelService.recordLate |
| running | 11 | +482 −42 | 정지 구간(PauseInterval·RunUpload·RunRecord·RunTrack), 티어 보정(TierApplier·TierReconcileService·TierUpdateListener), V21 |
| avatar | 8 | +584 −7 | 랜드마크 배경(BackgroundService·UnlockedBackground·Avatar), V25 |
| realtime | 12 | +450 −78 | 4401 만료(SocketSessionRegistry·TokenHandshakeInterceptor·AccessGrant), 채팅 입장(ChatPresenceRegistry·InboundMessage), 직렬화 |
| web.auth·web.attempt | 4·2 | +229·+153 | 게스트 시작·재개·연동 엔드포인트, AuthAttemptGuard 확장, common → web.attempt 이동 |
| reward·infra.reward | 4·2 | +117·+70 | 적립 집계 칼럼(RewardWallet·RewardAccrualRepository), V26 |
| notification·infra.notification·infra.integration | 6·1·2 | +99·+44·+32 | 묶음 알림(notifyCollapsing·saveCollapsing), FCM 묶음 키, verifyGrant |
| batch.* | 8 | +163 | IdleGuestWithdrawalJob·TierReconcileJob·BackgroundReconcileJob, 스케줄 설정 |
| battle·infra.battle·runimal·profile·analysis·audit·operator·policy·common | 40 | 합계 +350 | 매칭 취소 원자화(QueueRemoval), 태그·추천 삭제, 랜드마크 key, 시드 로그, ReasonCode 18개 |
| web.* 나머지(avatar·me·record·run·admin·admin.ranking·error) | 11 | +420 | 배경 엔드포인트, 정지 구간 DTO, 랭킹 컨트롤러 이동 |
| db/migration V19~V26 | 8 | +362 | enum CHECK, 회원·게스트, 정지·티어 표시, 크루 구조, 채팅, 신고, 배경, 리워드 집계 |
| infra·application.yml·build.gradle.kts·import.sql | 6 | +34 −12 | 스케줄러 풀 20, multipart 6MB, 게스트 한도 설정, 시험 힙 1,536m |

### 4차 잔여 추적

4차 합격 때의 잔여 경 11건(L3·L4·L13·L14·L16·L23·L25·L26·L29·L31·L32)을 이번 범위에서 다시 봤다.

| ID | 4차 상태 | 이번 근거(파일:줄) | 상태 |
|----|----------|--------------------|------|
| L3 | common에 AttemptLimiter·PageCursor | web/attempt/AttemptLimiter.java·ClientAddress.java로 이동(D-132, common 쪽 삭제). common/PageCursor.java는 그대로 | 일부(PageCursor는 crew 몫이라 (나)와 같이 본다) |
| L4 | AfterCommit 두 사본 | battle/AfterCommit.java, crew/AfterCommit.java 그대로. 채팅은 도메인 사건 + AFTER_COMMIT 수신자로 세 번째 사본을 만들지 않았다(D-101) | 결정 대기 |
| L13 | 추천 경로 전체 읽기 | CrewRecommender·Recommendation·TagOverlapRecommender 파일 삭제(D-91) | 소멸((나)에서 확인) |
| L14 | RewardWallet 적립 합계 전체 읽기 | reward/RewardWallet.java 집계 칼럼 `lastAccrualDate`·`lastAccrualDateAmount`, RewardAccrualRepository.sumOn(색인), V26 | 해결 |
| L16 | TierUpdateListener 실패 유실 | running/TierUpdateListener.java:41-60 재시도 3회, running/TierReconcileService.java + batch.running.TierReconcileJob 0시 10분 | 해결 |
| L23 | /admin/** permitAll 한 겹 | web/auth/SecurityConfig.java:91(`/admin/**`) 그대로 | 결정 대기 |
| L25 | X-Real-IP 신뢰 | web/attempt/ClientAddress.java:30 IP 글자가 아닌 값은 키로 쓰지 않음을 더함. 사설망·루프백 신뢰는 설정화되지 않음 | 일부, 결정 대기 |
| L26 | ai 내부 토큰 평문 http | 변경 없음 | 결정 대기 |
| L29 | 핸드셰이크 한 번만 토큰 검증 | realtime/SocketSessionRegistry.java:92-104(register), :127(expire) 등록 때 만료 시각에 4401 예약, LiveSocketHandler.java:105 메시지마다 만료 확인, TokenExpiryTest | 해결 |
| L31 | 시험 @DisplayName 비율 | src/test @DisplayName 714 / @Test·@ParameterizedTest 1,084 = 66% (4차 47%) | 개선(경, 미해결 유지) |
| L32 | 커밋 규약 | 5절 | 미해결(재발) |

잔여 경은 L3·L4·L23·L25·L26·L31·L32 일곱 건이다(해결 L14·L16·L29, 소멸 L13).

### 1. 설계 일치

의존 방향: 코드 import를 패키지 단위로 뽑아(`*.live`는 상위로 합침, web.admin.* 하위·crew.chat·report.target은 따로) 12.2 목록과 쌍마다 대조했다. (가) 묶음에서 목록에 없는 화살표는 0개다. 목록에 있는 쌍 중 코드에서 사라진 것은 (나) 몫인 crew 쪽(추천 삭제로 생긴 것)뿐이고 (가) 묶음 쌍은 모두 그대로 있다. ArchitectureTest는 PACKAGES 64개·ALLOWED 키 일치를 단언한다(ArchitectureTest.java:701-702).

| 점검 | 결과 |
|------|------|
| 새 패키지 화살표 | 없음. realtime → crew.chat(N11)·member(D54)·battle·crew, web.avatar → avatar·reward만(policy 의존은 64bc6bfc에서 stage 문자열로 해소), web.admin.ranking → analysis·audit(D77), web.auth·web.admin → web.attempt(R-1) |
| 안정성 역전 조건 | member → policy는 member/Member.java(TierBoundary)와 TierLevelService.java(OperationPolicy)뿐(D-133 조건 (1)). reward → battle은 BattleJudged·BattleWin·BattleResultService·Side뿐이고 Judgement는 import하지 않는다(D-134 조건 (1), BattleJudged.winnerSide·judgedAt가 대신 넘김) |
| Fan-Out(공통 포함) | analysis 7(상한), avatar 6, battle 6, reward 5, realtime 5, profile 5, operator 4, runimal 4, member·running·notification 3, web.* 3 이하. 상한 초과 없음 |
| 응집도 | running의 RunQueryService는 공개 메소드 25개로 analysis·reward·crew·battle·avatar·runimal이 읽는 조회 파사드다. 새로 들어온 `memberIdsSavedBetween`·`validDistanceOf`도 모두 RunRecord 자료를 읽는 통신적 응집이라 하한은 지킨다. 다만 `BackgroundService` 전용 좁은 조회가 늘어나는 방향이므로 다음 변경에서 25개를 더 키우지 않는다(참고) |
| 결합도 | 새 연산의 인수·반환은 값 전체를 쓰는 record(RunScoreInput·AccessGrant·NotificationRequest·UnlockedBackground)와 원시 값이라 데이터 결합이다. 제어 결합을 만드는 불리언 인수는 없다(BackgroundService.fill은 Consumer를 받지만 호출 분기를 정하는 플래그가 아니라 알림 후처리다). notify와 notifyCollapsing은 플래그 대신 메소드를 나눴다 |

이름·시그니처 대조(설계 06-01·06-02·06-03·06-05·06-06 서버 부분과 코드):

| 설계 | 코드 | 판정 |
|------|------|------|
| AuthService.resumeGuest(credential, now), linkSocialAccount(memberId, provider, token) → SocialLinkResult | member/AuthService.java:131, :152 | 일치 |
| SignUpService.startAsGuest(GuestStartForm) → GuestStartResult | member/SignUpService.java:107 | 일치 |
| Member.guest·attachGuestCredential·linkSocialAccount·matchesGuestSecret·recordAccess | member/Member.java:151, :163, :178, :192, :205 | 일치 |
| WithdrawalService.withdrawIdleGuests(now), IdleGuestWithdrawalGuard | member/WithdrawalService.java:150-172, member/IdleGuestWithdrawalGuard.java | 일치 |
| TokenIssuer.verifyGrant → AccessGrant(memberId, expiresAt) | member/TokenIssuer.java, infra/integration/auth/JwtTokenIssuer.java:114 | 일치 |
| MatchQueue.removeIfWaiting → QueueRemoval(REMOVED·ASSIGNED·ABSENT), MatchingService.cancel | battle/MatchQueue.java, battle/MatchingService.java:81-93 | 일치(06-03:182, :309). 구현 기록 I-3과도 같다 |
| BackgroundService.unlockReached·backfillReached·backgrounds·choose·reconcile | avatar/BackgroundService.java:81, :97, :115, :133, :150 | 일치 |
| NotificationService.notifyCollapsing, PushGateway.sendCollapsing | notification/NotificationService.java:82, notification/PushGateway.java | 일치 |
| RewardAccrualRepository(amountFor·sumOn·save) | reward/RewardAccrualRepository.java | 일치(D-109) |
| ReasonCode 18개와 상태 매핑 | common/ReasonCode.java:169-205, web/error/ApiExceptionHandler.java:46-50 | 일치(TOO_LARGE 413·UNSUPPORTED_IMAGE 422는 STATUS_BY_REASON) |
| 알림 종류 21개·감사 행위 19개·크루 역할·게시 종류·참석 응답 | NotificationType·AuditAction·MemberSettings.NOTIFICATION_TYPES, V19 CHECK | 일치(V19와 enum 값 목록 일치는 EnumChecksV011MigrationIntegrationTest가 고정) |
| 설계에 없는 이름 | TierApplier, RunRecordRepository.validRunScoreOf·tierPendingBetween·markTierApplied, TierLevelService.recordLateValidRun, TierProgress.addLateValidRun, RunRecord.tierApplied(V21), NotificationRepository.saveCollapsing, BattleJudged.winnerSide·judgedAt, AuthAttemptGuard.admitGuestStart·guestRejected, AttemptLimiter.tryAcquire·release, SettingItem BATTLE_SETTINGS의 proximityAlert 병합 | 구현 기록의 총괄 결정과 후속 리뷰 수정에서 나온 것이라 결함이 아니라 설계서 갱신 요청이다(8절) |
| 설계에 있고 쓰이지 않는 이름 | RunQueryService.validRunsAfter·RunRecordRepository.validRunsAfter(06-02:188, :242). 총괄 결정 7-3이 순번 비교를 tier_applied 표시로 바꿔 운영 코드가 부르지 않는다 | 설계서 갱신 요청(U5-A-1)과 코드 정리(BE5-A-9) |

### 2. 코딩 스타일·정확성

새 결함은 치명 0, 중 1, 경 17이다. 줄 번호는 bfe55e1c 기준이다.

| ID | 파일:줄 | 항목 | 현재 코드 한 줄 | 교정 예시 한 줄 | 등급 |
|----|---------|------|-----------------|-----------------|------|
| BE5-A-1 | member/Member.java:112 | 매개변수 5개(private 생성자). signUp(:139)·guest(:151) 두 팩터리가 이미 account null 여부로 갈리므로 accountType 인수는 중복이다. 3차 B-M1(accrueWithRetry 5개)과 같은 유형 | `private Member(SocialAccount account, AccountType accountType, Nickname nickname, Map<ConsentItem, Boolean> consents, LocalDateTime now) {` | `private Member(SocialAccount account, Nickname nickname, Map<ConsentItem, Boolean> consents, LocalDateTime now) {` 안에서 `this.accountType = account == null ? AccountType.GUEST : AccountType.LINKED;` | 중 |
| BE5-A-2 | realtime/LiveSocketHandler.java:140 | 채팅방 입장 신호(`ChatPresence` present 참)마다 DB 읽기(`CrewChatService.checkRoomAccess` → requireMember)를 하고 메시지 속도 제한이 없다. 크기는 1KB로 막지만 횟수는 안 막아(:110) 인증된 연결 하나가 같은 신호를 초당 수천 번 보내 읽기 부하를 만들 수 있다. 같은 기능의 REST 보내기는 ChatRateGuard로 초당 5건을 막는다. 소켓 하나는 순차 처리라 연결 풀(50)을 한 번에 하나만 쥐므로 경으로 둔다. 게스트는 접속지당 시간당 10개까지 만들 수 있어 연결 수도 크게 늘 수 있다 | `chatService.checkRoomAccess(memberId, presence.crewId());` | 이미 같은 세션이 그 방에 입장해 있으면 건너뛴다 `if (!chatPresence.isEntered(presence.crewId(), memberId, session.getId())) { chatService.checkRoomAccess(...); }`(ChatPresenceRegistry에 isEntered 추가), 또는 세션마다 AttemptLimiter로 10초 10회 | 경 |
| BE5-A-3 | running/RunUpload.java:89 | 순환 복잡도 11(if 6, for 1, `\|\|` 2, `&&` 1, + 1). 정지 구간 검사 한 함수에 개수·모드·범위·순서가 모여 있다 | `private static void requireValidPauses(List<PauseInterval> pauses, RunMode mode, LocalDateTime startedAt, LocalDateTime endedAt) {` | 개수·모드 검사 `requireAllowed(pauses, mode)`와 순회 검사 `requireOrderedWithin(pauses, startedAt, endedAt)`로 나눈다(각 6 안팎) | 경 |
| BE5-A-4 | avatar/BackgroundService.java:98, :108 | 해금 행이 하나도 없는 회원의 첫 랜드마크 알림이 조회 채우기에 삼켜질 수 있다. 리뷰 H1 수정(a6fe903f)은 "행이 하나라도 있는 회원"만 지킨다. 행이 없는 회원이 첫 랜드마크(인천 60km)를 넘는 기록을 저장한 직후 GET /avatar 또는 /avatar/backgrounds가 비동기 리스너(풀 core 2)보다 먼저 오면 backfillReached가 알림 없이 채워 알림이 영영 안 나간다. BackgroundUnlockIntegrationTest TC-6763이 "알림 ≤ 1"로 이 경우(0건)를 허용한다 | `boolean firstFill = transactions.read(() -> avatarRepository.findOrCreate(memberId).unlockedBackgrounds().isEmpty());` | 백필 판정을 배포 전 거리로 한정한다: `runQueryService.validDistanceBefore(memberId, cutoff)`(cutoff = V25 적용 시각 설정값)로 넘은 랜드마크만 알림 없이 채우고, 그 뒤 거리는 리스너·보정이 알림과 함께 넣는다 | 경 |
| BE5-A-5 | member/AuthService.java:161 | 이미 연동한 회원이 같은(자기) 소셜 계정으로 다시 연동하면(응답이 유실된 재시도) 자기 자신이 조회돼 ALREADY_LINKED가 아니라 ALREADY_MEMBER("이미 가입된 계정")가 나간다. 앱 A2는 ALREADY_LINKED일 때 기기 게스트 자격 증명을 지우는 갈래를 두었는데(implementation 3.8) 이 재시도에서는 그 갈래를 못 탄다. 설계 06-01:347의 확인 순서 그대로이므로 설계서 보완도 필요하다(U5-A-6) | `if (memberRepository.findByAccount(account).isPresent()) {` | `if (memberRepository.findByAccount(account).filter(m -> m.getId() != memberId).isPresent()) {`(자기 자신은 건너뛰고 Member.linkSocialAccount가 ALREADY_LINKED를 던진다) | 경 |
| BE5-A-6 | member/AuthService.java:210 | 고유 제약 위반을 예외 메시지 문자열에서 제약 이름을 찾아 판정한다. 같은 일을 RecordBattleService.isUniqueViolation(SQLSTATE 23505, 4차 B-L3)과 ApiExceptionHandler.sqlStateOf(:118)가 SQLSTATE로 하므로 판정 방식이 셋으로 갈린다. 메시지는 드라이버·로케일에 따라 달라진다 | `if (message != null && message.toLowerCase(Locale.ROOT).contains(constraint)) {` | 공용 `UniqueViolation.of(e)`(SQLSTATE 23505 + 제약 이름은 `PSQLException.getServerErrorMessage().getConstraint()`)로 모아 세 곳이 함께 쓴다 | 경 |
| BE5-A-7 | avatar/BackgroundService.java:209 | 재시도 루프만 `for (;;)`라 종료 조건이 헤더에 없다. 다른 재시도(RewardService.accrueWithRetry:96, BattleTransactions:35, TierUpdateListener:41)는 `attempt <= MAX_ATTEMPTS`를 헤더에 둔다. 루프 안 return·throw 자체는 그 패턴과 같아 허용한다 | `for (int attempt = 1; ; attempt++) {` | `for (int attempt = 1; attempt < MAX_ATTEMPTS; attempt++) { try { return work.get(); } catch (...) { log.info(...); } } return work.get();` | 경 |
| BE5-A-8 | running/TierApplier.java:33, :42 | 반환형이 boolean인데 항상 true이고 부르는 쪽(TierUpdateListener:44, TierReconcileService:81)은 반환값을 쓰지 않는다. 실패는 IllegalStateException으로만 알린다 | `boolean apply(long memberId, String recordId, RunScoreInput input) {` ... `return true;` | `void apply(...)`로 바꾸고 return을 지운다 | 경 |
| BE5-A-9 | running/RunQueryService.java:204, infra/running/JpaRunRecordRepository.java:345, reward/RewardAccrual.java:56, notification/Notification.java:64, common/TagOwner.java:6 | 운영 코드가 부르지 않는 코드가 남았다. (1) validRunsAfter는 총괄 결정 7-3 뒤 시험만 부른다. (2) 지갑이 적립 컬렉션을 잃으며 RewardAccrual.isFor가 호출자 없음. (3) Notification.collapse는 시험용 InMemoryNotificationRepositories만 부르고 실제 저장은 JpaNotificationRepository.saveCollapsing의 SQL이라 가짜와 실제 구현이 따로 논다. (4) TagOwner는 상수 CREW 하나뿐인데 TagSet.owner 필드·인수가 그대로 남아 있다 | `public List<ValidRunScore> validRunsAfter(long memberId, int ordinal) {` | 네 곳 모두 삭제하고(검증 전에 설계서에서 validRunsAfter를 뺀다) 관련 시험을 지운다. InMemoryNotificationRepositories는 SQL 동작을 따라 하도록 고친다 | 경 |
| BE5-A-10 | notification/NotificationService.java:82 | notifyCollapsing(36줄, 복잡도 6)이 notify(:56)와 수신 판정·토큰 조회·푸시 예약 20줄을 그대로 반복한다. 한쪽만 고치면 어긋난다(예: 탈퇴 판정) | `PushDecision decision = memberQueryService.pushDecision(req.recipientId(), req.type().name());` (두 곳) | `private void deliver(NotificationRequest req, Consumer<NotificationRequest> saver, Function<List<String>, PushResult> sender)`로 뽑아 notify는 `repository::save`, notifyCollapsing은 `saveCollapsing`을 넘긴다 | 경 |
| BE5-A-11 | avatar/Avatar.java:177, avatar/BackgroundService.java:133 | Optional을 메소드 인수로 쓴다. null과 비어 있음 두 가지로 "기본 배경"이 되고 호출부(AvatarController:115 `Optional.ofNullable(request.key())`)가 변환을 맡는다. D-36(불리언·플래그 인수 지양)과 같은 이유로 두 연산으로 나누는 편이 낫다. 설계 06-05:174가 이 시그니처라 설계서 갱신도 필요하다 | `public void selectBackground(Optional<String> key) {` | `selectBackground(String key)`(잠긴 키면 BACKGROUND_LOCKED)와 `useDefaultBackground()` 두 메소드로 나누고 컨트롤러가 `key == null`로 고른다 | 경 |
| BE5-A-12 | web/avatar/AvatarDtos.java:23, realtime/RealtimeMessageSerializer.java:64 | 시간대를 문자열 상수로 따로 둔다(`ZoneId.of("Asia/Seoul")`). 서버 시계 Clock 빈(RunkkApplication)이 시간대의 한 출처이고 3차 B-L5는 JwtTokenIssuer를 `clock.getZone()`으로 고쳤다. RunController(:55)는 주입한 zone을 쓴다. 같은 유형의 (나) 쪽 CrewDtos:64·ReportAdminController:36은 (나) 묶음에서 센다 | `private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");` | 변환기를 Clock에서 만든다: `AvatarDtos.BackgroundsResponse.of(view, clock.getZone())` 또는 공용 `common.ServiceZone.SEOUL` 한 곳 | 경 |
| BE5-A-13 | operator/OperatorBootstrap.java:77, infra/docker-compose.yml:58, backend/.env.example:47 | compose가 환경 변수를 늘 빈 문자열로 넣어(`${RUNKK_BOOTSTRAP_OPERATORS:-}`) D-166의 "변수가 없으면 시드 없음·로그 없음"이 compose 배포에서는 성립하지 않는다. 화이트리스트가 이미 채워졌는데 변수만 비워 둬도 기동 때마다 WARN `operator_seed_empty`가 나가 진짜 설정 실수를 가린다(순서가 빈 값 검사 → 화이트리스트 검사) | `log.warn("operator_seed_empty");` | seedIfEmpty에서 화이트리스트가 비어 있지 않으면 먼저 return하고 그다음에 빈 값 WARN을 낸다 | 경 |
| BE5-A-14 | backend/src/main/resources/application.yml:29, :40 | 비동기 실행기 주석이 "리포트·Runimal"만 말한다. 이번에 BackgroundUnlockListener(@Async)와 채팅 방 밖 알림(ChatDeliveryListener의 applicationTaskExecutor 수신자별 DB 쓰기·FCM 호출)이 같은 풀(core 2, max 4, queue 500)에 들어왔는데 크기는 그대로다. 큐가 차면 TaskRejectedException이 되어 방 밖 알림이 로그만 남고 사라지고, FCM 응답이 느리면 core 두 스레드가 막혀 해금·Runimal도 밀린다. 스케줄러 풀 20은 작업 수와 맞춰 갱신했다(주석·infra .env 일치 확인) | `core-size: 2` | 주석에 사용처 4곳을 적고 `core-size: 4`, `max-size: 8`로 하거나 채팅 알림 전용 실행기를 따로 둔다 | 경 |
| BE5-A-15 | backend/src/main/resources/db/migration/V20__member_guest_theme.sql:21-22, infra/deploy.sh | V20이 되돌릴 수 없는 `DROP COLUMN`(member.tags, crew_membership.crew_tags)을 하는데 배포 스크립트·infra/README에 마이그레이션 전 백업 단계가 없고 implementation.md 10절 "팀이 할 일"도 비어 있다(구현 기록 3.5가 "배포 전 백업"을 운영 주의로만 적었다) | `ALTER TABLE member DROP COLUMN tags;` | deploy.sh에 Flyway가 도는 api 기동 전 `pg_dump` 단계를 두거나 implementation.md 10절에 "V20 배포 전 pg_dump" 항목을 적는다 | 경 |
| BE5-A-16 | member/WithdrawalService.java:152, running/RunQueryService.java:189 | id만 쓰는데 행 전체를 읽는다. 미이용 게스트 후보는 Member 엔티티 전체(`findIdleGuests`)를 읽어 `map(Member::getId)`만 쓰고(첫 실행은 1년 치가 한 번에 몰릴 수 있다), memberIdsSavedBetween은 이틀 치 모든 회원의 SavedRun 행을 읽어 회원 ID 집합만 만든다 | `memberRepository.findIdleGuests(now.minus(GUEST_IDLE_LIMIT)).stream().map(Member::getId).toList();` | 저장소에 `findIdleGuestIds(before)`(`select m.id ...`)와 `memberIdsSavedBetween`용 `select distinct r.memberId`를 둔다 | 경 |
| BE5-A-17 | member/SignUpService.java:167, web/auth/SignUpController.java:77-92 | 기본 닉네임 중복 확인(`existsByNickname`)과 저장 사이 경합이 보류인 채다(구현 기록 3.5 "닉네임 동시 경합 재시도"). 겹치면 고유 제약(uq_member_nickname)이 409 CONCURRENT_UPDATE로 바뀌고 앱이 다시 보내지만, 컨트롤러의 catch(:83-91)가 DataIntegrityViolationException을 잡지 않아 처음 받아들인 시도의 게스트 생성 한도 자리가 반환되지 않는다 | `return memberRepository.existsByNickname(nickname.value())` | startAsGuest에서 고유 위반이면 번호를 바꿔 한 번 더 시도하고, 컨트롤러는 모든 비성공 경로에서 `guestRejected(attempt)`를 부른다(finally에서 성공 여부로 판단) | 경 |
| BE5-A-18 | common/ReasonCode.java:169 | 주석이 "v0.11 모두 409"인데 같은 블록의 TOO_LARGE(:203)는 413, UNSUPPORTED_IMAGE(:205)는 422다. 코드 주석이 STATUS_BY_REASON과 어긋난다 | `// v0.11 (설계 F.2.3, 06-01 ReasonCode: 모두 409)` | `// v0.11 (설계 F.2.3, 06-01 ReasonCode: TOO_LARGE 413·UNSUPPORTED_IMAGE 422 외 409)` | 경 |

BE5-A-1은 3차 B-M1·B-M2·B-M3(매개변수 5~6개)과 같은 유형의 재발이다. 4차에서 해결된 것과 달리 이번에는 도메인 엔티티 생성자에서 나왔다. BE5-A-12는 3차 B-L5(시간대 상수 중복)의 재발이고 BE5-A-6은 4차 B-L3(SQLSTATE 판정)의 방식을 새 코드가 따르지 않은 것이다.

### 3. 구조적 프로그래밍

goto 등가(라벨 break·continue, 예외로 정상 흐름 분기)는 없다. 반복 안 return은 재시도 루프(TierUpdateListener:41, BackgroundService:209)와 검색(WithdrawalService.idleSkipReason:210, AuthService.violates:210)뿐이고 각각 그 함수의 유일한 책임이라 허용한다. BackgroundService:209만 헤더에 종료 조건이 없어 BE5-A-7로 적었다. 중첩은 제어문 기준 3단계 이하다(들여쓰기 20칸 이상의 제어문 세 곳은 모두 람다 안이다: BackgroundService:86, AuthService:161, SocketSessionRegistry:112). 함수 길이는 50줄 이하이고(DTO record 선언 다섯 개와 enum 상수 SettingKey.MATCH_CONDITION 제외) 순환 복잡도 10 초과는 RunUpload.requireValidPauses(11) 하나다(7절).

### 4. 객체지향 구현

public 필드·검사 없는 setter는 새 코드에 없다(`public set*` 0개). Member·Avatar·RewardWallet의 상태 변경은 모두 업무 동사 메소드이고 ACTIVE 검사를 건다. UnlockedBackground·RewardAccrual은 getter뿐인 읽기 객체다. 생성자는 BE5-A-1 하나를 빼면 유효한 상태로 만든다(Member.signUp·guest가 null 검사, UnlockedBackground가 이름을 칼럼 길이로 자름). 다형성은 SettingItem.THEME처럼 상수별 메소드로 유지했고 새 `instanceof`는 패턴 매칭 일곱 곳(소켓 속성·JSON 노드)뿐이다.

점검했으나 결함이 아닌 것(동시성·보안·마이그레이션):

| 점검 | 결과 |
|------|------|
| 게스트 자격 증명 | 비밀 32바이트 SecureRandom, 저장은 SHA-256 16진 64자(Member.java:46 HASH, CHECK ck_member_withdrawn에 해시 지움 포함), 비교는 MessageDigest.isEqual(Member.java:196). 토큰 원문은 응답에만 있고 GuestCredential.Issued·Parsed·GuestStartResult·GuestSessionRequest·GuestStartResponse의 toString이 감춘다. 실패 사유는 모두 AuthFailureException 하나이고 컨트롤러는 401 본문 없음이다. 로그에 자격 증명·비밀을 남기는 줄은 없다(grep) |
| 4401 만료 | 등록 뒤에 예약을 채워 이미 지난 만료도 놓치지 않고(SocketSessionRegistry:104), 취소는 removeOnCancelPolicy로 큐에서 바로 지우며, 새 연결이 먼저 등록돼도 옛 예약이 새 등록을 닫지 않는다(`sessions.remove(memberId, registration)`). 메시지마다 만료를 다시 확인하고(LiveSocketHandler:105) 시각을 모르면 만료로 본다. 만료는 초 단위 exp에서 정각 포함이다 |
| 시도 제한 | admit이 접속지 요청·접속지 실패·자격 실패 세 키를 보고, 게스트 생성은 tryAcquire로 한도 자리를 미리 잡아 동시 요청이 한도를 넘지 못하며 입력 거부(400·409)면 release로 돌려준다. 시스템 장애(TEMPORARILY_UNAVAILABLE)는 돌려주지 않는다. GuestAuthControllerTest·AuthAttemptLimitTest(guestStartLimit, credentialFailureLimit_onGuestSession)가 고정한다. 앱의 SessionRefresher는 인증 헤더 없는 클라이언트로 갱신·재개를 불러 AuthFilter가 만료 토큰 헤더를 401로 막는 경로(permitAll 경로에도 적용)와 부딪치지 않는다 |
| 연동·회원 경합 | linkSocialAccount는 제공자 호출(수 초)을 트랜잭션 밖에 두고 짧은 쓰기만 감싼다. 같은 계정의 동시 연동은 uq_member_account가 막고(동작은 정상이고 판정 방식만 BE5-A-6 지적), Member @Version 충돌은 ApiExceptionHandler가 409로 바꾼다. 가입은 동의 이력을 같은 트랜잭션에 쓴다 |
| 미이용 게스트 파기 | 후보를 id 목록으로 뽑은 뒤 게스트마다 REQUIRES_NEW에서 다시 읽고(앱 사용 중 접속 기록·연동·탈퇴가 먼저 와도 건너뜀) 365일 정각 포함 경계를 Duration으로 센다(WithdrawalService:175-190). 한 게스트의 실패가 다른 게스트를 막지 않고 MemberWithdrawn은 커밋 뒤 수신자에게 간다. IdleGuestThreadIntegrationTest가 365일 정각·1초 모자람·진행 중 대결 건너뜀을 시험한다 |
| 티어 보정(총괄 결정 7-3) | tier_applied 표시를 조건부 UPDATE로만 세워(JpaRunRecordRepository.markTierApplied) 리스너와 보정이 겹쳐도 한쪽만 참이고, 이미 세워졌으면 IllegalStateException으로 그 트랜잭션의 티어 반영까지 되돌린다. 구멍(앞 순번이 늦게 옴)은 recordLateValidRun이 판정 없이 점수·거리만 더하고, TierProgress.addLateValidRun의 창 계산(`ordinal > validRunCount − 10`이면 창에 넣고 가장 오래된 것을 밀어냄)은 검산해 맞다. 판정 지점(10의 배수)이 구멍이면 늦은 반영이 평가를 다시 하지 않아 다음 판정 지점까지 티어가 늦는다(코드 주석에 명시된 총괄 결정이라 결함 아님, 설계서에 적을 것: U5-A-1) |
| 리워드 집계 | RewardService.accrue가 지갑 행 잠금 안에서 amountFor(유일 색인) → 오늘이면 칼럼, 더 새 날이면 0, 더 옛날이면 sumOn → wallet.accrue의 세 갈래로 합계를 정한다. 칼럼은 더 새 날로만 바뀌고 지난 날 보정은 칼럼을 건드리지 않아 맞다. V26 백필(날짜별 합계)과 ReceiverReconcileIntegrationTest(TC-6792)가 지난 날짜 갈래를 고정한다 |
| 묶음 알림 | JpaNotificationRepository.saveCollapsing은 읽지 않은 행 갱신 → INSERT … ON CONFLICT DO NOTHING → 못 넣었으면 다시 갱신 순서로 예외 없이 끝낸다(V23 부분 유일 색인, 트랜잭션이 깨지지 않음). 푸시 묶음 키는 "종류_대상"이고 FCM v1 규격(android.collapse_key, apns.headers.apns-collapse-id)과 맞다 |
| 매칭 취소 원자화 | Redis는 Lua 한 번, JDBC는 `delete … where status='WAITING'` 한 문장으로 상태 확인과 삭제를 묶는다. 지운 행이 없을 때의 ASSIGNED/ABSENT 구분은 거부 사유에만 쓰이고 둘 다 ALREADY_MATCHED라 사이에 바뀌어도 결과가 같다 |
| 마이그레이션 V19~V26 | 제약 이름이 앞 버전과 맞고(ck_notification_type·ck_audit_record_*·ck_crew_*·ck_member_withdrawn), V20 ck_member_account가 게스트·연동·탈퇴 세 상태를 모두 허용한다. V21은 기존 기록을 tier_applied = TRUE로 채운 뒤 기본값을 FALSE로 바꾼다. V25는 이름이 같은 원소의 두 번째부터 'LM_순번'을 붙여 키 충돌이 없다. 마이그레이션마다 PostgreSQL 통합 시험이 있다(EnumChecksV011·MemberGuestV20·RunRecordPauseV21·CrewV22·CrewChatMigration·ReportMigration·AvatarBackgroundMigration·RewardWalletDailyTotalMigration). V19·V20·V22는 CHECK 재생성과 칼럼 삭제를 한 트랜잭션에서 한다 |
| 정지 구간과 유효 판정 | 정지에 걸친 쌍은 최고 시속·경로 길이에서 빼고(RunTrack.crossesPause, 경계 비포함 D-163), 운동 시간과 wall(종료 − 시작 − 정지)로 isBackedBy를 건다(ValidityRule:48). 정지를 거짓으로 늘려 느린 구간을 숨겨도 경로 길이가 함께 빠져 선언 거리 ≤ 경로 길이 × 비율이 깨진다. 시험 PauseJudgementTest(521줄)·RunUploadPauseTest |
| 인프라 | 스케줄러 풀 20은 작업 20개(analysis 3, audit 1, avatar 2, battle 2, crew 5, member 3, notification 1, reward 1, running 2)와 맞고 application.yml·backend/.env.example·infra/.env.example·docker-compose가 모두 20이다. multipart 요청 6MB는 nginx client_max_body_size 10m 안이고 파일 한도는 1MB 그대로다. /ws의 nginx 읽기 제한은 2시간이다 |

### 5. 형상 관리

| 활동 | 판정 | 근거 |
|------|------|------|
| 형상 식별 | 예 | 모듈·패키지가 설계 64개와 ArchitectureTest로 식별되고 산출물 위치가 AGENTS.md·implementation.md 2절에 있다 |
| 버전 제어 | 예(부분) | 커밋 이력과 통합 브랜치·MR !14가 있다. 저장소에 git 태그가 0개이고 backend/build.gradle.kts:10 version이 `0.0.1-SNAPSHOT`이라 "태그 또는 버전 번호"는 4차와 같은 상태다(참고) |
| 변경 통제 | 아니오 | 범위 서버 커밋 48개 중 Jira 키 0개, 브랜치 `feature/runkk-v010`에 키가 없다(AGENTS.md는 `feature/{Jira 키}-{작업명}`). N-14 보류 결정(implementation U-04)이라 판정 요인에서 뺀다 |
| 형상 감사 | 예 | 기준선 d7618cb 대비 diff가 계약 파일(contract/)·마이그레이션·시험까지 재현된다 |
| 상태 보고 | 예(경 지적) | 커밋 제목이 사람이 읽을 수 있고 타입 접두가 붙어 있다. 다만 (1) 범위 48개 중 `merge:` 4개는 AGENTS.md 타입 목록(feat·fix·docs·style·refactor·test·chore) 밖이고 4차 L32의 `Merge branch`가 `merge:`로 바뀐 것뿐이다. (2) 한 커밋에 두 가지 일이 든 것이 31ef3545(미이용 게스트 따로 파기 + 생성 한도 선점), a6fe903f(조회 채움 알림 + 만료 예약 스케줄러), b78e98ab(알림 정리 시각 + 주간 거리 재합산 시각 + 운영자 시드 로그), 64bc6bfc(배경 단계 이름 + 시험 자료), 3f484502(초대 코드 + 사진 장수), 40564cc7(태그 + 추천 + Runimal) 여섯 건이다. "리뷰 반영:" 접두는 무엇을 바꿨는지 제목에 담지 못한다. L32로 계속 추적한다 |

### 6. 집계와 판정

| 구분 | 치명 | 중 | 경 | 구성 |
|------|------|----|----|------|
| 새 결함(BE5-A-1~18) | 0 | 1 | 17 | 중 BE5-A-1 / 경 BE5-A-2~18 |
| 4차 잔여 | 0 | 0 | 7 | L3(일부)·L4·L23·L25·L26·L31·L32. L14·L16·L29 해결, L13 소멸 |
| 합계 | 0 | 1 | 24 | |

판정: **합격**
근거: 치명 0, 중 1(3 이하). 설계 일치 점검을 수행했고 새 화살표·설계 외 이름 중 결함으로 센 것이 없다(설계 외 이름은 총괄 결정이라 8절로 보냈다). 형상 관리 5항목 중 변경 통제(Jira 키)만 아니오이며 N-14 보류의 사용자 결정으로 판정 요인에서 뺐다(4차와 같다). 중 1건 BE5-A-1은 생성자 인수 하나를 빼는 작은 수정이다. 경 중 우선순위는 BE5-A-4(알림 유실 경합)·BE5-A-5(재시도 오류 코드)·BE5-A-2(입장 신호 속도 제한)·BE5-A-15(V20 배포 전 백업)이다. MR 전체 판정은 (나) 묶음과 합친 종합을 따른다.

### 7. sw-test 전달: 우선 테스트 대상

순환 복잡도 10 초과 함수(약산):

| 함수 | 약산 복잡도 | 이유·경로 |
|------|-------------|-----------|
| running.RunUpload.requireValidPauses | 11 | 빈 목록, 모드가 FREE 아님(정지 없으면 통과), 정확히 100개와 101개, 시작·종료 null, `from == startedAt`(허용)·`from < startedAt`, `to == endedAt`(허용)·`to > endedAt`, 앞 `to == 뒤 from`(맞닿음 거부)·`to < from`(허용), 한 구간만 있을 때 previous null. 경계값 8개와 시간순 위반 1개가 기초 경로의 독립 경로 11개를 덮는다 |

10에 가까운 함수(8~10, 참고): infra.integration.auth.JwtTokenIssuer.parse(10, 4차와 같음), running.RunTrack.timeToReach(8), member.Member.editProfile(7), realtime.LiveSocketHandler.handleTextMessage(7), running.TierUpdateListener.onRunSaved(7), notification.NotificationService.notifyCollapsing(6).

회귀·신규 시험을 우선할 경로(결함 지적 연결과 위험 높은 새 동시성):

| 함수·경로 | 이유 |
|-----------|------|
| avatar.BackgroundService.backfillReached + unlockReached | BE5-A-4: 해금 행 없는 회원이 첫 랜드마크를 넘긴 직후 조회가 리스너보다 먼저 오는 순서에서 BACKGROUND_UNLOCKED 알림이 1건이 되는가(지금 TC-6763은 0건도 통과시킨다) |
| member.AuthService.linkSocialAccount | BE5-A-5: 이미 LINKED인 회원이 자기 계정으로 같은 요청을 다시 보낼 때의 오류 코드, 다른 회원의 계정이면 ALREADY_MEMBER, 동시 연동 uq_member_account 경합 |
| realtime.LiveSocketHandler.applyChatPresence | BE5-A-2: 같은 방 입장 신호 반복 시 checkRoomAccess 호출 횟수, 크루 밖이면 기록 안 함, 퇴장은 확인 없음 |
| member.Member 생성 경로(signUp·guest) | BE5-A-1 수정 뒤 accountType·동의 이력·lastAccessAt 동일성(MemberGuestV011Test 회귀) |
| operator.OperatorBootstrap.seedIfEmpty | BE5-A-13: 변수 없음·빈 문자열·공백·화이트리스트 이미 있음 조합의 로그(OperatorSeedLogTest 확장) |
| running.TierApplier / TierReconcileService.reconcile | 구멍 메움과 판정 지점 구멍(10번째가 늦을 때 다음 판정 전까지 티어 동일), 리스너와 보정이 같은 기록을 동시에 반영(markTierApplied 한쪽만 참) |
| reward.RewardService.accrue | 칼럼 날 = 오늘 / 더 새 날 / 더 옛날 세 갈래와 자정 직전·직후 사건 시각, 상한 경계 |
| web.auth.AuthAttemptGuard.admitGuestStart·guestRejected | 한도 직전 동시 요청, 입력 거부 뒤 자리 반환, 닉네임 경합(BE5-A-17) 뒤 자리 |
| realtime.SocketSessionRegistry.register·expire | 만료가 등록보다 먼저 도는 경우, 같은 회원 재연결과 옛 예약, 서버 종료 시 1012 |
| notification.JpaNotificationRepository.saveCollapsing | PostgreSQL 두 트랜잭션 동시 삽입(읽지 않은 행 1개), 읽은 뒤 새 행, 길이 500 초과 |
| db/migration V20·V25 | 기존 데이터가 있는 DB에서의 적용(V20 칼럼 삭제 전후 행 수, V25 이름 중복·이름 변경 기준값) |

### 8. 설계서 갱신 요청 (코드가 옳거나 설계 공백, `/sw-design-oo` 재호출 목록)

| ID | 대상 절 | 내용 | 비고 |
|----|---------|------|------|
| U5-A-1 | 06-02 TierReconcileService(:225-234)·TierUpdateListener(:223)·RunRecordRepository(:242)·RunQueryService(:188), 12 D-108, F.5.3 V21 | 총괄 결정 7-3을 반영한다: 판단 근거를 순번 비교에서 run_record.tier_applied 표시로 바꿨다. 새 이름 TierApplier(패키지 전용 보조), RunRecordRepository.tierPendingBetween·validRunScoreOf·markTierApplied, TierLevelService.recordLateValidRun, TierProgress.addLateValidRun, MemberQueryService.appliedValidRunCount의 용도(TierApplier가 지난 순번인지 가름). validRunsAfter는 삭제 또는 유지를 정한다(BE5-A-9). 판정 지점이 구멍이면 늦은 반영이 평가를 다시 하지 않는 한계를 적는다 | 구현 기록 3.3 |
| U5-A-2 | 06-05 NotificationRepository(06-05 NotificationService 절) | `void saveCollapsing(NotificationRequest, LocalDateTime)`: 읽지 않은 행 갱신 → INSERT … ON CONFLICT DO NOTHING → 갱신, V23 부분 유일 색인 | 구현 기록 3.7, 리뷰 M4 |
| U5-A-3 | 06-03 BattleJudged | `Optional<Side> winnerSide()`, `LocalDateTime judgedAt()` 접근자(reward가 Judgement를 import하지 않게, D-134 조건 (1)) | |
| U5-A-4 | 06-01 AuthAttemptGuard·web.attempt AttemptLimiter, D-132 | `admitGuestStart`·`guestRejected`(한도 자리를 받을 때 선점하고 입력 거부면 반환), AttemptLimiter.tryAcquire·release, GuestStart 한도 설정 `runkk.auth.attempts.guest-starts`·`guest-start-window` | 리뷰 MEDIUM, 구현 기록 3.5 |
| U5-A-5 | 06-01 SettingItem·BattleSettings | 대결 설정 값에 proximityAlert가 없으면 현재 값을 유지한다(옛 앱 호환, D-92) | |
| U5-A-6 | 06-01 AuthService.linkSocialAccount(:347) | 확인 순서를 정한다: 연동하려는 계정이 이미 요청한 회원 자신의 것이면 ALREADY_MEMBER가 아니라 ALREADY_LINKED로 답한다(BE5-A-5 재시도) | 설계 보완 |
| U5-A-7 | 06-05 BackgroundService.choose·06-05 Avatar.selectBackground(:174) | Optional 인수 대신 두 연산으로 나눈다(BE5-A-11) | 코드 정리와 함께 |
| U5-A-8 | 05-02 실시간(채팅 입장), 05-03 | ChatPresence 신호의 반복·속도 규칙(같은 세션의 같은 방 입장은 검사 생략, 또는 세션당 한도)을 적는다(BE5-A-2) | 보안 보강 |
| U5-A-9 | 5.4 배포, 05-02 D-166 | (1) compose의 빈 환경 변수 처리 규칙(RUNKK_BOOTSTRAP_OPERATORS 빈 값은 화이트리스트가 비었을 때만 WARN), (2) V20 같은 비가역 마이그레이션 전 백업 절차, (3) 비동기 실행기 사용처와 크기(core·max·queue, BE5-A-14) | 운영 |
| U5-A-10 | 06-01 ReasonCode 표(:113 부근), F.2.3 | 18개 중 TOO_LARGE 413·UNSUPPORTED_IMAGE 422와 나머지 409를 표에 나눠 적는다(코드 주석과 맞춤, BE5-A-18) | |

### 9. 다음 행동

- [ ] BE5-A-1: Member 생성자 인수를 4개로(accountType을 account에서 정함)
- [ ] BE5-A-4·BE5-A-5·BE5-A-2: 알림 유실 경합, 재시도 오류 코드, 입장 신호 반복 처리를 고치고 7절의 시험을 더한다
- [ ] BE5-A-15: V20 배포 전 백업 단계를 deploy.sh 또는 implementation.md 10절에 둔다
- [ ] 나머지 경(BE5-A-3·6~14·16~18): 다음 변경 때 정리(미사용 코드 삭제 BE5-A-9, 중복 제거 BE5-A-10 포함)
- [ ] 설계서 갱신: `/sw-design-oo`로 U5-A-1~U5-A-10 반영
- [ ] (나) 묶음 결과와 합쳐 MR !14 종합 판정을 낸다(`docs/sw/code-review.md` 5차 종합 절)

## 리뷰 2026-10-23 (5차, 백엔드 (나) 묶음: 크루·채팅·신고)

대상 커밋: feature/runkk-v010 bfe55e1c (기준 develop d7618cb). 범위는 `git diff d7618cb bfe55e1c -- backend` 중 (나) 패키지: crew(+live), crew.chat(+live), report, report.target, web.crew, web.chat, web.report, web.admin.report, infra.crew, infra.chat, infra.report, batch.crew, 그리고 이들의 시험·V22~V24 마이그레이션. 주 소스 +6,800 −570줄(새 파일 약 79개, 수정 약 44개), 이 범위를 건드린 커밋은 병합을 뺀 16개다. ((가) 묶음이 나머지 백엔드를 본다.)

입력: S15P31A207-v010 feature/runkk-v010 bfe55e1c, docs/sw/design-oo.md v0.11과 design-oo/06-04(6.1.7·6.1.7a)·06-05(6.1.12a)·06-06·06-07(A-12·A-24·A-25·A-28)·05-02(D-98~D-106, D-115, D-125~D-127, D-132, D-135, D-142, D-146, D-149, D-151~D-155, D-160, D-165)·05-03·12-의존도, design-review/17, implementation.md(3장 결정은 설계서 갱신 요청으로 분류), 4차 절.
방법: 읽기 전용. git log/diff/grep, 파일 읽기, 매개변수·길이·복잡도·반복 안 return 약산 스크립트(scratchpad/metrics.py, loops.py)만 썼다. 빌드·시험은 돌리지 않았다(통합 브랜치 검증 결과는 implementation.md 3.x를 따른다).

### 리뷰 기준

| 항목 | 기준 | 출처 |
|------|------|------|
| 설계 일치 | 설계와 다르면 불일치(코드가 낫거나 설계 공백이면 "설계서 갱신 요청"), 의존은 12-의존도·ArchitectureTest | design-oo 12, 13.1 |
| 매개변수 | 4개 이하(DI 생성자·record 구성요소 제외, 도메인 생성자·정적 팩터리·private 메소드는 포함) | D-73, 4차와 같음 |
| 중첩 / 함수 길이 | 제어 구조 3단계 이하 / 50줄 이하 | 기본 규칙 |
| 순환 복잡도 | 10 이하(조건·반복·case·&&·\|\|·?: 수 + 1 약산). 초과는 결함이 아니라 sw-test 전달 | 4차 방식 |
| 구조 | goto 등가, 예외로 정상 흐름 제어, 넓은 catch, 반복 안 return·continue | sw-code-review 5 |
| 객체지향 | 캡슐화, 내용 결합도, 전역 가변 상태, Tell Don't Ask, 타입 분기 | sw-code-review 6 |
| 동시성 | 행 잠금 순서, 재시도, 조건부 UPDATE, 고유 색인, 멱등 | 5.3, A-12·A-24·A-25·A-28 |
| 보안 | 권한표(3.0.3) 우회, 사진 업로드·EXIF 제거, 입력 한도, 속도 제한 | 3.0.3, D-100, D-142, NFR-042 |
| 형상 관리 | 제목 `{타입}: {내용} [{Jira 키}]`, 타입 feat·fix·docs·style·refactor·test·chore, 커밋은 한 가지 일. Jira 키 없음은 N-14 보류 결정 그대로(판정 요인 제외) | AGENTS.md, implementation.md U-04 |
| 등급 | 치명(goto 등가·내용/공통 결합·전역 가변 상태·설계에 없는 양방향 의존) / 중(데이터 손실·설계 불일치·권한 우회·규칙의 광범위 위반) / 경 | 요청 |
| 판정 | 치명 0·중 3 이하 합격 / 중 4~10 조건부 / 치명 1+ 또는 중 11+ 불합격 | sw-code-review |

### 대상

| 항목 | 내용 |
|------|------|
| 코드 | S15P31A207-v010/backend, 브랜치 feature/runkk-v010 bfe55e1c, MR !14(Draft, squash) |
| 범위 | 위 (나) 패키지 주 소스 +6,800 −570줄, 시험 약 200개(crew 99·crew.chat 31·report 35·web 36)와 스레드 통합 시험 3개(CrewThreadIntegrationTest·CrewV011ThreadIntegrationTest·ReportThreadIntegrationTest), V22·V23·V24 |
| 새로 들어온 것 | 운영진·권한표 16행, 게시 계층(SchedulePost ← CrewRunPost·MeetupPost, NoticePost, PollPost, BoardPost), 게시 사진·JpegMetadataStripper·PostPhotoStore, 공동 목표, CrewNewsNotifier·PollReminderJob, 탈퇴·강퇴 응답 정리(D-146), crew.chat·web.chat·infra.chat, report·report.target·infra.report·web.admin.report, web.attempt 이동(D-132) |
| 크루 Fan-Out | crew import 패키지 common·notification·audit·member·running·policy·runimal(+crew.live 합침) = 7, 상한 7 안. report.target → crew·crew.chat·report·common만, web.chat → crew.chat만, report → common·notification·audit·member만(crew·crew.chat을 모름). 새 화살표 없음, 순환 없음 |

### 4차 잔여 추적 ((나) 범위)

| ID | 4차 지적 | 근거(파일:줄) | 상태 |
|----|----------|---------------|------|
| L3 | common에 AttemptLimiter·PageCursor | AttemptLimiter는 web.attempt로 이동(D-132). 크루 쪽 쓰임은 web/crew/CrewJoinAttemptGuard.java:17, web/chat/ChatRateGuard. PageCursor는 common에 남김(D-132 (1) 결정) | 해결(설계 결정) |
| L4 | AfterCommit 두 사본 | crew/AfterCommit.java는 그대로, crew.chat은 사본 없이 도메인 사건 + @TransactionalEventListener(chat/ChatDeliveryListener.java:78, D-132 (3)) | 해결(설계 결정) |
| L13 | 추천 경로가 공개 크루 전체를 읽음 | crew/CrewQueryService.java에서 recommend·CrewRecommender 의존을 지움(FR-118 삭제, D-91). GET /crews/recommendations는 web/crew/CrewController.java:98 `{id:[0-9]+}`로 404 | 해결 |
| L31 | 시험 메소드 @DisplayName 47% | 이 범위 시험 198개 중 @DisplayName 190개(96%) | 해결 |
| L32 | 커밋 규약 | 범위 115커밋 중 병합 14개가 `merge:` 타입(타입 목록 밖), 1a9b10e2 한 커밋 86파일 8갈래, Jira 키 0개(N-14 보류) | 미해결(경, BE5-B-22) |

4차에서 닫은 유형의 재발: 매개변수 5개 이상(3차 B-M1~M3 유형) 7곳, 시간대 상수 중복(3차 B-L5 유형) 3곳, 반복 안 return(4차 "찾지 못함") 1곳이 새로 나왔다. 아래 BE5-B-2~7·BE5-B-10에 있다.

### 1. 설계 일치

설계가 정한 동작을 어긴 곳 1건(BE5-B-1)과 설계서 갱신 요청이 있다. 나머지는 일치한다. 의존은 위 표대로 12-의존도 안이고 ArchFixture 시험(ArchFixtureCrewUsesChat·ArchFixtureCrewUsesReportTarget·ArchFixtureCrewImplementsReportPort·ArchFixtureChatLiveUsesCrew·ArchFixtureReportUsesCrew·ArchFixtureWebCrewCallsReportedView)이 어긴 경우를 닫는다.

| ID | 파일:줄 | 항목 | 현재 코드 한 줄 | 교정 예시 한 줄 | 등급 |
|----|---------|------|-----------------|-----------------|------|
| BE5-B-1 | crew/chat/CrewChatService.java:151, crew/chat/CrewChatMessage.java:109-114 | 크루를 떠난·강퇴된 작성자가 자기 채팅 메시지를 지울 수 있다. 05-03 5.7.1 `DELETE /chat-messages/{id}` 권한은 "크루 구성원"이고 3.0.3 DELETE_OWN_CONTENT는 L·M·R뿐이다. `Crew.membershipOf`는 소속 중(isActive)만 보므로 떠난 회원의 relation이 OUTSIDER인데 `delete`는 `isAuthor`만 보고 통과시킨다. 크루 게시글 쪽 `CrewPost.delete`는 OUTSIDER를 먼저 막아(CrewPost.java:107) 두 삭제가 다르다. 강퇴된 사람이 퇴장 뒤 자기가 남긴 막말 메시지를 지워 근거를 없애고(D-126이 막으려는 신고 회피) 방 안 크루원에게 CrewChatRemoved(DELETED)가 나간다. 설계 6.1.7a `CrewChatMessage.delete` 문장("작성자 본인 또는 L·M")이 이 틈을 만들었으므로 코드와 설계 둘 다 고친다(U5-9). 시험(CrewChatServiceTest.delete)에 떠난·강퇴된 작성자 사례가 없다 | `CrewRelation relation = membership.relationOf(memberId, message.crewId());` | `CrewRelation relation = requireMember(memberId, message.crewId());`(NOT_CREW_MEMBER, 채팅의 기존 규칙) 또는 `delete` 첫 줄에 `if (relation == CrewRelation.OUTSIDER) throw new PermissionDeniedException(...)`, 시험 추가 | 중 |

점검했으나 결함 아님(통과):

| 점검 요청 | 결과 |
|-----------|------|
| 권한표 16행(3.0.3, F.5.1) | CrewPermissionPolicy.java:31-47이 16행을 불변 표로 옮겼다(시험 CrewPermissionPolicyTest가 16행×4관계 64칸). 운영진: 공지·고정(MANAGE_NOTICE)·목표(SET_GOAL)·가입 결정(VIEW_OR_DECIDE_JOIN)·남의 글 삭제(DELETE_ANY_CONTENT)는 STAFF, 지정·강퇴·정보 변경·양도·삭제는 LEADER_ONLY. 쓰기 호출은 모두 `crewAccess.check/checkForUpdate`를 먼저 거치고, 글 단위 연산(answerAttendance·pin·respondPoll·pollResult)은 글의 crewId로 검사해 다른 크루 글을 만질 수 없다 |
| 숨김 규칙 D-126·D-154 | 목록·응답·삭제·사진·고정이 모두 `visible`·`visibleForUpdate`(isVisible)를 거쳐 숨김·삭제 글은 POST_NOT_FOUND. 채팅은 `requireVisible`·`findByIdForUpdate().filter(isVisible)`. 신고 접수는 `ReportedContent.hidden`이면 REPORT_TARGET_NOT_FOUND(ReportService.java:70-76, D-115) |
| 신고 대상 연결 D-135 | 세 공개 연산(reportedView·hideReported·unhideReported)은 report.target 어댑터만 부른다(ArchFixture 시험). CrewPostReportAdapter.targetKindOf가 PostKind를 모두 다뤄 새 종류가 생기면 컴파일이 깨진다 |
| D-146·D-117 응답 정리 | leave·kick이 Crew.release와 같은 트랜잭션에서 `deleteOpenResponses`(시작 전 일정·마감 전 투표만, MembershipService.java:189·203), 회원 탈퇴는 투표 응답·대면 모임 참석을 모두 지우고 크루런 참가는 익명화(D-28, D-146 마지막 문장과 같다) |
| D-151·D-152·D-154·D-165 | MeetupPost·CrewRunPost 생성자가 작성자 GOING, enterLobby가 GOING으로 맞춤, unhideReported가 보이는 고정 3개면 고정을 풀고 보임(CrewPostService.java:457-473), pin 재고정은 변화 없이 참(:270-275), 마감 now ≥ closesAt(PollPost.java:101) |
| D-155 입력 오류 코드 | MeetupDraft·PollDraft 생성자가 IllegalArgumentException(400), 시각 조건만 SCHEDULE_INVALID·POLL_INVALID(409). 본문 길이는 모든 종류가 BODY_TOO_LONG(409) |
| 엔드포인트 (05-03) | 크루 18개·채팅 5개·신고 4개가 05-03 표와 경로·메소드가 같다. PUT /posts/{id}/vote는 지웠고 GET /crews/recommendations는 404 |

### 2. 코딩 스타일·정확성

새 결함은 치명 0, 중 4, 경 6이다. 매개변수 5개 이상이 가장 많다(Draft·record를 만들어 4개 안에 둔다는 D-73 방침과 어긋나는 곳).

| ID | 파일:줄 | 항목 | 현재 코드 한 줄 | 교정 예시 한 줄 | 등급 |
|----|---------|------|-----------------|-----------------|------|
| BE5-B-2 | crew/PostSummary.java:41 | 정적 팩터리 매개변수 6개(`of`). 호출은 CrewPost.baseSummary 한 곳이고 같은 값이 `new PostSummary(...)` 11개 구성요소 호출로도 가능하다 | `static PostSummary of(long postId, PostKind kind, Long authorId, String body, LocalDateTime writtenAt, boolean mine)` | 공통 머리를 `record PostHeader(long postId, PostKind kind, Long authorId, String body, LocalDateTime writtenAt)`로 묶어 `of(PostHeader header, boolean mine)`(2개) | 중 |
| BE5-B-3 | crew/WeeklyRace.java:51 | 매개변수 5개(`restore`). 목표·설정자·달성 일시는 저장소가 한 행에서 읽은 한 덩어리다 | `public static WeeklyRace restore(long crewId, Week week, Distance goal, Long goalSetBy, LocalDateTime achievedAt)` | `record GoalState(Distance goal, Long setBy, LocalDateTime achievedAt)`를 만들어 `restore(long crewId, Week week, GoalState state)` | 중 |
| BE5-B-4 | infra/crew/JpaCrewPostRepository.java:261 | 매개변수 5개(`deleteMemberRows`). 표 이름과 조건은 항상 짝이라 열거형 하나로 묶인다 | `private int deleteMemberRows(String table, String postCondition, long crewId, long memberId, LocalDateTime now)` | `enum OpenResponse { RUN("crew_run_participation", "post_kind = 'CREW_RUN' and scheduled_at > :now"), … }`를 두고 `deleteMemberRows(OpenResponse kind, long crewId, long memberId, LocalDateTime now)`(4개) | 중 |
| BE5-B-5 | crew/SchedulePost.java:20, crew/chat/CrewChatMessage.java:75, report/ContentReport.java:68, crew/RelayEntry.java:26 | 도메인 생성자 매개변수 5개 네 곳. `ContentReport`는 reason·detail이 이미 ReportCommand에 있고, `CrewChatMessage`는 write가 draft를 받아 두 값을 풀어 넘기며, `SchedulePost`는 CrewPost 4개 + scheduledAt, `RelayEntry`의 보조 생성자는 record 정규 생성자에서 mine만 뺀 것이다 | `protected SchedulePost(long crewId, long authorId, String body, LocalDateTime scheduledAt, LocalDateTime now)` | `ContentReport(long caseId, long reporterId, ReportCommand command, LocalDateTime at)`, `CrewChatMessage(long crewId, long authorId, ChatDraft trimmed, LocalDateTime at)`, SchedulePost는 `(PostHeader header, LocalDateTime scheduledAt)`, RelayEntry 보조 생성자는 지우고 호출부가 `false`를 준다 | 중 |
| BE5-B-7 | web/admin/report/ReportAdminController.java:36, web/crew/CrewDtos.java:64, batch/crew/CrewSchedulingConfig.java:25 | 시간대 상수 중복(3차 B-L5 유형 재발). 서버 시간대를 `ZoneId.of("Asia/Seoul")`로 세 곳(범위 밖까지 합치면 12곳)에서 따로 박는다. 채팅은 `clock.getZone()`을 써서 올바르다(CrewChatService.java:290). 시계 시간대를 바꾸면 응답 오프셋이 어긋난다 | `private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");` | 변환 시각이 필요한 곳은 주입된 `Clock`의 `clock.getZone()`을 쓰거나 common에 `ServerZone.ID` 한 곳을 둔다 | 경 |
| BE5-B-8 | crew/CrewPostService.java:222·432, crew/chat/CrewChatService.java:301·321, crew/WeeklyRaceService.java:206, crew/CrewQueryService.java:238, crew/CrewRunViews.java:110 | 같은 규칙의 사본. "탈퇴한 사용자" 대체(`orElse(DisplayName.WITHDRAWN)`)가 다섯 곳, 코드 포인트 100자 미리보기(`preview`)와 `PREVIEW_LENGTH = 100`이 CrewPostService·CrewChatService에 똑같이 두 벌 | `if (body.codePointCount(0, body.length()) <= PREVIEW_LENGTH) { return body; }` | `common.Preview.of(String text)` 한 곳(crew·crew.chat 모두 common을 이미 import)과 `MemberSummaries.displayNameOrWithdrawn(map, id)` 한 곳 | 경 |
| BE5-B-9 | crew/CrewPostService.java:312-346, web/crew/CrewDtos.java:459-484 | 람다가 세 겹(map 안의 map 안의 map)이라 읽기 어렵다. `resultOf`는 35줄에 들여쓰기가 끝없이 밀리고 `PollResultResponse.of`는 `r\n.displayName()`처럼 줄이 꺾였다 | `.map(o -> { List<PollResultView.Respondent> respondents = poll.responses().stream()...map(r -> new PollResultView.Respondent(...))` | 선택지 하나를 만드는 `optionOf(PollOption o, PollPost poll, Map<Long, MemberSummary> names)`와 DTO쪽 `Option.of(PollResultView.Option o)`로 나눈다 | 경 |
| BE5-B-10 | infra/report/JpaReportCaseRepository.java:141-155 | 반복문 안 return 둘(순회 검색의 조기 반환). 원인 사슬 검색은 스트림이나 재귀로 단일 출구에 둘 수 있다 | `for (Throwable cause = e; cause != null; cause = cause.getCause()) { if (...) { return true; } ... }` | `return Stream.iterate(e, Objects::nonNull, Throwable::getCause).anyMatch(JpaReportCaseRepository::mentionsReporterUnique)` 꼴로 한 줄 단일 출구 | 경 |
| BE5-B-11 | web/crew/CrewPostController.java:179 | 낡은 주석. 상수(:47)와 구현 결정은 private 5분인데 메소드 설명은 "private 캐시 1일" | `/** 게시 사진 바이트(image/jpeg, FR-212). 사진이 속한 크루의 현재 구성원만, private 캐시 1일. */` | `… private 캐시 5분(PHOTO_CACHE).` | 경 |
| BE5-B-12 | web/admin/report/ReportAdminController.java:50, report/ReportService.java:127 | 쓰지 않는 매개변수와 Optional 매개변수. `cases`의 `actorId`는 본문에서 안 쓰고(인터셉터가 운영자를 이미 확인), `ReportService.cases(Optional<ReportStatus> status, …)`는 선택 인자를 Optional로 받는다 | `@RequestAttribute(ACTOR_ID) String actorId,`(cases) / `cases(Optional<ReportStatus> status, PageCursor cursor)` | `cases`에서 actorId를 빼고(인터셉터가 막는다), 서비스는 `@Nullable ReportStatus`나 `cases(PageCursor)`·`cases(ReportStatus, PageCursor)` 둘로 나눈다 | 경 |

### 3. 구조적 프로그래밍

goto·넓은 catch(재던지기 외)·예외로 정상 흐름 제어는 새 코드에 없다. 반복 안 return·continue가 JpegMetadataStripper에 몰려 있다.

| ID | 파일:줄 | 항목 | 현재 코드 한 줄 | 교정 예시 한 줄 | 등급 |
|----|---------|------|-----------------|-----------------|------|
| BE5-B-6 | crew/JpegMetadataStripper.java:43-88 (`strip`), :119-131 (`hasIdentifier`), :137-154 (`scanDataEnd`) | 반복 안 return·continue. `strip`의 while에 return 1(:65)·continue 2(:58, :70)·throw 4가 섞여 출구가 여럿이고, 순환 복잡도 약 16(10 초과)·중첩 3이다. 업로드 보안(NFR-042)의 핵심 함수라 흐름을 한 번에 따라가기 어렵다. 동작(허용 목록, 프로그레시브 스캔 사이 제거, EOI 뒤 버림)은 맞고 시험 9개가 받친다 | `if (marker == EOI) { … out.write(jpeg, pos, 2); return out.toByteArray(); }` | 마커 한 개를 처리하는 `int step(byte[] jpeg, int pos, Output out)`(다음 위치를 돌려줌)과 종료 표시(`state.finished`)로 나눠 `while (!state.finished && pos < jpeg.length) { pos = step(...); }` 뒤 한 곳에서 `return out.toByteArray()`. `hasIdentifier`는 `Arrays.equals(jpeg, start, start + id.length, id, 0, id.length)` | 중 |

### 4. 객체지향 구현

캡슐화(필드 private, public 필드 0)와 불변 record 사용은 좋다. 엔티티가 외부에 상태를 직접 열지 않고(`hide`·`unhide`는 ArchUnit 규칙으로 report.target만 호출) 내용 결합도는 없다.

| ID | 파일:줄 | 항목 | 현재 코드 한 줄 | 교정 예시 한 줄 | 등급 |
|----|---------|------|-----------------|-----------------|------|
| BE5-B-13 | crew/CrewPostService.java:36(528줄, 생성자 :61 의존 11개), web/crew/CrewDtos.java(992줄) | 한 클래스·한 파일이 너무 많은 일을 한다. CrewPostService는 일곱 종류 글 쓰기·목록·참석 응답·고정·투표 결과·앨범·사진·삭제·신고 연산·투표 알림 배치를 한 클래스에 두고 의존이 11개다(설계 06-04는 9개: NotificationService·WeeklyRaceRepository가 추가됨, U5-1). CrewDtos는 크루 REST 전송 형식 전체를 한 파일에 둔다 | `public class CrewPostService { … 528줄 … }` | 투표(`respondPoll`·`pollResult`·`resultOf`)와 앨범·사진, 신고용 연산(`reportedView`·`hideReported`·`unhideReported`·`remindClosingPolls`)을 `CrewPollService`·`CrewAlbumService`·`CrewPostModeration`으로 나눈다. CrewDtos는 글·일정·투표·앨범·크루 한 곳으로 나눈다 | 경 |
| BE5-B-14 | crew/CrewPostService.java:393-398, crew/CrewNewsNotifier.java:37-42 | 타입 분기. 삭제 뒤 정리를 `instanceof CrewRunPost`·`instanceof BoardPost`로 나누고, 알림 종류를 `switch (post.kind())`로 정하며 "알림 없음"을 `null`로 돌려 다시 `if (type == null)`로 거른다 | `if (post instanceof CrewRunPost run) { crewRunService.endByDeletion(run); }` / `case CHEER, CREW_RUN, BOARD -> null;` | `CrewPost`에 `Optional<NotificationType> newsType()`(기본 빈 값)과 삭제 때 부르는 `onDeleted(PostCleanup cleanup)`을 두어 오버라이딩으로 바꾼다 | 경 |
| BE5-B-15 | crew/CrewPost.java:72-74 | 생성자에서 재정의 가능한 메소드를 부른다. `maxBody()`는 하위 클래스가 상수를 돌려줘 지금은 안전하지만 하위 클래스 필드를 쓰게 바뀌면 초기화 전에 읽는다 | `if (codePoints(text) > maxBody()) { throw … }` | `protected CrewPost(…, int maxBody, …)`로 상한을 인자로 받거나 생성 정적 팩터리에서 검사 | 경 |
| BE5-B-16 | crew/CrewPost.java:106-117, crew/chat/CrewChatMessage.java:142, crew/CrewPermissionPolicy.java:42-44 | 권한 규칙이 두 곳에 있다. 목록의 `canDelete`는 정책 표(`DELETE_ANY_CONTENT`)로 묻는데 실제 삭제는 `relation == LEADER \|\| MANAGER`를 코드로 비교하고(채팅 `isModerator`도 같다), 표의 `DELETE_OWN_CONTENT`·`REPORT` 행은 어디서도 묻지 않는다(신고는 `crewMemberIds`로 따로 판정). 표를 바꾸면 버튼과 동작이 어긋난다 | `} else if (relation == CrewRelation.LEADER \|\| relation == CrewRelation.MANAGER) {` | `delete(requesterId, relation, policy)`에서 `policy.isAllowed(relation, DELETE_OWN/ANY_CONTENT)`로 묻거나, 서비스가 정책 표 결과(SELF·FORCED)를 받아 엔티티는 `markDeleted()`만 한다 | 경 |
| BE5-B-17 | crew/WeeklyRaceRepository.java:36, infra/crew/JpaWeeklyRaceRepository.java:174 | 쓰지 않는 포트 연산. 설계 D-105는 `latestGoalBefore`로 직전 목표를 복사한다고 했지만 구현은 `CREATE_WEEK` 한 SQL(`insert … select … on conflict do nothing`, :24)로 하고 `latestGoalBefore`는 호출·시험 0개다 | `Optional<Distance> latestGoalBefore(long crewId, Week week);` | 연산과 구현을 지우고 설계를 "주 행 생성 SQL이 직전 목표를 복사한다"로 고친다(U5-5) | 경 |

### 5. 동시성·보안·성능 점검

잠금 순서·멱등·고유 색인은 설계(5.3, A-12·A-24·A-25)대로 구현됐고 아래 통과 표에 적었다. 새 결함은 경 3이다.

| ID | 파일:줄 | 항목 | 현재 코드 한 줄 | 교정 예시 한 줄 | 등급 |
|----|---------|------|-----------------|-----------------|------|
| BE5-B-18 | web/chat/ChatController.java:93-107, crew/chat/CrewChatService.java:177·188 | 리액션은 속도 제한이 없다. ChatRateGuard는 보내기만 걸고(:73), 리액션 달기·떼기는 바뀔 때마다 ChatReactionsChanged를 발행해 커밋 뒤 방 안 전원에게 CrewChatReactionChanged를 보낸다(조회 둘 + 소켓 전송). 한 회원이 달았다 뗐다를 반복하면 크루원 10명에게 초당 수십 건이 나간다 | `return ResponseEntity.ok(reaction(chatService.react(memberId, messageId, kind)));` | 같은 `AttemptLimiter` 창(회원당 초당 N건)을 `ChatRateGuard.admitReaction`으로 걸거나 리액션 변경 사건을 메시지당 일정 시간 한 번으로 모은다(D-132 보완) | 경 |
| BE5-B-19 | report/ReportService.java:138-158, crew/CrewPostService.java:229-236 | 목록 N+1. 관리툴 신고 목록 `view`는 사례마다 콘텐츠 조회·사유 집계·기간 질의 3번을 부른다(쪽 크기 최대 100 → 300 질의). 크루 게시 목록의 지난 크루런 기여 거리는 기록 ID마다 질의 1번(`weeklyRaceRepository::contributionOf`, 4차 이전부터 있음, 쪽이 100으로 묶여 상한은 생김) | `Map<ReportReason, Integer> reasons = cases.reasonCounts(c.getId());`(사례마다) | `reasonCounts(Collection<Long> caseIds)`·`reportedPeriods(Collection<Long>)`를 `group by case_id` 한 질의로, `contributionOf(Collection<String> recordIds)`도 `in` 한 질의로. 관리툴이라 급하지 않다 | 경 |
| BE5-B-20 | web/crew/CrewJoinAttemptGuard.java:40 | 초대 코드 실패 제한이 회원 단위다(10분 5회). 코드는 36^6 ≈ 2.2×10^9 가지라 한 회원에는 충분히 막지만, 게스트(v0.11, 계정을 만들기 쉬움)·다중 계정으로 우회하면 제한이 의미 없다. 게스트 생성 제한과 함께 봐야 해서 (가) 묶음의 게스트 시작 속도 제한 결과를 보고 확정한다(확인 필요) | `if (!failures.tryAcquire(key)) {`(key = memberId) | 접속지(web.attempt.ClientAddress)별 한도를 더해 회원·접속지 둘 중 하나가 넘으면 429 | 경 |

점검했으나 결함 아님(통과):

| 점검 요청 | 결과 |
|-----------|------|
| 행 잠금 순서(5.3, A-24) | 신고: report_case 행 → 콘텐츠 행(submit·hide·dismiss 모두). 고정: crew 행 → 글 행(CrewPostService.pin). 일정 응답·삭제·숨김: 글 행만, 크루 읽기는 잠금 없는 `require`. 크루 삭제·탈퇴·강퇴는 crew 행 → 응답 행 SQL. 서로 반대 순서로 잡는 경로가 없어 교착 고리가 없다. 글 행 잠금은 영속성 문맥의 낡은 사본을 떼어 낸 뒤 다시 읽어 낡은 정원으로 판정하지 않는다(JpaCrewPostRepository.java:66-74) |
| 동시 응답·고정·지정 | 운영진 2명(crew 행 잠금 안 managerCount), 고정 3개, 대면 모임 정원, 목표 달성 1회(`markAchievedIfReached`, achieved_at IS NULL 조건부 UPDATE), 합산(`addAtomically` insert on conflict + total 증가)이 CrewV011ThreadIntegrationTest(실 PostgreSQL)로 닫혀 있다 |
| 신고 동시성(A-24) | 새 사례는 `insert … on conflict do nothing` 뒤 다시 읽어 잠금(JpaReportCaseRepository.java:63-81, ReportService.lockCase), 같은 신고자는 `existsReport` + 고유 색인 uq_content_report_reporter 변환(ALREADY_REPORTED), 자동 숨김 전이는 잠근 사례에서 `addReport`가 한 번만 참. ReportThreadIntegrationTest가 동시 첫 신고·동시 3·5명·동시 재신고·운영자 둘의 동시 처리를 본다 |
| 채팅 멱등·방 전송 | 같은 작성자·같은 clientMessageId는 고유 색인 uq_crew_chat_client(V23)와 재조회로 한 건(CrewChatService.post:76-91), 다른 크루 ID 재사용은 400. 전송은 방 안 ∩ 현재 구성원, 방 밖 알림은 실행기+수신자별 새 트랜잭션, 실패는 로그만(ChatDeliveryListener). 읽기는 매번 relationOf로 막아 떠난 회원은 NOT_CREW_MEMBER |
| 사진 업로드·EXIF 제거(NFR-042, D-100, D-142) | 컨트롤러가 5장 초과를 변환 전에 거르고(CrewPostController.java:102), 장당 1MB·요청 6MB(application.yml:51-52), PhotoUpload 순서(크기 → 머리 FF D8 FF → strip), 허용 목록(JFIF·ICC_PROFILE·Adobe APP만, 모르는 마커·APP1·APP13·COM·MPF 제거), 프로그레시브 스캔 사이 제거, EOI 뒤 꼬리 버림, 길이·마커가 깨지면 422. 길이·인덱스 경계(segmentEnd:91-100, hasIdentifier:119-131, scanDataEnd)가 배열 밖을 읽지 않는다. 사진 조회는 글 가시성 → 크루 구성원 → 바이트 순이고 private 5분 캐시, 글 삭제·작성자 탈퇴 때 사진 행 삭제. 보안 결함 0 |
| SQL 조립 | JPQL·native 문자열 조립은 상수 표 이름·enum 단순 이름·정수 인덱스뿐이고 값은 전부 바인드(findVisible, deleteMemberRows). 주입 없음 |
| 마이그레이션 V22~V24 | 값 CHECK(meetup 정원 2~10, 투표 방식·마감, 공지 pinned, 사진 seq 1~5·1MB), FK ON DELETE CASCADE(응답·선택지·사진·채팅·리액션·신고), 부분 고유 색인(uq_crew_chat_client, uq_content_report_reporter, uq_notification_unread_collapse), 조회 색인(crew_id, id DESC)·(status, opened_at)·부분 색인 둘. 탈퇴 익명화가 고유 색인에 걸리지 않게 NULL 행을 뺐다. 실 PostgreSQL 이전 시험 3개(CrewV22·CrewChat·Report MigrationIntegrationTest) |

### 6. 시험 품질

| ID | 파일 | 항목 | 현재 | 교정 예시 | 등급 |
|----|------|------|------|-----------|------|
| BE5-B-21 | crew/chat/CrewChatServiceTest.java:331 (`delete`), web/chat/ChatControllerTest, CrewV011ThreadIntegrationTest | 빠진 시험 셋. (1) 떠난·강퇴된 작성자의 삭제(BE5-B-1)가 사례에 없다. (2) 리액션 폭주(BE5-B-18)·같은 회원의 투표 연속 두 번 누름(서비스 javadoc에 "마지막 응답이 남는다"고 적음)·떠나는 중 일정 응답(검사 뒤 잠금 사이)이 동시성 시험에 없다. (3) 스레드 통합 시험 셋은 `@Testcontainers(disabledWithoutDocker = true)`라 Docker 없는 환경에서는 조용히 건너뛴다(통합 검증이 Docker 환경에서 돌았는지는 implementation.md에 적혀 있지 않다) | `@Testcontainers(disabledWithoutDocker = true)` | CI는 Docker 필수 환경으로 못 박고(건너뜀을 실패로 보는 게이트), 위 세 사례를 시험에 더한다 | 경 |

통과: 이 범위 시험 198개 중 @DisplayName 190개(96%)에 TC 번호가 있어 추적된다. 단위(도메인 CrewPostHierarchyTest 21·CrewDomainTest 20·ReportCaseTest·CrewChatMessageTest)·서비스(InMemory 저장소 + SpringBootTest H2)·계약(V011ContractShellTest, 스냅샷)·구조(ArchFixture)·실 PostgreSQL(스레드·마이그레이션)이 층을 이룬다. @Disabled·Thread.sleep·단언 없는 시험 0개. JpegMetadataStripperTest 9개가 허용 목록·프로그레시브·EOI 꼬리·깨진 구조를 본다.

### 7. 형상 관리

| 활동 | 판정 | 근거 |
|------|------|------|
| 형상 식별 | 예 | 브랜치 feature/runkk-v010, MR !14(Draft, squash), 패키지·V22~V24·계약 파일 위치가 구현 기록(implementation.md 2~3장)에 단계별로 적혀 있다 |
| 버전 제어 | 예 | 기준 d7618cb 뒤 115커밋(병합 14 포함, 작성자 1명). 태그는 0개이고 build.gradle 버전은 0.0.1-SNAPSHOT 그대로(경, 병합 시 태그·버전 갱신 권고) |
| 변경 통제 | 아니오(판정 요인 제외) | 커밋 제목에 Jira 키가 없다(AGENTS.md `{타입}: {내용} [{Jira 키}]`). implementation.md U-04 · code-review N-14 보류 결정 그대로라 판정 요인에서 뺀다. 추가로 병합 14개가 `merge:` 타입(타입 목록 밖) |
| 형상 감사 | 예 | 기준선 d7618cb ↔ bfe55e1c diff로 확인, 단계(S0~S4·G)별 병합 커밋과 push 전 검증 표(implementation.md 6장) |
| 상태 보고 | 예 | 커밋 본문에 변경 이유·결정 번호·TC 번호가 사람이 읽을 수 있게 있다(예: 1a9b10e2 본문 9줄), implementation.md 3장 단계 기록 |

| ID | 항목 | 근거 | 교정 예시 | 등급 |
|----|------|------|-----------|------|
| BE5-B-22 | 커밋은 한 가지 일(L32 유형) | 이 범위 16커밋 중 1a9b10e2는 86파일 +5,781 −462줄에 운영진·게시 계층·사진·공동 목표·소식 알림·삭제 정리·시각 형식·web.attempt 이동을 한 커밋에 담았다(본문이 여덟 항목). 9a80d350(신고, 31파일)·419bdbee(채팅, 31파일)은 한 기능이라 허용. 병합 14개는 `merge:` 타입이라 타입 목록 밖 | 이런 큰 단계는 기능별로 나눠 커밋하고(`feat: 운영진 지정`, `feat: 공동 목표`…), 병합 커밋은 `chore: S3a 병합`처럼 목록 안 타입으로 쓴다. MR은 squash라 병합 때 제목을 `feat: … [키]` 한 줄로 정리 | 경 |

### 8. 집계와 판정

| 구분 | 치명 | 중 | 경 | 구성 |
|------|------|----|----|------|
| 새 결함 | 0 | 6 | 16 | 중: BE5-B-1(채팅 삭제 권한)·BE5-B-2~5(매개변수 5개 이상 7곳, 4건)·BE5-B-6(JpegMetadataStripper 다중 출구). 경: BE5-B-7~22 |
| 4차 잔여 | 0 | 0 | 1 | L32(커밋 규약)가 BE5-B-22로 이어짐, L3·L4·L13·L31은 해결 |
| 합계 | 0 | 6 | 16 | L32는 BE5-B-22에 포함해 셌다 |

판정: **조건부**
근거: 치명 0, 중 6(4~10). 보안·동시성의 핵심(EXIF 제거 허용 목록, 신고·채팅·일정 응답의 잠금·멱등·고유 색인, 숨김·삭제·탈퇴 규칙)은 설계와 맞고 실 PostgreSQL 시험이 받친다. 중 6건 중 설계와 다른 동작은 채팅 삭제 권한 1건이고 나머지 5건(매개변수 7곳, 다중 출구 1곳)은 기계적으로 고친다. 형상 관리는 변경 통제 한 항목이 N-14 보류라 요인에서 빼면 5항목 모두 예다. 중 6건을 고치면 합격 조건(중 3 이하)에 닿는다. 매개변수 5개 이상은 3차 B-M1~M3와 같은 유형이라 구현 단계 린트 규칙(예: Checkstyle ParameterNumber 4)을 걸어 재발을 막기를 권한다.

### 9. sw-test 전달: 우선 테스트 대상

순환 복잡도 10 초과 함수(약산):

| 함수 | 약산 복잡도 | 이유 |
|------|-------------|------|
| crew.JpegMetadataStripper.strip | 16 | 업로드 보안. 분기 경로: SOI 아님, 마커 아님, 채움 FF, EOI(SOS 전·후), 길이 없는 마커(TEM·RST), 허용·비허용 세그먼트, SOS 뒤 영상 자료, EOI 없이 끝남. 실제 카메라 JPEG(EXIF GPS·썸네일 포함)와 프로그레시브 표본 |
| crew.PollDraft 생성자 | 16 | 질문·방식·마감·선택지 개수·길이 경계(2·5개, 40자, 200자, 코드 포인트), YES_NO + 선택지 |

우선 테스트(복잡도는 낮지만 위험한 경로):

| 함수·경로 | 약산 복잡도 | 이유 |
|-----------|-------------|------|
| crew.chat.CrewChatService.delete | 4 | BE5-B-1: 떠난·강퇴된 작성자, 숨김 중, 크루장·운영진 감사 |
| crew.CrewPostService.pin / unhideReported / writeNotice | 7 | 고정 3개 경계, 재고정 멱등, 숨긴 고정 공지 해제 때 고정 풀기, 크루 → 글 잠금 순서 |
| crew.MeetupPost.answer / CrewRunPost.answer | 5 | 정원 경계(본인 제외 GOING), 일정 시각·마감 시각 경계(now ≥), 작성자 자동 참석 |
| crew.CrewPostService.remind / remindClosingPolls | 7 | 마감 now~+1시간 경계, reminded 중복, 숨김·삭제 투표 제외, 투표 하나의 실패가 다른 것을 막지 않음 |
| report.ReportService.submit | 9 | 숨김·타인 크루·본인 콘텐츠·기각 뒤 재신고·동시 3명, ALREADY_REPORTED 변환 |
| crew.WeeklyRaceService.setGoal / judgeGoal / reconcile | 8 | 목표 1km 경계, 정한 즉시 달성, 이미 달성한 주 재판정 없음, 직전 목표 이어 쓰기(CREATE_WEEK) |
| crew.CrewWithdrawalListener.leaveCrew | 6 | 크루장 자동 양도 후임(운영진 우선·가입일·ID), 혼자 남은 크루 삭제, 응답 정리 |
| web.crew.CrewJoinAttemptGuard.guard | 5 | 동시 20건 중 5건만 통과, 성공 때 초기화, 다른 사유 실패는 횟수에서 되돌림 |

### 10. 설계서 갱신 요청 (코드가 옳거나 설계 공백, `/sw-design-oo` 재호출 목록)

implementation.md 3장의 결정(4장 결정 목록은 비어 있음)을 설계서 갱신 요청으로 분류했다. 5차 ID는 U5-n이고 종합 결과서에서 CR5-nn으로 이어 번호를 매긴다.

| ID | 대상 절 | 내용 | 비고 |
|----|---------|------|------|
| U5-1 | 06-04 CrewPostService 의존 | 의존에 NotificationService(크루런 모집 알림, writeCrewRun)·WeeklyRaceRepository(지난 크루런 기여 거리) 추가. 생성자 의존 11개. BE5-B-13으로 나눌 때 함께 다시 쓴다 | 코드가 사실, 낡은 설계 |
| U5-2 | D-142, 06-04 JpegMetadataStripper | 허용 목록 방식: JFIF·ICC_PROFILE·Adobe 식별자 APP만 남기고 모르는 마커·APP1·APP13·COM·MPF 제거, 프로그레시브 스캔 사이 제거, EOI 뒤 버림, 마커가 없는 SOS 없음은 422. 현재 문장은 "SOS 전까지 APP1·APP13·COM만 뺀다" | 코드가 더 엄격(G 단계 구현 결정) |
| U5-3 | D-100 (4), F.2.2 | 게시 사진 캐시 private 5분(설계 1일). 신고 숨김·삭제 뒤 기기에 남는 시간을 줄임 | 구현 결정(제안 값) |
| U5-4 | 06-04 answerAttendance | 검사 순서: 글 없음·숨김 → check(RESPOND) → 일정이 아니면 TARGET_INVALID(설계는 TARGET_INVALID가 먼저). 비구성원에게 글 종류를 알리지 않으려는 구현 결정 | 코드가 낫다 |
| U5-5 | D-105, 06-04 WeeklyRaceService·WeeklyRaceRepository | 직전 목표 이어 쓰기는 `CREATE_WEEK` 한 SQL(insert … select … on conflict do nothing)이고 `latestGoalBefore`는 쓰지 않는다(BE5-B-17에서 연산 삭제). setGoal은 `findOrCreate`·`saveGoal`(목표 열만 UPDATE) | 코드가 사실 |
| U5-6 | 06-04 CrewPostRepository, 5.3 | `findByIdForUpdate`(글 행 PESSIMISTIC_WRITE, 영속성 문맥 사본을 떼어 낸 뒤 재조회), `countPinnedNotices`·`findClosingPolls`·`findActiveCrewRunsOf`·`findPhoto`·`findAlbum`·`deleteOpenResponses`·`deleteResponsesByMember` 시그니처. 일정 응답·투표 응답·삭제는 낙관적 잠금 재시도(3회)에 더해 글 행 잠금을 쓴다(implementation 3.9 "글 행 비관적 잠금 + 재시도") | 설계는 낙관적 잠금만 적음 |
| U5-7 | D-102, 5.3 채팅 | 방 밖 알림은 `applicationTaskExecutor`로 넘겨 수신자마다 새 트랜잭션에서 보낸다(순서 비보장, 요청 스레드가 연결 둘을 쥐지 않음). 묶음 알림 중복은 부분 고유 색인 uq_notification_unread_collapse(V23)가 막는다 | 구현 결정(implementation 3.7) |
| U5-8 | 05-03 5.7.1, F.2.0 | 크루 밖 쓰기 오류 코드 통일: 게시글·일정·투표는 403, 채팅은 409 NOT_CREW_MEMBER로 갈린다. 한쪽으로 정하거나 두 쪽을 표에 그대로 적는다(implementation 3.9가 "설계 통일 요청"으로 남김) | 구현 기록의 요청 |
| U5-9 | 05-03 5.7.1 DELETE /chat-messages/{id}, 06-04 CrewChatMessage.delete | `delete`의 "작성자 본인 또는 L·M"을 "현재 구성원 중 작성자 본인 또는 L·M"으로 고친다(BE5-B-1과 함께). 3.0.3 DELETE_OWN_CONTENT와 같은 표현으로 | 설계 공백, 코드도 고침 |
| U5-10 | F.2.2 신고 목록, 06-05 ReportAdminController | GET /admin/reports size 없을 때 기본 30(상한 100), 범위 밖 400. 신고 목록 크기를 G 단계에서 정했다 | 구현 결정 |
| U5-11 | 06-04 CrewPermissionPolicy | `table()`을 시험·점검용 패키지 전용으로 둔다. BE5-B-16 처리 결과(삭제 판정을 정책 표로 옮기는지)를 반영 | 코드 조정 뒤 |
| U5-12 | 06-04 PostSummary·RelayEntry | PostSummary가 종류별 정보 5개(crewRun·meetup·notice·poll·photoIds)를 한 record에 둔다. RelayEntry `mine`(D-161) 추가와 보조 생성자. BE5-B-2·5 처리 뒤의 시그니처로 | 코드 조정 뒤 |

### 11. 다음 행동

- [ ] BE5-B-1: CrewChatService.delete에서 `requireMember` 사용(떠난·강퇴 작성자 거부), 시험 추가, 설계 U5-9 반영
- [ ] BE5-B-2~5: 매개변수 5개 이상 7곳을 Draft·record·열거형으로 4개 이하로(PostSummary.of, WeeklyRace.restore, deleteMemberRows, 도메인 생성자 4곳)
- [ ] BE5-B-6: JpegMetadataStripper.strip 단일 출구 분리(동작·시험 9개 그대로)
- [ ] 경 16건(BE5-B-7~22): 시간대 상수·미리보기·탈퇴자 이름 사본 합치기, 람다 중첩, stale 주석, 리액션 속도 제한, 큰 클래스·파일 나누기, 시험 3종 추가, 커밋 분리 습관
- [ ] 설계서 갱신: `/sw-design-oo`로 U5-1~U5-12 반영
- [ ] MR 병합 전: 태그·버전 갱신(0.0.1-SNAPSHOT), 제목 `feat: … [키]` 한 줄로 squash 정리(N-14 결정 뒤)

## 리뷰 2026-10-23 (6차, 백엔드)

대상 커밋: S15P31A207-v010 `feature/runkk-v010` a27c914f(기준 4ee91aef). 범위는 `git diff 4ee91aef a27c914f -- backend infra`, 74파일 +746 −289(운영 코드 51파일 +459 −241, 시험 20파일, infra 5파일 + develop 병합이 가져온 `infra/nginx/https/wiki.conf.template` 73줄)이다. 범위 안 서버 커밋은 병합 제외 26개(5차 결함 수정 24 + compose `AVATAR_BACKFILL_CUTOFF` 1 + develop의 위키 nginx 1)다. 앱 쪽 커밋(AN5-*)은 이 결과서의 범위가 아니다.

입력: 위 코드, docs/sw/design-oo/(v0.11, 05-02 D-167~D-189 포함, 5차 리뷰 갱신 요청 U5-A-1~10·U5-B 반영본), 5차 결과서(위 5차 (가)·(나) 절), implementation.md 10절 "팀이 할 일".
방법: 읽기 전용. 변경 diff 전체(운영·시험)를 읽고, 5차 결함 40건의 현재 코드를 grep·파일 읽기로 다시 확인했다. 변경 파일의 매개변수·복잡도 약산은 직접 계산했고(백엔드 전체 5개 이상 매개변수 목록도 뽑아 새로 생긴 것이 없음을 확인), `.gitlab-ci.yml`로 배포 경로를 대조했다. 빌드·시험은 돌리지 않았다(통합 브랜치의 push 전 검증을 믿는다).

### 리뷰 기준

5차와 같다(매개변수 4 이하, 중첩 3 이하, 함수 50줄 이하, 복잡도 10 이하, 설계 12.2 의존 표, 판정 기준 합격/조건부/불합격). 등급은 치명(goto 등가·내용/공통 결합·전역 가변 상태·설계에 없는 양방향 의존) / 중(데이터 손실·설계 불일치·규칙 위반·안전장치 무력화) / 경. Jira 키 없음은 N-14 보류(U-04)라 판정 요인에서 뺀다.

### 5차 결함 추적

상태: 해결 / 일부 / 미해결 / 보류(사유). 근거는 a27c914f 기준 파일:줄이다.

#### (가) BE5-A-1~18

| ID | 등급 | 상태 | 근거 |
|----|------|------|------|
| BE5-A-1 | 중 | 해결 | member/Member.java:112 생성자 인수 4개, accountType을 `account == null ? GUEST : LINKED`로 정함(:118). signUp·guest 두 팩터리 호출부 모두 맞음. 시험 변경 없이 MemberGuestV011Test 회귀가 받친다 |
| BE5-A-2 | 경 | 해결(새 결함 BE6-4·5) | realtime/LiveSocketHandler.java:140-170, ChatPresenceRegistry.java:20-55. 같은 세션의 같은 방은 한 번만 확인, 서로 다른 방 10개까지(D-173). DB 읽기는 세션당 10번으로 막힌다. 처음 넣은 초당 5회 제한(fb2f4722)은 77a2fbcb가 방식을 바꿔 대체했다. 시험 ChatRealtimeTest 둘 |
| BE5-A-3 | 경 | 해결 | running/RunUpload.java:89-132. requireValidPauses(2) → requireAllowed(3)·requireOrderedWithin(8)로 나눔, 모두 10 이하 |
| BE5-A-4 | 경 | 해결(새 결함 BE6-6) | avatar/BackgroundService.java:96-120, BackfillProperties, RunQueryService.validDistanceBefore, JpaRunRecordRepository.validDistanceSavedBefore(저장 일시 < cutoff인 유효 기록). 첫 랜드마크 알림을 리스너가 낸다. BackgroundServiceTest 새 시험(배포 전 거리만 셈), TC-6763을 "알림 정확히 1"로 강화 |
| BE5-A-5 | 경 | 해결 | member/AuthService.java:162-166 자기 자신이 찾은 계정이면 건너뛰어 ALREADY_LINKED. GuestServicesTest에 재시도 사례 추가. D-177 (2)와 일치 |
| BE5-A-6 | 경 | 미해결 | member/AuthService.java:217 여전히 메시지 문자열에서 제약 이름을 찾는다. 이번 수정 목록에 없음. JpaReportCaseRepository:156도 SQLSTATE와 메시지를 함께 본다. 판정 방식이 셋으로 갈린 채 |
| BE5-A-7 | 경 | 해결 | BackgroundService.java:228 `attempt < MAX_ATTEMPTS`를 헤더에 두고 마지막 시도는 루프 밖 `return work.get()`(예외를 그대로 올림) |
| BE5-A-8 | 경 | 해결 | running/TierApplier.java:33 `void apply`. 호출부(TierUpdateListener:44 executeWithoutResult, TierReconcileService:81)와 시험(TierReconcileServiceTest:85) 정리 |
| BE5-A-9 | 경 | 미해결 | 이번에 지운 것은 반환값·인수뿐(1e7f80de). `validRunsAfter`(RunQueryService·JpaRunRecordRepository:360), RewardAccrual.isFor, Notification.collapse, TagOwner 한 상수가 그대로다. D-169가 "validRunsAfter는 지운다"고 적었으므로 설계와 코드가 어긋난 채다 |
| BE5-A-10 | 경 | 미해결 | notification/NotificationService.notifyCollapsing이 notify와 20줄을 반복하는 구조 그대로 |
| BE5-A-11 | 경 | 해결 | Avatar.selectBackground(String)·useDefaultBackground(), BackgroundService.choose·useDefault, AvatarController:115 분기. D-176 (4)와 일치 |
| BE5-A-12 | 경 | 해결 | common/ServerZone.ID 한 곳. 운영 코드의 `ZoneId.of("Asia/Seoul")`은 ServerZone 하나뿐(grep). 시계 빈·스케줄 설정 8개·직렬화·DTO가 같은 값을 쓴다. 각 파일의 `SEOUL = ServerZone.ID` 별칭은 남았으나 출처는 하나다 |
| BE5-A-13 | 경 | 해결(시험 공백 BE6-8) | operator/OperatorBootstrap.java:73-84 화이트리스트 검사를 빈 값 검사보다 먼저. D-175 (1)과 일치 |
| BE5-A-14 | 경 | 해결 | application.yml:29-43 주석에 사용처 추가, core 4 · max 8 · queue 500. BootstrapConfigTest도 맞춤 |
| BE5-A-15 | 경 | 해결(새 결함 BE6-1~3) | infra/deploy.sh:53-75 `backup_database`, 실패하면 배포 중단(pipefail), README·.gitignore. 그러나 보존 위치·권한·건너뜀 조건에 결함이 있다 |
| BE5-A-16 | 경 | 보류(D-177 "보류: findIdleGuests를 ID만 읽기") | WithdrawalService.java:153 `map(Member::getId)` 그대로. 사유는 설계에 기록됨(S1-3). 첫 실행 한 번의 부하라 수용 |
| BE5-A-17 | 경 | 보류(D-177 "기본 닉네임 동시 경합 재시도") | SignUpService.java:166 확인과 저장 사이 경합 그대로 |
| BE5-A-18 | 경 | 해결 | ReasonCode.java:169 주석 교정 |

(가) 집계: 중 1건 해결. 경 17건 중 해결 12, 보류 2(A-16·17), 미해결 3(A-6·9·10). 합계 해결 13, 보류 2, 미해결 3.

#### (나) BE5-B-1~22

| ID | 등급 | 상태 | 근거 |
|----|------|------|------|
| BE5-B-1 | 중 | 해결 | crew/chat/CrewChatService.java:152 `requireMember`로 먼저 막아 떠난·강퇴된 작성자는 NOT_CREW_MEMBER(409, D-167 (2)). CrewChatMessage.delete는 권한표(DELETE_OWN/ANY, D-188). 시험 CrewChatServiceTest.delete_byFormerMember_isRejected(저장 안 바뀜·사건 0). 게시글 쪽은 CrewPost.delete가 OUTSIDER를 표로 거부(403) |
| BE5-B-2 | 중 | 해결 | crew/PostSummary.java:41 `of(PostHeader, boolean)` 2개. PostHeader 5구성요소 record |
| BE5-B-3 | 중 | 해결 | crew/WeeklyRace.java:60 `restore(long, Week, GoalState)` 3개, JpaWeeklyRaceRepository·시험 맞춤 |
| BE5-B-4 | 중 | 해결 | infra/crew/JpaCrewPostRepository.java:236-260 열거형 OpenResponse, `deleteMemberRows` 4개 |
| BE5-B-5 | 중 | 해결 | SchedulePost(crewId, authorId, Slot, now) 4, CrewChatMessage(…, ChatDraft, at) 4, ContentReport(caseId, reporterId, Content, at) 4, RelayEntry 보조 생성자 삭제(호출부가 false를 명시) |
| BE5-B-6 | 중 | 해결 | crew/JpegMetadataStripper.java:44-160. strip(복잡도 7)이 step(약 6)·copySegment·dataSkip으로 나뉘고 한 곳에서 반환. 반복 안 return·continue 없음. 동작 대조: SOS 없이 끝남·EOI가 SOS보다 먼저·EOI 뒤 꼬리 버림·영상 자료로 끝남 모두 이전과 같다. JpegMetadataStripperTest 9개 그대로 통과 대상 |
| BE5-B-7 | 경 | 해결 | ServerZone. CrewDtos:65, ReportAdminController:37, CrewSchedulingConfig:26 |
| BE5-B-8 | 경 | 미해결 | `DisplayName.WITHDRAWN` 대체 여섯 곳과 PREVIEW_LENGTH 두 벌(CrewPostService:45, CrewChatService:41) 그대로 |
| BE5-B-9 | 경 | 미해결 | CrewPostService.resultOf·CrewDtos.PollResultResponse 3겹 람다 그대로 |
| BE5-B-10 | 경 | 해결 | JpaReportCaseRepository.java:142-157 `Stream.iterate(...).anyMatch`. null 제약 이름은 mentionsReporterUnique가 막는다 |
| BE5-B-11 | 경 | 해결 | CrewPostController.java:179 "private 캐시 5분" |
| BE5-B-12 | 경 | 일부 | `actorId` 매개변수는 지움(ReportAdminController:50). `ReportService.cases(Optional<ReportStatus>, …)`는 그대로(ReportService.java:130, 시험도 `any(Optional.class)`) |
| BE5-B-13 | 경 | 미해결 | CrewPostService 528줄·의존 11개, CrewDtos 993줄 그대로 |
| BE5-B-14 | 경 | 미해결 | CrewPostService:393·396 `instanceof`, CrewNewsNotifier switch 그대로 |
| BE5-B-15 | 경 | 미해결 | CrewPost.java:76 생성자에서 `maxBody()` 호출 그대로 |
| BE5-B-16 | 경 | 일부 | 삭제 판정은 권한표에 묻는다(D-188). REPORT 행은 여전히 어디서도 묻지 않고 신고는 crewMemberIds로 따로 판정. 표를 가리키는 방식이 새 결함(BE6-7)을 낳음 |
| BE5-B-17 | 경 | 미해결 | WeeklyRaceRepository.latestGoalBefore(:36)·JpaWeeklyRaceRepository:175 쓰이지 않음 그대로. 설계(02 동적 모델 1215·1796, 03 기능 모델 100)는 아직 이 연산을 쓴다고 적었다(U5-5 갱신 필요) |
| BE5-B-18 | 경 | 해결 | web/chat/ChatRateGuard.java 리액션 한도 10/초(MAX_REACTIONS), ChatController:92-107. 시험 ChatRateGuardTest·ChatControllerTest(BE6-9 참고) |
| BE5-B-19 | 경 | 미해결 | ReportService.view 사례별 질의 3번, contributionOf 기록별 질의 그대로 |
| BE5-B-20 | 경 | 미해결 | CrewJoinAttemptGuard 회원 단위만. (가)의 게스트 생성 제한(10/시간/접속지)을 봤으나 확정 결정이 없다 |
| BE5-B-21 | 경 | 일부 | 떠난 작성자 삭제·리액션 429 시험은 더함. 투표 연속 두 번·떠나는 중 일정 응답 동시성 시험과 Docker 없으면 건너뛰는 통합 시험(`disabledWithoutDocker` 33곳)의 게이트는 그대로 |
| BE5-B-22 | 경 | 일부 | 커밋이 작아졌다(범위 26개 중 최대 15파일). `merge:` 타입 3개와 GitLab 병합 1개는 타입 목록 밖 그대로 |

(나) 집계: 중 6건 모두 해결. 경 16건 중 해결 4(B-7·10·11·18), 일부 4(B-12·16·21·22), 미해결 8(B-8·9·13·14·15·17·19·20). 합계 해결 10, 일부 4, 미해결 8.

#### 요약

| 구분 | 해결 | 일부 | 보류 | 미해결 | 합계 |
|------|------|------|------|--------|------|
| 중 (A-1, B-1~6) | 7 | 0 | 0 | 0 | 7 |
| 경 (가) | 12 | 0 | 2 | 3 | 17 |
| 경 (나) | 4 | 4 | 0 | 8 | 16 |
| 합계 | 23 | 4 | 2 | 11 | 40 |

고쳤다고 보고된 항목(A-2·3·4·5·7·8·11·12·13·14·15·18, B-7·10·11·12·16·18)을 하나씩 확인했다. 이 중 B-12·B-16은 보고된 만큼 다 고쳐지지 않았다(Optional 인수 유지, REPORT 행 미사용). A-2·4·13·15는 고쳤으나 아래 새 결함이 딸려 있다.

### 1. 설계 일치

| 점검 | 결과 |
|------|------|
| 의존 방향 | 새 화살표 없음. crew.chat → crew(CrewAction·CrewPermissionPolicy)는 기존 N11 방향 안이고 avatar → running(validDistanceBefore)도 기존 D 화살표다. D-173은 AttemptLimiter(realtime → web.attempt) 대신 realtime 안 규칙을 골라 화살표를 만들지 않았다 |
| D-167 | 채팅 삭제는 NOT_CREW_MEMBER(409), 글 삭제는 PERMISSION_DENIED(403)로 갈려 있다. 코드와 일치 |
| D-169 | TierApplier 일치. "validRunsAfter는 지운다"는 코드에 남아 있어 불일치(BE5-A-9에 합쳐 센다) |
| D-173 | 확인한 방(통과·거부)을 세션이 닫힐 때까지 기억, 서로 다른 방 10개, 넘으면 버리고 로그. 일치. 전송 대상은 "방 안 ∩ 현재 구성원"(ChatDeliveryListener:156)이라 통과 캐시가 떠난 회원에게 전송하지 않는다는 근거도 코드로 확인했다 |
| D-175 | (1)·(3)·(4) 일치(WARN 순서, core 4·max 8·queue 500, 풀 20). (2)는 불일치: 설계는 "배포 묶음 체크리스트, 파일 이름에 배포 커밋"이고 거부안으로 "배포 스크립트가 자동 덤프"를 적었는데 코드는 그 거부안을 구현했고 파일 이름은 시각이다. 코드가 낫다고 볼 수 있어 설계서 갱신 요청(U6-1)으로 분류하고 implementation.md 10절의 수동 항목도 고친다 |
| D-176 | (2)·(4) 일치. 설정 이름 `runkk.avatar.backfill-cutoff`·환경 변수 `AVATAR_BACKFILL_CUTOFF`는 설계에 없다(U6-3) |
| D-177 (2) | 일치 |
| D-188 | 삭제는 권한표를 따르고 OUTSIDER는 작성자여도 거부. 일치. 구현 방식(엔티티 안 `new CrewPermissionPolicy()`)은 BE6-7 |
| 설계에 없는 이름 | common.ServerZone, WeeklyRace.GoalState, SchedulePost.Slot, ContentReport.Content, avatar.BackfillProperties, ChatRateGuard.admitReaction(리액션 10/초), CrewPost/CrewChatMessage의 `POLICY`. 설계서 갱신 요청(7절) |
| 설계 문서 내부 모순 | 05-02 5.4 게시 사진 문단은 `max-age=86400`, D-174 (2)는 300초(코드 300). 설계 쪽 정리 필요(U6-4) |

### 2. 새 결함

새 결함은 치명 0, 중 1, 경 8이다. 줄 번호는 a27c914f 기준이다.

| ID | 파일:줄 | 항목 | 현재 코드 한 줄 | 교정 예시 한 줄 | 등급 |
|----|---------|------|-----------------|-----------------|------|
| BE6-1 | infra/deploy.sh:63-71, .gitlab-ci.yml:174-196 | 배포 전 백업이 CI 작업 폴더 안 `infra/backups/`에 쌓인다. backend:deploy는 shell 러너가 같은 작업 폴더를 재사용하고(after_script가 `.env`를 지우는 이유) `.gitlab-ci.yml`에 `GIT_CLEAN_FLAGS`·`GIT_STRATEGY` 재정의가 없어 러너 기본 `git clean -ffdx`가 다음 잡 시작 때 .gitignore에 든 `backups/`까지 지운다(러너 설정을 못 봤으므로 확인 필요). 그러면 "최근 7개 유지"는 성립하지 않고, 되돌릴 수 없는 V20 이전 덤프는 다음 파이프라인이 돌면 사라진다. 5차 BE5-A-15가 지키려던 안전장치가 한 번의 푸시로 무력해진다 | `mkdir -p backups` / `file="backups/pre-deploy-$(date +%Y%m%d-%H%M%S).sql.gz"` | 작업 폴더 밖 고정 경로를 쓴다: `.env`에 `BACKUP_DIR`(기본 `/var/backups/runkk`, 호스트 디렉터리)을 두고 `mkdir -p`·`chmod 700`, 또는 CI 변수 `GIT_CLEAN_FLAGS: -ffdx -e infra/backups/`. 파일 이름에 `git rev-parse --short HEAD`도 붙인다(D-175) | 중 |
| BE6-2 | infra/deploy.sh:63-69 | 덤프 파일 권한이 umask를 따른다(보통 644, 폴더 755). 덤프에는 닉네임·러닝 경로(위치)·FCM 토큰·게스트 자격 증명 해시·갱신 토큰 해시가 모두 든다. 같은 호스트의 다른 계정(러너 사용자 외 docker 그룹 포함)이 읽을 수 있고, 암호화도 호스트 밖 사본도 없다(디스크가 사라지면 DB 볼륨과 함께 잃는다). `pg_dump`에 비밀번호를 인수로 주지 않고 컨테이너 소켓을 쓰므로 명령줄 노출은 없다(확인함) | `compose exec -T postgres pg_dump -U "$db_user" "$db_name" \| gzip > "$file"` | `(umask 077; ... > "$file")`와 `install -d -m 700 "$BACKUP_DIR"`, 가능하면 `gpg --symmetric` 또는 호스트 밖 복사 한 줄 | 경 |
| BE6-3 | infra/deploy.sh:56-59 | 건너뜀 조건이 "postgres가 실행 중이 아님"이다. `docker compose down` 뒤나 postgres가 재시작 중일 때도 데이터 볼륨(`postgres-data`)이 있는데 "처음 배포"로 보고 백업 없이 api를 올려 V20 같은 DROP COLUMN을 돌린다. 실행 중이 아닌 것과 데이터가 없는 것은 다르다 | `if [ -z "$(compose ps --status running --quiet postgres)" ]; then echo "... skipping"; return 0` | 볼륨이 있는데 postgres가 내려가 있으면 `compose up -d postgres`와 `wait_healthy postgres 12` 뒤 덤프한다. 볼륨도 없을 때만 건너뛴다(`docker volume inspect "${COMPOSE_PROJECT_NAME:-infra}_postgres-data"`) | 경 |
| BE6-4 | realtime/ChatPresenceRegistry.java:34-55, LiveSocketHandler.java:144-160 | 거부 결과(`false`)를 세션이 닫힐 때까지 기억한다(D-173이 정한 바). 한 번 거부된 방은 그 사이 크루에 (재)가입해도 같은 연결에서 입장 신호가 영영 무시되어 그 방의 실시간 신호(CrewChatPosted 등)가 오지 않고 방 안인데도 방 밖 푸시 알림만 온다. 앱 연결은 오래 유지되므로 몇 시간 이어질 수 있다. 반대 방향(통과 뒤 강퇴된 세션의 재입장)은 전송 대상이 현재 구성원과의 교집합이라 무해함을 확인했다 | `chatPresence.markChecked(sessionId, crewId, false);` | 거부는 기억하지 않고 "확인 횟수"만 센다(방 10개 한도 + 거부 재시도는 같은 방당 분당 1회), 또는 크루 가입 사건(MemberJoined)에서 그 회원의 거부 기록을 지운다 | 경 |
| BE6-5 | realtime/LiveSocketHandler.java:150-153 | 방 10개를 넘은 입장 신호는 프레임마다 INFO 한 줄을 남기고(`chat_presence_dropped`) 프레임 속도 제한이 없다. fb2f4722의 초당 5회 제한을 같은 시리즈의 77a2fbcb가 되돌리면서 확인된 방의 반복 신호·초과 신호 모두 무제한이 됐다. DB 읽기는 막혔지만 인증된 연결(게스트는 접속지당 시간당 10개 생성) 하나가 1KB 프레임을 쏟아 로그 디스크를 채울 수 있다 | `log.info("chat_presence_dropped memberId={} reason=too_many_rooms", memberId);` | 세션당 첫 한 번만 기록(`registry.markDropLogged(sessionId)`), 같은 세션이 초과 신호를 50번 넘기면 1008로 닫는다 | 경 |
| BE6-6 | application.yml:83, infra/docker-compose.yml:61, infra/.env.example:22, avatar/BackfillProperties.java:15 | 배포 전/후를 가르는 기준 시각이 과거 고정 값 `2026-10-22T00:00:00`으로 세 곳에 복제돼 있고 `backend/.env.example`에는 없다. 검증은 null뿐이다. 값을 안 바꾸고 배포하면 기준~배포 사이(예: 열흘)에 쌓인 거리를 채우기가 세지 않아 해당 회원은 다음 기록 때까지 "잠김"으로 보이고(0시 10분 보정은 최근 2일 기록 회원만, D-139), 다음 기록의 알림에 오래전에 넘은 랜드마크가 한꺼번에 실린다. 반대로 기준이 미래면 BE5-A-4가 되살아난다. 실제 적용 시각은 `flyway_schema_history`(V25 installed_on)에 이미 있다 | `public BackfillProperties { if (backfillCutoff == null) { throw ...` | 기동 때 `backfillCutoff.isAfter(now)`면 실패시키고, 기본값을 지우거나 V25 `installed_on`을 읽어 쓴다. 최소한 `.env.example` 한 곳만 남기고 compose 기본값은 비운다 | 경 |
| BE6-7 | crew/CrewPost.java:30, crew/chat/CrewChatMessage.java:35 | 권한표(CrewPermissionPolicy)는 Spring 빈인데 엔티티 두 개가 각자 `new CrewPermissionPolicy()`를 정적 필드로 만든다. 표가 불변이라 지금은 같지만, 표를 설정·운영 값으로 바꾸거나 빈을 바꾸는 날 엔티티 사본이 따로 논다(BE5-B-16이 막으려던 "규칙이 두 곳"이 인스턴스 세 개로 옮겨 감). 5차 제안은 정책을 인수로 받는 방식이었다 | `private static final CrewPermissionPolicy POLICY = new CrewPermissionPolicy();` | `delete(long requesterId, CrewRelation relation, CrewPermissionPolicy policy)`로 서비스가 빈을 넘기거나, 정책에 `static final CrewPermissionPolicy INSTANCE` 하나를 두고 빈도 그것을 쓴다 | 경 |
| BE6-8 | 시험: avatar·operator·infra.running | (1) `JpaRunRecordRepository.validDistanceSavedBefore`(저장 일시 `<` cutoff, 유효 기록만, 경계 같은 시각)를 실제 DB로 검사하는 시험이 없다. 시험 대역 InMemory 복사본과 목(mock)만 있다. (2) BE5-A-13 수정(화이트리스트가 이미 있으면 빈 값이어도 로그 없음, INFO `skipped=whitelist_present`)을 고정하는 시험이 없다(OperatorSeedLogTest 변경 0, 기존 사례는 화이트리스트가 빈 상태). (3) BackgroundServiceTest `total()`이 전체 거리와 배포 전 거리를 같은 값으로 묶어 새 시험 하나만 둘을 구분한다. 5차 7절이 "OperatorSeedLogTest 확장"을 요구했다 | `when(runs.validDistanceBefore(memberId, CUTOFF)).thenReturn(Distance.ofMeters(meters));`(total 안) | RunRecord 저장소 PostgreSQL 시험에 cutoff 직전·같은 시각·직후·무효 기록 4건 추가, OperatorSeedLogTest에 "빈 값 + 화이트리스트 있음 → WARN 0, INFO 1" 추가, `total`을 두 인수로 나눔 | 경 |
| BE6-9 | web/chat/ChatControllerTest.java 새 시험, infra/deploy.sh | (1) reactionRateLimit이 실제 시계의 1초 창에 기댄다. 40번 호출이 느린 CI에서 1초를 넘으면 10건 한도에 닿지 않아 `limited`가 거짓이 된다(불안정 시험). 같은 한도를 ChatRateGuardTest가 움직이는 시계로 이미 검증하므로 컨트롤러 시험은 연결만 보면 된다. (2) 새 `backup_database`(건너뜀·실패 중단·보존 개수)를 확인하는 시험이나 실행 기록이 없다. 새 셸 로직이 시험 0이다 | `for (int i = 0; i < 40 && !limited; i++) {` | 컨트롤러 시험은 `MovableClock`을 주입하거나 `@MockBean ChatRateGuard`로 429 변환만 확인한다. deploy.sh는 `bats` 또는 compose 없는 가짜 `docker` 함수로 세 갈래를 시험한다 | 경 |

#### 이전 결함이 새 코드에서 어떻게 보이는가(참고)

- 핵심 재발 방지 확인: 새 코드에서 매개변수 5개 이상인 일반 메소드·생성자(record 구성요소·생성자 주입 제외)는 없다. BackgroundService 생성자 6개는 주입이라 예외다.
- 함수 길이: 변경 함수 중 가장 긴 것은 JpegMetadataStripper.strip 23줄, applyChatPresence 29줄로 모두 50줄 이하다.
- 중첩: 새 함수의 제어문 중첩은 2 이하다(applyChatPresence if → if).

### 3. 구조적 프로그래밍

goto 등가 없음. 반복 안 return은 BackgroundService.retrying 한 곳으로 줄었다(루프 헤더에 종료 조건, 마지막 시도는 루프 밖에서 예외 전파). JpaReportCaseRepository의 반복 안 return 둘과 JpegMetadataStripper의 반복 안 return·continue는 제거됐다. 이번 범위의 복잡도 10 초과 함수는 없다(최대 RunUpload.requireOrderedWithin 8, applyChatPresence 7, JpegMetadataStripper.strip 7).

### 4. 객체지향 구현과 동시성·보안 점검

| 점검 | 결과 |
|------|------|
| 캡슐화 | public 필드·검사 없는 setter 없음. 새 record(PostHeader·GoalState·Slot·Content)는 불변 값이다. `Slot`은 `protected record`라 서브클래스(같은 패키지)에서만 만든다 |
| ChatPresenceRegistry 상태 | 세션별 확인 표(`checked`)는 세션이 닫힐 때 `leaveAll`에서 지운다. 정상 닫힘에서 누수 없음. 닫는 스레드와 처리 중인 프레임이 겹치면 닫은 뒤 `markChecked`·`enter`가 실행될 수 있고(이미 있던 `enter` 경쟁에 새 표가 더해짐), 한 세션당 최대 10개 항목이라 영향은 작다. 별도 결함으로 세지 않고 BE6-4에 포함해 본다. `leave(crewId, memberId)`가 같은 회원의 다른 세션 입장까지 지우는 것은 5차 이전부터의 동작이다 |
| 리액션 제한 | 회원 키 문자열로 AttemptLimiter(창 1초, 10건). 확인과 기록이 한 번에라 동시 요청도 한도를 못 넘는다. 멱등 반복(이미 달린 리액션)도 한 건으로 센다. 보내기 한도와 따로 센다. 보내기·리액션 둘 다 서비스 호출 전에 걸려 검증 실패도 소비한다(의도된 보수) |
| 배경 채우기 경합 | 판정 순서: 해금 행 없는 회원만 → 배포 전 거리로 채움(알림 없음) → 그 뒤 거리는 리스너·보정이 알림과 함께. 잠금(`findForUpdate`)과 재시도 3회는 그대로이고, 두 경로가 같은 랜드마크를 놓고 겹쳐도 `Avatar.unlock`이 새로 넣은 것만 돌려줘 알림은 한 번이다. 남은 위험은 BE6-6(기준 값 관리)뿐 |
| deploy.sh 보안 | 비밀번호가 명령줄·로그에 나오지 않는다(소켓 접속, `.env` 값은 `DB_USER`·`DB_NAME`만 읽음). `set -euo pipefail`이라 pg_dump 실패가 gzip 성공에 가려지지 않는다(확인함). 실패하면 부분 파일을 지우고 중단한다. 보존 정리의 `ls | tail | xargs rm`은 이름 규칙이 고정이라 안전하다. 문제는 BE6-1~3 |
| wiki.conf.template(develop 병합) | 80은 인증서 챌린지만 열고 301, 443은 `/console`·`/pgadmin/`·`/minio/`·`/gotrue/admin`을 404로 막는다. 변수 `proxy_pass`와 `resolver 127.0.0.11`(default.conf 전역)으로 AppFlowy 스택이 없어도 nginx는 뜬다. 주석대로 인증서 `appflowy-interim/`이 볼륨에 없으면 런크크 nginx 전체가 못 뜬다는 결합은 문서화돼 있다(경 참고, 결함으로 세지 않음). `client_max_body_size 2g`·`proxy_request_buffering off`는 위키 도메인에만 걸린다(런크크 server는 별도). 런크크 API 보안 설정에는 영향 없음 |
| 4401 만료·게스트 자격 증명·시도 제한 | 5차 점검 그대로(이번 범위에서 변경 없음) |

### 5. 시험 품질

변경 시험 20파일을 읽었다. 5차 결함마다 시험이 붙은 것: A-4(BackgroundServiceTest·TC-6763 강화), A-5(GuestServicesTest), A-2(ChatRealtimeTest 둘), B-1(CrewChatServiceTest), B-18(ChatRateGuardTest·ChatControllerTest), A-14(BootstrapConfigTest). 시그니처 변경은 시험을 따라 고쳤다(생성자 인수 4개, 6번째 `RelayEntry` 인수 등). 시험이 없는 것: A-13(BE6-8), A-15 deploy.sh(BE6-9), 새 JPQL(BE6-8). 시험 이름에 완전한 이름(`new com.a207.runkk.avatar.BackfillProperties(java.time.LocalDateTime.of(...))`, AvatarServicesTest:103)이 남은 것은 스타일 경 한 줄이라 따로 세지 않는다.

### 6. 형상 관리

| 활동 | 판정 | 근거 |
|------|------|------|
| 형상 식별 | 예 | 5차와 같다(설계 64패키지 + ArchitectureTest) |
| 버전 제어 | 예(부분) | 이력·통합 브랜치 있음. 태그 0개, `backend/build.gradle.kts:10` version `0.0.1-SNAPSHOT` 그대로(참고) |
| 변경 통제 | 아니오 | 범위 서버 커밋 26개 중 Jira 키 0개(`feat/fix … (BE5-A-n, D-nnn)`로 결함 ID는 모두 연결됨). N-14 보류(U-04)라 판정 요인에서 뺀다. 5차 결함 ID가 제목·본문에 있어 변경 요청 추적은 가능하다 |
| 형상 감사 | 예 | 기준 4ee91aef 대비 diff 재현. 설계 갱신 항목은 D-167~D-189로 설계서에 반영돼 있다 |
| 상태 보고 | 예(경 지적) | 제목에 타입 접두·결함 ID가 있다. 지적: (1) `merge:` 타입 3개(a27c914f·4844039a·82e172f7)와 GitLab 기본 병합 1개(4f51b203)가 AGENTS.md 타입 목록 밖(5차 B-22 일부와 같은 항목). (2) 한 커밋에 두 가지 일이 든 것이 6b3a8104(권한표 삭제 판정 + PostSummary 머리를 PostHeader로)와 136fcca1(배경 채우기 한정 + 선택 두 연산). 앞서 a358111e가 이미 머리를 묶었는데 6b3a8104가 다시 이름을 바꿔 같은 일이 두 번 나온다. fb2f4722(초당 5회 제한)는 2커밋 뒤 77a2fbcb에 대체돼 이력만 늘었다 |

### 7. 집계와 판정

| 구분 | 치명 | 중 | 경 | 구성 |
|------|------|----|----|------|
| 새 결함(BE6-1~9) | 0 | 1 | 8 | 중 BE6-1 / 경 BE6-2~9 |
| 5차 이월(미해결·일부·보류) | 0 | 0 | 17 | 미해결 11(A-6·9·10, B-8·9·13·14·15·17·19·20), 일부 4(B-12·16·21·22), 보류 2(A-16·17) |
| 합계 | 0 | 1 | 25 | 4차 이월 경(L4·L23·L25·L26 결정 대기 등)은 변동 없어 위 합계에 넣지 않는다 |

판정: **합격**
근거: 치명 0, 중 1(3 이하). 5차 중 7건은 모두 해결됐고 설계 일치(D-167·D-169(일부 코드 정리 남음)·D-173·D-175·D-176·D-177·D-188) 점검을 수행했으며 새 의존 화살표가 없다. 형상 관리는 변경 통제(Jira 키)만 아니오이며 N-14 보류로 판정 요인에서 뺐다(4·5차와 같다). 다만 BE6-1(백업이 다음 CI 잡에서 지워질 수 있음)은 5차 BE5-A-15를 사실상 되돌릴 수 있는 안전장치 결함이라 운영 DB에 MR을 배포하기 전에 고치는 것을 권한다.
이월 경 25건은 구조 정리(CrewPostService 분할·instanceof·N+1)와 정책 결정(B-20)이 대부분이며 MR !14를 막지 않는다.

### 8. sw-test 전달: 우선 테스트 대상

복잡도 10 초과 함수는 없다. 10에 가까운 함수: running.RunUpload.requireOrderedWithin 8, infra.integration.auth.JwtTokenIssuer.parse 10(5차와 같음), realtime.LiveSocketHandler.applyChatPresence 7, crew.JpegMetadataStripper.strip 7.

회귀·신규 시험 우선 경로:

| 함수·경로 | 이유 |
|-----------|------|
| infra.running.JpaRunRecordRepository.validDistanceSavedBefore | BE6-8: cutoff 직전·같은 시각·직후·무효 기록, savedAt 널(실 DB) |
| avatar.BackgroundService.backfillReached | 해금 행 없음 + 배포 전 거리 0/기준 직전/초과, 배포 후 기록 직후 조회·리스너 동시 실행에서 알림 정확히 1건(TC-6763 유지) |
| realtime.LiveSocketHandler.applyChatPresence + ChatPresenceRegistry | BE6-4·5: 거부 뒤 가입한 같은 세션의 재입장, 11번째 방, 세션 닫힘 직후 도착한 프레임, `checked` 항목 제거 |
| operator.OperatorBootstrap.seedIfEmpty | BE6-8: 빈 값 + 화이트리스트 있음/없음 조합의 로그 |
| infra/deploy.sh backup_database | BE6-3·9: postgres 실행 중/정지(볼륨 있음)/없음, pg_dump 실패, 보존 7개, 파일 권한 600 |
| crew.CrewPost.delete·crew.chat.CrewChatService.delete | 작성자·크루장·운영진·일반원·떠난 사람(OUTSIDER) 조합이 게시·채팅에서 같은 표를 따르는지(D-188), 403/409 구분(D-167) |
| web.chat.ChatController.react·unreact | 429 변환(움직이는 시계), 한도 경계 10/11 |
| crew.JpegMetadataStripper.strip | 리팩터링 전후 바이트 동일성(EXIF·COM·스캔 사이 APP, EOI 뒤 꼬리, EOI 없이 끝남, SOS 없이 EOI) |

### 9. 설계서 갱신 요청 (`/sw-design-oo` 재호출 목록)

| ID | 대상 절 | 내용 | 비고 |
|----|---------|------|------|
| U6-1 | 05-02 5.4 "되돌릴 수 없는 마이그레이션", D-175 (2), implementation.md 10절 | 배포 전 덤프를 deploy.sh가 자동으로 뜬다(실패하면 배포 중단, 보존 7개, 파일 이름은 시각). D-175의 거부안("배포 스크립트가 자동 덤프")을 채택으로 바꾸고 BE6-1~3 결정(보존 위치·권한·정지 시 처리)을 적는다 | 코드가 낫다 |
| U6-2 | 06-04 CrewPermissionPolicy, 05-02 D-132 | 리액션 달기·떼기도 회원당 초당 10건(ChatRateGuard.admitReaction), 429 TOO_MANY_ATTEMPTS. 엔티티가 정책을 쓰는 방식(BE6-7 결정 뒤) | 코드 반영 뒤 |
| U6-3 | 05-02 D-176, 5.4 환경 변수 표 | `runkk.avatar.backfill-cutoff`(`AVATAR_BACKFILL_CUTOFF`): 의미, 기본값 규칙, 미래 값 금지(BE6-6 결정 뒤) | |
| U6-4 | 05-02 5.4 게시 사진 문단 | `max-age=86400`을 `max-age=300`으로(D-174 (2)와 맞춤) | 설계 내부 모순 |
| U6-5 | 06-01·06-02·06-04 | 새 이름: common.ServerZone.ID, WeeklyRace.GoalState, SchedulePost.Slot, ContentReport.Content, avatar.BackfillProperties, RunQueryService.validDistanceBefore·RunRecordRepository.validDistanceSavedBefore(06-02에는 이미 적혀 있으면 서명만 대조) | 구현 반영 |
| U6-6 | 02 동적 모델 1215·1220·1796·1808, 03 기능 모델 100 | `latestGoalBefore`를 호출하지 않는다(CREATE_WEEK 한 SQL이 복사). BE5-B-17 코드 삭제와 함께 갱신 | U5-5 이어짐 |

### 10. 다음 행동

- [ ] BE6-1: 덤프 위치를 작업 폴더 밖으로 옮기거나 CI 정리 제외를 건다(MR !14를 운영 DB에 배포하기 전)
- [ ] BE6-2·3: 덤프 권한 600·폴더 700, postgres 정지 + 볼륨 있음일 때 처리
- [ ] BE6-6: 기준 시각 검증·기본값 정리 후 배포 체크리스트(implementation.md 10절)에 "V25 적용 시각으로 설정" 항목
- [ ] BE6-4·5: 거부 캐시 정책과 초과 신호 로그를 정리하고 D-173에 반영
- [ ] BE6-7·8·9: 권한표 사본 정리, 빠진 시험 세 개, 불안정 시험 고치기
- [ ] 이월 경(미해결 11, 일부 4, 보류 2): 다음 변경 때 정리. B-8·9·13~15·17은 한 번에 정리하는 리팩터링 커밋 한 개로 묶는다(5차 B-22의 "기능별 한 가지 일" 규칙 유지)
- [ ] 설계서 갱신: `/sw-design-oo`로 U6-1~U6-6 반영
- [ ] 6차 종합 판정은 `docs/sw/code-review.md`에 앱·AI 결과와 합쳐 적는다
