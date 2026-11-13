# 크루 일정(F-11~F-13) 피그마 ↔ 앱 차이

- 피그마: fileKey `NMTtVMWIT22D0CwOU2y0I9`, 프레임 390x844. F-11·F-12·F-13·F-11c·F-12b·F-13b·F-13c는 get_design_context, F-12f는 하위 노드 61771:5381(반복 시트)를 get_design_context로 봤다. 나머지 변형은 스크린샷으로만 봤다.
- 앱: develop 브랜치. 경로 접두어는 `android/feature/crew/src/main/java/com/a207/runkk/app/ui/crew/`(아래에서 `crew/`), `android/core/ui/src/main/java/com/a207/runkk/app/core/designsystem/`(아래에서 `ds/`)로 줄인다. strings는 `android/feature/crew/src/main/res/values/strings.xml`(아래에서 `strings`)이다.
- 피그마 토큰 표기: `textPrimary #171b12`처럼 피그마 변수 이름과 기본값을 쓴다. 앱 값은 라이트 테마 기준이다(`ds/RunkkColors.kt:128-163`).

## 0. 공통(모든 화면에 걸리는 차이)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 글꼴 | IBM Plex Sans KR (Regular/Medium/SemiBold/Bold) | Noto Sans KR (`ds/RunkkType.kt:26`), 굵기 Black(900)을 자주 쓴다 | 다름 |
| background | `#f7f8f2` | Background `#FAFAFA` | 다름 |
| surface | `#ffffff` | Surface `#FFFFFF` | 같음 |
| surfaceHigh | `#e9ede0`(탭 바탕, 아바타 바탕, 내용 상자, 휠 선택 띠, 오늘 표시, 날짜 고정 칸) | 해당 토큰 없음. 가장 가까운 값은 runimalTile `#E9EEDC` | 누락 |
| divider | `#e9ede0`(줄 구분선, 입력칸 테두리, 카드 테두리) | Border `#E4E4E7`, 입력칸은 InputBorder `#71717A` | 다름 |
| textPrimary / textSecondary | `#171b12` / `#67705b` | TextPrimary `#18181B` / TextSecondary `#52525B` | 다름 |
| primary / onPrimary | `#caeb2e` / `#171b12` | LimeFill `#CBEA2B` / OnLimeFill `#171B12` | 거의 같음 |
| secondary / onSecondary | `#5c4bdb` / white | Violet·Lavender `#5B4FD9`. MaterialTheme secondary는 lavender다(`ds/Theme.kt:27`) | 거의 같음 |
| 링크 글자 색 | textPrimary | RunkkTextLink는 Lime `#3F6212`(`ds/RunkkButtons.kt:487-510`) | 다름 |
| 딤 | `rgba(23,27,18,0.45)` | M3 기본 스크림 | 다름 |
| 드롭다운 테두리 | `color/dropdown/stroke #c7ccbf` | 없음 | 누락 |
| F 화면 헤더(RunKK/Header) | 높이 57, 좌우 12, 뒤로가기 40×40(배경 원 없음, arrow_back 24, textPrimary), 제목은 **가운데** 19 Bold(lh23), 오른쪽 ⋯는 44 버튼(more_horiz 24, right 12, top 7) | RunkkTopBar: 최소 높이 64, 좌우 16, 뒤로가기는 **보라 채움 원**(BackButton `#5C4BDB`, 흰 화살표), 제목 18 Black 왼쪽 정렬(isTitleCentered 기본 false) (`ds/RunkkBars.kt:77-121`) | 다름 |
| 바텀시트 공통 | 배경 surface, 위쪽 모서리 28, pt24 px20 pb32, 항목 간격 16. 머리: 제목 18 Bold(lh22) + 오른쪽 "✕" 18. 아래 버튼 Lime Medium(h48, 알약, 15 SemiBold) 전체 폭 | 크루 일정 쪽에는 이 형태의 시트가 없다(ModalBottomSheet는 응답 시트에만 쓴다, 드래그 핸들 있음, 닫기 ✕ 없음) | 누락 |
| CTA 버튼(Lime Large) | 높이 52, 모서리 100(알약), 16 SemiBold, 바깥 여백 pt12 px20 pb32, 구분선 없음 | RunkkPrimaryButton Regular: 높이 56, 모서리 16, 16 Black(`ds/RunkkButtons.kt:54-57, 74-135`) | 다름 |

## 1. F-11 크루 일정(크루런 탭)

