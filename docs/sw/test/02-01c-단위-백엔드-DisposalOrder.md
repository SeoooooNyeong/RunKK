> [런크크 테스트 계획 및 케이스](../test.md) 목차 · 이전: [02-01b-단위-백엔드-2.1.2.11](02-01b-단위-백엔드-2.1.2.11.md) · 다음: [02-01d-단위-백엔드-D-67-알림-0시-정리와](02-01d-단위-백엔드-D-67-알림-0시-정리와.md)
#### DisposalOrder (`running/DisposalOrder.java`, 상태도 2.4.10)

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1135 | FR-026, NFR-036 | schedule(memberId 1, ROUTE, 철회 t0) → execute(t1) → confirmDevice(t2) | 없음 | SCHEDULED, dueAt t0 + 7일 → EXECUTED → DEVICE_CONFIRMED | RunRecordTest#disposalOrder_stateTransitions | 미실행 |
| TC-1136 | FR-026 | confirmDevice(t1) → execute(t2) | SCHEDULED | confirmDevice 뒤 SCHEDULED 유지(확인 시각만), execute 뒤 DEVICE_CONFIRMED | RunRecordTest#disposalOrder_stateTransitions | 미실행 |
| TC-1137 | FR-026 | 금지 전이: EXECUTED에서 execute; DEVICE_CONFIRMED에서 confirmDevice·execute; schedule(1, null, t0) | 각 상태 | IllegalStateException 세 건, IllegalArgumentException | 없음 | 미실행 |
| TC-1138 | NFR-036 | isOverdue: dueAt − 1초 / dueAt / dueAt + 1초; EXECUTED에서 dueAt + 1일 | SCHEDULED | false / false / true; false(서버 파기 끝남) | 없음 | 미실행 |

#### RunningScore (`running/RunningScore.java`, A-01)

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1139 | FR-047, FR-104 | of(5km, 25:00); 그 점수의 paceFor(5km); average([]); of(999m, 10분) | 없음 | 약 38.3(±0.1, A-01 예); 300초/km(±1); IllegalArgumentException; IllegalArgumentException(d ≥ 1km) | RunningValuesTest#runningScore_vdotOf5kIn25Minutes, RunningValuesTest#runningScore_paceFor_invertsTheFormula, RunningValuesTest#runningScore_average | 미실행 |

#### Notification·NotificationRequest (`notification/`, 상태도 2.4.14)

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1140 | FR-178 | Notification.of(요청, t0) → markRead() → markRead() | 없음 | 생성 직후 read = false(UNREAD) → true(READ) → true 유지 | NotificationServiceTest#notification_markReadAndExpiry | 미실행 |
| TC-1141 | FR-175 | NotificationRequest content "" / "   " / null; Notification.of(요청, null) | 없음 | IllegalArgumentException 네 건 | NotificationServiceTest#notificationRequest_requiresContent | 미실행 |

#### AuditRecord·AuditService.purgeExpired (`audit/`)

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1142 | FR-176 | AuditEntry(actorRole null) / (action null) / (targetId ""); AuditRecord 공개 변경 메소드 | 없음 | IllegalArgumentException 세 건; 공개 setter·변경 메소드 0개(불변) | AuditServiceTest#auditEntry_requiresActorRoleActionAndTarget, AuditServiceTest#auditRecord_isImmutable_noPublicMutators | 미실행 |
| TC-1143 | FR-176, NFR-035 | purgeExpired(now = 2026-10-21 03:00) | 기록 at = 2026-04-21 03:00(정확히 6개월) / 2026-04-20 03:00(6개월 1일 전) / 2026-04-21 03:01 | 남음 / 삭제 / 남음, 반환 1 | AuditServiceTest#purgeExpired_deletesRecordsOlderThanSixMonths | 미실행 |

### 2.1.5 블랙박스 단위: member·running·notification 응용 서비스

인메모리 저장소, 가짜 Gateway, 사건 기록용 가짜 Publisher, 고정 Clock 2026-10-21 10:00을 쓴다.

#### NotificationService (`notification/NotificationService.java`, SQ-14)

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1144 | FR-175 | notify(수신자 7, BATTLE_CORRECTED, "결과가 정정됐어요") | pushDecision = WITHDRAWN | 알림 저장 0, 푸시 호출 0 | NotificationServiceTest#notify_muted_savesOnly_withdrawn_dropsAll | 미실행 |
| TC-1145 | FR-175 | 같은 요청 | pushDecision = MUTED, 토큰 2개 | 알림 저장 1(수신 설정과 무관, W-03), 푸시 호출 0 | NotificationServiceTest#notify_muted_savesOnly_withdrawn_dropsAll | 미실행 |
| TC-1146 | FR-175 | 같은 요청 | ALLOWED, 토큰 [a, b], 가짜 PushGateway가 무효 토큰 [b] 반환 | 저장 1, 푸시 호출 1(토큰 a·b), 토큰 b 삭제, a 유지 | NotificationServiceTest#notify_allowed_savesAndPushes_invalidTokensDeleted | 미실행 |
| TC-1147 | FR-175 | 같은 요청 | ALLOWED, ① 가짜 Gateway가 RuntimeException ② 토큰 0개 | ① 저장 1, 예외 전파 없음, 재시도 0, 운영 로그 1 ② 저장 1, 푸시 호출 0 | NotificationServiceTest#notify_pushFailure_isLoggedNotThrown_andNoDevicesMeansNoPush | 미실행 |
| TC-1148 | FR-178 | open(회원 7, 알림 id 3); open(회원 8, 알림 id 3); open(7, 없는 id 99) | 알림 3의 수신자 7, UNREAD | 이동 대상 반환, read = true; PermissionDeniedException; BusinessRuleException(NOTIFICATION_NOT_FOUND) | NotificationServiceTest#inbox_newestFirst_withRange_andOpenMarksRead | 미실행 |

#### MemberSettingService.withdrawConsent (`member/MemberSettingService.java:63`)

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1149 | FR-013, FR-026, FR-017 | withdrawConsent(회원 1, LOCATION); (1, HEART_RATE) | ACTIVE, 두 동의 있음 | 각각 저장 1, 감사 CONSENT_CHANGE 1(전 true, 후 false), ConsentWithdrawn 1(item LOCATION / HEART_RATE) | MemberAccountServicesTest#withdrawConsent_location_auditsAndPublishesOnce | 미실행 |
| TC-1150 | FR-025, FR-017 | withdrawConsent(1, MARKETING); giveConsent(1, MARKETING) | ACTIVE | 감사 1씩, ConsentWithdrawn 0(위치·심박만 발행) | MemberAccountServicesTest#withdrawConsent_marketing_doesNotPublish_giveConsentAudits | 미실행 |

#### WithdrawalService (`member/WithdrawalService.java:48, 71`)

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1151 | FR-020, FR-024 | withdraw(1) | 가드 2개 중 둘째가 "크루원이 있는 크루장" 사유 반환 | WithdrawalResult.rejected("크루원이 있는 크루장"), 회원 ACTIVE, 감사 0, MemberWithdrawn 0 | MemberAccountServicesTest#withdraw_guardRejects_orWithdrawsAuditsAndPublishes | 미실행 |
| TC-1152 | FR-020, FR-021 | withdraw(1) | 가드 모두 통과 | completed, 회원 WITHDRAWN, 감사 WITHDRAWAL 1(행위자 "1", 역할 RUNNER), MemberWithdrawn(1, 10:00) 1 | MemberAccountServicesTest#withdraw_guardRejects_orWithdrawsAuditsAndPublishes | 미실행 |
| TC-1153 | FR-021, NFR-026 | purgeWithdrawn(now) | 탈퇴 시각 now − 7일 − 1초 / now − 7일 + 1초 / ACTIVE 회원 | 반환 1: 첫 회원만 purgeResidual, 나머지 둘 불변 | MemberAccountServicesTest#purgeWithdrawn_onlyAfterSevenDays | 미실행 |

#### RunUploadService.upload (`running/RunUploadService.java:59`, SQ-02, A-04)

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1154 | FR-040 | 같은 recordId로 upload 두 번 | 첫 업로드 저장됨 | 둘째 UploadResult.alreadyStored(recordId), 저장 1건 유지, RunRecordSaved 1건만 | RunningServicesTest#upload_assignsOrdinals_skipsInvalid_andIsIdempotent | 미실행 |
| TC-1155 | FR-022, FR-047 | 유효 기록, 무효 기록(시속 30 구간), 유효 기록 순서로 upload | 유효 기록 0건인 회원 | RunRecordSaved validRunOrdinal = 1, 0, 2 (무효는 0, D-37) | RunningServicesTest#upload_assignsOrdinals_skipsInvalid_andIsIdempotent | 미실행 |
| TC-1156 | FR-184, NFR-038 | 첫 포인트 (37.56789, 126.97812) 기록 upload | ① 가짜 WeatherGateway 정상(18℃, CLEAR) ② Gateway 예외 | ① lookup 좌표 (37.57, 126.98), 회원 식별자 없음, 날씨 저장 ② 날씨 없이 저장, 재요청 0 | RunningServicesTest#upload_attachesWeatherWithCoarsenedPoint_orStoresWithout | 미실행 |
| TC-1157 | FR-014, FR-041 | 심박 60개 포함 upload | 심박 동의 없음 | 기록 저장, 심박 0건 저장 | RunningServicesTest#upload_heartRatesNeedConsent | 미실행 |

