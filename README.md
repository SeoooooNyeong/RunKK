# 런크크 (S15P31A207)

SSAFY 15기 자율프로젝트 A207 팀의 러닝 앱입니다. 기획·규칙·회의록은 팀 위키(AppFlowy)에 있고, 이 저장소는 코드만 다룹니다. 처음 합류했다면 [docs/ONBOARDING.md](docs/ONBOARDING.md)부터 읽으세요.

## 저장소 구조

| 폴더 | 내용 | 기술 |
| --- | --- | --- |
| `backend/` | API 서버 | Spring Boot 4.1.x, JDK 25, Gradle(Kotlin DSL), MySQL 8.4, Redis 8 |
| `android/` | 폰 앱 (워치 모듈은 기기 확보 후 추가) | Kotlin 2.4, AGP 9.4, Jetpack Compose(Material 3), Navigation 3, Hilt |
| `docs/` | 온보딩 등 저장소 안내 문서 | |
| `infra/` | 서버 스택과 EC2 이관 가이드 ([infra/README.md](infra/README.md)) | Docker Compose(nginx, api, MySQL 8.4, Redis 8), Let's Encrypt |

## 브랜치와 MR

- `main`: 배포된 버전. `develop`: 통합 브랜치(기본 브랜치). 둘 다 직접 push 금지, MR로만 반영합니다.
- 작업 브랜치: `{feature|fix|hotfix}/{Jira 이슈 키}-{작업명}` (예: `feature/S15P31A207-12-login`).
- 커밋·MR 제목: `{타입}: {작업 내용} [{Jira 이슈 키}]`. 타입은 feat, fix, docs, style, refactor, test, chore.
- MR 본문은 템플릿(`.gitlab/merge_request_templates/Default.md`)이 자동으로 채워집니다. 리뷰 승인 1명과 파이프라인 통과 후 작성자가 병합합니다.
- 코드 규칙은 [docs/CODE_CONVENTION.md](docs/CODE_CONVENTION.md)(위키 **컨벤션 > Code Convention** 과 동일)를 따릅니다. 포맷은 도구가 정합니다: 백엔드 `./gradlew spotlessApply`, 앱 `./gradlew ktlintFormat`을 커밋 전에 실행하세요(루트  포함).
- 자세한 규칙은 위키 **컨벤션** 과 **문서 > Git Workflow 지침** 을 따릅니다.

## 백엔드 실행

JDK는 Gradle이 자동으로 내려받습니다(`foojay-resolver`). 로컬에는 JDK 17 이상만 있으면 됩니다.

```bash
cd backend
./gradlew build          # 빌드 + 테스트
./gradlew bootRun        # local 프로필(기본): 아무것도 설치하지 않아도 뜹니다
```

프로필은 `src/main/resources/application.yml` 한 파일에 있습니다.

| 프로필 | 용도 | DB / Redis | 비고 |
| --- | --- | --- | --- |
| `local` (기본) | 개인 PC | H2 메모리 DB + 내장 Redis(앱이 직접 띄움, 포트 6370) | DDL create-drop, SQL 로그, H2 콘솔 `/h2-console` |
| `develop` | develop 브랜치 배포(EC2) 또는 PC에서 실제 DB로 테스트 | MySQL 8.4 + Redis 8. PC에서는 `docker compose up -d` (backend/compose.yaml) | Flyway + DDL validate, Swagger 항상 켬 |
| `prod` | main 브랜치 배포 | MySQL 8.4 + Redis 8, 값은 서버 `.env`에서만 | DDL validate, Swagger는 `SWAGGER_ENABLED=true`일 때만 |
| `test` | `@SpringBootTest` | H2 + 내장 Redis(포트 6371) | 테스트 코드 전용 |

```bash
./gradlew bootRun --args='--spring.profiles.active=develop'   # docker compose로 MySQL/Redis 띄운 뒤
```

- develop/prod가 읽는 환경 변수 목록은 `backend/.env.example`을 보세요. `.env`는 커밋하지 않습니다.
- 헬스체크 `GET /actuator/health`, API 문서 `GET /docs`.
- Spring Boot 4 주의: Spring Security 7은 람다 DSL만 지원하고(`global/config/SecurityConfig.java` 참고), springdoc은 3.x 라인을 씁니다. SSAFY 공통 과정의 Boot 3.x 예제를 그대로 복사하면 컴파일되지 않습니다.

