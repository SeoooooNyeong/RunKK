# 학사관리 시스템 설계서
> 작성: 2026-03-20 | 버전: v1.0 | 입력: srs.md v1.1

## 1. 설계 개요

### 1.1 설계 방식
구조적 설계. DFD Level 1·2를 모듈 트리로 변환하고, 표현 → 응용 → 도메인 → 인프라 4계층을 쓴다. 절차 설계는 의사코드와 의사결정표로 쓴다.

### 1.2 설계 원칙
- 응집도 하한: 통신적
- 결합도 상한: 스탬프 (제어는 검토, 외부·공통·내용 불허)
- Fan-Out 상한: 7
- 모듈 크기: 50~100줄. 넘으면 분해
- 저장소마다 접근 모듈 하나. 다른 모듈은 저장소를 직접 열지 않는다

### 1.3 SRS와의 관계
srs.md v1.1의 FR-001~FR-031(19건), NFR-001~NFR-010 전체를 다룬다. 선택 우선순위인 FR-031 이메일 발송은 설계에서 제외하고 알림함만 설계한다.

## 2. 데이터 설계

### 2.1 데이터 구조
| 구조명 | 출처(자료사전) | 키 | 속성 | 제약 |
|--------|---------------|----|------|------|
| STUDENT | 학적 파일 | student_id(학번) | name, dept_code, year, status, phone, guardian_phone(NULL) | status ∈ {재학,휴학,졸업,제적} |
| STUDENT_HISTORY | 학적변경이력 | history_id | student_id, field, old_value, new_value, reason, changed_by, changed_at | |
| COURSE | 수강 파일(과목) | course_code, term | name, professor_id, credit, timeslot, capacity, prereq_code(NULL) | |
| ENROLLMENT | 신청레코드 | enrollment_id | student_id, course_code, term, status, applied_at | status ∈ {확정,승인대기,반려,취소} |
| GRADE | 성적레코드 | student_id, course_code, term | score, letter, entered_by, entered_at, note(NULL) | 0 ≤ score ≤ 100 정수 |
| GRADE_HISTORY | 수정이력 | history_id | student_id, course_code, term, old_score, changed_by, changed_at, reason | |
| APPEAL | 이의신청 | appeal_id | student_id, course_code, term, reason, applied_at, decision(NULL), new_score(NULL), reply(NULL) | 과목당 1건 |
| SETTING | 기준값 파일 | setting_key, term | value, set_by, set_at | key ∈ {성적입력기간, 성적공개일, 수강신청기간, 학기최대학점, 등급기준표} |
| NOTICE | 알림 파일 | notice_id | user_id, kind, body, created_at, read_flag | |

자료사전 변환 규칙: `{수정이력}` 반복 → GRADE_HISTORY 별도 테이블. `(비고)` → note NULL 허용. `[확정 | 승인대기 | 반려 | 취소]` → status 코드 제약.

### 2.2 개체 관계도
```
STUDENT 1──N ENROLLMENT N──1 COURSE
   │                            │
   1                            1
   │                            │
   N                            N
GRADE ──────────────────────────┘   (GRADE는 STUDENT × COURSE × term)
   │
   1──N GRADE_HISTORY
   1──1 APPEAL (과목·학기당 최대 1)

STUDENT 1──N STUDENT_HISTORY
SETTING, NOTICE 는 독립
```

### 2.3 데이터 접근 모듈
| 데이터 구조 | 접근 모듈 | 제공 연산 |
|------------|-----------|-----------|
| STUDENT, STUDENT_HISTORY | StudentRepo | find(id), findByName(name), save(student), appendHistory(h) |
| COURSE | CourseRepo | find(code, term), search(cond), incrementApplied(code, term), decrementApplied(code, term) |
| ENROLLMENT | EnrollmentRepo | find(id), listByStudent(id, term), listByCourse(code, term), listPending(professor_id), save(e), updateStatus(id, status) |
| GRADE, GRADE_HISTORY | GradeRepo | find(sid, code, term), listByStudent(sid, term), listByCourse(code, term), save(g), appendHistory(h) |
| APPEAL | AppealRepo | find(id), exists(sid, code, term), save(a), decide(id, decision, new_score, reply) |
| SETTING | SettingRepo | get(key, term), set(key, term, value, by) |
| NOTICE | NoticeRepo | push(user_id, kind, body), listUnread(user_id), markRead(id) |
| 기존 학적 DB | LegacyStudentGateway | fetchChanges(since), pushStudent(student) |

