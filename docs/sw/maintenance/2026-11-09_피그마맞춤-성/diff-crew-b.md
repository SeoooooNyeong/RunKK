# 크루 관리 화면 피그마 대비 차이 (F-17~F-29)

- 피그마: fileKey `NMTtVMWIT22D0CwOU2y0I9`, 대상 프레임 390x844
- 앱: develop 브랜치(d3b9d4dd), `android/feature/crew/src/main/java/com/a207/runkk/app/ui/crew/` (아래 파일명은 이 폴더 기준. core는 `android/core/ui/src/main/java/com/a207/runkk/app/core/designsystem/`)
- 차이 종류: **누락**(피그마에 있는데 앱에 없음) / **다름** / **앱에만 있음** / **화면 없음**
- 피그마 토큰(라이트): background #F7F8F2, surface #FFFFFF, surfaceHigh #E9EDE0, divider #E9EDE0, textPrimary #171B12, textSecondary #67705B, primary(라임) #CAEB2E, onPrimary #171B12, secondary(보라) #5C4BDB, onSecondary #FFFFFF, barCrew #BFC6B3, respChip(승인) #67705B, 메뉴 테두리 #C7CCBF, 딤 rgba(23,27,18,0.45). 글꼴 IBM Plex Sans KR.

---

## 0. 여러 화면에 공통으로 걸리는 차이 (먼저 고치면 아래 표 대부분이 줄어든다)

