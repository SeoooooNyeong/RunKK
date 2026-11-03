> [런크크 객체지향 분석·설계서](../design-oo.md) 목차 · 이전: [B-E-변경-이력-색인-가정](B-E-변경-이력-색인-가정.md)

## F. v0.11 설계 변경 명세

> 작성: 2026-10-22 | 대상: design-oo.md v0.10 → v0.11 | 입력: srs v0.10 7.13(CR-01~CR-10)·3장 FR 본문·6장 모델, code-review.md 2차 9절(CR2-*)·3·4차 9절(CR34-01~28)·"결정 대기 13건", 팀 결정 2026-10-22, 코드 S15P31A207-impl feature/runkk-impl 45d33ae(마이그레이션 V18까지)

### F.0 이 명세의 지위와 읽는 법

- 이 파일이 v0.11 변경의 **단일 기준**이다. 설계 조각 파일을 고치는 사람(F.10)과 구현 에이전트(F.11)는 여기 적힌 API 경로, 메시지·항목 이름, enum 상수, 패키지·클래스·메소드 이름을 그대로 쓴다. 조각 파일과 이 파일이 다르면 v0.11 반영이 끝날 때까지 이 파일이 우선한다. 반영이 끝나면 이 파일은 부록 F(변경 근거)로 남고 본문 조각 파일이 기준이 된다.
- 이름 규칙: 서버·앱 코드 이름은 기존 코드 관례(영어 클래스·enum 상수, 패키지 `com.a207.runkk.<패키지>`, 앱 `com.a207.runkk.app.<계층>.<기능>`)를 따른다. SRS 자료사전 이름과의 대응은 F.1.6 한 표에 둔다. SRS 쪽 이름이 정합 작업으로 조금 바뀌어도 설계 이름은 바꾸지 않고 F.1.6의 왼쪽 열만 고친다.
- SRS의 **(제안)** 값(정지 60분, 근접 100m, 운영진 2명, 고정 3개, 채팅 500자 등)은 그 규칙을 가진 클래스의 상수로 둔다. 운영 기준값(policy.SettingKey)으로 올리지 않는다(D-113).
- 새로 만들거나 바꾸는 REST·실시간 항목의 시각은 D-84 형식(ISO-8601, `+09:00` 오프셋 포함)이다(v0.13 D-212·D-213으로 대체: 실시간 서버 → 앱 시각과 web.battle playedAt·startAt은 오프셋 없는 LocalDateTime). 표의 형 `OffsetDateTime`은 이 형식의 문자열이다.
- 상태 표시: **[신규]** 새로 만든다, **[변경]** 형식·동작이 바뀐다, **[삭제]** 없앤다, **(깨짐)** 지금 앱(android/contract, data DTO)이 쓰는 계약과 호환되지 않는다.
- 형 뒤의 `?`는 null 가능(JSON에서 빠지거나 null)이다. 권한 표기: **공개**(토큰 없음), **회원**(게스트 포함 로그인 회원), **크루 구성원**(LEADER·MANAGER·MEMBER), **운영진 이상**(LEADER·MANAGER), **크루장**(LEADER), **운영자**(관리툴 토큰, OperatorCheckInterceptor).

---

### F.1 요구 → 설계 대응표

워치·ai 열의 "없음"은 그 실행 단위의 코드가 바뀌지 않는다는 뜻이다. ai 서버는 이번 변경 전체에서 바뀌지 않는다: 크루 추천은 처음부터 api 서버의 TagOverlapRecommender였고(0.1, 부록 E E-03) 이번에 삭제되며, AI 코치 챗봇(CR-05)은 보류다.

#### F.1.1 신규 FR (25건)

| FR | 서버 (패키지 · 클래스.메소드) | 앱 (모듈·패키지 · 클래스) | 워치 | ai |
|----|------------------------------|---------------------------|------|----|
| FR-194 자유런 일시정지·재개 | running · `RunUpload.pauses`, `PauseInterval`, `RunRecord.pauses()`·`pausedTotal()`, `RunTrack.maxSegmentSpeedKmh(pauses)`·`pathLength(pauses)`; web.run `RunUploadRequest.pauses` | domain.run · `RunSession.pause(at)`·`resume(at)`·`State.PAUSED`, `RunTracker.onPauseTapped()`·`onResumeTapped()`, `PauseInterval`, `CompletedRun.pauses`; data.store `PendingRunEntity.pauses`(Room 3); ui.run 일시정지·재개 버튼 | wear.contract `PauseRequestMessage`, `WatchDisplayMessage.isPaused·isPausable`; watch.ui 일시정지 버튼; watch.link `PhoneLink.sendPauseRequest` | 없음 |
| FR-195 장시간 정지 자동 종료 | 없음(자동 종료된 기록도 POST /runs 한 경로) | domain.run · `RunTracker.tick`의 정지 한도 검사(`PauseLimit.WARN_AFTER` 55분, `END_AFTER` 60분), `GuidanceMessage.PAUSE_AUTO_END_SOON`·`PAUSE_AUTO_ENDED` | `WatchNotice.AUTO_END_SOON`·`AUTO_ENDED` 표시만 | 없음 |
| FR-196 워치 근접 알림 | member · `BattleSettings.proximityAlert` | domain.run · `ProximityAlertRule.evaluate`, `ProximityAlert`, `RunCoach.proximityOf`, `RunWatchBridge.publishProximity`·`publishLeadChange`; `WatchGateway.publishProximity`·`publishLeadChange`; data.watch `WatchLink` | wear.contract `ProximityMessage`·`LeadChangeMessage`; watch.alert `ProximityAlertPlayer` | 없음 |
| FR-197 게스트로 시작 | member · `SignUpService.startAsGuest(GuestStartForm)`, `Member.guest(...)`, `GuestCredential`, `AccountType.GUEST`, `Nickname.numbered`; web.auth `SignUpController` POST /members/guest | domain.account · `StartAsGuestUseCase`, `AccountRepository.startAsGuest`, `SessionRepository.saveGuestCredential`; ui.me 동의 화면(가입·게스트 공용) | 없음 | 없음 |
| FR-198 소셜 계정 연동 | member · `AuthService.linkSocialAccount(memberId, provider, providerToken)`, `Member.linkSocialAccount(SocialAccount)`; web.auth `AuthController` POST /me/social-account | domain.account · `LinkSocialAccountUseCase`; ui.me 계정 연동(SocialLoginClient 재사용) | 없음 | 없음 |
| FR-199 게스트 자료 유실 고지 | member · `MemberSettingsView.accountType` | ui.me · 게스트 시작 직후·설정·로그아웃 확인 화면의 고지; domain.account `LogoutUseCase` 게스트 분기 | 없음 | 없음 |
| FR-200 미이용 게스트 자료 파기 | member · `WithdrawalService.withdrawIdleGuests(now)`, `Member.recordAccess(now)`, `MemberRepository.findIdleGuests(before)`; batch.member `IdleGuestWithdrawalJob`(3시) | 없음 | 없음 | 없음 |
| FR-201 랜드마크 배경 해금 | avatar · `BackgroundService.unlockReached(memberId, now)`·`reconcile(now)`, `BackgroundUnlockListener`(RunRecordSaved), `Avatar.unlockBackgrounds`, `UnlockedBackground`; policy `Landmark.key`; notification `BACKGROUND_UNLOCKED`; batch.avatar `BackgroundReconcileJob`(0시 10분) | ui.notification 이동 `Screen.AVATAR_BACKGROUND` | 없음 | 없음 |
| FR-202 아바타 배경 선택 | avatar · `BackgroundService.backgrounds(memberId, now)`·`choose(memberId, key)`, `Avatar.selectBackground`, `AvatarView.backgroundKey`; profile `ProfileView.avatarBackgroundKey`(AvatarQuery.backgroundKeyOf) | domain.avatar · `AvatarRepository.backgrounds()`·`chooseBackground(key)`; ui.avatar 배경 선택; core.ui `AvatarBackdrop`(키 → 그림 리소스); ui.share 러닝 카드 배경 | 없음 | 없음 |
| FR-203 화면 테마 설정 | member · `ScreenTheme`, `MemberSettings.theme`, `SettingItem.THEME` | domain.settings · `SettingsSnapshot.theme`; data.settings; core.ui `RunkkTheme(darkTheme)`·라이트/다크 색 토큰; app `MainActivity` 테마 구독; ui.me 테마 설정 | 없음(워치 테마 그대로) | 없음 |
| FR-204 운영진 지정·해제 | crew · `CrewService.appointManager`·`dismissManager`, `Crew.appointManager`·`dismissManager`, `CrewRole.MANAGER`, `CrewRelation.MANAGER`, `CrewAction.APPOINT_MANAGER`; audit `MANAGER_APPOINT`·`MANAGER_DISMISS`; notification `MANAGER_CHANGED` | domain.crew · `CrewRepository.appointManager`·`dismissManager`; ui.crew 크루원 관리 | 없음 | 없음 |
| FR-205 크루 공지 작성 | crew · `NoticePost`, `NoticeDraft`, `CrewPostService.writeNotice`·`pin`, `CrewNewsNotifier`; notification `CREW_NOTICE` | domain.crew · `Notice`; ui.crew 공지 작성·고정, 크루 홈 상단 고정 공지 | 없음 | 없음 |
| FR-206 크루 투표 생성 | crew · `PollPost`, `PollDraft`, `PollMethod`, `PollOption`, `CrewPostService.writePoll`; notification `CREW_POLL` | domain.crew · `Poll`; ui.crew 투표 작성 | 없음 | 없음 |
| FR-207 크루 투표 응답 | crew · `PollPost.respond`, `PollResponse`, `CrewPostService.respondPoll`·`pollResult` → `PollResultView` | domain.crew · `PollResult`; ui.crew 투표 화면 | 없음 | 없음 |
| FR-208 채팅 보내기 | crew.chat · `CrewChatService.post(memberId, crewId, ChatDraft)`, `CrewChatMessage.write`, `CrewChatMessenger`(포트); web.chat `ChatController` | domain.chat · `ChatRoom.send`, `ChatRepository.post`; ui.chat(:feature:chat) | 없음 | 없음 |
| FR-209 채팅 조회 | crew.chat · `CrewChatService.history(memberId, crewId, ChatCursor)`, `ChatRoomPresence`(포트), crew.chat.live `CrewChatPosted`; realtime `ChatPresenceRegistry`, `InboundChatPresence` | domain.chat · `ChatRoom.open(crewId)`; domain.live `ChatPresence`·`CrewChatPosted`; data.live 해독·방 입장 알림 | 없음 | 없음 |
| FR-210 채팅 리액션 | crew.chat · `CrewChatService.react`·`unreact`, `ChatReaction`, `ReactionKind`, crew.chat.live `CrewChatReactionChanged` | domain.chat · `ChatRepository.react`·`unreact`; ui.chat | 없음 | 없음 |
| FR-211 크루 게시글 작성 | crew · `BoardPost`, `BoardDraft`, `PostPhoto`, `PostPhotoStore`(포트), `CrewPostService.writeBoard`; infra.crew `JdbcPostPhotoStore` | domain.crew · `CrewRepository.writeBoard(crewId, body, photoUris)`; api.crew 사진 압축(`PhotoCompressor`, JPEG ≤ 1MB·긴 변 1600px); ui.crew 게시글 작성 | 없음 | 없음 |
| FR-212 게시판·앨범 조회 | crew · `CrewPostService.list(memberId, crewId, PostQuery)`·`album`·`photo` | domain.crew · `Album`; ui.crew 게시판·앨범(사진은 api.core 인증 OkHttp로 /crew-photos/{id}) | 없음 | 없음 |
| FR-213 크루 콘텐츠 삭제 | crew · `CrewPost.delete(requesterId, relation)`(운영진 허용); crew.chat `CrewChatMessage.delete(requesterId, relation)`, `CrewChatService.delete`; audit `POST_FORCE_DELETE`·`CHAT_FORCE_DELETE`, `ActorRole.CREW_MANAGER` | domain.crew·domain.chat 삭제 연산, 목록의 `canDelete` | 없음 | 없음 |
| FR-214 콘텐츠 신고 | report · `ReportService.submit(reporterId, ReportCommand)`, `ContentReport`, `ReportCase`, `ReportTargetKind`, `ReportReason`, `ReportedContentPort`; crew `CrewPostReportAdapter`; crew.chat `ChatReportAdapter`; web.report `ReportController` | domain.report · `ReportRepository.report`; api.report; ui.crew·ui.chat 신고 대화상자 | 없음 | 없음 |
| FR-215 신고 누적 자동 숨김 | report · `ReportCase.addReport()`(서로 다른 신고자 `AUTO_HIDE_REPORTERS` = 3), `ReportService.submit` 안에서 `ReportedContentPort.hide` | 목록에서 숨김 글이 빠짐, 실시간 `CrewChatRemoved(HIDDEN)` | 없음 | 없음 |
| FR-216 신고 처리 | report · `ReportService.cases`·`resolve(operatorId, caseId, ReportDecision)`; web.admin.report `ReportAdminController`; audit `REPORT_HIDE`·`REPORT_DISMISS`; notification `REPORT_RESOLVED` | 알림함만 | 없음 | 없음 |
| FR-217 공동 목표 달성 판정 | crew · `WeeklyRace.goal`·`achievedAt`, `WeeklyRaceRepository.markAchievedIfReached`, `WeeklyRaceService.onRunSaved`·`reconcileAll` | 없음(조회는 FR-140·141) | 없음 | 없음 |
| FR-218 크루 소식 알림 | crew · `CrewNewsNotifier.announce`, `CrewPostService.remindClosingPolls(now)`; batch.crew `PollReminderJob`(1분); crew.chat `CrewChatService`의 방 밖 수신자 알림; notification `NotificationService.notifyCollapsing`, 새 종류 `CREW_NOTICE`·`CREW_POLL`·`POLL_CLOSING_SOON`·`CREW_SCHEDULE`·`CREW_CHAT` | data.push · 열린 채팅방 크루의 `CREW_CHAT` 표시 생략(`OpenChatRoomTracker`) | 없음 | 없음 |

#### F.1.2 되살린 FR (2건)

| FR | 서버 | 앱 | 워치 | ai |
|----|------|----|------|----|
| FR-138 크루 주간 공동 목표 설정 | crew · `WeeklyRaceService.setGoal(memberId, crewId, goal, now)`, `WeeklyRace.setGoal`, `WeeklyRaceRepository.latestGoalBefore`(지난 주 목표 이어 쓰기), `CrewAction.SET_GOAL`; web.crew PUT /crews/{id}/goal | domain.crew · `CrewRepository.setWeeklyGoal`; ui.crew 목표 설정 | 없음 | 없음 |
| FR-141 공동 목표 달성 알림·축하 카드 | crew · `RaceStatusView.celebration`(`CelebrationCard`); notification `CREW_GOAL_ACHIEVED` | domain.crew · `RaceStatus.celebration`; ui.crew 축하 카드; ui.share `CrewGoalCardRenderer`(FR-069 공유 방식) | 없음 | 없음 |

#### F.1.3 수정 FR (7.13.3 수정 표 전체)

| FR | 서버 | 앱 | 워치 | ai |
|----|------|----|------|----|
| FR-001 | 로그인 계약 그대로. `AuthService.login`이 `Member.recordAccess(now)` | ui.me 로그인 화면에 "게스트로 시작" 진입, 가입필요면 동의 화면 | 없음 | 없음 |
| FR-002 | `AuthService.logout` 그대로 | domain.account `LogoutUseCase`: 연동 안 한 게스트면 확인(FR-199) 뒤 `SessionRepository.clear()`(게스트 자격 증명 포함)·`PendingRunRepository.clear()`·`SettingsRepository.clear()` | 없음 | 없음 |
| FR-003 | member · `SignUpForm`을 (닉네임?, 동의)로 축소, `SignUpController.SignUpRequest` 항목 삭제(D-90) | ui.me 가입 단계를 동의 화면 하나로 | 없음 | 없음 |
| FR-004 | `GuestStartForm.validate`가 `AGE_14_PLUS` 확인(UNDER_14) | 동의 화면 공용 | 없음 | 없음 |
| FR-006 | `ProfileQueryService.view` 응답에 계정 유형을 싣지 않는다 | 없음 | 없음 | 없음 |
| FR-007 | `ProfileEdit`·`ProfileEditRequest`에서 tags 삭제 | ui.me 프로필 편집에서 태그 삭제 | 없음 | 없음 |
| FR-021 | crew `CrewWithdrawalListener`(공지·투표·일정·게시글 작성자 익명화, 게시글 사진·투표 응답 삭제), crew.chat `ChatWithdrawalListener`(메시지 작성자 익명화, 리액션 삭제), report `ReportWithdrawalListener`(신고자 익명화) | 없음 | 없음 | 없음 |
| FR-028 | 없음 | ui.me 동의 화면 문구("기록은 전체 공개로 시작해요…") | 없음 | 없음 |
| FR-034 | 없음 | domain.run `RunSession.tick`: 정지 중 지표 고정, 평균 페이스 = 운동 시간 ÷ 거리, `RunProgress.isPaused` | 정지 표시 | 없음 |
| FR-036 | running `RunRecord.pauses`, `RunDetail.pauses`; web.record `RecordDetailResponse.pauses`·`pausedSeconds` | `CompletedRun.pauses`, `elapsed` = 운동 시간, 정지 중 길게 눌러 종료 허용 | 없음 | 없음 |
| FR-042 | 없음 | `RunWatchBridge`가 자유런이면 `isPausable` 참, 대결이면 근접 알림 | 일시정지 조작·근접 알림 | 없음 |
| FR-047 | running `RunRecord.judgeValidity`: 운동 시간·평균 페이스는 정지 제외, 정지 경계 구간은 속도 판정·경로 길이에서 제외, 다섯째 조건 `ValidityRule.isBackedBy`(D-94) | 없음 | 없음 | 없음 |
| FR-048 | 없음(문서) | 없음 | 없음 | 없음 |
| FR-049 | 없음 | `SplitGuidanceRule`·`TargetReachedRule`이 운동 거리(정지 구간 제외 누적)로 판정, 정지 중 평가하지 않음 | 없음 | 없음 |
| FR-051 | analysis `DashboardService` 그대로; 해금은 avatar(FR-201) | 없음 | 없음 | 없음 |
| FR-109 | member `BattleSettings.proximityAlert`(기본 true) | domain.settings `GuidanceSettings.isProximityAlertOn`; ui.me 대결 설정 | 없음 | 없음 |
| FR-110 | crew 문구만(태그 규칙 그대로) | 없음 | 없음 | 없음 |
| FR-113 | crew `CrewPermissionPolicy`: `EDIT_CREW` 크루장만(그대로) | 없음 | 없음 | 없음 |
| FR-117 | crew `CrewQueryService.search(viewerId, CrewSearch, PageCursor)`만, 추천 결합 삭제; `CrewListView.recommendations` 삭제 | domain.crew `CrewList`에서 추천 삭제, 태그 필터를 주 경로로 | 없음 | 없음 |
| FR-128 | crew `CrewPermissionPolicy` 표를 3.0.3(운영진 열)로(F.5.1) | 화면 버튼 노출을 `myRole`로 | 없음 | 없음 |
| FR-130, FR-134 | crew `CrewPost.delete` 운영진 허용 | `canDelete` 표시 | 없음 | 없음 |
| FR-132 | crew `SchedulePost`(추상) 아래 `CrewRunPost`·`MeetupPost`, `CrewPostService.writeCrewRun`·`writeMeetup` | ui.crew 일정 작성(종류 선택) | 없음 | 없음 |
| FR-133 | crew `PostListView`의 일정 정보(참석·불참·미정 수, 장소명) | ui.crew 일정 목록 | 없음 | 없음 |
| FR-140 | crew `RaceStatusView` + 목표 진행률, 순위 숫자 없음 | ui.crew 기여도 막대·진행률 | 없음 | 없음 |
| FR-144 | crew `CrewProfileEdit`에서 tags 삭제, `CrewMembership.crewTags` 삭제 | ui.crew 크루 내 표시 정보 편집에서 태그 삭제 | 없음 | 없음 |
| FR-145 | crew `SchedulePost.answer(memberId, AttendanceAnswer, now)`, `CrewPostService.answerAttendance`, `AttendanceAnswer`(VoteAnswer 개명 + UNDECIDED). 작성자 자동 참석(크루런·대면 모임, D-151), 삭제·숨김 일정 응답은 POST_NOT_FOUND(D-154) (v0.11, test.md v0.6 TF-IT-21, TF-IT-30) | domain.crew `AttendanceAnswer` | 없음 | 없음 |
| FR-147 | crew `CrewRunService.enterLobby`: 소속 크루원 누구나, 입장하면 응답을 GOING으로(무응답·미정·불참 포함, D-152 (v0.11, test.md v0.6 TF-IT-22)) | 없음 | 없음 | 없음 |
| FR-152 | runimal `RunimalService`가 `MemberQueryService.tagsOf`를 부르지 않는다, `RunimalJudge.judge`의 태그 매개변수 삭제(태그 없음 규칙과 같은 결과) | 없음 | 없음 | 없음 |
| FR-155, FR-158 | 없음 | ui.avatar로 진입 이동, 기본 얼굴 1번(`DefaultCharacter.RABBIT`) | 없음 | 없음 |
| FR-162 | avatar `AvatarView.backgroundKey`; web.avatar `AvatarResponse.backgroundKey` | domain.avatar `Avatar.backgroundKey` | 없음 | 없음 |
| FR-175 | notification `NotificationType` 9종 추가(F.5.1) | 알림 설정 목록 9종 추가 | 없음 | 없음 |
| FR-178 | notification `Screen` 5종 추가(F.5.1, CREW_BOARD는 D-150으로 뺌 (v0.11, 7.13.5 TF-VC-24)) | core.navigation `Routes` 5종, ui.notification 이동 표 | 없음 | 없음 |
| FR-188 | 없음 | `RunTracker.onLeadChanged` → `RunWatchBridge.publishLeadChange` | 1초 긴 진동 1회 | 없음 |
| FR-193 | crew 문구만(`CREW_RUN_RECRUIT`·`CREW_RUN_SOON` 상수 그대로) | 알림 문구 리소스 | 없음 | 없음 |

