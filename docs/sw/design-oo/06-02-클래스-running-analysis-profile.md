> [런크크 객체지향 분석·설계서](../design-oo.md) 목차 · 이전: [06-01-클래스-common-member](06-01-클래스-common-member.md) · 다음: [06-03-클래스-battle](06-03-클래스-battle.md)

#### 6.1.3 running

##### RunRecord
| 항목 | 내용 |
|------|------|
| 패키지 | running |
| 책임 | 서버에 저장된 러닝 한 회의 측정 결과를 보관한다 |

속성:
| 이름 | 형 | 제약 |
|------|----|------|
| recordId | String | 기기 UUID, 기본 키, 불변 |
| memberId | long | 불변 |
| startedAt, endedAt | LocalDateTime | startedAt < endedAt |
| distance | Distance | > 0 |
| elapsed | Duration | > 0. 정지 시간을 뺀 운동 시간(FR-036) (v0.11, FR-036, D-93) |
| pauses | List<PauseInterval> | 자유런(mode == FREE)만 비어 있지 않을 수 있다. 칼럼 pauses TEXT("시작;끝" 줄, 기본 ''), V21 (v0.11, FR-194, FR-036) |
| pausedMillis | long | ≥ 0, 정지 구간 길이의 합. FREE가 아니면 0(ck_run_record_pause) (v0.11, FR-194) |
| savedAt | LocalDateTime | 서버 저장 일시(V13). markSaved로 한 번만 정한다. 보정 작업이 저장 일시로 기록을 고른다 (v0.11, CR34-03) |
| averagePace | Pace | |
| track | RunTrack | 합성 |
| heartRates | HeartRateSeries | 합성, nullable |
| mode | RunMode | |
| cardKind | CardKind | mode와 일관(FREE ↔ FREE_RUN, CREW_RUN ↔ CREW_RUN, BATTLE ↔ 나머지 셋) |
| battleId | Long | mode == BATTLE일 때만 |
| crewRunPostId | Long | mode == CREW_RUN일 때만 |
| valid | boolean | judgeValidity 뒤 확정 |
| excludedMeters, excludedMillis | long | ≥ 0. 유효 판정에서 뺀 초과 시속 구간의 거리(m)·시간(ms). 칼럼 excluded_meters·excluded_millis(V27, BIGINT NOT NULL DEFAULT 0, ck_run_record_excluded, 수정 불가). distance·elapsed·averagePace는 뺀 뒤 남은 집계 값이다 (v0.12 구현 반영, CR-11, D-190) |
| rejudgeState | RejudgeState? | 일회성 재판정 표시(V28 rejudge_state). 배포 때 무효였던 기록만 PENDING에서 시작하고 새 기록은 null. 엔티티 저장으로 바꾸지 않고 저장소의 조건부 갱신으로만 바꾼다 (v0.12 구현 반영, CR-11, D-191) |
| score | RunningScore | valid일 때만 |
| weather | Weather | nullable |
| newRecords | List<NewRecordMark> | 저장 때 확정 |

연산:
| 시그니처 | 설명 | 사전 조건 | 사후 조건 / 예외 |
|---------|------|----------|-----------------|
| `static RunRecord receive(long memberId, RunUpload upload)` | 수신 기록 생성 | 포인트 수 = 선언 수(NFR-010) | track.sortByTime(). 모드·ID 일관성 위반이면 IllegalArgumentException. upload.pauses를 그대로 옮기고 pausedMillis = 정지 구간 길이 합 (v0.11, FR-194) |
| `boolean judgeValidity(ValidityRule rule)` | 유효 판정 | 처음 한 번만 | 판정 규칙 judge(rule)(A-03): `excluded = track.overspeedSegments(pauses, rule)`. 뺀 구간이 있으면 집계 거리 = min(보낸 거리, `track.countedLength(excluded.withPauses(pauses))`), 집계 시간 = 보낸 운동 시간 − excluded.elapsed. 남은 거리·시간이 0 이하면 무효이고 저장 값은 보낸 값 그대로다. 아니면 distance·elapsed·averagePace를 남은 값으로 바꾸고 `rule.isSatisfiedBy(distance, elapsed, averagePace)` 그리고 `rule.isBackedBy(distance, track.pathLength(pauses) − excluded.distance, 보낸 운동 시간, Duration.between(startedAt, endedAt).minus(pausedTotal()))`. excludedMeters·excludedMillis를 남긴다. 운동 시간·평균 페이스는 정지를 뺀 값이다. valid, score 확정. 두 번째 호출은 IllegalStateException(FR-043 재계산 금지). 판정은 저장 때 한 번이고 경로 파기 뒤 다시 하지 않는다. 예외는 rejudgeInvalid 한 경로다 (v0.11, FR-047, CR-10, CR34-08, D-94) (v0.12 구현 반영, CR-11, D-190) |
| `boolean rejudgeInvalid(ValidityRule rule)`(패키지 전용) | 일회성 재판정 | 이미 판정했고 무효이며 경로가 남은 기록 | 아니면 IllegalStateException. 저장된 거리·시간을 측정값으로 보고 제외 구간 캐시를 비운 뒤 judge(rule)를 다시 적용한다. running.RunRejudgeService만 부른다(FR-043 예외 1건) (v0.12 구현 반영, CR-11, D-191) |
| `Distance getExcludedDistance()`, `Duration getExcludedElapsed()` | 뺀 거리·시간 | | 뺀 구간이 없으면 0 (v0.12 구현 반영, D-190) |
| `List<PauseInterval> stops()`(패키지 전용) | 정지 구간 + 제외 구간 | | pauses와 ValidityRule.STANDARD로 구한 초과 쌍을 시간순으로 합친 목록. 처음 쓸 때 계산해 둔다. timeToReach·RunQueryService.curve가 둘을 같이 건너뛴다. 옛 규칙으로 유효였던 기록은 초과 쌍이 없어 pauses와 같다. 판정(judge)과 같은 규칙으로 구한 같은 구간이어야 한다: 지금은 둘 다 STANDARD라 같고, 규칙을 매개변수로 바꾸면 judge가 구한 ExcludedSegments를 그대로 남긴다(BE7-3, D-190 (7)) (v0.12 구현 반영, D-190, U7-1) |
| `List<PauseInterval> pauses()` | 정지 구간 | | 읽기 전용 목록, 시간순 (v0.11, FR-036, FR-194) |
| `Duration pausedTotal()` | 정지 시간 합 | | pausedMillis. 정지가 없으면 0 (v0.11, FR-036, D-94) |
| `void markSaved(LocalDateTime at)` | 저장 일시 기록 | 저장 직전에 한 번 | savedAt = at. 두 번째 호출은 IllegalStateException. RunUploadService가 부른다 (v0.11, CR34-03) |
| `void attachWeather(Weather w)` | 날씨 붙이기 | | null이면 무시 |
| `void appendHeartRates(List<HeartRateSample> samples)` | 늦은 심박 덧붙이기 | 심박 동의 있음 | heartRates에 추가. valid·score 불변 |
| `void markNewRecords(List<NewRecordMark> marks)` | 신기록 표시 | valid | 무효면 무시(FR-186) |
| `void purgeRoute()` | 경로 파기 | | track.purge(). 거리·시간·페이스는 남긴다(FR-026) |
| `void purgeHeartRates()` | 심박 파기 | | 원자료 삭제, 요약값(평균·최고) 유지(FR-029) |
| `Optional<Duration> timeToReach(Distance d)` | d에 처음 닿은 경과 시간 | | track.timeToReach. 파기된 경로면 Optional.empty (저장 때 계산해 둔 최고 기록은 PersonalRecordBook에 남아 있다). track.timeToReach(d, pauses())로 정지 시간을 뺀다(D-163) (v0.11, test.md v0.6 TF-BE-33). v0.12: track.timeToReach(d, stops())로 제외 구간(초과 시속 쌍)도 정지처럼 거리·시간에서 뺀다 (v0.12 구현 반영, D-190) |
| `List<Pace> splitPaces()` | 1km 구간 페이스 | | |
| `Optional<Pace> fastestKm()` | 가장 빠른 1km | | splitPaces 최소 |
| `Map<HeartRateZone, Duration> heartRateZones(int maxHeartRate)` | 심박 구간 분포 | | FR-185 |
| `boolean reached(Distance d)` | 거리 도달 여부 | | 대결 검증(FR-092) |
| 조회 | getRecordId, getMemberId, getStartedAt, getDistance, getElapsed, getAveragePace, isValid, getScore, getMode, getCardKind, getWeather, getNewRecords, getSavedAt (v0.11, CR34-03), routePoints()(읽기 전용, 파기 뒤 빈 목록) | | |

