> [런크크 객체지향 분석·설계서](../design-oo.md) 목차 · 이전: [F-v0.11-변경-명세](F-v0.11-변경-명세.md) · 다음: [Fc-v0.11-변경-명세-F-7-설계-결정-D-89](Fc-v0.11-변경-명세-F-7-설계-결정-D-89.md)
#### F.3.3 연결 종료 코드와 토큰 만료 (L29, D-110)

| 닫기 코드 | 뜻 | 앱 동작 |
|-----------|----|--------|
| 1000 NORMAL | 같은 회원의 새 연결이 이 연결을 대신함 | 재연결하지 않는다(새 연결이 이미 있다) |
| 1008 POLICY_VIOLATION | 핸드셰이크 인증 실패(기존). v0.13 D-216으로 대체: 핸드셰이크 인증 실패는 HTTP 401 업그레이드 거부이고 1008은 세션 속성이 없을 때의 방어 경로다 | 재연결하지 않고 Unauthorized |
| 1012 SERVICE_RESTARTED | 서버 재시작(기존) | 기존 재연결 간격(1·2·4초, 이후 5초) |
| **4401 TOKEN_EXPIRED** [신규] | 핸드셰이크에 쓴 접근 토큰의 만료 시각이 됨 | api.core `SessionRefresher.refreshNow()`로 토큰을 갱신하고 **곧바로** 재연결한다(재연결 간격·실패 횟수에 넣지 않는다). 갱신이 거부되면 LoginState가 거짓이 되고 채널을 Unauthorized로 닫는다. 재연결 뒤 대결·크루런 메시지는 메일함에서, 채팅은 `?after=`로 받는다 갱신 일시 오류(5xx·429·연결 실패)는 로그인을 유지하고 채널을 닫은 채 일반 재연결 백오프로 다시 시도하며, 거부(400·401·403)만 Unauthorized다. 곧바로 재연결이 1분 안에 3번 반복되면 일반 재연결 백오프로 넘긴다(D-153) (v0.11, test.md v0.6 TF-IT-28, TF-AN-33) |

- 서버: `TokenIssuer.verifyGrant(accessToken)` → `member.AccessGrant(memberId, expiresAt)`. `TokenHandshakeInterceptor`가 세션 속성에 `expiresAt`을 넣고, `SocketSessionRegistry.register(memberId, session, expiresAt)`이 `TaskScheduler`로 만료 시각에 4401로 닫는 작업을 예약한다. `LiveSocketHandler.handleTextMessage`도 받을 때마다 만료를 확인하고 지났으면 4401로 닫는다(예약 작업이 늦을 때의 방어).
- 대결·크루런·채팅 모두 같은 정책이다. 접근 토큰 수명(1시간)보다 긴 대결도 재연결 1회로 이어진다(FR-090·FR-091의 측정 계속 규칙 그대로).
- 폴링 대체 모드는 REST라 AuthFilter의 401 → TokenAuthenticator 갱신으로 이미 처리된다. 바꿀 것이 없다.

---

### F.4 폰 ↔ 워치 계약 (wear-contract)

#### F.4.1 경로 (WearPaths)

| 상수 | 경로 | 방향·전송 | 상태 |
|------|------|-----------|------|
| START_REQUEST | /runkk/start-request | 워치 → 폰, MessageClient | 유지 |
| WATCH_DISPLAY | /runkk/watch-display | 폰 → 워치, MessageClient | 유지(항목 추가) |
| HEART_RATE_BATCH | /runkk/heart-rate-batch | 워치 → 폰, DataClient | 유지 |
| END_REQUEST | /runkk/end-request | 워치 → 폰, MessageClient | 유지 |
| **PAUSE_REQUEST** | /runkk/pause-request | 워치 → 폰, MessageClient | 신규 |
| **PROXIMITY** | /runkk/proximity | 폰 → 워치, MessageClient(유실 허용) | 신규 |
| **LEAD_CHANGE** | /runkk/lead-change | 폰 → 워치, MessageClient | 신규 |
| WATCH_CAPABILITY | "runkk_watch"(능력 이름) | 워치 res/values/wear.xml 선언 | 유지(CR34-16 설계 반영) |

경로 중복 검사 대상은 경로 7개다.

#### F.4.2 메시지

| 메시지 | 항목 (Kotlin 이름 → 직렬화 이름) | 규칙 |
|--------|------------------------------|------|
| `WatchDisplayMessage` [변경] | `isRunning → "running"`(@SerialName, N3-6), `elapsedSeconds`, `distanceMeters`, `currentPaceSecondsPerKm?`, `heartRateBpm?`, `planDistanceMeters?`, `planPaceSecondsPerKm?`, `isHeartRateCollectionAllowed → "heartRateCollectionAllowed"`(@SerialName), `notice: String?`, **`isPaused → "paused"`(기본 false)**, **`isPausable → "pausable"`(기본 false)** | `isPausable`은 자유런 측정 중일 때만 참(대결·크루런 거짓, FR-194). `isPaused`면 워치는 "일시정지"와 재개 버튼을 보인다. notice 문구는 폰이 문자열 리소스로 바꿔 보낸다(D-111): 새 `WatchNotice.AUTO_END_SOON`·`AUTO_ENDED` |
| `PauseRequestMessage` [신규] | `action: Action`(PAUSE / RESUME), `requestedAtEpochMillis: Long` | 워치는 마지막으로 받은 표시의 `isPausable`이 참일 때만 보낸다. 폰은 `RunTracker`가 폰 버튼과 같은 규칙으로 처리한다. 자유런이 아니거나 상태가 맞지 않으면 무시하고 다음 표시로 상태를 바로잡는다 |
| `ProximityMessage` [신규] | `isActive → "active"`, `gapMeters: Int`(절댓값), `intervalMillis: Long`, `isAhead → "ahead"`, `sentAtEpochMillis: Long` | 폰이 간격을 계산해 보낸다(D-96). `isActive` 거짓이면 워치는 반복을 멈춘다. 워치는 마지막 메시지 뒤 3초(제안) 안에 새 메시지가 없거나 표시의 `isRunning`이 거짓이면 스스로 멈춘다 |
| `LeadChangeMessage` [신규] | `kind: Kind`(OVERTOOK / OVERTAKEN), `atEpochMillis: Long` | 워치가 1초 긴 진동 1회(FR-188·FR-196). 근접 알림 반복 중이어도 이 진동을 먼저 낸다 |
| `StartRequestMessage`, `EndRequestMessage`, `HeartRateBatchMessage` | 그대로 | — |

