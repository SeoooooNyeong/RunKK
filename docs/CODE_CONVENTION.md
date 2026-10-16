<!-- 원본: 팀 위키 컨벤션 > Code Convention (GitHub/자율/자율프로젝트_코드컨벤션.md). 둘을 같이 고친다. -->

# 런크크 코드 컨벤션 v0.1 (제안)

작성 2026-10-15, 이성현(PM). 확정은 10/17 회의. 포매터가 정하는 것(들여쓰기·중괄호·공백)은 규칙으로 적지 않는다. 도구가 잡아주지 못하는 것만 적는다.

---

## 1. 공통

- 패키지 루트 `com.a207.runkk`. 패키지 이름은 소문자 한 단어.
- 클래스 PascalCase, 메서드·변수 camelCase, 상수 UPPER_SNAKE_CASE. 약어도 camelCase(`userId`, `httpClient`).
- Boolean은 `is`/`has`/`can` 접두사.
- 한 줄 120자. 파일 하나에 최상위 public 타입 하나.
- 매직 넘버·문자열은 이름 있는 상수로.
- `System.out`·`println` 금지. 로그는 백엔드 SLF4J, 앱 Timber.
- 주석은 "왜"만 적는다. "무엇을"은 이름으로 설명한다. 외부 제약(API 규격, 라이브러리 우회), 비직관적 수식, 임시 조치에만 쓴다.
- 주석 처리한 코드는 커밋하지 않는다. 지우고 git에 맡긴다.
- `TODO`는 Jira 키를 붙인다: `// TODO(S15P31A207-12): 매칭 타임아웃 서버 값으로 교체`.
- Javadoc·KDoc은 다른 모듈이 쓰는 public 타입에만. 한 줄 요약이면 충분하다.
- 예외는 삼키지 않는다. 잡으면 로그를 남기거나 다시 던진다.

---

## 2. 백엔드 (Java 25, Spring Boot 4.1)

**패키지**

```
com.a207.runkk
├─ global/        config, security, error(ErrorCode, BusinessException, GlobalExceptionHandler), common
└─ {도메인}/       controller, service, repository, entity, dto
```

도메인 예: user, run, battle, crew, race, runimal, avatar, coach, report.

**클래스**

| 종류 | 이름 | 규칙 |
|---|---|---|
| 컨트롤러 | `RunController` | `@RestController`, DTO만 주고받는다. 엔티티 노출 금지 |
| 서비스 | `RunService` | 클래스에 `@Transactional(readOnly = true)`, 쓰기 메서드만 `@Transactional` |
| 리포지토리 | `RunRepository` | `JpaRepository` 인터페이스. 복잡한 조회는 `RunQueryRepository` |
| 엔티티 | `Run` | 접미사 없음. setter 금지, 상태 변경은 의도가 드러나는 메서드(`finish()`, `cancel()`) |
| DTO | `RunCreateRequest`, `RunResponse` | `record`. 요청에 Bean Validation(`@NotNull` 등), 서비스에서 재검증하지 않음 |

- 의존성은 생성자 주입(`private final` + `@RequiredArgsConstructor`). 필드 `@Autowired` 금지.
- Lombok 허용: `@Getter`, `@RequiredArgsConstructor`, `@NoArgsConstructor(access = PROTECTED)`, `@Builder`. `@Data`·`@Setter` 금지.
- 연관관계는 `fetch = LAZY`. 양방향은 꼭 필요할 때만.
- 비즈니스 예외는 `BusinessException(ErrorCode)` 하나. `ErrorCode` enum이 HTTP 상태·코드·메시지를 가진다. 응답 형식은 API 명세서(1.3)를 따른다.
- 설정값은 `application.yml` + `@ConfigurationProperties`. `@Value` 금지. 비밀값은 환경 변수.
- URL은 `/api/v1/{복수명사}`, kebab-case. 동사 금지(`POST /api/v1/runs`, `PATCH /api/v1/runs/{runId}/finish`).
- Spring Security 7은 람다 DSL만 쓴다. Boot 3.x 예제 복사 금지.
- 테스트: 클래스 `RunServiceTest`, 메서드 `행위_조건_결과` 영어 snake_case(`finish_whenAlreadyFinished_throws`) + `@DisplayName` 한글. 서비스는 단위 테스트, 컨트롤러는 `@WebMvcTest`.

---

## 3. 앱 (Kotlin 2.4, Jetpack Compose, Hilt)

**패키지**