표 밖 소비자 (v0.40 반영, PM 지시로 대신 반영(2026-11-15)): run_record의 `record_id`, `member_id`, `started_at`, `distance_meters`, `valid` 다섯 열은 Grafana 이벤트 현황 대시보드가 읽기 전용 계정(`runkk_grafana_ro`, 열 단위 GRANT)으로 서버를 거치지 않고 직접 읽는다(design-oo 05-29 이벤트 현황 D-860~D-867, 9절). 유효 판정(`ValidityRule`, FR-047)이나 이 열의 이름·뜻을 바꾸는 변경은 `infra/monitoring/grafana/dashboards/runkk-event.json`의 공통 SQL과 `infra/monitoring/grafana/runkk-db/readonly-role.sql`의 run_record 열 목록을 같은 MR에서 함께 고친다(13.1 「표 밖 소비자」).

##### PauseInterval (값, record) (v0.11, FR-194, FR-036, D-93)
| 항목 | 내용 |
|------|------|
| 패키지 | running |
| 책임 | 자유런의 정지 구간 하나를 표현한다 |

속성: from(LocalDateTime), to(LocalDateTime, from < to). 자료사전 정지구간(정지시작일시 + 정지종료일시)과 1:1. JSON은 `pauses[{from, to}]`(OffsetDateTime, D-84).

| 시그니처 | 설명 | 사후 조건 / 예외 |
|---------|------|-----------------|
| `Duration duration()` | 정지 길이 | to − from |
| `boolean straddles(LocalDateTime a, LocalDateTime b)` | 구간 (a, b)가 이 정지에 걸치는가 | a < to이고 b > from이면 true(경계 비포함: 정지 시작·종료 시각과 같은 포인트는 정상 구간, D-163 (v0.11, test.md v0.6 TF-BE-33)). RunTrack이 정지 경계에 걸친 이웃 포인트 쌍을 뺄 때 쓴다 |

##### RunUpload (입력) (v0.11, FR-194, FR-036)
v0.11에서 pauses(List<PauseInterval>, null·빈 목록 = 정지 없음)를 더한다. 생성자 검사: mode = FREE일 때만 비어 있지 않을 수 있다, from < to, startedAt ≤ from, to ≤ endedAt, 시간순·겹침 없음, 최대 100개(제안). 위반은 IllegalArgumentException(→ 400 BAD_REQUEST). elapsed(elapsedMillis)는 정지 시간을 뺀 운동 시간이다 (v0.11, FR-036, FR-194, F.2.2). 맞닿거나 겹치는 구간(앞 구간 to ≥ 뒤 구간 from)도 IllegalArgumentException(400)이다(D-163) (v0.11, test.md v0.6 TF-BE-29)

##### ExcludedSegments (값, record) (v0.12 구현 반영, CR-11, D-190)
유효 판정에서 뺀 구간이다. 속성: intervals(List<PauseInterval>, 뺀 쌍마다 [앞 포인트 시각, 뒤 포인트 시각], 시간순), distance(Distance, 뺀 거리 합), elapsed(Duration, 뺀 시간 합). 상수 `NONE`(빈 목록, 0, 0). `boolean isEmpty()`, `List<PauseInterval> withPauses(List<PauseInterval> pauses)`(정지 구간과 합쳐 시간순 정렬, 비었으면 pauses 그대로). 정지 구간과 같은 형식이라 RunTrack의 거리·도달 시간·곡선 계산이 같은 규칙(PauseInterval.straddles)으로 건너뛴다. 불안정 구간 끝 쌍과 정지 경계 쌍은 처음부터 거리에 들지 않으므로 넣지 않는다.

##### RejudgeState (열거형) (v0.12 구현 반영, CR-11, D-191)
PENDING(V28이 배포 때 무효 기록에 세움), VALID(재판정으로 유효가 되어 부수 처리 대기), DONE(끝), SKIPPED(경로가 없어 판정 불가). 상태도는 2.4.22.

##### RunTrack
속성: points(List<LocationPoint>), unstableSegments(List<UnstableSegment>), purged(boolean).

| 시그니처 | 설명 | 사후 조건 / 예외 |
|---------|------|-----------------|
| `void sortByTime()` | 측정 일시 정렬 | 같은 일시 중복 포인트는 하나만 남긴다 |
| `double maxSegmentSpeedKmh(List<PauseInterval> pauses)` | 이웃 포인트 사이 최고 시속 | 불안정 구간을 지나는 구간과 정지 구간에 걸친(PauseInterval.straddles) 이웃 포인트 쌍은 뺀다. 인자 없는 `maxSegmentSpeedKmh()`는 빈 목록으로 위임한다 (v0.11, FR-047, D-94). v0.12부터 유효 판정은 이 값으로 기록 전체를 무효로 하지 않는다(overspeedSegments가 넘는 쌍만 뺀다) (v0.12 구현 반영, CR-11, D-190) |
| `ExcludedSegments overspeedSegments(List<PauseInterval> pauses, ValidityRule rule)` | 초과 시속 쌍(제외 구간) | maxSegmentSpeedKmh와 같은 쌍을 본다(불안정 구간 끝 쌍·정지 경계 쌍·시각 차 0인 쌍 제외). `rule.isOverspeed(시속)`인 쌍을 구간으로 모으고 거리 합(반올림 m)·시간 합을 낸다. 없으면 ExcludedSegments.NONE (v0.12 구현 반영, CR-11, D-190) |
| `Distance countedLength(List<PauseInterval> stops)` | 집계 거리를 경로로 다시 셈 | 불안정 구간 끝 쌍과 stops(정지·제외 구간)에 걸친 쌍을 뺀 이웃 포인트 거리의 합. 앱 RunSession이 달리는 중에 세는 거리와 같은 쌍을 더한다 (v0.12 구현 반영, D-190) |
| `Distance pathLength(List<PauseInterval> pauses)` | 경로 길이 | 이웃 포인트 사이 거리 합. 정지 구간에 걸친 쌍은 뺀다. 유효 판정 다섯째 조건(isBackedBy)의 경로 길이다. 인자 없는 `pathLength()`는 빈 목록으로 위임한다 (v0.11, CR34-08, D-94) |
| `Optional<Duration> timeToReach(Distance d, List<PauseInterval> pauses)` | 누적 거리 d 도달 시간 | 두 포인트 사이는 직선 보간. 정지 구간을 가로지르는 쌍은 거리를 더하지 않고, 도달 시각까지의 정지 시간을 뺀다(D-122, D-163) (v0.11, test.md v0.6 TF-BE-33) |
| `Optional<Duration> timeToArrive(Distance d, List<PauseInterval> pauses)` | 대결·크루런 도착 판정 | 부족한 거리가 0.5m 이하면 마지막으로 거리가 늘어난 포인트의 운동 시각으로 인정한다. 신기록·구간 계산은 엄격한 `timeToReach`를 쓴다(D-559, CR-151). |
| `List<CurvePoint> curve(Duration step, Duration end, List<PauseInterval> pauses)`(패키지 전용, 정지 없는 `curve(step, end)`는 빈 목록을 넘긴다) | 운동 시간 축의 (경과, 누적 거리) 표본 | 정지 구간을 잘라 내고 이후 포인트의 경과 시간을 정지 합만큼 당긴다. 정지에 걸친 쌍은 거리를 더하지 않는다. 정지가 없으면 벽시계 축과 같다 (v0.11 구현 반영, I-1, D-168) |
| `Distance cumulativeDistanceAt(Duration t)` | t 시점 누적 거리 | 고스트 상대 트랙 생성용 |
| `Optional<GeoPoint> firstPoint()` | 첫 포인트 | 파기 뒤 비어 있음 |
| `void purge()` | 포인트 삭제 | purged = true. 불안정 구간 시각·거리는 남긴다 |
| `boolean hasUnstableWithin(Distance from, Distance to)` | 구간에 불안정 구간이 있는가 | FR-063 표시 |

##### HeartRateSeries
속성: samples(List<HeartRateSample>), purged(boolean), summary(HeartRateSummary: avg, max — 파기 뒤에도 유지).

| 시그니처 | 설명 |
|---------|------|
| `void append(List<HeartRateSample> s)` | 덧붙이고 요약 갱신 |
| `HeartRateSummary summary()` | 평균·최고 |
| `Map<HeartRateZone, Duration> zoneDistribution(int maxHr)` | 5구간 시간(50~60/60~70/70~80/80~90/90%+). 50% 미만은 세지 않는다 |
| `void purge()` | 원자료 삭제, 요약 유지 |

##### ValidityRule (값, record)
속성: minDistance(1km), minElapsed(3분), fastestPace(3:00), slowestPace(15:00), maxSpeedKmh(25, v0.12부터 이웃 포인트 사이 시속 상한이고 넘는 쌍은 거리·시간에서 뺀다, D-190). 상수 `STANDARD`(결정 Q-22 고정값), `MAX_DECLARED_TO_PATH_RATIO = 2.0`, `WALL_CLOCK_TOLERANCE = 1초` (v0.11, CR34-08, D-94).

