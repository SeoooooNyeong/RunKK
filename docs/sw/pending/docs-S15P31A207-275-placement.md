# 병합 대기: docs/S15P31A207-275-placement

> 작성: 2026-11-04(갱신 2026-11-05, 2라운드 v0.25 묶음 포함) | 작성자: 이성현 | 스킬: sw-srs, sw-design-oo, sw-design review, sw-test, sw-code-review, sw-implement, sw-sqa

공용 담당이 쓴 브랜치라 정식 번호(CR-135~CR-146, FR-274~FR-290)와 버전(srs v0.24)은 이 브랜치에서 매겼다. 핫픽스 브랜치(`hotfix/S15P31A207-275-v0111-feedback`) 병합 대기 파일의 [임시:CR-성-1]~[임시:CR-성-4]는 CR-135~CR-138로 매겼다. 남은 것은 박재영 담당 조각의 문장 수정 요청이다. 근거와 전체 규칙은 srs/03-08-배치고사-화면개편-성.md와 srs/07-16-변경-요청-CR-v0.24.md(7.13.19)에 있다.

## 고친 조각

- srs/03-08-배치고사-화면개편-성.md: 새 조각. FR-274~FR-290, 분석 담당 결정 J24-1~J24-23.
- srs/03-01-회원-관리.md: FR-022(배치고사 3판으로 첫 배치), FR-027(재평가는 확정 티어만, 첫 평가 화면), FR-005·FR-006(티어 표시 규칙).
- srs/07-16-변경-요청-CR-v0.24.md: 새 조각. CR-135~CR-146, 기존 요구·코드와 달라지는 곳, 모델 반영, 변경 점검.
- 공용 조각: 01-개요(용어 티어·배치·언랭 변경, 배치고사·임시 티어·배치 중·확정 티어 추가), 02-전체-설명(S-03 1c·정상 흐름, S-26 정상 흐름, S-28 전제·2b), 03-07·04(목차 이동 링크), 05-외부-인터페이스(5.1 홈·러닝 결과·랭킹 행, 배틀런 화면 삭제 표시와 새 화면 4행), 06-04(대결종류·회원레코드·대시보드정보·티어), 06-05(1.6, 4.1 의사결정표, 4.6 배치고사 상대), 06-07(ERD 2행, 6.4.2 2행, 6.4.8 짧게 누름 행 CR-135, 새 6.4.9), 06-08(v0.24 자료 31개), 07-01·07-11 추적표(새 17행, 바뀐 21행 출처), 07-03(7.10.6), 07-15(다음 링크).
- 목차·공용: srs.md(머리 헤더 v0.24, 문서 상태, 목차 2행, 8. 다음 단계), README.md(srs 행).
- (sw-design-oo, design-oo v0.30) srs 정정: 03-08 FR-282·FR-290·J24-14(총괄 결정 J24-14R: 랭킹 이름 옆 아주 작은 공개 티어 배지)·J24-23(D-504: 배치 중 기록은 고스트 후보가 아님, 지금 코드와 같음), 07-03 N-20 행·요약, 07-16 CR-145, 05 5.1 랭킹 행, 06-05 3.6, 06-04 랭킹표 주석, 07-11 FR-282 출처.
- design-oo/05-21-설계-배치고사-화면개편-성.md: 새 조각. D-475~D-510, 서버·앱 클래스 명세, SQ-49·SQ-50, 상태도 2.4.27, V38 SQL(v0.31에서 V35_1로 바꿈), A-37·A-38, 앱 시각 명세, 피그마 차이 17건, 구현 단위(10절), 테스트 순서(11절).
- design-oo/05-03-API-실시간-메시지.md: 5.7.3 v0.30 계약(고정) 추가. v0.31: 항목·enum 값 그대로, P2 ALREADY_IN_BATTLE 뜻(생성 경합 포함), P4 서비스 연산(battle PlacementRoundReader), PlacementRoundState·placementState 뜻(미도착 유효 판 셈, 완료 뒤 판 NOT_COUNTED) 문장 보완.
- (sw-design review, design-review v0.22) design-review/25-v0.30-검증.md 새 조각(결함 R82~R95, 치명 1·중 7·경 6, v0.30 불합격 → v0.31 범위 합격), design-review.md(머리 v0.22, 판정, 목차 행, 26. 다음 단계, 이력), 24장 다음 링크.
- (design-oo v0.31, 설계 검토 반영) 05-21: 1.1절 D-511~D-520, D-476(V35_1 + develop만 out-of-order), D-478·D-481·D-483·D-484·D-487·D-490·D-495·D-501·D-510 문장, 2.1·2.2·2.4 클래스 명세, SQ-49·SQ-50, 5절, 9·10·11·12·13·14절. 06-01(이성현) 배치고사 요약, 06-06·12-의존도·13-14·A-추적표·B-E(v0.31 이력·색인)·design-oo.md(머리 v0.31, 목차, 상태)·README.
- design-oo/06-01-클래스-common-member.md(이성현): 배치고사 진행 요약 문단.
- 공용 design-oo 조각: 06-06(6.1.17 앱 요약), 12-의존도(12.4 v0.30 문단), 07-11(상태 표 PlacementProgress 행), A-추적표(FR-274~FR-290 17행), B-E(v0.30 이력, D 색인), design-oo.md(머리 헤더 v0.30, 목차 행, 상태·다음 단계), README.md(design-oo 행).

