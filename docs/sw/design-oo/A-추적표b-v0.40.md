> [런크크 객체지향 분석·설계서](../design-oo.md) 목차 · 이전: [A-추적표](A-추적표.md) · 다음: [B-E-변경-이력-색인-가정](B-E-변경-이력-색인-가정.md)

## A. 추적표 (이어서: v0.40 병합, 2026-11-14)

A-추적표가 100KB에 가까워 v0.40 병합(서버 시간대, 피그마 맞춤, 아바타 추가 요구, Grafana 이벤트 현황)의 새 요구 행을 이 조각에 모았다. 바뀐 기존 FR의 설계 요소는 05-29 1.1 표와 05-30 1.1 표가 원본이다. 랭킹 줄 모습(FR-070, FR-290)은 web.record `RankingController` → avatar `RunnerLookQuery.snapshotLooksOf`, infra.avatar `RedisRunnerLookSnapshotStore`, analysis 사건 `RankingSnapshotRebuilt` → web.record `RankingLookPrefill`이다(05-30 D-858). 런바디 `RunBodySummaryTab`은 주간/월간 토글을 지웠다(FR-302, 05-29 D-649).

### A.1 기능 요구 (FR, 이어서)

| 요구 ID | 클래스.연산 | 패키지 | 시퀀스 | 비고 |
|---------|-------------|--------|--------|------|
| FR-322 | 크루명 검색 화면: 앱 ui.crew CrewSearch, CrewRepository.search; 서버 CrewController.list(keyword) | ui.crew, web.crew | - | v0.40 신규(CR-184): 05-29 5.1, D-651, D-652 |
| FR-323 | 재가입 제한 남은 시간: CrewDetailResponse.rejoinAvailableAt, MyCrewResponse.rejoinAvailableAt | web.crew, crew | - | v0.40 신규(CR-184): 05-29 5.1 |
| FR-324 | 초대 코드 조회와 확인: CrewController.invitePreview, InvitePreviewRequest·Response, 시도 제한; 앱 InviteStep, InviteSheetState | web.crew, crew, ui.crew | - | v0.40 신규(CR-184): D-676, D-677 |
| FR-325 | 크루 로고: CrewLogoController, CrewLogoService(MAX_UNLINKED 3), CrewLogo(BYTEA); 앱 CrewLogoEncoder.turnOf | crew, web.crew, ui.crew | - | v0.40 신규(CR-184): D-678, D-711 |
| FR-326 | 반응한 사람: ChatReaction 저장소, ReactorListResponse; 앱 Reactor, ChatRepository.reactors, ChatUiState | crew.chat, domain.chat, ui.chat | - | v0.40 신규(CR-185): 05-29 5.2 |
| FR-327 | 러닝 완료 시스템 메시지: RaceContributionAdded → ChatRunFinishedListener → CrewChatService.postRunFinished, ChatMessageKind | crew, crew.chat | - | v0.40 신규(CR-185): D-675, D-679~D-681 |
| FR-328 | 크루런 반복: CrewRunSeries, CrewRunRepeat, CrewRunRepeater, CrewRunRepeatJob(일요일 9시) | crew, batch.crew | - | v0.40 신규(CR-186): D-682, D-710 |
| FR-329 | 일정 상세: CrewScheduleController.detail, CrewRunDetailResponse; 앱 CrewRunDetail | web.crew, ui.crew | - | v0.40 신규(CR-186): 05-29 5.3 |
| FR-330 | 일정 수정(작성자만): CrewScheduleController.edit, CrewRunEditRequest; 앱 CrewRunWriteMode | web.crew, crew, ui.crew | - | v0.40 신규(CR-186): 05-29 5.3 |
| FR-331 | 지난 크루런 달력: PastCrewRunsResponse; 앱 CrewPastRuns | web.crew, ui.crew | - | v0.40 신규(CR-186): D-673 |
| FR-332 | 지난 공동 목표: CrewGoalHistoryController, GoalHistoryResponse; 앱 CrewPastGoals | web.crew, ui.crew | - | v0.40 신규(CR-187): 05-29 5.4 |
| FR-333 | 크루원 정보 팝업: CrewMembersAssembler(accumulatedMeters, joinedAt, level, ageBand, gender); 앱 CrewMemberDialog | web.crew, ui.crew | - | v0.40 신규(CR-187): D-686, D-687 |
| FR-334 | 내 러너 화면: RunnerService, RunnerResponse, AppearanceCalculator.bodyVariant; 앱 AvatarRunner, AvatarRunnerUiState, RunkkRiveAvatar | avatar, web.avatar, ui.avatar, core.avatar | - | v0.40 신규(CR-188): D-689~D-691, D-707~D-709 |
| FR-335 | 잠긴 상점 아이템: ShopUnlock, UnlockKind, ShopService 잠김 계산 | policy, reward | - | v0.40 신규(CR-188): D-693 |
| FR-336 | 상점 아이템 그림: 상점 응답 imageKey, 앱 그림 리소스 대응표(contract-W8) | reward, ui.avatar | - | v0.40 신규(CR-188): D-692, 05-30 D-745 |
| FR-337 | 배경 해금 팝업: 앱 BackgroundUnlock, BackgroundUnlockSeenStore(확인한 배경 목록) | ui.avatar | - | v0.40 신규(CR-188): D-692, D-695 |
| FR-338 | 홈 Rive 아바타: HomeAvatarSlotContent, SlotKey.HOME_AVATAR, RiveAvatarStage | core.avatar, ui.avatar, app | - | v0.40 신규(CR-189): 05-30 2절, D-732~D-734, D-766, D-773 |
| FR-339 | 러닝 중 아바타: RunAvatarSlotContent, rememberRunFacingLeft, RunkkRiveAvatar(mirrored), LocalSlotAvatarFacingLeft, PacedRiveAnimationView | core.avatar, ui.avatar | - | v0.40 신규(CR-190): 05-30 7절, D-753, D-754, D-762, D-765, D-774 |
| FR-340 | 로딩 표시와 미리 준비: MyAvatarStore, prepareRiveAvatarLook, AvatarLoadingPlaceholder, SharedToneCache | core.avatar, ui.avatar | - | v0.40 신규(CR-189): 05-30 3절, D-735~D-738, D-771 |
| FR-341 | 셀카 작업 진행: SelfieJobPoller, SelfieJobWorker, SelfieProgressHub, AvatarSetupViewModel | data, domain.avatar, ui.avatar | 05-30 6.3 | v0.40 신규(CR-193): D-749~D-751, D-791, D-793 |
| FR-342 | 셀카 결과 알림: SelfieResultNotifier, SelfieNoticeTracker, SelfieNoticeIntent | domain.avatar, data.push, core.model | 05-30 6.3 | v0.40 신규(CR-193): D-752, D-792, D-794 |
| FR-343 | 바닥 정렬: AvatarStageLayout, AvatarStageFrame, RivGroundShadow | core.avatar | - | v0.40 신규(CR-194): 05-30 4절, D-739~D-741, D-764 |
| FR-344 | 규격 의상: OutfitCatalog, OutfitLibrary, OutfitLayerComposer, TopBodyCut, 파이프라인 tools/outfit-pipeline | core.avatar | - | v0.40 신규(CR-196): 05-30 5절, D-742~D-748, D-782~D-790, D-798~D-801 |
| FR-345 | 이벤트 현황(서버 밖): Grafana 데이터소스 runkk-db-<환경>, 대시보드 runkk-event.json, readonly-role.sql | (infra) | - | v0.40 신규(CR-208): 05-29-설계-이벤트현황-성 D-860~D-867 |

