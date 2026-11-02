# 스택별 ECC 구현 흐름

`sw-implement` 3단계에서 하위 작업자에게 적어 주는 순서다. 스킬 이름은 ECC 플러그인 기준이다(`ecc:` 접두어). Codex CLI에서는 접두어를 떼고 `$springboot-tdd`처럼 부르고, 에이전트는 `ecc-java-reviewer`처럼 하이픈으로 잇는다(`harness-codex.md`). 스킬을 불러올 수 없으면 표의 "할 일" 열을 지시문으로 쓴다. 모든 흐름의 공통 원칙은 **시험 먼저 → 실패 확인 → 구현 → 검증 → 자가 리뷰**다.

## 1. Spring Boot 서버

| 순서 | 스킬·에이전트 | 할 일 |
|------|---------------|-------|
| 1 | `ecc:springboot-patterns` | REST·서비스·데이터 접근·비동기·로깅 계층 확인 |
| 2 | `ecc:springboot-tdd` | JUnit 5·Mockito·MockMvc·Testcontainers로 시험 먼저 |
| 3 | `ecc:jpa-patterns` | 엔티티·연관·N+1·트랜잭션·잠금·페이지 나누기 |
| 4 | `ecc:springboot-security` | 인증·인가·검증·비밀값·로그 개인정보·시도 제한 |
| 5 | `ecc:springboot-verification` | 빌드 → 정적 분석 → 시험·커버리지 → 의존성·비밀 스캔 |

- 공통 표준: `ecc:java-coding-standards`.
- 자가 리뷰: `ecc:java-reviewer`(계층·JPA·보안·동시성), 보안이 큰 단계는 `ecc:security-reviewer`.
- 빌드 복구: `ecc:java-build-resolver` 또는 `/gradle-build`.
- 설계 규칙과 충돌하면 설계가 우선이다(예: setter 금지, 다른 패키지 엔티티는 ID로만 참조).

## 2. Android·Kotlin 앱, Wear OS

| 순서 | 스킬·에이전트 | 할 일 |
|------|---------------|-------|
| 1 | `ecc:android-clean-architecture` | 모듈 구조, UseCase·Repository, 데이터 계층 경계 |
| 2 | `ecc:kotlin-testing`, `ecc:kotlin-coroutines-flows` | 시험 먼저(코루틴 시험 도구, 가짜 포트, 고정 시계) |
| 3 | `ecc:compose-multiplatform-patterns` | 화면 상태·재구성(화면 단계) |
| 4 | `ecc:gradle-build` | 빌드 오류 최소 수정 |
| 5 | `ecc:kotlin-review` | null 안전성·코루틴 안전성·GlobalScope 금지 자가 점검 |

- 화면 단계는 `ecc:accessibility`와 `ecc:a11y-architect`로 접근성을 점검하고, 화면마다 스크린샷 시험(Roborazzi 등)을 둔다.
- 구조 규칙(모듈 의존, 패키지 의존 표)은 빌드 설정과 구조 시험(Konsist 등)으로 강제한다.

## 3. Python FastAPI ai 서버

| 순서 | 스킬·에이전트 | 할 일 |
|------|---------------|-------|
| 1 | `ecc:eval-harness` | 출력 검증 기준(빈 값·길이·시간 제한·상한)을 시험 목록으로 고정 |
| 2 | `ecc:fastapi-patterns`, `ecc:python-patterns` | 라우터 → 서비스 → 추상 클라이언트 한 방향, 주입, lifespan |
| 3 | `ecc:python-testing` | pytest + ASGI 전송 + 의존성 덮어쓰기로 시험 먼저 |
| 4 | `ecc:ai-regression-testing` | 응답 계약 표본(상태 코드별 JSON) 고정 |
| 5 | `ecc:cost-aware-llm-pipeline`, `ecc:security-review` | 동시성 제한·호출 상한, 내부 토큰·입력 검증 |

- 모듈 사이 의존은 import-linter 계약으로 강제한다.
- 리뷰: `ecc:python-reviewer`, `ecc:fastapi-reviewer`.

## 4. 리뷰·통합 단계에서 쓰는 것

| 용도 | 스킬·에이전트 |
|------|---------------|
| 코드 리뷰(판정) | `$sw-code-review` (보조로 언어별 reviewer) |
| 보안 점검 | `ecc:security-reviewer` |
| 시험 보강 | `ecc:pr-test-analyzer` |
| 조용한 실패 찾기 | `ecc:silent-failure-hunter` |
