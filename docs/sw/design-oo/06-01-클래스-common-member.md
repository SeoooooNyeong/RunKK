> [런크크 객체지향 분석·설계서](../design-oo.md) 목차 · 이전: [05-12-설계-결정-v0.17](05-12-설계-결정-v0.17.md) · 다음: [06-02-클래스-running-analysis-profile](06-02-클래스-running-analysis-profile.md)

## 6. 객체 설계

### 6.1 클래스 명세

모든 속성은 private이다. 표의 "가시성" 열은 생략하고 이 규칙을 따른다. public 메소드만 적는다(시그니처 = 이름, 매개변수 형, 반환 형, 예외). 매개변수가 5개 이상이 되는 곳은 입력 객체(…Command, …Spec, …Input)로 묶었다. 조회용 getter는 "외부가 읽어야 하는 것"만 "조회" 줄에 모았고 setter는 두지 않는다. 상태 변경은 업무 동사 메소드로만 한다.

예외 이름 규칙: 입력 형식 위반은 `IllegalArgumentException`, 상태 전이 위반은 `IllegalStateException`, 업무 규칙 거부는 `BusinessRuleException(사유 코드)`, 권한 거부는 `PermissionDeniedException`이다. 업무 규칙 거부의 사유 코드는 srs.md 6.3의 거부 사유 문구와 1:1로 대응한다(ApiExceptionHandler가 화면 문구로 바꾼다). 사유 코드는 enum `common.ReasonCode` 하나에 두고 상수마다 화면 문구(`message()`)를 갖는다. 상수는 그 사유를 처음 내는 패키지를 구현할 때 더한다. 상수 이름은 앱과의 계약(오류 본문 code)이라 더하기만 하고 이름을 바꾸거나 지우지 않는다 (v0.8, CR-U03, D-72).

#### 6.1.1 common

##### ServerZone (상수 클래스) (v0.12, U6-5)
패키지 common. 서버 시간대를 한 곳에 둔다. `public static final ZoneId ID = ZoneId.of("Asia/Seoul")`이고 인스턴스를 만들지 않는다(final, private 생성자). 날짜 경계(Week, DateRange, 0시 작업)와 LocalDateTime ↔ OffsetDateTime 변환이 이 값을 쓴다. 변경이 드문 불변 기본 형식이라 common 조건(D-48)을 만족한다.

##### Distance (값, record)
| 항목 | 내용 |
|------|------|
| 패키지 | common |
| 책임 | 거리 하나를 미터 단위로 표현한다 |
| 상위 클래스 / 인터페이스 | Comparable<Distance> |

속성:
| 이름 | 형 | 제약 |
|------|----|------|
| meters | long | ≥ 0 |

연산:
| 시그니처 | 설명 | 사전 조건 | 사후 조건 / 예외 |
|---------|------|----------|-----------------|
| `static Distance ofMeters(long m)` / `static Distance ofKm(double km)` | 생성 | m ≥ 0 | 음수면 IllegalArgumentException |
| `Distance plus(Distance d)` / `Distance minus(Distance d)` | 더하기·빼기 | minus는 결과 ≥ 0 | 새 객체. 음수면 ZERO |
| `boolean isAtLeast(Distance d)` | 비교 | | meters ≥ d.meters |
| `double km()` | km 환산 | | 소수 둘째 자리 반올림 |
| `String format(DistanceUnit unit)` | 표시 문자열 | | 저장 값은 그대로, 표시만 단위 환산(FR-009) |

##### Pace (값, record)
| 항목 | 내용 |
|------|------|
| 패키지 | common |
| 책임 | 1km를 달리는 데 걸린 시간을 표현한다 |

속성: secondsPerKm(int, > 0).

연산:
| 시그니처 | 설명 | 사전 조건 | 사후 조건 / 예외 |
|---------|------|----------|-----------------|
| `static Pace of(Duration elapsed, Distance distance)` | 경과 시간 ÷ 거리 | distance > 0 | 0이면 IllegalArgumentException |
| `boolean isSlowerThanBy(Pace target, double ratio)` | 목표보다 ratio 이상 느린가 | ratio ≥ 0 | secondsPerKm ≥ target × (1 + ratio) (FR-044는 0.1) |
| `boolean isWithin(Pace fastest, Pace slowest)` | 범위 안인가 | | FR-047 3:00~15:00 |
| `double speedKmh()` | 시속 환산 | | 3600 ÷ secondsPerKm |
| `String format(DistanceUnit unit)` | "5'30\"" 형식 | | mi 단위면 1.609배 |

##### GeoPoint (값, record)
속성: latitude(double, -90~90), longitude(double, -180~180).

| 시그니처 | 설명 | 사전 조건 | 사후 조건 / 예외 |
|---------|------|----------|-----------------|
| `Distance distanceTo(GeoPoint other)` | 하버사인 거리 | | 지구 반지름 6,371,000m |
| `GeoPoint coarsen(int decimals)` | 소수 자릿수 낮추기 | 0 ≤ decimals ≤ 6 | NFR-038은 2 |

##### Week (값, record)
속성: monday(LocalDate, 월요일만).

| 시그니처 | 설명 | 사전 조건 | 사후 조건 / 예외 |
|---------|------|----------|-----------------|
| `static Week of(LocalDateTime t)` | t가 속한 주 | | 월요일 0시 시작 |
| `boolean contains(LocalDateTime t)` | 주 안인가 | | [월 0시, 다음 월 0시) |
| `LocalDateTime closesAt()` | 일요일 24시 | | FR-140 마감 일시 |
| `Week previous()` | 지난주 | | |

##### DateRange (값, record)
속성: start(LocalDate), end(LocalDate, ≥ start). 연산: `boolean contains(LocalDate d)`, `Stream<LocalDate> days()`, `static DateRange lastWeeks(LocalDate base, int n)`, `static DateRange lastMonths(LocalDate base, int n)`.

##### TagSet (값)
| 항목 | 내용 |
|------|------|
| 패키지 | common |
| 책임 | 고른 크루 성향 태그 묶음이 개수 규칙을 지키게 한다 (v0.11, D-91) |

속성: tags(Set<PersonalityTag>, 불변), owner(TagOwner). TagOwner는 CREW 하나만 남는다. 러너 성향 태그(Member.tags, CrewMembership.crewTags, CrewProfileEdit.tags)를 지워 TagOwner.RUNNER를 쓰는 곳이 0이 되므로 RUNNER 상수를 지운다 (v0.11, FR-119, D-91).

| 시그니처 | 설명 | 사전 조건 | 사후 조건 / 예외 |
|---------|------|----------|-----------------|
| `static TagSet of(Set<PersonalityTag> tags, TagOwner owner)` | 생성·검증 | | 1~5개, 분류마다 1개 이하. 위반이면 BusinessRuleException(TAG_INVALID). owner.allows(category) 갈래는 TagOwner.RUNNER 삭제로 늘 참이 되어 지웠다 (v0.11, test.md v0.6 TF-BE-27) |
| `static TagSet empty(TagOwner owner)` | 태그 없음 | | 크루 찾기 필터를 비운 경우 (v0.11, D-91) |
| (삭제) `int overlapCount(TagSet other)` | 겹치는 태그 수 | | FR-118 일치도였다. FR-118 삭제로 쓰는 곳이 없어 지운다(v0.11 정리) (v0.11, FR-118) |
| `boolean containsAll(Set<PersonalityTag> wanted)` | 모두 포함 | | FR-117 AND 검색 |
| (삭제) `Optional<PersonalityTag> valueOf(TagCategory c)` | 분류의 선택값 | | FR-152 임시 유형의 동행·코스 축이었다. runimal이 태그 없이 판정하게 되어 쓰는 곳이 없어 지운다(v0.11 정리) (v0.11, FR-152, D-91) |
| `boolean isEmpty()` | 비었는가 | | |

##### DisplayName (값, record)
속성: value(String). 상수 `WITHDRAWN = "탈퇴한 사용자"`. 연산: `static DisplayName of(String)`, `static DisplayName ofNullable(String)`(null이면 WITHDRAWN). 표시명 규칙(srs.md 6.2 "표시명 = 닉네임 | 익명닉네임 | 가상닉네임 | 탈퇴한사용자")을 한곳에 둔다.

##### RecordVisibility (enum, 다형성)
| 상수 | allows(ViewerRelation r) | includedInPersonalRanking() | ghostEligible() |
|------|-------------------------|----------------------------|-----------------|
| PUBLIC | true | true | true |
| CREW | r == OWNER 또는 r == SAME_CREW | true | false |
| PRIVATE | r == OWNER | false | false |

ViewerRelation(enum: OWNER, SAME_CREW, OTHER)은 RecordQueryService가 크루 소속을 보고 정해 넘긴다. 상수별 메소드로 구현해 `if (visibility == …)` 분기를 없앴다(6.4).

##### StandardDistance (enum)
상수 KM1(1,000m), KM3, KM5, KM10, HALF(21,097m), FULL(42,195m). 연산: `Distance distance()`, `boolean isRandomBattle()`(1·3·5·10), `boolean isSelfBattle()`(3·5·10·하프), `boolean isCrewRun()`(1·3·5·10), `boolean isRecordBase()`(1·3·5·10·하프), `static Optional<StandardDistance> nearestAtMost(Distance d)`.

