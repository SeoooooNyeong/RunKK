> [런크크 테스트 계획 및 케이스](../test.md) 목차 · 이전: [02-02b-단위-안드로이드-2.2.6](02-02b-단위-안드로이드-2.2.6.md) · 다음: [02-02d-단위-안드로이드-2.2.15](02-02d-단위-안드로이드-2.2.15.md)
#### FallbackLiveChannelProvider (A/data/live/, D-57)

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1454 | FR-088 | open().connect() | 핸드셰이크 404 / 426 | 다시 시도 없이 PollingLiveChannel로 연결, Success | FallbackLiveChannelProviderTest#핸드셰이크가 404면 바로 폴링으로 바꾼다 | 미실행 |
| TC-1455 | FR-091 | open().connect() | 핸드셰이크 500이 (a) 2회 뒤 성공 (b) 3회 연속 | (a) 세 번째 시도(1초·2초 뒤)에서 WebSocket (b) 폴링으로 전환 | FallbackLiveChannelProviderTest#핸드셰이크가 3번 이어서 실패하면 폴링으로 바꾼다 | 미실행 |
| TC-1456 | FR-088 | open().connect() | 첫 핸드셰이크 성공 | WebSocket으로 송수신 | FallbackLiveChannelProviderTest#핸드셰이크가 되면 WebSocket으로 주고받는다 | 미실행 |
| TC-1457 | FR-088 | open() 두 번, 각 임대에 connect() | 가짜 WebSocket 핸드셰이크 성공 | 임대 2개가 같은 실제 연결을 씀, 핸드셰이크 1회 (v0.6, D-103: open()은 임대를 돌려준다. "같은 채널"을 "같은 실제 연결"로 고침) | FallbackLiveChannelProviderTest#닫기 전에는 같은 채널을 주고 connect를 여러 번 불러도 한 번만 연결한다 | 미실행 |
| TC-1458 | FR-088 | 임대 하나만 연 상태에서 그 임대의 close() 뒤 같은 임대로 connect(), 이어서 open() | 가짜 WebSocket 핸드셰이크 성공 | Failure(Network), 참조 수 0이라 실제 연결 닫힘, 다음 open()은 새 연결(핸드셰이크 1회 더) (v0.6, D-103: 임대가 여럿이면 TC-6462) | 없음 | 미실행 |
| TC-1459 | FR-087 | connect() 전에 messages 구독, 연결 직후 BattleStart 도착 | 가짜 WebSocket이 연결 직후 메시지 1건을 보냄 | 메시지 1건 받음(유실 0) | 없음 | 미실행 |

#### ServerMessageDecoder와 지연 기록 (A/data/live/, 5.7.2 v0.8 계약, D-65)

메시지 항목 이름은 5.7.2 표와 같아야 한다. 서버 쪽 직렬화와 맞추는 양방향 계약 시험은 03-통합 조각 몫이고, 여기서는 앱 디코더가 새 항목을 도메인 값으로 옮기는지만 본다. 시각은 모두 ISO-8601 오프셋 포함 형식(`2026-10-21T19:00:00+09:00`, 밀리초가 있으면 세 자리)이다(v0.5, D-84).

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1833 | NFR-005, FR-088 | {"type":"OpponentProgress","battleId":7,"opponentName":"러너B","opponentDistance":1200,"opponentElapsed":400,"gapMeters":20,"connection":"CONNECTED","sentAt":"2026-10-21T19:00:00.250+09:00"} | ServerMessageDecoder.decode | OpponentProgress(7, "러너B", 1,200m, 400초, 20, CONNECTED, sentAt = Instant 2026-10-21T10:00:00.250Z) (v0.4, D-65 (1)) (v0.5, D-84: +09:00 오프셋을 반영해 같은 순간으로 읽는다) | 없음 | 미실행 |
| TC-1834 | FR-149, FR-190, NFR-039 | CrewRunRelay(postId 31) entries 3줄: (rank 1, mine true, sentAt "2026-10-21T19:00:00.000+09:00") / (rank 2, mine false, sentAt null) / (rank 3, mine false, sentAt "2026-10-21T19:00:00.800+09:00") | decode | entries 3개 순서 유지, isMe(JSON 키 mine) 참·거짓·거짓, sentAt Instant·null·Instant, 이름 항목은 화면 표시용으로 그대로 (v0.4, D-65 (1)(2)) (v0.5, D-84: sentAt 두 값이 +09:00 오프셋을 반영한 Instant 10:00:00.000Z·10:00:00.800Z) (v0.6 후속, D-161: 도메인 이름은 RelayEntry.isMe이고 @SerialName("mine")으로 JSON 키를 잇는다. 해독 입력은 그대로, 직렬 서술자의 JSON 키에 "isMe"·"me" 0개, TF-AN-32 해소) | 없음 | 미실행 |
| TC-1835 | FR-083, FR-095 | {"type":"MatchFailed","reason":"MATCH_UNAVAILABLE"} | decode | MatchFailed(reason MATCH_UNAVAILABLE). ServerMessage 하위 형식 11종 중 하나 (v0.4, D-64, D-65 (3), R39) | 없음 | 미실행 |
| TC-1836 | FR-191, FR-116 | {"type":"CrewRunEnded","postId":31,"reason":"FINISHED"} / reason "DELETED", 각각 | decode | CrewRunEnded(31, FINISHED) / CrewRunEnded(31, DELETED) (v0.4, D-65 (4), D-66) | 없음 | 미실행 |
| TC-1838 | NFR-039 | TC-1834의 CrewRunRelay 수신 | 시험 빌드, 고정 Clock 수신 시각 10:00:01.000Z | 시험 빌드 지연 로그 2건 1,000ms·200ms, sentAt null 줄은 로그 0건, CrewRunState에 sentAt 항목 없음 (v0.4, D-65 (1)) | 없음 | 미실행 |

#### 매칭 화면 ViewModel (ui.battle, R38)

매칭 대기 중 채널은 ui.battle의 매칭 화면 ViewModel이 듣는다(6.1.14 onMessage, 5.1.2 ui.battle). A3b 화면이라 2.2.12 "구현 후 작성" 대상이지만 R38이 수신 주체를 바꾸었으므로 두 케이스를 먼저 둔다. 30초 대기 경계(29·30·31초)는 서버 Matchmaker 조각(02-01) 몫이다.

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1841 | FR-083, FR-095 | 매칭 대기 중 MatchFailed(MATCH_UNAVAILABLE) 수신 | 매칭 화면 ViewModel이 채널 구독, BattleRunCoordinator.onMatched 0회 | 채널 close 1회, 안내 "잠시 후 다시 시도해 주세요" 1건, 화면 상태 매칭 화면 첫 상태, RunTracker.startRun 0회, BattleRunCoordinator 호출 0회 (v0.4, R38, D-64 (4)) | 없음 | 미실행 |
| TC-1842 | FR-083, FR-086 | 매칭 대기 중 MatchFound(9) 수신 | 같은 조건 | BattleRunCoordinator.onMatched(MatchFound(9)) 1회, 실패 안내 0건 (매칭 결과 메시지 유효 클래스) (v0.4, R38) | 없음 | 미실행 |

#### 홈 상단 요약 (domain.account·domain.avatar 포트, ui.home, R35)

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1858 | FR-005 | AccountRepositoryImpl.mySummary() | MockWebServer 본인 프로필 응답(닉네임 "러너A", 티어 SILVER, 레벨 7 외 9항목) | Success(MySummary(displayName "러너A", tier SILVER, level 7)), 서버 호출 1회(기존 본인 프로필 API, 새 API 없음) (v0.4, R35) | 없음 | 미실행 |
| TC-1859 | FR-162 | AvatarRepositoryImpl.balance() | 아바타 응답 잔액 1,250 / 0, 각각 | Success(1250) / Success(0) (v0.4, R35) | 없음 | 미실행 |
| TC-1860 | FR-005, FR-050, FR-162 | 홈 화면 ViewModel 시작 | 가짜 AccountRepository·AvatarRepository·RecordRepository | mySummary 1회, balance 1회, AccountRepository.profile(SELF) 0회, AvatarRepository.avatar() 0회, 화면 상단 상태 = 닉네임·티어·레벨·잔액 4값 (v0.4, R35: 두 화살표를 데이터 결합으로) | 없음 | 미실행 |

#### 화면용 도메인 모델 이름 (D-78, 구조 시험)

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1855 | FR-050, FR-061, FR-070 | 서버 조회 결과 형식 DashboardView, RecordListView, RecordDetailView, GrowthView, ReportView, RankingView, PlanProgressView, TodayPlanView | api.record 매퍼가 돌려주는 domain.record 형식 이름을 리플렉션으로 모음 | Dashboard, RecordList, RecordDetail, Growth, Report, Ranking, PlanProgress, TodayPlan(View 접미어만 뺀 이름), domain 모듈에 이름이 View로 끝나는 형식 0개 (v0.4, D-78) | 없음 | 미실행 |
| TC-1856 | FR-050 | 서버에 같은 이름의 형식이 없거나 앱 안 이름과 겹치는 화면용 모델(예 RunSummary, RunCard) | api 매퍼 원본의 KDoc 확인 | 그 모델을 만드는 매퍼마다 KDoc에 서버 형식 이름 1개 이상 (v0.4, D-78) | 없음 | 미실행 |