- (sw-test, test v0.18) test/10-25-v018-배치고사-계획-단위.md·test/10-26-v018-배치고사-통합-검증-구현목록.md 새 조각(18장, TC-5693~TC-5953 261개: 핫픽스 CR-135~CR-138 10, 단위 서버 103, 화이트박스 8, 단위 앱 97, 통합 32, 검증·시스템 11, 구현 단위별 시험 목록 18.15), test.md(머리 v0.18, 상태, 함께 고친 문서, 변경 이력, 목차 2행, 1장 요약 수, 실행 이력, 다음 행동), test/01-계획.md 1.5 번호 대역 TC-5693~TC-5999 행, test/10-24 다음 링크, README.md(test 행).
- (sw-code-review, code-review v0.22) code-review/17-배치고사-1차.md 새 조각(hotfix df211dc5..bad8c750, BE-0·BE·AN-1·infra, 조건부 0/5/16, 결함 PL1-B1~B8·PL1-A1~A9·PL1-T1~T4, 설계서 갱신 요청 U-1~U-11, 시험서 갱신 요청 UT-1~UT-3), code-review.md(머리 v0.22, 최신 판정, 변경 이력, 새 절), 3차 리뷰 요약 절을 code-review/00-리뷰-2026-10-21-3차-요약.md로 옮김(목차 100KB 상한, 내용 그대로), README.md(code-review 행).
- (sw-code-review 2차, code-review v0.22 같은 판) code-review/18-배치고사-2차.md 새 조각(1차 수정 bad8c750..96cc3f5e 확인 + AN-2 3dfb8552..c2685561, 조건부 0/2/14, 결함 PL2-A1~A8·PL2-B1·PL2-T1, 설계서 갱신 요청 U2-1~U2-8, 시험서 UT2-1~UT2-4), code-review.md(머리 헤더 입력, 최신 판정, 변경 이력, 새 절), README.md(code-review 행).
- (sw-code-review 3차, code-review v0.22 같은 판) code-review/19-배치고사-3차.md 새 조각(2차 수정 c2685561..4129283c 확인, 조건부 0/1/9, 새 결함 PL3-A1(중)·PL3-A2(경), 설계서 갱신 요청 U3-1~U3-4, 시험서 UT3-1~UT3-3), code-review.md(머리 헤더 입력, 최신 판정, 변경 이력, 새 절), README.md(code-review 행, 조각 수 28).
- (sw-design-oo, design-oo v0.32) design-oo/05-22-설계-배치고사-리뷰반영-성.md 새 조각(D-521~D-546: code-review 17·18 설계서 갱신 요청 U-1~U-9·U-11·U2-1~U2-8 반영, U-10 철회, 구현 작업자가 설계와 다르게 한 것, SQ-51, 상태도 2.4.28, 피그마 차이 18~23). 05-21(헤더·각 결정·2.x·7.1·7.3·8·11·14절에 "v0.32" 표시와 05-22 링크, D-496 masterAccent 정정, 7.3 라이트 브론즈·골드 글자색), 05-03 5.7.3(출처, NOT_COUNTED 뜻, P4 오류, P8 연산, MatchFound null, 계약 시험 이름), 06-01(이성현) 배치고사 요약, 06-06 6.1.17, 07-11 상태 표 행, 12-의존도 v0.32 문단, A-추적표 FR-276·FR-280·FR-281·FR-287·FR-289, B-E(v0.32 이력·색인), srs/03-08 J24-22 보완(이력 라벨 「배틀런 · 배치고사 n판」, n = 만든 때 번호), design-oo.md(머리 v0.32, 목차 행, 상태), README.md(design-oo 행).
- (sw-test, test v0.19) test/10-28-v019-배치고사-리뷰반영.md 새 조각(19장, TC-5954~TC-5973 20개: 1차 수정 실제 DB 시험 PL1-B1·B3·B5와 번호 없던 단위 시험의 코드 이름 대응, 대결 진행 화면 「?」, 판 결과 회복·미확인 완료 화면, 상태도 2.4.28 전이, 실행 결과 자리 19.10), 10-25 18.8절을 test/10-27-v018-배치고사-단위-AN-2.md로 옮김(100KB), 10-25·10-26·10-27 기존 행 고침(UT-1~UT-3, UT2-1·UT2-3·UT2-4: TC-5750·5832·5874·5908·5931, 18.15.2 구현 목록, 18.15.8 기준 이미지), test/01-계획 1.5, test.md(머리 v0.19, 상태, 목차 3행, 1장 요약, 8장 실행 이력 자리, 9장), README.md(test 행).
- (sw-implement, implementation v0.11) implementation/15-v0.11.1-배치고사-화면개편.md 새 조각(핫픽스 원래 커밋 6개 CR-135~CR-138 기록 = 핫픽스 병합 대기 파일의 implementation 요청 처리, 단계·worktree, 통합 기록, 결정 I15-1~I15-40, 사고 기록, 리뷰 반복, 배포 확인, 총괄이 채울 자리 15.11), implementation.md(머리 v0.11, 0절, 목차 행), 14·A 조각 이웃 링크, README.md(implementation 행). (보정) design-oo 05-22·test 10-26~10-28을 2차 수정 커밋 0d648155·5a24de5c·ac411a32·e35dba34에 맞춤.