### F-11 크루 일정(제안·새 디자인) (61771:4397)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 헤더 여백 | pt12 px20, 간격 12 | start 16(탭 자리)·end 8·top 8·bottom 12 (`crew/CrewHomeScreens.kt:367-397`) | 다름 |
| 크루 이미지 | RunKK/Avatar/크루 기본 이미지 50×50, 모서리 15, 배경 surfaceHigh `#e9ede0`, 가운데 groups 아이콘 28(textSecondary) | CrewLogo 48×48, 모서리 12(size/4), 배경 primaryContainer(limeContainer `#ECF5D0`), 크루명 첫 글자(20 Black, Lime) (`crew/CrewComponents.kt:39-57`, 호출 `CrewHomeScreens.kt:386`) | 다름 |
| 크루명 | 17 Bold, lh20, textPrimary, 한 줄 | TopBarTitle 18 Black, 최대 2줄 (`CrewHomeScreens.kt:387-394`) | 다름 |
| ⋯ 버튼 | more_horiz 아이콘 24, 버튼 44, 색 textPrimary | MoreMenu: 48 상자에 Canvas로 점 3개(18×4), 색 onSurfaceVariant(TextSecondary) (`crew/BoardParts.kt:62-79`) | 다름 |
| 탭 줄(레이스·크루런·채팅) | 위 여백 14, 좌우 20. 바탕 surfaceHigh `#e9ede0`, 높이 44, 모서리 22, 안쪽 4. 탭 높이 36, 모서리 18. 고른 탭 primary `#caeb2e` + 14 Bold `#171b12`, 안 고른 탭 14 Regular **textPrimary** | RunkkTabRow: 바탕 Surface(흰색), 모서리 16, 안쪽 4, 탭 최소 40(터치 48), 모서리 12, 고른 탭 LimeFill + 13 Black, 안 고른 탭 13 Normal **TextSecondary**, 좌우 16 (`ds/RunkkChips.kt:382-420`, `CrewHomeScreens.kt:248-265`) | 다름 |
| 본문 여백 | pt12 px20 pb16. 모집하기 줄 아래 10 뒤 바로 히어로 | LazyColumn contentPadding 16(사방), 항목 간격 16 (`CrewHomeScreens.kt:207-212`). 머리 항목 안 간격 16 (`CrewScheduleTab.kt:79`) | 다름 |
| "+ 크루런 모집하기" | 바탕 surface, **테두리 없음**, 높이 28, 모서리 18, 좌우 14, 간격 6, plus 아이콘 12(textPrimary), 13 Regular lh16 | RunkkPillButton: 높이 40, 모서리 20, 테두리 1 BorderStrong `#8A8A93`, 좌우 16, 간격 8, 아이콘 16, 13 Regular (`CrewScheduleTab.kt:157-168`, `ds/RunkkButtons.kt:239-288`) | 다름 |
| "지난 크루런 ›" | 늘 보인다. "지난 크루런" 13 Medium textPrimary + "›" 16 Regular textSecondary, 간격 2, 오른쪽 6. 누르면 **별도 화면 F-11b** | 지난 크루런이 있을 때만 보인다. RunkkTextLink 12 Lime 한 덩어리 "지난 크루런 ›"(`strings:298`). 누르면 같은 목록의 "지난 크루런" 머리글로 스크롤 (`CrewScheduleTab.kt:166`, `CrewHomeScreens.kt:200-203`) | 다름 |
| 히어로 카드 | 배경 secondary `#5c4bdb`, 모서리 24, px20 py18, 간격 10 | 배경 RunkkColors.Violet `#5B4FD9`, 모서리 24, 20/18, 간격 10 (`CrewScheduleTab.kt:174-210`) | 거의 같음 |
| 히어로 윗줄 | "오늘 · 10.21(수) 19:30" / "참석 5명", 12 Regular onSecondary(white) | "오늘 10.21 (수) 19:30": 가운뎃점 없음, "(수)" 앞 공백 있음(`strings:290` `오늘 %1$s`, `strings:39` `M.d (E) HH:mm`) (`CrewScheduleTab.kt:264-270`) | 다름(문구) |
| 히어로 제목 | 20 Bold lh24 white, 뒤에 "›" 글자 20 Regular, 간격 4 | 20 Bold, ChevronRight 아이콘 16dp, 간격 2 (`CrewScheduleTab.kt:200-206, 273-305`) | 다름(작음) |
| 남은 시간 | "29:45" 28 Bold lh34 + "후 시작" 13 Regular lh16, 간격 6, 아래 정렬 | 같다. 하루 넘으면 "n일 후 시작"(앱 규칙) (`CrewScheduleTab.kt:213-239`) | 같음 |
| 대기실 버튼 | White Medium: 높이 48, 알약, 15 SemiBold textPrimary, 문구 "대기실 입장하기" | 같은 크기. 대기실이 아직 안 열렸으면 alpha 0.6에 "대기실 n분 뒤 열려요"·"대기실은 시작 30분 전에 열려요"·"대기실이 닫혔어요" (`CrewScheduleTab.kt:242-261, 576-587`) | 앱에만 있음(상태 문구). 피그마에 열리기 전 상태는 없다(확인 못 함) |
| 히어로 대상 | 예시는 "오늘" 일정 | 다가오는 첫 일정이면 날짜와 상관없이 히어로 (`CrewScheduleTab.kt:77, 88-90`) | 확인 못 함(오늘이 아닐 때 피그마 사양 없음) |
| 묶음 머리글 | "이번주" 15 Bold + 개수 13 Regular textSecondary, 간격 6, pt22 pb6 | 같은 글자. 위 6 + 목록 간격 16 = 22 (`CrewScheduleTab.kt:308-329`) | 같음 |
| "이후" 묶음 | 없음(이번주·다음주만) | LATER "이후" 묶음 (`CrewScheduleTab.kt:124-128`) | 앱에만 있음 |
| 일정 줄 | py12, 간격 14, 아래 구분선 divider `#e9ede0`. 날짜 칸 40×44: 일 20 Bold, 요일 12 Regular textSecondary | 같은 배치. 구분선 Border `#E4E4E7` (`CrewScheduleTab.kt:332-372`) | 색만 다름 |
| 줄 제목 | 15 Bold lh18 + "›" 16 Regular textSecondary, 간격 2 | 15 Bold + ChevronRight 아이콘 16dp (`CrewScheduleTab.kt:357-363`) | 거의 같음 |
| 줄 보조문구 | "07:00 · 제한 없음", "07:00 · 10km": 구분자 " · ", 12 Regular textSecondary | "07:00, 5km": 구분자 ", " (`strings:299` `%1$s, %2$s`). "제한 없음" 거리는 없다 | 다름 |
| 참석 칩(내가 참석) | RunKK chip: 74×34, 모서리 16, 배경 `color/respchip/bg #67705b`, 13 **Bold white** | 74×34, 모서리 16, 배경 TextSecondary `#52525B`, 13 Bold, 글자 Background `#FAFAFA` (`CrewScheduleTab.kt:378-411`) | 색만 다름 |
| 참석 칩(미참석) | M3 Text 칩: 74×34, 모서리 **8**, 테두리 1 divider `#e9ede0`, **14 Medium**, textSecondary, 자간 0.1 | 모서리 8, 테두리 Border, **13 Medium** (`CrewScheduleTab.kt:407`) | 다름(글자 크기) |
| 줄·제목 누름 | F-13 일정 상세 **화면**으로 이동 | 응답 바텀시트(CrewRunSheet)를 연다 (`CrewScheduleTab.kt:417-429`, `CrewHomeScreens.kt:304-321`) | 다름(구조) |
| 목록 아래 지난 크루런 | 없음(F-11b 별도 화면) | 목록 끝에 "지난 크루런" 머리글 + CrewCard 줄(제목, "크루런, 10.14, 5km, 6명" ›) (`CrewScheduleTab.kt:94-104, 590-636`) | 앱에만 있음 |
| 빈 상태 | 피그마 없음 | "아직 크루런이 없어요" (`CrewScheduleTab.kt:83`, `strings:285`) | 확인 못 함 |
| 하단 내비 | RunKK/Navigation/Floating 354×90, 하단 20 | RunkkBottomBar(`ds/RunkkBars.kt:184-`)가 이미 같은 컴포넌트를 구현 | 이 보고서 범위 밖 |