#### F.1.4 삭제 FR

| FR | 지우는 설계 요소 |
|----|-----------------|
| FR-118 크루 추천 | crew `CrewRecommender`, `TagOverlapRecommender`, `Recommendation`, `RecommendationView`, `CrewCandidate`, `CrewQueryService.recommend`, `CrewListView.recommendations`; web.crew GET /crews/recommendations, `CrewDtos.RecommendationsResponse`, `CrewListResponse.recommendations`, `CrewListResponse.Item.tagMatches`; 앱 domain.crew 추천 모델, api.crew 추천 DTO, ui.crew 추천 영역; 부록 E E-03 닫음 |
| FR-119 러너 성향 태그 선택 | member `Member.tags`(member.tags 칼럼), `SignUpForm.tags`, `ProfileEdit.tags`, `MemberQueryService.tagsOf`, `MemberSummary`의 태그; crew `CrewMembership.crewTags`(crew_membership.crew_tags 칼럼), `CrewProfileEdit.tags`; common `TagOwner.RUNNER`(쓰는 곳이 0이 되면 지운다); 앱 태그 선택 UI(가입·프로필·크루 내 정보) |

#### F.1.5 NFR·CR-10

| 항목 | 설계 대응 | 시험 |
|------|----------|------|
| NFR-040 채팅 3초 | crew.chat → realtime `WebSocketMessenger`(방 안 크루원에게 즉시 전송, 메일함 거치지 않음, D-101), 앱 `ChatRoom`이 받은 메시지를 바로 목록에 넣음. 폴링 대체 모드는 방 화면이 2초마다 `GET …/chat/messages?after=` | 14장 통합: 소켓 10개 + 메시지 100건, 보낸 시각 → 받은 시각 95% ≤ 3초(ChatLatencyIntegrationTest) |
| NFR-041 명도 대비 4.5:1 | core.ui `RunkkColors.light`·`dark` 토큰, 본문 글자/배경 짝마다 대비 계산 | `ColorContrastTest`(토큰 짝 대비 ≥ 4.5), Roborazzi 주요 화면 10개 × 테마 2 |
| NFR-042 게시 사진 위치 메타데이터 제거 | 앱 api.crew `PhotoCompressor` 재인코딩(메타데이터 복사 안 함) + 서버 crew `JpegMetadataStripper.strip`(PhotoUpload 생성자, APP1·APP13·COM 제거) 이중 방어(D-142) (v0.11, 7.13.5 TF-SY-25) | `JpegMetadataStripperTest`, 위치 정보가 든 사진 10장 게시 뒤 저장본 위치 항목 0건 |
| CR-10 (CR34-08) | running `ValidityRule.isBackedBy(declared, path, elapsed, wall)`를 `RunRecord.judgeValidity`가 `wall = endedAt − startedAt − pausedTotal`로 부른다(D-94) | `ValidityRuleTest`·`RunUploadServiceTest`: 정지 구간 있는 자유런의 다섯째 조건 경계(+1초) |

#### F.1.6 SRS 자료 이름 ↔ 설계 이름

| SRS 자료사전 (v0.10) | 설계 이름 (서버 / 앱) |
|----------------------|----------------------|
| 계정유형 = [게스트 / 연동회원] | `member.AccountType {GUEST, LINKED}` / `domain.account.AccountType` |
| 게스트시작요청 | `web.auth.SignUpController.GuestStartRequest` → `member.GuestStartForm` |
| 계정연동요청 | `web.auth.AuthController.SocialLinkRequest` → `AuthService.linkSocialAccount` |
| 마지막접속일시 | `Member.lastAccessAt` (member.last_access_at) |
| 게시종류 = [응원 / 일정 / 공지 / 투표 / 게시글] | `crew.PostKind {CHEER, CREW_RUN, MEETUP, NOTICE, POLL, BOARD}` — 일정 = CREW_RUN·MEETUP(`SchedulePost` 하위) |
| 일정종류 = [크루런 / 대면모임] | `CrewRunPost` / `MeetupPost` (PostKind CREW_RUN / MEETUP) |
| 일정응답 = [참석 / 불참 / 미정] | `crew.AttendanceAnswer {GOING, NOT_GOING, UNDECIDED}` (v0.10 `VoteAnswer` 개명) |
| 장소명, 정원 | `MeetupPost.placeName`, `MeetupPost.capacity` |
| 투표, 투표방식, 선택지, 투표응답 | `PollPost`, `PollMethod {YES_NO, CHOICES}`, `PollOption(optionNo, label)`, `PollResponse` |
| 공지고정여부 | `NoticePost.pinned` |
| 게시사진 | `crew.PostPhoto` (crew_photo) |
| 크루채팅메시지 | `crew.chat.CrewChatMessage` |
| 리액션, 리액션종류 = [좋아요 / 하트] | `crew.chat.ChatReaction`, `ReactionKind {LIKE, HEART}` |
| 크루 채팅 파일 D18 | crew_chat_message, crew_chat_reaction |
| 신고 | `report.ContentReport`(신고 한 건) + `report.ReportCase`(대상 하나의 처리 단위) |
| 신고대상종류 | `report.ReportTargetKind {CHEER, SCHEDULE, NOTICE, POLL, BOARD, CHAT_MESSAGE}` |
| 신고사유 = [욕설비방 / 음란 / 광고스팸 / 개인정보노출 / 기타] + (신고내용) | `report.ReportReason {ABUSE, SEXUAL, SPAM, PRIVACY, OTHER}` + `detail` |
| 신고처리상태 = [접수 / 자동숨김 / 숨김 / 기각] | `report.ReportStatus {RECEIVED, AUTO_HIDDEN, HIDDEN, DISMISSED}` (ReportCase.status) |
| 신고 파일 D19 | report_case, content_report |
| 공동목표 = 크루ID + 주시작일 + 목표거리 + 달성여부 + (달성일시) | `crew.WeeklyRace`(crewId, weekStart)의 `goal`·`achievedAt` (weekly_race.goal_meters·achieved_at) |
| 일시정지요청 = [일시정지 / 재개] | 폰: `RunTracker.onPauseTapped()`·`onResumeTapped()`, 워치: `PauseRequestMessage.Action {PAUSE, RESUME}` |
| 정지구간 = 정지시작일시 + 정지종료일시 | `running.PauseInterval(from, to)` / `domain.run.PauseInterval(from, to)` / JSON `pauses[{from, to}]` |
| 근접알림 = 거리차 + 알림간격 + [앞섬 / 뒤처짐] | `domain.run.ProximityAlert(gapMeters, interval, isAhead)` / `ProximityMessage` |
| 대결설정.근접알림여부 | `BattleSettings.proximityAlert` / `GuidanceSettings.isProximityAlertOn` |
| 화면테마 = [기기설정따름 / 라이트 / 다크] | `member.ScreenTheme {SYSTEM, LIGHT, DARK}` |
| 랜드마크배경 = 랜드마크번호 + 해금일시 | `avatar.UnlockedBackground(landmarkKey, landmarkName, stage, distance, unlockedAt)` — 랜드마크번호는 문자열 키 `Landmark.key`(D-107) |
| 선택배경 = [기본배경 / 랜드마크번호] | `Avatar.selectedBackgroundKey`(null = 기본 배경) |
| 크루역할 = [크루장 / 운영진 / 크루원] | `crew.CrewRole {LEADER, MANAGER, MEMBER}` |
| 운영진지정요청 | PUT·DELETE /crews/{id}/managers/{memberId} |
| 알림종류 += 9종 | `CREW_NOTICE`, `CREW_POLL`, `POLL_CLOSING_SOON`, `CREW_SCHEDULE`, `CREW_CHAT`, `CREW_GOAL_ACHIEVED`, `MANAGER_CHANGED`, `BACKGROUND_UNLOCKED`, `REPORT_RESOLVED` |

---

### F.2 서버 REST 계약

#### F.2.0 공통 규칙

- 경로는 모두 `/api/v1` 아래다. 회원 JWT는 AuthFilter가, 관리툴 토큰은 web.admin의 OperatorCheckInterceptor가 확인한다(5.7, D-42, D-50). 공개 경로는 SecurityConfig와 AuthFilter의 제외 목록에 더한다: POST /members/guest, POST /auth/guest/session.
- 오류 본문은 `ErrorResponse{code: String, message: String}`(web.error) 그대로다. 상태는 기존 규칙을 따른다: `BusinessRuleException(ReasonCode)`는 409(ApiExceptionHandler.STATUS_BY_REASON에 적은 것만 다른 상태), `PermissionDeniedException`은 403 `PERMISSION_DENIED`, 입력 검증 실패(Bean Validation, IllegalArgumentException)는 400 `BAD_REQUEST`, 크기 초과 413 `TOO_LARGE`, 이미지 형식 422 `UNSUPPORTED_IMAGE`, 시도 제한 429 `TOO_MANY_ATTEMPTS`. 크루 밖 회원: 권한표 행위로 판정하는 크루 게시·관리 쓰기는 403 `PERMISSION_DENIED`, 크루 소속이 전제인 자원(채팅, 신고 대상)은 409 `NOT_CREW_MEMBER`(D-167, 05-03 공통 규칙) (v0.11 구현 반영, U5-8, D-167).
- 시각은 `OffsetDateTime`(D-84). 이번에 다시 만드는 web.crew 전송 형식은 기존 `LocalDateTime` 항목도 모두 `OffsetDateTime`으로 바꾼다(D-114). 앱 api.crew 매퍼는 A2에서 오프셋 있는 값과 없는 값을 모두 읽게 먼저 고친다.
- 목록 페이지는 기존 `common.PageCursor(cursor, size)`와 같은 방식(size+1 읽기, `nextCursor`)이다. PageCursor의 위치 결정(CR34-11, F.8 P-1)과 무관하게 이 형식을 쓴다.
- 새 엔드포인트 24개, 바뀌는 엔드포인트 23개(표의 [변경] 19행), 지우는 엔드포인트 2개다.

#### F.2.1 엔드포인트 표 (05-03 5.7.1 형식 + 권한·상태)

| 상태 | 메소드·경로 | Controller → 서비스.연산 | 관련 FR | 권한 |
|------|-------------|------------------------|---------|------|
| [신규] | POST /members/guest | SignUpController(web.auth) → SignUpService.startAsGuest | FR-197, FR-004 | 공개 |
| [신규] | POST /auth/guest/session | AuthController(web.auth) → AuthService.resumeGuest | FR-197 | 공개 |
| [신규] | POST /me/social-account | AuthController(web.auth) → AuthService.linkSocialAccount | FR-198 | 회원 |
| [변경] | POST /members | SignUpController → SignUpService.signUp (요청 축소) | FR-003, FR-028 | 공개 |
| [변경] | POST /auth/login, POST /auth/refresh | AuthController → AuthService.login, refresh (계약 그대로, 마지막 접속 갱신) | FR-001, FR-200 | 공개 |
| [변경] | GET /me/settings | MeController → MemberSettingService.settingsOf | FR-199, FR-203, FR-109 | 회원 |
| [변경] | PATCH /me/settings | MeController → MemberSettingService.changeSetting (THEME, BATTLE_SETTINGS.proximityAlert) | FR-203, FR-109 | 회원 |
| [변경] | PATCH /me/profile | MeController → ProfileService.editProfile (tags 삭제) | FR-007 | 회원 |
| [변경] | GET /members/{id}/profile | MeController → ProfileQueryService.view (tags 삭제, 배경 키 추가) | FR-006, FR-202 | 회원 |
| [변경] | POST /runs | RunController → RunUploadService.upload (pauses) | FR-036, FR-047, FR-194 | 회원 |
| [변경] | GET /runs/{id} | RecordController → RecordQueryService.detail (pauses) | FR-036 | 회원 |
| [변경] | GET /crews | CrewController → CrewQueryService.search (추천·tagMatches 삭제) | FR-117 | 회원 |
| [삭제] (깨짐) | GET /crews/recommendations | (CrewQueryService.recommend 삭제) | FR-118 삭제 | — |
| [변경] | GET /crews/{id}, GET /crews/me | CrewController → CrewQueryService.detail, myCrew (myRole에 MANAGER) | FR-128 | 회원 |
| [변경] | PATCH /crews/{id}/me | CrewController → MembershipService.editCrewProfile (tags 삭제) | FR-144 | 크루 구성원 |
| [변경] | GET /crews/{id}/members | CrewController → CrewQueryService.members (tags 삭제, role MANAGER) | FR-135, FR-144 | 크루 구성원 |
| [변경] | GET /crews/{id}/join-requests, POST /join-requests/{id}/approve, POST /join-requests/{id}/reject | CrewController·CrewAdminController → CrewQueryService.pendingRequests, MembershipService.approveJoin, rejectJoin (운영진 허용) | FR-123, 3.0.3 | 운영진 이상 |
| [신규] | PUT /crews/{id}/managers/{memberId} | CrewAdminController → CrewService.appointManager | FR-204 | 크루장 |
| [신규] | DELETE /crews/{id}/managers/{memberId} | CrewAdminController → CrewService.dismissManager | FR-204 | 크루장 |
| [변경] | POST /crews/{id}/posts | CrewPostController → CrewPostService.writeCheer, writeCrewRun, writeMeetup, writeNotice, writePoll | FR-129, FR-132, FR-205, FR-206 | 크루 구성원(NOTICE는 운영진 이상) |
| [신규] | POST /crews/{id}/board-posts | CrewPostController → CrewPostService.writeBoard | FR-211 | 크루 구성원 |
| [변경] | GET /crews/{id}/posts | CrewPostController → CrewPostService.list(memberId, crewId, PostQuery) | FR-131, FR-133, FR-212 | 크루 구성원 |
| [변경] | DELETE /posts/{id} | CrewPostController → CrewPostService.delete (운영진 허용, 모든 종류) | FR-130, FR-134, FR-213 | 크루 구성원 |
| [신규] | PUT /posts/{id}/attendance | CrewPostController → CrewPostService.answerAttendance | FR-145 | 크루 구성원 |
| [삭제] (깨짐) | PUT /posts/{id}/vote | PUT /posts/{id}/attendance로 바뀜 | FR-145 | — |
| [신규] | PUT /posts/{id}/pin | CrewPostController → CrewPostService.pin | FR-205 | 운영진 이상 |
| [신규] | GET /posts/{id}/poll | CrewPostController → CrewPostService.pollResult | FR-207 | 크루 구성원 |
| [신규] | PUT /posts/{id}/poll-response | CrewPostController → CrewPostService.respondPoll | FR-207 | 크루 구성원 |
| [신규] | GET /crews/{id}/album | CrewPostController → CrewPostService.album | FR-212 | 크루 구성원 |
| [신규] | GET /crew-photos/{photoId} | CrewPostController → CrewPostService.photo | FR-212 | 크루 구성원 |
| [신규] | PUT /crews/{id}/goal | CrewController → WeeklyRaceService.setGoal | FR-138 | 운영진 이상 |
| [변경] | GET /crews/{id}/race | CrewController → WeeklyRaceService.status (목표·진행률·축하 카드) | FR-140, FR-141, FR-217 | 크루 구성원 |
| [신규] | GET /crews/{id}/chat/messages | ChatController(web.chat) → CrewChatService.history | FR-209 | 크루 구성원 |
| [신규] | POST /crews/{id}/chat/messages | ChatController → CrewChatService.post | FR-208 | 크루 구성원 |
| [신규] | DELETE /chat-messages/{id} | ChatController → CrewChatService.delete | FR-213 | 크루 구성원 |
| [신규] | PUT /chat-messages/{id}/reactions/{kind} | ChatController → CrewChatService.react | FR-210 | 크루 구성원 |
| [신규] | DELETE /chat-messages/{id}/reactions/{kind} | ChatController → CrewChatService.unreact | FR-210 | 크루 구성원 |
| [신규] | POST /reports | ReportController(web.report) → ReportService.submit | FR-214, FR-215 | 회원(대상 크루의 구성원) |
| [신규] | GET /admin/reports | (OperatorCheckInterceptor) ReportAdminController(web.admin.report) → ReportService.cases | FR-216 | 운영자 |
| [신규] | POST /admin/reports/{caseId}/hide | ReportAdminController → ReportService.resolve(HIDE) | FR-216 | 운영자 |
| [신규] | POST /admin/reports/{caseId}/dismiss | ReportAdminController → ReportService.resolve(DISMISS) | FR-216 | 운영자 |
| [변경] | GET /avatar | AvatarController(web.avatar) → AvatarService.view (backgroundKey) | FR-162 | 회원 |
| [신규] | GET /avatar/backgrounds | AvatarController → BackgroundService.backgrounds | FR-201, FR-202 | 회원 |
| [신규] | PUT /avatar/background | AvatarController → BackgroundService.choose | FR-202 | 회원 |
| [변경] | GET /notifications | NotificationController → NotificationService.inbox (새 type·screen 값) | FR-175, FR-178 | 회원 |