| 시그니처 | 설명 |
|---------|------|
| `boolean isSatisfiedBy(Distance d, Duration t, Pace avg)` | 세 조건 모두(거리 ≥ 1km, 시간 ≥ 3분, 페이스 3:00~15:00). d·t·avg는 초과 구간을 뺀 뒤 남은 값이다. 최고 시속 매개변수는 지웠다 (v0.12 구현 반영, CR-11, D-190) |
| `boolean isOverspeed(double speedKmh)` | 이웃 포인트 사이 시속이 상한을 넘는가 | speedKmh > maxSpeedKmh. 상한과 같으면 넘지 않는다. 앱 OverspeedRule.MAX_SEGMENT_SPEED_KMH와 같은 값이고 계약 파일 contract/policy/ValidityRule.txt(`maxSpeedKmh 25.0`)로 대조한다 (v0.12 구현 반영, D-190) |
| `boolean isBackedBy(Distance declared, Distance path, Duration elapsed, Duration wall)` | 다섯째 조건: 선언 거리 ≤ 경로 길이 × MAX_DECLARED_TO_PATH_RATIO 그리고 선언 운동 시간 ≤ wall + WALL_CLOCK_TOLERANCE. RunRecord.judgeValidity가 wall = endedAt − startedAt − pausedTotal로 부른다. 포인트 2개에 42km를 선언한 기록 같은 조작을 무효로 만든다 (v0.11, FR-047, CR-10, CR34-08, D-94). v0.12: declared는 남은 거리, path는 초과 구간을 뺀 경로 길이라 순간이동 한 쌍이 위조한 선언 거리를 뒷받침하지 못한다 (v0.12 구현 반영, D-190) |

##### RunningScore (값, record)
속성: value(double, VDOT).

| 시그니처 | 설명 | 사후 조건 / 예외 |
|---------|------|-----------------|
| `static RunningScore of(Distance d, Duration t)` | Daniels–Gilbert VDOT(6.2) | d ≥ 1km, t ≥ 3분 |
| `static RunningScore average(List<RunningScore> scores)` | 평균 | 빈 목록이면 IllegalArgumentException |
| `Pace paceFor(Distance d)` | 이 점수로 d를 달리는 평균 페이스 | 이분 탐색(6.2). 가상 기록 생성용 |

##### PersonalRecordBook
| 항목 | 내용 |
|------|------|
| 책임 | 회원의 거리별 최고 기록을 보관한다 |

속성: memberId(long), bests(EnumMap<StandardDistance, BestEntry>, 기준 거리 5개), longest(BestEntry, nullable). BestEntry(record: recordId, time 또는 distance, achievedOn, hasUnstable).

| 시그니처 | 설명 | 사후 조건 / 예외 |
|---------|------|-----------------|
| `List<NewRecordMark> apply(RunRecord r)` | 새 기록 반영 | 유효 러닝만. 기준 거리마다 r.timeToReach가 기존보다 짧으면 갱신하고 NewRecordMark(거리, 이전 값). 거리가 기존 최장보다 길면 갱신(FR-063, FR-076, FR-186) |
| `Optional<BestEntry> best(StandardDistance d)` | 거리별 최고 | d.isRecordBase() |
| `Optional<BestEntry> longest()` | 최장 | |

##### NewRecordMark (값, record)
속성: kind(LONGEST / BASE_DISTANCE), distance(StandardDistance, nullable), previous(String 표기, "직전 n km" 또는 이전 시간).

##### Weather (값, record)
속성: temperatureC(int), sky(SkyCondition: CLEAR, CLOUDY, OVERCAST, RAIN, SNOW).

##### DisposalOrder
속성: id, memberId, kind(DisposalKind), dueAt(철회 + 7일), status(DisposalStatus), executedAt, deviceConfirmedAt.

| 시그니처 | 설명 | 사후 조건 / 예외 |
|---------|------|-----------------|
| `boolean isDue(LocalDateTime now)` | 실행 대상 | SCHEDULED이면 true (기한 전이라도 매일 실행해 7일 안에 끝낸다) |
| `void execute(LocalDateTime now)` | 서버 파기 완료 표시 | SCHEDULED만. 기기 확인이 먼저 있었으면 DEVICE_CONFIRMED, 아니면 EXECUTED |
| `void confirmDevice(LocalDateTime now)` | 기기 파기 확인 | EXECUTED면 DEVICE_CONFIRMED. SCHEDULED면 deviceConfirmedAt만 기록 |
| `boolean isOverdue(LocalDateTime now)` | 7일 넘음 | 운영 로그 경보(NFR-036) |

##### RunUploadService
| 항목 | 내용 |
|------|------|
| 의존 | RunRecordRepository, PersonalRecordBookRepository, WeatherGateway, MemberQueryService(member: hasConsent, 기존 running → member 화살표 안, v0.8), DomainEventPublisher, Clock |

| 시그니처 | 설명 |
|---------|------|
| `UploadResult upload(long memberId, RunUploadCommand command)` | SQ-02, A-04. 같은 recordId면 alreadyStored. 심박이 있는데 심박 동의(MemberQueryService.hasConsent(HEART_RATE))가 없으면 심박을 버린다 → 수신 → 유효 판정 → 날씨(경로 있을 때, coarsen(2), 트랜잭션 밖) → 트랜잭션: PersonalRecordBook 행 잠금 → validRunOrdinal = 저장된 유효 기록 수 + 1(무효면 0) → 최고 기록 갱신 → markSaved(now) → 저장 → RunRecordSaved(validRunOrdinal 포함) 발행 (v0.8, TF-IT-06). 정지 구간(pauses)은 RunUpload 생성자가 검사하고 판정은 다섯째 조건을 포함한다(UploadResult.valid). 자동 종료(FR-195)된 기록도 같은 경로다 (v0.11, FR-036, FR-047, FR-194, FR-195, CR34-03, D-94) |
| `void appendLateHeartRates(long memberId, LateHeartRateCommand command)` | 기록 주인 확인(남의 기록 PermissionDeniedException, 없는 기록 RECORD_NOT_FOUND) → 심박 동의가 없거나 원자료를 파기한 기록이면 버린다 → RunRecord.appendHeartRates. 기간 제한이 없다(FR-043) (v0.8, CR-U26) |

##### DisposalService
| 항목 | 내용 |
|------|------|
| 의존 | DisposalOrderRepository, RunRecordRepository, AuditService, Clock |

| 시그니처 | 설명 |
|---------|------|
| `void onConsentWithdrawn(ConsentWithdrawn e)` | DisposalOrder(ROUTE 또는 HEART_RATE, now + 7일) 생성 |
| `int executeDue(LocalDateTime now)` | SCHEDULED 지시마다 그 회원 기록 전부 purgeRoute / purgeHeartRates → execute → 감사(위치경로파기). 처리 수 반환 |
| `List<DisposalOrderView> pendingFor(long memberId)` | 기기가 아직 확인하지 않은 지시 |
| `void confirmDevice(long memberId, long orderId)` | 기기 파기 확인 |

##### RunQueryService
| 항목 | 내용 |
|------|------|
| 패키지 | running (공개 조회 서비스) |
| 책임 | 다른 패키지가 읽을 러닝 기록 투영·통계를 내준다 |
| 의존 | RunRecordRepository, PersonalRecordBookRepository |

다른 패키지(analysis, battle, crew, runimal, avatar, profile, reward (v0.11, CR34-04))는 RunRecordRepository·PersonalRecordBookRepository를 쓰지 않고 이 서비스만 부른다(D-32). 돌려주는 값에는 RunRecord 엔티티가 없고 투영(RunSummary, RunCurve, RunDetail, RunSampleRow, RunComparison, 통계 값)만 있다. 받는 패키지가 절반 미만 항목만 쓰는 곳에는 좁은 연산을 둔다(D-41).