#### F.4.3 근접 알림 규칙 (폰 domain.run `ProximityAlertRule`)

- 켜는 조건: 모드 BATTLE(1:1 대결 4종 모두), `GuidanceSettings.isProximityAlertOn`, `WatchGateway.isConnected()`, 격차 값이 있음(`RunSession.gapMeters`), |격차| ≤ 100m(제안).
- 간격(ms) = 300 + (2000 − 300) × (clamp(|격차|, 5, 100) − 5) ÷ 95 (100m에서 2.0초, 5m 이하에서 0.3초, 선형, 제안). 앞서든 뒤처지든 같다. 결과는 10ms 단위로 반올림한다(D-158) (v0.11, test.md v0.6 TF-AN-25)
- 보내는 때: 켜짐·꺼짐이 바뀔 때, 간격이 100ms 이상 바뀔 때, 그렇지 않아도 2초마다 한 번(워치의 3초 만료를 막는 유지 신호). 격차는 랜덤 대결이면 서버 OpponentProgress(1초), 기록 대결이면 GapTracker(tick 1초)에서 온다. 이 회에 보낸 적이 없으면(RunWatchBridge.resetProximity 뒤) 바로 보낸다(D-158). 상대 연결 확인 중(OpponentProgress.connection = CHECKING)이면 격차 없음으로 보고 바로 끔을 보낸다(D-145) (v0.11, test.md v0.6 TF-AN-25) (v0.11, 7.13.5 TF-VB-20)
- 추월·역전은 `RunTracker.onLeadChanged`가 기존 화면 안내와 함께 `RunWatchBridge.publishLeadChange(kind)`를 부른다(근접 알림이 꺼져 있으면 보내지 않는다). 긴 진동은 근접알림여부만 따르고 진동 안내 설정과 무관하다(D-145) (v0.11, 7.13.5 TF-VB-19)

#### F.4.4 하위 호환

| 조합 | 동작 |
|------|------|
| 새 폰 + 옛 워치 | 옛 워치는 모르는 경로(PROXIMITY·LEAD_CHANGE)를 버리고(경로 필터), WatchDisplayMessage의 새 키는 `ignoreUnknownKeys = true`로 버린다. 일시정지 버튼·근접 알림이 없을 뿐 측정은 그대로 |
| 옛 폰 + 새 워치 | 새 워치는 `paused`·`pausable`이 없는 메시지를 기본값 false로 읽어 버튼을 숨긴다. PAUSE_REQUEST를 보내지 않는다. 근접 메시지가 오지 않으니 알림도 없다 |
| 직렬화 이름 | N3-6의 Boolean 이름 교정은 Kotlin 이름만 바꾸고 `@SerialName`으로 기존 JSON 키를 지킨다(D-97). 새 Boolean도 Kotlin `is…` 이름 + 접두어 없는 JSON 키 |
| 시험 | wear-contract 단위 시험: 메시지마다 왕복, 모르는 키 포함 해독, 새 키 없는 옛 JSON 해독(기본값), 경로 7개 중복 없음 |

---

### F.5 도메인 모델·DB

#### F.5.1 enum

| enum (패키지) | 상수 | 상태 |
|---------------|------|------|
| member.AccountType | GUEST, LINKED | 신규 |
| member.ScreenTheme | SYSTEM, LIGHT, DARK | 신규 |
| member.SettingItem | 기존 13개 + THEME(값 ScreenTheme 이름) | 변경 |
| crew.CrewRole | LEADER, **MANAGER**, MEMBER | 변경 |
| crew.CrewRelation | LEADER, **MANAGER**, MEMBER, OUTSIDER | 변경 |
| crew.PostKind | CHEER, CREW_RUN, **MEETUP**, **NOTICE**, **POLL**, **BOARD** (단일 테이블 구분 값, 10자 이하) | 변경 |
| crew.AttendanceAnswer | GOING, NOT_GOING, **UNDECIDED** (v0.10 VoteAnswer 개명, 기존 DB 값 유지) | 개명·변경 |
| crew.PollMethod | YES_NO, CHOICES | 신규 |
| crew.chat.ReactionKind | LIKE, HEART | 신규 |
| crew.chat.live.ChatRemovalReason | DELETED, HIDDEN | 신규 |
| report.ReportTargetKind | CHEER, SCHEDULE, NOTICE, POLL, BOARD, CHAT_MESSAGE | 신규 |
| report.ReportReason | ABUSE(욕설·비방), SEXUAL(음란), SPAM(광고·스팸), PRIVACY(개인정보 노출), OTHER(기타) | 신규 |
| report.ReportStatus | RECEIVED, AUTO_HIDDEN, HIDDEN, DISMISSED | 신규 |
| report.ReportDecision | HIDE, DISMISS | 신규 |
| notification.NotificationType | 기존 12 + CREW_NOTICE(→ CREW_NOTICE), CREW_POLL(→ CREW_POLL), POLL_CLOSING_SOON(→ CREW_POLL), CREW_SCHEDULE(→ CREW_SCHEDULE, 대면 모임), CREW_CHAT(→ CREW_CHAT), CREW_GOAL_ACHIEVED(→ CREW_HOME), MANAGER_CHANGED(→ CREW_HOME), BACKGROUND_UNLOCKED(→ AVATAR_BACKGROUND), REPORT_RESOLVED(→ CREW_HOME). 괄호는 기본 화면(defaultScreen) | 변경 |
| notification.Screen | 기존 6 + CREW_NOTICE, CREW_POLL, CREW_SCHEDULE, CREW_CHAT, AVATAR_BACKGROUND(CREW_BOARD는 D-150으로 뺌 (v0.11, 7.13.5 TF-VC-24)) | 변경 |
| audit.AuditAction | 기존 14 + MANAGER_APPOINT, MANAGER_DISMISS, CHAT_FORCE_DELETE, REPORT_HIDE, REPORT_DISMISS | 변경 |
| audit.ActorRole | RUNNER, CREW_LEADER, **CREW_MANAGER**, OPERATOR, SYSTEM | 변경 |
| 앱 domain.run.GuidanceMessage | 기존 + PAUSED, RESUMED, PAUSE_AUTO_END_SOON, PAUSE_AUTO_ENDED | 변경 |
| 앱 domain.run.Guidance.Kind | 기존 + PAUSE(정지·재개·자동 종료 안내) | 변경 |
| 앱 domain.run.WatchNotice | 기존 3 + AUTO_END_SOON, AUTO_ENDED | 변경 |