#### WatchLink (A/data/watch/, NFR-023, H-19)

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1462 | FR-041, NFR-023 | publish(진행 표시) | (a) 심박 동의 참·연동 참 (b) 동의 거짓 | (a) heartRateCollectionAllowed 참 (b) 거짓 | WatchLinkTest#표시 정보에 심박 수집 허용 여부를 실어 워치로 보낸다 | 미실행 |
| TC-1463 | FR-014, NFR-023 | 측정 중 심박 동의 철회(changes 방출) | 심박 동의 참·연동 참, 측정 중 표시 정보 보낸 뒤 | 다음 tick을 기다리지 않고 마지막 표시 정보 + 허용 거짓 1건 즉시 전송 | WatchLinkTest#심박 동의를 철회하면 바로 수집 금지 플래그를 다시 보낸다 | 미실행 |
| TC-1464 | FR-019 | 워치 연동 끔 | 보낸 표시 정보 없음 | 대기 화면(running 거짓) + 허용 거짓 전송 | WatchLinkTest#워치 연동을 끄면 표시 정보가 없어도 금지 플래그를 보낸다 | 미실행 |
| TC-1465 | FR-041 | 묶음 OK 2건 + FAILED 1건 | 허용 | heartRates에 2건 | WatchLinkTest#실패 표본은 거르고 검증된 심박만 넘긴다 | 미실행 |
| TC-1466 | NFR-023 | 묶음 3건 | 허용 거짓 | 0건 | WatchLinkTest#수집이 금지된 동안 온 심박은 버린다 | 미실행 |
| TC-1467 | NFR-032 | 같은 측정 시각 묶음이 다시 옴(재동기화) | 허용 | 1회만 넘김 | WatchLinkTest#같은 측정 시각의 심박이 다시 와도 한 번만 넘긴다 | 미실행 |
| TC-1468 | FR-042 | 워치 연결 거짓 → 참 | 마지막 표시 정보 있음 | 그 표시 정보를 현재 허용 여부로 다시 전송 | WatchLinkTest#워치가 연결되면 마지막 표시 정보를 다시 보낸다 | 미실행 |

#### ApiErrorMapper (A/api/core/ApiErrorMapper.kt, 9247f80 상태별 구분)

설계 6.1.14가 정한 것은 "네트워크 실패 → Network, 401 → Unauthorized, 409 + 사유 코드 → Rejected(code)" 세 가지다. 5xx는 재전송 규칙(FR-040, D-15)에서 기대 결과를 끌어낸다. 오류 형식 이름은 설계에 없다(TF-AN-11). v0.4: 설계 v0.8이 AppError.Server(status)와 기본 사유 코드(본문에 code가 없으면 404 NOT_FOUND, 400 BAD_REQUEST, 나머지 HTTP_ + 상태 번호, 500 이상은 Server)를 정해 TC-1473~TC-1476의 기대 결과를 그 이름으로 고쳤다. D-72·D-76으로 서버 409 본문은 code·message를 싣고, 셀카 동시 변환·없는 알림·만료된 가입 신청 같은 새 거부도 같은 매핑을 탄다(TC-1849~TC-1851).

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1471 | FR-040 | IOException(연결 실패), SocketTimeoutException | ApiErrorMapper.toAppError 직접 호출 | AppError.Network → UploadWorker 재전송 예약 | ApiErrorMapperTest#연결 실패와 시간 초과만 Network | 미실행 |
| TC-1472 | FR-001 | HTTP 401 | fromHttp(401, 본문 없음) | Unauthorized | ApiErrorMapperTest#401은 Unauthorized | 미실행 |
| TC-1473 | FR-082 | HTTP 409 본문 {"code":"MATCH_ALREADY_WAITING"} / 본문 없음 | fromHttp(409, 본문) | Rejected("MATCH_ALREADY_WAITING") / Rejected("HTTP_409"), Network·Server·재시도 대상 아님 (6.1.14 기본 사유 코드) (v0.4, TF-AN-11 설계 반영) | ApiErrorMapperTest#409는 본문 사유 코드로 거부된다 | 미실행 |
| TC-1474 | FR-040, NFR-014 | HTTP 499 / 500 / 501 / 598 / 599 / 600, 그리고 408·429 | 업로드 중 | 500·501·598·599·408·429: AppError.Server(그 상태), UploadWorker가 RetryPolicy 간격으로 재전송 예약. 499: Rejected("HTTP_499"), 재시도 안 함이나 보관한 기록은 지우지 않는다(NFR-014). 600: Server(600)(500 이상은 Server) (6.1.14 ApiErrorMapper, 14장) (v0.4, TF-AN-11 설계 반영) | ApiErrorMapperTest#5xx와 408·429는 Network가 아니라 다시 시도할 수 있는 Server | 미실행 |
| TC-1475 | FR-040 | HTTP 404 본문 없음 / 400 본문 {"code":"INVALID_DISTANCE"} / 403 | fromHttp(상태, 본문) | 404 본문 없음 → Rejected(NOT_FOUND), 400 + INVALID_DISTANCE → Rejected(INVALID_DISTANCE), 403 → Rejected("HTTP_403"). 셋 다 Network·Unauthorized·Server 아님 (6.1.14 기본 사유 코드) (v0.4, TF-AN-11 설계 반영) | ApiErrorMapperTest#404와 400은 본문 사유 코드로, 본문이 없으면 NOT_FOUND·BAD_REQUEST로 거부된다 | 미실행 |
| TC-1476 | FR-040 | 200 응답인데 해독할 수 없는 본문 | Retrofit 해독 단계에서 SerializationException | AppError.Server(0)(해독 불가는 status 0, Rejected 아닌 재시도 대상) (v0.4, TF-AN-11 설계 반영) | ApiErrorMapperTest#해독할 수 없는 응답은 Server(0) | 미실행 |
| TC-1477 | FR-038 | call { } 안에서 코루틴 취소 | ApiErrorMapper.call, 시험 범위 취소 | CancellationException 그대로 전파(Outcome으로 바꾸지 않음) | 없음 | 미실행 |
| TC-1849 | FR-157 | HTTP 409 본문 {"code":"SELFIE_IN_PROGRESS","message":"이미 변환 중"} | fromHttp(409, 본문) | Rejected("SELFIE_IN_PROGRESS"), Network·Server 아님(다시 보내지 않음) (v0.4, D-76 (4), D-72 409 본문 code·message) | 없음 | 미실행 |
| TC-1850 | FR-175 | (a) HTTP 409 {"code":"NOTIFICATION_NOT_FOUND"} (b) HTTP 403 본문 없음(남의 알림) | fromHttp(상태, 본문) | (a) Rejected("NOTIFICATION_NOT_FOUND")(보관 기간 지난 알림 열기도 같은 응답, D-67) (b) Rejected("HTTP_403") (v0.4, D-76 (3)) | 없음 | 미실행 |
| TC-1851 | FR-121 | HTTP 409 {"code":"JOIN_REQUEST_EXPIRED","message":"신청 만료"} | fromHttp(409, 본문) | Rejected("JOIN_REQUEST_EXPIRED") (v0.4, D-69) | 없음 | 미실행 |

#### D-84 앱–서버 JSON 시각 형식 (A/data/live/ServerMessageDecoder, 진행값 전송, A/api/core/ServerTime)