### 2.4 자료 추상화
| 추상 데이터 | 구성 | 연산 |
|------------|------|------|
| GradeRecord | student_id, course_code, term, score, letter | toLetter(기준표), isEditable(now, 기간) |
| EnrollmentRequest | student_id, term, [course_code] | totalCredit(), hasTimeConflict(기존) |
| Term | year, semester | current(), contains(date) |

## 3. 구조 설계

### 3.1 모듈 트리
```
학사관리 시스템
├── [표현] WebUI
│   ├── AcademicScreens      (학적 등록·조회·변경 화면)
│   ├── GradeScreens         (성적 입력·조회·이의 화면)
│   ├── EnrollmentScreens    (개설 조회·신청·승인 화면)
│   └── AdminScreens         (기준값·통계·알림함 화면)
├── [응용] 학적 관리 (M1)
│   ├── M1.1 학생 등록
│   ├── M1.2 학적 조회
│   ├── M1.3 학적 변경
│   └── M1.4 학적 DB 동기화
├── [응용] 성적 관리 (M2)
│   ├── M2.1 수강생 목록 조회
│   ├── M2.2 성적 검증
│   ├── M2.3 등급 계산
│   ├── M2.4 성적 조회
│   ├── M2.5 이의신청 처리
│   └── M2.6 성적표 생성
├── [응용] 수강신청 관리 (M3)
│   ├── M3.1 개설 과목 조회
│   ├── M3.2 신청 검증·등록
│   ├── M3.3 승인 처리
│   └── M3.4 통계 집계
├── [응용] 공통 관리 (M4)
│   ├── M4.1 기준값 설정
│   └── M4.2 알림
├── [도메인] 규칙 (M5)
│   ├── M5.1 등급 기준표 적용
│   ├── M5.2 기간 판정
│   └── M5.3 시간표 충돌 판정
└── [인프라] 저장소 (M6)
    ├── StudentRepo, CourseRepo, EnrollmentRepo, GradeRepo, AppealRepo, SettingRepo, NoticeRepo
    └── LegacyStudentGateway
```

### 3.2 모듈 목록
| ID | 모듈 | 계층 | 책임(한 문장) | 상위 모듈 |
|----|------|------|--------------|-----------|
| M1 | 학적 관리 | 응용 | 학생의 학적을 등록·조회·변경하고 기존 학적 DB와 맞춘다 | 시스템 |
| M1.1 | 학생 등록 | 응용 | 학생등록정보를 검증해 STUDENT에 저장한다 | M1 |
| M1.2 | 학적 조회 | 응용 | 권한에 따라 학적정보를 돌려준다 | M1 |
| M1.3 | 학적 변경 | 응용 | 학적 항목을 바꾸고 이력을 남긴다 | M1 |
| M1.4 | 학적 DB 동기화 | 응용 | 기존 학적 DB 변경분을 받아 STUDENT에 반영하고 차이를 보고한다 | M1 |
| M2 | 성적 관리 | 응용 | 성적의 입력·계산·조회·이의·출력을 처리한다 | 시스템 |
| M2.1 | 수강생 목록 조회 | 응용 | 과목·학기의 확정 수강생을 돌려준다 | M2 |
| M2.2 | 성적 검증 | 응용 | 성적입력의 기간·범위·수강 여부를 검증한다 | M2 |
| M2.3 | 등급 계산 | 응용 | 검증된 점수에 등급을 붙여 저장한다 | M2 |
| M2.4 | 성적 조회 | 응용 | 본인 성적을 공개 여부에 따라 돌려준다 | M2 |
| M2.5 | 이의신청 처리 | 응용 | 이의신청을 접수하고 교수 결정을 반영한다 | M2 |
| M2.6 | 성적표 생성 | 응용 | 공개된 성적으로 PDF 성적표를 만든다 | M2 |
| M3 | 수강신청 관리 | 응용 | 수강신청의 조회·신청·승인·통계를 처리한다 | 시스템 |
| M3.1 | 개설 과목 조회 | 응용 | 조건에 맞는 개설 과목과 정원 현황을 돌려준다 | M3 |
| M3.2 | 신청 검증·등록 | 응용 | 수강신청을 검증해 확정 또는 승인대기로 등록한다 | M3 |
| M3.3 | 승인 처리 | 응용 | 승인대기 신청을 확정 또는 반려로 바꾼다 | M3 |
| M3.4 | 통계 집계 | 응용 | 과목·학과별 상태별 인원을 센다 | M3 |
| M4 | 공통 관리 | 응용 | 기준값 설정과 알림을 처리한다 | 시스템 |
| M4.1 | 기준값 설정 | 응용 | 기간·학점·등급표를 설정하고 이력을 남긴다 | M4 |
| M4.2 | 알림 | 응용 | 알림을 기록하고 사용자별로 돌려준다 | M4 |
| M5 | 규칙 | 도메인 | 등급·기간·시간표 규칙을 판정한다 | 시스템 |
| M5.1 | 등급 기준표 적용 | 도메인 | 점수와 기준표로 등급을 결정한다 | M5 |
| M5.2 | 기간 판정 | 도메인 | 일시가 기간 안인지 판정한다 | M5 |
| M5.3 | 시간표 충돌 판정 | 도메인 | 두 시간표가 겹치는지 판정한다 | M5 |
| M6 | 저장소 | 인프라 | 데이터 구조별 접근과 외부 DB 연동을 담당한다 | 시스템 |

