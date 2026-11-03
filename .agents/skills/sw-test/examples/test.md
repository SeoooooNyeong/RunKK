# 테스트 계획 및 케이스: 학사관리 시스템 (성적 모듈)

> 작성: 2026-03-27 | 버전: v1.0 | 입력: docs/sw/srs.md v1.1, docs/sw/design-oo.md v1.2, docs/sw/code-review.md (2026-03-20), src/main/java/kr/ac/univ/grade/

## 1. 테스트 계획

### 1.1 범위

- 대상: `grade` 패키지 (GradeController, GradeService, GradeCalculator, GradeRepository, Grade). FR-012 성적 입력, FR-013 성적 조회, FR-014 이의신청, FR-021 성적표 출력 중 구현된 FR-012·013·014.
- 제외: FR-021 (미구현, 코드 리뷰 2026-03-20 참조). 기존 학적 DB(전산실 운영)는 `StudentRepository` 스텁으로 대체하고 실제 연동은 시스템 테스트에서만 수행.
- 프레임워크·환경: JUnit 5, H2 인메모리 DB(단위·통합), 전산실 스테이징 서버(시스템).

### 1.2 수준별 계획 (V-모델 대응)

| 수준 | 대상 | 기준 문서 | 주 기법 | 합격 기준 |
|------|------|-----------|---------|-----------|
| 단위 | Grade, GradeCalculator, GradeService | design-oo.md 5장 클래스 설계 | 기초 경로 + 동치·경곗값 | 경로 100%, 경곗값 전부 |
| 통합 | 스레드 "성적 입력", "성적 조회", "이의신청" | design-oo.md 6장 시퀀스 SD-03, SD-04, SD-05 | 스레드 기반 (하위 2층은 사용 기반) | 시퀀스 3개 100% |
| 검증 | FR-012, 013, 014 | srs.md 3장 | 요구 기반 블랙박스 | FR 커버 100% |
| 시스템 | NFR-001 응답, NFR-002 동시 접속, NFR-004 권한 | srs.md 4장 | 부하·보안 | 측정값 전부 합격 |

### 1.3 통합 전략

- 사용 기반 층 순서: 1층 Grade(엔티티) → 2층 GradeCalculator → 3층 GradeRepository(H2), StudentRepository(스텁) → 4층 GradeService → 5층 GradeController
- 스레드 목록:
  - 성적 입력: GradeController → GradeService → StudentRepository(스텁) → GradeCalculator → GradeRepository
  - 성적 조회: GradeController → GradeService → GradeRepository
  - 이의신청: GradeController → GradeService → GradeRepository → NotificationService(스텁)
- 스텁: StudentRepository.findStatus(studentId) → 재학/휴학/제적 중 케이스가 지정한 값. NotificationService.send() → 호출 기록만.

### 1.4 순서와 진입 조건

단위 → 통합 → 검증 → 시스템. 단위 합격률 100%에서 통합 진입. 통합 치명 결함 0에서 검증 진입. 검증 FR 합격 100%에서 시스템 진입.

## 2. 단위 테스트

### 2.1 화이트박스: GradeCalculator.calculateGrade(int score)

코드 위치: GradeCalculator.java:12-24 (코드 리뷰 후 else-if 체인으로 평탄화된 버전)

```java
public String calculateGrade(int score) {
    if (score < 0 || score > 100) return "INVALID";
    if (score >= 90) return "A";
    else if (score >= 80) return "B";
    else if (score >= 70) return "C";
    return "F";
}
```

제어 흐름도:

```
N1 [score<0?] ─true─────────────────┐
  │false                            │
N2 [score>100?] ─true───────────────┤
  │false                            ▼
N4 [score>=90?] ─true─▶ N5 [A] ─┐  N3 [INVALID]
  │false                        │   │
N6 [score>=80?] ─true─▶ N7 [B] ─┤   │
  │false                        │   │
N8 [score>=70?] ─true─▶ N9 [C] ─┤   │
  │false                        │   │
N10 [F] ────────────────────────┤   │
                                ▼   ▼
                              N11 [종료]
```