실시간 메시지(sentAt, BattleStart.startAt, CountdownStarted.endsAt, CrewRunStart.startAt)와 REST 요청의 시각 항목은 ISO-8601 오프셋 포함 형식 하나로 주고받는다: `2026-10-20T19:07:03+09:00`, 밀리초가 있으면 세 자리. 앱은 서버가 보낸 값을 오프셋을 반영해 Instant로 읽고, 보낼 때는 Asia/Seoul(+09:00)로 쓴다(api.core.ServerTime.formatWithOffset). 오프셋 없는 값은 받지 않는다. 서버 쪽 직렬화·거부(REST 400)는 03-통합 조각 몫이다.

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1881 | FR-087, FR-102, FR-147 | decode: BattleStart(battleId 7)의 startAt "2026-10-21T19:00:10+09:00", CountdownStarted(battleId 7, arrivedName "러너B")의 endsAt "2026-10-21T19:05:00+09:00", CrewRunStart(postId 31)의 startAt "2026-10-21T19:00:00+09:00" | ServerMessageDecoder.decode | 각각 Instant 2026-10-21T10:00:10Z, 2026-10-21T10:05:00Z, 2026-10-21T10:00:00Z(+09:00 오프셋을 반영, 앱 기기 시간대와 무관) (v0.5, D-84) | 없음 | 미실행 |
| TC-1882 | FR-088, FR-087, FR-147 | decode: ① OpponentProgress의 sentAt "2026-10-21T10:00:00.250"(오프셋 없음) ② BattleStart의 startAt "2026-10-21T19:00:10" ③ CrewRunStart의 startAt "2026-10-21T19:00:00" | ServerMessageDecoder.decode, 로그 수집, 기기 시간대를 UTC와 Asia/Seoul로 바꿔 두 번 | 세 경우 모두 그 메시지를 버리고(decode 결과 null, 예외 없음) 로그 1줄, 두 시간대에서 결과가 같다(오프셋 없는 값은 기기 시간대에 따라 다른 순간이 되므로 받지 않는다). 시험 빌드 지연 로그 0건, 상태 변화 0 (조정자 결정 R3: 설계 D-84는 "오프셋 없는 값은 받지 않는다"만 정하고 앱 쪽 처리를 적지 않아 D-85의 해독 실패 처리(버림, null, 예외 없음, 로그)와 같게 정했다) (v0.5, D-84) | 없음 | 미실행 |
| TC-1883 | FR-088, NFR-005 | api.core.ServerTime.formatWithOffset(Instant): ① 2026-10-21T10:00:03Z ② 2026-10-21T10:00:03.001Z ③ 2026-10-21T10:00:03.500Z ④ 2026-10-21T10:00:03.999Z ⑤ 2026-10-20T15:00:00Z ⑥ 2026-10-21T14:59:59Z | 순수 함수 | ① "2026-10-21T19:00:03+09:00" ② "2026-10-21T19:00:03.001+09:00" ③ "2026-10-21T19:00:03.500+09:00" ④ "2026-10-21T19:00:03.999+09:00" ⑤ "2026-10-21T00:00:00+09:00" ⑥ "2026-10-21T23:59:59+09:00". 밀리초가 0이면 소수부가 없고 있으면 세 자리다. 경곗값: 밀리초 0·1·500·999, +09:00 자정 직전(⑥)과 정각(⑤) (v0.5, D-84) | 없음 | 미실행 |
| TC-1884 | NFR-005, FR-088, FR-149 | 진행값 전송: ProgressReport(channel BATTLE, channelId 7, 35m, 11초, sentAt 2026-10-21T10:00:11Z)를 ① WebSocket 채널 ② 폴링 채널(POST /live/progress)로 보냄 | 가짜 WebSocket·MockWebServer가 보낸 본문을 기록 | 두 경우 본문의 sentAt = "2026-10-21T19:00:11+09:00", 위도·경도·코스 항목 없음, 오프셋 없는 시각 항목 0개 (v0.5, D-84) | 없음 | 미실행 |
| TC-1885 | FR-038, FR-043 | RunUploadRemote.upload(run) | MockWebServer, run.startedAt = 2026-10-21T10:00:00Z, 경과 30분 | 요청 본문의 시각 항목(시작·종료 일시 등)이 모두 "…+09:00"으로 끝나는 ISO-8601 오프셋 형식(예 시작 "2026-10-21T19:00:00+09:00"), 오프셋 없는 시각 항목 0개 (v0.5, D-84: 앱 api.* 요청 매퍼가 ServerTime.formatWithOffset을 씀) | 없음 | 미실행 |

#### D-86 크루런 카운트다운 중 삭제와 중계 줄의 rank (A/domain/crew/CrewRunCoordinator, A/data/live/ServerMessageDecoder)

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1886 | FR-116, FR-147, FR-149 | CrewRunStart(31, startAt 19:00:00Z) 뒤 출발 카운트다운 중(지금 19:00:01Z) CrewRunEnded(31, DELETED), 이어서 CrewRunRelay(31, 5명, mine 2위) | 상태 Countdown(31, 19:00:03Z), 측정 전, 채널 열림 | 카운트다운을 그대로 마치고 19:00:03Z에 startRun(mode CREW_RUN, cardKind CREW_RUN, crewRunPostId 31) 1회, endRun 0회, 채널 close 1회, 삭제 안내 1건. 뒤 중계로 onRankChanged 0회·상태의 표 갱신 0회(중계·순위 안내 없이 달린다). 기록은 크루런 연결 없이 저장된다(서버 onRunSaved가 지워진 크루런을 무시, D-66) (경로 handle P9) (v0.5, D-86) | 없음 | 미실행 |
| TC-1887 | FR-116, FR-030 | TC-1886과 같은 상황에서 CrewRunEnded(31, DELETED)가 카운트다운 종료 시각(19:00:03Z)과 같은 순간 도착하는 두 순서 ① DELETED가 startRun보다 먼저 ② startRun이 먼저 | 시험 디스패처로 순서 고정, 회 31 | 두 순서 모두 startRun 정확히 1회, endRun 0회, 채널 close 1회, 삭제 안내 1건(종료 시각 앞·뒤 어느 쪽에 와도 측정은 멈추지 않음). ②는 상태가 Running(31)이 된 뒤 DELETED를 받아 중계·순위 안내만 멈춘다(TC-1828과 같은 갈래) (v0.5, D-86, 시간 경계: 카운트다운 종료 시각 앞·정각 뒤) | 없음 | 미실행 |
| TC-1888 | FR-149, FR-190 | decode: CrewRunRelay(postId 31) entries 4줄: (rank 1, mine true) / (rank 2, mine false) / (rank null, mine false, 진행값 아직 없음) / (rank null, mine false, 그만둔 참가자) | ServerMessageDecoder.decode | RelayEntry.rank = 1·2·null·null(Int?, 0으로 바꾸지 않음), 줄 4개 순서 유지, 예외 없음 (v0.5, D-86) | 없음 | 미실행 |
| TC-1889 | FR-190, FR-149 | CrewRunRelay(31, 5명: mine = true 줄 rank 2, 다른 두 줄 rank null) | 상태 Running | onRankChanged(CrewRank(2, 5)) 1회(인원 = 줄 수), rank가 null인 줄이 섞여도 본인 순위·인원에 영향 없고 예외 없음. mine = true 줄의 rank가 null이면 0회(TC-1827) (v0.5, D-86) | 없음 | 미실행 |

