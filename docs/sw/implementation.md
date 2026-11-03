# 구현 기록: 런크크

> 작성: 2026-11-03 | 버전: v0.10 | 입력: pending/feature-S15P31A207-247-ai-coach.md(박재영 2026-11-03, sw-sdlc merge), implementation/ai-coach-consent-박.md(LLM 동의·AI 코치 화면 구현 기록, feature/S15P31A207-247-ai-coach e66eb8a0, 박재영 보고: Android 게이트 통과·JUnit 1,877건 실패 0); 이전 판(v0.9) 입력: 사용자 결정 W43(runkk-work/watch-v2/decisions.md), code-review 16차(AN16-1·AN16-2·WR16-1~WR16-4), 코드 저장소 `C:\Users\SSAFY\Documents\GitHub\S15P31A207-watch` feature/runkk-watch-v2 f4dd2a68..cc8c138b(R15, 14.3·14.4 I14-46·14.5 반영 표시); 이전 판(v0.8) 입력: 사용자 결정 W34~W42(runkk-work/watch-v2/decisions.md), 피그마 모션 목록 runkk-work/watch-v2/figma/motion.md, 코드 저장소 `C:\Users\SSAFY\Documents\GitHub\S15P31A207-watch` feature/runkk-watch-v2 4d85393d..f4dd2a68(R11~R14, UI5, M1, W42, 14.3·14.4 I14-36~I14-45·14.5); 이전 판(v0.7) 입력: docs/sw/srs.md v0.18(CR-92~CR-102), docs/sw/design-oo.md v0.22(05-16 D-365~D-394), docs/sw/design-review.md v0.17(22.9 조건부 합격), 사용자 결정 W01~W22(runkk-work/watch-v2/decisions.md), 계약 고정 runkk-work/watch-v2/contract-v2.md, 코드 저장소 `C:\Users\SSAFY\Documents\GitHub\S15P31A207-watch` feature/runkk-watch-v2(develop 6400955a에서 땀); 이전 판(v0.6) 입력: 코드 저장소 `C:\Users\SSAFY\Documents\GitHub\S15P31A207-fig` feature/runkk-figma-v1 57e11eec(758de88f 다음, MR !53, 13.11), 사용자 결정 D150·D151; 이전 판(v0.5) 입력: 코드 저장소 feature/runkk-figma-v1 758de88f(242612e9 다음, MR !53, 13.10), docs/sw/code-review.md v0.10(11차 합격 0/0/35, AN11-4~AN11-6·AN11-9), docs/sw/design-review.md v0.14(R59), docs/sw/test.md v0.12(TF-V12-01~03); 이전 판(v0.4) 입력: 사용자 결정 D131~D148(runkk-work/figma-1029/decisions-1029.md 표 끝), docs/sw/srs.md v0.17(07-09 CR-82~CR-91), docs/sw/design-oo.md v0.19(05-14 D-344~D-357, v0.17·v0.18 D-336~D-343), docs/sw/code-review.md v0.9(10차 합격 0/3/24), 코드 저장소 `C:\Users\SSAFY\Documents\GitHub\S15P31A207-fig` feature/runkk-figma-v1 cfbd103c..242612e9(직전 0ca948f8, MR !53); 이전 판(v0.3) 입력: 사용자 결정 D102~D130(runkk-work/figma-1029/decisions-1029.md "2026-10-30~31 후속 결정"), docs/sw/design-oo.md v0.16(05-11 D-313~D-330), docs/sw/srs.md v0.16(07-07 CR-59~CR-77, 07-08 CR-78~CR-81), docs/sw/code-review.md v0.8(9차 합격), 코드 저장소 `C:\Users\SSAFY\Documents\GitHub\S15P31A207-fig` feature/runkk-figma-v1 e72897c7..cfbd103c(e5475f3c, cfbd103c, MR !53); 이전 판(v0.2) 입력: docs/sw/design-oo.md v0.14(D-218~D-300), docs/sw/design-review.md v0.11(18장 조건부 합격), docs/sw/srs.md v0.13(7.13.9), docs/sw/test.md v0.9, 코드 저장소 `C:\Users\SSAFY\Documents\GitHub\S15P31A207`(기준 origin/develop 7c42b0fe); 그 이전 판(v0.1) 입력: docs/sw/design-oo.md v0.11(F 변경 명세, D-89~D-166), docs/sw/design-review.md v0.10(17장 조건부 합격: 불허 0·검토 2), docs/sw/srs.md v0.10(7.13 CR-01~CR-10, 7.13.5 보완), docs/sw/test.md v0.6, 코드 저장소 `C:\Users\SSAFY\Documents\GitHub\S15P31A207`(GitLab s15-final/S15P31A207, 기준 develop d7618cb)