| 시그니처 | 설명 |
|---------|------|
| `RunSummary summary(String recordId)` | 기록 요약. 없으면 BusinessRuleException(RECORD_NOT_FOUND). crew의 크루런 도착·결과, analysis의 카드 값용. battle은 쓰지 않는다(D-51) |
| `Optional<RunSummary> findSummary(String recordId)` | 요약이 없어도 정상인 호출에서 쓴다. 배치고사 판 읽기와 셈 수신자가 유효 여부를 조회한다(D-524). |
| `List<RunSummary> recentSummaries(long memberId, int limit)` | 최근 유효 기록 limit개, 최신순. analysis.ReportService(20)가 쓴다 |
| `List<RunSampleRow> runSamples(long memberId, int limit)`, `List<RunSampleRow> runSamples(long memberId, DateRange r)` | 최근 유효 기록 limit개 또는 기간 안 유효 기록, 최신순. RunSampleRow(recordId, startedAt, distance). runimal의 판정 표본·코스 비교 대상용(design-review.md R4) |
| `List<LocalDateTime> recentStartTimes(long memberId, int limit)` | 최근 유효 기록 limit개의 시작 일시, 최신순. avatar.AppearanceService의 피부색·마지막 유효 러닝 일시용(R6) |
| `List<RunSummary> summariesBetween(long memberId, DateRange r)` | 기간 안 기록 전부(무효 포함), 최신순. 기록 목록·주간 거리 재합산용 |
| `Map<String, RunSummary> summariesOf(Collection<String> recordIds)` | 여러 기록의 요약을 한 번에 돌려준다(기록 ID → 요약). 없는 ID는 맵에서 빠지고, 빈 입력이면 저장소를 부르지 않고 빈 맵이다. `RunRecordRepository.findSummaries`를 한 번 불러 기록·참가자마다 `findSummary`를 부르던 N+1을 없앤다. crew.CrewRunHistory(크루 상세 최근 크루런 거리 합, 시스템 메시지 거리)가 쓴다(06-04 v0.40 의존 표, 코드 리뷰 PL11-B3) (v0.40 반영, PM 지시로 대신 반영(2026-11-15)) |
| `RunDetail detail(String recordId, OptionalInt maxHeartRate)` | 1km 구간 페이스, 가장 빠른 1km, 심박 구간(최대 심박이 있을 때), 불안정 구간, 날씨, 신기록. analysis의 기록 상세용 |
| `RunComparison comparisonOf(String recordId)` | RunComparison(elapsed, averagePace, fastestKm). battle의 결과 비교표용(R5). 없으면 BusinessRuleException(RECORD_NOT_FOUND) |
| `Optional<RunComparison> findComparison(String recordId)` | 기록을 못 읽은 상태도 허용하는 대결 결과 비교표에 쓴다(D-524). |
| `Optional<RunListRow> latestListRow(long memberId)` | 저장소의 같은 연산으로 시작 시각이 가장 늦은 기록 한 건을 읽는다. 유효·무효를 모두 대상으로 하며 기록이 없으면 비어 있다(D-491·D-523·D-524). |
| `Map<LocalDate, DayStats> dailyStats(long memberId, DateRange r)` | 날짜별 거리·시간·횟수(유효만). 대시보드 오늘 요약·잔디·스트릭·달력 |
| `Map<LocalDate, Distance> dailyDistances(Collection<Long> memberIds, DateRange r)` | 여러 회원의 날짜별 유효 거리 합. 크루 스트릭·잔디(FR-054, FR-055) |
| `TotalStats totals(long memberId)` | 누적 유효 거리·시간·횟수. 무효(집계 제외) 기록은 세지 않는다(홈 누적, FR-050) (v0.12 구현 반영, CR-12, D-192) |
| `List<PeriodStats> periodTotals(long memberId, AggregationPeriod p, int count)` | 최근 count개 기간의 거리·횟수·평균 페이스 |
| `Map<Long, Distance> distancesByMember(Collection<Long> memberIds, DateRange r)` | 회원별 유효 거리 합(기록이 없으면 0). 랭킹, 크루 랜드마크(전체 기간), 주간 거리 재합산, 계획 미이행 확인이 쓴다 |
| `List<SavedRun> savedBetween(LocalDateTime from, LocalDateTime to)` | 저장 일시가 [from, to)인 기록 전부(무효 포함), 저장 순. 커밋 뒤 수신자의 일 단위 보정이 빠진 처리를 찾을 때 쓴다: reward.RewardReconcileService, crew.CrewRunLinkReconcileService, running.TierReconcileService, avatar.BackgroundService.reconcile (v0.11, CR34-03, D-107, D-108) |
| `Distance validDistanceOf(long memberId)` | 누적 유효 거리 하나. avatar.BackgroundService의 배경 해금 판정용(TotalStats 4항목 중 1개만 쓰던 것을 좁혔다, D-41) (v0.11 검증, D-139). v0.12: TotalStats가 유효만 세므로 totals.distance와 같다 (v0.12 구현 반영, D-192) |
| `Distance validDistanceBefore(long memberId, LocalDateTime cutoff)` | 저장 일시 < cutoff인 유효 기록의 누적 거리. avatar.BackgroundService.backfillReached가 배포 전에 넘은 랜드마크만 알림 없이 채울 때 쓴다(같은 avatar → running 화살표 D18) (v0.11 구현 반영, D-176) |
| `Set<Long> memberIdsSavedBetween(LocalDateTime from, LocalDateTime to)` | 저장 일시가 [from, to)인 기록이 있는 회원 ID. avatar.BackgroundService.reconcile용(SavedRun 7항목 중 1개만 쓰던 것을 좁혔다) (v0.11 검증, D-139) |
| ~~`List<ValidRunScore> validRunsAfter(long memberId, int ordinal)`~~ | 삭제. 티어 보정이 순번 비교 대신 기록별 반영 표시(tier_applied)로 대상을 고르게 되어(D-169) 운영 코드가 부르지 않는다. 코드 정리는 BE5-A-9가 따른다 (v0.11 구현 반영, U5-A-1, D-169) |
| `List<OwnedRun> validRunsReaching(StandardDistance d, Collection<Long> ownerIds)` | ownerIds 회원의 유효 기록 중 거리 ≥ d이고 경로가 남은 것. OwnedRun(recordId, memberId)만 돌려준다. battle.GhostMatcher가 쓴다(A-07, D-51) |
| `RunCurve curve(String recordId, Distance cut)` | 1초 간격 (경과 시간, 누적 거리) 표본을 cut 거리까지. 좌표가 없다(FR-089). 경로가 파기되었으면 BusinessRuleException(ROUTE_PURGED). 경과 시간은 운동 시간이다: 기록의 pauses를 넘겨 RunTrack.curve(step, end, pauses)로 정지를 잘라 내고 end는 timeToReach(cut, pauses)다. 고스트·나와의 싸움 상대 진행도가 정지 없는 러닝처럼 보인다 (v0.11 구현 반영, I-1, D-168) |
| `Optional<Duration> timeToReach(String recordId, Distance d)` | 크루런 도착 시각과 대결 검증용. `RunRecord.timeToArrive(d)`가 `stops()`를 넘겨 `RunTrack.timeToArrive`의 0.5m 허용 규칙을 적용한다(D-559, CR-151). |

기록이 없을 수 있는 호출에서는 Optional 조회를 쓴다. 참여 트랜잭션에서 예외를 잡는 방식은 커밋 때 롤백을 일으킬 수 있다(D-524).
| `List<GeoPoint> routePoints(String recordId)` | 경로 좌표(파기 뒤 빈 목록). runimal 코스 동일성, 기록 상세 지도, 카드 뒷면 |
| `Optional<BestEntry> best(long memberId, StandardDistance d)` | 거리별 최고 기록 |
| `Optional<BestEntry> longest(long memberId)` | 최장 거리 기록. analysis의 성장 분석용 |
| `Optional<Distance> longestDistance(long memberId)` | 최장 거리 값만. profile용(R7) |

##### RunSummary, RunDetail, RunCurve, RunSampleRow, OwnedRun, RunComparison (값, record)
RunSummary: recordId(String), memberId(long), startedAt(LocalDateTime), distance(Distance), elapsed(Duration), averagePace(Pace), valid(boolean), score(Double, 무효면 null), mode(RunMode), cardKind(CardKind, 카드 앞면 라벨. 모드만으로 정해지지 않는다, 1.5.3), battleId(Long), crewRunPostId(Long). RunSampleRow: recordId(String), startedAt(LocalDateTime), distance(Distance). OwnedRun: recordId(String), memberId(long). RunComparison: elapsed(Duration), averagePace(Pace), fastestKm(Pace, nullable). RunDetail: summary(RunSummary), splitPaces(List<Pace>), fastestKm(Pace, nullable), heartRateZones(Map<HeartRateZone, Duration>), unstableSegments(List<UnstableSegment>), weather(Weather, nullable), newRecords(List<NewRecordMark>), pauses(List<PauseInterval>, 시간순, 정지 없으면 빈 목록) (v0.11, FR-036). RunCurve: samples(List<CurvePoint(elapsed, distance)>), cut(Distance). battle은 RunCurve를 OpponentTrack으로 바꿔 쓴다. 좌표 필드가 없어 투영만으로 위치가 새지 않는다(NFR-021).

DayStats(date, distance, elapsed, count), TotalStats(distance, elapsed, count)(v0.12: 유효 기록만 센다. validDistance 항목은 지웠다, D-192), PeriodStats(from, to, distance, count, averagePace)는 통계 값이다.

##### SavedRun (값, record) (v0.11, CR34-03, CR2-U7)
| 항목 | 내용 |
|------|------|
| 패키지 | running |
| 책임 | 저장 일시로 고른 기록 한 건을 보정 작업에 넘긴다 |

속성: recordId(String), memberId(long), startedAt(LocalDateTime), distance(Distance), valid(boolean), crewRunPostId(Long, 없으면 null), savedAt(LocalDateTime, 서버 저장 일시). 좌표가 없다(NFR-021). `static SavedRun of(RunRecordSaved e)`: 커밋 뒤 수신자가 받은 사건을 보정 작업과 같은 형식으로 바꾼다(savedAt = 사건 발생 시각). reward 보정은 savedAt 날짜로 원래 사건일의 하루 상한을 세고, crew 크루런 연결 보정은 crewRunPostId·startedAt·distance로 수신자와 같은 연결을 다시 한다(crew.CrewRunService.link(post, SavedRun)). crew → running 화살표에 SavedRun이 실린다(12.2, CR34-04).