### F-11b 지난 일정 (61771:4543)

**화면 없음.** 앱은 같은 탭 목록 끝으로 스크롤한다(위 표). 피그마 사양:

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 헤더 | 공통 헤더, 제목 "지난 크루런", 오른쪽 동작 없음 | 없음 | 누락 |
| 본문 여백 | pt11 px20 pb16, 항목 간격 10 | 없음 | 누락 |
| 기간 필터 | 오른쪽 정렬. 바탕 surface, 테두리 1 divider, 높이 42, 모서리 21, pl18 pr16, 간격 8. 글자 "2026년 10월" 16 Medium lh19 textPrimary + 아래 꺾쇠(Chevron 12×6). 누르면 F-11c 시트 | 없음. 기간(월) 필터 개념 없음 | 누락(기능) |
| 줄 | 아래 구분선 divider, 최소 높이 45, pb6, 간격 12. 제목 "[화요일 인터벌]" 16 **Regular** lh19 textPrimary. 보조 "10.20(화) 19:00 · 5km · 6명" 12 Regular lh18 **textPrimary**, 제목과 간격 4. 오른쪽 35×16 빈 칸(용도 확인 못 함). 최신순 | 지금 앱의 지난 줄은 카드(RunkkCard, 테두리)에 BodyStrong 제목 + "크루런, 10.14, 5km, 6명" Caption textSecondary + 결과면 › (`CrewScheduleTab.kt:590-636`) | 다름 |
| 줄 누름 | F-11d 지난 일정 상세 | 끝난 크루런만 결과 화면(Routes.CrewRunResult)으로, 취소된 것은 누를 수 없음 (`CrewScheduleTab.kt:594, 610`) | 다름 |
| 취소된 일정 | 피그마 없음 | "취소됨" 문구 (`CrewScheduleTab.kt:600-604`) | 확인 못 함 |

### F-11c 기간 선택 (61771:4612)

**화면 없음(시트 없음).** 피그마 사양(노드 61771:4675 "Bottom Sheet - 기간 선택"):

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 형태 | **바텀시트**, 시트 위끝 y=506, 공통 시트 규격, 딤 0.45 | 없음 | 누락 |
| 제목 | "기간 선택" 18 Bold + "✕" | 없음 | 누락 |
| 휠 피커 | 높이 180, 열 2개(년·월) 간격 8. 항목 높이 36. 선택 띠: surfaceHigh `#e9ede0`, 높이 36, 모서리 12, top 72, 전체 폭. 고른 값 18 Bold lh22, 바로 옆 16 Regular opacity 0.45, 그 바깥 16 Regular opacity 0.20 | 앱에 휠 피커 컴포넌트가 없다(`ds/`에 없음) | 누락 |
| 값 | 년 "2026년"(아래 2027·2028), 월 "10월"(위 9월, 아래 11·12월). 위쪽 빈 칸 2개 | 없음 | 누락. 지난 일정인데 앞 연도가 보이는 범위는 확인 못 함 |
| 버튼 | "적용" Lime Medium h48 전체 폭 | 없음 | 누락 |

### F-11d 지난 일정 상세 (61771:4718)

**화면 없음.** 앱은 지난 줄을 누르면 크루런 결과 화면(F-16 계열, `crew/CrewRunResultScreen.kt`)으로 간다. 피그마 사양은 F-13 상세 틀과 같고 아래만 다르다(스크린샷 기준, 수치는 F-13 design context에서 가져옴):

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 헤더 | "크루런 상세", 오른쪽 ⋯ | 결과 화면 헤더 | 다름 |
| 작성 정보 | "[닉네임] · 종료된 일정" 12 Regular | 없음 | 누락 |
| 정보 카드 | 날짜 "10.13(화) 19:00"(반복 배지 없음), 거리 "5km", "참석 4명", 내용 상자 | 없음 | 누락 |
| 참석자 줄 | F-13 줄 + 오른쪽 끝에 달린 거리 "5.28km" Bold(약 16, 스크린샷 추정, 정확한 수치 확인 못 함) | 결과 화면의 참가자별 거리(CrewRunResult.participants) | 다름(데이터는 있음) |
| 하단 버튼 | 없음 | - | - |

## 2. F-12 일정 작성

