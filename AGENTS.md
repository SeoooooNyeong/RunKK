# 런크크 (S15P31A207) 에이전트 작업 규칙

Claude Code와 Codex가 공통으로 읽는 규칙이다. Claude Code는 `CLAUDE.md`가 이 파일을 불러온다. 사람이 읽는 안내는 `README.md`와 `docs/ONBOARDING.md`에 있다.

## 프로젝트

- 러닝 앱. `backend/` Spring Boot 3.5 (Java 21, Gradle Kotlin DSL, PostgreSQL 17, Redis 8), `android/` Kotlin 2.4 + Jetpack Compose + Hilt (minSdk 26, targetSdk 36, compileSdk 37), `infra/` Docker Compose(nginx, api, postgres, redis, ai), `ai/` Python 3.12 FastAPI(uv, ruff, pytest, import-linter).
- 문서 위치: 요구사항·설계·일정은 팀 위키(AppFlowy), 코드 규칙은 위키 **컨벤션 > Code Convention** (원본 md는 GitHub/자율/자율프로젝트_코드컨벤션.md), 개발 사이클은 위키 **컨벤션 > 런크크 개발 사이클 (ECC 하네스)** (원본 md는 GitHub/자율/자율프로젝트_개발사이클.md), 서버 인프라는 `infra/README.md`, 첫 안내는 `docs/ONBOARDING.md`.
- Spring Boot 3.5 기준: Security 6.x는 람다 DSL만 쓴다(체인 방식 `http.csrf().disable()`은 제거됨), springdoc은 2.x. 서버 설계서(design-oo) D-01이 Java 21 + Spring Boot 3을 정한다.

## 명령 (검증은 항상 이 명령으로)

| 대상 | 커밋 전 포맷 | 검증 | 실행 |
|---|---|---|---|
| backend | `./gradlew spotlessApply` | `./gradlew build` (check에 spotlessCheck·test 포함) | `./gradlew bootRun` (local 프로필, 설치 없이 뜸) |
| android | `./gradlew ktlintFormat` | `./gradlew ktlintCheck lintLocalDebug testLocalDebugUnitTest` | `./gradlew assembleLocalDebug` 후 에뮬레이터 설치 |
| ai | `cd ai && uv run ruff format .` | `uv run ruff check . && uv run lint-imports && uv run mypy && uv run pytest` | `uv run uvicorn --factory runkk_ai.main:create_app` (`RUNKK_INTERNAL_TOKEN` 필요, ai/README.md) |

JDK는 Gradle이 foojay로 21을 내려받는다. 로컬에는 JDK 17 이상만 있으면 된다. Android SDK Platform 37이 필요하다.

## 작업 사이클 (상세: 위키 컨벤션 > 런크크 개발 사이클)

1. 이슈 선택(Jira) → 2. 브랜치 `feature/{Jira 키}-{작업명}` 생성, Jira In Progress → 3. 관련 문서·기존 코드 읽기 → 4. 계획 작성(테스트 목록이 완료 조건, API 계약 먼저 고정) → 5. 테스트 먼저 작성하고 실패 확인 → 6. 구현·테스트 반복 → 7. 검증 게이트(포맷·빌드·테스트) → 8. 리뷰(AI 리뷰 뒤 사람 1명) → 9. MR(템플릿, `Closes {Jira 키}`) → 10. 승인 후 작성자 병합, Jira 자동 Done.

## 규칙

- `main`·`develop`에 직접 push 금지. MR로만 반영한다. 리뷰 승인 1명 뒤 작성자가 병합한다.
- 커밋·MR 제목: `{타입}: {내용} [{Jira 키}]`. 타입은 feat, fix, docs, style, refactor, test, chore.
- 테스트 없는 기능 코드를 만들지 않는다. 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다.
- 포맷은 도구가 정한다. 리뷰에서 포맷을 지적하지 않는다.
- `.env`, 키·토큰, `.jks`, `local.properties`는 커밋하지 않는다.
- 모르면 추측하지 말고 파일을 읽는다. 위키·명세와 코드가 다르면 사용자에게 묻는다.
- 응답과 문서는 한국어로 쓴다. 코드 식별자·주석 규칙은 위키 컨벤션 > Code Convention 을 따른다.

## 병렬 작업

- 같은 작업 트리에서 두 에이전트가 동시에 편집하지 않는다. git worktree로 분리한다.
- 계약(DTO·record, Repository 인터페이스, UiState)이 고정된 조각만 나눈다. 동시 2개까지.

## 에이전트 역할 (ECC)

| 용도 | 에이전트 |
|---|---|
| 탐색 | code-explorer |
| 계획·설계 | planner, code-architect |
| TDD | tdd-guide |
| 빌드 복구 | java-build-resolver, kotlin-build-resolver |
| 리뷰 | code-reviewer, java-reviewer, kotlin-reviewer, security-reviewer, pr-test-analyzer, silent-failure-hunter, a11y-architect |
