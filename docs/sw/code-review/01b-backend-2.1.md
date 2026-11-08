> [런크크 코드 리뷰 결과서](../code-review.md) 목차 · 이전: [01-backend](01-backend.md) · 다음: [01c-backend-2](01c-backend-2.md)
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

