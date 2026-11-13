> [런크크 코드 리뷰 결과서](../code-review.md) 목차 · 이전: [02d-android-AN5-C-17-상세-빠진](02d-android-AN5-C-17-상세-빠진.md) · 다음: [03-ai](03-ai.md)
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