v0.10 5.7.1에 없지만 코드에 있는 엔드포인트도 이번에 표에 올린다(CR2-U1): GET /battles/match(BattleController → MatchingService.status, 대기 상태와 기본 대결 거리), GET /crews/me(CrewQueryService.myCrew), GET /crews/{id}(CrewQueryService.detail), GET /avatar/parts/face/{ownerId}/{set}/{part}(AvatarController → FaceImageService.partImage), GET /admin/settings(SettingAdminController 조회), POST /admin/rankings/recompute(web.admin RankingAdminController → RankingService.recompute + AuditAction.RANKING_RECOMPUTED, V18).

#### F.2.2 요청·응답 본문

##### 회원·게스트·설정 (S1)

**POST /members/guest** [신규]

| 구분 | 형식 |
|------|------|
| 요청 `GuestStartRequest` | `consents: Map<ConsentItem, Boolean>` (필수 `AGE_14_PLUS`·`TERMS`·`PRIVACY`·`LOCATION` = true, 선택 `MARKETING`·`LLM_TRANSFER`, 빠지면 false. `HEART_RATE`·`FACE_TRANSFER`는 받지 않는다 — 그 기능을 처음 쓸 때 동의 변경으로 받는다) |
| 응답 201 `GuestStartResponse` | `memberId: long`, `nickname: String`("러너" + 숫자 4자리), `accessToken: String`, `refreshToken: String`, `guestCredential: String`(기기 자격 증명, D-89. 다시 내주지 않는다) |
| 오류 | 400 `BAD_REQUEST`(consents 없음), 409 `CONSENT_MISSING`, 409 `UNDER_14`, 429 `TOO_MANY_ATTEMPTS`(AuthAttemptGuard 접속지별 게스트 생성 한도, 제안 시간당 10회) |
| 동작 | `SignUpService.startAsGuest(GuestStartForm)` → `Member.guest(nickname, consents, GuestCredential 해시, now)`, 감사 `SIGN_UP_CONSENT`(행위자 RUNNER), `last_access_at = now` |

**POST /auth/guest/session** [신규]

| 구분 | 형식 |
|------|------|
| 요청 `GuestSessionRequest` | `guestCredential: String`(1~200자) |
| 응답 200 `TokenResponse` | `accessToken: String`, `refreshToken: String` (기존 /auth/refresh 응답과 같은 record) |
| 오류 | 401 본문 없음(형식 위조, 해시 불일치, 이미 연동한 회원, 탈퇴 회원 — 구분해 알리지 않는다), 429 `TOO_MANY_ATTEMPTS` |
| 동작 | `AuthService.resumeGuest(credential, now)`: `GuestCredential.parse` → 회원 조회 → `Member.matchesGuestSecret`(상수 시간 비교) → `TokenIssuer.issue(memberId)`, `Member.recordAccess(now)` |

**POST /me/social-account** [신규]

| 구분 | 형식 |
|------|------|
| 요청 `SocialLinkRequest` | `provider: ProviderType`(KAKAO / GOOGLE), `providerToken: String`(≤ 4096자) |
| 응답 200 `SocialLinkResponse` | `accountType: AccountType`(LINKED), `provider: ProviderType` |
| 오류 | 401 `AUTH_FAILED`, 503 `PROVIDER_UNAVAILABLE`, 409 `ALREADY_MEMBER`(그 소셜 계정이 다른 회원과 연동됨, 문구 "이미 가입된 계정"(FR-198, D-155 (v0.11, test.md v0.6 TF-IT-31))), 409 `ALREADY_LINKED`(이미 연동한 회원), 429 `TOO_MANY_ATTEMPTS` |
| 동작 | `AuthService.linkSocialAccount(memberId, provider, providerToken)`: SocialLoginGateway.authenticate → `MemberRepository.findByAccount`가 있으면 ALREADY_MEMBER → `Member.linkSocialAccount(account)`(account_type LINKED, guest_credential_hash 지움). 두 계정을 합치지 않는다. 토큰은 바꾸지 않는다 |

**POST /members** [변경]

| 구분 | 형식 |
|------|------|
| 요청 `SignUpRequest` | `signUpTicket: String`, `nickname: String?`(없으면 "러너" + 숫자 4자리, D-90), `consents: Map<ConsentItem, Boolean>`. **삭제**: birthYear, gender, photoUrl, intro, recordVisibility, tags (보내면 무시 — Spring Boot Jackson 기본 FAIL_ON_UNKNOWN_PROPERTIES=false) |
| 응답 201 | 그대로 `SignUpResponse{memberId, nickname, accessToken, refreshToken}` |
| 오류 | 그대로(CONSENT_MISSING, UNDER_14, NICKNAME_INVALID, NICKNAME_TAKEN, SIGN_UP_TICKET_INVALID, ALREADY_MEMBER). TAG_INVALID·INTRO_TOO_LONG은 이 경로에서 더 나오지 않는다 |

**GET /me/settings** [변경] — `MemberSettingsView`에 더한다: `theme: ScreenTheme`(SYSTEM / LIGHT / DARK, 기본 SYSTEM), `accountType: AccountType`, `linkedProvider: ProviderType?`(게스트면 null). `battleSettings`에 `proximityAlert: boolean`(기본 true). 계정 유형은 이 본인 전용 응답에만 있다(FR-006).

**PATCH /me/settings** [변경] — `SettingChangeRequest{item, value, notificationType?}` 그대로. 새 항목 `item = THEME`, `value = "SYSTEM" / "LIGHT" / "DARK"`. `item = BATTLE_SETTINGS`의 JSON에 `proximityAlert: Boolean?`을 더하고, 빠지면 현재 값을 유지한다(옛 앱 호환, D-92). 오류는 그대로 409 `SETTING_INVALID`.

**PATCH /me/profile** [변경] — `ProfileEditRequest`에서 `tags` 삭제(보내면 무시).

**GET /members/{id}/profile** [변경] — `ProfileResponse`에서 `tags` 삭제(앱 DTO는 기본값 emptySet이라 실행은 된다), `avatarBackgroundKey: String?` 추가(공개 수준이 아바타를 보이는 경우만, null = 기본 배경).

##### 러닝 (S2)

**POST /runs** [변경]

| 구분 | 형식 |
|------|------|
| 요청 `RunUploadRequest` 추가 항목 | `pauses: List<PauseRequest>?` (null·빈 목록 = 정지 없음). `PauseRequest{from: OffsetDateTime, to: OffsetDateTime}` |
| 규칙 | `mode = FREE`일 때만 비어 있지 않을 수 있다. `from < to`, `startedAt ≤ from`, `to ≤ endedAt`, 시간순·겹침 없음, 최대 100개(제안). 위반은 400 `BAD_REQUEST`(RunUpload 생성자의 IllegalArgumentException). `elapsedMillis`는 정지 시간을 뺀 **운동 시간**이다(FR-036) |
| 응답 | 그대로 `UploadResponse{recordId, status, valid}`. valid는 다섯째 조건(D-94)을 포함한 판정 |

**GET /runs/{id}** [변경] — `RecordDetailResponse`에 `pauses: List<{from: OffsetDateTime, to: OffsetDateTime}>`, `pausedSeconds: long` 추가. 기존 경과 시간 항목은 운동 시간이다.

##### 크루 (S3)

**GET /crews** [변경] — `CrewListResponse{crews, nextCursor}`. **삭제**: `recommendations`, `Item.tagMatches` (깨짐). 질의(keyword, tags AND, cursor, size)는 그대로(CR34-24).

**PATCH /crews/{id}/me** [변경] — `CrewProfileRequest{intro: String?}`(tags 삭제).

**GET /crews/{id}/members** [변경] — `Member{memberId, displayName, role, crewIntro, tier?, runimalType?, me}`. `tags` 삭제, `role` 값에 `MANAGER`(앱 CrewRole은 모르는 이름을 MEMBER로 읽는다 — CR34-28. A1에서 MANAGER를 더한다).

**PUT /crews/{id}/managers/{memberId}**, **DELETE /crews/{id}/managers/{memberId}** [신규]

| 구분 | 형식 |
|------|------|
| 요청 | 본문 없음 |
| 응답 200 `ManagerResponse` | `memberId: long`, `role: CrewRole`(지정이면 MANAGER, 해제면 MEMBER) |
| 오류 | 403 `PERMISSION_DENIED`(크루장 아님), 409 `CREW_NOT_FOUND`, 409 `TARGET_INVALID`(소속 크루원이 아님·본인·이미 그 역할), 409 `MANAGER_LIMIT`(운영진 2명, 지정만) |
| 동작 | crew 행 잠금(`CrewAccess.checkForUpdate`) 뒤 `Crew.appointManager(targetId)`·`dismissManager(targetId)`, 감사 `MANAGER_APPOINT`·`MANAGER_DISMISS`(행위자 역할 CREW_LEADER), 대상에게 `MANAGER_CHANGED` 알림 |

**POST /crews/{id}/posts** [변경] — 요청 `PostWriteRequest`

| 항목 | 형 | 쓰는 종류 |
|------|----|-----------|
| `kind` | PostKind (필수) | CHEER, CREW_RUN, MEETUP, NOTICE, POLL. BOARD는 400(board-posts로 쓴다) |
| `body` | String? | CHEER 필수 1~200자, CREW_RUN 0~200자, MEETUP 0~200자, NOTICE 필수 1~1,000자, POLL 필수(질문) 1~200자 |
| `distanceMeters` | Long? | CREW_RUN 필수 |
| `scheduledAt` | OffsetDateTime? (별칭 `meetAt` 유지) | CREW_RUN·MEETUP 필수, 지금보다 뒤 |
| `voteDeadline` | OffsetDateTime? | CREW_RUN 참석 응답 마감(비우면 scheduledAt) |
| `placeName` | String? | MEETUP 필수 1~50자 |
| `capacity` | Integer? | MEETUP 2~10, 비우면 크루 정원 |
| `pinned` | Boolean? | NOTICE, 기본 false |
| `pollMethod` | PollMethod? | POLL 필수 (YES_NO / CHOICES) |
| `options` | `List<String>`? | POLL·CHOICES 2~5개, 각 1~40자. YES_NO면 비운다(서버가 "찬성"·"반대" 두 선택지를 만든다) |
| `closesAt` | OffsetDateTime? | POLL 필수, 지금보다 뒤 |

응답 201 `PostResponse{id: long}`. 오류: 400 `BAD_REQUEST`(종류별 필수 항목 빠짐), 403 `PERMISSION_DENIED`(NOTICE를 크루원이 씀, 크루 밖. D-167, v0.15 D-311 정정), 409 `BODY_TOO_LONG`, 409 `CREW_RUN_INVALID`(기존), 409 `SCHEDULE_INVALID`(대면 모임 일시·장소·정원 위반), 409 `POLL_INVALID`(선택지 수·마감 일시), 409 `PIN_LIMIT`(고정 공지 3개 초과). 작성 뒤 `CrewNewsNotifier.announce`가 작성자를 뺀 크루원에게 `CREW_NOTICE`·`CREW_POLL`·`CREW_SCHEDULE`(MEETUP)을 보낸다. 크루런은 기존 `CREW_RUN_RECRUIT`(FR-193). v0.11 test: 장소명·정원·투표 질문·선택지 수·선택지 길이 위반은 400 `BAD_REQUEST`(MeetupDraft·PollDraft 생성자, srs 3.0.4)이고 409 `SCHEDULE_INVALID`·`POLL_INVALID`는 일시·마감이 지금 이전일 때만이다. 숨긴 고정 공지는 `PIN_LIMIT` 계산에서 뺀다. 대면 모임 작성자는 GOING으로 들어간다(D-155, D-154, D-151) (v0.11, test.md v0.6 TF-IT-32, TF-IT-30, TF-IT-21)

**POST /crews/{id}/board-posts** [신규] — `multipart/form-data`

| 구분 | 형식 |
|------|------|
| 요청 파트 | `body`: text, 0~1,000자. `photos`: file 0~5개, 각 `image/jpeg` ≤ 1MB(앱이 긴 변 1600px·JPEG로 줄여 보낸다, D-100). 요청 전체 ≤ 6MB |
| 응답 201 | `PostResponse{id: long}` |
| 오류 | 409 `POST_EMPTY`(빈 글이면서 사진 0장), 409 `PHOTO_LIMIT`(6장 이상), 413 `TOO_LARGE`(파일 1MB 또는 요청 6MB 초과), 422 `UNSUPPORTED_IMAGE`(JPEG 머리가 아님), 409 `BODY_TOO_LONG`, 403 `PERMISSION_DENIED`(크루 밖, D-167, v0.15 D-311 정정) |
| 동작 | `CrewPostService.writeBoard(memberId, crewId, BoardDraft(body, List<PhotoUpload>))` 한 트랜잭션에서 crew_post + crew_photo 저장 |

**GET /crews/{id}/posts** [변경]

| 구분 | 형식 |
|------|------|
| 질의 | `kinds: Set<PostKind>?`(쉼표 또는 반복, 없으면 전부. 기존 단수 `kind`도 받는다), `pinnedOnly: Boolean?`(NOTICE 고정만), `cursor: Long?`, `size: Integer?` → `PostQuery(kinds, pinnedOnly, PageCursor)` |
| 응답 `PostListResponse{posts, nextCursor}`의 `Item` | 기존 `postId, kind, authorName, body, writtenAt(OffsetDateTime), mine, crewRun?` + `canDelete: boolean`(본인 글 또는 운영진 이상) + `meetup: Meetup?` + `notice: Notice?` + `poll: Poll?` + `photos: List<Photo>?` |
| `CrewRun` | 기존 항목(`goingCount`, `myAnswer` 값 GOING / NOT_GOING / UNDECIDED) + `notGoingCount: int`, `undecidedCount: int`. 시각 항목은 OffsetDateTime |
| `Meetup` | `scheduledAt: OffsetDateTime`, `placeName: String`, `capacity: int`, `goingCount: int`, `notGoingCount: int`, `undecidedCount: int`, `myAnswer: AttendanceAnswer?` |
| `Notice` | `pinned: boolean` |
| `Poll` | `method: PollMethod`, `closesAt: OffsetDateTime`, `closed: boolean`, `responseCount: int`, `myOptionNo: Integer?` |
| `Photo` | `photoId: long`, `url: String`(`/api/v1/crew-photos/{photoId}`) |
| 규칙 | 삭제·숨김 글은 빠진다(FR-212). 고정 공지는 `pinnedOnly=true`로 따로 읽는다 |

**DELETE /posts/{id}** [변경] — 권한: 작성자 본인 또는 운영진 이상(FR-213). 남의 글이면 감사 `POST_FORCE_DELETE`(행위자 역할 CREW_LEADER 또는 CREW_MANAGER). BOARD의 사진은 함께 지운다. 진행 중 크루런 삭제 규칙(D-66)은 그대로.

**PUT /posts/{id}/attendance** [신규, PUT /posts/{id}/vote 대체 (깨짐)]

| 구분 | 형식 |
|------|------|
| 요청 `AttendanceRequest` | `answer: AttendanceAnswer`(GOING / NOT_GOING / UNDECIDED) |
| 응답 200 `AttendanceResponse` | `postId: long`, `myAnswer: AttendanceAnswer`, `goingCount: int`, `notGoingCount: int`, `undecidedCount: int` |
| 오류 | 409 `POST_NOT_FOUND`, 403 `PERMISSION_DENIED`(크루 밖, D-167, v0.15 D-311 정정), 409 `TARGET_INVALID`(일정이 아닌 글), 409 `VOTE_CLOSED`(크루런 응답 마감·대면 모임 일시가 지남), 409 `SCHEDULE_FULL`(대면 모임 GOING이 정원에 닿음), 409 `CONCURRENT_UPDATE`(낙관적 잠금 재시도 소진) |
| 동작 | `SchedulePost.answer(memberId, answer, now)` 다형성: CrewRunPost는 GOING이면 크루런 참가자(기존 갈래요), MeetupPost는 정원 검사 |

**PUT /posts/{id}/pin** [신규] — 요청 `{pinned: boolean}`, 응답 200 `{postId: long, pinned: boolean}`. 오류 403, 409 `TARGET_INVALID`(공지 아님), 409 `PIN_LIMIT`. crew 행 잠금 안에서 고정 수를 센다.

**GET /posts/{id}/poll** [신규] — 응답 200 `PollResultResponse{postId: long, question: String, method: PollMethod, closesAt: OffsetDateTime, closed: boolean, options: List<{optionNo: int, label: String, count: int, respondents: List<{memberId: long, displayName: String}>}>, myOptionNo: Integer?}`. 응답자는 익명이 아니다(FR-207). 탈퇴 회원 응답은 지워져 있다(FR-021).

**PUT /posts/{id}/poll-response** [신규] — 요청 `{optionNo: int}`, 응답 200 `PollResultResponse`. 마감 전에는 바꿀 수 있다. 오류 409 `POLL_CLOSED`, 409 `POLL_INVALID`(없는 선택지), 403 `PERMISSION_DENIED`(크루 밖, D-167, v0.15 D-311 정정), 409 `POST_NOT_FOUND`.

