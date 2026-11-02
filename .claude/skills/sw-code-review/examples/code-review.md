# 코드 리뷰 결과서: 학사관리 시스템

> 작성: 2026-03-20 | 버전: v0.2 | 입력: src/main/java/kr/ac/univ/grade/, docs/sw/design-oo.md v1.1, docs/sw/design-review.md v1.0

## 리뷰 기준

| 항목 | 기준 | 출처 |
|------|------|------|
| 매개변수 수 | 4개 이하 | 기본 규칙 |
| 중첩 깊이 | 3단계 이하 | 기본 규칙 |
| 함수 길이 | 50줄 이하 | 기본 규칙 |
| 순환 복잡도 | 10 이하 | 기본 규칙 |
| 결합도 상한 / 응집도 하한 | 스탬프 / 통신적 | design-review.md v1.0 |

코딩 규약 문서: 없음, 기본 규칙 적용 (SRS NFR-007 "Java 표준 명명 규칙을 따른다"만 존재)
형상 관리 도구·브랜치 전략: Git, `main`/`develop`/`feature/*`

## 언어 평가 (최초 1회, 2026-03-06)

언어: Java 17

| 기준 | 판정 | 근거 |
|------|------|------|
| 친숙성 | 적합 | 5명 중 4명 Java 3년 이상 |
| 사용 능력 | 적합 | 트랜잭션·동시성 라이브러리 충분 |
| 처리 효율성 | 적합 | NFR-001 응답 2초, NFR-002 동시 1,000명 대비 여유 |
| 과거 개발 실적 | 적합 | 전산실 기존 학적 DB 연동 모듈이 Java |
| 문제 유형 | 적합 | 사무 처리형 |
| 자료 구조 | 적합 | 표준 컬렉션으로 충분 |
| 이식성 | 적합 | JVM, 전산실 Linux 서버 |
| 개발자 경험 | 보통 | 동시성 고급 기능 경험자 1명(책임 프로그래머) |
| 컴파일러·도구 | 적합 | Maven, IntelliJ |

부적합 0건 → 재검토 불필요

---

## 리뷰 2026-03-06 (1차, 요약)

대상: `grade/` 패키지 4개 파일. 치명 1 (GradeService가 Enrollment.status 직접 수정), 중 6, 경 11. 판정 **불합격**. 상세는 커밋 `a3f1c2e` 이전 이력 참조. 아래 2차 리뷰에서 치명 결함 수정 여부를 확인한다.

---

## 리뷰 2026-03-20 (2차)

### 대상

| 파일 | 줄 수 | 대응 설계 모듈 | 마지막 커밋 |
|------|-------|----------------|-------------|
| grade/GradeController.java | 84 | grade.GradeController | 7b2d9e1 |
| grade/GradeService.java | 162 | grade.GradeService | 7b2d9e1 |
| grade/GradeCalculator.java | 58 | grade.GradeCalculator | 5c0a4f3 |
| grade/GradeRepository.java | 41 | grade.GradeRepository | 2e8b7d0 |
| grade/Grade.java | 67 | grade.Grade (엔티티) | 2e8b7d0 |

### 1. 설계 일치

| 설계서 항목 | 코드 | 일치 | 비고 |
|-------------|------|------|------|
| GradeService.inputGrade(studentId, courseId, score) | inputGrade(Long, String, int) | 예 | |
| GradeService.requestObjection(gradeId, reason) | requestObjection(Long, String, boolean notify) | 아니오 | 매개변수 `notify` 추가. 제어 결합도 |
| GradeCalculator.calculateGrade(score) | calculateGrade(int) | 예 | |
| GradeCalculator.calculateGpa(grades) | calculateGpa(List<Grade>) | 예 | 스탬프. 기준 상한 이내 |
| Grade.markConfirmed() | markConfirmed() | 예 | 1차 치명 결함 교정으로 신설. 설계서 v1.1 반영됨 |
| grade → enrollment 의존: Enrollment ID만 | import enrollment.Enrollment; `enrollment.confirm()` 호출 | 예 | 1차 내용 결합 → 메소드 호출로 교정됨 |
| GradeReportPrinter (설계서에 있음) | 없음 | 아니오 | 미구현. FR-021 성적표 출력 |

설계에 없는 의존 화살표:

| From → To | 주고받는 것 | 결합도 | 등급 |
|-----------|-------------|--------|------|
| GradeService → common.NotificationUtil | `notify` 플래그에 따라 호출 | 제어 | 중 |
| GradeService → academic.StudentRepository | studentId로 조회 | 데이터 | 없음 (설계서 갱신 대상) |

설계서 갱신 요청 (코드가 옳고 설계가 낡은 경우):
- grade → academic 의존 추가. 성적 입력 시 학적 상태(휴학·제적) 확인은 FR-012 수용 기준에 있으므로 설계서 패키지 의존도에 반영해야 한다. `design-review.md` 재검증 필요 (Fan-Out grade 2 → 3, 기준 7 이내).

### 2. 코딩 스타일