M4의 책임 문장에 "과"가 들어 있다(기준값 설정 **과** 알림). 두 책임이 섞여 있으므로 검증 결과서(design-review.md)에서 응집도 검토 대상이다.

### 3.3 구조 지표
| 지표 | 값 | 기준 | 판정 |
|------|----|------|------|
| 깊이 | 3 (시스템 → M2 → M2.3) | ≤ 7 | 합격 |
| 최대 넓이 | 7 (M6 아래 Repo 7 + Gateway 1 = 8) | ≤ 7 | 검토 → M6를 StudentRepo 그룹과 나머지로 2단 분리 가능. 모두 같은 종류의 접근 모듈이라 현 상태 유지 |
| 최대 Fan-Out | 6 (M2.5 이의신청 처리: GradeRepo, AppealRepo, M5.1, M5.2, M4.2, EnrollmentRepo) | ≤ 7 | 합격 |
| 최대 Fan-In | 6 (SettingRepo: M2.2, M2.4, M3.2, M4.1, M5.1, M5.2) | 공용 모듈 | 합격. 읽기 전용 접근이며 변경 드묾 |

## 4. 인터페이스 설계

### 4.1 모듈 간 인터페이스 (주요 20건)
| No | 호출 | 피호출 | 넘기는 것 | 돌려받는 것 |
|----|------|--------|-----------|-------------|
| I1 | GradeScreens | M2.1 | course_code, term, professor_id | [student_id, name, dept] |
| I2 | GradeScreens | M2.2 | [GradeRecord], professor_id, now | 저장건수, [오류(student_id, 사유)] |
| I3 | M2.2 | M5.2 | now, 기간(start, end) | boolean |
| I4 | M2.2 | SettingRepo | "성적입력기간", term | 기간(start, end) |
| I5 | M2.2 | EnrollmentRepo | course_code, term | [student_id] (확정만) |
| I6 | M2.2 | M2.3 | [GradeRecord](검증 통과) | 저장건수 |
| I7 | M2.3 | M5.1 | score, 등급기준표 | letter |
| I8 | M2.3 | GradeRepo | GradeRecord | void |
| I9 | GradeScreens | M2.4 | student_id, term, now | [과목, 학점, letter 또는 "미공개"], 평점평균 |
| I10 | M2.4 | SettingRepo | "성적공개일", term | date |
| I11 | GradeScreens | M2.5 | appeal(student_id, course_code, term, reason), now | 접수 결과 |
| I12 | M2.5 | AppealRepo | student_id, course_code, term | boolean(존재 여부) |
| I13 | M2.5 | M4.2 | user_id, kind, body | void |
| I14 | EnrollmentScreens | M3.2 | EnrollmentRequest, now | [course_code, status, 거부사유] |
| I15 | M3.2 | M5.3 | timeslot_new, [timeslot_existing] | boolean |
| I16 | M3.2 | CourseRepo | course_code, term | capacity, applied_count, prereq_code |
| I17 | M3.2 | EnrollmentRepo | Enrollment | enrollment_id |
| I18 | EnrollmentScreens | M3.3 | enrollment_id, decision, reason, professor_id | 승인 결과 |
| I19 | M3.3 | EnrollmentRepo | enrollment_id, status | void |
| I20 | M1.4 | LegacyStudentGateway | since(datetime) | [student 변경분] |