### F-12 일정 작성 (61771:4799)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 헤더 | 공통 헤더(가운데 제목) "크루런 모집하기" | RunkkTopBar 왼쪽 제목, 보라 원 뒤로가기 (`crew/CrewRunScreens.kt:152`) | 다름 |
| 본문 여백 | pt11 px20 pb16, 칸 사이 12 | padding 16 사방, 간격 16 (`CrewRunScreens.kt:153-156`) | 다름 |
| 칸 순서 | 제목 → 내용 → 날짜 → 시간 → 거리 → 반복 | 제목 → 내용 → 거리(세그먼트) → 날짜·출발 시각(한 줄 두 칸) (`CrewRunScreens.kt:158-159, 181-232`) | 다름 |
| 칸 라벨 | 13 **Medium** lh16 **textPrimary**, 라벨과 상자 간격 9 | Caption 12 Regular TextSecondary, 간격 8 (`ds/RunkkFields.kt:108`) | 다름 |
| 입력 상자 | 바탕 surface, 테두리 1 **divider `#e9ede0`**, 모서리 12, 안쪽 px12 py14, 최소 높이 48, 글자·자리문구 15 Regular lh18, 자리문구 textSecondary | 테두리 InputBorder `#71717A`, 모서리 12, 안쪽 px16 py12, 글자 16 (`ds/RunkkFields.kt:98-124`) | 다름 |
| 내용 상자 | Text Area 높이 72 고정, 모서리 **16** | RunkkTextField 여러 줄, 높이 내용 따라, 모서리 12 (`CrewRunScreens.kt:194-200`) | 다름 |
| 제목 자리문구 | "예) 토요일 한강 같이 뛰어요" | "토요일 아침 5km 같이 뛰어요" (`strings:178`) | 다름 |
| 내용 라벨·자리문구 | "내용" / "코스, 페이스 등 모임 안내를 적어 주세요" | "내용(선택)" / "페이스, 분위기를 알려 주세요" (`strings:179-180`) | 다름 |
| 날짜 칸 | 전체 폭 입력 상자 모양, 자리문구 "날짜 선택", 누르면 **달력 바텀시트**(F-12b). 고른 뒤 값 "10.24 (토)" textPrimary | RunkkSelectField **드롭다운**, 반 폭, 라벨 "날짜", 기본값 내일 "내일 10/22(목)" 형식, 오늘~6일 뒤 7개 (`CrewRunScreens.kt:211-243`, `CrewRunWriteUiState.kt:14-27`) | 다름 |
| 시간 칸 | 라벨 "시간", 자리문구 "시간 선택", 누르면 **휠 바텀시트**(F-12d). 값 "오전 07:00" | 라벨 "출발 시각"(`strings:183`), 드롭다운, 05:00~23:30 30분 단위, 형식 "HH:mm", 기본 07:00 (`CrewRunWriteUiState.kt:28-29`, `CrewRunScreens.kt:223-230, 245`) | 다름 |
| 거리 칸 | 입력 상자 모양, 자리문구 "거리 선택", 누르면 **휠 바텀시트**(F-12e). 값 "5km" | RunkkSegmentRow 1·3·5·10km, 기본 5km (`CrewRunScreens.kt:201-207`, `crew/CrewSupport.kt:26-27`) | 다름 |
| 반복 칸 | 라벨 "반복", 기본값 "반복 안 함", 누르면 반복 바텀시트(F-12f) | 없음 | 누락(기능) |
| 기본값 | 날짜·시간·거리 모두 비어 있음(자리문구) | 날짜 내일, 07:00, 5km가 미리 들어 있다 | 다름 |
| 올리기 버튼 | Lime Large h52 알약 "올리기" 16 SemiBold, 컨테이너 pt12 px20 pb32, 위 구분선 없음 | 위에 HorizontalDivider, RunkkPrimaryButton h56 모서리 16, 16 Black, 여백 16/16/16/32 (`CrewRunScreens.kt:169-176`) | 다름 |
| 올리기 활성 조건 | 확인 못 함 | 제목만 있으면 활성(`CrewRunWriteUiState.kt:23-24`) | 확인 못 함 |
| 오류 문구 | 피그마 없음 | 13 Bold secondary(라벤더) 문구 (`CrewRunScreens.kt:160-167`) | 앱에만 있음 |

### F-12b 날짜 선택 팝업 (61771:4850)

**시트 없음(앱은 드롭다운).** 피그마 사양(노드 61771:4889):

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 형태 | **바텀시트**, 위끝 y=408, 공통 규격 | DropdownMenu 목록(`ds/RunkkFields.kt:150-188`) | 다름 |
| 제목 | "날짜 선택" + ✕ | 없음 | 누락 |
| 월 이동 줄 | 양끝 정렬. RunKK/Calendar/이동 화살표 32×32, 글자 "‹"/"›" 28 Regular lh26. 비활성 textSecondary, 활성 textPrimary. 가운데 "10월" 16 Bold lh19 | 없음 | 누락 |
| 요일 머리 | 일~토, 칸 높이 36, 14 Regular lh17 textSecondary, 일요일 시작 | 없음 | 누락 |
| 날짜 칸 | 7열 같은 폭, 높이 36, 주 사이 간격 4. 지난 날짜 14 Regular textSecondary(고를 수 없음, 스크린샷에서는 더 흐림). 고를 수 있는 날 14 **Bold** textPrimary | 없음 | 누락 |
| 오늘 표시 | RunKK/Calendar/오늘 표시: 36×36 원(모서리 18), 배경 `color/todaymark/bg #e9ede0`, 숫자 Bold | 없음 | 누락 |
| 고른 날 표시 | 확인 못 함(피그마에 고른 상태 그림 없음. 24일 칸에만 모서리 18 지정) | - | 확인 못 함 |
| 고를 수 있는 범위 | 10.21(오늘)~11.4: 오늘 포함 **15일**(F-12c에서 11월 1~4일만 Bold) | 오늘~6일 뒤 **7일** (`CrewRunWriteUiState.kt:27`, `CrewRunWriteViewModel.kt:onDay`) | 다름(기능) |
| 버튼 | "확인" Lime Medium h48 | 없음(고르면 바로 닫힘) | 누락 |