##### ValidRunScore (값, record) (v0.11, D-108)
속성: ordinal(int, 유효 러닝 순번 ≥ 1), score(double), distance(Distance). RunRecordRepository.validRunScoreOf(recordId)의 결과이고 TierReconcileService가 RunScoreInput(score, distance, ordinal)으로 바꿔 TierApplier에 넘긴다. 순번은 그 회원의 유효 기록을 저장 일시·기록 ID 순으로 센 차례(1부터)다 (v0.11 구현 반영, U5-A-1, D-169).

##### RunRecordSaved (사건, record)
running이 발행한다(D-31). 항목은 6.1.1 사건 표와 같다. validRunOrdinal은 이 기록을 포함한 그 회원의 유효 러닝 순번이고 무효 기록이면 0이다.

##### TierUpdateListener
| 항목 | 내용 |
|------|------|
| 패키지 | running |
| 책임 | 유효 러닝 저장 사건을 member의 티어 갱신 호출로 넘긴다 |
| 의존 | member.TierLevelService |

`void onRunSaved(RunRecordSaved e)`: `@TransactionalEventListener(AFTER_COMMIT)` 동기. e.valid이고 순번 ≥ 1일 때만 새 트랜잭션(REQUIRES_NEW)에서 TierApplier.apply(e.memberId, e.recordId, new RunScoreInput(e.score, e.distance, e.validRunOrdinal))를 부른다(반영과 그 기록의 tier_applied 표시가 한 트랜잭션). 같은 회원 행을 함께 고쳐 생긴 일시 오류(TransientDataAccessException)면 3번까지 다시 하고, 그래도 실패하면 운영 로그(tier_update_failed)를 남긴다. 표시가 서지 않은 기록은 TierReconcileService가 다음 0시 10분에 채운다(재시도 큐를 두지 않는다, M4 결정과 같은 꼴) (v0.11, D-108) (v0.11 구현 반영, U5-A-1, D-169). 다음 유효 기록의 순번이 건너뛰어 오면 TierProgress가 경고를 남기고, 그 사이에 판정 지점이 있으면 판정한다(D-40). 이 수신자는 D-03의 예외(running이 아는 후속 처리는 티어 하나)다.

##### TierReconcileService (v0.11, D-108)
| 항목 | 내용 |
|------|------|
| 패키지 | running |
| 책임 | 티어에 빠진 유효 러닝 반영을 매일 보정한다 |
| 의존 | RunRecordRepository(tierPendingBetween, validRunScoreOf), TierApplier, PlatformTransactionManager — member 호출은 TierApplier를 거치며 기존 running → member 화살표(12.2 D3) 안 (v0.11 구현 반영, U5-A-1, D-169) |

| 시그니처 | 설명 |
|---------|------|
| `int reconcile(LocalDateTime now)` | batch.running의 TierReconcileJob(cron `runkk.batch.tier-reconcile-cron`, 기본 `0 10 0 * * *`)이 부른다. RunRecordRepository.tierPendingBetween(오늘 − 2일 0시, now)로 반영 표시가 없는(tier_applied 거짓) 유효 기록을 회원별로 묶고, 회원마다 기록을 저장 순서대로 validRunScoreOf(recordId) → TierApplier.apply(memberId, recordId, RunScoreInput(score, distance, ordinal))에 넘긴다. 표시가 선 기록은 다시 고르지 않으므로 여러 번 돌려도 같다(멱등) (v0.11 구현 반영, U5-A-1, D-169). 회원마다 새 트랜잭션(REQUIRES_NEW)이고 실패는 로그 뒤 다음 회원. 보정한(빠진 순번을 하나 이상 반영한) 회원 수를 돌려준다(A-27과 같다) (v0.11, test.md v0.6 TF-BE-40) |

##### TierApplier (v0.11 구현 반영, U5-A-1, D-169)
| 항목 | 내용 |
|------|------|
| 패키지 | running (패키지 전용 보조, public 아님) |
| 책임 | 유효 기록 하나를 티어에 반영하고 그 기록의 반영 표시를 세운다 |
| 의존 | member.TierLevelService, member.MemberQueryService(appliedValidRunCount), RunRecordRepository(markTierApplied) |

`void apply(long memberId, String recordId, RunScoreInput input)`: 부르는 쪽 트랜잭션 안에서 돈다. input.validRunOrdinal > MemberQueryService.appliedValidRunCount(memberId)이면 TierLevelService.recordValidRun(판정 지점이면 판정), 아니면 뒤 순번이 먼저 반영된 구멍이라 TierLevelService.recordLateValidRun(판정 없이 점수·거리만)으로 넘긴 뒤 RunRecordRepository.markTierApplied(recordId)로 표시를 세운다. 표시를 세우지 못하면(리스너와 보정이 겹쳐 이미 반영) IllegalStateException으로 그 트랜잭션의 티어 반영까지 되돌린다. 코드의 반환형 boolean(늘 참)은 void로 고친다(BE5-A-8). 한계: 판정 지점(3, 10의 배수)의 기록이 구멍으로 늦게 오면 늦은 반영은 평가를 다시 하지 않아 다음 판정 지점까지 티어가 늦게 바뀐다(D-169).

##### RunRejudgeService (v0.12 구현 반영, CR-11, D-191)
| 항목 | 내용 |
|------|------|
| 패키지 | running |
| 책임 | 배포 때 무효였던 기록을 새 유효 판정 규칙으로 한 번 다시 판정한다 |
| 의존 | RunRecordRepository, PersonalRecordBookRepository, TierApplier, MemberQueryService(member: isActive, 기존 running → member 화살표 D3 안), DomainEventPublisher, PlatformTransactionManager(기록마다 REQUIRES_NEW) |

`Result run()`: batch.running.RunRejudgeJob이 기동 뒤 한 번 부른다. 기동 스레드 밖에서 부르고 한 번에 기록 1,000개 또는 60초까지만 처리해 남은 것은 다음 기동이 잇는다(D-199, 설계 결정·구현 대기). 1단계 PENDING, 2단계 VALID를 BATCH(100)개씩 골라 기록마다 새 트랜잭션에서 처리하고 Result(nowValid, stillInvalid, skipped, applied, failed)를 돌려주며 처리한 것이 있으면 운영 로그 run_rejudged 한 줄을 남긴다. 알고리즘은 A-29. 한 기록의 실패는 로그(run_rejudge_failed)만 남기고 표시를 그대로 둬 다음 기동에 다시 한다. 이미 끝난 대결·크루런 판정, 지난 주 크루 레이스, Runimal, 리포트는 바꾸지 않는다. 랭킹은 다음 0시 집계부터 반영된다(BE7-5).

##### RunRecordRejudged (사건, record) (v0.12 구현 반영, CR-11, D-191)
recordId(String), memberId(long), distance(Distance, 초과 구간을 뺀 집계 거리), occurredAt(LocalDateTime, 원래 저장 일시). 재판정으로 유효가 된 기록에 저장 때 일어났어야 할 일 가운데 저장 시점에만 쌓이는 값(리워드 적립, 랜드마크 배경 해금)을 멱등하게 다시 맞추라는 뜻이다. 수신자: reward.RewardService.onRunRejudged, avatar.BackgroundUnlockListener.onRunRejudged(둘 다 AFTER_COMMIT, 알림 없음). 대결·크루런·Runimal·리포트·티어 리스너는 받지 않는다(티어는 RunRejudgeService가 TierApplier로 직접 반영).

##### RunWithdrawalListener
`void onMemberWithdrawn(MemberWithdrawn e)`: 그 회원의 RunRecord(경로·심박 포함), PersonalRecordBook, DisposalOrder를 지운다(FR-021 D2).

##### 포트 인터페이스 (running이 정의)