### 2.1.6 블랙박스 단위: battle·runimal (B3)

#### MatchTicket (`battle/MatchTicket.java`, 상태도 2.4.1)

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1158 | FR-080, FR-022 | open(1, UNRANKED, KM5) / (1, GOLD, HALF) / (1, GOLD, KM5) | 없음 | IllegalArgumentException(배치 전 언랭은 랜덤 매칭 불가) / IllegalArgumentException(랜덤 거리 1·3·5·10km만) / WAITING | MatchTicketTest#open_rejectsUnrankedAndNonRandomDistance | 미실행 |
| TC-1159 | FR-081 | canPairWith: ① 같은 회원 ② 상대 ASSIGNED ③ KM3 대 KM5 ④ GOLD 대 SILVER ⑤ GOLD·KM5 대 GOLD·KM5 | tierWindow 0, 모두 WAITING(②만 상대 ASSIGNED) | ① false ② false ③ false ④ false ⑤ true (srs 6.3 4.2) | MatchTicketTest#canPairWith_decisionTable | 미실행 |
| TC-1160 | FR-083 | isExpired(waitingSince + 14.999초 / + 15.000초 / + 15.001초, 15초) | WAITING | false / false / true (15초 초과) | MatchTicketTest#isExpired_boundary | 미실행 |
| TC-1161 | FR-082 | assign() 두 번 | WAITING | 첫 호출 ASSIGNED, 둘째 IllegalStateException(금지 전이) | MatchTicketTest#assign_onlyOnce | 미실행 |

#### Battle 상태 전이 (`battle/Battle.java`, 상태도 2.4.2)

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1162 | FR-082 | random(a, b, now): ① a WAITING ② a·b 같은 회원 ③ 둘 다 ASSIGNED·같은 거리 | 없음 | ① IllegalStateException ② IllegalArgumentException ③ WAITING_START, 참가자 2명 | BattleTest#random_needsAssignedTickets | 미실행 |
| TC-1163 | FR-087 | scheduleStart(T0) 뒤 start(T0 − 1초) / start(T0) | WAITING_START | IllegalStateException(상태 유지) / IN_PROGRESS | BattleTest#start_waitsForStartAt | 미실행 |
| TC-1164 | FR-096, FR-097 | againstRecord(SELF, 상대 도착 25:00) → start(T1); againstRecord(GHOST) | 없음 | SELF: IN_PROGRESS·startAt 없음 → startAt T1, 상대 도착 시각 T1 + 25:00. GHOST: WAITING_START | BattleTest#againstRecord_initialStatesAndStartReport | 미실행 |
| TC-1165 | FR-107 | recordArrival(alice, T) → (bob, T + 10초) → (bob, T + 10.001초)(새 대결에서) | IN_PROGRESS | true, COUNTDOWN, countdownEndsAt T + 10초 → false(기록만), 상태 COUNTDOWN → 카운트다운 뒤 도착은 무시(false, 도착 없음) | BattleTest#recordArrival_countdown | 미실행 |
| TC-1166 | FR-107, FR-108 | closeCountdown(T + 9.999초) / (T + 10초); 5km 대결 isCloseDue(startAt + 74:59.999 / + 75:00) | COUNTDOWN / IN_PROGRESS | IllegalStateException / RESULT_PENDING; false / true (최대 시간 = 거리 × 15분) | BattleTest#closeCountdown_whenDue | 미실행 |
| TC-1167 | FR-094 | forfeit(alice): WAITING_START / IN_PROGRESS / 기록 대결 IN_PROGRESS; forfeit(비참가자 9) | 각 상태 | IllegalStateException / alice 패·bob 승·FINISHED / alice 패·기록 상대 승; IllegalArgumentException | BattleTest#forfeit_losesAndFinishes, BattleTest#forfeitAgainstRecord | 미실행 |
| TC-1168 | FR-092, FR-107 | verify(bob, 무효, 도착 T), verify(alice, 유효, 도착 T + 10초 / T + 10.001초) | RESULT_PENDING, countdownEndsAt T + 10초 | 카운트다운 안(T + 10초): alice 승(규칙 3); 카운트다운 뒤: bob 패, alice 무승부(규칙 3, 카운트다운 안 유효 도착 없음) | 없음 | 미실행 |
| TC-1169 | FR-108 | expirePending(E + 24시간 − 1초 / E + 24시간) | RESULT_PENDING, E = countdownEndsAt, bob 먼저 도착·기록 없음, alice 유효 | 빈 결과 / bob 기록 무효로 보고 alice 승, corrected = false(보류 뒤 첫 판정, srs 4.5). 먼저 도착한 bob의 기록이 없어 무효로 보는 규칙 3이지만 24시간 만료라 정정 아님, 알림 0 (v0.4, D-63 (3) 예외) | BattleTest#expirePending_after24Hours | 미실행 |

#### OpponentTrack (`battle/OpponentTrack.java`, A-08)

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1170 | FR-098 | uniform(5km, 25:00).distanceAt: −1초 / 0 / 12:30 / 25:00 / 30:00 | 없음 | 0m / 0m / 2,500m / 5,000m / 5,000m(마지막 표본 뒤는 마지막 거리) | OpponentTrackContractTest#everyLineOfTheContractTable_matches | 미실행 |
| TC-1171 | FR-098 | of([(10초, 40m), (20초, 100m)]).distanceAt(5초) / (15초) / (20초) | 없음 | 20m(출발점과 첫 표본 사이 직선 보간) / 70m / 100m | OpponentTrackContractTest#everyLineOfTheContractTable_matches | 미실행 |
| TC-1172 | FR-098, FR-102 | uniform(5km, 25:00).arrivalTime(0m) / (2.5km) / (5km) / (5.001km) | 없음 | 0 / 12:30 / 25:00 / 비어 있음 | BattleValuesTest#opponentTrack_helpers | 미실행 |
| TC-1173 | FR-098 | of([(20초, 40m), (10초, 60m)]) / of([(10초, 60m), (20초, 40m)]); uniform(5km, 0) | 없음 | IllegalArgumentException 세 건(경과 시간 오름차순, 누적 거리 단조 증가, 완주 시간 초과 0) | 없음 | 미실행 |

#### VirtualRecordFactory.generate (`battle/VirtualRecordFactory.java`, A-21)

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1174 | FR-104 | generate(기본 경계, 기본 규칙, 아무 닉네임도 안 겹침) | 고정 난수 | 100개 = 티어 5(브론즈~마스터) × 거리 4(1·3·5·10km) × 5개, (티어, 거리)마다 정확히 5개 | VirtualRecordFactoryTest#generates100 | 미실행 |
| TC-1175 | FR-104 | SILVER·5km 5개의 완주 시간 | 같음 | 오프셋 0 기록의 RunningScore.of(5km, 완주 시간) = 32.5 ± 0.1(티어 가운데 점수), 나머지 넷의 페이스 = 기준 페이스 × 0.95 / 0.975 / 1.025 / 1.05 | VirtualRecordFactoryTest#middleOffset_matchesMidScore | 미실행 |
| TC-1176 | FR-104, FR-003 | 고정 난수가 첫 후보로 "바람같은치타"를 내고, nicknameTaken("바람같은치타") = true | 같음 | 100개 이름이 서로 다르고 모두 2~20자, "바람같은치타"는 쓰이지 않음 | VirtualRecordFactoryTest#nicknames_uniqueAndNotTaken | 미실행 |

