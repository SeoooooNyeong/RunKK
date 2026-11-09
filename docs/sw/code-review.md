# 런크크 코드 리뷰 결과서
> 작성: 2026-11-09 | 버전: v0.29 | 입력: (같은 판 보완 2026-11-09(세 번째 병합): 병합 대기 feature-S15P31A207-273-coach-consent.md(박재영, sw-sdlc merge), 박재영 자가 리뷰 조각 code-review/코치재동의-박.md; 같은 판 보완 2026-11-09: 병합 대기 docs-S15P31A207-277-owned-specs.md, feature-S15P31A207-241-ai-coach-sse.md, feature-S15P31A207-243-writer-model-admin.md(박재영, 2026-11-09, sw-sdlc merge), 박재영 자가 리뷰 조각 code-review/코치중계-박.md, code-review/writer-모델관리-박.md) 병합 대기 feature-S15P31A207-275-event-ui-align.md(이성현, sw-sdlc merge), code-review/이벤트-화면-디자인-성.md(MR !104, 코드 12db88c2, 문서 7c64b3a8, 수정 88e31d9a), 임시 결함 번호 EV-성-18~EV-성-21 → PL8-A1~PL8-A4; 이전 판(v0.28) 입력: 병합 대기 docs-S15P31A207-275-event-docs.md(이성현, sw-sdlc merge), 코드 hotfix/S15P31A207-275-v0114-coach-celeb origin/main...d824b2a2(MR !90 이벤트 1차), 수정 d221fd54, 2f99b573, 응모자 명단 d06641bb, code-review/이벤트-1차-성.md, 코드 b26b3317(공지 알림), 수정 62b7f7e8, code-review/공지알림-1차-성.md, 임시 결함 번호 → PL6, PL7 계열; 이전 판(v0.27) 입력: 2차 배포 통합 MR !91 코드 리뷰 결과서 초안(runkk-work/2nd-release/code-review-draft.md), 코드 feature/S15P31A207-222-2nd-release 55cfd678..e9baef18(읽기만 함), srs v0.29·design-oo v0.37, 2026-11-08 사용자 결정(결함 9건 모두 병합 전 수정); 이전 판(v0.26) 입력: 병합 대기 fix-S15P31A207-272-coach-robustness.md(AC-1·AC-3·AC-9·AC-10 해소 근거, code-review/16 후속 절); 이전 판(v0.25) 입력: 100KB 넘던 조각 01-backend·02-android 분할(내용 변경 없음, CONVENTIONS 7절); 이전 판(v0.24) 입력: (v0.24, 병합 반영) 박재영 병합 대기 #244 ai-privacy(develop 60c3385f)·#276 avatar-async(1d1e5d44)·#271 watch-celebrity(7876cba9), code-review/ai-자료파기-박.md·얼굴비동기-박.md·워치셀럽404-박.md; 이전 판(v0.23) 입력: 코드 hotfix/S15P31A207-275-v0111-feedback 4a2722fd..80592a08(핫픽스 추가 변경 9커밋)과 수정 45f7d383, design-oo v0.33 05-23, design-review v0.23 27장, test v0.20 20장; 이전 판(v0.22) 입력: 코드 hotfix/S15P31A207-275-v0111-feedback 4129283c(MR !70, 배치고사 3차 확인, 범위 git diff c2685561 4129283c: 서버 e35dba34(병합 23e74149), 앱 b4798db8·0d648155·5a24de5c·ac411a32·5d3d8871·6d13a501·13668d24·0aab80c5·eb9161e1), code-review/18-배치고사-2차 결함 표, design-oo 05-22(v0.32 D-521~D-546, SQ-51, 2.4.28), test 10-28(v0.19 TC-5954~TC-5973), 총괄 결정 기록(AN·BE 수정 2차 행); 같은 판 2차 입력: 코드 hotfix/S15P31A207-275-v0111-feedback c2685561(MR !70, 배치고사 2차, 범위 git diff bad8c750 96cc3f5e: 서버 1차 결함 수정 병합 3dfb8552, git diff 3dfb8552 c2685561: AN-2 6a57bee1·a477be32, 앱 1차 수정 d30b944c), code-review/17-배치고사-1차 결함 표, design-oo 05-21 D-499~D-501·D-519·D-520·2.4.3·3.4·7·8절, srs 03-08 FR-276·FR-279~FR-286·J24-3·J24-12·J24-20·J24-22, test v0.18 TC-5864~TC-5910·18.15.8, 총괄 결정 기록(BE 수정 1차·AN-2·PL1-A 행); 같은 판 1차 입력: 코드 hotfix/S15P31A207-275-v0111-feedback bad8c750(MR !70, 배치고사 1차, 범위 git diff df211dc5..bad8c750: BE-0 9368e22e, AN-1 12d15f1d·a1185e20, BE d37d0cfe, infra bad8c750), design-oo 05-21(v0.31 D-475~D-520), 05-03 5.7.3, srs 03-08(FR-274~FR-290, J24-1~J24-23), design-review 25장(R82~R95), test v0.18(10-25·10-26), 총괄 결정 기록(self-decisions BE·AN-1 행); 이전 판(v0.21) 입력: 코드 develop 09e568dc(MR !59 feature/S15P31A207-247-ai-coach, 범위 git diff c29df9fa 09e568dc -- android/, S15P31A207-233 AI 코치 화면 독립 리뷰), design-oo 05-19(v0.26·v0.28 D-450~D-455·D-474, 2.6 앱 표, 3절), srs 03-03 FR-261~FR-264·03-01 FR-015, code-review/ai-coach-consent-박.md(자가 리뷰 대조); 이전 판(v0.20) 입력: pending/feature-S15P31A207-247-ai-coach.md(박재영 2026-11-03, sw-sdlc merge), code-review/ai-coach-consent-박.md(feature/S15P31A207-247-ai-coach e66eb8a0, LLM 동의·AI 코치 화면); 이전 판(v0.19) 입력: 코드 S15P31A207-watch feature/runkk-watch-v2 793eb85e(18차 확인, 범위 --first-parent 4017839f..793eb85e: R17 W50), 4017839f(17차 확인, 범위 --first-parent f4dd2a68..4017839f: R15·R16), 사용자 결정 W43~W49, design-oo.md v0.27(05-20 D-461~D-473), design-review.md v0.20(24장 R79~R81), srs.md v0.22; 이전 판(v0.17) 2026-11-02 | 버전: v0.17 | 입력: 코드 S15P31A207-watch feature/runkk-watch-v2 f4dd2a68(16차 확인, 범위 --first-parent 4d85393d..f4dd2a68: R11·R12·R13·UI5·R14 W40·M1 W41·W42·f4dd2a68), 사용자 결정 watch-v2/decisions.md W37~W42, watch-v2/figma/motion.md, design-oo.md v0.25(05-18 D-456~D-460)·v0.26, design-review.md v0.19, srs.md v0.21; 이전 판(v0.16) 2026-11-02 | 버전: v0.16 | 입력: 코드 S15P31A207-watch feature/runkk-watch-v2 3ee5138e(15차 확인, 범위 6906c76a..3ee5138e: UI1~UI4, W34~W36), 사용자 결정 watch-v2/decisions.md W34~W36, design-review.md v0.19; 이전 판(v0.15) 2026-11-02 | 버전: v0.15 | 입력: 코드 S15P31A207-watch feature/runkk-watch-v2 89457e26(14차 확인, 범위 807d744a..89457e26: R4~R7, MR !57), design-oo.md v0.25, design-review.md v0.19; 이전 판(v0.14) 2026-11-01 | 버전: v0.14 | 입력: 코드 S15P31A207-watch feature/runkk-watch-v2 807d744a(13차 확인, 범위 a80c86b4..807d744a: R1·R2·W26·R3, MR !57), design-oo.md v0.23, srs.md v0.19; 이전 판(v0.13) 2026-11-01 | 버전: v0.13 | 입력: 코드 S15P31A207-watch feature/runkk-watch-v2 a80c86b4(12차, 기준 develop 6400955a, MR !57), design-oo.md v0.22(05-16 D-365~D-394), srs.md v0.18(CR-92~CR-102), implementation.md v0.7(14장 I14-1~I14-35, PV-1); 이전 판(v0.12) 2026-10-31 | 버전: v0.12 | 입력: 코드 S15P31A207-fig feature/runkk-figma-v1 57e11eec(11차 해소 기록 2, 758de88f 다음, MR !53, 호출자 보고 서버 1,690건 실패 0), 사용자 결정 D150·D151, 코드 S15P31A207-fig feature/runkk-figma-v1 758de88f(11차 해소 기록, 242612e9 다음, MR !53, 호출자 보고 앱 단위 1,474건 실패 0), design-oo.md v0.19(05-12 D-331~D-340, 05-13 D-341~D-343, 05-14 D-344~D-357, 11차), srs.md v0.17(07-09 CR-82~CR-91, 11차), 코드 S15P31A207-fig feature/runkk-figma-v1 0ca948f8(범위 A, 기준 cfbd103c: de6f17ed·56c6d90b·f0691b78·c26b1be5·5f861d0e·3b59215b·0ca948f8)·242612e9(범위 B, 기준 0ca948f8)(11차), code-review 07-10차 조각 결함 표(11차 추적), 코드 feature/runkk-figma-v1 cfbd103c(10차, 기준 5885a608: v0.15 재대조 5885a608..e72897c7 + v0.16 후속 e72897c7..cfbd103c), design-oo.md v0.16(05-11 D-313~D-330, 02-04, 05-10 D-301~D-312, 10차)·srs.md v0.16(07-06~07-08 CR-52~CR-81, 10차)·implementation.md v0.3(12장 K16·V16·12.9, 10차)·design-review.md v0.12(19장, 10차), 2026-10-31 사용자 결정(리뷰 기준 5885a608, D-327 상대 성별 회원 ID 고정 채택), 코드 feature/runkk-figma-v1 5885a608(r8be·r8an 수정, 9차, 기준 c2e75161), design-oo.md v0.14(D-218~D-300, 05-06·05-07·05-08·05-09·02-03, 8차)·design-review.md 18장(v0.14 검증, 8차)·test.md v0.9(12.9.3 우선 케이스, 8차), 코드 feature/runkk-figma-v1 c2e75161(MR !53, 기준 origin/develop 7c42b0fe, 8차), design-oo.md v0.11(D-1~D-189, 5~7차)·v0.7~v0.10(1~4차), design-review.md v0.10(17장, 5~7차)·v0.7, implementation.md v0.1(5차), 코드 feature/runkk-v010 7117785f(7차, 기준 447a3f7c)·a27c914f(6차)·bfe55e1c(MR !14 4ee91aef, 5차, 기준 develop d7618cb)·feature/runkk-impl 9f2af7e~d562d26(MR !8, 1~4차)

**최신 판정(리뷰 2026-11-08 2차 배포 통합 확인, MR !91 e9baef18..1ae0b108): 종합 ■ 합격  □ 조건부  □ 불합격 (0 / 0 / 4).** 1차 결함 9건(BR-1 중 포함)과 실제 ai 서버 시험 결함 7건, 시간대 후속 모두 해소. 새 경 4건(CR2-A1, CR2-B1, CR2-B2, CR2-T1)은 사용자 결정으로 모두 고쳤다(1e93c919 B1, 17fd942e T1, b23837ca A1, c5ee333a B2). develop 합치기 9fbcdf43(-272·-278, 충돌 ProgressNotice.kt·ProgressNoticeText.kt 양쪽 살림). 설계서 갱신 요청 U2-1~U2-3([23장](code-review/23-2차배포-통합-확인.md) 8절). MR !91은 2026-11-09 develop에 squash 병합됐다(69e16ff5, 리뷰 승인 없이 사용자 결정). 리뷰 뒤 더한 2차 잠금 해제·카드·셀카(9c7f967f..cbca2c38)는 리뷰 범위 밖이다.

(v0.29) 2026-11-09 이벤트 화면 피그마 정식 디자인 반영 리뷰(MR !104)는 합격이다(0 / 1 / 1, 제안 2. 주요 PL8-A1 문서 병합 충돌은 88e31d9a에서 수정, 경미 PL8-A2와 제안 PL8-A3, PL8-A4는 남김). 끝의 「리뷰 2026-11-09 (이벤트 화면 디자인 반영)」 절과 조각 [이벤트-화면-디자인-성](code-review/이벤트-화면-디자인-성.md)에 있다.

(v0.28) 같은 날 핫픽스 0.11.4(MR !90)의 이벤트 1차 리뷰는 합격(조건부 배포, 0 / 2 / 10, PL6-B5 배포 절차 남음), 운영자 공지 알림 1차 리뷰는 합격(0 / 1 / 3)이다. 끝의 두 리뷰 절과 조각 [이벤트-1차-성](code-review/이벤트-1차-성.md), [공지알림-1차-성](code-review/공지알림-1차-성.md)에 있다.

**최신 판정(리뷰 2026-11-08 2차 배포 통합 1차, MR !91 feature/S15P31A207-222-2nd-release 55cfd678..e9baef18): 종합 ■ 합격  □ 조건부  □ 불합격 (0 / 1 / 8), 단 BR-1 병합 전 수정 조건.** 중 1: BR-1(앱은 크루런 일시정지를 허용하는데 서버 POST /runs가 크루런 정지 구간을 400으로 거부해 멈춘 크루런 기록이 저장되지 않음). 경 8: BR-2~BR-5, BR-C2, AN-3~AN-5. 사용자 결정(2026-11-08): 결함 9건 모두 병합 전에 고친다 → 수정 완료(e9baef18..1ae0b108, 19커밋, e2e 결함 7건 포함), 확인 리뷰 진행 중. 설계서 갱신 요청 U-1~U-6([22장](code-review/22-2차배포-통합-1차.md) 8절).

**최신 판정(리뷰 2026-11-05 핫픽스 추가 변경 1·2차, hotfix 4a2722fd..80592a08 + 수정 45f7d383): 종합 ■ 합격  □ 조건부  □ 불합격 (0 / 0 / 7).** 1차 중 2(PL5-B1 서버 0.5m 도달 허용이 신기록·구간까지 번짐, PL5-A1 랭킹 늦은 응답이 고른 기간을 덮음)와 경 PL5-A2(동순위 「n위까지」)·PL5-T1(시험 이름 번호)을 45f7d383에서 TDD로 고쳤고 android 게이트(2,496건)·backend build(1,935건) 통과. 남은 경 7(PL5-A2 남은 경우, PL5-A3~A8).