### 2.2.11 블랙박스: 워치(:wear) 심박 수집·종료 요청·알림 권한

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1481 | FR-041, NFR-023 | HeartRateCollector.start() | 처음 상태(폰 허용 없음), OS 권한 참 | 상태 BLOCKED, 센서 열림 0회 | HeartRateCollectorTest#처음에는 폰 허용이 없어 막혀 있고 start해도 센서를 켜지 않는다 | 미실행 |
| TC-1482 | FR-041 | allow(참) → start() | OS 권한 참 | COLLECTING, samples 방출 | HeartRateCollectorTest#허용과 권한이 있으면 start에 센서를 켜고 값을 samples로 내보낸다 | 미실행 |
| TC-1483 | FR-014, NFR-023 | 수집 중 allow(거짓) | COLLECTING | 즉시 BLOCKED, 센서 해제, 이후 값 0건 | HeartRateCollectorTest#허용 플래그가 거짓으로 오면 즉시 센서를 끄고 이후 값을 내보내지 않는다 | 미실행 |
| TC-1484 | FR-041 | start(), 권한 받은 뒤 onPermissionChanged() | 허용 참, OS 권한 거짓 | NEEDS_PERMISSION → COLLECTING | HeartRateCollectorTest#OS 권한이 없으면 권한 필요 상태이고 권한을 받은 뒤 수집한다 | 미실행 |
| TC-1485 | FR-041 | 센서 흐름 오류 | COLLECTING | UNAVAILABLE, stop → start 뒤 다시 COLLECTING | HeartRateCollectorTest#센서 오류면 쓸 수 없음 상태가 되고 stop 뒤 start로 다시 시도한다 | 미실행 |
| TC-1486 | FR-041 | start() 3회 | 허용·권한 참 | 측정 세션 1개 | HeartRateCollectorTest#start를 거듭 불러도 측정 세션은 하나다 | 미실행 |
| TC-1487 | FR-181 | PhoneLink.sendEndRequest() | (a) 폰 노드 1개 (b) 노드 없음 (c) 전송 실패 | (a) END_REQUEST 경로 1건, 참 (b) 거짓 (c) 거짓 | PhoneLinkTest#종료 요청은 연결된 폰에 END_REQUEST 경로로 보낸다 | 미실행 |
| TC-1488 | FR-183 | PhoneLink.sendStartRequest() | 노드 없음 | 보내지 않고 거짓 | PhoneLinkTest#폰과 연결되지 않았으면 시작 요청을 보내지 않고 거짓을 돌려준다 | 미실행 |
| TC-1489 | FR-041, NFR-023 | 표시 정보 running 참, 허용 거짓 | 보류 심박 5건 | 수집 시작 0, 보류 심박 비움, latestHeartRate null | PhoneLinkTest#러닝 중이어도 수집 금지면 심박을 모으지 않는다 | 미실행 |
| TC-1490 | NFR-032, FR-043 | 보류 심박 9 / 10 / 11건 | 허용, 러닝 중, 묶음 크기 10·주기 5초(구현 상수) | 9건 → 주기에 묶음 1개, 10건 → 즉시 묶음 1개, 11건 → 즉시 10건 + 주기에 1건. 폰 수신 건수 = 측정 건수 | PhoneLinkTest#심박을 묶음 크기만큼 모으면 DataClient로 wear contract 형식의 묶음을 보낸다 | 미실행 |
| TC-1491 | NFR-032 | 끊긴 동안 쌓인 심박 299 / 300 / 301건, 재연결 | 묶음 상한 300(DataItem 100KB 제한) | 묶음 1 / 1 / 2개, 폰 수신 299 / 300 / 301건(누락 0) | PhoneLinkTest#밀린 심박은 묶음 상한 크기로 나눠 보내고 하나도 빠뜨리지 않는다 | 미실행 |
| TC-1492 | NFR-023 | 철회 → 다시 허용 | 철회 전 잰 심박 5건 보류 | 철회 전 심박 0건 전송 | PhoneLinkTest#철회된 뒤 다시 허용되면 철회 전에 잰 심박은 보내지 않는다 | 미실행 |
| TC-1493 | NFR-032 | 묶음 putData 실패 뒤 다음 주기 | 허용 | 그 묶음을 버리지 않고 다음 전송에 포함 | PhoneLinkTest#저장에 실패한 묶음은 버리지 않고 다음 전송에 함께 보낸다 | 미실행 |
| TC-1494 | FR-181 | WatchViewModel.onEndTapped() | 러닝 중 | 안내 "길게 눌러 종료", 종료 요청 0건 | WatchViewModelTest#종료 버튼을 짧게 누르면 길게 눌러 종료 안내를 보이고 요청은 보내지 않는다 | 미실행 |
| TC-1495 | FR-181 | WatchViewModel.onEndLongPressed() | (a) 폰 연결 (b) 끊김 | (a) 종료 요청 1건 (b) 안내 "폰과 연결 필요" | WatchViewModelTest#종료 버튼을 길게 누르면 종료 요청을 폰에 보낸다 | 미실행 |
| TC-1496 | FR-181 | 폰이 보낸 표시 정보 notice "대결·크루런은 폰에서 끝내 주세요" | 대결 중 | 그 문구 그대로 표시 | WatchViewModelTest#폰이 대결 크루런이라 거절하면 그 안내를 그대로 보인다 | 미실행 |
| TC-1497 | FR-042 | 워치 마지막 심박이 29,999 / 30,000 / 30,001ms 전 | 폰 심박 없음, 허용 참 | 표시 / 표시 / 빈칸(6.1.15 "30초 넘게 없으면 빈칸") | WatchViewModelTest#심박이 30초 넘게 없으면 빈칸으로 둔다 | 미실행 |
| TC-1498 | FR-183 | onStartTapped() | 폰 끊김 | 안내 "폰과 연결 필요"(FR-183), 시작 기다림 표시 없음 | WatchViewModelTest#폰과 연결되지 않은 채 시작을 누르면 폰과 연결 필요를 보인다 | 미실행 |
| TC-1499 | FR-041 | 심박 수집이 처음 COLLECTING이 됨 | (a) API 33 이상, 알림 권한 없음, 물은 적 없음 (b) 이미 물음 (c) API 32 | (a) 알림 권한 요청 1회 표시, 거절해도 수집 계속 (b) 묻지 않음 (c) 묻지 않음 | WatchViewModelTest#심박 수집이 처음 켜지고 알림 권한이 필요하면 한 번만 묻는다 | 미실행 |

### 2.2.12 구현 후 작성 (A3b, 통합 브랜치에 없음)

화면은 업무 규칙을 갖지 않고 Outcome을 화면 상태로 바꾸므로(6.1.14 표현), 단위 케이스는 ViewModel이 조정자·Repository의 결과를 어떤 화면 상태로 바꾸는지를 본다. A3b가 통합되면 이 대역 다음 판에서 케이스를 만든다. 검증 케이스는 4.4·4.5·4.6 조각에 이미 있다.

| 화면 모듈 | 대상 (6.1.14 화면별 ViewModel) | 단위로 볼 것 | 관련 요구 | 구현 |
|-----------|--------------------------------|--------------|-----------|------|
| ui.battle | 매칭·대결 진행·결과·기록 대결 선택 화면 ViewModel | BattleRunState 6종 → 화면 상태, failure → 안내, 매칭 대기 취소, 그만두기 확인 단계, "상대 연결 확인 중"(3초). v0.4: 매칭 화면 ViewModel의 MatchFailed 수신(R38)은 TC-1841·TC-1842로 먼저 두었다 | FR-080, FR-085, FR-086, FR-088, FR-090, FR-093, FR-094, FR-095, FR-096, FR-097, FR-099, FR-100, FR-101, FR-106, FR-109 | 미구현(A3b) |
| ui.crew | 크루 홈·목록·가입·응원보드·모집글·대기실·크루런 진행·결과 ViewModel | CrewRepository Outcome → 화면 상태, 대기실 준비 상태 토글, 그만두기 확인, 크루런 결과 표 | FR-110, FR-117, FR-120, FR-121, FR-122, FR-129, FR-131, FR-132, FR-133, FR-135, FR-137, FR-140, FR-145, FR-147, FR-149, FR-191, FR-192 | 미구현(A3b) |
| ui.runimal | Runimal 카드·남은 횟수·공유 ViewModel | 유형 없음(NO_TYPE) 화면, 남은 판정 횟수 | FR-151, FR-154, FR-168 | 미구현(A3b) |
| ui.avatar | 셀카 생성·기본 캐릭터·상점·착용 ViewModel | 생성 횟수 한도 거부, 얼굴 변환 동의 화면 먼저, 잔액 부족 거부. v0.6: 배경 선택(해금·잠김 목록, chooseBackground) | FR-155, FR-157, FR-158, FR-160, FR-161, FR-162, FR-201, FR-202 | 미구현(A3b) |
| ui.me (v0.6) | 로그인·동의(가입·게스트 공용)·계정 연동·테마·대결 설정 ViewModel | "게스트로 시작" 진입, 동의 화면 하나, 게스트 시작 직후·설정·로그아웃 확인의 유실 고지(FR-199), 연동 거부 안내(ALREADY_MEMBER), 테마 선택 즉시 반영, 근접 알림 켬·끔. 게스트 UseCase는 TC-6431~TC-6443 | FR-001, FR-003, FR-028, FR-109, FR-197, FR-198, FR-199, FR-203 | 미구현(A3) |
| ui.run (v0.6) | RunViewModel 일시정지·재개 버튼 | 자유런만 버튼, 정지 중 "일시정지" 표시·지표 고정, 잠금 중 버튼 무동작. 도메인 규칙은 TC-6371~TC-6381 | FR-034, FR-194 | 미구현(A3) |
| ui.chat (v0.6) | ChatViewModel·ChatRoomScreen | 보이면 open·사라지면 close, 1~500자 보내기, 위로 끌어 이전 페이지, 신고 대화상자. 먼저 둔 케이스 TC-6486·TC-6495 | FR-208, FR-209, FR-210, FR-213, FR-214 | 미구현(A3) |
| ui.crew (v0.6) | 운영진·공지·투표·일정·게시판·앨범·공동 목표 ViewModel | myRole로 버튼 노출, 일정 응답 AttendanceAnswer(참석·불참·미정), 추천 영역 없음. 사진 압축은 TC-6511~TC-6518 | FR-128, FR-132, FR-138, FR-140, FR-141, FR-145, FR-204~FR-207, FR-211, FR-212 | 미구현(A3) |

### 2.2.13 화이트박스(설계 v0.11): RunSession.pause·resume·pausedTotal·tick·onLocation (v0.6)

v0.11 앱 코드가 없어 6.2 A-02 의사 코드와 6.1.14 RunSession 연산 표로 그래프를 그렸다. "정지 중"은 상태 PAUSED·PAUSED_LOCKED를 함께 뜻하는 술어 하나로 센다. A-02는 PAUSED만 적었지만 6.1.14·D-120이 PAUSED_LOCKED의 정지 규칙을 PAUSED와 같게 정했으므로 같은 술어로 보았다(TF-AN-23). v0.6 후속: D-157 (2)가 이 술어를 RunSession.isPaused(PAUSED 또는 PAUSED_LOCKED)로 정해 onLocation·tick·finish/end·정지 한도 검사가 모두 이것을 쓴다(TF-AN-23 해소). 술어 하나라 V(G)는 어느 쪽이든 같다.

