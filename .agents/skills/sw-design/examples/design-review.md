# 학사관리 시스템 설계 검증 결과서
> 작성: 2026-03-20 | 버전: v1.0 | 입력: design.md v0.1 (1차), design.md v0.2 (2차)

**최종 판정: 합격 (2차)**
1차(design.md v0.1)는 내용 결합 1건, 공통 결합 2건, 순환 의존 1건, 쓰레기통 패키지 1개로 **불합격**. 개선안을 반영한 2차(v0.2)에서 불허 0건, 검토 1건(admin 응집도)이며 개선안이 있어 **합격**으로 판정한다. 검토 1건은 조건부가 아니라 "다음 유지보수 시 분할 권장"으로 기록한다.

## 0. 입력 정리

### 0.1 검증 기준
| 항목 | 기준 |
|------|------|
| 결합도 상한 | 스탬프 (제어는 검토, 외부·공통·내용 불허) |
| 응집도 하한 | 통신적 (절차적·시간적 검토, 논리적·우연적 불허) |
| Fan-Out 상한 | 7 |
| 순환 의존 | 0 |

### 0.2 패키지 목록 (1차, design.md v0.1)
| ID | 패키지 | 계층 | 책임(한 문장) | 포함 요소 |
|----|--------|------|--------------|-----------|
| P1 | academic | 응용 | 학적을 등록·조회·변경하고 기존 DB와 맞춘다 | M1.1~M1.4 |
| P2 | grade | 응용 | 성적의 입력·계산·조회·이의·출력을 처리한다 | M2.1~M2.6 |
| P3 | enrollment | 응용 | 수강신청의 조회·신청·승인·통계를 처리한다 | M3.1~M3.4 |
| P4 | common | 공통 | 날짜 유틸, 등급 계산 함수, 전역 설정값, DB 연결, 알림 전송을 담는다 | DateUtil, GradeUtil, GlobalConfig, DbConn, Notifier |
| P5 | ui | 표현 | 화면 요청을 받아 응용 계층에 전달한다 | 4개 Screens |

의심 표시: **P4 common** — 이름이 common이고 책임 문장에 쉼표로 다섯 가지가 나열됨(논리적 이하 의심). P2의 "입력·계산·조회·이의·출력"은 모두 성적 엔티티를 다루므로 통신적 가능.

### 0.3 의존 화살표 목록 (1차)
| No | From | To | 의존 형태 | 주고받는 것 |
|----|------|----|-----------|-------------|
| D1 | ui | academic | 메서드 호출 | student_id, Student 구조 |
| D2 | ui | grade | 메서드 호출 | [GradeRecord], professor_id |
| D3 | ui | enrollment | 메서드 호출 | EnrollmentRequest |
| D4 | grade | enrollment | **필드 직접 접근** | `enrollment.status == "확정"` 읽기 + 이의 수정 시 `enrollment.gradeLocked = true` 쓰기 |
| D5 | grade | common | 전역 참조 | `GlobalConfig.gradeTable`, `GlobalConfig.gradeInputPeriod` (가변, 교무처가 바꿈) |
| D6 | grade | common | 메서드 호출 | GradeUtil.toLetter(score) |
| D7 | enrollment | grade | 메서드 호출 | `grade.isCourseGraded(course_code, term)` — 성적 입력 완료 과목은 신청 취소 불가 |
| D8 | enrollment | common | 전역 참조 | `GlobalConfig.maxCredit`, `GlobalConfig.enrollPeriod` (가변) |
| D9 | academic | common | 메서드 호출 | DbConn.legacy() 연결 객체 |
| D10 | grade | common | 메서드 호출 | Notifier.send(user_id, body) |
| D11 | enrollment | common | 메서드 호출 | Notifier.send(user_id, body) |
| D12 | academic | common | 메서드 호출 | DateUtil.now() |

## 1. 의존 행렬과 지표 (1차)

| From \ To | academic | grade | enrollment | common | ui | Fan-Out |
|-----------|----------|-------|------------|--------|----|---------|
| academic | – | | | 2 | | 1 |
| grade | | – | 1 | 3 | | 2 |
| enrollment | | 1 | – | 2 | | 2 |
| common | | | | – | | 0 |
| ui | 1 | 1 | 1 | | – | 3 |
| **Fan-In** | 1 | 2 | 2 | 4 | 0 | |

(Fan-Out은 의존하는 패키지 수. 화살표 수가 아님)