#### RunimalProfile (`runimal/RunimalProfile.java`, 상태도 2.4.11)

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1177 | FR-152, FR-153 | onValidRun 표(직전 → 새 순번): 0→1 / 2→3 / 3→3 / 3→4 / 8→9 / 9→10 / 19→20 | 각 직전 순번으로 만든 프로필 | NONE / PROVISIONAL / NONE(재전달, validRunCount 3 유지) / NONE / NONE(4~9회 재판정 없음, Q-47) / CONFIRM / CONFIRM | RunimalProfileTest#onValidRun_table | 미실행 |
| TC-1178 | FR-152, FR-153 | 건너뛴 순번: 2→4 / 9→11 / 2→11 / 0→10 / 11→19 | 같음 | PROVISIONAL / CONFIRM / CONFIRM(지점이 여럿이면 가장 큰 지점 하나, D-40) / CONFIRM / NONE | RunimalProfileTest#onValidRun_table, RunimalServicesTest#skippedThird_isRecoveredOnFourth | 미실행 |
| TC-1179 | FR-154 | remainingToNextJudgement: 직전 순번 0 / 2 / 3 / 9 / 10 / 15 | 없음 | 3 / 1 / 7 / 1 / 10 / 5 | RunimalProfileTest#remaining | 미실행 |
| TC-1180 | FR-152 | ① onValidRun(1)(NONE) 뒤 assignProvisional ② onValidRun(3) 뒤 confirm ③ onValidRun(3) 뒤 assignProvisional 두 번 | 새 프로필 | ① IllegalStateException ② IllegalStateException ③ 첫 호출 반영, 둘째 IllegalStateException(판정 지점당 한 번) | RunimalProfileTest#transitions, RunimalProfileTest#confirmWithoutProvisional | 미실행 |
| TC-1181 | FR-153, FR-151 | onValidRun(10) 뒤 confirm(아침 참새) → onValidRun(20) 뒤 confirm(아침 참새) → onValidRun(30) 뒤 confirm(쳇바퀴 햄스터) | 임시 유형 아침 참새 | true(처음 확정) → false(같은 유형) → true(새 카드). card()는 확정 유형 우선, 확정 전에는 임시 표시 카드, 판정 전에는 비어 있음 | RunimalProfileTest#transitions, RunimalJudgeTest#card_showsDominantPercent | 미실행 |

#### RunimalJudge와 축 평가 4개 (`runimal/RunimalJudge.java:39, 51`, `RunimalAxis.java`, A-14)

공통 실행 조건: RunimalRule 기본값(동행 0.5, 단거리 3km·0.5, 같은 코스 0.6, 습관 2시간·0.6), 최근 10회 표본.

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1182 | FR-153 | 동행: 최근 한 달 10회 중 크루런 연결 4 / 5 / 6회 | judgeConfirmed | 솔로런(A) / 소셜런(B) / 소셜런(B) (크루런 비율 50% 미만이면 솔로, srs 3.6.1) | RunimalJudgeTest#companion | 미실행 |
| TC-1183 | FR-153 | 거리: 3km 미만 4 / 5 / 6회; 정확히 3,000m 10회 | judgeConfirmed | 장거리(B) / 단거리(A) / 단거리(A); 장거리(B)(3km는 "미만"이 아님) | RunimalJudgeTest#distance | 미실행 |
| TC-1184 | FR-153, FR-166 | 코스: 같은 코스 5 / 6회(10회 모두 판정됨); 판정 3회(같음 2)·판정 없음(경로 파기) 7회; 판정 2회(첫 러닝은 비교 대상이 없어 sameCourse 거짓, 둘째는 같음) | judgeConfirmed | 탐험길(B) / 단골길(A); 단골길(A)(판정 없는 기록은 빼고 2 ÷ 3); 탐험길(B)(비교 대상이 없는 러닝은 같은 코스 아님으로 표본에 들어 1 ÷ 2 = 0.5 < 0.6, D-147, FR-166) (v0.6 후속, D-147) | RunimalJudgeTest#course | 미실행 |
| TC-1185 | FR-153 | 습관: 07:00 시작 5회 + 09:00(중앙값에서 120분) 1회 + 나머지 15:00 4회; 09:00 대신 09:01; 23:00 3회·01:00 3회·12:00 4회 | judgeConfirmed | 6 ÷ 10 → 루틴런(A); 5 ÷ 10 → 즉흥런(B); 원 위 23:00·01:00 차이 120분이라 6 ÷ 10 → 루틴런(A) | RunimalJudgeTest#habit | 미실행 |
| TC-1186 | FR-153, FR-166 | judgeConfirmed: 코스 판정 0회; 최근 한 달 0회(동행 표본 없음) | 나머지 축 데이터 있음 | 코스 비율 0.5(카드 표시용) + CourseAxisEvaluator.leanWithoutSample() = B → 탐험길(B)(judgeProvisional도 같다, 경로 파기 기록만 있어 표본 0이어도 같다); 동행 비율 0.5(카드 표시용) + 기본 leanWithoutSample() = A → 솔로런(A)(동행·거리·습관은 판정 회차 기록이 표본에 들어가 실제로는 비지 않아 AxisEvaluator 단위로만 본다) (v0.6 후속, D-147). v0.5의 태그 FAMILIAR_ROUTE·EXPLORER_ROUTE 갈래는 폐기: RunimalJudge.judge의 태그 매개변수를 지웠다 (v0.6, CR-04, D-91, FR-152) | RunimalJudgeTest#confirmed_allData_courseFallsBackToTag | 미실행 |
| TC-1187 | FR-152 | judgeProvisional: 태그 SOLO_RUN·EXPLORER_ROUTE, 데이터는 크루런 100%·같은 코스 100% | 3회 표본 | 동행 솔로런(A)·코스 탐험길(B)(동행·코스는 태그 우선, W-14), 거리·습관은 데이터. 폐기: 러너 성향 태그(FR-119)를 지워 임시 판정은 태그 없이 데이터로만 한다(TC-1188과 같은 규칙) (v0.6, CR-04, D-91) | RunimalJudgeTest#provisional_usesTagsForCompanionAndCourse | 폐기(v0.6, CR-04) |
| TC-1188 | FR-152 | judgeProvisional(태그 매개변수 없음): 데이터 있음; 표본 0회 | 3회 표본 / 표본 없음 | 데이터로 판정; 동행·거리·습관은 E-08(A쪽)이고 코스는 표본이 없으면 탐험길(B) → 골목 탐험 다람쥐(솔로·단거리·탐험·루틴, 임시·확정 판정이 같다) (v0.6 후속, D-147). v0.11에서 모든 회원이 이 규칙을 탄다("태그 없음 규칙과 같은 결과", FR-152) (v0.6, CR-04, D-91) | RunimalJudgeTest#provisional_fallsBackToDataThenA | 미실행 |
| TC-1189 | FR-151 | 축 결과 (A,A,A,A) / (A,A,A,B) | 없음 | 아침 참새 / 쳇바퀴 햄스터 (srs 3.6.1 유형 표) | RunimalJudgeTest#card_showsDominantPercent | 미실행 |

#### CourseSimilarityJudge.isSameCourse (`runimal/CourseSimilarityJudge.java:24-36`, A-15)

공통 실행 조건: CourseSimilarityRule 기본값(coverage 0.8, 반경 50m, 비교 30회).

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1190 | FR-166 | 새 경로 = 이전 경로와 같은 점들; 이전 경로 목록 []; 새 경로 [] | 없음 | true; false; false | CourseSimilarityJudgeTest#sameRoadShifted_isSame, CourseSimilarityJudgeTest#resampleAndEmpty | 미실행 |
| TC-1191 | FR-166 | 같은 길을 동쪽으로 30m / 80m 평행 이동한 새 경로 | 이전 경로 1개 | true / false (반경 50m) | CourseSimilarityJudgeTest#sameRoadShifted_isSame, CourseSimilarityJudgeTest#farRoad_isDifferent | 미실행 |
| TC-1192 | FR-166 | 10m 간격 100점 새 경로 중 이전 경로 50m 안이 80점 / 79점 | 이전 경로 1개 | true / false (80% 이상) | CourseSimilarityJudgeTest#coverageThreshold_andAnyPrevious | 미실행 |
| TC-1193 | FR-166 | 이전 경로 [먼 경로, 같은 경로]; 5m 간격 200점 새 경로 | 없음 | true(어느 한 코스); 10m 간격으로 다시 표본화해 약 100점으로 판정 | CourseSimilarityJudgeTest#coverageThreshold_andAnyPrevious, CourseSimilarityJudgeTest#resampleAndEmpty | 미실행 |

#### LiveBattleSession (`battle/LiveBattleSession.java:52, 72, 96, 112`, A-09)

