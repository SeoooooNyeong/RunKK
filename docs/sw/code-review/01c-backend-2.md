> [런크크 코드 리뷰 결과서](../code-review.md) 목차 · 이전: [01b-backend-2.1](01b-backend-2.1.md) · 다음: [01d-backend-리뷰-기준](01d-backend-리뷰-기준.md)
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