### A.2 비기능 요구 (NFR, 이어서)

| 요구 ID | 설계 요소 | 패키지 | 비고 |
|---------|-----------|--------|------|
| NFR-058 | JVM 시간대 Asia/Seoul, hibernate.jdbc.time_zone 삭제, V48, V49 | (설정, 마이그레이션) | v0.40 신규: 05-28 D-633~D-638 |
| NFR-059 | core designsystem 테마 토큰과 공통 부품, 중립색 대응표 | core.designsystem | v0.40 신규: 05-29 D-639~D-641, D-643, D-696~D-701, D-717, 05-30 9절 D-758 |
| NFR-060 | MetaRow, 가운뎃점 정적 검사 | core.designsystem | v0.40 신규: 05-29 D-642, D-713 |
| NFR-061 | AvatarStage 표시 로그, 미리 준비 | core.avatar | v0.40 신규: 05-30 D-738, D-767 |
| NFR-062 | AvatarStageLayout | core.avatar | v0.40 신규: 05-30 4절, D-739 |
| NFR-063 | OutfitAssetSpecTest, 의상 자산 규격 | core.avatar | v0.40 신규: 05-30 5.5, D-746 |
| NFR-064 | 읽기 전용 계정 표·열 권한, 비밀번호 CI 변수 | (infra) | v0.40 신규: 05-29-설계-이벤트현황-성 D-860~D-867 |
| NFR-065 | 환경별 DB 전용 internal 망 | (infra) | v0.40 신규: 05-29-설계-이벤트현황-성 D-860~D-867 |

#### v0.43 병합 행 (2026-11-18, srs 7.13.31)

행마다의 설계 요소와 시험은 srs [07-11 v0.34 병합 행](../srs/07-11-추적표-v0.18.md)에 있다. 여기에는 요구 묶음 → 설계 조각만 둔다.

| 요구 | 클래스·부품 | 패키지 | 설계 |
|------|-------------|--------|------|
| FR-376, FR-389, FR-390 | CrewMembersView, ChatPage, 얼굴 캐시, KakaoLinkSharer, InviteLinkReader, CrewInviteJoin | web.crew, web.chat, feature.crew, feature.chat | v0.43 신규: 05-31 A절 D-896~D-915, D절 D-975~D-984 |
| FR-377~FR-384, NFR-066 | OpponentLooks, RunRecord.LookColumns, TrackLayout, TrackScene, RunkkTrackCard, MatchViewModel | running, battle, core.ui, feature.battle, feature.crew | v0.43 신규: 05-31 B절 D-916~D-949, D.1 D-985~D-1006 |
| FR-385~FR-388, NFR-067 | ResultStage, RecordCardFront, FirstRunGuide, GuideReplay | core.ui, feature.run, feature.record, feature.home | v0.43 신규: 05-31 C절 D-950~D-974, D.1 |
| FR-346~FR-353 | HeadTarget, SideNeckRegistration, 얼굴 원 ASSET_SCALE, 본인 성별 보정 | core.avatar, feature.chat, feature.crew | v0.43 신규: 05-31 D.2 D-868~D-876 |
| FR-354~FR-372, NFR-068~NFR-073 | CrewReadSession, CrewRunService, CrewRunCoordinator, CrewRunPost, CrewRunCompletion | crew, web.crew, feature.crew, domain.run | v0.43 신규: 05-32 D-877~D-892 |
| FR-373~FR-375, NFR-074 | 절전 판정과 설정 대체, 공용 안내 상태 | core.ui, feature.home, feature.battle, feature.crew | v0.43 신규: 05-31 E절 D-893~D-895 |