| 항목 | 피그마 | 앱(파일:줄) | 차이 |
|---|---|---|---|
| 상단 바 높이·제목 | 높이 57, 좌우 12, 제목 **가운데** 19 Bold(lh23) | `RunkkTopBar` 높이 64, 좌우 16, 제목 왼쪽 18 Black (core RunkkBars.kt:77-97, RunkkType.kt:78). 크루 관리 화면들은 `isTitleCentered` 안 씀 | 다름 |
| 뒤로 버튼 | `RunKK / Buttons / 뒤로가기`: 40x40, **배경 원 없음**, arrow_back(화살표 몸통 있는 Material 아이콘) 24, textPrimary | `RunkkBackButton` 라이트에서 보라(#5C4BDB) 채움 원 + 흰 셰브런(RunkkIcons.Back은 꺾쇠 `M15 18L9 12L15 6`) (RunkkBars.kt:108-119) | 다름 |
| 확인 대화상자 형태 | 카드 334폭(좌우 28), 모서리 28, 안쪽 위24·좌우20·아래20, 간격 10. 제목 18 Bold(lh26) textPrimary, 본문 14 Regular(lh21) **textPrimary**. 버튼 줄(위 여백 10): 왼쪽 "취소" 외곽선 알약(흰 바탕, divider 테두리, 높이 48, 15 SemiBold), 오른쪽 보라(#5C4BDB) 채움 알약 높이 48(흰 글자). 두 칸 간격 10 | `RunkkConfirmDialog` = Material `AlertDialog` + 글자 버튼 두 개(확인 글자가 라벤더/라임, 본문 TextSecondary) (core RunkkFields.kt:364-396). 이미 피그마 형태로 만든 `CrewQuitDialog`(CrewQuitDialog.kt:36-81, 모서리 24)가 있으니 공용화 대상 | 다름 |
| 안내 대화상자(버튼 하나·닫기 X) | 같은 카드에 라임 알약 하나(높이 48, 폭 가득) 또는 오른쪽 위 닫기 X(36 터치, 20 아이콘, 카드 기준 left 288·top 10) | `RunkkActionDialog`: 모서리 24, 아래 여백 16, 간격 12, 버튼 줄에 글자 버튼(dismissLabel) + 라임 알약, X 없음 (core RunkkActionDialog.kt:30-76) | 다름 |
| 색 토큰 | 위 피그마 토큰 | 앱 라이트 background #FAFAFA, border #E4E4E7, borderStrong #8A8A93, textPrimary #18181B, textSecondary #52525B, violet #5B4FD9, 라임 글자색 lime #3F6212(올리브) (core RunkkColors.kt:128-162) | 다름(전 앱 공통. 다른 담당과 함께 정할 일) |
| 글꼴 | IBM Plex Sans KR (Regular/Medium/SemiBold/Bold) | Noto Sans KR (400/500/700/900), 굵은 글자 대부분 Black(900) (core RunkkType.kt:16-26, 75-96) | 다름(공통) |
| 크루 기본 이미지 | `RunKK / Avatar / 크루 기본 이미지`: surfaceHigh 바탕 + Material **groups** 아이콘(textSecondary). Size 50(홈 머리글, 모서리 15, 아이콘 28) / 74(크루 정보, 모서리 24, 아이콘 40) / 66(수정, 모서리 ≈19.25~24) | `CrewLogo`: 이름 첫 글자, 모서리 size/4, isAccent면 primaryContainer 바탕+primary 글자 (CrewComponents.kt:40-57) | 다름 |

---

## F-17 크루 홈·크루원 메뉴 (61772:5250) / F-24 크루 홈·크루장 메뉴 (61773:5665)

두 프레임의 메뉴는 **완전히 같다**(크루 정보, 크루원 두 줄). 크루장 분기가 없다.

| 항목 | 피그마 | 앱(파일:줄) | 차이 |
|---|---|---|---|
| 메뉴 항목 | "크루 정보", "크루원" 두 개(크루원·크루장 동일) | "크루 정보", "크루원", 크루장·운영진이면 "크루 관리" 추가 (CrewHomeScreens.kt:400-405) | 앱에만 있음("크루 관리") |
| 메뉴 형태 | 딤 rgba(23,27,18,0.45)가 화면 전체를 덮고, 그 위 카드: 폭 176, 화면 기준 left 194·top 62(오른쪽 여백 20), 흰 바탕, 테두리 1 #C7CCBF, 모서리 16, 그림자 0 4 12 rgba(0,0,0,0.18), 위아래 안쪽 6 | Material `DropdownMenu` + `DropdownMenuItem`(기본 모양·그림자·위치), 딤 없음 (BoardParts.kt:62-93) | 다름 |
| 메뉴 줄 | 좌우 16·위아래 13, 15 Regular(lh18) textPrimary, 첫 줄 아래 divider 1 #E9EDE0 | `RunkkTextStyles.Body`(14) onSurface, 구분선 없음 (BoardParts.kt:84-90) | 다름 |
| ⋯ 단추 | `RunKK / Buttons / 아이콘` Medium44: 44x44, Material more_horiz 24, textPrimary | 48x48, Canvas 점 셋(18x4) onSurfaceVariant (BoardParts.kt:67-78) | 다름 |
| 머리글(메뉴 뒤 배경) | 위 12·좌우 20, 간격 12, 아바타 50(groups), 크루명 17 Bold(lh20), 뒤로 버튼 없음 | 위 8·좌 16(또는 8)·우 8·아래 12, CrewLogo 48(첫 글자), TopBarTitle 18 Black·최대 2줄, 탭 자리 아니면 뒤로 버튼 (CrewHomeScreens.kt:367-397) | 다름(크루 홈 담당과 겹침) |

---

## F-18 크루 정보(내 크루, 크루원) (61772:5391) / F-25 크루 정보(크루장) (61773:5806)

공통 부분(둘 다):

| 항목 | 피그마 | 앱(파일:줄) | 차이 |
|---|---|---|---|
| 상단 바 | "크루 정보" 가운데 19 Bold, 오른쪽 "초대 코드" 버튼: 흰 바탕, divider 테두리, 높이 36, 좌우 16, 완전 둥근(100), 14 Medium, 오른쪽 여백 19 | RunkkTopBar(제목 왼쪽) + `RunkkPillButton`(높이 40, 모서리 20, 테두리 borderStrong #8A8A93, 15 Bold) (CrewInfoPage.kt:50-58, core RunkkButtons.kt:239-280) | 다름 |
| 본문 여백 | 위 10·좌우 20·아래 16, 항목 간격 14 | padding 16, 간격 16 (CrewInfoPage.kt:59-62) | 다름 |
| 로고 | 74 기본 이미지(groups, 모서리 24) | CrewLogo 72 첫 글자 (CrewInfoPage.kt:68) | 다름 |
| 로고·이름 간격 | 14 | 16 (CrewInfoPage.kt:67) | 다름 |
| 크루명 | 19 Bold(lh23) | TopBarTitle 18 Black (CrewInfoPage.kt:70-75) | 다름 |
| 이름 아래 줄 | "[yyyy. mm. dd] · 7명" 13 Regular textPrimary(만든 날 · 인원, 정원 없음) | "7/50명" Caption 12 onSurfaceVariant (CrewInfoPage.kt:76-80, strings `crew_meta_headcount`) | 다름 + 누락(만든 날) |
| 한 줄 소개 | 16 Regular(lh22.4) textPrimary, 좌우 안쪽 5 | Body 14 (CrewInfoPage.kt:83-85) | 다름 |
| 태그 | `RunKK / Tag / 태그` Medium: 높이 28, 좌우 14, 모서리 14, 14 Medium. 평일=보라 틴트(바탕 #EBE7FC, 글자 #5C4BDB), 주말=라임 틴트(바탕 #F2F9D0, 글자 #3D4A00). 간격 8 | `RunkkBadge`: 높이 28, 모서리 8, 12 글자. 평일=Neutral(흰 바탕·검은 글자), 주말=Lime(LimeContainer·Bold) (CrewComponents.kt:191-205, core RunkkChips.kt:303-320) | 다름 |
| 통계 두 칸 | "누적 거리"(예 1,284.67km) · "누적 크루런 횟수"(예 [n]회). 칸: 흰 바탕, divider 테두리, 모서리 24, 안쪽 14, 간격 6, 이름 12 Regular textPrimary, 값 20 Bold(lh24). 두 칸 간격 10 | "이번 주 달린 거리" · "지난 주 달린 거리" (CrewInfoPage.kt:87-98). CrewStatCard: 이름 Caption onSurfaceVariant, 값 20 Black, CrewCard(모서리 20, 안쪽 16) (CrewComponents.kt:132-142), 간격 12 | 다름(항목 자체가 다름. 데이터 필요) |
| 지난 기록 메뉴 | "지난 크루런 ›", "지난 목표 ›" 두 줄: 높이 52, 아래 divider, 15 Regular textPrimary, "›" 18 textSecondary 오른쪽 끝 | 없음 | 누락(→ F-20, F-21로 이동) |
| 아래 링크 | "탈퇴하기" 13 Regular textSecondary **밑줄**, 안쪽 좌우 8·위아래 6, 화면 아래(본문 top 792) 가운데 | `RunkkTextLink`: Caption 12, **라임 글자(#3F6212)**, 밑줄 없음, 아래 여백 24 (CrewInfoPage.kt:100-104, core RunkkButtons.kt:487-511) | 다름 |
| 오류·빈 상태 | 피그마에 없음 | `RunkkErrorState` (CrewInfoPage.kt:63-66) | 앱에만 있음(유지해도 됨) |

크루장(F-25)에서만 다른 부분:

| 항목 | 피그마 | 앱(파일:줄) | 차이 |
|---|---|---|---|
| "수정" 링크 | 로고 줄 오른쪽(줄 기준 left 320·top 10.5, 높이 40), 13 Regular textSecondary 밑줄. 누르면 F-26 | 크루 정보에는 없음. ⋯ 메뉴 "크루 관리" → 크루 관리 화면 → "크루 정보 수정 ›" 줄 (CrewManageScreens.kt:170-175) | 누락(위치 다름) |
| 가입 신청 구역 | 지난 기록 메뉴 아래 "가입 신청 2건"(가입 신청 15 Medium + " 2건" 13 Regular, lh22.5), 그 아래 신청 카드 목록(간격 8) | 별도 "크루 관리" 화면의 `ManageRequests`: "가입 신청" CardTitle 16 Black + 개수 primary색 (CrewManageScreens.kt:238-263, 501-513) | 다름(화면 위치·문구 "N건") |
| 신청 카드 | 카드: divider 테두리, 모서리 16, 최소 높이 52, 좌우 20·위아래 14, 간격 10. 왼쪽 38x38 이미지(모서리 8, divider 테두리), 이름 14 Regular(lh21), 아래 줄 12 Regular "골드 · Lv23 · 20대"(F-25b·F-28에서는 "골드 · Lv23 · 20대 · 남성"처럼 성별까지 있는 것도 있음). 오른쪽 "거절"(외곽선, 높이 36, 폭 54, 모서리 16, 13 Regular) 다음 "승인"(채움 #67705B, 흰 13 Bold, 높이 36, 모서리 16) | `PersonRow`: 카드 테두리 없음, 40 원형 첫 글자, 이름 BodyStrong, 보조 줄 없음, 오른쪽 `RunkkPillButton` "승인" 다음 "거절" (순서 반대) (CrewManageScreens.kt:247-262, 286-316) | 다름 + 누락(티어·레벨·나이·성별 줄) |
| 빈 신청 | 확인 못 함(피그마는 2건 상태만) | "대기 중인 가입 신청이 없어요" (CrewManageScreens.kt:240-246) | 확인 못 함 |
| 아래 링크 둘 | "크루 삭제하기"(왼쪽, 본문 left 107.5) · "탈퇴하기"(오른쪽, left 218.5), 둘 다 13 Regular textSecondary 밑줄, 사이 22 | 탈퇴하기 링크만(CrewInfoPage.kt:100-104). 크루 삭제는 크루 관리 화면 맨 아래 "크루 삭제" BodyStrong secondary색 (CrewManageScreens.kt:176, 347-359) | 누락(위치·문구 다름) |
| 초대 코드 재발급·사용 기한 | 피그마 크루 정보·팝업 어디에도 없음 | 크루 관리 화면: 코드 32 display primary색, 복사, "M.d까지 사용할 수 있어요", "새로 발급" (CrewManageScreens.kt:183-234) | 앱에만 있음(SRS FR-114 충돌, 아래 "문서 충돌") |
| 운영진(MANAGER) 화면 | 피그마에 운영진 변형 없음 | 운영진에게도 ⋯ "크루 관리" 노출, 가입 신청·공동 목표 설정 줄을 봄 (CrewHomeUiState.kt:61, CrewManageScreens.kt:160-169) | 앱에만 있음(문서 충돌) |

---

## F-18b 초대 코드 팝업(크루원) (61772:5470) / F-25b (크루장) (61773:5924)

두 팝업은 **같다**(크루장도 재발급 없음). 배경만 F-18/F-25.

| 항목 | 피그마 | 앱(파일:줄) | 차이 |
|---|---|---|---|
| 딤 | rgba(23,27,18,0.45), 카드 세로 가운데 | Compose `Dialog` 기본 딤 | 다름(색) |
| 카드 | 폭 334(좌우 28), 흰 바탕, 모서리 28, 안쪽 위24·좌우20·아래20, 간격 10 | RunkkActionDialog: 모서리 24, 아래 16, 간격 12 (core RunkkActionDialog.kt:41-50) | 다름 |
| 제목 | "초대 코드" 18 Bold(lh26) | CardTitle 16 Black (core RunkkActionDialog.kt:51-56) | 다름 |
| 코드 칸 | surfaceHigh(#E9EDE0) 바탕, 높이 56, 모서리 14, 가운데 "RK7Q2M" 24 Bold, 자간 4 | `background`색 바탕, 위아래 16, RunkkShapes.Medium(16), display(28) Barlow, 글자 사이 공백 문자로 띄움("R K 7 Q 2 M") (CrewInfoPage.kt:150-161) | 다름 |
| 버튼 | "복사" 라임 알약 하나, 폭 가득, 높이 48, 15 SemiBold, 위 여백 10 | 왼쪽 글자 버튼 "닫기" + 오른쪽 라임 알약 "복사" (반반) (CrewInfoPage.kt:138-149) | 다름 + 앱에만 있음("닫기" 글자 버튼) |
| 닫기 X | 카드 오른쪽 위(left 288·top 10) 36x36, X 아이콘 20 | 없음 | 누락 |

---

## F-19 크루 탈퇴 확인·크루원 (61772:5567)

| 항목 | 피그마 | 앱(파일:줄) | 차이 |
|---|---|---|---|
| 형태 | 0장 "확인 대화상자 형태"(카드 334, 모서리 28, 외곽선 취소 + 보라 채움) | RunkkConfirmDialog(AlertDialog, 글자 버튼) (CrewHomeScreens.kt:280-289) | 다름 |
| 제목 | "크루를 탈퇴할까요?" | 같음 (strings `crew_home_leave_title`) | 같음 |
| 본문 | "탈퇴 후 24시간 동안 다른 크루에 가입할 수 없어요." | "탈퇴하면 24시간 동안 다른 크루에 가입할 수 없어요." (`crew_home_leave_message`) | 다름(문구) |
| 왼쪽 버튼 | "취소" 외곽선 폭 149 높이 48, 15 SemiBold | 글자 버튼 "취소" | 다름 |
| 오른쪽 버튼 | "탈퇴하기" 보라 #5C4BDB 채움, 높이 48, 모서리 24, 16 Medium 흰 글자 | 글자 버튼 라벤더 "탈퇴하기" | 다름 |

## F-28 크루 탈퇴·크루장 양도 안내 (61773:6307)

| 항목 | 피그마 | 앱(파일:줄) | 차이 |
|---|---|---|---|
| 제목 | "지금은 크루를 탈퇴할 수 없어요" | 같음 (`crew_home_leave_blocked_title`) | 같음 |
| 본문 | "다른 크루원에게 크루장을 넘긴 뒤 탈퇴할 수 있어요." | "크루원이 남아 있는 크루의 크루장은 다른 크루원에게 크루장을 넘긴 뒤 탈퇴할 수 있어요." (`crew_home_leave_blocked_message`) | 다름(문구) |
| 버튼 | 라임 알약 **하나** "닫기"(폭 가득, 높이 48, 15 SemiBold) | 확인 "크루장 넘기기"(누르면 크루 관리로 이동) + 취소 "닫기" (CrewHomeScreens.kt:290-302) | 다름 + 앱에만 있음("크루장 넘기기") |
| 형태 | 카드 334, 모서리 28 | AlertDialog | 다름 |
| 표시 조건 | 크루장 + 다른 크루원 있음(배경 F-25) | `isLeader && headcount > 1` (CrewHomeViewModel.kt:296-304) | 같음 |

---

## F-20 지난 크루런·달력 (61772:5650) / F-20b 기간 선택 (61773:4748) / F-20c 날짜 선택 (61773:4979)

앱: **화면 없음.** 지난 크루런은 크루 홈 "크루런" 탭 목록 맨 아래 묶음과 위쪽 "지난 크루런 ›" 링크(목록을 그 머리글로 스크롤)로만 있다 (CrewScheduleTab.kt:74-104, 156-167, 589-636, CrewHomeScreens.kt:200-203).

| 항목 | 피그마 | 앱(파일:줄) | 차이 |
|---|---|---|---|
| 진입 | 크루 정보(F-18·F-25) "지난 크루런 ›" | 크루런 탭 "지난 크루런 ›" | 화면 없음 |
| 상단 바 | "지난 크루런" 가운데, 뒤로 | — | 화면 없음 |
| 월 이동 줄 | 본문 위 11·좌우 20. 양쪽 `RunKK / Calendar / 이동 화살표` 32x32("‹" "›" 28 Regular; 이번 달이면 "›" 비활성 textSecondary), 가운데 "2026년 10월" 16 Bold + 아래 꺾쇠(8x4) — 누르면 F-20b | — | 화면 없음 |
| 달력 카드 | 흰 바탕, 모서리 16, 줄 간격 4. 요일 줄 "일 월 화 수 목 금 토" 14 Regular textSecondary(높이 36). 날짜 칸 50x36, 14 Regular textPrimary(오늘 뒤 날짜는 textSecondary). 크루런 있는 날은 숫자 아래 점 4x4(칸 기준 left 23·top 30, 검정). 오늘은 지름 36 원(#E9EDE0, `RunKK / Calendar / 오늘 표시`) | — | 화면 없음 |
| 선택 날짜(F-20c) | 고른 날짜에 지름 36 라임(#CAEB2E) 원, 목록 제목 "10월 13일 크루런 1", 목록은 그날 것만 | — | 화면 없음 |
| 목록 제목 | "10월 크루런 10"(15 Bold + 13 Regular 개수), 높이 30 | 탭 안 "지난 크루런" CardTitle (CrewScheduleTab.kt:94-102) | 다름 |
| 목록 줄 | 높이 58, 아래 divider, 아래 안쪽 6, 제목 16 Regular(lh19), 그 아래(간격 4) 12 Regular "10.20(화) 19:00 · 5km · 6명"(거리 없으면 "제한 없음", 하프는 "하프"). 오른쪽 35x16 빈 칸 | `PastSchedule`: CrewCard(테두리 카드), 제목 BodyStrong, 보조 "크루런, 10.14, 5km, 6명" Caption(쉼표 구분, 요일·시각 없음, 취소면 "취소됨"), 끝난 것만 › (CrewScheduleTab.kt:589-636) | 다름 |
| 스크롤 막대 | 오른쪽 4폭 surfaceHigh 막대 표시 | — | 확인 못 함(기본 스크롤 표시 여부) |
| F-20b 기간 선택 시트 | 딤 위 바텀시트(위 모서리 28, 안쪽 위24·좌우20·아래32, 간격 16). 머리 "기간 선택" 18 Bold + 오른쪽 "✕" 18. 휠 2열(년·월, 높이 180, 열 간격 8, 줄 높이 36): 가운데 선택 줄 18 Bold + 뒤 선택 막대(surfaceHigh, 높이 36, 모서리 12), ±1 줄 16 Regular 불투명 45%, ±2 줄 20%. 아래 "적용" 라임 알약 높이 48 | 없음 | 화면 없음 |

## F-21 지난 목표 (61773:5110)

앱: **화면 없음.** (공동 목표는 레이스 탭 이번 주만, `CrewGoalSetScreen`의 "지난 주 결과"만 있음, strings `crew_goal_set_last_week`)

| 항목 | 피그마 | 앱 | 차이 |
|---|---|---|---|
| 상단 바 | "지난 목표" 가운데, 뒤로 | — | 화면 없음 |
| 목록 | 본문 위 11·좌우 20, 카드 간격 12, 최신 주부터 | — | 화면 없음 |
| 카드 | 바탕 없음(배경색 그대로), divider 테두리, 모서리 28, 안쪽 14, 간격 8 | — | 화면 없음 |
| 카드 1줄 | 왼쪽 "10월 3주차" 13 Regular, 오른쪽 "달성"/"달성 실패" 12 Bold textPrimary(색 구분 없음) | — | 화면 없음 |
| 카드 2줄 | "72.0" 22 Bold(lh26) + "/ 80 km" 13 Regular(소수 첫째 자리) + 오른쪽 "90%" 15 Bold | — | 화면 없음 |
| 막대 | 트랙 높이 8 surfaceHigh 모서리 4, 채움 `RunKK / Race / 막대 채움` Style=Crew(#BFC6B3), 100% 넘으면 가득 | — | 화면 없음 |
| 카드 3줄 | "참여 6명" 12 Regular | — | 화면 없음 |

---

## F-22 크루원 목록(크루원) (61773:5263) / F-29 크루원 목록(크루장) (61773:6440)

공통:

| 항목 | 피그마 | 앱(파일:줄) | 차이 |
|---|---|---|---|
| 상단 바 | "크루원" 가운데, 뒤로 | RunkkTopBar 제목 왼쪽 (CrewInfoPage.kt:113-116) | 다름 |
| 하단 내비 | 플로팅 내비(크루 탭 선택) 보임 | 확인 못 함(크루 홈 안 쪽 화면이라 탭 자리면 보일 것으로 추정) | 확인 못 함 |
| 본문 여백 | 위 11·좌우 20·아래 16, 간격 10 | padding 16, 간격 16 (CrewInfoPage.kt:117-120) | 다름 |
| 내 정보 카드 | 흰 바탕, divider 테두리, 모서리 24, 안쪽 14, 간격 2. 1줄: "내 정보" 14 Bold + 오른쪽 "편집" 버튼(흰 바탕, divider 테두리, 높이 30, 좌우 12, 모서리 18, 13 Regular). 2줄: "[티어] · [레벨] · [나이]" 14 Regular(lh24) (F-29는 "· [성별]"까지) | CrewCard: "크루에서 보이는 내 정보" Caption + RunkkPillButton "편집"(높이 40), 2줄은 **내 한줄 소개**(없으면 "한줄 소개가 없어요") (CrewMembersTab.kt:66-85) | 다름(제목 문구·2줄 내용) |
| 목록 머리 | "크루원 7" 15 Bold(lh18) + 오른쪽 "누적 거리" 12 Regular textPrimary, 기준선 맞춤 | "크루원 7" CardTitle 16 Black + "이번 주 거리" Caption onSurfaceVariant (CrewMembersTab.kt:47-59) | 다름(문구·크기) |
| 크루원 줄 | 최소 높이 53, 아래 divider 1, 간격 12. 아바타 36 원(surfaceHigh, 글자 없음). 이름 14 Regular(나는 "나" 14 Bold). 역할 배지는 **크루장만**: 흰 바탕, divider 테두리, 높이 20, 좌우 8, 모서리 16, 11 Regular. 보조 줄 없음. 오른쪽 누적 거리 14 Bold("512.39km") | CrewLogo 40 원 첫 글자(나는 accent), 이름 BodyStrong 14 Bold 전원, 크루장(Lime)·운영진(Muted) `RunkkBadge`(높이 28, 모서리 8, 12), 보조 줄 "티어, 한줄 소개", 오른쪽 **이번 주** 거리 14 Bold, 구분선 없음 (CrewMembersTab.kt:92-139) | 다름 + 앱에만 있음(운영진 배지·보조 줄) |
| 정렬 | 누적 거리 내림차순으로 보임 | 서버 순서 그대로 (CrewMembersTab.kt:60-62) | 다름(확인 필요) |
| 줄 누름 | 크루원 정보 팝업(F-22b·F-29b) | 다른 크루원은 프로필 화면(F8, Routes.MemberProfile)으로 이동, 내 줄은 안 눌림 (CrewMembersTab.kt:96-108, CrewHomeScreens.kt:114) | 다름 |

크루장(F-29)에서만 다른 부분:

| 항목 | 피그마 | 앱(파일:줄) | 차이 |
|---|---|---|---|
| 목록 첫 줄 | "나" + "크루장" 배지 | 크루원 탭 목록은 동일 컴포넌트. 크루 관리 화면 목록은 "나 (크루장)" 문구 (CrewManageScreens.kt:270-272) | 다름 |
| 줄 끝 ⋯ | 없음(줄 누르면 F-29b) | 크루 관리 화면 목록에만 ⋯(`CrewMoreButton` 48, 점 4dp) → 바텀시트 (CrewManageScreens.kt:266-282, CrewMoreButton.kt:22-41) | 앱에만 있음 |
| 내 정보 2줄 | "[티어] · [레벨] · [나이] · [성별]" (F-22는 성별 없음) | 한줄 소개 | 다름(피그마 안에서도 F-22/F-29 불일치) |

## F-22b 크루원 정보 팝업 (61773:5372) / F-29b 크루원 정보 팝업(크루장) (61773:6549)

앱: **화면 없음**(팝업 대신 프로필 화면 이동, 크루장은 크루 관리 화면의 바텀시트 `MemberMenuSheet`).

| 항목 | 피그마 | 앱(파일:줄) | 차이 |
|---|---|---|---|
| 카드 | 폭 310(좌우 40), 흰 바탕, divider 테두리, 모서리 24, 안쪽 위28·좌우20·아래20, 간격 16, 가운데 정렬, 세로 가운데 | 바텀시트(ModalBottomSheet), 좌우 20·아래 28 (CrewManageScreens.kt:362-421) | 화면 없음 / 다름 |
| 닫기 X | 오른쪽 위(left 263~264·top 9~10) 36x36, X 20 | 시트 맨 아래 "닫기" RunkkSecondaryButton 48 (CrewManageScreens.kt:413-418) | 다름 |
| 프로필 사진 | 80 원, surfaceHigh 바탕, divider **점선** 테두리(자리 표시 "[아바타]") | CrewLogo 40 원 첫 글자 (CrewManageScreens.kt:377) | 다름 |
| 닉네임 | 18 Bold(lh22), 가운데 | CardTitle 16 Black, 왼쪽 (CrewManageScreens.kt:379-384) | 다름 |
| 역할 배지 | F-22b(대상이 크루장)에서 닉네임 아래 "크루장" 배지(목록 배지와 같은 모양). F-29b(대상이 일반 크루원)는 배지 없음 | 보조 줄 "티어, 역할" Caption (CrewManageScreens.kt:385-390) | 다름 |
| 정보 줄 5개 | 티어 / 레벨 / 나이 / 성별 / 가입일. 줄 간격 10, 왼쪽 이름 13 Regular textSecondary, 오른쪽 값 14 Bold textPrimary (예 "골드", "26", "20대", "비공개", "[yyyy. mm. dd]") | 없음 | 누락(데이터 필요) |
| 한 줄 소개 칸 | surfaceHigh 바탕, 모서리 14, 좌우 14·위아래 12, 간격 6: "한 줄 소개" 12 Regular textSecondary + 본문 15 Regular(lh21) | 없음 | 누락 |
| 크루장 행동(F-29b만) | 버튼 두 칸(간격 8, 높이 48, 15 SemiBold): 왼쪽 "크루장 넘기기" 외곽선(흰 바탕·divider 테두리) → F-29c, 오른쪽 "내보내기" 보라 #5C4BDB 채움 흰 글자 → F-29d | 시트 줄 4개: "프로필 보기", "운영진으로 지정/운영진 해제", "크루장 넘기기", "크루에서 내보내기"(secondary 글자) (CrewManageScreens.kt:393-412) | 다름 + 앱에만 있음("프로필 보기", 운영진 지정·해제) |
| 크루원(F-22b) | 행동 버튼 없음, 정보만 | 프로필 화면 이동 | 화면 없음 |

## F-29c 크루장 넘기기 확인 (61773:6702)

| 항목 | 피그마 | 앱(파일:줄) | 차이 |
|---|---|---|---|
| 형태 | 0장 확인 대화상자(카드 334, 모서리 28, 외곽선 "취소" + 보라 채움) | RunkkConfirmDialog, `isDestructive=true` (CrewManageScreens.kt:477-498) | 다름 |
| 제목 | "[닉네임] 님에게 크루장을 넘길까요?" (닉네임 뒤 띄어 씀) | "%1$s님에게 크루장을 넘길까요?" (`crew_manage_transfer_title`) | 다름(띄어쓰기, 피그마 자리 표시라 확인 필요) |
| 본문 | "넘긴 뒤에는 크루장 권한이 사라져요." | "넘기면 나는 크루원이 되고 크루 관리를 할 수 없어요." (`crew_manage_transfer_message`) | 다름 |
| 버튼 | "취소" / "넘기기"(보라) | "취소" / "넘기기"(라벤더 글자) | 다름(모양) |
| 진입 | F-29b 팝업 "크루장 넘기기" | 크루 관리 → ⋯ → 시트 "크루장 넘기기" | 다름 |

## F-29d 내보내기 확인 (61773:6835)

| 항목 | 피그마 | 앱(파일:줄) | 차이 |
|---|---|---|---|
| 형태 | 0장 확인 대화상자 | RunkkConfirmDialog (CrewManageScreens.kt:471-475, 491-498) | 다름 |
| 제목 | "[닉네임] 님을 내보낼까요?" | "%1$s님을 내보낼까요?" (`crew_manage_kick_title`) | 다름(띄어쓰기) |
| 본문 | "내보낸 크루원은 다시 가입 신청을 해야 해요." | "내보내면 24시간 동안 다른 크루에 가입할 수 없어요." (`crew_manage_kick_message`) | 다름(문구·규칙 설명 다름. 서버 24시간 제한과 맞는지 확인 필요) |
| 버튼 | "취소" / "내보내기"(보라) | "취소" / "내보내기"(라벤더 글자) | 다름(모양) |

## F-23 한 줄 소개 편집 (61773:5510)

| 항목 | 피그마 | 앱(파일:줄) | 차이 |
|---|---|---|---|
| 시트 | 위 모서리 24, 안쪽 위12·좌우20·아래28, 간격 12, 손잡이 40x4 surfaceHigh 모서리 2 | ModalBottomSheet 기본(손잡이·모서리 기본), 좌우 20·아래 28, 간격 12 (CrewHomeScreens.kt:327-361) | 다름(모서리·손잡이 색 확인 필요) |
| 제목 | "내 정보" 17 Bold(lh20) | "크루에서 보이는 내 정보" CardTitle 16 Black (`crew_home_intro_title`) | 다름 |
| 입력 이름 | "한 줄 소개" 13 Medium textSecondary + " [n]/40" 13 Regular | "한줄 소개 n/40" Caption 12 TextSecondary (`crew_home_intro_label`, core RunkkFields.kt:110) | 다름(띄어쓰기 "한 줄", 크기) |
| 입력칸 | divider 테두리(#E9EDE0), 모서리 12, 최소 높이 48, 좌우 12·위아래 14, 16 Regular | 흰 바탕, 테두리 InputBorder #71717A, 모서리 12, 좌우 16·위아래 12, 16 (core RunkkFields.kt:111-118) | 다름 |
| 나이 공개 줄 | "나이" 15 Bold + "  20대 · 공개" 15 Regular, 오른쪽 토글 44x26(켜짐: 라임), 최소 높이 45, 아래 divider | 없음 | 누락(데이터 필요) |
| 성별 공개 줄 | "성별" 15 Bold + "  여자 · 비공개" 15 Regular, 토글 꺼짐(회색) | 없음 | 누락(데이터 필요) |
| 저장 | 라임 알약 높이 52, 16 SemiBold, 위 여백 6 | RunkkPrimaryButton "저장" (CrewHomeScreens.kt:353-358) | 확인 못 함(RunkkPrimaryButton 높이·글자 세부는 미확인) |

## F-26 크루 정보 수정 (61773:6061)

앱: 크루 관리 화면 안 `CrewEditPage` (CrewManageScreens.kt:440-461), 입력은 `CrewFormFields` (CrewComponents.kt:61-98).

| 항목 | 피그마 | 앱(파일:줄) | 차이 |
|---|---|---|---|
| 진입 | 크루 정보(F-25) "수정" | 크루 관리 → "크루 정보 수정 ›" | 다름 |
| 상단 바 | "크루 정보 수정" 가운데 | 제목 왼쪽 (CrewManageScreens.kt:444) | 다름 |
| 본문 여백 | 위 10·좌우 20·아래 16, 간격 12 | padding 16, 간격 16 (CrewManageScreens.kt:445-448, CrewComponents.kt:62) | 다름 |
| 로고 | `RunKK / Avatar / 크루 이미지 수정` 66x66: 기본 이미지 위 검정 50% 덮개(모서리 24) + 가운데 연필 아이콘 24(흰색). 누르면 사진 변경으로 보임 | CrewLogo 72 첫 글자, 누를 수 없음 (CrewManageScreens.kt:449) | 다름 + 누락(로고 변경) |
| 첫 줄 배치 | 로고와 "크루명" 입력이 **한 줄**(간격 14, 아래 정렬) | 로고 아래 입력들이 세로로 쌓임 | 다름 |
| 입력 이름 | "크루명", "한 줄 소개" 13 Medium textPrimary | Caption 12 TextSecondary, "한줄 소개"(붙여 씀), 자리 표시 "2~20자"·"어떤 크루인지 한 줄로" (strings 40-43) | 다름 |
| 입력칸 | 흰 바탕, divider 테두리, 모서리 12, 최소 48, 좌우 12·위아래 14, 크루명 15 Regular / 소개 16 Regular(여러 줄) | 테두리 #71717A, 좌우 16·위아래 12, 16 (core RunkkFields.kt:111-118) | 다름 |
| 태그 제목 | "태그"(13 Medium) + " 최대 3개"(13 Regular), textPrimary | "태그 최대 3개" 12 Bold onSurfaceVariant (CrewComponents.kt:163-165) | 다름 |
| 태그 칩 | 줄 이름 "평일"/"주말" 14 Regular(칸 44), 칩 높이 32, 좌우 12, 모서리 16, 칩 간격 56 기준(사이 약 8~12), 선택 = #67705B 채움 + 13 Bold 글자(#E9EDE0), 미선택 = divider 테두리 + 13 Regular, 체크 표시 없음. 두 줄 세로 간격 8(줄 높이 72 안) | 줄 이름 BodyStrong 14 Bold, `RunkkChoiceChip` 높이 40, 모서리 20, 선택 = 라임 채움 + 체크, 미선택 = borderStrong 테두리, 간격 6 (CrewComponents.kt:155-186, core RunkkChips.kt:49-80) | 다름 |
| 공개 여부·가입 방식 | 이름 13 Medium. 세그먼트: surfaceHigh 바탕, 모서리 22, 안쪽 4, 칸 높이 36, 고른 칸 흰 알약(모서리 18) + 14 Bold, 안 고른 칸 14 Regular. "공개/비공개", "바로 가입/승인 후 가입" | `RunkkSegmentRow`: 이름 Caption 12, 칸 따로따로(간격 8) 체크 달린 선택지 (core RunkkChips.kt:168-214) | 다름 |
| 저장 버튼 | 라임 알약 높이 52, 16 SemiBold, 바깥 위 12·좌우 20·아래 32, **위 구분선 없음** | HorizontalDivider + RunkkPrimaryButton, 위 16·좌우 16·아래 32 (CrewManageScreens.kt:452-459) | 다름 + 앱에만 있음(구분선) |

## F-27 크루 삭제 확인 (61773:6166)

| 항목 | 피그마 | 앱(파일:줄) | 차이 |
|---|---|---|---|
| 진입 | 크루 정보(F-25) 아래 "크루 삭제하기" | 크루 관리 화면 맨 아래 "크루 삭제" (CrewManageScreens.kt:176, 347-359) | 다름 |
| 형태 | 0장 확인 대화상자 | RunkkConfirmDialog (CrewManageScreens.kt:483-498) | 다름 |
| 제목·본문 | "크루를 삭제할까요?" / "크루의 모든 기록이 지워지고 되돌릴 수 없어요." | 같음 (`crew_manage_delete_title`·`_message`) | 같음 |
| 버튼 | "취소" 외곽선 / "삭제" 보라 채움 | 글자 버튼 "취소" / 라벤더 "삭제" | 다름 |

## 피그마에 없는 앱 화면

| 앱 화면 | 위치 | 처리 제안 |
|---|---|---|
| 크루 관리 화면 전체(초대 코드 카드·재발급, 가입 신청, 크루원 ⋯, 주간 공동 목표 설정 줄, 크루 정보 수정 줄, 크루 삭제) | CrewManageScreens.kt:74-180 | 피그마는 이 기능을 크루 정보(F-25)·크루원(F-29)·팝업에 흩어 넣었다. 피그마대로 옮기면 이 화면은 없어진다. 단 재발급·운영진·공동 목표 설정 줄은 피그마에 자리가 없다(문서 충돌) |
| 크루원 메뉴 바텀시트 | CrewManageScreens.kt:362-437 | F-29b 팝업으로 바꿈 |
| 운영진 지정 확인(M9) | CrewStaffConfirmDialog.kt:12-20 | 피그마 F 화면에 없음 |
| ⋯ 단추 컴포넌트 | CrewMoreButton.kt:22-41 | F-29에는 ⋯가 없음 |

---

## 문서(SRS·설계)와 피그마 충돌 — 사용자 확인 필요

1. **초대 코드 재발급(FR-114, 권한표 "크루 정보·태그·공개 여부·가입 방식 수정, 초대 코드 재발급 = 크루장", docs/sw/srs 03-05)**: 피그마 F-25b는 크루장 팝업에도 "복사"만 있다. 재발급 버튼·사용 기한 표시를 없애면 FR-114를 화면에서 못 한다.
2. **운영진 지정·해제(FR-204, D-98)**: 피그마 F-29b 크루장 행동은 "크루장 넘기기"·"내보내기"뿐. 운영진 지정·해제 진입점이 없다. 또 운영진(MANAGER) 역할의 화면 변형(가입 신청 결정 FR-136·FR-123, 공동 목표 설정 FR-138)이 크루 관리 프레임에 없다.
3. **주간 공동 목표 설정(FR-138)**: 앱은 크루 관리 화면에 "주간 공동 목표 설정 ›" 줄이 있다. 이 담당 프레임들에는 없다(레이스 탭 쪽에 있는지는 다른 담당 확인 필요).
4. **크루장 탈퇴 안내(FR-126)**: 앱은 안내에서 바로 "크루장 넘기기"로 보내지만 피그마는 "닫기"만.
5. **내보내기 안내 문구**: 앱 "내보내면 24시간 동안 다른 크루에 가입할 수 없어요" vs 피그마 "내보낸 크루원은 다시 가입 신청을 해야 해요". 서버 규칙(강퇴 후 재가입 제한)과 어느 쪽이 맞는지 확인 필요.
6. **나이·성별 공개 토글(F-23)**: 앱의 공개 설정은 계정 프로필 단위(Profile.isAgePublic/isGenderPublic, ProfileEdit, PATCH 프로필 네 필드)다. 피그마는 크루 "내 정보" 시트 안에 둔다. 크루별 설정인지 계정 전체 설정을 여기서도 바꾸는 것인지 정해야 한다.
7. **피그마 안의 불일치**: 내 정보 2줄이 F-22·F-23은 "[티어] · [레벨] · [나이]", F-29·F-29b·F-29c는 "· [성별]"까지. 가입 신청 보조 줄도 F-25 "골드 · Lv23 · 20대"·"언랭 · Lv4 · 여성" vs F-25b "… · 20대 · 남성"·"… · 30대 · 여성". 기간 선택(F-20b)에 미래 연도(2027·2028년)가 보인다(지난 기록 화면인데 미래 선택 가능 여부).

---

## 요약 A. 기능·데이터가 필요한 차이 (서버 API·응답 필드·UiState 추가)

| 필요한 것 | 쓰는 화면 | 지금 상태 |
|---|---|---|
| 크루 만든 날(createdAt) | F-18·F-25 "[yyyy. mm. dd] · 7명" | CrewDetailView·CrewDetail에 없음 (backend crew/CrewDetailView.java, android domain/crew/CrewDetail.kt:12-29) |
| 크루 누적 거리, 누적 크루런 횟수 | F-18·F-25 통계 칸 | 없음(이번 주·지난 주 거리만) |
| 지난 크루런 월별 조회(일정 시각·제목·목표 거리·참가 인원, 달력 점용 날짜 목록) | F-20·F-20b·F-20c | 게시 목록(GET /crews/{id}/posts) 커서 쪽 넘김만 있음. 월 필터 없음 → 서버에 월 범위 파라미터를 더하거나 앱이 끝까지 읽어야 함. 새 화면·ViewModel·UiState(선택 월, 선택 날짜, 기간 시트 열림) 필요 |
| 지난 공동 목표 목록(주차, 합계 거리, 목표, 달성 여부, 참여 인원) | F-21 | 목록 API 없음. GET /crews/{id}/race?weekStart= 로 한 주씩만 가능 (backend web/crew/CrewController.java:174-184) → 기록 목록 API 추가 권장. 새 화면·UiState 필요 |
| 크루원별 **누적** 거리 | F-22·F-29 목록 오른쪽, 정렬 | 앱은 레이스 기여(이번 주)로 계산 (CrewMembersTab.kt:148-153). 크루원 응답(CrewMembersResponse.Member)에 누적 거리 없음 |
| 크루원 정보: 레벨, 나이대, (공개된) 성별, 크루 가입일 | F-22b·F-29b 팝업, F-22·F-29 내 정보 줄 | 크루원 응답에는 tier·crewIntro만(backend CrewMembersAssembler Member 85-92). 레벨·나이대·성별은 회원 프로필(Profile: level, ageBand, shownGender)에 있으니 팝업 열 때 프로필 조회로 채울 수 있음. **크루 가입일(joinedAt)은 어디에도 없음** → 서버 추가 필요. UiState에 `openedMember`(팝업 대상)와 그 상세 필요 |
| 가입 신청자 티어·레벨·나이대·성별 | F-25 신청 카드 보조 줄 | 서버 응답에 tier·runimalType은 있는데 앱 PendingRequestsResponse가 버림 (android data/api/crew/PendingRequestsResponse.kt:8-13). 레벨·나이대·성별은 서버에 없음 |
| 크루 정보(F-25) 안 가입 신청 목록·승인·거절 | F-25 | 지금은 CrewManageViewModel에만 있음. CrewHomeViewModel(크루 정보 쪽)로 가져오거나 공유 필요(UiState에 requests, 승인/거절 동작) |
| 크루원 목록(F-29)에서 양도·내보내기 | F-29b·F-29c·F-29d | CrewManageViewModel의 onAsk/onConfirm을 크루 홈 크루원 쪽에서 쓸 수 있게 옮겨야 함 |
| 나이·성별 공개 토글 저장 | F-23 | 크루 내 프로필 PATCH(/crews/{id}/me)는 intro만 받음 (backend CrewDtos.java:194-199). 계정 프로필 PATCH를 같이 부를지 결정 필요 |
| 크루 로고 사진 변경 | F-26 연필 덮개 | 서버는 logoUrl(https 주소) 문자열만 받음, 업로드 API 없음 (backend CrewDtos.java:114-135) |
| 크루 정보에서 "수정"·"크루 삭제하기"로 바로 이동 | F-25 | 지금 경로는 Routes.CrewManage 화면 경유. CrewHomePage에 편집 쪽을 더하거나 경로 변경 필요 (CrewHomePage.kt) |

## 요약 B. 화면만 고치면 되는 차이

- 공통: 상단 바(높이 57, 가운데 제목 19 Bold, 원 없는 arrow_back), 확인 대화상자(카드 334·모서리 28·외곽선 취소 + 보라 채움 48 알약), 안내 대화상자(라임 알약 하나 또는 닫기 X), 크루 기본 이미지(groups 아이콘).
- F-17/F-24: ⋯ 메뉴를 딤 + 176폭 카드(테두리 #C7CCBF, 모서리 16, 그림자, 15 Regular 줄, 구분선)로. "크루 관리" 항목 제거(문서 충돌 정리 후).
- F-18/F-25: 여백 20/14, 로고 74, 크루명 19 Bold, 인원 "7명" 13 Regular, 소개 16, 태그 틴트 색(평일 보라·주말 라임, 높이 28·모서리 14·14 Medium), 통계 칸 모양(모서리 24·안쪽 14·간격 10), "지난 크루런 ›"·"지난 목표 ›" 메뉴 줄(52, divider), 아래 링크 13 Regular textSecondary 밑줄. 크루장: "수정" 링크, "가입 신청 N건" 구역과 카드(거절 외곽선 → 승인 #67705B 채움 순서), "크루 삭제하기"·"탈퇴하기" 링크 두 개.
- F-18b/F-25b: 코드 칸(surfaceHigh, 56 높이, 24 Bold 자간 4, 공백 문자 대신 자간), "복사" 하나, X 닫기, "닫기" 글자 버튼 제거.
- F-19: 본문 문구 "탈퇴 후 24시간 동안 …", 대화상자 형태.
- F-28: 본문 문구 "다른 크루원에게 크루장을 넘긴 뒤 탈퇴할 수 있어요.", 버튼 "닫기" 하나.
- F-22/F-29: 내 정보 카드 제목 "내 정보"·편집 버튼 30 높이, 목록 머리 "누적 거리", 줄 모양(36 원, 14 Regular, 크루장 배지 20 높이 11 글자, 아래 divider, 보조 줄 제거), 줄 누르면 팝업.
- F-22b/F-29b: 팝업 카드(310, 모서리 24, 80 원 사진, 18 Bold 이름, 정보 5줄, 한 줄 소개 칸, X), 크루장만 "크루장 넘기기"(외곽선)·"내보내기"(보라).
- F-29c/F-29d/F-27: 문구("넘긴 뒤에는 크루장 권한이 사라져요.", "내보낸 크루원은 다시 가입 신청을 해야 해요.")와 대화상자 형태.
- F-23: 제목 "내 정보" 17 Bold, 입력 이름 "한 줄 소개 [n]/40" 13, 입력칸 테두리 divider·안쪽 12/14, 손잡이 surfaceHigh. (토글은 A로)
- F-26: 로고·크루명 한 줄 배치, 입력 이름 13 Medium "한 줄 소개", 입력칸 모양, 태그 칩(32 높이, #67705B 선택, 체크 없음), 세그먼트(surfaceHigh 통 + 흰 알약), 저장 위 구분선 제거·여백 20.

## 앱에 없는 피그마 에셋

| 에셋 | 피그마 이름(노드) | 쓰는 곳 | 앱 상태 |
|---|---|---|---|
| groups(여러 사람) 아이콘 | Material `groups` (`RunKK / Avatar / 크루 기본 이미지` 61869:4933 안) | 크루 기본 이미지 28/40/35.75, 하단 내비 크루 탭 | 없음(RunkkIcons.Users는 lucide 모양, core RunkkIcons.kt:90) |
| more_horiz | `RunKK / Buttons / 아이콘` (61865:12380) | ⋯ 단추 24 | 없음(Canvas로 점 그림) |
| arrow_back | `RunKK / Buttons / 뒤로가기` (61831:9677) | 모든 상단 바 | 다름(RunkkIcons.Back은 꺾쇠) |
| X(닫기) 20 | "Icon - X" (61772:5532 등) | 초대 코드 팝업, 크루원 정보 팝업 | RunkkIcons.Close 있음(선 두께·크기 일치 여부 확인 못 함) |
| 연필(편집) 24 | "Icon - 연필" (62158:15041, `RunKK / Avatar / 크루 이미지 수정` 62158:15043) | F-26 로고 덮개 | 없음 |
| 달력 이동 화살표 ‹ › | `RunKK / Calendar / 이동 화살표` (62051:7073) — 글자 "‹" "›" 28 | F-20 | 없음(글자라 에셋 불필요) |
| 월 제목 아래 꺾쇠 8x4 | "Chevron" (61772:5663) | F-20 | RunkkIcons.ChevronDown로 대체 가능 |
| 크루런 점 4x4 | "크루런 점" (61772:5689) | F-20 달력 | 없음(원으로 그리면 됨) |
| 오늘 표시 원 36 | `RunKK / Calendar / 오늘 표시` (62158:12555, 색 color/todaymark/bg #E9EDE0) | F-20 | 없음(도형) |
| 토글 44x26 켜짐/꺼짐 | `RunKK / Control / 토글` (62113:7024 / 62113:7026) | F-23 | Switch로 구현 가능(core RunkkSwitchRow 있음, 모양 일치 확인 못 함) |
| 태그 틴트 색 | `RunKK / Tag / 태그` PurpleTint(#EBE7FC/#5C4BDB)·LimeTint(#F2F9D0/#3D4A00) (61940:5173) | F-18·F-25 | 색 토큰 없음 |
| 승인 칩 색 | color/respChip/bg #67705B | F-25 가입 신청 | 토큰 없음 |
| 막대 채움 Crew | `RunKK / Race / 막대 채움` color/barCrew #BFC6B3 (61941:10414) | F-21 | 확인 못 함(레이스 탭에서 쓰는지 다른 담당) |

확인 못 함: F-22b·F-29c·F-29d·F-27 대화상자의 세부 수치는 스크린샷으로만 봤고 F-19·F-29b와 같은 컴포넌트로 보인다고 판단했다. F-20c의 선택 원 크기(36)와 색(#CAEB2E)도 스크린샷 기준이다. 하단 플로팅 내비가 크루원 쪽 화면에서 보이는지, RunkkPrimaryButton 높이·글자, ModalBottomSheet 모서리·손잡이 색은 실행해 보지 않았다.