- (sw-implement, implementation v0.11 같은 판 보완, 2026-11-05) implementation/15: 총괄이 채울 자리(15.11) 채움 — 2·3차 수정 커밋(e35dba34 → 23e74149, 앱 9커밋 → 4129283c, 06ca6de4 → 564db6af), 캐시 없는 게이트 수치, 결정 I15-41~I15-46, 리뷰 3·4차 행, main 219940e1 충돌 3파일과 반영 판단, 배포 확인 8번(에뮬레이터). implementation.md 머리 입력·0절(마지막 push 564db6af)·목차 행.
- (sw-test, test v0.19 같은 판 보완) test/10-28: 19.10 실행 결과와 TC-5954~TC-5973 결과 칸(TC-5973 미실행), UT3-1~UT3-3·UT4-1, 새 19.12 TC-4839~TC-4841, J-T19-7~9. test/01-계획 1.5 TC-4839~TC-4841 행. test.md 머리·상태·함께 고친 문서·변경 이력·목차 행·8장 첫 행·9장.
- (sw-design-oo, design-oo v0.32 같은 판 보정) design-oo/05-22: code-review 19 U3-1~U3-4(D-531 랜덤 대결 미확인 완료·isCompletionUnseen·프로세스 사본·포트 이동 완료, D-533 progressError·onResumed, D-537 남은 판 막대 Border, D-539 단언 실패 처리, 2.4·S-PL4·SQ-51·3.4·5·6절). A-추적표 FR-281, 07-11 PlacementResultViewModel 행, B-E v0.32 이력·색인, design-oo.md 머리·상태.
- (sw-sqa, sqa v0.9) sqa/09-검토-2026-11-05.md 새 조각(핫픽스 v0.11.1 MR !70 564db6af 배포 판정: 조건부 배포 가능, 조건 5, DF-055~DF-061, 관문 HQ1~HQ10, 위험 가번호 RW-13~RW-17, J-Q9-1~5). sqa.md 머리 v0.9·최신 판정·목차 행. README.md(sqa·code-review·구현·테스트·설계 행).

- (sw-srs, srs v0.25, 2라운드: 핫픽스 4a2722fd..80592a08) srs/07-17-변경-요청-CR-v0.25.md 새 조각(7.13.20, CR-147~CR-153). srs/03-08: FR-287 처리(요약 칸 → 러닝 기록, CR-147), FR-290 처리(랭킹 v3, CR-149), J24-12 철회, 새 3.8.7 FR-291(워치 배치고사)·FR-292(대결 출발 전 GPS 예열), 새 3.8.8 J25-1~J25-8. 공용: 05(화면 방향 문단 CR-148, 랭킹 행), 07-03(7.10.7), 07-11(FR-291·FR-292 행, FR-257·FR-287·FR-290 출처, 행 수), 07-16(다음 링크), srs.md(머리 v0.25, 상태, 목차 2행, 8장), README.md(srs 행).

- (sw-design-oo, design-oo v0.33, 2라운드) design-oo/05-23-설계-핫픽스-추가변경-성.md 새 조각(D-547~D-563, SQ-52·SQ-53, 상태도 2.4.29, 피그마 차이 24~26). 05-21 D-500·05-22 D-546에 철회 표시, 05-22 다음 링크, A-추적표(FR-223·FR-287·FR-290 보충, FR-291·FR-292 행), B-E(v0.33 이력, D 색인), 07-11(상태 표 BattleRun 예열 행), 12-의존도(v0.33 문단), design-oo.md(머리 v0.33, 목차 행, 상태), README.md(design-oo 행). srs 같은 판 보정: 03-07 FR-223(랭킹 크루 분절은 잠긴 칸), 03-08 FR-292(안내 한 줄은 두 상태 모두), 07-17 CR-149 바뀐 요구에 FR-223.

- (sw-design review, design-review v0.23, 2라운드) design-review/27-v0.33-검증.md 새 조각(27장, 조건부 합격, R96~R99 경, H-68~H-70, C1~C7), design-review.md(머리 v0.23, 판정, 목차 행, 26. 다음 단계, 이력), 25장 다음 링크, design-oo 05-23 7절 협력 객체 수 보정(C1), README.md(design-review 행).

- (sw-test, test v0.20, 2라운드) test/10-29-v020-핫픽스-추가변경.md 새 조각(20장, TC-4842~TC-4880 39개, T-61·T-62, J-T20-1~4, 실행 결과 80592a08). test.md(머리 v0.20, 상태, 함께 고친 문서, 변경 이력, 목차 행, 8장, 9장), test/01-계획 1.5 행, 10-25·10-26·10-27·10-09 기존 행 v0.20 표시(TC-5858~TC-5860, TC-5864, TC-5866~TC-5868, TC-9742), 10-28 다음 링크, README.md(test 행).

- (구현 기록, implementation v0.12, 2라운드) implementation/16-v0.11.1-핫픽스-추가변경.md 새 조각(16장, 9커밋, I16-1~I16-12). implementation.md(머리 v0.12, 0절, 목차 행), 15장 다음 링크, README.md(implementation 행). `/sw-implement`는 돌리지 않고 양식대로 기록만 했다.

- (sw-code-review, code-review v0.23, 2라운드) code-review/21-핫픽스-추가변경-1차.md 새 조각(1차 0/2/8 → 수정 45f7d383 → 2차 합격 0/0/7, U5-1·U5-2·UT5-1). code-review.md(머리 v0.23, 최신 판정, 새 절), README.md(code-review 행). 같은 판 보정: design-oo 05-23 1.3절(D-549·D-550·D-559)·2.4 표, test 10-29(TC-4842·TC-4852·TC-4866·TC-4867 행, 새 20.8.1 TC-4881·TC-4882, 20.10), test.md(상태, 8장), implementation 16.4·16.7, implementation.md 0절.