### F-12c 날짜 팝업 11월 (61771:5007)

F-12b와 같은 시트에서 달만 바뀐 상태(스크린샷 기준).

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 이전 화살표 | 활성(textPrimary) | 없음 | 누락 |
| 다음 화살표 | 비활성(textSecondary) — 범위 끝 달 | 없음 | 누락 |
| 날짜 | 1~4일 Bold(고를 수 있음), 5~30일 흐림 | 없음 | 누락 |

### F-12d 시간 선택 (61771:5166)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 형태 | **바텀시트**(위끝 약 y=506), "시간 선택" + ✕, 휠 3열 + "확인" | 드롭다운 목록 (`CrewRunScreens.kt:223-230`) | 다름 |
| 열 | 오전/오후 · 시(두 자리 "07") · 분(두 자리 "00") | 한 열 "HH:mm" | 다름 |
| 분 단위 | **5분**(50, 55, 00, 05, 10) | 30분 (`CrewRunWriteUiState.kt:28-29`) | 다름(기능) |
| 시 범위 | 보이는 값 05~09(12시간제). 전체 범위 확인 못 함 | 05~23시 | 확인 못 함 |
| 휠 모양 | F-11c와 같은 휠(띠 surfaceHigh h36 모서리 12, 고른 값 18 Bold, 옆 0.45, 바깥 0.20) | 없음 | 누락 |
| 날짜 칸 값 표시 | "10.24 (토)" (요일 앞 공백) | dayLabel "10/24(토)", 오늘·내일은 "오늘 …"/"내일 …" (`CrewRunScreens.kt:235-244`) | 다름 |

### F-12e 거리 선택 (61771:5265)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 형태 | **바텀시트**, "거리 선택" + ✕, 휠 1열 + "확인" | 세그먼트 4칸 (`CrewRunScreens.kt:201-207`) | 다름 |
| 선택지 | **제한 없음**, 3km, 5km, 10km, **하프** (보이는 범위. 하프 아래 더 있는지는 확인 못 함). **1km 없음** | 1·3·5·10km (`crew/CrewSupport.kt:26-27`) | 다름(기능) |
| 시간 칸 값 | "오전 07:00" | "07:00" | 다름 |

### F-12f 반복 선택 (61771:5342)

**시트 없음.** 피그마 사양(노드 61771:5381 "Bottom Sheet - 반복 선택"):

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 형태 | **바텀시트**(위끝 약 y=565), 공통 규격 | 없음 | 누락 |
| 제목 | "반복 설정" 18 Bold + ✕ | 없음 | 누락 |
| 선택지 1 | "반복 안 함" 줄: 높이 52, 아래 구분선 divider, 글자 16 Regular lh19 textPrimary, 오른쪽 라디오 22×22(안 고름: 테두리 1.5 divider `#e9ede0`, 모서리 11) | 없음 | 누락 |
| 선택지 2 | "매주 토요일"(고른 날짜의 요일) pt14, 고른 라디오(보라 테두리 원 + 검은 점, SVG 에셋) | 없음 | 누락 |
| 안내 | 고른 선택지 아래 pt3 pb14, 13 Regular lh16 textSecondary: 매주 고름 → "매주 일요일에 다음 모집글이 자동으로 생성돼요", 반복 안 함 고름 → "다음 모집글이 생성되지 않아요"(F-13e) | 없음 | 누락 |
| 버튼 | "확인" Lime Medium h48 | 없음 | 누락 |
| 반복 칸 값 | "매주 토요일" | 없음 | 누락 |

## 3. F-13 일정 상세

### F-13 일정 상세 (61771:5416)