크루 권한표(`crew.CrewPermissionPolicy`, 3.0.3, FR-128). L = LEADER, M = MANAGER, R = MEMBER(크루원), O = OUTSIDER.

| CrewAction | 허용 관계 | v0.10과 차이 |
|------------|----------|--------------|
| VIEW_PUBLIC_INFO | L, M, R, O | 그대로 |
| EDIT_CREW (정보·태그·공개·가입 방식·초대 코드) | L | 그대로(FR-113) |
| TRANSFER_OR_DELETE | L | 그대로 |
| APPOINT_MANAGER | L | 신규 |
| KICK | L | 그대로 |
| VIEW_OR_DECIDE_JOIN | L, M | M 추가(제안) |
| MANAGE_NOTICE (공지 작성·고정) | L, M | 신규 |
| SET_GOAL | L, M | 신규 |
| WRITE_POST (응원·일정·투표·게시글·채팅·리액션) | L, M, R | 그대로(범위 확대) |
| RESPOND (참석·투표 응답) | L, M, R | VOTE_OR_JOIN_CREW_RUN 개명 |
| REPORT | L, M, R | 신규 |
| DELETE_ANY_CONTENT | L, M | DELETE_ANY_POST 개명, M 추가 |
| DELETE_OWN_CONTENT | L, M, R | DELETE_OWN_POST 개명 |
| VIEW_CREW_INTERNAL, EDIT_OWN_CREW_PROFILE, LEAVE | L, M, R | 그대로 |

#### F.5.2 엔티티·값 객체 (서버)

