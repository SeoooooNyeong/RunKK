# 런크크 산출물 안내 (큰 흐름)
> 작성: 2026-11-03 | 버전: v0.4 | 입력: docs/sw/ 산출물 11개의 머리 헤더(2026-11-03, sw-sdlc merge 뒤). 이전 판(v0.3, 2026-10-28) 입력: 같은 머리 헤더

이 폴더는 런크크 프로젝트의 소프트웨어공학 산출물을 모은 곳이다. 문서를 처음 보는 사람은 1절의 흐름을 보고, 찾는 내용이 있으면 3절 표에서 파일을 고른다.
100KB가 넘는 문서 4개(plan, srs, design-oo, design-review)는 목차 파일과 같은 이름의 폴더로 나눴다(4절).

## 1. 생명주기 흐름

```
[계획]  할 가치가 있는가? 얼마나, 언제까지, 누가?
        plan.md v0.31 ──▶ estimate.md v0.4 ──▶ schedule.md v0.12        (결정 기록: decisions.md)
              │
              ▼
[분석]  무엇을 만들 것인가?
        srs.md v0.22  (요구 도출 → 분석 → 명세 → 확인, DFD·자료사전·소단위 명세)
              │
              ▼
[설계]  어떻게 만들 것인가?
        design-oo.md v0.28 ──▶ design-review.md v0.21
              │
              ▼
[구현]  설계대로 코드로 옮겼는가?          implementation.md v0.10(워치 v2 develop 병합, AI 코치 화면 준비) ⇄ code-review.md v0.20 (18차 확인 합격 0/0/2, 경 합계 21)
[테스트] 요구대로 동작하는가? 품질은?       test.md v0.16, sqa.md v0.7   ◀── 지금 여기(워치 v2 R16·R17 시험 보강, 2차 배포 11/13)
[유지보수] 운영 중 변화에 대응하는가?       maintenance/
```

생명주기 모형은 점진적 개발(증분 모형)에 1주 스프린트를 붙인 것이다(plan.md 2026-10-18 결정). 증분 단위는 1차 v0.9 → 2차 v0.95 → 최종 v1.0이고, 증분마다 구현 → 테스트(code-review, test, sqa)를 반복한다.

## 2. 문서 목록

| 단계 | 문서 | 버전 | 작성일 | 입력 | 형태 |
|------|------|------|--------|------|------|
| 계획 | [plan.md](plan.md) 프로젝트 계획서 | v0.31 | 2026-11-02 | schedule.md v0.11 담당 바로잡음(R-068·R-069), 이전 판 v0.30(1차 배포 완료, 2차 배포 11/13), v0.29(워치 위험 R-060~R-066), v0.28(ai 서버 계약 A1~A9) | 목차 + `plan/` 6개 |
| 계획 | [estimate.md](estimate.md) 비용 산정서 | v0.4 | 2026-11-02 | plan.md v0.28, 코드 실측 c29df9fa·f95450db | 한 파일 (91KB) |
| 계획 | [schedule.md](schedule.md) 일정표와 조직 계획 | v0.12 | 2026-11-02 | 오규연 디자인 전환(앱 일 → 박재영, 날짜 고정), 담당 재배치(v0.11), 2차 배포 11/13, estimate.md v0.4, plan.md v0.31 | 목차 + schedule/ 조각 8개 |
| 계획 | [decisions.md](decisions.md) 결정 기록 | v0.1 | 2026-10-19 | schedule.md v0.3, plan.md v0.16 | 한 파일 |
| 분석 | [srs.md](srs.md) 요구사항 명세서 | v0.22 | 2026-11-03 | 워치 v2 결정 W34~W48(CR-122~CR-131, FR-273·NFR-054), design-oo.md v0.27 SR27-1~SR27-8, 이전 판 v0.21(W31·W32), v0.20(ai 서버 연결 CR-110~CR-118) | 목차 + `srs/` 35개 |
| 설계 | [design-oo.md](design-oo.md) 객체지향 분석·설계서 | v0.28 | 2026-11-03 | AI 코치 병합 대기(D-474 ui.record.coach, 폰 159·워치 37), 이전 판 v0.27(워치 v2 R11~R15, D-461~D-473), v0.26(ai 서버 연결 D-434~D-455) | 목차 + `design-oo/` 44개 |
| 설계 | [design-review.md](design-review.md) 설계 검증 결과서 | v0.21 | 2026-11-03 | design-oo.md v0.27(24장 조건부 합격: R79·R80, 코드 cc8c138b), AI 코치 화면 범위 검토(ai-coach-consent-박, 허용) | 목차 + `design-review/` 19개 |
| 구현 | [implementation.md](implementation.md) 구현 기록(목차 + implementation/ 조각) | v0.10 | 2026-11-03 | 워치 v2 14장(R1~R17, feature/runkk-watch-v2 → develop 599dc319), AI 코치 동의·화면 준비(ai-coach-consent-박, e66eb8a0) | 목차 + `implementation/` 8개 |
| 구현 | [code-review.md](code-review.md) 코드 리뷰 결과서 | v0.20 | 2026-11-03 | 워치 v2 12~18차(18차 확인 793eb85e 합격, 경 합계 21), AI 코치 동의·화면(ai-coach-consent-박, 조건부) | 목차 + `code-review/` 21개 |
| 테스트 | [test.md](test.md) 테스트 계획·케이스 | v0.16 | 2026-11-03 | srs.md v0.22·design-oo.md v0.27 워치 v2 구현 반영(TC-5401~TC-5626), AI 코치(TC-5627~TC-5640) | 목차 + `test/` 34개 |
| 테스트 | [sqa.md](sqa.md) 품질 검토 결과서 | v0.7 | 2026-11-03 | srs v0.22, design-oo v0.27, design-review v0.20, test v0.15, code-review v0.17, implementation v0.9, plan v0.31, 코드 cc8c138b | 목차 + `sqa/` 7개 |