##### ReasonCode (enum, v0.8, CR-U03, D-72)
업무 규칙 거부 사유 코드 전부. 상수마다 `String message()`(화면 문구). BusinessRuleException이 이 값을 갖고, ApiExceptionHandler가 409 본문 `{"code": 이름, "message": 문구}`로 바꾼다. common 유지 조건(D-48)의 예외로 인정한 형식이다: 상수 추가는 기존 사용자에게 영향이 없는 덧붙임이고 이름·문구 변경은 하지 않는다(D-72). v0.8에서 정한 사유: JOIN_REQUEST_EXPIRED("신청 만료", D-69), SELFIE_IN_PROGRESS("이미 변환 중"), NOTIFICATION_NOT_FOUND("알림 없음").

상태가 409가 아닌 사유는 ApiExceptionHandler.STATUS_BY_REASON에 적은 것뿐이다. TEMPORARILY_UNAVAILABLE("잠시 후 다시 시도해 주세요")은 503이고, 서버 저장소 일시 장애(갱신·로그아웃 때 갱신 토큰 폐기 목록을 쓸 수 없음)에만 쓴다. PROVIDER_UNAVAILABLE("로그인 제공자 연결 실패")은 소셜 제공자 무응답 전용이다 (v0.11, CR34-25, CR34-05).

v0.11에서 더하는 사유 18개(16개는 409, TOO_LARGE 413·UNSUPPORTED_IMAGE 422는 ApiExceptionHandler.STATUS_BY_REASON) (v0.11, F.2.3) (v0.11 구현 반영, U5-A-10):

| 상수 | 문구 | 내는 곳 |
|------|------|---------|
| ALREADY_LINKED | 이미 연동한 계정 | AuthService.linkSocialAccount (Member.linkSocialAccount) |
| MANAGER_LIMIT | 운영진은 2명까지 지정할 수 있음 | Crew.appointManager |
| PIN_LIMIT | 공지는 3개까지 고정할 수 있음 | CrewPostService.writeNotice·pin |
| SCHEDULE_INVALID | 일정 입력 오류 | MeetupPost 생성(일시가 지금 이전일 때만, 길이·범위는 400, D-155) (v0.11, test.md v0.6 TF-IT-32) |
| SCHEDULE_FULL | 정원이 찼음 | MeetupPost.answer |
| POLL_INVALID | 투표 입력 오류 | PollPost 생성(마감이 지금 이전)·respond(선택지 길이·개수는 400, D-155) (v0.11, test.md v0.6 TF-IT-32) |
| POLL_CLOSED | 마감된 투표 | PollPost.respond |
| POST_EMPTY | 내용이나 사진이 필요함 | BoardPost 생성 |
| PHOTO_LIMIT | 사진은 5장까지 | BoardPost 생성 |
| CHAT_EMPTY | 빈 메시지 | CrewChatMessage.write |
| CHAT_MESSAGE_NOT_FOUND | 메시지 없음 | CrewChatService |
| REPORT_TARGET_NOT_FOUND | 신고할 대상이 없음 | ReportService.submit |
| ALREADY_REPORTED | 이미 신고함 | ReportService.submit |
| REPORT_NOT_FOUND | 신고 없음 | ReportService.hide·dismiss (v0.11 검증, D-138) |
| REPORT_DECIDED | 이미 처리한 신고 | ReportService.hide·dismiss (v0.11 검증, D-138) |
| BACKGROUND_LOCKED | 아직 해금하지 않은 배경 | BackgroundService.choose (Avatar.selectBackground) |
| TOO_LARGE | 사진이 너무 커요 | PhotoUpload(게시 사진 1MB 초과, D-100). 413 (v0.11 구현 반영, U5-A-10) |
| UNSUPPORTED_IMAGE | 지원하지 않는 형식 | PhotoUpload·JpegMetadataStripper(JPEG 아님·깨짐, D-100, D-142). 422 (v0.11 구현 반영, U5-A-10) |

쓰지 않게 되는 상수(TAG_INVALID의 러너 태그 용도, INTRO_TOO_LONG의 가입 경로 등)는 지우지 않는다(옛 앱 해독 호환). TAG_INVALID는 크루 태그에 계속 쓴다 (v0.11, F.2.3, D-72).

##### Tier (enum)
상수 UNRANKED, BRONZE, SILVER, GOLD, PLATINUM, MASTER. 연산: `boolean isRanked()`, `Set<Tier> adjacent()`(위아래 한 단계), `List<Set<Tier>> searchLadder()`(같은 티어 → 인접 → 전체 랭크 티어, FR-095).

##### DomainEvent와 DomainEventPublisher (인터페이스)
DomainEvent는 사건 형식임을 표시하는 표지 인터페이스(`LocalDateTime occurredAt()`)이고, DomainEventPublisher는 `void publish(DomainEvent e)` 하나를 가진다. Spring ApplicationEventPublisher로 구현한다. 두 형식만 common에 두고, 사건 record는 **발행하는 패키지**에 둔다(5.6 D-31). 수신자는 이미 발행 패키지에 의존하고 있어 새 의존 화살표가 생기지 않는다.

| 사건 (패키지) | 항목 | 발행 | 수신 |
|------|------|------|------|
| RunRecordSaved (running) | recordId, memberId, startedAt, distance, elapsed, valid, score, mode, battleId, crewRunPostId, hasRoute, validRunOrdinal | RunUploadService.upload | running.TierUpdateListener(→ member.TierLevelService.recordValidRun), crew.WeeklyRaceService, reward.RewardService, battle.BattleResultService, crew.CrewRunService, avatar.BackgroundUnlockListener(커밋 뒤 동기) / analysis.ReportService, runimal.RunimalService(비동기). 커밋 뒤 수신자가 실패해 빠진 처리는 보정 작업(RewardReconcileService, CrewRunLinkReconcileService, TierReconcileService, BackgroundService.reconcile)이 RunQueryService.savedBetween으로 다시 채운다 (v0.11, CR34-03, D-107, D-108) |
| MemberWithdrawn (member) | memberId, at | WithdrawalService.withdraw, WithdrawalService.withdrawIdleGuests | 패키지별 …WithdrawalListener 10개(running, analysis, battle, crew, crew.chat, runimal, avatar, reward, notification, report) (v0.11, FR-021, FR-200) |
| ConsentWithdrawn (member) | memberId, item(LOCATION / HEART_RATE), at | MemberSettingService.withdrawConsent | running.DisposalService |
| BattleJudged (battle) | battleId, type, judgement(참가자별 판정, 정정 여부), at. 접근자 winnerSide()·judgedAt() (v0.11 구현 반영, U5-A-3) | BattleResultService, BattleClockService | reward.RewardService |
| OperationSettingChanged (policy) | key, at | OperationSettingService.change | battle.VirtualRecordService(key가 TIER_BOUNDARY 또는 VIRTUAL_RECORD_RULE일 때 재생성, D-45) |

validRunOrdinal은 이 기록을 포함한 그 회원의 유효 러닝 순번이다(무효 기록이면 0). RunUploadService가 저장 트랜잭션 안에서 회원의 유효 기록 수를 세어 정한다. 티어(TierProgress)와 Runimal(RunimalProfile)은 이 순번 하나로 판정 시점(3회, 10의 배수)을 정한다(5.6 D-37).

##### ProgressReport (값, record)
속성: distance(Distance), elapsed(Duration), sentAt(LocalDateTime). 위치 필드가 없다(FR-089). sentAt은 앱이 보낸 값을 RealtimeMessageSerializer.readSentAt이 서버 시간대로 맞춰 담지만 battle·crew 어느 쪽도 쓰지 않는다(OpponentProgress·CrewRunRelay에 싣지 않는다, v0.13 정정 D-211·D-214). battle·crew가 함께 받으므로 불변 기본 형식으로 common에 둔다. 채널 구분(BATTLE / CREW_RUN)과 채널 ID(대결 ID·크루런 게시글 ID)는 realtime의 수신 형식 InboundProgress에 있다. LiveSocketHandler가 그것으로 서비스를 고른 뒤 채널 ID를 onProgress 매개변수로 따로 넘긴다(D-48). 실시간 채널이 늘어도 common은 바뀌지 않는다.

##### Timestamps (값, record)
속성: createdAt(LocalDateTime), updatedAt(LocalDateTime, ≥ createdAt). 불변이다. 연산: `static Timestamps createdAt(LocalDateTime now)`(두 값 모두 now), `Timestamps touched(LocalDateTime now)`(updatedAt만 바꾼 새 값). 엔티티는 상태를 바꾸는 연산 끝에 `timestamps = timestamps.touched(now)`로 필드를 바꿔 끼운다. JPA @Embeddable로 매핑하고 상속(BaseEntity) 대신 포함한다(1.3.7). 엔티티가 쓰는 값이라 인프라가 아닌 common에 둔다(design-review.md 9.4, D-48).

v0.1에 common에 있던 실시간 송신 포트와 메시지 상위 형식은 없앴다. 송신 포트는 쓰는 패키지가 정의한다: battle.BattleMessenger(6.1.6), crew.CrewRunMessenger(6.1.7). 메시지 record는 만드는 패키지의 하위 패키지(battle.live, crew.live)에 둔다. realtime이 두 포트를 구현한다(5.6 D-06).