| 클래스 (패키지) | 종류 | 속성 변경 | 연산 (Java 시그니처) |
|-----------------|------|----------|---------------------|
| Member (member) | 엔티티, 변경 | + accountType(AccountType), lastAccessAt(LocalDateTime), guestCredentialHash(String, SHA-256 hex 64자, 게스트만); − tags | `static Member guest(Nickname nickname, Map<ConsentItem, Boolean> consents, String credentialHash, LocalDateTime now)`, `void linkSocialAccount(SocialAccount account)`(LINKED면 BusinessRuleException(ALREADY_LINKED), 해시 지움), `boolean matchesGuestSecret(String secret)`(MessageDigest.isEqual), `boolean recordAccess(LocalDateTime now)`(날짜가 바뀐 때만 갱신·true), `boolean isGuest()`. withdraw는 guestCredentialHash도 지운다 |
| GuestCredential (member) | 값, 신규 | token 형식 `g1.<memberId>.<base64url 32바이트>` | `static Issued issue(long memberId, SecureRandom random)` → `Issued(String token, String hash)`, `static Optional<Parsed> parse(String token)` → `Parsed(long memberId, String secret)`, `static String hashOf(String secret)` |
| AccessGrant (member) | 값, 신규 | memberId(long), expiresAt(Instant) | — (`TokenIssuer.verifyGrant(String accessToken)`의 반환, 실패는 AuthFailureException) |
| MemberSettings / BattleSettings (member) | 값, 변경 | MemberSettings + theme(ScreenTheme, 칼럼 screen_theme); BattleSettings + proximityAlert(boolean, 칼럼 battle_proximity_alert) | `void changeTheme(ScreenTheme t)`, `BattleSettings withProximityAlert(boolean on)`, `BattleSettings.defaults()`는 proximityAlert = true |
| SignUpForm, GuestStartForm (member) | 입력, 변경·신규 | SignUpForm = (nickname?, consents); GuestStartForm = (consents) | 둘 다 `void validate()`: 필수 동의(TERMS·PRIVACY·LOCATION) → CONSENT_MISSING, AGE_14_PLUS 거짓 → UNDER_14. 공통 검사는 `RequiredConsents.check(Map)`(member, 패키지 내부) |
| Nickname (member) | 값, 변경 | — | `static Nickname numbered(int number)`("러너" + 4자리, 0000~9999). SignUpService가 겹치면 다른 숫자로 최대 20번, 그래도 겹치면 5자리 |
| PauseInterval (running) | 값, 신규 | from, to(LocalDateTime) | `Duration duration()`, `boolean straddles(LocalDateTime a, LocalDateTime b)`(a ≤ to이고 b ≥ from) |
| RunUpload (running) | 입력, 변경 | + pauses(List\<PauseInterval\>) | 생성자 검사(F.2.2 POST /runs 규칙) |
| RunRecord (running) | 엔티티, 변경 | + pauses(칼럼 pauses TEXT, "시작;끝" 줄), pausedMillis | `List<PauseInterval> pauses()`, `Duration pausedTotal()`, `boolean judgeValidity(ValidityRule rule)`(D-94) |
| RunTrack (running) | 값, 변경 | — | `double maxSegmentSpeedKmh(List<PauseInterval> pauses)`, `Distance pathLength(List<PauseInterval> pauses)`: 정지 구간에 걸친 이웃 포인트 쌍은 뺀다. 인자 없는 옛 연산은 빈 목록으로 위임 |
| ValidRunScore (running) | 값, 신규 | ordinal(int), score(double), distance(Distance) | — (`RunRecordRepository.validRunScoreOf(String recordId)`의 결과. validRunsAfter는 지움) (v0.11 구현 반영, U5-A-1, D-169) |
| TierReconcileService (running) | 응용, 신규 | — | `int reconcile(LocalDateTime now)`(D-108) |
| Crew (crew) | 엔티티, 변경 | 운영진은 CrewMembership.role = MANAGER, `MAX_MANAGERS = 2` | `void appointManager(long targetId)`, `void dismissManager(long targetId)`, `CrewRelation relationOf(long memberId)`(MANAGER 반환). transferLeadership: 새 크루장이 운영진이었으면 그 자리가 빈다, 이전 크루장은 MEMBER |
| CrewMembership (crew) | 엔티티, 변경 | − crewTags | `void promote()`, `void demote()` |
| CrewPost (crew) | 추상 엔티티, 변경 | + hidden(boolean); body 칼럼 1,000자 | `DeletionKind delete(long requesterId, CrewRelation relation)`(본인 SELF, L·M이면 FORCED), `void hide()`, `void unhide()`, `boolean isVisible()`(!deleted && !hidden), `protected abstract int maxBody()` |
| CheerPost (crew) | 엔티티 | maxBody 200 | 그대로 |
| SchedulePost (crew) | 추상 엔티티, 신규(CrewPost 하위) | scheduledAt | `abstract AttendanceView answer(long memberId, AttendanceAnswer answer, LocalDateTime now)`, `abstract AttendanceCounts counts()` |
| CrewRunPost (crew) | 엔티티, 변경(SchedulePost 하위로 이동) | 그대로 | `vote` → `answer`. UNDECIDED는 참가자가 아니다. GOING = 기존 갈래요 |
| MeetupPost (crew) | 엔티티, 신규(SchedulePost 하위) | placeName(≤ 50자), capacity(2~10, 비우면 10), attendances(List\<MeetupAttendance\>) | `answer`: 일시 지남 → VOTE_CLOSED, GOING이 정원이면 SCHEDULE_FULL |
| MeetupAttendance (crew) | 엔티티, 신규 | postId, memberId, answer, answeredAt | — |
| NoticePost (crew) | 엔티티, 신규 | pinned(boolean), maxBody 1,000 | `void pin()`, `void unpin()` |
| PollPost (crew) | 엔티티, 신규 | method, options(List\<PollOption\>, @ElementCollection), closesAt, reminded, responses(List\<PollResponse\>), maxBody 200 | `void respond(long memberId, int optionNo, LocalDateTime now)`, `boolean isClosed(LocalDateTime now)`, `Set<Long> nonRespondents(Collection<Long> memberIds)`, `void markReminded()` |
| PollOption (crew) | 값(embeddable), 신규 | optionNo(1~5), label(≤ 40자) | — |
| PollResponse (crew) | 엔티티, 신규 | postId, memberId, optionNo, respondedAt | — |
| BoardPost (crew) | 엔티티, 신규 | photos(List\<PostPhoto\>, 최대 5), maxBody 1,000 | 생성 때 빈 글·사진 0 → POST_EMPTY |
| PostPhoto (crew) | 엔티티, 신규(메타만) | id, postId, crewId, seq, sizeBytes, createdAt | 바이트는 포트 `PostPhotoStore`(`void save(long photoId, byte[] jpeg)`, `Optional<byte[]> load(long photoId)`, `void deleteByPost(long postId)`), 구현 infra.crew `JdbcPostPhotoStore` |
| PhotoUpload (crew) | 입력, 신규 | jpeg(byte[]) | 생성자에서 크기 ≤ 1MB, JPEG 머리(FF D8 FF) 확인 뒤 JpegMetadataStripper.strip 결과만 갖는다(D-142 (v0.11, 7.13.5 TF-SY-25)) |
| JpegMetadataStripper (crew) | 도구, 신규(패키지 전용) | — | `static byte[] strip(byte[] jpeg)`: APP1(EXIF·XMP)·APP13(IPTC)·COM 제거, 깨진 세그먼트면 UNSUPPORTED_IMAGE (NFR-042, D-142) (v0.11, 7.13.5 TF-SY-25) |
| PostQuery, MeetupDraft, NoticeDraft, PollDraft, BoardDraft (crew) | 입력, 신규 | F.2.2의 항목 | 생성자 검사. 연산 매개변수 4개 이하를 지키려고 묶었다 |
| AttendanceView, PollResultView, AlbumView, PhotoContent, CelebrationCard (crew) | 조회 결과, 신규 | F.2.2 응답과 같은 항목 | — |
| WeeklyRace (crew) | 엔티티, 변경 | + goal(Distance?), goalSetBy(Long?), achievedAt(LocalDateTime?) | `void setGoal(Distance goal, long setterId)`(1km 미만 GOAL_INVALID), `boolean isAchieved()` |
| CrewNewsNotifier (crew) | 응용 협력, 신규 | — | `void announce(CrewPost post, long authorId)`, `void remindClosing(PollPost poll, Collection<Long> recipients)` |
| CrewPostReportAdapter (crew) | 포트 구현, 신규 | — | report.ReportedContentPort 구현(CHEER·SCHEDULE·NOTICE·POLL·BOARD) |
| CrewChatMessage (crew.chat) | 엔티티, 신규 | id, crewId, authorId?, clientMessageId, content(≤ 500자), sentAt, deleted, hidden | `static CrewChatMessage write(long crewId, long authorId, ChatDraft draft, LocalDateTime now)`, `void delete(long requesterId, CrewRelation relation)`, `void hide()`, `void unhide()`, `void anonymizeAuthor()` |
| ChatReaction (crew.chat) | 엔티티, 신규 | messageId, memberId, kind, reactedAt | — |
| ChatDraft, ChatCursor, ChatPage, ReactionView (crew.chat) | 입력·조회, 신규 | ChatCursor(before?, after?, size) | — |
| ReportCase (report) | 엔티티, 신규 | id, targetKind, targetId, crewId, status, openReporterCount, openedAt, resolvedAt?, resolverId?, version, `AUTO_HIDE_REPORTERS = 3` | `boolean addReport()`(열린 신고자 수 +1, RECEIVED이고 3 이상이면 AUTO_HIDDEN으로 바꾸고 true), `void resolve(ReportDecision d, String operatorId, LocalDateTime now)`(HIDDEN·DISMISSED면 REPORT_DECIDED), `void reopen()`(DISMISSED에 새 신고가 오면 RECEIVED, 열린 수 0부터) |
| ContentReport (report) | 엔티티, 신규 | id, caseId, reporterId?, reason, detail?, reportedAt, resolved | `void anonymizeReporter()` |
| ReportedContentPort (report) | 포트, 신규 | — | `boolean supports(ReportTargetKind kind)`, `Optional<ReportedContent> find(ReportTargetKind kind, long targetId)`, `void hide(ReportTargetKind kind, long targetId)`, `void unhide(ReportTargetKind kind, long targetId)` |
| ReportedContent (report) | 값, 신규 | crewId, authorId?, preview(≤ 100자) | 신고자 소속 확인은 포트를 구현한 패키지가 `isCrewMember(long memberId)`로 답한다(값에 판정 연산을 담는다) |
| Avatar (avatar) | 엔티티, 변경 | + selectedBackgroundKey(String?), unlockedBackgrounds(List\<UnlockedBackground\>) | `List<UnlockedBackground> unlock(Collection<Landmark> reached, LocalDateTime now)`(새로 해금한 것만 반환), `void selectBackground(Optional<String> key)`(잠긴 키면 BACKGROUND_LOCKED) |
| UnlockedBackground (avatar) | 엔티티, 신규 | landmarkKey, landmarkName, stage, distance, unlockedAt (해금 때의 스냅숏) | — |
| BackgroundService (avatar) | 응용, 신규 | — | `List<UnlockedBackground> unlockReached(long memberId, LocalDateTime now)`(새로 해금한 것에 BACKGROUND_UNLOCKED 알림), `BackgroundsView backgrounds(long memberId, LocalDateTime now)`(먼저 알림 없이 해금을 채운다), `BackgroundsView choose(long memberId, Optional<String> key)`, `int reconcile(LocalDateTime now)` |
| Landmark (policy) | 값, 변경 | + key(String, 영문 대문자·숫자·밑줄, 표 안에서 고유) | SettingKey.LANDMARKS 검사에 키 고유·형식 추가 |
| RewardWallet (reward) | 엔티티, 변경 | − accruals 컬렉션 매핑; + lastAccrualDate(LocalDate?), lastAccrualDateAmount(int) | `Optional<RewardAccrual> accrue(AccrualRequest req, RewardRule rule, int alreadyOnThatDay)`(D-109), `OptionalInt accruedOn(LocalDate day)`(집계 칼럼의 날이면 그 합계, 아니면 빈 값 → RewardService가 `RewardAccrualRepository.sumOn`으로 읽는다) |
| RewardAccrualRepository (reward) | 포트, 신규 | — | `int amountFor(long memberId, AccrualReason reason, String sourceId)`, `int sumOn(long memberId, LocalDate day)`, `void save(RewardAccrual a)` |