I2, I6은 GradeRecord 목록 전체를 넘긴다(스탬프). 받는 쪽이 모든 필드를 쓰므로 허용. I14는 EnrollmentRequest 구조를 넘기며 전 필드 사용. 허용.

오류 시 동작:
| No | 오류 조건 | 동작 |
|----|-----------|------|
| I2 | 기간 외 | 저장 0건, 오류 "기간 외" + 교무처 연락처 |
| I2 | 점수 범위 밖 1건 이상 | 전체 저장 안 함, 오류 목록 반환 (부분 저장 금지) |
| I11 | 7일 경과 / 중복 / 수강 내역 없음 | 각 사유로 거부 |
| I14 | 기간 외 | 전체 거부 |
| I20 | 외부 DB 연결 실패 | 3회 재시도 후 실패 보고, 다음 배치에서 재시도 |

### 4.2 사용자 인터페이스
| 화면 | 입력 검증 위치 | 호출 모듈 | 화면 전환 |
|------|---------------|-----------|-----------|
| 성적 입력 | 형식(숫자 여부)은 화면, 범위·기간·수강 여부는 M2.2 | M2.1 → M2.2 | 교수 홈 → 과목 선택 → 입력 (3단계, NFR-006) |
| 수강신청 | 형식은 화면, 충돌·학점·정원은 M3.2 | M3.1 → M3.2 | 학생 홈 → 과목 검색 → 신청 |
| 이의신청 | 사유 길이는 화면, 기한·중복은 M2.5 | M2.4 → M2.5 | 성적 조회 → 과목 선택 → 이의신청 |

### 4.3 외부 시스템 인터페이스
| 외부 시스템 | 호출 모듈 | 형식 | 빈도 | 실패 시 |
|------------|-----------|------|------|---------|
| 기존 학적 DB | LegacyStudentGateway | 전산실 제공 뷰(V_STUDENT_CHANGES) 읽기, 등록은 저장 프로시저 호출 | 하루 1회 02:00 배치 + 등록 시 즉시 | 3회 재시도, 실패 시 M4.2로 전산실 알림 |
| 통합 인증 | WebUI 공통 필터 | SSO 토큰 → user_id, user_type | 로그인마다 | 로그인 화면으로 |

## 5. 절차 설계

### 5.1 M2.2 성적 검증
```
성적 검증(records, professor_id, now):
    기간 ← SettingRepo.get("성적입력기간", records[0].term)
    IF NOT M5.2.기간 판정(now, 기간) THEN
        RETURN (0, [("*", "기간 외 입력. 교무처 02-000-0000")])
    수강생 ← EnrollmentRepo.listByCourse(records[0].course_code, records[0].term)   -- 확정만
    오류 ← []
    FOR EACH r IN records
        IF r.score 정수 아님 OR r.score < 0 OR r.score > 100 THEN
            오류.추가((r.student_id, "점수 범위 오류"))
        ELSE IF r.student_id NOT IN 수강생 THEN
            오류.추가((r.student_id, "수강생 아님"))
    IF 오류 비어있지 않음 THEN
        RETURN (0, 오류)
    RETURN M2.3.등급 계산(records)
```
오류 처리:
- 기간 외 → 저장 0, 안내 메시지
- 범위 오류 1건이라도 → 전체 저장 안 함 (부분 저장 시 교수가 어디까지 저장됐는지 모른다)

