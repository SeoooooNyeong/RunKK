# 병합 대기: fix/S15P31A207-300-flaky-tests

> 작성: 2026-11-15 | 작성자: 이성현(PM 지시로 박재영 담당 요청을 대신 처리) | 스킬: 없음(흔들리는 시험 원인 수정, sqa 위험 상태 갱신 요청)

흔들리는 시험 두 개의 원인을 찾아 고치고, CI가 느릴 때 함께 실패한 대결 고스트 측정 시험을 안정화했다. 위험 번호는 요청 문장의 R-104(= [임시:R-성-29], `AiCoachControllerTest`)와 R-095(= [임시:R-성-20], `test_queue_wait_counts_toward_timeout`)다. 두 번호는 아직 plan 9절 표(R-085까지)에 없으므로 병합 때 공용 담당이 정식 번호와 맞춘다.

## 1. backend `AiCoachControllerTest` (R-104, [임시:R-성-29]) — 기능 결함, 해소

- 증상: 전체 빌드에서 가끔 `eligible()`의 `PUT /api/v1/me/consents/LLM_TRANSFER?version=2`가 400. 이번에 전체 빌드 1회에서 재현했다(2026-11-15, 이번에는 `authenticatesActualHttpStream…`의 첫 줄, 응답 400 `BAD_REQUEST`, 처리된 예외 `IllegalArgumentException`).
- 원인: `common.Timestamps`가 받은 `now`를 100ns 자리까지 그대로 들고, DB 열(timestamp(6), H2·PostgreSQL)은 마이크로초로 **반올림**해 저장한다. 이 PC의 Java 벽시계는 약 1ms(기본 15.6ms) 눈금으로만 바뀌고 100ns 자리까지 준다(측정: 2초 동안 3,600만 번 읽어 바뀐 횟수 약 2,000번, 값의 90%에 1µs 미만 자리). 가입과 다음 요청이 한 눈금 안에 들면 두 요청의 `now`가 같은데, 다시 읽은 생성 시각은 반올림으로 그보다 최대 0.5µs 늦다. 그래서 `touched(now)`가 「수정 시각은 생성 시각 이후여야 한다」로 `IllegalArgumentException` → 400. 시험이 가입 직후 곧바로 동의를 바꾸고(MockMvc라 수 ms 안), 순서·부하에 따라 눈금이 겹치는지가 달라져 흔들렸다.
- 수정(TDD): `Timestamps.createdAt`·`touched`가 시각을 열 정밀도(마이크로초)로 잘라 둔다. 같은 방식은 이미 `FriendInvite`·`RunBodyJob`·`SelfieJob`·`AiPurgeRequest`가 쓴다.
  - 새 시험 `JpaMemberRepositoryIntegrationTest#consentInSameClockTickAsSignUp_isAccepted`(PostgreSQL): 100ns 자리가 있는 같은 시각으로 가입 → 다른 트랜잭션에서 동의. 수정 전 실패(`수정 시각은 생성 시각 이후여야 한다: …47.123457, …47.123456700`), 수정 뒤 통과.
  - 새 시험 `SmallValuesTest#timestamps_keepDbPrecisionOfMicroseconds`: 수정 전 실패, 수정 뒤 통과.
- 운영 영향: 리눅스 서버도 PostgreSQL이 반올림하므로 같은 회원의 두 요청이 1µs 안에 겹치면 같은 400이 날 수 있었다(드묾). 수정 뒤에는 생기지 않는다.

## 2. ai `tests/writer/test_service.py::test_queue_wait_counts_toward_timeout` (R-095, [임시:R-성-20]) — 시험 결함, 해소

- 원인: 두 요청을 실제 지연으로 겨루게 했다(0.08초 응답 두 건 대 0.1초 제한). 앞 요청의 여유가 20ms뿐이라 Windows 타이머 해상도(약 15.6ms)와 부하에 따라 앞 요청까지 제한을 넘겨 `assert first.feedback.source == "writer"`가 깨졌다. 수정 전 15번 가운데 8번 실패.
- 수정: 시험이 writer 차례(`service._turn`)를 직접 쥔 채 요청을 보낸다. 기다린 시간이 제한(0.5초)에 들어가면 템플릿으로 끝나고 writer를 부르지 않는다(`client.calls == []`). 들어가지 않으면 요청이 끝나지 않아 바깥 5초 감시에 걸려 실패한다. 차례가 비면 같은 제한 안에서 writer 문장이 된다는 대조도 넣었다. 기능 코드는 그대로다.
- 확인: 제한을 차례 안쪽으로 옮긴 틀린 서비스로 바꾸면 새 시험이 실패, 되돌리면 통과. 반복 20번 실패 0.

## 3. backend `BattleThreadIntegrationTest.t3_ghostThread` — 측정 안정화