| 파일:줄 | 항목 | 현재 | 교정 예시 | 등급 |
|---------|------|------|-----------|------|
| GradeService.java:48 | 매개변수 | `requestObjection(Long gradeId, String reason, boolean notify)` | `requestObjection(Long gradeId, String reason)` + 알림은 호출자가 `notificationService.send()` | 중 |
| GradeService.java:71-98 | 함수 길이 | `inputGrade` 54줄 | 유효성 검사를 `validateInput()`으로 분리 | 중 |
| GradeService.java:77 | 변수명 | `int s = score;` | 불필요한 복사. `score` 직접 사용 | 경 |
| GradeService.java:112 | 매직 넘버 | `if (score > 100)` | `if (score > MAX_SCORE)` | 경 |
| GradeCalculator.java:14-31 | 중첩 깊이 | if 안 if 안 if (깊이 3) | else-if 체인으로 평탄화 | 경 (기준 3 이내, 가독성 권고) |
| GradeCalculator.java:40 | 변수 용도 | `double sum`에 합계 후 평균을 덮어씀 | `double average = sum / count;` 별도 선언 | 중 |
| Grade.java:22 | 변수명 | `private String st;` | `private GradeStatus status;` | 경 |
| GradeRepository.java:30 | 임시 변수 | `List<Grade> r = query(); return r;` | `return query();` | 경 |

### 3. 구조적 프로그래밍

| 함수 | 조건문 | 반복문 | 약산 복잡도 | 위반 |
|------|--------|--------|-------------|------|
| GradeService.inputGrade | 6 | 0 | 7 | 없음 |
| GradeService.requestObjection | 4 | 0 | 5 | 없음 |
| GradeService.confirmAll | 3 | 2 | 6 | 반복문 안 `return false;` (123행). 중 |
| GradeCalculator.calculateGrade | 5 | 0 | 6 | 없음 |
| GradeCalculator.calculateGpa | 2 | 1 | 4 | 없음 |

GradeService.confirmAll:123 — `for` 안에서 실패 시 `return false`. 교정: 실패 목록을 모아 반복 종료 후 한 번 반환하거나, 단건 확정을 `confirmOne()`으로 뽑아 그 함수의 유일한 책임으로 만든다.

goto 등가물: 없음.

### 4. 객체지향 구현

| 파일:줄 | 항목 | 현재 | 교정 예시 | 등급 |
|---------|------|------|-----------|------|
| Grade.java:22-25 | 캡슐화 | 필드 모두 private | 통과 | 없음 |
| Grade.java:44 | 접근자 | `setScore(int score) { this.score = score; }` 검사 없음 | `if (score < 0 \|\| score > MAX_SCORE) throw new IllegalArgumentException(...)` | 경 |
| Grade.java:18 | 생성자 | 기본 생성자 후 setter 4회 호출해야 유효 | `Grade(Long studentId, String courseId, int score)` 생성자 추가 | 경 |
| GradeService.java:48 | 다형성 | `if (notify) notificationUtil.send(...)` | 알림 여부를 호출자 책임으로. 플래그 제거 | 중 (2절과 동일 건) |
| Grade.java:30 | this | `this.status = status;` | 통과 | 없음 |
| 전체 | 상속 | 상속 없음. 합성만 사용 | 통과 | 없음 |

1차 치명 결함 `enrollment.status = "CONFIRMED"` (GradeService:88)는 `enrollment.confirm()`으로 교정 확인.

### 5. 형상 관리

| 활동 | 판정 | 근거 |
|------|------|------|
| 형상 식별 | 예 | 패키지·클래스가 설계서 모듈 ID와 1:1 |
| 버전 제어 | 예 | Git, 태그 v0.9.0 (2026-03-13) |
| 변경 통제 | 아니오 | 커밋 7b2d9e1 메시지 "성적 수정"에 요구 ID 없음. 변경 요청 절차 미정의 |
| 형상 감사 | 예 | v0.9.0 대비 diff 확인 가능 |
| 상태 보고 | 아니오 | CHANGELOG 없음. 최근 20개 커밋 중 12개가 "수정", "update" |

제안: `references/scm.md` 5절의 커밋 메시지 규칙(`feat(grade): … Refs: FR-012`)과 변경 요청 절차를 적용한다. 형상 관리 대상에 비용은 포함하지 않는다.

### 6. 집계와 판정

| 등급 | 건수 |
|------|------|
| 치명 | 0 |
| 중 | 5 (제어 결합 1, 함수 길이 1, 변수 용도 1, 반복문 내 return 1, 플래그 분기는 제어 결합과 동일 건으로 1회만 집계) |
| 경 | 7 |

판정: **조건부**
근거: 치명 0, 중 5 (4 이상 10 이하), 형상 관리 2항목 아니오. 1차 대비 치명 1 → 0, 중 6 → 5, 경 11 → 7로 개선.

미구현 GradeReportPrinter(FR-021)는 코드 결함이 아니라 진척 항목이므로 판정에 넣지 않고 `sw-sqa` 추적표 점검으로 넘긴다.

### 7. sw-test 전달: 우선 테스트 대상

| 함수 | 약산 복잡도 | 이유 |
|------|-------------|------|
| GradeService.inputGrade | 7 | 유효성 분기 6개. FR-012 핵심 경로 |
| GradeCalculator.calculateGrade | 6 | 등급 경계값 5개. 기초 경로 검사 대상 |
| GradeService.confirmAll | 6 | 반복 + 조기 반환. 루프 검사 대상 |

복잡도 10 초과 함수는 없다.

### 8. 다음 행동

- [ ] 중 결함 5건 수정: 담당 책임 프로그래머, 기한 2026-03-24
- [ ] 설계서 갱신: `/sw-design-oo` — grade → academic 의존 추가 후 `/sw-design`으로 design-review.md 재검증
- [ ] 변경 통제 절차와 커밋 규칙 도입: 담당 PM, 기한 2026-03-24
- [ ] `/sw-test` 호출: 위 7절 3개 함수 우선