```kotlin
// 설계 v0.11 의사 코드 (A-02, D-93, D-121, D-137)
fun pause(at: Instant): Boolean {
    if (!spec.mode.isPausable) return false                                 // U1 → U3
    if (state != RUNNING) return false                                      // U1b → U3
    pausedSince = at; state = PAUSED; return true                           // U2
}
fun resume(at: Instant): Boolean {
    if (state != PAUSED) return false                                       // W1 → W2
    pauses += PauseInterval(pausedSince!!, at); pausedSince = null
    lastResumedAt = at; lastNormal = null; window.clear(); state = RUNNING
    return true                                                             // W3
}
// v0.6 후속: 워치 요청 전용 연산 (D-157 (1)(5)). at은 폰이 요청을 처리한 시각
fun pauseFromWatch(at: Instant): Boolean {
    if (!spec.mode.isPausable) return false                                 // V1 → V2
    if (state == RUNNING) { state = PAUSED }                                // V3 → V4
    else if (state == LOCKED) { state = PAUSED_LOCKED }                     // V5 → V6
    else return false                                                       // V7
    pausedSince = at; return true                                           // V8
}
fun resumeFromWatch(at: Instant): Boolean {
    if (!isPaused) return false                                             // Z1 → Z2
    closePause(at)                                                          // Z3 (resume의 정지 구간 닫기·lastNormal·창 비우기와 같다)
    state = if (state == PAUSED_LOCKED) LOCKED else RUNNING                 // Z4 → Z5 / Z6
    return true                                                             // Z7
}
internal fun pausedTotal(now: Instant): Duration {
    var sum = Duration.ZERO
    for (p in pauses) sum += p.to - p.from                                  // M1 반복, M2
    return sum + (pausedSince?.let { now - it } ?: Duration.ZERO)           // M3, M4
}
fun tick(now: Instant): RunProgress {
    val elapsed = now - startedAt - pausedTotal(now)                        // K1
    if (isPaused) return RunProgress(elapsed, distance, null, last.average, …, isPaused = true, heartRate = lastHeartRate)  // K1 판정 → K2 (D-144, D-157 (4))
    val current = if (dDist > 0 && dTime > 0) Pace.of(dTime, dDist)         // K3, K4 → K5
                  else last.currentPace                                     // K6
    val average = if (distance > 0) Pace.of(elapsed, distance) else null    // K7 → K8 / K9
    return RunProgress(elapsed, distance, current, average, …, isPaused = false).also { last = it }  // K10
}
```

노드: pause U1 `!isPausable`, U1b `state ≠ RUNNING`, U2 정지 시작·참, U3 거짓, U4 끝. resume W1 `state ≠ PAUSED`, W2 거짓, W3 구간 닫기·참, W4 끝. pausedTotal M1 다음 닫힌 구간 있음?, M2 더함, M3 `pausedSince` 있음?, M4 열린 정지 더함, M5 반환·끝. tick K1 경과 계산 + 정지 중?, K2 정지 때 값을 고정한 진행값(K1의 elapsed, 현재 페이스 null, 심박 마지막 값, isPaused 참) 반환(v0.6 후속, D-144, D-157 (4)), K3 창 거리 차 > 0?, K4 창 시각 차 > 0?, K5 현재 페이스 계산, K6 직전 현재 페이스, K7 거리 > 0?, K8 평균 계산, K9 평균 null, K10 RunProgress 반환, K11 끝.

간선: pause U1→U3, U1→U1b, U1b→U3, U1b→U2, U2→U4, U3→U4. resume W1→W2, W1→W3, W2→W4, W3→W4. pausedTotal M1→M2, M2→M1, M1→M3, M3→M4, M3→M5, M4→M5. tick K1→K2, K1→K3, K2→K11, K3→K4, K3→K6, K4→K5, K4→K6, K5→K7, K6→K7, K7→K8, K7→K9, K8→K10, K9→K10, K10→K11.

pauseFromWatch(v0.6 후속): 노드 V1 `!isPausable`, V2 거짓, V3 `state == RUNNING`, V4 PAUSED로, V5 `state == LOCKED`, V6 PAUSED_LOCKED로, V7 거짓(그 밖의 상태), V8 시작 시각 기록·참·끝. 간선 10개: V1→V2, V1→V3, V2→V8, V3→V4, V3→V5, V4→V8, V5→V6, V5→V7, V6→V8, V7→V8. resumeFromWatch(v0.6 후속): 노드 Z1 `!isPaused`, Z2 거짓, Z3 정지 구간 닫기·재개 처리(resume과 같음), Z4 `state == PAUSED_LOCKED`, Z5 LOCKED로, Z6 RUNNING으로, Z7 참·끝. 간선 8개: Z1→Z2, Z1→Z3, Z2→Z7, Z3→Z4, Z4→Z5, Z4→Z6, Z5→Z7, Z6→Z7.

onLocation(설계 v0.11): 2.2.6 그래프에서 N1 `!isActive`가 "RUNNING·LOCKED가 아님"이 되어 A-02 첫 줄(정지 중이면 버림)이 같은 노드에 들어간다. N5(from = startedAt)가 `lastResumedAt ?: startedAt` 판정이 되어 N5a(from = lastResumedAt)·N5b(from = startedAt)로 갈린다(D-121). 간선 N5→N6 하나가 N5→N5a, N5→N5b, N5a→N6, N5b→N6 넷이 되어 노드 17, 간선 24다.

| 연산 | N / E | E − N + 2 | 단순 조건 + 1 | 영역 수 |
|------|-------|-----------|---------------|---------|
| pause | 5 / 6 | **3** | `!isPausable`, `state ≠ RUNNING` = 2, + 1 = **3** | U1 1 + U1b 1 + 바깥 1 = **3** |
| resume | 4 / 4 | **2** | `state ≠ PAUSED` 1, + 1 = **2** | 안쪽 1 + 바깥 1 = **2** |
| pauseFromWatch (v0.6 후속) | 8 / 10 | **4** | `!isPausable`, `state == RUNNING`, `state == LOCKED` = 3, + 1 = **4** | V1 1 + V3 1 + V5 1 + 바깥 1 = **4** |
| resumeFromWatch (v0.6 후속) | 7 / 8 | **3** | `!isPaused`, `?:` = 2, + 1 = **3** | Z1 1 + Z4 1 + 바깥 1 = **3** |
| pausedTotal | 5 / 6 | **3** | for 1, `?:` 1 = 2, + 1 = **3** | 반복 1 + M3 1 + 바깥 1 = **3** |
| tick | 11 / 14 | **5** | 정지 중 1, `dDist > 0` 1, `dTime > 0` 1, `distance > 0` 1 = 4, + 1 = **5** | K1 1, K3·K4 2, K7 1, 바깥 1 = **5** |
| onLocation (설계 v0.11) | 17 / 24 | **9** | 2.2.6의 7 + `lastResumedAt ?:` 1 = 8, + 1 = **9** | 2.2.6의 8 + N5 마름모 1 = **9** |

| 연산 | 경로 | 노드 | 새 간선 | TC |
|------|------|------|---------|----|
| pause | U-P1 | U1→U3→U4 | U1→U3, U3→U4 | TC-6302 |
| pause | U-P2 | U1→U1b→U3→U4 | U1→U1b, U1b→U3 | TC-6303 |
| pause | U-P3 | U1→U1b→U2→U4 | U1b→U2, U2→U4 | TC-6301 |
| resume | W-P1 | W1→W2→W4 | W1→W2, W2→W4 | TC-6305 |
| resume | W-P2 | W1→W3→W4 | W1→W3, W3→W4 | TC-6304 |
| pauseFromWatch | V-P1 | V1→V2→V8 (대결) | V1→V2, V2→V8 | TC-6320 (c) |
| pauseFromWatch | V-P2 | V1→V3→V4→V8 (RUNNING → PAUSED) | V1→V3, V3→V4, V4→V8 | TC-6320 (a), TC-6379 |
| pauseFromWatch | V-P3 | V1→V3→V5→V6→V8 (LOCKED → PAUSED_LOCKED) | V3→V5, V5→V6, V6→V8 | TC-6320 (b), TC-6376 |
| pauseFromWatch | V-P4 | V1→V3→V5→V7→V8 (그 밖의 상태) | V5→V7, V7→V8 | TC-6320 (d)(e) |
| resumeFromWatch | Z-P1 | Z1→Z2→Z7 (정지 중 아님) | Z1→Z2, Z2→Z7 | TC-6320 (h)(i) |
| resumeFromWatch | Z-P2 | Z1→Z3→Z4→Z6→Z7 (PAUSED → RUNNING) | Z1→Z3, Z3→Z4, Z4→Z6, Z6→Z7 | TC-6320 (f) |
| resumeFromWatch | Z-P3 | Z1→Z3→Z4→Z5→Z7 (PAUSED_LOCKED → LOCKED) | Z4→Z5, Z5→Z7 | TC-6320 (g), TC-6377 |
| pausedTotal | R1 | M1→M3→M5 (닫힌 0, 열린 없음) | M1→M3, M3→M5 | TC-6306 |
| pausedTotal | R2 | M1→M2→M1→M3→M5 | M1→M2, M2→M1 | TC-6307 |
| pausedTotal | R3 | M1→M3→M4→M5 | M3→M4, M4→M5 | TC-6313 |
| tick | K-P1 | K1→K2→K11 (정지 중) | K1→K2, K2→K11 | TC-6308, TC-6313, TC-6332 |
| tick | K-P2 | K1→K3→K4→K5→K7→K8→K10→K11 | K1→K3, K3→K4, K4→K5, K5→K7, K7→K8, K8→K10, K10→K11 | TC-6306 |
| tick | K-P3 | K1→K3→K6→K7→K8→K10→K11 | K3→K6, K6→K7 | TC-6309 |
| tick | K-P4 | K1→K3→K4→K6→… | K4→K6 | TC-6310 |
| tick | K-P5 | …K7→K9→K10… | K7→K9, K9→K10 | TC-6311 |
| onLocation | P1~P6 | 2.2.6과 같음(P5는 N5→N5b→N6) | P5에 N5→N5b, N5b→N6 | TC-1271~TC-1276, 정지 중 P1 TC-6314, 재개 뒤 첫 NORMAL P2 TC-6315, P5 TC-6317 |
| onLocation | P9 | N1→N3→N4→N5→N5a→N6→N7→N8→N11→N12→N15 (재개 뒤 첫 포인트 UNSTABLE) | N5→N5a, N5a→N6 | TC-6316, TC-6318 |
| onLocation | P7·P8 | 2.2.6과 같음 | 없음 | 도달 불가(2.2.6 이유 그대로. resume이 lastNormal과 창을 함께 비우므로 새로 도달할 수 있게 되지 않는다) |