공통 실행 조건: 5km 랜덤 대결 세션, 참가자 alice(1)·bob(2), open 시각 t0.

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1616 | FR-088 | open(names 1명 / 2명 / 3명) | 없음 | IllegalArgumentException / 세션 생성 / IllegalArgumentException (랜덤 대결은 2명) | 없음 | 미실행 |
| TC-1617 | FR-088, FR-089, NFR-021 | onProgress(alice, 1,000m·5:00, t0 + 5분); onProgress(회원 9, …) | bob 진행값 없음(0m) | bob에게 OpponentProgress 1건(표시명 alice, 1,000m, 5:00, 거리 차 −1,000m, CONNECTED), 메시지에 위도·경도 필드 없음; 회원 9는 빈 목록. 메시지의 sentAt은 alice 진행값의 sentAt 그대로(TC-1713) (v0.4, D-65) | BattleServicesTest#relayAndOvertake | 미실행 |
| TC-1618 | FR-188 | alice 1,020m·bob 1,000m로 선두 alice가 정해진 뒤 bob이 alice보다 9m / 10m / 11m 앞선 진행값 | 선두 alice | 처음 선두가 정해질 때 추월 안내 0. 9m·10m: 선두 유지, 안내 0. 11m: bob 추월 +1, bob에게 OVERTOOK(11m), alice에게 OVERTAKEN(11m) (10m 초과만 순서 변경) | BattleValuesTest#liveSession_overtakeMarginArrivalAndSnapshot | 미실행 |
| TC-1619 | FR-188, FR-093 | 선두가 alice → bob → alice → bob으로 세 번 바뀌는 진행값(차이 모두 20m) | 없음 | overtakeCounts() = {alice 1, bob 2} | BattleServicesTest#relayAndOvertake | 미실행 |
| TC-1620 | FR-102, FR-107 | alice 진행값 4,999m / 5,000m / 5,010m 순서 | 없음 | newArrival(): 비어 있음 / alice / 비어 있음(도착은 한 번만) | BattleValuesTest#liveSession_overtakeMarginArrivalAndSnapshot | 미실행 |
| TC-1621 | FR-088, FR-090 | alice 마지막 진행값 시각 t; detectTimeouts(t + 2.999초 / t + 3.000초 / t + 3.001초), 한 번 더 t + 4초 | bob은 계속 진행값을 보냄 | 끊김 0 / 0 / alice 1건(bob에게 CHECKING "상대 연결 확인 중", timedOut() = [alice]) / 다시 표시하지 않음(0건) | BattleValuesTest#liveSession_timeouts | 미실행 |
| TC-1622 | FR-090, FR-091 | ① 도착한 alice가 10초 동안 진행값 없음 → detectTimeouts ② 끊긴 alice의 새 진행값 | ① alice 도착 ② alice 끊김 표시 중 | ① 끊김 0 ② reconnected() = alice, bob에게 CONNECTED | BattleServicesTest#timeoutAndReconnect | 미실행 |
| TC-1623 | FR-188 | snapshot() → restore(snapshot) 뒤 같은 진행값 | 선두 bob, 추월 {1, 2}, alice 도착, bob 끊김 | 복원 세션의 선두·추월·도착·끊김이 같고, 다음 추월 판정 결과도 원래 세션과 같음 | BattleValuesTest#liveSession_overtakeMarginArrivalAndSnapshot | 미실행 |

#### BattleParticipant·Disconnection (`battle/BattleParticipant.java`, 상태도 2.4.3)

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1624 | FR-090 | disconnect(t1) → disconnect(t2) → reconnect(t3) → reconnect(t4) → disconnect(t5) | CONNECTED | 끊김 기록 1건(t1) → 그대로 1건(열린 끊김 중복 없음) → t1~t3 닫힘 → 변화 없음 → 2건. 끊김 시작·복구 일시가 남음 | BattleTest#disconnectAndReconnect | 미실행 |
| TC-1625 | FR-107 | arrive(t) → arrive(t + 2초) | 도착 전 | true, arrivedAt t → false, arrivedAt t 유지 | BattleTest#recordArrival_countdown | 미실행 |
| TC-1626 | FR-092 | claim(T0): ① 기록 없음, 실시간 도착 t ② verify(유효, 25:00) ③ verify(유효, 도달 없음) | startAt T0 | ① (alice, t, 기록 없음) ② (alice, T0 + 25:00, 유효) ③ (alice, 도착 없음, 유효) (D-30) | BattleTest#arrivalUsesRecordedReach | 미실행 |
| TC-1627 | FR-188 | recordOvertakes(−1) / (0) / (3) | 없음 | IllegalArgumentException / 0회 / 3회 | 없음 | 미실행 |

#### BattleType·ComparisonItem·ComparisonTable (`battle/BattleType.java`, `ComparisonItem.java`, `ComparisonTable.java`, 6.4)

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1628 | FR-093 | comparisonItems() for RANDOM / GHOST / SELF / CELEBRITY | 없음 | [기록, 평균 페이스, 가장 빠른 1km, 추월 횟수] / 같음 / [기록, 평균 페이스, 가장 빠른 1km, 가장 앞섰을 때 거리 차] / [기록, 평균 페이스, 가장 빠른 1km, 최대 거리 차] | BattleValuesTest#battleTypeAndComparison | 미실행 |
| TC-1629 | FR-101, FR-087, FR-096 | historyGroup(), startsWithCountdown(), isAgainstRecord() for 네 상수 | 없음 | 이력 분류 RANDOM / RANDOM(고스트를 랜덤으로, Q-02) / SELF / CELEBRITY; 카운트다운 시작 false / false / true / true; 기록 상대 false / true / true / true | BattleValuesTest#battleTypeAndComparison | 미실행 |
| TC-1630 | FR-093 | ComparisonTable.of(RANDOM, 본인(25:00, 5'00", 4'40"), 상대(25:30, 5'06", 가장 빠른 1km 없음, 추월 2), 본인 추월 3) | 없음 | 4줄을 위 순서로: 기록 "25:00"·"25:30", 평균 페이스 "5'00\""·"5'06\"", 가장 빠른 1km "4'40\""·"공개 안 됨", 추월 "3회"·"2회" | BattleValuesTest#battleTypeAndComparison | 미실행 |
| TC-1631 | FR-093 | 기록 59:59 / 1:00:00 / 0:05; 본인 가장 빠른 1km 없음(1km 미만 구간) | 없음 | "59:59" / "1:00:00" / "00:05"; 본인 값 "-" | 없음 | 미실행 |
| TC-1632 | FR-093 | SELF 비교표 MAX_LEAD: 본인 최대 앞섬 120m / 없음 | 없음 | "120m" / "-" | 없음 | 미실행 |
| TC-1633 | FR-093 | CELEBRITY 비교표 MAX_GAP: 본인이 최대 80m 앞섬, 최대 150m 뒤처짐 | 없음 | 본인 칸 "80m", 상대 칸 "150m"(쪽마다 그 쪽이 앞섰던 최대 거리) (조정자 결정 R3: FR-093 비교표는 항목마다 본인·상대 두 칸이므로 쪽마다 값을 적는 해석. 관련: TF-BE-14) | 없음 | 미실행 |

#### CelebrityRecord·Source (`battle/CelebrityRecord.java:71`, `Source.java`, srs 6.3 7.1)

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1634 | FR-170 | register: 출처명 "" / "   " / null; 출처명 ""·익명 닉네임 "" 동시 | 거리 42,195m, 완주 2:05:00 | 네 경우 모두 BusinessRuleException(SOURCE_REQUIRED)(출처 검사가 먼저, 7.1 순서) | RecordAndAdminServicesTest#celebrityRegister_validation | 미실행 |
| TC-1635 | FR-171 | 익명 닉네임 "" / "   "; "마라토너 K" | 출처명 "대회 공식 기록" | ANONYMOUS_NAME_REQUIRED 두 건; 등록, 저장 이름 "마라토너 K" | RecordAndAdminServicesTest#celebrityRegister_validation | 미실행 |
| TC-1636 | FR-170 | 거리 0m / 1m; 완주 0초 / −1초 / 1초 | 나머지 유효 | VALUE_INVALID / 등록; VALUE_INVALID / VALUE_INVALID / 등록 (0 이하 거부, 한쪽 열린 하한) | RecordAndAdminServicesTest#celebrityRegister_validation | 미실행 |
| TC-1637 | FR-170, FR-097 | ① 구간 [(10초, 100m), (20초, 90m)] ② 구간 없음 ③ 구간 [(4:50, 1km), …, (2:05:00, 42,195m)] | 나머지 유효 | ① SPLITS_INVALID ② 등록, isEstimated() = true, track(42,195m) = uniform(42,195m, 2:05:00) ③ isEstimated() = false, track은 구간 표 | RecordAndAdminServicesTest#celebrityRegister_validation, RecordAndAdminServicesTest#celebrityBattle | 미실행 |
| TC-1638 | FR-093, FR-100 | comparisonValues(42,195m): 구간 없음 / 구간 있음; Source("대회 공식 기록", null).label(); Source("", null) | 없음 | 가장 빠른 1km 없음(비교표 "공개 안 됨"), 기록·평균 페이스 있음 / 가장 빠른 1km 있음; "공개 기록 기준 · 대회 공식 기록"; IllegalArgumentException | BattleValuesTest#battleTypeAndComparison | 미실행 |
| TC-1639 | FR-170, FR-171 | ① 출처명 "대회 공식 기록", 출처 주소 "" ② 익명 닉네임 21자 | 나머지 유효 | ① BusinessRuleException(SOURCE_REQUIRED, "출처 필요"), 저장 0 (SRS 기준: FR-170 본문 우선, 6.3 7.1과 충돌 → /sw-srs 정정. 설계·코드 불일치: TF-BE-13) ② 21자 등록 거부·저장 0, 20자는 등록, 사유 코드는 SRS 미정 (조정자 결정 R3: 표시명 규칙 2~20자(FR-003, Q-75 가상닉네임과 같은 형식)와 "최대 N" = N 허용·N+1 거부). 설계 v0.9 6.1.6 CelebrityRecord.register가 ① 출처 주소 빔도 SOURCE_REQUIRED ② 2~20자 밖은 VALUE_INVALID로 정했다 (v0.4, 설계 v0.9 TF-BE-13 반영) | 없음 | 미실행 |