**GET /crews/{id}/album** [신규] — 질의 `cursor: Long?`(photoId), `size: Integer?`(기본 30). 응답 `AlbumResponse{photos: List<{photoId: long, postId: long, url: String, postedAt: OffsetDateTime}>, nextCursor: Long?}`. 최신순, 삭제·숨김 글의 사진은 빠진다.

**GET /crew-photos/{photoId}** [신규] — 200 `image/jpeg` 바이트, `Cache-Control: private, max-age=300`(5분, 처음 설계 1일) (v0.11 구현 반영, U5-3, D-174). 그 사진이 속한 크루의 현재 구성원만. 오류 403 `PERMISSION_DENIED`, 409 `POST_NOT_FOUND`(사진·글 삭제·숨김).

**PUT /crews/{id}/goal** [신규] — 요청 `{goalMeters: long}`(≥ 1,000, 제안). 이번 주(`Week.of(now)`)의 목표만 정한다. 응답 200 `RaceStatusResponse`. 오류 403, 409 `GOAL_INVALID`(1km 미만, 기존 상수 재사용). 정한 즉시 이번 주 합산이 목표 이상이면 그 자리에서 달성 판정한다(FR-217, 이미 달성한 주는 다시 판정하지 않는다).

**GET /crews/{id}/race** [변경] — `RaceStatusResponse`에 더한다: `goalMeters: Long?`(이번 주 목표, 이어 쓴 값 포함, 없으면 null), `goalProgressPercent: Integer?`(min(100, total × 100 ÷ goal)), `achievedAt: OffsetDateTime?`, `celebration: Celebration?{crewName: String, totalMeters: long, goalMeters: long, participantCount: int}`(달성한 주만). `closesAt`은 OffsetDateTime. 기여자 목록은 순위 숫자 없이 거리 내림차순(FR-140).

##### 크루 채팅 (S3)

| 경로 | 요청 | 응답 | 오류 |
|------|------|------|------|
| GET /crews/{id}/chat/messages | 질의 `before: Long?`(이 ID보다 오래된 것), `after: Long?`(이 ID보다 새 것, 재연결 뒤 메우기), `size: Integer?`(기본 50, 최대 100). before·after 둘 다면 400 | 200 `ChatPageResponse{messages: List<ChatMessageItem>, hasMore: boolean}`. 목록은 항상 오래된 → 새 순(FR-209). 삭제·숨김 메시지는 빠진다. 가입 전 메시지도 보인다 | 409 `NOT_CREW_MEMBER` |
| POST /crews/{id}/chat/messages | `ChatPostRequest{clientMessageId: String(UUID 36자), content: String(앞뒤 공백 제거 뒤 1~500자)}` | 201 `ChatMessageItem`. 같은 작성자·같은 clientMessageId 재전송이면 200 + 처음 만든 메시지(멱등) | 409 `CHAT_EMPTY`, 409 `BODY_TOO_LONG`, 409 `NOT_CREW_MEMBER`, 429 `TOO_MANY_ATTEMPTS`(회원당 초당 5건, 제안) |
| DELETE /chat-messages/{id} | 없음 | 204 | 403 `PERMISSION_DENIED`, 409 `CHAT_MESSAGE_NOT_FOUND`. 남의 메시지면 감사 `CHAT_FORCE_DELETE` |
| PUT /chat-messages/{id}/reactions/{kind} | 경로 kind = ReactionKind(LIKE / HEART) | 200 `ReactionResponse{messageId: long, reactions: Map<ReactionKind, int>, myReactions: Set<ReactionKind>}`. 이미 있으면 그대로(멱등) | 409 `CHAT_MESSAGE_NOT_FOUND`, 409 `NOT_CREW_MEMBER` |
| DELETE /chat-messages/{id}/reactions/{kind} | 같음 | 200 `ReactionResponse`(없으면 그대로) | 같음 |

`ChatMessageItem{messageId: long, authorId: Long?(탈퇴면 null), authorName: String(탈퇴면 "탈퇴한 사용자"), content: String, sentAt: OffsetDateTime, reactions: Map<ReactionKind, int>, myReactions: Set<ReactionKind>, mine: boolean, canDelete: boolean, clientMessageId: String?(본인 메시지만)}`.

##### 신고 (S3)

**POST /reports** [신규]

| 구분 | 형식 |
|------|------|
| 요청 `ReportRequest` | `targetKind: ReportTargetKind`, `targetId: long`(게시글 ID 또는 채팅 메시지 ID), `reason: ReportReason`, `detail: String?`(≤ 200자, reason = OTHER면 필수) |
| 응답 201 `ReportResponse` | `reportId: long`, `status: ReportStatus`(접수 결과 사례 상태: RECEIVED 또는 AUTO_HIDDEN) |
| 오류 | 409 `REPORT_TARGET_NOT_FOUND`(없음·삭제·숨김·종류 불일치), 409 `NOT_CREW_MEMBER`(대상 크루의 구성원이 아님), 409 `TARGET_INVALID`(본인 콘텐츠), 409 `ALREADY_REPORTED`(같은 대상 재신고, 기각 뒤 포함. 기각 대상에 다른 신고자의 새 신고는 사례를 다시 연다), 400 `BAD_REQUEST`(기타인데 detail이 비었거나 200자 초과, D-127) (D-149 (v0.11, 7.13.5 TF-VC-21, TF-VC-22, TF-VC-23)) |

**GET /admin/reports** [신규] — 질의 `status: ReportStatus?`(없으면 RECEIVED·AUTO_HIDDEN), `cursor`, `size`(없으면 30, 상한 100, 범위 밖 400 (v0.11 구현 반영, U5-10, D-179)). 응답 `ReportCaseListResponse{cases: List<ReportCaseItem>, nextCursor: Long?}`. `ReportCaseItem{caseId: long, targetKind: ReportTargetKind, targetId: long, crewId: long, contentPreview: String?(본문 앞 100자, 대상이 지워졌으면 null), targetMissing: boolean, authorId: Long?, status: ReportStatus, reporterCount: int, reasons: Map<ReportReason, int>, firstReportedAt: OffsetDateTime, lastReportedAt: OffsetDateTime}`.

**POST /admin/reports/{caseId}/hide**, **POST /admin/reports/{caseId}/dismiss** [신규] — 본문 없음. 응답 200 `{caseId: long, status: ReportStatus}`. 오류 409 `REPORT_NOT_FOUND`, 409 `REPORT_DECIDED`(이미 HIDDEN·DISMISSED). 숨김은 콘텐츠를 숨긴 채 두고, 기각은 자동 숨김을 푼다. 감사 `REPORT_HIDE`·`REPORT_DISMISS`(행위자 운영자 계정, 대상 caseId), 그 사례의 열린 신고자에게 `REPORT_RESOLVED` 알림.

##### 아바타 배경 (S4)

**GET /avatar** [변경] — `AvatarResponse`에 `backgroundKey: String?`(null = 기본 배경) 추가.

**GET /avatar/backgrounds** [신규] — 응답 `BackgroundsResponse{selectedKey: String?, backgrounds: List<{key: String, name: String, stage: String(DOMESTIC / EAST_ASIA / INTERCONTINENTAL), distanceMeters: long, unlocked: boolean, unlockedAt: OffsetDateTime?}>}`. 순서: 현재 운영 기준값의 개인 랜드마크 순서, 그 뒤에 표에서 빠졌지만 이미 해금한 배경(해금 유지, FR-201).

**PUT /avatar/background** [신규] — 요청 `{key: String?}`(null = 기본 배경). 응답 200 `BackgroundsResponse`. 오류 409 `BACKGROUND_LOCKED`(해금하지 않은 키).

##### 알림 (S3·S4)

**GET /notifications** [변경] — `InboxView.Item.type`에 9종, `target.screen`에 5종이 더해진다(F.5.1, CREW_BOARD 뺌, D-150 (v0.11, 7.13.5 TF-VC-24)). 형식은 그대로.

#### F.2.3 새 사유 코드 (common.ReasonCode, 기본 409. TOO_LARGE 413·UNSUPPORTED_IMAGE 422) (v0.11 구현 반영, U5-A-10)

| 상수 | 문구(message) | 쓰는 곳 |
|------|---------------|---------|
| ALREADY_LINKED | 이미 연동한 계정 | AuthService.linkSocialAccount |
| MANAGER_LIMIT | 운영진은 2명까지 지정할 수 있음 | Crew.appointManager |
| PIN_LIMIT | 공지는 3개까지 고정할 수 있음 | CrewPostService.writeNotice·pin |
| SCHEDULE_INVALID | 일정 입력 오류 | MeetupPost 생성(일시가 지금 이전일 때만, 길이·범위는 400, D-155 (v0.11, test.md v0.6 TF-IT-32)) |
| SCHEDULE_FULL | 정원이 찼음 | MeetupPost.answer |
| POLL_INVALID | 투표 입력 오류 | PollPost 생성(마감이 지금 이전)·respond(선택지 길이·개수는 400, D-155 (v0.11, test.md v0.6 TF-IT-32)) |
| POLL_CLOSED | 마감된 투표 | PollPost.respond |
| POST_EMPTY | 내용이나 사진이 필요함 | BoardPost 생성 |
| PHOTO_LIMIT | 사진은 5장까지 | BoardPost 생성 |
| CHAT_EMPTY | 빈 메시지 | CrewChatMessage.write |
| CHAT_MESSAGE_NOT_FOUND | 메시지 없음 | CrewChatService |
| REPORT_TARGET_NOT_FOUND | 신고할 대상이 없음 | ReportService.submit |
| ALREADY_REPORTED | 이미 신고함 | ReportService.submit(기각 뒤 포함, 고유 색인 위반은 JpaReportCaseRepository.saveReport가 이 코드로 바꾼다, D-149 (v0.11, test.md v0.6 TF-IT-23)) |
| REPORT_NOT_FOUND | 신고 없음 | ReportService.hide·dismiss(D-138) |
| REPORT_DECIDED | 이미 처리한 신고 | ReportService.hide·dismiss(D-138) |
| BACKGROUND_LOCKED | 아직 해금하지 않은 배경 | BackgroundService.choose |
| TOO_LARGE | 사진이 너무 커요 | PhotoUpload(1MB 초과). 413 (v0.11 구현 반영, U5-A-10) |
| UNSUPPORTED_IMAGE | 지원하지 않는 형식 | PhotoUpload·JpegMetadataStripper. 422 (v0.11 구현 반영, U5-A-10) |

쓰지 않게 되는 상수는 지우지 않는다(옛 앱 해독 호환). `TAG_INVALID`는 크루 태그에 계속 쓴다.

#### F.2.4 앱 계약(android/contract)과 깨지는 곳

| 계약 파일 | 바뀌는 것 | 구분 | 앱 쪽 조치 |
|-----------|----------|------|-----------|
| enums/VoteAnswer.txt | `AttendanceAnswer.txt`로 이름이 바뀌고 `UNDECIDED` 추가 | 깨짐 | A1: domain.crew VoteAnswer → AttendanceAnswer, A2: api.crew DTO |
| enums/PostKind.txt | MEETUP, NOTICE, POLL, BOARD 추가 | 깨짐(모르는 값 해독 실패) | A1: PostKind 확장, A2: 모르는 종류의 글은 목록에서 버리는 해독 |
| enums/CrewRole.txt | MANAGER 추가 | 호환(CR34-28 모르는 이름 MEMBER) | A1: MANAGER 추가 |
| enums/NotificationType.txt, Screen.txt | 9종, 6종 추가 | 호환(모르는 종류는 알림함 일반 표시) | A3: 이동 표 추가 |
| enums/ReasonCode.txt | 16개 추가 | 호환(모르는 code는 기본 문구) | A3: 문구 리소스 |
| enums/SettingItem.txt | THEME 추가 | 호환 | A1·A3: 테마 설정 |
| 새 enums | AccountType, ScreenTheme, PollMethod, ReactionKind, ReportTargetKind, ReportReason, ReportStatus | 추가 | A1: domain 모델 |
| dto `SignUpController.SignUpRequest` | 6개 항목 삭제 | 호환(서버가 무시) | A2: 동의만 보내게 |
| dto `MeController.ProfileResponse`·`ProfileEditRequest` | tags 삭제, avatarBackgroundKey 추가 | 호환(앱 기본값) | A2: 태그 삭제 |
| dto `CrewDtos.CrewListResponse`·`.Item` | recommendations·tagMatches 삭제 | 깨짐(앱 DTO가 필수로 두었으면 해독 실패) | A2: 앱 DTO 항목 삭제 |
| dto `CrewDtos.RecommendationsResponse`·`.Item` | 삭제 | 깨짐 | A2: 추천 호출 삭제(GET /crews/recommendations는 404) |
| dto `CrewDtos.CrewProfileRequest`, `CrewMembersAssembler.CrewMembersResponse.Member` | tags 삭제 | 호환 | A2: 태그 삭제 |
| dto `CrewDtos.VoteRequest` | `AttendanceRequest`로 바뀌고 경로 /vote → /attendance | 깨짐 | A2: api.crew 경로·DTO |
| dto `CrewDtos.PostWriteRequest`·`PostListResponse…`·`RaceStatusResponse`·`LobbyResponse` 등 web.crew 전체 | 항목 추가, 시각 OffsetDateTime(D-114) | 깨짐(시각 형식) | A2: 매퍼가 두 형식을 읽게 먼저 |
| dto `MemberSettingsView`·`BattleSettings` | theme·accountType·linkedProvider·proximityAlert 추가 | 호환 | A2: 설정 사본 확장 |
| dto `RunController.RunUploadRequest`, `RecordDtos.RecordDetailResponse` | pauses 추가 | 호환(없으면 정지 없음) | A2: 정지 구간 보내기·표시 |
| dto `AvatarDtos.AvatarResponse` | backgroundKey 추가 | 호환 | A2: 배경 표시 |

---

### F.3 실시간 계약

(v0.13: 이 절의 시각 형식·1008 핸드셰이크 거부 등은 코드와 달라 5.6 D-210~D-217([05-05](05-05-설계-결정-v0.13.md))과 05-03 5.7.2 "v0.13 계약 정정" 문단으로 대체됐다. 이 절은 v0.11 변경 명세 기록으로 남긴다.)

#### F.3.1 경로와 연결

WebSocket 경로는 `/ws` 하나 그대로다. 크루 채팅은 새 경로를 만들지 않고 같은 연결에 메시지 종류(type)를 더한다. 서버는 회원마다 연결 하나만 둔다(SocketSessionRegistry.register가 이전 연결을 닫는다). 그래서 앱은 대결·크루런·채팅이 한 연결을 함께 쓴다(D-103). 채팅 **보내기는 REST**(POST /crews/{id}/chat/messages)이고 실시간 채널은 **받기와 방 입장 알림**만 한다(D-101).

#### F.3.2 메시지 (05-03 5.7.2 형식)

| 메시지 | 방향 | 항목 | 보내는 연산 |
|--------|------|------|------------|
| 진행값 (InboundProgress) [유지] | 앱 → 서버 | 그대로. `type` 항목이 없으면 진행값으로 읽는다(옛 앱 호환). 새 앱은 `type: "Progress"`를 함께 보낸다 | BattleRunCoordinator·CrewRunCoordinator |
| ChatPresence [신규] | 앱 → 서버 | `type: "ChatPresence"`, `crewId: long`, `present: boolean`. 채팅방 화면이 보이면 true, 떠나면 false. 재연결마다 다시 보낸다 | domain.chat `ChatRoom` → `LiveChannel.send(ChatPresence)` |
| CrewChatPosted [신규] | 서버 → 앱 | `crewId: long`, `messageId: long`, `authorId: Long?`, `authorName: String`, `content: String`, `sentAt: OffsetDateTime`, `clientMessageId: String`(작성자 앱이 자기 낙관적 표시와 맞춘다) | CrewChatService.post(커밋 뒤) → CrewChatMessenger.send |
| CrewChatRemoved [신규] | 서버 → 앱 | `crewId: long`, `messageId: long`, `reason: String`(crew.chat.live enum `ChatRemovalReason` 상수 DELETED / HIDDEN) | CrewChatService.delete, ChatReportAdapter.hide |
| CrewChatReactionChanged [신규] | 서버 → 앱 | `crewId: long`, `messageId: long`, `reactions: Map<ReactionKind, int>`(LIKE·HEART 수). 누가 달았는지는 싣지 않는다(본인 상태는 REST 응답으로 안다) | CrewChatService.react·unreact |
| 기존 대결 7종·크루런 4종 [유지] | 서버 → 앱 | 5.7.2 그대로 | 그대로 |

- 채팅 메시지 record는 `crew.chat.live`에 두고 `crew.chat.CrewChatSignal`(`String type()` 하나)을 구현한다. battle.live·crew.live와 같은 규칙이다(13.1 규칙 (7): *.live는 상위 패키지와 common만 import).
- 받는 사람: `CrewChatService`가 `ChatRoomPresence.presentMembers(crewId)` ∩ `CrewMembershipReader.activeMemberIds(crewId)`를 계산해 `CrewChatMessenger.send(memberIds, signal)`을 부른다. 크루를 떠난 회원이 방 입장 상태로 남아 있어도 받지 못한다(FR-209).
- 채팅 메시지는 MessageMailbox에 넣지 않는다. 연결이 없으면 버리고, 앱은 재연결 뒤 `GET …/chat/messages?after={마지막 messageId}`로 메운다. 대결·크루런 메시지의 메일함 동작은 그대로다.
- 방 밖 크루원(현재 구성원 − 방 안 − 작성자)에게는 `CREW_CHAT` 알림(FR-218)을 `NotificationService.notifyCollapsing`으로 보낸다(D-102).
- 폴링 대체 모드(`runkk.live=polling`): `PollingChatPresence`는 빈 집합을 돌려주고 `PollingMessenger`의 채팅 전송은 아무것도 하지 않는다. 앱 채팅방은 2초마다 `?after=`로 읽고, 열린 방 크루의 `CREW_CHAT` 푸시는 앱이 표시하지 않는다(OpenChatRoomTracker).
- 어떤 채팅 메시지에도 위치 항목이 없다(NFR-021 그대로).

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

### F.7 설계 결정 D-89 ~ D-114 (5.6에 그대로 옮긴다)