- (sw-sqa, sqa v0.10, 2라운드) sqa/10-검토-2026-11-05-2.md 새 조각(10회차: 조건부 배포 가능, DF-062~DF-068, RW-18~RW-20, RW-13 종료, J-Q10-1~4). sqa.md(머리 v0.10, 최신 판정, 목차 행), 09 다음 링크, README.md(sqa 행, 흐름 그림).

## 새 임시 번호

없음(정식 번호로 매김).

| 정식 번호 | 종류 | 한 줄 설명 | 위치 |
|-----------|------|------------|------|
| CR-135~CR-138 | CR | 핫픽스 v0.11.1: 종료 안내 캡션 하나, 코치 오늘 계획 다시 눌러 취소, 설정 톱니바퀴, 여자 얼굴 흰 조각 | srs/07-16 |
| CR-139~CR-145 | CR | 배치고사, 티어 표시·엠블럼, 배치고사 화면, 대결 분리, 홈 요약, 러닝 결과 상용안, 랭킹 시상대 | srs/07-16 |
| CR-146 | CR | 말풍선(2차 배포로 미룸)·폴드(범위 밖) 기록 | srs/07-16 |
| FR-274~FR-290 | FR | 배치고사 규칙 5, 배치고사 화면 3, 티어 표시 2, 대결 분리 3, 홈·결과·랭킹 4 | srs/03-08 |
| D-475~D-510 | D | 배치고사·티어 표시·화면 개편 설계 결정(기존 최대 D-474 다음) | design-oo/05-21 |
| SQ-49·SQ-50, 2.4.27, A-37·A-38 | 시퀀스·상태도·알고리즘 | 배치고사 요청·성사, 판 셈·확정, PlacementProgress 상태, 판 셈·상대 점수 | design-oo/05-21 |
| D-511~D-520 | D | 설계 검토 반영(판 인정 = 기록 유효, 기록 연결 사건, 판 결과 상태 battle 안, V35_1, 생성 경합, 판 셈 잠금, 읽기 순서, lockForMatch Optional, 화면 연결, 모드 인자 분리) | design-oo/05-21 1.1절 |
| R82~R95, H-62~H-67, C1~C11 | 검토 결함·숨은 의존·코드 대조 | 기존 최대 R81·H-61 다음 | design-review/25 |
| TC-5693~TC-5953 | TC | 배치고사·화면 개편·핫픽스 시험 261개(기존 최대 TC-5692 다음, 대역 TC-5693~TC-5999, 예비 TC-5954~). T-59·T-60 스레드, J-T18-1~13, TF-V18-01~04, R-V18-01~04 | test/10-25, test/10-26 |
| D-521~D-546, SQ-51, 2.4.28 | D·시퀀스·상태도 | code-review v0.22 1·2차 설계서 갱신 요청과 구현 작업자 결정 반영(기존 최대 D-520·SQ-50·2.4.27 다음) | design-oo/05-22 |
| TC-5954~TC-5973 | TC | 배치고사 코드 리뷰 반영 시험(예비 대역 TC-5954~에서, 남은 TC-5974~TC-5999는 다른 브랜치 임시 TC 몫) | test/10-28 |
| TC-4839~TC-4841 | TC | 배치고사 3·4차 리뷰 새 케이스(PL3-A1 판정 함수, PL3-A2 저장 실패, PL4-A1 후속). v0.12 뒤 남은 예비 TC-4839~TC-4999에서(TC-5974~TC-5999는 다른 브랜치 몫) | test/10-28 19.12 |
| DF-055~DF-061 | SQA 결함 | 핫픽스 v0.11.1 배포 판정 결함(중 2·경 5, 기존 최대 DF-054 다음) | sqa/09 |
| CR-147~CR-153 | CR | 2라운드: 요약 칸 이동, 화면 방향, 랭킹 v3, 워치 배치고사, 도착 판정 거리, GPS 예열, main W51~W53 기록 | srs/07-17 |
| FR-291·FR-292 | FR | 워치 배치고사, 대결 출발 전 GPS 예열 | srs/03-08 3.8.7 |
| D-547~D-563, SQ-52·SQ-53, 2.4.29 | D·시퀀스·상태도 | 2라운드 핫픽스 추가 변경 설계(기존 최대 D-546·SQ-51·2.4.28 다음) | design-oo/05-23 |
| R96~R99, H-68~H-70 | 검토 결함·숨은 의존 | 2라운드 설계 검토(기존 최대 R95·H-67 다음, 장 27) | design-review/27 |
| TC-4842~TC-4880, T-61·T-62 | TC·스레드 | 2라운드 시험(v0.11 대역 남은 예비에서, TC-5974~TC-5999는 다른 브랜치 몫 그대로) | test/10-29 |
| PL5-B1, PL5-A1~A8, PL5-T1, U5-1·U5-2, UT5-1 | 코드 리뷰 결함·요청 | 2라운드 코드 리뷰(21장) | code-review/21 |
| TC-4881·TC-4882 | TC | 코드 리뷰 반영 시험 | test/10-29 20.8.1 |
| DF-062~DF-068, RW-18~RW-20, J-Q10-1~4 | SQA 결함·위험 가번호·검토자 결정 | 2라운드 배포 판정(기존 최대 DF-061·RW-17 다음). RW-18~RW-20은 `/sw-plan`이 R-081~로 | sqa/10 |
| J25-1~J25-8 | 분석 담당 결정 | 순위 변동 미표시, 워치 문구 축약·점선 원 색(확인 필요), 칩 판 번호, 옛 서버 잠금 문구, 예열 범위, 도착 판정, 세로 고정 범위 | srs/03-08 3.8.8 |
| RW-13~RW-17 | 위험 가번호 | main 합치기, develop 환경 미경유, prod 설정, 옛 앱, (제안) 문구. `/sw-plan`이 R-076부터 매긴다 | sqa/09 7절 |