## 0. 문서 상태

| 항목 | 값 |
|------|----|
| 현재 단계 | **워치 v2 반복(14장) 1단계 진행 중**: BE1 서버 API 2개(`-watch-be`) ∥ A1 :data:phone 분리·domain 포트·wear-contract(`-watch-an`). 통합 브랜치 `feature/runkk-watch-v2`, Draft MR은 1단계 push 때 연다(W22). 이전 반복: v0.19 소리·첫 화면·오늘 계획 페이스 반복(D139~D148) 구현·통합 검증·실기기 확인 완료, MR !53으로 develop에 병합(6400955a). 그 반복은 13장이다. 코드 리뷰 11차 합격(0/0/35) 뒤 리뷰·설계 검토·시험 지적을 758de88f로 반영해 push했다(13.10). 앞 반복: v0.16 화면 확인 후속(12장)은 코드 리뷰 10차 합격(0/3/24), 10차 결함 수정과 D136·D138(cfbd103c..0ca948f8)은 13.0 요약 |
| 통합 브랜치 / MR | `feature/runkk-figma-v1` / https://lab.ssafy.com/s15-final/S15P31A207/-/merge_requests/53 |
| 마지막 push 커밋 | 57e11eec(직전 758de88f, 242612e9, 0ca948f8, 12장 끝 cfbd103c) |
| 다음 단계 | 배포 전 조건(BE10-3, BE10-7, BE10-6; AN10-5는 57e11eec로 해소, 13.11), /sw-sqa. 남은 시험 보강(BE11-3, AN11-9)은 13.11에서 끝났다. 11차 이전 다음 단계: /sw-code-review 11차(13.9, 끝남) |

v0.16 설계(D-313~D-330)는 design-review 19~21장에서 검증을 마쳤다(2026-10-31 정정: 앞 판의 "아직 거치지 않았다"는 낡은 문장이었다). 워치 v2 설계(v0.22)는 22.9에서 조건부 합격이고 남은 검토는 이전부터 이어진 R-2, R-3, R54, R57뿐이라 구현을 막지 않는다. 이전 판의 설계 검증 판정은 조건부 합격이다. 남은 검토 2건(R-2 member → policy, R-3 reward → battle 안정성 역전)은 D-133·D-134로 조건부 허용했고 구조 시험 규칙 (11)로 고정했으므로 구현을 막지 않는다고 판단했다.

## 목차

본문은 장 단위로 나눠 `implementation/` 폴더에 두었다(2026-10-31 분할, 내용 변경 없음, CONVENTIONS.md 7절). 절 번호는 원래 번호를 그대로 쓴다.

| 파일 | 담은 장·절 |
|------|-----------|
| [01-기준-단계](implementation/01-기준-단계.md) | 1. 기준 · 2. 실행 단위와 단계 · 3. 단계 기록 |
| [04-결정-계약-리뷰](implementation/04-결정-계약-리뷰.md) | 4. 결정 목록 · 5. 계약 · 6. 통합 기록 · 7. 미리보기 확인 · 8. 리뷰 반복 · 9. 설계서 갱신 요청 · 10. 팀이 할 일 |
| [11-v0.14-피그마-반영](implementation/11-v0.14-피그마-반영.md) | 11. v0.14 피그마 반영 반복 (2026-10-29~30) |
| [12-v0.16-화면-확인-후속](implementation/12-v0.16-화면-확인-후속.md) | 12. v0.16 화면 확인 후속 반복 (2026-10-30~31) |
| [13-v0.19-소리-첫화면](implementation/13-v0.19-소리-첫화면.md) | 13. v0.19 소리·첫 화면·오늘 계획 페이스 반복 (2026-10-31 오후) |
| [14-v0.22-워치-v2](implementation/14-v0.22-워치-v2.md) | 14. v0.22 워치 v2 반복 (2026-10-31~) |
| [ai-coach-consent-박](implementation/ai-coach-consent-박.md) | LLM 동의·AI 코치 화면 구현 기록 (박재영, #247·#246 준비, 2026-11-03) |
| [A-이전-구현-요약](implementation/A-이전-구현-요약.md) | 부록 A. 이전 구현 요약 (기초 시스템, 2026-10-20~22) |