| 패키지 | Fan-In | Fan-Out | 불안정도 I | 해석 |
|--------|--------|---------|-----------|------|
| ui | 0 | 3 | 1.00 | 최상위 진입점. 정상 |
| academic | 1 | 1 | 0.50 | |
| grade | 2 | 2 | 0.50 | enrollment와 서로 의존 → 순환 |
| enrollment | 2 | 2 | 0.50 | grade와 서로 의존 → 순환 |
| common | 4 | 0 | 0.00 | 가장 안정. 그런데 안에 **가변 전역 설정**이 있음. 안정해야 할 곳이 자주 바뀐다 |

## 2. 결합도 판정 (1차)

### 2.1 화살표별
| No | From → To | 주고받는 것 | 등급 | 판정 |
|----|-----------|-------------|------|------|
| D1 | ui → academic | student_id, Student 구조 | 스탬프 | 허용 |
| D2 | ui → grade | [GradeRecord], professor_id | 스탬프 | 허용 |
| D3 | ui → enrollment | EnrollmentRequest | 스탬프 | 허용 |
| D4 | grade → enrollment | enrollment.status 읽기, gradeLocked 쓰기 | **내용** | **불허** |
| D5 | grade → common | GlobalConfig 가변 전역 | **공통** | **불허** |
| D6 | grade → common | score → letter | 데이터 | 허용 |
| D7 | enrollment → grade | course_code, term → boolean | 데이터 | 허용 (단 D4와 양방향) |
| D8 | enrollment → common | GlobalConfig 가변 전역 | **공통** | **불허** |
| D9 | academic → common | DB 연결 객체 | 외부 | **불허** (연결 객체를 넘겨 받아 academic이 직접 쿼리) |
| D10 | grade → common | user_id, body | 데이터 | 허용 |
| D11 | enrollment → common | user_id, body | 데이터 | 허용 |
| D12 | academic → common | 없음 → datetime | 데이터 | 허용 |

근거:
- D4: grade 패키지의 M2.5가 `enrollment.gradeLocked = true`로 **다른 패키지 객체의 필드를 직접 수정**한다. enrollment는 자기 상태가 언제 바뀌는지 통제할 수 없다. 읽기(`status == "확정"`)도 enrollment 내부 표현(문자열 "확정")에 의존한다. 질문표 1번 "예" → 내용 결합.
- D5, D8: `GlobalConfig`는 교무처가 기준값 설정 화면에서 바꾸는 **가변 전역 상태**다. grade와 enrollment가 같은 전역을 읽고, admin 기능이 쓴다. 누가 언제 바꿨는지 추적 불가. 질문표 2번 "예" → 공통 결합.
- D9: `DbConn.legacy()`가 연결 객체를 돌려주고 academic이 그 연결로 직접 SQL을 실행한다. 기존 학적 DB의 테이블 구조에 academic이 직접 의존 → 외부 결합. (연결을 감싼 Gateway가 메서드를 제공했다면 데이터 결합)
- D1~D3: 구조를 넘기지만 받는 쪽이 전 필드를 쓴다. 스탬프 허용.
- D7: course_code, term만 넘기고 boolean을 받는다. 데이터 결합. 단 D4(grade → enrollment)와 합쳐 양방향.

### 2.2 패키지 쌍 요약 (최악 등급)
| 쌍 | 화살표 | 등급 | 양방향 |
|----|--------|------|--------|
| ui – academic | D1 | 스탬프 | 아니오 |
| ui – grade | D2 | 스탬프 | 아니오 |
| ui – enrollment | D3 | 스탬프 | 아니오 |
| grade – enrollment | D4, D7 | **내용** | **예** |
| grade – common | D5, D6, D10 | **공통** | 아니오 |
| enrollment – common | D8, D11 | **공통** | 아니오 |
| academic – common | D9, D12 | **외부** | 아니오 |

### 2.3 숨은 의존
| 파생 요소 (위치) | 원본 (위치) | 갱신 책임 |
|-----------------|-------------|-----------|
| COURSE.applied_count (enrollment) | ENROLLMENT 행 수 (enrollment) | 같은 패키지. M3.2 등록 시 +1, M3.3 반려·취소 시 -1. 명시됨 |
| GRADE.letter (grade) | SETTING 등급기준표 (common/admin) | 기준표가 바뀌어도 이미 계산된 letter는 재계산하지 않음(스냅샷). **이 규칙이 문서에 없음** → 설계서에 명시 필요 |
| 성적 조회의 평점평균 (grade) | GRADE.letter, COURSE.credit | 조회 시 계산. 저장 안 함. 문제 없음 |