`RunRecordRepository.findSummary(String): Optional<RunSummary>`와 `latestListRow(long): Optional<RunListRow>`는 요약과 최신 목록 행을 직접 읽는 투영이다(D-524).
| 인터페이스 | 시그니처 |
|-----------|---------|
| RunRecordRepository | `boolean exists(String recordId)`, `Optional<RunRecord> findById(String)`, `List<RunSummary> findSummaries(Collection<String> recordIds)`(v0.40 반영, summariesOf용), `RunRecord save(RunRecord)`, `List<RunRecord> findByMember(long memberId)`, `List<RunRecord> recentValid(long memberId, int limit)`, `List<RunRecord> findByMemberBetween(long memberId, DateRange range)`, `int countValid(long memberId)`, `List<SavedRun> savedBetween(LocalDateTime from, LocalDateTime to)`(v0.11 CR34-03), `List<SavedRun> tierPendingBetween(LocalDateTime from, LocalDateTime to)`(저장 일시 [from, to)이고 tier_applied 거짓인 유효 기록, 저장 일시·기록 ID 순, 색인 idx_run_record_tier_pending), `Optional<ValidRunScore> validRunScoreOf(String recordId)`(유효 기록 하나의 점수·거리·순번, 무효거나 없으면 비어 있음), `boolean markTierApplied(String recordId)`(조건부 UPDATE, 이번에 세웠으면 참) (v0.11 구현 반영, U5-A-1, D-169), `Distance validDistanceSavedBefore(long memberId, LocalDateTime cutoff)`(저장 일시 < cutoff인 유효 기록 거리 합, RunQueryService.validDistanceBefore용, D-176) (v0.12, U6-5), 일회성 재판정 연산 넷 `List<String> rejudgeCandidates(RejudgeState state, int limit)`(저장 일시·기록 ID 순, 색인 idx_run_record_rejudge)·`Optional<RunRecord> findForUpdate(String recordId)`(행 잠금)·`boolean storeRejudgement(RunRecord record, RejudgeState from, RejudgeState to)`(거리·시간·페이스·유효·점수·제외 값과 표시를 조건부 UPDATE. 거리·시간 열은 updatable = false라 이 연산만 쓴다)·`boolean moveRejudgeState(String recordId, RejudgeState from, RejudgeState to)`(표시만 조건부 UPDATE) (v0.12 구현 반영, D-191), 통계 조회(RunQueryService용), `void deleteByMember(long memberId)`. running 밖에서 쓰지 않는다(ArchUnit 규칙, 13.1) (v0.11, CR34-03, D-108) |
| PersonalRecordBookRepository | `PersonalRecordBook findOrCreateForUpdate(long memberId)`(행 잠금), `PersonalRecordBook findOrCreate(long memberId)`, `void save(PersonalRecordBook)`, `void deleteByMember(long)`. running 밖에서 쓰지 않는다 |
| DisposalOrderRepository | `void save(DisposalOrder)`, `List<DisposalOrder> findScheduled()`, `List<DisposalOrder> findNotConfirmedByMember(long)`, `Optional<DisposalOrder> findById(long)` |
| WeatherGateway | `Optional<Weather> lookup(GeoPoint coarsePoint, LocalDateTime at)` (3초 제한, 실패면 비어 있음) |

#### 6.1.4 analysis

##### RunningGoal
| 항목 | 내용 |
|------|------|
| 패키지 | analysis |
| 책임 | 회원의 주간 목표와 날짜별 계획을 보관한다 |

속성: memberId(long), weeklyGoals(Map<Week, WeeklyGoal>), plans(SortedMap<LocalDate, DailyPlan>).

| 시그니처 | 설명 | 사전 조건 | 사후 조건 / 예외 |
|---------|------|----------|-----------------|
| `void setWeeklyGoal(WeeklyGoal goal)` | 주간 목표 설정 | | 같은 주면 바꾼다 |
| `void putPlan(DailyPlan plan, LocalDate today)` | 날짜별 계획 | plan.date ≥ today | 과거면 BusinessRuleException(PAST_DATE). 같은 날짜면 바꾼다(FR-073) |
| `Optional<DailyPlan> planOn(LocalDate d)` | 그날 계획 | | |
| `Optional<WeeklyGoal> goalOf(Week w)` | 그 주 목표 | | |
| `Optional<PlanCheck> checkOn(LocalDate d, DayActual actual)` | 그날 이행 점검 | | 부족 = max(0, 계획 − 실제)(FR-074) |
| `Distance supplementFor(LocalDate today, Map<LocalDate, Distance> actuals, Distance cap)` | 오늘 보충 추천 거리 | | min(cap, 올림(오늘 계획 + 이번 주 오늘 전 부족 합))(FR-187) |
| `List<LocalDate> plannedDays(DateRange r)` | 기간 안 계획일 | | |

##### WeeklyGoal (값, record)
속성: week(Week), count(int, 1~7), distance(Distance, > 0). `static WeeklyGoal of(Week, int, Distance)`(위반이면 BusinessRuleException(GOAL_INVALID)), `WeeklyAchievement achievement(int actualCount, Distance actualDistance)`(100% 넘으면 실제 값 그대로, FR-056).

##### DailyPlan (값, record)
속성: date(LocalDate), distance(Distance, > 0), targetPace(Pace, nullable).

##### PlanCheck (값, record)
속성: plan(DailyPlan), actual(Distance), actualPace(Pace, nullable), shortfall(Distance), fulfilled(boolean).

##### Streak (값)
`static int countBack(Set<LocalDate> runDays, LocalDate base)`: base에 기록이 없으면 전날부터, 거꾸로 끊길 때까지 센다(FR-052, FR-054). 개인·크루가 같은 규칙이라 한 메소드를 쓴다.

##### Grass (값)
속성: cells(SortedMap<LocalDate, Distance>). `static Grass of(Map<LocalDate, Distance> daily, DateRange last12Weeks)`, `int intensityOn(LocalDate d)`(0~4단계, 그 기간 최댓값 대비 비율).

##### FitnessSummary (값, record)
속성: fromRun, toRun(유효 러닝 순번), averageScore(RunningScore), distanceChange(Distance, 부호 있음 → 표현은 meters long), paceChangeSeconds(int), frequentDay(DayOfWeek), frequentHourBand(int, 3시간 단위 0~7).

| 시그니처 | 설명 |
|---------|------|
| `static FitnessSummary from(List<RunSummary> recent, List<RunSummary> previous)` | recent = 최근 유효 10회(10회 미만이면 있는 만큼), previous = 그 직전 10회. 직전이 없으면 변화량 0. RunSummary의 거리·시간·점수·시작일시 4항목만 쓴다 |
| `Trend distanceTrend()`, `Trend paceTrend()`, `Trend frequencyTrend()` | 향상·정체·하락(규칙 문구 선택용) |

##### Report
| 항목 | 내용 |
|------|------|
| 책임 | 회원의 최근 10회 리포트를 보관한다 |

속성: memberId(long, 고유), summary(FitnessSummary), comment(Comment, nullable), createdAt(LocalDateTime).

| 시그니처 | 설명 | 사후 조건 / 예외 |
|---------|------|-----------------|
| `void refresh(FitnessSummary s, LocalDateTime now)` | 요약 교체 | s.toRun이 지금 요약과 같으면 무시한다(같은 사건을 다시 받아도 코멘트가 지워지지 않는다). 다르면 교체하고 comment = null(코멘트 준비 중) |
| `void attachComment(Comment c)` | 코멘트 붙이기 | 요약이 바뀐 뒤 늦게 온 코멘트는 버린다(요약 순번이 다르면 무시) |
| `boolean hasComment()` | | |
| `ReportView view()` | 조회 결과 | 코멘트 없으면 "코멘트 준비 중" |

##### Comment (값, record)
속성: text(String), source(CommentSource), forRun(int, 어느 요약 순번의 코멘트인지).

##### CommentGenerator (인터페이스)
| 시그니처 | 설명 |
|---------|------|
| `Optional<Comment> generate(FitnessSummary summary, CommentContext context)` | 만들 수 없으면 비어 있음 |

CommentContext(record): llmAgreed(boolean), aiEnabled(boolean), memberId(long — 한도 계산용, LLM에는 보내지 않는다), today(LocalDate).

##### AiCommentGenerator (CommentGenerator 구현)
속성: gateway(LlmCommentGateway), quota(LlmCallQuota), limit(LlmCallLimit 공급자, OperationPolicy.llmCallLimit), timeout(Duration, 설정 runkk.ai.comment-timeout 기본 3초).
`generate`: llmAgreed && aiEnabled && quota.tryAcquire 일 때만 gateway.requestComment(summary, timeout). 실패·초과·예외면 Optional.empty (FR-065, FR-066). 받은 Comment의 forRun은 이 클래스가 summary.toRun으로 채운다(ai 서버는 기록 순번을 모른다, D-58).

##### RuleCommentGenerator (CommentGenerator 구현)
속성: catalog(RuleCommentCatalog: 20개 문구, (Trend × 지표) 키). `generate`는 언제나 값을 돌려준다(source RULE).

##### FallbackCommentGenerator (CommentGenerator 구현)
속성: primary, fallback(CommentGenerator). `generate`: primary 결과가 있으면 그것, 없으면 fallback 결과. 축소 운영(AI코멘트사용 꺼짐)은 CommentContext.aiEnabled = false로 primary가 비어 자연히 규칙 문구로 간다.

##### LlmCallQuota
속성: counter(QuotaCounter).
`boolean tryAcquire(long memberId, LocalDate day, LlmCallLimit limit)`: 회원 하루 1회, 전체 하루 200회를 검사한다. 두 키의 증가·검사·되돌리기를 QuotaCounter의 Lua 스크립트 하나로 원자 처리하고 키 TTL은 48시간이다. 넘으면 둘 다 되돌리고 false. LLM 호출이 실패해도 차감은 되돌리지 않는다(비용이 났을 수 있고 남용을 막는다, D-58).

##### RankingSnapshot
| 항목 | 내용 |
|------|------|
| 책임 | 한 번 집계한 랭킹표를 보관한다 |

속성: kind(RankingKind), period(AggregationPeriod), computedAt(LocalDateTime), entries(List<RankEntry>, 순위순).

| 시그니처 | 설명 | 사후 조건 / 예외 |
|---------|------|-----------------|
| `static RankingSnapshot build(RankingKind kind, AggregationPeriod period, LocalDateTime at, List<RankScore> scores)` | 정렬·순위 | 같은 거리는 같은 순위, 다음 순위는 건너뛴다(1, 2, 2, 4) |
| `Optional<RankEntry> entryOf(long subjectId)` | 본인(또는 본인 크루) 줄 | 나만 보기면 비어 있음 |
| `Optional<Distance> gapToNext(long subjectId)` | 바로 위 순위까지 남은 거리 | 1위면 비어 있음 |