#### AxisRatios·RunimalType (`runimal/AxisRatios.java`, `RunimalType.java`, srs 3.6.1)

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1640 | FR-151 | AxisRatios: 동행 비율 −0.01 / 0 / 1 / 1.01 (나머지 0.5); 습관 축 빠짐 | 없음 | IllegalArgumentException / 생성 / 생성 / IllegalArgumentException; IllegalArgumentException (4축 모두 0~1) | 없음 | 미실행 |
| TC-1641 | FR-151 | percentOfDominant: 동행 0.78 / 0.22 / 0.5; percentOf(동행, B) of 0.78 | 없음 | 78 / 78 / 50; 22 (카드 막대 "솔로 78%") | RunimalJudgeTest#card_showsDominantPercent | 미실행 |
| TC-1642 | FR-151, FR-153 | RunimalType.of: 16개 조합 전부(예: B·B·B·B, B·A·B·A, A·B·A·A) | 없음 | 16개가 서로 다른 유형이고 srs 3.6.1 표와 같음(예: 들판의 야생마, 탐험대 수달, 출근도장 거북이), 아이콘은 동물 이름 텍스트(참새 등) | 없음 | 미실행 |
| TC-1643 | FR-151 | RunimalType.of(동행·거리·코스 3축만) | 없음 | IllegalArgumentException(4축 모두 필요) | 없음 | 미실행 |

#### MatchingService.requestMatch·cancel (`battle/MatchingService.java:54, 89`, srs 6.3 4.1)

공통 실행 조건: 인메모리 MatchQueue·BattleRepository, 가짜 MemberQueryService(티어·동의), 가짜 MemberSettingService, 고정 시계.

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1644 | FR-080 | requestMatch(1, HALF) | GOLD, 위치 동의 | BusinessRuleException(BATTLE_DISTANCE_INVALID, "대결 거리 오류"), 대기열 0 (규칙 1) | BattleServicesTest#requestMatch_decisionTable | 미실행 |
| TC-1645 | FR-080, FR-031 | requestMatch(1, KM5) | GOLD, 위치 동의 없음 | LOCATION_CONSENT_REQUIRED("위치 수집 동의 필요"), 대기열 0 (규칙 1) | BattleServicesTest#requestMatch_decisionTable | 미실행 |
| TC-1646 | FR-080, FR-022 | requestMatch(1, KM5) | UNRANKED, 위치 동의 | RANKED_ONLY("유효 러닝 3회 뒤 랜덤 대결 가능"), 대기열 0 (규칙 2) | BattleServicesTest#requestMatch_decisionTable | 미실행 |
| TC-1647 | FR-080 | requestMatch(1, KM5) | ① 이미 대기 중 ② IN_PROGRESS 대결 참가 중 | 두 경우 ALREADY_IN_BATTLE("이미 대기 중이거나 대결 중"), 대기 표는 ① 1개 그대로 ② 0개 (규칙 3) | BattleServicesTest#requestMatch_decisionTable | 미실행 |
| TC-1648 | FR-080 | requestMatch(1, KM5) | ① GOLD, 위치 동의, 대기·대결 없음 ② ①과 같고 rememberBattleDistance가 예외 | ① MatchAcceptance(대기, KM5), WAITING 표 1개, 기본 대결 거리 KM5(N-10) (규칙 4) ② 예외 전파, 대기열 0(되돌림) | BattleServicesTest#requestMatch_decisionTable | 미실행 |
| TC-1649 | FR-085 | cancel(1) | ① WAITING(QueueRemoval REMOVED) ② ASSIGNED(매칭됨, QueueRemoval ASSIGNED) ③ 대기도 대결도 없음(QueueRemoval ABSENT) | ① 대기해제, 표 삭제 ② BusinessRuleException(ALREADY_MATCHED, "이미 매칭됨"), 표 유지 ③ BusinessRuleException(ALREADY_MATCHED, "이미 매칭됨"), 상태 변화 0 (SRS 기준: srs 6.3 4.1 "대기 상태 = 대기이면 지우고 대기해제, 아니면 거부 + 이미 매칭됨". 설계 v0.10 D-83은 ABSENT를 "거부(대기 중 아님)"로만 적어 사유 이름은 SRS 문구를 따른다: R1. 세 경우 모두 MatchQueue.removeIfWaiting 1회로 정하고 findWaiting·remove는 부르지 않는다. 코드 불일치: TF-BE-15) (v0.5, D-83) | BattleServicesTest#cancel_andQueueFailure | 미실행 |

#### battle·runimal 응용 서비스 (B3)

공통 실행 조건: 인메모리 BattleRepository·LiveSessionStore·VirtualRecordRepository·CelebrityRecordRepository·RunimalProfileRepository·CourseVerdictRepository, 가짜 BattleMessenger(보낸 메시지 기록)·NotificationService·DomainEventPublisher·MemberQueryService·RunQueryService, 커밋 뒤 실행을 흉내 내는 트랜잭션 도구, 고정 Clock N = 2026-10-21 19:00. 랜덤 대결은 alice(1)·bob(2), 5km.

##### BattleStartService (`battle/BattleStartService.java:66, 132`)

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1650 | FR-086, FR-087 | announceMatch(랜덤 대결 alice·bob) | alice 5km 최고 25:00, bob 최고 기록 없음 | startAt = N + 13초(성사 + 3초 + 10초, D-17). alice에게 MatchFound(상대 bob 표시명·티어, 5km, 본인 25:00, 상대 없음), bob에게 MatchFound(상대 alice, 본인 없음, 상대 25:00), 두 사람에게 BattleStart(startAt, 트랙 없음). 메시지는 커밋 뒤에만 나가고 커밋 전 0건 | BattleServicesTest#pairsAndAnnounces | 미실행 |
| TC-1651 | FR-086, FR-095, FR-089 | announceMatch(고스트 대결, 상대 "바람처럼" 25:30) | 기록 상대 있음 | MatchFound 상대 표시명 "바람처럼"·주인 티어·상대 도착 25:30, 고스트 표시 없음(Q-02). BattleStart에 1초 간격 (경과 초, 누적 m) 트랙 포함, 좌표 필드 없음 | BattleServicesTest#expiredTicket_getsGhostFromVirtualRecords | 미실행 |
| TC-1652 | FR-086, FR-021 | announceMatch(랜덤 대결) | bob이 그 사이 탈퇴(요약 없음) | alice의 MatchFound 상대 표시명 "탈퇴한 사용자", 티어 UNRANKED, 예외 없음 | 없음 | 미실행 |
| TC-1653 | FR-087 | startDue(now): startAt − 1초 / startAt; 대결 3건 중 1건 start가 예외 | WAITING_START 랜덤 1·고스트 1 | 0건 시작 / 2건 IN_PROGRESS, 랜덤만 LiveBattleSession 저장(커밋 뒤), 고스트는 세션 없음; 실패 1건은 세지 않고 나머지 2건은 시작(한 건 실패가 다른 건을 막지 않음, 5.3) | BattleServicesTest#startDue_opensSession | 미실행 |

##### BattleClockService (`battle/BattleClockService.java`, A-11)

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1654 | FR-102, FR-107 | tick(T + 24:59) / tick(T + 25:00) | 나와의 싸움 IN_PROGRESS, startAt T, 상대 도착 25:00 | 변화 없음 / COUNTDOWN, countdownEndsAt T + 25:10, CountdownStarted(상대 표시명, 종료 시각) 1건, OPPONENT_ARRIVED 알림 1건 | BattleServicesTest#recordOpponentArrival_andPendingExpiry | 미실행 |
| TC-1655 | FR-102 | TC-1654 뒤 tick(T + 25:01) | COUNTDOWN | CountdownStarted 0건, 알림 0건(같은 도착을 두 번 알리지 않음) | BattleServicesTest#recordOpponentArrival_andPendingExpiry | 미실행 |
| TC-1656 | FR-107, FR-108, FR-188 | tick(E) | 랜덤 COUNTDOWN, countdownEndsAt E, 세션 추월 {alice 2, bob 1}, ① bob 기록 없음 ② 두 기록 모두 검증됨 | 참가자 추월 횟수 2·1 저장, 세션 삭제(커밋 뒤). ① RESULT_PENDING, BattleEnded(RESULT_PENDING) 2건, BattleJudged 0 ② FINISHED, BattleEnded(FINISHED) 2건, BattleJudged 1 | BattleServicesTest#arrivalCountdownAndClose | 미실행 |
| TC-1657 | FR-092 | tick(S + 74:59) / tick(S + 75:00) | 5km 랜덤 IN_PROGRESS, startAt S, 아무도 미도착 | 변화 없음 / RESULT_PENDING(최대 시간 = 거리 × 15분), BattleEnded 2건 | 없음 | 미실행 |
| TC-1658 | FR-108 | tick(E + 24시간 − 1초) / tick(E + 24시간) | RESULT_PENDING, 먼저 도착한 bob 기록 없음, alice 유효 | 변화 없음 / FINISHED, alice 승, corrected = false, BattleJudged 1 (srs 4.5 보류 만료). 규칙 3이어도 24시간 만료만 정정이 아니다, BATTLE_CORRECTED 0 (v0.4, D-63 (3) 예외) | BattleServicesTest#recordOpponentArrival_andPendingExpiry | 미실행 |
| TC-1659 | FR-087 | resumeLiveSessions(N) | 세션 4개: IN_PROGRESS·COUNTDOWN·FINISHED 대결, 없는 대결 | 반환 2, FINISHED·없는 대결의 세션 삭제, 남은 두 대결은 다음 tick부터 이어 감(D-55) | BattleServicesTest#resume_dropsFinishedSessions | 미실행 |

