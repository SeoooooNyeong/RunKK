> [런크크 코드 리뷰 결과서](../code-review.md) 목차 · 리뷰 2026-10-20 (1차) · 이전: [01e-backend-4](01e-backend-4.md) · 다음: [02b-android-1](02b-android-1.md) · 2차: [리뷰 2026-10-21 (2차)](#리뷰-2026-10-21-2차) · 3차: [리뷰 2026-10-21 (3차)](02b-android-1.md#리뷰-2026-10-21-3차) · 4차: [리뷰 2026-10-21 (4차)](02c-android-2.3.md#리뷰-2026-10-21-4차)

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