**화면 없음.** 앱은 같은 내용을 바텀시트 `CrewRunSheet`로 보인다(`crew/CrewScheduleTab.kt:417-574`). 피그마 사양과 앱 시트의 대응:

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 형태 | 전체 **화면**(Routes 필요), 헤더 "크루런 상세" + 오른쪽 ⋯ | ModalBottomSheet, 머리 없음 (`CrewScheduleTab.kt:417-429`) | 다름(구조) |
| 본문 여백 | pt11 px20 pb16, 덩어리 간격 14 | 좌우 20, 아래 28, 간격 12 (`CrewScheduleTab.kt:422-424`) | 다름 |
| 작성 정보 | "[닉네임] · 2시간 전"(작성 경과 시간), 12 Regular lh18 textPrimary. 내 글은 "나 · 2시간 전" | 종류 배지 "크루런, 비대면"(Lime) + 작성자/"나" Caption textSecondary, 오른쪽 "참석 n" (`CrewScheduleTab.kt:459-488`, `strings:287`) | 다름. 배지는 앱에만 있음, 경과 시간은 누락 |
| 제목 | 20 Bold lh28 textPrimary(제목만) | summary(제목+내용 전체) BodyStrong 14 (`CrewScheduleTab.kt:443-445`) | 다름 |
| 정보 카드 | 테두리 1 divider, 모서리 **28**, 안쪽 14, 줄 간격 10, 배경 없음 | 없음(평문 줄) | 누락 |
| 일시 줄 | 달력 아이콘 18 + "10.24 (토) 07:00" 15 Regular lh18 + 반복 배지 "매주"(배경 surfaceHigh, 모서리 10, px8 py2, 12 Medium lh14) | "10.24 (토) 07:00, 5km" Body 한 줄 (`CrewScheduleTab.kt:446-451`) | 다름. 아이콘·반복 배지 누락 |
| 거리 줄 | 경로 아이콘 18 + "제한 없음"/"5km" 15 Regular | 위 일시 줄에 합침 | 다름 |
| 참석 수 줄 | 사람 아이콘 18 + "참석 3명" 15 Regular | "참석 6, 불참 1, 미정 2, 10.24 (금)까지 응답" Caption (`CrewScheduleTab.kt:491-499`) | 다름. 불참·미정·응답 마감은 앱에만 있음 |
| 내용 상자 | 배경 surfaceHigh, 모서리 10, px12 py10, 높이 40(한 줄 예시), 14 Regular lh21 | 내용이 제목과 한 덩어리(summary) | 누락 |
| 참석자 머리 | "참석자" 15 Bold lh18, 위 2 | 없음 | 누락 |
| 참석자 줄 | 높이 45, 간격 12, 아래 구분선 divider. 아바타 32 원(배경 surfaceHigh, 이미지 없음). 이름 15 Regular, 작성자는 뒤에 " 작성자" 12 Regular. 나는 "나" **Bold** | 없음(참석자 목록 데이터 없음) | 누락(기능) |
| 하단 버튼(미참석) | "참석하기" Lime Large h52 (pt12 px20 pb32). 피그마 프레임에서는 본문 높이 787 때문에 화면 밖으로 밀려 있다(배치 오류로 보임) | 참석·불참·미정 세 버튼(선택=Primary, 나머지 Secondary) (`CrewScheduleTab.kt:502-551`) | 다름 |
| 대기실 버튼 | 상세에 없음 | LobbyButton (`CrewScheduleTab.kt:455, 557-574`) | 앱에만 있음 |
| 신고 | 남의 글 ⋯ 메뉴 내용은 확인 못 함 | "신고" 텍스트 링크 (`CrewScheduleTab.kt:480-486`) | 확인 못 함 |

### F-13b 작성자 메뉴 (61771:5548)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 상태 | 내 글, 내가 참석 중: 작성 정보 "나 · 2시간 전", 참석자 첫 줄 "**나** 작성자", 참석 4명 | - | - |
| 하단 버튼(참석 중) | "참석 취소하기" White Medium: 높이 48, 알약, 배경 surface, 15 SemiBold textPrimary, 테두리 없음 | 참석 칩 해제 또는 시트의 "미정" (`CrewScheduleTab.kt:394-397`) | 다름 |
| ⋯ 메뉴 형태 | **드롭다운**(RunKK/Menu/모집글 드롭다운): 너비 90, 배경 surface, 테두리 1 `color/dropdown/stroke #c7ccbf`, 모서리 16, 그림자 0 4 12 rgba(0,0,0,0.18), 안쪽 py6. 위치 left 280 top 48.5(⋯ 바로 아래 오른쪽 정렬, 오른쪽 여백 20) | 상세 화면이 없어 없음. 크루 홈 ⋯는 M3 DropdownMenu 기본 모양(`crew/BoardParts.kt:80-95`) | 누락 |
| 메뉴 항목 | "수정하기" / 구분선 1px `color/menudivider #e9ede0` / "삭제하기", 각 px16 py13, 15 Regular lh18 textPrimary | 삭제는 시트 머리의 "삭제" 텍스트 링크(Caption, Lime), 수정 없음 (`CrewScheduleTab.kt:473-479`) | 다름 / 수정은 누락 |
| 메뉴 노출 조건 | 작성자 | canDelete(본인 또는 운영진 이상)면 삭제 링크 | 확인 못 함(운영진에게 피그마 메뉴가 보이는지) |

### F-13c 삭제 확인 (61771:5668)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 형태 | 가운데 **다이얼로그**, 딤 `rgba(23,27,18,0.45)`, 좌우 28 | RunkkConfirmDialog(M3 AlertDialog) (`CrewHomeScreens.kt:270-279`, `ds/RunkkFields.kt:364-397`) | 다름(모양) |
| 상자 | 너비 334, 배경 surface, 모서리 **28**, pt24 px20 pb20, 간격 10 | AlertDialog 기본(모서리는 테마 extraLarge 24, `ds/Theme.kt:84-90`) | 다름 |
| 제목 | "모집글을 삭제할까요?" 18 Bold lh26 textPrimary | "글을 지울까요?" CardTitle 16 Black (`strings:109`) | 다름 |
| 본문 | "삭제한 모집글은 되돌릴 수 없어요." 14 Regular lh21 **textPrimary** | "지운 글은 되돌릴 수 없어요." Body 14 TextSecondary (`strings:110`) | 다름 |
| 버튼 줄 | 위 10, 간격 10, 같은 높이 48. 왼쪽 "취소": 너비 143, 배경 surface, 테두리 1 divider, 알약, 15 SemiBold textPrimary. 오른쪽 "삭제": 나머지 폭, Purple(`#5c4bdb`) 채움, 15 SemiBold white | TextButton 두 개: "취소" BodyStrong TextSecondary, "삭제" BodyStrong Lavender 글자 | 다름 |
| 삭제 뒤 이동 | 확인 못 함(상세 화면이 닫히는지) | 크루 홈 목록에서 지움 | 확인 못 함 |

### F-13d 모집글 수정 (61771:5824)