**최신 판정(리뷰 2026-11-05 배치고사 4차 확인, hotfix 564db6af, 3차 결함 해소 확인): 종합 ■ 합격  □ 조건부  □ 불합격 (0 / 0 / 9).** PL3-A1(랜덤 대결 화면이 미확인 완료 화면을 열도록 `PlacementProgress.isCompletionUnseen`으로 공용화, 홈 카드·직접 진입 모두 덮음)과 PL3-A2(저장 실패 때 회원 ID로 묶은 프로세스 사본, 무한 replace 없음, 다른 회원에게 안 샘) 해소. 구조 허용 표 무변경. 새 경 1(PL4-A1 시험 공백), 이월 경 8. 상세는 [20-배치고사-4차](code-review/20-배치고사-4차.md). 이전 판정: 3차 조건부(0/1/9) [19-배치고사-3차](code-review/19-배치고사-3차.md).

**최신 판정(리뷰 2026-11-05 배치고사 2차, hotfix c2685561, 1차 수정 확인 + AN-2): 종합 □ 합격  ■ 조건부  □ 불합격 (0 / 2 / 14).** 1차 중 5건 모두 닫힘(PL1-B2는 시험 출력 증거로). 새 중 2: PL2-A1(대결 진행 화면이 배치고사 상대를 「?」로 보이지 않음, FR-276), PL2-A2(판 셈이 30초 안에 안 정해지거나 진행 읽기 실패면 완료 화면에 닿을 길 없음, FR-281). 판정표 숫자는 합격 구간이나 이번 반복 합격 조건(총괄: 치명·중 0)에 따라 조건부. 상세는 [18-배치고사-2차](code-review/18-배치고사-2차.md).

**최신 판정(리뷰 2026-11-04 배치고사 1차, hotfix bad8c750, BE-0·BE·AN-1·infra): 종합 □ 합격  ■ 조건부  □ 불합격 (0 / 5 / 16).** 중 5: PL1-B1(요약 없는 판의 결과 조회 500, TF-V18-01 경로), PL1-B2(잠금 대기 뒤 판 목록이 옛 값일 수 있음, 추정), PL1-B3(탈퇴 ∥ 매칭 때 진행 행 남음), PL1-A1(홈 잔디·오늘이 UTC 날짜), PL1-T1(TC-5923 동시 셈 시험이 순차). AN-2는 2차에서 본다. 상세는 [17-배치고사-1차](code-review/17-배치고사-1차.md).

**최신 판정(리뷰 2026-11-03 18차 확인, 워치 v2 4017839f..793eb85e, R17 W50): 종합 ■ 합격  □ 조건부  □ 불합격 (0 / 0 / 21).** 셀프·셀럽 카드 두 번 눌러 시작이 W50·CR-132와 같다. 새 경 2건(WR18-1 Picker.selected 설명, WR18-2 누르기 이름 화면 시험 없음). 상세는 [15-18차-v022-확인](code-review/15-18차-v022-확인.md).

**최신 판정(리뷰 2026-11-03 17차 확인, 워치 v2 f4dd2a68..4017839f, R15·R16): 종합 ■ 합격  □ 조건부  □ 불합격 (0 / 0 / 19).** 16차 지적 6건과 설계 검증 R79~R81 모두 해소. 새 경 1건(AN17-1 홈 띠가 전체 미전송 목록을 구독).

**최신 판정(리뷰 2026-11-02 16차 확인, 워치 v2 4d85393d..f4dd2a68, R11~R14·UI5·W41·W42): 종합 ■ 합격  □ 조건부  □ 불합격 (0 / 1 / 23).** 15차 지적 6건(WR15-1~6) 모두 해소. 새 중 1건(AN16-1 워치에만 있는 거부 기록을 다시 보낼 길 없음), 새 경 5건(AN16-2, WR16-1~4). WR16-4는 CI ktlintCheck를 막으므로 push 전에 고친다.

**최신 판정(리뷰 2026-11-02 15차 확인, 워치 v2 6906c76a..3ee5138e, UI1~UI4): 종합 ■ 합격  □ 조건부  □ 불합격 (0 / 0 / 24).** 14차 지적 5건(BE14-1~3, WR14-1·2) 모두 해소. 새 경 6건(WR15-1~6), 중 이상 없음.

**최신 판정(리뷰 2026-11-02 14차 확인, 워치 v2 807d744a..89457e26, MR !57): 종합 ■ 합격  □ 조건부  □ 불합격 (0 / 0 / 23).** 13차 새 경 8건 모두 해소. 병합 전 BE14-1·WR14-2는 R8·R9에서 고친다.

**최신 판정(리뷰 2026-11-01 13차 확인, 워치 v2 수정 a80c86b4..807d744a, MR !57): 종합 ■ 합격  □ 조건부  □ 불합격 (0 / 0 / 26: 새 경 8, 보류 4, 앱 이월 14).** 12차 중 3건 모두 해소. 병합 전 WR13-1·WR13-4·BE13-3은 수정 반복 R4·R5에서 고친다.

**최신 판정(리뷰 2026-11-01 12차, 워치 v2 feature/runkk-watch-v2 6400955a..a80c86b4, MR !57): 종합 ■ 합격  □ 조건부  □ 불합격 (서버 0/0/7, 앱 0/3/12 + 이월 경 14).** 앱 중 결함이 합격 상한 3에 닿아 병합 전에 AN12-1·WR12-1·WR12-2를 고친다. 아래 11차 판정은 이전 반복이다.

**최신 판정(리뷰 2026-10-31 11차, 10차 결함 수정·v0.18 결정·v0.19 소리와 첫 화면 feature/runkk-figma-v1 cfbd103c..242612e9, MR !53): 종합 ■ 합격  □ 조건부  □ 불합격 (치명 0 / 중 0 / 경 35)**
**해소 기록 2(57e11eec, 2026-10-31):** AN10-5·AN11-7·AN11-8·AN11-9·BE11-3 해소. 남은 결함 0 / 0 / 27, 판정 합격 그대로. 배포 전 조건은 BE10-3·BE10-7·BE10-6만 남는다. 상세는 [08-11차-v017-v019](code-review/08-11차-v017-v019.md) 11.1절.
**해소 기록(758de88f, 2026-10-31):** AN11-4·AN11-5·AN11-6 해소, AN11-9 일부 해소(앱 시험 8건과 경로 단언 추가, 첫 화면·LoopingMusic 시험은 남음), 서버 BE11-3 남음, 나머지 경은 그대로다. 남은 결함 0 / 0 / 32, 판정 합격 그대로. 상세는 [08-11차-v017-v019](code-review/08-11차-v017-v019.md) 11절.
10차 중 3건(BE10-1, AN10-1, AN10-2)과 경 BE10-2·BE10-4, 알려진 결함 K16-1·K16-2, 설계 요청 U10-A2가 모두 해소됐다. 새 결함은 서버 0/0/3(BE11-1~BE11-3), 앱 0/0/10(AN11-1~AN11-10)이고 중 이상은 없다. 경 35는 새 경 13과 이월 경 22(서버 BE10-3·5·6·7·8·9, BE9-1·2, BE8-7·10·13, 앱 AN10-3~AN10-9, AN8-14·18·20·21)다. 먼저 볼 경은 AN11-4(스플래시 아래에 미리 그린 화면 위로 남은 대결 대화상자가 뜨고 TalkBack이 아래 단추를 누를 수 있다, CR-86), AN11-6(저장 중 고른 목표 페이스가 말없이 버려지는데 화면에는 선택돼 보인다), AN11-9(새 규칙 단위 시험 없음)다. 배포 전 조건은 10차와 같다: BE10-3 nginx yaml, BE10-7 CI 변수 RUNKK_AI_DB_PASSWORD, BE10-6 assetlinks 지문, AN10-5 로그아웃 때 성별 사본. 11차 절은 이 파일 끝의 `## 리뷰 2026-10-31 (11차)`와 [08-11차-v017-v019](code-review/08-11차-v017-v019.md)에 있다.
이전 판정(리뷰 2026-10-31 10차, cfbd103c): 합격(0/3/24). 이전 판정(리뷰 2026-10-30 9차, 5885a608): 합격(0/1/10). 이전 판정(리뷰 2026-10-30 8차, c2e75161): 종합 조건부(0/6/29, 서버 합격 0/3/10 + 앱 합격 0/3/19, 2차 규칙대로 중 합산). 이전 판정(리뷰 2026-10-27 7차, 7117785f): 합격(0/2/6). 이전 판정(리뷰 2026-10-23 6차, a27c914f): 합격(0/1/36). 이전 판정(리뷰 2026-10-23 5차, bfe55e1c): 조건부(0/10/65). 이전 판정(리뷰 2026-10-21 4차, 기초 시스템 d562d26): 합격(0/0/18).

## 변경 이력