#### F.5.3 마이그레이션 (PostgreSQL, Flyway)

마지막 번호가 V18이므로 V19부터 예약한다. 단계(F.11)마다 번호를 정해 두어 병렬 작업이 번호를 다투지 않게 한다. Flyway out-of-order는 켜지 않는다. 통합 브랜치에 합치는 순서가 번호 순서와 다르면 합치는 쪽이 파일 이름의 번호를 바꾼다(아직 어느 운영 DB에도 적용되지 않은 번호만).

| 번호·이름 | 단계 | 내용 |
|-----------|------|------|
| V19__enum_checks_v011.sql | S0 | 값 목록 CHECK만 넓힌다(뒤 단계가 쓸 값까지 한 번에): `ck_notification_type` + 9종, `ck_audit_record_action` + 5종, `ck_audit_record_actor_role` + CREW_MANAGER, `ck_crew_membership_role` + MANAGER, `ck_crew_post_kind` + MEETUP·NOTICE·POLL·BOARD, `ck_crew_run_participation_answer` + UNDECIDED. 데이터 변경 없음 |
| V20__member_guest_theme.sql | S1 | member: `account_type VARCHAR(10) NOT NULL DEFAULT 'LINKED'`(CHECK GUEST·LINKED), `last_access_at TIMESTAMP(6)`(기존 행은 updated_at으로 채운 뒤 NOT NULL), `guest_credential_hash VARCHAR(64)`, `screen_theme VARCHAR(10) NOT NULL DEFAULT 'SYSTEM'`(CHECK), `battle_proximity_alert BOOLEAN NOT NULL DEFAULT TRUE`, **`DROP COLUMN tags`**. 제약 `ck_member_account`: (GUEST ⇒ provider·provider_account_id NULL) 또는 (LINKED ⇒ WITHDRAWN이거나 provider NOT NULL). `ck_member_withdrawn`을 다시 만들어 탈퇴 행은 guest_credential_hash도 NULL. 색인 `idx_member_idle_guest (last_access_at) WHERE account_type = 'GUEST' AND status = 'ACTIVE'`. crew_membership: **`DROP COLUMN crew_tags`**. 지우는 태그 값은 옮기지 않는다(CR-04: 러너 태그 기능 자체를 없앤다) |
| V21__run_record_pause.sql | S2 | run_record: `pauses TEXT NOT NULL DEFAULT ''`, `paused_millis BIGINT NOT NULL DEFAULT 0`, `ck_run_record_pause CHECK (paused_millis >= 0 AND (mode = 'FREE' OR paused_millis = 0))`. 색인 `idx_run_record_member_valid_saved (member_id, saved_at, record_id) WHERE valid`(validRunScoreOf의 순번). `tier_applied BOOLEAN NOT NULL`(기존 행은 TRUE로 채운 뒤 기본값 FALSE), 색인 `idx_run_record_tier_pending (saved_at) WHERE valid AND NOT tier_applied` (v0.11 구현 반영, U5-A-1, D-169) |
| V22__crew_v011.sql | S3 | crew_post: `body` VARCHAR(200) → VARCHAR(1000), `hidden BOOLEAN NOT NULL DEFAULT FALSE`, `pinned BOOLEAN`, `place_name VARCHAR(50)`, `capacity INTEGER`, `poll_method VARCHAR(10)`, `poll_closes_at TIMESTAMP(6)`; CHECK `ck_crew_post_meetup`(MEETUP ⇒ scheduled_at·place_name NOT NULL, capacity 2~10), `ck_crew_post_poll`(POLL ⇒ poll_method ∈ {YES_NO, CHOICES}, poll_closes_at NOT NULL), `ck_crew_post_notice`(NOTICE ⇒ pinned NOT NULL); 색인 `idx_crew_post_pinned_notice (crew_id) WHERE post_kind = 'NOTICE' AND pinned AND NOT deleted`, `idx_crew_post_poll_closing (poll_closes_at) WHERE post_kind = 'POLL' AND NOT deleted AND reminded = FALSE`. 새 표: `crew_meetup_attendance(post_id → crew_post ON DELETE CASCADE, member_id, answer CHECK, answered_at, PK(post_id, member_id))`, `crew_poll_option(post_id → crew_post CASCADE, option_no SMALLINT 1~5, label VARCHAR(40), PK(post_id, option_no))`, `crew_poll_response(post_id, member_id, option_no, responded_at, PK(post_id, member_id), FK(post_id, option_no) → crew_poll_option)`, `crew_photo(id IDENTITY PK, post_id → crew_post CASCADE, crew_id, seq SMALLINT 1~5, size_bytes INTEGER ≤ 1048576, jpeg BYTEA NOT NULL, created_at, UNIQUE(post_id, seq))` + 색인 `(crew_id, id DESC)`. weekly_race: `goal_meters BIGINT`(NULL 또는 ≥ 1000), `goal_set_by BIGINT`, `achieved_at TIMESTAMP(6)`(NULL이거나 goal_meters NOT NULL) |
| V23__crew_chat.sql | S3 | `crew_chat_message(id IDENTITY PK, crew_id → crew(id) ON DELETE CASCADE, author_id BIGINT, client_message_id VARCHAR(36) NOT NULL, content VARCHAR(500) NOT NULL, sent_at TIMESTAMP(6) NOT NULL, deleted BOOLEAN NOT NULL DEFAULT FALSE, hidden BOOLEAN NOT NULL DEFAULT FALSE)`, 고유 색인 `uq_crew_chat_client (author_id, client_message_id) WHERE author_id IS NOT NULL`, 색인 `(crew_id, id DESC)`, `(author_id)`. `crew_chat_reaction(message_id → crew_chat_message ON DELETE CASCADE, member_id, kind CHECK LIKE·HEART, reacted_at, PK(message_id, member_id, kind))` + 색인 `(member_id)`. 크루 삭제(CrewDeletion.deleteAll이 crew 행을 지운다)는 FK CASCADE로 채팅을 지운다 |
| V24__content_report.sql | S3 | `report_case(id IDENTITY PK, target_kind VARCHAR(15) CHECK, target_id BIGINT NOT NULL, crew_id BIGINT NOT NULL, status VARCHAR(12) CHECK, open_reporter_count INTEGER NOT NULL DEFAULT 0, opened_at, resolved_at, resolver_id VARCHAR(100), version BIGINT NOT NULL, UNIQUE(target_kind, target_id))` + 색인 `(status, opened_at)`. `content_report(id IDENTITY PK, case_id → report_case ON DELETE CASCADE, reporter_id BIGINT, reason VARCHAR(10) CHECK, detail VARCHAR(200), reported_at, resolved BOOLEAN NOT NULL DEFAULT FALSE)`, 고유 색인 `uq_content_report_reporter (case_id, reporter_id) WHERE reporter_id IS NOT NULL`, 색인 `(reporter_id)`. crew 표에 FK를 걸지 않는다(대상이 지워지면 사례는 "대상 없음"으로 남고 운영자가 기각한다) |
| V25__avatar_background.sql | S4 | avatar: `selected_background VARCHAR(40)`. 새 표 `avatar_background(member_id → avatar(member_id) ON DELETE CASCADE, landmark_key VARCHAR(40), landmark_name VARCHAR(40) NOT NULL, stage VARCHAR(20) NOT NULL, distance_meters BIGINT NOT NULL, unlocked_at TIMESTAMP(6) NOT NULL, PK(member_id, landmark_key))`. operation_setting의 LANDMARKS 행이 있으면 jsonb로 원소마다 `key`를 붙인다: 기본 표 이름이면 F.7 D-107의 키, 아니면 `'LM_' || 순번`. 이미 넘은 랜드마크의 해금 행은 마이그레이션이 만들지 않는다. 배경 조회 때 알림 없이 채운다(D-107 (3)) |
| V26__reward_wallet_daily_total.sql | S4 | reward_wallet: `last_accrual_date DATE`, `last_accrual_date_amount INTEGER NOT NULL DEFAULT 0 CHECK (>= 0)`; 기존 행은 reward_accrual에서 회원별 가장 최근 적립일과 그날 합계로 채운다. 색인 `idx_reward_accrual_member_accrued (member_id, accrued_at)`(과거 날짜 합계 조회) |

