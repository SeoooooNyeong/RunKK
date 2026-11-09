# 런바디(RunBody) 화면 피그마 대조표

- **기준 커밋: bb3e3fbe** (develop, "Merge branch 'fix/S15P31A207-275-server-timezone'"). 앱 열의 파일:줄과 차이 판정은 모두 이 작업 트리 기준이다.
- 이 기준에는 811eef40 "fix: 런바디 화면 피그마 맞춤(요약 주간 기록·최근 10회 평균, 월간·상세) [S15P31A207-229]"이 들어 있다. 그 커밋에서 이미 맞춘 항목은 차이에서 뺐고, 표에서 필요한 곳에만 "811eef40에서 맞춤"으로 적었다.
- 피그마: fileKey `NMTtVMWIT22D0CwOU2y0I9`, 대상 13프레임(390x844). 값은 라이트 변수 기준이다.
- 앱: 아래 표의 파일명은 `android/feature/record/src/main/java/com/a207/runkk/app/ui/record/runbody/` 기준이다. 문자열은 `android/feature/record/src/main/res/values/strings.xml`이고 `strings:NNN`으로 적었다. 공통 코드는 `android/core/ui/src/main/java/com/a207/runkk/app/core/designsystem/`에 있다.
- 조사 방법
  - get_design_context: G-13, G-14, G-30, G-17 전체와, G-13 분석 중·G-20·G-22의 하위 노드
  - get_metadata: G-13 분석 중 섹션, G-20, G-21, G-22, G-23, G-32, G-33
  - get_screenshot: 전 프레임
- 확인하지 못한 것은 "확인 못 함"으로 적었다. 다크 모드 변수 값은 조회하지 않았다(확인 못 함).

---

## 0. 피그마 토큰과 앱 토큰 대응 (모든 화면 공통)

| 피그마 변수(라이트) | 값 | 앱 토큰(라이트 값, RunkkColors.kt) | 차이 |
|---|---|---|---|
| runkk-color-background | #F7F8F2 | Background #FAFAFA (:130) | 다름 |
| runkk-color-surface | #FFFFFF | Surface #FFFFFF (:133) | 같음 |
| runkk-color-textPrimary | #171B12 | TextPrimary #18181B (:139) | 근사 |
| runkk-color-textSecondary | #67705B | TextSecondary #52525B (:140) | 다름 |
| runkk-color-primary | #CAEB2E | LimeFill #CBEA2B (:146) | 근사 |
| runkk-color-onPrimary | #171B12 | OnLimeFill #171B12 (:148) | 같음 |
| runkk-color-secondary | #5C4BDB | Violet #5B4FD9 (:160), BackButton #5C4BDB (:154) | Violet 근사 |
| runkk-color-divider / surfaceHigh | #E9EDE0 | 대응 없음. 앱은 이 자리에 Border #E4E4E7을 쓴다(주간 칩·쉰 날 원·막대 트랙) | 누락 |
| color-secondary-container | #E3DFF9 | 대응 없음. 앱은 Lavender(라이트 #5B4FD9) 알파 0.3으로 근사 | 누락 |
| color-accent-container | #EBE7FC | RunkkTierColors.periodChip #EBE7FC만 있고 일반 토큰은 없음 | 누락 |

- 글꼴: 피그마는 IBM Plex Sans KR이다. 앱은 Noto Sans KR과 숫자용 Barlow Condensed Italic을 쓴다(RunkkType.kt). 앱에 IBM Plex Sans KR 파일이 없다.
- 굵기: 피그마 제목·값은 Bold(700)이다. 앱 런바디는 여전히 Black(900)이 많다(RunBodyParts.kt:47, 91 등). 811eef40의 새 카드(주간 러닝 기록, 평균 타일, 코멘트)는 Bold다.
- 페이스 기호: 피그마는 `5′48″`(U+2032, U+2033), 앱은 `5'48"`(RunBodyFormats.kt:18).
- 토큰 값을 바꾸면 앱 전체가 바뀐다. 런바디만 맞출지, 테마를 피그마 변수로 옮길지는 사용자가 결정해야 한다.