| ID | 결정 | 근거 | 대안(기각) |
|----|------|------|-----------|
| D-89 | 게스트 계정과 자격 증명. (1) 게스트는 `Member.accountType = GUEST`인 회원이다. 회원ID·토큰·권한·노출은 연동 회원과 같다. (2) 게스트 생성 응답에 기기 자격 증명 `guestCredential`(`g1.<memberId>.<32바이트 base64url>`)을 한 번만 내준다. 서버는 SHA-256 해시만 member.guest_credential_hash에 둔다. 앱은 data.session EncryptedTokenStore에 보관하고, 갱신 토큰이 만료·거부되면 api.core TokenAuthenticator가 토큰을 지우기 전에 POST /auth/guest/session을 한 번 시도한다. (3) 마지막 접속일시는 로그인·토큰 갱신·게스트 세션 재개 때 날짜가 바뀐 경우에만 쓴다. (4) 게스트 생성은 AuthAttemptGuard로 접속지별 한도를 둔다(제안 시간당 10회). 연동하면 자격 증명을 지운다 | 갱신 토큰 수명이 14일이라 2주 넘게 앱을 열지 않은 게스트가 계정을 잃는다. FR-200은 1년 미접속까지 자료를 지킨다. 해시만 두어 DB가 새도 사칭할 수 없다. 접속 일시를 요청마다 쓰면 쓰기가 많다 | 게스트만 갱신 토큰 1년(탈취 피해 큼, 회전 규칙 분기), 기기 식별자(ANDROID_ID) 로그인(재설치 때 바뀜, 개인정보 쟁점), Firebase 익명 인증(새 외부 의존) |
| D-90 | 가입 축소와 소셜 연동. (1) `SignUpForm = (nickname?, consents)`. 닉네임이 없으면 "러너" + 숫자 4자리(겹치면 다른 숫자 20번, 그다음 5자리). D-75(앱이 제공자 이름을 넣는다)는 그대로. (2) 연동은 POST /me/social-account 하나다. 같은 회원ID를 유지하고 account_type을 LINKED로 바꾼다. 소셜 계정이 이미 다른 회원과 연동되어 있으면 ALREADY_MEMBER로 거부하고 합치지 않는다. 연동 회원은 게스트로 돌아갈 수 없다. (3) 계정 유형은 GET /me/settings에만 싣고 프로필·크루·랭킹 응답에는 싣지 않는다(FR-006) | FR-197·FR-198. 계정 합치기는 기록·대결·크루 소유가 겹쳐 규칙이 커진다(SRS 제안 값) | 연동 때 새 회원을 만들고 자료를 옮김(모든 표의 회원 ID 변경), 두 계정 합치기 |
| D-91 | 러너 성향 태그 제거. (1) member.tags와 crew_membership.crew_tags 칼럼을 지우고 값은 옮기지 않는다(V20). (2) `MemberQueryService.tagsOf`, MemberSummary의 태그, SignUpForm·ProfileEdit·CrewProfileEdit의 tags를 지운다. (3) runimal은 태그 없이 판정한다: `RunimalJudge.judge`의 태그 매개변수를 지우고 축 평가기의 "태그가 없으면 기록 3회" 경로만 남긴다. 결과는 지금 태그 없는 회원과 같다(FR-152). (4) 크루 추천(FR-118) 클래스와 GET /crews/recommendations를 지운다. (5) 크루 태그(crew.tags, CrewTags, TagOwner.CREW)와 크루 찾기 태그 AND 필터는 그대로다. TagOwner.RUNNER는 쓰는 곳이 0이 되면 지운다. (6) ai 서버는 영향이 없고 부록 E E-03을 닫는다 | CR-04. 쓰지 않는 개인 성향 자료를 남기면 최소 수집 원칙에 어긋난다 | 칼럼만 남기고 쓰지 않기 |
| D-92 | 테마·근접 알림 설정의 저장 위치. (1) 화면 테마는 서버 회원 설정(member.screen_theme)에 두고 앱 DataStore 사본(SettingsSnapshot.theme)으로 바로 적용한다. app의 MainActivity가 사본 Flow를 구독해 RunkkTheme을 다시 그린다(앱 재시작 없음, FR-203). (2) 근접 알림 여부는 `BattleSettings.proximityAlert`(member.battle_proximity_alert, 기본 true). (3) BATTLE_SETTINGS 값 JSON에 proximityAlert가 없으면 현재 값을 유지한다(`SettingItem.BATTLE_SETTINGS.applyTo`가 병합). (4) 워치 테마는 바꾸지 않는다 | FR-203이 회원 설정 항목으로 정했다. 다른 기기에서 로그인해도 이어진다. 옛 앱이 boolean을 빠뜨리면 Jackson이 false로 읽어 알림이 꺼지는 함정을 막는다 | 기기 전용 설정(기기를 바꾸면 사라짐) |
| D-93 | 자유런 일시정지 모델. (1) 정지 구간은 앱 RunSession이 만든다: `pause(at)`·`resume(at)`, 상태 PAUSED. 정지 중 위치 포인트는 버리고(경로에 넣지 않음) 심박은 남긴다. 재개 뒤 첫 정상 포인트는 직전 정상 포인트와의 거리를 더하지 않는다(lastNormal 초기화). (2) 운동 시간 = 벽시계 경과 − 정지 합, 평균 페이스 = 운동 시간 ÷ 거리, 현재 페이스 창(30초)은 재개 때 비운다. (3) RunMode.FREE만. 대결·크루런이면 pause가 거짓을 돌려준다. (4) 폰 화면 잠금(LOCKED) 중에는 폰 버튼으로 정지하지 않는다(FR-194). 워치의 요청은 폰 잠금과 무관하게 받는다(제안, F.8 확인 필요). (5) 정지 중에도 길게 눌러 종료할 수 있고 마지막 정지 구간은 종료 시각에 닫는다(FR-036). (6) 자동 종료(FR-195): `RunTracker.tick`이 지금 정지가 55분이면 PAUSE_AUTO_END_SOON 안내 한 번, 60분이면 endRun과 같은 저장 규칙으로 끝내고 PAUSE_AUTO_ENDED를 안내한다. (7) 서버는 정지 구간을 검사·저장하고 판정에만 쓴다(RunUpload 검사, RunTrack 경계 구간 제외, D-94). (8) 1km 안내·목표 도달·코칭은 운동 거리로 하고 정지 중에는 평가하지 않는다(FR-049) | 측정은 앱, 판정은 서버라는 기존 구조(D-56, A-04)를 그대로 쓴다. 정지 중 위치를 경로에 넣으면 거리와 지도가 오염된다 | 서버가 위치 공백으로 정지를 추정(오판), 정지 중 위치를 경로에 남김 |
| D-94 | 유효 러닝 다섯째 조건(CR-10, CR34-08). `RunRecord.judgeValidity`는 `rule.isSatisfiedBy(distance, elapsed, averagePace, track.maxSegmentSpeedKmh(pauses))` 그리고 `rule.isBackedBy(distance, track.pathLength(pauses), elapsed, Duration.between(startedAt, endedAt).minus(pausedTotal()))`이다. 선언 거리 ≤ 경로 길이 × 2, 선언 운동 시간 ≤ 벽시계 − 정지 + 1초(`ValidityRule.MAX_DECLARED_TO_PATH_RATIO`, `WALL_CLOCK_TOLERANCE` 그대로). 판정은 저장 때 한 번이고 경로 파기 뒤 다시 하지 않는다 | 팀 결정 2026-10-22 "넣음", 코드 isBackedBy 그대로. 정지가 생기면서 벽시계와 운동 시간이 달라졌다 | 다섯째 조건 없이 4조건 유지(조작 기록을 막지 못함) |
| D-95 | RunTracker 책임 분리(S-1, 생성자 주입 4개). `RunTracker(archive: RunArchive, watchBridge: RunWatchBridge, coach: RunCoach, runtime: MeasurementRuntime)`. RunArchive(PendingRunRepository, UploadScheduler, CoroutineScope): `saveInBackground(run, onFailed)`(세 번 시도), `appendLateHeartRates(recordId, samples)`. RunWatchBridge(WatchGateway, CoroutineScope): `bind(listener: WatchRequestListener)`(시작·종료·일시정지 요청과 심박을 RunTracker의 내부 리스너로 넘긴다), `publish(display)`, `publishHome(plan)`, `publishProximity(alert?)`, `publishLeadChange(kind)`. RunCoach(GuidanceOutput, SettingsRepository): `begin(mode)`(CoachingJudge를 회마다 새로), `guide(progress, session)`(GuidanceContext 조립·평가·출력), `play(guidance)`, `proximityOf(gapMeters): ProximityAlert?`. MeasurementRuntime(LocationPermission, MeasurementHost, SettingsRepository, Clock): `checkStartable()`, `startForeground()`, `stop()`, `now()`. RunTracker의 공개 연산은 그대로이고 `onPauseTapped()`·`onResumeTapped()`를 더한다. D-73(생성자 주입은 매개변수 기준에서 뺀다)은 일반 규칙으로 남기고 RunTracker에만 4개 이하를 적용한다 | 결정 대기 S-1 팀 결정. 일시정지·근접 알림을 얹으면 RunTracker 의존이 10개가 된다. 협력 객체마다 책임이 한 문장이다 | 8개 유지 |
| D-96 | 근접 알림은 폰이 계산하고 워치는 재생만 한다(F.4.3). domain.run `ProximityAlertRule`이 격차·설정·연결로 `ProximityAlert(gapMeters, interval, isAhead)`를 만들고 `RunWatchBridge`가 ProximityMessage로 보낸다. 워치 watch.alert `ProximityAlertPlayer`는 간격대로 비프음·진동을 반복하고 3초 동안 새 메시지가 없으면 멈춘다. 추월·역전은 LeadChangeMessage로 1초 긴 진동 한 번 | 격차는 폰만 안다(랜덤은 서버 OpponentProgress, 기록 대결은 GapTracker). 설정도 폰 사본에 있다. 규칙을 한 곳에 둔다 | 워치가 격차로 간격 계산(규칙 두 곳, 격차를 매초 보내야 함), 서버가 워치로 직접(경로 없음) |
| D-97 | wear-contract 하위 호환: 경로는 더하기만 하고, 새 항목은 기본값 있는 선택 항목이며, 기존 항목의 이름·의미를 바꾸지 않는다. Boolean 이름 교정(결정 대기 N3-6: `running`, `heartRateCollectionAllowed`)은 Kotlin 이름만 `isRunning`·`isHeartRateCollectionAllowed`로 바꾸고 `@SerialName`으로 JSON 키를 지킨다. 새 Boolean도 Kotlin `is…`·JSON 접두어 없음 | 폰과 워치는 따로 갱신된다(Play 배포 시차). 계약 시험이 키 호환을 지킨다 | 메시지 버전 번호(지금 규모에 과함), 키 이름까지 교정(옛 앱 깨짐) |
| D-98 | 크루 운영진. `CrewRole.MANAGER`(CrewMembership.role). 2명 상한은 crew 행 잠금(CrewAccess.checkForUpdate) 안에서 `Crew.appointManager`가 센다. 권한은 `CrewPermissionPolicy` 표 하나(F.5.1). 운영진이 탈퇴·강퇴·크루 떠나기를 하면 역할도 사라진다. 크루장 양도 때 새 크루장이 운영진이었으면 그 자리가 비고 이전 크루장은 MEMBER가 된다. 감사 행위자 역할 CREW_MANAGER | FR-204, FR-128, 3.0.3. 정원·가입 결정과 같은 잠금 경계(5.3) | 별도 표 crew_manager(역할 정보가 두 곳) |
| D-99 | 크루 게시 계층. `CrewPost` ← CheerPost, `SchedulePost`(추상) ← CrewRunPost·MeetupPost, NoticePost, PollPost, BoardPost. 단일 테이블 crew_post(구분 값 post_kind)를 유지한다. 일정 응답은 `SchedulePost.answer` 다형성이다: 크루런은 crew_run_participation(기존), 대면 모임은 crew_meetup_attendance(신규). `VoteAnswer`를 `AttendanceAnswer`(+ UNDECIDED)로, 경로 /vote를 /attendance로 바꾼다. "투표"는 PollPost만 뜻한다. 본문 길이는 하위 클래스 `maxBody()`(응원·크루런·대면 모임·투표 200, 공지·게시글 1,000) | 두 일정은 일시·참석 응답을 함께 갖지만 대기실·동시 출발·중계는 크루런만 갖는다(FR-132). MeetupPost를 CrewRunPost 하위로 두면 쓰지 않는 상태를 물려받는다(1.3.7 거부한 상속에 적는다). 상속 깊이 3 | 종류마다 표(목록이 UNION), CrewRunPost에 대면 플래그(타입 분기 반복) |
| D-100 | 게시글 사진. (1) 글과 사진을 multipart 한 요청(POST /crews/{id}/board-posts)으로 받아 한 트랜잭션에 저장한다. (2) 앱이 JPEG·긴 변 1600px·1MB 이하로 줄여 보낸다. "장당 10MB 이하 JPEG·PNG"(FR-211)는 앱이 고를 수 있는 원본 상한이다. 서버는 장당 1MB·요청 6MB(spring.servlet.multipart max-file-size 1MB 그대로, max-request-size 2MB → 6MB, 메모리 수신 D-59 유지)와 JPEG 머리를 확인한다. nginx client_max_body_size 10m는 그대로. (3) 저장은 PostgreSQL crew_photo.jpeg(BYTEA)이고 포트 `PostPhotoStore` 뒤에 둔다(infra.crew JdbcPostPhotoStore, 나중에 객체 저장소로 바꿀 수 있게). (4) 조회는 GET /crew-photos/{id}, 현재 크루 구성원만, private 캐시 5분 (v0.11 구현 반영, U5-3, D-174). (5) 글 삭제·숨김이면 조회를 거부하고, 글 삭제·작성자 탈퇴(FR-021) 때 사진 행을 지운다. (6) 용량 가정: 크루원 50명 × 주 5장 × 0.5MB ≈ 주 125MB 이하(제안). 5.4 백업(7일 보존)과 함께 적고 디스크 사용량을 감시한다 | face_part_image와 같은 저장 방식이라 새 외부 의존·비용이 없고 글과 함께 지워진다(SRS 7.13.2 "사진 저장소 비용") | S3 + 프리사인 URL(외부 의존·키 관리), 컨테이너 볼륨 파일(백업·정리 따로) |
| D-101 | 크루 채팅 저장·전송. 새 패키지 crew.chat(crew → crew.chat 화살표 없음). 보내기는 REST(clientMessageId로 멱등, 고유 색인), 받기는 기존 /ws(메일함 없음, F.3), 조회는 메시지 ID 커서(before·after, 기본 50, 최대 100), 저장은 PostgreSQL에 영구(보관 기한 없음, 크루 삭제·탈퇴 규칙만), 삭제는 표시(deleted) 후 목록 제외, 리액션은 별도 표. 채팅방 입장 여부는 realtime 메모리(ChatPresenceRegistry: crewId → memberIds, 소켓이 닫히면 지움)이고 api 컨테이너 1개 전제(5.4)를 따른다. 커밋 뒤 전송은 crew.chat 안의 도메인 사건 + `@TransactionalEventListener(AFTER_COMMIT)`로 하고 AfterCommit 사본을 새로 만들지 않는다(L4) | crew는 이미 100개 가까운 클래스이고 채팅은 생애주기가 다른 고빈도 객체다. 보내기를 REST로 두면 인증·검증·오류 응답·멱등을 기존 경로가 처리하고, 실시간 채널의 "앱 → 서버는 짧은 신호뿐" 원칙이 유지된다 | 별도 STOMP·/ws/chat(회원당 연결 1개 원칙 깨짐), Redis 저장(백업 없음), crew 패키지 안에 둠(응집 저하) |
| D-102 | 채팅 알림 묶기. 방 밖 크루원(현재 구성원 − 방 안 − 작성자)에게 CREW_CHAT을 `NotificationService.notifyCollapsing`으로 보낸다: 같은 수신자·같은 크루(target_id = crewId)에 읽지 않은 CREW_CHAT 알림이 있으면 새 행을 만들지 않고 그 행의 내용·일시를 바꾸며, 푸시는 FCM collapse key `crew_chat_<crewId>`로 보낸다. 수신 설정(PushDecision, FR-175)을 확인한다. 앱은 열린 채팅방의 크루면 표시하지 않는다(data.push OpenChatRoomTracker, 폴링 모드 보완) | 메시지마다 알림 행을 만들면 알림함이 채팅으로 덮인다(FR-218) | 알림 없음(FR-218 위반), 메시지마다 알림 |
| D-103 | 앱 실시간 연결 하나 공유. `LiveChannelProvider.open()`은 연결 하나에 대한 임대를 돌려주고 참조 수가 0이 될 때만 실제 연결을 닫는다(data.live FallbackLiveChannelProvider). 보내기는 `LiveChannel.send(m: ClientMessage)`(sealed: ProgressReport, ChatPresence), 받기 Flow는 공유하고 사용자마다 필요한 메시지만 거른다 | 서버는 회원당 연결 하나이고 새 연결이 이전 연결을 닫는다. 크루런 대기실과 채팅방을 함께 열면 서로 끊는다. 지금 open()은 재사용하지만 close()가 모두를 닫는다 | 서버가 회원당 여러 연결(SocketSessionRegistry·메일함 전면 변경) |
| D-104 | 신고 처리 위치. 새 패키지 report. 대상 하나에 `ReportCase` 한 행 + 신고 한 건마다 `ContentReport`. 자동 숨김은 `ReportService.submit` 트랜잭션 안에서 report_case 행을 잠그고(없으면 만들고 고유 제약 충돌이면 다시 읽어 잠금) `ReportCase.addReport()`가 서로 다른 열린 신고자 3명에 닿으면 AUTO_HIDDEN으로 바꾼 뒤 같은 트랜잭션에서 `ReportedContentPort.hide`를 부른다. 숨김 표시(crew_post.hidden, crew_chat_message.hidden)는 콘텐츠를 가진 패키지(crew, crew.chat)가 포트를 구현해 바꾼다. 운영자 처리는 사례 단위다: HIDE는 숨김 유지(아니면 숨김), DISMISS는 unhide. 열린 신고를 resolved로 두고 그 신고자에게 REPORT_RESOLVED. 기각 뒤 새 신고는 사례를 다시 연다(열린 신고자 0부터). 같은 회원은 같은 대상을 한 번만 신고한다(고유 색인) | FR-214~FR-216. "기각하면 자동 숨김을 푼다"는 대상 단위 상태가 필요하다. 동시 신고 경쟁을 행 잠금으로 막는다. report는 crew를 모르고 crew·crew.chat이 포트를 구현해 순환이 없다 | crew 안에 신고(게시글·채팅 두 곳 중복), 비동기 집계 배치(숨김 지연) |
| D-105 | 크루 공동 목표. WeeklyRace(crew_id, week_start) 행에 goal·achievedAt를 둔다. 목표 설정은 그 주 행을 만들거나(합계 0) 고친다. 이어 쓰기: 주 행을 처음 만들 때(진행값 원자적 증가의 첫 행, setGoal, reconcileAll) 그 주 목표가 없으면 `WeeklyRaceRepository.latestGoalBefore`로 직전 목표를 복사한다(FR-138). 처음 만든 크루는 목표 없음. 달성: 원자적 합산 증가(A-12) 직후 같은 트랜잭션에서 `markAchievedIfReached`(조건부 UPDATE … WHERE achieved_at IS NULL AND goal_meters IS NOT NULL AND total_meters >= goal_meters)가 한 행을 바꾸면 커밋 뒤 모든 크루원에게 CREW_GOAL_ACHIEVED. reconcileAll(0시 30분)·setGoal도 같은 연산을 부른다. 축하 카드는 저장하지 않고 조회 때 만든다(참여 인원 = 그 주 race_contribution의 서로 다른 회원 수) | FR-217 "한 주에 한 번", 두 기록이 함께 넘겨도 알림은 한 번 | 별도 표 crew_goal(조인 증가), 배치로만 판정(알림 지연) |
| D-106 | 크루 소식 알림(FR-218). `CrewNewsNotifier`가 커밋 뒤 작성자를 뺀 현재 크루원에게 CREW_NOTICE·CREW_POLL·CREW_SCHEDULE을 보낸다. 투표 마감 임박은 batch.crew `PollReminderJob`(1분 간격) → `CrewPostService.remindClosingPolls(now)`: 마감이 now ~ now + 1시간이고 reminded가 거짓인 투표의 미응답 현재 크루원에게 POLL_CLOSING_SOON, reminded를 참으로(크루런 CREW_RUN_SOON과 같은 칼럼). 모든 알림은 PushDecision으로 거른다. 운영진 지정·해제는 대상에게 MANAGER_CHANGED | 1시간 전 알림은 1분 정밀도면 충분하다. 1초 시계에 얹지 않는다 | CrewRunTickJob(1초)에 얹기 |
| D-107 | 랜드마크 배경. (1) `Landmark`에 문자열 key를 더한다. 기본 키: 인천 INCHEON, 개성 KAESONG, 대전 DAEJEON, 전주 JEONJU, 여수 YEOSU, 부산 BUSAN, 제주도 JEJU, 베이징 BEIJING, 도쿄 TOKYO, 타이베이 TAIPEI, 홍콩 HONG_KONG, 하노이 HANOI, 방콕 BANGKOK, 쿠알라룸푸르 KUALA_LUMPUR, 호놀룰루 HONOLULU, 시드니 SYDNEY, 런던 LONDON, 파리 PARIS, LA LOS_ANGELES, 뉴욕 NEW_YORK, 산티아고 SANTIAGO, 지구 둘레 1바퀴 EARTH_LAP, (크루 단계) 지구 2바퀴 EARTH_2_LAPS, 10만 km KM_100K, 20만 km KM_200K, 달나라 진입 MOON_APPROACH, 달까지 MOON, 50만 km KM_500K, 달 왕복 MOON_ROUND_TRIP, 100만 km KM_1M. 배경은 개인 단계(DOMESTIC·EAST_ASIA·INTERCONTINENTAL) 22개만이다. 앱은 키로 그림을 찾고 모르는 키는 단계별 기본 그림을 쓴다. (2) 해금은 avatar가 한다: BackgroundUnlockListener(RunRecordSaved, 유효 기록, AFTER_COMMIT) → `BackgroundService.unlockReached`: `RunQueryService.totals(memberId)`의 누적 유효 거리 ≥ 랜드마크 거리인 개인 랜드마크 중 없는 것을 avatar_background에 키·이름·단계·거리 스냅숏으로 넣고 BACKGROUND_UNLOCKED 알림(여럿이면 한 건, 새로 해금한 배경 이름을 모두 적는다, D-148 (v0.11, 7.13.5 TF-VC-20)). (3) 보정: batch.avatar BackgroundReconcileJob(0시 10분)이 최근 2일 저장 기록의 회원으로 unlockReached를 부른다. 배포 전에 이미 넘은 랜드마크는 GET /avatar·GET /avatar/backgrounds 조회 때 알림 없이 채운다. (4) 해금은 취소하지 않는다(기준값이 바뀌어도 행을 지우지 않는다). (5) 선택은 `Avatar.selectedBackgroundKey`, 해금 행이 있는 키만 | FR-201·FR-202. 기준값이 바뀌어도 해금을 유지하려면 순번·이름이 아닌 고정 키와 스냅숏이 필요하다. avatar는 이미 running·policy·notification에 의존해(D18·D20·D21) 새 화살표가 없다 | 순번(표가 바뀌면 어긋남), 이름(수정·번역 때 깨짐), analysis가 해금(analysis → avatar 새 화살표) |
| D-108 | 티어 갱신 보정 배치(결정 대기 L16). `running.TierReconcileService.reconcile(now)`: `RunQueryService.savedBetween(오늘 − 2일 0시, now)`의 유효 기록 회원마다 `MemberQueryService.appliedValidRunCount(memberId)`(TierProgress.validRunCount)와 running의 유효 기록 수를 비교하고, 모자라면 `RunQueryService.validRunsAfter(memberId, applied)`를 저장 순서대로 `TierLevelService.recordValidRun(memberId, RunScoreInput(score, distance, ordinal))`에 넘긴다. TierProgress.addValidRun이 반영 순번 이하를 건너뛰므로 멱등이다. 회원마다 REQUIRES_NEW, 실패는 로그 뒤 다음 회원. batch.running `TierReconcileJob`, cron `runkk.batch.tier-reconcile-cron` 기본 `0 10 0 * * *`(RewardReconcileJob과 같은 0시 10분). TierUpdateListener는 그대로(실패는 로그, 보정이 채운다 — M4 결정과 같은 꼴). D-88의 0시대 작업 목록에 보정 작업으로 더한다 | 팀 결정. 리워드·크루런 연결 보정(CR34-02·03)과 같은 방식이라 운영·시험이 같다. running → member(D3) 방향 그대로 | 실패 큐 표(새 저장소), 리스너 재시도만(일시 장애가 길면 유실) |
| D-109 | 리워드 집계 칼럼(결정 대기 L14). RewardWallet에서 적립 내역 컬렉션 매핑을 없애고(지갑을 읽을 때 전체 내역을 읽지 않는다) `last_accrual_date`·`last_accrual_date_amount` 두 칼럼을 둔다. 적립: 중복은 `RewardAccrualRepository.amountFor`(유일 색인)로 확인, 그날 합계는 날짜가 last와 같으면 칼럼 값, 더 새 날이면 0, 더 옛날(보정 배치의 지난 날짜)이면 `sumOn`(member_id, accrued_at 색인). 적립 뒤 날짜가 last 이상이면 칼럼을 고친다. 지갑 행 잠금과 고유 제약(5.3)은 그대로 | 팀 결정 "집계 칼럼". 보정 배치가 원래 사건일로 하루 상한을 세므로(M4) 칼럼 하나로 모든 날을 담을 수 없어 가장 최근 날만 칼럼, 지난 날은 색인 합계로 한다 | 일별 집계 표(행 증가), 칼럼 없이 SUM 조회만 |
| D-110 | 실시간 토큰 만료(결정 대기 L29, F.3.3). `TokenIssuer.verifyGrant`가 `AccessGrant(memberId, expiresAt)`를 돌려주고, SocketSessionRegistry가 만료 시각에 4401로 닫는 작업을 예약하며, LiveSocketHandler가 받을 때마다 만료를 확인한다. 앱은 4401이면 `SessionRefresher.refreshNow()` 뒤 바로 재연결한다(재연결 간격·횟수에 넣지 않는다). 대결·크루런·채팅 공통 | 팀 결정 "만료 시 연결 종료 + 앱 재연결". 탈퇴·폐기된 토큰으로 연결이 계속되는 것을 막는다 | 만료 무시(지금), 소켓 메시지로 토큰 갱신(프로토콜 복잡) |
| D-111 | 도메인 문구 키화와 Boolean 접두어(CR34-13, U4-10). domain.\*은 문구를 갖지 않고 enum 키(GuidanceMessage, WatchNotice)와 인자만 갖는다. 문자열 변환은 data 계층(data.device GuidanceTexts, data.watch의 WatchNotice.textRes)이 strings.xml로 한다. 화면 문구는 strings.xml과 core.ui UiText(Res·Plain). 새 키(F.5.1)도 같다. domain Boolean 이름은 is·has·can만 쓴다(should 불허): GuidanceSettings.shouldFollowCoachPlan → isFollowingCoachPlan, 설정 필드 locationConsented → isLocationConsented, runVoice → isRunVoiceOn 등, UploadReceipt.isAlreadySaved. 직렬화 이름이 걸린 곳은 @SerialName(D-97). 2차 컨벤션 갱신 요청 8(한글 리터럴 허용안)은 폐기 | 팀 결정 "제안대로". 기준표에 should가 없어 새 접두어를 만들지 않는다 | should 허용(기준표 개정 필요) |
| D-112 | 한 파일 허용 규칙(CR34-19). 타입마다 한 파일이 원칙이다. 예외는 둘: (1) sealed 계층(부모와 하위 타입, 예 domain.live.ServerMessage·ClientMessage), (2) private 함수를 공유하는 짝 UseCase(예 LoginUseCase·SignUpUseCase). 그 밖에 한 파일에 여러 최상위 타입을 두면 경 결함. 팀 컨벤션 1장에 같은 문장을 넣고 S-3을 닫는다 | 팀 결정 "제안대로" | 무조건 한 파일 한 타입(sealed 하위가 흩어짐) |
| D-113 | (제안) 값의 위치. ProximityAlertRule(100m, 2.0~0.3초, 3초 만료), PauseLimit(55·60분), Crew.MAX_MANAGERS(2), NoticePost 고정 3, ReportCase.AUTO_HIDE_REPORTERS(3), CrewChatMessage 500자, BoardPost 5장, WithdrawalService.GUEST_IDLE_LIMIT(1년), 투표 마감 알림 1시간, 공동 목표 최소 1km는 소유 클래스의 상수다. 운영 기준값으로 올리지 않는다 | SRS 운영 기준값 목록(FR-174)에 없고 관리툴 변경 요구가 없다. 바꾸려면 배포한다 | SettingKey 추가(SRS 7.2 개정 필요) |
| D-114 | 새 web 전송 형식의 시각은 OffsetDateTime(D-84)이다. web.crew는 S3에서 기존 LocalDateTime 항목까지 모두 바꿔 D-84의 남은 반영을 끝낸다. 앱 api.crew 매퍼는 A2에서 두 형식(오프셋 있음·없음)을 모두 읽게 먼저 바꾸고, 서버 S3 배포 뒤 오프셋 없는 해독을 지운다 | D-84 미반영분이 web.crew에 남아 있다(코드 CrewDtos의 LocalDateTime). 이번에 크루 DTO를 대부분 다시 만든다 | 새 항목만 오프셋(한 응답 안에 형식이 섞임) |