마이그레이션 시험: V20(태그 칼럼 삭제·last_access_at 채움), V25(LANDMARKS JSON 키 부여, 이름 없는 원소), V26(집계 채움)은 BattleActiveSlotMigrationIntegrationTest처럼 PostgreSQL Testcontainers 통합 시험을 둔다.

---

### F.6 패키지·의존

#### F.6.1 서버 패키지 (5.1.1)

현재 코드는 패키지 54개다(5.1.1의 53개 + batch.reward, CR34-02). v0.11은 7개를 더해 **61개**다.

| 패키지 | 계층 | 책임(한 문장) | 포함 클래스 |
|--------|------|--------------|------------|
| crew.chat [신규] | 응용+도메인 | 크루 채팅 메시지의 생애주기를 관리한다 | CrewChatMessage, ChatReaction, ReactionKind, ChatDraft, ChatCursor, ChatPage, ChatPostResult, ReactionView, CrewChatService, CrewChatMessageRepository(포트), ChatReactionRepository(포트), CrewChatMessenger(포트), ChatRoomPresence(포트), CrewChatSignal, ChatReportAdapter, ChatWithdrawalListener; 하위 crew.chat.live: CrewChatPosted, CrewChatRemoved, CrewChatReactionChanged, ChatRemovalReason |
| report [신규] | 응용+도메인 | 콘텐츠 신고의 접수·숨김·처리를 관리한다 | ReportCase, ContentReport, ReportTargetKind, ReportReason, ReportStatus, ReportDecision, ReportCommand, ReportResult, ReportCaseView, ReportResolution, ReportedContent, ReportedContentPort(포트), ReportService, ReportCaseRepository(포트), ReportWithdrawalListener |
| web.chat [신규] | 표현 | 채팅 요청을 crew.chat 호출로 바꾼다 | ChatController, 요청·응답 DTO |
| web.report [신규] | 표현 | 회원의 신고 요청을 report 호출로 바꾼다 | ReportController, 요청·응답 DTO |
| web.admin.report [신규] | 표현 | 관리툴 신고 처리 요청을 report 호출로 바꾼다 | ReportAdminController, 요청·응답 DTO(actorId 요청 속성, D-50) |
| infra.chat [신규] | 인프라 | crew.chat의 저장 포트를 구현한다 | JpaCrewChatMessageRepository, JpaChatReactionRepository |
| infra.report [신규] | 인프라 | report의 저장 포트를 구현한다 | JpaReportCaseRepository |

바뀌는 기존 패키지(포함 클래스 열에 더하고 뺄 것):

