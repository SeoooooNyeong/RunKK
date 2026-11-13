> [런크크 코드 리뷰 결과서](../code-review.md) 목차 · 이전: [02c-android-2.3](02c-android-2.3.md) · 다음: [02e-android-5](02e-android-5.md)
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

