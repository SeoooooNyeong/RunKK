> [런크크 객체지향 분석·설계서](../design-oo.md) 목차 · 이전: [06-02-클래스-running-analysis-profile](06-02-클래스-running-analysis-profile.md) · 다음: [06-04-클래스-crew](06-04-클래스-crew.md)

#### 6.1.6 battle

> v0.16: Battle·BattleParticipant·BattleType·BattleResultService·GhostMatcher·BattleStartService·CelebrityRecord에 더한 연산(상대 캐릭터 성별, 셀럽 그림 키, cancelByRunner, allowsCancel)과 신규 OpponentGenders·BattleRandomConfig는 [05-11 2.1](05-11-설계-결정-v0.16.md)에 있다. v0.14의 cancelOnForfeit은 없어졌다(D-318).

v0.11의 대결 근접 알림(FR-196)과 추월·역전 긴 진동(FR-188)은 폰 domain.run이 격차로 계산해 워치로 보낸다. 서버 battle의 클래스·메시지는 바뀌지 않는다(랜덤 대결 격차는 기존 OpponentProgress 그대로 쓴다) (v0.11, FR-196, D-96)

##### MatchTicket
| 항목 | 내용 |
|------|------|
| 패키지 | battle |
| 책임 | 매칭 대기자 한 명의 대기 상태를 관리한다 |

속성: memberId(long, 대기열 키), tier(Tier, 랭크 티어만), distance(StandardDistance, 랜덤 거리만), waitingSince(LocalDateTime), status(MatchStatus).

| 시그니처 | 설명 | 사전 조건 | 사후 조건 / 예외 |
|---------|------|----------|-----------------|
| `static MatchTicket open(long memberId, Tier tier, StandardDistance d, LocalDateTime now)` | 생성 | tier.isRanked(), d.isRandomBattle() | 위반이면 IllegalArgumentException |
| `boolean isExpired(LocalDateTime now, Duration limit)` | 대기 시간 초과 | | now − waitingSince > limit. Matchmaker가 고스트 전환(ghostAfter 15초)과 포기(ghostAfter + 15초)에 쓴다 (v0.8, D-64). 정각은 만료가 아니다: 30초 정각 tick은 표를 유지하고 다음 tick(31초)에 MatchFailed를 보낸다(v0.10, TF-VB-15) |
| `boolean canPairWith(MatchTicket other, MatchCondition cond)` | 후보 판정 | | 다른 회원, 둘 다 WAITING, 같은 거리, cond.accepts(tier, other.tier) (srs.md 6.3 4.2 표) |
| `void assign()` | 배정됨 | WAITING | ASSIGNED. 아니면 IllegalStateException |
| `void release()` | 배정 되돌리기 | ASSIGNED | WAITING, waitingSince 유지. 대결 저장·고스트 매칭 트랜잭션이 실패했을 때만 (v0.8, D-64) |

QueueRemoval(enum, battle): REMOVED, ASSIGNED, ABSENT. MatchQueue.removeIfWaiting이 취소 시점의 표 상태를 원자 연산 한 번으로 알려 주는 값이다(v0.10, TF-IT-16, D-83).

##### Battle
| 항목 | 내용 |
|------|------|
| 패키지 | battle |
| 책임 | 대결 한 건의 생애주기를 관리한다 |

속성:
| 이름 | 형 | 제약 |
|------|----|------|
| id | long | |
| type | BattleType | 불변 |
| distance | Distance | 불변. 랜덤·고스트·나와의 싸움은 표준 거리, 유명인은 기록 거리 |
| status | BattleStatus | 상태도 2.4.2. 바꾸는 곳은 private moveTo 하나이고, 바꿀 때마다 참가자의 활성 대결 칸을 맞춘다(BattleParticipant.activeMemberId) (v0.11, CR34-01) |
| participants | List<BattleParticipant> | 랜덤 2명, 기록 대결 1명 |
| opponent | OpponentRef | 기록 대결만 |
| createdAt, startAt, countdownEndsAt | LocalDateTime | |
| closedAt | LocalDateTime | nullable. 결과 보류에 들어간 시각 또는 취소 시각(cancelUnstarted) (v0.11, CR34-01) |
| judgement | Judgement | nullable |
| version | long | 낙관적 잠금 |

상수: `ABANDON_AFTER = 10분`. 출발하지 못한 대결을 버려진 것으로 보는 시간이다. 시작 보고 없는 기록 대결은 생성 뒤, 출발 예약 대결(WAITING_START)은 출발 시각(없으면 생성 시각) 뒤 이만큼 지나도 시작하지 않으면 CANCELLED다. 가장 짧은 기록 대결 최대 시간(3km × 15분 = 45분)보다 짧다. SRS에 값이 없어 코드 값을 옮겼다(SRS 보완 요청) (v0.11, CR34-01)

활성 대결 1개 불변식: 한 회원은 활성 대결(WAITING_START, IN_PROGRESS, COUNTDOWN)을 많아야 하나 가진다. battle_participant.active_member_id(활성이면 회원 ID, 아니면 NULL)의 고유 색인(V15, 기존 자료 정리 V17)이 동시 생성 경합까지 지킨다 (v0.11, CR34-01)