---

### F.8 미정·보류·확인 필요

#### F.8.1 /sw-design 검증 단계로 넘기는 결정: P-1 common 경계 (CR34-11·CR2-U3 = L3, CR2-U5의 위치 부분 = L4)

| 선택지 | 내용 | 장점 | 단점 |
|--------|------|------|------|
| A. common 예외 등록 | AttemptLimiter·PageCursor를 common 허용 목록(D-48)에 예외로 적고, AfterCommit 2개·*Transactions 5개는 패키지별 사본을 두되 차이를 13.1에 적는다 | 코드 이동 0, 화살표 변화 0 | common의 "불변 기본 형식만" 조건이 깨진다(AttemptLimiter는 가변 메모리 상태). 사본 중복(L4)이 남는다 |
| B. 쓰는 패키지로 이동 | PageCursor는 crew·web.crew·infra.crew로, AttemptLimiter는 web.auth·web.admin·crew로 옮긴다 | common이 깨끗해진다 | 이번 변경으로 PageCursor를 crew.chat·report·web.chat·web.report·web.admin.report도 쓴다. 사본이 늘거나 새 화살표가 생긴다 |
| C. 지원 패키지 신설 | PageCursor는 불변 값이므로 common에 남긴다. 가변·Spring 의존 실행 도구(AttemptLimiter, AfterCommit, 재시도 트랜잭션 틀 RetryingTransactions)는 새 패키지 `support`(계층: 기반, Fan-Out은 common뿐)로 모으고 *Transactions 5개와 AfterCommit 2개를 하나로 줄인다 | 중복 제거, common 조건 유지, Fan-In이 크고 Fan-Out이 작은 안정 패키지 | 패키지 1개·화살표 여러 개 추가, ArchitectureTest 갱신 |

권고: **C**(PageCursor는 common 유지 + support 신설). PageCursor는 불변 값이고 이번 변경으로 쓰는 패키지가 6개 이상으로 늘어 공유 가치가 크다. AttemptLimiter·AfterCommit·재시도 틀은 가변 상태·Spring 의존이라 common 조건(D-48)에 맞지 않고, 패키지별 사본은 L4 중복이다. 결정은 `/sw-design review`(design-review.md)에서 Fan-In/Fan-Out·불안정도를 계산한 뒤 한다. 그 전까지 구현은 현 위치를 그대로 쓴다. 새 패키지(crew.chat·report)는 PageCursor를 common에서 쓰고, 커밋 뒤 작업은 AfterCommit 사본을 만들지 않고 도메인 사건 + `@TransactionalEventListener(AFTER_COMMIT)`로 한다(D-101).

**결정(v0.11 검증, D-132)**: design-review.md 17.5.2에서 Fan-In/Fan-Out·불안정도·응집도를 비교해 **수정 C**로 정했다. PageCursor는 common, AttemptLimiter·ClientAddress는 새 표현 패키지 web.attempt(Fan-In 4, Fan-Out 0), AfterCommit·*Transactions는 패키지 전용 사본이다. 권고 C의 support는 쓰는 패키지의 Fan-Out을 analysis 8·crew 9로 밀고 사본마다 다른 정책을 묶으면 제어 매개변수가 생겨 기각했다.

#### F.8.2 보류(이번 v0.11에서 설계를 바꾸지 않음, 팀 결정 2026-10-22)