## 3. 무엇을 어디서 찾나

### 3.1 기능 영역별

srs의 DFD 프로세스 번호(1~7)와 design-oo의 패키지가 같은 기능 영역을 가리킨다.

| 기능 영역 | 기능 요구 (srs) | 소단위 명세 (srs 6.3) | 클래스 명세 (design-oo 6.1) |
|-----------|-----------------|-----------------------|-----------------------------|
| 회원 | [03-01-회원-관리](srs/03-01-회원-관리.md) | [06-05](srs/06-05-소단위명세-1-4.md) 1.x | [06-01 common·member](design-oo/06-01-클래스-common-member.md) |
| 러닝 측정 | [03-02-러닝-측정](srs/03-02-러닝-측정.md) | [06-05](srs/06-05-소단위명세-1-4.md) 2.x | [06-02 running](design-oo/06-02-클래스-running-analysis-profile.md) |
| 기록 분석 | [03-03-기록-분석](srs/03-03-기록-분석.md) | [06-05](srs/06-05-소단위명세-1-4.md) 3.x | [06-02 analysis·profile](design-oo/06-02-클래스-running-analysis-profile.md) |
| 대결 | [03-04-대결-관리](srs/03-04-대결-관리.md) | [06-05](srs/06-05-소단위명세-1-4.md) 4.x | [06-03 battle](design-oo/06-03-클래스-battle.md) |
| 크루 | [03-05-크루-관리](srs/03-05-크루-관리.md) | [06-06](srs/06-06-소단위명세-5-7.md) 5.x | [06-04 crew](design-oo/06-04-클래스-crew.md) |
| 캐릭터 | [03-06-캐릭터-관리](srs/03-06-캐릭터-관리.md) | [06-06](srs/06-06-소단위명세-5-7.md) 6.x | [06-05 runimal·avatar·reward](design-oo/06-05-클래스-runimal-policy-operator.md) |
| 운영 | [03-07-운영-관리](srs/03-07-운영-관리.md) | [06-06](srs/06-06-소단위명세-5-7.md) 7.x | [06-05 policy·notification·audit·operator](design-oo/06-05-클래스-runimal-policy-operator.md) |
| 앱·워치·ai 서버 | [05-외부-인터페이스](srs/05-외부-인터페이스.md) | (없음) | [06-06 표현·인프라·앱·워치·ai](design-oo/06-06-클래스-표현-인프라-앱-워치-ai.md) |