## 3. 응집도 판정 (1차)

| ID | 패키지 | 같이 있는 이유 | 등급 | 판정 |
|----|--------|---------------|------|------|
| P1 | academic | 모두 STUDENT 엔티티를 다룸 | 통신적 | 허용 |
| P2 | grade | 모두 GRADE 엔티티를 다룸. 검증 → 계산 → 저장은 순차적이지만 조회·이의·출력은 데이터 공유만 | 통신적 | 허용 |
| P3 | enrollment | 모두 ENROLLMENT 엔티티를 다룸 | 통신적 | 허용 |
| P4 | common | 날짜 유틸, 등급 함수, 전역 설정, DB 연결, 알림이 "공통으로 쓰여서" 모임. 서로 데이터·순서·시점 관계 없음 | **논리적** | **불허** |
| P5 | ui | 모두 화면 요청 처리 | 통신적(사용자 요청이라는 같은 입력) | 허용 |

근거:
- P4: 질문표를 좋은 것부터 물으면 1~5번 모두 "아니오". 6번 "종류가 비슷해서(유틸이라서) 모였는가" → "예". 논리적 응집. 안의 요소 중 `GradeUtil.toLetter`는 성적 규칙이고 `Notifier`는 독립 기능이며 `GlobalConfig`는 상태다. 하나를 고치면 Fan-In 4개 패키지 전부가 재테스트 대상이다.

이동 후보:
| 요소 | 현재 패키지 | 내부 연결 | 외부 연결 | 이동 대상 |
|------|------------|-----------|-----------|-----------|
| GradeUtil.toLetter | common | 0 | 1 (grade만 사용) | grade 또는 rules |
| Notifier | common | 0 | 2 (grade, enrollment) | 별도 패키지(admin/notice) |
| GlobalConfig | common | 0 | 2 (grade, enrollment) + 기준값 설정 화면이 씀 | 설정 저장소(SettingRepo) + 조회 메서드 |
| DbConn | common | 0 | 1 (academic) | 인프라 패키지(repo) |

## 4. 구조 결함 (1차)

| 결함 | 결과 | 관련 패키지 | 근거 |
|------|------|------------|------|
| 순환 의존 | **있음** | grade ↔ enrollment | D4, D7. grade는 수강 확정 여부를, enrollment는 성적 입력 완료 여부를 서로 묻는다 |
| 계층 역행 | 없음 | | 응용 → 공통, 표현 → 응용 방향만 |
| 허브 | 없음 | | Fan-In·Fan-Out 동시 높음 없음 |
| 쓰레기통 | **있음** | common | Fan-In 4 + 논리적 응집 |
| 안정성 위반 | **있음** | common | I = 0(가장 안정)인데 안에 가변 GlobalConfig가 있어 교무처 설정 변경마다 바뀜. "안정해야 할 곳이 자주 바뀐다" |
| 고립 | 없음 | | |
| 정보 은닉 위반 | **있음** | grade → enrollment | D4 내용 결합 |

## 5. 판정과 개선안

### 5.1 종합 판정표 (1차)
| 항목 | 결과 | 판정 |
|------|------|------|
| 내용·공통·외부 결합 화살표 수 | 4 (D4, D5, D8, D9) | 불합격 |
| 제어 결합 화살표 수 | 0 | 합격 |
| 스탬프(검토) 화살표 수 | 0 (D1~D3은 전 필드 사용) | 합격 |
| 순환 의존 수 | 1 | 불합격 |
| 계층 역행 수 | 0 | 합격 |
| 최대 Fan-Out | 3 | 합격 |
| 응집도 불허 패키지 수 | 1 (common) | 불합격 |
| 응집도 검토 패키지 수 | 0 | 합격 |

**1차 판정: 불합격.**