| 버전 | 날짜 | 바뀐 것 |
|------|------|---------|
| v0.29 | 2026-11-09 | 같은 판 보완(세 번째 협업 병합). 박재영 자가 리뷰 조각 [코치재동의-박](code-review/코치재동의-박.md)(#273, 검토 지점 3개 반영) 연결. 결함 번호 없음 |
| v0.29 | 2026-11-09 | 같은 판 보완(두 번째 협업 병합). 박재영 자가 리뷰 조각 2개 연결: [코치중계-박](code-review/코치중계-박.md)(#241, 검토 지점 5개 반영), [writer-모델관리-박](code-review/writer-모델관리-박.md)(#243, 검토 5개 반영). 사람 리뷰는 MR에서 했고 이 문서에 결함 번호를 새로 매기지 않았다 |
| v0.29 | 2026-11-09 | 협업 병합(`/sw-sdlc merge`). 이성현 조각 [이벤트-화면-디자인-성](code-review/이벤트-화면-디자인-성.md) 연결(MR !104, 0 / 1 / 1, 제안 2, 합격), 결함 PL8-A1~PL8-A4, 설계 D-629~D-632 |
| v0.28 | 2026-11-09 | 협업 병합(`/sw-sdlc merge`). 이성현 조각 2개 연결: [이벤트-1차-성](code-review/이벤트-1차-성.md)(MR !90 이벤트 1차, 0/2/10, 합격 조건부 배포), [공지알림-1차-성](code-review/공지알림-1차-성.md)(MR !90 b26b3317 운영자 공지 알림, 0/1/3, 합격). 임시 결함 번호를 정식으로 바꿨다: 이벤트 1~17 → PL6-B1~PL6-B6(1~6), PL6-A1~PL6-A3(7~9), PL6-B7~PL6-B14(10~17), 공지 18~23 → PL7-B1~PL7-B4(18~21), PL7-A1(22), PL7-B5(23). B는 서버와 인프라, A는 앱이다 |
| v0.27 | 2026-11-08 | 2차 배포 통합 MR !91 1차 리뷰를 새 조각 [22-2차배포-통합-1차](code-review/22-2차배포-통합-1차.md)로 더했다(합격 0/1/8, BR-1 병합 전 수정 조건, 결함 BR-1~BR-5·BR-C2·AN-3~AN-5, 설계서 갱신 요청 U-1~U-6). 목차 100KB 상한 때문에 4차 리뷰 요약 절을 [00-리뷰-2026-10-21-4차-요약](code-review/00-리뷰-2026-10-21-4차-요약.md)으로 옮겼다(내용 그대로) 같은 판 보정(2026-11-08): 확인 리뷰를 새 조각 [23-2차배포-통합-확인](code-review/23-2차배포-통합-확인.md)으로 더했다(합격 0/0/4, 경 4건 수정, develop 합치기 9fbcdf43 기록), 22장 9절에 23장 링크. 같은 판 보정(2026-11-09): 23장 9절에 MR !91 병합(69e16ff5, 리뷰 승인 없이 사용자 결정)과 리뷰 범위 밖 커밋 기록 |
| v0.25 | 2026-11-08 | 100KB 넘던 01-backend(231KB)·02-android(238KB)를 60KB 이하 조각으로 나눴다(원래 이름은 첫 조각, 내용 변경 없음). 새 조각: [01b-backend-2.1](code-review/01b-backend-2.1.md), [01c-backend-2](code-review/01c-backend-2.md), [01d-backend-리뷰-기준](code-review/01d-backend-리뷰-기준.md), [01e-backend-4](code-review/01e-backend-4.md), [02b-android-1](code-review/02b-android-1.md), [02c-android-2.3](code-review/02c-android-2.3.md), [02d-android-AN5-C-17-상세-빠진](code-review/02d-android-AN5-C-17-상세-빠진.md), [02e-android-5](code-review/02e-android-5.md). 리뷰 2026-10-21 (3차)·(4차) 앵커 링크는 옮겨 간 조각으로 고쳤다 |
| v0.24 | 2026-11-07 | 박재영 자가 리뷰 조각 3개 추가(병합 반영): [ai-자료파기-박](code-review/ai-자료파기-박.md)(#244), [얼굴비동기-박](code-review/얼굴비동기-박.md)(#276), [워치셀럽404-박](code-review/워치셀럽404-박.md)(#271). 사람 리뷰는 MR에서 받는다 |
| v0.22 | 2026-11-05 | 배치고사 4차 확인 리뷰(3차 결함 수정 4129283c..564db6af) 추가: 판정 합격(0/0/9), 조각 [20-배치고사-4차](code-review/20-배치고사-4차.md) 신설. 같은 판 안의 추가(총괄 지시) |
| v0.22 | 2026-11-05 | 배치고사 3차 확인 리뷰(2차 수정 c2685561..4129283c) 추가: 판정 조건부(0/1/9), 조각 [19-배치고사-3차](code-review/19-배치고사-3차.md) 신설. 같은 판 안의 추가(총괄 지시) |
| v0.22 | 2026-11-05 | 배치고사 2차 리뷰(1차 수정 bad8c750..96cc3f5e 확인 + AN-2 3dfb8552..c2685561) 추가: 판정 조건부(0/2/14), 조각 [18-배치고사-2차](code-review/18-배치고사-2차.md) 신설. 같은 판 안의 추가(총괄 지시) |
| v0.22 | 2026-11-04 | 배치고사 1차 리뷰(hotfix df211dc5..bad8c750, BE-0·BE·AN-1·infra) 추가: 판정 조건부(0/5/16), 조각 [17-배치고사-1차](code-review/17-배치고사-1차.md) 신설. 목차 파일 100KB 상한 때문에 3차 리뷰 요약 절을 [00-리뷰-2026-10-21-3차-요약](code-review/00-리뷰-2026-10-21-3차-요약.md)으로 옮겼다(내용 그대로) |
| v0.21 | 2026-11-03 | AI 코치 화면 독립 리뷰(-233, 09e568dc) 추가: 판정 조건부(0/0/10, 변경 통제 사후), 조각 [16-ai-코치-화면-리뷰](code-review/16-ai-코치-화면-리뷰.md) 신설 |
| v0.19 | 2026-11-03 | 18차 확인 리뷰(4017839f..793eb85e, R17 W50) 추가: 판정 합격(0/0/21), 조각 [15-18차-v022-확인](code-review/15-18차-v022-확인.md) 신설 |
| v0.18 | 2026-11-03 | 17차 확인 리뷰(f4dd2a68..4017839f) 추가: 판정 합격(0/0/19), 조각 [14-17차-v022-확인](code-review/14-17차-v022-확인.md) 신설 |
| v0.12 | 2026-10-31 | 11차 해소 기록 2(57e11eec): 조각 [08-11차-v017-v019](code-review/08-11차-v017-v019.md)에 11.1절 추가(AN10-5·AN11-7·AN11-8·AN11-9·BE11-3 해소, 남은 경 27). 판정 합격 유지 |
| v0.11 | 2026-10-31 | 11차 해소 기록(758de88f): 조각 [08-11차-v017-v019](code-review/08-11차-v017-v019.md)에 11절 해소 기록 추가(AN11-4·5·6 해소, AN11-9 일부 해소, BE11-3 남음, 남은 경 32). 판정 합격 유지 |
| v0.10 | 2026-10-31 | 11차 리뷰(cfbd103c..242612e9, 범위 A 0ca948f8·범위 B 242612e9) 추가: 판정 합격(0/0/35), 조각 [08-11차-v017-v019](code-review/08-11차-v017-v019.md) 신설. 목차 파일 100KB 상한 때문에 2차 리뷰 요약 절을 [00-리뷰-2026-10-21-2차-요약](code-review/00-리뷰-2026-10-21-2차-요약.md)으로 옮겼다(내용 그대로). 이 변경 이력 표를 새로 두었다 |
| v0.9 이전 | 2026-10-20~10-31 | 1~10차 리뷰. 판마다 바뀐 것은 각 `## 리뷰` 절과 둘째 줄 헤더의 입력 목록에 있다 |

## 리뷰 기준

세 실행 단위가 같은 기준을 쓴다. 단위마다 다르게 적용한 점은 "단위별 적용" 열과 각 조각 파일 머리의 리뷰 기준 표에 있다.

| 항목 | 기준 | 출처 | 단위별 적용 |
|------|------|------|-------------|
| 매개변수 수 | 4개 이하 (5개 이상은 중) | 기본 규칙 | 백엔드: record 표준 생성자는 세지 않는다. 안드로이드: Hilt 생성자 주입은 클래스 Fan-Out 7로 본다. ai: self 제외 |
| 중첩 깊이 | 3단계 이하 | 기본 규칙 | 같음 |
| 함수 길이 | 50줄 이하 | 기본 규칙 | 같음 |
| 순환 복잡도 | 10 이하 (조건문 + 반복문 + 1 약산) | 기본 규칙 | ai는 ruff mccabe 값. 함수 호출은 제어 구조로 세지 않는다 |
| 결합도 상한 / 응집도 하한 | 스탬프 / 통신적, Fan-Out 7 이하, 순환 0 | design-review.md 0.1 | 백엔드는 의존 방향을 12.2(D1~D74)·13.1 ArchUnit 규칙 10개와도 대조 |

코딩 규약 문서: `GitHub/자율/자율프로젝트_코드컨벤션.md` v0.1(제안). 백엔드는 1·2장, 안드로이드는 1·3장, ai는 1장(Python 절이 없어 나머지는 기본 규칙과 `ai/pyproject.toml`의 ruff·mypy strict·import-linter 설정). 규약이 설계서와 충돌하는 곳(버전, 패키지 배치)은 설계서를 따르고 규약 갱신 요청으로 따로 적었다. 포맷은 Spotless·ktlint·ruff format이 정하므로 지적하지 않았다.
형상 관리 도구·브랜치 전략: Git(GitLab), `main`·`develop` 보호, `feature/{Jira 키}-{작업명}` → MR, 커밋 제목 `{타입}: {내용} [{Jira 키}]`(저장소 `AGENTS.md`).

## 언어 평가 (최초 1회, 2026-10-20)

9개 기준(친숙성, 사용 능력, 처리 효율성, 과거 개발 실적, 문제 유형, 자료 구조, 이식성, 개발자 경험, 컴파일러·도구)별 판정과 근거는 조각 파일의 "언어 평가" 절에 있다. "4세대 언어인가"는 기준으로 쓰지 않았다.

| 실행 단위 | 언어 | 적합 / 보통 / 부적합 | 결론 |
|-----------|------|----------------------|------|
| 백엔드 | Java 21, Spring Boot 3.5.16 | 7 / 2 / 0 | 재검토 불필요 |
| 안드로이드 | Kotlin 2.4 (JVM 17), Compose, Hilt | 7 / 2 / 0 | 재검토 불필요 |
| ai | Python 3.12, FastAPI 0.150 | 6 / 3 / 0 | 재검토 불필요 |

---

## 리뷰 2026-10-20

1차 리뷰(MR !8, 기초 시스템 9f2af7e) 요약 절(대상, 목차, 1~5 점검 요약, 형상 관리, 집계와 판정 불합격 0/16/54, sw-test 전달, 설계서 갱신 요청 CR-U01~CR-U41, 작성 점검, 다음 행동)은 [00-리뷰-2026-10-20-1차-요약](code-review/00-리뷰-2026-10-20-1차-요약.md)으로 옮겼다(2026-10-27, 목차 파일 100KB 상한). 본문은 그대로 [01-backend](code-review/01-backend.md)·[02-android](code-review/02-android.md)·[03-ai](code-review/03-ai.md)에 있다.

---

## 리뷰 2026-10-21 (2차)

2차 리뷰(1차 결함 해소 확인, 백엔드 f20a3f2·안드로이드 cff0c6e·ai f20a3f2) 요약 절(대상, 목차, 1~5 점검 요약, 형상 관리, 집계와 판정 불합격 0/13/64, 1차 대비 추세, 결함 조치 상태, sw-test 전달, 설계서 갱신 요청 CR2-U1~U8·CR2-A1~A7·CR2-I1~I3, 작성 점검, 다음 행동)은 [00-리뷰-2026-10-21-2차-요약](code-review/00-리뷰-2026-10-21-2차-요약.md)으로 옮겼다(2026-10-31, 목차 파일 100KB 상한). 본문은 그대로 [01-backend](code-review/01-backend.md#리뷰-2026-10-21-2차)·[02-android](code-review/02-android.md#리뷰-2026-10-21-2차)·[03-ai](code-review/03-ai.md#리뷰-2026-10-21-2차)에 있다.

## 리뷰 2026-10-21 (3차)

3차 리뷰(2차 수정 병합 통합 0009d29) 요약 절(대상, 목차, 1~5 점검 요약, 형상 관리, 집계와 판정 조건부 0/7/33, 그 뒤 절)은 [00-리뷰-2026-10-21-3차-요약](code-review/00-리뷰-2026-10-21-3차-요약.md)으로 옮겼다(2026-11-04, 목차 파일 100KB 상한). 본문은 그대로 [01-backend](code-review/01b-backend-2.1.md#리뷰-2026-10-21-3차)·[02-android](code-review/02b-android-1.md#리뷰-2026-10-21-3차)·[03-ai](code-review/03-ai.md#리뷰-2026-10-21-3차)에 있다.

## 리뷰 2026-10-21 (4차)

4차 리뷰(통합 d562d26) 요약 절(대상, 검증, 목차, 1~5 점검 요약, 형상 관리, 집계와 판정, 추세, 결정 대기, 결함 조치 상태, sw-test 전달, 설계서 갱신 요청, 작성 점검, 다음 행동)은 [00-리뷰-2026-10-21-4차-요약](code-review/00-리뷰-2026-10-21-4차-요약.md)으로 옮겼다(v0.26, 목차 100KB 상한, 내용 그대로).

## 리뷰 2026-10-23 (5차)

v0.10 변경(컨설턴트 피드백 CR-01~CR-10, 설계 v0.11)의 첫 리뷰다. 대상은 `feature/runkk-v010` bfe55e1c(기준 develop d7618cb, 커밋 100개·병합 14개, 1,137파일 +55,380 −9,605). MR !14(Draft)의 push 커밋 4ee91aef는 여기에 develop의 /legal/ 정적 페이지 병합만 더했다. 변경이 커서 4묶음으로 나눠 병렬로 리뷰했다(sonnet, 읽기 전용). 빌드·시험은 다시 돌리지 않고 push 직전 통합 전체 검증(백엔드 build·안드로이드 전체·ai 214·compose)을 근거로 삼았다.

| 파일 | 묶음 | 판정 | 치명 / 중 / 경 | 4차 잔여 | 설계서 갱신 요청 |
|------|------|------|----------------|----------|------------------|
| [01-backend](code-review/01-backend.md) (가) | member·running·battle·avatar·reward·realtime·web 일부·batch·infra·V19~V26 | 합격 | 0 / 1 / 17 | 결정 대기 경 유지 | U5-A-1~10 |
| [01-backend](code-review/01-backend.md) (나) | crew·crew.chat·report·report.target·web.crew·web.chat·web.report·web.admin.report | 조건부 | 0 / 6 / 16 | L3·L4·L13·L31 해결, L32 재발 | U5-1~12 |
| [02-android](code-review/02-android.md) (다) | domain·data·contract·wear·wear-contract | 합격 | 0 / 2 / 16 | N-14·N3-7 결정 대기 | U5-C1~10 |
| [02-android](code-review/02-android.md) (라) | feature/*·core·app·build-logic | 합격 | 0 / 1 / 16 | N3-7 유형 재발(결정 대기) | U5-D-1~8 |
| [03-ai](code-review/03-ai.md) | ai·infra | 합격 | 0 / 0 / 0 | — | — |
| **종합** | | **조건부** | **0 / 10 / 65** | | 40건 |

### 중 10건 (머지 전 수정 권고)

| ID | 위치 | 내용 | 교정 |
|----|------|------|------|
| BE5-B-1 | crew/chat/CrewChatService.java:151, CrewChatMessage.java:109 | 크루를 떠났거나 강퇴된 작성자가 자기 채팅 메시지를 지울 수 있다(게시글은 막음). 05-03 권한 "크루 구성원"과 불일치 | delete에서 `requireMember`, 시험 추가 |
| AN5-C-1 | data/live/FallbackLiveChannelProvider.kt:52-55, 79-94 | 연결 고르기와 임대 사이에 마지막 임대가 반납돼 연결이 닫히면 닫힌 연결의 임대가 계속 Failure(Network) | 확인과 참조 수 증가를 한 번에 하는 `tryLease()` |
| AN5-C-2 | api/crew/PhotoCompressor.kt:63-84 | 중첩 4단계·반복 안 return. 긴 변 64px 미만 그림을 한 번도 인코딩하지 않고 TOO_LARGE로 거부 | 함수 분리 + do-while, 작은 그림 시험 |
| AN5-D-1 | me/ProfileEditViewModel.kt:52, crew/CrewHomeViewModel.kt:255, CrewPollWriteUiState.kt:26-27, CrewRunWriteViewModel.kt:48 | D-143 코드 포인트가 닉네임·응원글·투표·장소명에 미적용(서버는 코드 포인트) | 공용 `CrewText` 코드 포인트 함수로 통일 |
| BE5-B-6 | crew/JpegMetadataStripper.java:43-88 | 출구 7개(반복 안 return·continue·throw), V(G) 약 16 | 세그먼트 읽기·판정 함수로 분리 |
| BE5-A-1 | member/Member.java:112 | private 생성자 매개변수 5개(3차 B-M1 유형) | accountType을 account 유무로 정함 |
| BE5-B-2 | crew/PostSummary.java:41 | `of` 매개변수 6개 | 종류별 정보 record로 묶음 |
| BE5-B-3 | crew/WeeklyRace.java:51 | `restore` 5개 | 목표 record |
| BE5-B-4 | infra/crew/JpaCrewPostRepository.java:261 | `deleteMemberRows` 5개 | 대상 열거형 + 조회 조건 record |
| BE5-B-5 | SchedulePost·CrewChatMessage·ContentReport 생성자, RelayEntry 보조 생성자 | 5개 4곳 | 입력 Draft record |

### 경 중 먼저 볼 것

- BE5-A-4: 해금 행이 없는 회원의 첫 랜드마크 알림이 조회 채우기(`backfillReached`)에 삼켜질 수 있다(시험 TC-6763이 알림 0건도 통과).
- BE5-A-5: 이미 연동한 회원의 같은 계정 재연동이 ALREADY_LINKED가 아니라 ALREADY_MEMBER(설계 06-01 순서 그대로 — 설계 보완).
- BE5-A-15: V20 `DROP COLUMN` 배포 전 백업 단계가 deploy.sh·implementation.md 10절에 없다.
- AN5-C-4·5·7·8: 핸드셰이크 401 뒤 재연결 계속, 채팅방이 1000으로 닫히면 멈춤, RunTracker tick 락 밖 판단(자동 종료·재개 겹침), SessionRefresher 로그아웃 되살림 경쟁.
- BE5-A-2: ChatPresence 입장 신호마다 DB 읽기, 횟수 제한 없음. BE5-B 리액션 속도 제한 없음.
- 형상: `merge:` 커밋 타입(AGENTS.md 목록 밖), 한 커밋 여러 일(L32·N3-7 유형 재발).

### sw-test 전달 (V(G) > 10, 우선 테스트 대상)

| 함수 | V(G) | 묶음 |
|------|------|------|
| crew.JpegMetadataStripper.strip | 16 | 나 |
| crew.PollDraft 생성자 | 16 | 나 |
| running.RunUpload.requireValidPauses | 11 | 가 |
| data.live.ServerMessageDecoder.decode | 22 | 다 |
| data.settings.DataStoreSettingsRepository.toSnapshot | 16 | 다 |
| api.account.AccountMappers.withSettingChanged | 16 | 다 |
| api.account.MemberSettingsResponse.toDomain | 12 | 다 |
| wear WatchViewModel.reduce | 12 | 다 |
| ui.crew CrewHomeRoute | 22 | 라 |
| ui.notification inboxDestination | 17 | 라 |
| ui.battle verdictMessage | 16 | 라 |
| ui.run RunViewModel.onProgress | 12 | 라 |
| onCoordinator, homeTabItems, ChatBubble | 11 | 라 |

### 설계서 갱신 요청 (`/sw-design-oo` 재호출 목록)

코드가 옳거나 설계가 비어 있는 것 40건이다: 백엔드 (가) U5-A-1~10, (나) U5-1~12, 안드로이드 (다) U5-C1~10, (라) U5-D-1~8(조각 파일 각 절). 대표: 총괄 결정 7-3 tier_applied·TierApplier, saveCollapsing·부분 유일 색인, CrewPostService 의존 11, D-142 허용 목록, 사진 캐시 5분, 글 행 비관적 잠금, 크루 밖 오류 코드 403/409 통일(결정 필요), 채팅 삭제 권한 문장, D-143 적용 항목 목록, ChatRoom.failures·RunTracker.takeUnclaimedEnd, RunkkReportDialog 등 core.ui 공용 요소. 그 밖에 구현 작업자들이 보고한 갱신 요청은 implementation.md 3장에 있다(4·9장 정리 필요 — 리뷰 지적).

### 다음 행동

- [ ] 중 10건 수정(기능 4건 우선: BE5-B-1, AN5-C-1, AN5-C-2, AN5-D-1) → 6차 확인 리뷰
- [ ] 경 우선 항목(BE5-A-4·5·15, AN5-C-4·5·7·8) 수정
- [ ] implementation.md 4장(결정 목록)·9장(설계서 갱신 요청)을 단계 기록에서 모아 채운다
- [ ] `/sw-design-oo`: 갱신 요청 40건 반영, 크루 밖 오류 코드 결정
- [ ] `/sw-test`: v0.6 케이스를 구현 코드로 실행하고 위 V(G) 상위 함수의 기초 경로 시험 보강
- [ ] MR !14 본문 붙여넣기, 실기기(워치) 확인, E2E 재실행 뒤 Draft 해제

## 리뷰 2026-10-23 (6차)

5차 결함 수정(서버 r6be 24커밋, 앱 r6an 28커밋)의 확인 리뷰다. 대상 diff `4ee91aef..a27c914f`(compose AVATAR_BACKFILL_CUTOFF, develop 위키 프록시 nginx 병합 포함). 서버·안드로이드 2묶음 병렬(sonnet, 읽기 전용). 안드로이드 묶음은 경쟁 시험 2개를 실제로 돌려 시간을 쟀다(RunTrackerRaceTest 9.2초, LiveChannelLeaseTest 2.3초).

| 파일 | 묶음 | 5차 추적 | 새 결함 (치명/중/경) | 판정 |
|------|------|----------|------------------|------|
| [01-backend](code-review/01-backend.md) | 서버·infra | 40건: 해결 23, 일부 4, 보류 2, 미해결 11. **중 7/7 해결** | 0 / 1 / 8 | 합격 |
| [02-android](code-review/02-android.md) | 앱·워치 | 35건: 해결 25, 일부 4, 미해결 6. **중 3/3 해결** | 0 / 0 / 7 | 합격 |
| **종합** | | **중 10/10 해결** | **0 / 1 / 15** (이월 경 포함 36) | **합격** |

### 남은 중 1건

| ID | 위치 | 내용 | 교정 |
|----|------|------|------|
| BE6-1 | infra/deploy.sh `backup_database` | 배포 전 덤프를 CI 작업 폴더 `infra/backups/`에 쓴다. 러너 기본 `git clean -ffdx`면 다음 잡이 지워 "최근 7개 유지"가 성립하지 않고 V20 이전 덤프가 사라진다 | 작업 폴더 밖 영속 경로(`RUNKK_BACKUP_DIR`, 기본 호스트 경로)로, 파일 권한 600 |

### 경 중 먼저 볼 것

- BE6-2·3: 덤프 파일 권한 644, postgres가 정지 상태면 데이터 볼륨이 있어도 백업을 건너뜀.
- AN6-2: 토큰 세대 확인이 `clear()`만 잡아 갱신 대기 중 `save()`로 바뀐 토큰을 덮어쓸 수 있다(이전 코드보다 퇴보).
- AN6-3: 갱신은 성공하는데 재연결 핸드셰이크가 계속 401이면 5초마다 무한 반복.
- BE6-4·5: ChatPresenceRegistry가 거부 결과를 세션 동안 기억해 재가입한 방 신호가 같은 연결에서 누락, 10개 초과 신호 INFO 로그 폭주.
- BE6-6: 배경 backfill 기준 시각 기본값이 과거 고정값으로 세 곳에 복제(배포 때 실제 V25 적용 시각으로 설정 필요 — implementation.md 10장).

### 다음 행동

- [x] BE6-1~3, AN6-2·3 수정(배포 전 필수) → push. r7(5커밋, 결함마다 1개): 덤프 폴더 `RUNKK_BACKUP_DIR`(작업 폴더 밖 절대 경로, 기본 러너 사용자 `$HOME/runkk-backups`, 폴더 700·파일 600, 파일 이름에 커밋 sha), postgres가 멈춰 있어도 데이터 볼륨이 있으면 올려서 덤프, `save()`도 세대를 올림, 갱신 뒤 핸드셰이크 401이 3번 이어지면 채널을 닫고 Unauthorized. 시험 7건 추가(수정 전 실패 확인), 백업은 Docker 컨테이너에서 10가지 경우 재현. 통합 e0b360fb(develop APK ABI 분할 병합 포함)
- [ ] 이월 경(백엔드 17, 안드로이드 12) 정리는 다음 반복
- [ ] 설계서 갱신 요청 U6-1~6(백엔드), U6-A1~A4(안드로이드) → `/sw-design-oo`
- [ ] `/sw-test`: v0.6 케이스를 구현 코드로 실행
- [ ] MR !14 본문 붙여넣기, 실기기(워치)·E2E 확인 뒤 Draft 해제

## 리뷰 2026-10-27 (7차)

v0.10 후속 변경(CR-11 초과 시속 구간 제외, FR-043 좁은 예외인 일회성 재판정, CR-14 자유런 취소 FR-219, CR-15 대결 결과 다시 읽기)과 화면 결함 수정 11커밋의 리뷰다. 대상 `447a3f7c..7117785f`의 병합 제외 커밋 11개, 76파일 +2,680 −170(스크린샷 기준 이미지 128장 제외). 한 사람이 읽기 전용으로 읽었고 빌드·시험은 돌리지 않았다. 01·02 조각이 60KB 상한을 넘어 7차는 새 조각에 적었고, 이 목차 파일이 100KB에 닿아 1차 요약 절을 조각으로 옮겼다.

| 파일 | 범위 | 새 결함 (치명/중/경) | 판정 | 설계서 갱신 요청 |
|------|------|----------------------|------|------------------|
| [04-7차-v010-후속](code-review/04-7차-v010-후속.md) | 백엔드 running·batch.running·infra.running·reward·avatar·analysis, V27·V28 / 안드로이드 domain.run·ui.run·ui.record·ui.home·ui.battle·app·core.ui | 0 / 2 / 6 | 합격 | U7-1~U7-6 |

### 중 2건

| ID | 위치 | 내용 | 교정 |
|----|------|------|------|
| BE7-1 | batch/running/RunRejudgeJob.java:33-36 | ApplicationReadyEvent에서 PENDING 전부를 동기로 처리해 준비 상태(ACCEPTING_TRAFFIC)가 그만큼 늦다. compose 상태 확인이 `/actuator/health/readiness`, deploy.sh가 150초만 기다리므로 무효 기록이 많으면 배포가 실패한다. 같은 사건의 진행 중 대결 시계 복구(BattleStartupListener)와 순서도 정해지지 않았다 | `@Async`로 기동 스레드 밖에서, 한 번 처리 상한을 두고 남은 것은 다음 기동으로. 또는 배포 전에 운영 DB 무효 기록 수 확인 |
| AN7-1 | ui/battle/MatchViewModel.kt:210-219, :360 | 성사 뒤 대기 임대를 닫지 않고 `channel = null`만 한다. 조정자는 자기 임대를 새로 열고, 연결은 임대 수가 0일 때만 닫히므로 랜덤 대결 한 번 뒤 실시간 연결이 앱이 끝날 때까지 남는다(기존 결함). 3105b12b의 출발 예약 선수신이 이 열린 임대에 기댄다 | 출발 예약을 넘긴 뒤와 onCleared에서 대기 임대를 닫고 임대 수 시험 |

### 경 중 먼저 볼 것

- BE7-6·AN7-2: 재판정의 순번 두 갈래(늦은 순번·다음 순번)·TierReconcile 겹침·두 인스턴스 동시 실행, 출발 예약의 두 도착 순서와 다른 battleId 무시 시험이 없다.
- BE7-3: RunRecord.stops()는 ValidityRule.STANDARD 고정, judge는 매개변수 규칙이라 둘이 다른 구간을 뺄 수 있다.
- BE7-5: 재판정 주석의 "랭킹은 기록을 읽어 다시 센다"가 틀렸다. 랭킹은 0시 스냅숏이라 다음 날부터 반영된다.
- 확인 결과 문제 없음: 판정 규칙의 이중 차감 방지(min), 제외 구간 경계(straddles 경계 제외), 재판정 트랜잭션(기록마다 REQUIRES_NEW·행 잠금·조건부 갱신, 멱등), V27 기본값 열 추가·V28 표시(PostgreSQL), 취소의 잠금·대결 거부.

### sw-test 전달 (V(G) > 10, 우선 테스트 대상)

| 함수 | V(G) | 커밋 |
|------|------|------|
| ui.record RecordListScreen DayCell | 약 14 | b6f4c2b4 |

10에 가까운 것: RunRejudgeService.rejudgeOne 8, RunRecord.judge 7, RunTrack.overspeedSegments 7, BattleResultViewModel.load 7. 판정 경곗값(시속 25.0, 남은 1km·3분, 남은 값 0)과 재판정 경로는 조각 7절에 있다.

### 설계서 갱신 요청 (`/sw-design-oo` 재호출 목록)

U7-1 FR-047 v0.10 판정 규칙(ExcludedSegments, overspeedSegments·countedLength, isSatisfiedBy 3인자, 서버 평균 페이스 재계산, V27), U7-2 일회성 재판정(RejudgeState, rejudgeInvalid, RunRejudgeService·Job, RunRecordRejudged 수신자, 저장소 4연산, V28, D71 설명), U7-3 TotalStats·대시보드 유효 기록만, U7-4 앱 취소(discard·discardRun·isDiscardable, 상태도 2.4.7)와 OverspeedRule·계약 파일, U7-5 앱 화면 상태(isDiscardConfirming, isExcluded, excludedDays, onResumed, isAwaitingVerdict 3초×20, 출발 예약 선수신), U7-6 화면별 아래 여백·한국어 Phrase 줄바꿈(API 33 이상에서만 효과). 같은 커밋을 반영 중인 design-oo 갱신과 대조한다. 새 의존 화살표는 없다.

### 다음 행동

- [ ] BE7-1: 재판정 비동기·상한, 또는 운영 DB 무효 기록 수 확인 뒤 배포 체크리스트에 기록(운영 배포 전)
- [ ] AN7-1: 대기 임대 닫기와 임대 수 시험
- [ ] 경 BE7-2~6, AN7-2: 다음 반복
- [ ] `/sw-design-oo`: U7-1~U7-6
- [ ] `/sw-test`: DayCell 기초 경로, 판정 경곗값, 재판정·출발 예약 시험

## 리뷰 2026-10-30 (8차)

v0.14 피그마 디자인 반영(srs 7.13.9 CR-24~CR-51, 설계 v0.14 D-218~D-300)의 첫 리뷰다. 대상은 `feature/runkk-figma-v1` c2e75161(MR !53, 기준 origin/develop 7c42b0fe, 42커밋). 서버·infra와 앱을 두 작업자가 같은 시간에 나눠 읽기 전용으로 리뷰했고 빌드·시험은 돌리지 않았다. 서버 범위는 `-- backend infra android/contract` 166파일 +8,097 −376(운영 코드 117파일 +3,959 −213)이고 보조 리뷰(ecc:security-reviewer, ecc:java-reviewer, sonnet)를 돌린 뒤 결함은 모두 코드로 다시 확인했다.

| 파일 | 범위 | 새 결함 (치명/중/경) | 판정 | 설계서 갱신 요청 |
|------|------|----------------------|------|------------------|
| [05-8차-v014-서버](code-review/05-8차-v014-서버.md) | battle(친구 초대·포기 취소·FRIEND)·running(기록 삭제·묘비·경로 모양·평가 표시)·runimal(16유형·비율·동행 축)·member·profile·analysis·reward·web·batch·infra(nginx 정적 파일), V30 | 0 / 3 / 10 | 합격 | U8-1~U8-10 |
| [05-8차-v014-앱](code-review/05-8차-v014-앱.md) | android(앱 작업자) | 0 / 3 / 19 | 합격 | 앱 U8-1~U8-7 |
| **종합** | | 서버 0 / 3 / 10 + 앱 0 / 3 / 19 = 0 / 6 / 29 | **조건부**(중 6, 2차 규칙: 종합 중은 조각의 합) | 서버 10 + 앱 7 |

8차 앱 중 3건은 AN8-1(이력·알림함에서 열 때마다 평가 화면), AN8-2(친구 초대 제목에 이름 빠짐), AN8-3(마이가 편집 저장 뒤 갱신되지 않음)이다. 9차에서 중 6건 중 5건이 해소되어 판정은 합격으로 올랐다.

### 중 3건 (서버)

| ID | 위치 | 내용 | 교정 |
|----|------|------|------|
| BE8-1 | db/migration/V30__figma_v014.sql:1, application.yml flyway(out-of-order 없음) | V29를 MR !49(analytics 스키마, 1311717d에 V29 있음)에 비워 두고 V30을 넣었다. 이 MR이 먼저 적용된 DB에 V29가 오면 Flyway 검증이 실패해 api가 기동하지 못하고 배포가 막힌다 | !49 먼저 병합, 또는 늦게 들어오는 쪽이 V31로 번호를 올린다. 두 MR 본문과 배포 체크리스트에 적는다 |
| BE8-2 | infra/docker-compose.yml:29-72, application.yml:97, infra/README.md:321 | compose가 FRIEND_INVITE_LINK_BASE를 api에 넘기지 않고 application.yml은 운영 주소가 기본값이라 release(8443)도 운영 주소로 초대 링크를 만든다. 설계 D-219·05-08 6절의 "release는 그 환경 주소"를 지킬 방법이 없다 | compose에 `${FRIEND_INVITE_LINK_BASE:?…}`, README 필수 목록, 운영 프로필 기본값 제거 |
| BE8-3 | profile/ProfileDisclosure.java:61-66 | private view 매개변수 5개(boolean 플래그 포함) | 보이는 항목 record로 묶어 3개 |

### 확인한 것 (특히 볼 것)

친구 초대: by-token 조회는 읽기 전용 트랜잭션이고 인증이 필요하다. 토큰은 SecureRandom 128비트, 수락은 `@Version` 낙관적 잠금 + 재시도로 한 명만, 본인 수락은 만료보다 먼저 거부, 만료는 수락 시점 판정 + 5초 정리 작업, 남의 초대 취소는 403. 대기 초대 하나는 부분 고유 인덱스, 친구 승리 리워드는 실시간·0시 10분 보정 두 경로 모두 제외, 셀프·셀럽 진행 중 포기는 취소, 동행 축의 크루런은 CREW_RELEASED가 켜질 때만. 기록 삭제는 무효·재판정 확정 기록만, 묘비로 재업로드 무시, 삭제 사건 수신자 셋(battle·crew·runimal). 응답은 더하기만이고 v0.9 앱 해독기(ignoreUnknownKeys, enumByName)는 FRIEND·FULL·새 code에서 실패하지 않는다. 구조 시험 65 패키지·W1~W5. 남은 경은 정리 작업과 새 초대의 좁은 경합(BE8-4), 고유 제약 해석 범위(BE8-5), nginx 접근 로그의 토큰(BE8-6), 시도 제한 없음(BE8-7), 삭제 수신자 실패 보정 없음(BE8-8), 우선 케이스 시험 공백(BE8-12) 등이다.

### 다음 행동

- [ ] BE8-1·BE8-2: 병합·배포 전 필수. assetlinks.json 실제 지문도 배포 전
- [ ] BE8-3, 경 BE8-4~BE8-6 우선, 나머지는 다음 반복
- [x] 앱 조각의 판정과 합쳐 종합 판정을 이 표에 적는다(조건부, 9차에서 합격으로 상승)
- [ ] `/sw-design-oo`: U8-1~U8-10(서버) + 앱 조각 갱신 요청
- [ ] `/sw-test`: 서버 조각 7절 경로와 BE8-12의 빈 우선 케이스(TC-9352, TC-9357, TC-9359·TC-9825, TC-9805, TC-9814, TC-9828)

## 리뷰 2026-10-30 (9차)

8차 결함 수정(서버 r8be 6커밋, 앱 r8an 5커밋)의 확인 리뷰다. 대상 `c2e75161..5885a608`(99파일 +1,452 −212). 한 사람이 읽기 전용으로 읽었고 빌드·시험은 돌리지 않았다. 통합 검증(backend build, android ktlintCheck lintLocalDebug testLocalDebugUnitTest, 앱 1,763/0, 서버 1,637/0)은 호출 측 보고를 따랐다.

| 파일 | 범위 | 8차 추적 | 새 결함 (치명/중/경) | 판정 | 설계서 갱신 요청 |
|------|------|----------|----------------------|------|------------------|
| [06-9차-v014-확인](code-review/06-9차-v014-확인.md) | 서버·infra(BE8-1~13) + 앱(AN8-1~22) 수정 확인 | 35건: 해소 26, 보류 9, 미해소 0. **중 6건 중 5건 해소, BE8-1 보류** | 0 / 0 / 2 | **합격** | 8차 17건 + U9-1~U9-4 (21건) |

### 판정 근거와 조건

치명 0, 미해소 중 1(BE8-1, 보류), 새 결함 경 2건(BE9-1 FriendInviteLinks Javadoc, BE9-2 nginx error_log의 요청 줄). 이월 경 8건(BE8-7, BE8-8, BE8-10, BE8-13, AN8-14, AN8-18, AN8-20 잔여, AN8-21)을 더하면 경 10건이다. 8차 종합(조건부, 0/6/29)에서 한 단계 올랐다.

- 탈퇴한 초대자 토큰의 INVITE_NOT_FOUND(r8be c3ad7bf7): 결함이 아니다. 앱이 INVITE_NOT_FOUND·INVITE_EXPIRED·INVITE_CANCELLED를 같은 문구로 보이고, 회원 ID를 담은 초대 행을 파기하는 쪽이 서버 U8-8의 미결정을 닫는다. 설계 D-223 보강으로 U9-1에 적었다.
- BE8-2 필수 변수: 코드 결함이 아니라 병합·배포 전 조건이다. CI 변수 `INFRA_ENV`·`INFRA_ENV_PROD`에 FRIEND_INVITE_LINK_BASE가 없으면 compose 해석 단계에서 실패해(deploy.sh 133행, 백업·마이그레이션 앞) 배포가 막히고 실행 중인 스택은 건드리지 않는다. develop은 병합 즉시 자동 배포이므로 변수 등록을 먼저 한다.
- 조건 셋: CI 변수 등록, MR !49 먼저 병합(또는 MR 본문에 V29·V30 순서 규칙), assetlinks.json 실제 지문.

### 다음 행동

- [ ] 병합 전: CI 변수 등록, MR !53 본문에 V29·V30 순서와 이 결과서 경로 기록
- [ ] 경 BE9-1·BE9-2 정리(한 줄씩), 이월 경은 다음 반복
- [ ] `/sw-design-oo`: 서버 U8-1~U8-10 + 앱 U8-1~U8-7 + U9-1~U9-4 (결정 필요: 서버 U8-4, 서버 U8-6)
- [ ] `/sw-test`: 조각 7절 경로, TC 번호 미고정 13건, e2e 잠금 흐름(실기기)

## 리뷰 2026-10-31 (10차)

v0.15 재대조와 v0.16 화면 확인 후속 반복의 리뷰다. 대상 `feature/runkk-figma-v1` cfbd103c(MR !53). 기준은 9차 대상 5885a608이다(2026-10-31 사용자 결정으로 implementation.md 12.9의 e72897c7 기준을 넓혔다). 그래서 리뷰 기록이 없던 v0.15 재대조 38커밋(5885a608..e72897c7, develop의 V29 병합, D66·D67·D80, 그림·아바타·재대조 r2a~r2d)과 이번 회차 두 커밋(e5475f3c 서버, cfbd103c 앱)을 함께 봤다. 범위 전체는 460파일 +6,234 −2,048이다(그림·스크린샷 포함). 서버·infra와 앱을 두 작업자가 같은 시간에 나눠 읽기 전용으로 리뷰했고 빌드·시험은 돌리지 않았다. 통합 검증은 implementation.md 12.3을 인용했다(backend build 1,661/0, android 게이트 1,819/0). 중 이상 결함은 호출 측이 코드로 다시 확인했다.

| 파일 | 범위 | 이월 추적 | 새 결함 (치명/중/경) | 판정 | 설계서 갱신 요청 |
|------|------|-----------|----------------------|------|------------------|
| [07-10차-v016-서버](code-review/07-10차-v016-서버.md) | battle(취소 API·그만두기 뜻 변경·상대 성별 V31·셀럽 figure_key V32·기록 10건), member.publicGendersOf, analysis.streakWeeks, web.battle·web.admin, D67 결과 기록 ID, nginx(D80 문서 차단, assetlinks), Flyway V29~V32, 계약 파일 | 7건: 해소 2(BE8-1, BE8-8), 보류 3, 미해소 2(BE9-1, BE9-2) | 0 / 1 / 8 | 합격 | U10-1~U10-4 |
| [07-10차-v016-앱](code-review/07-10차-v016-앱.md) | android(폰). 워치 운영 코드 변경 없음 | 4건: 미해소 4(AN8-14, AN8-18, AN8-20, AN8-21 증가) | 0 / 2 / 7 | 합격 | U10-A1~U10-A3 |
| **종합** | | 이월 경 9 | 0 / 3 / 15 + 이월 경 9 = **0 / 3 / 24** | **합격**(중 3, 상한) | 서버 4 + 앱 3 = 7 |

알려진 결함은 세지 않았다: implementation.md 12.7의 K16-1(D129 표시 성별 미구현), K16-2(지도 오류 때 격자 대체 없음), K16-3(관리 API 정수 초), K16-4(가림 이름 형식), K16-5(IconDuplicates, 두 그림 쌍 MD5 같음), K16-6(ArchitectureTest 주석)은 모두 코드로 그대로임을 확인했다. 앱 Fan-Out 상한 초과(ui.battle 8, app 9의 E32 AppStartup → domain.run·domain.record)는 design-review 19장 R51·R52가 이미 불허로 판정했고 import가 같음만 확인했다(V16-1과 같은 취급). 서버는 새 화살표가 없다.

### 중 3건

| ID | 위치 | 내용 | 교정 |
|----|------|------|------|
| BE10-1 | battle/BattleStartService.java:112-116, GhostMatcher.java:79-86, OpponentGenders.java:15-21, member/MemberQueryService.java:42-53 | 성별 비공개·미입력 회원 상대의 그림 성별을 매칭마다 무작위로 정한다. 2026-10-31 사용자 결정(D-327 제안 채택, V16-2 닫음)과 CR-79는 회원 ID로 고정된 값을 쓰게 했다. 같은 회원을 두 번 만나면 그림이 바뀌어 비공개임이 드러난다 | K16-1과 함께 member에 `shownGendersOf`·`ShownGender.fallbackOf(memberId)`를 만들고 BattleStartService·GhostMatcher가 쓰게 한다. `OpponentGenders.of`의 무작위 갈래를 지우고 무작위는 가상 기록에만 남긴다. 매칭 때 한 번 정해 V31 칼럼에 남기는 것은 유지(R55의 "읽을 때 붙임"은 랭킹 스냅숏에만) |
| AN10-1 | feature/battle BattleRunningScreen.kt:138-140, BattleRunningViewModel.kt:118-123·143-146, domain BattleRunCoordinator.kt:187-190 | 셀프·셀럽런 카운트다운에서 뒤로가기 → `onForfeitLongPressed`(COUNTDOWN 허용) → "패배로 기록" 확인 → `forfeit()`. 서버는 출발 전이라 승패 없이 취소하지만 앱은 Finished로 닫혀 측정을 본 적이 없으므로 FAILED("위치 권한과 연결을 확인해 주세요")로 끝난다. D-324는 출발 전 카운트다운을 cancel → Discarded로 정했다. 5885a608에서는 기록을 버리고 기록런 탭으로 돌아갔다(회귀). 셀럽런 카운트다운에는 "다시 고르기"가 없어 뒤로가기가 유일한 나가기다 | 기록 상대 대결이 출발 전이면 `isConfirmingCancel`을 세우고 확인 때 `coordinator.cancel()`. 셀럽런 카운트다운 뒤로가기 → Discarded·cancel 1회 시험 |
| AN10-2 | feature/battle ActiveBattleViewModel.kt:40-53 | 앱 재실행 때 남은 대결을 정리하는 오버레이가 종류와 관계없이 `forfeit`을 보낸다. 셀프·셀럽 대결은 이제 패배가 되고(D-318), cancel 보고가 세 번 실패해 기기에서 기록을 버린 경우 사용자가 고른 "기록 없이 취소"가 전적의 패배로 바뀐다 | type이 SELF·CELEBRITY면 `battles.cancel`, 나머지는 forfeit. 확인 문구도 종류별로 |

### 경 중 먼저 볼 것

- BE10-3: nginx `app-locations.conf:27` 정규식이 `/v3/api-docs.yaml`을 막지 못한다(develop 8443에서 토큰이 있으면 API 문서를 받는다). 다음 에지 배포 전에 접두사로 막고 스모크에 yaml 경로를 더한다.
- BE10-7: `infra/README.md:314` prod 필수 키 목록에 RUNKK_AI_DB_PASSWORD가 없다(V29 이후 compose가 이 값 없이 멈춘다). 배포 전 CI 변수 등록.
- BE10-2: streakWeeks는 잔디 12주 범위만 보므로 이번 주 기록 전이면 최대 11이다(D-319의 "최대 12"와 다름).
- AN10-5: AccountRepository.myGender를 로그아웃·탈퇴 때 비우지 않아 다음 계정이 앞 계정의 캐릭터로 보일 수 있다. AN10-6: 스플래시 `isIntroDone`이 `remember`라 구성 변경 때 다시 돈다. AN10-4: `cancel()`에 카드 종류 가드가 없다.
- BE10-8·BE10-9(형상): fabf933f가 앱 drawable 19개를 함께 지워 fabf933f·01ee313b·012310ce에서 앱이 빌드되지 않고, e5475f3c 한 커밋에 결정 넷이 D·CR 번호 없이 섞였다(BE8-13과 같은 유형).
- AN10-9·AN8-21: 50줄 초과 함수 12 → 25, 매개변수 5개 이상 7 → 21로 늘었다. BattleModeScreen·KakaoRunMap·BattleRoute부터 나눈다.

### sw-test 전달 (V(G) > 10, 우선 테스트 대상)

| 함수 | V(G) 약산 | 볼 것 |
|------|-----------|-------|
| battle.CelebrityRecord.register | 약 13 | figureKey null·빈·공백·20자·21자, 실명 동의 반쪽과 함께 올 때 사유 순서 |
| core.designsystem defaultAvatarOf | 약 16 | 성별 2 × 장면 7 표, K16-5 같은 그림 쌍 |
| core.designsystem RunkkBottomBar | 약 12~16 | 탭 수·선택·움직임 줄이기·전환 중 |
| core.designsystem KakaoRunMap | 약 13 | 지도 준비 전·후, 경로 0·1·2점, 오류 때 격자(K16-2) |
| ui.record ShareStep, RecordCardScreen | 약 10~14 | CARD_BACK 열림·닫힘, 캡처 null |

회귀·신규 경로: AN10-1(카운트다운 뒤로가기 → cancel·Discarded), AN10-2(남은 대결 종류별 cancel·forfeit), 취소된 대결 ID로 늦게 온 기록(저장되고 판정·이력·리워드에 없음, Runimal 동행 축 포함 여부), cancel × forfeit × applyRecord 동시 실행(PostgreSQL, 409 BATTLE_NOT_FOUND·INVALID_STATE만), cancel 오류 표의 빈 곳(RESULT_PENDING, 시작 보고 전), streakWeeks 경계(12주 이상 연속의 월요일 기록 전·후, 13주), BE10-1 수정 뒤 같은 비공개 회원의 성별 고정, RandomBattleGate·OpponentMarkerPlacer·달리는 아바타 경곗값, 잠금·열림 두 범위의 알림 숨김. robolectric.properties 교체(G16-4)로 가려진 시험은 없지만 RunkkApp.onCreate를 지나는 시험이 하나도 남지 않았다. 자세한 표는 두 조각의 7절에 있다.

### 설계서 갱신 요청 (`/sw-design-oo` 재호출 목록)

서버 U10-1(D-313·2.1·D-327·SQ-38을 D-327 채택으로, V16-2 닫음), U10-2(D-319 상한 문장), U10-3(D-312 (1) 문서 차단 경로를 접두사로), U10-4(배포 필수 값에 RUNKK_AI_DB_PASSWORD). 앱 U10-A1(D-324에 출발 전 나가기·남은 대결 정리의 종류별 cancel·forfeit 규칙), U10-A2(잠금 빌드에서도 아바타·크루 시스템 푸시가 뜨는데 끌 스위치는 숨겨짐, 사용자 확인 필요), U10-A3(새 이름 myGender·randomRunsLeft·isConfirmingCancel 등). implementation.md의 V16-1·V16-3은 중복 요청하지 않았다.

### 다음 행동

- [ ] 1차 배포 전: AN10-1, AN10-2, K16-2(지도 오류 격자). BE10-1은 K16-1과 함께 다음 반복
- [ ] 배포 전 조건: CI 변수 RUNKK_AI_DB_PASSWORD(BE10-7)·FRIEND_INVITE_LINK_BASE(9차), 운영 celebrity_record 중복 확인(V32), assetlinks 지문, BE10-3 nginx
- [ ] 경 AN10-4~AN10-6, BE10-2 먼저, 나머지와 이월 경은 다음 반복
- [ ] `/sw-design-oo`: U10-1~U10-4, U10-A1~U10-A3. 사용자 확인: U10-A2
- [ ] `/sw-design` review: R51·R52 권고(MySummary 투영, DeviceCopySync) 반영 뒤 2차 재검증
- [ ] `/sw-test`: 위 표와 두 조각 7절, V16-3(v0.16 확인 대상에 TC 번호)

## 리뷰 2026-10-31 (11차)

10차 결함 수정·v0.18 결정(범위 A: cfbd103c..0ca948f8, 커밋 7개, 98파일 +1,465 −322)과 v0.19 소리·첫 화면·오늘 계획 페이스(범위 B: 242612e9, 28파일 +765 −115)의 리뷰다. 대상 `feature/runkk-figma-v1` 242612e9(MR !53). 범위 A는 보조 작업자가, 범위 B는 리뷰 담당이 읽기 전용으로 읽었고 빌드·시험은 돌리지 않았다. 통합 검증은 design-oo 05-14 D-357 (4)를 인용했다(안드로이드 1,465/0, 서버 1,679/0).

| 파일 | 범위 | 10차 추적 | 새 결함 (치명/중/경) | 판정 | 설계서 갱신 요청 |
|------|------|-----------|----------------------|------|------------------|
| [08-11차-v017-v019](code-review/08-11차-v017-v019.md) | 서버·infra(표시 성별, 주 연속, 닫힌 기능 푸시 차단, 계약) + 앱(취소 규칙, 잠금 알림, 지도 격자, 배경 음악·효과음·소리 설정, 첫 화면, 오늘 계획 페이스) | 중 3건 모두 해소, 경 BE10-2·BE10-4 해소, K16-1·K16-2·U10-A2 해소, 나머지 이월 경 22 | 0 / 0 / 13 + 이월 경 22 = **0 / 0 / 35** | **합격** | U11-1~U11-6 |

### 해소 기록 (758de88f)

같은 날 커밋 758de88f(24파일 +197 −29)가 AN11-4(덮인 동안 Overlay를 붙이지 않고 NavHost를 접근성에서 숨김), AN11-5(isAvatarReady 전에는 아바타·얼굴을 비우고 누름·읽기 없음), AN11-6(저장 중 페이스 무시, 실패 시 되돌림, 읽은 오늘 계획 페이스를 따름)을 고쳤다. AN11-9는 앱 시험 8건(코치 페이스 3, 저장 연타 1, RunkkSoundVolume 4)과 크루런 결과 경로 단언으로 일부 해소했고 첫 화면 4초 상한·붙잡기와 LoopingMusic 시험은 남았다. 서버 BE11-3은 남았다. 호출자 보고: 안드로이드 단위 1,474건 실패 0, 홈 스크린샷 2장 갱신, 서버 변경 없음. 남은 결함 0 / 0 / 32, 판정 합격 그대로.

### 경 중 먼저 볼 것

- AN11-4: 242612e9부터 RunkkNavHost를 스플래시 아래에 미리 그리는데 `featureEntries.forEach { it.Overlay(top) }`가 그대로 붙어 남은 대결 AlertDialog(별도 창)가 스플래시 위에 뜬다. 스플래시는 손가락만 막아 TalkBack은 아래 홈 단추를 누를 수 있다(CR-86 "스플래시 동안 아래 화면은 눌리지 않는다"). 덮인 동안 Overlay를 붙이지 않고 NavHost 칸을 접근성 트리에서 숨긴다.
- AN11-6: CoachViewModel.onTodayPace가 상태를 먼저 바꾼 뒤 `save`가 저장 중이면 말없이 버리고, refresh의 `pace ?: it.todayPace`가 그 값을 남겨 화면과 서버·TodayPlanCache가 어긋난다. 저장 중에는 칸을 막고 실패면 되돌린다.
- AN11-9·BE11-3: 새 규칙(onTodayPace, RunkkSoundVolume, 첫 화면 붙잡기·4초 상한, LoopingMusic 처음만 확인, ProfileEdit 성공 뒤 연타)과 서버 notifyCollapsing 푸시 차단·크루 일 연속에 시험이 없다.
- AN11-1·AN11-2: LOCK_UNRELEASED 규칙이 :app·:data build.gradle.kts에, "셀프·셀럽만 취소" 규칙이 ui.battle·domain 세 곳에 복사돼 있다.
- AN11-7·AN11-8: 페이스 선택지가 RunkkFormat.pace를 다시 구현했고, 라임 알약 모양과 ExoPlayer 열기가 두 곳씩 있다(설계 담당이 넘긴 중복 2건).
- AN10-6(이월): `isRevealed`도 `remember`라 구성 변경 때 스플래시가 다시 덮고 배경 음악이 잠깐 멈춘다.

### sw-test 전달

순환 복잡도 10 초과 새·변경 함수는 없다. 새 규칙과 회귀 경로(CoachViewModel 페이스 순서·저장 중·실패, RunkkSoundVolume toggle·경계, 첫 화면 붙잡기·4초·대화상자, LoopingMusic 다른 앱 확인·ownPlaying, ProfileEdit 연타, notifyCollapsing, 크루 일 연속)와 실기기 NFR-045~047은 조각 8절 표에 있다.

### 설계서 갱신 요청 (`/sw-design-oo` 재호출 목록)

U11-1(D-338 isCancellable 위치), U11-2(D-332 DeviceCopySync.clearAccount에 성별 사본), U11-3(13장 LOCK_UNRELEASED 한 정의), U11-4(D-350 막대 읽기 문구, 페이스 선택지 읽기), U11-5(D-352·SQ-39 스플래시 동안 Overlay·접근성 숨김), U11-6(D-354 저장 중 페이스·실패 되돌림·refresh 조건). 05-14 7절이 이미 넘긴 core.designsystem 응집도와 매개변수 기존 4건은 중복 요청하지 않았다.

### 다음 행동

- [ ] 배포 전: BE10-3, BE10-7, BE10-6, AN10-5(U11-2와 함께)
- [ ] 다음 반복 먼저: AN11-4, AN11-6, AN11-9, BE11-3 → 758de88f에서 AN11-4·AN11-5·AN11-6 해소, AN11-9 일부 해소. 남은 것은 BE11-3과 AN11-9의 첫 화면·LoopingMusic 시험
- [ ] `/sw-design-oo`: U11-1~U11-6
- [ ] `/sw-design` review: core.designsystem 응집도(05-14 5절), 매개변수 기존 4건과 AN11-3
- [ ] `/sw-test`: 조각 8절, srs 7.13.13 시험 영향, 실기기 NFR-045~047

## 리뷰 2026-11-01 (12차)

워치 v2 반복(develop 6400955a..feature/runkk-watch-v2 a80c86b4, MR !57)의 리뷰다. 기준은 design-oo v0.22(05-16 D-365~D-394), srs v0.18(CR-92~CR-102), 구현 기록 implementation/14-v0.22-워치-v2.md(결정 I14-1~I14-35, 14.5 설계서 갱신 요청, 미리보기 결함 PV-1)다. 서버와 앱을 보조 작업자 둘이 나눠 읽기 전용으로 읽었고, 빌드·시험은 통합 브랜치 검증 결과(14.3)를 인용했다. 언어 적정성 점검은 최초 리뷰에서 했으므로 생략했다.

| 파일 | 범위 | 새 결함 (치명/중/경) | 판정 | 설계서 갱신 요청 |
|------|------|----------------------|------|------------------|
| [09-12차-v022-서버](code-review/09-12차-v022-서버.md) | 서버: POST /auth/watch-session(발급 한도), GET /battles/record-tracks, startDevice V33, 계약 자료 | 0 / 0 / 7 | 합격 | U12-1~U12-6 |
| [09-12차-v022-앱](code-review/09-12차-v022-앱.md) | 앱: :data:phone 분리, domain 포트, wear-contract, 폰 WatchSessionPublisher·WatchLink·동시 측정 막기, 워치 watch.run·session·cache·guide·link·ui | 0 / 3 / 12 + 11차 이월 경 14 | 합격(중이 상한 3) | 14.5 앱 4행 + U12-1~U12-11 |

형상 관리는 두 조각 모두 변경 통제(커밋 이슈 키)만 아니오이고, 이전 차수처럼 사용자 결정(이슈 키 없이 진행, 2026-10-31)이라 판정 요인에서 뺐다.

### 중 결함 (병합 전에 고친다)

- AN12-1: 폰에 WearableListenerService가 없어, 폰 앱 프로세스가 죽어 있을 때 워치의 SESSION_REQUEST가 사라지고 이후에도 "이미 보낸 회원"으로 발급을 건너뛴다. 세션을 잃은 워치가 미로그인 화면에 머문다(FR-240).
- WR12-1(미리보기 PV-1): 서버 거부 사유(RANKED_ONLY 등)를 워치가 "매칭하지 못했어요" 한 문구로 바꾼다. awaitMatch가 실패 사유를 버리고, 셀프·셀럽 거부도 한 문구다. 폰의 요청 전 잠금(RandomBattleGate)도 없다.
- WR12-2: 하드웨어 뒤로 키가 측정·결과 등에서 막히지 않아 앱이 닫히고, 대결 성사~출발 사이면 다시 열었을 때 대결 상태를 잃는다(상태도 2.4.25 위반).

### 배포 전에 볼 경 결함

- BE12-1: 셀럽 record-tracks가 모든 셀럽의 1초 표본을 싣는다(풀 기록 약 340KB, 압축 없음) → 사용자 결정 W24: 서버 응답 gzip.
- BE12-2: V33 CHECK가 전 행 검사 동안 쓰기를 막는다 → NOT VALID 뒤 VALIDATE로 나눈다(V33 미배포).
- U12-6(서버 설계 갱신 항목): 폰 로그아웃 때 서버가 워치 토큰을 끊지 못함 → 사용자 결정 W25: 로그아웃·탈퇴·워치연동 끄기 때 서버가 워치용 갱신 토큰 폐기(DELETE /auth/watch-session 추가).

### sw-test 전달

조건문+반복문+1 기준 10 초과 함수는 없다. `?:`·`&&`까지 세면 11~12인 워치 함수 4개를 앱 조각의 우선 테스트 대상으로 적었다. 서버는 최대 약 4다.

### 다음 행동

- [ ] 수정 반복(R1 서버: BE12-1·BE12-2·W25·쉬운 경, R2 앱: AN12-1·WR12-1·WR12-2·워치연동 끄기 DELETE 호출·쉬운 경) → 13차 확인 리뷰
- [ ] `/sw-design-oo`: 서버 U12-1~U12-6, 앱 14.5 4행 + U12-1~U12-11
- [ ] `/sw-test`: 워치 v2 케이스(FR-240~FR-260, NFR-048~NFR-051), 우선 테스트 대상

## 리뷰 2026-11-01 (13차 확인)

12차 결함 수정(R1 서버, R2 앱), 사용자 결정 W26(워치 토큰 발급 거부), R3(W27 온라인 셀프·셀럽 서버 판정, W28 동점은 진 것)을 확인한 리뷰다. 범위 a80c86b4..807d744a. 보조 작업자가 읽기 전용으로 읽었고 빌드·시험은 통합 검증 결과(implementation 14.3)를 인용했다.

| 파일 | 12차 추적 | 새 결함 (치명/중/경) | 판정 | 설계서 갱신 요청 |
|------|-----------|----------------------|------|------------------|
| [10-13차-v022-확인](code-review/10-13차-v022-확인.md) | 21건: 해소 15, 일부 2(AN12-1 재요청 부분 → WR13-1, WR12-6 → WR13-3), 보류 4(BE12-4, AN12-2, WR12-7, WR12-8), 미해소 0. 폰 MeasurementService M5·서버 폐기 재시도 M1은 보류 | 0 / 0 / 8 (+ 보류 4, 앱 이월 14) | 합격 | U13-1~U13-6 |

새 경 결함: BE13-1(쓰지 않는 RefreshGrant 팩터리), BE13-2(watch_session_generation 행 정리 규칙 없음), BE13-3(압축 뒤 셀럽 응답 크기 시험 없음), AN13-1(다른 회원 로그인 직후 잘못된 서버 폐기 가능, 드묾), WR13-1(NotLoggedIn 재요청 없음, D-404), WR13-2(재진입 때 기록 대결이 배틀런으로 복원), WR13-3(TTS shutdown이 결과 완료에만), WR13-4(ACTION_STOP 서비스 쪽 시험 없음).

### 다음 행동

- [ ] 수정 반복 R4(앱: 설계 검증 R75 사용자 결정 W30에 따른 watch.battle 분리, R73 팩토리 분리, R76, WR13-1~WR13-4, AN13-1)·R5(서버: BE13-1~BE13-3, R74·M86) → 14차 확인 리뷰
- [ ] 병합 전 flyway_schema_history에 옛 V33이 없는지 확인(V33을 NOT VALID로 고쳤음)
- [ ] `/sw-design-oo` v0.24 → `/sw-design` review
- [ ] `/sw-test`: 13차 조각 11절 우선 테스트 대상

## 리뷰 2026-11-02 (14차 확인)

R4(watch.battle 분리, 사용자 결정 W30), R5(서버 정리), R6·R7(사용자 결정 W31 셀럽 목록만 미리 받고 고를 때 단건)을 확인한 리뷰다. 범위 807d744a..89457e26.

| 파일 | 13차 추적 | 새 결함 (치명/중/경) | 판정 | 설계서 갱신 요청 |
|------|-----------|----------------------|------|------------------|
| [11-14차-v022-확인](code-review/11-14차-v022-확인.md) | 새 경 8건 모두 해소 | 0 / 0 / 5 (BE14-1~3, WR14-1·2; 폰 0) | 합격(경 합계 23) | U14-1~U14-5 |

### 다음 행동

- [ ] R8(BE14-1~3)·R9(WR14-1·2) → 통합·검증·push
- [ ] 병합 전 flyway_schema_history에 옛 V33이 없는지 확인(이월)
- [ ] `/sw-sqa` v0.6(워치 v2 범위)

## 리뷰 2026-11-02 (15차 확인)

UI1(캐러셀 Smart Animate·목표 카드 크기), UI2·UI4(메인 로고 제거, 아바타 유지, W34·W36), UI3(폰 동행 표시를 워치 측정 화면으로 통일, W35)을 확인한 리뷰다. 범위 6906c76a..3ee5138e. 읽기 전용이고 빌드·시험은 돌리지 않았다.

| 파일 | 14차 추적 | 새 결함 (치명/중/경) | 판정 | 설계서 갱신 요청 |
|------|-----------|----------------------|------|------------------|
| [12-15차-v022-확인](code-review/12-15차-v022-확인.md) | 5건 모두 해소(BE14-1~3, WR14-1·2) | 0 / 0 / 6 (WR15-1~6) | 합격(경 합계 24) | U15-1·U15-2 |

새 경 결함: WR15-1(캐러셀 위치를 구성 단계에서 읽어 프레임마다 카드 전체 재구성), WR15-2(끌기 델타를 코루틴으로 반영해 같은 프레임 이벤트가 겹치면 손실), WR15-3(pointerInput(Unit)이 첫 구성의 놓기 람다를 붙잡음), WR15-4(작은 카드 터치 영역 44dp, 48dp 미달), WR15-5(역전 방향 kind를 버리고 띠 색을 근접 값 부호로 정함), WR15-6(끌기·움직임 줄이기 시험 없음).

### 다음 행동

- [ ] R10(워치): WR15-4·5 먼저, WR15-1·2·3·6 한 묶음 → 통합·검증·push
- [ ] `/sw-design-oo`: U15-1(동행 변환 규칙)·U15-2(캐러셀) 반영
- [ ] 병합 전 flyway_schema_history에 옛 V33이 없는지 확인(이월)
- [ ] `/sw-sqa` v0.6(워치 v2 범위)

## 리뷰 2026-11-02 (16차 확인)

R11(15차 결함 WR15-1~6), R12(정지 중 종료 400·거부 기록 REJECTED, Room 판 5), R13(셀프런 myReach 없음 STOP 오판정 외), UI5(W37 Barlow Condensed, W38 피그마 세부), R14(W40 거부 기록 다시 보내기), M1(W41 워치 모션), W42(연결 중 카운트다운), f4dd2a68(측정 km 기준선)을 확인한 리뷰다. 범위 `--first-parent 4d85393d..f4dd2a68`(develop 병합 기준선 이후). 읽기 전용이고 빌드·시험은 돌리지 않았다.

| 파일 | 15차 추적 | 새 결함 (치명/중/경) | 판정 | 설계서 갱신 요청 |
|------|-----------|----------------------|------|------------------|
| [13-16차-v022-확인](code-review/13-16차-v022-확인.md) | 6건 모두 해소(WR15-1~6, WR15-1은 절충) | 0 / 1 / 5 (AN16-1, AN16-2, WR16-1~4) | 합격(경 합계 23) | U16-1~U16-7 |

새 결함: AN16-1 중(폰 설정 기록 전송 줄이 폰 거부 건수가 있을 때만 보여 워치에만 있는 거부 기록은 다시 보낼 길이 없음), AN16-2(워치 미연결이면 요청이 버려지는데 화면은 다시 보내는 중), WR16-1(움직임 줄이기 판단이 절전 모드 변경을 따라가지 않음, 이제 앱 전체), WR16-2(다이얼 숫자 77sp가 64dp 칸을 넘치는데 10km 기준 없음), WR16-3(측정 거리 전환 중 옛 값이 의미 트리에 남음), WR16-4(쓰지 않는 import 10개, CI ktlintCheck 실패 예상).

### 다음 행동

- [ ] push 전: WR16-4 import 정리, `./gradlew ktlintCheck`
- [ ] 사용자 결정: W40 워치 거부 기록 표시 방식(AN16-1), 요청을 메시지로 둘지 데이터 항목으로 둘지(AN16-2)
- [ ] R15(폰·워치): AN16-1·AN16-2, 함께 WR16-1~3 → 통합·검증·push
- [ ] `/sw-design-oo`: U16-1~U16-7 반영(REJECTED 상태도, 기록 끝 규칙, W40 클래스·경로, W41 모션 규칙, R13 결과 규칙)
- [ ] 병합 전 flyway_schema_history에 옛 V33이 없는지 확인(이월)
- [ ] `/sw-test`: 13-16차 조각의 우선 테스트 대상

## 리뷰 2026-11-03 (17차 확인)

R15(AN16-1·AN16-2 W43, WR16-1~4)와 R16(R79~R81 리팩터링, W48·W49 홈 재전송 안내 띠, 시험 자동화)을 확인한 리뷰다. 범위 `--first-parent f4dd2a68..4017839f`. 읽기 전용이고 빌드·시험은 돌리지 않았다.

| 파일 | 16차·설계 검증 추적 | 새 결함 (치명/중/경) | 판정 | 설계서 갱신 요청 |
|------|---------------------|----------------------|------|------------------|
| [14-17차-v022-확인](code-review/14-17차-v022-확인.md) | 9건 모두 해소(AN16-1·2, WR16-1~4, R79~R81) | 0 / 0 / 1 (AN17-1) | 합격(경 합계 19) | U17-1·U17-2 |

## 리뷰 2026-11-03 (18차 확인)

R17(W50 셀프 기록 카드·셀럽 목록 두 번 눌러 시작)을 확인한 리뷰다. 범위 `--first-parent 4017839f..793eb85e`(develop 받기는 뺌). 읽기 전용이고 빌드·시험은 돌리지 않았다.

| 파일 | 17차 추적 | 새 결함 (치명/중/경) | 판정 | 설계서 갱신 요청 |
|------|-----------|----------------------|------|------------------|
| [15-18차-v022-확인](code-review/15-18차-v022-확인.md) | 남은 경 그대로(AN17-1 등) | 0 / 0 / 2 (WR18-1, WR18-2) | 합격(경 합계 21) | 없음(05-20 10.2에 반영) |

새 결함: AN17-1(홈 ViewModel이 재전송 안내를 다시 세는 신호로 미전송 목록 전체를 구독, R80이 없앤 스탬프가 ui.home에 생김).

### 다음 행동

- [ ] Gradle 검증 결과 확인(ArchitectureTest 155·37, 새 시험, ktlintCheck)
- [ ] AN17-1: 바뀜 신호를 COUNT 흐름으로
- [ ] `/sw-design-oo`: U17-1·U17-2

## 리뷰 2026-11-03 (AI 코치 동의·화면, 박재영)

#247 LLM 동의 안내와 #246 코치 대화 상태·화면 준비(feature/S15P31A207-247-ai-coach, develop에 병합 e66eb8a0)를 작성자가 검토한 기록이다. 워치 v2 리뷰 차수(12~18차)와 따로 센다. 사람 리뷰는 MR에서 받는다.

| 파일 | 범위 | 새 결함 (치명/중/경) | 판정 | 설계서 갱신 요청 |
|------|------|----------------------|------|------------------|
| [ai-coach-consent-박](code-review/ai-coach-consent-박.md) | feature/me 동의, domain.coach, ui.record.coach, 시험, ArchitectureTest | 0 / 0 / 0 | 조건부(사람 리뷰 전, #246 실제 서버·제품 경로 미연결, TalkBack·실기기 미실측) | 하위 패키지 배치 → design-oo D-474(v0.28에 반영) |

### 다음 행동 (AI 코치)

- [ ] #241 연동 때: 요청·SSE·목록 JSON 표본을 앱 계약 시험으로 고정하고, CoachFailure 6개 매핑과 화면 종료 시 요청 취소 전파를 리뷰한다
- [ ] #246 후속: Hilt 바인딩·런바디 진입·디자인 #265 대조 뒤 다시 리뷰한다

## 리뷰 2026-11-03 (AI 코치 화면 독립 리뷰, -233)

#247 LLM 동의 안내와 #246 코치 대화 상태·화면 준비(develop 09e568dc, MR !59)를 PM 요청으로 따로 검토한 사람 쪽 리뷰다. 범위 `git diff c29df9fa 09e568dc -- android/`. 자가 리뷰 [ai-coach-consent-박](code-review/ai-coach-consent-박.md)의 주장을 코드 줄로 대조했다. 읽기 전용이고 빌드·시험은 다시 돌리지 않았다(PM 보고 0b16eafe 2,312건 실패 0). 피그마 일치는 미확인, #241·어댑터·Hilt·런바디 진입은 미연결이다.

| 파일 | 범위 | 새 결함 (치명/중/경) | 판정 | 설계서 갱신 요청 |
|------|------|----------------------|------|------------------|
| [16-ai-코치-화면-리뷰](code-review/16-ai-코치-화면-리뷰.md) | domain.coach, ui.record.coach, feature/me 동의, 시험, ArchitectureTest | 0 / 0 / 10 (AC-1~AC-10) | 조건부(코드 결함은 합격선, 사람 리뷰 승인 전 병합이라 변경 통제 사후. PM 기록 뒤 합격) | UAC-1~UAC-5 |

새 결함 가운데 먼저 볼 것: AC-1(동의 제목 2개의 가운뎃점), AC-3(조건 없는 CancellationException 재던짐이 어댑터 연결 뒤 전송 중 상태를 남길 수 있음), AC-2(끊긴 답이 완료 답과 같아 보임).

### 다음 행동 (AI 코치 독립 리뷰)

- [ ] PM: 이 리뷰를 MR !59·Jira에 사람 리뷰 결과로 남긴다
- [ ] AC-1, AC-3·AC-9를 어댑터(D-452) 연결 전에 고친다
- [ ] `/sw-design-oo`: UAC-1~UAC-5
- [ ] `/sw-test`: 조각 7절 우선 테스트 대상

## 리뷰 2026-11-04 (배치고사 1차, S15P31A207-275)

배치고사·티어 표시·홈 요약·러닝 결과 상용안·랭킹 시상대 핫픽스(MR !70 → main)의 1차 리뷰다. 범위 `git diff df211dc5..bad8c750`(BE-0 계약, BE 서버, AN-1 앱 기반·화면, infra 스케줄러 풀). AN-2(대결 분리·배치고사 화면·워치)는 구현 중이라 2차에서 1차 해소와 함께 본다. 관점별 리뷰어 넷(서버 동시성, 앱, 보안·개인정보, 시험 대조)의 지적을 코드 줄로 다시 확인해 등급을 매겼다. 읽기 전용이고 빌드·시험은 다시 돌리지 않았다(통합 빌드 결과 서버 시험 묶음 237개 건너뜀 0·실패 0).

| 파일 | 범위 | 새 결함 (치명/중/경) | 판정 | 설계서 갱신 요청 |
|------|------|----------------------|------|------------------|
| [17-배치고사-1차](code-review/17-배치고사-1차.md) | BE-0·BE·AN-1·infra, 서버·앱 시험 | 0 / 5 / 16 (PL1-B1~B8, PL1-A1~A9, PL1-T1~T4) | 조건부 | U-1~U-11, 시험서 UT-1~UT-3 |

설계 일치: 배치고사 규칙 1~13, D-475~D-520, R82~R95가 모두 코드에 있다. 이름 차이는 모두 총괄 결정에 근거가 있어 설계 불일치 결함 0, 설계서 갱신 요청으로 분리했다. 임시 티어 노출 경로 없음, prod 마이그레이션 설정 그대로, 잠금 순서 교착 없음.

### 다음 행동 (배치고사 1차)

- [ ] 병합 전: PL1-B1, PL1-B3, PL1-A1 고침, PL1-T1 두 스레드 통합 시험으로 PL1-B2 확인(재현되면 고침)
- [ ] 2차 리뷰: AN-2 + 위 해소 확인
- [ ] `/sw-design-oo`: U-1~U-11. `/sw-test`: UT-1~UT-3, 조각 7절 우선 테스트 대상
- [ ] 배포 확인: prod flyway 최대 V35, 운영 `.env` SCHEDULER_POOL_SIZE 21

## 리뷰 2026-11-05 (배치고사 2차, S15P31A207-275)

1차 결함 해소 확인(서버 `git diff bad8c750 96cc3f5e`, 앱 d30b944c)과 AN-2 새 코드(`git diff 3dfb8552 c2685561`: 랜덤·친구 대결 분리, 배치고사 진행·판 결과·완료, 매칭 「?」, 워치 잠금, BattleMode 삭제, MatchFound.placementRound)의 2차 리뷰다. 관점별 리뷰어 셋(서버 동시성, 앱 흐름, 접근성)의 지적을 코드 줄과 서버 시험 출력으로 다시 확인했다. 읽기 전용이고 빌드·시험은 다시 돌리지 않았다(통합 게이트: 서버 시험 1,931개 실패 0, 앱 검증·Roborazzi 성공).

| 파일 | 범위 | 새 결함 (치명/중/경) | 판정 | 설계서 갱신 요청 |
|------|------|----------------------|------|------------------|
| [18-배치고사-2차](code-review/18-배치고사-2차.md) | 1차 수정 확인, AN-2, 시험 | 0 / 2 / 14 (PL2-A1~A8, PL2-B1, PL2-T1, 1차 이월 경 6) | 조건부 | U2-1~U2-8, 시험서 UT2-1~UT2-4 |

1차 해소: 중 5건 모두 닫힘(PL1-B1·B3·A1·T1 해소, PL1-B2는 시험 출력의 follow-on 잠금 version 충돌 증거로 닫음), 경 16건 중 11건 해소·6건 이월. AN-2는 D-499~D-501·D-520과 브리프 화면 범위(하단 내비 숨김, 뒤로 = 홈, 임시 티어 본인만, 기준 이미지 목록 안)를 따른다. 남은 중 2건은 필수 요구 FR-276(대결 진행 화면 「?」)과 FR-281(완료 화면에 닿는 길)이다.

### 다음 행동 (배치고사 2차)

- [ ] 병합 전: PL2-A1, PL2-A2 고침 + 시험 → 3차 확인
- [ ] `/sw-design-oo`: U-1~U-9·U-11(U-10 철회), U2-1~U2-8. `/sw-test`: UT-1~UT-3, UT2-1~UT2-4

## 리뷰 2026-11-05 (배치고사 3차 확인, S15P31A207-275)

2차 결함 수정(`git diff c2685561 4129283c`: 서버 PL2-B1 e35dba34, 앱 FR-276 b4798db8, PL2-A2 0d648155·eb9161e1, PL2-A4~A8·PL1-T4)의 확인 리뷰다. 읽기 전용, 변경 줄과 진입 경로를 직접 읽었다.

| 파일 | 범위 | 결함 (치명/중/경) | 판정 | 설계서 갱신 요청 |
|------|------|-------------------|------|------------------|
| [19-배치고사-3차](code-review/19-배치고사-3차.md) | 2차 수정 확인 | 0 / 1 / 9 (새 PL3-A1·PL3-A2, 2차 남음 PL2-A3·A6·T1, 1차 이월 5) | 조건부 | U3-1~U3-4, 시험서 UT3-1~UT3-3 |

2차 해소: PL2-A1·A4·A5·A7·A8·B1, PL1-T4 해소. PL2-A2·A6·T1 일부 해소, PL2-A3은 D-534대로 후속. 남은 중 PL3-A1은 미확인 완료 화면 확인이 배치고사 진행 화면에만 있어, 3판째가 정리 중인 채 홈으로 나간 러너가 홈 랜덤 카드로 들어오면 완료 화면(FR-281)을 건너뛰는 것이다. 고칠 곳은 `RandomBattleViewModel.loadPlacement`(같은 판정)와 저장 실패 대비(PL3-A2)다.

### 다음 행동 (배치고사 3차)

- [ ] 병합 전: PL3-A1 + PL3-A2 고침과 시험 → 4차 확인(diff)
- [ ] 후속 이슈: PL2-A3, PL2-A6 나머지, PL2-T1 나머지, PL1-A3·A4·A6·A7·A9
- [ ] `/sw-design-oo`: U3-1~U3-4. `/sw-test`: UT3-1~UT3-3

## 리뷰 2026-11-05 (배치고사 4차 확인, S15P31A207-275)

3차 결함 수정(`git diff 4129283c 564db6af`, 커밋 06ca6de4: PL3-A1·PL3-A2)의 확인 리뷰다. 읽기 전용, 변경 줄과 진입 경로를 직접 읽었다.

| 파일 | 범위 | 결함 (치명/중/경) | 판정 | 설계서 갱신 요청 |
|------|------|-------------------|------|------------------|
| [20-배치고사-4차](code-review/20-배치고사-4차.md) | 3차 수정 확인 | 0 / 0 / 9 (새 PL4-A1, 2차 남음 3, 1차 이월 5) | 합격 | 없음, 시험서 UT4-1 |

PL3-A1·PL3-A2 해소. 랜덤 대결 화면이 판정 함수로 완료 화면을 열고(replace라 백스택 왕복 없음), 저장 실패는 같은 회원 한정 프로세스 사본으로 흡수해 완료 ↔ 랜덤 반복이 없다.

### 다음 행동 (배치고사 4차)

- [ ] 병합: 총괄 전체 게이트 통과 뒤
- [ ] 후속 이슈: PL4-A1, PL2-A3, PL2-A6 나머지, PL2-T1 나머지, PL1-A3·A4·A6·A7·A9
- [ ] `/sw-test`: UT4-1

## 리뷰 2026-11-05 (핫픽스 추가 변경 1·2차, S15P31A207-275)

| 조각 | 범위 | 치명 / 중 / 경 | 판정 | 설계서·시험서 갱신 요청 |
|------|------|----------------|------|--------------------------|
| [21-핫픽스-추가변경-1차](code-review/21-핫픽스-추가변경-1차.md) | 4a2722fd..80592a08 9커밋(1차), 수정 45f7d383(2차 확인) | 1차 0 / 2 / 8 → 2차 0 / 0 / 7 | 1차 수정 필요 → 2차 합격 | U5-1·U5-2(05-23 D-549·D-550·D-559), UT5-1(test 20장) |

### 다음 행동 (핫픽스 추가 변경)

- [x] 1차 중 2·경 2 수정(45f7d383), 게이트 통과
- [ ] 후속 경: PL5-A2 남은 경우, PL5-A3~A8
- [ ] `/sw-sqa` v0.10: 이 결과와 실기기 조건(TC-4879·TC-4880)


## 리뷰 2026-11-04~06 (박재영 자가 리뷰: #244 AI 파기, #276 얼굴 비동기, #271 워치 셀럽 404)

박재영 브랜치 세 개를 작성자가 검토한 기록이다. 모두 develop에 병합됐다(#244 60c3385f 2026-11-04, #276 1d1e5d44 2026-11-04, #271 7876cba9 2026-11-06). 워치·핫픽스 리뷰 차수와 따로 센다. 치명·중·경 건수 표는 조각에 없어 적지 않는다. 사람 리뷰는 MR에서 받는다.

| 조각 | 범위 | 조각이 적은 결과 | 비고 |
|------|------|------------------|------|
| [ai-자료파기-박](code-review/ai-자료파기-박.md) | aiprivacy·infra.aiprivacy·batch.aiprivacy, V37, 감사 행위 | 확인·수정 6건 반영, 남은 조치 필요 지적 없음 | 서비스 생성자 인자 6개는 검토 항목 |
| [얼굴비동기-박](code-review/얼굴비동기-박.md) | avatar, infra.avatar, infra.integration.ai, web.avatar, V36 (CR-154, FR-293~FR-298) | 발견 7건 수정 | 서비스 생성자 인자 8개는 검토 항목 |
| [워치셀럽404-박](code-review/워치셀럽404-박.md) | WatchFlowViewModel.onCelebritySelected와 목록 보정 함수 (FR-259) | 수정 전 4실패 후 통과, 관련 102건 통과 | onCelebritySelected 복잡도 10 초과는 우선 시험 대상 |
| [코치중계-박](code-review/코치중계-박.md) | web.coach, aicoach, infra.aicoach, batch.aicoach, AiServerCoachGateway, V46 (FR-015, FR-261~FR-264) | 검토 지점 5개(동시 질문과 탈퇴 경합, 철회와 탈퇴 때 스트림, 응답 종료, 비동기 인증, 입력과 외부 오류) 반영 | 남은 조치 필요 지적 없음(작성자 보고) |
| [writer-모델관리-박](code-review/writer-모델관리-박.md) | web.admin.model, aimodel, AiServerModelGateway, V47 (FR-268~FR-271, CR-179) | 검토 5개(인증과 내부 호출, 등록 URL, 감사 실패, 동시 등록과 롤백, 마이그레이션) 반영 | AI 직접 호출과 다중 인스턴스는 AI 등록부 동시성 범위를 따른다 |
| [코치재동의-박](code-review/코치재동의-박.md) | android api.coach, ui.record.coach, ui.me 동의 설정, api.core BoundSession (FR-015, FR-261~FR-264, #273) | 검토 지점 3개(계정 전환과 401 재시도, 동의 저장 실패와 복귀, SSE 취소) 반영 | 남은 조치 필요 지적 없음(작성자 보고). 실기기 확인은 배포 시험 |

### 다음 행동 (박재영 병합분)

- [ ] 사람 리뷰어: #271 실기기 화면·접근성 확인(조각 기록상 미확인)
- [ ] #244: 운영 배치·실제 AI·Node 삭제 확인 후 재검토
- [ ] #276: 앱 폴링·3레이어 합성 연동 때 재검토
- [ ] #241: 앱 코치 연동, 배포 구성 활성 모델 연결 확인 뒤 재검토(srs 7.10.9 K1, K4). (세 번째 병합) 앱 코치 연동은 #273으로 들어왔다
- [ ] #243: writer 실제 교체 배포 시험 뒤 재검토(srs 7.10.9 K5)

## 리뷰 2026-11-08 (2차 배포 통합 1차, S15P31A207-222)

| 조각 | 범위 | 치명 / 중 / 경 | 판정 | 설계서·시험서 갱신 요청 |
|------|------|----------------|------|--------------------------|
| [22-2차배포-통합-1차](code-review/22-2차배포-통합-1차.md) | 55cfd678..e9baef18 42커밋(합치기 6, MR !91) | 0 / 1 / 8 | 합격, 단 BR-1 병합 전 수정 조건. 사용자 결정: 9건 모두 병합 전 수정 → 수정 완료(1ae0b108), 확인 리뷰 진행 중 | U-1·U-3·U-4 design-oo 05-25 반영, U-2 05-25·F.2.2 반영과 FR-194 병합 대기 요청, U-5 계약 파일(문서 밖), U-6 AN-3 수정(878c5e21) 뒤 05-25에 반영 완료. sw-test 우선 대상 7절 |

## 리뷰 2026-11-08 (2차 배포 통합 확인, S15P31A207-222)

| 조각 | 범위 | 치명 / 중 / 경 | 판정 | 설계서·시험서 갱신 요청 |
|------|------|----------------|------|--------------------------|
| [23-2차배포-통합-확인](code-review/23-2차배포-통합-확인.md) | e9baef18..1ae0b108 수정 19커밋(1차 결함 9건, e2e 1~7, 시간대 후속) | 0 / 0 / 4 | 합격. 새 경 4건 CR2-A1·B1·B2·T1 모두 수정(1e93c919·17fd942e·b23837ca·c5ee333a), develop 합치기 9fbcdf43 | U2-1~U2-3 design-oo 05-25 반영. sw-test 우선 대상 7절 |

## 리뷰 2026-11-08 (이벤트 1차, S15P31A207-275)

| 조각 | 범위 | 치명 / 중 / 경 | 판정 | 설계서·시험서 갱신 요청 |
|------|------|----------------|------|--------------------------|
| [이벤트-1차-성](code-review/이벤트-1차-성.md) | hotfix/S15P31A207-275-v0114-coach-celeb origin/main...d824b2a2(MR !90): event, infra.event, web.event, web.admin.event, battle 경계, reward EVENT, V35_2, nginx /events/, 정적 페이지, 앱 이벤트 화면과 브리지 | 0 / 2 / 10 (제안 4, 확인 필요 1) | 합격(조건부 배포). 주요 PL6-B1 수정, PL6-B5 배포 절차(MR !90 병합 직후 develop 역병합, 그 전 develop, release 배포 보류). 경 7 수정(d221fd54, 2f99b573), 응모자 명단 d06641bb | 설계 05-26 D-611 갱신, 새 D-620(응모자 명단). 시험 test/이벤트-성 9절 우선 1~6 |

## 리뷰 2026-11-08 (운영자 공지 알림 1차, S15P31A207-275)

| 조각 | 범위 | 치명 / 중 / 경 | 판정 | 설계서·시험서 갱신 요청 |
|------|------|----------------|------|--------------------------|
| [공지알림-1차-성](code-review/공지알림-1차-성.md) | MR !90 b26b3317: notification 공지, web.admin.notification, V35_3, push, 앱 알림함 공지, 계약 | 0 / 1 / 3 (제안 2) | 합격. 주요 PL7-B1(링크 점 조각 우회, 보안) 수정 62b7f7e8, 경 PL7-B2 수정, PL7-B3, PL7-B4 운영 절차(다시 보내지 않기, 롤백 전 NOTICE 행 삭제) | 설계 05-27 D-624 갱신(링크 경로 규칙), 8절 R10~R14 판정. 시험 test/이벤트-성 13절 TC-2739~TC-2777 |

## 리뷰 2026-11-09 (이벤트 화면 디자인 반영, S15P31A207-275)

| 조각 | 범위 | 치명 / 중 / 경 | 판정 | 설계서·시험서 갱신 요청 |
|------|------|----------------|------|--------------------------|
| [이벤트-화면-디자인-성](code-review/이벤트-화면-디자인-성.md) | MR !104 feature/S15P31A207-275-event-ui-align: core.designsystem 새 부품(RunkkStatusChip, RunkkDdayBadge, RunkkUnderlineTabs, RunkkPageDots, RunkkNotificationDot, CelebrityPortrait), 알림함 이벤트 행과 공지 행, 홈 알림 점, 셀럽런 카드, 결과 화면 쪽 점 | 0 / 1 / 1 (제안 2) | 합격. 주요 PL8-A1(05-26 보충이 MR !100과 다섯 곳 충돌) 88e31d9a 수정. 경미 PL8-A2(hasBadge 점 테두리 색), 제안 PL8-A3(꺼진 넘김 점 다크 대비), PL8-A4(홈 알림 점 기준 이미지) 남김. 남은 조건: 박재영의 ui.battle 변경 확인 | 없음(설계 D-629~D-632 일치, v0.39 병합에서 05-26 표로 옮김) |