##### LiveBattleService (`battle/LiveBattleService.java`, SQ-04)

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1660 | FR-088 | onProgress(alice, 대결 10, 1,000m) ; onProgress(회원 9, 대결 10, …); onProgress(alice, 대결 11, …) | 대결 10 세션 있음, 대결 11은 세션 없음(이미 끝남) | bob에게 OpponentProgress 1건, 세션 저장 1; 회원 9·대결 11은 메시지 0·저장 0(무시) | BattleServicesTest#relayAndOvertake | 미실행 |
| TC-1661 | FR-102, FR-107 | alice 5,000m 진행값 → bob 5,000m 진행값 | IN_PROGRESS | alice 도착: Battle COUNTDOWN, CountdownStarted(도착자 alice) 2건(커밋 뒤), bob에게 OPPONENT_ARRIVED 알림 1건. bob 도착: 도착만 기록, CountdownStarted·알림 0건 | BattleServicesTest#arrivalCountdownAndClose | 미실행 |
| TC-1662 | FR-090 | detectTimeouts(alice 마지막 진행값 + 3.001초) | ① 대결 IN_PROGRESS ② 대결이 그 사이 FINISHED | ① Battle 끊김 기록 1건(alice), bob에게 CHECKING 1건 ② 세션 삭제, 메시지 0 | BattleServicesTest#timeoutAndReconnect | 미실행 |
| TC-1663 | FR-091, FR-090 | ① 끊긴 alice의 새 진행값 ② alice·bob 진행값을 두 스레드에서 동시에 onProgress | ① alice 끊김 표시 중 ② IN_PROGRESS | ① 끊김 기록의 복구 시각 = 수신 시각, bob에게 CONNECTED ② 두 진행값 모두 세션에 반영, 유실 0(대결별 잠금) | BattleServicesTest#timeoutAndReconnect | 미실행 |

##### BattleResultService (`battle/BattleResultService.java`, SQ-05)

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1664 | FR-092 | onRunSaved: ① battleId 없음 ② 없는 대결 99 ③ 회원 9(비참가자)의 기록 | 대결 10 RESULT_PENDING | 세 경우 모두 판정 0, BattleJudged 0, 대결 상태 변화 0 | BattleServicesTest#pendingView_andIgnoredRecord | 미실행 |
| TC-1665 | FR-092 | onRunSaved(alice, valid, timeToReach 25:00) | bob 검증됨(유효, 25:05), RESULT_PENDING | alice 승·bob 패, BattleJudged 1, 정정 알림 0 | BattleServicesTest#arrivalCountdownAndClose | 미실행 |
| TC-1666 | FR-092, FR-047 | onRunSaved(alice, valid = true, timeToReach 없음(5km 미도달)); onRunSaved(alice, valid = false, 25:00) | RESULT_PENDING | 두 경우 alice 기록검증결과 = 무효(유효 러닝 AND 대결 거리 도달, srs 4.5) | 없음 | 미실행 |
| TC-1667 | FR-103 | onRunSaved로 판정이 바뀜(FINISHED alice 승 → alice 무효) | FINISHED | BattleJudged 1(corrected = true), BATTLE_CORRECTED 알림이 참가자마다 1건 | BattleServicesTest#correction_notifies | 미실행 |
| TC-1668 | FR-094 | forfeit(alice, 10); forfeit(회원 9, 10); forfeit(alice, 99) | IN_PROGRESS | alice 패·bob 승, bob에게 BattleEnded(OPPONENT_FORFEITED) 1건(커밋 뒤), BattleJudged 1; 회원 9는 PermissionDeniedException(403, 설계 6.1 예외 규칙), 상태 변화 0; 없는 대결은 BusinessRuleException(BATTLE_NOT_FOUND) | BattleServicesTest#forfeit | 미실행 |
| TC-1669 | FR-108 | result(alice, 10); result(bob, 10) | RESULT_PENDING, alice 기록 없음, bob 기록 있음 | 둘 다 보류 표시. 재시도 안내는 alice만 true, bob false(기록이 없는 참가자에게만) | BattleServicesTest#pendingView_andIgnoredRecord | 미실행 |
| TC-1670 | FR-093 | result(alice, 10) | FINISHED alice 승, 이번 주(월 0시 이후) 대결 3건(승 2·패 1), 지난주 일요일 23:59 대결 1건 | 판정 WIN, 본인·상대 도달 시간, 정정 여부, RANDOM 비교표 4줄, 이번 주 전적 2승 1패(지난주 대결 제외) | BattleServicesTest#pendingView_andIgnoredRecord | 미실행 |

##### RecordBattleService (`battle/RecordBattleService.java`, srs 6.3 4.6)

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1671 | FR-096 | selfCandidates(alice) | 5km 최고 25:00(2026-10-14)만 있음 | 후보 4개(3·5·10km·하프, 1km 없음). 5km는 "10/14의 나"·25:00·날짜, 나머지 셋은 "아직 기록이 없어요" | RecordAndAdminServicesTest#selfCandidates | 미실행 |
| TC-1672 | FR-096, FR-031 | startSelfBattle(alice, KM5) | 위치 동의 없음, 5km 최고 있음 | BusinessRuleException(LOCATION_CONSENT_REQUIRED, "위치 수집 동의 필요"), 대결 0 (Q-74) | RecordAndAdminServicesTest#startSelfBattle_andMarkStarted | 미실행 |
| TC-1673 | FR-096 | startSelfBattle(alice, KM10) | 위치 동의, 10km 최고 없음 | BusinessRuleException(NO_RECORD, "아직 기록이 없어요"), 대결 0 | RecordAndAdminServicesTest#startSelfBattle_andMarkStarted | 미실행 |
| TC-1674 | FR-096, FR-089 | startSelfBattle(alice, KM5) | 위치 동의, 5km 최고 25:00(2026-10-14), alice GOLD | SELF 대결 IN_PROGRESS(startAt 없음), 상대 표시명 "10/14의 나", 티어 GOLD, 트랙 = 그 기록의 5km까지 곡선(좌표 없음), 저장 1 | RecordAndAdminServicesTest#startSelfBattle_andMarkStarted | 미실행 |
| TC-1675 | FR-096, FR-031 | startSelfBattle(alice, KM1) | ① 위치 동의 없음 ② 위치 동의 있음 | ① BusinessRuleException(LOCATION_CONSENT_REQUIRED). srs 4.6은 위치 동의를 먼저 본다(SRS 기준, 설계·코드 불일치: TF-BE-17) ② BusinessRuleException(BATTLE_DISTANCE_INVALID), 대결 0 (나와의 싸움 거리는 3·5·10km·하프) | 없음 | 미실행 |
| TC-1676 | FR-097, FR-100 | celebrityCandidates(alice, 필터 없음 / HALF); startCelebrityBattle(alice, 5) / (alice, 99) | 유명인 기록 5(풀, 구간 없음)·6(하프), 위치 동의 | 2건(각 출처 "공개 기록 기준 · …"·추정 여부) / 1건; CELEBRITY 대결(거리 42,195m, 표시명 익명 닉네임, 출처, 추정 = true) / BusinessRuleException(CELEBRITY_REMOVED, "고를 수 없는 기록"), 대결 0 | RecordAndAdminServicesTest#celebrityBattle | 미실행 |
| TC-1677 | FR-096, FR-097 | markStarted(alice, SELF 대결) 두 번; markStarted(회원 9, 같은 대결); markStarted(alice, 랜덤 대결) | 시작 보고 전 | 첫 호출 startAt = N, 둘째는 멱등(startAt 그대로); PermissionDeniedException(403); IllegalArgumentException(400, 시작 보고는 기록 대결만) | RecordAndAdminServicesTest#startSelfBattle_andMarkStarted | 미실행 |