| 방법 | 값 |
|------|----|
| 노드 N | 11 |
| 간선 E | 15 |
| V(G) = E − N + 2 | 6 |
| 단순 조건 P + 1 | 5 + 1 = 6 |
| 영역 수 | 6 |

독립 경로:

| 경로 | 노드 순서 | TC |
|------|-----------|----|
| P1 | N1→N3→N11 | TC-001 |
| P2 | N1→N2→N3→N11 | TC-002 |
| P3 | N1→N2→N4→N5→N11 | TC-003 |
| P4 | N1→N2→N4→N6→N7→N11 | TC-004 |
| P5 | N1→N2→N4→N6→N8→N9→N11 | TC-005 |
| P6 | N1→N2→N4→N6→N8→N10→N11 | TC-006 |

보조 기법: 조건 검사 — `score < 0 || score > 100`에서 각 단순 조건이 결과를 독립적으로 결정하는 조합은 P1(첫 조건 참), P2(첫 거짓·둘째 참), P3~P6(둘 다 거짓)으로 이미 덮임. 루프·데이터 흐름: 해당 없음.

복잡도 10 초과: 아니오.

### 2.2 화이트박스: GradeService.inputGrade(Long studentId, String courseId, int score)

코드 위치: GradeService.java:71-110 (validateInput 분리 후)

단순 조건: 학생 존재(1), 재학 상태(2), 과목 존재(3), 수강 등록(4), 이미 확정됨(5), 점수 범위(6) → V(G) = 7. 노드 13, 간선 18 → 18 − 13 + 2 = 7. 영역 7. 일치.

| 경로 | 조건 결과 | TC |
|------|-----------|----|
| P1 | 학생 없음 | TC-010 |
| P2 | 학생 있음, 휴학 | TC-011 |
| P3 | 재학, 과목 없음 | TC-012 |
| P4 | 과목 있음, 수강 미등록 | TC-013 |
| P5 | 수강 등록, 이미 확정 | TC-014 |
| P6 | 미확정, 점수 범위 밖 | TC-015 |
| P7 | 전부 통과 → 저장 | TC-016 |

복잡도 10 초과: 아니오 (7).

### 2.3 화이트박스: GradeService.confirmAll(String courseId)

코드 위치: GradeService.java:118-140 (반복문 내 return 제거 후: 실패 목록 수집 방식)

단순 조건: 과목 존재(1), for 루프(2), 성적 null(3), 이미 확정(4), 실패 목록 비어 있음(5) → V(G) = 6.

루프 검사 (수강생 수 n, 최대 200):

| 반복 횟수 | 입력 | TC |
|-----------|------|----|
| 0회 | 수강생 0명 | TC-020 |
| 1회 | 1명 | TC-021 |
| 2회 | 2명 | TC-022 |
| n−1회 | 199명 | TC-023 |
| n회 | 200명 | TC-024 |
| n+1회 | 201명 (정원 초과 데이터) | TC-025 |

데이터 흐름 검사: `failedIds` 리스트 — 정의(118행 초기화) → 사용(루프 안 add, 137행 isEmpty) 쌍 2개. TC-021(실패 0건)·TC-022(실패 1건 포함)로 덮임.

### 2.4 블랙박스: 성적 입력 (FR-012)

동치 분할:

| 조건 | 클래스 | 대표값 | 유효/무효 |
|------|--------|--------|-----------|
| score 0~100 | 범위 안 | 85 | 유효 |
| score 0~100 | 미만 | −5 | 무효 |
| score 0~100 | 초과 | 105 | 무효 |
| 학번 10자리 숫자 | 적합 | 2024123456 | 유효 |
| 학번 10자리 숫자 | 9자리 | 202412345 | 무효 |
| 학번 10자리 숫자 | 문자 포함 | 20241234A6 | 무효 |
| 학번 10자리 숫자 | 빈 값 | "" | 무효 |
| 과목코드 존재 | 존재 | CS101 | 유효 |
| 과목코드 존재 | 미존재 | XX999 | 무효 |
| 학생 상태 ∈ {재학} | 재학 | 재학 | 유효 |
| 학생 상태 ∈ {재학} | 휴학·제적 | 휴학 | 무효 |