### 5.2 M2.3 등급 계산
```
등급 계산(records):
    기준표 ← SettingRepo.get("등급기준표", records[0].term)
    IF 과목이 상대평가 THEN
        records를 score 내림차순 정렬
        분포 ← 기준표.상대평가분포
        FOR i, r IN records
            r.letter ← 분포에서 순위 비율에 해당하는 등급
    ELSE
        FOR EACH r IN records
            r.letter ← M5.1.등급 기준표 적용(r.score, 기준표)
    FOR EACH r IN records
        기존 ← GradeRepo.find(r.student_id, r.course_code, r.term)
        IF 기존 존재 AND 기존.score ≠ r.score THEN
            GradeRepo.appendHistory(기존 → r)
        GradeRepo.save(r)
    RETURN records.길이
```

### 5.3 M5.1 등급 기준표 적용
```
등급 기준표 적용(score, 기준표):
    FOR EACH (등급, 하한) IN 기준표 (하한 내림차순)
        IF score ≥ 하한 THEN RETURN 등급
    RETURN "F"
```

### 5.4 M2.5 이의신청 처리 — 접수
| 조건 \ 규칙 | 1 | 2 | 3 | 4 |
|-------------|---|---|---|---|
| now ≤ 공개일 + 7일 | Y | Y | N | - |
| AppealRepo.exists | N | Y | - | - |
| 해당 과목 수강 확정 | Y | - | - | N |
| 동작: AppealRepo.save, M4.2 교수 알림 | X | | | |
| 동작: 거부 "중복 신청" | | X | | |
| 동작: 거부 "기간 경과" | | | X | |
| 동작: 거부 "수강 내역 없음" | | | | X |

### 5.5 M3.2 신청 검증·등록
```
신청 검증·등록(req, now):
    기간 ← SettingRepo.get("수강신청기간", req.term)
    IF NOT M5.2.기간 판정(now, 기간) THEN RETURN 전체 거부("기간 외")
    최대학점 ← SettingRepo.get("학기최대학점", req.term)
    기존 ← EnrollmentRepo.listByStudent(req.student_id, req.term)  -- 확정·승인대기
    누적학점 ← SUM(기존.credit)
    결과 ← []
    FOR EACH code IN req.course_codes
        과목 ← CourseRepo.find(code, req.term)
        IF M5.3.시간표 충돌 판정(과목.timeslot, 기존.timeslots) THEN
            결과.추가((code, "거부", "시간표 중복")); CONTINUE
        IF 누적학점 + 과목.credit > 최대학점 THEN
            결과.추가((code, "거부", "최대 학점 초과")); CONTINUE
        IF 과목.applied_count < 과목.capacity AND (과목.prereq 없음 OR 이수함(req.student_id, 과목.prereq)) THEN
            status ← "확정"; CourseRepo.incrementApplied(code, req.term)
        ELSE
            status ← "승인대기"
        EnrollmentRepo.save(Enrollment(req.student_id, code, req.term, status, now))
        기존.추가(과목); 누적학점 += 과목.credit
        결과.추가((code, status, NULL))
    RETURN 결과
```

## 6. 모듈 의존도

패키지 단위로 묶은 의존도. 화살표 옆은 주고받는 것.

```
 ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
 │ academic     │   │ grade        │   │ enrollment   │   │ admin        │   [응용]
 │ (M1)         │   │ (M2)         │   │ (M3)         │   │ (M4)         │
 └──┬───────────┘   └──┬───┬───┬───┘   └──┬───┬───┬───┘   └──┬───────────┘
    │                  │   │   │          │   │   │          │
    │ student_id       │   │   │ 확정 수강생│   │   │ 알림     │
    │                  │   │   └──────────┼───┼───┼─────────▶│ (M4.2)
    │                  │   │              │   │   │          │
    │                  │   │ score,기준표  │   │   │ 시간표    │
    │                  ▼   ▼              ▼   ▼   ▼          │
    │             ┌───────────────────────────────────┐      │
    │             │ rules (M5)  등급·기간·충돌 판정       │      │   [도메인]
    │             └───────────────┬───────────────────┘      │
    │                             │ (의존 없음)               │
    ▼                             ▼                          ▼
 ┌─────────────────────────────────────────────────────────────┐
 │ repo (M6)  StudentRepo … NoticeRepo, LegacyStudentGateway    │   [인프라]
 └─────────────────────────────────────────────────────────────┘
```