**화면 없음.** 피그마 사양(스크린샷 기준, 틀은 F-12와 같음):

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 헤더 | "모집글 수정"(가운데), ⋯ 없음 | 없음 | 누락 |
| 칸 | F-12와 같은 6칸, 기존 값이 채워짐(제목, 내용, 10.24 (토), 오전 07:00, 5km, 매주 토요일), 값 글자 textPrimary | 없음 | 누락 |
| 날짜·시간 칸 | **수정 불가** 상태: 배경 surfaceHigh `#e9ede0`, 테두리 없음(스크린샷 기준), 글자 textSecondary | 없음 | 누락 |
| 버튼 | "저장" Lime Large h52 | 없음 | 누락 |

### F-13e 반복 선택 팝업(수정) (61771:5875)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 형태 | F-12f와 같은 반복 바텀시트(위끝 약 y=572) | 없음 | 누락 |
| 상태 | "반복 안 함" 고름 + 안내 "다음 모집글이 생성되지 않아요", "매주 토요일" 안 고름 | 없음 | 누락 |

## 4. 기능·데이터가 필요한 차이

| 항목 | 지금 | 필요한 것 |
|---|---|---|
| 일정 상세 화면(F-13) | 상세 API·화면 없음. 목록 응답(`PostList.Item`, `android/domain/.../crew/PostList.kt`)과 바텀시트로 대신함 | `Routes.CrewRunDetail(postId)` 같은 경로, 상세 UiState·ViewModel. 목록 응답만으로 그릴지, `GET /posts/{id}` 같은 상세 API를 둘지 결정 필요(서버 `CrewPostController`에 단건 조회 없음) |
| 참석자 목록 | 서버·앱 모두 없음(`goingCount`만 있음) | 응답에 참석자(이름, 회원ID, 작성자 여부, 나 여부, 아바타) 목록. 지난 일정(F-11d)은 참가자별 달린 거리까지. 지난 거리 데이터는 `crewRunResult(postId)`(`CrewRunResult.participants`)에 이미 있다 |
| 제목·내용 분리 | 앱이 제목과 내용을 줄바꿈으로 합쳐 `body` 하나로 보낸다(`android/data/.../api/crew/CrewMappers.kt:39`), 목록 `summary`는 body 전체(`CrewMappers.kt:140`) | 상세 제목(20 Bold)과 내용 상자를 나누려면 첫 줄 분리 규칙을 앱에서 정하거나 서버에 title 필드 추가 |
| 작성 경과 시간 "2시간 전"·"종료된 일정" | `writtenAt` 있음. 문구 형식 없음 | 상대 시간 문구 함수(서버 변경 불필요) |
| 반복(매주 n요일) | 서버·도메인·UiState 모두 없음(백엔드 grep "repeat/recurr" 결과 없음) | 서버: 반복 규칙 저장, 다음 모집글 자동 생성(피그마 문구상 매주 일요일 생성) 스케줄러, 응답에 반복 여부. 앱: `CrewRunForm`·`CrewRunWriteUiState`에 repeat 필드, 상세에 "매주" 배지 |
| 거리 "제한 없음"·"하프", 1km 제외 | 서버가 1·3·5·10km만 받는다(`backend/.../web/crew/CrewDtos.java:80-86`, `StandardDistance.isCrewRun`), 앱도 같음(`crew/CrewSupport.kt:26-27`) | 서버 거리 규칙 변경(제한 없음 = null 등), 앱 `Distance` 표현, 목록·상세 "제한 없음" 표기. 대기실·합산·레이스 기여 계산이 거리 없는 경우를 처리하는지 확인 필요 |
| 날짜 범위 15일 | 앱 7일(`CrewRunWriteUiState.DAYS = 7`) | UiState를 dayOffset 대신 LocalDate로, 범위 오늘~14일 뒤. 서버 상한 유무 확인 필요(확인 못 함) |
| 시간 5분 단위·12시간제 | 30분 단위 목록 | UiState 시간 선택 단위 변경(서버 제약은 없어 보임, 확인 못 함) |
| 모집글 수정(F-13d) | 서버에 글 수정 API 없음, `CrewRepository`에 없음 | `PATCH /posts/{id}`(제목·내용·거리·반복, 날짜·시간은 고정), 권한(작성자만?), 앱 수정 화면·Route·ViewModel |
| 지난 크루런 화면·월 필터(F-11b·F-11c) | 목록 API는 종류·커서만 받는다(`CrewDtos.PostListQuery`, `CrewDtos.java:1050`). 앱은 한 목록에서 상태로 나눔(`CrewScheduleTab.kt:117-121`) | 지난 크루런 전용 조회(상태=끝남/취소, 년·월 기간) 파라미터 또는 앱 쪽 거르기 + 페이지 넘김. 별도 Route·UiState(선택 년월) |
| 참석/참석 취소 두 상태 | 앱·서버는 참석·불참·미정 세 응답(FR-145) | 피그마대로 하면 "참석하기"=GOING, "참석 취소하기"=UNDECIDED 또는 NOT_GOING 중 어느 값으로 보낼지 결정 필요 |
| 작성자 ⋯ 메뉴 노출 | `isMine`, `canDelete` 있음 | 수정 권한 필드(작성자만인지) 필요 |

## 5. 화면만 고치면 되는 차이