## 1. 공통 틀

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 상단바 높이·제목 | 62, 19 Bold | 최소 64, 18 Black (RunkkBars.kt:84, RunkkType.kt:78) | 다름 |
| 뒤로 버튼 | 원 없는 arrow_back(←) 24, textPrimary, 터치 48, 왼쪽 16·위 7 | 라이트에서 보라 채움 원 40 + 흰 꺾쇠(‹) (RunkkBars.kt:111-121, RunkkIcons.kt:16) | 다름 (G-33만 보라 원, 16절 참고) |
| 본문 좌우 여백 | 20 | 16 (RunBodyScreen.kt:113) | 다름 |
| 본문 위 여백·간격 | 8, 16 | 8, 16 (RunBodyScreen.kt:113-114) | 같음 |
| 카드 테두리 | 없음 | RunkkCard가 항상 1dp Border를 그린다(RunkkCards.kt:103). RunBodyCard, SummaryTile이 모두 이것을 쓴다(RunBodyParts.kt:31-42, RunBodySummaryCards.kt:268-276) | 앱에만 있음 |
| 카드 모서리·안쪽·간격 | 24, 20, 12(최근 10회 변화·주간 러닝 기록은 18) | 24, 20, spacing 인자(기본 12) (RunBodyParts.kt:40-42) | 같음 |
| 카드 제목 굵기 | 18 Bold / 20 Bold | Black (RunBodyParts.kt:47) | 다름 |
| AI 코치 버튼 | M3 Extended FAB 124x56, 라임, 모서리 16, 채운 채팅 아이콘 24 + "AI 코치" 16 Medium, 간격 8, 안쪽 16, 그림자 Elevation 3, 오른쪽·아래 20 | RunkkLimePillButton: 높이 40, 모서리 20, 13 Black, 아이콘·그림자 없음, 오른쪽·아래 16 (RunBodyScreen.kt:119-123, RunkkButtons.kt:292-339) | 다름 |
| AI 코치 표시 화면 | 요약·러닝별·월간·상세에 있고 러닝 선택에는 없음 | 같음 | 같음 |
| 기본 CTA | Lime Large: 높이 52, 알약(100), 16 SemiBold | RunkkPrimaryButton: 높이 56, 모서리 16, 16 Black (RunkkButtons.kt:54-57, 74) | 다름 |
| 비활성 버튼 | Gray: 바탕 #E9EDE0, 글자 textSecondary | 라임 + 알파 | 다름 |
| 로딩·오류 | 피그마 프레임 없음 | RunkkLoading·RunkkErrorState (RunBodyScreen.kt:130) | 앱에만 있음(유지) |