##### 구현 보조 클래스 (P-1 결정: PageCursor는 common, 나머지 둘은 web.attempt, D-132)
설계 표에 없던 구현 보조 클래스다. P-1을 design-review.md 17장에서 정했다: PageCursor는 불변 값이라 common에 남고(D-48 조건 안), AttemptLimiter·ClientAddress는 표현 하위 패키지 web.attempt로 옮긴다(시도 제한만 담는다) (v0.11, CR2-U7, CR34-11) (v0.11 검증, D-132).

| 클래스 | 종류 | 책임 | 속성 | 연산 | 쓰는 곳 |
|--------|------|------|------|------|---------|
| PageCursor (common) | 값, record | 커서 페이지 요청 하나를 표현한다 | cursor(Long, 앞 페이지 마지막 항목 ID, 첫 페이지면 null, ≥ 1), size(Integer, 1~100, 전부면 null), 상수 MAX_SIZE = 100 | `static PageCursor all()`(페이지 나누기 없음), `<T> Slice<T> slice(List<T> newestFirst, ToLongFunction<T> idOf)`(최신순 목록에서 cursor 다음 size개, 더 남았으면 nextCursor). Slice(record: items, nextCursor) | crew 목록·게시글, 대결 이력. v0.11에서 crew.chat·report·web.chat·web.report·web.admin.report도 쓴다(size+1 읽기, nextCursor 형식, F.2.0) |
| AttemptLimiter (web.attempt) | 가변 객체(스프링 빈 아님) | 키(회원·접속지)별 실패 횟수 창을 센다 | maxFailures(int ≥ 1), window(Duration > 0), clock(Clock), failures(ConcurrentHashMap<String, Deque<Instant>>, 메모리 값, 재시작하면 비워짐) | `AttemptLimiter(int maxFailures, Duration window, Clock clock)`, `boolean isBlocked(String key)`, `void recordFailure(String key)`, `void reset(String key)`, `boolean tryAcquire(String key)`(창 안 횟수가 상한 미만이면 한 번을 남기고 참, 상한이면 거짓. 확인과 기록을 한 번에 해 동시 요청이 상한을 넘지 못한다), `void release(String key)`(tryAcquire로 남긴 마지막 한 번을 되돌린다) (v0.11 구현 반영, U5-A-4, D-177). 키가 10,000개 쌓이면 만료 키를 치운다 | web.auth AuthAttemptGuard(게스트 생성 한도 포함), web.admin 운영자 로그인, web.chat ChatRateGuard, web.crew CrewJoinAttemptGuard(초대 코드 입력, crew에서 옮김) (v0.11 검증, D-132) |
| ClientAddress (web.attempt) | 값, record | 요청을 보낸 접속지(시도 제한 키)를 표현한다 | value(String, not null) | `static ClientAddress resolve(String remoteAddr, String realIpHeader)`: X-Real-IP는 요청이 같은 호스트·사설망(nginx)에서 왔을 때만 믿는다 | web.auth, web.admin (v0.11 검증, D-132) |

#### 6.1.2 member

##### Member
| 항목 | 내용 |
|------|------|
| 패키지 | member |
| 책임 | 회원 한 명의 계정 상태를 보관한다 |
| 상위 클래스 / 인터페이스 | 없음 (엔티티) |

속성:
| 이름 | 형 | 제약 |
|------|----|------|
| id | long | 발급 뒤 불변 |
| account | SocialAccount | (provider, providerAccountId) 고유. GUEST면 없음(null), linkSocialAccount 때 한 번 정하고 그 뒤 불변. LINKED면 탈퇴 전까지 있음(ck_member_account) (v0.11, FR-197, FR-198, D-89) |
| accountType | AccountType | GUEST → LINKED 한 방향. 가입(signUp)은 LINKED, 게스트 시작(guest)은 GUEST (v0.11, FR-197, D-89) |
| guestCredentialHash | String | GUEST만. 기기 자격 증명 비밀의 SHA-256 hex 64자. 연동·탈퇴 때 지운다 (v0.11, D-89) |
| lastAccessAt | LocalDateTime | 마지막 접속일시. 날짜가 바뀐 때만 갱신 (v0.11, FR-200, D-89) |
| nickname | Nickname | 고유 |
| birthYear | Integer | nullable, 4자리 |
| gender | Gender | nullable |
| photoUrl | String | nullable |
| intro | String | nullable, 40자 이하 |
| tier | Tier | 기본 UNRANKED |
| level | int | ≥ 1 |
| joinedAt | LocalDateTime | 불변 |
| settings | MemberSettings | 합성 |
| consents | ConsentHistory | 합성 |
| tierProgress | TierProgress | 합성 |
| status | MemberStatus | ACTIVE → WITHDRAWN |
| withdrawnAt | LocalDateTime | nullable, withdraw 때 기록 |
| timestamps | Timestamps | 값 객체 |

연산:
| 시그니처 | 설명 | 사전 조건 | 사후 조건 / 예외 |
|---------|------|----------|-----------------|
| `static Member signUp(SocialAccount account, SignUpForm form, Nickname nickname, LocalDateTime now)` | 가입 | form.validate 통과 | 설정 기본값(srs.md 6.3 1.2, 기록 공개 범위는 PUBLIC으로 시작, FR-028), 동의 이력 기록, tier UNRANKED, level 1, accountType LINKED, lastAccessAt = now (v0.11, FR-003, FR-028) |
| `static Member guest(Nickname nickname, Map<ConsentItem, Boolean> consents, LocalDateTime now)` | 게스트 시작 | GuestStartForm.validate 통과 | account 없음, accountType GUEST, guestCredentialHash 없음(저장 뒤 attachGuestCredential로 붙인다, D-128), 설정 기본값·동의 이력은 signUp과 같다, lastAccessAt = now (v0.11, FR-197, D-89) |
| `void attachGuestCredential(String credentialHash)` | 게스트 자격 증명 해시 붙이기 | GUEST이고 해시가 비어 있음 | guestCredentialHash = credentialHash. 이미 있거나 LINKED면 IllegalStateException. SignUpService.startAsGuest가 저장으로 회원 ID를 얻은 뒤 같은 트랜잭션에서 부른다 (v0.11, FR-197, D-89, D-128) |
| `void linkSocialAccount(SocialAccount account)` | 소셜 계정 연동 | ACTIVE | 이미 LINKED면 BusinessRuleException(ALREADY_LINKED). account를 정하고 accountType LINKED, guestCredentialHash를 지운다. 회원 ID는 그대로다. LINKED에서 GUEST로 돌아가는 연산은 없다 (v0.11, FR-198, D-90) |
| `boolean matchesGuestSecret(String secret)` | 게스트 비밀 확인 | | GUEST이고 GuestCredential.hashOf(secret)이 guestCredentialHash와 같으면 true. 비교는 MessageDigest.isEqual(상수 시간) (v0.11, FR-197, D-89) |
| `boolean recordAccess(LocalDateTime now)` | 마지막 접속 기록 | ACTIVE | now의 날짜가 lastAccessAt 날짜와 다를 때만 lastAccessAt = now로 바꾸고 true. 같은 날이면 false(쓰기 없음) (v0.11, FR-200, D-89). 더 이른 시각으로 되돌리지 않는다: now < lastAccessAt이면 false(쓰기 없음, D-164) (v0.11, test.md v0.6 TF-BE-31) |
| `boolean isGuest()` | 게스트인가 | | accountType == GUEST (v0.11, FR-199) |
| `void editProfile(ProfileEdit edit, Nickname newNickname)` | 프로필 편집 | ACTIVE | intro 40자 초과면 BusinessRuleException(INTRO_TOO_LONG). 태그 항목은 없다 (v0.11, FR-007, D-91). 출생연도: 형식 오류(4자리 숫자 아님)는 IllegalArgumentException → 400, (기준 연도 − 출생연도) ≤ 14면 BusinessRuleException(UNDER_14) → 409(FR-004, D-155) (v0.11, test.md v0.6 TF-IT-27) |
| `SettingOutcome changeSetting(SettingChange change)` | 설정 변경 | ACTIVE, 동의 항목이 아님 | 동의 항목이면 BusinessRuleException(CONSENT_ITEM_NOT_SETTING). WATCH_LINK 켜짐인데 심박 동의가 없으면 SettingOutcome.needsConsent(HEART_RATE)(FR-019). 그 밖은 settings.apply |
| `ConsentChange giveConsent(ConsentItem item, LocalDateTime now)` | 동의 | ACTIVE | 이력에 동의를 덧붙인다. 이전·이후 값을 돌려준다(감사용) |
| `ConsentChange withdrawConsent(ConsentItem item, LocalDateTime now)` | 동의 철회 | ACTIVE, item이 철회 불가 필수 동의(약관·개인정보·만 14세)가 아님 | 이력에 철회를 덧붙인다. 이전·이후 값을 돌려준다(감사·사건용) |
| `boolean hasConsent(ConsentItem item)` | 동의 여부 | | consents.isAgreed(item) |
| `void rememberBattleDistance(StandardDistance d)` | 기본 대결 거리 저장 | ACTIVE, d.isRandomBattle() | settings.rememberBattleDistance(d). MemberSettingService만 부른다(FR-080) |
| `TierChange recordValidRun(RunScoreInput input, TierBoundary boundary)` | 유효 러닝 반영 | ACTIVE | tierProgress.addValidRun이 평가 평균을 주면 tier ← boundary.tierOf(평균). level ← 1 + ⌊누적 km ÷ 50⌋ |
| `TierChange recordLateValidRun(RunScoreInput input)` | 늦게 온 유효 러닝 반영 | ACTIVE | tierProgress.addLateValidRun 뒤 티어는 그대로, level만 누적 거리로 다시 센다 (v0.11 구현 반영, U5-A-1, D-169) |
| `void withdraw(LocalDateTime now)` | 탈퇴 | ACTIVE | 식별정보(닉네임, 계정, 출생연도, 성별, 사진, 소개)와 guestCredentialHash 삭제, consents.wipe(), status WITHDRAWN, withdrawnAt = now. 두 번째 호출은 IllegalStateException (v0.11, D-89) |
| `void purgeResidual()` | 남은 개인 자료 정리 | WITHDRAWN(탈퇴 뒤 7일은 서비스가 확인) | settings·tierProgress를 비우고 id·status·withdrawnAt만 남긴다. 표시명은 계속 DisplayName.WITHDRAWN. 다시 불러도 결과가 같다 (v0.11, D-91) |
| `OptionalInt ageIn(int year)` | 나이 | | 출생연도가 없으면 비어 있음 |
| `DisplayName displayName()` | 표시명 | | WITHDRAWN이면 DisplayName.WITHDRAWN |
| 조회 | `getId()`, `getTier()`, `getLevel()`, `getAccountType()`, `getSettings()`(불변 사본 MemberSettingsView), `getStatus()` | | getSettings()가 돌려준 값을 고쳐도 Member는 바뀌지 않는다. 설정 변경은 Member의 변경 연산으로만 한다(design-review.md A44). getTags()는 지운다 (v0.11, D-91) |