연산:
| 시그니처 | 설명 | 사전 조건 | 사후 조건 / 예외 |
|---------|------|----------|-----------------|
| `static Battle random(MatchTicket a, MatchTicket b, LocalDateTime now)` | 랜덤 대결 생성 | a, b ASSIGNED | WAITING_START |
| `static Battle againstRecord(RecordBattleSpec spec, LocalDateTime now)` | 기록 대결 생성 | spec.type ∈ {GHOST, SELF, CELEBRITY} | GHOST는 WAITING_START, SELF·CELEBRITY는 IN_PROGRESS 대기(startAt 없음) |
| `void scheduleStart(LocalDateTime startAt)` | 출발 시각 | WAITING_START | |
| `void start(LocalDateTime now)` | 출발 | WAITING_START이고 now ≥ startAt, 또는 기록 대결의 시작 보고 | IN_PROGRESS. 기록 대결이면 startAt = now, 상대 도착 시각 = startAt + opponent.arrivalElapsed |
| `boolean recordArrival(Side side, LocalDateTime at)` | 도착 기록 | IN_PROGRESS 또는 COUNTDOWN | 첫 도착이면 COUNTDOWN, countdownEndsAt = at + 10초, true. 카운트다운 안 도착이면 기록만, false. 카운트다운 뒤면 무시 |
| `void recordOvertakes(long memberId, int count)` | 추월 횟수 | | |
| `void markDisconnected(long memberId, LocalDateTime at)` / `void markReconnected(long memberId, LocalDateTime at)` | 끊김·복구 | | Disconnection 추가·닫기 |
| `void forfeit(long memberId, LocalDateTime now)` | 포기 | 참가자, IN_PROGRESS 또는 COUNTDOWN | 포기자 패, 상대 승(랜덤). FINISHED. 출발 전 대결은 이 연산 전에 cancelIfUnstarted로 취소한다(v0.13, D-200) |
| `void closeCountdown(LocalDateTime now)` | 카운트다운 종료·최대 시간 초과 | now ≥ countdownEndsAt 또는 now ≥ startAt + 거리 × 15분 | RESULT_PENDING |
| `void verify(long memberId, RecordCheck check)` | 참가자 기록 검증 | 참가자 | 기록 ID, 대결 판정용 유효 여부, 도달 경과 시간, 유효 러닝 순번을 참가자에 기록한다(D-482). |
| `void markPlacement(int round)` / `OptionalInt placementRound()` | 배치고사 판 번호 지정·조회 | 저장 전 GHOST 대결에 한 번만 지정 | 생성 당시 판 번호가 이력·매칭 성사에 쓰인다(D-478·D-534). |
| `Optional<Judgement> judge(LocalDateTime now)` | 판정 | | srs.md 6.3 4.5 의사결정표(6.2 A-10). WAITING_START이거나 포기한 참가자가 있으면 비어 있음(포기 판정은 forfeit가 이미 했다). 먼저 도착한 쪽 기록(또는 1초 안에 따라 도착한 쪽 기록)이 아직 없으면 비어 있음. 판정되면 FINISHED. 무승부는 두 기록이 모두 유효하고 1초 이내일 때만이다. 규칙 3(먼저 도착한 쪽 무효)이면 첫 판정이어도 corrected = true이고, 상대에게 유효한 도착이 없으면 두 참가자 모두 무승부(승자 없음)다. 이미 FINISHED였고 결과가 바뀌어도 corrected = true (v0.8, TF-BE-08, TF-VB-02, TF-VB-03, TF-BE-10, D-63). 규칙 3 갈래는 판정 전 상태가 FINISHED가 아닐 때만 정정이라, 이미 FINISHED인 대결에 늦은 기록이 와 판정이 같으면 corrected = false다(v0.10, TF-BE-20, D-82) |
| `Optional<Judgement> expirePending(LocalDateTime now)` | 24시간 보류 만료 | RESULT_PENDING, now ≥ countdownEndsAt + 24시간 | 없는 기록을 무효로 보고 판정. 규칙 3이어도 corrected = false(srs.md 6.3 4.5) |
| `boolean isAbandoned(LocalDateTime now)` | 버려진 대결인가 | | 시작 보고 없는 기록 대결(IN_PROGRESS, startAt 없음, SELF·CELEBRITY)이면 createdAt부터, WAITING_START면 startAt(없으면 createdAt)부터 ABANDON_AFTER가 지났으면 true. 그 밖의 상태는 false. BattleRepository.findAbandoned의 조건과 같다 (v0.11, CR34-01) |
| `void cancelUnstarted(LocalDateTime now)` | 출발하지 못한 대결 취소 | WAITING_START이거나 시작 보고 전 기록 대결 | CANCELLED(활성 아님), closedAt = now, 참가자 활성 칸을 비운다. 그 밖의 상태면 IllegalStateException. 판정·이력·전적·리워드에 나타나지 않는다 (v0.11, CR34-01) |
| `boolean cancelIfAwaitingStartReport(LocalDateTime now)` | 시작 보고 전 기록 대결이면 취소 | | 시작 보고 전 기록 대결이면 cancelUnstarted 뒤 true, 그 밖의 활성 대결은 그대로 두고 false. 상태 확인과 취소를 한 연산으로 묶었다(Tell, Don't Ask, 코드 리뷰 3차 B-L4) (v0.11, CR34-01) |
| `boolean cancelIfUnstarted(LocalDateTime now)` | 출발 전 포기 = 대결 취소 | | 출발하지 못한 대결(WAITING_START, 시작 보고 전 기록 대결)이면 cancelUnstarted 뒤 true(CANCELLED, 승패·리워드·이력·결과 없음), 출발한 대결은 그대로 두고 false. BattleResultService.forfeit가 부른다. 상태 확인과 취소를 한 연산으로 묶었다(cancelIfAwaitingStartReport와 같은 방식) (v0.13, CR-16, D-200) |
| `boolean isCancelled()` | 취소된 대결인가 | | status == CANCELLED (v0.11, CR34-01) |
| `boolean isParticipant(long memberId)`, `boolean isActive()` | | | 활성 = WAITING_START, IN_PROGRESS, COUNTDOWN. CANCELLED는 활성이 아니다 (v0.11, CR34-01) |
| 조회 | getId, getType, getDistance, getStatus, getStartAt, getCountdownEndsAt, getJudgement, getOpponent, participant(memberId) | | |

Side는 참가자 memberId 또는 OPPONENT(기록 상대)를 가리키는 값이다(sealed: MemberSide(long), OpponentSide).

BattleStatus(enum, battle): WAITING_START, IN_PROGRESS, COUNTDOWN, RESULT_PENDING, FINISHED, CANCELLED. `static Set<BattleStatus> active()` = 앞 셋, `boolean isActive()`, `boolean isRunning()`(IN_PROGRESS·COUNTDOWN, 러너가 달리는 중일 수 있는 상태). CANCELLED는 끝 상태이고 판정·이력·전적에 없다(상태도 2.4.2) (v0.11, CR34-01)

##### BattleParticipant
속성: memberId, arrivedAt, recordId, verified(Boolean, null = 기록 없음), overtakes(int), forfeited(boolean), disconnections(List<Disconnection>), reachElapsed(Duration, 기록상 도달 경과 시간), activeMemberId(Long, 대결이 활성이면 memberId·아니면 null, 고유 색인 칼럼 battle_participant.active_member_id) (v0.11, CR34-01).

| 시그니처 | 설명 |
|---------|------|
| `void syncActive(boolean active)` | 패키지 전용. Battle.moveTo만 부른다. 활성이면 activeMemberId = memberId, 아니면 null (v0.11, CR34-01) |
| `boolean arrive(LocalDateTime at)` | 처음이면 true |
| `void verify(String recordId, boolean valid, Duration reachElapsed)` | 기록 검증 결과와 기록상 도달 시간 |
| `void forfeit()` | 포기 |
| `ArrivalClaim claim(LocalDateTime startAt)` | 판정용 도착 주장 = startAt + reachElapsed (D-30) |

##### OpponentRef (값, record)
속성: kind(OpponentKind), recordId(String 또는 long을 문자열로), displayName(DisplayName), displayTier(Tier), arrivalElapsed(Duration). 기록이 지워져도(유명인 삭제, 기록 주인 탈퇴) 대결 이력은 이 값으로 남는다(FR-172).

##### ArrivalClaim, Judgement (값, record)
ArrivalClaim: side, arrivedAt, verified(Boolean). Judgement: verdicts(Map<Side, Verdict>), winner(Side, nullable), corrected(boolean), at.

##### OpponentRecord (인터페이스)
| 시그니처 | 설명 |
|---------|------|
| `OpponentTrack track(Distance battleDistance)` | 경과 시간별 누적 거리 표. 대결 거리 도달 뒤는 자른다 |
| `DisplayName displayName()` | 상대 표시명 |
| `Tier displayTier()` | 상대 티어(유명인은 표시하지 않으므로 UNRANKED) |
| `OpponentKind kind()` | RUN_RECORD, VIRTUAL, CELEBRITY |
| `ComparisonValues comparisonValues(Distance battleDistance)` | 비교표 상대 값. 공개되지 않은 항목은 비어 있음 → "공개 안 됨"(FR-093) |

##### RunRecordOpponent (OpponentRecord 구현)
속성: recordId(String), comparison(RunComparison), curve(RunCurve), ownerName(DisplayName), ownerTier(Tier), label(나와의 싸움이면 "MM/DD의 나").
`track`은 running.RunQueryService.curve가 1초 간격으로 표본화한 RunCurve를 OpponentTrack으로 바꾼다(위치 좌표는 표에 없다, FR-089). `comparisonValues`는 RunQueryService.comparisonOf(recordId)가 내준 RunComparison의 기록(elapsed)·평균 페이스를 쓴다. 고스트·나와의 싸움 모두 같은 연산으로 채운다(D-51, design-review.md M20). RunSummary를 받지 않는다. RunRecord 엔티티를 갖지 않는다(D-32). 경로가 파기된 기록은 RunQueryService.validRunsReaching이 돌려주지 않아 고스트 후보에서 빠진다. 고스트 주인 이름·티어는 MemberQueryService.ghostEligibleOwners의 GhostOwner에서, 나와의 싸움은 본인 MemberSummary에서 채운다.

##### VirtualRecord (OpponentRecord 구현, 엔티티)
속성: id, tier, distance(StandardDistance), nickname(String), finishTime(Duration), track(OpponentTrack, 균등 페이스). 생성은 VirtualRecordFactory만 한다.

##### CelebrityRecord (OpponentRecord 구현, 엔티티)
| 항목 | 내용 |
|------|------|
| 책임 | 운영자가 등록한 유명인 공개 기록 하나를 보관한다 |

속성: id, anonymousName(String, not blank), distance(Distance > 0), finishTime(Duration > 0), splits(OpponentTrack, nullable), source(Source), registeredAt.

| 시그니처 | 설명 | 사후 조건 / 예외 |
|---------|------|-----------------|
| `static CelebrityRecord register(CelebrityInput in, LocalDateTime now)` | 등록 | 출처명 또는 출처 주소 빔 → SOURCE_REQUIRED(FR-170 v0.9), 익명 닉네임 빔 → ANONYMOUS_NAME_REQUIRED, 익명 닉네임 2~20자 밖 → VALUE_INVALID(표시명 규칙, 20자 허용·21자 거부), 거리·시간 ≤ 0 → VALUE_INVALID, 거리가 StandardDistance.isCelebrity()(5000·10000·21097·42195m)가 아님 → BATTLE_DISTANCE_INVALID(409), 구간이 줄어듦 → SPLITS_INVALID (srs.md 6.3 7.1, v0.8, TF-BE-13). v0.8의 "거리는 대결 거리 집합에 묶이지 않고 1m도 허용"은 v0.13 D-205로 대체 (v0.13, CR-21, D-205) |
| `OpponentTrack track(Distance d)` | splits가 없으면 OpponentTrack.uniform(distance, finishTime) | |
| `boolean isEstimated()` | splits 없음 → 화면에 "평균 페이스로 추정"(A-006) | |
| `ComparisonValues comparisonValues(Distance d)` | 기록·평균 페이스만, 가장 빠른 1km는 splits가 있을 때만 | |

##### Source (값, record)
속성: name(String, not blank), url(String, not blank, FR-170 v0.9 출처 주소 필수, v0.8, TF-BE-13). `String label()` = "공개 기록 기준 · {name}"(FR-100).

##### OpponentTrack (값)
| 항목 | 내용 |
|------|------|
| 책임 | 경과 시간별 누적 거리 표로 상대 위치를 보간한다 |

속성: samples(List<TrackSample(elapsed, distance)>, 경과 시간 오름차순, 누적 거리 단조 증가).

| 시그니처 | 설명 |
|---------|------|
| `static OpponentTrack uniform(Distance d, Duration finish)` | 균등 페이스 표(1초 간격) |
| `Distance distanceAt(Duration elapsed)` | 직선 보간. 마지막 표본 뒤는 마지막 거리 |
| `Optional<Duration> arrivalTime(Distance d)` | d 도달 경과 시간 |
| `OpponentTrack cut(Distance d)` | d 이후 표본 제거 |

앱에도 같은 이름·연산의 Kotlin 클래스가 있다(domain.battle.OpponentTrack). 계약 테스트로 두 구현의 결과가 같은지 확인한다(14장).

##### ComparisonTable (값)
`static ComparisonTable of(BattleType type, RunComparison mine, ComparisonValues theirs, BattleParticipant me)`: type.comparisonItems() 순서대로 ComparisonItem.mine/theirs 값을 줄로 만든다. mine은 RunQueryService.comparisonOf가 돌려준 값(elapsed, averagePace, fastestKm)이고 세 항목을 모두 쓴다(D-41).

##### ComparisonItem (enum, 다형성)
상수별 `String mine(RunComparison r, BattleParticipant p)`, `String theirs(ComparisonValues v)`: RECORD_TIME, AVERAGE_PACE, FASTEST_KM, OVERTAKES, MAX_LEAD(가장 앞섰을 때 거리 차), MAX_GAP(최대 거리 차).

##### BattleType (enum, 다형성)
| 상수 | comparisonItems() | historyGroup() | startsWithCountdown() |
|------|-------------------|----------------|----------------------|
| RANDOM | 기록, 평균 페이스, 가장 빠른 1km, 추월 횟수 | RANDOM | false (성사 13초 뒤) |
| GHOST | RANDOM과 같음 | RANDOM (고스트 표시 안 함, Q-02) | false |
| SELF | 기록, 평균 페이스, 가장 빠른 1km, 가장 앞섰을 때 거리 차 | SELF | true (3초) |
| CELEBRITY | 기록, 평균 페이스, 가장 빠른 1km, 최대 거리 차 | CELEBRITY | true (3초) |

##### LiveBattleSession
| 항목 | 내용 |
|------|------|
| 책임 | 진행 중인 랜덤 대결의 최신 진행값을 보관한다 |

속성: battleId, distance(Distance), latest(Map<Long, LiveProgress(distance, elapsed, sentAt, receivedAt)>, sentAt은 보낸 기기의 ProgressReport.sentAt, v0.8, TF-SY-05), leaderId(Long, nullable), overtakes(Map<Long, Integer>), arrived(Set<Long>), lastSeen(Map<Long, LocalDateTime>), disconnected(Set<Long>), names(Map<Long, DisplayName>).

v0.13 정정(코드 기준, D-211): 코드는 참가자마다 Runner(memberId, name, distanceMeters, elapsedMillis, lastSeen, overtakes, arrived, disconnected) 하나로 들고(runners: Map<Long, Runner>, 저장 형식 Snapshot(battleId, distanceMeters, leaderId, runners)) sentAt을 두지 않는다. 받은 시각은 lastSeen뿐이고 OpponentProgress에 sentAt이 없다. 위 v0.8 속성의 sentAt은 v0.13 D-211로 대체.

| 시그니처 | 설명 |
|---------|------|
| `List<BattleDelivery> onProgress(long memberId, ProgressReport r, LocalDateTime now)` | 최신값 갱신 → 상대에게 OpponentProgress → 추월 판정(거리 차 10m 초과로 선두가 바뀜, FR-188) → 도착 판정(누적 ≥ 대결 거리) → 끊겼던 참가자면 복구 표시. 돌려준 목록(받는 회원 + 메시지)을 서비스가 BattleMessenger로 보낸다 (v0.11, CR2-U7) |
| `List<BattleDelivery> detectTimeouts(LocalDateTime now)` | 3초 넘게 진행값이 없는 참가자를 끊김으로 표시하고 상대에게 알린다(FR-090) (v0.11, CR2-U7) |
| `Optional<Long> newArrival()` | 방금 도착한 참가자(서비스가 Battle.recordArrival 호출) |
| `Map<Long, Integer> overtakeCounts()` | 카운트다운 종료 때 Battle.recordOvertakes로 옮긴다 |

##### VirtualRecordFactory
| 시그니처 | 설명 |
|---------|------|
| `List<VirtualRecord> generate(TierBoundary boundary, VirtualRecordRule rule, Predicate<String> nicknameTaken)` | srs.md 6.3 7.5, 6.2 알고리즘. 티어 5 × 거리 4 × 5개 = 100개 |

##### MatchingService
| 항목 | 내용 |
|------|------|
| 의존 | MemberQueryService(member), MemberSettingService(member), MatchQueue, BattleRepository, Clock |

| 시그니처 | 설명 |
|---------|------|
| `MatchAcceptance requestMatch(long memberId, StandardDistance distance)` | 거리·위치 동의·티어를 확인한다. 배치 중이면 `PlacementMatcher.start`로 대결을 즉시 만든다. 잠금 뒤 배치 완료가 확인되면 일반 대기열로 이어 간다. 일반 대기열을 쓸 수 없으면 QUEUE_UNAVAILABLE이다. 성공한 거리는 MemberSettingService.rememberBattleDistance로 저장한다(D-478·D-515·D-540). |
| `MatchAcceptance cancel(long memberId)` | MatchQueue.removeIfWaiting(memberId) 한 번으로 정한다: REMOVED면 대기해제, ASSIGNED면 거부(ALREADY_MATCHED, FR-085), ABSENT면 거부(대기 중 아님). 상태 확인과 삭제를 따로 부르지 않아 고스트 시작(tryAssign)과 겹쳐도 "취소됐는데 고스트 대결"이 없다(v0.10, TF-IT-16, D-83). 고스트 매칭 중(tryAssign 뒤)도 ASSIGNED라 거부되고, 실패로 release되면 다시 취소할 수 있다 (v0.8, D-64) |
| `MatchAcceptance status(long memberId)` | 지금 매칭 대기 상태와 기본 대결 거리(GET /battles/match). MatchQueue.findWaiting이 WAITING이면 (대기 중, 대기 거리, 기본 거리), 아니면 대기해제 + 기본 거리. 기본 거리는 MemberSettingService.battleSettingsOf(memberId).defaultDistance(마지막으로 요청한 거리, FR-080·FR-109)다. 앱 대결 화면이 회원 설정을 따로 읽지 않고 홈 배틀런 버튼의 거리를 정한다 (v0.11, CR2-U1) |

MatchAcceptance(값, record)는 `waiting`, `distance`, `defaultDistance`, `placementRound`, `battleId`를 담는다. 배치고사 즉시 성사 때 판 번호와 대결 ID가 있고, 기존 3인자 생성자는 두 값을 null로 둔다(D-529, D-540). 거부는 BusinessRuleException의 사유 코드로 알린다.

##### 배치고사 시작과 상대 생성

| 클래스 | 명세 |
|---|---|
| PlacementMatcher | `start(memberId, distance, now): Optional<Battle>`. 회원의 배치 진행을 잠가 다시 확인한다. 이미 완료됐으면 empty를 반환한다. `saveImmediately`로 GHOST 대결과 참가자 활성 칸을 저장한다. 활성 칸 고유 제약 충돌은 ALREADY_IN_BATTLE로 대응한다(D-515·D-518). |
| PlacementOpponentFactory | `create(basis, distance, boundary, rule): PlacementOpponent`. 첫 판은 기준값의 공개 최저 점수, 이후 판은 임시 티어 가운데 점수로 가상 완주 시간과 상대 기록을 만든다. 같은 거리 가상 기록에서 닉네임을 빌리고 후보가 없으면 MATCH_FAILED다(FR-276, A-38). |
| PlacementOpponent | OpponentRecord 구현. 가상 상대의 기록 ID·표시명·도달 시간·거리 곡선을 제공한다. 성사 통지의 상대 티어는 null이다(FR-276). |
| RecordCheck | record(recordId, valid, reachElapsed, validRunOrdinal). valid는 대결 거리 도달을 포함한 판정용 검증값이다. BattleParticipant는 순번이 양수일 때 `validRunOrdinal`에 저장한다. 배치고사 판수 인정에는 붙은 러닝 기록 자체의 유효 여부를 쓴다(D-482·D-511). |

##### 배치고사 판수와 결과 조회

| 클래스 | 명세 |
|---|---|
| PlacementRoundCheck | record(finished, cancelled, forfeited, hasRecord, recordValid, expiredWithoutRecord, summaryMissingExpired). `of(battle, memberId, recordValid)`는 셈·복구용, `of(battle, memberId, recordValid, now)`는 결과 조회용이다. 기록 ID가 있어도 요약을 읽지 못하면 조회에서 `SUMMARY_GRACE`(판정 뒤 24시간) 경과 여부를 확인한다(D-521). |
| PlacementRoundState | `of(check, counted, progressCompleted)`는 이미 센 판을 COUNTED로, 취소·포기·무효·기한 만료·배치 완료 뒤 판정은 NOT_COUNTED로, 그 밖에는 PENDING으로 분류한다(D-487·D-521). |
| PlacementRoundReader | BattleRepository·RunQueryService·PlacementService·Clock과 협력한다. `stateOf(memberId, battleId)`는 참가자만 읽을 수 있다. 취소·미존재 대결은 409 BATTLE_NOT_FOUND, 일반 대결은 empty다. `findSummary`로 기록 유효 여부를 읽는다. 셀 수 있는 PENDING이 판정 뒤 7일을 넘기면 경고를 남기고 NOT_COUNTED로 표시한다(D-522·D-525). |
| PlacementRecordAttached | record(battleId, memberId, at). 이미 판정된 배치 대결에 늦게 연결된 기록에서 판정이 바뀌지 않아도 판수 반영을 다시 시도한다(D-512). |
| PlacementRoundListener | BattleJudged와 PlacementRecordAttached를 수신한다. `count(battleId)`는 셌거나 이미 셌으면 참이다. `uq_placement_round_battle` 중복은 수신자가 이미 센 것으로 처리한다. 정정은 count 뒤에 적용한다. 요약 누락은 경고를 남기고 후속 복구로 넘긴다(D-524·D-538). |
| PlacementReconcileService | `reconcile(now)`는 `BattleRepository.findPlacementFinishedBetween(from, to)`로 최근 7일간의 판정 대결을 읽어 판수 반영을 다시 시도한다(D-490). |

판수 동시 반영 시 Hibernate의 follow-on 잠금과 버전 확인 뒤 충돌한 수신자가 새로 읽어 재시도한다. Hibernate 변경의 회귀 기준은 TC-5923이다. 판 목록 크기와 판수의 불변식 위반은 오류로 남기고 일일 복구 작업으로 넘긴다(D-539).

requestMatch는 이미 대기 중이거나 활성 대결이 있으면 ALREADY_IN_BATTLE로 거부한다(활성 대결 1개 불변식) (v0.11, CR34-01)

##### Matchmaker
| 시그니처 | 설명 |
|---------|------|
| `void tick(LocalDateTime now)` | 대기 표를 오래 기다린 순으로 한 번 돈다. 6.2 A-06. 이미 짝지은 표는 건너뛰고, partnerOf로 후보를 찾아 pairUp이 참이면 두 표를 짝지은 것으로 두고 거짓이면 resolveUnpaired로 넘긴다(V(G) 4, v0.10, TF-BE-21, D-79). 15초 초과(후보가 없었거나 tryAssignPair가 실패한 경우 모두) → 고스트, 30초 초과 → MatchFailed(code "MATCH_FAILED", message 문구, v0.13 정정 D-210) (v0.8, TF-BE-11, TF-BE-12, TF-BE-16, D-64). 의존: MatchQueue, BattleRepository, BattleStartService, GhostMatcher, BattleMessenger, OperationPolicy, BattleTransactions. 돌기 전에 대기 표 회원 전체를 BattleRepository.activeMemberIds로 한 번에 조회해(대기자 수만큼 조회하지 않는다, 코드 리뷰 3차 B-L8) 활성 대결이 이미 있는 회원의 표는 WAITING일 때 지우고 MatchFailed로 대기 화면을 닫는다. 대기 등록과 기록 대결 생성이 겹쳐 표와 활성 대결을 함께 가진 회원이 매 tick 활성 칸 고유 제약에 걸려 짝이 된 상대까지 막는 일을 없앤다 (v0.11, CR34-01) |
| `private MatchTicket partnerOf(MatchTicket a, List<MatchTicket> waiting, Set<Long> paired, MatchCondition cond)` | a와 짝지을 첫 후보를 찾는다. waiting에서 a 뒤의 표 중 a.canPairWith(b, cond)이고 paired에 없는 첫 표, 없으면 null(V(G) 4, v0.10, D-79) |
| `private boolean pairUp(MatchTicket a, MatchTicket b, LocalDateTime now)` | 후보와의 랜덤 대결 성사를 한 번 시도한다. b가 null이거나 tryAssignPair(a, b)가 실패하면 false. 성공하면 새 트랜잭션에서 Battle.random 저장·announceMatch → 커밋되면 두 표 remove, 실패면 두 표 release와 로그 random_match_failed(두 회원 ID) 뒤 true(V(G) 4, v0.10, D-79) |
| `private void resolveUnpaired(MatchTicket a, MatchCondition cond, LocalDateTime now)` | 짝을 짓지 못한 표를 대기 시간으로 처리한다. ghostAfter + 15초를 넘었으면 remove 성공 때 MatchFailed(code "MATCH_FAILED", message 문구, v0.13 정정 D-210), ghostAfter를 넘었으면 tryAssign → 새 트랜잭션 GhostMatcher.match → 성공 remove, 실패 release와 로그 ghost_match_failed(회원 ID). 둘 다 아니면 아무것도 하지 않는다(V(G) 6, v0.10, D-79) |

##### GhostMatcher
| 시그니처 | 설명 |
|---------|------|
| `Battle match(MatchTicket ticket, LocalDateTime now)` | A-07. Tier.searchLadder 순으로 MemberQueryService.ghostEligibleOwners(tiers, 요청자) → RunQueryService.validRunsReaching(distance, 주인 ID들) → OwnedRun 목록 → 후보가 있으면 무작위 하나를 골라 RunQueryService.comparisonOf(recordId)·curve(recordId, distance)로 RunRecordOpponent를 만든다(D-51). 세 단계 모두 없으면 같은 티어 가상 기록 5개 중 무작위 → Battle.againstRecord(GHOST) → 저장 → BattleStartService.announceMatch. 가상 기록이 0개면 IllegalStateException을 던지고 대결을 만들지 않는다(FR-095 v0.9). 대기 표를 되돌려 다시 시도하는 것은 Matchmaker 몫이다 (v0.8, TF-BE-16) |

##### BattleStartService
| 시그니처 | 설명 |
|---------|------|
| `void announceMatch(Battle b)` | 양쪽(또는 한쪽) 최고 기록을 RunQueryService.best로 읽어 MatchFound → Battle.scheduleStart(now + 3초 + 10초) → BattleStart(기록 상대면 track 포함). 두 메시지는 부른 트랜잭션이 커밋된 뒤에만 BattleMessenger로 나간다. 저장이 되돌려진 대결의 통지가 나가지 않게 한다 (v0.8, D-64) |
| `int startDue(LocalDateTime now)` | startAt이 지난 WAITING_START 대결을 start |
| `void resendStart(long memberId)` | 읽기 전용 트랜잭션. BattleRepository.findActive(memberId)의 대결 가운데 카운트다운으로 시작하지 않는 종류(BattleType.startsWithCountdown 거짓: RANDOM·GHOST)이고 startAt이 있는 대결마다 startMessage(battle)로 BattleStart를 BattleMessenger.send로 다시 보낸다. realtime.LiveSocketHandler.afterConnectionEstablished가 등록·우편함 비우기(messenger.flush) 뒤 부르고, 실패(RuntimeException)는 로그만 남기고 연결을 유지한다. 출발 예약 때 미리 보낸 BattleStart가 끊기던 연결로 나가 사라지는 일(보내기가 실패해야 우편함에 남는다)을 막는다. 앱은 같은 BattleStart를 한 번만 쓴다 (v0.13, D-207) |
| `int cancelAbandoned(LocalDateTime now)` | BattleRepository.findAbandoned(now − ABANDON_AFTER)의 대결마다 새 트랜잭션(BattleTransactions.once)에서 다시 읽어 isAbandoned면 cancelUnstarted. 시작 보고 없이 버려진 나와의 싸움·유명인 대결과 출발 시각이 지나도 시작하지 못한 대결이 활성 칸을 계속 쥐어 새 대결을 막는 일을 없앤다. 시작 보고가 없는 기록 대결은 도착 시각을 정할 수 없어 판정하지 않고 취소한다. 취소한 수를 돌려준다. batch.battle AbandonedBattleJob이 1분마다 부른다 (v0.11, CR34-01, CR34-02) |

##### LiveBattleService
| 항목 | 내용 |
|------|------|
| 의존 | LiveSessionStore, BattleRepository, BattleMessenger, NotificationService, Clock |

| 시그니처 | 설명 |
|---------|------|
| `void onProgress(long memberId, long battleId, ProgressReport r, LocalDateTime now)` | SQ-04. battleId는 realtime이 수신 메시지의 채널 ID로 넘긴다(D-48). 대결이 IN_PROGRESS·COUNTDOWN이 아니거나 회원이 참가자가 아니면 무시 |
| `void detectTimeouts(LocalDateTime now)` | 진행 중 세션 전부에 detectTimeouts → Battle.markDisconnected |

##### BattleClockService
| 시그니처 | 설명 |
|---------|------|
| `void tick(LocalDateTime now)` | (1) startDue (2) 기록 상대 도착 시각이 된 대결 → Battle.recordArrival(OPPONENT) → CountdownStarted + 알림(OPPONENT_ARRIVED) (3) 카운트다운 끝·최대 시간 초과 → closeCountdown → BattleEnded, 추월 횟수 저장 → judge(기록이 이미 다 왔으면 판정, corrected면 BATTLE_CORRECTED 알림, v0.8, D-63) (4) 보류 24시간 → expirePending → BattleJudged |
| `int resumeLiveSessions(LocalDateTime now)` | 서버 시작 때 한 번. LiveSessionStore에 남은 세션 중 BattleRepository에서 IN_PROGRESS·COUNTDOWN인 대결만 남기고 나머지 세션을 지운다. 남긴 대결은 다음 tick부터 시계가 이어진다. 시작 사이에 지난 최대 시간·카운트다운은 첫 tick이 처리한다. 이어 간 대결 수를 돌려준다(5.5, D-55) |

##### BattleStartupListener
`void onApplicationReady(ApplicationReadyEvent e)`: VirtualRecordService.ensureGenerated() → BattleClockService.resumeLiveSessions(clock.now()). 시작 작업을 가진 패키지가 직접 받는다(bootstrap → battle 화살표를 만들지 않는다, D-55, design-review.md E12).

##### RecordBattleService
| 항목 | 내용 |
|------|------|
| 의존 | MemberQueryService(member), RunQueryService(running), CelebrityRecordRepository, BattleRepository, Clock |

| 시그니처 | 설명 |
|---------|------|
| `RecordBattleCandidates selfCandidates(long memberId)` | 3·5·10km·하프 최고 기록(RunQueryService.best, 없으면 "아직 기록이 없어요") |
| `RecordBattleCandidates celebrityCandidates(long memberId, Optional<StandardDistance> filter)` | 유명인 기록 목록·출처, 거리 필터 |
| `RecordBattleStart startSelfBattle(long memberId, StandardDistance d)` | d.isSelfBattle(), 위치 동의 확인(MemberQueryService.hasConsent) → RunQueryService.best(memberId, d)(없으면 NO_RECORD) → comparisonOf(recordId), curve(recordId, d) → RunRecordOpponent("MM/DD의 나") → Battle.againstRecord(SELF) → 시작 정보(track, 표시명) |
| `RecordBattleStart startCelebrityBattle(long memberId, long celebrityId)` | 위치 동의 확인 → CelebrityRecordRepository.findById(없으면 CELEBRITY_REMOVED) → Battle.againstRecord(CELEBRITY) → 시작 정보(track, 익명 닉네임, 출처, 추정 여부) |
| `void markStarted(long memberId, long battleId)` | 3초 카운트다운 끝에 앱이 보고 → Battle.start(now) (SRS 보완 요청 R-02). 이미 시작했으면 무시(앱 재시도 멱등). 버려져 CANCELLED가 된 대결이면 BATTLE_NOT_FOUND (v0.11, CR34-01) |

v0.1의 `candidates(memberId, BattleType, filter)`와 `start(memberId, RecordBattleRequest)`는 type 값이 전혀 다른 조회·생성 경로를 골라 제어 결합이었다. 종류마다 연산을 나눴다(D-36).

기록 대결 생성 규칙(startSelfBattle·startCelebrityBattle 공통, 패키지 전용 create): (1) 랜덤 매칭 대기 중이면 ALREADY_IN_BATTLE. (2) BattleRepository.findActive(memberId)의 대결마다 Battle.cancelIfAwaitingStartReport(now)가 거짓이면(출발했거나 출발 예약된 대결) ALREADY_IN_BATTLE, 참이면 saveImmediately로 먼저 써서 활성 칸을 비운다(앱에서 카운트다운 전에 뒤로 가 다시 고른 경우 새 대결이 이전 대결을 대신한다). (3) 새 대결을 saveImmediately. (4) 낙관적 잠금 충돌(같은 이전 대결을 다른 요청이 먼저 대신함)과 SQLSTATE 23505(활성 칸 고유 제약 위반, 동시 생성 경합에서 짐)만 ALREADY_IN_BATTLE로 바꾸고, 그 밖의 무결성 위반(23502 NOT NULL 등)은 그대로 던진다(코드 리뷰 3차 B-L3). 제약 이름 대신 SQLSTATE로 보는 것은 H2 시험 스키마의 제약 이름이 PostgreSQL과 달라서다 (v0.11, CR34-01)

##### BattleResultService
| 항목 | 내용 |
|------|------|
| 의존 | BattleRepository, RunQueryService(running), MemberQueryService(member), NotificationService, DomainEventPublisher, BattleMessenger, Clock |

| 시그니처 | 설명 |
|---------|------|
| `void onRunSaved(RunRecordSaved e)` | 대결ID가 있을 때만. 사건의 valid와 RunQueryService.timeToReach(recordId, 대결 거리)로 verify(valid AND reached, 도달 경과 시간)(D-51) → judge → 판정되면 BattleJudged, corrected면 참가자마다 알림(BATTLE_CORRECTED). 규칙 3의 첫 판정도 corrected다 (v0.8, D-63). 이미 FINISHED인 대결에 늦은 기록이 와서 판정이 같으면 corrected가 거짓이라 정정 알림을 다시 보내지 않는다(v0.10, TF-BE-20, D-82). 대결이 없거나 참가자가 아니거나 CANCELLED면 아무것도 하지 않는다 (v0.11, CR34-01) |
| `List<BattleWin> winsJudgedBetween(LocalDateTime from, LocalDateTime to)` | 판정 시각이 [from, to)이고 회원이 이긴 FINISHED 대결(BattleRepository.findMemberWinsJudgedBetween, 읽기 전용). 정정된 대결은 지금 승자와 정정 판정 시각이다. reward.RewardReconcileService가 빠진 BATTLE_WIN 적립을 찾을 때 부른다(reward → battle 메서드 호출, M4 일 단위 보정) (v0.11, CR34-01, CR34-03, CR34-04) |
| `void forfeit(long memberId, long battleId)` | Battle.cancelIfUnstarted(now)가 참이면(출발 전) 승패·리워드 없이 CANCELLED로 저장하고 랜덤 상대에게 BattleEnded(OPPONENT_FORFEITED)만 커밋 뒤 보낸 뒤 끝낸다(BattleJudged 없음, 로그 battle_cancelled_by_forfeit). 거짓이면 기존대로 Battle.forfeit → 상대에게 BattleEnded(OPPONENT_FORFEITED) → BattleJudged. 취소된 대결의 결과 조회는 409 BATTLE_NOT_FOUND다 (v0.13, CR-16, D-200) |
| `BattleResultView result(long memberId, long battleId)` | 참가자만. 보류면 "보류" + 기록이 없는 본인에게 재시도 안내. 아니면 판정 문구, ComparisonTable(RunQueryService.comparisonOf), 이번 주 전적. 적립 리워드는 이 결과에 없다. web.battle의 BattleResultAssembler가 reward.RewardService.accruedFor(BATTLE_WIN, battleId, memberId)를 붙인다(battle → reward 의존 제거, D-33). CANCELLED 대결은 결과가 없고(BATTLE_NOT_FOUND) 이번 주 전적에서도 뺀다 (v0.11, CR34-01) |

`BattleResultService.applyRecord`는 `RecordCheck`로 참가자 기록을 검증하고 판정이 나오면 BattleJudged를 발행한다. 판정 없이 배치고사 기록만 붙으면 PlacementRecordAttached를 발행한다(D-512). `comparison`과 `theirs`는 `RunQueryService.findComparison`을, `recordGaps`는 `curveOf`를 사용해 기록 없음도 처리한다(D-524). 결과의 `placementRound`와 `PlacementRoundReader`가 읽은 `placementState`를 배치고사 응답에 제공한다(D-513·D-522).

##### BattleHistoryService
| 시그니처 | 설명 |
|---------|------|
| `BattleHistoryView history(long memberId, DateRange range, Optional<BattleType> filter)` | FINISHED·RESULT_PENDING 최신순. 필터 RANDOM은 GHOST 포함(BattleType.historyGroup). 상대 표시명: 랜덤은 상대 회원 표시명(탈퇴면 "탈퇴한 사용자"), 기록 대결은 OpponentRef.displayName. 통계(대결 수, 승·패, 나와의 싸움 신기록 수) |
| `Optional<ActiveBattleView> active(long memberId)` | 읽기 전용. BattleRepository.findActive(memberId)의 첫 대결(회원당 많아야 1개)을 ActiveBattleView(battleId, type, status, distance, opponentName, startAt)로 바꾼다. 상대 표시명은 history와 같은 규칙(OpponentNames.of, 랜덤 상대는 MemberQueryService.summaries 한 번)이고 종류는 이력 분류(historyGroup)가 아니라 BattleType 그대로(GHOST는 GHOST)다. startAt은 시작 보고 전 기록 대결이면 비어 있다. 없으면 빈 Optional → web.battle이 204 (v0.13, CR-18, FR-220, D-202) |

##### ActiveBattleView (값, record, v0.13)

battleId(long), type(BattleType), status(BattleStatus, 활성 셋 중 하나), distance(Distance), opponentName(DisplayName, 탈퇴면 "탈퇴한 사용자"), startAt(LocalDateTime, 없으면 null). web.battle의 BattleDtos.ActiveBattleResponse.of가 `{battleId, type, status, distanceMeters, opponentName, startAt}`(type·status는 enum 이름, startAt은 오프셋 없는 LocalDateTime)로 바꾼다 (v0.13, FR-220, D-202, D-213)

##### VirtualRecordService
| 항목 | 내용 |
|------|------|
| 의존 | VirtualRecordRepository, VirtualRecordFactory, MemberQueryService(member), OperationPolicy(policy), Clock |

| 시그니처 | 설명 |
|---------|------|
| `void ensureGenerated()` | 시작 때. 100개가 아니면 regenerate |
| `int regenerate()` | VirtualRecordFactory.generate(policy.tierBoundary(), policy.virtualRecordRule(), MemberQueryService::isNicknameTaken) → 기존 전부 교체(한 트랜잭션)(FR-104, Q-86). 회원 닉네임과 겹치는 가상 닉네임을 만들지 않는다(design-review.md H-11', D-45) |
| `void onOperationSettingChanged(OperationSettingChanged e)` | policy가 발행한 사건을 커밋 뒤 받는다. e.key가 TIER_BOUNDARY 또는 VIRTUAL_RECORD_RULE이면 regenerate(). 두 값 모두 가상 기록 생성의 입력이다(design-review.md H-14, D-45). policy가 battle을 부르지 않게 한다(D-34) |

##### CelebrityRecordService
| 항목 | 내용 |
|------|------|
| 패키지 | battle |
| 책임 | 유명인 기록 등록·삭제 유스케이스를 조정한다 |
| 의존 | CelebrityRecordRepository, AuditService, Clock |

| 시그니처 | 설명 |
|---------|------|
| `CelebrityResult register(String actorId, CelebrityInput in)` | CelebrityRecord.register → 저장 → 감사(유명인기록등록, 행위자 actorId·OPERATOR)(FR-170, FR-171) |
| `CelebrityResult delete(String actorId, long celebrityId, String reason)` | 삭제 → 감사(유명인기록삭제, 사유). 진행 중·끝난 대결의 OpponentRef는 그대로 남는다(FR-172) |

actorId는 web.admin의 OperatorCheckInterceptor가 operator.OperatorAuthorizer.ensureOperator로 확인하고 web.admin.celebrity가 요청 속성으로 받아 넘긴 운영자 계정이다. 이 서비스는 operator를 import하지 않는다(D-34, D-50). 결과 CelebrityResult(celebrityId, 처리 결과)는 battle 소유다. 관리 서비스마다 결과 형식을 따로 두어 battle·policy가 한 형식을 함께 쓰지 않는다(design-review.md M31, R29). v0.1 관리툴 패키지의 유명인 기록 서비스를 옮긴 것이다.

##### BattleMessenger (인터페이스)와 BattleMessage
| 시그니처 | 설명 | 사후 조건 / 예외 |
|---------|------|-----------------|
| `void send(long memberId, BattleMessage m)` | 회원 한 명에게 보낸다 | 연결이 없으면 버리지 않고 짧은 우편함(30초)에 둔다. 재연결 때 최신 순으로 전달 |
| `void sendAll(Collection<Long> memberIds, BattleMessage m)` | 여러 명에게 보낸다 | |

BattleMessage는 `String type()` 하나를 가진 인터페이스다. 구현 record 7종(MatchFound, MatchFailed(v0.8, D-64. 항목은 code: String("MATCH_FAILED")·message: String이고 MatchFailReason enum은 없다, v0.10 D-85를 v0.13 D-210으로 대체), BattleStart, OpponentProgress, BattleGuidance, CountdownStarted, BattleEnded)은 battle.live 하위 패키지에 있고 항목은 5.7.2 표와 같다. sealed를 쓰지 않는다. 구현: realtime.WebSocketMessenger, realtime.PollingMessenger(D-06).

MatchFound에 생성 당시의 `placementRound`를 싣는다. 기존 8인자 생성자는 null을 사용한다(D-529). `BattleHistoryView.Entry`는 같은 판 번호와 `opponentMemberId`를 담고, 판 번호가 있으면 「배틀런 · 배치고사 n판」을 표시한다(D-534).

##### BattleJudged (사건, record)
battle이 발행한다(D-31). 항목: battleId, type(BattleType), judgement(참가자별 판정, 정정 여부), at. 수신: reward.RewardService. 접근자 `Optional<Side> winnerSide()`(judgement.winnerSide()에 위임, 무승부·무효면 비어 있음)와 `LocalDateTime judgedAt()`(judgement의 판정 시각)을 둔다. reward는 이 둘만 읽고 Judgement를 import하지 않는다(D-134 조건 (1), 구조 규칙 (11)) (v0.11 구현 반영, U5-A-3).

##### BattleWin (값, record)
속성: battleId(long), winnerId(long, 이긴 회원), judgedAt(LocalDateTime, BattleJudged의 Judgement.at과 같다). 회원이 이긴 판정 한 건이다. BattleResultService.winsJudgedBetween이 돌려주고 reward 일 단위 보정이 읽는다. 정정으로 승자가 바뀌었으면 지금 승자와 정정 시각이다 (v0.11, CR34-01, CR2-U7)

##### BattleWithdrawalListener, ActiveBattleGuard, VirtualNicknameChecker
- BattleWithdrawalListener.`onMemberWithdrawn(e)`: MatchQueue.remove, 대결 기록은 남긴다(참가자 표시명은 조회 때 회원이 없으면 "탈퇴한 사용자", FR-021).
- ActiveBattleGuard(member.WithdrawalGuard 구현).`check(memberId)`: 대기 중이거나 활성 대결이 있으면 "진행 중인 대결 종료 필요". member.IdleGuestWithdrawalGuard도 같은 check로 구현한다(미이용 게스트 파기는 건너뛰고 다음 날 다시) (v0.11 검증, D-141)
- VirtualNicknameChecker(member.ReservedNicknameChecker 구현).`isReserved(nickname)`: 가상 닉네임과 같으면 true.

##### 포트 인터페이스 (battle이 정의)
| 인터페이스 | 시그니처 |
|-----------|---------|
| MatchQueue | `boolean enqueue(MatchTicket)`, `boolean remove(long memberId)`(무조건 삭제: Matchmaker 정리·탈퇴), `QueueRemoval removeIfWaiting(long memberId)`(WAITING일 때만 원자적으로 지우고 REMOVED, ASSIGNED면 ASSIGNED, 표가 없으면 ABSENT. 취소 전용, QueueRemoval은 battle enum, v0.10, TF-IT-16, D-83), `Optional<MatchTicket> findWaiting(long memberId)`, `List<MatchTicket> waitingOldestFirst()`, `boolean tryAssignPair(long a, long b)`, `boolean tryAssign(long memberId)`(WAITING → ASSIGNED, 고스트 매칭 동안 취소를 막는다), `void release(long memberId)`(ASSIGNED → WAITING) (v0.8, D-64) |
| BattleRepository | `Battle save(Battle)`, `Battle saveImmediately(Battle)`(저장하고 바로 쓴다. 이전 대결의 활성 칸을 비운 뒤 같은 회원의 새 대결을 넣는 고유 제약 순서용), `Optional<Battle> findById(long)`, `boolean existsActive(long memberId)`, `Set<Long> activeMemberIds(Collection<Long> memberIds)`(그중 활성 대결이 있는 회원, Matchmaker tick 일괄 조회), `List<Battle> findActive(long memberId)`(활성 대결, 많아야 1개), `List<Battle> findAbandoned(LocalDateTime cutoff)`(출발하지 못한 채 cutoff 전에 생성됐거나 출발 시각이 지난 대결, Battle.isAbandoned 조건), `List<Battle> findStartDue(LocalDateTime)`, `List<Battle> findCountdownDue(LocalDateTime)`, `List<Battle> findOpponentArrivalDue(LocalDateTime)`, `List<Battle> findPendingExpired(LocalDateTime)`, `List<Battle> findHistory(long memberId, DateRange)`, `List<BattleWin> findMemberWinsJudgedBetween(LocalDateTime from, LocalDateTime to)`(판정 순), `WeeklyRecord weeklyRecord(long memberId, Week)` (v0.11, CR34-01) |
| LiveSessionStore | `Optional<LiveBattleSession> load(long battleId)`, `void save(LiveBattleSession)`, `void delete(long battleId)`, `List<Long> activeBattleIds()` |
| VirtualRecordRepository | `List<VirtualRecord> findByTierAndDistance(Tier, StandardDistance)`, `long count()`, `void replaceAll(List<VirtualRecord>)`, `boolean existsByNickname(String)` |
| CelebrityRecordRepository | `List<CelebrityRecord> findAll(Optional<StandardDistance>)`, `Optional<CelebrityRecord> findById(long)`, `CelebrityRecord save(CelebrityRecord)`, `void delete(long)` |
| BattleMessenger | `void send(long memberId, BattleMessage m)`, `void sendAll(Collection<Long> memberIds, BattleMessage m)` — 구현 realtime.WebSocketMessenger, PollingMessenger |

battle의 Repository·Store 구현은 infra.battle에 있고 battle 밖에서 쓰지 않는다(ArchUnit 규칙, 13.1).

##### 보조 클래스 (v0.11, CR2-U7)
구현 중 생긴 협력 클래스를 한 줄씩 올린다. 의존 화살표는 12.2 안이다(battle 밖을 새로 부르지 않는다) (v0.11, CR2-U7)

| 클래스 | 종류 | 책임 · 연산 |
|--------|------|------------|
| AfterCommit | 유틸(final, 패키지 전용) | 되돌릴 수 없는 부수 효과(실시간 메시지·세션 정리)를 커밋 뒤로 미룬다. `static void run(Runnable effect)`: 트랜잭션 동기화가 없으면 바로 실행. 낙관적 잠금 재시도·롤백 때 메시지가 두 번 나가거나 남지 않게 한다(5.3). 같은 꼴의 사본이 crew에도 있다(위치 통합은 F.8 P-1, CR2-U5) (v0.11, CR2-U7) |
| BattleDelivery | 값(record) | recipientId(long), message(BattleMessage). 받는 회원이 정해진 대결 메시지 한 건. LiveBattleSession이 돌려주고 LiveBattleService가 BattleMessenger.send로 보낸다 (v0.11, CR2-U7) |
| BattleRandomConfig | 설정(@Configuration, 패키지 전용) | 고스트 후보·가상 기록 선택에 쓰는 `RandomGenerator`(SecureRandom) 빈을 만든다. 시험은 씨앗을 고정한 생성기를 넘긴다(5.1.1 "설정 클래스는 설정하는 패키지에") (v0.11, CR2-U7) |
| BattleTransactions | 응용 협력(@Component, 패키지 전용) | 대결 한 건 단위 트랜잭션(REQUIRES_NEW). `<T> T retrying(Supplier<T>)`·`void retryingRun(Runnable)`: 낙관적 잠금 충돌이면 다시 읽어 최대 3회(MAX_ATTEMPTS, 5.3 대결 도착·판정). `boolean once(String job, Runnable)`: 한 번 실행, 실패하면 로그 뒤 false(1초 시계·버려진 대결 정리의 한 건, 다음 차례에 다시) (v0.11, CR2-U7) |
| BattleWin | 값(record) | 위 BattleWin 절 (v0.11, CR2-U7) |
| OpponentNames | 유틸(final, 패키지 전용) | `static DisplayName of(Battle battle, long memberId, Map<Long, MemberSummary> names)`: 이력·결과 화면 공통 상대 표시명. 기록 대결은 OpponentRef.displayName(FR-172), 랜덤 대결은 상대 회원 표시명, 회원이 없으면 "탈퇴한 사용자"(FR-021, FR-093, FR-101) (v0.11, CR2-U7) | v0.1의 고스트 후보 조회 포트(run_record·member 테이블 조인)는 없앴다. 고스트 후보는 MemberQueryService.ghostEligibleOwners와 RunQueryService.validRunsReaching을 GhostMatcher가 조합해 구한다(design-review.md H-02, D-32).