## 공용 파일 반영

이 브랜치에서 반영했다. (test v0.19) srs 추적표에 더할 값은 test/10-28 19.7(FR-021·FR-276·FR-280·FR-281·FR-282·FR-287·FR-289·NFR-005). (test v0.18) srs 추적표(07-01·07-11) 테스트 케이스 열에 넣을 값은 test/10-26 18.14에 있고 다음 공용 정리 때 넣는다. 영향 받는 기존 TC 26개의 원래 행 표시(18.16)도 그때 한다. 다른 브랜치 임시 TC([임시:TC-박-파기1~9], [임시:TC-박-얼굴1~8])는 그 브랜치 병합 때 예비 TC-5974~에서 매긴다(TC-5954~TC-5973은 test v0.19가 씀). 남은 것: 다른 브랜치의 임시 번호([임시:FR-박-얼굴1]~[임시:FR-박-얼굴6], [임시:CR-박-얼굴1], 각 TC 임시 번호)는 이 판 번호 뒤에서 매긴다. (srs v0.25 정정) 이 브랜치 2라운드가 FR-291·FR-292와 CR-147~CR-153을 먼저 썼으므로 그 브랜치 번호는 FR-293~, CR-154~에서 매긴다. DFD 6.1 그림에 새 흐름(4.1 → 4.6 배치고사매칭요청, 4.5 → 1.6 배치판결과)을 그리는 것은 다음 공용 정리 때 한다(7.13.19.2).

(test v0.20) srs 추적표(07-11) 시험 열: FR-291 → TC-4854~TC-4862·TC-4877·TC-4879, FR-292 → TC-4868~TC-4876·TC-4880, FR-287 +TC-4842, FR-290 +TC-4845~TC-4853(test 10-29 20.9). 다음 공용 정리 때.

(sqa v0.10) plan.md 9절에 RW-13 종료, RW-18~RW-20 → R-081~R-083은 다음 `/sw-plan`에서. (sqa v0.9) plan.md 9절에 RW-13~RW-17 → R-076~R-080, R-V18-03 종료, R-019 발생(조치함)은 다음 `/sw-plan`에서. srs 추적표(07-01·07-11)에 test 18.14·19.7 값과 TC-4839~TC-4841(FR-281)은 다음 공용 정리 때(sqa DF-061).

## 다른 담당에게 요청

박재영(대결·러닝 측정·기록 분석 담당). 아래 문장을 담당 조각에 넣는다. 각 끝에 "(v0.24 변경, CR-xxx)"를 붙인다. 반영 전에는 srs/03-08이 앞선다.

### 핫픽스에서 넘어온 요청 (원문: `S15P31A207-hotfix/docs/sw/pending/hotfix-S15P31A207-275-v0111-feedback.md` "다른 담당에게 요청", df211dc5)

- srs/03-02-러닝-측정.md FR-181: "측정 중 뒤로 가기는 종료가 아니라 '길게 눌러 종료' 안내"를 "버튼 아래 '길게 눌러 종료' 캡션이 늘 보이고, 짧게 누름·뒤로 가기는 측정을 이어 간다"로 바꾼다(CR-135, 핫픽스 [임시:CR-성-1]).
- srs/03-03-기록-분석.md FR-073 처리에 "이미 오늘 계획인 거리를 다시 누르면 오늘 계획을 취소한다"를 더한다(CR-136, 핫픽스 [임시:CR-성-2]).
- 코치 오늘 계획 거리 칸은 접근성 역할이 RadioButton이라 화면 낭독기가 "다시 누르면 취소"를 알 수 없다. 후속으로 안내를 더할지 정한다(핫픽스 원문 그대로).

### 배치고사 (CR-139)

- srs/03-04-대결-관리.md FR-080 처리: "티어가 언랭(유효 러닝 3회 전)이면 랜덤 대결 분절의 매칭 시작 버튼에 자물쇠를 보이고, 누르면 매칭을 요청하지 않고 "러닝을 n번 더 뛰면 배틀런이 가능해요!"를 보인다. n은 3 − 유효 러닝 횟수이고 1보다 작으면 1이다. 유효 러닝 횟수를 알 수 없으면 "러닝을 더 뛰면 배틀런이 가능해요!"를 보인다. 친구 대결 분절은 잠그지 않는다 (v0.15 변경, CR-61). 그래도 언랭 러너의 랜덤 매칭 요청이 서버에 오면 거부한다 (v0.13 변경, CR-32)."와 "언랭 거절(RANKED_ONLY)을 받은 매칭 화면은 그 화면을 떠날 때까지 매칭을 다시 요청하지 않아야 한다 (v0.12 추가, CR-22)"를 지우고 "티어가 언랭(배치 중)이면 잠그거나 거부하지 않고 배치고사 한 판으로 받는다(FR-274). 배치고사는 대기열에 넣지 않는다"로 바꾼다. 입력에 회원레코드(배치진행)를 더한다.
- FR-081 처리 끝에 "배치 중(언랭) 러너는 대기열에 들어가지 않으므로 매칭 후보가 아니다(FR-274)"를 더한다.
- FR-083 처리 끝에 "배치고사는 대기 없이 배치고사 상대 기록(FR-276)으로 바로 성사되므로 15초 전환을 쓰지 않는다. 상대 기록 생성 실패는 이 FR의 대결 생성 실패와 같이 안내한다"를 더한다.
- FR-094 처리 끝에 "배치고사 판을 진행 중에 포기하면 패로 기록하되 배치고사 판으로 세지 않는다(FR-275)"를 더한다.
- FR-095 처리 끝에 "배치고사(FR-276)에는 이 탐색을 쓰지 않는다. 배치 중(언랭) 러너의 기록은 지금과 같이 고스트 후보가 아니다(J24-23, design-oo v0.30 D-504로 정정)"를 더한다.
- FR-101(대결 이력) 처리에 "배치고사 판은 랜덤 대결로 남기고 「배치고사 n판」을 덧붙인다 (제안, J24-22)"를 더한다.
- FR-257 처리: "유효 러닝이 3회 전(언랭)이면 매칭을 요청하기 전에 거리 카드의 "매칭 시작"을 잠그고 남은 횟수 안내를 보인다(폰 FR-080과 같은 규칙)"를 "티어가 언랭(배치 중)이면 매칭 시작을 잠그고 「폰에서 배치고사를 마치면 열려요」 (제안)를 보인다. 배치고사는 폰에서만 치른다(J24-12)"로 바꾸고, 아래 문구 표의 두 언랭 행(남은 횟수 앎·모름)을 이 한 행으로 바꾼다. 앱 공용 `RandomBattleGate`를 폰에서 없앨 때 워치 잠금은 남겨야 한다.