v0.11에서 지운 것: 속성 tags(member.tags 칼럼, V20)와 getTags(). 러너 성향 태그 기능을 없앴다 (v0.11, FR-119, CR-04, D-91).

##### SocialAccount (값, record)
속성: provider(ProviderType), providerAccountId(String, not blank). equals/hashCode는 두 값으로.

##### AccountType (enum) (v0.11, FR-197, D-89)
상수 GUEST(게스트), LINKED(연동회원). 자료사전 계정유형과 1:1이다(F.1.6). 칼럼 member.account_type(기본 'LINKED', 기존 회원은 모두 LINKED). 본인 전용 응답 GET /me/settings에만 싣고 프로필·크루·랭킹 응답에는 싣지 않는다(FR-006, D-90).

##### GuestCredential (값) (v0.11, FR-197, D-89)
| 항목 | 내용 |
|------|------|
| 패키지 | member |
| 책임 | 게스트 기기 자격 증명의 형식과 해시 규칙을 한곳에 둔다 |

속성: 토큰 형식 `g1.<memberId>.<base64url 32바이트>`(패딩 없음, 43자, D-164 (v0.11, test.md v0.6 TF-BE-30)). 서버는 비밀의 해시만 Member.guestCredentialHash에 둔다. 토큰은 게스트 생성 응답에 한 번만 내주고 다시 내주지 않는다.

| 시그니처 | 설명 | 사후 조건 / 예외 |
|---------|------|-----------------|
| `static Issued issue(long memberId, SecureRandom random)` | 발급 | Issued(String token, String hash) |
| `static Optional<Parsed> parse(String token)` | 해독 | 형식이 틀리면 비어 있음. Parsed(long memberId, String secret) |
| `static String hashOf(String secret)` | 해시 | SHA-256 hex 64자 |

##### AccessGrant (값, record) (v0.11, D-110, L29)
속성: memberId(long), expiresAt(Instant). `TokenIssuer.verifyGrant(String accessToken)`의 반환이다. realtime이 이 만료 시각에 웹소켓을 4401로 닫는다(F.3.3). 검증 실패는 AuthFailureException.

##### GuestStartResult, SocialLinkResult (결과, record) (v0.11, FR-197, FR-198)
GuestStartResult: memberId(long), nickname(String), tokens(TokenPair), guestCredential(String, 한 번만). SocialLinkResult: accountType(AccountType, LINKED), provider(ProviderType). web.auth가 GuestStartResponse·SocialLinkResponse(F.2.2)로 바꾼다(12.2 D45). record 항목 이름은 응답 형식(F.2.2)과 같게 둔다.

##### Nickname (값, record)
속성: value(String, 2~20자, 앞뒤 공백 제거). 글자 수는 보이는 글자(코드 포인트, String.codePointCount)로 센다(D-143) (v0.11, 7.13.5 TF-VA-32, TF-VB-26).

| 시그니처 | 설명 | 사전 조건 | 사후 조건 / 예외 |
|---------|------|----------|-----------------|
| `static Nickname of(String v)` | 생성 | | 2~20자가 아니면 BusinessRuleException(NICKNAME_INVALID) |
| `Nickname withSuffix(int fourDigits)` | 숫자 4자리 붙이기 | 0~9999 | 20자를 넘으면 앞부분을 잘라 20자에 맞춘다. 자르기는 코드 포인트 경계에서 한다(D-143) (v0.11, 7.13.5 TF-VA-32) |
| `static Optional<Nickname> fromProviderName(String raw)` | 가입 닉네임(제공자 이름) 고치기 | raw는 null·빈 값 허용 | 앞뒤 공백을 지우고 2자 미만이면 비어 있음(호출자가 numbered를 쓴다), 20자를 넘으면 앞 20자(코드 포인트)만. 예외를 던지지 않는다(srs FR-003: 가입은 거부하지 않는다, D-143) (v0.11, 7.13.5 TF-VA-32) |
| `static Nickname numbered(int number)` | 기본 닉네임 | 0~99999 | number < 10000이면 "러너" + 숫자 4자리(0000~9999), 그 이상이면 5자리(10000~99999). SignUpService가 0~9999에서 고르고 겹치면 다른 숫자로 최대 20번, 그래도 겹치면 10000~99999에서 고른다(별도 연산 없이 범위를 넓혔다, v0.11 정리) (v0.11, FR-003, FR-197, D-90). 5자리에서도 5번까지만 다시 시도하고 그래도 겹치면 TEMPORARILY_UNAVAILABLE(503, D-164) (v0.11, test.md v0.6 TF-BE-30) |

##### MemberSettings
| 항목 | 내용 |
|------|------|
| 책임 | 회원 설정 값을 보관한다 |

속성: unit(DistanceUnit, 기본 KM), recordVisibility(RecordVisibility, 기본 PUBLIC. 가입 단계 선택을 없앴다, FR-028), courseMapShared(boolean, 기본 false), profileVisibility(ProfileVisibility, 기본 PRIVATE), notificationPrefs(EnumMap<NotificationType, Boolean>, 기본 모두 true. v0.11 새 알림 종류 9개도 기본 true), watchLinked(boolean, 기본 false), theme(ScreenTheme, 기본 SYSTEM, 칼럼 screen_theme), runSettings(RunSettings), battleSettings(BattleSettings). (v0.11, FR-028, FR-175, FR-203, D-92)

| 시그니처 | 설명 | 사전 조건 | 사후 조건 / 예외 |
|---------|------|----------|-----------------|
| `void apply(SettingChange change)` | 설정 하나 변경 | change.item이 동의 항목이 아님 | change.item.validate(value) 실패면 BusinessRuleException(SETTING_INVALID) |
| `void changeTheme(ScreenTheme t)` | 화면 테마 변경 | | theme = t. apply가 item THEME일 때 부른다 (v0.11, FR-203, D-92) |
| `boolean allowsPush(NotificationType type)` | 푸시 허용 | | FR-175 |
| `void rememberBattleDistance(StandardDistance d)` | 기본 대결 거리 저장 | d.isRandomBattle() | FR-080, N-10. Member.rememberBattleDistance만 부른다 |
| `MemberSettingsView view()` | 읽기 전용 사본 | | 모든 항목을 복사한 record. 변경 연산이 없다 |
| `static MemberSettings defaults(RecordVisibility chosen)` | 가입 기본값 | | srs.md 6.3 1.2. 가입(signUp)·게스트 시작(guest) 모두 PUBLIC을 넘긴다 (v0.11, FR-028) |

##### ScreenTheme (enum) (v0.11, FR-203, D-92)
상수 SYSTEM(기기 설정 따름), LIGHT, DARK. 자료사전 화면테마와 1:1. 앱은 DataStore 사본(SettingsSnapshot.theme)으로 바로 적용한다. 워치 테마는 바꾸지 않는다.

##### MemberSettingsView (조회, record)
v0.11에서 더하는 항목: theme(ScreenTheme), accountType(AccountType), linkedProvider(ProviderType, 게스트면 null), battleSettings.proximityAlert(boolean). 계정 유형은 이 본인 전용 응답(GET /me/settings)에만 있다(FR-006, D-90) (v0.11, FR-199, FR-203, FR-109).