| 패키지 | 더함 | 뺌 |
|--------|------|----|
| member | AccountType, ScreenTheme, GuestCredential, GuestStartForm, GuestStartResult, SocialLinkResult, AccessGrant, RequiredConsents | (태그 관련 연산·속성) |
| running | PauseInterval, ValidRunScore, TierReconcileService | — |
| crew | SchedulePost, MeetupPost, MeetupAttendance, NoticePost, PollPost, PollOption, PollResponse, BoardPost, PostPhoto, PostPhotoStore(포트), PhotoUpload, PostQuery, MeetupDraft, NoticeDraft, PollDraft, BoardDraft, AttendanceAnswer, PollMethod, AttendanceView, AttendanceCounts, PollResultView, AlbumView, PhotoContent, CelebrationCard, CrewNewsNotifier, CrewPostReportAdapter | CrewRecommender, TagOverlapRecommender, Recommendation, RecommendationView, CrewCandidate, VoteAnswer(개명) |
| avatar | UnlockedBackground, BackgroundService, BackgroundUnlockListener, BackgroundsView | — |
| reward | RewardAccrualRepository(포트) | — |
| realtime | InboundMessage(sealed), InboundChatPresence, ChatPresenceRegistry(ChatRoomPresence 구현), PollingChatPresence; WebSocketMessenger·PollingMessenger가 CrewChatMessenger도 구현 | — |
| batch.member / batch.running / batch.crew / batch.avatar | IdleGuestWithdrawalJob(3시) / TierReconcileJob(0시 10분) / PollReminderJob(1분) / BackgroundReconcileJob(0시 10분) | — |
| infra.crew / infra.reward | JdbcPostPhotoStore / JpaRewardAccrualRepository | — |

#### F.6.2 12.2 의존 목록 변경

새 화살표 (From → To 쌍):

| No(가칭) | From | To | 의존 형태 | 주고받는 것 |
|----------|------|----|-----------|-------------|
| N1 | crew.chat | crew | 메서드 호출 | (memberId, crewId) → CrewRelation(`CrewMembershipReader.relationOf`, 신규 연산); crewId → 활성 구성원 ID 목록(`activeMemberIds`) |
| N2 | crew.chat | member | 메서드 호출·사건 수신 | memberIds → MemberSummary(작성자 표시명); MemberWithdrawn 수신 |
| N3 | crew.chat | notification | 메서드 호출 | NotificationRequest(CREW_CHAT) → notifyCollapsing |
| N4 | crew.chat | audit | 메서드 호출 | AuditEntry(CHAT_FORCE_DELETE) |
| N5 | crew.chat | report | 인터페이스 구현 | ReportedContentPort(CHAT_MESSAGE) — ChatReportAdapter |
| N6 | crew | report | 인터페이스 구현 | ReportedContentPort(CHEER·SCHEDULE·NOTICE·POLL·BOARD) — CrewPostReportAdapter |
| N7 | report | notification | 메서드 호출 | NotificationRequest(REPORT_RESOLVED) |
| N8 | report | audit | 메서드 호출 | AuditEntry(REPORT_HIDE, REPORT_DISMISS) |
| N9 | report | member | 사건 수신 | MemberWithdrawn(신고자 익명화) |
| N10 | crew.chat, report | common | 참조 | 값·enum·오류 형식, PageCursor (D44 "핵심 패키지 13개"가 15개가 된다) |
| N11 | realtime | crew.chat | 메서드 호출·인터페이스 구현 | (memberId, crewId) → checkRoomAccess; CrewChatMessenger·ChatRoomPresence 구현(CrewChatSignal은 type()으로 직렬화) |
| N12 | web.chat | crew.chat | 메서드 호출 | ChatDraft·ChatCursor → ChatPage·ChatPostResult·ReactionView |
| N13 | web.report | report | 메서드 호출 | ReportCommand → ReportResult |
| N14 | web.admin.report | report | 메서드 호출 | (actorId, caseId, ReportDecision) → ReportResolution; (status, PageCursor) → ReportCaseView 목록 |
| N15 | infra.chat | crew.chat | 인터페이스 구현·엔티티 매핑 | CrewChatMessageRepository, ChatReactionRepository |
| N16 | infra.report | report | 인터페이스 구현·엔티티 매핑 | ReportCaseRepository |
| N17 | web.chat, web.report, web.admin.report, infra.chat, infra.report | common | 참조 | (D67 확장) |

내용이 바뀌는 기존 화살표:

| No | From → To | 변경 |
|----|-----------|------|
| D3 | running → member | + memberId → appliedValidRunCount(TierReconcileService) |
| D5 | runimal → member | − tagsOf. MemberWithdrawn 수신만 남는다 |
| D18 | avatar → running | + RunRecordSaved 수신(BackgroundUnlockListener), memberId → TotalStats(totals, 누적 유효 거리), (from, to) → SavedRun 목록(savedBetween, 보정) |
| D20 | avatar → policy | + LandmarkTable(개인 랜드마크, key) |
| D21 | avatar → notification | + BACKGROUND_UNLOCKED |
| D22 | crew → member | MemberSummary에서 태그 삭제 |
| D26 | crew → notification | + CREW_NOTICE, CREW_POLL, POLL_CLOSING_SOON, CREW_SCHEDULE, CREW_GOAL_ACHIEVED, MANAGER_CHANGED (8종 → 14종) |
| D27 | crew → audit | + MANAGER_APPOINT, MANAGER_DISMISS. POST_FORCE_DELETE 행위자 역할에 CREW_MANAGER |
| D37 | profile → avatar | + memberId → backgroundKey(AvatarQuery.backgroundKeyOf) |
| D45 | web.auth → member | + GuestStartForm → GuestStartResult, guestCredential → TokenPair, (memberId, provider, token) → SocialLinkResult |
| D50 | web.crew → crew | + 운영진·공지·투표·대면 모임·게시글·앨범·사진·참석·목표 연산 |
| D51 | web.avatar → avatar | + BackgroundService(backgrounds, choose) |
| D54 | realtime → member | TokenIssuer.verify → verifyGrant(AccessGrant) |
| D56 | batch.crew → crew | + now → CrewPostService.remindClosingPolls |
| D65 | batch.avatar → avatar | + now → BackgroundService.reconcile |
| D71 | batch.running → running | + now → TierReconcileService.reconcile |
| D72 | batch.member → member | + now → WithdrawalService.withdrawIdleGuests |