### 티어 표시 (CR-140)

- FR-086(매칭 성사 통지) 처리에 "상대 티어는 상대가 배치 중이면 「배치 중」이다. 배치고사 상대는 상대 티어를 보내지 않는다(FR-276, FR-282)"를 더한다.

### 배치고사 화면 (CR-141)

- FR-093(대결 결과 표시) 처리에 "배치고사 1·2판은 배치고사 판 결과 화면(FR-280), 3판은 배치고사 완료 화면(FR-281)이 이 화면을 대신한다"를 더한다.

### 대결 분리 (CR-142)

- FR-080 처리 앞부분 "홈의 배틀런 카드("랜덤 또는 친구와 대결")를 누르면 배틀런 화면을 연다. 배틀런 화면은 랜덤 대결과 친구 대결(FR-227) 두 분절이고 처음에는 랜덤 대결이 골라져 있다. 랜덤 대결 분절에는 1·3·5·10km 카드를 두고 카드마다 그 거리의 내 최고 기록과 날짜(FR-063)를 보인다."와 ""n km 랜덤 매칭 시작"을 눌러야"를 "홈의 배틀런 카드를 누르면 진입 시트(FR-284)가 열리고, 랜덤 대결을 고르면 확정 티어 러너는 랜덤 대결 화면(FR-285), 배치 중 러너는 배치고사 진행 화면(FR-279)이 열린다. 랜덤 대결 화면은 1·3·5·10km 거리 칩과 고른 거리의 내 최고 기록(FR-063)을 보이고 「n km 매칭 시작」을 눌러야"로 바꾼다. 설명의 "배틀런 화면의 랜덤 대결에서"를 "랜덤 대결 화면에서"로 바꾼다.
- FR-227 설명의 "배틀런 화면의 친구 대결에서"를 "친구 대결 화면(FR-286)에서"로, 처리 앞부분 "배틀런 화면(FR-080)의 친구 대결 분절에 1·3·5·10km 카드를 두고 카드마다 그 거리의 내 최고 기록과 날짜(FR-063)를 보인다. 처음 골라진 거리와 기록 없는 거리 처리는 랜덤 대결(FR-080)과 같다 (제안). 러너가 카드를 고른 뒤 "카카오톡으로 친구 초대"를 누르면"을 "친구 대결 화면(FR-286)의 거리 선택 상자(1·3·5·10km, 처음 값은 기본대결거리)에서 거리를 고르고 「카카오톡으로 초대하기」를 누르면"으로 바꾼다. "친구 대결 거리 선택 화면에 남는다"는 "친구 대결 화면에 남는다"로 바꾼다.

### 홈 요약 패널 (CR-143)

- srs/03-03-기록-분석.md FR-050 처리 첫 문장 "기능은 남기되 홈 화면에는 보이지 않는다 (v0.13 변경, CR-31)."를 "오늘요약은 홈의 「내 기록 요약」 펼침 패널(FR-287)에 보인다. 누적요약은 홈에 보이지 않는다."로 바꾼다.
- FR-053 처리 첫 문장을 "최근 12주 잔디는 홈에 보이지 않는다. 홈의 「내 기록 요약」은 5주 × 7일 달림 여부만 보인다(FR-287)."로 바꾼다.

### 러닝 결과 상용안 (CR-144)

- FR-078 처리: "하단에 "기록 카드"(FR-068)와 "홈으로"를 둔다"를 "화면 구성과 순서는 FR-289를 따른다. 하단 고정 바에 채운 단추 「홈으로」와 글자 단추 「기록 카드 만들기」(FR-068)를 둔다. 티어 배지는 보이지 않는다"로 바꾼다.
- FR-185 처리 끝에 "러닝 결과 화면(FR-289)의 누적 막대는 4단계로 보이며 4·5구간(80% 이상)을 Z4로 합친다. 서버 자료는 5구간 그대로다(J24-17)"를 더한다.

### 랭킹 시상대 (CR-145)