루프 검사(pausedTotal의 닫힌 정지 반복, 상한은 설계에 없고 서버 RunUpload가 100개까지 받는다): 0회 TC-6306, 1회 TC-6307, 2회·100회 TC-6312. 데이터 흐름: `pausedSince` 정의 U2 → 사용 W3·M3·finish/end 닫기·currentPause (TC-6304, TC-6313, TC-6363, TC-6323). `lastResumedAt` 정의 W3 → 사용 N5 (TC-6316, TC-6318: 두 번째 재개가 다시 정의). `lastNormal` W3에서 null → 사용 N4·N9 (TC-6315). `window` W3에서 비움 → 사용 K3·K4 (TC-6310). `elapsed` 정의 K1 → 사용 K2·K8 (TC-6308, TC-6307). v0.6 후속: K-P1에서도 K1의 `elapsed`가 K2의 결과에 쓰여 정의-사용 없는 정의가 없다(D-157 (4), TF-AN-27 해소).

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-6301 | FR-194 | pause(10:10:00Z) | 자유런(FREE), RUNNING, 시작 10:00:00Z | 반환 참, 상태 PAUSED, currentPause(10:10:30Z) = 30초, pauses 0개(열린 구간은 목록에 아직 없음) (U-P3, D-93 (3)) | 없음 | 미실행(구현 전) |
| TC-6302 | FR-194 | pause(10:10:00Z) | (a) 대결(BATTLE) (b) 크루런(CREW_RUN), 각각 RUNNING | 두 경우 거짓, 상태 RUNNING, currentPause(10:10:30Z) = null, pauses 0개 (U-P1, RunMode.isPausable 거짓, D-137) | 없음 | 미실행(구현 전) |
| TC-6303 | FR-194, FR-182 | pause(10:12:00Z) | 자유런, 상태 (a) LOCKED (b) PAUSED(10:10:00Z부터) (c) COUNTDOWN (d) FINISHED (e) PAUSED_LOCKED(10:10:00Z부터), 각각 새 RunSession | 다섯 경우 모두 거짓, 상태 그대로, (b)·(e)의 열린 정지 시작 10:10:00Z 그대로(덮어쓰지 않음), 예외 없음 (U-P2). (a)는 폰 버튼 경로이고 워치 요청의 LOCKED → PAUSED_LOCKED는 pauseFromWatch(TC-6320, TC-6376, D-157 (1)). 정지 중 판정은 isPaused(PAUSED·PAUSED_LOCKED, D-157 (2)) | 없음 | 일부 폐기(v0.9, CR-26: 앱 화면 잠금 삭제(FR-182 삭제됨), 새 상태 집합은 TC-9623·TC-9624): 앱 화면 잠금 상태(LOCKED·PAUSED_LOCKED, 해제 안내) 부분의 기대 결과는 폐기, 나머지는 유효 |
| TC-6304 | FR-194, FR-036 | resume(10:15:00Z) | 자유런 PAUSED(10:10:00Z부터) | 참, RUNNING, pauses = [PauseInterval(10:10:00Z, 10:15:00Z)], currentPause = null, lastResumedAt 10:15:00Z (W-P2) | 없음 | 미실행(구현 전) |
| TC-6305 | FR-194 | resume(10:15:00Z) | (a) RUNNING (b) LOCKED (c) COUNTDOWN (d) FINISHED | 네 경우 거짓, 상태·pauses 그대로 (W-P1). PAUSED_LOCKED는 TC-6375·TC-6377 | 없음 | 미실행(구현 전) |
| TC-6306 | FR-034 | tick(10:20:00Z) | 자유런 RUNNING, 시작 10:00:00Z, 정지 0회, 창 처음 (10:19:30Z, 1,900m)·끝 (10:20:00Z, 2,000m), 거리 2,000m | 경과 1,200초, 현재 페이스 300 s/km, 평균 페이스 600 s/km, isPaused 거짓 (K-P2, pausedTotal R1) | 없음 | 미실행(구현 전) |
| TC-6307 | FR-034, FR-194 | tick(10:20:00Z) | 시작 10:00:00Z, 닫힌 정지 [10:05:00Z~10:10:00Z] 1개, RUNNING, 거리 1,500m, 창 유효 | 경과 900초(벽시계 20분 − 정지 5분), 평균 페이스 600 s/km(운동 시간 ÷ 거리. 벽시계로 나눈 800 s/km가 아님, FR-034 v0.10) (K-P2, R2, 반복 1회) | 없음 | 미실행(구현 전) |
| TC-6308 | FR-034, FR-194 | tick(10:12:00Z), tick(10:14:00Z) | 자유런, 시작 10:00:00Z, 10:10:00Z tick 진행값(경과 600초, 1,000m, 현재 330 s/km, 평균 600 s/km, 심박 150bpm) 직후 10:10:00Z pause, 정지 중 onHeartRate(160bpm, 10:11:00Z). (a) PAUSED (b) 그 뒤 lock()으로 PAUSED_LOCKED | 두 tick 모두 경과 600초(정지 시작 때 값. 벽시계 − 정지 합), 거리 1,000m, 평균 600 s/km, 현재 페이스 null(빈 값), 심박 160bpm(정지 중에도 갱신), isPaused 참. (b)는 isLocked도 참 (K-P1, FR-034 "정지 중에는 지표를 늘리지 않는다", D-144, D-157 (4)) | 없음 | 미실행(구현 전) |
| TC-6309 | FR-034 | tick(10:20:00Z) | RUNNING, 창 처음 (10:19:30Z, 1,900m)·끝 (10:20:00Z, 1,900m)(제자리), 직전 현재 페이스 320 s/km, 거리 1,900m | 현재 페이스 320(직전 값), 평균 페이스는 계산(1,200초 ÷ 1.9km ≈ 632 s/km) (K-P3) | 없음 | 미실행(구현 전) |
| TC-6310 | FR-034, FR-194 | (a) resume(10:15:00Z) 뒤 NORMAL 포인트 1개(10:15:01Z), 이어 tick(10:15:01Z) (b) 이어 NORMAL 포인트 1개(10:15:11Z, 30m 앞), tick(10:15:11Z) | 정지 전 마지막 현재 페이스 330 s/km, 정지 중 tick으로 현재 페이스가 빈 값이 된 상태 | (a) 창 1건(처음·끝 시각 같음), 현재 페이스 null(빈 값. 재개 때 창을 비우고 직전 값이 정지 중 빈 값이라 새 구간 표본이 찰 때까지 빈 값), 창에 정지 전 포인트 0건 (K-P4, D-93 (2), D-144, D-157 (4)) (b) 창 처음(10:15:01Z) 끝(10:15:11Z) 거리 차 30m, 현재 페이스 약 333 s/km(10초 ÷ 0.03km) (K-P2) | 없음 | 미실행(구현 전) |
| TC-6311 | FR-034 | tick(10:00:05Z) | 시작 10:00:00Z, 포인트 없음 | 경과 5초, 거리 0, 평균 페이스 null (K-P5) | 없음 | 미실행(구현 전) |
| TC-6312 | FR-034, FR-194 | tick(11:00:00Z) | 시작 10:00:00Z, RUNNING, 닫힌 정지 (a) 2개(5분, 10분) (b) 100개(각 10초), 각각 새 세션 | (a) 경과 2,700초 (b) 경과 2,600초 (pausedTotal 반복 2회·100회. 100은 서버 RunUpload 정지 구간 상한과 같은 수) | 없음 | 미실행(구현 전) |
| TC-6313 | FR-194, FR-195 | pausedTotal(10:13:00Z)(internal, 시험 가시성), currentPause(10:13:00Z), tick(10:13:00Z) | 시작 09:50:00Z, 닫힌 정지 [10:00:00Z~10:05:00Z], 열린 정지 10:10:00Z부터(PAUSED) | pausedTotal 8분(닫힌 5분 + 열린 3분), currentPause 3분, tick 경과 900초(벽시계 23분 − 8분. 열린 정지 항이 tick 결과의 경과에 쓰여 공개 연산으로도 관찰된다) (K-P1, D-157 (4), TF-AN-27 해소) | 없음 | 일부 폐기(v0.9, CR-25: 정지 시간 상한·자동 종료 삭제(FR-195 삭제됨), 대체 TC-9621·TC-9622): FR-195 자동 종료·정지 한도 부분만 폐기, 나머지는 유효 |
| TC-6314 | FR-194 | onLocation(NORMAL 10:11:00Z), onLocation(NORMAL 10:11:01Z, 5m 떨어짐) | 정지 전 경로 600건·거리 1,000m. (a) PAUSED (b) PAUSED_LOCKED | 두 경우 경로 600건·거리 1,000m·창 그대로(포인트 버림), 불안정 구간 0건 (onLocation P1, D-93 (1), D-157 (2) 정지 중 = isPaused) | 없음 | 미실행(구현 전) |
| TC-6315 | FR-034, FR-194 | resume(10:15:00Z) 뒤 NORMAL n2(10:15:01Z, 정지 전 마지막 정상 포인트 n1과 300m), 이어 NORMAL n3(10:15:02Z, n2와 3m) | 정지 전 거리 1,000m | n2: 경로 +1건, 거리 1,000m 그대로(n1–n2 300m를 더하지 않음, onLocation P2), 창 1건. n3: 거리 1,003m (P3) (FR-194 "재개 첫 유효 위치 포인트와 정지 직전 포인트 사이 거리는 넣지 않는다") | 없음 | 미실행(구현 전) |
| TC-6316 | FR-033, FR-194 | resume(10:15:00Z) 뒤 첫 포인트 UNSTABLE u(10:15:12Z) | 정지 [10:10:00Z~10:15:00Z], 정지 전 거리 1,000m | 불안정 구간 1건(시작 10:15:00Z = 마지막 재개 시각, 끝 10:15:12Z, 건너뛴 거리 0), 정지 구간과 겹치는 시간 0, 거리 1,000m 그대로 (P9, D-121) | 없음 | 미실행(구현 전) |
| TC-6317 | FR-033 | 첫 포인트 UNSTABLE 10:00:12Z | 시작 10:00:00Z, 재개한 적 없음(lastResumedAt 없음) | 불안정 구간(10:00:00Z~10:00:12Z, 0m) (P5, N5b. TC-1275와 같은 결과) | 없음 | 미실행(구현 전) |
| TC-6318 | FR-033, FR-194 | 재개 두 번(10:15:00Z, 10:30:00Z), 두 번째 재개 뒤 첫 포인트 UNSTABLE 10:30:05Z | 두 정지 사이에 정상 포인트 있음 | 불안정 구간 시작 10:30:00Z(가장 최근 재개 시각. lastResumedAt 다시 정의 → 사용) (P9) | 없음 | 미실행(구현 전) |
| TC-6319 | FR-041, FR-194 | onHeartRate(150bpm, 10:12:00Z), 이어 resume(10:15:00Z), finish(10:20:00Z) | 자유런 PAUSED(10:10:00Z부터), 거리 1,000m | RunSession 심박 목록과 CompletedRun.heartRates에 10:12:00Z 표본 포함 (D-93 (1) 정지 중 심박은 남긴다) | 없음 | 미실행(구현 전) |
| TC-6320 | FR-194, FR-182 | RunSession.pauseFromWatch(10:10:00Z), resumeFromWatch(10:15:00Z) | 각각 새 RunSession(시작 10:00:00Z). 정지: (a) 자유런 RUNNING (b) 자유런 LOCKED (c) 대결 RUNNING (d) 자유런 PAUSED(10:05:00Z부터) (e) 자유런 COUNTDOWN. 재개: (f) 자유런 PAUSED(10:10:00Z부터) (g) 자유런 PAUSED_LOCKED(10:10:00Z부터) (h) 자유런 RUNNING (i) 자유런 LOCKED | 정지 (a) 참, PAUSED, 열린 정지 시작 10:10:00Z (b) 참, PAUSED_LOCKED, 열린 정지 시작 10:10:00Z (c) 거짓, 상태 그대로 (d)(e) 거짓, 상태 그대로이고 (d)의 열린 정지 시작 10:05:00Z 그대로. 재개 (f) 참, RUNNING, pauses [10:10:00Z~10:15:00Z], lastResumedAt 10:15:00Z, lastNormal·창 비움 (g) 참, LOCKED, pauses [10:10:00Z~10:15:00Z], lastResumedAt 10:15:00Z (h)(i) 거짓, 상태·pauses 그대로 (V-P1~V-P4, Z-P1~Z-P3, D-157 (1). 시각은 폰이 처리한 시각, D-157 (5), TF-AN-23 해소) | 없음 | 일부 폐기(v0.9, CR-26: 앱 화면 잠금 삭제(FR-182 삭제됨), 새 상태 집합은 TC-9623·TC-9624): 앱 화면 잠금 상태(LOCKED·PAUSED_LOCKED, 해제 안내) 부분의 기대 결과는 폐기, 나머지는 유효 |

