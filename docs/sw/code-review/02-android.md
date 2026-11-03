> [런크크 코드 리뷰 결과서](../code-review.md) 목차 · 리뷰 2026-10-20 (1차) · 이전: [01-backend](01-backend.md) · 다음: [03-ai](03-ai.md) · 2차: [리뷰 2026-10-21 (2차)](#리뷰-2026-10-21-2차) · 3차: [리뷰 2026-10-21 (3차)](#리뷰-2026-10-21-3차) · 4차: [리뷰 2026-10-21 (4차)](#리뷰-2026-10-21-4차)

## 2. 안드로이드 A1 (멀티 모듈·core.model·wear.contract·domain)

입력: S15P31A207-impl/android (브랜치 feature/runkk-impl, 커밋 9f2af7e, 기준선 origin/develop d38b99f), docs/sw/design-oo.md v0.7 (5.1.2, 5.2, 6.1.14, 6.1.15, 6.2 A-02·A-08, 12장, 13.2, 14장), docs/sw/design-review.md v0.7

### 리뷰 기준

| 항목 | 기준 | 출처 |
|------|------|------|
| 매개변수 수 | 4개 이하 (함수·메소드). Hilt/JSR-330 생성자 주입은 사람이 부르지 않으므로 클래스 Fan-Out 7로 본다 | 기본 규칙, design-review.md 0.1 |
| 중첩 깊이 | 3단계 이하 (if·for·when·반복 람다 기준) | 기본 규칙 |
| 함수 길이 | 50줄 이하 | 기본 규칙 |
| 순환 복잡도 | 10 이하 (조건·반복·when 갈래·&&·||·?: + 1로 약산) | 기본 규칙 |
| 결합도 상한 / 응집도 하한 | 스탬프 (제어는 검토, 외부·공통·내용 불허) / 통신적 (절차적·시간적 검토, 논리적·우연적 불허) | design-review.md 0.1 |
| Fan-Out 상한 | 7 | design-review.md 0.1 |

코딩 규약 문서: `GitHub/자율/자율프로젝트_코드컨벤션.md` v0.1 3장(앱)과 1장(공통)을 우선 적용하고, 규약에 없는 항목은 `references/coding-style.md` 기본 규칙을 적용. 포맷(들여쓰기·중괄호·줄 길이)은 규약 4장대로 ktlint가 정하므로 지적하지 않는다. 저장소 `AGENTS.md`의 커밋·브랜치 규칙을 형상 관리 기준으로 쓴다.
형상 관리 도구·브랜치 전략: Git(GitLab), `main`·`develop` 보호, `feature/{Jira 키}-{작업명}` → MR. 커밋 제목 `{타입}: {내용} [{Jira 키}]`.

경로 줄임말 (이 문서의 모든 "파일:줄"은 아래 접두어를 붙여 읽는다):

| 줄임 | 실제 경로 (`S15P31A207-impl/android/` 아래) |
|------|------|
| `D/` | `domain/src/main/kotlin/com/a207/runkk/app/domain/` |
| `M/` | `core/model/src/main/kotlin/com/a207/runkk/app/core/model/` |
| `W/` | `wear-contract/src/main/kotlin/com/a207/runkk/wear/contract/` |
| `A/` | `app/src/main/java/com/a207/runkk/app/` |
| `H/` | `feature/home/src/main/java/com/a207/runkk/app/ui/home/` |
| `U/` | `core/ui/src/main/java/com/a207/runkk/app/core/` |
| `B/` | `build-logic/convention/src/main/kotlin/` |

### 언어 평가 (최초 1회)

언어: Kotlin 2.4 (JVM 17 대상), Jetpack Compose, Hilt, kotlinx.coroutines

| 기준 | 판정 | 근거 |
|------|------|------|
| 친숙성 | 보통 | 팀 분포는 확인하지 못했다. 서버 쪽이 Java라 Kotlin 경험자 수는 확인 필요 |
| 사용 능력 | 적합 | 코루틴·Flow로 1초 측정·실시간 채널·워치 수집을 한 언어로 다룬다 |
| 처리 효율성 | 적합 | 1초 주기 측정·하버사인 계산 수준. JVM·ART로 충분 |
| 과거 개발 실적 | 적합 | 1주차 위치 수집 스파이크와 공식 샘플이 Kotlin(C-010) |
| 문제 유형 | 적합 | 모바일·실시간 제어형. Android 표준 언어 |
| 자료 구조 | 적합 | 표준 컬렉션·sealed 계층·value class로 충분 |
| 이식성 | 적합 | 폰(Android)·워치(Wear OS)가 같은 언어. 순수 JVM 모듈(:domain)은 JVM 단위 시험으로 돈다 |
| 개발자 경험 | 보통 | 코루틴 구조적 동시성의 함정(범위·디스패처)을 아는 사람이 있는지 확인 필요. 이번 리뷰의 중 결함 3건이 이 영역이다 |
| 컴파일러·도구 | 적합 | Gradle 9·AGP, ktlint, Android Lint, Konsist |

부적합 0건 → 재검토 불필요

### 대상

구현 범위는 A1(멀티 모듈 재구성, `:core:model`, `:wear-contract`, `:domain` 전부, `:app`의 RunkkApp·AppScopeModule·RunkkNavHost·MainActivity·Konsist 구조 시험, `:feature:home`의 HomeScreen·HomeFeatureEntry, `:core:ui` 테마·내비게이션, build-logic)다. 기준선 대비 android 변경은 121파일, +6,196 / −93줄이다. main 소스 71파일 3,618줄을 모두 읽었다. 시험 소스(domain 86개, core.model·wear.contract·Konsist 포함 약 138개 시험)는 구조·누락 확인용으로만 봤다.

| 파일 (줄임 경로) | 줄 수 | 대응 설계 모듈 | 마지막 커밋 |
|------|-------|----------------|-------------|
| M/ AppError, CardKind, Distance, GeoPoint, Outcome, Pace, RunMode, SignalState (8개) | 178 | core.model | c277658 |
| W/ WearPaths, HeartRateBatchMessage, WatchDisplayMessage, StartRequestMessage, WearJson (5개) | 94 | wear.contract | c277658 |
| D/run/RunTracker.kt | 282 | domain.run RunTracker | 418bd2b |
| D/run/RunSession.kt | 195 | domain.run RunSession | 0898678 |
| D/run/GuidanceRule.kt | 158 | domain.run GuidanceRule 외 구현 6개 | 418bd2b |
| D/run/ 나머지 (CoachingJudge, CompletedRun, Guidance, LocationFilter, MeasurementValues, Ports, RunProgress, RunStartSpec, WatchDisplay) | 326 | domain.run | 0898678·418bd2b |
| D/battle/BattleRunCoordinator.kt | 298 | domain.battle BattleRunCoordinator | 418bd2b |
| D/battle/ OpponentTrack, GapTracker, BattleValues, BattleRepository | 264 | domain.battle | 0898678 |
| D/crew/CrewRunCoordinator.kt | 206 | domain.crew CrewRunCoordinator | 418bd2b |
| D/crew/ CrewRepository, CrewRunState | 210 | domain.crew | 0898678 |
| D/live/ LiveChannel, ProgressReport, ServerMessage | 133 | domain.live | 0898678 |
| D/sync/ UploadPendingRunsUseCase, SyncDisposalsUseCase, RetryPolicy, SyncPorts, SyncValues | 149 | domain.sync | 0898678·418bd2b |
| D/account/ 6개 | 226 | domain.account | 0898678 |
| D/settings/ 4개 | 98 | domain.settings | 0898678 |
| D/notification/ 2개 | 78 | domain.notification | 0898678 |
| D/record/ 2개, D/runimal/ 1개, D/avatar/ 1개 | 231 | domain.record·runimal·avatar | 0898678 |
| A/ AppScopeModule, MainActivity, RunkkApp, RunkkNavHost | 105 | app | 418bd2b·e73e644 |
| H/ HomeScreen, HomeFeatureEntry | 55 | ui.home | e73e644 |
| U/ designsystem/Theme, navigation/Routes, navigation/FeatureEntry | 60 | core.designsystem, core.navigation | e73e644 |
| B/ convention 플러그인 4개, Common, ModuleRules | 272 | 13.2 Gradle 모듈·계층 규칙 | e73e644 |
| app/src/test/.../ArchitectureTest.kt | 210 | 14장 구조 시험(Konsist) | 71066b7 |

미구현 (다음 단계 예정, 판정에 넣지 않음):

| 설계 요소 | 예정 단계 | 비고 |
|-----------|-----------|------|
| `:data` 전체: api.core·api.account·api.run·api.record·api.battle·api.crew·api.runimal·api.avatar·api.notification·api.live, data.store·settings·sync·watch·live·device·push·session (18개 패키지), 바인딩 모듈(…Bindings) | A2 | `:data`는 build.gradle.kts만 있다 |
| ui.run·record·battle·crew·runimal·avatar·me·notification·share, 화면별 ViewModel, ui.home의 ViewModel·대시보드 | A3 이후 | `:feature:*` 9개는 build.gradle.kts만 있다 |
| RunkkApp 시작 작업(SyncDisposalsUseCase, SettingsRepository.refresh, PushTokenSync.syncCurrent, loggedIn 구독과 SettingsRepository.clear) | A2 | A/RunkkApp.kt:6-10 KDoc에 예정으로 적혀 있다 |
| core.designsystem 공용 버튼·카드·지표 표시 | 화면 단계 | |
| `:wear` 본체: watch.heart(HeartRateCollector), watch.link(PhoneLink), watch.ui(WatchHomeScreen, WatchRunDisplay) | A4 | 매니페스트·build.gradle.kts만 있다 |
| 14장 "단위(앱 데이터)" 시험, 실시간 메시지 계약 시험(H-17) | A2 | wear.contract 왕복 시험은 있다 |

### 1. 설계 일치

설계서는 design-oo.md v0.7이 기준이다. 13.2 마지막 문단대로 클래스·메소드 이름이 다르면 설계 불일치로 본다.

#### 1.1 이름·시그니처 대조

| 설계서 항목 | 코드 | 일치 | 비고 |
|-------------|------|------|------|
| 13.2 Gradle 모듈 `:app`, `:core:model`, `:core:ui`, `:domain`, `:data`, `:feature:*` 10개, `:wear`, `:wear-contract` | settings.gradle.kts:28-44 | 예 | |
| 13.2 계층 규칙 (`:feature:*` → `:domain`·`:core:*`, record·runimal → share 예외, `:data` → domain·core.model·wear-contract) | B/ModuleRules.kt:16-34, `enforceModuleRules` | 예 | Gradle 선언 위반을 설정 단계에서 막는다 |
| 13.2 `:domain`은 Android 플러그인 없는 JVM 모듈 | domain/build.gradle.kts:3 `runkk.jvm.library` | 예 | |
| core.model: Distance, Pace, GeoPoint, RunMode, CardKind, SignalState, AppError(Network, Unauthorized, Storage, Rejected), Outcome(Success, Failure) | M/ 8개 파일 | 예 | GeoPoint.metersTo 추가(갱신 요청 7), Rejected 사유 상수 추가(갱신 요청 11) |
| core.navigation: Routes(경로 이름 문자열), FeatureEntry(route, register(builder)) | U/navigation/Routes.kt:7-13, FeatureEntry.kt:10-14 | 예 | Routes.Key 추가(갱신 요청 13) |
| core.designsystem: RunkkTheme | U/designsystem/Theme.kt:17 | 예 | 공용 구성 요소는 미구현 |
| wear.contract: WearPaths, HeartRateBatchMessage, WatchDisplayMessage, StartRequestMessage | W/ 4개 + WearJson | 예 | WatchDisplayMessage 항목 추가(갱신 요청 9) |
| RunSession: start, onLocation, onHeartRate, tick, lock, hintUnlock, unlock, hintFinish, finish, end, arrivalAt, updateGap, updateRank | D/run/RunSession.kt:55-154 | 예 | 13개 모두 이름·매개변수·반환형 일치 |
| RunTracker: checkStartable, startRun, onLocationSample, tick, lockScreen, onUnlockTapped, onUnlockLongPressed, onFinishTapped, onGapChanged, onRankChanged, progress, consentRequired, watchStartRequests | D/run/RunTracker.kt:55-188 | 예 | |
| RunTracker.onFinishLongPressed(), endRun() (반환 없음) | `: FinishOutcome` (D/run/RunTracker.kt:155, 172) | 아니오 | 갱신 요청 2 |
| RunTracker.onLateHeartRates "부르는 쪽: 내부" | `fun onLateHeartRates` 공개 (D/run/RunTracker.kt:191) | 아니오 | 코드 결함 경(4절 O-2) |
| RunTracker 의존 "RunSession, LocationFilter, CoachingJudge … 모두 생성자 주입" | 포트 7개 + Clock + CoroutineScope 주입, RunSession·LocationFilter·CoachingJudge는 회마다 생성 (D/run/RunTracker.kt:34-48, 85-87) | 아니오 | 회마다 새 상태가 필요해 코드가 옳다. 갱신 요청 3·4 |
| LocationFilter.accept, CoachingJudge.evaluate, GuidanceRule 6개 이름 | D/run/LocationFilter.kt:15, CoachingJudge.kt:11, GuidanceRule.kt:18-135 | 예 | 모드별 조립(자유런 4, 대결 2, 크루런 2)도 일치 |
| 포트 PendingRunRepository·UploadScheduler·WatchGateway·GuidanceOutput·LocationPermission·MeasurementHost | D/run/Ports.kt:12-66 | 예(이름) / 아니오(반환형) | PendingRunRepository 연산이 Unit·List 대신 Outcome. 13.2 "포트·UseCase는 Outcome"과 맞으므로 6.1.14 쪽이 낡았다. 갱신 요청 1 |
| CompletedRun(purgeRoute, purgeHeartRates, uploadState, savedAt), FinishOutcome | D/run/CompletedRun.kt:13-48 | 예 | |
| UploadPendingRunsUseCase.invoke(): List<UploadOutcome>, RetryPolicy.nextDelay, SyncDisposalsUseCase.invoke() | D/sync/ | 예 / 반환형만 Outcome | 갱신 요청 1 |
| SettingsRepository(refresh, 읽기 6개, changes, clear), SettingsRemote.fetch | D/settings/SettingsRepository.kt:10-31, SettingsRemote.kt:6-8 | 예(이름) | clear·fetch 반환형 Outcome. 갱신 요청 1 |
| SettingsSnapshot.guidanceSettings(음성·진동, 간격, 순위 알림, 코치 계획) | D/settings/GuidanceSettings.kt:7-15 | 아니오 | 음성·진동을 러닝·대결로 나눴다. 갱신 요청 6 |
| LoginUseCase.invoke(token), LogoutUseCase, WithdrawUseCase, ChangeSettingUseCase | D/account/ | 예 | 호출 순서·실패 때 멈춤 모두 표와 같다 |
| LoginUseCase에 가입 연산 없음 | `suspend fun signUp(form)` (D/account/LoginUseCase.kt:21) | 아니오 | 13.2 UseCase 규칙 위반(경, 2절 S-15). 가입 흐름은 갱신 요청 10 |
| LiveChannel(connect, send, messages, close), LiveChannelProvider.open, ServerMessage 10종, TrackTable·TrackPoint, ProgressReport | D/live/ | 예 | 10종 이름 모두 일치 |
| BattleRunCoordinator: onMatched, onStart, onRecordBattleStart, onProgress, onMessage, onArrived, forfeit, state | D/battle/BattleRunCoordinator.kt:83-141 | 예 | 의존에 GuidanceOutput·Clock 추가. 갱신 요청 14 |
| OpponentTrack.distanceAt (A-08), GapTracker | D/battle/OpponentTrack.kt:19-40, GapTracker.kt:21-33 | 예 | A-08 보간과 같다. 계약 표 시험 있음 |
| BattleRepository 10개 연산 | D/battle/BattleRepository.kt:7-29 | 예 | |
| CrewRunCoordinator: onStart, onProgress, onMessage, onArrived, quit | D/crew/CrewRunCoordinator.kt:80-107 | 예 | |
| "크루런 대기 진입 때 범위를 만든다" (연산 이름 없음) | `onLobbyEntered(postId, distance, myName)` (D/crew/CrewRunCoordinator.kt:68) | 아니오 | 갱신 요청 8 |
| PushTokenSync.onTokenChanged, syncCurrent, 포트 PushTokenRemote·PushTokenSource·LoginState, NotificationRepository | D/notification/ | 예 | CoroutineScope 주입 추가. 갱신 요청 14 |
| RecordRepository(대시보드·기록·성장·리포트·랭킹·코치·오늘 계획·카드), RunCard, "화면용 도메인 모델" | D/record/ | 예 | 이름 규칙(서버 …View에서 View 제거)은 설계서에 없다. 갱신 요청 12 |
| RunimalRepository·RunimalStatus·RunimalCard, AvatarRepository | D/runimal/, D/avatar/ | 예 | |
| app: RunkkApp, MainActivity, RunkkNavHost(Set<FeatureEntry>), AppScopeModule(`CoroutineScope(SupervisorJob() + Dispatchers.Default)`) | A/ 4개 | 예 | 기준선의 RunkkApplication·RunkkNavDisplay를 설계 이름으로 바꿨다(코드가 설계에 맞춰짐). AppScopeModule에 예외 기록 핸들러 추가. 갱신 요청 14 |
| ui.home: FeatureEntry 구현 @IntoSet | H/HomeFeatureEntry.kt:15-31 | 예 | |
| 14장 구조 시험: domain의 android·retrofit2·room·hilt import 0, ui·app → api·data 0, 5.1.2 표 밖 import 0 | app/src/test/.../ArchitectureTest.kt:36-73 | 예 | 표 전체(api·data·ui·watch 포함)를 미리 적어 다음 단계도 같은 시험으로 지킨다 |

#### 1.2 의존 방향 대조 (5.1.2 표, 13.2 계층 규칙)

코드 import로 패키지 사이 의존을 다시 셌다.

| 모듈 | 실제 import 대상 | 5.1.2 표 | 결과 |
|------|------------------|----------|------|
| domain.settings·live·record·runimal·avatar·notification | core.model | core.model | 일치 |
| domain.run | domain.settings, core.model | 같음 | 일치 |
| domain.sync | domain.run, core.model | 같음 | 일치 |
| domain.account | domain.run, domain.settings, core.model | 같음 | 일치 |
| domain.battle, domain.crew | domain.run, domain.live, core.model | 같음 | 일치 |
| app | core.navigation, core.designsystem | 표의 부분집합 | 일치 (domain 4개 import는 A2에서 생긴다) |
| ui.home | core.navigation, core.designsystem | 표의 부분집합 | 일치 |

설계에 없는 의존 화살표: 없음. 순환 0, 계층 역행 0, 최대 Fan-Out(패키지) domain.battle·domain.crew 3. 기준 7 안이다.

같은 화살표 안에서 주고받는 것이 늘어난 곳:

| From → To | 주고받는 것 | 결합도 | 등급 |
|-----------|-------------|--------|------|
| domain.battle → domain.run | 설계: RunTracker 연산. 추가: GuidanceOutput.play(Guidance), PlanSnapshot, RunProgress, RunStartSpec | 스탬프 (Guidance·RunProgress 객체 전체) | 없음 (기준 상한 이내). 갱신 요청 14 |
| domain.crew → domain.run | RunTracker 연산, CrewRank, RunProgress, RunStartSpec | 스탬프 | 없음 |
| domain.run → domain.settings | SettingsRepository 읽기, GuidanceSettings | 데이터·스탬프 | 없음 |

클래스 단위 Fan-Out(생성자 주입 수)은 RunTracker가 9로 기준 7을 넘는다. 2절 S-1에 적는다.

#### 1.3 응집도 회귀

| 모듈 | 설계 판정 | 코드 | 결과 |
|------|-----------|------|------|
| core.model | "변경이 드문 불변 값과 오류 형식" | 업무 사유 코드 상수 3개(LOCATION_CONSENT, RUN_IN_PROGRESS, SIGN_UP_REQUIRED)가 들어왔다 (M/AppError.kt:17-27). 사유 코드는 기능이 늘 때마다 더해지는 값이라 Fan-In이 가장 큰 모듈이 자주 바뀐다 | 경 (2절 S-14) |
| domain.run | 기능적 (러닝 한 회 측정) | 측정·안내·포트만 있다 | 유지 |
| domain.crew | 통신적 (크루 데이터) | CrewRepository 28개 연산과 모델 14종. 같은 크루 자료를 다뤄 통신적은 유지. 화면 영역별 포트 분리는 design-review.md의 RecordRepository 권고와 같은 종류의 권고로 남긴다 | 유지 (권고) |
| 나머지 domain.* | 통신적 이상 | 관련 없는 유틸 메소드 유입 없음 | 유지 |

#### 1.4 설계 동작과 다른 곳 (코드 결함)

| ID | 파일:줄 | 현재 | 교정 예시 | 등급 |
|----|---------|------|-----------|------|
| F-1 | D/battle/BattleRunCoordinator.kt:217, 224-226 / D/crew/CrewRunCoordinator.kt:145 | `_state.value = BattleRunState.Running(m.battleId, m.gapMeters, m.opponentDistance, previous)` — CountdownStarted로 넣은 `Closing`(종료 카운트다운)을 다음 OpponentProgress·BattleGuidance가 곧바로 `Running`으로 덮는다. 크루런은 Countdown 중 중계가 `Running`으로 덮는다. 6.1.14의 상태 흐름(MATCHED → … → 종료)과 다르고 종료 카운트다운 화면이 1초 안에 사라진다 | `_state.update { if (it is BattleRunState.Closing) it else BattleRunState.Running(m.battleId, m.gapMeters, m.opponentDistance, previous) }` | 중 |
| F-2 | D/settings/GuidanceSettings.kt:11, D/run/GuidanceRule.kt:130, D/battle/BattleRunCoordinator.kt:289, 292 | `battleVibration`을 읽는 곳이 없다. 격차 안내는 `vibration = false`, 추월·역전 안내는 `voice = false, vibration = true` 고정이라 대결 설정의 음성·진동(FR-048·FR-187)을 무시한다 | GapGuidanceRule: `Guidance(Guidance.Kind.GAP, message, voice = true, vibration = settings.battleVibration)`, 추월 안내는 RunTracker가 GuidanceContext의 설정으로 만들게 `runTracker.onOvertake(kind)`로 넘긴다 | 중 |
| F-3 | D/battle/BattleRunCoordinator.kt:114, 138 / D/crew/CrewRunCoordinator.kt:104 | `run.battleId?.let { battleRepository.forfeit(it) }` — markStarted·forfeit·quit의 Outcome.Failure를 버린다. 기기는 끝났는데 서버는 참가 중으로 남고 사용자에게 알림이 없다(팀 규약 1장 "예외는 삼키지 않는다", 5.1.2 "Outcome을 화면 상태로 바꾼다") | `val reported = battleRepository.forfeit(id); if (reported is Outcome.Failure) _state.value = BattleRunState.ReportFailed(id, reported.error)` | 중 |
| F-4 | D/run/RunTracker.kt:193, D/sync/UploadPendingRunsUseCase.kt:37 | `scope.launch { pendingRuns.appendHeartRates(recordId, samples) }` — 결과를 버린다. 기록이 이미 전송·삭제됐으면 append가 실패해 늦은 심박(FR-043)이 사라지고, 전송이 pending()을 읽은 뒤 append가 들어오면 `remove(recordId)`가 그 심박까지 지운다 | `if (pendingRuns.appendHeartRates(recordId, samples) is Outcome.Failure) uploadScheduler.requestLateHeartRates(recordId, samples)` (서버 경로는 갱신 요청 16) | 중 |

설계서 갱신 요청 (코드가 옳거나 설계에 정할 곳이 없는 경우, `/sw-design-oo` 재호출 목록):

| No | 구현 결정 | 판정 | 설계서 고칠 곳 |
|----|-----------|------|----------------|
| 1 | 포트 반환형을 Unit·List 대신 `Outcome<T>` (PendingRunRepository 전부, SettingsRepository.clear, SettingsRemote.fetch, SessionRepository.save·clear, RunUploadRemote.appendHeartRates, DisposalRemote, SyncDisposalsUseCase) | 갱신 요청. 13.2 "포트·UseCase는 Outcome"을 코드가 따랐고 6.1.14 시그니처 표가 낡았다. 같은 규칙으로 `LiveChannel.connect()`·`MeasurementHost.startForeground()`도 `Outcome<Unit>`로 바꿀 것을 함께 요청한다. 지금은 채널 연결 실패를 조정자가 CoroutineExceptionHandler로만 받고(D/battle/BattleRunCoordinator.kt:282), 포그라운드 시작 거부(Android 12+)는 받을 길이 없어 세션이 측정 중으로 남는다(D/run/RunTracker.kt:85, 92) | 6.1.14 포트 표, SettingsRepository 표, 계정 포트 문장, domain.sync 포트 문장, LiveChannel 문장 |
| 2 | RunTracker.onFinishLongPressed·endRun이 `FinishOutcome` 반환 | 갱신 요청. 화면이 종료 요약(srs.md 1.3: 저장·거부·저장 안 함)을 보여야 하므로 반환이 필요하다. 세션이 없을 때 `NotSaved`를 돌려주는 것(D/run/RunTracker.kt:158, 175)은 "거리 0"과 구분되지 않으니 설계서에서 의미를 정한다 | 6.1.14 RunTracker 표 |
| 3 | Clock 주입 (RunTracker, BattleRunCoordinator, CrewRunCoordinator) | 갱신 요청. onFinishLongPressed()·endRun()에 now 매개변수가 없어 시계가 필요하다. 단 Clock 바인딩을 누가 내주는지 설계서에 없다(D-61은 app이 applicationScope 하나만 내준다고 적었다). A2에서 RunTracker를 주입하는 순간 Hilt 컴파일이 깨지므로 AppScopeModule이 `Clock.systemUTC()`도 내준다고 D-61·5.1.2 설계 규칙에 적어야 한다 | 6.1.14 RunTracker 의존, 5.1.2 바인딩 규칙, D-61 |
| 4 | RunSession·LocationFilter·CoachingJudge는 회마다 RunTracker가 만든다 | 갱신 요청. 회마다 새 상태가 필요해 싱글턴에 주입할 수 없다 | 6.1.14 RunTracker "의존" 행 |
| 5 | RunProgress.heartRate 추가 | 갱신 요청. GuidanceContext가 심박을 받을 길이 설계서에 없어 BreathGuidanceRule(FR-045)이 판정할 수 없다 | 6.1.14 GuidanceContext, A-02 tick의 RunProgress 항목 |
| 6 | GuidanceSettings 음성·진동을 러닝(runVoice·runVibration)·대결(battleVoice·battleVibration)로 분리 | 갱신 요청. 서버 RunSettings·BattleSettings 구분(FR-048·FR-187)과 맞다. battleVibration을 쓰지 않는 것은 코드 결함 F-2 | 6.1.14 SettingsSnapshot 문장 |
| 7 | 현재 페이스를 창 처음·끝 포인트의 직선 거리 대신 누적 거리 차로 계산, GeoPoint.metersTo(Double) 추가 | 갱신 요청. 직선 거리는 곡선·되돌아오는 코스에서 페이스를 과대 추정하고 불안정 구간 점프를 포함한다. 누적 거리 차는 총거리와 같은 기준이다. 시각 차가 0일 때 1 s/km가 나오는 것은 코드 결함 S-19 | 6.2 A-02 tick, 14장 단위 시험 목록(GeoPoint.metersTo) |
| 8 | CrewRunCoordinator.onLobbyEntered(postId, distance, myName) | 갱신 요청. 설계서가 "대기 진입 때 범위를 만든다"고만 적고 연산이 없다 | 6.1.14 CrewRunCoordinator 문장 |
| 9 | WatchDisplayMessage.heartRateCollectionAllowed·notice, WatchDisplay.running·notice | 갱신 요청. 수집 금지 플래그(NFR-023)와 시작 요청 거절 안내(FR-183)를 실을 곳이 설계서에 없었다. 권고: 수집 허용은 진행 표시와 수명이 달라(측정 중이 아닐 때도 철회 즉시 보내야 함) 별도 메시지(`/runkk/collection-policy`, DataClient)로 나누는 안을 함께 검토 | 6.1.15, 6.1.14 WatchLink 행 |
| 10 | 가입 흐름 (LoginUseCase.signUp) | 설계서에는 가입 흐름 추가를 요청하고, 코드는 13.2 UseCase 규칙(`invoke` 하나)에 맞춰 SignUpUseCase로 나눈다(S-15) | 6.1.14 계정 UseCase 표 |
| 11 | AppError.Rejected 사유 코드 상수 | 사유 코드를 상수로 두는 것은 갱신 요청(목록 위치가 설계서에 없다). core.model에 두는 위치는 코드 결함 경(S-14). 설계서에 "사유 코드 상수는 그 사유를 내는 domain.<기능>에 둔다"를 적는다. RunimalRepository KDoc의 `NO_TYPE`(D/runimal/RunimalRepository.kt:9)은 상수가 없다 | 5.1.2 core.model 행, 설계 규칙 |
| 12 | 화면용 도메인 모델 이름 = 서버 조회 결과 이름에서 `View` 접미어를 뺀 것 (D/record/RecordModels.kt:12) | 갱신 요청. 5.1.2는 "화면용 도메인 모델"이라고만 적었다 | 5.1.2 domain.record·crew·avatar 행 또는 13.2 "도메인 모델과 매퍼" |
| 13 | Routes.Key(route: String) : NavKey (Navigation 3 백 스택 키) | 갱신 요청. 설계서는 경로 이름 문자열만 정했다. design-review.md의 H-24 권고(Routes 인자 키)와 함께 정한다 | 5.1.2 core.navigation 행 |
| 14 | 의존 보완: BattleRunCoordinator ← GuidanceOutput·Clock, PushTokenSync ← CoroutineScope(onTokenChanged가 suspend가 아님), AppScopeModule에 CoroutineExceptionHandler | 갱신 요청. 모두 이미 있는 화살표 안이다 | 6.1.14 조정자·PushTokenSync·AppScopeModule 행 |
| 15 | 크루런 본인 순위를 표시명으로 찾음 (D/crew/CrewRunCoordinator.kt:146) | 갱신 요청. CrewRunRelay.Entry(5.7.2)에 참가자 식별자가 없어 코드는 이름밖에 쓸 수 없다. 동명이인이면 남의 순위를 안내한다. Entry에 `participantId` 또는 `mine` 항목을 더한다 | 5.7.2 CrewRunRelay, 6.1.14 CrewRunCoordinator |
| 16 | 늦은 심박의 기록이 이미 보관함에서 지워졌을 때의 경로 | 갱신 요청. 설계서는 appendHeartRates를 PendingRunRepository로만 보내게 했다(design-review.md M32). 전송 뒤 도착한 심박은 RunUploadRemote.appendHeartRates로 직접 보내는 경로를 정한다(코드 결함 F-4와 짝) | 6.1.14 RunTracker.onLateHeartRates, domain.sync |

설계서가 아니라 팀 코드 컨벤션 갱신 요청: 코드 컨벤션 3장 "화면 키는 `ui/RunkkNavDisplay.kt`의 `NavKey`에 추가"는 멀티 모듈 뒤 `core.navigation.Routes`와 각 `FeatureEntry`로 바뀌었다. 같은 3장의 패키지 표(`ui/`, `data/`, `di/`, `core/`)도 5.1.2 계층(domain·api·data·ui·app)으로 바꿔야 한다.

### 2. 코딩 스타일

함수 길이 50줄 초과 0개(최장 BattleRunCoordinator.handle 35줄), 함수 매개변수 5개 이상 0개(최대 3개), 중첩 깊이 4 이상 0개(UploadPendingRunsUseCase.invoke의 map → when → if가 3)다. `!!`, GlobalScope, `println`, `catch`, `runCatching`, Jira 키 없는 `TODO`는 main 소스에 없다.

| ID | 파일:줄 | 항목 | 현재 | 교정 예시 | 등급 |
|----|---------|------|------|-----------|------|
| S-1 | D/run/RunTracker.kt:34-44 | 모듈화 (클래스 Fan-Out) | 생성자 주입 9개(포트 7 + Clock + CoroutineScope), 기준 7 초과 | 저장·재시도·전송 요청(211-220)을 `class CompletedRunSaver @Inject constructor(pendingRuns, uploadScheduler, guidanceOutput)`로 뽑아 RunTracker 주입을 7개로 줄인다 | 경 |
| S-2 | D/battle/BattleRunCoordinator.kt:246-285 ↔ D/crew/CrewRunCoordinator.kt:162-201, 그리고 178 ↔ 122 | 모듈화 (중복 블록) | `openRun`(직렬 디스패처·SupervisorJob·예외 처리 범위 생성, 이전 회 정리), `finish`, `close`, 진행값 구독이 두 조정자에 거의 같은 30여 줄로 있다 | domain.run에 `internal fun CoroutineScope.serialChildScope(onFailure: () -> Unit): CoroutineScope`와 `class RunRound(scope, tracker)`(running 표시·endRun·cancel)를 두고 두 조정자가 쓴다 | 중 |
| S-3 | D/run/GuidanceRule.kt:13-158 외 | 팀 규약 1장 "파일 하나에 최상위 public 타입 하나" | GuidanceRule.kt에 공개 타입 7개. Ports.kt 6개, MeasurementValues.kt 5개, AccountPorts.kt·AccountValues.kt·CrewRepository.kt·BattleValues.kt·RecordModels.kt·NotificationPorts.kt·SyncPorts.kt·SyncValues.kt·CompletedRun.kt·RunStartSpec.kt·ServerMessage.kt도 여러 개 | `GuidanceRule.kt`(interface), `SplitGuidanceRule.kt` … 로 나눈다. 아니면 규약에 "sealed 계층·포트 묶음·작은 값 묶음은 한 파일 허용"을 더한다(규약 쪽이 Kotlin 관례와 다르다) | 경 |
| S-4 | D/battle/BattleRunCoordinator.kt:78-79, D/run/Guidance.kt:6, D/run/RunProgress.kt:18, D/run/WatchDisplay.kt:12 외 | 팀 규약 1장 "Boolean은 is/has/can" | `var scheduled = false`, `var running = false`, `val voice: Boolean`, `val locked: Boolean`, `val running: Boolean` | `var isScheduled`, `var isRunning`, `val hasVoice`, `val isLocked`. 서버 JSON 항목을 그대로 옮긴 조회 모델(`valid`, `pending`, `owned` …)은 매퍼가 이름을 바꾸거나 규약에 예외를 적는다 | 경 |
| S-5 | D/run/GuidanceRule.kt:24, 39, 57, 88, 127, 129, 142, D/run/RunSession.kt:110, 117, D/run/RunTracker.kt:219, 249-250, D/battle/BattleRunCoordinator.kt:289, 292 | 팀 규약 3장 "문자열 하드코딩 금지" | `"${km}km 지점입니다"` 등 TTS·화면 문구를 도메인이 만든다. 도메인(순수 JVM)은 리소스를 못 써서 다국어·문구 수정이 도메인 변경이 된다 | `Guidance(kind = Guidance.Kind.SPLIT, args = listOf(km), …)`만 만들고 data.device.AndroidGuidanceOutput이 `context.getString(R.string.guidance_split, km)`로 바꾼다 | 경 |
| S-6 | H/HomeScreen.kt:16 | 팀 규약 3장 "문자열 하드코딩 금지" | `Text(text = "RunKK", …)` | `Text(text = stringResource(R.string.home_title), …)` | 경 |
| S-7 | A/AppScopeModule.kt:27 | 팀 규약 1장 "로그는 Timber" | `Log.e(TAG, "applicationScope 작업 실패", error)` | `Timber.e(error, "applicationScope 작업 실패")` | 경 |
| S-8 | D/battle/BattleRunCoordinator.kt:281, D/crew/CrewRunCoordinator.kt:196 | 팀 규약 3장 "lateinit은 Hilt 주입 외 금지" | `lateinit var created: BattleRun` | 범위를 회 객체 안에서 만든다: `private inner class BattleRun(parent: CoroutineContext) { val scope = CoroutineScope(parent + SupervisorJob(parent.job) + serial + CoroutineExceptionHandler { _, _ -> finish(this, null) }) }` | 경 |
| S-9 | D/battle/BattleRunCoordinator.kt:279, D/crew/CrewRunCoordinator.kt:194 | 팀 규약 3장 "Dispatchers는 직접 쓰지 않고 주입" | `parent[ContinuationInterceptor] as? CoroutineDispatcher ?: Dispatchers.Default` | `val base = requireNotNull(parent[ContinuationInterceptor] as? CoroutineDispatcher) { "applicationScope에 디스패처가 없다" }` | 경 |
| S-10 | D/battle/BattleRunCoordinator.kt:241, D/crew/CrewRunCoordinator.kt:158 | 팀 규약 3장 "when은 exhaustive" | sealed ServerMessage에 `else -> Unit` — 메시지 종류가 늘어도 컴파일러가 알리지 않는다 | `is MatchFound, is CrewRunStart, is CrewRunCancelled, is CrewRunRelay, is CrewRunEnded -> Unit` | 경 |
| S-11 | D/run/RunTracker.kt:223, 228, 238 | 변수명 (섀도잉) | 바깥 `val late`(기록ID·심박 쌍)와 안쪽 `val late`(심박 목록)가 같은 이름이고, `{ (recordId, samples) -> … }`가 매개변수 `samples`(받은 전체)를 늦은 심박으로 가린다 | `val lateBatch = synchronized(lock) { … val lateSamples = mutableListOf<HeartRateSample>() … }`, `lateBatch?.let { (recordId, lateSamples) -> onLateHeartRates(recordId, lateSamples) }` | 경 |
| S-12 | D/battle/OpponentTrack.kt:32-36 | 변수명 | `val a = samples[i]; val b = samples[i + 1]; val r = …` (A-08 의사 코드 기호를 그대로 씀) | `val before = samples[i]; val after = samples[i + 1]; val ratio = …` | 경 |
| S-13 | D/battle/GapTracker.kt:33, D/battle/BattleRunCoordinator.kt:205-206 | 모듈화 (같은 판정 두 곳) | 도착 판정 `GapTracker.hasArrived`는 시험에서만 쓰이고 조정자는 `p.distance.isAtLeast(target)`를 따로 한다 | `if (gapTracker?.hasArrived(p.distance) ?: p.distance.isAtLeast(target)) finish(run, null)` 또는 hasArrived를 지운다 | 경 |
| S-14 | M/AppError.kt:17-27 | 응집도 (안정 모듈에 업무 상수) | `const val LOCATION_CONSENT`, `RUN_IN_PROGRESS`, `SIGN_UP_REQUIRED` | `object RunRejections { const val LOCATION_CONSENT = "LOCATION_CONSENT" }`를 domain.run에, `SIGN_UP_REQUIRED`는 domain.account에 둔다 | 경 |
| S-15 | D/account/LoginUseCase.kt:21 | 13.2 UseCase 규칙 (`invoke` 하나) | `suspend fun signUp(form: SignUpForm): Outcome<Unit>` | `class SignUpUseCase @Inject constructor(account, session, settings) { suspend operator fun invoke(form: SignUpForm) }`, 세션 시작 3단계는 `internal class SessionStarter`로 공유 | 경 |
| S-16 | D/battle/BattleRunCoordinator.kt:118-121·178, D/crew/CrewRunCoordinator.kt:85-88·122 | 모듈화 (같은 입력 두 경로) | 공개 `onProgress(p)`와 내부 `runTracker.progress` 구독이 둘 다 `progress(run, p)`를 부른다. 화면이 onProgress도 부르면 진행값 전송·격차 갱신이 두 번 된다 | 내부 구독만 남기고 `fun onProgress`는 지우거나 `internal`로 시험 전용 표시(설계서 6.1.14의 onProgress 행도 "내부 구독"으로 고친다) | 경 |
| S-17 | D/battle/BattleRunCoordinator.kt:258, D/crew/CrewRunCoordinator.kt:174 | 제어 구조 (상태 되돌림 없음) | `_state.value = BattleRunState.Finished(it, status)` 뒤 Idle로 돌아가지 않아, 다음 대결 화면이 열리자마자 지난 회의 Finished를 받는다 | `fun acknowledgeFinished() { _state.compareAndSet(current, BattleRunState.Idle) }`를 두고 결과 화면이 부른다 | 경 |
| S-18 | D/run/RunTracker.kt:202-205 | 제어 구조 (종료 통지 누락) | `complete`가 `measurementHost.stop()`만 하고 워치에 종료를 보내지 않아 워치 화면이 마지막 진행값에 멈춘다(FR-042) | `watch.publish(_progress.value.toWatchDisplay(plan = null, notice = null, running = false))`를 complete에 더한다 | 경 |
| S-19 | D/run/RunSession.kt:184-186 | 제어 구조 (경계값) | 창의 처음·끝 시각이 같으면 `Pace.of(Duration.ZERO, …)`가 `coerceAtLeast(1)`로 1 s/km를 만든다 | `if (meters <= 0 \|\| !toAt.isAfter(fromAt)) return null` | 경 |
| S-20 | W/WatchDisplayMessage.kt:19 | 변수 사용 (안전한 기본값) | `val heartRateCollectionAllowed: Boolean,` — 이 항목이 없는 옛 메시지는 해독 예외가 난다 | `val heartRateCollectionAllowed: Boolean = false,` (개인정보 쪽 안전한 값, D-53과 같은 원칙) | 경 |

### 3. 구조적 프로그래밍

goto 등가물(라벨 break·continue, 예외로 정상 흐름 분기, while(true) 다중 break, switch fallthrough): 없음. `check()`·`require()`는 금지 전이·불변식 위반에만 쓰고 호출자가 먼저 막는다(RunTracker가 `takeIf { it.isActive }`로 거른 뒤 RunSession 전이를 부른다).

복잡도 약산 상위 함수(전체 표는 7절):

| 함수 | 조건문 | 반복문 | 약산 복잡도 | 위반 |
|------|--------|--------|-------------|------|
| BattleRunCoordinator.handle (D/battle/BattleRunCoordinator.kt:209) | 11 (when 갈래 5, if 5, ?: 1) | 0 | 12 | 복잡도 10 초과(7절). when 갈래 안 `return`은 갈래 끝과 같아 허용 |
| CrewRunCoordinator.handle (D/crew/CrewRunCoordinator.kt:139) | 9 | 0 | 10 | 없음 (상한) |
| UploadPendingRunsUseCase.invoke (D/sync/UploadPendingRunsUseCase.kt:18) | 7 | 1 (map) | 9 | 없음 |
| BattleRunCoordinator.progress (:182) | 7 | 0 | 8 | 없음 |
| RunSession.onLocation (D/run/RunSession.kt:61) | 5 | 1 | 8 | 없음 |
| GapGuidanceRule.check (D/run/GuidanceRule.kt:116) | 7 | 0 | 8 | 없음 |
| RunTracker.save (D/run/RunTracker.kt:211) | 2 | 1 | 3 | 반복 안 출구 (P-1) |
| SyncDisposalsUseCase.invoke (D/sync/SyncDisposalsUseCase.kt:17) | 4 | 1 | 6 | 반복 안 출구 (P-2) |
| RunSession.arrivalAt (:133), OpponentTrack.arrivalTime (:43) | 2 | 1 | 4·5 | 반복 안 return이지만 그 함수의 유일한 책임이 "처음 닿은 지점 찾기"라 허용 |
| RunTracker의 `synchronized(lock) { … ?: return }` (:98, 109, 130, 143, 158, 175) | | | | 임계 구역 첫 줄의 가드 절이라 허용 |

| ID | 파일:줄 | 현재 | 교정 예시 | 등급 |
|----|---------|------|-----------|------|
| P-1 | D/run/RunTracker.kt:212-216 | `repeat(SAVE_ATTEMPTS) { attempt -> if (pendingRuns.save(run) is Outcome.Success) { uploadScheduler.requestUpload(); return } … }` — 반복 람다 안의 비지역 return이 함수 출구다 | `var attempt = 0; var saved = false; while (!saved && attempt < SAVE_ATTEMPTS) { saved = pendingRuns.save(run) is Outcome.Success; … attempt++ }` 뒤 `if (saved) uploadScheduler.requestUpload() else guidanceOutput.play(…)` | 중 |
| P-2 | D/sync/SyncDisposalsUseCase.kt:29, 31 | `if (purged is Outcome.Failure) return purged` / `if (confirmed is Outcome.Failure) return confirmed` — for 안의 출구 2개 | 한 건 처리를 `private suspend fun apply(order): Outcome<Unit>`로 뽑고 `var result: Outcome<Unit> = Outcome.Success(Unit); val it = orders.iterator(); while (result is Outcome.Success && it.hasNext()) result = apply(it.next()); return result` | 중 |

### 4. 객체지향 구현

| ID | 파일:줄 | 항목 | 현재 | 교정 예시 | 등급 |
|----|---------|------|------|-----------|------|
| — | 전체 | 캡슐화 | 공개 가변 필드 0. 상태 노출은 `private set`·`StateFlow`·`asSharedFlow()`만(13.2 클래스 속성 규칙) | 통과 | 없음 |
| O-1 | D/battle/BattleRunCoordinator.kt:269-277, D/crew/CrewRunCoordinator.kt:184-192 | 캡슐화 (클래스 불변식) | KDoc은 "상태 변경은 모두 그 범위의 직렬 디스패처에서 돈다"(46행)인데 `openRun`이 호출 스레드에서 `previous.running`을 읽고 쓴다. 이전 회의 finish와 겹치면 `endRun`이 두 번 불리거나, 새 회의 측정이 막 시작된 뒤라면 그 세션을 끝낼 수 있다 | `val running = AtomicBoolean(false)`로 바꾸고 정리하는 두 곳 모두 `if (previous.running.compareAndSet(true, false)) runTracker.endRun()` | 중 |
| O-2 | D/run/RunTracker.kt:191 | 캡슐화 (설계: 내부 연산) | `fun onLateHeartRates(recordId: String, samples: List<HeartRateSample>)` 공개. design-review.md M32가 appendHeartRates 경로를 이 클래스 하나로 묶은 뜻이 공개 연산으로 풀린다. 시험도 이 연산을 직접 부르지 않는다 | `private fun onLateHeartRates(…)` | 경 |
| O-3 | D/battle/BattleRunCoordinator.kt:69-80, D/crew/CrewRunCoordinator.kt:59-65 | 접근자 (검사 없는 가변 필드) | private 중첩 클래스라 외부 수정(내용 결합)은 아니지만 7개 `var`을 조정자의 여러 함수가 직접 고쳐 회의 상태 전이(예약 → 측정 → 종료)가 흩어져 있다 | `BattleRun`에 `fun schedule(track: OpponentTrack?, distance: Distance?)`, `fun markRunning(): Boolean` 같은 전이 연산을 두고 필드는 `private set` | 경 |
| O-4 | D/run/RunTracker.kt:130, 137, 143 | 다형성 (13.2 "enum + when은 상태 전이 메소드 안에서만") | `session?.takeIf { it.state == RunSession.State.RUNNING }` — RunTracker가 RunSession의 상태 값을 보고 갈래를 고른다 | RunSession에 `fun tryLock(): Boolean`, `fun tryUnlock(): Boolean`, `fun unlockHintIfLocked(): Guidance?`를 두고 RunTracker는 결과만 쓴다 | 경 |
| O-5 | D/battle/BattleRunCoordinator.kt:187-204 | 다형성 (종류 분기) | `if (track != null && gapTracker != null) { 기록 상대 } else { 실시간 채널 전송 }` — 상대 종류를 null 여부로 가른다. 이 분기가 handle의 복잡도 12에도 기여한다 | `interface OpponentFeed { fun onProgress(p: RunProgress): Int? }`, `RecordedOpponentFeed(track, gapTracker)`, `LiveOpponentFeed(channel)`를 start 때 한 번 고른다 | 경 |
| O-6 | D/battle/BattleRunCoordinator.kt:258, 282 | 상태 모델 | 채널 연결 실패로 닫힌 회와 정상 종료가 둘 다 `Finished(battleId, status = null)`라 화면이 "연결 실패"와 "결과 대기"를 구분할 수 없다 | `data class Failed(val battleId: Long, val error: AppError) : BattleRunState`를 더하고 CoroutineExceptionHandler에서 `close(created, failed = AppError.Network)` | 경 |
| — | D/run/RunSession.kt:153 | this | `this.rank = rank` | 통과 | 없음 |
| — | 전체 | 상속 | 클래스 상속 없음. interface·sealed interface만(GuidanceRule 6개, ServerMessage 10종) | 통과 | 없음 |
| — | 전체 | 생성자 | 값 객체는 생성 직후 유효(Distance·Pace·GeoPoint·CrewRank·GuidanceSettings의 `require`). setter를 여러 번 불러야 쓸 수 있는 클래스 없음 | 통과 | 없음 |
| — | D/run/CoachingJudge.kt:15-23 | 다형성 (RunMode 분기 수) | RunMode `when`은 규칙 묶음 조립 1곳뿐. 6.4 "RunMode 앱 1곳" 허용과 같다 | 통과 | 없음 |
| — | 전체 | 절차적 스타일 | 모든 연산이 객체 메소드. 최상위 함수는 Outcome 확장 2개, build-logic 설정 함수뿐 | 통과 | 없음 |

### 5. 형상 관리

| 활동 | 판정 | 근거 |
|------|------|------|
| 형상 식별 | 예 | Gradle 모듈이 13.2와 1:1이고 패키지가 5.1.2 모듈 이름과 1:1이다. Konsist 시험이 "모든 파일은 5.1.2 표의 패키지 안에 있다"로 확인한다 |
| 버전 제어 | 예 | Git 이력 5개 커밋(android), `versionName "0.1.0"`(app/build.gradle.kts:16). 태그는 0개다. A1 마감 때 `android-v0.1.0-a1` 같은 태그를 권고한다 |
| 변경 통제 | 아니오 | Jira 키 없이 진행 중이다. 브랜치 `feature/runkk-impl`은 AGENTS.md의 `feature/{Jira 키}-{작업명}`과 다르고, 커밋 5개 모두 제목에 `[{Jira 키}]`가 없다(예: `feat: domain 계층 11개 패키지 구현 (도메인 모델·포트·UseCase·조정자)`). 변경이 설계서 절 번호(5.1.2, 13.2, D-56, D-61)에는 묶여 있으나 승인된 변경 요청과는 묶여 있지 않다 |
| 형상 감사 | 예 | 기준선 origin/develop d38b99f(병합 기준과 같음) 대비 `git diff --stat origin/develop...HEAD -- android`로 121파일 변경을 확인할 수 있다 |
| 상태 보고 | 예 | 커밋 본문이 무엇을 왜 바꿨는지 목록으로 적었다(e73e644, 0898678, 418bd2b). CHANGELOG는 없다 |

제안: Jira에 A1 이슈(예: "안드로이드 멀티 모듈·domain 구현")를 만들고 MR 제목을 `feat: 안드로이드 A1 멀티 모듈·domain [S15P31A207-nn]`으로 단다. MR 본문에 이 리뷰의 갱신 요청 16건 링크를 넣어 변경 통제를 잇는다. 이미 올린 커밋은 다시 쓰지 않고 MR·병합 커밋에서 키를 단다. 형상 항목에 비용은 넣지 않는다.

### 6. 집계와 판정

| 등급 | 건수 | 항목 |
|------|------|------|
| 치명 | 0 | goto 등가물·내용 결합·공통 결합·설계에 없는 양방향 의존 없음 |
| 중 | 8 | F-1 Closing 상태 덮어쓰기, F-2 대결 진동 설정 무시, F-3 서버 보고 실패 무시, F-4 늦은 심박 덧붙이기 실패 무시·전송 경쟁, S-2 조정자 중복 블록, P-1 RunTracker.save 반복 안 출구, P-2 SyncDisposalsUseCase 반복 안 출구, O-1 openRun의 직렬 불변식 위반 |
| 경 | 24 | S-1·S-3~S-20 19건, O-2~O-6 5건 |

판정: **조건부**
근거: 치명 0, 중 8(4 이상 10 이하), 설계 일치 점검 수행, 형상 관리 1항목 아니오(변경 통제). 미구현 항목(:data, ui.* 9개, RunkkApp 시작 작업, :wear 본체)은 진척 항목이라 판정에 넣지 않고 `sw-sqa` 추적표 점검으로 넘긴다.

조치 상태(2026-10-20): 중 8건·경 24건 **수정 중(A2에 포함)**. 단 S-15(LoginUseCase.signUp을 SignUpUseCase로 분리)는 **통합 때** 한다. MR !8 전체 판정은 [code-review.md](../code-review.md)의 집계와 판정을 따른다.

집계에 넣지 않은 것:
- 기준선(origin/develop)부터 있던 사항: local 플레이버 `API_BASE_URL`이 `http://10.0.2.2:8080/`(app/build.gradle.kts:54)인데 networkSecurityConfig가 없어 targetSdk 36에서 평문 HTTP가 막힌다. A2에서 서버 호출을 붙일 때 `src/local/res/xml/network_security_config.xml`(10.0.2.2만 허용)을 함께 넣는다.
- 설계서가 정한 동작: 정지 때 현재 페이스를 직전 값으로 유지(A-02 "아니면 직전 값"), 서버 startAt과 단말 시계 직접 비교(시계 오차 보정은 설계서에 없음, 권고).
- 기준(복잡도 10)을 넘는 함수 1개는 결함으로 세지 않고 7절로 넘긴다.

보조 리뷰: ecc:kotlin-reviewer 결과 16건을 대조해 F-1·F-3·F-4·O-1·S-16~S-20을 이 결과서 등급 기준으로 다시 매겨 넣었고, 설계서가 정한 동작이거나 기준선 사항인 3건은 위처럼 집계에서 뺐다.

### 7. sw-test 전달: 우선 테스트 대상

| 함수 | 약산 복잡도 | 이유 |
|------|-------------|------|
| BattleRunCoordinator.handle | 12 | 유일한 복잡도 10 초과. 메시지 5종 × battleId 불일치 가드. F-1(Closing 유지) 회귀 시험이 없다. 갈래마다 `onOpponentProgress`·`onBattleGuidance`·`onCountdownStarted`로 나누면 각 3 이하가 된다 |
| CrewRunCoordinator.handle | 10 | 상한. 순위 범위 가드(1..인원)와 Countdown 중 중계 |
| UploadPendingRunsUseCase.invoke | 9 | 전송·이미 저장·심박 덧붙이기·삭제 실패 조합. F-4 경쟁(전송 중 append) 시험 없음 |
| BattleRunCoordinator.progress | 8 | 기록 상대·랜덤 두 경로 + 도착 판정 |
| RunSession.onLocation | 8 | NORMAL·UNSTABLE 전환과 창 정리. UNSTABLE → NORMAL 직후 거리 누적 경계 시험 없음 |
| GapGuidanceRule.check | 8 | 간격 변경·격차 부호·남은 거리 유무 |
| BreathGuidanceRule.check | 7 | 30초 지속·60초 간격·출생연도 없음 |

추가로 시험이 없는 경로: F-2(대결 진동 설정), F-3(서버 보고 실패), O-1(openRun과 finish 동시 진행), RunTracker의 위치 콜백·tick 동시 호출, 포그라운드 시작 실패.

### 8. 다음 행동

- [ ] 중 결함 8건 수정 (F-1~F-4, S-2, P-1, P-2, O-1): 담당 안드로이드 구현자, A2 착수 전
- [ ] 설계서 갱신 16건: `/sw-design-oo`로 6.1.14·6.1.15·5.1.2·5.7.2·6.2 A-02·D-61·14장 반영. 특히 3번(Clock 바인딩 위치)은 A2에서 RunTracker를 주입하기 전에 정해야 Hilt 그래프가 선다. 반영 뒤 `/sw-design`으로 design-review.md 확인(새 화살표는 없다)
- [ ] 팀 코드 컨벤션 3장 갱신: 화면 키(RunkkNavDisplay → Routes·FeatureEntry), 패키지 표, 파일당 공개 타입 규칙의 Kotlin 예외 여부(S-3), Boolean 접두사 규칙의 조회 모델 예외 여부(S-4)
- [ ] 변경 통제: Jira 이슈 발급, MR 제목·병합 커밋에 키, A1 태그
- [ ] `/sw-test` 호출: 7절 7개 함수와 "시험이 없는 경로" 5개

## 리뷰 2026-10-21 (2차)

대상 커밋: feature/runkk-impl cff0c6e(merge) 기준 (1차 9f2af7e, 기준선 origin/develop d38b99f). 우선 확인: 최근 android 커밋 40개와 E2E 수정 커밋 e7032f3..9fbfbf3.

입력: S15P31A207-impl/android, sw-sdlc-skills docs/sw/design-oo.md v0.10(2026-10-20)과 design-oo/05-01(5.1.2 모듈 표·의존 표), 05-02(D-61·D-77·D-78), 06-06(앱 클래스). 1차가 본 v0.7보다 새 판이다(1차 갱신 요청 16건이 반영된 판). 1차 [02-android.md](02-android.md).
방법: 읽기 전용(코드 수정·커밋·브랜치 전환·gradle 실행 없음). 코드를 직접 읽었고 함수 길이·매개변수·복잡도·중첩은 스크립트로 약산했다.

### 대상

| 항목 | 내용 |
|------|------|
| 코드 | S15P31A207-impl/android, 브랜치 feature/runkk-impl, 최신 커밋 cff0c6e(merge) 기준. Kotlin 약 365개 파일(시험 포함 31,337줄). 모듈 :app :core:model :core:ui :domain :data :feature:* 10개 :wear :wear-contract |
| 설계서 | S15P31A207-impl/docs/sw/design-oo.md는 없다. sw-sdlc-skills/docs/sw/design-oo.md v0.10(2026-10-20)과 design-oo/05-01(5.1.2 모듈 표·의존 표), 05-02(D-61·D-77·D-78), 06-06(앱 클래스)을 기준으로 썼다. 1차가 본 v0.7보다 새 판이다(1차 갱신 요청 16건이 반영된 판) |
| 우선 확인 | 최근 40개 android 커밋, E2E 수정 커밋 e7032f3..9fbfbf3(RunResultViewModel, WatchLink PlayServicesTransport, CrewFindViewModels.refreshMine, CrewHomeScreens NaN, localDebug E2E 토큰 경로) |
| 방법 | 코드를 직접 읽었다. 함수 길이·매개변수·복잡도·중첩은 스크립트로 약산했다(조건·반복·catch·논리 연산자·엘비스·when 갈래 +1, 람다 인자 화살표 제외). 약산이라 1~2 오차가 있다. 중첩은 if·for·while·when·try·반복 람다 기준으로 4단계 이상 0건이다 |
| 제외 | 시험 소스(src/test, androidTest, testFixtures), build-logic, e2e/maestro 스크립트, 생성 코드 |

경로 줄임말(이 문서의 "파일:줄"은 접두어를 붙여 읽는다, 모두 S15P31A207-impl/android/ 아래):

| 줄임 | 실제 경로 |
|------|-----------|
| D/ | domain/src/main/kotlin/com/a207/runkk/app/domain/ |
| DT/ | data/src/main/kotlin/com/a207/runkk/app/ |
| F/m/ | feature/m/src/main/java/com/a207/runkk/app/ui/m/ (m은 모듈 이름) |
| A/ | app/src/main/java/com/a207/runkk/app/ |
| W/ | wear/src/main/kotlin/com/a207/runkk/watch/ |

### 리뷰 기준

| 축 | 항목 | 기준 | 출처 |
|----|------|------|------|
| 1 설계 일치 | 모듈·클래스·시그니처 이름 | 설계서와 다르면 불일치(코드가 더 낫다면 "설계서 갱신 요청") | design-oo 13.2 마지막 문단 |
| 1 | 의존 방향 | 5.1.2 의존 표 밖 화살표 0, domain은 android·retrofit2·room·hilt import 0, feature 사이 직접 의존 0(record·runimal에서 share는 예외) | design-oo 5.1.2, 14장 Konsist |
| 2 코딩 스타일 | 매개변수 | 4개 이하. Composable은 modifier·콜백 람다를 빼고 센다 | 기본 규칙 |
| 2 | 중첩 / 함수 길이 / 이름 | 3단계 이하 / 50줄 이하 / Boolean은 is·has·can, 문자열 하드코딩 금지, Dispatchers 주입, 로그는 Timber | 기본 규칙, 팀 컨벤션 1·3장 |
| 3 구조 | 예외 흐름 제어, 반복 안 return | 예외를 정상 분기에 쓰지 않는다. 반복 한가운데 return 금지 | 기본 규칙 |
| 4 OO·Kotlin | 가변 상태 노출, 전역 가변 상태 | MutableStateFlow 공개 금지, object 안 var 금지 | 13.2 클래스 속성 규칙 |
| 4 | 코루틴 안전 | GlobalScope·runBlocking·취소 무시·Dispatchers 하드코딩 | 팀 컨벤션 3장 |
| 4 | Compose 안정성·수명주기 | collectAsStateWithLifecycle, Lazy 목록 key, Context 누수 없음 | Android 관례 |
| 5 보안 | 토큰·키 로그, 시험 전용 경로, 평문 HTTP | 로그에 토큰 없음, localDebug 코드는 다른 변형에 없음, 평문은 local 플레이버 10.0.2.2만 | AGENTS.md, 설계 D-61 |
| 6 형상 관리 | 커밋·브랜치 | 제목 `{타입}: {내용} [{Jira 키}]`, 브랜치 `feature/{Jira 키}-{작업명}` | AGENTS.md |
| 복잡도 | 순환 복잡도 | 10 이하(약산) | 기본 규칙 |

등급: 치명(goto 등가, 내용 결합도, 전역 가변 상태 공유, 설계에 없는 모듈 간 양방향 의존) / 중 / 경. 판정: 치명 0·중 3 이하 합격, 중 4~10 조건부, 치명 1+ 또는 중 11+ 불합격.

언어 평가: 1차(2026-10-20)에서 수행했다. 이번에는 다시 하지 않는다.

### 1차 지적 추적표 (중 8, 경 24)

1차 결함은 32건(중 8, 경 24)이다. 결과: 해결 24, 일부 3, 미해결 5. 중 8건은 7건 해결, 1건 일부(F-4)다.

| 1차 ID | 등급 | 1차 내용 | 상태 | 확인 근거 (현재 코드) |
|--------|------|----------|------|-----------------------|
| F-1 | 중 | Closing 상태를 진행 메시지가 덮음 | 해결 | D/battle/BattleRunCoordinator.kt:346-354 updateRunning이 Closing·Finished를 그대로 둠. D/crew/CrewRunCoordinator.kt:207-212 Countdown·Finished 보존 |
| F-2 | 중 | 대결 진동 설정 무시 | 해결 | D/run/RunTracker.kt:221-230 onLeadChanged가 battleVoice·battleVibration 적용, 조정자는 D/battle/BattleRunCoordinator.kt:399-407에서 위임 |
| F-3 | 중 | 서버 보고 실패 무시 | 해결 | D/run/CoordinatedRound.kt:79-88 report(재시도 2회), D/battle/BattleRunCoordinator.kt:272-278 failure 흐름과 화면 안내, D/crew/CrewRunCoordinator.kt:113-114 |
| F-4 | 중 | 늦은 심박 append 실패 무시·전송 경쟁 | 일부 | D/run/RunTracker.kt:243-267 재시도·재보관, D/sync/UploadPendingRunsUseCase.kt:31-37 이미 저장된 기록의 심박 전송까지는 구현. 그러나 설계 v0.10의 LateHeartRates 경로(머리 정보 없는 기록의 늦은 심박 보존)가 없다. 새 결함 M-1 |
| S-1 | 경 | RunTracker 생성자 주입 9개 | 미해결 | D/run/RunTracker.kt:39-49 여전히 9개(포트 6 + SettingsRepository + Clock + CoroutineScope) |
| S-2 | 중 | 두 조정자 중복 블록 | 해결 | D/run/CoordinatedRound.kt로 범위·측정 여부·보고·진행값 중복 제거, 두 조정자가 사용 |
| S-3 | 경 | 파일당 공개 타입 여러 개 | 일부 | GuidanceRule이 파일 7개로 분리됨. D/account/AccountValues.kt(8), D/crew/CrewRepository.kt(19), D/live/ServerMessage.kt(12), D/record/RecordModels.kt(12) 등 9개 파일은 여전히 여러 타입 |
| S-4 | 경 | Boolean 이름 is/has | 미해결 | D/run/Guidance.kt:6 `val voice: Boolean`, D/run/RunProgress.kt:18 `val locked: Boolean`, D/run/WatchDisplay.kt:12 `val running: Boolean`. private var는 isScheduled·isMeasuring으로 고쳐짐 |
| S-5 | 경 | 도메인의 문구 하드코딩 | 미해결 | D/run/RunTracker.kt:299 `"러닝 기록을 기기에 저장하지 못했어요"` 등 그대로. 새 결함 M-3에 합침 |
| S-6 | 경 | HomeScreen 문자열 하드코딩 | 미해결(확대) | F/home/HomeScreen.kt:294 `text = "자유런 시작",` 등. 앱 전체로 퍼짐. 새 결함 M-3에 합침 |
| S-7 | 경 | Log.e를 Timber로 | 해결 | A/AppScopeModule.kt:25 Timber.e |
| S-8 | 경 | lateinit var created | 해결 | D/run/CoordinatedRound.kt:31 val scope를 init에서 만듦. main 도메인에 lateinit 0 |
| S-9 | 경 | Dispatchers.Default 대체값 | 해결 | D/run/CoordinatedRound.kt:40 requireNotNull(... as? CoroutineDispatcher) |
| S-10 | 경 | ServerMessage when의 else | 해결 | D/battle/BattleRunCoordinator.kt:300-302, D/crew/CrewRunCoordinator.kt:197-199 갈래 명시 |
| S-11 | 경 | late 섀도잉 | 해결 | D/run/RunTracker.kt:304-319 lateBatch·lateSamples |
| S-12 | 경 | a·b·r 변수명 | 해결 | D/battle/OpponentTrack.kt:32-34 before·after·ratio |
| S-13 | 경 | 도착 판정 두 곳 | 해결 | D/battle/BattleRunCoordinator.kt:499 RecordedOpponentFeed.hasArrived가 gapTracker.hasArrived 사용 |
| S-14 | 경 | core.model에 업무 상수 | 해결(설계서 반영) | 코드는 그대로(core/model/.../AppError.kt:25-32). 설계 5.1.2 v0.8 CR-U21이 "여러 기능이 함께 내는 사유만 core.model"로 정해 결함이 아니게 됨 |
| S-15 | 경 | LoginUseCase.signUp | 해결 | D/account/LoginUseCase.kt:24-31 SignUpUseCase 분리 |
| S-16 | 경 | onProgress와 구독 이중 처리 | 일부 | D/run/CoordinatedRound.kt:63-67 accept가 경과 시간이 늘 때만 받아 이중 처리는 막음. 공개 onProgress는 남음(D/battle/BattleRunCoordinator.kt:144, D/crew/CrewRunCoordinator.kt:94) |
| S-17 | 경 | Finished 뒤 Idle 없음 | 해결 | D/battle/BattleRunCoordinator.kt:234-237, D/crew/CrewRunCoordinator.kt:152-155 acknowledgeFinished |
| S-18 | 경 | 종료 때 워치 통지 누락 | 해결 | D/run/RunTracker.kt:278-282 complete가 watch.publish(homeDisplay()) |
| S-19 | 경 | 시각 차 0일 때 1 s/km | 해결 | D/run/RunSession.kt 현재 페이스 계산이 `!toAt.isAfter(fromAt)`이면 null |
| S-20 | 경 | WatchDisplayMessage 기본값 없음 | 해결 | wear-contract/.../WatchDisplayMessage.kt:20 `= false` |
| P-1 | 중 | save 반복 안 return | 해결 | D/run/RunTracker.kt:288-301 while 조건 하나로 출구 |
| P-2 | 중 | SyncDisposalsUseCase 반복 안 return | 해결 | D/sync/SyncDisposalsUseCase.kt:24-25 while 조건과 apply 분리 |
| O-1 | 중 | openRun 직렬 불변식 위반 | 해결 | D/run/CoordinatedRound.kt:32,70-72 AtomicBoolean compareAndSet |
| O-2 | 경 | onLateHeartRates 공개 | 미해결 | D/run/RunTracker.kt:243 `fun onLateHeartRates(...)` 여전히 public(내부 호출만 있음, 설계 06-06:107도 "내부") |
| O-3 | 경 | BattleRun var 7개 | 해결 | D/battle/BattleRunCoordinator.kt:445-472 private set과 schedule 전이 연산 |
| O-4 | 경 | RunSession 상태 값으로 분기 | 해결 | D/run/RunTracker.kt:161,172 tryLock·tryUnlock, D/run/RunSession.kt:120-134 |
| O-5 | 경 | 상대 종류 null 분기 | 해결 | D/battle/BattleRunCoordinator.kt:476-500 OpponentFeed 인터페이스 |
| O-6 | 경 | 연결 실패와 정상 종료 구분 불가 | 해결 | D/battle/BattleRunCoordinator.kt:75-81 failure StateFlow, fail()이 연결 실패를 채움 |
| 7절 | 참고 | BattleRunCoordinator.handle 복잡도 12 | 해결 | handle이 갈래별 함수로 나뉨(D/battle/BattleRunCoordinator.kt:287-344). 지금 최대 약 6 |
| 집계 외 | - | local 평문 HTTP에 networkSecurityConfig 없음 | 해결 | data/src/local/AndroidManifest.xml과 data/src/local/res/xml/network_security_config.xml: 10.0.2.2·localhost만 평문, develop·prod는 https/wss만(data/build.gradle.kts:34-43) |
| 5절 | - | 형상 관리: Jira 키 없음 | 미해결 | 최근 android 커밋 40개 중 Jira 키가 있는 것 0개. 브랜치도 feature/runkk-impl 그대로. 새 결함 N-14 |

### 1. 설계 일치

#### 1.1 의존 방향(코드 import로 센 결과 vs 5.1.2 표)

| 모듈 | 실제 import 대상 | 5.1.2 표 | 결과 |
|------|------------------|----------|------|
| domain.run / sync / account / battle / crew | settings·core.model / run / run·settings / run·live / run·live (모두 core.model 포함) | 같음 | 일치 |
| api.account, api.run, api.battle, api.record·crew·runimal·avatar·notification | api.core + 같은 domain.기능(api.run은 domain.sync·run, api.battle은 domain.live, api.account는 domain.settings) | 같음 | 일치 |
| data.store, device, sync, settings, watch, live, push, session | domain.run / run / run·sync / settings / run·settings·wear.contract / live·api.core·api.live / notification / account·notification·api.core | 같음 | 일치 |
| ui.home, run, record, battle, crew, avatar, me, notification, runimal, share | 표의 부분집합(ui.record·runimal에서 ui.share 포함) | 같음 | 일치 |
| app | domain.sync, account, settings, notification, core.navigation, core.designsystem, core.model | 같음 | 일치(LoginState 사용은 1.3절 갱신 요청 3) |
| watch.link / watch.ui | wear.contract·watch.heart / watch.link | 같음 | 일치 |

Gradle 선언: feature 모듈은 :feature:share(record·runimal만)와 testFixtures(:core:ui) 외 프로젝트 의존 선언이 없다. :data는 :domain·:core:model·:wear-contract, :wear는 :wear-contract, :domain은 :core:model(api). 순환 0, 설계에 없는 화살표 0, 양방향 의존 0. feature·core.ui·app·domain·wear main 소스에 api.·data. 패키지 import는 0건이다.

#### 1.2 이름·시그니처 대조(설계 v0.10)

| 설계서 항목 | 코드 | 일치 | 비고 |
|-------------|------|------|------|
| 5.1.2 domain·api·data·ui 패키지 | 코드 패키지 전부 존재 | 예 | api.live는 단순 인터페이스 |
| wear.contract에 EndRequestMessage | wear-contract/.../EndRequestMessage.kt | 예 | |
| RunTracker.onLateHeartRates "내부" | D/run/RunTracker.kt:243 public | 아니오 | 1차 O-2 미해결 |
| LateHeartRates·pendingLateHeartRates·removeLateHeartRates·uploadLateHeartRates (D-77·D-80) | 0건 | 아니오 | 새 결함 M-1 |
| CrewRunCoordinator.onLobbyEntered(postId, distance, myName) | `onLobbyEntered(postId, distance)` D/crew/CrewRunCoordinator.kt:74 | 아니오 | 코드가 더 낫다(CrewRunRelay.Entry.me로 본인 식별). 1.3절 갱신 요청 2 |
| RunkkApp이 시작 작업을 부른다 | A/RunkkApp.kt가 AppStartup.start()를 부른다 | 아니오 | 1.3절 갱신 요청 3 |
| app은 domain.account SessionRepository.loggedIn 구독 | A/AppStartup.kt가 LoginState(domain.notification)를 구독 | 아니오 | 1.3절 갱신 요청 3 |
| RunTracker·조정자 Clock은 data.device DeviceBindings가 제공(D-61) | DT/data/device/DeviceBindings.kt:32 `Clock.systemUTC()` | 예 | 1차 갱신 요청 3 반영됨 |
| SignUpUseCase 분리 | D/account/LoginUseCase.kt:24 | 예 | |
| 화면용 도메인 모델은 View 접미어 제거(D-78) | D/record/RecordModels.kt | 예 | |

#### 1.3 설계서 갱신 요청 (코드가 낫거나 설계서가 비어 있음, `/sw-design-oo` 재호출 목록)

| No | 구현 결정 | 판정 | 설계서 고칠 곳 |
|----|-----------|------|----------------|
| 1 | D/run/CoordinatedRound(internal): 대결·크루런 조정자 공용 "한 회" 기반(직렬 범위, AtomicBoolean 측정 여부, 진행값 중복 거름, 서버 보고 재시도) | 갱신 요청. 1차 S-2·O-1의 교정이라 설계서에 없다. 같은 모듈 안 internal이라 의존 화살표는 늘지 않는다 | 5.1.2 domain.run 포함 클래스, 6.1.14 조정자 의존 |
| 2 | CrewRunCoordinator.onLobbyEntered에서 myName을 빼고 본인 줄은 CrewRunRelay.Entry.me로 찾음 | 갱신 요청. 표시명 비교는 동명이인일 때 남의 순위를 안내하는 결함이었다(1차 갱신 요청 15) | 06-06:211 onLobbyEntered 시그니처, 5.7.2 CrewRunRelay.Entry |
| 3 | app에 AppStartup(시작 작업·로그인 전환 구독)이 있고 RunkkApp은 호출만 한다. 로그인 상태는 LoginState(domain.notification)로 구독 | 갱신 요청. Application에 로직을 두지 않아 시험이 쉽다. app에서 domain.notification으로 가는 화살표는 표에 이미 있다 | 5.1.2 app 행, 06-06:162·184·221·253의 "RunkkApp이 부른다" 문구 |
| 4 | BattleRunCoordinator·CrewRunCoordinator의 acknowledgeFinished·failure, BattleRunCoordinator.abandonMatching, RunTracker.publishWatchHome | 갱신 요청. 1차 S-17·F-3·O-6 교정과 매칭 이탈 취소(FR-085)가 설계서 연산 표에 없다(onLeadChanged만 있음) | 6.1.14 조정자·RunTracker 연산 표 |
| 5 | E2E 시험 경로: :app의 localDebug 소스셋(E2eBuildConfigModule, E2eKakaoTokenSource), 멀티바인딩 KakaoTokenOverride, 빌드 속성 RUNKK_E2E_KAKAO_TOKEN·RUNKK_LOCAL_API_URL·RUNKK_LOCAL_WS_URL, 접근성 리소스 ID(testTagsAsResourceId) | 갱신 요청. 시험 전용 코드가 release에 섞이지 않는 조건(변형 이름 localDebug 한정)과 로컬 평문 주소 범위를 13장 빌드 변형·보안 절에 적어야 한다. N-1·N-3은 코드 쪽 교정 | 13장 빌드·플레이버, 14장 시험 전략 |
| 6 | KakaoAccessTokenSource.requiresNativeAppKey(키 없는 대체 창구만 false) | 갱신 요청 | 06-06 ui.me SocialLoginClient 행 |
| 7 | DT/api/core의 SelfMember, FaceImageSource(계정별 얼굴 부위 캐시, 크기 상한, 로그아웃 때 비움) | 갱신 요청. 설계서에 포함 클래스로 없다 | 5.1.2 api.core·api.avatar 행 |
| 8 | 폰 앱 문구 정책: 현재 한글 리터럴 직접 사용(1,634줄) | 팀이 한국어 단일 출시로 확정하면 컨벤션 3장에 "화면 문구는 Compose에서 직접 허용, 다국어 때 일괄 추출"을 적고 M-3을 닫는다. 아니면 M-3대로 코드를 고친다. 워치(wear)는 이미 리소스를 쓰므로 어느 쪽이든 폰·워치 규칙을 통일할 것 | 팀 코드 컨벤션 3장 |

### 2. 새 결함

치명 0건. goto 등가물(라벨 break·continue, while(true) 다중 break), 내용·공통 결합, object 안 var 같은 전역 가변 상태, 설계에 없는 모듈 간 화살표(코드 import와 Gradle 선언 모두)는 찾지 못했다. 확인한 근거는 1.1·1.2절에 있다.

#### 2.1 치명

없음.

#### 2.2 중 (4건)

| ID | 파일:줄 | 현재 | 교정 예시 | 근거 |
|----|---------|------|-----------|------|
| M-1 | D/run/RunTracker.kt:263-269 와 DT/data/store/RoomPendingRunRepository.kt:125 | `appended.value ?: return Outcome.Failure(AppError.Rejected(RECORD_NOT_FOUND))` (보관함이 머리 정보 없는 기록의 늦은 심박을 거부하고, RunTracker는 Rejected가 아닌 실패만 재시도하므로 그 심박을 버린다) | `appended.value ?: run { dao.upsertLateHeartRates(recordId, samples); true }` 로 심박만 담은 LateHeartRates 항목을 남기고, UploadPendingRunsUseCase.invoke 끝에 `uploadLateHeartRates()` 결과를 이어 붙인다 | 설계 v0.10 D-77·D-80(TF-AN-16·18·19)과 06-06:107·138·156-157이 정한 LateHeartRates·pendingLateHeartRates·removeLateHeartRates·uploadLateHeartRates가 코드에 0건(grep 0). FR-043(늦은 심박 보존) 데이터 손실. 1차 F-4의 남은 부분 |
| M-2 | F/record/RecordDetailScreen.kt:107 외 55개 | `private fun DetailBody(detail: RecordDetail) {` 가 151줄 | 구역별 Composable로 쪼갠다: `DetailBody` 안을 `SummarySection(detail)`, `PaceSection(detail)`, `HeartRateSection(detail)`로 나눠 각 50줄 이하 | 50줄 초과 함수 56개가 전부 Composable(feature 48, core.ui 6, wear 2). 비 Composable은 0개. 100줄 초과 5개: RecordDetailScreen.DetailBody 151줄, CrewFindScreens.kt:152 CrewFindScreen 136줄, CrewHomeScreens.kt:707 CrewManageScreen 136줄, AnalysisScreens.kt:268 RankingScreen 122줄, RunResultScreen.kt:139 ResultBody 111줄. 1차는 "50줄 초과 0개"였다(화면 단계가 새로 들어옴) |
| M-3 | F/home/HomeScreen.kt:294 외 약 1,630줄 | `text = "자유런 시작",` | `text = stringResource(R.string.home_start_free_run),` (도메인은 D/run/RunTracker.kt:299의 `Guidance.screenOnly(Guidance.Kind.SAVE_FAILED, "러닝 기록을 ...")`를 messageKey 열거형으로 바꾸고 data.device가 문자열 리소스로 바꾼다) | 한글 문자열 리터럴이 있는 줄이 feature·core.ui·domain·data·wear main에 1,634줄. 폰 앱 strings.xml은 app_name 1개(app/src/main/res/values/strings.xml), wear만 리소스 20개 사용. 팀 컨벤션 3장 "문자열 하드코딩 금지" 위반이 1차(S-5·S-6, 경)보다 크게 늘었다. 한국어 단일 출시로 확정이면 컨벤션을 고쳐 닫을 수 있다(1.3절 갱신 요청 8) |
| M-4 | F/battle/MatchViewModel.kt:156 | `if (connected is Outcome.Failure) throw ChannelConnectFailed(connected.error)` (자식 코루틴이 예상된 연결 실패를 예외로 던져 first 대기를 끊고, 바깥 catch 164행의 `e: Exception`이 받아 failWaiting으로 분기한다) | `val failures = Channel<AppError>(1)`; `launch { (opened.connect() as? Outcome.Failure)?.let { failures.trySend(it.error) } }`; `val outcome = merge(opened.messages.filter(::isMatchResult), failures.receiveAsFlow()).first()` 뒤 `when (outcome)`으로 분기 | 구조적 프로그래밍 기준 "예외를 정상 분기 수단으로 쓰지 않는다". 같은 catch가 프로그래밍 오류(IllegalState 등)까지 failWaiting으로 삼킨다(TooGenericExceptionCaught 억제 표시) |

#### 2.3 경 (새 결함 14건)

| ID | 파일:줄 | 현재 | 교정 예시 |
|----|---------|------|-----------|
| N-1 | A/MainActivity.kt:44 | `modifier = Modifier.semantics { testTagsAsResourceId = true }` (release에도 켜져 testTag가 접근성·UI Automator에 리소스 ID로 노출됨) | `Modifier.semantics { testTagsAsResourceId = BuildConfig.DEBUG }` |
| N-2 | app/build.gradle.kts:41 | `isMinifyEnabled = false` (CI가 서명하는 prod release가 난독화·축소 없이 나감) | `isMinifyEnabled = true` 와 Kakao·Hilt·kotlinx.serialization 보존 규칙을 proguard-rules.pro에 추가 |
| N-3 | app/src/localDebug/java/com/a207/runkk/app/ui/me/E2eKakaoTokenSource.kt:1 | `package com.a207.runkk.app.ui.me` (:app 모듈 파일이 feature:me 패키지를 흉내 내 import 없이 KakaoAccessTokenSource를 씀. Konsist "app은 ui.* import 0" 규칙에 걸리지 않고 같은 패키지가 두 모듈에 갈라진다) | `package com.a207.runkk.app`으로 바꾸고 `import com.a207.runkk.app.ui.me.KakaoAccessTokenSource`를 명시한 뒤 ArchitectureTest에 localDebug 한 곳만 예외로 적는다(또는 토큰 창구를 feature/me/src/debug로 옮긴다) |
| N-4 | app/build.gradle.kts:64 | `buildConfigField("String", "API_BASE_URL", ...)` 값 http://10.0.2.2:8080/ (app 코드는 BuildConfig.API_BASE_URL을 읽지 않는다. data의 값은 .../api/v1/ 이고 RUNKK_LOCAL_API_URL로 덮여 두 값이 다르다) | app/build.gradle.kts:63-71의 productFlavors 3개 API_BASE_URL 줄을 지우고 주소는 data/build.gradle.kts 한 곳에서만 관리 |
| N-5 | DT/data/watch/WatchLink.kt:222 | `while (isActive) {` 안에서 `quietly { refreshNodes() }` 와 `delay(NODE_POLL_MILLIS)` (프로세스가 살아 있는 동안 5초마다 connectedNodes를 계속 조회) | `Wearable.getCapabilityClient(context).addListener({ refreshNodes() }, WATCH_CAPABILITY)`로 바뀔 때만 갱신하고 시작 때 한 번 조회 |
| N-6 | DT/data/watch/WatchLink.kt:279 | `} catch (e: Exception) {` 뒤 주석만 있고 기록 없음(ApiException 외 프로그래밍 오류도 소리 없이 사라짐) | `} catch (e: Exception) { Timber.d(e, "워치 통신 실패") }` (data 모듈 build.gradle.kts에 libs.timber 추가) |
| N-7 | F/share/.../ShareTargets.kt:49 외 CardSharer.kt:35 | `val target = runCatching { resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) }.getOrNull()` (실패 원인 기록 없음, 같은 파일 51·68·87·108행도 같다) | `try { resolver.insert(...) } catch (e: SecurityException) { Timber.w(e, "기기 저장 실패"); null }`처럼 잡을 예외를 좁히고 기록 |
| N-8 | DT/api/core/FaceImageSource.kt:109 | `throw PartFailed(ApiErrorMapper.fromHttp(response.code, response.body.string()))` (예상된 HTTP 오류를 예외로 올려 69행 catch가 Outcome으로 바꿈. 형제 async 취소 목적이 있어 M-4보다 정당하다) | fetch가 `Outcome<ByteArray>`를 돌려주고 loadLayers가 `results.firstOrNull { it is Outcome.Failure }`로 분기 |
| N-9 | D/battle/BattleRunCoordinator.kt:393-397 | `} catch (e: CancellationException) { null }` (awaitOrNull이 호출 코루틴의 취소까지 null로 바꿔 삼킴. 결과적으로 return@launch로 끝나 동작은 맞지만 규칙 위반) | `} catch (e: CancellationException) { currentCoroutineContext().ensureActive(); null }` |
| N-10 | DT/data/session/EncryptedTokenStore.kt:62 외 4곳 | `override suspend fun save(tokens: SessionTokens): Outcome<Unit> = withContext(Dispatchers.IO) {` (다른 곳: F/share/.../CardSharer.kt:34·45, F/avatar/.../AvatarViewModels.kt:78 Dispatchers.Default, F/record/.../RecordViewModels.kt:217) | 생성자에서 `ioDispatcher: CoroutineDispatcher`를 받고 `withContext(ioDispatcher)`. F/runimal/.../RunimalViewModel.kt:90의 보조 생성자 방식을 따라도 된다 |
| N-11 | W/link/WatchModule.kt:42 | `fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)` (폰의 A/AppScopeModule.kt:25-26은 CoroutineExceptionHandler를 달았는데 워치는 없어 자식 코루틴의 미처리 예외가 워치 프로세스를 죽인다) | `CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Timber.e(e, "워치 작업 실패") })` |
| N-12 | F/crew/CrewFindViewModels.kt:101 | `val firstFind = _state.value.crews.isEmpty() && _state.value.error == null` (refreshMine(E2E 수정)이 화면 복귀 때마다 loadMine(quiet)을 부르고, 공개 크루가 정말 0개이면 firstFind가 늘 참이라 복귀 때마다 search·recommend를 다시 보낸다) | `val firstFind = !_state.value.findLoaded`처럼 "한 번 읽었는가" 플래그를 두고 읽은 뒤 true로 바꾼다 |
| N-13 | F/crew/CrewFindScreens.kt:416 | `items(crew.tags.toList()) { RunkkBadge(RunkkTags.label(it)) }` (key 없음) | `items(crew.tags.toList(), key = { it }) { ... }` |
| N-14 | 형상 관리(커밋·브랜치) | 최근 android 40개 커밋 제목에 Jira 키 0개, 브랜치 feature/runkk-impl. 예: `57437be test: 에뮬레이터 스모크 E2E(Maestro 흐름 7개, 카카오 스텁, 실행 스크립트)` | `test: 에뮬레이터 스모크 E2E(Maestro 흐름 7개) [S15P31A207-nn]`. 이미 올린 커밋은 다시 쓰지 말고 MR 제목·병합 커밋에 키를 단다. AGENTS.md는 브랜치도 `feature/{Jira 키}-{작업명}`을 요구 |

1차에서 넘어온 미해결·일부 경(추적표 번호 그대로, 새 결함 아님): S-1(RunTracker 주입 9개), S-3(여러 타입 파일 9개), S-4(Boolean 이름), S-16(공개 onProgress), O-2(onLateHeartRates public). 교정 예시는 1차 문서 그대로이고 S-5·S-6은 M-3에 합쳤다.

#### 2.4 확인했고 문제 없는 것

- 보안: 요청·응답 본문 로깅 없음(DT/api/core/ApiCoreBindings.kt:24 주석, HttpLoggingInterceptor 0건). Timber는 A/RunkkApp.kt:19에서 debug 빌드만 DebugTree를 심고, 토큰을 찍는 줄은 0개다. 평문 HTTP는 local 플레이버의 10.0.2.2·localhost만이다. 토큰 저장은 EncryptedTokenStore. 매니페스트 exported는 런처 MainActivity, Kakao 콜백 AuthCodeHandlerActivity, 워치 PhoneMessageService(메시지 수신에 필요)뿐이고 서비스·FileProvider는 false다.
- E2E 시험 경로: 토큰 창구 코드(src/localDebug)와 BuildConfig.E2E_KAKAO_TOKEN은 onVariants(selector().withName("localDebug"))에만 들어가고(app/build.gradle.kts:75-86), 다른 변형에는 코드도 값도 없다. 토큰이 비면 Kakao SDK를 그대로 쓰고, 대체 창구가 둘 이상이면 check로 막는다(feature/me .../SocialLoginClient.kt chooseKakaoTokenSource). E2eLoginPathTest가 있다. 다만 N-1(testTag 노출)·N-3(패키지 흉내)은 따로 남는다.
- 설계 일치: 코드 import로 센 패키지 의존이 5.1.2 표와 일치(4절). feature 사이 import는 record·runimal에서 share로만(설계 예외). domain에서 android·androidx·retrofit·okhttp·dagger import 0(javax.inject만).
- 코루틴·상태: GlobalScope 0, `!!` 0, MutableStateFlow 공개 0(전부 private과 asStateFlow), object 안 var 0. runBlocking은 OkHttp Authenticator 스레드의 DT/api/core/TokenAuthenticator.kt:50 한 곳뿐이고 코루틴 밖이라는 이유가 주석에 있다. `catch (e: Exception)` 앞에는 CancellationException 재던짐이 있다(N-9 제외).
- E2E 수정 커밋: PlayServicesTransport는 outbox·nodeIds를 init보다 먼저 선언해 초기화 순서 결함을 고쳤다(WatchLink.kt:204-211). CrewHomeScreens의 NaN은 분모를 `coerceAtLeast(1)`로, 결과를 `coerceIn(0, PERCENT)`로 막았다(CrewHomeScreens.kt:360-365). RunResultViewModel은 applyServer로 중복을 줄였고 load는 31줄이다. CrewFindViewModel.refreshMine은 `s.mine == null && !s.checkingMine`일 때만 LifecycleResumeEffect에서 부른다(수명주기 안전, N-12만 남음).

### 3. 구조적 프로그래밍: 순환 복잡도 10 초과 함수

약산 방식: 조건·반복·catch·논리 연산자·엘비스·when 갈래 + 1, 람다 인자 화살표 제외. 총 22개(비 Composable 9, Composable 13). 1차의 유일한 초과 함수(BattleRunCoordinator.handle 12)는 해소됐다.

| 순위 | 복잡도 | 줄 | 함수 | 파일:줄 | 비고 |
|------|--------|-----|------|---------|------|
| 1 | 17 | 50 | ServerMessageDecoder.decode | DT/data/live/ServerMessageDecoder.kt:33 | 메시지 종류별 when. 종류마다 decodeXxx 함수로 나누면 각 3 이하 |
| 2 | 17 | 74 | ResultBody (Composable) | F/crew/CrewRunScreens.kt:578 | |
| 3 | 16 | 23 | verdictMessage | F/battle/BattleResultViewModels.kt:44 | 평평한 when(결과 문구). 표 조회로 바꾸면 줄어듦 |
| 4 | 16 | 98 | CrewHomeScreen (Composable) | F/crew/CrewHomeScreens.kt:168 | |
| 5 | 15 | 81 | HistoryList (Composable) | F/battle/BattleResultScreens.kt:348 | |
| 6 | 15 | 136 | CrewFindScreen (Composable) | F/crew/CrewFindScreens.kt:152 | |
| 7 | 14 | 71 | CrewRunPost (Composable) | F/crew/CrewHomeScreens.kt:474 | |
| 8 | 14 | 70 | CrewRunScreen (Composable) | F/crew/CrewRunScreens.kt:254 | |
| 9 | 13 | 23 | withSettingChanged | DT/api/account/AccountMappers.kt:114 | 설정 키별 when |
| 10 | 13 | 21 | toSnapshot | DT/data/settings/DataStoreSettingsRepository.kt:120 | 키별 기본값 엘비스 연쇄 |
| 11 | 13 | 31 | onLoadMore | F/crew/CrewHomeViewModels.kt:254 | |
| 12 | 12 | 46 | RunkkSegmentedTabs (Composable) | core/ui/.../designsystem/RunkkChips.kt:296 | |
| 13 | 12 | 41 | Gap (Composable) | F/battle/BattleRunningScreen.kt:338 | |
| 14 | 12 | 136 | CrewManageScreen (Composable) | F/crew/CrewHomeScreens.kt:707 | |
| 15 | 12 | 70 | RelayBody (Composable) | F/crew/CrewRunScreens.kt:448 | |
| 16 | 12 | 82 | RecordCardScreen (Composable) | F/record/RecordCardScreen.kt:62 | |
| 17 | 12 | 25 | RunViewModel.onProgress | F/run/RunViewModel.kt:220 | |
| 18 | 11 | 29 | UploadPendingRunsUseCase.invoke | D/sync/UploadPendingRunsUseCase.kt:18 | 1차 9에서 늘었고 설계 06-06:156의 V(G) 8보다 크다(alreadySaved를 32행과 40행에서 두 번 검사). M-1 구현 때 uploadLateHeartRates 분리와 함께 한 번 검사로 정리 |
| 19 | 11 | 85 | BattleRunningScreen (Composable) | F/battle/BattleRunningScreen.kt:122 | |
| 20 | 11 | 30 | BattleRunningViewModel.onCoordinator | F/battle/BattleRunningViewModel.kt:177 | |
| 21 | 11 | 66 | RecordBattleScaffold (Composable) | F/battle/RecordBattleScreens.kt:196 | |
| 22 | 11 | 34 | CrewHomeViewModels.refresh | F/crew/CrewHomeViewModels.kt:134 | |

domain 쪽 최대는 UploadPendingRunsUseCase.invoke 11이다. 매개변수는 비 Composable 5개 이상 0개, Composable에서 modifier·콜백 람다를 뺀 5개 이상은 3개(core/ui/.../RunkkCards.kt:124 RunkkStatTile 7개, RunkkCards.kt:209 RunkkBigNumber 5개, F/record/RecordListScreen.kt:349 run 5개)다. 값 묶음을 받게 하면 4개 이하가 되며 판정에는 넣지 않았다(스크립트의 modifier·콜백 제외 약산).

### 4. 형상 관리

| 활동 | 판정 | 근거 |
|------|------|------|
| 변경 통제 | 아니오 | 최근 android 커밋 40개 중 Jira 키 0개, 브랜치 `feature/runkk-impl` 그대로(N-14). 1차와 같은 상태 |

이번 리뷰는 변경 통제만 판정했고 나머지 4개 활동은 활동별 표를 따로 만들지 않았다.

### 5. 집계와 판정

| 등급 | 건수 | 항목 |
|------|------|------|
| 치명 | 0 | goto 등가·내용 결합·전역 가변 상태 공유·설계에 없는 양방향 의존 모두 없음 |
| 중 | 4 | M-1 늦은 심박 LateHeartRates 경로 미구현(설계 v0.10 불일치, 1차 F-4 일부), M-2 50줄 초과 Composable 56개, M-3 한글 문구 하드코딩 약 1,630줄(1차 S-5·S-6 확대), M-4 예외를 흐름 제어에 사용(MatchViewModel) |
| 경 | 19 | 새 결함 N-1~N-14(14건) + 1차 이월 5건(S-1, S-3, S-4, S-16, O-2) |

참고(판정 제외): 복잡도 10 초과 함수 22개(비 Composable 9), 설계서 갱신 요청 8건, 1차 32건 중 해결 24·일부 3·미해결 5.

판정: **조건부**

근거: 치명 0, 중 4(4 이상 10 이하), 설계 일치 점검 수행(의존 방향 위반 0), 형상 관리 변경 통제 1항목 아니오(N-14). 1차 중 8건은 7건이 해결돼 크게 줄었다. 새 중 3건 중 M-2·M-3은 화면 구현량에서 나왔고 M-4는 작은 수정이다. 합격 기준(중 3 이하)까지는 M-4만 고쳐도 중 3이 되어 합격 구간에 든다. M-1은 설계 반영 누락에 따른 데이터 손실이라 우선순위가 가장 높고, M-3은 1.3절 갱신 요청 8(컨벤션 확정)로 닫을 수 있다.

조치 상태(2026-10-21): 중 4건(M-1~M-4)과 경 19건(새 결함 N-1~N-14, 1차 이월 5건)은 현재 수정 중이다. MR 전체 판정은 [code-review.md](../code-review.md)의 2차 종합 절을 따른다.

### 6. sw-test 전달: 우선 테스트 대상

복잡도 10 초과 함수는 22개(비 Composable 9)이며 위 3절 표에 있다. 아래는 그중 domain·data의 함수와 이번 중 결함의 회귀 시험 대상이다.

| 함수·경로 | 약산 복잡도 | 이유 |
|-----------|-------------|------|
| ServerMessageDecoder.decode (DT/data/live/ServerMessageDecoder.kt:33) | 17 | 메시지 종류별 when. 종류마다 분기 경로를 검사한다 |
| verdictMessage (F/battle/BattleResultViewModels.kt:44) | 16 | 결과 문구 when. 결과 종류 전부 |
| UploadPendingRunsUseCase.invoke (D/sync/UploadPendingRunsUseCase.kt:18) | 11 | alreadySaved를 두 번 검사. M-1 구현 때 uploadLateHeartRates 분리와 함께 회귀 시험 |
| 늦은 심박 경로 (RunTracker.onLateHeartRates, RoomPendingRunRepository.appendHeartRates) | — | M-1: 머리 정보 없는 기록의 늦은 심박이 보존되는지, 설계 D-77·D-80 시험(TF-AN-16·18·19) |
| MatchViewModel 연결 실패 분기 (F/battle/MatchViewModel.kt:156) | — | M-4: 연결 실패와 프로그래밍 오류를 구분하는 회귀 시험 |

### 7. 다음 행동

- [ ] M-1: 설계 D-77·D-80대로 LateHeartRates 저장·전송 구현(PendingRunRepository 2개 연산 추가, UploadPendingRunsUseCase 분리). 담당 안드로이드 구현자
- [ ] M-4: MatchViewModel 연결 실패를 예외 없이 흐름으로 합치기
- [ ] M-3 결정: 한국어 단일 출시면 컨벤션 3장 개정(1.3절 8), 아니면 strings.xml 이관 일정 수립
- [ ] M-2: 100줄 초과 Composable 5개부터 구역별로 분리
- [ ] 경: N-1·N-2(release 보안 설정)는 prod 출시 전에, N-3·N-4·N-11은 다음 정리 커밋에
- [ ] 변경 통제: Jira 이슈 키 발급, MR 제목·병합 커밋에 키(N-14)
- [ ] sw-design-oo 재호출로 1.3절 갱신 요청 1~7 반영, 이어서 sw-test로 M-1·M-4 회귀 시험 추가


## 리뷰 2026-10-21 (3차)

대상 커밋: feature/runkk-impl 0009d29 (merge, 2차 cff0c6e, 2차 수정 병합 뒤 통합 브랜치). 2차 이후 android 커밋 32개(`git log --oneline 67a5ffa..0009d29 -- android`).

입력: S15P31A207-impl/android, sw-sdlc-skills docs/sw/design-oo.md v0.10, design-oo/05-01·05-02(D-77·D-80)·06-06, 2차 원자료와 위 2차 절.
방법: 읽기 전용(코드 수정·커밋·브랜치 전환·gradle 실행 없음). 코드를 직접 읽었고 함수 길이·복잡도·한글 리터럴·여러 타입 파일은 파이썬 스크립트로 쟀다. 주석·KDoc은 지우고 문자열 리터럴만 본 뒤 @Preview 함수와 Preview 이름 함수는 뺐다. 복잡도는 약산이다(조건·반복·catch·논리 연산자·엘비스·when 갈래 +1, else 갈래와 람다 화살표 제외, ±1 오차). 정적 확인에 android/app/build/outputs/mapping/prodRelease/(configuration.txt, seeds.txt, mapping.txt)를 읽었고 이번 리뷰에서 빌드는 하지 않았다. 시험도 실행하지 않았다(아래 판정은 코드 읽기 기준).

경로 줄임말(모두 S15P31A207-impl/android/ 아래): D/ = domain/src/main/kotlin/com/a207/runkk/app/domain/, DT/ = data/src/main/kotlin/com/a207/runkk/app/, F/m/ = feature/m/src/main/java/com/a207/runkk/app/ui/m/, A/ = app/src/main/java/com/a207/runkk/app/, W/ = wear/src/main/kotlin/com/a207/runkk/watch/.

### 리뷰 기준

| 축 | 항목 | 기준 | 출처 |
|----|------|------|------|
| 1 설계 일치 | 모듈·클래스·시그니처 | 설계서와 다르면 불일치(코드가 낫다면 "설계서 갱신 요청") | design-oo 13.2 마지막 문단 |
| 1 | 의존 방향 | 5.1.2 의존 표 밖 화살표 0, domain에 android·retrofit2·room·hilt import 0, feature 사이 직접 의존 0(record·runimal의 share 예외) | design-oo 5.1.2, 14장 Konsist |
| 2 코딩 스타일 | 매개변수 / 중첩 / 함수 길이 | 4개 이하(Composable은 modifier·콜백 제외) / 3단계 이하 / 50줄 이하 | 기본 규칙 |
| 2 | 이름·문구·로그·디스패처 | Boolean은 is·has·can, 화면 문구 하드코딩 금지, Dispatchers 주입, 로그는 Timber | 기본 규칙, 팀 컨벤션 1·3장 |
| 3 구조 | 예외 흐름 제어, 반복 안 return | 예외를 정상 분기에 쓰지 않는다. 반복 한가운데 return 금지 | 기본 규칙 |
| 4 OO·Kotlin | 가변 상태 노출, 전역 가변 상태, 코루틴 안전 | MutableStateFlow 공개·object 안 var·GlobalScope·runBlocking·취소 삼킴 금지 | 13.2, 팀 컨벤션 3장 |
| 4 | Compose·수명주기·Context | collectAsStateWithLifecycle, Lazy 목록 key, ViewModel에 Context 없음 | Android 관례 |
| 5 보안·출시 | 토큰·testTag·R8 | 로그에 토큰 없음, 시험 전용 경로는 localDebug만, release는 축소·난독화와 보존 규칙 | AGENTS.md, 설계 D-61 |
| 6 형상 관리 | 커밋·브랜치 | 제목 `{타입}: {내용} [{Jira 키}]`, 브랜치 `feature/{Jira 키}-{작업명}`, 커밋은 한 가지 일 | AGENTS.md |
| 복잡도 | 순환 복잡도 | 10 이하(약산) | 기본 규칙 |

등급: 치명(goto 등가, 내용 결합도, 전역 가변 상태 공유, 설계에 없는 양방향 의존) / 중(데이터 손실·설계 불일치·규칙의 광범위 위반) / 경. 판정: 치명 0·중 3 이하 합격, 중 4~10 조건부, 치명 1+ 또는 중 11+ 불합격.

### 대상

| 항목 | 내용 |
|------|------|
| 코드 | S15P31A207-impl/android, 브랜치 feature/runkk-impl, HEAD 0009d29(merge). 2차 이후 android 커밋 32개. 시험·build-logic·e2e를 뺀 Kotlin 334개 파일 34,064줄 |
| 설계서 | sw-sdlc-skills/docs/sw/design-oo.md v0.10, design-oo/05-01·05-02(D-77·D-80)·06-06 |
| 우선 확인 | 2차 중 4건(M-1~M-4)의 수정, Room 판 1→2 이전, 늦은 심박 전송의 멱등·삭제 조건, WaitEnd 분기의 취소 전파, UiText와 Context, R8 보존 규칙, 병합 충돌을 풀었던 feature/share |

### 2차 지적 추적표

결과: 2차 중 4건은 M-1·M-4 해결, M-2·M-3 일부(중 기준에서 내려가 경으로 이월). 경 19건은 해결 15, 일부 3(S-1·S-3·S-4), 미해결(보류) 1(N-14). 이월 항목 S-16·O-2는 해결이다.

| ID | 등급 | 2차 내용 | 상태 | 확인 근거 (현재 코드) |
|----|------|----------|------|-----------------------|
| M-1 | 중 | 머리 정보 없는 기록의 늦은 심박을 버림(LateHeartRates 미구현) | 해결 (구현에서 새 결함 X-1 발생) | D/run/LateHeartRates.kt:8, D/run/PendingRunRepository.kt:32-40(pendingLateHeartRates·removeLateHeartRates), DT/data/store/RoomPendingRunRepository.kt:130-144·158-180(머리 정보 없으면 mergeLateEntry), D/sync/UploadPendingRunsUseCase.kt:23-73(invoke 끝에 uploadLateHeartRates, RECORD_NOT_FOUND면 삭제), D/run/RunArchive.kt:44-60(Rejected 아닌 실패만 재시도). 시험: RoomPendingRunRepositoryTest.kt:167-204, SyncTest.kt:168 |
| M-2 | 중 | 50줄 초과 @Composable 56개 | 일부 | 직접 센 결과 50줄 초과 비 Composable 0, @Composable 2개: W/ui/WatchHomeScreen.kt:29-86(58줄), W/ui/WatchRunDisplay.kt:31-86(56줄). feature·core.ui·app은 0개(최대 49줄 F/me/MeScreen.kt:85). Composable 431개 중 최대 58줄. 스크린샷 png 변경 0건(`git diff --stat 67a5ffa..0009d29 -- android/**/*.png`)이라 분리가 화면 결과를 바꾸지 않은 정황. 잔여는 경 N3-2로 이월 |
| M-3 | 중 | 한글 문구 하드코딩 약 1,630줄 | 일부 | 주석·@Preview·시험을 뺀 한글 문자열 리터럴 93개. 그중 로그·예외 메시지(Timber·require·check·shareFailureOrNull 원인 등)가 약 74개이고 화면·알림에 보이는 것이 19개: A/RunkkNavHost.kt:134(2), A/RunkkAppContent.kt:59(1), DT/api/notification/NotificationRemotes.kt:43-58(13), DT/data/device/MeasurementService.kt:170-172(3). 정의된 문자열 1,036개, 미사용 0, 이름 중복 0(app_name은 폰·워치 별개 앱). 도메인 문구는 D/run/GuidanceMessage.kt·WatchNotice.kt 키로, 변환은 DT/data/device/GuidanceTexts.kt:15-33. 잔여는 경 N3-1로 이월 |
| M-4 | 중 | MatchViewModel이 연결 실패를 예외로 흐름 제어 | 해결 | F/battle/MatchViewModel.kt:184-195 awaitWaitEnd(sealed WaitEnd 값 반환, 넓은 catch 없음), :155-163 when 분기. 취소 전파는 4.3. messages가 SharedFlow(DT/data/live/WebSocketLiveChannel.kt:70)라 onCompletion의 Lost(null)은 시험용 채널에서만 도달 |
| N-1 | 경 | testTagsAsResourceId release 노출 | 해결 | A/MainActivity.kt:45 `testTagsAsResourceId = BuildConfig.DEBUG` |
| N-2 | 경 | isMinifyEnabled=false | 해결 | app/build.gradle.kts:42-44 isMinifyEnabled·isShrinkResources true, app/proguard-rules.pro 보존 규칙, prodRelease mapping 산출물 존재. 규칙별 점검은 4.5(누락 없음) |
| N-3 | 경 | E2E 토큰 창구가 ui.me 패키지를 흉내 | 해결 | app/src/localDebug/java/com/a207/runkk/app/E2eKakaoTokenSource.kt:1 package com.a207.runkk.app, :5-6 ui.me import 명시, app/src/test/java/com/a207/runkk/app/ArchitectureTest.kt:100-105·128-129 예외를 localDebug 한 파일로 한정 |
| N-4 | 경 | app의 쓰지 않는 API_BASE_URL | 해결 | app/build.gradle.kts:59-60(주소는 data 한 곳), :69 buildConfigField는 E2E 토큰 하나. data/build.gradle.kts:28-40 |
| N-5 | 경 | 워치 노드 5초 조회 | 해결 | DT/data/watch/WatchLink.kt:236-239 capabilities.addListener + 시작 때 한 번 조회, wear/src/main/res/values/wear.xml:3-5 능력 선언, 값 일치(WearPaths.kt:24 "runkk_watch") |
| N-6 | 경 | 워치 통신 예외 기록 없음 | 해결 | DT/data/watch/WatchLink.kt:303 `Timber.d(e, "워치 통신 실패")`, data/build.gradle.kts timber 의존 |
| N-7 | 경 | runCatching로 공유·저장 실패 삼킴 | 해결 (시험 부족은 N3-5) | F/share/.../ShareTargets.kt:138-157 shareFailureOrNull이 SecurityException·IOException·ActivityNotFoundException·IllegalArgumentException·IllegalStateException·UnsupportedOperationException만 잡고 Timber.w. CardSharer.kt:37 |
| N-8 | 경 | FaceImageSource 예외로 HTTP 오류 전달 | 해결 (map 안 return 1건은 N3-3) | DT/api/core/FaceImageSource.kt:62-83 loadLayers가 Outcome과 select로 첫 실패에서 나머지를 끊음, :113-141 download가 Outcome 반환, PartFailed 삭제 |
| N-9 | 경 | awaitOrNull이 호출 코루틴 취소를 삼킴 | 해결 | D/battle/BattleRunCoordinator.kt:402-407 `currentCoroutineContext().ensureActive()` 뒤 null |
| N-10 | 경 | Dispatchers.IO·Default 하드코딩 | 해결 | 생성자 매개변수: DT/data/session/EncryptedTokenStore.kt:36, F/share/.../CardSharer.kt:24, F/avatar/.../AvatarViewModels.kt:57, F/record/.../RecordViewModels.kt:216, F/runimal/.../RunimalViewModel.kt:97. 남은 직접 사용은 보조 생성자 기본값과 CoroutineScope 제공 2곳(A/AppScopeModule.kt:26, W/link/WatchModule.kt:47)뿐 |
| N-11 | 경 | 워치 applicationScope 예외 처리기 없음 | 해결 | W/link/WatchModule.kt:44-48 CoroutineExceptionHandler + Timber.e, W/ui/RunkkWatchApp.kt:24 debug만 DebugTree |
| N-12 | 경 | 공개 크루 0개일 때 복귀마다 찾기 재조회 | 해결 | F/crew/CrewFindViewModels.kt:83(hasRequestedFind), :106 `val firstFind = !hasRequestedFind`, :126 load()에서 true |
| N-13 | 경 | 태그 LazyRow key 없음 | 해결 | F/crew/CrewFindScreens.kt:459 `items(crew.tags.toList(), key = { it })`. main 소스 전체에서 key 없는 items 호출 0 |
| N-14 | 경 | 커밋 제목·브랜치에 Jira 키 없음 | 미해결(보류) | 2차 이후 커밋 32개 중 제목에 `[키]`가 있는 것 0개, 브랜치 feature/runkk-impl·feature/runkk-impl-android-strings. AGENTS.md:23·28 요구 위반이 이어짐 |
| S-1 | 경 | RunTracker 생성자 주입 9개 | 일부 | D/run/RunTracker.kt:34-46 이제 8개(포트 둘과 재시도 상태를 RunArchive로 묶음 D/run/RunArchive.kt:21-24). 4개 이하 기준에는 못 미침 |
| S-3 | 경 | 한 파일에 공개 타입 여러 개(domain 9개 파일) | 일부 | 17개 파일을 타입마다 한 파일로 나눔(커밋 6afa354). domain에 남은 것은 D/live/ServerMessage.kt(12, sealed 계층)와 D/account/LoginUseCase.kt(2)뿐이며 커밋 메시지가 사유를 밝혔다. 설계서 갱신 요청 8로 허용 규칙을 정하면 닫힌다 |
| S-4 | 경 | Boolean 이름 is·has | 일부 | Guidance.voice는 hasVoice·hasVibration(D/run/Guidance.kt:11-12), WatchDisplay.running은 isRunning(D/run/WatchDisplay.kt:12)으로 해결. 보류: D/run/RunProgress.kt:18 `val locked: Boolean`, D/battle/MyProgress.kt:20 `val locked: Boolean`. 같은 규칙을 어기는 domain·core.model·wear-contract Boolean 속성이 35개(예 D/avatar/Shop.kt:13 `val owned`)라 N3-6으로 이월 |
| S-16 | 경 | 공개 onProgress | 해결 | D/battle/BattleRunCoordinator.kt:150·D/crew/CrewRunCoordinator.kt:97 `internal fun onProgress`, 운영 경로는 D/run/CoordinatedRound.kt:54-58의 구독 하나, 모듈 밖 호출 0 |
| O-2 | 경 | onLateHeartRates public | 해결 | D/run/RunTracker.kt:239 `private fun onLateHeartRates` |

### 1. 설계 일치

#### 1.1 의존 방향 (5.1.2 표, 코드 import로 센 결과)

- feature·core.ui·app·domain·wear main의 import에서 data·api 패키지 참조는 0건, domain의 android·retrofit·room·hilt import 0건(javax.inject만). Gradle 선언은 2차와 같다.
- feature 사이 직접 의존은 record·runimal에서 share로만 있다(설계 예외). :app의 localDebug 한 파일이 ui.me를 import하는 것은 ArchitectureTest.kt:100-105가 한 곳으로 한정한다.

#### 1.2 설계서 갱신 요청 (코드가 낫거나 설계서가 비어 있음, `/sw-design-oo` 재호출 목록)

로직 쪽이 먼저 보고한 10건과 겹치는 것은 묶었다. 설계서가 낡은 것이지 코드 결함이 아니다. 조각 파일에서는 표 번호(1~12)를 쓰고, 종합 결과서에서는 CR34-12~CR34-23으로 옮긴다. 2차 갱신 요청 8(한글 리터럴 허용안)은 코드가 리소스 방식으로 확정돼 폐기한다(아래 2번).

| No | 구현 결정 | 판정 | 설계서 고칠 곳 |
|----|-----------|------|----------------|
| 1 | D/run/RunArchive(보관·늦은 심박 재시도 협력 객체, RunTracker 생성자 9에서 8). appendHeartRates는 RunArchive만 부른다 | 갱신 요청 (로직 쪽 보고와 같음) | 5.1.2 domain.run 포함 클래스, 06-06 RunTracker 표, D-77의 "RunTracker가 보관함을 부른다" 문구 |
| 2 | 도메인 문구를 키로: GuidanceMessage·WatchNotice, Guidance(kind, message, args, hasVoice, hasVibration), 변환은 data.device GuidanceTexts·data.watch가 문자열 리소스로. 폰 화면 문구는 strings.xml과 core.ui UiText(Res·Plain)로 | 갱신 요청 (로직·문구 쪽 보고 통합). 2차 갱신 요청 8(한글 리터럴 허용안)은 폐기: 코드가 리소스 방식으로 확정됨 | 06-06 Guidance·WatchDisplay 행, 5.1.2 core.ui(UiText), 팀 컨벤션 3장 문구 규칙 |
| 3 | Room 판 2: late_heart_rate 표(LateHeartRateEntity·LateHeartRateDao), AutoMigration 1에서 2, keepLatestSent 30건 | 갱신 요청 | 06-06 data.store 행·13.2 저장, D-77 |
| 4 | BattleRunCoordinator·CrewRunCoordinator.onProgress는 internal(운영 경로는 CoordinatedRound의 측정 구독 하나). RunTracker.onLateHeartRates는 private | 갱신 요청 (로직 쪽 보고와 같음) | 06-06:97·107·203 |
| 5 | WearPaths.WATCH_CAPABILITY("runkk_watch")와 워치 res/values/wear.xml의 능력 선언, 폰은 능력 변화 리스너로 연결을 안다(5초 조회 폐지) | 갱신 요청 | 06-06 data.watch·wear.contract 행 |
| 6 | 출시 빌드: R8(isMinifyEnabled·isShrinkResources)과 proguard-rules.pro 보존 규칙, testTagsAsResourceId는 debug만, E2E 토큰 창구는 localDebug 한정(app 패키지, ui.me 창구 자리 명시 import) | 갱신 요청 (R8·testTags·E2E 패키지 통합) | 13장 빌드 변형·보안, 14장 시험 전략 |
| 7 | ArchitectureTest의 예외: localDebug E2eKakaoTokenSource가 ui.me 창구 자리 2개를 import | 갱신 요청 | 14장 Konsist 규칙 |
| 8 | 한 파일 허용 규칙: sealed 계층(ServerMessage)과 private 함수를 공유하는 짝 UseCase(LoginUseCase·SignUpUseCase)는 한 파일, 그 밖은 타입마다 한 파일 | 갱신 요청 (로직 쪽 보고와 같음). S-3 일부를 닫는다 | 팀 컨벤션 1장, 14장 |
| 9 | 늦은 심박 항목 삭제를 "읽은 내용과 같을 때만"으로(X-1 교정 시): `removeLateHeartRates(recordId, sent)` | 설계 변경 요청. X-1을 고치면 D-80의 연산 시그니처가 바뀐다 | D-80, 06-06:157, PendingRunRepository 표 |
| 10 | MatchViewModel의 WaitEnd(Found·Failed·Lost) sealed 분기와 연결 실패의 값 전달, FaceImageSource.loadLayers의 Outcome·select 구조 | 갱신 요청 (WaitEnd 보고와 같음, FaceImageSource는 5.1.2 api.core 행에 함께) | 06-06 ui.battle MatchViewModel 행, 5.1.2 api.core |
| 11 | Timber 범위: 폰(A/RunkkApp)·워치(W/ui/RunkkWatchApp) debug만 DebugTree, 워치 applicationScope 예외 처리기, 공유·워치 통신 실패 기록 | 갱신 요청 (Timber 범위 보고와 같음) | 팀 컨벤션 3장 로그, 06-06 워치 행 |
| 12 | ui.share 시그니처: ShareOutcome.Failed·Fallback이 문자열 리소스 번호(messageRes), RunCardRenderer(ApplicationContext), CardSharer의 ioDispatcher·uriFor, shareFailureOrNull | 갱신 요청 | 06-06 ui.share 행 |

### 2. 새 결함

치명 0건. goto 등가물(라벨 break·continue), 내용·공통 결합, object 안 var, 설계에 없는 모듈 간 화살표는 찾지 못했다.

#### 2.1 중 (1건)

| ID | 파일:줄 | 현재 코드 | 교정 예시 | 근거 |
|----|---------|-----------|-----------|------|
| X-1 | DT/data/store/RoomPendingRunRepository.kt:144 와 D/sync/UploadPendingRunsUseCase.kt:67 | `override suspend fun removeLateHeartRates(recordId: String): Outcome<Unit> = storage { lateDao.delete(recordId) }` | 읽은 심박과 같을 때만 지운다. DAO에 `@Query("DELETE FROM late_heart_rate WHERE recordId = :recordId AND heartRates = :sent") suspend fun deleteIfSame(recordId: String, sent: ByteArray)`를 두고 UseCase가 `removeLateHeartRates(entry.recordId, entry.samples)`로 부른다 | 늦은 심박 항목을 읽어(UploadPendingRunsUseCase.kt:55-62) 서버로 보내는 동안(네트워크, 수 초) 같은 기록의 다음 늦은 심박 묶음이 mergeLateEntry(RoomPendingRunRepository.kt:174-179)로 같은 행에 합쳐지면, 전송이 끝난 뒤 recordId만 보고 지워 합쳐진 새 심박이 서버에 간 적 없이 사라진다. 워치가 끊겼다 돌아오면 묶음이 연달아 오고 appendHeartRates가 매번 전송 요청(requestUploadQuietly)을 하므로 겹칠 가능성이 크다. 미전송 기록 쪽은 같은 경쟁을 handedOutHeartRates(RoomPendingRunRepository.kt:54·85)로 막아 두었으나 항목 쪽에는 없다. FR-043·NFR-032(동기화 누락 0건) 위반이다. 설계 D-80도 `removeLateHeartRates(recordId)`로 정해 설계서 갱신 요청 9에 묶었다. 이 경쟁을 겨냥한 시험 없음(RoomPendingRunRepositoryTest.kt:187-204는 순차 호출만 본다) |

#### 2.2 경 (새 결함 7건과 Composable 매개변수)

| ID | 파일:줄 | 현재 코드 | 교정 예시 |
|----|---------|-----------|-----------|
| N3-1 (M-3 잔여) | A/RunkkNavHost.kt:134, A/RunkkAppContent.kt:59, DT/api/notification/NotificationRemotes.kt:43-58, DT/data/device/MeasurementService.kt:170-172, F/crew/CrewHomeScreens.kt:817 | `RunkkEmptyState(title = "준비 중인 화면이에요", message = "곧 만나요")` / `contentDescription = "런크크 시작 중"` / `"PLAN_MISSED" to "계획 미이행",` (알림함 제목 12개와 기본 제목 "알림") / `private const val NOTIFICATION_TITLE = "러닝 측정 중"` (채널 이름·제목·본문 3개) / `internal fun isLeader(member: CrewMembers.Member): Boolean = member.role.uppercase() == "LEADER" &#124;&#124; member.role == "크루장"` | 화면은 `RunkkEmptyState(title = stringResource(R.string.app_coming_soon_title), ...)`, 서비스 알림은 `context.getString(R.string.measure_notification_title)`. 알림함 제목은 data가 type만 주고 화면이 `UiText.Res(InboxTitles.of(type))`로 바꾸게 한다. 마지막 줄은 서버 역할 값이 "LEADER" 하나이면 `member.role.uppercase() == "LEADER"`만 남긴다 |
| N3-2 (M-2 잔여) | W/ui/WatchHomeScreen.kt:29 (58줄), W/ui/WatchRunDisplay.kt:31 (56줄) | `fun WatchHomeScreen(state: WatchUiState, onStart: () -> Unit, onRequestPermission: () -> Unit, modifier: Modifier = Modifier) {` (매개변수는 줄바꿈해 적혀 있음) | 시작 버튼을 `edgeButton = { StartEdgeButton(state, onStart) }`로, 목록 본문을 `HomeItems(state, onRequestPermission)`로 빼 50줄 이하로 만든다. WatchRunDisplay도 구역별로 나눈다 |
| N3-3 | DT/api/core/FaceImageSource.kt:86 | `is Outcome.Failure -> return result` (`FacePart.entries.zip(results).map { ... }` 반복 안 return) | map 앞에서 `results.filterIsInstance<Outcome.Failure>().firstOrNull()?.let { return it }`로 실패를 먼저 걸러 내고 map 안은 `FaceLayer(part, (result as Outcome.Success).value)`만 둔다 |
| N3-4 | F/battle/MatchViewModel.kt:205-207 | `val text = if (error ==` 다음 줄 `AppError.Unauthorized` 다음 줄 `) {` (서식기가 조건식을 세 줄로 끊어 놓음) | `val isAuthLost = error == AppError.Unauthorized` 를 앞 줄에 두고 `val text = if (isAuthLost) BattleErrorText.of(error) else UiText.Res(R.string.battle_match_connection_lost)` |
| N3-5 | F/share/src/test/.../ShareTest.kt (실패 시험 없음), F/share/.../ShareTargets.kt:138 | `internal fun <T> shareFailureOrNull(what: String, block: () -> T): T? = try {` (6가지 예외 갈래와 DeviceSaveTarget의 반쯤 쓴 항목 지우기 :76 모두 시험 없음. ShareTest.kt:124는 인스타그램 없음 갈래 하나) | `@Test fun 저장중_SecurityException이면_null() { assertNull(shareFailureOrNull("x") { throw SecurityException() }) }` 와 DeviceSaveTarget에 가짜 ContentResolver를 넣은 실패 시험 |
| N3-6 (S-4 이월) | D/run/RunProgress.kt:18, D/battle/MyProgress.kt:20, D/avatar/Shop.kt:13 외 32개 | `val locked: Boolean,` / `val owned: Boolean,` / `val newRecord: Boolean,` | `val isLocked: Boolean,` / `val isOwned: Boolean,` / `val isNewRecord: Boolean,` (서버 필드 이름은 data 매퍼에서만 맞춘다). RunProgress·MyProgress는 2차에서 보류로 보고됨 |
| N3-7 | 형상 관리: 커밋 f372c8f, 384525f, 92948b6, 26a0aed, 6ca691a 등 화면별 refactor 커밋 10개 | `refactor: 홈 화면 문구를 문자열 리소스로 옮기고 긴 Composable을 나눈다` (한 커밋에 문구 이전과 구조 분리 두 가지 일) | 앞으로는 `refactor: 홈 화면 문구를 문자열 리소스로 옮긴다 [키]`와 `refactor: 홈 화면 긴 Composable을 나눈다 [키]`로 나눈다. 이미 올린 커밋은 다시 쓰지 말고 MR 설명에 두 일이 섞였다고 적는다 |

Composable 매개변수(modifier·콜백 제외) 5개 이상 3건도 경이다(2차 3건 가운데 RecordListScreen의 run은 미리보기용 도우미라 뺐고, 분리하며 1건이 새로 생겼다): core/ui/src/main/java/com/a207/runkk/app/core/designsystem/RunkkCards.kt:124 `fun RunkkStatTile(` (8개), RunkkCards.kt:210 `fun RunkkBigNumber(` (5개), F/crew/CrewHomeScreens.kt:674 `private fun CrewRunEntryButtons(post: PostList.Item, label: String, canEnter: Boolean, finished: Boolean, actions: CrewHomeActions)`(5개). 교정은 canEnter·finished를 `entry: CrewRunEntryState` 값 하나로 묶는다(나머지 둘은 `StatTileStyle` 값으로 묶는다).

#### 2.3 점검 요청 항목별 결과

**점검 1. Room 1에서 2로 이전 안전성: 문제 없음**

- DT/data/store/PendingRunDatabase.kt:12-17 version 2, `autoMigrations = [AutoMigration(from = 1, to = 2)]`, exportSchema true. data/schemas/.../1.json과 2.json을 비교한 결과 pending_run 엔티티는 동일하고 late_heart_rate 표(recordId 기본키, heartRates BLOB, savedAtMillis)만 늘었다. 표 추가는 자동 이전으로 충분하다.
- DT/data/store/StoreBindings.kt:23 `Room.databaseBuilder(...).build()`에 fallbackToDestructiveMigration이 없어 이전 실패가 데이터 삭제로 번지지 않는다.
- 시험: data/src/test/.../PendingRunDatabaseMigrationTest.kt가 1판 행을 넣고 2판 이전 뒤 행 보존과 새 표 존재를 본다. data/build.gradle.kts:51-53이 schemas를 시험 자산으로 넣는다.
- 부수 변경: PendingRunDao.keepLatestSent(30건)가 이전의 deleteSentBefore(하루)를 대신한다(설계 D-77 일치). 기존 설치의 보낸 머리 정보 행은 다음 remove 때 30건으로 정리된다.

**점검 2. 늦은 심박 전송의 멱등·삭제 조건: 중 1건(X-1), 나머지는 설계대로**

- 삭제 조건: 서버 성공 또는 Rejected(RECORD_NOT_FOUND)만 지운다(UploadPendingRunsUseCase.kt:65-73, 설계 D-80 일치). 그 밖의 실패는 항목을 남기고 Failed를 더하며 UploadWorker가 재시도를 예약한다(DT/data/sync/UploadWorker.kt:28-40, Rejected만 남으면 예약하지 않음).
- 같은 심박 재전송: 전송 성공 뒤 지우기 실패나 프로세스 종료로 다시 보내면 같은 samples가 간다. 서버가 같은 시각 심박을 한 번만 두는지는 서버 코드를 이번 범위에서 보지 않아 확인하지 못했다(백엔드 리뷰 대상).
- 항목 쓰기는 merge(`distinctBy { it.at }`, RoomPendingRunRepository.kt:182-183)로 같은 시각을 한 번만 둔다. 미전송 기록 쪽 remove는 handedOut으로 읽은 뒤 덧붙은 심박을 남긴다(:84-110).
- 정리: purgeHeartRates·clear는 트랜잭션 안에서 항목 표도 지운다(:117-122, :147-152).

**점검 3. WaitEnd 분기의 취소 전파: 문제 없음**

F/battle/MatchViewModel.kt:184-195 awaitWaitEnd는 coroutineScope로 연결 코루틴과 `merge(...).first()`를 묶고, 끝나면 `connecting.cancel()`로 거둔다. CancellationException을 잡는 곳이 없어 viewModelScope.launch(waitJob, :153)가 취소되면 first()에서 그대로 올라간다. 연결 실패는 `lost.trySend(WaitEnd.Lost(error))`로 값이 되어 :155-163 when에서 failWaiting으로 간다. 이전 코드의 넓은 catch(Exception)이 삼키던 프로그래밍 오류는 이제 launch 밖으로 올라간다(의도에 맞음). MatchViewModel 시험은 직접 확인하지 못했다.

**점검 4. UiText가 ViewModel에 Context를 끌어들이는가: 아니오**

core/ui/.../designsystem/UiText.kt는 문자열 리소스 번호와 인자만 담는 sealed 값(Res·Plain)이고, Context는 `asString(context)`와 Composable `asString()`에서만 받는다. feature ViewModel에서 Context를 import하는 곳은 F/me/LoginViewModel.kt:3·47(`onProviderTapped(provider, context)`) 하나뿐이다. 2차 전부터 있던 것이고 상태에 저장하지 않으며 signIn 호출 동안만 쓰므로 UiText 도입으로 늘지 않았다. ViewModel은 `UiText.Res(R.string.x)`와 `uiText(R.string.x, arg)`로 문구를 만든다. 문자열 서식 자리표시자(숫자형) 75개의 인자 형식 일치는 린트 실행 없이는 확인하지 못했다. 퍼센트 기호가 들어 있는 문구 중 인자 없이 읽는 것은 guidance_pace_slow 하나이고 formatted="false"로 처리돼 있다(DT/data/device/GuidanceTexts.kt:10-13).

**점검 5. R8 보존 규칙 누락 가능성: 누락을 찾지 못함(정적)**

점검 근거는 prodRelease/configuration.txt와 seeds.txt다. 실행 시험은 하지 않았다.

| 대상 | 규칙·근거 | 결과 |
|------|-----------|------|
| kotlinx.serialization | app/proguard-rules.pro:11-17(Companion·INSTANCE·serializer 함수와 생성된 serializer 클래스 보존, 대상 com.a207.runkk 아래 전부). wear-contract의 @Serializable 4개도 같은 패키지 접두어라 포함 | 이상 없음 |
| Retrofit 인터페이스 9개 | proguard-rules.pro:20-25(Signature·어노테이션 속성, Call·Response·Continuation, api 패키지의 @retrofit2.http 메서드) | 이상 없음 |
| Hilt | proguard-rules.pro:28 HiltViewModel 클래스 이름 보존, configuration.txt:137-154에 ViewModel 보존 반영. 진입점은 라이브러리 규칙 | 이상 없음 |
| Room | proguard-rules.pro:31 RoomDatabase 생성자 보존, 라이브러리 규칙 | 이상 없음 |
| Kakao SDK·지도 | proguard-rules.pro:34-44가 공식 안내와 같다(model 필드, Gson TypeAdapter, dontwarn 3개, vectormap) | 이상 없음 |
| enum valueOf 사용처(RunMode·CardKind·UploadState, 실시간 JSON enum 4곳: DT/data/store/PendingRunMapper.kt:49-59, DT/data/live/LiveMessages.kt:119-167) | configuration.txt:46 AGP 기본 enum values·valueOf 보존 규칙, seeds.txt:4282·8470에 UploadState values·valueOf | 이상 없음 |
| WorkManager | configuration.txt:459 ListenableWorker 하위 클래스 이름 보존(UploadWorker는 기본 WorkerFactory가 이름으로 만든다) | 이상 없음 |
| Tink(EncryptedTokenStore) | configuration.txt:1524-1540 tink-android 1.8.0 규칙 포함 | 이상 없음 |

참고: 워치(:wear)는 release 축소를 켜지 않았다(wear/build.gradle.kts에 buildTypes 없음). CI는 워치 release를 만들지 않아(.gitlab-ci.yml:244 develop debug만) 지금은 결함이 아니다. 워치를 출시하기 전에 같은 규칙을 정해야 한다. e2e/verify_release.sh는 저장소 루트 e2e/에 있다(README.md:21).

**점검 6. 병합 충돌을 풀었던 feature/share: 이상 없음, 시험 보강만 필요(N3-5)**

- CardSharer.kt: @VisibleForTesting 기본 생성자(ioDispatcher 기본값, uriFor)와 @Inject 보조 생성자가 한 클래스에 있어 Hilt 생성자 주입은 한 개다. ShareTest.kt:97·107 `CardSharer(context, FakeSettings(...), uriFor = ::fileUri)`가 새 매개변수 순서와 맞는다.
- ShareTargets.kt: 세 ShareTarget이 모두 shareFailureOrNull을 거치고 ShareOutcome.Failed와 Fallback이 문자열 리소스 번호로 바뀐 것이 사용처(F/record, F/runimal)와 맞는다. RunCardRenderer가 ApplicationContext를 받는 것과 시험(ShareTest.kt:81 `RunCardRenderer(context)`, CardScreenshotTest.kt:52·61)이 맞는다.
- ShareTest.kt: 충돌 흔적이나 중복 정의 없음. 다만 실패 갈래 시험이 없다(N3-5).

### 3. 구조적 프로그래밍: 순환 복잡도 10 초과 함수

2차 22개에서 8개로 줄었다(비 Composable 7, Composable 1). 약산이라 ±1 오차가 있다.

| 순위 | 복잡도 | 줄 | 함수 | 파일:줄 | 비고 |
|------|--------|-----|------|---------|------|
| 1 | 17 | 50 | ServerMessageDecoder.decode | DT/data/live/ServerMessageDecoder.kt:33 | 2차 1위 그대로. 종류별 decodeXxx 함수로 나누면 각 3 이하 |
| 2 | 16 | 17 | GuidanceMessage.textRes | DT/data/device/GuidanceTexts.kt:16 | 새로 생김. 키에서 리소스로 가는 when 15갈래. 표 조회(EnumMap)로 바꾸면 1 |
| 3 | 14 | 24 | verdictMessage | F/battle/BattleResultViewModels.kt:47 | 2차 16에서 줄었으나 여전함. 표 조회로 |
| 4 | 13 | 23 | withSettingChanged | DT/api/account/AccountMappers.kt:114 | 설정 키별 when |
| 5 | 13 | 21 | toSnapshot | DT/data/settings/DataStoreSettingsRepository.kt:120 | 키별 기본값 엘비스 연쇄 |
| 6 | 12 | 46 | RunkkSegmentedTabs (Composable) | core/ui/src/main/java/com/a207/runkk/app/core/designsystem/RunkkChips.kt:297 | 2차의 나머지 Composable 12개는 분리로 10 이하가 됨 |
| 7 | 11 | 30 | RunViewModel.onProgress | F/run/RunViewModel.kt:223 | |
| 8 | 11 | 34 | CrewHomeViewModel.refresh | F/crew/CrewHomeViewModels.kt:142 | |

경계(else 갈래까지 세면 11 이상): BattleRunningScreen.Gap(F/battle/BattleRunningScreen.kt:384, 12), BattleResultScreens.VerdictCard(:207, 11), BattleRunningViewModel.onCoordinator(:182, 11), CrewHomeViewModel.onLoadMore(:262, 11), RecordListScreen.DayCell(:284, 11). UploadPendingRunsUseCase.invoke는 2차 11에서 3으로 내려갔다(설계 06-06:156의 8 이하).

### 4. 형상 관리

| 활동 | 판정 | 근거 |
|------|------|------|
| 변경 통제 | 아니오 | 2차 이후 커밋 32개 중 제목에 `[키]`가 있는 것 0개, 브랜치 feature/runkk-impl·feature/runkk-impl-android-strings(N-14). N3-7: 화면별 refactor 커밋 10개가 문구 이전과 구조 분리 두 가지 일을 한 커밋에 담음. 사용자 결정으로 판정 요인에서 제외 |

변경 통제만 판정했고 나머지 4개 활동은 활동별 표를 따로 만들지 않았다.

### 5. 집계와 판정

| 등급 | 건수 | 항목 |
|------|------|------|
| 치명 | 0 | goto 등가·내용 결합·전역 가변 상태·설계에 없는 양방향 의존 없음 |
| 중 | 1 | X-1 늦은 심박 항목 무조건 삭제(전송 중 합쳐진 심박 유실) |
| 경 | 11 | 새 결함 N3-1(문구 잔여, M-3 이월), N3-2(워치 Composable 2개 50줄 초과, M-2 이월), N3-3(map 안 return), N3-4(끊긴 조건식), N3-5(공유 실패 시험 없음), N3-6(Boolean 이름 35개, S-4 이월), N3-7(커밋에 두 가지 일), Composable 매개변수 5개 이상 3건을 한 건으로, 이월 S-1(RunTracker 8개), S-3(domain 2개 파일), N-14(Jira 키, 보류) |

참고(판정 제외): 복잡도 10 초과 8개(2차 22개), 설계서 갱신 요청 12건(통합 후), 2차 추적 결과 중 4건은 해결 2·일부 2, 경 19건은 해결 15·일부 3·미해결 1.

등급 조정 사유: 2차 M-2(56개)와 M-3(약 1,630줄)은 각각 2개와 19개로 줄어 규칙 위반의 광범위함이 사라졌으므로 경으로 내렸다. 잔여를 중으로 유지해 센다면 중 3건(X-1, M-2 잔여, M-3 잔여)이며 그래도 합격 구간이다.

판정: **합격**

근거: 치명 0, 중 1(3 이하), 설계 일치 점검 수행(의존 방향 위반 0), 형상 관리 변경 통제 1항목 "아니오"(N-14, 보류로 보고됨). 2차 중 M-1·M-4가 해결됐고 R8 규칙과 Room 이전은 점검에서 문제가 없었다. 다만 합격은 시험 실행 결과를 확인하지 못한 상태의 코드 읽기 판정이다.

조치 상태(2026-10-21): 중 X-1과 경 새 결함·이월분은 수정 후 통합 브랜치 d562d26에 반영됐고 [4차 절](#리뷰-2026-10-21-4차)에서 X-1·N3-1~N3-5와 Composable 매개변수는 해결, N3-6·S-1·S-3·N-14·N3-7은 결정 대기로 확인했다. MR 전체 판정은 [code-review.md](../code-review.md)의 3차 종합 절을 따른다.

### 6. sw-test 전달: 우선 테스트 대상

복잡도 10 초과 함수 8개는 위 3절 표에 있다. 아래는 이번 중 결함과 시험이 없던 경로의 시험 대상이다.

| 함수·경로 | 약산 복잡도 | 이유 |
|-----------|-------------|------|
| RoomPendingRunRepository.removeLateHeartRates, mergeLateEntry | — | X-1: 전송 중 같은 기록에 새 심박이 합쳐진 경우 보낸 것만 지우고 남은 것을 유지(동시 합침) |
| ServerMessageDecoder.decode | 17 | 메시지 종류별 when 분기 경로 |
| verdictMessage | 14 | 결과 종류별 문구 when |
| shareFailureOrNull, DeviceSaveTarget | — | N3-5: 6가지 예외 갈래와 반쯤 쓴 항목 지우기 실패 시험 |
| MatchViewModel 연결 실패 분기 | — | M-4 회귀: 연결 실패(WaitEnd.Lost)와 프로그래밍 오류 구분 |
| 실제 기기 release 빌드(prodRelease) | — | R8 규칙은 정적으로만 확인함. 로그인·러닝 저장·푸시 수신 1회 실행 시험 |

### 7. 다음 행동

- [x] X-1: 늦은 심박 항목 삭제를 읽은 내용과 같을 때만으로 바꾸고 동시 합침 시험을 추가한다(설계 D-80 갱신 요청 9와 함께). 담당 안드로이드 구현자
- [x] N3-1·N3-2: 남은 한글 문구 19개를 리소스로, 워치 Composable 2개를 50줄 이하로
- [ ] N-14·N3-7: MR 제목과 병합 커밋에 Jira 키를 단다. 앞으로 커밋은 한 가지 일로
- [ ] sw-design-oo 재호출로 6절 갱신 요청 1~12 반영
- [ ] assembleProdRelease 산출물로 실행 시험(로그인·러닝 저장·푸시 수신) 1회: R8 규칙은 정적으로만 확인함

(위 체크 목록은 3차 리뷰 시점 원문이다. X-1·N3-1·N3-2는 4차에서 해결로 확인했다. N-14·N3-7은 결정 대기, assembleProdRelease 실행 시험은 통합 d562d26에서 에뮬레이터 E2E와 release APK 점검으로 일부 확인했으나 실제 기기 실행은 아직이다.)


## 리뷰 2026-10-21 (4차)

대상 커밋: feature/runkk-impl d562d26 (3차 0009d29, 3차 수정 병합 뒤 통합 브랜치). 범위는 `git diff 0009d29..d562d26` 중 안드로이드 커밋 11개다(전체 164파일 +1,966 −804, 커밋 32개).

입력: S15P31A207-impl/android, docs/sw/design-oo.md와 design-oo/ 하위, 3차 원자료와 위 3차 절.
방법: 읽기 전용. git log/diff/grep과 파일 읽기, 줄 수·매개변수 약산 스크립트만 썼다. 리뷰 자체는 코드 읽기 기준이다. 같은 통합 브랜치의 실행 검증(android 전체 게이트, 에뮬레이터 스모크 E2E)은 [code-review.md](../code-review.md)의 4차 종합 절에 적었다. 순환 복잡도 10 초과 안드로이드 함수는 3차와 같이 참고로만 적고 집계에서 뺐다.

### 리뷰 기준

3차와 같다(위 3차 절 리뷰 기준). 이름 규칙에 "Boolean은 is·has·can, 화면 문구 하드코딩 금지, Dispatchers 주입, 로그는 Timber"를 그대로 적용했다.

### 대상

| 항목 | 내용 |
|------|------|
| 코드 | S15P31A207-impl/android, 브랜치 feature/runkk-impl-android-r3를 병합한 통합 d562d26 |
| 범위 | 3차 이후 안드로이드 커밋 11개(문구 이전 b784864, 워치 분리 1ceecec, Boolean 이름 7b9658d 등). 3차 해결 항목(M-1 등)이 되돌아간 곳은 없다(RunTracker·UploadPendingRunsUseCase·PendingRunDatabase 변경은 이름 바꿈과 X-1뿐) |

### 3차 지적 추적표

| ID | 3차 지적 | 근거(파일:줄) | 상태 |
|----|----------|---------------|------|
| X-1 | 늦은 심박 항목 무조건 삭제(전송 중 합쳐진 심박 유실) | data/store/RoomPendingRunRepository.kt:148-161 보낸 심박(측정 시각)만 빼고 남으면 upsert·없으면 delete를 한 트랜잭션에서, domain/run/PendingRunRepository.kt:43 `removeLateHeartRates(sent: LateHeartRates)`, domain/sync/UploadPendingRunsUseCase.kt:67 `removeLateHeartRates(entry)`. 합치는 쪽 appendInTransaction도 withTransaction이라 직렬화. 시험 RoomPendingRunRepositoryTest(+28줄) | 해결 |
| N3-1 | 남은 한글 문구 19개 | 주석·@Preview·로그·예외를 뺀 한글 리터럴 재스캔: 화면·알림에 보이는 것 0. 알림함 제목은 data/res/values/strings_notification.xml, api/notification/NotificationRemotes.kt:23 titleOf(context.getString), MeasurementService는 getString, crew 역할은 CrewRole(CrewMappers.kt:105) | 해결 |
| N3-2 | 워치 Composable 2개 50줄 초과 | wear/ui/WatchHomeScreen.kt:29-51(23줄), WatchRunDisplay.kt:31-61(31줄), 분리 함수 StartEdgeButton·homeItems·DistanceRow·PaceAndHeartRate | 해결 |
| N3-3 | map 안 return | data/api/core/FaceImageSource.kt:84-91 layersOf(실패 먼저 거르고 반복 밖 return) | 해결 |
| N3-4 | 끊긴 조건식 | feature/battle/MatchViewModel.kt:205-207 `val isAuthLost = error == AppError.Unauthorized` | 해결 |
| N3-5 | 공유·저장 실패 시험 없음 | feature/share/src/test/.../ShareFailureTest.kt 신규(@Test 7개) | 해결 |
| N3-6 | Boolean 이름 35개 | domain·core.model의 is·has 위반 0(재스캔). 보류분: wear-contract/WatchDisplayMessage.kt:13 `val running`, :20 `val heartRateCollectionAllowed`(전송 계약). 3차가 세지 않은 feature·wear 상태의 Boolean은 N4-1 | 일부, 결정 대기(wear-contract 2) |
| N3-7 | 한 커밋에 두 가지 일 | 이번 범위 안드로이드 11커밋은 각각 한 가지 일(문구 이전 b784864, 워치 분리 1ceecec, Boolean 이름 7b9658d 등). 3차 커밋의 이력은 다시 쓰지 않는 한 남는다 | 결정 대기 |
| Composable 매개변수 5+ 3건 | RunkkStatTile 8, RunkkBigNumber 5, CrewRunEntryButtons 5 | core/ui/.../RunkkCards.kt:137 `RunkkStatTile(label, value: StatTileValue, modifier, caption, style: StatTileStyle, onClick, footer)`(modifier·콜백 빼고 4), :219 `RunkkBigNumber(value, modifier, caption, style)`(3), feature/crew/CrewHomeScreens.kt:680 `CrewRunEntryButtons(post, label, entry, actions)`(4) | 해결 |
| S-1 | RunTracker 생성자 주입 8 | domain/run/RunTracker.kt:34-46 그대로 | 결정 대기 |
| S-3 | 한 파일 여러 타입(domain 2파일) | domain/live/ServerMessage.kt(sealed 계층), domain/account/LoginUseCase.kt | 결정 대기 |
| N-14 | 커밋·브랜치에 Jira 키 | 범위 안드로이드 커밋 11개 제목에 키 표기 0, 브랜치 feature/runkk-impl-android-r3 | 결정 대기 |
| 참고: 순환 복잡도 10 초과 8개 | 3차 5절(집계 제외) | GuidanceMessage.textRes 16 → 1 (data/device/GuidanceTexts.kt:16 표 조회로) 해결. 나머지 7개(ServerMessageDecoder.decode 17, verdictMessage 14, withSettingChanged 13, toSnapshot 13, RunkkSegmentedTabs 12, RunViewModel.onProgress 11, CrewHomeViewModel.refresh 11)는 이번 범위에서 이름만 바뀌어 그대로. 집계는 3차와 같게 제외(집계에 넣으면 경 +7) | 참고 |

3차 해결 항목(M-1 등)은 이번 범위에서 되돌아간 곳이 없다(RunTracker·UploadPendingRunsUseCase·PendingRunDatabase 변경은 이름 바꿈과 X-1뿐).

### 1. 설계 일치

새 화살표는 없다. 안드로이드는 domain에 android·data import 0, 모듈 의존 변경 0이다. NotificationRepositoryImpl이 data 모듈에서 ApplicationContext를 받는 것은 ViewModel·domain에 Context가 없다는 3차 점검을 지킨다. 설계서 갱신 요청은 8절에 있다.

### 2. 새 결함

치명 0, 중 0, 경 1이다.


| ID | 파일:줄 | 항목 | 현재 코드 한 줄 | 교정 예시 한 줄 | 등급 |
|----|---------|------|-----------------|-----------------|------|
| N4-1 | feature·wear의 상태 클래스 81곳(feature 70, wear 11). 예 feature/avatar/.../AvatarViewModels.kt:34, feature/battle/.../BattleRunningViewModel.kt:53, feature/battle/.../RecordBattleViewModels.kt:38, wear/.../WatchUiState.kt:8 | Boolean 이름 규칙(is·has·can). 3차 N3-6은 domain·core.model·wear-contract만 세어, 이번에 domain이 모두 바뀐 반면 화면 상태는 그대로라 한 값이 두 이름을 갖는다(`estimated = c.isEstimated`, `locked = mine.isLocked`). 서버 DTO(api/*Dtos.kt)는 전송 필드라 제외 | `val loading: Boolean = true,` | `val isLoading: Boolean = true,` (같은 모듈 안 이름이라 일괄 변경 가능. 한 번에 어렵다면 새로 만드는 Boolean부터 적용) | 경 |

점검했으나 결함 아님(통과):

| 점검 요청 | 결과 |
|-----------|------|
| removeLateHeartRates(sent) 트랜잭션 | 읽기(find)와 쓰기(upsert·delete)가 한 withTransaction이고 합치는 appendInTransaction도 withTransaction이라 Room이 직렬화한다. 합쳐진 새 심박은 보낸 시각 집합에 없는 것만 남기고 savedAtMillis를 보존해 purge 기준이 변하지 않는다. 같은 시각 표본이 전송본과 겹치면 이미 보낸 것이라 빠져도 손실 없음 |
| NotificationRepositoryImpl의 Context 주입 | 데이터 모듈의 저장소 구현에서 ApplicationContext를 받는다. ViewModel·domain에는 Context가 없고(domain 포트 Inbox.Item.title은 String) 3차 4.4의 ViewModel에 Context 없음을 지킨다. 같은 모듈의 ApiCoreBindings.kt:74·AndroidGuidanceOutput이 이미 쓰는 방식. 시험 AvatarAndRunimalAndNotificationTest가 생성자 갱신 |
| CrewRole 매핑의 모르는 값 | CrewMappers.kt:105는 모르는 이름이면 MEMBER. 서버 enum은 LEADER·MEMBER 둘뿐이고 ServerContractTest가 이름을 대조한다. 모르는 이름은 가장 낮은 권한으로 내려 크루장 UI가 잘못 열리지 않는 안전한 쪽(EnumNames.kt 계약 문구 기본값을 쓴다와 일치). 같은 파일 :49의 myRole은 null로 처리해 방식이 다르나 두 값의 의미가 달라(목록 줄 대 내 역할) 결함으로 보지 않음 |
| RunkkStatTile 시그니처 변경 | 호출처 전부 갱신(home·battle·crew·record·run·시험·미리보기). BigNumber의 unit·color 인자는 호출처가 없어(RunScreen.kt:416 한 곳) 제거. StatTileValue·StatTileStyle는 Immutable 표시라 리컴포지션 안정. 스크린샷 png 변경 0건 |
| GuidanceMessage 표 조회 | getValue는 키가 빠지면 런타임 예외이나 GuidanceTextsTest가 모든 GuidanceMessage·WatchNotice를 읽어 잡는다. 컴파일 보증이 시험 보증으로 바뀐 것은 복잡도 16에서 1로의 맞바꿈으로 수용 |
| 의존 방향 | 새 화살표 없음. crew가 common.PageCursor를 인터페이스에서도 import(이미 있던 화살표의 확대), reward에서 policy(D16 안), infra.crew에서 common, web.admin에서 operator 예외(D52). 안드로이드는 domain에 android·data import 0, 모듈 의존 변경 0 |

### 3. 구조적 프로그래밍

반복 안 return(N3-3)·끊긴 조건식(N3-4)은 해결됐다. 순환 복잡도 10 초과 8개 중 GuidanceMessage.textRes(16 → 1)가 해결됐고 나머지 7개는 이름만 바뀌어 그대로다(위 추적표의 참고 행). 집계는 3차와 같게 제외했다(넣으면 경 +7).

### 4. 형상 관리

| 활동 | 판정 | 근거 |
|------|------|------|
| 변경 통제 | 아니오 | 범위 안드로이드 커밋 11개 제목에 Jira 키 0, 브랜치 feature/runkk-impl-android-r3(N-14, 결정 대기). 이번 11커밋은 각각 한 가지 일을 했다(N3-7은 3차 커밋 이력이라 결정 대기). 사용자 결정으로 판정 요인에서 제외 |

### 5. 집계와 판정

| 구분 | 치명 | 중 | 경 | 구성 |
|------|------|----|----|------|
| 새 결함 | 0 | 0 | 1 | N4-1 |
| 3차 잔여 | 0 | 0 | 5 | 결정 대기 5(S-1·S-3·N-14·N3-7·N3-6 잔여 wear-contract 2) |
| 합계 | 0 | 0 | 6 | 3차 중 X-1 해결, 경 11 중 6 해결·5 결정 대기 |

판정: **합격**
근거: 치명 0, 중 0. X-1 해결, 워치·문구·매개변수 정리 해결. 경 6 중 5는 결정 대기다. 변경 통제(Jira 키)는 사용자 결정으로 판정 요인에서 뺐다.

조치 상태(2026-10-21): N4-1은 87fe682~90d5d9d(8커밋)에서 해결(이름 약 105개, wear-contract·서버 DTO·Room·DataStore·프레임워크 이름은 제외). 통합 928c829(검증: backend 884, ai 214, android 824 + 게이트, release 3종 시험 전용 코드 0건, compose, E2E 7/7). 결정 대기 5건은 [code-review.md](../code-review.md)의 4차 종합 절 "결정 대기 13건" 표에 모았다. MR 전체 판정은 종합 4차 절을 따른다.

### 6. sw-test 전달: 우선 테스트 대상

| 함수·경로 | 약산 복잡도 | 이유 |
|-----------|-------------|------|
| RoomPendingRunRepository.removeLateHeartRates(sent) | — | X-1 해결분: 보낸 심박만 빼고 남으면 upsert·없으면 delete(RoomPendingRunRepositoryTest +28줄 있음). 합침과 전송의 인터리빙 |
| GuidanceTexts (GuidanceMessage·WatchNotice 전부) | 1 | 표 조회로 바뀌어 키가 빠지면 런타임 예외. GuidanceTextsTest가 전부 읽는지 |
| ShareFailureTest | — | 3차 N3-5 해결분, 6가지 예외 갈래 |
| ServerMessageDecoder.decode 외 7개 | 17 외 | 복잡도 10 초과(참고) |
| 화면 상태 Boolean 이름 변경(N4-1) | — | 이름만 바뀌는 대량 변경이므로 스크린샷(Roborazzi)·상태 저장 키 회귀 |

### 7. 설계서 갱신 요청 (`/sw-design-oo` 재호출 목록)

| ID | 대상 절 | 내용 | 비고 |
|----|---------|------|------|
| U4-8 | 06-06 PendingRunRepository(:138)·D-80(05-02:194) | removeLateHeartRates(sent: LateHeartRates): 보낸 심박만 빼고 남으면 유지, 없으면 항목 삭제 | 3차 안드로이드 갱신 요청 9와 같음 |
| U4-9 | 06-06 core.ui 디자인 시스템, ui.crew, data.api | RunkkStatTile(label, StatTileValue, caption, StatTileStyle...)·RunkkBigNumber(unit·color 제거), CrewMembers.Member.role: CrewRole(모르는 이름은 MEMBER), data.api.notification의 알림함 제목은 문자열 리소스(NotificationRepositoryImpl이 ApplicationContext로 해석), GuidanceMessage를 문구 표 조회로 | 신규(06-06은 이 시그니처를 적지 않아 보강 요청) |
| U4-10 | 팀 컨벤션 Boolean 이름 규칙, 01-02·06-06 설정 필드 이름 | domain Boolean은 is·has·should(GuidanceSettings.shouldFollowCoachPlan). 기준표는 is·has·can뿐이라 should 허용 여부를 정하거나 isFollowingCoachPlan으로 바꾼다. 설정 필드 이름(locationConsented는 isLocationConsented, runVoice는 isRunVoiceOn 등)과 UploadReceipt.isAlreadySaved | 3차 안드로이드 갱신 요청 2·N3-6과 묶음 |

### 8. 다음 행동

- [x] N4-1: 화면 상태 Boolean 이름 is·has·can·should(87fe682~90d5d9d)
- [ ] 결정 대기 5건(S-1·S-3·N-14·N3-7·N3-6 잔여): 사용자·팀 결정 뒤 반영
- [ ] 설계서 갱신: `/sw-design-oo`로 U4-8~U4-10과 3차 갱신 요청 1~12 반영
- [ ] 출시 전 실제 기기에서 release 빌드 실행 확인(R8 규칙 정적 확인만 함)

## 리뷰 2026-10-23 (5차, 안드로이드 (다) 묶음: domain·data·contract·wear·wear-contract)

대상 커밋: feature/runkk-v010 bfe55e1c (기준 develop d7618cb, push된 4ee91aef는 bfe55e1c에 develop 3efbef8b 병합만 더한 것). 범위는 `git diff d7618cb bfe55e1c -- android/{domain,data,contract,wear,wear-contract}`다(330파일 +12,971 −1,845 중 이 다섯 모듈, 안드로이드 커밋 63개 중 45개). feature/*·core·app은 (라) 묶음이 본다.

입력: S15P31A207-v010/android, design-oo 06-06 6.1.14·6.1.15, 06-07 A-02·A-23, 05-01 5.1.2, 05-02 5.6(D-95·D-97·D-103·D-111·D-112·D-119~D-121·D-124·D-130·D-131·D-137·D-142·D-144·D-145·D-153·D-157~D-162), 05-03, F.4, implementation.md, 4차 절(위 4차).
방법: 읽기 전용. git diff·log·grep, 파일 읽기, 줄 수·매개변수·순환 복잡도 약산 스크립트(조건·분기·`&&`·`||`·`?:` 수 + 1, 이전 차수 숫자와 ±2 차이가 있어 변화량만 쓴다)만 썼다. 빌드·시험은 돌리지 않았다(구현 기록 3.x의 직접 검증 결과를 믿고, 시험은 읽어서 점검했다).

### 리뷰 기준

4차와 같다: 매개변수 4 이하(생성자 주입은 D-73, DTO·값 객체 생성자는 제외), 중첩 3단계 이하, 함수 50줄 이하, 순환 복잡도 10 이하(초과는 4차처럼 참고로만 적고 집계에서 뺀다), 결합도 스탬프 이하·응집도 통신적 이상·Fan-Out 7 이하(design-review.md 17장). 이름 규칙: Boolean은 is·has·can(domain·wear·wear-contract는 `should` 불허, DTO 전송 필드 제외), 화면 문구 하드코딩 금지, Dispatchers 주입, 로그는 Timber, GlobalScope·runBlocking 금지. D-112 한 파일 규칙(예외: sealed 계층, 짝 UseCase). 설계서가 기준이고 코드가 더 나으면 설계서 갱신 요청으로 뺀다.

### 대상

| 모듈 | 파일(주 / 시험) | 줄(+ / −) | 비고 |
|------|-----------------|-----------|------|
| domain | 76 / 16 | 4,177 / 318 | run(RunTracker 분리·일시정지·근접 알림), chat, live, account, crew, settings, report 신규·변경 |
| data | 153 / 33 | 6,286 / 1,362 | live(임대·4401), api.chat·report·crew·account·avatar·core(SessionRefresher), data.watch·push·session·settings·store(Room 3) |
| contract | 17 | 856 / 92 | dto-records.json, enum 파일, sync_from_server.py |
| wear | 14 / 6 | 1,202 / 42 | watch.alert 신설, PhoneLink·WatchViewModel·WatchRunDisplay |
| wear-contract | 5 / 9 | 450 / 31 | 메시지 3종 + WatchDisplayMessage 2필드 + 경로 3개 |

### 4차 지적 추적표 ((다) 범위에 걸린 것)

| ID | 4차 지적 | 근거(파일:줄) | 상태 |
|----|----------|---------------|------|
| S-1 | RunTracker 생성자 주입 8 | domain/run/RunTracker.kt:28-35 협력 객체 넷(RunArchive·RunWatchBridge·RunCoach·MeasurementRuntime), 시험 TC-6382가 생성자 4개를 고정 | 해결(D-95) |
| S-3 | 한 파일 여러 타입(domain 2파일) | domain/live/ServerMessage.kt(sealed 15), live/ClientMessage.kt(sealed 2), account/LoginUseCase.kt(짝 UseCase 2)만 남음. D-112가 두 예외를 설계로 확정 | 해결(설계 확정). 단 wear에는 새로 생김(AN5-C-14) |
| N3-6 잔여 | wear-contract Boolean 이름 2 | wear-contract/.../WatchDisplayMessage.kt:18·25 `@SerialName("running") val isRunning`, `@SerialName("heartRateCollectionAllowed") val isHeartRateCollectionAllowed`. 키를 지킨 채 이름만 바꿈(D-97), 호환 시험 WearContractCompatibilityTest(+220줄) | 해결 |
| U4-10 | `shouldFollowCoachPlan` | domain/settings/GuidanceSettings.kt:17 `isFollowingCoachPlan`. domain·wear·wear-contract의 is·has·can 아닌 Boolean 0(재스캔) | 해결 |
| X-1 | 늦은 심박 무조건 삭제 | domain/run/PendingRunRepository.kt:43 `removeLateHeartRates(sent)` 그대로, 이번 범위에서 되돌아간 곳 없음(RoomPendingRunRepositoryTest +15줄) | 해결 유지 |
| N-14 | 커밋·브랜치 Jira 키 | 범위 45커밋 제목에 키 0, 브랜치 feature/runkk-v010 | 결정 대기(사용자 결정으로 판정 요인에서 제외) |
| N3-7 | 한 커밋에 두 가지 일 | 4절 참고. MR !14는 squash라 병합 뒤 이력은 한 커밋 | 결정 대기 유지 |
| 참고: 순환 복잡도 10 초과 | decode 17, withSettingChanged 13, toSnapshot 13 외 | 3절 참고 | 증가 3·신규 초과 2(참고) |

### 1. 설계 일치

이름 대조: 설계 6.1.14·6.1.15의 클래스가 모두 코드에 있다(RunTracker 협력 객체 넷, RunWatchBridge·WatchRequestListener·RunCoach·RunArchive·MeasurementRuntime, ProximityAlertRule·ProximityAlert·PauseInterval·PauseLimit, ChatRoom·OpenChatRoomRegistry·ChatRepository, ReportRepository, SessionRefresher, FallbackLiveChannelProvider의 임대, OpenChatRoomTracker, PhotoCompressor, ProximityAlertPlayer, wear-contract 메시지 3종). 설계에 없는데 코드에 생긴 공개 연산·타입은 7절 설계서 갱신 요청으로 뺐다. 설계에 있는데 코드에서 쓰이지 않는 것은 AccountType(AN5-C-11) 하나다.

인터페이스 대조: 시그니처는 설계와 같다. 다른 곳은 둘이다. (1) RunWatchBridge.publishProximity(alert, now)는 설계가 `publishProximity(alert)`로 적었으나 6.1.14 "시각은 모두 매개변수" 규칙을 따른 것이라 코드가 옳다(설계서 갱신 요청). (2) CrewRepository.writePoll·writeMeetup이 매개변수 5개(AN5-C-3, 설계 06-06:311은 이름만 적고 시그니처가 없다).

의존 방향 대조(5.1.2): 폰은 새 화살표가 없다. domain은 core.model 밖을 import하지 않는다(256개 import 전부 core.model). data.live → api.core·api.live·domain.live, data.push → domain.chat(D-130), api.chat·api.report → domain.* 이 설계표에 있다. 워치는 5개다: watch.link → wear.contract·watch.heart·watch.alert, watch.alert → wear.contract, watch.ui → watch.link. 설계의 "워치 5"와 같고 watch.ui가 wear.contract를 import하지 않는다. wear-contract는 의존이 없다. 내용·공통·외부 결합도는 찾지 못했다(화살표마다 주고받는 값: data.live ↔ api.core는 SessionRefresher.refreshNow()의 결과 값 한 종류라 데이터 결합, data.watch ↔ domain.run은 WatchGateway 포트 값 객체라 데이터 결합, watch.link ↔ watch.alert는 메시지 값 한 개라 데이터 결합).

응집도 회귀: ChatRoom이 한 클래스 406줄에 책임 다섯(연결 상태 따르기·메우기·폴링·낙관적 전송·목록 병합)이다(AN5-C-15). 그 밖의 새 클래스는 책임이 한 문장이다.

### 2. 새 결함

치명 0, 중 2, 경 16이다.

| ID | 파일:줄 | 항목 | 현재 코드 한 줄 | 교정 예시 한 줄 | 등급 |
|----|---------|------|-----------------|-----------------|------|
| AN5-C-1 | data/live/FallbackLiveChannelProvider.kt:52-55, 79-82, 85-94 | 임대 경쟁: open()이 끝난 연결에 임대를 건다 | `val connection = current?.takeUnless { it.isEnded } ?: Connection().also { current = it }` 다음 `connection.lease()`가 `synchronized(stateLock) { leases++ }` | 끝났는지 확인과 `leases++`를 한 번에 하는 `tryLease()`가 null이면 새 연결을 만든다(아래 상세) | 중 |
| AN5-C-2 | data/api/crew/PhotoCompressor.kt:63-84 (특히 :69-79) | 중첩 4단계(try → while → for → if)와 반복 안 return, 그리고 긴 변 64px 미만 그림이 TOO_LARGE로 거부되는 결함 | `while (longEdge >= MIN_LONG_EDGE) { for (quality in …) { … if (encoded.size <= MAX_RESULT_BYTES) return Outcome.Success(encoded) } … }` 다음 `return Outcome.Failure(AppError.Rejected(TOO_LARGE))` | 한 겹 인코딩을 `encodeAtBestQuality(image, longEdge): ByteArray?`로 빼고 `do { … } while (result == null && longEdge >= MIN_LONG_EDGE)`(아래 상세) | 중 |
| AN5-C-3 | domain/crew/CrewRepository.kt:78-84(writePoll), :92-98(writeMeetup), data/api/crew/CrewRepositoryImpl.kt:145, :171 | 매개변수 5개(폼 값 객체 없이 낱개) | `suspend fun writePoll(crewId: Long, question: String, method: PollMethod, options: List<String>, closesAt: Instant): Outcome<Long>` | `suspend fun writePoll(crewId: Long, form: PollForm)`, `writeMeetup(crewId: Long, form: MeetupForm)`(같은 파일의 CrewForm·CrewRunForm 방식) | 경 |
| AN5-C-4 | data/live/WebSocketLiveChannel.kt:284-314 (:308-312) | 재연결 루프에서 핸드셰이크 401을 끝으로 보지 않는다. 갱신이 거부되어 로그아웃된 뒤에도 5초마다 무한 재시도 | `connectOrThrow()` 다음 `catch (e: IOException) { // 다음 간격 뒤 다시 시도한다. }` (HandshakeFailed(401)도 IOException) | `catch (e: HandshakeFailed) { if (e.code == UNAUTHORIZED) needsRefresh = true }`로 다음 시도에서 갱신부터(거부면 이미 있는 `Rejected -> closeForGood()`가 닫는다) | 경 |
| AN5-C-5 | domain/chat/ChatRoom.kt:219-225, :248 | 연결 결과·CLOSED를 무시하고, 첫 쪽 읽기가 핸드셰이크 뒤로 밀린다 | `opened.channel.connect()` 한 줄 뒤에 `var isLoaded = if (opened.isReopen) { … } else { loadLatest(opened.crewId) }`, `LiveState.CONNECTING, LiveState.CLOSED -> Unit` | `opened.scope.launch { opened.channel.connect() }`로 연결을 따로 띄우고, `LiveState.CLOSED -> work = opened.scope.launch { pollEvery(opened.crewId) }`(아래 상세) | 경 |
| AN5-C-6 | domain/chat/ChatRoom.kt:160-179 (:165, :176) | 이전 쪽 읽기 표지가 코루틴이 시작도 못 하고 취소되면 영영 참으로 남음 | `if (!loadingOlder.compareAndSet(false, true)) return` 다음 `opened.scope.launch { try { … } finally { loadingOlder.set(false) } }` | `opened.scope.launch { … }.invokeOnCompletion { loadingOlder.set(false) }`(시작 전 취소에도 불린다) | 경 |
| AN5-C-7 | domain/run/RunTracker.kt:154-167, :301-307 | tick이 락 밖에서 낡은 판단을 쓴다: (a) 60분 도달 판단 뒤 재개가 끼어도 endAfterPauseLimit이 다시 확인하지 않고 끝냄, (b) 끝난 뒤 늦은 tick이 달리는 중 표시를 워치로 보냄 | `pause >= PauseLimit.END_AFTER -> return endAfterPauseLimit(now)`, 마지막 줄 `watchBridge.publish(progress.toWatchDisplay(active.spec))` | (a) `endActive`에 `requirePaused` 인자 없이 `endIfStillPaused(now)`를 두어 락 안에서 `active.isPaused`를 다시 확인, (b) publish 직전에 `synchronized(lock) { session === active && active.isActive }`를 다시 확인 | 경 |
| AN5-C-8 | data/api/core/SessionRefresher.kt:76, :87 / data/session/EncryptedTokenStore.kt:65 | "로그아웃이 되살아나지 않게"가 확인 후 저장(check-then-act)이라 완전하지 않고 시험도 없다 | `is Reply.Issued -> if (tokens.refreshToken() == refresh) store(issued) else Refresh.Rejected`(store는 `tokens.update(...)`) | TokenProvider에 `fun replace(expectedRefresh: String, access: String, refresh: String): Boolean`을 두고 EncryptedTokenStore가 lock 안에서 비교·저장을 한 번에 | 경 |
| AN5-C-9 | data/api/crew/CrewRepositoryImpl.kt:195-200 / wear/.../alert/ProximityAlertPlayer.kt:101-110 / data/live/WebSocketLiveChannel.kt:288-303 | 반복 안 return(4차 N3-3 유형 재발). writeBoard는 `map` 안, play와 reconnectLoop는 `while` 안 | `is Outcome.Failure -> return compressed` (`photoUris.map { … }` 안), `if (current != generation) return` (`while (true)` 안) | writeBoard는 N3-3 방식(map 앞에서 실패를 먼저 거름), play는 `var isDone`을 조건으로(아래 상세) | 경 |
| AN5-C-10 | data/api/crew/CrewRepositoryImpl.kt:196, :210 | Dispatchers.IO 하드코딩(4차 N-10 재발). 시험이 디스패처를 바꿀 수 없다 | `withContext(Dispatchers.IO) { photos.compress(uri) }` | 생성자 `ioDispatcher: CoroutineDispatcher`를 받아 `withContext(ioDispatcher)`(EncryptedTokenStore.kt:36 방식) | 경 |
| AN5-C-11 | data/api/account/AccountMappers.kt:82, :158 / domain/account/AccountType.kt | 설계에 있는 AccountType(GUEST·LINKED, 06-06:246)을 코드가 쓰지 않고 문자열 상수로 비교 | `isGuest = accountType == ACCOUNT_GUEST` 와 `private const val ACCOUNT_GUEST = "GUEST"` | `isGuest = enumByName<AccountType>(accountType) == AccountType.GUEST` 하고 상수 삭제(계약 시험이 이미 이름을 대조) | 경 |
| AN5-C-12 | data/api/core/SessionRefresher.kt:137, :140 / api/account/SocialLinkRequest.kt:7, LoginRequest.kt:7, TokenPairResponse.kt:6 | 비밀 값 DTO의 toString 마스킹이 일관되지 않다(GuestStartResponse·GuestSession·Refresh.Renewed는 마스킹). 지금 로깅 인터셉터가 없어 노출 경로는 없고 방어선 문제 | `private data class GuestSessionBody(val guestCredential: String)` (기본 toString이 값을 찍음) | `override fun toString(): String = "GuestSessionBody(***)"` | 경 |
| AN5-C-13 | domain/run/RunWatchBridge.kt:45-53 | 워치 요청 처리 중 RuntimeException을 삼키고 `lastDeliveryFailure`에만 담는다(시험·진단용이라 운영에서는 흔적이 없다). domain은 Timber를 쓸 수 없다 | `catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) { lastDeliveryFailure = e }` | 앱 범위의 예외 처리기로 넘긴다: `scope.coroutineContext[CoroutineExceptionHandler]?.handleException(scope.coroutineContext, e)`(구독은 이어 간다) | 경 |
| AN5-C-14 | wear/.../alert/AlertPorts.kt:3-22, alert/AndroidAlertOutputs.kt:23·53·108, ui/WatchUiState.kt:42 | 한 파일 여러 타입(D-112 유형). 새 파일 둘과 PauseControl 추가. Konsist 규칙이 폰(domain·data·ui)만 지켜 워치는 걸러지지 않음 | `AlertPorts.kt`에 `interface AlertVibrator`, `interface AlertBeeper`, `fun interface MonotonicClock`; `WatchUiState.kt`에 `enum class PauseControl` | 타입마다 한 파일로 나누고 구조 시험 TC-6583을 :wear까지 넓힌다(설계서 D-112 적용 범위 확인은 7절) | 경 |
| AN5-C-15 | domain/chat/ChatRoom.kt (406줄) | 응집도: 목록 병합 규칙(merge·sameAs·addPosted·removed 처리)이 연결 수명·폴링과 한 클래스에 섞여 있어 시험이 코루틴 없이는 병합을 못 건드림 | `private fun merge(current: ChatPage, incoming: List<ChatMessage>): ChatPage {` 외 5개 병합 보조 함수가 ChatRoom 안 | 병합을 `internal object ChatPageMerger { fun merge(current, incoming): ChatPage }`로 빼고 ChatRoom은 수명·전송만(같은 domain.chat 안이라 화살표 없음) | 경 |
| AN5-C-16 | domain/account/SessionRepository.kt:25, data/session/EncryptedTokenStore.kt:93-95, domain/account/LogoutUseCase.kt:27 | 동기 포트 `hasGuestCredential()`이 첫 호출에서 암호화 저장소(Keystore)를 열 수 있다. 부르는 쪽이 각자 IO로 감싸야 했고(f60e01cf: MeViewModel·SettingsViewModel) LogoutUseCase는 호출 스레드 그대로 읽는다 | `fun hasGuestCredential(): Boolean` | `suspend fun hasGuestCredential(): Boolean = withContext(ioDispatcher) { guest.value != null }`로 바꾸고 호출자의 IO 감싸기를 걷는다 | 경 |
| AN5-C-17 | data/src/test/.../api/core/SessionRefresherTest.kt:85, data/src/test/.../data/live/FallbackLiveChannelProviderTest.kt:48·189-206 | 시험 품질: (a) `Thread.sleep(SLOW_MILLIS)` 300ms 안에 세 호출이 몰리길 기대해 느린 CI에서 갱신이 두 번 나갈 수 있다, (b) 실제 소켓·실시간 `delay`(3회 실패 시험은 1+2초), (c) 위 결함을 잡는 시험이 없다 | `Thread.sleep(SLOW_MILLIS)` | (a) 세 호출이 모두 서버에 닿았음을 CountDownLatch로 확인한 뒤 응답을 푼다, (c) 아래 상세의 시험 | 경 |
| AN5-C-18 | docs/sw/implementation.md 4·5·8·9장 | 상태 보고: 4장 결정 목록과 9장 설계서 갱신 요청이 비어 있고, 총괄 결정은 3.x 단계 기록 문장에만 흩어져 있다(A1 갱신 요청 1, A2 로그아웃 갈래·sentAt 오프셋, S3a 크루 밖 쓰기 403 대 채팅 409 통일 요청 등). 5장 계약 "마지막 갱신"이 develop d7618cb 그대로다(android/contract는 이번에 3번 다시 만듦) | `| ID | 단계 | 내용 | 근거 | 분류 |` (본문 행 0개) | 3.x의 결정을 4장 행으로 옮기고 7절 목록을 9장에 옮긴다. 5장 갱신일을 bfe55e1c로 | 경 |

점검했으나 결함 아님(통과):

| 점검 요청 | 결과 |
|-----------|------|
| 4401 갱신 → 곧바로 재연결(D-153) | WebSocketLiveChannel.kt:239-262. 갱신 거부면 닫고, 일시 오류면 닫은 채 일반 간격으로 "갱신 → 재연결", 1분 안 세 번째 4401부터 일반 간격(`rapidReconnects < 3`, 연결이 60초 넘게 이어지면 0부터, 60초 정각은 세 번째로 셈, 시험 TC-6473 (a)(b)(c)). 4401 처리 중 같은 소켓의 onClosing·onClosed가 함께 와도 붙은 소켓만 처리해 한 번(:197-207). 반복 갱신 코루틴은 `reconnecting` Job 하나로 막고 `releasingOnExit`이 예외로 끝나도 비운다 |
| 붙이기 전에 끊긴 소켓(lostBeforeAttach) | :115-135. 슬롯이 하나라 늦은 옛 소켓 알림이 새 소켓의 끊김을 덮을 수 있으나 재연결 간격(≥1초)이 옛 알림 도착보다 길어 실제 겹침은 없다. 비교는 `===`라 옛 소켓 알림은 무시됨 |
| SessionRefresher 동시 호출·결과 공유 | :57-66. 대기 중 다른 호출이 끝났으면(generation) 그 결과를 돌려주고 refresh 한 번. 갱신 거부 → 게스트 재개 1회 → 거부면 clear, 일시 오류(5xx·408·429·연결·해독)는 토큰 유지(D-159 (4), 시험 TC-6451~6456). 갱신 요청은 인증 없는 base 클라이언트로 나가 만료 토큰을 싣지 않음. `runBlocking { tokens.clear() }`은 OkHttp 작업 스레드·IO에서만 불려 허용 |
| 게스트 자격 증명 보관(FR-197, D-89) | EncryptedSharedPreferences(AES256-GCM 키 세트·SIV 이름)에 토큰과 같은 파일, clear()가 함께 지움, allowBackup=false(app·wear 매니페스트), GuestStartResponse·GuestSession·Refresh.Renewed toString 마스킹, 기기에 못 쓰면 Storage 실패로 시작을 되돌림(StartAsGuestUseCase.kt:197-212, 시험 GuestAccountUseCaseTest). 연동 성공·ALREADY_LINKED는 자격 증명을 지우고 연동했는데 지우지 못하면 실패를 알림(LinkSocialAccountUseCase) |
| 로그아웃 게스트 갈래(FR-199) | LogoutUseCase.kt:27 `settings.isGuest() && session.hasGuestCredential()`. 설정 사본이 아직 안 읽혀 거짓이거나 연동 직후 사본이 낡아도 자격 증명이 둘 다 있어야 미전송 기록을 지우므로 기록 손실 쪽으로 틀리지 않는다 |
| Room 판 3 마이그레이션(CR34-14) | PendingRunDatabase.kt `AutoMigration(from = 2, to = 3)`, schemas/…/3.json 생성, 칼럼 `pauses` BLOB 기본값 `x''`, `toSentHeader`는 정지 구간을 비움. 시험 TC-6521(2→3, 2건과 늦은 심박 항목 유지)·TC-6523(1→3). 빈 BLOB은 빈 목록으로 읽음 |
| PhotoCompressor EXIF·메모리(D-142, NFR-042) | 방향을 픽셀에 반영하고 새 JPEG를 비트맵에서 다시 만들어 EXIF·GPS를 복사하지 않음(시험 TC-6515·6516이 APP1 세그먼트 0·GPS 태그 0 확인). 원본은 상한+1바이트까지만 읽고(readLimited), 긴 변이 1600 이상으로 남는 만큼만 inSampleSize로 줄여 풀며, 중간 비트맵은 recycle, 투명 PNG는 흰 바탕, OOM은 예외 대신 거부 값. 순서(방향 → 품질 85에서 5씩 하한 50 → 긴 변 0.8배)가 설계 표와 같음 |
| WebSocket 닫기 코드 분기(F.3.3) | 1000·1008 → closeForGood(CLOSED, 재연결 없음), 4401 → 갱신, 그 밖(1012·끊김) → 1·2·4초 뒤 5초 간격. 끊긴 동안 send는 버리고 ChatRoom이 CONNECTED 진입 때 입장을 다시 보냄(D-131) |
| 임대 반납 의미(D-103) | Lease.close()는 compareAndSet으로 한 번만 반납, 반납한 임대의 messages는 끝나고 send는 버림, 참조 수 0에서만 실제 닫기, 닫힌 연결의 다음 open()은 새 연결(시험 TC-6461~6465). 고른 채널이 스스로 CLOSED가 되면 연결을 끝내고 current를 비움(단, AN5-C-1의 경쟁은 제외) |
| 실시간 계약(05-03) | ServerMessageDecoder가 모르는 type·모르는 삭제 사유·형식 오류를 null(로그)로 버리고 예외를 밖으로 내지 않음. 앱 → 서버 진행값은 `type`과 오프셋 포함 `sentAt`, ChatPresence는 `present` 키(위치 항목 없음). RealtimeContractTest가 서버와 같은 표본 파일 8개를 씀 |
| REST 계약 | ServerContractTest가 새 enum 10개와 DTO(요청·응답 20여 개)를 contract/ 파일과 이름 대조, VoteAnswer·추천 잔재 0 확인(TC-6854·6857). sync_from_server.py는 하위 패키지(crew.chat)를 처리하도록 고침 |
| wear-contract 하위 호환(D-97, F.4.4) | 키는 접두어 없는 이름 유지(@SerialName), paused·pausable·heartRateCollectionAllowed 없는 옛 메시지는 안전한 값(거짓)으로 읽음, 모르는 PauseRequest action은 해독 실패 → 수신 쪽이 버리고 경고 로그, 경로 7개 겹침 없음. 표본 7개로 양방향 시험 |
| 워치 근접 알림 재생(FR-196, D-158) | 단조 시계로 만료·간격을 재고, 간격은 폰이 준 값을 100ms~3초로만 묶고, 마지막 메시지 뒤 3초 정각에 스스로 멈추며, 멈출 때 ToneGenerator를 놓음. 진동기·비프 예외는 기록만 하고 삼켜 PhoneLink 수신 코루틴이 죽지 않음. isRunning 거짓 표시가 오면 재생을 멈춤, 러닝이 끝난 뒤 늦은 PROXIMITY·LEAD_CHANGE는 버림(PhoneLink.kt isRunning 확인) |
| 코루틴 안전 | GlobalScope 0. runBlocking은 SessionRefresher.kt:100 하나(위 사유). 새 코루틴은 모두 주입 범위(applicationScope, 방별 SupervisorJob 자식)에서 시작해 취소가 전파됨: ChatRoom 방 범위는 close()에서 cancel, RunWatchBridge·WatchLink는 앱 범위. CancellationException을 다시 던지는 곳 확인(RunWatchBridge.deliver, PlayServicesTransport.quietly, DataStoreSettingsRepository init) |
| 설정 사본 읽기 순서(A4) | DataStoreSettingsRepository.changes()가 `isSettled` 전에는 내보내지 않고, 읽기 예외는 모두 "사본 없음"으로 정해 첫 테마 구독이 영영 안 오는 일이 없음. 메모리 사본은 세대 번호 compare-and-set이라 로그아웃 뒤 늦은 읽기가 되살리지 못함 |
| 문구·이름·로그 규칙 | domain·data·wear 주 소스에서 한글 리터럴(주석·Timber·예외 메시지 제외) 0, Boolean 이름(domain·wear·wear-contract) 위반 0, 로그는 Timber 사용(주석 외 Log.* 0) |
| 순환 복잡도·길이 | 50줄 초과 함수 0(분리 후 최대 ChatRoom.start 37줄, PhotoCompressor.compress 25줄). 중첩 4단계는 AN5-C-2 한 곳뿐(그 밖의 `if` 들여쓰기 20칸 이상은 람다 안이거나 한 단계) |

#### AN5-C-1 상세(경쟁과 교정)

열린 연결에서 마지막 임대를 반납하는 스레드 B와 새 임대를 여는 스레드 A가 겹치면 이렇게 된다. A가 `lock` 안에서 `current`를 읽는다(아직 끝나지 않음). B의 `release()`가 `stateLock` 안에서 `leases--`가 0이 되어 `end()`로 `isEnded = true`를 세운다(B는 `lock`을 잡지 않아 A와 겹친다). A가 `lease()`로 `leases++`를 하고 끝난 연결의 임대를 돌려받는다. 이 임대의 `connect()`는 `if (isEnded) return Failure(AppError.Network)`로 영원히 실패하고 state는 CLOSED라, 채팅방이나 대결 화면이 다시 열리기 전까지 실시간이 죽는다. 확률은 낮으나(두 호출이 나노초 단위로 겹침) 결과가 되돌려지지 않는다. 현재 시험은 모두 한 스레드라 이 경쟁을 만들지 못한다.

```kotlin
override fun open(): LiveChannel = synchronized(lock) {
    current?.tryLease() ?: Connection().also { current = it }.lease()
}

/** 끝나지 않았을 때만 임대한다(확인과 증가를 stateLock 한 번에). 끝났으면 null. */
fun tryLease(): LiveChannel? = synchronized(stateLock) {
    if (isEnded) null else Lease(this).also { leases++ }
}
```

시험: `val c = provider.open(); c.close()` 뒤 내부 연결에 `tryLease()`를 부르면 null이고, `open()`은 새 핸드셰이크를 부른다. 스레드 둘로 open·close를 수천 번 겹치는 시험도 한 개 둔다.

#### AN5-C-2 상세(교정)

```kotlin
fun compress(original: ByteArray): Outcome<ByteArray> {
    if (original.size > MAX_ORIGINAL_BYTES) return Outcome.Failure(AppError.Rejected(TOO_LARGE))
    if (!isJpeg(original) && !isPng(original)) return Outcome.Failure(AppError.Rejected(UNSUPPORTED_IMAGE))
    val image = decodeOrNull(original) ?: return Outcome.Failure(AppError.Rejected(UNSUPPORTED_IMAGE))
    return try {
        encodeWithinLimit(image)?.let { Outcome.Success(it) } ?: Outcome.Failure(AppError.Rejected(TOO_LARGE))
    } catch (e: OutOfMemoryError) {
        Outcome.Failure(AppError.Rejected(TOO_LARGE))
    } finally {
        codec.release(image)
    }
}

/** 긴 변을 0.8배씩 줄이며 1MB 이하가 되는 첫 결과. 한 번은 반드시 시도한다(작은 그림도). */
private fun encodeWithinLimit(image: Image): ByteArray? {
    var longEdge = minOf(MAX_LONG_EDGE, max(image.width, image.height))
    var result: ByteArray?
    do {
        result = encodeAtBestQuality(image, longEdge)
        longEdge = (longEdge * SHRINK).roundToInt()
    } while (result == null && longEdge >= MIN_LONG_EDGE)
    return result
}

private fun encodeAtBestQuality(image: Image, longEdge: Int): ByteArray? =
    (START_QUALITY downTo MIN_QUALITY step QUALITY_STEP).asSequence()
        .map { codec.encode(image, longEdge, it) }
        .firstOrNull { it.size <= MAX_RESULT_BYTES }
```

현재 코드는 `longEdge = min(1600, max(w, h))`가 64 미만이면(예: 48×48 아이콘 PNG) while 조건이 처음부터 거짓이라 한 번도 인코딩하지 않고 `Rejected(TOO_LARGE)`를 돌려준다. 사유도 틀리다(원본은 작다). 부기로, OOM을 TOO_LARGE로 돌려주는 것도 원본 크기와 무관한 기기 메모리 부족을 "너무 큼"으로 안내하게 된다(설계 D-142 표가 OOM 사유를 정하지 않았으니 7절에서 사유 코드를 정하게 한다). 시험: 48×48 PNG가 JPEG로 성공하는 케이스, 투명 PNG가 흰 바탕이 되는 케이스를 AndroidPhotoCodecTest에 더한다(지금은 둘 다 없다).

#### AN5-C-5 상세(교정)

```kotlin
private suspend fun start(opened: OpenRoom) {
    opened.scope.launch(start = CoroutineStart.UNDISPATCHED) {
        opened.channel.messages.collect { onMessage(opened.crewId, it) }
    }
    opened.scope.launch { opened.channel.connect() }   // 핸드셰이크를 기다리지 않고 첫 쪽부터 읽는다
    var isLoaded = if (opened.isReopen) { fillAfterLast(opened.crewId); true } else loadLatest(opened.crewId)
    …
        LiveState.POLLING, LiveState.CLOSED -> work = opened.scope.launch { pollEvery(opened.crewId) }
```

현재 `connect()`는 WebSocket 핸드셰이크를 최대 3번(OkHttp 연결 시간 초과 × 3 + 1·2초 대기) 시도한 뒤에야 돌아오므로 네트워크가 나쁠 때 방 목록이 그만큼 늦게 뜬다. 또 다른 기기가 같은 회원으로 접속해 이 연결이 1000으로 닫히면(Lease.state가 CLOSED가 된다) ChatRoom은 `Unit`으로 두어 실시간도 폴링도 없이 방이 멈춘다. 설계 D-131은 CLOSED를 정하지 않았으므로 7절에서 정하게 했다. 닫기를 폴링으로 잇는 것은 제안이고, 안내만 하고 멈추는 것으로 정해도 `Failure.Action`을 더해 화면에 알려야 한다.

#### AN5-C-9 상세(교정)

```kotlin
// CrewRepositoryImpl.writeBoard
val compressed = photoUris.map { uri -> withContext(ioDispatcher) { photos.compress(uri) } }
compressed.filterIsInstance<Outcome.Failure>().firstOrNull()?.let { return it }
val parts = compressed.map {
    MultipartBody.Part.createFormData(PHOTOS_PART, PHOTO_FILE, (it as Outcome.Success).value.toRequestBody(JPEG))
}

// ProximityAlertPlayer.play: 끝낼지는 한 곳에서 정한다
private suspend fun play(current: Long) {
    var isDone = false
    while (!isDone) {
        val now = clock.elapsedMillis()
        val step = synchronized(lock) { nextStepLocked(now) }
        delay(step.atMillis - now)
        isDone = synchronized(lock) { beatOrStopLocked(current, step) }   // 옛 반복이거나 만료면 참
    }
}
```

reconnectLoop(WebSocketLiveChannel.kt:284-314)도 같은 방식(`var isFinished`)으로 `while (scope.isActive && !isFinished)`로 바꾼다. FallbackLiveChannelProvider.choose(:151-168)와 RunSession.arrivalAt은 "처음 성공·처음 닿은 지점 찾기"가 함수의 유일한 책임이라 4차(02-android.md:241)처럼 허용 범위로 봤다.

#### AN5-C-17 상세(빠진 시험)

| 시험 | 대상 | 확인할 것 |
|------|------|-----------|
| 임대 경쟁 | FallbackLiveChannelProvider | AN5-C-1: 끝난 연결에 tryLease가 null, 스레드 둘로 open·close 겹침 반복에도 모든 임대가 CONNECTED 또는 새 연결 |
| 재연결 중 401 | WebSocketLiveChannel | AN5-C-4: 4401 뒤 재연결 핸드셰이크가 401이고 갱신이 Rejected면 CLOSED, Unavailable이면 5초 간격 재시도 계속 |
| 갱신 중 로그아웃 | SessionRefresher | AN5-C-8: 응답을 기다리는 사이 clear()가 불리면 새 토큰을 저장하지 않고 loggedIn이 거짓으로 남음 |
| 작은 그림 | PhotoCompressor | AN5-C-2: 긴 변 64 미만 그림은 JPEG 성공 |
| 닫힌 연결의 채팅방 | ChatRoom | AN5-C-5: 임대 state가 CLOSED가 되어도 방이 폴링으로 잇는다(또는 실패를 알린다), connect()가 늦어도 첫 쪽이 먼저 보임 |
| 이전 쪽 읽기 중 닫기 | ChatRoom | AN5-C-6: loadOlder 직후 close()·open() 해도 다시 loadOlder가 동작 |
| 60분 직전 재개 | RunTracker | AN5-C-7: tick이 60분을 본 직후 재개가 끼면 끝내지 않는다(락 밖 호출을 가짜로 끼워 넣기) |

### 3. 구조적 프로그래밍·순환 복잡도(참고, 집계 제외)

4차와 같게 순환 복잡도 10 초과 함수는 참고로만 적고 집계에서 뺐다(넣으면 경 +5). 이번 범위에서 변한 것: ServerMessageDecoder.decode(data/live/ServerMessageDecoder.kt:37) 약산 19 → 22(채팅 메시지 3종 분기 추가, 분기 하나가 `liveJson.decodeFromJsonElement(...)` 한 줄이라 평평한 표 분기), DataStoreSettingsRepository.toSnapshot(:155) 13 → 16(근접 알림·테마·게스트 엘비스 3개 추가), AccountMappers.withSettingChanged(:124) 15 → 16, 신규 초과 둘: MemberSettingsResponse.toDomain(AccountMappers.kt:56) 10 이하 → 12(theme·isGuest·근접 알림 기본값 분기 추가), WatchViewModel.reduce(wear/ui/WatchViewModel.kt:204) 10 → 12(isPaused·isPausable 두 줄의 `&&`). 경계인 함수: BattleRunCoordinator.handle·CrewRunCoordinator.handle은 sealed 전체 `when`에 분기 한 줄(채팅 3종)씩 늘어 10이고 초과가 아니다. 반복 안 return과 단일 출구는 AN5-C-9(경), 가드 절은 허용했다.

### 4. 형상 관리

| 활동 | 판정 | 근거 |
|------|------|------|
| 형상 식별 | 예 | 모듈(domain·data·contract·wear·wear-contract) 경계가 설계 5.1.2와 같고, 기준선 develop d7618cb와 통합 브랜치 feature/runkk-v010(MR !14, Draft)이 implementation.md 0장에 적혀 있다 |
| 버전 제어 | 예 | 안드로이드 커밋 63개(이 묶음 45개)가 전부 `feat|fix|refactor|test|chore|style|merge:` 접두어와 Co-Authored-By 줄을 가진다. 태그는 0개이고 versionName은 0.1.0 그대로(출시 전이라 이전 차수와 같게 아니오로 세지 않음) |
| 변경 통제 | 아니오 | 45커밋 제목에 Jira 키 0, 브랜치 feature/runkk-v010(N-14, 결정 대기). 요구사항·결정 ID(FR-·D-·TC-)는 커밋과 코드 주석에 있다. 사용자 결정으로 판정 요인에서 제외 |
| 형상 감사 | 예 | `git diff d7618cb bfe55e1c`로 이 묶음 변경이 그대로 재현된다. 단 implementation.md 5장 계약 "마지막 갱신"이 d7618cb로 낡았다(AN5-C-18) |
| 상태 보고 | 예(보완) | 커밋 메시지와 implementation.md 3.x가 읽을 만하나 4·9장 표가 비어 있다(AN5-C-18) |

커밋 단위(N3-7 유형, 결정 대기 유지): ac8f634a(data.live: 임대·닫기 코드·4401 재연결·채팅 송수신, 15파일 +946), ad73c34d(api.crew 새 엔드포인트·PhotoCompressor·추천 삭제, +1,069), a85dac4d(RunTracker 분리·일시정지·정지 한도·근접 알림, 23파일 +1,512), 12d5e8e1(api.account 게스트·연동·설정 사본 확장)이 한 커밋에 여러 가지 일을 담는다. MR이 squash 병합이라 develop에는 한 커밋으로 남아 실효가 낮아 새 결함으로 세지 않았다.

### 5. 집계와 판정

| 구분 | 치명 | 중 | 경 | 구성 |
|------|------|----|----|------|
| 새 결함 | 0 | 2 | 16 | 중: AN5-C-1·2, 경: AN5-C-3~18 |
| 4차 잔여 | 0 | 0 | 2 | 결정 대기 2(N-14·N3-7). S-1·S-3·N3-6 잔여 해결 |
| 합계 | 0 | 2 | 18 | 4차 대비 중 +2, 경 +12(범위가 안드로이드 다섯 모듈 +12,971줄이라 늘어난 면적 대비) |

판정: **합격**(이 묶음 단독)
근거: 치명 0, 중 2(≤ 3), 설계 일치 점검 수행. 형상 관리는 변경 통제 1항목만 아니오이고 4차와 같이 사용자 결정(N-14)으로 판정 요인에서 뺐다. 빼지 않으면 "조건부"다. MR 전체 판정은 (가)·(나)·(라) 묶음과 종합 절을 따른다. 중 둘은 둘 다 한 곳 고치기로 풀리고(AN5-C-1 tryLease 8줄, AN5-C-2 함수 분리) 시험이 없어 통과되었던 것이라 머지 전에 고치기를 권한다. 경 가운데 AN5-C-4(401 무한 재시도)·AN5-C-5(ChatRoom CLOSED)·AN5-C-8(refresh 저장 경쟁)은 사용자에게 보이거나 세션 상태에 닿으므로 먼저 본다.

### 6. sw-test 전달: 우선 테스트 대상

| 함수·경로 | 약산 복잡도 | 이유 |
|-----------|-------------|------|
| FallbackLiveChannelProvider.open·Connection.lease·release·connect·follow | 8 이하 | 임대 참조 수와 끝남 표시의 경쟁(AN5-C-1), 다중 스레드 시험 필요 |
| WebSocketLiveChannel.onLost·onLostAttached·connectOrThrow·reconnectLoop·renewAndReconnect | 8 이하 | 4401·붙이기 전 끊김·재연결 루프 종료 조건(AN5-C-4), 가상 시계 시험 |
| SessionRefresher.refreshNow·renew·resumeGuest | 6 이하 | 동시 호출 결과 공유, 갱신 중 clear(AN5-C-8) |
| PhotoCompressor.compress | 9 → 분리 후 5 | 품질 5씩·긴 변 0.8배·64px 미만(AN5-C-2), 경계값 시험 |
| ServerMessageDecoder.decode | 22 | 메시지 종류 15개 표 분기, 모르는 type·사유·형식 오류가 null |
| DataStoreSettingsRepository.toSnapshot / AccountMappers.MemberSettingsResponse.toDomain·withSettingChanged | 16 / 12 / 16 | 모르는 테마·누락 필드 기본값, 키 오타 |
| WatchViewModel.reduce | 12 | isPaused·isPausable 조합, 연결 끊김·안내 만료 경계 |
| RunTracker.tick·warnPauseLimitOnce·endAfterPauseLimit·acceptHeartRates | 8 이하 | 정지 55분·60분 경계(TC-6323~6329 있음), 재개와의 경쟁(AN5-C-7) |
| ChatRoom.start·fillAfterLast·merge·loadOlder | 9 이하 | 재연결 메우기·중복 병합·이전 쪽 표지(AN5-C-5·6) |
| ProximityAlertPlayer.play·nextStepLocked | 6 | 단조 시계 만료 정각, 추월 긴 진동 중 비프 미룸 |

### 7. 설계서 갱신 요청 (`/sw-design-oo` 재호출 목록)

코드가 옳고 설계가 낡은 것(결함으로 세지 않음). implementation.md 4장·9장에도 옮겨 적는다(AN5-C-18).

| ID | 대상 절 | 내용 | 비고 |
|----|---------|------|------|
| U5-C1 | 06-06 RunWatchBridge(:158) | `publishProximity(alert: ProximityAlert?, now: Instant)`: 마지막 전송 뒤 지난 시간을 재려고 시각을 매개변수로 받는다(6.1.14 "시각은 매개변수" 규칙). 워치 요청 처리 중 예외는 구독을 끊지 않고 삼킨다 | 코드 RunWatchBridge.kt:87 |
| U5-C2 | 06-06 RunTracker(:119-150) | 공개 `ended: SharedFlow<FinishOutcome>`(워치·정지 자동 종료·조정자 종료 결과)와 `takeUnclaimedEnd()`(러닝 화면이 떨어져 있던 동안 끝난 자유런을 10분 안에 한 번 꺼냄, UNCLAIMED_TTL). A3a·A4 결정 | 설계에 없음 |
| U5-C3 | 06-06 ChatRoom(:280-296) | `loadOlder()`(가장 오래된 messageId를 before로, hasMore, 겹침 방지), `failures: SharedFlow<Failure>`(Action REACT·DELETE·LOAD_OLDER), 같은 크루 방을 닫았다 다시 열면 목록 유지하고 메우기, CLOSED 상태의 동작(AN5-C-5 결정 필요) | 코드 ChatRoom.kt:72-82, 160 |
| U5-C4 | 06-06 SettingsRepository·SettingsSnapshot | `isGuest()`·`SettingsSnapshot.theme`·`isGuest`, `changes()`는 기기 사본을 처음 읽기 전에는 내보내지 않음(첫 프레임 테마·동의 구독 보호, A4). 로그아웃 게스트 판정 = accountType GUEST ∧ 기기 자격 증명(A2 총괄 결정) | F에는 isGuest 한 줄만 있음 |
| U5-C5 | 06-06 PhotoCompressor(:334) | 작은 그림은 키우지 않고 최소 긴 변 64px까지 줄여도 1MB를 못 넘기면 TOO_LARGE, 투명 PNG는 흰 바탕, OOM의 사유 코드 정하기(AN5-C-2 부기), 원본은 상한+1바이트까지만 읽음 | 코드 PhotoCompressor.kt |
| U5-C6 | 06-06 WatchLink(:340 부근) | Transport 보낼 큐: 경로마다 최신 하나만 남기는 경로(WATCH_DISPLAY·PROXIMITY), 상한 32, 한 코루틴이 차례로 보냄(유실 허용 아닌 LEAD_CHANGE 보존). 이미 넘긴 심박은 측정 시각으로 걸러 한 번만(최근 4,096개 기억). 워치 연결은 능력 변화 리스너 + 시작 때 한 번 조회 | 코드 PlayServicesTransport.kt:84-88, WatchLink.kt:103 |
| U5-C7 | 06-06 6.1.15 watch.alert | AlertVibrator·AlertBeeper·MonotonicClock 포트와 구현(AndroidAlertVibrator USAGE_ALARM, ToneGeneratorBeeper STREAM_MUSIC·재생마다 열고 정지 때 놓음, SystemMonotonicClock). 간격 하한 100ms, 상한 3초로 묶음 | 설계에는 ProximityAlertPlayer만 있음 |
| U5-C8 | 05-02 D-112, 06-06 6.1.14 머리말 | 한 파일 규칙의 적용 범위를 6.1.15(워치)까지 넓힐지 정하기(AN5-C-14). 넓히면 Konsist TC-6583을 :wear까지 | 결정 |
| U5-C9 | 06-06 CrewRepository(:311) | writePoll·writeMeetup 시그니처(폼 값 객체 PollForm·MeetupForm 도입 여부, AN5-C-3). 코드는 현재 낱개 5개 | 결정 |
| U5-C10 | 06-06 AccountType(:246) | 코드가 쓰지 않는 AccountType을 쓰도록 고치기(AN5-C-11)로 해결하면 설계는 그대로. 쓰지 않기로 하면 설계에서 지움 | 결정 |

### 8. 다음 행동

- [ ] AN5-C-1: FallbackLiveChannelProvider에 `tryLease()`를 두고 open()이 쓰게 한다. 스레드 겹침 시험 추가
- [ ] AN5-C-2: PhotoCompressor.compress를 함수 둘로 나누고 64px 미만 그림 시험·투명 PNG 시험 추가
- [ ] AN5-C-4·5·6·7·8: 세션·연결·채팅방·측정 경쟁 보강(7절 시험 표 7건)
- [ ] AN5-C-3·9~18: 경 정리(폼 값 객체, 반복 안 return, Dispatchers 주입, AccountType 사용, toString 마스킹, 예외 처리기 전달, wear 파일 분리, 병합 분리, suspend 포트, 시험, implementation.md)
- [ ] 설계서 갱신: `/sw-design-oo`로 U5-C1~U5-C10 반영
- [ ] 결정 대기 2건(N-14·N3-7)은 사용자·팀 결정 뒤 반영

## 리뷰 2026-10-23 (5차, 안드로이드 (라) 묶음: 화면·core.ui·app)

대상 커밋: feature/runkk-v010 4ee91aef(통합 브랜치, android 쪽 마지막 변경은 bfe55e1c). 범위는 `git diff d7618cb bfe55e1c -- android` 중 (라) 묶음이다: android/feature/*(run, me, avatar, record, share, notification, home, crew, chat 신설, battle, runimal), android/core(ui), android/app, android/build-logic, settings.gradle.kts와 이들의 시험·Roborazzi 기준 이미지. domain·data·wear는 (다) 묶음이 본다. (라) 범위를 포함한 android 쪽 커밋은 d7618cb..bfe55e1c에서 63개(merge 제외 58개)이고 4ee91aef는 약관 페이지(nginx)만 더해 android는 그대로다.

입력: S15P31A207-v010/android, docs/sw/design-oo/06-06 6.1.14(화면·core.ui 행), 05-01 5.1.2(의존 표), 05-02 5.6(D-111·D-112·D-120·D-137·D-140·D-143·D-151~D-155·D-157·D-160·D-162), srs 3.0.3 크루 권한표·NFR-041, implementation.md 3.10, 4차 절(위 02-android.md 4차)과 code-review.md 4차 종합(결정 대기 13건).
방법: 읽기 전용(코드 수정·커밋·gradle 실행 없음). git diff·log, 파일 읽기, 파이썬 약산 스크립트(함수 길이·매개변수·복잡도·제어 중첩·한 파일 한 타입·한글 리터럴·하드코딩 색·Boolean 이름·끊긴 조건식 스캔)만 썼다. 시험도 실행하지 않았다(아래 판정은 코드 읽기 기준이고, 통합 검증은 implementation.md 6.x와 code-review.md 종합 절을 따른다). 복잡도는 약산(조건·반복·catch·논리 연산자·엘비스·when 갈래 +1, 람다 화살표 제외, ±1 오차)이다. 주의: 같은 scratchpad를 쓰는 다른 리뷰가 metrics.py 이름을 쓰고 있어 내 스크립트는 an5d_ 접두어로 따로 두었다.

경로 줄임말(모두 android/ 아래): F/m/ = feature/m/src/main/java/com/a207/runkk/app/ui/m/, C/ = core/ui/src/main/java/com/a207/runkk/app/core/designsystem/, A/ = app/src/main/java/com/a207/runkk/app/.

### 리뷰 기준

4차와 같다. 이름 규칙은 "Boolean은 is·has·can, 화면 문구 하드코딩 금지, Dispatchers 주입, 로그는 Timber", 매개변수 4개 이하(Composable은 modifier·콜백 제외, 생성자 주입은 D-73에 따라 제외), 중첩 3단계, 함수 50줄·Composable 100줄, 순환 복잡도 10 이하(초과는 참고, 집계 제외)다. 더해서 이번 범위의 설계 기준(D-111 문구 리소스, D-112 한 파일 규칙, D-120·D-157 정지·잠금, D-137 isPausable, D-140 공유 카드 의존, D-143 코드 포인트, D-151~D-155 일정·투표, D-160 채팅 목록, D-162 설정 기본값, 크루 권한표 3.0.3, NFR-041 명도 대비)를 쓴다.

### 대상

| 항목 | 내용 |
|------|------|
| 코드 | 변경·추가한 Kotlin 소스 248개(feature·core·app의 src/main, localDebug 포함, 함수 796개 측정) + 시험 파일 48개(대상 모듈의 @Test 전체 650개) + Roborazzi 기준 이미지 56개(추가 33·수정 20·삭제 3) |
| 신설 | :feature:chat(ChatRoomScreen·ChatViewModel·ChatFeatureEntry), 크루 v0.11 화면(공지·투표·일정·게시판·앨범·공동 목표·신고·운영진), AvatarBackgroundScreen·AvatarBackdrop, RunkkReportDialog, RunkkColors 라이트·다크 벌과 RunkkTheme(darkTheme), 게스트 시작·동의 화면 하나·계정 연동, 러닝 일시정지·재개, 알림 이동 표(ui.notification) |
| 구조 | 한 파일 규칙(D-112)으로 파일 쪼개기(crew 소스 64개 등, battle·runimal 포함), CrewGoalCardRenderer를 ui.share로(D-140), ArchitectureTest에 ui.chat·한 파일 규칙·문구 키 규칙 추가 |
| 우선 확인 | 크루 권한표에 따른 버튼 노출, D-143 코드 포인트, D-160 채팅 목록, D-157 정지·잠금 화면, NFR-041 대비(토큰 짝을 직접 계산), 앞 차수 유형 재발(한글 리터럴·Composable 길이·끊긴 조건식·문구 리소스) |

### 4차 지적 추적표 ((라) 범위만)

| ID | 4차 지적 | 이번 확인 | 상태 |
|----|----------|-----------|------|
| N4-1 | 화면 상태 Boolean 이름 | 상태 클래스의 `val x: Boolean`은 is·has·can으로 통일된 채 유지, 새 코드에서 `shouldShowConsentDialog`(F/run/RunUiState.kt:30)와 Composable 지역 상태 6곳이 새로 어긋남 | 일부 재발 → AN5-D-13 |
| N3-1 | 한글 리터럴(화면에 보이는 것) | 주석·@Preview·로그·예외를 뺀 한글 리터럴 재스캔: 화면에 보이는 것 0. 남은 것은 로그 설명 문자열(F/share/DeviceSaveTarget.kt:26·30·48, StoryShareTarget.kt:22, SystemShareTarget.kt:19 `shareFailureOrNull("…")`)과 예외 문구(F/me/SocialLoginClient.kt:24), 로그용 마스크(SocialLoginKeys.kt:11)뿐. 새 영어·단위 문구 3곳이 있다 | 한글은 해결 유지, 영어·단위 3곳 → AN5-D-7 |
| N3-2·Composable 길이 | Composable 100줄·함수 50줄 | 비 Composable 50줄 초과 0, 제어 중첩 3단계 초과 0. Composable 100줄 초과 2건 | 재발 → AN5-D-5 |
| N3-4 | 끊긴 조건식 | `if (kind ==` 줄바꿈 `ReactionKind.LIKE` 형태가 33곳으로 늘었다(3차는 1곳) | 재발 → AN5-D-2 |
| N3-5 | 공유·저장 실패 시험 | ShareFailureTest 유지(+2줄), 새 ShareTarget 셋(DeviceSave·Story·System)도 shareFailureOrNull을 거침 | 유지 |
| 4차 Composable 매개변수 5+ | StatTile·BigNumber·CrewRunEntryButtons 해결 | 새 Composable 7건이 다시 5개 이상 | 재발 → AN5-D-3 |
| S-3 | 한 파일 여러 타입 | 최상위 타입 둘 이상인 파일 0(feature·core·app 스캔), ArchitectureTest TC-6583이 core·app·ui 전체로 넓어졌다(D-112) | (라) 범위 해결, 도메인 2건은 (다) 묶음 |
| N3-7 | 한 커밋에 두 가지 일 | "리뷰 반영" 커밋 4개가 4~7가지를 묶음 | 재발(결정 대기 그대로) → 4절 |
| N-14 | 커밋·브랜치에 Jira 키 | 새 범위 63개 커밋 제목에 키 0, 브랜치 feature/runkk-v010 | 결정 대기 그대로 |
| 참고: 복잡도 10 초과 | verdictMessage 14, onProgress 11 등 | 3절 표(verdictMessage 16, RunViewModel.onProgress 12로 늘었다) | 참고(집계 제외) |

### 1. 설계 일치

의존 방향은 설계와 같다. 모듈 import 집계(feature별 `import com.a207.runkk.app.(domain|core|ui)…` 패키지 수)와 build.gradle.kts의 project 의존을 5.1.2 표와 맞췄다.

| 모듈 | 코드의 import(domain·ui) | 5.1.2 표 | 결과 |
|------|--------------------------|----------|------|
| ui.chat | domain.chat, domain.report, core.designsystem·navigation·model | 같음(5) | 일치. build.gradle.kts에 feature·domain 프로젝트 의존 없음(ModuleRules가 featureBase만 허용, ModuleRules.kt에 `:feature:chat` 추가) |
| ui.crew | domain.crew, domain.report, ui.share(5) | domain.crew, domain.report, ui.share(6) | 일치. crew → :feature:share는 ModuleRules 예외에 추가됨(D-140) |
| ui.record | domain.record, domain.run, domain.avatar, ui.share | 같음(7, 상한) | 일치 |
| ui.home | domain.record, domain.run, domain.account, domain.avatar | 같음(7) | 일치. ArchitectureTest.kt:381 주석("5.1.2 표에는 아직 없다")만 낡았다 → AN5-D-8 |
| ui.share | domain.crew·record·runimal·settings, core.designsystem | 같음(6) | 일치(D-140) |
| ui.run·battle·runimal·avatar·me·notification | 표와 같음 | 같음 | 일치 |
| app | 새 import 없음(MainActivity가 domain.settings·domain.account 구독) | 같음 | 일치. ui.*를 import하지 않음(E2eLoginModule만 localDebug 예외) |

이름·시그니처 대조는 아래 표와 같다. 6.1.14에 없는 이름이 코드에 새로 생긴 곳은 7절(설계서 갱신 요청)로 보냈다.

| 설계 항목 | 코드 | 결과 |
|-----------|------|------|
| ChatRoomScreen·ChatViewModel·ChatFeatureEntry, 보내기 1~500자·리액션·삭제·신고, onShown·onHidden (D-103·D-130) | F/chat/ChatRoomScreen.kt:75 `LifecycleStartEffect`가 onShown·onHidden, ChatViewModel이 ChatRoom.open·close, 보내기 `canSend = draftLength in 1..500`(코드 포인트) | 일치 |
| D-160 삭제·숨김은 자리표시, 실패 재전송, 이전 쪽 | ChatBubble이 `isRemoved`면 자리표시 문구·리액션 없음(ChatRoomScreen.kt:186·203), FAILED 줄과 다시 보내기(:194) | 일치 |
| 크루 권한표 3.0.3(버튼 노출) | F/crew/CrewHomeUiState.kt:56-62 `isLeader`·`canManage`(크루장·운영진)·`canSetGoal`. 관리 메뉴 `takeIf { state.canManage }`(CrewHomeScreens.kt:567), 공지 쓰기·고정 `canManage`(CrewNoticeTab.kt:48), 공동 목표 설정 `canSetGoal`(CrewRaceTab.kt:141), 가입 결정은 크루장·운영진, 초대 코드·운영진 지정·크루원 내보내기·정보 수정·삭제는 `isLeader`만(CrewManageScreens.kt:108), 삭제는 `canDelete`(서버가 본인·운영진 판정), 신고는 `!isMine`(CrewPostRow.kt:60). 읽기 실패 시 `myRole` null이면 모두 숨김(안전한 쪽) | 일치. 크루 나가기 메뉴가 크루장에게도 보이는 것은 표의 "양도 뒤 허용(제안)"을 서버가 거부하는 것으로 처리(CrewHomeViewModel.kt onLeaveConfirm, FR-126) |
| D-120·D-157·D-137 정지 | 러닝 화면의 일시정지·재개는 자유런 화면(RunScreen)에만, `onPauseTapped`·`onResumeTapped`는 RUNNING일 때만(F/run/RunViewModel.kt onPauseTapped), PAUSED_LOCKED는 `PausedNotice(run_paused_locked_notice)`로 해제 안내, 길게 눌러 종료 거부 시 `run_hold_to_unlock`(onFinishLongPressed) | 일치 |
| D-162 기본값 | SettingKeys.DEFAULTS THEME=SYSTEM, BATTLE_PROXIMITY_ALERT=true, 개인정보 항목 false·PRIVATE, 사본 없는 알림 키는 true(SettingsUiState.value) | 일치 |
| D-143 코드 포인트 | 공지·게시글·일정 내용·신고 상세·채팅은 코드 포인트(CrewText.takeCodePoints). 닉네임·응원·투표·장소 등은 UTF-16 그대로 | 부분 불일치 → AN5-D-1 |
| D-151·D-152 | 일정 쓰기 화면은 작성자 GOING을 서버에 맡김(CrewRunWriteUiState 주석), 대기실 단추는 응답과 무관하게 시각으로만 열림(CrewScheduleTab.kt:225 `isEnabled = lobby == LobbyAvailability.Open`) | 일치 |
| D-154·D-155 | 선택지 2~5개·질문 200자·선택지 40자·정원 2~10·장소 1~50(UiState 검사) 일치. 지난 마감은 클라이언트가 먼저 막음 | 일치(글자 수 단위만 AN5-D-1) |
| D-140 공유 카드 | RunCardRenderer.render(card, side, backgroundKey), CrewGoalCardRenderer ui.share 이동(e21c7e6d), ui.record가 `AvatarRepository.backgroundKey()` | 일치(LM_n 키 단계 처리만 AN5-D-14) |
| D-111 문구 | 새 화면의 보이는 문구는 strings.xml·UiText. 영어 3곳이 예외(AN5-D-7) | 대체로 일치 |
| NFR-041 명도 대비 | RunkkColors 토큰 짝을 직접 계산: 라이트·다크 모두 글자 토큰(textPrimary·textSecondary·lime·lavender·onLime)과 배경 토큰(background·surface·surfacePressed·limeContainer·border·backgroundLocked·lime/lavender 위 onLime) 짝이 4.5:1 이상. 비글자(borderStrong·inputBorder·limeDim)는 라이트 3:1 이상. 시험 짝 목록이 실제 쓰는 짝을 모두 덮는다(위 계산에서 미달 짝 0) | 일치 |

### 2. 새 결함

치명 0, 중 1, 경 16이다.

#### 2.1 중 (1건)

| ID | 파일:줄 | 현재 코드 | 교정 예시 | 근거 |
|----|---------|-----------|-----------|------|
| AN5-D-1 | F/me/ProfileEditUiState.kt:13, ProfileEditViewModel.kt:52, ProfileEditScreen.kt:111 (닉네임), F/crew/CrewHomeUiState.kt:49, CrewHomeViewModel.kt:255, CrewCheerTab.kt:21·24 (응원글 200), CrewPollWriteUiState.kt:26-27, CrewPollWriteViewModel.kt:28·37, CrewWriteScreens.kt:111·115·144 (투표 질문 200·선택지 40), CrewRunWriteViewModel.kt:48, CrewRunScreens.kt:192·194 (장소명 50) | `nickname.trim().length in NICKNAME_LENGTH`, `value.take(ProfileEditUiState.NICKNAME_LENGTH.last)`, `cheerDraft.length <= CHEER_MAX`, `text.take(QUESTION_MAX)`, `RunkkTextField(maxLength = …)` (모두 UTF-16 길이) | 같은 파일의 `String.codePointLength()`·`takeCodePoints(max)`(F/crew/CrewText.kt)를 쓰고, 닉네임은 me 모듈에 같은 두 함수를 두거나 core.ui로 올린다: `nickname.trim().codePointCount(0, n) in 2..20`, `onNicknameChange = value.takeCodePoints(20)`. 입력칸 `maxLength`는 쓰지 않고(UTF-16라 이모지가 2로 센다) 글자 수 표시도 코드 포인트로 | D-143 (2)는 "글자 수는 보이는 글자(코드 포인트)로 센다"이고 첫 줄에 Nickname 2~20자를 든다. 서버도 닉네임·모든 게시글 본문(응원·크루런·대면 모임·투표 200, 공지·게시글 1,000)·투표 질문·선택지·장소명을 코드 포인트로 센다(backend CrewPost.java:80-86 "앱과 같은 기준, D-143"). 이번 d7de16b6 "글자 수를 코드 포인트로 센다(D-143)"는 공지·게시글·일정 본문·신고 상세만 고쳐 절반만 적용됐다. 결과: 이모지 하나로 된 닉네임(UTF-16 2)은 앱이 저장 가능으로 보지만 서버가 2자 미만으로 거부하고, 이모지 11~20자 닉네임은 앱이 10자에서 자른다. take가 서로게이트 쌍을 가를 수 있다. 응원글·투표는 앱이 서버보다 엄격(150자 이모지 글을 막음)해 같은 글이 화면마다 다르게 세진다. 시험은 공지·게시글·일정만 코드 포인트를 검증한다(CrewV011ViewModelsTest 3건). 크루 이름·크루 내 소개·제목은 서버도 UTF-16(CrewName.java:19, CrewProfile.java:40)이라 그대로 둔다 |

#### 2.2 경 (16건)

| ID | 파일:줄 | 현재 코드 | 교정 예시 | 근거 |
|----|---------|-----------|-----------|------|
| AN5-D-2 (N3-4 재발) | 서식기가 조건식을 끊은 33곳. 줄 끝이 `==`·`!=`·`<=`로 끝나는 31곳(feature·core·app main 스캔): F/chat/ChatRoomScreen.kt:290, F/me/SignUpViewModel.kt:114, F/notification/InboxViewModel.kt:60, F/avatar/ShopViewModel.kt:75, F/crew/CrewMembersTab.kt:90, CrewPollTab.kt:85·100, CrewRunScreens.kt:348·358·690, F/battle/BattleResultScreens.kt:94, BattleResultText.kt:42, BattleRunningScreen.kt:269·351, MatchViewModel.kt:138, F/record/AnalysisScreens.kt:428, RecordListScreen.kt:309, RecordListViewModel.kt:51·62, F/run/RunResultScreen.kt:321, RunScreen.kt:253, F/share/RunimalCardRenderer.kt:54, C/RunkkBars.kt:93·196, C/RunkkButtons.kt:125·226·271·474, C/RunkkChips.kt:211, C/RunkkFeedback.kt:162, C/RunkkFields.kt:204. 여기에 `||`·`in`으로 끊긴 F/crew/CrewRunWriteViewModel.kt:40-41·55가 더 있다 | `if (kind ==` 다음 줄 `ReactionKind.LIKE` 다음 줄 `) {`(CrewRunWriteViewModel.kt:40은 `if (kind == CREW_RUN ||` 다음 줄 `kind == MEETUP`) | 조건을 이름 있는 값으로 뺀다: `val isLike = kind == ReactionKind.LIKE` 후 `stringResource(if (isLike) … else …)`, 또는 `val isAuthLost = error == AppError.Server(TOO_MANY_REQUESTS)`. 같은 모양이 반복되면 서식기(ktlint) 설정으로 줄 길이를 늘리는 것이 근본 해결 | 3차 N3-4는 한 곳을 이름 있는 값으로 고쳐 해결로 닫았으나 같은 서식 산물이 33곳(줄 끝 비교 연산자 31 + `||`·`in` 2)으로 퍼졌다. 읽기 어렵고(조건 `==`와 피연산자가 다른 줄) 매번 손으로 고칠 수 없는 종류라 서식 규칙 쪽에서 막아야 한다 |
| AN5-D-3 | C/RunkkReportDialog.kt:36 `RunkkReportDialog`(비콜백 12), F/crew/CrewWriteScreens.kt:308 `WriteScaffold`(7), F/crew/CrewHomeDialogs.kt:11 `OtherTabDialogs`(6), F/crew/CrewHomeScreens.kt:79 `CrewHomeRoute`(8), :230 `CrewHomeScreen`(5), :452 `InputDialog`(5), F/avatar/AvatarBackgroundScreen.kt:156 `BackgroundTile`(6) | `fun RunkkReportDialog(title, reasons, selectedIndex, onSelect, detail, onDetail, detailLabel, submitLabel, isSubmitEnabled, onSubmit, onDismiss, detailSupporting, detailError, reasonsLabel, submitHint, dismissLabel)` | 값을 묶는다. 신고 대화상자는 문구 묶음 `ReportDialogTexts(title, reasonsLabel, detailLabel, detailSupporting, submitLabel, dismissLabel)`과 상태 묶음 `ReportDialogState(reasons, selectedIndex, detail, detailError, submitHint, isSubmitEnabled)`로 3개짜리(texts, state, actions)로. BackgroundTile은 `BackgroundTileState(key, stage, name, isSelected, lockedDistance, isChoosing)` 하나로 | 앞 차수 기준(modifier·콜백 제외 4개 이하, 4차에서 StatTile·BigNumber·CrewRunEntryButtons를 고쳐 해결)을 새 Composable이 다시 어겼다. 4차가 지적하지 않은 이전 요소 RunkkCard(7)·RunkkTextField(8)·RunkkCheckRow(5)·RunkkConfirmDialog(5)도 같은 기준이면 해당한다(옵션 묶음 성격이라 이번 집계에서는 새 코드만 센다). 생성자 주입은 D-73에 따라 뺐다(SettingsViewModel 6, MeViewModel 4 등) |
| AN5-D-4 | F/crew/CrewHomeDialogs.kt:11 `OtherTabDialogs(notice, poll, board, notices: CrewNoticeViewModel, polls: CrewPollViewModel, boardVm: CrewBoardViewModel)`, :52 `CrewReportDialog(state, report: CrewReportViewModel)` | `DeleteConfirm(isShown = notice.confirmDelete != null, onConfirm = notices::onDeleteConfirm, onDismiss = notices::onDeleteDismiss)` | Composable은 ViewModel 대신 상태와 콜백만 받는다: `CrewReportDialog(state, actions: CrewReportActions)`. `OtherTabDialogs`는 세 ViewModel을 받지 않고 Route가 `confirmDelete` 상태와 콜백 셋을 넘기는 `DeleteConfirm` 세 번 호출로 줄인다(Route에서 풀어 쓴다) | 이 프로젝트의 화면은 "상태 없는 Screen + Route(ViewModel 연결)"로 나뉘는데(`ChatRoomScreen`·`CrewHomeScreen`) 이 둘은 상태 없는 Composable이 ViewModel 인스턴스를 직접 받아 미리보기·시험에 ViewModel이 필요하고, 호이스팅 위반이다. CrewHomeRoute가 ViewModel 5개를 직접 들고 있는 것도 같은 뿌리(AN5-D-5) |
| AN5-D-5 | F/crew/CrewHomeScreens.kt:79 `CrewHomeRoute`(102줄, 복잡도 22, ViewModel 5개), F/crew/CrewWriteScreens.kt:79 `CrewPollWriteScreen`(104줄, 매개변수 11) | `ForwardMessages(viewModel, noticeState.message to notices::onMessageShown)`를 4번 부르고, `onLoadMore`·`hasMoreOther`·`lazyTab`에서 `when (state.tab)`을 세 번 반복 | Route를 탭별 연결(`NoticeTabHost`, `PollTabHost`, `BoardTabHost` 같은 작은 Composable)로 나누고 `hasMoreOther`·`onLoadMore` 선택을 `CrewTab` 확장 함수로 한 곳에. `CrewPollWriteScreen`은 질문 칸·선택지 목록·마감 선택을 `PollQuestionField`·`PollOptionRows`·`PollCloseFields`로 빼 100줄 아래로 | 4차 기준 Composable 100줄 초과. CrewHomeRoute는 안내 문구를 다른 ViewModel에서 이 ViewModel로 되넘기는(ForwardMessages) 우회까지 두어 구조가 복잡하다. 화면 쪽 단일 안내 줄 하나를 공유하는 상태 소유자(예: 안내 줄 전용 `CrewMessageBus`)가 있으면 우회가 사라진다 |
| AN5-D-6 | F/crew/CrewHomeScreens.kt:300 `item(key = "tab:${state.tab}") { Column { … } }`, CrewScheduleTab.kt:74·76 `posts.forEach`, CrewCheerTab.kt:39, CrewNoticeTab.kt:81, CrewPollTab.kt:69 | 탭 본문(일정·응원·공지·투표)을 한 `item` 안의 Column에 `forEach`로 그린다 | 게시판처럼 글마다 항목: `items(posts, key = { "schedule:${it.postId}" }) { CrewRunPost(it, …) }`. 탭 함수들을 `LazyListScope` 확장으로 바꾸고 `homeTabItems`가 호출 | be71aa98(크루 홈 탭 내용을 지연 목록 하나로)의 목적은 "보이는 것만 그린다"인데 게시판·앨범(CrewBoardTab.kt:121·173)만 항목 단위이고 나머지 4개 탭은 이어 받은 쪽(최대 `COVER_PAGES_MAX` 10쪽까지 갱신)을 전부 한 번에 구성·측정한다. implementation.md 3.10 "남은 일: 크루 홈 지연 목록"이 일부만 반영됐다. 글이 쌓일수록(응원·공지) 첫 프레임 비용이 커진다 |
| AN5-D-7 | F/run/RunScreen.kt:402 `RunkkOverline("PAUSED · ${modeSuffix(…)}", …)`(405의 `"RUNNING · …"`은 이월), F/crew/CrewRaceTab.kt:246 `Text("$km km", …)`, F/crew/CrewPollTab.kt:120 `contentDescription = "$toggleLabel, ${post.summary.take(SUMMARY_CHARS)}"` | 영어 상태 문구·단위·구분 기호를 코드에 직접 씀 | `stringResource(R.string.run_overline_paused, mode)`, `stringResource(R.string.crew_contribution_km, km)`, `stringResource(R.string.crew_poll_toggle_description, toggleLabel, summary)` | D-111 "화면 문구는 strings.xml과 UiText". 새 코드의 "PAUSED"는 보조 기술이 한국어 앱에서 영어로 읽는다. 단위 "km"는 같은 줄의 다른 문구(crew_contribution_row_description)와 달리 리소스가 아니다. 한글 리터럴은 0이라 N3-1은 해결 유지 |
| AN5-D-8 | F/share/CardColors.kt:3-4 주석과 :8-15 값 8개, A/…/ArchitectureTest.kt(app/src/test/java/com/a207/runkk/app/ArchitectureTest.kt) :381 주석, F/crew/CrewRunWriteViewModel.kt:1-9 KDoc | `const val BACKGROUND = 0xFF09090B.toInt()` … ("ui.share는 5.1.2 표상 core.designsystem을 import하지 않으므로 같은 값을 여기 둔다") | `val dark = RunkkColors.dark; const 대신 dark.background.toArgb()`처럼 읽는다(`RunkkColors.dark`는 Compose 밖에서 쓰라고 만든 소문자 속성, C/RunkkColors.kt:10-11). 주석은 지운다. ArchitectureTest.kt:381 "설계서 5.1.2 표(ui.home: …)에는 아직 없다"는 5.1.2에 이미 있어 지운다. CrewRunWriteViewModel KDoc은 "일정 쓰기: 크루런·대면 모임"으로 | ui.share는 이미 core.designsystem을 import한다(RunimalArt·AvatarBackdrop, D-140이 표에 올림). 같은 색 값이 두 곳이라 한쪽만 고치면 카드와 앱이 어긋난다. 낡은 주석 세 곳은 코드와 반대로 읽힌다 |
| AN5-D-9 | F/chat/ChatRoomScreen.kt:356-396(ChatDialogs·labelRes) ↔ F/crew/CrewHomeDialogs.kt:52-84(CrewReportDialog·labelRes), F/chat/res/values/strings.xml·F/crew/res/values/strings.xml의 `*_report_*` 12개 | 같은 신고 대화상자 연결 코드와 같은 한글 문구 12개가 두 모듈에 복제 | RunkkReportDialog에 사유 라벨 리소스를 core.ui가 갖는 `ReportReasonLabels`(사유 5개 문자열, domain을 몰라도 인덱스로)를 두고 두 모듈은 제목·상세 칸 문구만. 또는 core.ui에 `ReportDialogHost(state, reasons…)` | 두 feature는 서로를 import할 수 없어 core.ui가 공통 자리다(RunkkReportDialog가 이미 그 방향). 문구를 한 곳에서 고쳐야 같은 신고가 다르게 보이지 않는다. 사유 라벨이 5.1.2가 허용한 core.designsystem에 domain을 모르고도 둘 수 있다 |
| AN5-D-10 | F/crew/CrewFindScreens.kt:419 `crewMeta(crew, isLeader: Boolean)`, 호출 :398·:483 | `crewMeta(crew, isLeader = true)` (두 호출 모두 true), 본문의 `if (isLeader) null else stringResource(crew.joinPolicy.labelRes)` | 매개변수를 지우고 크루장 이름·인원·거리 한 줄만 만든다: `crewMeta(crew)`. 가입 방식 표시가 다시 필요해지면 그때 함수를 따로 | 이번 diff에서 `crewMeta(crew, isLeader = false)` 호출 한 곳이 지워져 거짓 분기가 죽었다. 제어 결합도(플래그 매개변수)이고 이름도 "크루장 이름을 보일까"라 `isLeader`는 틀린 이름이다 |
| AN5-D-11 | F/me/LoginViewModel.kt:36 `fun onProviderTapped(provider: ProviderType, context: Context)` | `viewModelScope.launch { val token = client.signIn(context) … }` | SettingsRoute처럼 화면 코루틴에서 SDK 창을 띄우고 ViewModel은 토큰만 받는다: `onProviderStart(provider): SocialLoginClient?`, `onToken(token: Outcome<ProviderToken>)` | f60e01cf가 SettingsViewModel에서 "ViewModel 코루틴이 Activity Context를 잡아 화면이 사라진 뒤에도 붙들지 않게" 같은 문제를 고쳤으나 로그인 ViewModel은 같은 구조로 남았다. 3차는 "signIn 동안만 쓴다"로 보았으나 같은 잡음(회전 중 SDK 로그인)이 이번에 인정돼 일관성이 깨졌다 |
| AN5-D-12 | F/crew/src/test/snapshots/ 의 A3b_crew_board_write_dark, A3b_crew_meetup_write_dark, A3b_crew_notice_dark, A3b_crew_poll_dark, A3b_crew_poll_write_dark, A3b_crew_schedule_meetup_dark(라이트 짝 없음). 그 밖의 라이트 벌 미기록: G1~G8 크루 화면, 알림함, 러너멀·상점·셀럽 결과 | 새 화면 6장이 다크만 기록(구 G1~G5도 다크) | CrewV011ScreenshotTest에 `capture(isDark = false)`를 같은 화면에 더해 `*_light.png`를 기록 | FR-203·NFR-041은 라이트·다크 두 벌이고 이번 범위의 색 토큰은 짝 계산으로 보증된다. 그러나 컨트롤 경계·그림자 같은 시각 회귀는 기준 이미지로만 잡히고, 이번에 라이트 기록은 홈·마이·러닝·기록·채팅·아바타·배틀 일부에 그쳤다. 크루 새 화면은 라이트에서 한 번도 기록되지 않았다 |
| AN5-D-13 (N4-1 재발) | F/run/RunUiState.kt:30 `val shouldShowConsentDialog: Boolean`, 지역 상태: F/crew/CrewHomeScreens.kt:554 `var menu`, F/me/MeScreen.kt:95 `confirmLogout`, SettingsScreens.kt:214·457 `askHeartRate`, WithdrawScreen.kt:77 `confirm`, C/RunkkFields.kt:158 `expanded` | `var menu by remember { mutableStateOf(false) }` | `isMenuOpen`, `isConfirmingLogout`, `isAskingHeartRate`, `isConfirming`, `isExpanded`; 상태 속성은 `isConsentDialogVisible` | 4차 N4-1에서 상태 클래스를 is·has·can으로 통일했고 도메인은 D-111이 "should 불허"를 정했다. UI 상태도 같은 규칙으로 읽히게 새 코드부터 맞춘다. 상태 클래스 `val x: Boolean`은 이번 범위 전체에서 위반 1건뿐이다 |
| AN5-D-14 | F/share/RunCardRenderer.kt:44 `context.getDrawable(avatarBackdropRes(backgroundKey))`, C/AvatarBackdrop.kt:27 `avatarBackdropRes(backgroundKey, stage)` | 카드는 `stage` 없이 호출해 `STAGE_OF_KEY`에 없는 키(운영 기준값이 더한 LM_n)는 기본 그림 | `AvatarRepository.backgroundKey()`가 키와 단계를 함께 돌려주는 값(`BackgroundChoice(key, stage)`)을 쓰고 `render(card, side, background)`에 넘긴다 | D-107 (1)은 "운영 기준값이 더한 키는 목록의 stage로 찾는다"이고 AvatarBackdrop KDoc도 그렇다. 배경 고르기 화면·아바타는 단계 그림을 쓰는데 같은 키의 러닝 카드는 기본 그림이라 어긋난다. 22개 기본 키는 영향 없고 운영이 키를 늘릴 때만 드러난다. domain 쪽 시그니처가 걸려 설계서 갱신 요청 U5-D-5와 함께 정한다 |
| AN5-D-15 | F/me/SignUpViewModel.kt:56-72 `onViewLegal`, SettingsViewModel.kt:154-170 `onViewLegal`, SignUpScreen.kt:67·SettingsScreens.kt:73 `runCatching { uriHandler.openUri(url) }` | 두 ViewModel에 같은 고지 문서 분기(`startsWith("http://") \|\| startsWith("https://")`)가 복제, 열기 실패는 삼킴 | `LegalDocumentLoader`(account.legalDocument → 본문 또는 https 주소) 한 곳에. `https://`만 외부로 열고 http는 본문으로 보이거나 거부. 열기 실패는 `message = R.string.me_legal_open_failed` | 같은 규칙이 두 곳이라 한쪽만 바뀐다. 서버 값이 http 주소면 암호화 없는 주소를 그대로 연다. 브라우저가 없거나 막히면 단추가 아무 일도 하지 않는다(문서 열람은 FR-018 필수 고지) |
| AN5-D-16 | F/chat/ChatRoomScreen.kt:166 | `items(newestFirst, key = { it.messageId?.toString() ?: "c:${it.clientMessageId}" })` | `key = { it.clientMessageId?.let { id -> "c:$id" } ?: "m:${it.messageId}" }` | 내가 보낸 메시지는 낙관적 항목(키 "c:…")이다가 서버 확정으로 `messageId`가 붙으면 키가 "123"으로 바뀌어 같은 말풍선이 항목을 다시 만든다(깜빡임·애니메이션·리액션 줄 상태 손실). 내 메시지는 clientMessageId를 계속 가지므로 그것을 우선하면 키가 한 번도 안 바뀐다 |
| AN5-D-17 | F/chat/ChatRoomScreen.kt:343 | `Column(Modifier.padding(bottom = if (overLimit != null) 28.dp else 24.dp)) { RunkkPrimaryButton(…) }` | 보내기 단추를 입력칸 `Row`의 `Alignment.Top`에 두고 입력칸과 같은 높이로 맞추거나, 입력칸 안 보조 문구 높이를 재서 정렬한다(상수 padding 제거) | 보내기 단추를 입력칸 아래 보조 문구 높이(24·28dp)를 짐작해 맞춘다. 글자 크기 확대(WCAG 1.4.4)나 보조 문구가 두 줄이 되면 어긋난다. 스크린샷은 기본 글자 크기만 기록한다 |

### 3. 구조적 프로그래밍: 순환 복잡도 10 초과 함수

함수 50줄 초과는 비 Composable 0, Composable 100줄 초과는 2건(AN5-D-5). 제어문 중첩 3단계 초과는 0이다(`if·for·while·when·try` 블록만 센 스캔, 레이아웃 람다 제외). goto 등가물(라벨 break·continue)·예외로 흐름 제어·반복 안 return은 새로 찾지 못했다. `CancellationException`은 잡는 곳(CrewHomeViewModel.kt:243, SettingsScreens.kt:onLink)에서 다시 던진다.

복잡도 10 초과 7개(약산, 집계 제외, 4차와 같은 기준)다.

| 함수 | 복잡도 | 줄 | 4차 | 메모 |
|------|--------|----|-----|------|
| F/crew/CrewHomeScreens.kt:79 CrewHomeRoute | 22 | 102 | — | AN5-D-5. 탭별 `when`이 세 번 |
| F/notification/InboxDestination.kt:14 inboxDestination | 17 | 17 | — | 11개 `when` 갈래 + 엘비스 3. 표(`Map<String, (Long?) -> NavKey>`)로 바꾸면 1. 같은 11개 이름이 `INBOX_SCREENS`에 한 번 더 있어 두 목록이 어긋날 수 있다(시험이 서버 Screen과 맞추는 것으로 지킴) |
| F/battle/BattleResultText.kt:9 verdictMessage | 16 | 24 | 14 | 4차보다 늘었다. 승·패·무 × 이름·차이 조합을 `ResultVariant`(이름·차이 유무) 하나로 골라 리소스 표를 찾으면 줄어든다 |
| F/run/RunViewModel.kt:215 onProgress | 12 | 30 | 11 | 1km 구간 배너 계산과 단계 전환을 `SplitBanner`·`phaseOf`로 나눈다 |
| F/battle/BattleRunningViewModel.kt:133 onCoordinator | 11 | 30 | — | 같은 `battleId` 확인을 갈래마다 반복 |
| F/crew/CrewHomeScreens.kt:286 homeTabItems | 11 | 31 | — | AN5-D-6과 함께 탭별 `LazyListScope` 확장으로 나누면 줄어든다 |
| F/chat/ChatRoomScreen.kt:203 ChatBubble | 11 | 38 | — | 내 메시지·삭제·상태 조합 분기. 색·모양을 `BubbleStyle`로 |

### 4. 형상 관리

| 활동 | 판정 | 근거 |
|------|------|------|
| 형상 식별 | 예 | 모듈(:feature:chat 신설, settings.gradle.kts) ·패키지가 5.1.2와 1:1이고 ArchitectureTest가 모듈-패키지 대응을 지킨다. versionName 0.1.0(app/build.gradle.kts:41) |
| 버전 제어 | 예 | 커밋 이력 63개(android, merge 제외 58개), 브랜치 feature/runkk-v010. git 태그 0건·versionName은 0.1.0 그대로(v0.10·v0.11 설계 버전과 번호가 다르다, 4차 때와 같은 참고) |
| 변경 통제 | 아니오 | 커밋 제목·브랜치에 Jira 키 0(N-14, 결정 대기 그대로). 요구사항 ID(FR-·D-)는 제목·본문에 있어 변경 요청과의 연결은 본문으로 추적된다. 사용자 결정으로 판정 요인에서 제외 |
| 형상 감사 | 예 | 기준선 d7618cb 대비 diff로 범위가 정해지고(android 전체 706파일), 구현 기록 3.10과 6.x 통합 표가 병합 단계·검증을 남긴다. 단 구현 기록 4절(결정 목록)·9절(설계서 갱신 요청)이 비어 있다(7절 참고) |
| 상태 보고 | 예 | 커밋 본문에 항목별 설명·근거 FR이 있다. CHANGELOG는 없다. 커밋 제목 규칙 `{타입}: {내용}`을 따른다 |

변경 통제 외 눈에 띄는 것: N3-7(한 커밋 한 가지 일) 재발. 5f26965a(35파일: 채팅 재입장·탭 갱신·사진 메모리·접근성 4~5가지), f0b60847(6가지: 워치 메시지·세션 갱신·연결 Job·압축 등, 일부는 (다) 범위), 25960a56(4가지: 테마 읽기 실패·러닝 결과 경쟁·postsCovering·연동 SDK), 49c90574(a11y 대비·아바타 자리 글자색·정지·배경 칸 읽기), 6caeb603(마이 한 커밋에 게스트·동의 하나·유실 고지·연동·테마·근접 알림·태그 삭제 7가지, 45파일). 앞 차수 결정 대기(커밋 이력 재작성은 하지 않고 MR 설명에 적는 방식)를 유지하되, 앞으로의 "리뷰 반영" 커밋은 지적마다 나누는 것이 4차 N3-7 교정 예시다. 통합 한 MR 원칙은 지켜졌다(MR !14).

### 5. 집계와 판정

| 구분 | 치명 | 중 | 경 | 구성 |
|------|------|----|----|------|
| 새 결함 | 0 | 1 | 16 | 중 AN5-D-1, 경 AN5-D-2~AN5-D-17 |
| 4차 잔여((라) 범위) | 0 | 0 | 2 | N3-7(재발), N-14(결정 대기 그대로) |
| 합계 | 0 | 1 | 18 | 복잡도 10 초과 7개는 참고(집계 제외, 넣으면 경 +7) |

판정: **합격** (치명 0, 중 1 ≤ 3, 설계 일치 점검 수행, 형상 관리는 변경 통제 1항목만 아니오이고 그것은 사용자 결정으로 판정 요인에서 제외).
근거: 의존 방향·권한표·정지·잠금·D-160 채팅·NFR-041 모두 설계와 일치한다. D-143 코드 포인트가 절반만 적용된 것(AN5-D-1)은 서버와 어긋나는 입력 검사라 중으로 두었으나 데이터 손실은 아니다. 새로 재발한 유형은 끊긴 조건식(33곳)·Composable 매개변수(7건)·Composable 길이(2건)·문구 하드코딩(3곳)이다.

참고: 시험은 이번 리뷰에서 실행하지 않았다. 통합 검증(android 전체 게이트·스크린샷 verify)은 implementation.md 6.x 표와 code-review.md 종합 절을 따른다. 정적으로 확인한 시험 품질: ChatViewModelTest·ChatRoomScreenTest·CrewV011ViewModelsTest·CrewV011ScreensTest·InboxTest가 요구사항·결정 ID(TC-6486·TC-6495·TC-6535, D-143·D-160 등)를 제목에 달고 갈래(권한표·이모지 1000자·마감 거부·정지 한도·실패 안내)를 구체 값으로 검증하며, @Ignore·assertTrue(true)는 0이다. 약점은 위 AN5-D-1(닉네임·응원·투표·장소의 코드 포인트 시험 없음), AN5-D-12(라이트 기준 이미지)와 6절의 비어 있는 시험 몇 가지다.

### 6. sw-test 전달: 우선 테스트 대상

| 함수·경로 | 약산 복잡도 | 이유 |
|-----------|-------------|------|
| CrewHomeRoute 탭·로드 더 보기 연결(CrewHomeScreens.kt:79-185) | 22 | 탭별 `when` 세 번, 다섯 ViewModel의 bind·refresh·onLoadMore 순서. 현재 시험은 ViewModel 단위와 Screen 단위뿐이고 Route 통합(탭 전환 + 재개 + 더 보기)은 없다 |
| 닉네임 코드 포인트 검사(ProfileEditViewModel.onNicknameChange·canSave) | — | AN5-D-1: 이모지 1자·11~20자·서로게이트 경계(`"😀"`, `"😀".repeat(10)`)의 경곗값 시험이 없다. 응원 200·투표 질문 200·선택지 40·장소 50도 같다 |
| inboxDestination | 17 | 11개 갈래 + id 파싱(숫자 아님·null). InboxTest TC-6535가 갈래별 값을 보나 `targetId`가 숫자가 아닐 때의 갈래(알림 화면 CREW_HOME인데 id가 "abc")를 더 보면 좋다 |
| verdictMessage | 16 | 승·패·무 × 이름 유무 × 격차 유무 조합 12갈래의 표 시험 |
| ChatBubble·ChatMessageRow 상태 조합 | 11 | 내 메시지·남의 메시지 × 삭제됨·보내는 중·실패·정상 × 리액션 유무(8가지). 현재는 삭제됨·실패·리액션을 각각 한 건씩 본다 |
| RunViewModel.onProgress | 12 | 1km 경계(999→1000m, 정지 중 거리 고정), 화면 복귀 첫 진행값에서 배너를 띄우지 않는 규칙(lastSplitKm null) |
| MainActivity 테마 첫 값 전 빈 화면(AppStartupTest·RunkkAppThemeTest) | — | 사본 읽기 실패·첫 값 지연 때 빈 화면이 영영 계속되지 않는지(MainActivity.kt `val theme = stored ?: return@setContent`). 읽기 실패를 사본 없음으로 정하는 쪽은 data 시험이 보나 Activity 쪽 연결은 없다 |
| CardSharer·RunCardRenderer 배경 | — | 알 수 없는 키·null 키·LM_n 키의 그림 선택(AN5-D-14), 앞면 배경이 있을 때 글자가 읽히는지(스크린샷 share_run_card_front_background.png는 한 가지 키만) |

### 7. 설계서 갱신 요청 (`/sw-design-oo` 재호출 목록)

코드가 옳거나 설계가 비어 있는 경우다. 구현 기록 4절(결정 목록)은 비어 있어 아래는 3.10과 이번 읽기에서 모았다.

| ID | 대상 절 | 내용 | 비고 |
|----|---------|------|------|
| U5-D-1 | 05-02 D-143 | 코드 포인트로 세는 항목을 서버 구현과 맞춰 완성: 닉네임 2~20, 채팅 500, 신고 상세 200(현재 문장) + 모든 게시글 본문(응원·크루런·대면 모임·투표 200, 공지·게시글 1,000), 투표 질문 200·선택지 40·장소명 50(서버 CrewPost.codePoints 주석이 이미 "D-143"). 크루 이름(2~?)·크루 내 소개 40·제목 30은 서버가 UTF-16이라 제외임을 적는다 | AN5-D-1의 근거. 앱은 이 표를 따라 고친다 |
| U5-D-2 | 06-06 core.ui 행 | RunkkReportDialog(신고 대화상자, FR-214)·RunkkTextLink·RunkkSegmentRow 등 공용 요소를 목록에 더하고, 신고 사유 문구를 core.ui가 갖는 방향(AN5-D-9)을 정한다 | 6.1.14 core.ui 행은 RunkkColors·RunkkTheme·AvatarBackdrop·UiText·StatTile·BigNumber만 있다 |
| U5-D-3 | 06-06 ui.chat 행 | "이전 메시지 더 보기" 단추(키보드·보조 기술 대안), 새 남의 메시지를 liveRegion으로 알림(WCAG 4.1.3), 리액션·삭제·이전 쪽 읽기 실패를 안내 줄로(f4719f58) | D-160은 실패 재전송까지만 적었다 |
| U5-D-4 | 06-06 ui.crew 행 | 크루 홈 탭 7개(레이스·일정·응원·크루원·공지·투표·게시판), ViewModel 구성(CrewHome·Notice·Poll·Board·Report·Manage·RunWrite·PollWrite·BoardWrite), 사진 규칙(동시에 받고 푸는 3장·썸네일 400px·RGB_565·ViewModel 기억 12장), 채팅 알림 진입 화면(CrewChatEntryRoute, GET /crews/me로 내 크루 확인), 일정 쓰기 정원 2~10 | 행은 "크루 화면 … 채팅방 진입"까지만 적혀 있다 |
| U5-D-5 | 06-06 ui.record·ui.share·domain.avatar, D-107·D-140 | 러닝 카드 배경 키가 LM_n(운영 기준값이 더한 키)일 때 단계 그림을 쓰려면 `backgroundKey()`가 단계도 돌려줘야 한다. 아니면 카드는 기본 그림으로 두고 그 사실을 적는다 | AN5-D-14. (다) 묶음의 domain.avatar와 함께 결정 |
| U5-D-6 | 06-06 ui.me 행, RunViewModel | 연동 SDK 로그인은 화면 코루틴(UNDISPATCHED)에서 띄우고 ViewModel은 Activity Context를 잡지 않는다(AN5-D-11로 로그인도 맞춘 뒤), 게스트 자격 증명은 IO에서 읽는다. RunViewModel은 ended를 먼저 구독(UNDISPATCHED)한 뒤 `takeUnclaimedEnd()`로 화면 부재 중 끝난 자유런을 받는다(RunTracker가 Saved만 10분 보관, 25960a56·a680f9ec) | 도메인 쪽은 (다) 묶음의 RunTracker 행에 적는다 |
| U5-D-7 | 06-06 MainActivity·RunkkNavHost 행 | 저장된 테마를 읽기 전에는 그리지 않고 마지막 값은 rememberSaveable로 두어 Activity 재생성 때 빈 프레임을 없앤다(7151d033, 25960a56). 사본 읽기 실패는 사본 없음(SYSTEM)으로 본다 | D-162와 함께 |
| U5-D-8 | 구현 기록 4절·9절 | 4절(결정 목록)·9절(설계서 갱신 요청)이 비어 있다. 위 U5-D-1~7과 A3a의 "ui.home 의존 추가", 한 파일 규칙의 core·app·ui 전체 확대(5448d41a, 23a903d1, 7a77441f)를 4절에, 이 표를 9절에 옮긴다 | 팀이 읽는 기록이 구현 중 결정과 어긋난다 |

### 8. 다음 행동

- [ ] AN5-D-1(중): 닉네임·응원·투표·장소 코드 포인트(U5-D-1 확정 뒤 같은 `takeCodePoints`로), 경곗값 시험 추가
- [ ] AN5-D-2: 끊긴 조건식 33곳은 서식 규칙으로 줄을 늘리거나 이름 있는 값으로 뺀다(수작업 반복 금지)
- [ ] AN5-D-3~D-5: 신고 대화상자 인자 묶기, ViewModel을 받는 Composable 정리, CrewHomeRoute·CrewPollWriteScreen 쪼개기
- [ ] AN5-D-6: 크루 홈 탭 4개(일정·응원·공지·투표)를 글마다 항목으로
- [ ] AN5-D-7~D-11, D-13: 문구 3곳, CardColors 복제, 신고 문구 중복, 죽은 `isLeader` 플래그, LoginViewModel Context, Boolean 이름
- [ ] AN5-D-12: 새 크루 화면 6장 라이트 기준 이미지
- [ ] AN5-D-14~D-17: LM_n 카드 배경, 고지 문서 로딩 한 곳·https만, 채팅 key·보내기 단추 정렬
- [ ] 설계서 갱신: `/sw-design-oo`로 U5-D-1~8 반영(구현 기록 4절·9절 채우기 포함)
- [ ] 결정 대기 N3-7·N-14는 사용자 결정 그대로

## 리뷰 2026-10-23 (6차, 안드로이드)

대상 커밋: feature/runkk-v010 a27c914f(5차 반영 확인용). 범위는 `git diff 4ee91aef a27c914f -- android`다(5차 결함 수정 안드로이드 커밋 28개, 파일 138개 +1,975 −827 중 시험 이미지 제외). 5차 (다) 묶음(domain·data·contract·wear·wear-contract)과 (라) 묶음(feature·core·app)을 한 절에서 확인한다. docs 변경은 이 범위에 0건이다.

입력: S15P31A207-v010/android(리뷰 작업본은 같은 코드의 S15P31A207-v010-r6an 워크트리, 안드로이드 쪽은 99fb4a14와 a27c914f가 같음), 5차 (다)·(라) 절(02-android.md 914행~), design-oo 06-06(v0.11) 6.1.14·6.1.15, 05-01 5.1.2, 05-02 5.6 D-167~D-189(특히 D-183~D-189), implementation.md, 4차·5차 형상 관리 판정.

방법: 읽기 전용(소스 수정·커밋 없음). git diff·log, 파일 읽기, 파이썬 약산 스크립트(미사용 import·함수 길이·매개변수·빈 줄·줄 길이)와 아래 세 가지 실행만 했다. 빌드 산출물 폴더만 바뀌고 소스·git 상태는 그대로다.
- `./gradlew :wear:ktlintMainSourceSetCheck --offline --rerun-tasks`: 성공(7 tasks executed). 미사용 import가 있어도 CI 린트 게이트(`ktlintCheck`)는 통과한다는 뜻이다.
- `./gradlew :domain:test --tests '*RunTrackerRaceTest'`: 3건 통과, 합계 9.2초(4.1 + 3.0 + 2.2).
- `./gradlew :data:testLocalDebugUnitTest --tests '*LiveChannelLeaseTest'`: 10건 통과, 합계 2.3초(5만 회 경쟁 시험 포함).
전체 시험·스크린샷 verify는 돌리지 않았다(통합 검증은 implementation.md 6.x를 따른다). 경쟁 시험이 수정 전 코드에서 실제로 실패하는지는 소스를 되돌려 보지 않아 확인하지 못했다(AN6-6).

### 리뷰 기준

5차와 같다: 매개변수 4 이하(생성자 주입은 D-73, 값 객체 생성자 제외, Composable은 modifier·콜백 제외), 중첩 3단계 이하, 함수 50줄·Composable 100줄 이하, 순환 복잡도 10 이하(초과는 참고, 집계 제외), 결합도 스탬프 이하·응집도 통신적 이상·Fan-Out 7 이하. 이름: Boolean은 is·has·can, 화면 문구 리소스(D-111), Dispatchers 주입, 로그 Timber, 한 파일 규칙(D-112, 워치까지 D-183). 이번에 더한 점검: 동시성 수정의 정확성(락 범위·교착·확인과 쓰기의 원자성), 경쟁 시험의 CI 시간과 플레이크 위험.

### 대상

| 구분 | 내용 | 줄(+ / −) |
|------|------|-----------|
| (다) data | api.core(SessionRefresher·TokenProvider), data.live(Fallback 임대·WebSocket 재연결), data.session(EncryptedTokenStore), api.crew(PhotoCompressor·CrewRepositoryImpl), api.account·api.avatar | 약 330 / 140 |
| (다) domain | run(RunTracker·RunWatchBridge), chat(ChatRoom), crew(PollForm·MeetupForm), account(SessionRepository), avatar(BackgroundChoice) | 약 140 / 70 |
| (다) wear | 한 파일 규칙 분리(alert·heart·link·ui, 신규 파일 14개) | 225 / 174 |
| (라) feature·core·app | 신고 대화상자 묶음, 코드 포인트(core.ui RunkkTextLength), 크루 홈 탭 지연 목록, 로그인·고지 문서, 공유 카드 색·배경 단계, Boolean 이름, 문구 리소스 | 약 900 / 450 |
| 시험 | RunTrackerRaceTest(신규)·LiveChannelLeaseTest·SessionRefresherTest·EncryptedTokenStoreTest·WebSocketLiveChannelTest·ChatRoomTest·PhotoCompressorTest 등 | 약 700 |
| 기준 이미지 | 새 크루 화면 라이트 6장 | |

### 5차 지적 추적표

#### (다) 묶음 AN5-C-1~18

| ID | 5차 지적 | 이번 확인(파일:줄) | 상태 |
|----|----------|--------------------|------|
| AN5-C-1 (중) | 임대가 막 끝난 연결을 고름 | FallbackLiveChannelProvider.kt:57 `current?.tryLease() ?: Connection().also { current = it }.lease()`, `tryLease()`가 `synchronized(stateLock)` 안에서 `isEnded` 확인과 `leases++`를 함께 함. 락 순서는 open: lock → stateLock, release: stateLock을 놓은 뒤 lock(중첩 역순 없음), follow: 둘을 차례로. 시험 2건(스레드 둘 5만 회 + 반납 뒤 open) | 해결 |
| AN5-C-2 (중) | 64px 미만 그림 거부, 중첩 4 | PhotoCompressor.kt:69-98 `encodeWithinLimit`(do-while, 처음 긴 변은 반드시 시도)·`encodeAtBestQuality`(시퀀스). 순서·하한·0.8배는 이전과 같고 D-189와 일치. 시험: 48×48 PNG 성공(JPEG, 키우지 않음), 투명 PNG 흰 바탕, 48px에서 품질 85→50만 낮추고 TOO_LARGE | 해결 |
| AN5-C-3 | 매개변수 5(writePoll·writeMeetup) | CrewRepository.kt `writePoll(crewId, form: PollForm)`·`writeMeetup(crewId, form: MeetupForm)`, 값 객체 파일 둘, 호출부·시험 갱신, D-184 | 해결 |
| AN5-C-4 | 재연결 401 무한 재시도 | WebSocketLiveChannel.kt:278-322 `tryConnect()`가 HandshakeFailed 401이면 참 → 다음 시도에서 갱신부터, 거부면 `closeForGood`. 시험 3건(갱신 거부·4401 직후 401·일시 오류 이어 가기, 가상 시계). 새 허점은 AN6-3 | 해결 |
| AN5-C-5 | 첫 쪽 읽기가 핸드셰이크 뒤로 밀림, CLOSED 정지 | ChatRoom.kt:215-259 connect를 따로 띄우고(실패하면 `isConnectFailed`) `effectiveState`로 CLOSED 취급, `POLLING, CLOSED -> pollEvery`. 시험 3건(늦은 connect·CLOSED 폴링·connect 실패 폴링) | 해결 |
| AN5-C-6 | 이전 쪽 표지가 영영 참 | ChatRoom.kt:166-175 `launch { … }.invokeOnCompletion { loadingOlder.set(false) }`. 시험: loadOlder 직후 close·open 뒤 다시 loadOlder | 해결 |
| AN5-C-7 | tick의 낡은 판단 | RunTracker.kt:158-176·309-328: `endAfterPauseLimit(active, now)` → `endIfStillPaused`가 lock 안에서 `session === active && isActive && pause ≥ 한도`를 다시 확인하고 같은 lock에서 `end`와 `rememberFinished`, `publishWhileActive`가 lock 안에서 세션·활성 확인 뒤 publish. 정확성 분석은 아래 "점검했으나 결함 아님". 99fb4a14가 정지·재개 뒤 표시에도 같은 확인을 씌움. 시험 3건. tick의 `coach.guide`는 그대로(AN6-5) | 해결 |
| AN5-C-8 | 토큰 갱신·로그아웃 check-then-act | EncryptedTokenStore.kt:72-80 세대 확인과 저장을 같은 lock, `clear()`가 lock 안에서 `generation++`, SessionRefresher.kt:69-100이 요청 전에 읽은 세대로 `updateInSession`. 시험: 응답 직후 clear를 끼우는 훅으로 결정적(일반 갱신·게스트 재개 둘). 단 `save()`로 바뀐 토큰은 세대가 막지 못함(AN6-2) | 해결(퇴보 1건은 AN6-2) |
| AN5-C-9 | 반복 안 return 3곳 | writeBoard는 map 밖에서 실패를 거름(CrewRepositoryImpl.kt:190-191), reconnectLoop는 `while (scope.isActive && !isSettled())`로 해결. ProximityAlertPlayer.play(:101-110)는 `while (true)` 안 `return` 둘 그대로 | 일부(play 미해결) |
| AN5-C-10 | Dispatchers.IO 하드코딩(CrewRepositoryImpl) | 생성자 `ioDispatcher`, `@Inject` 보조 생성자가 `Dispatchers.IO`를 기본으로(EncryptedTokenStore와 같은 방식). 압축·사진 읽기 둘 다 주입 사용 | 해결 |
| AN5-C-11 | AccountType 미사용 | AccountMappers.kt:83 `enumByName<AccountType>(accountType) == AccountType.GUEST`, 문자열 상수 삭제, D-184 | 해결 |
| AN5-C-12 | 비밀 DTO toString 마스킹 | GuestSessionBody·SocialLinkRequest·LoginRequest·TokenPairResponse 변경 없음 | 미해결 |
| AN5-C-13 | 워치 요청 예외를 삼킴 | RunWatchBridge.kt:53 `scope.coroutineContext[CoroutineExceptionHandler]?.handleException(...)`. 앱 범위(AppScopeModule.kt:25)가 Timber.e로 남김, 워치 범위(WatchModule.kt:61)도 있음. `lastDeliveryFailure` 삭제, 시험은 핸들러로 받은 예외를 확인 | 해결 |
| AN5-C-14 | 워치 한 파일 규칙 | 5차 3곳(AlertPorts·AndroidAlertOutputs·WatchUiState·PauseControl) 모두 타입마다 파일로 분리, 추가로 heart(5)·link(WearMessage)·ui(ForegroundServiceLauncher 등)도 나눔. 전체 main 소스 스캔 결과 최상위 타입 둘 이상인 파일은 sealed·짝 UseCase 예외 3 + core.model FaceLayer(sealed) 뿐. 구조 시험 TC-6583(워치)이 :wear를 봄. D-183 | 해결 |
| AN5-C-15 | ChatRoom 406줄·책임 5 | ChatRoom.kt 414줄(+8). 병합 분리 없음 | 미해결 |
| AN5-C-16 | hasGuestCredential 동기 포트 | `suspend fun hasGuestCredential() = withContext(ioDispatcher) { … }`, LogoutUseCase는 suspend 안이라 그대로 호출, 시험(디스패처 계수). 그러나 호출자의 IO 감싸기를 걷지 않음: MeViewModel.kt:51·SettingsViewModel.kt:151이 다시 `withContext(ioDispatcher)`로 감싸고 생성자 인자 `ioDispatcher`도 남음 | 일부 |
| AN5-C-17 | 시험 품질 | 경쟁·401·갱신 중 로그아웃·작은 그림·CLOSED·이전 쪽·60분 직전 재개 시험이 모두 생김. SessionRefresherTest.kt:86 `Thread.sleep(SLOW_MILLIS)`와 FallbackLiveChannelProviderTest의 실제 소켓·실시간 delay는 그대로 | 일부 |
| AN5-C-18 | implementation.md 4·9장 비어 있음 | 이 범위의 docs 변경 0건. 5장 계약 갱신일도 그대로 | 미해결 |

(다) 묶음 요약: 18건 중 해결 12(C-1·2·3·4·5·6·7·8·10·11·13·14), 일부 3(C-9·16·17), 미해결 3(C-12·15·18). 중 2건(C-1·C-2)은 모두 해결이다.

#### (라) 묶음 AN5-D-1~17

| ID | 5차 지적 | 이번 확인(파일:줄) | 상태 |
|----|----------|--------------------|------|
| AN5-D-1 (중) | D-143 코드 포인트 절반만 적용 | core.ui RunkkTextLength.kt(`codePointLength`·`takeCodePoints`)로 올리고 crew 쪽 중복(CrewText.kt)·chat의 private 사본을 삭제. 닉네임(ProfileEditUiState.kt:14, ViewModel:53)·응원(CrewHomeUiState.kt:50, ViewModel:257)·투표 질문·선택지(CrewPollWriteUiState.kt:26-31, ViewModel:30·39)·장소명(CrewRunWriteViewModel.kt:50, CrewRunScreens.kt:193)이 코드 포인트, 입력칸 `maxLength`는 제거하고 VM이 자름. 서버와 같은 단위(D-143·U5-D-1). 시험: RunkkTextLengthTest(이모지·서로게이트) + me·crew ViewModel 시험. 크루 이름·소개·제목은 서버 UTF-16이라 그대로(맞음) | 해결 |
| AN5-D-2 | 끊긴 조건식 33곳 | 줄 끝이 `==`·`!=`·`<=`인 곳 31(5차와 같음), `||`·`in` 포함 시 45. 서식 규칙·이름 있는 값 어느 쪽도 하지 않음 | 미해결 |
| AN5-D-3 | Composable 매개변수 5+ | RunkkReportDialog 비콜백 2(texts·state), WriteScaffold 1(`WriteScaffoldState`), InputDialog 3(`InputDialogTexts`), OtherTabDialogs 3, BackgroundTile 1(`BackgroundTileState`). 남음: CrewHomeRoute(8, Hilt ViewModel 기본값 5 포함), CrewHomeScreen(비콜백 5) | 일부 |
| AN5-D-4 | Composable이 ViewModel 직접 수령 | OtherTabDialogs(deletes, viewerPhotoId, boardActions), CrewReportDialog(state, `CrewReportActions`). 둘 다 상태와 콜백만 받음. 단 CrewHomeRoute는 여전히 ViewModel 5개를 직접 듦(D-5) | 해결 |
| AN5-D-5 | Composable 100줄 초과 2 | CrewHomeRoute 114줄(5차 102, 늘었음)·복잡도 약 22 그대로, CrewPollWriteScreen 104줄 그대로 | 미해결(악화) |
| AN5-D-6 | 크루 홈 탭 4개 한 항목 | 일정·응원·공지·투표가 `LazyListScope.xxxTabItems`로 글마다 `items(key = "…:${postId}")`(CrewScheduleTab.kt:42, CrewCheerTab.kt:23, CrewNoticeTab.kt:51, CrewPollTab.kt:63), 머리는 한 항목. 레이스·크루원은 한 항목 유지(글 목록이 아님). 키 접두어가 서로 달라 충돌 없음. 시험·스크린샷 갱신 | 해결 |
| AN5-D-7 | 영어·단위 문구 3곳 | `run_overline_running/paused`(translatable=false, 보조 기술은 `core_spoken_paused`와 spokenLabel 시험 한 줄), `crew_contribution_km`, `crew_poll_toggle_description` | 해결 |
| AN5-D-8 | CardColors 복제·낡은 주석 | CardColors.kt `RunkkColors.dark.xxx.toArgb()`, share/build.gradle.kts 주석 정정, ArchitectureTest 주석 둘 정정. 단 CrewRunWriteViewModel KDoc은 이미 고쳐져 있음 | 해결 |
| AN5-D-9 | 신고 문구 12개 복제 | core.ui `ReportReasonLabels`(ORDER 인덱스)·`reportDialogTexts`, chat·crew의 `*_report_*` 문자열 10개씩 삭제하고 core.ui에 한 벌. chat·crew에 `ReportReasonOrderTest`(ReportReason.entries 순서 고정). D-186 | 해결 |
| AN5-D-10 | 죽은 isLeader 분기 | `crewMeta(crew)`로 매개변수·분기 삭제(CrewFindScreens.kt:419), 호출 둘 갱신 | 해결 |
| AN5-D-11 | LoginViewModel이 Activity Context 보유 | LoginViewModel `onLoginStart(provider): SocialLoginClient?`·`onLoginToken`·`onLoginCancelled`, LoginRoute가 화면 코루틴(UNDISPATCHED)에서 `client.signIn(context)`, 취소는 진행 표시만 풂. 시험 +26줄 | 해결 |
| AN5-D-12 | 크루 새 화면 라이트 기준 이미지 없음 | A3b_crew_{board_write, meetup_write, notice, poll, poll_write, schedule_meetup}_light.png 6장 추가, 시험이 `isDark` 인자로 두 벌을 같은 함수에서 만듦. poll_light 내용 확인(빈 이미지 아님). 그 밖 라이트 벌 미기록(G1~G8·알림함 등)은 5차에서도 참고였음 | 해결(6장) |
| AN5-D-13 | Boolean 이름 7곳 | `isConsentDialogVisible`, `isMenuOpen`, `isConfirmingLogout`, `isAskingHeartRate`(둘), `isConfirming`, `isExpanded`. `var … by remember { mutableStateOf(false) }` 전체 스캔에서 is·has·can 아닌 것 0 | 해결 |
| AN5-D-14 | 러닝 카드 LM_n 배경 | domain `BackgroundChoice(key, stage)`·`AvatarRepository.backgroundChoice()`, api가 /avatar/backgrounds에서 만들고, ui.record가 key·stage를 문자열로 풀어 `RunCardRenderer.render(card, side, key, stage)`. 시험: LM_99 + EAST_ASIA면 기본 그림과 다름. D-185, 05-01 5.1.2 ui.record 행에도 반영 | 해결 |
| AN5-D-15 | 고지 문서 http·복제·실패 삼킴 | `legalLinkOrNull`(https만), SignUp·Settings 두 ViewModel이 사용, 열기 실패는 `me_legal_open_failed`(SignUp은 error, Settings는 message) | 해결 |
| AN5-D-16 | 채팅 항목 키 | `ChatMessage.listKey()` = `clientMessageId?.let { "c:$it" } ?: "m:$messageId"`(ChatRoomScreen.kt:419). ChatRoomScreenTest +10줄 | 해결 |
| AN5-D-17 | 보내기 단추 정렬 상수 | ChatRoomScreen.kt:346 `padding(bottom = if (overLimit != null) 28.dp else 24.dp)` 그대로 | 미해결 |

(라) 묶음 요약: 17건 중 해결 13(D-1·4·6·7·8·9·10·11·12·13·14·15·16), 일부 1(D-3), 미해결 3(D-2·5·17). 중 1건(D-1)은 해결이다.

추적 합계: 35건 중 해결 25, 일부 4(C-9·C-16·C-17·D-3), 미해결 6(C-12·C-15·C-18·D-2·D-5·D-17). 중 3건(AN5-C-1·C-2·D-1)은 모두 해결이다. 보고된 24건(C-3·4·5·6·7·8·10·11·13·14·16, D-3·4·6·7·8·9·10·11·12·13·14·15·16) 가운데 해결 22, 일부 2(C-16·D-3)이고 거짓 보고는 없다(C-16·D-3은 보고도 "일부"로 읽을 수 있는 범위만 고침).

### 1. 설계 일치

이름 대조: D-183~D-189와 5차 설계서 갱신 요청 U5-C·U5-D 전부가 05-02 5.6에 올라 있고(D-183 한 파일 범위 :wear, D-184 PollForm·MeetupForm·AccountType, D-185 BackgroundChoice, D-186 ReportReasonLabels·ReportDialogTexts·State, D-187 워치 출력 포트, D-189 사진 압축 경계), 코드의 이름이 그와 같다. 05-01 5.1.2 ui.record 행도 `backgroundChoice()`로 고쳐 있다.

인터페이스 대조: 시그니처가 모두 설계와 같다. `RunCardRenderer.render(card, side, backgroundKey, backgroundStage)`(D-185 그대로), `RunkkReportDialog(texts, state, onSelect, onDetail, onSubmit, onDismiss)`(D-186), `CrewRepository.writePoll(crewId, form)`(D-184). 코드가 설계에 없이 새로 만든 공개 타입은 `TokenProvider.sessionGeneration()`·`updateInSession()`(AN5-C-8 교정, 6.1.14 api.core 행에 없음), `RunkkTextLength`의 `codePointLength`·`takeCodePoints`(core.ui로 이동, D-143과 일치), `CrewReportActions`·`TabDeleteConfirm`·`InputDialogTexts`·`WriteScaffoldState`·`BackgroundTileState`(ui 내부 값 묶음)다. 앞의 둘은 7절 설계서 갱신 요청이다.

의존 방향(5.1.2): 새 화살표는 없다. ui.share → core.designsystem(D-140, `RunkkColors.dark`), ui.chat·ui.crew → core.designsystem(ReportReasonLabels, 이미 있던 화살표). ModuleRules·ArchitectureTest의 주석 정정 외 규칙 변경 없음. ReportReasonLabels는 domain을 모르고 사유 이름 문자열 목록(`ORDER`)과 시험 둘로만 domain.report.ReportReason과 묶이는 데이터 결합이다(문자열 5개라 스탬프 아님). 내용·공통·외부 결합도는 없다.

응집도 회귀: ChatRoom이 414줄로 5차보다 8줄 늘었다(AN5-C-15 미해결). 그 밖의 변경은 책임이 한 문장이다.

### 2. 새 결함

치명 0, 중 0, 경 7이다. 아래는 이번 수정분에서 새로 생긴 것이고, 5차에서 이어진 미해결·일부는 추적표에 있다.

| ID | 파일:줄 | 항목 | 현재 코드 한 줄 | 교정 예시 한 줄 | 등급 |
|----|---------|------|-----------------|-----------------|------|
| AN6-1 | wear: alert/AndroidAlertVibrator.kt, alert/SystemMonotonicClock.kt(미사용 12), alert/ToneGeneratorBeeper.kt(9), heart/ExerciseInUseException·ExerciseOwner·SensorPermission, link/WearMessage(`Flow`), ui/AndroidForegroundServiceLauncher.kt(15), ui/ForegroundServiceLauncher.kt(5), ui/HeartRateCollectionService.kt(3), ui/NotificationPermissionGate.kt(7). 그 밖 data CrewRepositoryImpl.kt:30·domain CrewRepository.kt:5(`Instant`), RunkkFields.kt:12(`height`), RunScreen.kt:26(`width`), MeScreen.kt(`FlowRow` 외 5), CrewWriteScreens.kt:47(`UiText`) | 쓰지 않는 import | `SystemMonotonicClock.kt`가 `Context`·`AudioAttributes`·`Vibrator`·`Timber` 등 12개를 import하고 본문은 `SystemClock` 한 줄 | 분리하면서 원본의 import 머리를 통째로 복사한 것이다. 쓰는 것만 남긴다(IDE의 Optimize Imports). ktlint가 잡지 않으므로 규칙으로 막지 못한다 | 경 |
| AN6-2 | data/session/EncryptedTokenStore.kt:72-80·118, api/core/SessionRefresher.kt:69-100 | 세대 확인이 `clear()`만 잡는다. 5차 이전 코드가 `tokens.refreshToken() == refresh`로 막던 "갱신 응답을 기다리는 사이 `save()`(새 로그인)가 토큰을 바꾼 경우"를 못 막아 응답이 새 로그인의 토큰을 덮어쓴다 | `generation++`는 `clear()` 안에만 있고 `save()`·`update()`는 세대를 올리지 않음 | `updateInSession`이 세대와 함께 "요청 전에 읽은 갱신 토큰"도 같은 lock 안에서 비교하거나, `save()`(로그인)도 `generation++` | 경 |
| AN6-3 | data/live/WebSocketLiveChannel.kt:294-322 | 재연결 루프에 상한이 없다: 갱신은 Renewed인데 핸드셰이크가 계속 401이면(서버가 이 소켓만 거부, 계정 정지 등) 5초마다 갱신(토큰 회전)과 연결이 끝없이 반복. 5차 AN5-C-4 교정이 "401이면 다음 시도에서 갱신부터"만 정했다 | `catch (e: HandshakeFailed) { e.code == UNAUTHORIZED }` → 매번 `refresh()` 후 다시 `tryConnect()` | 갱신 직후 401이 연속 세 번이면 `closeForGood()`(또는 간격을 늘린다). 시험: refresh=Renewed·handshake=401 고정일 때 N번 뒤 CLOSED | 경 |
| AN6-4 | data/api/crew/CrewRepositoryImpl.kt:190-195 | writeBoard가 첫 사진이 실패해도 나머지 사진을 모두 압축한다(최대 5장 디코딩·인코딩, 이전에는 첫 실패에서 멈춤). 성공 추출도 `filterIsInstance<Outcome.Success<ByteArray>>()`로 제네릭을 지운 검사(컴파일러 경고, 형 안전하지 않음) | `val compressed = photoUris.map { uri -> withContext(ioDispatcher) { photos.compress(uri) } }` | `val compressed = mutableListOf<Outcome<ByteArray>>(); for (uri in photoUris) { compressed += …; if (compressed.last() is Outcome.Failure) break }`, 부분은 `mapNotNull { (it as? Outcome.Success)?.value }` | 경 |
| AN6-5 | domain/run/RunTracker.kt:162 | AN5-C-7 교정이 워치 표시(publish)만 lock 안에서 다시 확인했고 같은 낡은 판단을 쓰는 `coach.guide(progress, active)`는 lock 밖 그대로다. 정지 버튼·종료와 겹치면 정지 중이나 종료 뒤에 음성·진동 안내가 한 번 나갈 수 있다(FR-049). 확률은 낮고 한 번으로 끝남 | `pause == null -> coach.guide(progress, active)` | `publishWhileActive`와 같이 `isStillRunning(active)`(lock 안 `session === active && isActive && !isPaused`)가 참일 때만 guide. 안내 재생은 lock 밖에서 | 경 |
| AN6-6 | domain/src/test/…/run/RunTrackerRaceTest.kt, data/src/test/…/live/LiveChannelLeaseTest.kt(AN5-C-1 시험) | 경쟁 시험이 확률적이다. 실제 스레드를 배리어로 동시에 출발시켜 2만(×3)·5만 회 돌리는 방식이라 맞는 구현에서는 항상 통과하지만, 수정 전 코드에서 실패한다는 기록이 없고 창이 나노초 단위라 못 잡고 지나갈 수 있다. 같은 PR의 AN5-C-8 시험은 훅(응답 직후 clear)으로 결정적이라 방식이 일관되지 않다. 시간·플레이크는 문제 없음: 내 PC에서 9.2초와 2.3초, 단언은 "횟수 0"뿐이고 시간 의존 단언은 없으며 라운드마다 5초 제한, `CyclicBarrier`는 라운드 끝에 두 작업이 모두 끝나 재사용 안전. 느린 CI(2코어)라면 곱 2~3배(약 25초)로 예상 | `repeat(ROUNDS) { … race({ tick }, { onResumeTapped }) }` | tick·release 사이에 훅을 끼운 결정적 시험을 하나씩 더하고(RunTracker는 `watchBridge`·`coach` 가짜가 락 밖 호출에서 `onResumeTapped()`를 부르게, Fallback은 `current` 읽기 직후 반납을 부르는 가짜 `Connection`), 확률 시험은 라운드를 줄여 "보조"로 둔다. 수정 전 코드에서 실패하는지 한 번 기록 | 경 |
| AN6-7 | feature/crew/…/CrewHomeScreens.kt:238, :305-310·:309, data/live/WebSocketLiveChannel.kt:289, feature/record/…/RecordCardViewModel.kt:65 | 정리 잔재 넷: (a) CrewHomeScreen KDoc이 지운 매개변수 `[otherTab]`을 가리킴(공지·투표는 이제 lazyTab) (b) `homeTabItems`의 `when` 마지막 갈래에 군더더기 `return`(함수가 끝나는 자리) (c) `isSettled()`가 이름은 질문인데 `reconnecting = null`을 바꿔 부수 효과가 있음 (d) `val (key, stage) = chosen?.key to chosen?.stage`가 Pair를 만들어 바로 해체 | `* 공지·투표는 [otherTab]으로, 글이 많은…`, `private fun isSettled(): Boolean = synchronized(lock) { (closed \|\| socket != null).also { settled -> if (settled) reconnecting = null } }` | KDoc에서 `[otherTab]` 삭제, `return` 삭제, `isSettled`는 판단만 하고 비우기는 루프 끝에서(`finally`나 `releasingOnExit`가 이미 비움), `val key = chosen?.key; val stage = chosen?.stage` | 경 |

#### AN6-2 상세(세대 확인의 빈틈)

`TokenProvider.updateInSession(generation, …)`은 요청 전에 읽은 세대가 지금도 같을 때만 저장한다. 세대는 `clear()`에서만 오른다. 5차 이전의 `tokens.refreshToken() == refresh`는 "내가 보낸 갱신 토큰이 아직 저장돼 있다"를 확인해서 로그아웃(토큰 삭제)과 `save()`(다른 토큰으로 교체) 두 경우를 모두 막았다. 새 방식은 앞쪽만 막는다. `save()`는 LoginUseCase·StartAsGuestUseCase가 로그아웃(`clear`) 뒤에 부르므로 평소 세대가 이미 올라 있지만, 로그아웃 없이 바로 로그인 화면으로 가는 흐름(세션 만료 안내 뒤 재로그인 등)이 생기면 늦은 갱신 응답이 새 로그인을 덮는다. 지금 코드의 흐름에서는 드물어 경으로 뒀다. 교정은 한 줄이다: 세대를 `save()`에서도 올리거나, `updateInSession`에 `expectedRefresh` 인자를 더해 `synchronized(lock) { if (generation == g && memory.value?.second == expectedRefresh) … }`.

#### 점검했으나 결함 아님(통과)

| 점검 요청 | 결과 |
|-----------|------|
| tryLease 락 범위·교착 | `Connection.tryLease`가 stateLock 안에서 `isEnded` 확인과 `leases++`·Lease 생성을 함께 한다. `release`는 stateLock 안에서 `leases--`·`end()`(isEnded=true)를 하고 락을 놓은 뒤 `closing?.close()`와 `synchronized(lock) { current = null }`. open은 lock → stateLock 순, release는 stateLock 해제 뒤 lock이라 두 락을 역순으로 쥐는 곳이 없다. release가 끝난 연결을 `current`에서 지우기 전에 open이 끼어들면 `tryLease`가 null을 돌려 새 연결을 만들고, 뒤늦은 release의 `if (current === this)`가 새 연결을 지우지 않는다. `lease()`는 갓 만든 연결에만 부르므로 `checkNotNull`이 던질 수 없음 |
| RunTracker endIfStillPaused·publishWhileActive | `endIfStillPaused`는 lock 안에서 세션 동일·활성·정지 한도를 모두 다시 보고 `end`+`rememberFinished`까지 한 번에 하므로 "한도 판단 뒤 재개가 끼면 끝내지 않음"이 성립한다(같은 `now`로 `currentPause`를 다시 계산해 재개 뒤 다시 정지한 짧은 정지도 걸러짐). `publishWhileActive`는 lock 안에서 `session === active && isActive`를 보고 그 안에서 publish한다. lock 순서는 RunTracker.lock → RunWatchBridge.lock → WatchLink.lock → transport.pending이고 거꾸로 RunTracker를 부르는 곳이 없다(워치 요청은 별도 코루틴에서 RunTracker에 들어옴). publish가 lock 안에서 막힐 수 있는 곳 점검: `collectionAllowed()`는 설정 사본의 메모리 값 읽기, `transport.send`는 큐에 넣고 `trySend`뿐이라 lock을 오래 잡지 않는다. 새 세션 시작 뒤의 늦은 publish는 `session === active` 비교로 버려짐 |
| EncryptedTokenStore 세대·저장 원자성 | 세대 읽기·증가·저장·`clear`가 모두 같은 `lock`이고 `updateInSession`은 `store`(lock 재진입)를 한 번에 부른다. 저장 실패(`persist`가 거짓)해도 메모리는 갱신되는 기존 방식 그대로(주석에 있음). `hasGuestCredential`은 `ioDispatcher`에서 `guest.value`를 읽을 뿐 lock을 잡지 않음 |
| SessionRefresher 거부 처리 | 세대 불일치는 `Refresh.Rejected`를 돌려주되 `endSession()`을 부르지 않는다: 이미 로그아웃됐으므로 토큰을 또 지울 필요가 없고, WebSocket 쪽은 `Rejected → closeForGood`, OkHttp 쪽은 인증 실패로 끝나 일관됨. 시험이 일반 갱신·게스트 재개 두 경로를 모두 덮음 |
| 재연결 루프 종료 조건 | `reconnectLoop`가 `scope.isActive && !isSettled()`를 매 바퀴 확인하고 `isSettled`는 lock 안(`closed`·`socket` 확인), `reconnectOnce`가 Rejected면 `closeForGood`. `tryConnect`는 HandshakeFailed(401)와 IOException을 구분(HandshakeFailed가 IOException의 하위라 순서 맞음) |
| ChatRoom 새 흐름 | connect를 따로 띄우고 첫 쪽은 곧바로 읽는다. connect 실패와 CLOSED는 모두 폴링으로 잇는다(설계 D-131이 정하지 않은 CLOSED 동작을 U5-C3로 7절에 남겨 둠). 연결 실패 표지는 방(start)마다 새로 만든다 |
| 글자 수 수정의 부작용 | `maxLength`를 지우고 VM이 `takeCodePoints`로 자르므로 붙여 넣기도 같은 상한. 라벨의 "n/200"은 코드 포인트. 크루 이름·소개는 UTF-16 그대로(서버와 같음). 접근성: 라벨이 글자 수를 그대로 읽음 |
| 신고 대화상자 접근성 | 라디오 줄 48dp·`selectableGroup`·`liveRegion`(보내기가 꺼진 이유)·제목 `heading()`은 이동 전과 같고 `ReportDialogTexts`로 문구가 core.ui 한 곳에 있음 |
| 크루 홈 지연 목록 | 글마다 `items(key)`라 보이는 글만 구성한다. 안내 줄·고정 공지·탭 줄 등 앞부분은 한 항목(key `…:head`), 더 보기는 `…:more`. 라이트·다크 스크린샷 두 벌로 간격 회귀를 잡음 |
| 로그인 SDK 창 | `scope.launch(start = UNDISPATCHED)`에서 `signIn(context)` → 취소(회전·이탈)는 `onLoginCancelled`로 진행 표시만 풀고 다시 던짐. 토큰을 받은 뒤의 로그인은 ViewModel 코루틴이라 화면이 사라져도 이어 감 |
| 미사용 import의 CI 영향 | `:wear:ktlintMainSourceSetCheck --rerun-tasks` 성공. 린트 게이트 실패는 없다(AN6-1은 경) |
| 줄 길이·빈 줄·끝 공백 | 변경 Kotlin 파일 스캔: 120자 초과는 주석·원시 문자열·백틱 시험 이름뿐(4ee91aef에도 같은 유형이 1,350줄이고 CI 통과), 연속 빈 줄·끝 공백·줄바꿈 누락 0 |

### 3. 구조적 프로그래밍·순환 복잡도(참고, 집계 제외)

수정분 함수 50줄 초과(비 Composable)는 0, 새로 생긴 Composable 100줄 초과도 0이다. 5차의 100줄 초과 둘(CrewHomeRoute 114줄, CrewPollWriteScreen 104줄)이 그대로다(AN5-D-5). 중첩 4단계는 없다(PhotoCompressor 분리로 AN5-C-2 해소). 반복 안 return은 ProximityAlertPlayer.play 둘이 남음(AN5-C-9). 새 순환 복잡도 초과 함수는 없다: WebSocketLiveChannel의 새 함수(`tryConnect` 3, `reconnectOnce` 5, `reconnectLoop` 4), RunTracker(`endIfStillPaused` 6, `publishWhileActive` 3), ChatRoom.start는 약 9. CrewHomeRoute는 복잡도 약 22 그대로, `homeTabItems`는 갈래 합치기로 11 → 약 9.

### 4. 형상 관리

| 활동 | 판정 | 근거 |
|------|------|------|
| 형상 식별 | 예 | 모듈·패키지 경계가 5.1.2와 같고(ModuleRules·ArchitectureTest TC-6583이 :wear까지) 통합 브랜치 feature/runkk-v010(MR !14)이 implementation.md 0장에 있음 |
| 버전 제어 | 예 | 안드로이드 커밋 28개가 전부 `fix|refactor|style|test:` 접두어와 Co-Authored-By 줄을 가진다. 지적 하나당 커밋 하나가 원칙이고(제목 끝에 AN5-C-n·D-n) 큰 것은 07856caf(14파일, 신고 대화상자 D-3·D-4·D-9)·69bd7e26(25파일, D-1)·4ee23aa9(25파일, C-14 이동)뿐이며 모두 한 가지 일이다(N3-7이 이번에는 지켜짐). 태그 0, versionName 0.1.0(출시 전이라 5차와 같게 아니오로 세지 않음) |
| 변경 통제 | 아니오 | 커밋 제목에 Jira 키 0(N-14, 결정 대기 그대로, 사용자 결정으로 판정 요인에서 제외). 결정 ID(AN5-C-n·D-n·FR·NFR)로 5차 리뷰와 설계서 D-183~D-189까지는 추적됨 |
| 형상 감사 | 예 | `git diff 4ee91aef a27c914f -- android`로 재현. 설계서는 D-183~D-189·05-01 ui.record 행에 반영됨. 단 implementation.md 5장 계약 "마지막 갱신"이 그대로임(AN5-C-18) |
| 상태 보고 | 예(보완) | 커밋 본문이 읽을 만하다. implementation.md 4장(결정 목록)·9장(설계서 갱신 요청)이 5차 지적대로 비어 있음(AN5-C-18 미해결) |

### 5. 집계와 판정

| 구분 | 치명 | 중 | 경 | 구성 |
|------|------|----|----|------|
| 새 결함(AN6) | 0 | 0 | 7 | AN6-1~7 |
| 5차 잔여 | 0 | 0 | 10 | 일부 4(C-9·C-16·C-17·D-3), 미해결 6(C-12·C-15·C-18·D-2·D-5·D-17). 중 3건은 모두 해결 |
| 결정 대기 | 0 | 0 | 2 | N-14(Jira 키), N3-7(이전 병합 이력, 이번 28커밋은 지켜짐) |
| 합계 | 0 | 0 | 19 | 5차 (다)·(라) 합계(중 3, 경 36, 결정 대기 N-14·N3-7이 양쪽에 중복)에서 중 3 해결, 경은 19로 줄었다(새 7 + 잔여 10 + 대기 2) |

판정: **합격** (치명 0, 중 0 ≤ 3, 설계 일치 점검 수행, 형상 관리는 변경 통제 1항목만 아니오이고 N-14 사용자 결정으로 판정 요인에서 제외, 제외하지 않으면 "조건부").
근거: 5차 중 3건(임대 경쟁·64px 압축·코드 포인트)이 모두 고쳐졌고 시험이 붙었다. 보고된 24건 중 22건이 맞게 해결됐고 나머지 둘(C-16·D-3)은 일부다. 동시성 수정(tryLease·endIfStillPaused·publishWhileActive·세대 확인)은 락 범위와 순서를 읽어 확인했고 교착 경로가 없다. 새 결함은 모두 경이며 그중 먼저 볼 것은 AN6-2(save()로 바뀐 토큰을 지키는 확인이 약해짐)와 AN6-3(401 반복 상한 없음)이다. 5차 경 가운데 AN5-D-5(Composable 100줄, 오히려 늘었음)·D-2(끊긴 조건식 31곳)·C-15(ChatRoom 414줄)·C-12는 손대지 않은 채 남았다.

### 6. sw-test 전달: 우선 테스트 대상

| 함수·경로 | 약산 복잡도 | 이유 |
|-----------|-------------|------|
| EncryptedTokenStore.updateInSession / SessionRefresher.renew·resumeGuest | 6 이하 | 세대 확인과 `save()` 교체(AN6-2): 요청 중 `save(새 토큰)`이 끼면 저장하지 않는 시험 추가 |
| WebSocketLiveChannel.reconnectOnce·tryConnect·reconnectLoop | 5 이하 | 갱신 Renewed + 핸드셰이크 401 반복의 상한(AN6-3), 가상 시계 시험 |
| RunTracker.tick·endIfStillPaused·publishWhileActive·changePause | 8 이하 | 경쟁 시험을 결정적 훅으로(AN6-6), `guide` 낡은 판단(AN6-5) |
| CrewRepositoryImpl.writeBoard | 4 | 첫 사진 실패 때 나머지를 압축하지 않음(AN6-4), 5장 모두 성공·중간 실패 경곗값 |
| CrewHomeRoute 탭·더 보기 연결 | 22 | 5차와 같음(Route 통합 시험 없음, AN5-D-5) |
| ChatRoom.start·effectiveState | 9 | CLOSED·connect 실패·재오픈 조합(시험 3건이 있어 갈래별 표 시험으로 확장) |
| 코드 포인트 경계(닉네임 2·20, 응원 200, 투표 질문 200·선택지 40, 장소 50) | — | 이모지 경곗값 시험이 ViewModel 단위에 있는지 다시 확인(닉네임 `"😀"`=1자는 거부, 11~20개 이모지 허용) |

### 7. 설계서 갱신 요청 (`/sw-design-oo` 재호출 목록)

코드가 옳고 설계가 비어 있는 것이다.

| ID | 대상 절 | 내용 | 비고 |
|----|---------|------|------|
| U6-A1 | 06-06 6.1.14 api.core(SessionRefresher·TokenProvider) | `TokenProvider.sessionGeneration()`·`updateInSession(generation, access, refresh): Boolean`(갱신 응답을 기다리는 사이 로그아웃이 있으면 저장하지 않음, AN5-C-8). AN6-2를 고치면 "세대 + 요청 때의 갱신 토큰" 규칙으로 적는다 | 설계는 "응답 뒤 확인"으로만 적혀 있음 |
| U6-A2 | 05-02 D-131 또는 새 D | 채팅방 CLOSED·connect 실패 때 REST 폴링으로 잇는 규칙(effectiveState) | 5차 U5-C3에서 이미 요청한 항목, 코드가 정했으니 확정만 하면 됨 |
| U6-A3 | 05-02 D-143 | 적용 항목 표(닉네임 2~20·응원 200·투표 질문 200·선택지 40·장소 50·게시글 본문)와 core.ui로 올라간 `RunkkTextLength`(U5-D-1 반영 확인) | 코드가 이미 같음, 설계서 문장만 확인 |
| U6-A4 | implementation.md 4장·9장·5장 | 5차 U5-C·U5-D 목록과 D-183~D-189, 계약 마지막 갱신일 | AN5-C-18과 같은 일 |

### 8. 다음 행동

- [ ] AN6-2·3: 세대 확인에 갱신 토큰 비교(또는 save에서 세대 증가), 401 반복 상한과 시험
- [ ] AN6-4·5: writeBoard 압축을 첫 실패에서 멈춤, tick의 guide를 lock 안 확인 뒤에 재생
- [ ] AN6-6: 경쟁 시험 옆에 결정적 훅 시험 한 쌍, 수정 전 코드에서 실패하는지 한 번 기록
- [ ] AN6-1·7: 미사용 import·KDoc·군더더기 return 정리(ktlint가 잡지 않으므로 IDE의 Optimize Imports)
- [ ] 5차 잔여: C-9(play)·C-12·C-15·C-16(ViewModel의 IO 감싸기 제거)·C-17(Thread.sleep)·C-18, D-2·D-3(CrewHomeRoute·CrewHomeScreen)·D-5·D-17
- [ ] 설계서 갱신: `/sw-design-oo`로 U6-A1~A4
- [ ] 결정 대기 2건(N-14·N3-7)은 사용자·팀 결정 뒤 반영