##### RunSettings / BattleSettings (값, record)
RunSettings: target(FreeRunTarget), voice(boolean), vibration(boolean), followCoachPlan(boolean). `static defaults()` = FREE, true, true, false.
BattleSettings: defaultDistance(StandardDistance), voice, vibration, gapAlertInterval(GapAlertInterval), proximityAlert(boolean, 칼럼 battle_proximity_alert) (v0.11, FR-109, FR-196, D-92). `static defaults()` = KM5, true, true, KM1, proximityAlert true (v0.11, FR-109). `withDefaultDistance(StandardDistance)`, `BattleSettings withProximityAlert(boolean on)` (v0.11, FR-109). BATTLE_SETTINGS 값 JSON에 proximityAlert가 없으면 현재 값을 유지한다(SettingItem.BATTLE_SETTINGS.applyTo가 병합, 옛 앱 호환) (v0.11, D-92). 키가 있어도 값이 null이면 없는 것으로 본다 (v0.11 구현 반영, U5-A-5).

##### ConsentHistory
| 시그니처 | 설명 | 사후 조건 / 예외 |
|---------|------|-----------------|
| `void record(ConsentItem item, boolean agreed, LocalDateTime at)` | 이력 덧붙이기 | 이전 이력을 지우지 않는다(FR-017) |
| `boolean isAgreed(ConsentItem item)` | 가장 최근 값 | 이력이 없으면 false |
| `void wipe()` | 탈퇴 때 비우기 | 감사 로그의 동의 기록은 AuditRecord에 따로 남아 6개월 보존된다 |
| `List<ConsentRecord> records()` | 읽기 전용 목록 | |

##### TierProgress
| 항목 | 내용 |
|------|------|
| 책임 | 티어 평가에 쓸 유효 러닝 누계를 보관한다 |

속성: validRunCount(int ≥ 0), recentScores(최근 10개, Deque<RunningScore>), totalValidDistance(Distance).

| 시그니처 | 설명 | 사후 조건 / 예외 |
|---------|------|-----------------|
| `OptionalDouble addValidRun(RunScoreInput input)` | 누계 갱신 | input.validRunOrdinal ≤ validRunCount면 이미 반영한 순번이라 무시하고 비어 있음. 아니면 점수를 넣고 창 크기를 evaluationWindowBetween(validRunCount, 순번)으로 정한다: 3이면 최근 3회 평균, 10이면 최근 10회 평균을 돌려주고, 비면 비어 있음(FR-022, FR-027). validRunCount ← 순번(직전 반영 순번). 1 넘게 건너뛰면 경고 로그를 남기되 그 사이 판정 지점은 놓치지 않는다(D-40) |
| `static OptionalInt evaluationWindowBetween(int lastOrdinal, int newOrdinal)` | 평가 창 크기 | lastOrdinal < p ≤ newOrdinal인 판정 지점 p(3 또는 10의 배수) 가운데 가장 큰 것으로 정한다. p = 3이면 3, 10의 배수면 10, 지점이 없으면 비어 있음. 예: (2, 3) → 3, (2, 4) → 3, (9, 11) → 10, (2, 11) → 10, (3, 9) → 비어 있음. RunimalProfile.onValidRun과 같은 순번 규칙(D-37, D-40) |
| `void addLateValidRun(RunScoreInput input)` | 늦게 온 유효 러닝 | 뒤 순번이 먼저 반영돼 이미 지난 순번(ordinal ≤ validRunCount)이다. 판정하지 않고 거리를 더하며, 최근 10회 창 안의 순번(ordinal > validRunCount − 10)이면 점수를 창에 넣고 가장 오래된 점수를 밀어낸다. 아직 오지 않은 순번이면 IllegalArgumentException (v0.11 구현 반영, U5-A-1, D-169) |
| `Distance totalValidDistance()` | 누적 유효 거리 | 레벨 계산용 |

RunScoreInput(record: score(double), distance(Distance), validRunOrdinal(int ≥ 1))은 running.TierUpdateListener가 넘기는 값이다. 매개변수를 4개 이하로 두려고 묶었다.

##### SignUpForm (입력) (v0.11, FR-003, D-90)
속성: nickname(String, nullable), consents(Map<ConsentItem, Boolean>). v0.10의 birthYear, gender, photoUrl, intro, recordVisibility, tags는 지웠다(요청에 오면 무시, F.2.2). 출생연도·성별·사진·소개는 프로필 편집(FR-007)으로만 넣는다 (v0.11, FR-003, FR-119, D-90, D-91).

| 시그니처 | 설명 | 사후 조건 / 예외 |
|---------|------|-----------------|
| `void validate()` | 6.3 1.2 의사결정표 | RequiredConsents.check(consents)를 부른다: 필수 동의(TERMS·PRIVACY·LOCATION) 중 false가 있으면 BusinessRuleException(CONSENT_MISSING), AGE_14_PLUS가 거짓이면 UNDER_14. 닉네임이 있으면 Nickname.of 검사(NICKNAME_INVALID). TAG_INVALID는 이 경로에서 더 나오지 않는다 (v0.11, FR-003, FR-004). v0.11 7.13.5: 가입 경로(SignUpForm)는 닉네임을 검사하지 않는다. 제공자 이름은 SignUpService가 Nickname.fromProviderName으로 고친다(D-143) (v0.11, 7.13.5 TF-VA-32) |

v0.10의 `missingRequiredConsents()`와 `isUnder14(int currentYear)`는 RequiredConsents로 옮기고 지운다. 출생연도가 없으므로 만 14세 판정은 AGE_14_PLUS 동의 하나로 한다 (v0.11, FR-004).

##### GuestStartForm (입력) (v0.11, FR-197, FR-004)
속성: consents(Map<ConsentItem, Boolean>). 필수 AGE_14_PLUS·TERMS·PRIVACY·LOCATION = true, 선택 MARKETING·LLM_TRANSFER(빠지면 false). HEART_RATE·FACE_TRANSFER는 받지 않는다(그 기능을 처음 쓸 때 동의 변경으로 받는다).

| 시그니처 | 설명 | 사후 조건 / 예외 |
|---------|------|-----------------|
| `void validate()` | 필수 동의 확인 | RequiredConsents.check(consents): CONSENT_MISSING, AGE_14_PLUS 거짓이면 UNDER_14. consents에 가입 때 받지 않는 항목(HEART_RATE, FACE_TRANSFER)이 있으면 IllegalArgumentException(400, D-164) (v0.11, test.md v0.6 TF-BE-30) |

##### RequiredConsents (member 패키지 내부) (v0.11, FR-003, FR-004)
SignUpForm·GuestStartForm이 함께 쓰는 필수 동의 검사다. `static void check(Map<ConsentItem, Boolean> consents)`: 필수 동의(TERMS·PRIVACY·LOCATION) 중 거짓·빠짐이 있으면 BusinessRuleException(CONSENT_MISSING), AGE_14_PLUS가 거짓이면 BusinessRuleException(UNDER_14). 두 입력 클래스에 같은 검사가 중복되지 않게 한곳에 둔다.

##### SettingChange (입력, record)
속성: item(SettingItem), value(String), notificationType(NotificationType, ALERT 항목일 때만). 연산: `boolean isConsentChange()`(item.consentItem() 존재), `Optional<ConsentItem> consentItem()`. 동의 항목은 SettingChange로 받지 않는다. changeSetting은 isConsentChange()가 true면 거부하고, 동의는 giveConsent·withdrawConsent로만 바꾼다(5.6 D-36).

##### SettingItem (enum)
상수 14개(자료사전 설정항목 13개 + THEME). 상수별 `void validate(String value)`와 `Optional<ConsentItem> consentItem()`을 가진다. 예: LOCATION_CONSENT.consentItem() = LOCATION, UNIT.validate는 KM·MI만 허용. THEME.validate는 ScreenTheme 이름("SYSTEM"·"LIGHT"·"DARK")만 허용한다 (v0.11, FR-203). BATTLE_SETTINGS의 값 JSON에 proximityAlert(Boolean?)를 더하고, 빠지면 현재 값을 유지한다(applyTo 병합) (v0.11, FR-109, D-92).

##### ConsentItem (enum)
AGE_14_PLUS, TERMS, PRIVACY, LOCATION(필수 4개, `isRequired()` = true) / MARKETING, LLM_TRANSFER(가입 때 선택) / HEART_RATE, FACE_TRANSFER(사용 시점에 받음, `collectedAtSignUp()` = false). 철회할 수 있는 항목은 LOCATION과 선택 동의 4개(FR-013~FR-016, FR-025). LOCATION은 필수지만 철회 가능하다(Q-74).

##### AuthService
| 항목 | 내용 |
|------|------|
| 책임 | 로그인 유스케이스를 조정한다 |
| 의존 | Map<ProviderType, SocialLoginGateway>, MemberRepository, TokenIssuer, Clock |