### 2.2.14 화이트박스(설계 v0.11): RunTracker.tick 정지 한도 (v0.6)

6.1.14 tick 행과 A-02 "RunTracker.tick(now)의 정지 한도 검사"를 합친 의사 코드다. 자동 종료 안내는 RunWatchBridge.publishNotice(notice: WatchNotice?)로 워치에 싣는다(D-157 (7), TF-AN-26 해소). 표시의 notice는 재개(publishNotice(null)) 또는 종료 때까지 남는다.

```kotlin
// 설계 v0.11 의사 코드 (6.1.14 RunTracker.tick, A-02, D-93 (6), D-95, PauseLimit)
fun tick(now: Instant) {
    val s = session ?: return                                                 // N1 → N2
    val progress = s.tick(now); _progress.value = progress
    val pause = s.currentPause(now)                                           // N3: pause == null?
    if (pause == null) { coach.guide(progress, s) }                           // N4
    else if (pause >= PauseLimit.END_AFTER) {                                 // N5
        endRun(); coach.play(Guidance(PAUSE, PAUSE_AUTO_ENDED)); watchBridge.publishNotice(AUTO_ENDED); return   // N6
    } else if (pause >= PauseLimit.WARN_AFTER                                 // N7
               && warnedPauseStart != s.pauseStart) {                         // N8
        warnedPauseStart = s.pauseStart
        coach.play(Guidance(PAUSE, PAUSE_AUTO_END_SOON)); watchBridge.publishNotice(AUTO_END_SOON)   // N9 (재개 또는 종료까지 유지)
    }
    watchBridge.publish(display(progress, s.spec.mode.isPausable))            // N10 (notice는 RunWatchBridge가 publishNotice로 받은 값을 싣는다)
}                                                                             // N11
```

간선 15개: N1→N2, N1→N3, N2→N11, N3→N4, N3→N5, N4→N10, N5→N6, N5→N7, N6→N11, N7→N10, N7→N8, N8→N10, N8→N9, N9→N10, N10→N11.

| 방법 | 값 |
|------|----|
| E − N + 2 | 15 − 11 + 2 = **6** |
| 단순 조건 + 1 | 세션 없음 1, 정지 아님 1, `≥ END_AFTER` 1, `≥ WARN_AFTER` 1, 이번 정지에 경고함 1 = 5, + 1 = **6** |
| 영역 수 | N1 1, N3 1, N5 1, N7·N8 2, 바깥 1 = **6** |

| 경로 | 노드 | 새 간선 | TC |
|------|------|---------|----|
| P1 | N1→N2→N11 | N1→N2, N2→N11 | TC-6321 |
| P2 | N1→N3→N4→N10→N11 | N1→N3, N3→N4, N4→N10, N10→N11 | TC-6322 |
| P3 | N1→N3→N5→N6→N11 | N3→N5, N5→N6, N6→N11 | TC-6323 |
| P4 | N1→N3→N5→N7→N10→N11 | N5→N7, N7→N10 | TC-6324 |
| P5 | …N7→N8→N9→N10… | N7→N8, N8→N9, N9→N10 | TC-6325 |
| P6 | …N7→N8→N10… | N8→N10 | TC-6325(둘째 tick) |