- 공통 토큰: background `#f7f8f2`, textPrimary `#171b12`, textSecondary `#67705b`, divider `#e9ede0`, surfaceHigh `#e9ede0` 추가, dropdown stroke `#c7ccbf`, 딤 0.45. 글꼴 IBM Plex Sans KR(Bold 위주, Black 사용 중단).
- 공통 헤더: 가운데 19 Bold 제목, 원 없는 뒤로가기 40, ⋯ 44(more_horiz 아이콘).
- 공통 바텀시트(위 모서리 28, 제목+✕, Lime Medium 확인 버튼)와 휠 피커, 달력 시트, 라디오 목록 시트 컴포넌트 신설.
- F-11: 헤더 크루 기본 이미지·여백, 탭 줄 색·크기, "+ 크루런 모집하기" 칩(h28, 테두리 없음), "지난 크루런 ›" 글자 색·크기·늘 보이기, 히어로 날짜 문구 "오늘 · 10.21(수) 19:30", 줄 보조문구 구분자 " · ", 미참석 칩 14 Medium, 목록 끝 지난 크루런 묶음과 "이후" 묶음 제거 여부, 본문 여백 20.
- F-12: 칸 순서, 라벨 13 Medium textPrimary, 입력 상자 테두리 divider·안쪽 12/14·글자 15, 내용 높이 72·모서리 16, 자리문구 문구, 날짜·시간·거리 칸을 입력 상자 모양 + 시트로, 기본값 비우기, 올리기 버튼 h52 알약·구분선 제거.
- F-13c: 다이얼로그 문구("모집글을 삭제할까요?", "삭제한 모집글은 되돌릴 수 없어요."), 모서리 28, 취소(외곽선)·삭제(보라 채움) 두 알약 버튼.
- F-13b: 드롭다운 메뉴 모양(너비 90, 모서리 16, 테두리, 그림자, 구분선).

## 6. 앱에 없는 피그마 에셋

| 노드 이름 | 노드 id | 쓰는 곳 | 앱 상태 |
|---|---|---|---|
| groups(크루 기본 이미지 아이콘), RunKK/Avatar/크루 기본 이미지 | 61869:4925 / 컴포넌트 61869:4933 | F-11 헤더 | 없음(앱은 이니셜) |
| more_horiz | I61865:12414;61865:12375, I62183:56456;61865:12375 (컴포넌트 RunKK/Buttons/아이콘 61865:12380) | F-11 헤더, F-13 헤더 | 없음(앱은 Canvas 점) |
| Icon - plus | 61771:4417 | F-11 모집하기 | RunkkIcons.Plus 있음(크기 12로 써야 함, 같은 모양인지 확인 못 함) |
| arrow_back(RunKK/Buttons/뒤로가기 Icon) | 61831:9676 (컴포넌트 61831:9677) | 모든 F 헤더 | RunkkIcons.Back 있음(원 배경 제거 필요) |
| Chevron(기간 필터 아래 꺾쇠 12×6) | 61771:4624 | F-11b | RunkkIcons.ChevronDown 있음(같은 모양인지 확인 못 함) |
| 상세 일시 아이콘(달력) | 61771:5434 | F-13 정보 카드 | RunkkIcons.Calendar 있음(같은 모양인지 확인 못 함) |
| 상세 거리 아이콘(경로) | 61771:5442 | F-13 정보 카드 | RunkkIcons.Route 있음(확인 못 함) |
| 상세 참석 아이콘(사람 둘) | 61771:5449 | F-13 정보 카드 | RunkkIcons.Users 있음(확인 못 함) |
| Radio(선택됨, 보라 테두리+점) | 61771:5391 | F-12f, F-13e | 없음 |
| RunKK/Calendar/이동 화살표(‹ ›, 글자) | 컴포넌트 62051:7073 | F-12b·c | 없음(글자라 에셋 아님) |
| RunKK/Calendar/오늘 표시 | 62158:12554 | F-12b | 없음(원 모양, 에셋 아님) |
| RunKK/Menu/모집글 드롭다운 | 62159:12503 | F-13b | 없음(컴포넌트) |
| 하단 내비 아이콘(groups, directions_walk, Home, 랭킹, 마이) | I61771:4482;60959:2218 외 | F-11 하단 | RunkkBottomBar가 따로 구현(이 보고서 범위 밖) |

## 7. 문서 충돌

| 문서 | 문서 내용 | 피그마 | 비고 |
|---|---|---|---|
| `docs/sw/srs/03-05-크루-관리.md:285-293` FR-132 | 크루런 거리는 **1·3·5·10km 중 하나** | 제한 없음·3·5·10km·하프(1km 없음) | 충돌. 서버 `CrewDtos.crewRunDistance`도 문서대로 |
| FR-132 | 반복 일정 언급 없음 | 반복(매주 n요일, 자동 생성) | 문서에 없음 |
| FR-132·FR-134·FR-213 | 일정 작성·삭제만 정의, **수정 없음** | 모집글 수정(F-13d), 날짜·시간 고정 | 문서에 없음 |
| `srs/03-05-크루-관리.md:561-568` FR-145 | 응답은 **참석·불참·미정** 셋, 응답 마감 일시 | 참석하기/참석 취소하기 둘, 응답 마감 표시 없음 | 충돌 |
| `srs/03-05-크루-관리.md:297-305` FR-133 | 일정마다 참석·불참·미정 인원과 본인 응답, 크루런 상태(투표 중·대기실 열림·진행 중·지난)를 보인다. 지난 크루런은 "n명 참여"와 **결과(FR-191)로 가는 길** | 목록·상세에 참석 수만, 상태 표시 없음. 지난 일정은 상세(F-11d, 참석자별 거리)로 간다 | 충돌(상태 표시, 지난 크루런 진입처) |
| FR-133 | 지난 크루런 조회 범위 언급 없음 | 년·월 기간 필터 | 문서에 없음 |
| 피그마 F-12f 안내 | - | "매주 토요일" 선택인데 "매주 **일요일**에 다음 모집글이 자동으로 생성돼요" | 피그마 내부 문구 확인 필요(생성 시점이 일요일인지, 오타인지) |
| 피그마 날짜 형식 | - | "10.21(수)"(F-11, F-11b, F-11d)와 "10.24 (토)"(F-12, F-13)가 섞여 있다 | 앱 `crew_schedule_pattern`은 "M.d (E) HH:mm". 하나로 정해야 함 |