| 시그니처 | 설명 |
|---------|------|
| `LoginResult login(ProviderType provider, String providerToken)` | SQ-17. 제공자 무응답 → LoginResult.failed(PROVIDER_UNAVAILABLE), 인증 실패 → failed(AUTH_FAILED), 회원 있음 → Member.recordAccess(now) 뒤 loggedIn(토큰 쌍), 없음 → signUpRequired(가입 티켓). 계약은 그대로다 (v0.11, FR-001, FR-200, D-89) |
| `void logout(long memberId, String refreshToken)` | 갱신 토큰 폐기. 기기 미전송 기록은 앱이 지우지 않는다(FR-002). 폐기 목록에 쓸 수 없으면 TokenIssuer.revoke가 ProviderUnavailableException을 던지고 web.auth가 503 TEMPORARILY_UNAVAILABLE로 바꾼다(fail-closed) (v0.11, CR34-05, CR34-25) |
| `TokenPair refresh(String refreshToken)` | 접근 토큰 재발급(D-19). TokenIssuer.redeemRefreshToken(검증 + 폐기, 한 번만 쓰기)으로 회원 ID를 얻고, 탈퇴했거나 없는 회원이면 AuthFailureException. 폐기 목록을 확인할 수 없으면 ProviderUnavailableException(→ 503 TEMPORARILY_UNAVAILABLE, fail-closed). 발급 때 Member.recordAccess(now) (v0.11, CR34-05, CR34-25, D-89) |
| `TokenPair resumeGuest(String credential, LocalDateTime now)` | POST /auth/guest/session. GuestCredential.parse → 회원 조회 → Member.matchesGuestSecret(상수 시간 비교) → TokenIssuer.issue(memberId), Member.recordAccess(now). 형식 위조·해시 불일치·이미 연동한 회원·탈퇴 회원은 모두 AuthFailureException 하나로(→ 401 본문 없음, 구분해 알리지 않는다) (v0.11, FR-197, D-89) |
| `SocialLinkResult linkSocialAccount(long memberId, ProviderType provider, String providerToken)` | POST /me/social-account. SocialLoginGateway.authenticate(실패 AuthFailureException → 401 AUTH_FAILED, 무응답 → 503 PROVIDER_UNAVAILABLE) → MemberRepository.findByAccount가 요청한 회원이 아닌 다른 회원을 찾으면 BusinessRuleException(ALREADY_MEMBER, 두 계정을 합치지 않는다). 찾은 회원이 요청한 회원 자신이면(이미 연동했고 응답이 유실돼 다시 보낸 요청) 건너뛰고 → Member.linkSocialAccount(account)(이미 LINKED면 ALREADY_LINKED, 앱은 이때도 기기 게스트 자격 증명을 지운다) → 저장 (v0.11 구현 반영, U5-A-6, D-177). 코드는 BE5-A-5로 이 순서에 맞춘다. 토큰은 바꾸지 않는다 (v0.11, FR-198, D-90). ALREADY_MEMBER 문구는 "이미 가입된 계정"(srs FR-198, D-155) (v0.11, test.md v0.6 TF-IT-31) |
| `int purgeExpiredRevocations(LocalDateTime now)` | 토큰 만료가 지난 폐기 목록 행 정리. batch.member의 RevocationPurgeJob이 3시에 부른다. TokenIssuer.purgeExpiredRevocations에 넘기고 지운 수를 돌려준다 (v0.11, CR34-05, CR34-02) |

##### SignUpService
| 항목 | 내용 |
|------|------|
| 의존 | MemberRepository, ReservedNicknameChecker, TokenIssuer, AuditService, Clock, SecureRandom(GuestCredential.issue에 넘긴다) (v0.11, D-89) |

| 시그니처 | 설명 |
|---------|------|
| `SignUpResult signUp(SignUpCommand command)` | 가입 티켓 검증 → form.validate → 닉네임 확정(겹치면 숫자 4자리, 붙여 20자를 넘으면 앞 16자 + 숫자 4자리, 5회 재시도) → Member.signUp → 저장 → 감사(가입동의) → 토큰 발급. FR-003 "닉네임을 넣지 않으면 제공자제공이름"은 앱이 지킨다: 앱은 제공자 SDK 로그인에서 받은 이름을 닉네임 칸에 미리 채우고, 사용자가 비워 보내도 그 이름을 닉네임으로 채워 보낸다. 서버는 제공자 이름을 받거나 저장하지 않는다(가입 티켓은 계정 ID만 싣는다, NFR-024 최소 수집). 닉네임이 끝내 비어 오면(제공자가 이름을 주지 않음) 서버 기본값 Nickname.numbered("러너" + 숫자 4자리, 겹치면 다른 숫자로 20번, 그다음 5자리) (v0.8, TF-VA-08, D-75) (v0.11, FR-003, D-90) — v0.11 7.13.5: 닉네임 확정은 Nickname.fromProviderName(form.nickname)이 비면 numbered, 있으면 그 값(2자 미만은 비어 있음, 20자 초과는 앞 20자)에 겹침 처리를 한다. NICKNAME_INVALID로 거부하지 않는다(D-143, D-75의 v0.10 "서버 거부" 문장 대체) (v0.11, 7.13.5 TF-VA-32) |
| `GuestStartResult startAsGuest(GuestStartForm form)` | POST /members/guest. form.validate → Nickname.numbered(겹치면 다른 숫자로 20번, 그다음 5자리) → Member.guest(nickname, consents, now) → 저장(회원 ID 확정) → GuestCredential.issue(회원 ID, SecureRandom) → Member.attachGuestCredential(해시)(같은 트랜잭션, D-128) → 감사(SIGN_UP_CONSENT, 행위자 RUNNER) → 토큰 발급. 자격 증명 원문은 결과에 한 번만 싣고 저장하지 않는다. 접속지별 생성 한도(제안 시간당 10회, 429)는 web.auth AuthAttemptGuard가 이 연산을 부르기 전에 확인한다. (v0.11, FR-197, FR-004, D-89, D-128) — v0.11 7.13.5: srs FR-197이 같은 접속지 1시간 10회(제안)로 보완되어 AuthAttemptGuard 한도와 같다(E-31 닫음) (v0.11, 7.13.5 TF-VA-34). 닉네임은 4자리 20번 → 5자리 5번, 그래도 겹치면 503 TEMPORARILY_UNAVAILABLE(D-164) (v0.11, test.md v0.6 TF-BE-30) |

##### ProfileService
| 시그니처 | 설명 |
|---------|------|
| `ProfileEditResult editProfile(long memberId, ProfileEdit edit)` | 닉네임 중복(회원·가상) 검사 → Member.editProfile. 닉네임 변경 횟수 제한 없음(FR-007). ProfileEdit에서 tags를 지웠다(요청에 오면 무시) (v0.11, FR-007, D-91) |

##### MemberSettingService
| 항목 | 내용 |
|------|------|
| 의존 | MemberRepository, AuditService, DomainEventPublisher, LegalDocumentCatalog(설정 파일), Clock |

| 시그니처 | 설명 |
|---------|------|
| `SettingResult changeSetting(long memberId, SettingChange change)` | 동의가 아닌 설정만. Member.changeSetting → 저장. 새 항목 THEME(값 SYSTEM·LIGHT·DARK), BATTLE_SETTINGS의 proximityAlert(빠지면 현재 값 유지). 위반은 그대로 SETTING_INVALID (v0.11, FR-203, FR-109, D-92) |
| `ConsentResult giveConsent(long memberId, ConsentItem item)` | Member.giveConsent → 저장 → 감사(동의변경). 위치·심박·얼굴 변환·LLM 전송 동의를 사용 시점에 받을 때도 이 연산이다(FR-013~FR-016, FR-019) |
| `ConsentResult withdrawConsent(long memberId, ConsentItem item)` | Member.withdrawConsent → 저장 → 감사(동의변경) → item이 LOCATION·HEART_RATE면 ConsentWithdrawn 발행(FR-025, FR-026, FR-029) |
| `void rememberBattleDistance(long memberId, StandardDistance d)` | battle.MatchingService가 매칭 요청 때 부른다. Member.rememberBattleDistance(d) → 저장(FR-080). 다른 패키지가 Member의 설정 객체를 직접 고치지 않게 하는 유일한 경로다(design-review.md A44) |
| `LegalDocument legalDocument(DocumentType type)` | 문서 주소(FR-018). 대결 상대 종류 고지는 TERMS·HELP 문서에 둔다(FR-106) |
| `MemberSettingsView settingsOf(long memberId)` | GET /me/settings. 앱 설정 사본(SettingsRepository.refresh)이 읽는 원본이다: 위치·심박·얼굴 변환·LLM 동의, 워치 연동, 코스지도공유, 출생연도, 안내 설정. Member.getSettings의 불변 사본이고 서버 배치 설정(실시간 방식 등)은 싣지 않는다(design-review.md M33, D-57). v0.11에서 화면 테마(theme), 계정 유형(accountType), 연동 제공자(linkedProvider, 게스트면 null), 근접 알림 여부(battleSettings.proximityAlert)를 더한다. 앱은 accountType으로 게스트 자료 유실 고지(FR-199)를 정한다 (v0.11, FR-199, FR-203, FR-109) |

LegalDocumentCatalog(@Component, 설정 runkk.legal.*: termsUrl, privacyPolicyUrl, locationTermsUrl, helpUrl)는 생성 때 네 주소가 하나라도 비면 IllegalStateException으로 기동을 멈춘다(필수 설정, 메시지에는 설정 키 이름만). `LegalDocument find(DocumentType type)` (v0.11, CR34-27).