##### RankEntry (값, record)
속성: rank(int), subjectId(long, 회원 또는 크루), displayName(DisplayName), tier(Tier, 크루 랭킹은 null), distance(Distance).

##### RankingSnapshotRebuilt (사건, record) (v0.40 반영, 05-30 D-858, sqa DF-089. PM 지시로 대신 반영(2026-11-15))
랭킹 스냅숏 하나를 새로 저장했다는 사건이다. analysis가 발행한다(D-31). 속성: kind(RankingKind), period(AggregationPeriod), computedAt(LocalDateTime, 저장한 스냅숏의 집계 시각이며 조회 응답 aggregatedAt과 같은 값, 밀리초 단위), topSubjectIds(List<Long>, 상위 줄 회원 또는 크루 ID 최대 `RankingService.TOP` = 100개, 순위순, 불변 사본). `occurredAt()`은 computedAt이다.

- 발행: `RankingService`의 패키지 내부 `save(RankingSnapshot)`가 스냅숏 저장과 **같은 쓰기 트랜잭션**(`AnalysisTransactions.write`) 안에서 `DomainEventPublisher.publish(rebuiltOf(snapshot))`. 0시 `rebuild`, 시작 때 `rebuildIfMissing`, 운영자 `recompute`가 모두 이 경로를 탄다. 재집계 시각은 `at.truncatedTo(ChronoUnit.MILLIS)`로 자른다(Redis 키와 DB 값이 같은 밀리초).
- 수신: `web.record.RankingLookPrefill.onRebuilt`(`@TransactionalEventListener(phase = AFTER_COMMIT)`, 새 읽기 전용 트랜잭션 REQUIRES_NEW). 개인 스냅숏이고 상위 줄이 있을 때만 `avatar.RunnerLookQuery.snapshotLooksOf`로 그 스냅숏 키(`RankingSnapshotIds.of`)에 줄 모습을 미리 채운다. 크루 스냅숏은 건너뛰고, 실패는 경고 로그만 남겨 재집계는 성공으로 둔다(조회 때 지연 채움이 안전망).
- analysis는 avatar를 import하지 않는다. analysis → avatar 화살표를 만들지 않으려고 사건으로 넘긴다(D-46). 12.2 의존 표는 바뀌지 않는다(web.record → analysis·avatar는 이미 허용).

##### DashboardService
| 항목 | 내용 |
|------|------|
| 의존 | RunQueryService(running), RunningGoalRepository, CrewMembershipReader(crew), MemberQueryService(member), OperationPolicy, Clock |

| 시그니처 | 설명 |
|---------|------|
| `DashboardView dashboard(long memberId, LocalDate baseDate)` | srs.md 6.3 3.1. 오늘·누적 요약(RunQueryService.dailyStats, totals), LandmarkTable.personalProgressOf(누적 유효 거리), Streak.countBack, Grass.of, 크루가 있으면(CrewMembershipReader.activeCrewOf → CrewSummary의 crewId, 크루 영역 제목에 name) CrewMembershipReader.activeMemberIds → RunQueryService.dailyDistances로 크루 스트릭·잔디, distancesByMember(현재 크루원, 전체 기간) 합으로 LandmarkTable.crewProgressOf(크루 합), WeeklyGoal.achievement, 달력. 거리 표시는 MemberQueryService.preferenceOf(memberId).unit. WeeklyRace 테이블을 읽지 않는다(v0.1의 외부 결합 제거, D-32) |

`DashboardView.latestRun`은 `RunQueryService.latestListRow`로 조회한 본인 최신 기록을 목록과 같은 행 형식으로 담고, 없으면 null이다. 대결 결과는 `DashboardController`가 대결 ID로 읽어서 응답에 붙인다(D-491·D-523). 이 필드는 홈 「내 기록 요약」의 최신 기록에 쓰인다(FR-287).

##### RecordQueryService
| 항목 | 내용 |
|------|------|
| 의존 | RunQueryService(running), MemberQueryService(member), CrewMembershipReader(crew), WeeklyRaceService(crew, contributionOf), RewardService(reward, accruedFor), OperationPolicy, Clock |

| 시그니처 | 설명 |
|---------|------|
| `RecordListView list(long viewerId, long ownerId, DateRange range)` | 공개 판정(MemberQueryService.preferenceOf(ownerId).recordVisibility.allows) 뒤 RunQueryService.summariesBetween 최신순. 무효 러닝은 "집계 제외" 표시. 본인 기기의 미전송 기록은 앱이 합쳐서 보인다 |
| `RecordDetailView detail(long viewerId, String recordId)` | srs.md 6.3 3.2 의사결정표. RunQueryService.detail·routePoints. 코스 지도 좌표는 본인이거나 코스지도공유 켜짐일 때만. 소모 열량 CalorieCoefficient.estimate, 심박 구간(본인이고 MemberQueryService.ageOf(ownerId, 올해)가 있을 때 maxHeartRate = 220 − 나이를 RunQueryService.detail에 넘긴다), 신기록, 크루 반영 거리, 적립 리워드(본인). RecordDetailView에 정지 구간 pauses(RunDetail.pauses)와 정지 시간 합 pausedSeconds를 더한다. 기존 경과 시간 항목은 운동 시간이다(web.record RecordDetailResponse.pauses·pausedSeconds) (v0.11, FR-036) |
| `RunCardData cardData(long memberId, String recordId)` | 본인 기록만. 카드 앞면 값(RunQueryService.summary, 카드 종류는 RunSummary.cardKind) + 뒷면 경로 좌표(routePoints) + 코스지도공유 값(공유 때 뒷면 허용 여부) |

##### GrowthService
| 시그니처 | 설명 |
|---------|------|
| `GrowthView growth(long memberId, AggregationPeriod period)` | 주간 8주·월간 6개월 묶음(RunQueryService.periodTotals), RunQueryService.best ×5, longest |

##### ReportService
| 시그니처 | 설명 |
|---------|------|
| `void onRunSaved(RunRecordSaved e)` | 비동기. 유효 러닝만. RunQueryService.recentSummaries(memberId, 20) → FitnessSummary.from → Report.refresh → ReportCommentService.generateComment |
| `ReportView report(long memberId)` | 리포트가 없으면 "유효 러닝 기록 없음" |

##### ReportCommentService
| 시그니처 | 설명 |
|---------|------|
| `void generateComment(long memberId)` | CommentContext 구성(MemberQueryService.preferenceOf(memberId).llmAgreed, policy.features().aiCommentEnabled) → FallbackCommentGenerator.generate → Report.attachComment |

##### RankingService
| 시그니처 | 설명 |
|---------|------|
| `void rebuild(LocalDateTime now)` | 0시. 개인·크루 × 주간·월간 4개 스냅숏. 대상 회원 = MemberQueryService.rankedMemberIds()(나만 보기 제외), 크루 = CrewMembershipReader.activeMembersByCrew(). 두 목록의 회원 합집합으로 RunQueryService.distancesByMember(range)를 한 번 부른다. 개인은 회원별 거리, 크루는 크루원 거리 합 ÷ 크루원 수(비공개 포함). 회원 표시명은 MemberQueryService.summaries, 크루 이름은 CrewMembershipReader.crewNames로 붙인다(design-review.md M6). 운영자 재계산(recompute)이 돌고 있으면 끝날 때까지 기다렸다 이어서 집계한다(0시 집계를 건너뛰지 않는다) (v0.11, CR2-U1) |
| `RankingRecomputeResult recompute()` | POST /admin/rankings/recompute(web.admin.ranking RankingAdminController, v0.11 검증 D-136). 0시를 기다리지 않고 지금 시각으로 rebuild와 같은 경로(종류·기간마다 한 트랜잭션, 커밋 뒤 캐시 판 교체)를 같은 잠금 안에서 탄다. 다른 집계가 돌고 있으면 기다리지 않고 IllegalStateException(web.error가 409로 바꾼다). 감사 AuditAction.RANKING_RECOMPUTED(V18)는 web.admin.ranking이 남긴다 (v0.11, CR2-U1) |
| `RankingView personalRanking(long memberId, AggregationPeriod period)` | 개인 최근 스냅숏 + entryOf + gapToNext + 집계 기준 시각. 나만 보기면 안내 문구. 페이지와 본인 행의 회원을 `MemberQueryService.summaries`로 읽어 `RankingView.publicTiers`에 현재 공개 티어를 붙인다. `publicTierOf(entry)`는 회원 ID로 읽는다. UNRANKED는 「배치 중」으로 표시한다(D-486, FR-282). |
| `RankingView crewRanking(long memberId, AggregationPeriod period)` | 크루 최근 스냅숏 + 본인 크루의 entryOf + gapToNext + 집계 기준 시각. 크루 없음이면 본인 크루 순위 비움. 크루 랭킹의 `publicTiers`는 빈 맵이다(D-486). |

