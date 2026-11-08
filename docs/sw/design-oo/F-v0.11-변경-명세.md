> [런크크 객체지향 분석·설계서](../design-oo.md) 목차 · 이전: [B-E-변경-이력-색인-가정](B-E-변경-이력-색인-가정.md) · 다음: [Fb-v0.11-변경-명세-F-3-3-연결-종료-코드](Fb-v0.11-변경-명세-F-3-3-연결-종료-코드.md)

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
| 규칙 | `mode = FREE`일 때만 비어 있지 않을 수 있다(**v0.37 변경, srs FR-300·CR-164, [code-review 22장](../code-review/22-2차배포-통합-1차.md) BR-1·U-2: `mode = FREE` 또는 `CREW_RUN`일 때. 대결(BATTLE)은 그대로 거부. 서버 RunUpload.requireAllowed와 V43으로 반영 완료(4a10fae7). 업로드 성공은 200 STORED**). `from < to`, `startedAt ≤ from`, `to ≤ endedAt`, 시간순·겹침 없음, 최대 100개(제안). 위반은 400 `BAD_REQUEST`(RunUpload 생성자의 IllegalArgumentException). `elapsedMillis`는 정지 시간을 뺀 **운동 시간**이다(FR-036) |
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