### 3.2 주제별

| 찾는 것 | 파일 |
|---------|------|
| 범위, 축소 우선순위 | [plan/03-범위](plan/03-범위.md) |
| 위험 목록, 품질 목표 | [plan/08-09-품질-위험](plan/08-09-품질-위험.md) |
| 비용·인월 | [estimate.md](estimate.md) |
| WBS, 임계 경로, 간트 차트, 팀 구성 | [schedule.md](schedule.md) |
| 용어 정의, 시스템 개요(배경도) | [srs/01-개요](srs/01-개요.md), [srs/02-전체-설명](srs/02-전체-설명.md) |
| 비기능 요구(NFR) | [srs/04-비기능-요구사항](srs/04-비기능-요구사항.md) |
| DFD | [Level 0·1](srs/06-01-DFD-구성-Level1.md), [Level 2](srs/06-02-DFD-Level2.md), [Level 3](srs/06-03-DFD-Level3.md) |
| 자료사전, ERD·상태전이도 | [srs/06-04-자료사전](srs/06-04-자료사전.md), [srs/06-07-ERD-상태전이도](srs/06-07-ERD-상태전이도.md) |
| 요구사항 추적표 | [srs/07-01-추적표](srs/07-01-추적표.md) (요구 → 모델), [design-oo/A-추적표](design-oo/A-추적표.md) (요구 → 클래스) |
| 팀 결정 기록 | [decisions.md](decisions.md), [plan/부록-A-E](plan/부록-A-E-변경-결정.md), [srs/07-03-미결-사항-결정](srs/07-03-미결-사항-결정.md), [design-oo/05-02](design-oo/05-02-계층-동시성-설계-결정.md) (5.6 설계 결정 D-xx) |
| 패키지 분할, 계층 구조 | [design-oo/05-01-서브시스템-분할](design-oo/05-01-서브시스템-분할.md), [design-oo/05-02](design-oo/05-02-계층-동시성-설계-결정.md) |
| REST·실시간 메시지 계약 | [design-oo/05-03-API-실시간-메시지](design-oo/05-03-API-실시간-메시지.md) |
| 알고리즘 | [design-oo/06-07-알고리즘-상속-다형성](design-oo/06-07-알고리즘-상속-다형성.md) |
| UML 다이어그램(유스케이스·클래스·시퀀스·상태·활동) | [design-oo/07-11-UML-다이어그램](design-oo/07-11-UML-다이어그램.md) |
| 의존도, DSM | [design-oo/12-의존도](design-oo/12-의존도.md) |
| 구현 지침, 테스트 순서 | [design-oo/13-14-구현-지침-테스트-순서](design-oo/13-14-구현-지침-테스트-순서.md) |
| 최신 설계 검증 판정 | [design-review/24-v0.27-검증](design-review/24-v0.27-검증.md) (이전 판정 이력은 09~23) |

## 4. 분할 규칙

- 목차 파일 이름은 원래 이름(`srs.md` 등)을 그대로 쓴다. `sw-*` 스킬이 이 이름으로 산출물을 찾는다. 목차 파일에는 제목, 작성 헤더, 문서 상태, 다음 단계와 목차 표가 남는다.
- 본문은 같은 이름의 폴더에 `장-순번-주제.md`로 나눴다. 파일 하나가 60KB를 넘지 않도록 큰 장은 절(###)이나 패키지(####) 단위로 다시 나눴다.
- 장·절 번호는 원래 번호를 그대로 쓴다. 다른 문서가 "srs.md 6.3"처럼 참조하면 목차 표에서 해당 파일을 찾는다.
- 조각 파일의 첫 줄은 목차·이전·다음 링크다. 본문을 고칠 때는 조각 파일을 고치고, 버전은 목차 파일 헤더에서 올린다.
- estimate.md(91KB), decisions.md는 100KB 아래라 나누지 않았다. schedule.md는 2026-11-02 v0.8에서 100KB를 넘어 schedule/ 조각으로 나눴다(v0.12 기준 8개).