경곗값:

| 경계 | 값 6개 |
|------|--------|
| score 0~100 | −1, 0, 1, 99, 100, 101 |
| 내부 경계 A/B 90 | 89, 90, 91 |
| 내부 경계 B/C 80 | 79, 80, 81 |
| 내부 경계 C/F 70 | 69, 70, 71 |

오류 예측: null 점수, 소수점 85.5(정수 필드), 같은 성적 2초 내 중복 제출, 교수 두 명이 같은 성적 동시 입력.

### 2.5 단위 테스트 케이스

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 |
|----|---------|------|-----------|-----------|
| TC-001 | FR-012 | score = −1 | 없음 | "INVALID" |
| TC-002 | FR-012 | score = 101 | 없음 | "INVALID" |
| TC-003 | FR-012 | score = 95 | 없음 | "A" |
| TC-004 | FR-012 | score = 85 | 없음 | "B" |
| TC-005 | FR-012 | score = 75 | 없음 | "C" |
| TC-006 | FR-012 | score = 50 | 없음 | "F" |
| TC-007 | FR-012 | score = 0 / 1 / 99 / 100 | 없음 | "F" / "F" / "A" / "A" |
| TC-008 | FR-012 | score = 89 / 90 / 91 | 없음 | "B" / "A" / "A" |
| TC-009 | FR-012 | score = 79 / 80 / 81, 69 / 70 / 71 | 없음 | "C"/"B"/"B", "F"/"C"/"C" |
| TC-010 | FR-012 | studentId = 9999999999 | 스텁: 학생 없음 | StudentNotFoundException, 저장 0건 |
| TC-011 | FR-012 | studentId = 2024123456 | 스텁: 휴학 | InvalidStudentStatusException("휴학"), 저장 0건 |
| TC-012 | FR-012 | courseId = XX999 | 스텁: 재학 | CourseNotFoundException, 저장 0건 |
| TC-013 | FR-012 | courseId = CS101, 수강 미등록 | 스텁: 재학 | NotEnrolledException, 저장 0건 |
| TC-014 | FR-012 | 기존 성적 status = CONFIRMED | 스텁: 재학, 수강 등록 | GradeAlreadyConfirmedException, 저장 0건 |
| TC-015 | FR-012 | score = 105 | 스텁: 재학, 수강 등록, 미확정 | IllegalArgumentException("0~100"), 저장 0건 |
| TC-016 | FR-012 | score = 85, 2024123456, CS101 | 스텁: 재학, 수강 등록, 미확정 | 저장 1건, status = ENTERED, letter = "B" |
| TC-017 | FR-012 | score = null | 스텁: 재학 | IllegalArgumentException, 저장 0건 |
| TC-018 | FR-012 | 동일 요청 2회 (간격 1초) | 스텁: 재학 | 저장 1건, 두 번째는 GradeAlreadyEnteredException |
| TC-020 | FR-012 | courseId = CS101, 수강생 0명 | 없음 | 확정 0건, 실패 목록 비어 있음, 반환 true |
| TC-021 | FR-012 | 수강생 1명, 성적 입력됨 | 없음 | 확정 1건 |
| TC-022 | FR-012 | 수강생 2명, 1명 성적 null | 없음 | 확정 1건, 실패 목록 1건, 반환 false |
| TC-023 | FR-012 | 수강생 199명 | 없음 | 확정 199건 |
| TC-024 | FR-012 | 수강생 200명 | 없음 | 확정 200건 |
| TC-025 | FR-012 | 수강생 201명 | 없음 | 확정 201건 (정원은 enrollment 책임. grade는 거부하지 않음) |
| TC-030 | FR-013 | studentId = 2024123456, 학기 = 2026-1 | 성적 3건 존재 | 3건 반환, 등급·점수 포함 |
| TC-031 | FR-013 | 성적 0건 | 없음 | 빈 목록, 예외 없음 |
| TC-032 | FR-014 | gradeId = 77, reason = "채점 오류" | status = CONFIRMED, 이의신청 기간 내 | status = OBJECTED, 알림 1회 |
| TC-033 | FR-014 | reason = "" | status = CONFIRMED | IllegalArgumentException, 상태 불변 |
| TC-034 | FR-014 | 이의신청 기간 종료 후 | status = CONFIRMED | ObjectionPeriodClosedException |
| TC-035 | FR-014 | status = ENTERED (미확정) | | InvalidStateException("확정 전 이의신청 불가") |

