# 병합 대기: fix/S15P31A207-275-poll-reminder-clock

> 작성: 2026-11-09 | 작성자: 이성현 | 스킬: 구현(시험 결함 수정)

CI 서버 시험에서 `java.lang.NullPointerException: Cannot invoke java.time.ZoneId.getRules() because the return value of java.time.Clock.getZone() is null at com.a207.runkk.batch.crew.PollReminderJob.run(PollReminderJob.java:29)`가 ERROR 로그로 찍혀, 출력에 ERROR가 없는지 보는 시험(OperatorSeedLogTest 등)을 흔들었다. 운영 코드는 고치지 않았다(운영 Clock 빈은 `Clock.system(ServerZone.ID)`라 시간대가 늘 있다).

## 원인

- `AiPrivacyWithdrawalIntegrationTest`가 Clock 빈을 `@MockitoBean Clock clock`(Mockito 목)으로 바꾼 별도 시험 문맥을 띄운다. `getZone()`·`instant()`는 `@BeforeEach`에서만 정했다.
- 시험 프로필은 대결 시계, 크루런 1초 시계, 런바디 진행만 끄고, 1분 투표 마감 알림(`CrewSchedulingConfig`의 `addFixedDelayTask(pollReminderJob::run, 1m)`)은 켜 둔다. 고정 지연 작업은 문맥이 뜨자마자 한 번 돈다.
- 그래서 (1) 문맥이 뜰 때 아직 정하지 않은 목, (2) 각 시험이 끝나 목이 초기화(MockReset.AFTER)된 뒤 문맥 캐시에 남아 1분마다 도는 동안, `LocalDateTime.now(clock)`이 `getZone()` null로 NPE를 내고 `PollReminderJob`이 `poll_reminder_failed`를 ERROR로 남긴다. 이 로그가 같은 JVM에서 그 시각에 도는 다른 시험의 출력 캡처에 섞인다.

## 같은 문제를 가진 다른 작업

- 목 Clock 문맥에서 실제로 도는 작업 가운데 고정 지연은 `PollReminderJob` 하나다. `AbandonedBattleJob`(1분), `FriendInviteExpiryJob`(5초), `BattleTickJob`(1초)도 같은 `LocalDateTime.now(clock)` 모양이지만 시험 프로필이 `battle-tick-enabled: false`로 대결 스케줄 설정 전체를 끈다. `RunBodyPollJob`은 `runbody-poll-enabled: false`, `CrewRunTickJob`은 `crew-tick-enabled: false`로 꺼져 있다.
- 시각 고정 작업(크루 0시 30분 재합산·0시 10분 기록 연결·3시 가입 신청 만료, 리워드·티어 0시 10분 보정, 알림 0시 정리, 탈퇴·폐기 3시, 아바타 외형·배경, 분석 계획·랭킹, 감사 기록 정리, Runimal 비율 등 `LocalDateTime.now(clock)`을 쓰는 17개. 배치고사 보정은 대결 설정과 함께 꺼짐)은 시험 프로필에서도 켜져 있어, 시험이 그 시각에 걸리면 같은 목 문맥에서 같은 NPE를 낸다. 이번 수정으로 함께 막힌다.

## 고친 것

- `backend/src/test/java/com/a207/runkk/aiprivacy/AiPrivacyWithdrawalIntegrationTest.java`: `@MockitoBean Clock`을 `@TestBean Clock` + 시간대(Asia/Seoul)를 늘 가진 `MovableClock`(시각만 옮김)으로 바꿨다. 시험 시각(2026-11-04 12:00 기준)과 시험 내용은 그대로다.
- `backend/src/test/java/com/a207/runkk/TestClockRuleTest.java`(새 시험): 시험 클래스에 `@MockitoBean` 붙은 `Clock` 필드가 없어야 한다는 ArchUnit 규칙. 수정 전에 `AiPrivacyWithdrawalIntegrationTest.clock` 1건으로 실패하는 것을 확인했다(재현).
- 시험 프로필에서 투표 마감 알림 스케줄을 끄는 방법은 쓰지 않았다. 끄려면 운영 코드 `CrewSchedulingConfig`에 켜기 설정을 더해야 하고, 시각 고정 작업이 같은 목 Clock으로 깨지는 문제는 그대로 남기 때문이다.

## 새 임시 번호
| 임시 번호 | 종류 | 한 줄 설명 | 위치 |
|-----------|------|------------|------|
| [임시:TD-성-1] | 결함(주요, 수정) | 시험 문맥 목 Clock(시간대 null)으로 PollReminderJob이 NPE·ERROR 로그를 내 출력 검사 시험이 흔들림 | 이 파일 |

## 공용 파일 반영
| 파일 | 넣을 내용 |
|------|-----------|
| test/02-01 계열(단위-백엔드) 또는 다음 판 시험 조각 | [임시:TD-성-1] 결함 행과 회귀 시험 `TestClockRuleTest`(시험 문맥 Clock 규칙) |
| test.md | 머리 헤더 입력란에 「(다음 판 변경, 2026-11-09 브랜치 fix/S15P31A207-275-poll-reminder-clock) 시험 문맥 목 Clock 결함」 |

## 다른 담당에게 요청
- 박재영: `AiPrivacyWithdrawalIntegrationTest`(탈퇴 ai 파기 시험)의 Clock 교체를 MR에서 확인한다. 문서 조각 test/ai-자료파기-박.md는 고칠 것이 없다.

## 검증

- backend `./gradlew build` 통과(spotless와 전체 시험 포함, 2026-11-09).
- 전체 시험 출력에 `Clock.getZone()` NPE와 `poll_reminder_failed` 0줄.
- TestClockRuleTest 1건, AiPrivacyWithdrawalIntegrationTest 6건, OperatorSeedLogTest 3건 합격.