- FR-070 처리 끝에 "1~3위는 시상대(FR-290)로 보이고 목록은 4위부터다. 배치 중 러너도 순위에 든다. 시상대·본인 행·목록 행의 닉네임 옆에 아주 작은 티어 배지로 공개 티어(확정 티어, 없으면 「배치 중」)를 보인다(FR-282, 총괄 결정 J24-14R: CR-45의 랭킹 티어 미표시를 바꾼다)"를 더한다. 처리 앞부분 "랭킹 행에는 순위, 아바타, 닉네임, 누적 유효 거리만 보이고"의 "만"을 지우고 "와 공개 티어 배지"를 더한다.

### 설계 (design-oo v0.30, sw-design-oo)

- 박재영(design-oo/06-03-클래스-battle.md): 05-21 2.2의 새 클래스 PlacementMatcher, PlacementOpponentFactory, PlacementOpponent, PlacementRoundListener, PlacementReconcileService, PlacementRoundState, RecordCheck와 기존 클래스 변경(Battle.placementRound·markPlacement·verify(memberId, RecordCheck), BattleParticipant.validRunOrdinal, MatchingService.requestMatch 언랭 갈래, MatchAcceptance.placement, MatchFound.placementRound, BattleHistoryView.Entry.placementRound·opponentMemberId, BattleRepository.findPlacementFinishedBetween, BattleResultService.applyRecord)을 6.1.6 명세에 옮긴다. 06-03의 RANKED_ONLY 거부·Battle.verify(memberId, recordId, valid, reachElapsed) 문장은 05-21 D-478·D-482가 대신한다.
- 박재영(design-oo/06-03-클래스-battle.md, v0.31 추가): 05-21 1.1절의 PlacementRoundCheck(D-511), PlacementRecordAttached와 PlacementRoundListener.onRecordAttached(D-512), PlacementRoundReader와 BattleResultView.placementRound(D-513), PlacementMatcher.start의 `Optional<Battle>` 반환·saveImmediately·활성 칸 고유 제약 → ALREADY_IN_BATTLE(D-515·D-518), PlacementRoundState.of(check, counted, progressCompleted)(D-487), BattleResultService.applyRecord의 사건 발행을 6.1.6에 옮긴다. 판 인정은 참가자 검증값이 아니라 붙은 기록의 유효 러닝이다(R82).
- 박재영(design-oo/06-02-클래스-running-analysis-profile.md): RankingView.publicTiers·publicTierOf(조회 때 MemberQueryService.summaries로 붙임, D-486), DashboardView.latestRun·latestRunOutcome(D-491)을 6.1.4에 옮긴다.
- 박재영(design-oo/06-03·06-02, code-review v0.22 1차 결과): 06-03에 옮길 때 05-21의 v0.31 문장 대신 code-review 17 8절 U-1(PlacementRoundCheck 구성요소 7, of(…, now)), U-2(PlacementRoundReader + Clock), U-4(취소 대결 결과 조회 404와 placementState), U-9(MatchAcceptance·MatchFound 보조 생성자)를 반영한 문장을 쓴다. 06-02에는 U-3(DashboardView.latestRunOutcome 대신 DashboardController가 결과를 붙임)을 쓴다. 코드 결함 PL1-B1(RunQueryService.summary 예외의 rollback-only)은 running(박재영 도메인)에 예외 없는 조회 `findSummary`를 더하는 방향이라 그 MR 승인을 받는다.
- 박재영(코드 검토 요청): 계약(05-03 5.7.3)의 battle·analysis 항목은 제공하는 쪽이 박재영 도메인이므로 MR에서 승인을 받는다(OWNERS 3절 5). 워치 잠금 문구(D-500)도 워치 담당 확인.

- 박재영(design-oo/06-03-클래스-battle.md, design-oo v0.32): 06-03에 옮길 때 05-22의 문장을 쓴다. PlacementRoundCheck 7구성요소·`of` 두 겹침·SUMMARY_GRACE(D-521), PlacementRoundReader 협력 + Clock·`findSummary`·취소 = BATTLE_NOT_FOUND·복구 창(7일) 뒤 NOT_COUNTED(D-522·D-525), PlacementRoundListener의 uq 위반 처리·`count` 반환 뜻·정정은 count 뒤·로그(D-538), 판 셈 동시성 근거와 Hibernate 회귀 기준 TC-5923(D-539), PlacementMatcher QUEUE_UNAVAILABLE·MatchFound null 직렬화(D-540), MatchAcceptance 3인자·MatchFound 8인자 보조 생성자(D-529), BattleResultService.comparison·theirs·recordGaps의 Optional 조회(D-524). 이전 요청의 "U-1·U-2·U-4·U-9 반영 문장"은 이 항목으로 읽는다.
- 박재영(design-oo/06-02-클래스-running-analysis-profile.md, design-oo v0.32): RunQueryService `findSummary`·`findComparison`·`latestListRow`와 포트 `RunRecordRepository.latestListRow`, "없을 수 있는 기록은 예외를 잡지 말고 Optional 조회" 규칙(D-524), DashboardView는 `latestRun`만(대결 결과는 web.record DashboardController가 붙임, D-523). 이전 요청의 `latestRunOutcome`은 지운다.
- 박재영(srs/03-04-대결-관리.md FR-101, design-oo v0.32 D-534): 이전 요청 "배치고사 판은 랜덤 대결로 남기고 「배치고사 n판」을 덧붙인다"를 "「배틀런 · 배치고사 n판」(n은 대결을 만든 때 판 번호)"로 읽는다(srs 03-08 J24-22 보완과 같음).
- 박재영(코드 검토 요청, design-oo v0.32): running 패키지 `RunQueryService`·`RunRecordRepository`에 더한 예외 없는 조회 3개(PL1-B1·PL1-B5 수정)는 박재영 도메인이라 MR !70에서 승인을 받는다. 워치 잠금 문구 줄바꿈(D-546)도 워치 담당 확인.