```
com.a207.runkk
├─ ui/{기능}/      {기능}Screen.kt, {기능}ViewModel.kt, {기능}UiState.kt
├─ data/{기능}/    {기능}Repository.kt, remote/{기능}Api.kt + dto, local/{기능}Dao.kt
├─ di/            Hilt 모듈
└─ core/          네트워크·공용 컴포넌트·유틸
```

- 흐름은 한 방향: Screen → ViewModel → Repository → (Retrofit Api | Room Dao). 네트워크·DB 호출은 Repository 안에서만.
- `{기능}Screen`은 ViewModel을 받아 상태를 꺼내고, 실제 그리기는 `{기능}Content(state, onXxx)`가 한다. Preview는 `Content`에 대해 `private`으로.
- 컴포저블은 PascalCase 함수, 첫 선택 파라미터는 `modifier: Modifier = Modifier`, 호출 측에서 `modifier`를 넘긴다.
- ViewModel은 `StateFlow<{기능}UiState>` 하나만 노출. UiState는 `data class`, 이벤트·결과는 `sealed interface`.
- ViewModel에서 `Context`·`View`·`Activity` 참조 금지. 문자열은 리소스 id로 넘긴다.
- 코루틴은 `viewModelScope`. Repository는 `suspend`/`Flow`. `Dispatchers`는 직접 쓰지 않고 주입.
- API DTO는 `@Serializable` + `{기능}Request`/`{기능}Response`. UI 모델과 분리하고 `toXxx()` 확장 함수로 변환.
- `!!` 금지. `when`은 exhaustive. `lateinit`은 Hilt 주입 외 금지.
- 화면 키는 `ui/RunkkNavDisplay.kt`의 `NavKey`에 `@Serializable data object/class`로 추가.
- 문자열 하드코딩 금지. 리소스 이름은 snake_case + 접두사: 문자열 `{화면}_{용도}`, drawable `ic_`/`img_`/`bg_`, 색은 `Theme.kt`의 `MaterialTheme.colorScheme`만.
- 테스트: ViewModel 단위 테스트(`kotlinx-coroutines-test`). 클래스 `HomeViewModelTest`, 메서드는 백틱 한글 허용.

---

## 4. 도구로 강제

| 대상 | 도구 | CI |
|---|---|---|
| 공통 | 루트 `.editorconfig` (UTF-8, LF, 4칸, 120자, ktlint 규칙값 포함) | - |
| 백엔드 | Spotless 8.10 + google-java-format(aosp, Spotless 기본 버전). `backend/gradle.properties`의 `--add-exports` JVM 옵션이 필요하다 | `./gradlew build` (`check`가 `spotlessCheck`를 포함) |
| 앱 | ktlint 1.8(Gradle 플러그인 14.2, `android_studio` 스타일 = official + Android 규칙, Composable 함수명 예외) + Android Lint | `./gradlew ktlintCheck lintLocalDebug testLocalDebugUnitTest` |

- 포맷은 커밋 전에 `./gradlew spotlessApply`(backend) / `./gradlew ktlintFormat`(android). 리뷰에서 포맷 지적은 하지 않는다.
- google-java-format은 JDK 17 이상에서 javac 내부 API 접근 옵션이 필요해 `backend/gradle.properties`에 `--add-exports` 6개를 넣어 두었다. 지우면 `spotlessJavaApply`가 `InvocationTargetException`으로 실패한다. 버전을 직접 지정하면(1.37.0 등) Spotless 8.10과 맞지 않아 같은 오류가 나므로 Spotless 기본 버전을 쓴다.
- MR 체크리스트에 "포매터·린트 통과" 항목 추가.
- 규칙 변경은 이 문서 변경 이력에 한 줄로 남기고 Jira 이슈를 건다.

---

## 5. 변경 이력

| 버전 | 일자 | 내용 | 작성 |
|---|---|---|---|
| v0.1 | 2026-10-15 | 초안. 기존 JS 템플릿(Code Convention 페이지)을 Java·Kotlin 스택 기준으로 대체 | 이성현 |
| v0.1 | 2026-10-16 | 저장소 적용(Jira S15P31A207-52): `docs/CODE_CONVENTION.md`, 루트 `.editorconfig`, 백엔드 Spotless, 앱 ktlint, MR 템플릿 체크리스트. 4장을 실제 설정 기준으로 수정(add-exports, 작업 이름). 팀 위키 컨벤션 > Code Convention 페이지 교체 | 이성현 |
