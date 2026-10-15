# 런크크 (S15P31A207)

SSAFY 15기 자율프로젝트 A207 팀의 러닝 앱입니다. 기획·규칙·회의록은 팀 위키(AppFlowy)에 있고, 이 저장소는 코드만 다룹니다. 처음 합류했다면 [docs/ONBOARDING.md](docs/ONBOARDING.md)부터 읽으세요.

## 저장소 구조

| 폴더 | 내용 | 기술 |
| --- | --- | --- |
| `backend/` | API 서버 | Spring Boot 4.1.x, JDK 25, Gradle(Kotlin DSL), MySQL 8.4, Redis 8 |
| `android/` | 폰 앱 (워치 모듈은 기기 확보 후 추가) | Kotlin 2.4, AGP 9.4, Jetpack Compose(Material 3), Navigation 3, Hilt |
| `docs/` | 온보딩 등 저장소 안내 문서 | |
| `infra/` | EC2 배포 설정 (EC2 배정 후 추가 예정) | Docker Compose, Nginx, GitLab CI |

## 브랜치와 MR

- `main`: 배포된 버전. `develop`: 통합 브랜치(기본 브랜치). 둘 다 직접 push 금지, MR로만 반영합니다.
- 작업 브랜치: `{feature|fix|hotfix}/{Jira 이슈 키}-{작업명}` (예: `feature/S15P31A207-12-login`).
- 커밋·MR 제목: `{타입}: {작업 내용} [{Jira 이슈 키}]`. 타입은 feat, fix, docs, style, refactor, test, chore.
- MR 본문은 템플릿(`.gitlab/merge_request_templates/Default.md`)이 자동으로 채워집니다. 리뷰 승인 1명과 파이프라인 통과 후 작성자가 병합합니다.
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
| `develop` | develop 브랜치 배포(EC2) 또는 PC에서 실제 DB로 테스트 | MySQL 8.4 + Redis 8. PC에서는 `docker compose up -d` (backend/compose.yaml) | DDL update, Swagger 항상 켬 |
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

## 링크

- Jira: https://ssafy.atlassian.net/jira/software/c/projects/S15P31A207/boards/16675/backlog
- 팀 위키: https://appflowy.kktrkkt.dev/app/60f43234-b29b-4fe8-b1e1-3528d8815da1/ded3ab26-2325-441d-b99a-8c36784884d1 (SSAFY 내부망)