##### VirtualRecordService·CelebrityRecordService·BattleHistoryService

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1678 | FR-104 | ensureGenerated() | 저장된 가상 기록 100 / 99 / 0 / 101개 | 재생성 0 / 1 / 1 / 1회, 재생성 뒤 100개 | RecordAndAdminServicesTest#startupListener, BattleThreadIntegrationTest#t14_startup | 미실행 |
| TC-1679 | FR-104, FR-174 | onOperationSettingChanged(TIER_BOUNDARY) / (VIRTUAL_RECORD_RULE) / (LLM_DAILY_LIMIT) | 가상 기록 100개 | 재생성 1 / 1 / 0회 (D-45) | RecordAndAdminServicesTest#virtualRecords_regenerate, BattleThreadIntegrationTest#t11_settingChange_regeneratesVirtualRecords | 미실행 |
| TC-1680 | FR-104 | regenerate() | ① 회원 닉네임 "바람같은치타" 존재 ② VirtualRecordFactory가 IllegalStateException | ① 새 100개 중 "바람같은치타" 0개, 기존 전부 교체 ② 예외 전파, 기존 100개 그대로(한 트랜잭션 교체) | RecordAndAdminServicesTest#virtualRecords_regenerate | 미실행 |
| TC-1681 | FR-170, FR-176 | register("KAKAO:op1", 유효 입력 "마라토너 K") | 없음 | 저장 1, 감사 CELEBRITY_REGISTER 1(행위자 "KAKAO:op1", 역할 OPERATOR, 대상 = 새 ID, after "마라토너 K"), 결과 REGISTERED | RecordAndAdminServicesTest#celebrityService_audits | 미실행 |
| TC-1682 | FR-170 | register("KAKAO:op1", 출처명 빈 입력) | 없음 | BusinessRuleException(SOURCE_REQUIRED), 저장 0, 감사 0 | RecordAndAdminServicesTest#celebrityService_audits | 미실행 |
| TC-1683 | FR-172, FR-176 | delete("KAKAO:op1", 5, "권리자 요청"); delete(…, 99, …) | 기록 5로 끝난 대결 1건 | 행 삭제, 감사 CELEBRITY_DELETE 1(before "마라토너 K", after "권리자 요청"), 끝난 대결 상대 표시명 "마라토너 K" 유지; 99는 BusinessRuleException(RECORD_NOT_FOUND, "기록 없음"), 감사 0 | RecordAndAdminServicesTest#celebrityService_audits | 미실행 |
| TC-1684 | FR-101 | history(alice, 이번 달, 필터 없음) | 대결 5건: 랜덤 승·고스트 패·SELF 승(신기록)·유명인 무승부(FINISHED)·랜덤 RESULT_PENDING | 5건 생성일시 최신순, 통계 대결 수 5·승 2·패 1·신기록 1, 보류 항목은 판정 없음 | RecordAndAdminServicesTest#history | 미실행 |
| TC-1685 | FR-101 | 필터 RANDOM / SELF / CELEBRITY | 같음 | 3건(랜덤 2 + 고스트 1, 고스트도 종류 RANDOM으로 표시, Q-02) / 1건 / 1건 | RecordAndAdminServicesTest#history | 미실행 |
| TC-1686 | FR-101 | 필터 GHOST(목록 밖 값) | 같음 | 요청 거부(IllegalArgumentException, 400) (조정자 결정 R3: 열거형 목록 밖 필터 값은 400. 설계·코드 불일치: TF-BE-18) | 없음 | 미실행 |
| TC-1687 | FR-101, FR-172 | history(alice) 상대 표시명 | 랜덤 상대 bob 탈퇴, 고스트 상대 "바람처럼", 유명인 기록 삭제됨, SELF 패배 | "탈퇴한 사용자" / "바람처럼" / 대결 당시 익명 닉네임 / SELF 신기록 false(이긴 대결만 신기록) | RecordAndAdminServicesTest#history | 미실행 |

##### RunimalService·CourseSimilarityService·RunimalQuery

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1688 | FR-047, FR-152 | onRunSaved(valid = false, 순번 0) | 프로필 순번 2 | 코스 판정 0, 프로필 변화 0, 판정 0 | RunimalServicesTest#provisionalThenConfirmed | 미실행 |
| TC-1689 | FR-152, FR-166 | onRunSaved(valid, 순번 3, 경로 있음) | 프로필 순번 2 | CourseSimilarityService.judge 1회 → PROVISIONAL → judgeProvisional(태그 매개변수 없음) → 임시 유형 저장, 순번 3. MemberQueryService.tagsOf 호출 0회(연산 삭제) (v0.6, CR-04, D-91) | RunimalServicesTest#provisionalThenConfirmed | 미실행 |
| TC-1690 | FR-153 | onRunSaved(순번 5) / (순번 10) | 임시 유형 있음 | 순번 5: 판정 0, 순번만 5 / 순번 10: judgeConfirmed → 확정 유형 저장 | RunimalServicesTest#provisionalThenConfirmed | 미실행 |
| TC-1691 | FR-152, FR-153 | onRunSaved(순번 4) ; 같은 순번 10 두 번 | 프로필 순번 2(3회째 처리 유실) ; 순번 9 | 순번 4에서 임시 판정(D-40) ; 확정 판정 1회만 | RunimalServicesTest#skippedThird_isRecoveredOnFourth | 미실행 |
| TC-1692 | FR-166, FR-026 | onRunSaved(valid, 순번 4, 경로 없음(파기)) | 순번 3 | 코스 판정 0, 순번 4 반영 | 없음 | 미실행 |
| TC-1693 | FR-151, FR-154, FR-168 | status(alice); shareCard(bob) | alice 순번 4·임시 유형 / bob 순번 1 | alice: 임시 유형, 남은 횟수 6("다음 확정까지 6회"), 임시 카드, 16유형 목록 / bob: BusinessRuleException(NO_TYPE) | RunimalServicesTest#noType_andQuery | 미실행 |
| TC-1694 | FR-166, FR-026 | judge(alice, 기록 r) | r의 경로 파기(빈 목록) | CourseVerdict 0 | RunimalServicesTest#courseVerdict_savedOnlyWithPreviousRoutes | 미실행 |
| TC-1695 | FR-166 | judge(alice, r) | ① 이전 유효 기록 전부 경로 파기 ② runSamples가 r 포함 31개 | ① 비교 대상 없음 → CourseVerdict 0 ② r을 뺀 최근 30개만 비교(31번째 이전 기록은 보지 않음) | RunimalServicesTest#courseVerdict_savedOnlyWithPreviousRoutes | 미실행 |
| TC-1696 | FR-166 | judge(alice, r) | 이전 경로 중 같은 길 있음 / 없음 | CourseVerdict(r, alice, true, N) 1건 / (r, alice, false, N) 1건 | RunimalServicesTest#courseVerdict_savedOnlyWithPreviousRoutes | 미실행 |
| TC-1697 | FR-005, FR-151 | typesOf([]) ; typesOf([1, 2, 3]) | 1: 임시 A·확정 B, 2: 임시만, 3: 유형 없음 | 빈 맵, 저장소 조회 0 ; {1: B(확정 우선), 2: 임시}, 3은 맵에 없음 | RunimalServicesTest#noType_andQuery | 미실행 |
| TC-1698 | FR-021 | 회원 1 탈퇴(MemberWithdrawn) 뒤 typesOf([1]) | 회원 1 확정 유형 | 빈 맵(프로필·코스 판정 삭제) | RunimalServicesTest#withdrawal_deletes | 미실행 |

### 2.1.7 구현 후 작성

B4·B5는 통합 브랜치(18442f7)에 없다. 클래스 명세가 있으므로 구현이 들어오면 아래 규칙으로 블랙박스(필요하면 화이트박스) 케이스를 만든다. 남은 번호(TC-1077~TC-1080, TC-1194~TC-1199, TC-1699)로는 모자라므로 B4·B5가 들어오면 조정자에게 새 대역을 받는다.