### 5.2 개선안
| 우선 | 대상 | 문제 | 개선 기법 | 효과 | 비용 |
|------|------|------|-----------|------|------|
| 1 | D4 grade → enrollment | 내용 결합, 정보 은닉 위반 | enrollment 내부 필드 접근을 **공개 메서드/저장소 조회**로 바꾼다. `enrollment.status == "확정"` → `EnrollmentRepo.listByCourse(code, term)`(확정만 반환). `enrollment.gradeLocked = true` → 이 플래그 자체를 없애고, 취소 가능 여부는 enrollment가 grade에게 묻는 D7로만 판단 | 내용 결합 제거. grade가 enrollment 내부 표현을 모름 | 낮음. M2.2, M2.5 수정 |
| 1 | D4 + D7 순환 | grade ↔ enrollment | D4를 위와 같이 바꾸면 grade → enrollment 방향은 "수강 데이터 읽기"만 남는다. 이것을 **repo 패키지의 EnrollmentRepo 경유**로 내리면 패키지 간 직접 화살표가 사라진다. D7(enrollment → grade `isCourseGraded`)도 **GradeRepo.exists(code, term)** 조회로 바꿔 repo 경유. 두 응용 패키지가 서로를 부르지 않고 인프라만 부른다 | 순환 제거. 두 기능을 독립 개발·테스트 가능 | 낮음 |
| 1 | D5, D8 GlobalConfig | 공통 결합, 안정성 위반 | 가변 전역을 없애고 **SettingRepo**(SETTING 테이블 접근 모듈)를 둔다. 필요한 곳이 `SettingRepo.get(key, term)`으로 **값을 받아** 쓴다. 기준값 설정 화면은 `SettingRepo.set`으로 쓴다 | 공통 → 데이터 결합. 누가 언제 바꿨는지 SETTING 이력으로 추적 | 중간. SETTING 테이블 추가, 읽는 곳 5군데 수정 |
| 2 | D9 DbConn | 외부 결합 | 연결 객체를 넘기지 않고 **LegacyStudentGateway**가 `fetchChanges(since)`, `pushStudent(student)` 메서드를 제공. academic은 기존 DB 테이블 구조를 모른다 | 외부 → 데이터 결합. 기존 DB 뷰가 바뀌어도 Gateway만 수정 | 낮음 |
| 2 | P4 common | 논리적 응집, 쓰레기통 | **목적별로 분할**: GradeUtil → `rules`(도메인 규칙: 등급·기간·시간표 판정), DbConn·GlobalConfig 후속 → `repo`(인프라: 접근 모듈 7개 + Gateway), Notifier → `admin`(M4.2 알림, 기준값 설정과 함께), DateUtil → 표준 라이브러리로 대체하고 삭제 | common 소멸. rules는 기능적, repo는 통신적(모두 저장소 접근) | 중간. 패키지 2개 신설 |
| 3 | 숨은 의존 GRADE.letter | 스냅샷 규칙 미문서화 | design.md 2.4 자료 추상화에 "letter는 계산 시점 기준표의 스냅샷. 기준표 변경 시 재계산하지 않음"을 명시 | 유지보수 시 오해 방지 | 없음 |

### 5.3 2차 의존도 (design.md v0.2)
```
 ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
 │ academic     │   │ grade        │   │ enrollment   │   │ admin        │   [응용]
 └──┬───────────┘   └──┬───────┬───┘   └──┬───────┬───┘   └──┬───────────┘
    │                  │       │   알림    │       │   알림   │
    │                  │       └──────────┼───────┼─────────▶│
    │                  │                  │       │          │
    │                  │ score,기준표,기간  │       │ 시간표,기간│
    │                  ▼                  ▼       ▼          │
    │             ┌───────────────────────────────────┐      │
    │             │ rules  (등급·기간·충돌 판정)          │      │   [도메인]
    │             └───────────────────────────────────┘      │
    ▼                             ▼                          ▼
 ┌─────────────────────────────────────────────────────────────┐
 │ repo  (StudentRepo … SettingRepo, NoticeRepo, LegacyGateway) │   [인프라]
 └─────────────────────────────────────────────────────────────┘
          ▲
          │ (ui → academic/grade/enrollment/admin 은 생략)
```

| No | From | To | 의존 형태 | 주고받는 것 | 등급 |
|----|------|----|-----------|-------------|------|
| D1' | ui → academic/grade/enrollment/admin | 메서드 호출 | 구조(전 필드 사용) | 스탬프 |
| D2' | academic → repo | 메서드 호출 | student_id, Student, since | 데이터/스탬프 |
| D3' | grade → repo | 메서드 호출 | GradeRecord, id들, setting key | 데이터/스탬프 |
| D4' | grade → rules | 메서드 호출 | score+기준표, now+기간 | 데이터 |
| D5' | grade → admin | 메서드 호출 | user_id, kind, body | 데이터 |
| D6' | enrollment → repo | 메서드 호출 | Enrollment, id들, setting key | 데이터/스탬프 |
| D7' | enrollment → rules | 메서드 호출 | timeslot, now+기간 | 데이터 |
| D8' | enrollment → admin | 메서드 호출 | user_id, kind, body | 데이터 |
| D9' | admin → repo | 메서드 호출 | setting key·value, Notice | 데이터/스탬프 |

