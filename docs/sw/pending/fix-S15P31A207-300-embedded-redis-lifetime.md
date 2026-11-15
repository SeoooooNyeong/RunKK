# 병합 대기: fix/S15P31A207-300-embedded-redis-lifetime

> 작성: 2026-11-15 | 작성자: 이성현 | 스킬: 없음(시험 환경 결함 수정, plan 위험 R-109 상태 갱신 요청)

plan/09-2 위험 R-109(내장 Redis 수명)를 고쳤다. 시험 실행(JVM) 하나에서 내장 Redis를 포트마다 한 번만 띄우고 JVM이 끝날 때 멈춘다. Gradle 시험은 JVM마다 빈 포트를 써서 같은 PC의 다른 작업 트리와 Redis를 나눠 쓰지 않는다.

## 1. 원인

- `bootstrap.EmbeddedRedisConfig`가 `DisposableBean`이라 Redis를 띄운 시험 문맥이 닫히면 `destroy()`에서 Redis를 멈췄다. 뒤에 뜬 문맥은 포트가 이미 열려 있어 「다시 씀」으로 넘어가고 Redis를 갖지 않는다. 그래서 첫 문맥이 문맥 캐시(최대 32개)에서 밀려 닫히면 남은 문맥의 Redis 명령이 모두 60초씩 기다리다 실패했다.
- 포트가 고정(시험 6371)이라 같은 PC의 다른 작업 트리 시험이 먼저 띄운 Redis를 다시 쓰고, 그쪽 JVM이 끝나면 함께 끊겼다(그쪽 시험이 지운 자료를 이쪽 시험이 보는 문제도 있다).
- 확인: 두 문맥을 같은 포트로 띄우고 첫 문맥을 닫은 뒤 PING을 보내는 시험이 수정 전 `Connection refused`로 실패했다(2절).

## 2. 수정(TDD)

| 파일 | 바꾼 것 |
|------|---------|
| `backend/src/main/java/com/a207/runkk/bootstrap/EmbeddedRedisConfig.java` | `DisposableBean`을 뺐다. 띄운 포트를 클래스 전역 집합에 적어 JVM에서 포트마다 한 번만 띄운다. 문맥이 닫혀도 멈추지 않고, 라이브러리(embedded-redis 1.4.4)가 `start()` 때 거는 종료 훅(`RedisInstanceCleaner`, `stop()` 호출)이 JVM 종료 때 멈춘다. 포트가 다른 프로세스에 열려 있으면 다시 쓰는 동작은 그대로다. |
| `backend/build.gradle.kts` | 시험 작업이 JVM 인자로 빈 포트를 `-DEMBEDDED_REDIS_PORT`에 넘긴다(`FreeEmbeddedRedisPort`). `application-test.yml`의 `${EMBEDDED_REDIS_PORT:6371}`이 이 값을 받는다. 속성이 없는 인자 공급자라 시험 작업 입력이 바뀌지 않아 빌드 캐시를 깨지 않는다. `EMBEDDED_REDIS_PORT` 환경 변수가 있으면 넘기지 않는다. |
| `backend/src/test/java/com/a207/runkk/bootstrap/EmbeddedRedisLifetimeTest.java` | 새 시험. 같은 포트로 문맥 두 개 → 첫 문맥 닫기 → PING이 `+PONG`. 수정 전 실패(`expected "+PONG" but was "연결 실패: Connection refused"`), 수정 뒤 통과. |
| `backend/src/test/java/com/a207/runkk/bootstrap/BootstrapConfigTest.java` | L3 시험이 포트 6371 고정값 대신 「바인딩된 포트 = `spring.data.redis.port`」를 본다(Gradle이 빈 포트를 넘기므로). |

- local 프로필(bootRun)은 문맥이 하나라 Redis가 앱이 끝날 때까지 도는 것은 같다. 바뀌는 점은 devtools 재시작 때 Redis를 다시 띄우지 않아 자료가 남는다는 것뿐이다. develop·prod는 내장 Redis를 켜지 않아(`runkk.embedded-redis.enabled` 없음) 영향이 없다.

## 3. 검증(2026-11-15, JDK 21)

| 명령 | 결과 |
|------|------|
| 새 시험 `EmbeddedRedisLifetimeTest` 수정 전 | 실패(Connection refused) |
| 문맥 캐시 1(`spring.test.context.cache.maxSize=1`, 임시 `spring.properties`) + 6371을 응답 없는 프로세스가 잡은 채 `bootstrap.*`·`web.chat.*`·`web.coach.*` | 통과, 41건 실패 0, 2분 48초. 문맥 3개가 캐시에서 밀려 닫혔고 Redis 대기 실패 없음, 내장 Redis는 Gradle이 준 포트에서 한 번만 떴다 |
| backend `./gradlew spotlessApply` 뒤 `./gradlew build` | 통과(BUILD SUCCESSFUL 19분 25초), 시험 2,403건 실패 0(새 시험 1 포함). 실행 중 문맥 2개가 캐시(32개)에서 밀려 닫혔지만 Redis 대기 실패가 없었고, 내장 Redis는 Gradle이 준 포트와 수명 시험 포트에서 한 번씩만 떴다 |

## 고친 조각
- 없음(공용 plan 조각은 아래 「공용 파일 반영」으로 요청)

## 새 임시 번호
- 없음

## 공용 파일 반영
| 파일 | 넣을 내용 |
|------|-----------|
| plan/09-2-위험-v0.34.md | R-109 상태 「해소(2026-11-15, fix/S15P31A207-300-embedded-redis-lifetime): 내장 Redis를 JVM 전역에서 포트마다 한 번만 띄우고 라이브러리 종료 훅으로 JVM 종료 때 멈춘다(문맥이 닫혀도 멈추지 않음). Gradle 시험은 JVM마다 빈 포트를 넘겨 다른 작업 트리와 나눠 쓰지 않는다. 회귀 시험 `EmbeddedRedisLifetimeTest`, 문맥 캐시 1 재현 조합 통과」. 조치 칸 「없음」 → 위 수정 |
| plan/09-2-위험-v0.34.md 머리 문단, plan.md 판 기록 | R-109 해소 한 줄 |

## 다른 담당에게 요청
- 박재영(backend): `EmbeddedRedisConfig`가 문맥 종료 때 Redis를 멈추지 않게 바뀌었고, 시험 Redis 포트가 6371 고정이 아니라 Gradle이 고른 빈 포트다(IDE에서 Gradle 없이 돌리면 6371). 검토 부탁드린다.