| ID | 내용 | 설계에 적을 것 |
|----|------|---------------|
| L23 | /admin/** permitAll 한 겹(이중 방어) | 5.4 또는 13.1에 "보류(2026-10-22)" 한 줄 |
| L25 | X-Real-IP 신뢰 프록시 설정화 | 5.4에 보류 한 줄 |
| L26 | 서버 → ai 평문 http | 5.4에 보류 한 줄(compose 내부 망 가정 유지) |
| N3-7 | 3차 커밋 이력 재작성 | 설계 대상 아님 |
| N-14 | 커밋·브랜치 Jira 키 | 설계 대상 아님 |

#### F.8.3 확인 필요 (부록 E에 새 항목으로)

| 가칭 | 내용 | 이 명세의 잠정 처리 |
|------|------|-------------------|
| E-v011-1 | 워치의 일시정지 요청을 폰 화면 잠금 중에도 받는가(FR-194 "화면 잠금 중에는 받지 않는다"의 범위) | 받는다(D-93 (4), 잠금은 폰 화면 오조작 방지). **닫음: srs FR-182·FR-194 보완 (v0.11, 7.13.5 TF-VA-33)** |
| E-v011-2 | 대면 모임·투표 질문 본문 길이, 선택지 길이(SRS에 값 없음) | 200자, 200자, 40자 |
| E-v011-3 | 신고 사유 "기타"의 내용 필수 여부와 길이 | 필수, 200자. **닫음: srs FR-214 보완 (v0.11, 7.13.5 TF-VC-22)** |
| E-v011-4 | REPORT_RESOLVED 알림을 누르면 가는 화면 | CREW_HOME. **닫음: srs FR-178·FR-216 보완 (v0.11, 7.13.5 TF-VC-24)** |
| E-v011-5 | FR-211 "장당 10MB 이하"와 서버 압축본 1MB의 관계(SRS 문구 보완 요청) | 10MB는 앱이 고르는 원본 상한, 서버는 압축본만 받는다(D-100) **닫음: srs FR-211 보완 (v0.11, 7.13.5 TF-VC-25)** |
| E-v011-6 | 채팅 메시지 보관 기한(개인정보 처리방침) | 기한 없음, 크루 삭제·탈퇴 규칙만 |
| E-v011-7 | 게스트 생성 한도와 다중 계정 랭킹 어뷰징 위험 수용(plan 9절 위험) | 접속지별 시간당 10회 **닫음(한도 값): srs FR-197 보완 (v0.11, 7.13.5 TF-VA-34)** |
| E-03 | 크루 추천 위치 | 닫음(FR-118 삭제) |

---

### F.9 코드 리뷰 설계 갱신 요청 41건 처리표

대상은 2차 CR2-* 중 CR34로 넘어가지 않은 13건(CR2-U1·U2·U4·U5·U7·U8, CR2-A1~A7)과 CR34-01~CR34-28이다(CR2-U3 → CR34-11, CR2-U6 → CR34-08, CR2-I1~I3 → CR34-10). "v0.10 상태"는 design-oo/ 조각 파일을 키워드로 grep해 확인한 결과다. 담당 묶음은 F.10의 (가)~(마).

| ID | 고칠 설계 파일·절 | 반영 내용 | v0.10 상태 | 담당 |
|----|------------------|----------|-----------|------|
| CR2-U1 | 05-03 5.7.1; 06-01 MemberQueryService; 06-03 MatchingService; 06-04 CrewQueryService; 06-05 avatar | GET /battles/match(MatchingService.status, 기본 대결 거리), GET /crews/me(myCrew), GET /crews/{id}(detail), GET /avatar/parts/face/{ownerId}/{set}/{part}(FaceImageService.partImage), MemberQueryService.profileOf를 표·명세에 올린다. 함께 GET /admin/settings, POST /admin/rankings/recompute(RankingAdminController, RANKING_RECOMPUTED, V18) | 미반영 | 나·다·라 |
| CR2-U2 | 05-01 policy·operator 행; 06-05 6.1.9 OperatorWhitelistSeeder 절·6.1.12; 12.2 D40·D41 | policy.OperatorWhitelistSeeder → operator.OperatorBootstrap(위치·이름), 의존 operator → policy·audit | 미반영(Seeder 이름 그대로) | 나·다 |
| CR2-U4 | 05-01 bootstrap 행; 06-06 6.1.13 bootstrap 행 | ProductionSecretsCheck(운영 프로필 필수 비밀 값 확인) 추가, 책임 문장 "시작하고 실행 환경을 조립·점검한다" | 일부(EmbeddedRedisConfig만 있음) | 나·마 |
| CR2-U5 | 13-14 13.1 트랜잭션 규칙 | 낙관적 재시도가 필요한 쓰기는 TransactionTemplate 기반 `*Transactions`(재시도 3회), 커밋 뒤 실행은 AfterCommit 헬퍼(기존) 또는 도메인 사건 + AFTER_COMMIT 수신(새 코드, D-101). 통합 위치는 F.8 P-1 | 미반영 | 나 |
| CR2-U7 | 05-01 각 패키지 포함 클래스; 06-01~06-06 각 패키지 표 | 설계에 없는 보조 클래스를 한 줄씩 올린다. grep으로 확인한 목록: common AttemptLimiter·ClientAddress·PageCursor, member MemberLoader·MemberProfile, running SavedRun, analysis AnalysisTransactions·RankingPage·RankingRecomputeResult·RecordRow, battle AfterCommit·BattleDelivery·BattleRandomConfig·BattleTransactions·BattleWin·OpponentNames, crew AfterCommit·CrewAccess·CrewDeletion·CrewDetailView·CrewRunAnnouncer·CrewRunLinkReconcileService·CrewRunViews·CrewTags·CrewTransactions·MyCrewView, runimal RunSampleReader·RunimalTransactions, avatar AvatarTransactions·FaceImageService·SelfieConversion·SelfieFailureStore·SelfieLockStore, reward AccrualSource·RewardReconcileService, operator OperatorBootstrap, realtime MessageMailbox·ProgressDispatcher·TokenHandshakeInterceptor, bootstrap ProductionSecretsCheck, web.auth ActiveMembers·AuthAttemptGuard | 미반영 | 나·다·라·마 |
| CR2-U8 | 06-03 Matchmaker, D-64·D-79 | 결함 M1이 코드에서 해결(Matchmaker tryAssign·release, ghostAfter + 15초에만 MatchFailed)되어 설계가 이미 맞다. 고칠 것 없음 | **반영됨** | — |
| CR2-A1 | 05-01 5.1.2 domain.run; 06-06 조정자 | CoordinatedRound(internal): 대결·크루런 조정자 공용 "한 회" 기반, 같은 모듈이라 화살표 없음 | 미반영 | 나·마 |
| CR2-A2 | 06-06 CrewRunCoordinator.onLobbyEntered; 05-03 CrewRunRelay | onLobbyEntered에서 myName 삭제, 본인 줄은 Entry.isMe(@SerialName("mine"), D-161 (v0.11, test.md v0.6 TF-AN-32)) | 미반영(myName 그대로) | 마·나 |
| CR2-A3 | 05-01 app 행; 06-06 RunkkApp 행 | AppStartup(시작 작업·로그인 전환 구독)을 더하고 RunkkApp은 AppStartup만 부른다. LoginState(domain.notification) 구독은 이미 있음 | 일부 | 나·마 |
| CR2-A4 | 06-06 조정자·RunTracker 연산 표 | acknowledgeFinished·failure, abandonMatching(FR-085), RunTracker.publishWatchHome | 미반영 | 마 |
| CR2-A5 | 13-14 13.2 빌드·플레이버, 14 | E2E 경로(localDebug 소스셋, 빌드 속성, testTagsAsResourceId)와 release에 섞이지 않는 조건 | 미반영 | 나 |
| CR2-A6 | 06-06 ui.me SocialLoginClient 행 | KakaoAccessTokenSource.requiresNativeAppKey | 미반영 | 마 |
| CR2-A7 | 05-01 5.1.2 api.core·api.avatar 행 | SelfMember, FaceImageSource(계정별 얼굴 부위 캐시, 크기 상한, 로그아웃 때 비움) | 미반영 | 나 |
| CR34-01 | 02 2.4.2 대결 상태도; 06-03 Battle·BattleStatus·BattleRepository·BattleStartService·RecordBattleService | CANCELLED 상태, ABANDON_AFTER 10분, cancelUnstarted·isAbandoned·cancelIfAwaitingStartReport(now), battle_participant.active_member_id 활성 대결 1개 불변식(V15·V17), findActive·findAbandoned·saveImmediately·findMemberWinsJudgedBetween·activeMemberIds(Collection), BattleWin, cancelAbandoned, SQLSTATE 23505만 ALREADY_IN_BATTLE, Matchmaker tick 일괄 조회(U4-3 묶음) | 미반영 | 가·라 |
| CR34-02 | 05-01 batch 행(batch.reward 신설, 54번째); 06-06 6.1.13 batch; 05-02 5.5 시각 작업 | batch.reward RewardReconcileJob(0시 10분), batch.crew CrewRunLinkReconcileJob, batch.battle AbandonedBattleJob, batch.member RevocationPurgeJob, cron 설정 | 미반영 | 나·마 |
| CR34-03 | 06-05 reward; 06-02 running; 06-04 crew | RewardReconcileService, AccrualSource(memberId, reason, sourceId, at)·accrueWithRetry(source, amountOf)·restore, 적립량 0 근거 건너뜀, RunQueryService.savedBetween·SavedRun.of(RunRecordSaved), RunRecord.savedAt(V13), CrewRunLinkReconcileService·CrewRunService.link(post, SavedRun), M4 결정(U4-1 묶음) | 미반영 | 다·라 |
| CR34-04 | 12 12.1·12.2·12.3 | operator → audit(운영자 로그인 감사), batch.reward → reward, reward → member·running·battle 메서드 호출(D13~D15를 사건 수신 + 호출로), crew → running(SavedRun) | 미반영 | 나 |
| CR34-05 | 06-01 TokenIssuer; 05-02 5.5 | RefreshTokenRevocations(PostgreSQL, V14), fail-closed(503 TEMPORARILY_UNAVAILABLE), purgeExpiredRevocations. "Redis 장애는 매칭·실시간만" 원칙과 같은 줄 | 미반영 | 다·나 |
| CR34-06 | 06-05 6.1.12 OperatorAuthorizer, 6.1.11 AuditAction; D-76 | authenticate(provider, providerToken, client), OPERATOR_LOGIN_SUCCESS·FAILURE(V16). 401 규칙은 이미 D-76과 같다 | 미반영 | 다 |
| CR34-07 | 13-14 13.1 코딩 규약 | @Value 대신 기능별 @ConfigurationProperties record 22개, 비밀 포함 record의 toString 가림 | 미반영 | 나 |
| CR34-08 | 06-02 ValidityRule·RunRecord; 06-07 A-03 | 다섯째 조건 isBackedBy와 정지 시간 반영(D-94) | 미반영 → 이번 결정으로 반영 | 다·마 |
| CR34-09 | 06-06 6.1.13 web.auth; 05-03 5.7 | AuthAttemptGuard(접속지 요청 수·실패·자격 실패 제한, 이번 게스트 생성 한도 포함), ActiveMembers(탈퇴 회원 접근 토큰 거부) | 미반영 | 마·나 |
| CR34-10 | 05-02 5.4 배포 | compose `environment:` 컨테이너별(env_file 없음), RUNKK_INTERNAL_TOKEN `:?` 필수, ai read_only·cap_drop ALL·no-new-privileges·mem_limit 512m·tmpfs 16m(api 64m), deploy.sh의 api·ai 헬스 대기와 롤백에 ai 이미지, env_value(줄 없으면 기본값), nginx /ws Upgrade·읽기 2h·resolver 재해석·/actuator/ 차단, redis --requirepass(REDIS_PASSWORD), DB 필수 값 `:?`·develop 기본 비밀번호 제거, postgres·redis 포트 고정(U4-6 묶음). L25·L26은 보류 표시 | 미반영("환경 변수 파일 한 곳" 문장 그대로) | 나 |
| CR34-11 | 05-01 common; D-48 | 결정하지 않는다. F.8 P-1(선택지·권고)을 05-01 비고에 링크하고 /sw-design review에서 정한다 | **미정(검증 단계로)** | 나(검증 뒤) |
| CR34-12 | 05-01 domain.run; 06-06 RunTracker 표; D-77 문구 | RunArchive(보관·늦은 심박 재시도 협력 객체). 이번 S-1로 RunTracker 4 협력 객체(D-95) | 미반영 | 마·나 |
| CR34-13 | 06-06 Guidance·WatchDisplay 행; 05-01 core.ui UiText; 팀 컨벤션 3장 | 도메인 문구 키화(D-111), Guidance(kind, message, args, hasVoice, hasVibration), U4-10 Boolean 접두어 | 미반영 → 이번 결정으로 반영 | 마·나 |
| CR34-14 | 06-06 data.store 행; 13.2 저장; D-77 | Room 판 2: late_heart_rate(LateHeartRateEntity·LateHeartRateDao), AutoMigration 1→2, keepLatestSent 30건. 이번 판 3(pauses, AutoMigration 2→3)을 함께 적는다 | 미반영 | 마 |
| CR34-15 | 06-06 조정자·RunTracker 행 | BattleRunCoordinator·CrewRunCoordinator.onProgress internal(운영 경로는 CoordinatedRound의 측정 구독), RunTracker.onLateHeartRates private | 미반영 | 마 |
| CR34-16 | 06-06 data.watch·wear.contract 행 | WearPaths.WATCH_CAPABILITY("runkk_watch"), 워치 res/values/wear.xml 능력 선언, 폰은 능력 변화 리스너로 연결을 안다(5초 조회 폐지) | 미반영 | 마 |
| CR34-17 | 13-14 13.2 빌드 변형·보안, 14 | release R8(isMinifyEnabled·isShrinkResources)·proguard-rules.pro 보존 규칙, testTagsAsResourceId debug만, E2E 토큰 창구 localDebug 한정 | 미반영 | 나 |
| CR34-18 | 13-14 14 Konsist | ArchitectureTest 예외: localDebug E2eKakaoTokenSource가 ui.me 창구 자리 2개 import | 미반영 | 나 |
| CR34-19 | 13-14 13.2; 팀 컨벤션 1장 | 한 파일 허용 규칙(D-112), S-3 닫음 | 미반영 → 이번 결정으로 반영 | 나 |
| CR34-20 | 06-06 PendingRunRepository 행; 05-02 D-80 | `removeLateHeartRates(sent: LateHeartRates)`: 보낸 심박만 빼고 남으면 유지, 없으면 항목 삭제(U4-8 묶음). D-80 연산 시그니처 개정 | 미반영(옛 시그니처 removeLateHeartRates(recordId)) | 마·나 |
| CR34-21 | 06-06 ui.battle MatchViewModel 행; 05-01 api.core | MatchViewModel WaitEnd(Found·Failed·Lost) sealed 분기, 연결 실패 값 전달, FaceImageSource.loadLayers의 Outcome·select 구조 | 미반영 | 마·나 |
| CR34-22 | 06-06 6.1.14 app·6.1.15 워치; 팀 컨벤션 3장 | Timber 범위: 폰 RunkkApp·워치 RunkkWatchApp debug만 DebugTree, 워치 applicationScope 예외 처리기, 공유·워치 통신 실패 기록 | 미반영 | 마 |
| CR34-23 | 06-06 ui.share 행 | ShareOutcome.Failed·Fallback(messageRes), RunCardRenderer(ApplicationContext), CardSharer(ioDispatcher·uriFor), shareFailureOrNull | 미반영 | 마 |
| CR34-24 | 06-04 CrewRepository 행, CrewQueryService.search | search(CrewSearch, PageCursor, Week), 태그 AND·페이지를 SQL로 거르고 size+1 읽기. 추천 경로는 이번에 삭제(D-91)되므로 PageCursor.all() 문장은 지운다 | 미반영 | 라 |
| CR34-25 | 06-06 ApiExceptionHandler; 06-01 ReasonCode; 05-03 | ReasonCode.TEMPORARILY_UNAVAILABLE(503, "잠시 후 다시 시도해 주세요"): 갱신·로그아웃의 폐기 목록 저장소 장애. PROVIDER_UNAVAILABLE은 소셜 제공자 무응답 전용 | 미반영 | 마·다·나 |
| CR34-26 | 06-06 6.1.16 FaceRouter·CommentRouter; 05-02 5.4 | FaceRouter 422 UNSUPPORTED_IMAGE·422 TOO_LARGE를 입력 관문이 머리만 읽어 낸다, Settings.max_image_pixels(RUNKK_MAX_IMAGE_PIXELS, 기본 4096x4096), CommentRouter 본문 4KiB 상한 413 TOO_LARGE·정의 밖 422 INVALID_REQUEST | 미반영 | 마·나 |
| CR34-27 | 06-01 LegalDocumentCatalog | 법정 문서 주소 4개가 비면 기동 실패(필수 설정) | 미반영 | 다 |
| CR34-28 | 06-06 core.ui·ui.crew·data.api | RunkkStatTile(label, StatTileValue, caption, StatTileStyle…)·RunkkBigNumber(unit·color 제거), CrewMembers.Member.role: CrewRole(모르는 이름 MEMBER — 이번 MANAGER 추가와 함께), 알림함 제목 문자열 리소스(NotificationRepositoryImpl이 ApplicationContext로 해석), GuidanceMessage 문구 표 조회 | 미반영 | 마 |

집계: 41건 중 **반영됨 1**(CR2-U8), **이번에 새로 반영 39**(그중 일부 반영돼 있던 것 2: CR2-U4·CR2-A3, 이번 팀 결정으로 반영 가능해진 것 3: CR34-08·CR34-13·CR34-19), **미정 1**(CR34-11, 검증 단계로).

---

### F.10 설계 파일별 수정 지시표

담당 묶음: (가) 01-xx·02·03·04 분석 모델 + 07-11 UML, (나) 05-01·05-02·05-03·12·13-14, (다) 06-01·06-02·06-05, (라) 06-03·06-04, (마) 06-06·06-07. A 추적표·B-E·design-oo.md 목차는 총괄. 모든 파일은 고친 줄 끝에 `(v0.11, <근거 ID>)`를 붙인다(근거 ID: FR 번호, CR-nn, CR2-*, CR34-nn, D-nn).

| 파일 | 담당 | 고칠 것 |
|------|------|---------|
| 01-01 명사·동사 추출 | 가 | FR-194~218·FR-138·141 설명문의 명사·동사 추가(게스트, 계정 연동, 일시정지·정지 구간, 근접 알림, 랜드마크 배경, 화면 테마, 운영진, 공지·고정, 투표·선택지·응답, 채팅 메시지·리액션, 게시글·사진·앨범, 대면 모임·장소·정원·참석 응답, 신고·사유·자동 숨김·처리, 공동 목표·축하 카드). FR-118·119 명사(러너 성향 태그 선택, 크루 추천) 삭제 표시. 제외 사유 열(예: "장소명"은 MeetupPost 속성) |
| 01-02 클래스 목록 | 가 | 1.2.x에 F.5.2의 새 클래스(분석 수준: 이름·책임·주요 속성)와 새 절 "크루 채팅(crew.chat)"·"신고(report)". CrewRecommender·TagOverlapRecommender 삭제. Member 속성(계정유형·마지막접속일시) |
| 01-03 관계·클래스도 | 가 | 관계: CrewPost ← SchedulePost ← CrewRunPost·MeetupPost, NoticePost·PollPost·BoardPost; Crew 1 — 0..2 운영진; CrewChatMessage \*—1 Crew, ChatReaction \*—1 CrewChatMessage; ReportCase 1 — 1..\* ContentReport, ReportCase → 대상(ReportedContentPort로 다형 연관); Avatar 1 — 0..\* UnlockedBackground; RunRecord 1 — 0..\* PauseInterval; WeeklyRace 목표. 1.3.7 거부한 상속에 "MeetupPost를 CrewRunPost 하위로 두지 않음"(D-99). 1.4 클래스도 갱신. 1.5 자료사전 대응에 F.1.6 표 |
| 02 동적 모델 | 가 | 시나리오·사건 추적도 추가: 게스트 시작과 연동, 자유런 일시정지·재개·자동 종료, 대결 근접 알림(폰 → 워치), 채팅 보내기·받기와 재연결 메우기, 신고 → 자동 숨김 → 운영자 처리, 공동 목표 달성, 배경 해금. 상태도: RunSession(COUNTDOWN → RUNNING ⇄ PAUSED, RUNNING ⇄ LOCKED, → FINISHED, PAUSED에서 60분 → FINISHED), ReportCase(RECEIVED → AUTO_HIDDEN → HIDDEN·DISMISSED, RECEIVED → HIDDEN·DISMISSED, DISMISSED → RECEIVED), WeeklyRace 목표(목표 없음 → 목표 있음 → 달성), MeetupPost 참석(정원), PollPost(열림 → 마감), Member 계정 유형(GUEST → LINKED, GUEST → WITHDRAWN 1년 미접속), CR34-01 Battle CANCELLED |
| 03 기능 모델 | 가 | 3.2 프로세스 ↔ 연산 대응: 1.2 게스트, 1.1 연동, 2.2·2.6 정지·자동 종료, 4.4·2.4 근접 알림, 5.1.5 운영진, 5.4.1~5.4.5(응원·일정·공지·투표·게시판), 5.6.1·5.6.2 공동 목표, 5.8 채팅, 6.8 배경 해금, 7.7 신고 처리, 7.3 알림. 5.2.2 크루 추천 행 삭제. 3.3 제약 검사 위치: 운영진 2명(Crew), 고정 3개(CrewPostService), 투표 마감·선택지(PollPost), 정원(MeetupPost), 신고 중복·자동 숨김(ReportService·ReportCase), 채팅 500자(CrewChatMessage), 사진 5장·1MB(BoardPost·PhotoUpload), 정지 구간(RunUpload) |
| 04 분석 모델 검증 | 가 | 4.1 교차 확인 수치 갱신, 4.2 분석 중 추가한 클래스(SchedulePost, ReportCase, GuestCredential, RunWatchBridge 등), 4.3 SRS 보완 요청에 F.8.3 E-v011-1·2·3·5 |
| 05-01 서브시스템 분할 | 나 | 5.1.1 머리말 패키지 수 53 → 61, F.6.1 새 7개 행과 기존 행 더함·뺌, batch.reward 행(CR34-02), CR2-U2·U4·U7 클래스, common 행 비고에 P-1 링크(CR34-11). 5.1.2 표·의존 표: F.6.4, CR2-A1·A3·A7, CR34-12·13·21 |
| 05-02 계층·동시성·결정 | 나 | 5.3 동시성 행 추가: 운영진 상한·공지 고정(crew 행 잠금), 대면 모임 정원(게시글 낙관적 잠금 + 재시도), 투표 응답·리액션(복합 기본 키 upsert), 채팅 멱등(고유 색인), 신고 자동 숨김(report_case 행 잠금), 공동 목표 달성(조건부 UPDATE), 배경 해금(avatar 버전 + 기본 키), 게스트 닉네임(고유 제약 재시도), 리워드 집계 칼럼(지갑 행 잠금). 5.4: 사진 저장 용량·multipart 한도(D-100), CR34-10 전체, CR34-26, L25·L26 보류 표시. 5.5: 시각 작업에 PollReminderJob(1분), TierReconcileJob·BackgroundReconcileJob·RewardReconcileJob(0시 10분), IdleGuestWithdrawalJob(3시), CR34-02 작업들; 장애 행에 "실시간 토큰 만료 4401"; CR34-05. 5.6: D-89~D-114, D-73 보충(S-1), D-77·D-80 개정(CR34-12·20), D-88 목록 보충 |
| 05-03 API·실시간 메시지 | 나 | 5.7.1 표를 F.2.1로(새 24·변경 19·삭제 2, CR2-U1 행), 머리말에 공개 경로 2개와 시각 형식(D-114). 5.7.2에 F.3 전체(메시지 3종 + 입력 1종, 닫기 코드 표, 채팅 수신자 규칙), CR2-A2(Entry.me), CR34-25 |
| 06-01 common·member | 다 | common: ReasonCode 16개(F.2.3), CR34-25 TEMPORARILY_UNAVAILABLE, TagOwner.RUNNER 삭제, PageCursor·AttemptLimiter·ClientAddress 행(CR2-U7, 위치는 P-1). member: Member(F.5.2), AccountType, ScreenTheme, GuestCredential, AccessGrant, MemberSettings.theme, BattleSettings.proximityAlert, SignUpForm 축소·GuestStartForm·RequiredConsents, Nickname.numbered, AuthService.resumeGuest·linkSocialAccount, SignUpService.startAsGuest, WithdrawalService.withdrawIdleGuests, MemberQueryService(− tagsOf, + appliedValidRunCount, profileOf CR2-U1), TokenIssuer.verifyGrant, CR34-05, CR34-27, MemberLoader(CR2-U7) |
| 06-02 running·analysis·profile | 다 | running: PauseInterval, RunUpload.pauses, RunRecord(pauses·pausedTotal·judgeValidity·savedAt), RunTrack(정지 인식 연산), ValidityRule(CR34-08), ValidRunScore, TierReconcileService, RunQueryService(validRunsAfter, savedBetween·SavedRun CR34-03), RunDetail.pauses, TierUpdateListener 문구(보정이 채움). analysis: RecordQueryService 상세 pauses, RankingService.recompute(CR2-U1), AnalysisTransactions·RankingPage·RankingRecomputeResult·RecordRow(CR2-U7). profile: ProfileView − tags + avatarBackgroundKey |
| 06-03 battle | 라 | CR34-01 전체(U4-3 묶음), CR2-U1 MatchingService.status, CR2-U7 battle 보조 클래스(AfterCommit·BattleDelivery·BattleRandomConfig·BattleTransactions·BattleWin·OpponentNames). 근접 알림은 서버 battle을 바꾸지 않는다고 한 줄 |
| 06-04 crew | 라 | Crew(운영진), CrewMembership(− crewTags), CrewRole·CrewRelation, CrewPermissionPolicy 표(F.5.1), CrewPost 계층(D-99, F.5.2 전체), MeetupAttendance·PollOption·PollResponse·PostPhoto·PostPhotoStore·PhotoUpload·Draft·View, WeeklyRace 목표(D-105), CrewNewsNotifier(D-106), CrewPostReportAdapter, 서비스 연산(CrewService.appointManager·dismissManager, CrewPostService 새 연산, WeeklyRaceService.setGoal·status, MembershipService 권한), 추천 계열 삭제, CR34-24, CR34-03의 crew 부분, CR2-U7 crew 보조 클래스. 새 절 6.1.7a crew.chat(F.5.2의 CrewChatMessage·ChatReaction·CrewChatService·포트·live 메시지) |
| 06-05 runimal·avatar·reward·policy·notification·audit·operator | 다 | runimal: tagsOf 호출 삭제, RunimalJudge 시그니처, RunSampleReader·RunimalTransactions(CR2-U7). avatar: Avatar 배경, UnlockedBackground, BackgroundService, BackgroundUnlockListener, FaceImageService(CR2-U1), Selfie* 보조(CR2-U7). reward: RewardWallet 집계 칼럼(D-109), RewardAccrualRepository, CR34-03. policy: Landmark.key·LANDMARKS 검사(D-107), OperatorWhitelistSeeder → operator.OperatorBootstrap(CR2-U2). notification: NotificationType·Screen(F.5.1), notifyCollapsing(D-102). audit: AuditAction·ActorRole(F.5.1), CR34-06. operator: OperatorBootstrap, CR34-06. 새 절 6.1.x report(F.5.2 ReportCase·ContentReport·ReportService·ReportedContentPort) |
| 06-06 표현·인프라·앱·워치·ai | 마 | 6.1.13: web.auth(게스트·연동 DTO, CR34-09), web.me, web.crew(새 DTO·OffsetDateTime), 새 web.chat·web.report·web.admin.report, realtime(InboundMessage·InboundChatPresence·ChatPresenceRegistry·PollingChatPresence, 세션 만료 예약, CrewChatMessenger 구현), batch 새 작업(F.6.1, CR34-02), infra.chat·infra.report·JdbcPostPhotoStore·JpaRewardAccrualRepository, bootstrap(CR2-U4), ApiExceptionHandler(CR34-25). 6.1.14 앱: RunSession(PAUSED·pause·resume), RunTracker(D-95)와 RunArchive·RunWatchBridge·RunCoach·MeasurementRuntime, PauseLimit, ProximityAlertRule, WatchGateway 확장, domain.live ClientMessage·임대(D-103), domain.chat·domain.report, domain.account 게스트·연동, settings theme·isProximityAlertOn·isFollowingCoachPlan, data.live 4401·SessionRefresher, data.session 게스트 자격 증명, data.store Room 3, data.push OpenChatRoomTracker, api.crew PhotoCompressor, ui.chat·ui.crew·ui.me·ui.run·ui.avatar·ui.share 변경, core.ui 테마·AvatarBackdrop, CR2-A2·A3·A4·A6, CR34-12~16·20~23·28. 6.1.15 워치: F.4 전체, watch.alert ProximityAlertPlayer, 일시정지 버튼, CR34-16·22. 6.1.16 ai: CR34-26만(이번 기능 변경 없음) |
| 06-07 알고리즘·상속·다형성 | 마 | A-02(RunSession 정지·재개, 재개 첫 포인트 거리 제외, 운동 시간), A-03(정지 경계 구간 제외 + isBackedBy, D-94), A-12(달성 판정 조건부 UPDATE), A-17(RewardWallet 집계 칼럼, D-109). 새 알고리즘: A-23 ProximityAlertRule.evaluate(간격 공식, F.4.3), A-24 ReportCase.addReport와 submit 잠금 순서, A-25 채팅 커서 조회(before·after), A-26 BackgroundService.unlockReached, A-27 TierReconcileService.reconcile, A-28 CrewPostService.remindClosingPolls. 6.3 상속: SchedulePost 도입(깊이 3). 6.4 다형성: SchedulePost.answer, CrewPost.maxBody, ReportedContentPort 구현 둘. 6.5 접근 경로: 채팅 (crew_id, id DESC), 앨범 (crew_id, id DESC), 신고 (status, opened_at), 고정 공지 부분 색인, 게스트 미접속 부분 색인 |
| 07-11 UML | 가 | 7 유스케이스: 러너(게스트로 시작한다, 소셜 계정을 연동한다, 러닝을 일시정지한다, 근접 알림을 받는다, 화면 테마를 정한다, 배경을 고른다), 크루(공지를 쓴다, 투표를 만든다·응답한다, 채팅한다, 게시글을 쓴다, 앨범을 본다, 일정에 참석 응답한다, 콘텐츠를 신고한다, 공동 목표를 정한다, 운영진을 지정한다), 운영자(신고를 처리한다). "크루를 추천받는다" 삭제. 7.7 기술서 해당 행. 8 클래스도(crew 계층, crew.chat, report), 9 시퀀스(02와 같은 이름), 10 상태도(02와 같은 것), 11 활동도(신고 처리, 일시정지 자동 종료) |
| 12 의존도 | 나 | 12.1 그림에 crew.chat·report·web.chat·web.report·web.admin.report·infra.chat·infra.report. 12.2에 F.6.2(N1~N17, 변경 행), CR34-04 화살표, 화살표 수 다시 세기. 12.3 DSM에 crew.chat·report 행·열(핵심 15개). 12.4 순환 0·계층 역행 0 확인(F.6.3) |
| 13-14 구현 지침·테스트 순서 | 나 | 13.1: CR2-U5, CR34-07, multipart 규칙(D-100), 채팅 멱등·커밋 뒤 사건(D-101), ArchitectureTest PACKAGES 61·ALLOWED 갱신, 마이그레이션 번호 예약 규칙(F.5.3). 13.2: Room 3, CR2-A5, CR34-17·18·19, wear-contract @SerialName 규칙(D-97), 문구 키화(D-111). 14: F.11의 시험 순서(스레드 기반 통합: 게스트·연동, 일시정지 업로드·판정, 채팅(NFR-040 측정), 신고 자동 숨김 동시성, 공동 목표 동시 달성, 배경 해금, 토큰 만료 4401, 티어·리워드 보정), 계약 시험 갱신 지점 |
| A 추적표 | 총괄 | A.1에 신규 FR 25·되살림 2 행(설계 요소 = F.1.1·F.1.2 서버 열), 수정 FR 36건의 설계 요소 열(F.1.3), FR-118·119 삭제 표시, 유효 FR 184 → 209. A.2에 NFR-040·041(F.1.5) |
| B-E 변경 이력·색인·가정 | 총괄 | B에 v0.11 행(입력 srs v0.10, 이 명세, 결정 D-89~D-114, 41건 처리 결과). C 자료사전 대응에 F.1.6. D 클래스 색인에 F.5.2·F.6.1의 새 클래스와 삭제 클래스. E: E-03 닫음, F.8.3 새 항목, P-1(검증 단계) |
| design-oo.md | 총괄 | 머리 둘째 줄 v0.11·입력(srs v0.10, code-review.md 2~4차 9절, 이 명세). 0.1 ai 행의 "크루 추천" 문구 정리(D-91). 0.2 범위(유효 FR 209, 삭제 FR 7 + 2 − 되살림 2, NFR 41, 제외 목록에서 대면 모임·신고 빼기, 저장소 D18·D19 추가). 0.3 배치 작업 수(14종 → 새 작업 반영). 목차 표에 F 행 |

---

### F.11 구현 실행 단위 제안

#### F.11.1 단계

| 단위 | 범위 | 마이그레이션 | 앞선 조건 | 병렬 |
|------|------|-------------|-----------|------|
| **S0 계약 고정**(서버, 0.5일) | ReasonCode 16개, enum 추가·개명(F.5.1: AccountType, ScreenTheme, SettingItem.THEME, CrewRole.MANAGER, CrewRelation.MANAGER, PostKind 4개, VoteAnswer → AttendanceAnswer, PollMethod, ReactionKind, ReportTargetKind·Reason·Status·Decision, NotificationType 9·Screen 5(CREW_BOARD 뺌, D-150 (v0.11, 7.13.5 TF-VC-24)), AuditAction 5, ActorRole), 새 web DTO record 껍데기(F.2.2 항목 그대로, 컨트롤러는 501), AppContractSnapshotTest 기준 갱신 → android/contract 동기화 | V19 | 이 명세 | 단독(가장 먼저) |
| **S1 회원·게스트·설정** | D-89·90·91·92: 게스트 생성·세션 재개·연동, 가입 축소, 태그 제거(member·crew_membership·runimal·추천 삭제), 테마·근접 알림 설정, 마지막 접속, IdleGuestWithdrawalJob | V20 | S0 | S2·S4와 병렬 |
| **S2 러닝·판정·티어 보정** | D-93·94·108: pauses 수신·저장·검사, RunTrack 경계 제외, 다섯째 조건, 기록 상세 pauses, TierReconcileService·Job | V21 | S0 | S1·S4와 병렬 |
| **S3a 크루 구조·게시판** | D-98·99·100·105·106: 운영진, 권한표, 게시 계층·참석 응답·투표·공지·고정·대면 모임, 사진 multipart·저장·앨범, 공동 목표·축하 카드, 소식 알림·PollReminderJob, web.crew OffsetDateTime(D-114) | V22 | S0, S1(태그 제거와 crew_membership 겹침) | S2·S4와 병렬 |
| **S3b 크루 채팅** | D-101·102: crew.chat·infra.chat·web.chat, realtime 입력 ChatPresence·ChatPresenceRegistry·CrewChatMessenger 구현, notifyCollapsing | V23 | S3a(CrewRelation.MANAGER, relationOf) | S3c와 병렬 |
| **S3c 신고** | D-104: report·infra.report·web.report·web.admin.report, CrewPostReportAdapter(crew), ChatReportAdapter(crew.chat, S3b 뒤 붙임) | V24 | S3a(hidden 칼럼), ChatReportAdapter는 S3b | S3b와 병렬 |
| **S4 배경·리워드·실시간 토큰** | D-107·109·110: Landmark.key·배경 해금·선택·BackgroundReconcileJob, RewardWallet 집계 칼럼, AccessGrant·4401 | V25, V26 | S0 | S1·S2·S3a와 병렬. 4401은 realtime을 S3b와 함께 고치므로 S3b와 같은 사람이 맡거나 S3b 뒤에 합친다 |
| **A1 계약·도메인**(앱) | contract 동기화, domain.\*: account(게스트·연동·테마), run(정지·S-1 분리·근접 알림 규칙·WatchGateway), live(ClientMessage·새 ServerMessage·임대 계약), crew(새 모델·포트·AttendanceAnswer), chat·report(신규), avatar(배경), settings. 순수 Kotlin 단위 시험 | Room 없음 | S0 | 기능별로 2~3명 병렬(run·live / crew·chat·report / account·settings·avatar) |
| **A2 데이터** | api.\*(새 엔드포인트 DTO·매퍼·시각 두 형식), api.core SessionRefresher·게스트 재개, data.live(임대·4401·새 해독), data.session, data.settings, data.store Room 3, data.watch 새 경로, data.push 억제 | Room 3 | A1(포트), 서버 계약(S0 DTO로 MockWebServer 시험 가능) | 기능별 병렬 |
| **A3 화면** | feature:me·run·crew·chat(신규)·avatar·share·notification, core:ui 테마·대비 시험 | — | A2 | 기능 모듈별 병렬 |
| **W 워치** | wear-contract(A1과 함께 먼저), watch.link 새 경로, watch.alert ProximityAlertPlayer, watch.ui 일시정지 버튼 | — | wear-contract | A2·A3와 병렬 |
| **ai** | 없음(CR34-26은 설계 문서 반영만) | — | — | — |

#### F.11.2 병렬 운영 규칙

- 계약(이 명세 F.2~F.5의 이름·형식)을 바꾸려면 이 명세를 먼저 고치고 S0 산출물(enum·DTO·contract 기준 파일)을 다시 만든다. 단위 작업 안에서 계약 이름을 바꾸지 않는다.
- 마이그레이션 번호는 F.5.3 예약 번호를 쓴다. 통합 브랜치에 합치는 순서가 번호와 다르면 합치는 쪽이 아직 적용되지 않은 자기 번호를 뒤로 민다.
- realtime(S3b·S4)과 crew(S1 태그 제거·S3a)는 같은 파일을 고치므로 합칠 때 충돌을 예상하고 S3a → S3b → S4(4401) 순서로 합친다.
- 서버 S3a 배포 전에 앱 A2의 api.crew가 시각 두 형식을 읽어야 한다(D-114). /vote → /attendance, 추천 삭제, PostKind 확장은 앱 A2·A3와 서버 S3a를 같은 배포 묶음으로 낸다(깨짐 항목, F.2.4).

#### F.11.3 계약 시험 갱신 지점

| 시험 | 위치 | 갱신 내용 | 단계 |
|------|------|----------|------|
| AppContractSnapshotTest | backend/src/test/java/com/a207/runkk/AppContractSnapshotTest.java, 기준 src/test/resources/contract/app/ | `ENUMS` 맵: member + AccountType·ScreenTheme, crew VoteAnswer → AttendanceAnswer + PollMethod, 새 키 "crew.chat"(ReactionKind)·"report"(ReportTargetKind·ReportReason·ReportStatus). `RUNKK_CONTRACT_UPDATE=true`로 기준 갱신, 그 diff가 앱 작업 목록 | S0, 이후 단계마다 |
| sync_from_server.py | android/contract/ | 같은 ENUMS 목록, enums/AttendanceAnswer.txt 생성·VoteAnswer.txt 삭제, dto-records.json 재생성 | S0 직후 A1 |
| RealtimeContractTest | backend realtime/RealtimeContractTest.java, android data/src/test/.../data/live/RealtimeContractTest.kt | CrewChatPosted·CrewChatRemoved·CrewChatReactionChanged 직렬화 ↔ 해독, 입력 ChatPresence, `type` 없는 진행값 호환, 닫기 코드 4401 | S3b·S4, A2 |
| wear-contract 시험 | android/wear-contract/src/test | 메시지 왕복(PauseRequestMessage·ProximityMessage·LeadChangeMessage·WatchDisplayMessage 새 키), 옛 JSON 해독(기본값), 모르는 키, 경로 7개 중복 없음, @SerialName 키 불변 | W(가장 먼저) |
| ArchitectureTest(백엔드) | backend ArchitectureTest.java, ArchitectureRuleNegativeTest | PACKAGES 61개, ALLOWED에 F.6.2 화살표, *.live 규칙에 crew.chat.live | S3b·S3c |
| Konsist ArchitectureTest(앱) | android | :feature:chat, domain.chat·domain.report가 data·ui를 import하지 않음, D-112 한 파일 규칙 | A1·A3 |
| BatchJobsTest | backend BatchJobsTest.java | IdleGuestWithdrawalJob·TierReconcileJob·PollReminderJob·BackgroundReconcileJob의 cron·호출 하나 | S1·S2·S3a·S4 |
| 마이그레이션 통합 시험 | backend(Testcontainers) | V20(태그 칼럼 삭제·last_access_at), V25(LANDMARKS 키 부여), V26(집계 채움) | S1·S4 |
| NFR 시험 | backend 통합, 앱 core:ui | ChatLatencyIntegrationTest(NFR-040), ColorContrastTest·Roborazzi 테마 2벌(NFR-041) | S3b, A3 |