`RankingView.activeMemberIds()`(v0.40 반영, 05-30 D-858. PM 지시로 대신 반영(2026-11-15)): `Set<Long>`. 개인 랭킹 줄(entries·mine) 가운데 탈퇴하지 않은 회원 ID, 곧 `publicTiers`의 키(D-486과 같은 탈퇴 제외)다. web.record `RankingController`가 줄 회원 모습(look)을 붙일 대상으로 쓰고, 집계 시각이 없거나 이 집합이 비면 모습을 읽지 않는다. 크루 랭킹과 스냅숏 없음이면 비어 있다.

v0.1의 `ranking(memberId, RankingKind, period)`은 kind가 개인·크루 처리 경로를 골라 제어 결합이었다. 두 연산으로 나눴다(D-36). RankingKind는 RankingSnapshot 안의 구분으로만 남는다.

##### CoachService
| 항목 | 내용 |
|------|------|
| 의존 | RunningGoalRepository, RunQueryService(running: distancesByMember, dailyStats), NotificationService(notification: PLAN_MISSED), OperationPolicy(policy: SupplementCap, CoachingRule), Clock (design-review.md M29) |

| 시그니처 | 설명 |
|---------|------|
| `GoalResult setWeeklyGoal(long memberId, WeeklyGoalCommand cmd)` | WeeklyGoal.of → RunningGoal.setWeeklyGoal |
| `GoalResult putPlan(long memberId, DailyPlan plan)` | RunningGoal.putPlan(plan, today) |
| `PlanProgressView progress(long memberId, DateRange range)` | 날짜마다 checkOn + supplementFor(cap = policy.supplementCap().cap()) |
| `TodayPlanView todayPlan(long memberId)` | 워치 시작 화면·코치 계획 따르기용(FR-042, FR-048) |
| `int remindMissedPlans(LocalDateTime now)` | 20시. batch.analysis의 PlanReminderJob이 부른다. RunningGoalRepository.membersWithPlanOn(today) → RunQueryService.distancesByMember(그 회원들, 오늘)로 오늘 기록 0인 회원마다 NotificationService.notify(PLAN_MISSED)(FR-075) |

##### AnalysisWithdrawalListener
`void onMemberWithdrawn(MemberWithdrawn e)`: RunningGoal, Report를 지운다(D3, D4).

##### 구현 보조 클래스 (analysis) (v0.11, CR2-U7, CR2-U1)
| 클래스 | 종류 | 책임 | 속성·연산 |
|--------|------|------|-----------|
| AnalysisTransactions | 패키지 전용 보조 | analysis 작업을 짧은 트랜잭션으로 나눈다 | TransactionTemplate 두 개(새 트랜잭션 REQUIRES_NEW 쓰기, 읽기 전용). `<T> T write(Supplier<T> work)`, `<T> T read(Supplier<T> work)`. 코멘트 생성(ai 호출 최대 3초) 동안 리포트 행을 잡지 않게 읽기 → 생성 → 다시 읽고 붙이기를 나누고, 0시·20시 작업은 대상마다 따로 감싸 한 건의 실패가 다른 건을 막지 않게 한다. 트랜잭션 틀의 통합 위치는 F.8 P-1(P-1, /sw-design 검증에서 결정) |
| RankingPage | 조회 결과, record | 랭킹 조회 한 번에 필요한 부분을 담는다 | computedAt(LocalDateTime), top(List<RankEntry>, 순위순, 불변 사본), mine(RankEntry, 없으면 null), gapToNext(Distance, 없으면 null). RankingSnapshotRepository.latestPage가 돌려준다. 캐시는 스냅숏 전체가 아니라 상위 N줄과 회원별 줄을 따로 둔다 |
| RankingRecomputeResult | 결과, record | 랭킹 다시 계산 한 번의 결과를 담는다 | computedAt(LocalDateTime, 한국 시간), personalCount(int), crewCount(int), failedSnapshots(int, 0~4. 실패한 종류·기간은 이전 스냅숏이 남는다). RankingService.recompute의 반환, 관리 API 응답 |
| RecordRow | 조회 결과, record | 기록 목록·상세의 기록 한 줄을 담는다 | recordId, startedAt, distance, elapsed, averagePace, valid(무효면 "집계 제외"), mode(String), cardKind(String). 모드·카드 종류는 이름 문자열로 내줘 web.record가 running을 import하지 않게 한다(12.2 D48). `static RecordRow of(RunSummary s)`(패키지 전용) |

##### 포트 인터페이스 (analysis가 정의)
| 인터페이스 | 시그니처 |
|-----------|---------|
| RunningGoalRepository | `RunningGoal findOrCreate(long memberId)`, `void save(RunningGoal)`, `List<Long> membersWithPlanOn(LocalDate d)`, `void deleteByMember(long)` |
| ReportRepository | `Optional<Report> findByMember(long)`, `void save(Report)`, `void deleteByMember(long)` |
| RankingSnapshotRepository | `void save(RankingSnapshot)`(같은 종류·기간의 이전 스냅숏을 대신한다), `Optional<RankingSnapshot> latest(RankingKind, AggregationPeriod)`(DB 전체, 시작 때 스냅숏 유무 확인용), `Optional<RankingPage> latestPage(RankingKind kind, AggregationPeriod period, int top, Optional<Long> subjectId)`(상위 top줄과 subject 줄, 캐시 우선·비면 DB) (v0.11, CR2-U7) |
| QuotaCounter | `long incrementAndGet(String key, Duration ttl)`, `void decrement(String key)` |
| LlmCommentGateway | `Optional<Comment> requestComment(FitnessSummary summary, Duration timeout)` — 회원 식별자 매개변수가 없다(NFR-024). 구현 infra.integration.ai.AiServerCommentGateway |

v0.1의 기록 통계 조회 포트(analysis 소유 포트를 run_record·member·crew_membership 테이블 조인으로 구현)는 없앴다. 다른 패키지 테이블을 읽는 쿼리라 원본이 바뀌어도 갱신 책임이 없었다(design-review.md H-01). 같은 값은 RunQueryService·MemberQueryService·CrewMembershipReader를 조합해 만든다(D-32).

#### 6.1.5 profile

##### ProfileQueryService
| 항목 | 내용 |
|------|------|
| 책임 | 프로필 조회 유스케이스를 조정한다 |
| 의존 | MemberQueryService(member), RunimalQuery(runimal), AvatarQuery(avatar), CrewMembershipReader(crew), RunQueryService(running) |

| 시그니처 | 설명 |
|---------|------|
| `ProfileView view(long viewerId, long targetId)` | ProfileData를 모아 ProfileDisclosure.from(MemberQueryService.disclosureOf(viewerId, {targetId}).get(targetId)).filter(data). 공개 수준 판정은 member가 한다(D-52). 표시명·티어는 MemberQueryService.summaries, 레벨·사진·소개·나이대·성별은 MemberQueryService.profileOf(targetId, 올해)(v0.11 CR2-U1), 공개 범위·설정은 preferenceOf, 유형은 RunimalQuery.typesOf, 얼굴은 AvatarQuery.faceKey, 배경은 AvatarQuery.backgroundKeyOf(v0.11 FR-202), 소속 크루는 CrewMembershipReader.activeCrewOf(CrewSummary), 최장 거리는 RunQueryService.longestDistance. 응답에 계정 유형을 싣지 않는다(FR-006). 다른 패키지의 엔티티·Repository를 받지 않는다(D-29, D-32) (v0.11, FR-006, FR-202, CR2-U1, D-91) |

##### ProfileView (조회 결과, record) (v0.11, FR-007, FR-202, D-91)
v0.11 변경: tags 항목을 지우고(러너 성향 태그 삭제), avatarBackgroundKey(String, null = 기본 배경)를 더한다. avatarBackgroundKey는 아바타 얼굴을 보이는 공개 수준(SELF·PUBLIC·PRIVATE)에서만 싣고 WITHDRAWN에서는 비운다(ProfileDisclosure.filter). web.me ProfileResponse.avatarBackgroundKey로 나간다 (v0.11, FR-202, F.2.2).

##### ProfileDisclosure (enum, 다형성)
| 상수 | 대응하는 member.DisclosureLevel | filter가 남기는 항목 |
|------|------|--------------------|
| SELF | SELF | 전부 + 최장 거리 + 설정 (FR-005) |
| PUBLIC | PUBLIC | 설정·최장 거리를 뺀 항목 |
| PRIVATE | PRIVATE | 표시명, 아바타 얼굴, 티어, 소속 크루 (Q-69) |
| WITHDRAWN | WITHDRAWN | 표시명 "탈퇴한 사용자"만 (FR-006) |

`static ProfileDisclosure from(DisclosureLevel level)`, `ProfileView filter(ProfileData data)`. 어느 수준인지는 정하지 않는다. v0.3의 of(viewerId, MemberSummary)는 열람자 관계 판단을 여기서 해서 crew가 같은 규칙을 따로 구현해야 했다(design-review.md H-18). 판단은 member.DisclosureLevel.of로 옮겼다(D-52).