| From \ To | academic | grade | enrollment | admin | rules | repo | Fan-Out |
|-----------|----------|-------|------------|-------|-------|------|---------|
| ui | 1 | 1 | 1 | 1 | | | 4 |
| academic | – | | | | | 1 | 1 |
| grade | | – | | 1 | 1 | 1 | 3 |
| enrollment | | | – | 1 | 1 | 1 | 3 |
| admin | | | | – | | 1 | 1 |
| rules | | | | | – | | 0 |
| repo | | | | | | – | 0 |
| **Fan-In** | 1 | 1 | 1 | 3 | 2 | 4 | |

| 패키지 | Fan-In | Fan-Out | I | 해석 |
|--------|--------|---------|---|------|
| ui | 0 | 4 | 1.00 | 진입점 |
| academic | 1 | 1 | 0.50 | |
| grade | 1 | 3 | 0.75 | |
| enrollment | 1 | 3 | 0.75 | |
| admin | 3 | 1 | 0.25 | 알림 제공자. 안정해야 함 |
| rules | 2 | 0 | 0.00 | 순수 규칙. 가장 안정. 적절 |
| repo | 4 | 0 | 0.00 | 인프라. 안정. 적절 |

행렬이 상삼각 → 순환 없음. 모든 화살표가 I 큰 쪽 → 작은 쪽.

## 6. 재검증과 전후 비교

| 항목 | 1차 | 2차 | 판정 |
|------|-----|-----|------|
| 내용·공통·외부 결합 화살표 수 | 4 | 0 | 합격 |
| 제어 결합 화살표 수 | 0 | 0 | 합격 |
| 순환 의존 수 | 1 | 0 | 합격 |
| 계층 역행 수 | 0 | 0 | 합격 |
| 응집도 통신적 미만 패키지 수 | 1 | 0 | 합격 |
| 최대 Fan-Out | 3 | 4 (ui) | 합격 (≤ 7) |
| 패키지 수 | 5 | 7 | 허용 (분할로 증가) |

2차 응집도:
| 패키지 | 같이 있는 이유 | 등급 | 판정 |
|--------|---------------|------|------|
| rules | 등급·기간·충돌 세 판정 함수. 서로 데이터 공유 없음. "도메인 규칙"이라는 종류로 모임 | 논리적에 가까우나 세 함수가 모두 **순수 함수이고 변경 사유가 "학사 규정 변경" 하나**로 같음 | 허용. 변경 사유가 하나면 기능적 응집의 기준(한 목적)을 만족한다고 본다. 단, 규칙이 10개를 넘으면 엔티티별로 분할 |
| repo | 모두 "저장소 접근"이라는 같은 종류 | 논리적 | 조건부 허용. 인프라 계층의 관례적 패턴이며 각 Repo는 서로 독립. 책임 문장에 "데이터 접근 모듈만 둔다. 업무 규칙 금지"를 명시 |
| admin | 기준값 설정 + 알림. 서로 데이터·순서 관계 없음 | 논리적(관리 기능이라는 종류) | **검토**. Fan-In 3이고 알림은 grade·enrollment가 의존. 기준값 설정이 바뀌면 알림 의존 패키지가 재테스트 대상. 다음 유지보수 시 `notice` 패키지로 분리 권장 |

부작용 점검:
- ui의 Fan-Out이 3 → 4로 늘었으나 기준 7 이내.
- 새 패키지 rules, repo가 쓰레기통이 되지 않도록 책임 문장에 제약을 적었다(rules: "학사 규정 판정 함수만", repo: "데이터 접근 모듈만").
- 패키지 수 5 → 7. 전체 그림은 한 장에 들어간다.

## 7. 확정
| 항목 | 내용 |
|------|------|
| 최종 판정 | 합격 (검토 1건: admin 응집도, 분할 권장 기록) |
| 설계서 반영 버전 | design.md v1.0 (v0.2에 숨은 의존 스냅샷 규칙 추가) |
| 검토자 | 개발사 설계 담당, PM |
| 날짜 | 2026-03-20 |