- 판단: 같은 성격(실행 시간 단언)이다. 3초 예산(TC-2147)은 기능 성능 요구라 단언은 그대로 두었다. 측정에는 문맥을 띄운 뒤 첫 고스트 매칭에만 드는 비용(클래스 적재·JIT·쿼리 계획·연결)이 섞여 있어, CI 러너가 느리면 이것만으로 예산을 넘길 수 있었다.
- 수정: 러닝 기록이 없는 골드 회원으로 고스트 매칭을 한 번 먼저 돌린 뒤(이 회원은 뒤 매칭의 후보가 아니다) 기존 측정을 한다. 측정 구간과 예산, 나머지 단언은 바꾸지 않았다. 로컬 측정 144ms → 48~82ms.
- 남는 한계: TC-2147은 회원 1,000명·기록 10,000건 조건인데 시험은 2명으로 잰다(이전과 같음).

## 4. 함께 찾은 시험 환경 문제(이번에 고치지 않음)

- 내장 Redis 수명: `bootstrap.EmbeddedRedisConfig`는 처음 띄운 시험 문맥이 Redis를 소유하고, 그 문맥이 문맥 캐시(최대 32개)에서 밀려 닫히면 Redis를 멈춘다. 그 뒤 Redis를 쓰는 시험은 명령마다 60초씩 기다리다 실패해 빌드가 수십 분 늘어난다. 문맥 캐시 크기 1로 `web.chat` + `web.coach`를 돌리면 매번 재현된다. 실패한 시험을 먼저 돌리는 Gradle 순서나 같은 PC의 다른 작업 트리가 같은 포트(6371)를 쓸 때 실제로 일어났다(이번 확인 중 2회). CI 시간 초과의 후보이기도 하다. 고치려면 시험 실행 전체에서 Redis를 한 번 띄우고 JVM 종료 때 멈추게 해야 한다.

## 5. 검증(2026-11-15, JDK 21)

| 명령 | 결과 |
|------|------|
| backend `./gradlew spotlessApply` 뒤 `./gradlew build` | 통과, 2,402건 실패 0(새 시험 2 포함) |
| backend 대상 반복(`AiCoachControllerTest`, `BattleThreadIntegrationTest`, `JpaMemberRepositoryIntegrationTest`, `SmallValuesTest`) 5회 | 5회 모두 실패 0, 고스트 측정 46~54ms |
| backend 수정 전 전체 실행(재현 시도) | 자연 순서 5회 중 1회 `AiCoachControllerTest` 400 재현 |
| ai `ruff format`, `ruff check`, `lint-imports`, `mypy`, `pytest` | 통과, 505건 통과·27건 건너뜀·실패 0 |
| ai 대상 시험 반복 | 수정 전 15번 중 8번 실패 → 수정 뒤 30번 실패 0 |

## 고친 조각
- 없음(공용 sqa 조각은 아래 「공용 파일 반영」으로 요청)

## 새 임시 번호
| 임시 번호 | 종류 | 한 줄 설명 | 위치 |
|-----------|------|------------|------|
| [임시:R-성-31] | 위험 | 내장 Redis를 소유한 시험 문맥이 캐시에서 밀려 닫히면 Redis가 멈춰 뒤 시험이 60초씩 기다리다 실패한다(4절) | sqa 위험 표 |

## 공용 파일 반영
| 파일 | 넣을 내용 |
|------|-----------|
| plan/08-09-품질-위험.md (또는 정식 번호를 매긴 sqa 위험 표) | R-104([임시:R-성-29]) 상태 「해소(2026-11-15, fix/S15P31A207-300-flaky-tests): 원인은 `Timestamps`가 100ns 시각을 들고 DB가 마이크로초로 반올림해 같은 시계 눈금 안 다음 요청의 `touched`가 거부된 것. 마이크로초로 잘라 고침, 회귀 시험 2건」 |
| 같은 표 | R-095([임시:R-성-20]) 상태 「해소(2026-11-15): 실제 지연 경쟁을 writer 차례를 쥔 결정적 시험으로 바꿈, 20회 반복 실패 0」 |
| 같은 표 | [임시:R-성-31] 새 행(4절), 상태 열림, 대응: 시험 실행 전체에서 내장 Redis를 한 번 띄우는 수정(별도 이슈) |
| sqa/랭킹-다른러너-아바타-성.md 6절 | [임시:R-성-29] 행 상태를 위 R-104 문장으로 |
| test(대결) TC-2147 행 | 비고 「측정 전 고스트 매칭 1회 예열(첫 호출 비용 제외), 예산 3초 그대로」 |

## 다른 담당에게 요청
- 박재영: `ai/tests/writer/test_service.py`의 시험 하나와 backend `BattleThreadIntegrationTest` t3를 대신 고쳤다(PM 지시). 검토 부탁드린다. `common.Timestamps`(공용 값 객체)는 시각을 마이크로초로 자르게 바뀌었다.