## 2. 탭 줄 "요약·러닝별·월간"

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 컴포넌트 | M3 Segmented button(붙은 3칸, 양 끝만 알약, 테두리 1 #E9EDE0) | RunkkSegmentRow: 8dp 띄운 3칸, 칸마다 모서리 12, 테두리 BorderStrong (RunBodyScreen.kt:82, RunkkChips.kt:99-230) | 다름 |
| 안 고른 칸 | 흰 바탕, 14 Medium textPrimary | 투명, 13 Normal TextSecondary | 다름 |
| 고른 칸 | primary + 체크 18 + 간격 8 + 14 Medium | 라임 + 체크 18·8 + 13 Bold | 다름(글자) |
| 높이 | 전체 48, 칸 40 | 칸 40, 터치 48 | 같음 |

## 3. G-14 런바디 · 요약 (61502:3069)

카드 구성과 순서는 811eef40에서 맞췄다: 최근 10회 변화 → 주간 러닝 기록 → 최근 10회 평균 → 러닝 코멘트(RunBodySummaryTab.kt:53-81). "이번 주 요약" 카드를 뺀 것, 코멘트 제목 "러닝 코멘트"와 말풍선, 페이스 축 끝(6′30″~5′30″)도 같은 커밋에서 맞췄다.

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 주간/월간 토글 | 없음 | 최근 10회 값이 있을 때만 RunkkSegmentedTabs (RunBodySummaryTab.kt:55-63) | 앱에만 있음 (G-33·FR-302와 충돌, 16절) |
| 최근 10회 변화: 제목 | 20 Bold lh1.3 | 20 Black (RunBodySummaryTab.kt:109-114) | 다름(굵기) |
| 범위 "11~20번째 러닝" | 13 Medium textSecondary | RunBodyCaption 14 Normal (:115, RunBodyParts.kt:70-71) | 다름 |
| 회차 칩 | 바탕 #E3DFF9, 12 Bold #5C4BDB, 모서리 999 | 바탕 Lavender 0.3, 12 Bold Violet, 모서리 14 (:117-125, 384, 386) | 다름 |
| 카드 요소 간격 | 18 | 12 (TrendCard가 RunBodyCard 기본값을 씀, :94) | 다름 |
| 변화 칩 | trending_down 16 + 간격 3 + "14초 단축" 13 Bold, 모서리 999 | 아이콘 없음, 모서리 14 (:158-168) | 누락(아이콘)·다름 |
| 직전 값 | "직전 10회" 12 Medium + "6′02″" 22 Bold, 자간 -0.22, textSecondary | 캡션 14 + display(24) Barlow Italic (:171-179) | 다름 |
| 최근 값 | "5′48″" 40 Bold lh1.1, 자간 -0.8 + 간격 3 + "/km" 14 Medium(기준선 정렬) | display(44) Barlow Italic + 4 + Body 14, 아래 8 (:187-203) | 다름 |
| 공통 축 | 트랙 #E9EDE0 4, 모서리 2. 단축 구간은 primary 6이고 끝이 각짐. 직전 14원, 최근 20원 | 트랙 Border 4, 둥근 끝. 구간 둥근 끝. 직전 반지름 6, 최근 반지름 8 + 테 9 (:213-229) | 다름(근사) |
| 축 눈금 글자 | 11 Medium, 축과 간격 12 | Caption 12, 간격 8 (:218, 231-239) | 다름 |
| 비교 줄 | 원 36(#E3DFF9) + 아이콘 20 + 간격 12. 이름 15 Medium, 값 17 Bold, 설명 12 Bold | 원 36(Lavender 0.3), 아이콘 18. 이름 Body 14, 값 18 Black (:300-320) | 다름 |
| 빈도 아이콘 | Material today | Calendar 선 아이콘 (:281, 287) | 근사 |
| 나의 러닝 유형 | 바탕 #EBE7FC, 모서리 16. 흰 원(안쪽 2) 안에 44 그림 "그림 / face". 12 Medium / 17 Bold | 바탕 Lavender 0.3, 모서리 20. 러니멀 그림 48. Caption / 18 Black (:324-354, 387) | 다름. 그림이 아바타인지 러니멀인지는 확인 못 함 |
| 주간 러닝 기록: 머리 | "주간 러닝 기록" 18 Bold + ← 48 / "10월 3주" 14 Bold / → 48(묶음 폭 176) | 811eef40에서 맞춤. 남은 차이: 화살표가 꺾쇠 Back·ChevronRight (RunBodySummaryCards.kt:75-92) | 다름(아이콘) |
| 주간 러닝 기록: 합계·칩 | "18.4" 40 Bold + "km" 15 Medium. 칩 바탕 surfaceHigh #E9EDE0 | 811eef40에서 맞춤. 남은 차이: 칩 바탕 Border #E4E4E7 (RunBodySummaryCards.kt:145-157) | 다름(색) |
| 주간 러닝 기록: 칩 아이콘 | directions_walk, schedule | Walk, Clock (RunBodySummaryCards.kt:132-133) | 근사 |
| 주간 러닝 기록: 요일 원 | 달린 날 primary + 12 Bold. 쉰 날 surfaceHigh #E9EDE0 | 811eef40에서 맞춤. 남은 차이: 쉰 날 Border #E4E4E7 (RunBodySummaryCards.kt:179-189) | 다름(색) |
| 최근 10회 평균 | 타일 2개(모서리 20, 안쪽 16, 간격 6), "Icon / 거리"·favorite 18, 26 Bold + 13 Medium | 811eef40에서 맞춤 (RunBodySummaryCards.kt:196-237). 남은 차이: 타일 테두리 1dp, 거리 아이콘은 Route(사용자 정의 그림과 다를 수 있음), 심박은 Heart(선) | 다름(테두리·아이콘 근사) |
| 러닝 코멘트 | 모서리 20, 안쪽 16, 간격 8. 말풍선 18 + 13 Bold 보라. 본문 14 Regular lh21 textPrimary | 811eef40에서 맞춤 (RunBodySummaryCards.kt:239-256). 남은 차이: 테두리 1dp | 앱에만 있음(테두리) |
| 10회 전 상태 | 피그마 프레임 없음 | "러닝을 N번 더 하면…" 카드 (RunBodySummaryTab.kt:87-91) | 앱에만 있음(FR-302 근거) |

## 4. G-33 런바디 · 요약 코멘트 준비 중 (61509:5366)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 뒤로 버튼 | 보라 채움 원(61641:5042), G-14와 다름 | 보라 원 | 같음 (피그마끼리 충돌) |
| 주간/월간 세그먼트 | 350x44. 바탕 #E9EDE0, 모서리 22, 안쪽 4. 고른 칸 흰색, 모서리 18, 높이 36, 14 Bold. 안 고른 칸 14 Regular. 둘 다 textPrimary | RunkkSegmentedTabs: 바탕 Background, 모서리 16. 고른 탭 라임 15 Black, 안 고른 탭 15 Normal TextSecondary, 칸 40 (RunkkChips.kt:330-370) | 다름 |
| 코멘트 준비 중 | "코멘트 준비 중" 14 Regular, **textPrimary** | 14 lh21, **TextSecondary** (RunBodySummaryCards.kt:250-254, strings:95) | 다름(색) |
| 나머지 카드 | 3절과 같음 | 3절과 같음 | 3절 참고 |

## 5. G-13 런바디 (2차: AI 코치 진입) · 러닝별 결과 (61502:3197)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 고른 기록 칸 | 모서리 12, 높이 64, 안쪽 16x8, 테두리 없음. 윗줄 12 Medium 자간 0.5. 아랫줄 "5.2km · 기록 선택 ▾" 한 덩어리, 16 Regular | 모서리 16, 테두리 1. 거리 16 Bold + 8 + "기록 선택" 16 + ChevronDown 16 (RunBodyRunTab.kt:65-90) | 다름(굵기·▾·모양). 가운뎃점은 16절 규칙 충돌 |
| 프로필 카드 안쪽 | 위·좌우 20, 아래 16 | 사방 20 | 다름 |
| 제목 "이번 러닝 프로필" | 20 Bold | 20 Black (RunBodyRunTab.kt:213) | 다름(굵기) |
| 레이더 상자 | 310x258.3, background, 모서리 20 | 높이 258, 모서리 20 (RunBodyRadar.kt:45-47) | 같음 |
| 오각형 | 외접 반지름 약 79.5, 중심 약 (155, 123.2) | 반지름 75, 중심 y 약 137(상자 가운데 + 8) (RunBodyRadar.kt:49, 145) | 다름 |
| 눈금·축 색 | divider #E9EDE0으로 추정(변수 목록 기준) | Border (RunBodyRadar.kt:42) | 다름. 선 굵기는 확인 못 함 |
| 점수 선·면 | 점수 있는 축끼리 선으로 잇고 면은 숨김. 색 #5C4BDB | 다섯 축이 다 있으면 면을 알파 0.16으로 채움 (RunBodyRadar.kt:59-73) | 앱에만 있음(면) |
| 점 | 지름 약 4 | 지름 6 (RunBodyRadar.kt:79) | 다름 |
| 축 이름·점수 | 12 Medium / 14 Bold | Caption 12 / 14 Bold (RunBodyRadar.kt:120-126) | 근사 |
| 점수 없는 축 | 축 묶음 전체 불투명도 0.35 | 알파 0.5 (RunBodyRadar.kt:115, 149) | 다름 |
| 축 라벨 위치 | 운동 강도: 위 가운데. 지구력: x 238~310, y 75.5. 심폐체력: x 0~71.5, y 75.5. 페이스 안정성: x 167~278, y 216.6. 러닝 폼: x 31.8~143, y 216.6 | 정렬과 여백으로 근사 (RunBodyRadar.kt:89-105) | 근사 |
| 안내 줄 | "4개 항목 분석 완료 · 러닝 폼 데이터 없음" | "…, 러닝 폼 데이터 없음" (strings:246) | 16절 규칙 충돌 |
| 피드백 줄 | 줄 높이 42, 간격 8. 원 36(#E3DFF9) + 간격 10 + 13 Medium. 값 17 Bold. 아이콘 20 | 간격 12, Body 14, 값 18 Black, 아이콘 18 (RunBodyParts.kt:76-96) | 다름 |
| 상세 버튼 | "구간·피드백 자세히 보기", 52 알약 | "구간과 피드백 자세히 보기", 56/16 (strings:254, RunBodyRunTab.kt:241-247) | 모양 다름. 문구는 16절 규칙 충돌 |

## 6. G-17 RunBody · 분석 대기 (61509:4801)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 제목 | "분석을 준비하고 있어요" 16 Bold | 16 Black (RunBodyRunTab.kt:100-103) | 다름(굵기) |
| 본문 2줄 | "11월 2일 아침 러닝 5.2km", "저장된 러닝은 자동으로 분석돼요" 14 lh21 | 같은 문구 (strings:257-258) | 같음 |
| "다시 확인" 버튼 | 없음 | RunkkSecondaryButton (RunBodyRunTab.kt:115, 163-170) | 앱에만 있음 (16절) |

## 7. G-13 · 분석 중 상태 (G-18 통합) (61509:4851)

메모(61570:7759) 원문: "G-13 동일 화면의 분석 중 상태. 별도 창이나 모달을 열지 않음. 탭·선택한 기록·오각형 프로필·AI 코치 위치 유지. 분석 중에는 점수를 표시하지 않고 상세보기 비활성. 완료 시 같은 영역에 결과 반영. 이 프레임은 상태 명세용이며 별도 제품 화면이 아님."

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 가운데 글자 | "분석 중이에요" 17 Bold lh24 | BodyStrong 14 (RunBodyRadar.kt:81-88) | 다름 |
| 축 이름 | 4축은 보통 색, 러닝 폼 묶음만 0.35 | 다섯 축 모두 알파 0.5 | 다름 |
| 축 값 "—" | 14 Bold textPrimary, 불투명도 0.45 | 알파 0.5 TextSecondary | 다름 |
| 피드백 값 "분석 중" | 17 Bold textPrimary, 불투명도 0.45 | 18 Black TextSecondary (RunBodyRunTab.kt:179-180, RunBodyParts.kt:91) | 다름 |
| "다시 확인" | 없음 | 카드 안에 있음 (RunBodyRunTab.kt:181) | 앱에만 있음 |
| 상세 버튼 | "분석 완료 후 상세보기", Gray Large 52 알약 | 라임 비활성 56/16 (RunBodyRunTab.kt:183-188) | 다름 |
| 안내 줄 | "러닝 기록을 분석 중이에요. 완료되면 결과가 표시돼요." | 같음 (strings:253) | 같음 |

## 8. G-19 RunBody · 분석 실패 (61509:4895)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 제목·본문 | "분석하지 못했어요" 16 Bold, 두 줄 본문 | 같은 문구, 제목 Black (RunBodyRunTab.kt:120-129, strings:264-267) | 다름(굵기) |
| 버튼 | "상태 다시 확인" 52 알약 | 56/16 | 다름 |
| 일시 장애·출생연도 없음 | 피그마 프레임 없음 | 카드 있음 (RunBodyRunTab.kt:132-156) | 앱에만 있음(유지) |

## 9. G-21 · 일부 항목 분석 (61509:4945)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 제목 | "분석 가능한 러닝 프로필" 20 Bold | 20 Black (RunBodyRunTab.kt:203) | 다름(굵기) |
| 레이더 | 점 하나, 선 없음, 나머지 축 0.35 | 점 하나, 나머지 축 0.5 | 다름(불투명도) |
| 안내 줄 | "페이스 안정성 1개 항목 분석 완료" | 같음 (strings:247) | 같음 |
| 상세 버튼 | 52 알약 | 56/16 | 다름 |

## 10. G-31 · 분석 데이터 부족 (61509:5012)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 제목·안내 | "분석할 데이터가 부족해요" 20 Bold / "다른 러닝을 선택해 분석 결과를 확인해 보세요." | 같은 문구, 제목 Black (RunBodyRunTab.kt:230-238, strings:243-244) | 다름(굵기) |
| 레이더 | 다섯 축 모두 흐림 0.35 | 0.5 | 다름 |
| 상세 버튼 | 있음 | 404 기록(NotAnalyzable)에서는 숨김 (RunBodyRunTab.kt:144) | 다름 (16절) |

## 11. G-30 · 러닝 선택 (61509:4753)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 목록 칸 | 모서리 20, 높이 96, 안쪽 16x10, 테두리 없음, 칸 간격 16 | 모서리 24, 안쪽 16x20, 테두리 있음(고른 칸은 라임) (RunBodyPickPage.kt:37-43) | 다름 |
| 날짜 | 16 Medium | 16 Bold (RunBodyPickPage.kt:46-50) | 다름 |
| 아랫줄 | 14 Regular, "5.2km · 선택됨" | Label 13, "5.2km, 선택됨" (RunBodyPickPage.kt:51-58, strings:196-197) | 크기 다름. 기호는 16절 규칙 충돌 |
| 점수 | "러닝 점수" 12 Regular / "78 · ▲ 6" 16 Bold 한 덩어리, 칸 폭 84 | Caption / "78" 17 Black + "▲ 6" 14 Bold Violet (RunBodyPickPage.kt:70-108) | 다름 |

## 12. G-20 · 러닝 분석 상세 (61502:3302)

구간 기록을 안내 문구 + 한 줄 형식으로 바꾼 것, "마지막 0.2 km", 비교 "5.2 km / 5.0 km", 피드백·코칭 라벨을 본문과 한 줄로 합친 것은 811eef40에서 맞췄다.

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 기록 줄 | "11월 2일 오전 7:10 · 5.2km" 13 Regular | "…, 5.2km" Label 13 (RunBodyDetailPage.kt:30-38, strings:283) | 16절 규칙 충돌 |
| 요약·구간·비교 줄 이름 | 13 Medium textSecondary, 값 17 Bold, 기준선 정렬 | RunBodyValueRow: Body 14 Normal, 가운데 정렬 (RunBodyParts.kt:58-64) | 다름 |
| 활동 시간 | "00:30:10" | 1시간 미만이면 "30:10" (RunBodyDetailPage.kt:58, RunkkFormat.kt:38-47) | 다름 |
| 구간 값 | "5′42″/km · 148 bpm" | "5'42\"/km, 148 bpm" (RunBodyDetailPage.kt:86-99, strings:295) | 기호 다름. 가운뎃점은 16절 규칙 충돌 |
| 피드백 판정 | 14 Regular textSecondary | BodyStrong textPrimary (RunBodyDetailPage.kt:109) | 다름 |
| 피드백·코칭 항목 | 한 줄 전체가 14 Regular textSecondary("좋았던 점 · …") | 라벨만 보라 Bold, 본문은 textPrimary (RunBodyDetailPage.kt:155-169) | 다름 |
| 코칭 항목 제목 | "초반 페이스 조절" 14 Regular textSecondary | BodyStrong textPrimary (RunBodyDetailPage.kt:121) | 다름 |
| 코칭 카드 바탕 | #EBE7FC | 흰색 + 테두리 (RunBodyDetailPage.kt:119) | 다름 |
| 비교 페이스 | "5′48″ / 6′02″" | 기호만 다름 (RunBodyDetailPage.kt:148-151) | 다름(기호) |

## 13. G-22 · 월간 (61509:5079)

주간 거리 막대(높이 6, 5km 단위 축)와 부하 거리 "18.4 km"는 811eef40에서 맞췄다.

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 달 이동 | "‹  2026년 10월  ›" 글자 하나, 16 Bold, 왼쪽 정렬, 높이 24 | 40dp 아이콘 버튼 2개 + 17 Black (RunBodyMonthTab.kt:78-111) | 다름 |
| 월간 분석 카드 간격 | 14 | 12 (RunBodyMonthTab.kt:117) | 다름 |
| 제목 | 20 Bold | 20 Black | 다름(굵기) |
| 캡션 | "선택한 달의 평균 분석 점수 · 100점 만점" | "…, 100점 만점" (strings:318) | 16절 규칙 충돌 |
| 축 줄 | 13 Medium / 17 Bold. 막대 6, 모서리 3, 트랙 #E9EDE0, 이름과 막대 간격 0 | Label 13 / 17 Black. 막대 기본 8, 트랙 Border, 간격 4 (RunBodyMonthTab.kt:120-137) | 다름 |
| 축 막대 색 | 가장 높은 축 primary, 나머지 secondary | 같음 (:136) | 같음 |
| 기준선 캡션 | "분석된 러닝 20회 · 최근 최대 10회 평균" | "…20회, …" (strings:325) | 16절 규칙 충돌 |
| 부하 비율 | "1.14 · 적정" | "1.14 적정" (strings:332) | 16절 규칙 충돌 |
| 주간 거리 막대 색 | 가장 긴 주만 primary, 나머지 secondary | 모두 Violet (RunBodyMonthTab.kt:300-305) | 다름 |
| 주간 거리 트랙 | #E9EDE0 | Border(RunkkProgressBar 기본) | 다름 |
| 주간 거리 줄 배치 | 막대 폭 220, 값 칸 54 | 막대는 남은 폭, 값 칸 72 (RunBodyMonthTab.kt:293-313, 329) | 다름(근사) |
| 개인 최고 | 같음 | 같음 | 같음 |

## 14. G-23 · 기준선·부하 준비 중 (61509:5175)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 기준선 준비 중 | "분석된 러닝 2/3회 · 1회가 더 필요해요." | "…2/3회, 1회가…" (strings:327) | 16절 규칙 충돌 |
| 부하 준비 중 3줄 | 문구 | 같음 (strings:337-339) | 같음 |

## 15. G-32 · 월간 분석 없음 (61509:5259)

빈 달 제목 두 줄은 811eef40에서 맞췄다.

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 빈 달 제목 | "이번 달의 러닝을 / 아직 분석하지 않았어요" 18 Bold | 같은 문구, Black (strings:320) | 다름(굵기) |
| 지난 달 제목 | 피그마 없음 | "이 달의 러닝을…"과 "이 달 개인 최고" (strings:321, 341, RunBodyMonthTab.kt:145-151) | 앱에만 있음 |
| "이전 달 보기" | 52 알약, 항상 표시 | 56/16. 첫 러닝 달이면 숨김 (RunBodyMonthTab.kt:62-65, 145-160) | 모양 다름. 숨김은 앱에만 있음 |
| 개인 최고·주간 거리 숨김 | 숨김 | 같음 | 같음 |

---

## 16. 충돌: 사용자 확인이 필요한 곳

1. **가운뎃점(·)**: 피그마는 화면 문구 곳곳에 " · "를 쓴다(기록 칸, 안내 줄, 구간 값, 캡션, 부하 비율, 점수 "78 · ▲ 6", "구간·피드백" 등). 반면 설계 규칙은 "화면 문구에 가운뎃점을 넣지 않는다"이다(`docs/sw/design-oo/05-19-설계-결정-v0.26.md:262`, `05-20-설계-결정-v0.27.md:185`). 811eef40도 이 규칙대로 쉼표나 띄어쓰기를 썼다. 앱을 피그마에 맞추려면 이 규칙을 바꿔야 한다.
2. **요약 주간/월간 토글**: G-14에는 없고, G-33·SRS FR-302(`docs/sw/srs/03-09-2차배포-런바디-측정-성.md:31`)·design-oo 05-25:133에는 있다.
3. **뒤로 버튼**: 대부분의 프레임은 원 없는 ←이고 G-33만 보라 원이다. 앱은 앱 전체 결정(R2-05·D85)으로 보라 원을 쓴다.
4. **빈 달 문구**: SRS FR-267(`docs/sw/srs/03-03-기록-분석.md:533`)은 "이번 달 분석된 러닝이 없어요"이고, 피그마와 앱은 "이번 달의 러닝을 아직 분석하지 않았어요"다.
5. **"다시 확인" 버튼(G-17·분석 중)**: 피그마에는 없다. 앱은 MR !91 e2e 4 결정으로 넣었다.
6. **G-31 상세 버튼**: 피그마는 버튼이 있다. 앱은 404 기록이면 상세가 없어서 숨긴다.
7. **"이번 주 요약"**: 811eef40에서 뺐다. SRS FR-067(`docs/sw/srs/03-03-기록-분석.md:227`)은 "코멘트가 아직 없으면 체력 요약만 보여 주…"라고 적고 있어, 문서를 고쳐야 할 수 있다.
8. **거리 띄어쓰기**: 피그마 안에서도 "5.2km"와 "5.2 km"가 섞여 있다. 앱은 811eef40에서 피그마의 카드별 표기를 그대로 따랐다.

## 17. 기능·데이터가 필요한 차이

811eef40에서 처리한 것
- 주간 러닝 기록: 클라이언트가 기록 목록을 주 단위로 집계한다(RunBodyWeek.kt). UiState에 week, weekRuns, weekError, canGoPrevious/NextWeek가 있고(RunBodyUiState.kt:39-63), 액션은 onPrevious/NextWeek, onRetryWeek다.
- 최근 10회 평균 거리: Trend Window에 averageDistance 필드를 추가했다. 서버 RunBodyDtos에도 있다.
- 월간·주간 이동 하한: Trend에 firstRunAt 필드를 추가했다.

남은 것

| 항목 | 화면 | 필요한 것 |
|---|---|---|
| 나의 러닝 유형 그림 | G-14·G-33 | 피그마는 "그림 / face"(사람 얼굴)다. 사용자 아바타라면 아바타 레이어 데이터를 런바디 상태에 넣어야 한다. 러니멀이라면 화면만 고치면 된다. 확인 못 함 |
| 주간/월간 토글 제거 여부 | G-14 vs G-33 | 없앤다면 RunBodyPeriod, onPeriod, UiState.period를 정리해야 한다(16절 2번 결정 뒤) |
| 활동 시간 "00:30:10" | G-20 | 형식만 바꾸면 된다(화면) |

## 18. 화면만 고치면 되는 차이

- **공통**
  - 상단바: 62, 19 Bold, 원 없는 ←
  - 좌우 여백 20
  - 카드 테두리 제거: 새 SummaryTile 포함
  - 제목·값 Black → Bold
  - 버튼: 52 알약, 비활성은 Gray
  - AI 코치를 Extended FAB으로
  - 페이스 기호 ′ ″
  - 색 토큰 #F7F8F2, #67705B, #E9EDE0, #E3DFF9, #EBE7FC (적용 범위는 결정 필요)
- **탭 줄**: M3 Segmented button 모양
- **G-14·G-33**
  - 최근 10회 변화: 카드 간격 18, 회차·변화 칩(모서리 999, trending_down 아이콘), 숫자 글꼴(22/40 Bold), 축 색, 비교 줄 글자, 러닝 유형 상자
  - 주간 기록: 화살표 ← →, 칩·쉰 날 색 #E9EDE0
  - G-33: 세그먼트 모양, 준비 중 글자색 textPrimary
- **G-13·G-21·G-31**
  - 고른 기록 칸 모양
  - 레이더: 반지름, 중심, 점 크기, 면 제거, 흐림 0.35
  - 피드백 줄 글자·간격
- **G-13 분석 중**: 가운데 17 Bold, 값 흐림 0.45, Gray 버튼
- **G-30**: 목록 칸 모양, 글자 크기, 점수 표시
- **G-20**
  - 줄 이름 13 Medium
  - 피드백·코칭 글자 14 Regular textSecondary
  - 코칭 카드 바탕 #EBE7FC
- **G-22·G-23·G-32**
  - 달 표시를 글자 하나로
  - 축 막대 6, 트랙 #E9EDE0, 간격 0
  - 주간 거리: 가장 긴 주만 라임, 트랙 색
  - 카드 간격 14

## 19. 피그마 에셋 중 앱에 없는 것

811eef40에서 Route(평균 거리용)와 ChatBubble(러닝 코멘트용)이 추가됐다(RunkkIcons.kt:109, 117).

| 에셋 | 쓰는 곳 | 앱 상태 |
|---|---|---|
| arrow_back(←) 24 | 상단바 뒤로, 주간 기록 이전 주 | 없음(Back은 꺾쇠) |
| arrow_forward(→) 24 | 주간 기록 다음 주 | ArrowRight(:22)는 있으나 주간 기록은 ChevronRight를 씀 |
| AI 코치 FAB 아이콘(채운 말풍선, 3b845.svg) 24 | FAB | 없음(ChatBubble과 같은 모양인지 확인 못 함) |
| trending_down(Icon / 빨라짐) 16 | 평균 페이스 변화 칩 | 없음 |
| Icon / 거리(6058b.svg) 18 | 평균 거리 | Route로 대체(같은 그림인지 확인 못 함) |
| today, favorite, directions_walk, schedule | 비교 줄, 평균 심박, 주간 칩 | Calendar, Heart, Walk, Clock으로 근사 |
| 그림 / face(ff1ce.png) | 나의 러닝 유형 | 러니멀 그림 사용, 같은 대상인지 확인 못 함 |
| IBM Plex Sans KR | 전체 | 없음 |
