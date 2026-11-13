> [런크크 코드 리뷰 결과서](../code-review.md) 목차 · 이전: [02b-android-1](02b-android-1.md) · 다음: [02d-android-AN5-C-17-상세-빠진](02d-android-AN5-C-17-상세-빠진.md)
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