##### WithdrawalService
| 항목 | 내용 |
|------|------|
| 의존 | MemberRepository, List<WithdrawalGuard>, List<IdleGuestWithdrawalGuard>(v0.11 검증, D-141), AuditService, DomainEventPublisher, Clock |

| 시그니처 | 설명 |
|---------|------|
| `int purgeWithdrawn(LocalDateTime now)` | batch.member의 WithdrawalPurgeJob이 3시에 부른다. MemberRepository.findWithdrawnBefore(now − 7일)로 고른 회원마다 Member.purgeResidual() → 저장. 처리 수를 돌려준다. member 자기 자료만 다루고 다른 패키지 자료는 각 패키지의 탈퇴 수신자가 지운다(D-28, design-review.md M3) |
| `WithdrawalResult withdraw(long memberId)` | 6.3 1.5 의사결정표. 확인 단계("정말 탈퇴할까요")는 앱 화면이 하고 서버는 확인된 요청만 받는다(5.6 D-36). 가드 중 하나라도 거부 사유를 주면 rejected(사유). 통과하면 Member.withdraw → 감사(회원탈퇴) → MemberWithdrawn 발행 → withdrawn |
| `int withdrawIdleGuests(LocalDateTime now)` | batch.member의 IdleGuestWithdrawalJob이 3시에 부른다. MemberRepository.findIdleGuests(now − GUEST_IDLE_LIMIT)로 마지막 접속이 1년 넘은 ACTIVE 게스트를 골라 회원마다 Member.withdraw → 감사(회원탈퇴) → MemberWithdrawn 발행(각 패키지 탈퇴 수신자가 자료를 지운다). 처리 수를 돌려준다. 상수 GUEST_IDLE_LIMIT = 1년(D-113, 운영 기준값이 아님). 연동 회원은 대상이 아니다. IdleGuestWithdrawalGuard만 본다(구현 battle.ActiveBattleGuard 하나, 형식 검사 없이 주입으로 고른다, D-141): 진행 중 대결이 있으면 그 게스트를 건너뛰고 다음 날 다시 본다. CrewLeaderGuard는 부르지 않고, 크루장 게스트의 크루는 CrewWithdrawalListener가 넘기거나 지운다(FR-200). 감사는 WITHDRAWAL, 행위자 역할 SYSTEM, 행위자 ID "system:idle-guest"다(D-118) (v0.11, FR-200, D-89, D-113, D-118) (v0.11 검증, D-141) — GUEST_IDLE_LIMIT = 365일, findIdleGuests(now − 365일)은 lastAccessAt ≤ 기준(정각 포함)이라 정확히 365일 지난 게스트도 그날 3시에 파기한다(D-156) (v0.11, test.md v0.6 TF-IT-25). 대상은 lastAccessAt ≤ now − 365일(1년 정각 포함) (v0.11, test.md v0.6 TF-BE-31) |

##### TierLevelService
| 시그니처 | 설명 |
|---------|------|
| `TierChange recordValidRun(long memberId, RunScoreInput input)` | running.TierUpdateListener가 유효 기록 저장 커밋 뒤 동기로 부른다. Member.recordValidRun(input, policy.tierBoundary()) 뒤 저장. 같은 순번이 다시 오면 TierProgress가 무시한다(멱등). 수신자가 실패해 빠진 순번은 running.TierReconcileService가 0시 10분에 같은 연산으로 채운다 (v0.11, D-108). 부르는 쪽은 running.TierApplier다 (v0.11 구현 반영, U5-A-1, D-169) |
| `TierChange recordLateValidRun(long memberId, RunScoreInput input)` | 뒤 순번이 먼저 반영돼 구멍이 된 유효 러닝을 늦게 반영한다. Member.recordLateValidRun(input) 뒤 저장. 판정하지 않는다. running.TierApplier만 부른다 (v0.11 구현 반영, U5-A-1, D-169) |

member는 running의 RunRecordSaved를 받지 않는다. 받으면 member → running 의존이 생겨 running → member(탈퇴·철회 사건 수신, 티어 갱신 호출)와 순환이 되기 때문이다. 그래서 running 쪽 수신자가 member를 부른다(5.6 D-03 예외).

##### MemberQueryService
| 항목 | 내용 |
|------|------|
| 책임 | 다른 패키지가 읽을 회원 정보 투영을 내준다 |
| 의존 | MemberRepository |