## 3. 통합 테스트

### 스레드: 성적 입력

참여: GradeController → GradeService → StudentRepository(스텁) → GradeCalculator → GradeRepository(H2)
기준: design-oo.md SD-03

| TC | 요구 ID | 입력 | 실행 조건 (스텁 포함) | 기대 결과 |
|----|---------|------|-----------------------|-----------|
| TC-101 | FR-012 | POST /grades {2024123456, CS101, 85} | 교수 권한, 스텁 재학 | 201, 응답 letter "B", DB 1건 |
| TC-102 | FR-012 | 같은 요청, score 105 | 교수 권한 | 400, 메시지 "0~100", DB 0건 |
| TC-103 | FR-012 | 같은 요청 | 학생 권한 | 403, DB 0건 |
| TC-104 | FR-012 | 같은 요청 | 스텁 휴학 | 409, 메시지 "휴학", DB 0건 |
| TC-105 | FR-012 | 같은 요청 | 스텁이 타임아웃(3초) | 503, DB 0건, 로그 1건 |

메시지 확인 항목:
- [ ] GradeController → GradeService.inputGrade 1회
- [ ] GradeService → StudentRepository.findStatus 1회 (저장 전)
- [ ] GradeService → GradeCalculator.calculateGrade 1회
- [ ] GradeService → GradeRepository.save 1회 (성공 경로만)

### 스레드: 성적 조회

참여: GradeController → GradeService → GradeRepository
기준: SD-04

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 |
|----|---------|------|-----------|-----------|
| TC-111 | FR-013 | GET /grades?student=2024123456&term=2026-1 | 본인 학생 권한 | 200, 3건 |
| TC-112 | FR-013 | 같은 요청 | 다른 학생 권한 | 403 |
| TC-113 | FR-013 | 같은 요청 | 교수 권한(담당 과목) | 200, 담당 과목만 |

### 스레드: 이의신청

참여: GradeController → GradeService → GradeRepository → NotificationService(스텁)
기준: SD-05

| TC | 요구 ID | 입력 | 실행 조건 | 기대 결과 |
|----|---------|------|-----------|-----------|
| TC-121 | FR-014 | POST /grades/77/objection {"채점 오류"} | 본인, 기간 내 | 200, status OBJECTED, 알림 스텁 호출 1회 |
| TC-122 | FR-014 | 같은 요청 | 기간 종료 | 409 |

사용 기반 하위층 (1~3층) 케이스는 2절 단위 테스트와 동일하므로 별도 작성하지 않는다. 4층 GradeService는 3층 실제 H2 저장소로 재실행한다 (TC-010~035를 스텁 대신 H2로).

## 4. 검증 테스트 (요구 기반)

| TC | 요구 ID | 시나리오 | 입력 | 기대 결과 |
|----|---------|----------|------|-----------|
| TC-201 | FR-012 | 교수가 한 과목 수강생 30명 성적을 입력하고 확정한다 | 30건 점수 | 30건 확정, 학생별 등급 표시 |
| TC-202 | FR-013 | 학생이 이번 학기 성적을 조회한다 | 학번 | 수강 과목 전부, 평점 계산값 |
| TC-203 | FR-014 | 학생이 확정 성적에 이의신청하고 교수가 수정한다 | 사유 | 상태 OBJECTED → 수정 → CONFIRMED, 알림 2회 |
| TC-204 | FR-012 | 확정 후 교수가 임의 수정을 시도한다 | 새 점수 | 거부. 이의신청 절차 안내 |