경곗값: WARN_AFTER 55분(54:59·55:00·55:01, TC-6326), END_AFTER 60분(59:59·60:00·60:01, TC-6323). 둘 다 한쪽 경계("닿으면" = 이상)다. 데이터 흐름: `warnedPauseStart` 정의 N9 → 사용 N8 (TC-6325 같은 정지, TC-6327 다음 정지). 조건 검사 `≥ WARN_AFTER && 경고 안 함`: (참, 참) TC-6325 첫 tick, (참, 거짓) TC-6325 둘째 tick, (거짓, −) TC-6324.

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-6321 | FR-034, FR-195 | tick(10:00:05Z) | 세션 없음(측정 전·종료 뒤) | progress 방출 0, RunCoach.guide 0, RunWatchBridge.publish 0, endRun 0, 예외 없음 (P1) | 없음 | 일부 폐기(v0.9, CR-25: 정지 시간 상한·자동 종료 삭제(FR-195 삭제됨), 대체 TC-9621·TC-9622): FR-195 자동 종료·정지 한도 부분만 폐기, 나머지는 유효 |
| TC-6322 | FR-034, FR-042, FR-049 | tick(10:05:00Z) | 자유런 RUNNING | progress 방출 1, guide 1, publish(WatchDisplay isRunning 참, isPaused 거짓, isPausable 참, notice null) 1, 정지 안내 0 (P2) | 없음 | 미실행(구현 전) |
| TC-6323 | FR-195, FR-036 | 정지 시작 10:00:00Z 뒤 tick 10:59:59Z / 11:00:00Z / 11:00:01Z, 각각 새 회(셋째는 11:00:00Z tick이 건너뛴 경우) | 자유런, 시작 09:30:00Z, 정지 전 거리 3,000m, 이전 정지 없음 | 10:59:59Z: endRun 0. 11:00:00Z: endRun 1회 → CompletedRun(pauses [10:00:00Z~11:00:00Z], 경과 1,800초, 3,000m) Saved, MeasurementRuntime.stop 1, RunArchive.saveInBackground 1, Guidance(PAUSE, PAUSE_AUTO_ENDED) 1건, RunWatchBridge.publishNotice(AUTO_ENDED) 1회, 워치 표시 1건(isRunning 거짓, notice AUTO_ENDED), 세션 없음. 11:00:01Z: 같은 결과(정지 구간 끝 11:00:01Z) (P3, END_AFTER 경계 −1초·정각·+1초, D-93 (6), D-157 (7)) | 없음 | 폐기(v0.9, CR-25: 정지 시간 상한·자동 종료 삭제(FR-195 삭제됨), 대체 TC-9621·TC-9622) |
| TC-6324 | FR-195 | tick(10:54:59Z) | 자유런 PAUSED(10:00:00Z부터) | 정지 안내 0, publish(isPaused 참, notice null) 1, guide 0, endRun 0 (P4) | 없음 | 폐기(v0.9, CR-25: 정지 시간 상한·자동 종료 삭제(FR-195 삭제됨), 대체 TC-9621·TC-9622) |
| TC-6325 | FR-195 | tick 10:55:00Z, 10:55:01Z, 10:56:00Z 연속 | 자유런 PAUSED(10:00:00Z부터) | 10:55:00Z: Guidance(PAUSE, PAUSE_AUTO_END_SOON) 1건, publishNotice(AUTO_END_SOON) 1회로 그 tick의 워치 표시 notice가 AUTO_END_SOON. 10:55:01Z·10:56:00Z: 추가 안내 0, publishNotice 추가 0회이며 워치 표시의 notice는 AUTO_END_SOON 그대로 유지(재개 또는 종료까지 남음) (P5 뒤 P6, 정지 구간마다 한 번, D-157 (7), TF-AN-26 해소) | 없음 | 폐기(v0.9, CR-25: 정지 시간 상한·자동 종료 삭제(FR-195 삭제됨), 대체 TC-9621·TC-9622) |
| TC-6326 | FR-195 | 각각 새 회에서 정지 뒤 첫 tick이 10:54:59Z / 10:55:00Z / 10:55:01Z | 자유런 PAUSED(10:00:00Z부터) | 경고 0 / 1 / 1건(tick이 55분 정각을 건너뛰어도 경고, ≥ 비교) (WARN_AFTER 경계) | 없음 | 폐기(v0.9, CR-25: 정지 시간 상한·자동 종료 삭제(FR-195 삭제됨), 대체 TC-9621·TC-9622) |
| TC-6327 | FR-195, FR-194 | 정지 A 10:00:00Z~10:56:00Z(경고 받음) 뒤 재개, 정지 B 11:00:00Z부터, tick 11:54:59Z / 11:55:00Z / 11:59:59Z | 자유런, 거리 2,000m | 11:54:59Z 경고 0, 11:55:00Z 경고 1(정지 B의 첫 경고, 회 전체 2건), 11:59:59Z 자동 종료 0. 한도는 지금 정지만 센다(정지 A·B 합이 60분을 넘어도 끝내지 않음). 정지 A의 재개(10:56:00Z) 때 publishNotice(null) 1회로 AUTO_END_SOON 표시가 지워진다(D-157 (7)) | 없음 | 폐기(v0.9, CR-25: 정지 시간 상한·자동 종료 삭제(FR-195 삭제됨), 대체 TC-9621·TC-9622) |
| TC-6328 | FR-195, FR-194, FR-182 | 잠금 중 워치 PAUSE로 PAUSED_LOCKED(10:00:00Z부터) 뒤 tick 10:55:00Z, 11:00:00Z | 자유런, 거리 2,000m | 10:55:00Z 경고 1, 11:00:00Z endRun(잠금과 무관하게 확정, RunSession.end) → Saved, 저장 1 (D-120 "정지 한도는 PAUSED와 같다", D-157 (2) 정지 중 = isPaused. PAUSED_LOCKED는 워치 PAUSE 요청이 pauseFromWatch로 만든다, TF-AN-23 해소) | 없음 | 폐기(v0.9, CR-26: 앱 화면 잠금 삭제(FR-182 삭제됨), 새 상태 집합은 TC-9623·TC-9624; CR-25: 정지 시간 상한·자동 종료 삭제(FR-195 삭제됨), 대체 TC-9621·TC-9622) |
| TC-6329 | FR-195, FR-036 | 시작 10:00:00Z, 첫 위치 포인트 전 10:00:10Z pause, tick 11:00:10Z | 거리 0 | endRun → NotSaved, saveInBackground 0, stop 1, 세션 없음, PAUSE_AUTO_ENDED 안내 1건, publishNotice(AUTO_ENDED) 1회 (D-157 (7): 거리 0이면 FR-036대로 저장하지 않고 같은 안내만 낸다, TF-AN-26 해소) | 없음 | 폐기(v0.9, CR-25: 정지 시간 상한·자동 종료 삭제(FR-195 삭제됨), 대체 TC-9621·TC-9622) |
| TC-6330 | FR-049, FR-045, FR-194 | 정지 중 tick 10회(1초 간격) | 정지 전 거리 999m, 심박 180bpm 계속(기준 171), 정지 9분 | RunCoach.guide 0회, CoachingJudge.evaluate 0회, SPLIT·BREATH 안내 0건 (P4, FR-049 v0.10 "일시정지 중에는 안내하지 않는다", D-93 (8)) | 없음 | 미실행(구현 전) |
| TC-6331 | FR-195 | PauseLimit.WARN_AFTER, PauseLimit.END_AFTER | object 상수 읽기 | 55분, 60분, WARN_AFTER < END_AFTER. SettingKey(운영 기준값)에 두 값 없음 (D-113 소유 클래스 상수) | 없음 | 폐기(v0.9, CR-25: 정지 시간 상한·자동 종료 삭제(FR-195 삭제됨), 대체 TC-9621·TC-9622) |
| TC-6332 | FR-034, FR-042, FR-194 | 정지 중 tick 뒤 RunWatchBridge.publish | 자유런, 10:10:00Z 정지(경과 600초, 1,000m, 평균 600 s/km, 정지 전 현재 페이스 330 s/km, 심박 150), 정지 중 onHeartRate(160bpm, 10:11:00Z), tick(10:12:00Z). (a) PAUSED (b) PAUSED_LOCKED | 폰 진행값(경과 600초, 1,000m, 현재 페이스 null, 평균 600, 심박 160, isPaused 참)과 같은 값의 WatchDisplay 1건(현재 페이스 없음이라 워치 빈칸, heartRateBpm 160, isPaused 참, isPausable 참). 정지 중 심박은 표시에 반영되고 지표는 늘지 않음 (D-144 "WatchDisplay도 같은 값") | 없음 | 미실행(구현 전) |
| TC-6333 | FR-195, FR-194 | AUTO_END_SOON 경고(10:55:00Z) 뒤 (a) 폰 onResumeTapped() (b) 워치 RESUME 요청 | 자유런 PAUSED(10:00:00Z부터). (b)는 PAUSED_LOCKED | (a)(b) 모두 publishNotice(null) 1회, 다음 tick의 워치 표시 notice null, 정지 한도 경고 상태 해제(다음 정지는 새 구간으로 센다, TC-6327) (D-157 (7) "재개 또는 종료까지 표시에 남긴다") | 없음 | 폐기(v0.9, CR-25: 정지 시간 상한·자동 종료 삭제(FR-195 삭제됨), 대체 TC-9621·TC-9622) |