| 시그니처 | 설명 |
|---------|------|
| `Map<Long, MemberSummary> summaries(Collection<Long> memberIds)` | 표시명·티어·공개 범위. 한 번의 조회(N+1 방지, 6.5). 탈퇴 회원은 DisplayName.WITHDRAWN. 태그는 싣지 않는다 (v0.11, D-91) |
| `MemberPreference preferenceOf(long memberId)` | MemberPreference(unit, recordVisibility, courseMapShared, profileVisibility, llmAgreed). analysis·profile이 엔티티 대신 읽는다 |
| `MemberProfile profileOf(long memberId, int year)` | 프로필 화면의 자기소개 항목 MemberProfile(level, photoUrl, intro, ageBand("20대", 출생연도 없으면 null), gender). MemberSummary·MemberPreference에 없는 항목만 담은 좁은 투영이고 profile.ProfileQueryService가 쓴다(FR-005, D-41) (v0.11, CR2-U1) |
| `int appliedValidRunCount(long memberId)` | 티어에 반영한 유효 러닝 순번(TierProgress.validRunCount). running.TierApplier가 넘길 기록이 지난 순번인지(구멍인지) 가른다(12.2 D3) (v0.11, D-108) (v0.11 구현 반영, U5-A-1, D-169) |
| `boolean isActive(long memberId)` | 회원이 있고 ACTIVE인가. web.auth의 ActiveMembers(탈퇴 회원의 남은 접근 토큰 거부)와 reward.RewardReconcileService(탈퇴 회원 지갑을 다시 만들지 않음)가 쓴다 (v0.11, CR34-03, CR34-09) |
| `OptionalInt ageOf(long memberId, int year)` | year 기준 나이(Member.ageIn). 출생연도가 없으면 비어 있음. analysis.RecordQueryService가 심박 구간의 최대 심박(220 − 나이)을 정할 때 쓴다(design-review.md M4) |
| `boolean isNicknameTaken(String nickname)` | 회원 닉네임이 이미 있는가(MemberRepository.existsByNickname). battle.VirtualRecordService가 가상 닉네임을 만들 때 nicknameTaken으로 넘긴다(design-review.md H-11', R11) |
| `boolean hasConsent(long memberId, ConsentItem item)` | battle(위치), avatar(얼굴 변환), analysis(LLM 전송)가 동의 여부만 묻는다 |
| `PushDecision pushDecision(long memberId, String notificationType)` | WITHDRAWN(탈퇴 회원) / MUTED(그 종류 푸시를 껐음) / ALLOWED. notification이 쓴다. 종류는 NotificationType 이름 문자열로 받는다(member가 notification을 import하면 notification → member와 순환이다. v0.11 정리에서 코드에 맞춤) |
| `Map<Long, GhostOwner> ghostEligibleOwners(Set<Tier> tiers, long excludeMemberId)` | 상태 ACTIVE, recordVisibility PUBLIC, tier ∈ tiers인 회원(요청자 제외). GhostOwner(memberId, displayName, tier). battle.GhostMatcher가 쓴다(A-07) |
| `Set<Long> rankedMemberIds()` | 상태 ACTIVE이고 recordVisibility.includedInPersonalRanking()인 회원. analysis.RankingService가 개인 랭킹 대상으로 쓴다(FR-070) |
| `Map<Long, DisclosureLevel> disclosureOf(long viewerId, Collection<Long> targetIds)` | 열람자에게 대상 회원의 프로필을 어디까지 보이는가. DisclosureLevel.of(viewerId, targetId, status, profileVisibility)로 정한다: 본인 SELF, 탈퇴 WITHDRAWN, 개인정보공개 공개 PUBLIC, 비공개 PRIVATE. 한 번의 조회. profile.ProfileQueryService(항목 거르기)와 crew.CrewQueryService.members(크루원 목록의 공개 대상)가 같은 판정을 쓴다(D-52, design-review.md H-18) |
| `Map<Long, Gender> shownGendersOf(Collection<Long> memberIds)` | 다른 사람에게 보이는 캐릭터 성별(v0.17, D-336, D-327 구현). 넘긴 ID마다 값이 있다(없는 회원도 대체값). 성별이 남·여이고 성별 공개(PUBLIC)면 그 값, 아니면 ShownGender.fallbackOf(memberId). battle(BattleStartService·GhostMatcher), analysis(RankingService.viewOf), profile(ProfileQueryService.view)이 쓴다. 지운 publicGendersOf를 대체한다 |

v0.11에서 지운 연산: `TagSet tagsOf(long memberId)`. runimal이 태그 없이 판정하고(FR-152) 크루 추천(FR-118)을 지워 쓰는 곳이 없다. 12.2 D5(runimal → member)는 MemberWithdrawn 수신만 남는다 (v0.11, FR-119, FR-152, D-91).

다른 패키지는 Member 엔티티나 MemberRepository를 받지 않고 이 서비스만 부른다(5.6 D-32).

ShownGender(member 패키지, `public final class`, v0.17 D-336): 회원 표시 성별 규칙 한 곳이다. `static Gender of(long memberId, Gender gender, ProfileVisibility visibility)`(패키지 전용)는 성별이 남·여이고 공개면 그 값, 아니면 fallbackOf를 돌려준다. `public static Gender fallbackOf(long memberId)`는 회원 ID를 SplitMix64 마무리 함수로 섞어 한 비트로 남·여를 정한다(저장 칼럼 없음, 볼 때마다 같음). battle의 OpponentGenders.shownOf가 맵에 없는 회원에 이 대체값을 쓴다.

##### MemberLoader (member 패키지 내부 보조) (v0.11, CR2-U7)
`static Member load(MemberRepository repository, long memberId)`: member 응용 서비스가 함께 쓰는 회원 읽기. 토큰으로 정한 회원 ID라 없으면 IllegalArgumentException(입력 오류). 같은 조회·예외 코드가 서비스마다 중복되지 않게 한다. 패키지 밖에 열지 않는다.

##### 포트 인터페이스 (member가 정의)
| 인터페이스 | 시그니처 | 구현 |
|-----------|---------|------|
| MemberRepository | `Optional<Member> findById(long)`, `Optional<Member> findByAccount(SocialAccount)`, `boolean existsByNickname(String)`, `List<Member> findWithdrawnBefore(LocalDateTime)`, `List<Member> findIdleGuests(LocalDateTime before)`(ACTIVE 게스트 중 lastAccessAt ≤ before(정각 포함, D-156 (v0.11, test.md v0.6 TF-IT-25)), 부분 색인 idx_member_idle_guest, v0.11 FR-200), `Member save(Member)`, `Map<Long, MemberSummary> summaries(Collection<Long>)`, `List<GhostOwner> ghostOwners(Set<Tier>, long)` (v0.11, FR-200) | infra.member.JpaMemberRepository. member 밖에서 쓰지 않는다(ArchUnit 규칙, 13.1) |
| SocialLoginGateway | `SocialAccount authenticate(String providerToken)` (실패 AuthFailureException, 무응답 ProviderUnavailableException), `ProviderType provider()` | infra.integration.auth.KakaoLoginGateway, GoogleLoginGateway |
| TokenIssuer | `TokenPair issue(long memberId)`, `String issueSignUpTicket(SocialAccount)`, `SocialAccount redeemSignUpTicket(String)`, `long verify(String accessToken)`, `AccessGrant verifyGrant(String accessToken)`(회원 ID + 만료 시각, 실패 AuthFailureException, v0.11 D-110), `void revoke(String refreshToken)`(이미 폐기됐거나 읽을 수 없으면 아무것도 하지 않음, 폐기 목록에 쓸 수 없으면 ProviderUnavailableException), `long redeemRefreshToken(String refreshToken)`(검증 뒤 폐기하고 회원 ID, 위조·만료·폐기 AuthFailureException, 폐기 목록을 확인할 수 없으면 ProviderUnavailableException), `int purgeExpiredRevocations(LocalDateTime now)`. 회원 토큰 전용이다. web.auth.AuthFilter가 verify를, realtime(TokenHandshakeInterceptor, 웹소켓 연결)이 verifyGrant를 부른다(12.2 D54). 관리툴 토큰은 operator.OperatorTokenIssuer(D-42) (v0.11, CR34-05, D-110) | infra.integration.auth.JwtTokenIssuer. 갱신 토큰 폐기 목록은 infra.integration.auth의 RefreshTokenRevocations(구현 JdbcRefreshTokenRevocations, PostgreSQL refresh_token_revocation, V14)에 둔다: `boolean claim(String jti, Instant expiresAt)`(처음 쓰면 true, 회전), `void revoke(String jti, Instant expiresAt)`, `int purgeExpired(Instant now)`. 보안 기록이라 Redis 장애·재시작과 무관하게 남고(5.5 "Redis 장애는 매칭·실시간만"과 같은 원칙), 저장소를 쓸 수 없으면 갱신·로그아웃을 거부한다(fail-closed, 503 TEMPORARILY_UNAVAILABLE). 토큰 원문은 저장하지 않는다 (v0.11, CR34-05) |
| WithdrawalGuard | `Optional<String> check(long memberId)` (거부 사유) | battle.ActiveBattleGuard, crew.CrewLeaderGuard |
| IdleGuestWithdrawalGuard | `Optional<String> check(long memberId)` (건너뛸 사유). 미이용 게스트 파기(withdrawIdleGuests)만 본다. WithdrawalGuard 목록에서 구현 형식을 골라내지 않으려고 따로 둔다 (v0.11 검증, D-141) | battle.ActiveBattleGuard |
| ReservedNicknameChecker | `boolean isReserved(String nickname)` | battle.VirtualNicknameChecker |

MemberSummary(record: id, displayName, tier, profileVisibility)는 다른 패키지가 회원 여러 명의 표시 정보를 한 번에 읽는 조회 전용 값이다(N+1 조회 방지, 6.5). MemberQueryService.summaries로만 내준다. tags 항목은 지웠다(12.2 D22) (v0.11, D-91).

MemberProfile(record: level(int), photoUrl(String?), intro(String?), ageBand(String?), gender(Gender?))은 profile이 읽는 자기소개 항목 투영이다. MemberQueryService.profileOf로만 내준다 (v0.11, CR2-U1).

##### DisclosureLevel (enum, 다형성)
| 상수 | 조건 | showsProfile() |
|------|------|----------------|
| SELF | viewerId == targetId | true |
| WITHDRAWN | 대상이 탈퇴 | false |
| PUBLIC | 개인정보공개 = 공개 | true |
| PRIVATE | 개인정보공개 = 비공개(기본) | false |

`static DisclosureLevel of(long viewerId, long targetId, MemberStatus status, ProfileVisibility visibility)`는 위 표 순서로 처음 맞는 상수를 고른다. 공개 범위 규칙은 이 enum 한 곳에만 있다. 수준마다 화면에 남길 항목은 profile.ProfileDisclosure가, 크루원 목록에서 프로필(Runimal 유형 등)을 보일지는 showsProfile()이 정한다(D-52).

##### 배치고사 진행 (v0.30~v0.32, FR-274~FR-278, D-475~D-490, D-514·D-516~D-518, D-529·D-536)

전체 명세는 [05-21 2.1](05-21-설계-배치고사-화면개편-성.md)과 v0.32 고친 명세 [05-22](05-22-설계-배치고사-리뷰반영-성.md) 2.1이다. 요약:

- `PlacementProgress`(엔티티, 표 placement_progress, 키 member_id): 한 회원의 배치고사 진행(센 판, 임시 티어, 확정 시점)을 보관한다. `ROUNDS = 3`, `start`, `nextRound`, `basis`, `provisionalTier`, `isCompleted`, `hasCounted`, `count(PlacementRoundInput, TierBoundary): PlacementCount`(A-37), `correctVerdict`, `confirmedRunOrdinal`, `rounds`.
- `PlacementRound`(@Embeddable, 표 placement_round), `PlacementRoundInput`·`PlacementBasis`·`PlacementView`(record), `PlacementCount`(sealed: Provisional·Confirmed·Ignored), `PlacementStatus`(IN_PROGRESS·COMPLETED·EXEMPT), `PlacementVerdict`(WIN·LOSE·DRAW).
- `PlacementProgressRepository`(포트, infra.member 구현: findById, lockOrCreate, findByIdForUpdate, save, deleteByMemberId, existsRound), `PlacementService`(view, lockForMatch → `Optional<PlacementBasis>`, countRound(행 잠금 뒤 판단, 행을 만들지 않음), correctVerdict, isCounted, isCompleted) (v0.31 변경, D-516·D-518). v0.32(D-536): `lockForMatch`는 잠근 뒤 `MemberRepository.refresh(Member)`(새 포트 연산)로 회원을 다시 읽어 ACTIVE가 아니면 `MATCH_FAILED`, 랭크 티어이거나 완료면 empty. `WithdrawalService` 두 탈퇴 경로는 탈퇴 가드 앞에서 `lockOrCreate`로 진행 행을 잠가 커밋 전 매칭과 직렬화하고 같은 트랜잭션에서 지운다(D-516 "행은 lockForMatch만 만든다"의 유일한 예외). `PlacementProgress`의 공개 멤버 `DUPLICATE`·`completedAt()`·`countedRounds()`·`getMemberId()`도 명세에 있다(D-529).
- 바뀐 것: `Member.confirmPlacement(Tier)` 신규, `Member.recordValidRun(RunScoreInput, TierBoundary, int placementOrdinal)`(재평가는 랭크 티어이고 10의 배수 p > placementOrdinal일 때만), `TierProgress`의 3회 창(PLACEMENT)과 FIRST_PLACEMENT 생성 제거(`reevaluationPointBetween`), `TierLevelService`가 Member를 먼저 읽고 진행 행을 그다음 읽어 placementOrdinal을 넘김(v0.31 D-517), `WithdrawalService` 두 탈퇴 경로가 같은 트랜잭션에서 배치 진행을 지움(D-489). 위 TierProgress·TierLevelService·Member 명세의 "3회째 첫 배치" 문장은 이 문단이 대신한다 (v0.30, D-484).