## 5. 시스템 테스트

| TC | NFR ID | 항목 | 측정 방법 | 합격 수치 |
|----|--------|------|-----------|-----------|
| TC-301 | NFR-001 | 성적 조회 응답 | 스테이징, 동시 1,000 세션, 10분, p95 | 2.0초 이하 |
| TC-302 | NFR-002 | 동시 접속 | 1,000 세션 유지 중 오류율 | 0.1% 이하 |
| TC-303 | NFR-004 | 권한 | 학생 토큰으로 성적 입력·확정 API 전 경로 호출 | 100% 403 |
| TC-304 | NFR-001 | 실제 학적 DB 연동 응답 | 스텁 대신 전산실 DB, p95 | 2.0초 이하 |
| TC-305 | NFR-003 | DB 단절 복구 | 입력 중 DB 연결 차단 30초 | 데이터 손실 0, 복구 후 재시도 성공 |

## 6. 요구사항 추적표

| 요구 ID | 설계 모듈 | TC 목록 | 커버 |
|---------|-----------|---------|------|
| FR-012 | grade.GradeService, GradeCalculator | TC-001~025, 101~105, 201, 204 | 예 |
| FR-013 | grade.GradeService | TC-030~031, 111~113, 202 | 예 |
| FR-014 | grade.GradeService | TC-032~035, 121~122, 203 | 예 |
| FR-021 | grade.GradeReportPrinter | 없음 (미구현) | 아니오 |
| NFR-001 | 전체 | TC-301, 304 | 예 |
| NFR-002 | 전체 | TC-302 | 예 |
| NFR-003 | 전체 | TC-305 | 예 |
| NFR-004 | 전체 | TC-303 | 예 |

커버되지 않은 요구: 1건 (FR-021, 구현 후 추가 예정)

## 7. 판정

| 항목 | 기준 | 결과 |
|------|------|------|
| 기초 경로 커버리지 | 우선 대상 3함수 100% | 통과 (6+7+6 경로 전부) |
| 경곗값 | 범위 조건마다 6개 | 통과 (외부 6 + 내부 경계 9) |
| 동치 분할 | 유효·무효 전부 | 통과 (유효 4, 무효 7) |
| FR 커버 | 100% | 미달 (FR-021 미구현, 3/4) |
| NFR 측정 방법 | 100% | 통과 |
| 통합 시퀀스 커버 | 100% | 통과 (SD-03, 04, 05) |

계획 판정: **미완성** (FR-021 구현 후 케이스 추가 시 완성). 구현된 범위에 한해서는 기준 전부 통과.

## 8. 실행 이력

| 날짜 | 수준 | 실행 | 합격 | 실패 TC | 비고 |
|------|------|------|------|---------|------|
| 2026-03-27 | 단위 | 35 | 33 | TC-008 (90 → "B" 반환), TC-018 (중복 저장 2건) | `>= 90`이 `> 90`으로 작성됨. 중복 제출 검사 없음 |
| 2026-03-28 | 단위 | 35 | 35 | 없음 | 수정 커밋 c91e4a7 |
| 2026-03-28 | 통합 | 13 | 12 | TC-105 (타임아웃 시 500) | 예외 매핑 누락 |
| 2026-03-29 | 통합 | 13 | 13 | 없음 | |
| 2026-03-29 | 검증 | 4 | 4 | 없음 | 교무처 담당자 입회 |
| 2026-03-31 | 시스템 | 5 | 4 | TC-304 (p95 2.6초) | 학적 DB 조회에 인덱스 없음. 전산실 협의 |

TC-008 실패는 경곗값 분석이 아니면 발견되지 않았을 결함이다 (대표값 95, 85로는 통과).

## 9. 다음 행동

- [ ] TC-304: 전산실에 학적 DB `student.status` 인덱스 요청. 담당 PM, 기한 2026-04-03
- [ ] FR-021 구현 후 TC-040~, TC-131~ 추가. 담당 책임 프로그래머
- [ ] $sw-sqa 호출: 추적표(6절)와 실행 이력(8절) 입력