- 박재영(코드 검토 요청, sqa v0.9 조건 5): MR !70의 battle·running 경계 계약(05-03 5.7.3, RunQueryService 예외 없는 조회)과 워치 잠금 문구(D-546)를 승인한다. main 219940e1 반영 때 워치 3파일 충돌 해결 결과(J24-12 문구와 W52·W53 안내)도 함께 본다.

### v0.25 (2라운드, CR-147~CR-153, 2026-11-05)

박재영(대결·러닝 측정·워치·기록 분석 담당). 끝에 "(v0.25 변경, CR-xxx)"를 붙인다. 반영 전에는 srs/03-08 3.8.7과 07-17이 앞선다.

- srs/03-04-대결-관리.md FR-257: 위 v0.24 요청("티어가 언랭(배치 중)이면 매칭 시작을 잠그고 「폰에서 배치고사를 마치면 열려요」 (제안)를 보인다. 배치고사는 폰에서만 치른다(J24-12)")은 **철회**한다. 대신 "유효 러닝이 3회 전(언랭)이면 … 남은 횟수 안내를 보인다(폰 FR-080과 같은 규칙)"를 "배치 중(언랭)이어도 잠그지 않고 배치고사 판으로 치른다(FR-291). 서버가 RANKED_ONLY로 거부하면(배치고사 이전 서버) 그때부터 「폰에서 배치고사를 마치면 열려요」를 보인다"로 바꾸고, 문구 표의 두 언랭 행(남은 횟수 앎·모름)을 RANKED_ONLY 거부 한 행으로 바꾼다(CR-150).
- srs/03-03-기록-분석.md FR-070 처리: "랭킹 행에는 순위, 아바타, 닉네임, 누적 유효 거리만 보이고 바로 위 순위까지 남은 거리, 집계 기준 시각, 티어는 보이지 않는다 (v0.13 변경, CR-45)"를 "랭킹 행에는 순위, 아바타, 닉네임, 공개 티어 배지(FR-282), 누적 유효 거리를 보이고 집계 기준 시각은 보이지 않는다. 본인 순위 카드에는 바로 위 순위까지 남은 거리를 보인다(FR-290 v3). 화면 위에 집계 기간과 마감까지 남은 시간을 보인다"로 바꾼다(CR-149, v0.24 요청의 공개 티어 배지 문장과 합친다).
- srs/03-03-기록-분석.md FR-071 처리 끝에 "크루 랭킹이 열리지 않은 배포에서는 개인|크루 분절의 크루 칸을 자물쇠로 잠가 보이고 누를 수 없다(FR-223)"를 더한다(CR-149).
- srs/03-04-대결-관리.md FR-092 처리의 "대결 거리 도달로 검증하고"에 "(경로 끝까지 모자란 거리가 0.5m 이하이면 닿은 것으로 보고 마지막으로 거리가 늘어난 위치의 시각을 도달 시각으로 쓴다. 미터 반올림 차이, CR-151)"를 더한다.
- srs/03-04-대결-관리.md FR-107(또는 도착 판단 문장이 있는 FR) 처리에 "러너 기기는 화면 표시용 반올림 거리가 아니라 반올림 전 누적 거리가 대결 거리 이상일 때 도착으로 본다(CR-151)"를 더한다.
- srs/03-02-러닝-측정.md FR-245 처리에 "워치 시스템 위치가 꺼져 있으면 측정을 시작하지 않고 위치 설정 열기를 안내한다(main W52, CR-153)"를 더한다.
- srs/03-02-러닝-측정.md FR-243 처리에 "워치 메인 화면이 보이는 동안 GPS를 미리 켜고(상한 5분) 「GPS 찾는 중」·「GPS 준비됨」을 보인다(main W53, CR-153)"를 더한다.
- 코드 검토 요청(MR !70): 워치 배치고사(1f04f1df, wear·domain RandomBattleGate), 도착 판정(ef3290f1 domain.battle·domain.run, 0905c26d 서버 running.RunTrack), GPS 예열(80592a08 domain.run GpsWarmUp·BattleRunCoordinator·data.device)은 박재영 도메인이라 MR에서 승인을 받는다. 서버 `RunTrack.timeToReach` 허용 오차는 running 경계라 제공 쪽(박재영) 승인이 필요하다.
- 박재영(design-oo/06-02·06-03·06-06, design-oo v0.33): 05-23의 D-558(RunProgress.exactDistanceMeters, GapTracker.hasArrived(Double), OpponentFeed.hasArrived), D-559(서버 RunTrack ROUNDING_TOLERANCE·exerciseTime), D-560·D-561(GpsWarmUp 포트, BattleRunCoordinator 협력 gpsWarmUp·warmUpGps·showGps, BattleRunState gps, LocationFilter.MAX_ACCURACY_METERS 공개), D-553~D-557(워치 배치고사), D-563(main의 watch.ui.GpsWarmUp·HealthServicesGpsWarmUp·LocationSettingState)를 담당 명세에 옮긴다. 05-21 D-500·05-22 D-546의 워치 잠금 문장은 쓰지 않는다.
- 박재영(코드 검토 요청, code-review 21장): 45f7d383의 서버 running `RunTrack.timeToArrive`·`RunRecord.timeToArrive`와 `RunQueryService.timeToReach(recordId, d)`의 도착용 전환(PL5-B1)은 running 경계라 MR !70에서 승인한다. 06-02 반영 문장은 design-oo 05-23 1.3절 D-559 보정.