## Android 실행

Android Studio 최신 안정판으로 `android/` 폴더를 엽니다. 명령줄 빌드:

```bash
cd android
./gradlew assembleDebug testDebugUnitTest   # JDK 17 이상, Android SDK Platform 37 필요 (compileSdk 37 / targetSdk 36)
```

- `local.properties`의 `sdk.dir`은 각자 PC의 Android SDK 경로로 바꿉니다(커밋하지 않음).
- 버전은 `gradle/libs.versions.toml` 한 곳에서 관리합니다. minSdk 26 / targetSdk 36.
- 빌드 플레이버 `local` / `develop` / `prod`가 `BuildConfig.API_BASE_URL`을 정합니다. 평소 개발은 `localDebug`(에뮬레이터에서 PC의 백엔드 local 프로필에 접속), 배포 빌드는 `prodRelease`. develop/prod 주소는 EC2 도메인 배정 후 `app/build.gradle.kts`에서 바꿉니다.
- 화면은 `ui/` 아래 기능별 패키지로 나누고, 화면 키는 `ui/RunkkNavDisplay.kt`의 `NavKey`에 추가합니다.
- 릴리스 서명 키는 스토어 등록 단계에서 CI 변수로 올립니다. 저장소에 `.jks`를 넣지 않습니다.

## CI/CD

루트 `.gitlab-ci.yml` 하나가 `backend/`·`android/` 변경을 나눠 돌립니다. 러너는 EC2 한 대에 둘 다 등록합니다.

| 트리거 | 백엔드 | 안드로이드 |
| --- | --- | --- |
| MR → develop | lint(Spotless) → test(단위 + ArchUnit 패키지 규칙 + Testcontainers MySQL·Redis, JUnit·Jacoco 리포트) | lint(ktlint + Android Lint) → test(단위) → build(develop APK 아티팩트) |
| develop 병합 | 위 + build(이미지) → deploy(`infra/deploy.sh`) → smoke(health, /docs) | 위와 동일 |
| main 병합 | build → deploy(수동 승인) → smoke | release(서명 AAB, 키스토어 변수 있을 때만) |

- **러너 등록(EC2 배정 후, PM)**: `docker` 태그 = docker executor(`config.toml`의 `volumes = ["/var/run/docker.sock:/var/run/docker.sock", "/cache"]`), `deploy` 태그 = shell executor(러너 사용자를 docker 그룹에 추가). Settings > CI/CD > Runners에서 토큰을 받아 `gitlab-runner register`.
- **CI 변수(masked + protected)**: `INFRA_ENV`·`INFRA_ENV_PROD`(File 타입, `infra/.env` 내용), `ANDROID_KEYSTORE_BASE64`·`ANDROID_KEYSTORE_PASSWORD`·`ANDROID_KEY_ALIAS`·`ANDROID_KEY_PASSWORD`.
- **Android SDK 이미지**: 러너 등록 뒤 develop 파이프라인의 `android:ci-image` 수동 잡을 한 번 실행하면 `ci/android/Dockerfile`이 프로젝트 레지스트리에 올라갑니다. 이 이미지가 없으면 Android 잡은 실패합니다.
- **배포·롤백**: EC2가 한 대라 main 배포는 develop 스택을 교체합니다. 롤백은 `docker tag runkk-api:<이전 sha> runkk-api:latest` 후 `infra/`에서 `docker compose up -d --no-build api`.
- **스키마 변경**: develop·prod는 Flyway + `ddl validate`입니다. 엔티티를 바꾸면 `backend/src/main/resources/db/migration/V{n}__{설명}.sql`을 함께 넣어야 통합 테스트와 배포가 통과합니다. local·test 프로필은 H2 create-drop이라 마이그레이션 없이 돕니다.
- **로컬에서 CI와 같은 검사**: 백엔드 `./gradlew spotlessCheck test`(Docker가 있으면 Testcontainers 통합 테스트까지, 없으면 건너뜀), Android `./gradlew ktlintCheck lintLocalDebug testLocalDebugUnitTest`.

## 링크

- Jira: https://ssafy.atlassian.net/jira/software/c/projects/S15P31A207/boards/16675/backlog
- 팀 위키: https://appflowy.kktrkkt.dev/app/60f43234-b29b-4fe8-b1e1-3528d8815da1/ded3ab26-2325-441d-b99a-8c36784884d1 (SSAFY 내부망)
