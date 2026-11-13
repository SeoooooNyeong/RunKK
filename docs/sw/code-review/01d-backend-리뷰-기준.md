> [런크크 코드 리뷰 결과서](../code-review.md) 목차 · 이전: [01c-backend-2](01c-backend-2.md) · 다음: [01e-backend-4](01e-backend-4.md)
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