CR34-04가 요청한 화살표(operator → audit, batch.reward → reward·common, reward → member·running·battle 메서드 호출, crew → running SavedRun)도 이번 개정에 함께 넣는다(F.9). 화살표 수는 (나) 담당이 12.2를 다시 세어 적는다(이번 변경으로 쌍 22개 안팎이 는다).

#### F.6.3 순환 없음 확인

- report는 notification·audit·member·common만 import한다. 이 넷은 report·crew·crew.chat을 import하지 않는다(notification → member·policy·common, audit → common, member → audit·policy·common).
- crew와 crew.chat은 report의 포트를 **구현**만 한다(crew → runimal의 CrewRunParticipationQuery 구현, D24와 같은 꼴). report는 crew·crew.chat을 모른다.
- crew.chat → crew는 한 방향이다. crew는 crew.chat을 import하지 않는다: 크루 삭제는 DB FK CASCADE로, 탈퇴는 crew.chat이 MemberWithdrawn을 직접 받는다. 크루를 떠난 회원의 접근은 crew.chat이 읽을 때마다 `relationOf`로 막는다.
- realtime → crew.chat은 호출·구현이고 crew.chat은 realtime을 모른다(포트 CrewChatMessenger·ChatRoomPresence).
- avatar → running(사건·조회)은 기존 D18과 같은 방향이다. running은 avatar를 모른다.
- 따라서 핵심 패키지 15개 사이에 순환이 없다. ArchitectureTest(백엔드)의 `PACKAGES`·`ALLOWED`에 7개 패키지와 위 화살표를 더해 시험으로 지킨다(F.11).

#### F.6.4 앱·워치 모듈 (5.1.2)

| 모듈 (Gradle · 패키지) | 상태 | 포함·변경 | 의존 대상 |
|------------------------|------|----------|-----------|
| :domain · domain.chat | 신규 | ChatMessage, ChatPage, ReactionKind, ChatRoom(방 열기·보내기·리액션·삭제·닫기, REST 페이지 + 실시간 합치기, 재연결 뒤 `after` 메우기), ChatRepository(포트) | domain.live, core.model |
| :domain · domain.report | 신규 | ReportTarget, ReportReason, ReportStatus, ReportRepository(포트) | core.model |
| :domain · domain.run | 변경 | PauseInterval, PauseLimit, RunSession.PAUSED·pause·resume, ProximityAlertRule, ProximityAlert, RunCoach, RunWatchBridge, WatchRequestListener, MeasurementRuntime, RunArchive(scope 받음), WatchGateway 확장, GuidanceMessage·WatchNotice 확장 | 그대로 |
| :domain · domain.live | 변경 | ClientMessage(sealed: ProgressReport, ChatPresence), ServerMessage + CrewChatPosted·CrewChatRemoved·CrewChatReactionChanged, `LiveChannel.send(m: ClientMessage)`, `LiveChannelProvider.open()`은 임대(D-103) | 그대로 |
| :domain · domain.crew / account / settings / avatar | 변경 | crew: CrewRole.MANAGER, PostKind 확장, AttendanceAnswer, Notice·Poll·PollResult·Meetup·BoardPost·Album·RaceStatus 목표, CrewRepository 연산; account: AccountType, StartAsGuestUseCase, LinkSocialAccountUseCase, LogoutUseCase 게스트 분기, SessionRepository 게스트 자격 증명; settings: SettingsSnapshot.theme, GuidanceSettings.isProximityAlertOn·isFollowingCoachPlan(D-111); avatar: 배경 | 그대로 |
| :data · api.chat, api.report | 신규 | ChatApi·ReportApi(Retrofit), DTO, ChatRepositoryImpl·ReportRepositoryImpl, ChatBindings·ReportBindings | api.core, domain.chat / domain.report, core.model |
| :data · api.crew / account / avatar / core | 변경 | crew: 새 엔드포인트, `PhotoCompressor`(JPEG ≤ 1MB, 긴 변 1600px), 시각 두 형식 해독; account: 게스트·연동; avatar: 배경; core: `SessionRefresher.refreshNow()`(TokenAuthenticator와 data.live가 함께 쓴다), 게스트 자격 증명으로 세션 재개 | 그대로 |
| :data · data.live / session / settings / store / watch / push | 변경 | live: 임대 참조 수로 연결 하나 공유, 새 메시지 해독, 4401 처리; session: 게스트 자격 증명 보관(EncryptedTokenStore); settings: theme; store: Room 3(pauses, AutoMigration 2→3); watch: 새 경로 3개; push: OpenChatRoomTracker로 열린 방의 CREW_CHAT 표시 생략 | 그대로 |
| :feature:chat · ui.chat | 신규 | ChatRoomScreen, ChatViewModel, ChatFeatureEntry(Routes.CrewChat(crewId)) | domain.chat, domain.report, core.ui, core.navigation |
| :feature:crew · ui.crew | 변경 | 운영진 관리, 공지, 투표, 일정(크루런·대면 모임), 게시판·앨범, 공동 목표·축하 카드, 신고 대화상자, 추천 영역 삭제 | + domain.report |
| :feature:me, :feature:run, :feature:avatar, :feature:share, :feature:notification | 변경 | me: 게스트 시작·동의 화면 하나·계정 연동·테마·유실 고지·근접 알림 설정; run: 일시정지; avatar: 배경 선택; share: CrewGoalCardRenderer·러닝 카드 배경; notification: 새 화면 이동 | 그대로 |
| :core:ui (core.designsystem) | 변경 | RunkkColors light·dark, RunkkTheme(darkTheme), AvatarBackdrop, UiText(CR34-13) | 그대로 |
| :wear-contract | 변경 | F.4 | 없음 |
| :wear · watch.alert | 신규 | ProximityAlertPlayer(Vibrator·ToneGenerator, 간격 반복, 긴 진동 1회, 3초 만료) | wear.contract |
| :wear · watch.link, watch.ui | 변경 | link: PAUSE_REQUEST 보내기, PROXIMITY·LEAD_CHANGE 받기; ui: 일시정지·재개 버튼, 정지 표시 | 그대로 |

domain.\*은 data·ui를 import하지 않고, domain.chat → domain.live는 domain.battle·domain.crew와 같은 방향이라 순환이 없다. 앱 Konsist ArchitectureTest에 :feature:chat과 domain.chat·domain.report 규칙을 더한다.

---

