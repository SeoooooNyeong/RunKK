> [런크크 코드 리뷰 결과서](../code-review.md) 목차 · 이전: [02-android](02-android.md) · 다음: [02c-android-2.3](02c-android-2.3.md)
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