| No | From | To | 의존 형태 | 주고받는 것 |
|----|------|----|-----------|-------------|
| D1 | academic | repo | 메서드 호출 | student_id, Student 구조, since(datetime) |
| D2 | grade | repo | 메서드 호출 | GradeRecord, student_id, course_code, term, setting key |
| D3 | grade | rules | 메서드 호출 | score + 기준표, now + 기간 |
| D4 | grade | enrollment | 메서드 호출 (M2.2 → EnrollmentRepo 경유) | course_code, term → [student_id] |
| D5 | grade | admin | 메서드 호출 (M2.5 → M4.2) | user_id, kind, body |
| D6 | enrollment | repo | 메서드 호출 | Enrollment 구조, course_code, term, setting key |
| D7 | enrollment | rules | 메서드 호출 | timeslot, now + 기간 |
| D8 | enrollment | admin | 메서드 호출 (M3.3 → M4.2) | user_id, kind, body |
| D9 | admin | repo | 메서드 호출 | setting key·value, Notice 구조 |
| D10 | rules | (없음) | – | – |

D4는 grade가 enrollment의 데이터를 EnrollmentRepo를 통해 읽는 것이므로 실제 화살표는 grade → repo다. 패키지 경계 관점에서 "grade가 수강 데이터에 의존한다"는 사실은 남으므로 D4로 기록해 둔다. 검증은 design-review.md에서 한다.

## 7. 추적표
| 요구 ID | 담당 모듈 | 인터페이스 | 비고 |
|---------|-----------|-----------|------|
| FR-001 | M1.1 | StudentRepo.save, LegacyStudentGateway.pushStudent | |
| FR-002 | M1.2 | StudentRepo.find/findByName | 교수 권한은 EnrollmentRepo로 확인 |
| FR-003 | M1.3 | StudentRepo.save/appendHistory, EnrollmentRepo.updateStatus | 제적 시 수강 취소 |
| FR-004 | M1.4 | I20 | |
| FR-010 | M2.1 | I1 | |
| FR-011 | M2.2 | I2, I3, I4, I5 | |
| FR-012 | M2.3, M5.1 | I6, I7, I8 | |
| FR-013 | M2.2, M2.3 | I2 (수정도 같은 경로), GradeRepo.appendHistory | |
| FR-014 | M2.4 | I9, I10 | |
| FR-015 | M2.5 | I11, I12, I13 | 의사결정표 5.4 |
| FR-016 | M2.5 | AppealRepo.decide → M2.3 | |
| FR-017 | M2.6 | GradeRepo.listByStudent, StudentRepo.find | PDF 생성 라이브러리는 구현 단계 선택 |
| FR-020 | M3.1 | CourseRepo.search | |
| FR-021 | M3.2 | I14~I17 | |
| FR-022 | M3.3 | I18, I19, M4.2 | 기한 내 미처리 자동 반려는 배치 |
| FR-023 | M3.2 | EnrollmentRepo.updateStatus, CourseRepo.decrementApplied | |
| FR-024 | M3.4 | EnrollmentRepo.listByCourse | |
| FR-030 | M4.1 | SettingRepo.set | |
| FR-031 | M4.2 | NoticeRepo.push/listUnread | 이메일 제외 |
| NFR-001 | M2.4, M3.1 | 조회 경로 인덱스: GRADE(student_id, term), COURSE(term, dept) | |
| NFR-003 | WebUI 필터, M2.4 | user_id = student_id 검사 | |
| NFR-004 | M2.3 | GradeRepo.appendHistory | |
| NFR-008 | M4.1, M5.1 | 등급표를 SETTING에서 읽음 | |
| NFR-009 | M2.2 | 범위 검증 | |
| NFR-010 | M1.4 | 배치 + 차이 보고 | |

## 8. 변경 이력
| 버전 | 날짜 | 변경 내용 | 승인자 |
|------|------|-----------|--------|
| v0.1 | 03-15 | 초안 | |
| v0.2 | 03-18 | design-review v0.1 반영: common 패키지를 rules와 repo로 분할, grade → enrollment 내부 필드 접근을 EnrollmentRepo 경유로 변경 | |
| v1.0 | 03-20 | design-review v1.0 합격. 기준선 | 개발사 PM |