| 클래스 | 단계 | 설계 절 | 검사할 규칙 |
|--------|------|---------|-------------|
| Crew, CrewName, InviteCode | B4 | 6.1.7 | 정원 10명(9·10·11명째 가입), 크루명 2~20자·중복 거부, 초대 코드 형식 |
| CrewMembership, CrewPermissionPolicy, JoinEligibility | B4 | 6.1.7 | 1인 1크루, 퇴장 뒤 24시간 경계, 크루장·크루원 권한 표. 운영진 열을 넣은 권한표 전체와 운영진 지정은 2.1.10.6·2.1.10.7 (v0.6) |
| JoinRequest | B4 | 6.1.7, 상태도 2.4.4 | 신청·승인·거절·취소·만료 전이와 금지 전이 |
| CrewRunPost, CrewRunParticipation, CrewRunLiveSession | B4 | 6.1.7, A-13, 상태도 2.4.5·2.4.6 | 대기실 개방 30분 전, 최소 준비 2명, depart·close(거리 × 15분), 준비 상태 전이 |
| WeeklyRace, TagOverlapRecommender | B4 | 6.1.7, A-12 | 주간 합산 이중 합산 0, 기여율 소수 버림. 공동 목표·달성 판정은 2.1.10.13. 태그 일치 수·상위 5개(TagOverlapRecommender)는 폐기(v0.6, CR-04: FR-118 크루 추천 삭제, D-91) |
| RewardWallet | B4 | 6.1.8, A-17 | ⌊km × 10⌋ 적립, 일일 상한 200, 같은 사건 이중 적립 0, 잔액 부족 구매 거부. 집계 칼럼(D-109)은 2.1.10.14 (v0.6) |
| Avatar, AppearanceCalculator, BodyShape | B4 | 6.1.8, A-16, 상태도 2.4.12 | 체형 4단계와 4일 넘게 쉬면 하루 한 단계 하강(1단계 아래 없음), 피부색 최다 시간대·동률이면 최근, 셀카 하루 1회. 랜드마크 배경 해금·선택은 2.1.10.15 (v0.6) |
| RunningGoal, WeeklyGoal, Streak, Grass | B5 | 6.1.4, A-20 | 주간 목표 달성률, 보충 상한 10km, 스트릭 연속 일수 경계(자정) |
| Report, LlmCallQuota, 코멘트 생성기 3개 | B5 | 6.1.4, 상태도 2.4.15 | 회원 하루 1회·전체 200 한도, 3초 초과 대체 문구, 같은 사건 재수신 유지 |
| RankingSnapshot | B5 | 6.1.4, A-19 | 같은 거리 순위 처리, 다음 순위까지 거리, 크루 1인 평균 |
| ProfileDisclosure | B5 | 6.1.5 | SELF·PUBLIC·PRIVATE·WITHDRAWN마다 남길 항목 |
| OperatorAuthorizer, OperatorId | B5 | 6.1.12 | 화이트리스트 밖·토큰 위조·회원 JWT 거부(403) |
| batch.* 작업 8개 중 B4·B5 소속 | B4·B5 | 5.1.1 | 실행 시각과 처리 수 반환 |

B3에 통합된 클래스는 모두 2.1.2·2.1.6에 케이스를 썼다. v0.4에서 설계 결정이 시그니처·경계를 정한 crew·operator 연산(D-65·D-66·D-69·D-74·D-76)은 2.1.8에 먼저 썼고, 나머지 B4·B5 클래스는 이 표대로 구현 뒤 쓴다.

### 2.1.8 v0.4 설계 결정 단위 케이스 (설계 v0.9)

설계 v0.8·v0.9가 서버 클래스의 연산·경계를 바꾼 곳마다 단위 케이스를 더했다. 기대 결과는 05-02 결정 표와 6.1 클래스 명세(v0.8 표시 문장)에서 가져왔다. 크루·운영자 클래스는 18442f7에 없지만(B4·B5) 결정이 연산 시그니처와 경계를 정했으므로 2.1.7의 "구현 후 작성"과 별개로 이 결정들만 먼저 적는다. 대응 시험은 18442f7 시험 폴더를 grep해 확인된 것만 적었다. 공통 실행 조건은 2.1.6과 같다(인메모리 저장소, 가짜 Messenger·NotificationService, 커밋 뒤 실행을 흉내 내는 트랜잭션 도구, 고정 Clock N = 2026-10-21 19:00).

#### D-65 서버 메시지 (battle.live·crew.live, 5.7.2 v0.8)

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1713 | FR-088, NFR-005 | LiveBattleSession.onProgress(alice, ProgressReport(1,000m, 5:00, sentAt N − 0.4초), N) | 5km 랜덤 세션 | bob에게 OpponentProgress 1건, sentAt = N − 0.4초(받은 시각 N이 아니라 보낸 기기의 생성 시각 그대로), latest[alice]에 sentAt·receivedAt이 따로 남음. sentAt이 null인 진행값은 IllegalArgumentException(ProgressReport 필수 항목) (v0.4, D-65 (1), A-09) | 없음 | 미실행 |
| TC-1714 | FR-083, FR-095 | new MatchFailed(MATCH_UNAVAILABLE).type(); 구성요소 이름 | battle.live | type() = "MatchFailed", 구성요소는 reason 하나이고 그 형식은 battle.live의 enum MatchFailReason(common.ReasonCode가 아님), BattleMessage 구현, 위치·회원 ID 항목 0 (v0.4, D-65 (3), 5.7.2) (v0.5, D-85) | 없음 | 미실행 |
| TC-1715 | FR-149, FR-190 | CrewLiveSession.relayFor(b) / relayFor(c) | 참가자 a·b·c, b와 c의 표시명이 둘 다 "러너"(동명이인), 순위 a 1위·c 2위·b 3위 | relayFor(b): 줄 3개가 순위 순서, mine = 참인 줄은 b의 줄 하나(3번째 줄). relayFor(c): mine = 참은 2번째 줄 하나. 어느 줄에도 회원 ID 없음 (v0.4, D-65 (2), 6.1.7 relayFor) | 없음 | 미실행 |
| TC-1716 | FR-149, NFR-021, NFR-039 | CrewLiveSession.onProgress(a, ProgressReport(3,120m, 18:00, sentAt S), N) | 참가자 10명, 그중 d는 아직 진행값 없음 | 반환 CrewRunRelay 10건(받는 사람마다 하나), 각 건에서 mine = 참인 줄 정확히 1개, a 줄 sentAt = S, d 줄 sentAt = null, 위도·경도 항목 0 (v0.4, D-65 (1)·(2)) | 없음 | 미실행 |
| TC-1717 | FR-191, FR-116 | CrewRunEnded(postId 77, FINISHED).type(); CrewRunEnded(77, DELETED); reason 없이 생성 | crew.live | "CrewRunEnded", reason FINISHED / DELETED(crew.live 안의 enum, common.ReasonCode가 아님); reason null은 IllegalArgumentException (v0.4, D-65 (4)) (v0.5, D-85) | 없음 | 미실행 |

#### D-66 진행 중 크루런 삭제 (crew, 6.1.7)

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 | 대응 시험 | 결과 |
|----|---------|------|-----------|-----------|-----------|------|
| TC-1718 | FR-116, FR-149 | CrewRunService.endByDeletion(모집글 77) | 77 RUNNING, 출발 참가자 5명, 세션 있음, 참가자 2명은 이미 기록 저장(linkedRecordId 있음) | 커밋 뒤 CrewRunEnded(77, DELETED) 5건(출발 참가자마다), 커밋 전 0건, CrewLiveSessionStore.delete(77) 1회, RunRecord 삭제 0(저장된 개인 기록 그대로), CrewRunCancelled 0 (v0.4, D-66) | 없음 | 미실행 |
| TC-1719 | FR-116, FR-147 | endByDeletion(모집글 78) | ① LOBBY_OPEN, 대기실 입장 3명 ② RECRUITING(대기실 열기 전) ③ ENDED | ① 커밋 뒤 CrewRunCancelled(78, "크루런이 삭제되었어요") 3건, CrewRunEnded 0 ② ③ 메시지 0건. 세 경우 모두 세션 delete 1회 (v0.4, D-66) | 없음 | 미실행 |
| TC-1720 | FR-116, FR-149 | 삭제 뒤 CrewRunLiveService.onProgress(a, 77, 진행값, N + 1초) | TC-1718 뒤(세션 없음) | 진행값 버림, CrewRunRelay 0건, 예외 0 (v0.4, D-66 "삭제 뒤 중계 0건") | 없음 | 미실행 |
| TC-1721 | FR-116, FR-146 | CrewRunService.onRunSaved(RunRecordSaved(crewRunPostId 77)) | 77 삭제됨 | linkRun 0, recordArrival 0, 예외 0. 기록은 running에 그대로 있고 crewRunPostId도 남는다(가리키는 글이 없어 연결 없음, D-02) (v0.4, D-66) | 없음 | 미실행 |
| TC-1722 | FR-116 | ① CrewService.delete(크루장, 크루 7) ② CrewPostService.delete(크루장, 모집글 77) | ① 크루 7에 RUNNING 크루런 1건·LOBBY_OPEN 1건 ② 77 RUNNING | ① 거부 없이 삭제, endByDeletion 2회(글마다), 크루원 알림 CREW_DELETED, 감사(크루삭제) 1 ② 삭제, endByDeletion 1회. 두 경우 모두 진행 중 크루런 때문에 거부하지 않는다(FR-116 v0.9) (v0.4, D-66) | 없음 | 미실행 |

