# 크루 화면 피그마 대비 차이 (F-01 ~ F-13)

- 피그마: fileKey `NMTtVMWIT22D0CwOU2y0I9`, 프레임 390x844, 라이트 테마 기준.
- 앱: 저장소 develop 브랜치(d3b9d4dd 기준). 경로 약어 `crew/` = `android/feature/crew/src/main/java/com/a207/runkk/app/ui/crew/`, `ds/` = `android/core/ui/src/main/java/com/a207/runkk/app/core/designsystem/`, `res` = `android/feature/crew/src/main/res/values/strings.xml`.
- 차이 종류: **누락**(피그마에 있고 앱에 없음), **다름**(둘 다 있으나 값이 다름), **앱에만 있음**.
- F-01~F-08은 피그마 `get_design_context`(기본 화면·팝업 노드)와 스크린샷으로, F-09~F-13은 같은 방법으로 별도 확인한 결과를 합쳤다.

## 0. 공통(모든 크루 화면에 걸리는 차이)

먼저 공통 토큰·컴포넌트를 피그마에 맞추면 화면별 항목 상당수가 함께 풀린다.

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 라이트 색 토큰 | background `#F7F8F2`, surface `#FFFFFF`, surfaceHigh `#E9EDE0`, divider `#E9EDE0`, textPrimary `#171B12`, textSecondary `#67705B`, primary(라임) `#CAEB2E`, onPrimary `#171B12`, secondary(보라) `#5C4BDB`, color/barCrew `#BFC6B3`, tagPurple bg `#EBE7FC`/text `#5C4BDB`, tag LimeTint bg `#F2F9D0`/text `#3D4A00`, Dim `rgba(23,27,18,0.45)` | `ds/RunkkColors.kt:128-163` light: background `#FAFAFA`, border `#E4E4E7`, textPrimary `#18181B`, textSecondary `#52525B`, limeFill `#CBEA2B`, lavender/violet `#5B4FD9`. surfaceHigh·barCrew·tagPurple·LimeTint 토큰 없음 | 다름·누락 |
| 글꼴 | IBM Plex Sans KR(Regular/Medium/SemiBold/Bold) | `ds/RunkkType.kt:26` Noto Sans KR(400/500/700/900), 숫자는 Barlow Condensed. `android/core/ui/src/main/res/font`에 IBM Plex 없음 | 다름 |
| 글자 굵기 | 제목·강조 Bold(700), 버튼 SemiBold(600) | 앱은 Black(900)을 많이 씀(`RunkkTextStyles.TopBarTitle`·`CardTitle`·`Button`·`Headline`, `ds/RunkkType.kt:75-102`) | 다름 |
| 상단 바(서브 화면) | `RunKK / Header` 57h, 좌우 12, 뒤로 `arrow_back` 24 아이콘만(배경 원 없음, textPrimary, 터치 40x40), 제목 **가운데** Bold 19/23 | `ds/RunkkBars.kt:77-121` RunkkTopBar 최소 64h, 좌우 16, 뒤로는 보라 채움 원 + 흰 셰브론(`RunkkIcons.Back`), 제목 **왼쪽** 18 Black | 다름 |
| 기본 버튼 | `RunKK / Buttons / 기본` 알약(radius 100). Large 52h 16 SemiBold, Medium 48h 15 SemiBold, Small 36h 14 Medium, 좌우 16. Lime=primary 채움, Gray(비활성)=surfaceHigh 채움+textSecondary 글자, Outline=surface+divider 1px | `ds/RunkkButtons.kt:54-57,74-` RunkkPrimaryButton 56h, radius 16, 16 Black. 알약 버튼 40h radius 20(`:239-`, `:292-`) | 다름 |
| 하단 고정 CTA 영역 | 상단 12·좌우 20·하단 32 여백, 구분선 없음 | `crew/CrewWriteScaffold.kt:68-74`, `crew/CrewFindScreens.kt:542-553` 구분선(HorizontalDivider) + 좌우 16 | 다름·앱에만 있음(구분선) |
| 본문 좌우 여백 | 20 | 대부분 16(`crew/CrewFindScreens.kt:210`, `crew/CrewHomeScreens.kt:210`, `crew/CrewWriteScaffold.kt:55`) | 다름 |
| 카드 | surface 채움 + divider 1px, radius 24(목록·통계) / 28(레이스 카드), padding 12~14 | `ds/RunkkCards.kt:78-` RunkkCard 기본 radius 20, padding 16, 테두리 `outlineVariant`(=border `#E4E4E7`) | 다름 |
| 크루 기본 이미지 | `RunKK / Avatar / 크루 기본 이미지`: surfaceHigh 바탕 + Material `groups` 아이콘(textSecondary). 48(r14, 아이콘26) / 50(r15, 28) / 74(r24, 40) / 80(원형, 44) | `crew/CrewComponents.kt:40-57` CrewLogo: 크루명 첫 글자, radius=size/4, isAccent면 primaryContainer 바탕 | 다름(아이콘 대신 글자) |
| 크루 태그(목록용 Small) | `RunKK / Tag` Outline Small: divider 1px 테두리, radius 999, 좌우 8·상하 2, Regular 11/13 textPrimary | 목록은 태그를 쉼표로 이은 한 줄 텍스트(`crew/CrewFindScreens.kt:476-484`) | 다름 |
| 크루 태그(상세·조건용 Medium) | 평일=PurpleTint(bg `#EBE7FC`, 글자 `#5C4BDB`), 주말=LimeTint(bg `#F2F9D0`, 글자 `#3D4A00`), 28h, radius 14, 좌우 14, Medium 14 | `crew/CrewComponents.kt:188-205` + `ds/RunkkChips.kt:303-320` RunkkBadge: 28h, radius 8, 좌우 8, 12 Regular. 평일=Surface+textPrimary, 주말=LimeContainer `#ECF5D0`+Lime `#3F6212` Bold | 다름 |
| 시간대 칩 | `RunKK / Chip / 시간대 칩` 32h, radius 16, 좌우 12, 13. 선택 안 됨=divider 1px 테두리+textPrimary Regular, 선택=**textSecondary(`#67705B`) 채움 + surfaceHigh 글자 Bold**, 체크 아이콘 없음 | `crew/CrewComponents.kt:176-180` RunkkChoiceChip(`ds/RunkkChips.kt`) 40h, radius 20, 선택 시 라임 채움+체크 | 다름 |
| 세그먼트(2·3탭) | `RunKK / Control`: surfaceHigh 바탕, padding 4, radius 22, 탭 36h radius 18. 선택 탭 = 흰색(2탭 폼) 또는 primary 라임(크루 홈 3탭), Bold 14, 미선택 Regular 14 | `ds/RunkkChips.kt:385-409` RunkkTabRow·RunkkSegmentRow: Surface 바탕 radius 16, 칸 40h radius 12, 선택 라임, 13 Black | 다름 |
| 바텀시트 | 상단 radius 28, padding 상24·좌우20·하32, 헤더(제목 Bold 18 + `✕` 18) 사이 간격 20, **드래그 핸들 없음** | Material `ModalBottomSheet` 기본(드래그 핸들 있음, 기본 모서리), 내부 padding 좌우20·하28(`crew/CrewFindScreens.kt:378-381`) | 다름 |
| 하단 내비 | `RunKK · Navigation / Floating` 354x90(크루·기록런·홈·랭킹·마이) | 앱 공용 하단 바(이 문서 범위 밖) | 확인 못 함 |

## F-01 크루 찾기·조건 설정 전 (61769:4116)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 머리글 | 57h, 좌20·우12. "크루" Bold 18/22. 오른쪽 Outline Small "초대 코드"(36h, w77) + 간격 8 + Lime Small "크루 만들기"(36h, w87, **아이콘 없음**) | `crew/CrewFindScreens.kt:278-297` "크루" Headline(24 Black), RunkkPillButton 40h + RunkkLimePillButton 40h(**`+` 아이콘 있음**) | 다름 |
| 목록 영역 여백 | 위 11, 좌우 20, 항목 간격 12, 아래 120 | `crew/CrewFindScreens.kt:209-216` 위16·좌우16·간격16 | 다름 |
| 검색 바 | `RunKK / Search / 크루 검색 바` 46h, radius 23(알약), surface+divider 1px, 좌우 12, 돋보기 18, 간격 8, 자리 문구 "크루명 검색" Regular 14 textSecondary | `crew/CrewFindScreens.kt:300-344` 48h, radius 16, InputBorder `#71717A` 1px, 좌우14, 아이콘20, 간격10, 15. 문구 "크루명, 한줄소개 검색"(`res:56`) | 다름(문구·모양) |
| 검색 동작 | 입력 후 별도 화면 F-01d로 이동(아래) | 같은 목록 화면에서 키보드 검색 단추로 다시 읽음(`crew/CrewFindScreens.kt:310-311`) | 다름 |
| 조건 설정 칸 | surface+divider, radius 24, padding 12, 간격 8. "조건 설정하기" Bold 14/17. 안내 "나와 비슷한 시간대에 뛰는 크루를 찾아보세요" Regular 13/16 textSecondary | `crew/CrewFindScreens.kt:347-371` RunkkCard(radius 20, padding 16). 안내 "요일과 시간대를 선택해 조건에 맞는 크루를 찾아보세요"(`res:68`), Caption 12 | 다름(문구·여백) |
| 목록 제목 줄 | "전체 크루" Bold 14/17 + 오른쪽 **새로고침 단추**(32x32 원, divider 1px, "↻" Bold 16) | `crew/CrewFindScreens.kt:407-418` 제목만(13 Bold), 새로고침 단추 없음 | 다름·누락 |
| 크루 카드 | surface+divider, radius 24, 좌우14·상하12, 간격12. 왼쪽 크루 기본 이미지 48. 가운데 세로 간격 3: 크루명 Bold 14/17 → 한줄 소개 Regular 13/18.2 textPrimary **한 줄 말줄임(너비 200)** → 태그 Outline Small 줄(간격 4) | `crew/CrewFindScreens.kt:454-489` RunkkCard(radius 20, padding16), 간격16, CrewLogo(글자), 크루명 15 Black, **소개 대신 "크루장 X, 4/10명, 이번 주 12.3km" 메타**(`:491-497`), 태그는 쉼표 텍스트 | 다름 |
| 카드 오른쪽 | 인원 "4/10" Bold 18/22 textPrimary(오른쪽 13, 세로 가운데) | 가입 방식 배지 "승인제"/"바로 가입"(`crew/CrewFindScreens.kt:486`) | 다름(인원 누락, 배지 앱에만 있음) |
| 대기 중인 가입 신청 카드 | 피그마 F-01에 없음 | `crew/CrewFindScreens.kt:245-249,424-452` PendingJoinCard(크루명, "가입 신청 대기 중, …", "승인 대기" 배지, "취소") | 앱에만 있음(피그마 상태 확인 못 함) |
| 빈 목록 | (F-01에 없음, F-01e 참고) | `crew/CrewFindScreens.kt:260-266` "조건에 맞는 공개 크루가 없어요" + "크루 만들기" 동작 | 확인 못 함 |

## F-01b 조건 설정 팝업 (61769:4273, 시트 61769:4380)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 형태 | 바텀시트(상단 radius 28, padding 24/20/32, 섹션 간격 20), Dim `rgba(23,27,18,0.45)`, 드래그 핸들 없음 | `crew/CrewFindScreens.kt:374-404` ModalBottomSheet(핸들 있음), 간격 16, 하단 28 | 다름 |
| 헤더 | "조건 설정하기" Bold 18/22 왼쪽, `✕` 글자 18 오른쪽(배경 없음) | 제목 CardTitle 16 Black + RunkkIconButton(테두리 원) Close | 다름 |
| 행 구성 | "평일"/"주말" Bold 14/17 + 간격 12 + 칩 4개(간격 8). 행 간격 12 | `crew/CrewComponents.kt:153-186` 라벨 BodyStrong 고정폭 44, 칩 간격 6, 행 간격 8 | 다름 |
| 칩 | 공통 "시간대 칩" 참고(32h, 선택=`#67705B` 채움+`#E9EDE0` Bold 13) | RunkkChoiceChip(40h, 라임+체크) | 다름 |
| 적용 단추 | Lime Medium 48h 알약 전체 폭 "적용하기" SemiBold 15 | RunkkPrimaryButton 56h radius16 "적용하기" | 다름(크기·모양) |

## F-01c 조건 설정 후 (61769:4466)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 조건 칸 | 제목 아래 고른 조건을 **Medium 태그**(평일=PurpleTint, 주말=LimeTint, 28h r14 좌우14 Medium 14), 간격 8, 한 줄 | `crew/CrewFindScreens.kt:367-369` CrewTagBadges(RunkkBadge, 평일=흰 배지, 주말=연라임, 간격 8, FlowRow) | 다름(색·모양) |
| 목록 제목 | "조건에 맞는 크루" Bold 14 + 새로고침 단추 | `res:58` 문구 같음. 새로고침 없음 | 누락(새로고침) |
| 조건 지우기 | 피그마에 별도 지우기 단추 없음(팝업에서 해제) | 같음 | 같음 |

## F-01d 크루명 검색 (61769:4649)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 화면 | 별도 "크루 검색" 화면(하단 내비 없음). 머리글 57h 좌20·우12: `‹` Regular 28/34 + 간격 8 + "크루 검색" Bold 18/22(왼쪽 정렬) | 없음(검색은 F-01 목록 안에서 처리, `crew/CrewFindScreens.kt:242`) | **화면 없음** |
| 검색 바 | 46h 알약, 입력값 Regular 14 textPrimary, 오른쪽 `✕` 지우기(Regular 14 textSecondary, 오른쪽 끝) | 지우기 단추 없음 | 누락 |
| 결과 수 | "검색 결과 [n]개" Bold 13/16 | 없음 | 누락 |
| 목록 | F-01 카드와 같은 모양(간격 12) | (F-01 카드 참고) | — |
| 조건 칸·새로고침 | 검색 화면에는 없음 | — | — |

## F-01e 검색 결과 없음 (61769:4714)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 빈 상태 | "검색 결과 0개" 아래 높이 420 가운데: "검색 결과가 없어요" Medium 16/19 + 간격 8 + "크루명을 다시 확인하거나\n조건 설정으로 크루를 찾아보세요" Regular 13/16 가운데, 단추 없음 | `crew/CrewFindScreens.kt:260-266` RunkkEmptyState "조건에 맞는 공개 크루가 없어요" + "크루 만들기" 단추 | **화면 없음**(검색 화면 자체 없음), 문구 다름, 단추 앱에만 있음 |

## F-02 크루 상세·크루 밖 (61769:4736)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 상단 바 | 공통 Header "크루 정보"(가운데) | `crew/CrewDetailPage.kt:41` RunkkTopBar(왼쪽 제목, 보라 원 뒤로) | 다름 |
| 본문 여백 | 위10·좌우20·아래16, 섹션 간격 14 | `crew/CrewDetailPage.kt:72-73` padding 16, 간격 16 | 다름 |
| 프로필 줄 | **가로 배치**: 크루 기본 이미지 74(r24) + 간격 14 + [크루명 Bold 19/23, 간격 6, "[yyyy. mm. dd] · 7명" Regular 13/16] | `crew/CrewDetailPage.kt:75-96` **가운데 세로 배치**: CrewLogo 72(라임 강조, 글자) / 크루명 18 Black / "4/10명, 승인 후 가입" Caption | 다름 |
| 개설일 | "yyyy. mm. dd" 개설일 표시 | 없음(도메인 `CrewDetail`·서버 `CrewDetailView`에 createdAt 없음) | 누락(데이터 필요) |
| 인원 표기 | "7명"(정원 없이) | "4/10명" + 가입 방식 | 다름 |
| 소개 | Regular 16/22.4 textPrimary, 좌우 안쪽 5 | `crew/CrewDetailPage.kt:97` Body 14 | 다름 |
| 태그 | Medium 태그(Purple/LimeTint), 간격 8 | CrewTagBadges | 다름(공통 태그 참고) |
| 통계 칸 | **2칸 그리드**(각 170, 간격 10): "지난 주 달린 거리"/"64.27km", "누적 크루런 횟수"/"[n]회". 칸 = surface+divider, r24, padding 14, 간격 6, 이름 Regular 12/18, 값 Bold 20/24 | `crew/CrewDetailPage.kt:99` CrewStatCard 1칸(지난 주 거리)만. 값 20 Black | 다름·누락(누적 크루런 횟수) |
| 최근 크루런 | 제목 "최근 크루런" Bold 14/22 + 행 3개(surface+divider, r24, padding14, 간격6): 날짜 "10.21 (수) 저녁 7:30" Regular 12/18 + "[n]명 참여 · 총 [n]km" Bold 13/16 | 없음 | 누락(데이터 필요) |
| 하단 단추(승인제) | 영역 상12·좌우20·하32, Lime Large 52h 알약 "가입 신청하기" | `crew/CrewDetailPage.kt:101-128` RunkkPrimaryButton 56h, 문구 같음 | 다름(크기) |
| 가입 신청 대기 상태 | 피그마 없음 | `crew/CrewDetailPage.kt:131-163` "가입 신청을 보냈어요"/"M.d까지 결정돼요" + "신청 취소" | 앱에만 있음 |
| 정원 참 | 피그마 없음 | "정원이 찼어요" 비활성(`crew/CrewDetailPage.kt:114-124`) | 앱에만 있음(피그마 상태 확인 못 함) |

## F-02b 가입하기(바로 가입 크루) (61769:4818)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 하단 단추 문구 | "가입하기" | "바로 가입하기"(`res:81`, `crew/CrewDetailPage.kt:119`) | 다름 |
| 나머지 | F-02와 같음 | — | — |

## F-02c 24시간 가입 제한 (61769:4900)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 하단 단추 | Gray(비활성) Large 52h: surfaceHigh 채움, textSecondary 글자 "[hh:mm:ss] 후에 가입할 수 있어요"(**남은 시간 카운트다운**) | 없음. 가입을 누른 뒤 서버 오류 COOLDOWN을 안내 줄 "크루를 나온 뒤 24시간 동안은 가입하거나 만들 수 없어요"로만 보임(`crew/CrewSupport.kt:46`, `res:6`) | 누락(상태·데이터 필요) |
| 통계 이름 | 이 프레임만 "**이번 주** 달린 거리" | "지난 주 달린 거리" | 피그마 내부 불일치(F-02·F-02b는 "지난 주"). 확인 필요 |

## F-03 초대 코드 입력 (61769:4976, 시트 61769:5083)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 형태 | F-01 위 **바텀시트**(Dim 0.45, 상단 r28, padding 24/20/32, 간격 20) | `crew/CrewInvitePage.kt:39-66` **전체 화면**(RunkkTopBar "초대 코드로 가입" + Headline "초대 코드를 입력해 주세요") | 다름 |
| 헤더 | "초대 코드로 가입" Bold 18 + `✕` | 상단 바 제목 + 큰 안내 문구(앱에만 있음) | 다름 |
| 코드 칸 | 6칸, 각 **44x56 고정**, 간격 8, radius 12, divider 1px, 글자 Bold 22/26 가운데(빈 칸은 테두리만), 왼쪽 정렬(남는 폭은 비움) | `crew/CrewInvitePage.kt:69-115` weight(1f)로 폭 채움, 비율 0.82, radius 12, 기본 InputBorder 1px, **현재 칸 라임 2px**, Barlow display 28 | 다름 |
| 확인 단추 | Medium 48h 알약 **"확인"**. 6자 미만이면 Gray(surfaceHigh+textSecondary) | RunkkPrimaryButton 56h **"가입하기"**(`res:86`), 6자 미만 비활성 | 다름(문구·흐름) |
| 흐름 | 확인 → 코드로 크루 조회 → F-03c 미리보기 → "가입하기" | 확인 단계 없이 바로 가입 요청(`CrewFindViewModel.onInviteSubmit`) | 다름(기능 필요) |

## F-03b 일치하는 크루 없음 (61769:5157, 시트 61769:5264)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 칸 오류 표시 | 6칸 모두 surfaceHigh 채움 + 테두리 **`#615D67` 1.5px** | 없음 | 누락 |
| 오류 문구 | 칸 아래 "일치하는 크루가 없어요. 초대 코드를 다시 확인해 주세요." Regular 13/16 textPrimary. 시트 간격 12 | 안내 줄(스낵바) "초대 코드가 없거나 7일이 지났어요"(`res:10`) | 다름(위치·문구) |
| 단추 | Lime "확인"(다시 시도 가능) | — | 다름 |

## F-03c 크루 확인 (61769:5340, 시트 61769:5447)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 크루 미리보기 카드 | 코드 칸 아래 카드: divider 1px, r16, padding 16, 간격 12. [이미지 74(r24) + 간격14 + 크루명 Bold 19/23 · "[yyyy. mm. dd] · 7명" Regular 13/16] / 소개 Regular 14/19.6 / Medium 태그 줄(간격 8) / 오른쪽 `›` Regular 22(눌러 크루 상세로 가는 것으로 보임) | 없음 | **화면 없음**(기능 필요) |
| 단추 | Lime Medium 48h "가입하기" | — | 누락 |

## F-04 크루 만들기 (61769:5551)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 상단 바 | 공통 Header "크루 만들기"(가운데) | `crew/CrewFindScreens.kt:527` RunkkTopBar | 다름 |
| 본문 여백 | 위11·좌우20·아래16, 항목 간격 12 | `crew/CrewFindScreens.kt:529-530` padding16, 간격16(필드 사이 `crew/CrewComponents.kt:62` 16) | 다름 |
| 로고 등록 | 크루명 왼쪽 **66x66** 칸: surfaceHigh 채움, divider **점선** 1px, r24, 가운데 `add` 아이콘 28 | 없음(도메인 `CrewForm`에 logo 없음. 서버 요청은 `logoUrl` 받음 `backend/.../web/crew/CrewDtos.java:114-125`) | 누락(기능 필요: 이미지 고르기·업로드) |
| 크루명 칸 | 이미지 오른쪽, 라벨 "크루명" Medium 13/16, 간격 9, 입력 min 48h, r12, surface+divider, 좌우12·상하14, 자리 문구 **"2~12자"** Regular 15/18 textSecondary | `crew/CrewComponents.kt:63-69` RunkkTextField 전체 폭, 자리 문구 "2~20자"(`res`), NAME_MAX 20(`crew/CrewFormState.kt:23`) | 다름(배치·문구·길이 규칙, 문서 충돌 참고) |
| 한 줄 소개 | 라벨 **"한 줄 소개"**, 자리 문구 **"최대 40자"** | 라벨 "한줄 소개", 자리 문구 "어떤 크루인지 한 줄로"(`res`) | 다름(문구) |
| 태그 머리 | "태그"(Medium 13) + " 최대 3개"(Regular 13) 한 줄 + 안내 "크루에서 주로 뛰는 시간대를 선택해 주세요" Regular 12/16 textSecondary, 간격 4 | `crew/CrewComponents.kt:163-165` "태그 최대 3개" 12 Bold textSecondary 한 줄, 안내 없음 | 다름·누락(안내) |
| 태그 행 | "평일"/"주말" **Regular** 14 + 칩 4개, 모두 간격 8, 행 간격 8 | 라벨 BodyStrong 폭 44, 칩 간격 6 | 다름 |
| 공개 여부 | 라벨 "공개 여부" Medium 13 + 2탭 Control(surfaceHigh, 선택=흰 r18, Bold 14) "공개 / 비공개" | `crew/CrewComponents.kt:82-90` RunkkSegmentRow(라벨은 groupLabel 의미만, 보이는 라벨 확인 못 함) | 다름(모양) |
| 가입 방식 | 라벨 "가입 방식" + Control "바로 가입 / 승인 후 가입" | `crew/CrewComponents.kt:91-96` 같은 문구 | 다름(모양) |
| 하단 단추 | Lime Large 52h "크루 만들기", 구분선 없음 | `crew/CrewFindScreens.kt:542-553` 구분선 + 56h | 다름 |
| 오류 문구 | 피그마 없음 | `crew/CrewFindScreens.kt:533-540` 오류 텍스트(보라 13 Bold) | 앱에만 있음 |

## F-05 크루 홈·레이스 (61770:4195)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 머리글 | 위12·좌우20, 간격 12: 크루 기본 이미지 **50(r15)** + 크루명 Bold 17/20 + 더보기 `more_horiz` 24(터치 44, 배경 없음) | `crew/CrewHomeScreens.kt:367-397` padding 시작16/8·위8·아래12, CrewLogo 48(글자), 크루명 TopBarTitle 18 Black, MoreMenu 48(`crew/CrewMoreButton.kt:25`, 점 3개 직접 그림) | 다름 |
| 탭 줄 | 위14·좌우20. 3탭 Control 44h: surfaceHigh 바탕, padding 4, r22, 탭 36h r18, 선택 = primary 라임 + Bold 14 `#171B12`, 미선택 Regular 14 | `crew/CrewHomeScreens.kt:248-265` RunkkTabRow(Surface 바탕 r16, 칸 40h r12, 13 Black) | 다름 |
| 탭 내용 여백 | 위11·좌우20, 간격 12 | `crew/CrewHomeScreens.kt:210-211` padding 16, 간격 16 | 다름 |
| 목표 카드 틀 | surface+divider, **r28**, padding 14, 간격 8 | `crew/CrewRaceTab.kt:78-85` r24, padding 16 | 다름 |
| 목표 카드 제목 | **"이번 주 목표"** Regular 13/16 textPrimary | "이번 주 공동 목표"(`res:243`) Caption 12 textSecondary | 다름 |
| 마감 | "일요일 자정 마감" Regular 12/18 **textPrimary** | `crew/CrewRaceTab.kt:96-100` 12 Bold, `colors.secondary`(보라) | 다름 |
| 큰 숫자 | "[nn.n]" **Bold 30/30**(IBM Plex) + "/ 80 km" Regular 13 + 오른쪽 끝 "[nn]%" Bold 15 **textPrimary** | `crew/CrewRaceTab.kt:108-140` Barlow display **44**, "/ 80 km" Body 14 textSecondary, % 15 Black `colors.primary`(라이트에서 올리브 `#3F6212`) | 다름 |
| 진행 막대 | 8h, 트랙 surfaceHigh r4. 그 위에 **두 겹**: 전체 진행 = barCrew `#BFC6B3` 알약, 그 앞에 **내 기여 구간 = secondary `#5C4BDB`** 알약(왼쪽부터) | `crew/CrewRaceTab.kt:141-142` RunkkProgressBar 한 겹, 색 colorScheme.primary(라이트 `#3F6212`), 트랙 outlineVariant | 다름(내 기여 구간 누락) |
| 카드 아래 줄 | "참여 [n]명 · 내 기여 " Regular 12/18 + "nn%" **Bold 14** | `crew/CrewRaceTab.kt:150-158` "내 기여 Xkm"(`res`) Caption + 오른쪽 "목표 설정" 라임 텍스트 링크 | 다름(참여 인원 누락, km→%, 링크 위치) |
| 보드 제목 | **"러닝 보드"** Bold 14/17 | "기여도 보드"(`res:266`) CardTitle 16 Black | 다름 |
| 주간/월간 | 오른쪽 작은 세그먼트: surfaceHigh 바탕 padding 3 r18, 칸 30h 좌우12, 선택 = 흰 채움+divider 1px r15 Bold 12, 미선택 Regular 12 | `crew/CrewRaceTab.kt:278-286` RunkkSegmentedTabs isCompact(32h, 고른 칸 TabSelected 라임) | 다름 |
| 보드 행 | 행 간격 12, 행 안 간격 10: 32 원(surfaceHigh, 프로필 자리) / 이름 Regular 13/16 폭 64 / 막대 11.2h r5.6, 트랙 surfaceHigh, 채움 = 남 **barCrew `#BFC6B3`**, 나 **secondary `#5C4BDB`** / 거리 Bold 13 오른쪽 정렬 폭 52 | `crew/CrewRaceTab.kt:305-351` 간격16(부모), 원 32에 이름 첫 글자(나는 라임 강조), 이름 Body 14, 막대 14h r7, 트랙 outlineVariant, 채움 남 onSurfaceVariant `#52525B`·나 colorScheme.primary(라이트 `#3F6212`), 거리 폭 56 | 다름 |
| 보드 행 원 | 프로필 이미지 자리로 보임(빈 원) | 이름 첫 글자 | 확인 못 함(프로필 이미지 쓸지) |

## F-06 공동 목표 없음·크루장 (61770:4331, 카드 61770:4349)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 카드 제목 | "이번 주 달린 거리" Regular 13 | `res:244` 같은 문구, Caption 12 textSecondary | 다름(크기·색) |
| 목표 정하기 | 제목 줄 **오른쪽**에 "목표 정하기" Regular 13/16 textPrimary **밑줄**, 최소 높이 32 | 카드 맨 아래 오른쪽 "목표 설정"(`res:249`) 라임 RunkkTextLink(`crew/CrewRaceTab.kt:157`) | 다름(문구·위치·색) |
| 큰 숫자 | "42.34" Bold 30 + " km" Regular 13 | Barlow 44 + "km" Body | 다름 |
| 막대 | 목표가 없어도 막대 표시: barCrew 전체 폭 + 내 기여 구간(secondary, 내 기여율 폭) | 목표 없으면 막대 없음(`crew/CrewRaceTab.kt:141-149`) | 누락 |
| 아래 줄 | "참여 [n]명 · 내 기여 30%" | "내 기여 Xkm" | 다름 |
| 마감 문구 | 없음 | 없음(목표 없을 때 숨김) | 같음 |

## F-06b 공동 목표 없음·크루원 (61770:4466)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 목표 정하기 | 없음 | 없음 | 같음 |
| 안내 문구 | **없음**(막대와 "참여 · 내 기여"만) | "이번 주 공동 목표가 없어요"(`res:248`, `crew/CrewRaceTab.kt:143-149`) | 앱에만 있음 |
| 막대·아래 줄 | F-06과 같음 | 막대 없음, "내 기여 Xkm" | 누락·다름 |

## F-07 주간 공동 목표 설정 (61770:4598)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 상단 바 제목 | **"주간 목표"** | "주간 공동 목표"(`res:251`) | 다름 |
| 주 표시 | "10월 4주차" Bold 16/19 + 간격 6 + "10.19 (월) ~ 10.25 (일)" Regular 13/16 textPrimary | `crew/CrewGoalSetScreen.kt:65-75` "이번 주 10.19 (월) ~ 10.25 (일)"(`res:252`) Body textSecondary 한 줄 | 다름(주차 제목 누락) |
| 목표 거리 입력 | 라벨 "목표 거리" Medium 13/16, 간격 6. 입력칸 52h **알약(r26)** surface+divider 좌우12, 값 **Bold 20/24** + 간격 8 + "km" Bold 18/22 | `crew/CrewGoalSetScreen.kt:76-89` RunkkTextField(기본 모양) + "km" BodyStrong | 다름 |
| 빠른 선택 칩 | **없음** | 50·80·100km 칩(`crew/CrewGoalSetScreen.kt:90-105`, `CrewGoalSetUiState.QUICK_KM`) | 앱에만 있음 |
| 지난 목표 | 제목 **"지난 목표"** Bold 16/19 + **카드 3개(지난 3주)**. 카드: 채움 없음(배경 투명)+divider 1px, r28, padding 14, 간격 8. "10월 3주차" Regular 13 / 오른쪽 "달성 실패"·"달성" Bold 12 textPrimary / "72.0" Bold 22/26 + "/ 80 km" Regular 13 + 오른쪽 "90%" Bold 15 / 막대 8h(트랙 surfaceHigh, 채움 barCrew, 100% 넘으면 꽉 참) / "참여 6명" Regular 12/18 | `crew/CrewGoalSetScreen.kt:106,110-161` **지난 주 1개**만. CrewCard(surface 채움 r20). 머리 "지난 주 결과"(Caption) + "달성"/"달성 못 함"/"목표 없음"(Caption, 달성은 primary 색) / "72.0 / 80 km" BodyStrong 한 줄 / 막대 primary / "참여 6명" | 다름·누락(3주·주차명·% 표시) |
| 달성 실패 문구 | "달성 실패" | "달성 못 함"(`res:260`) | 다름 |
| % 표시 | 100 넘는 값 그대로(106%, 101%) | 지난 주 카드에 % 없음 | 누락(문서 충돌 참고) |
| 하단 단추 | Lime Large 52h "저장", 구분선 없음 | WriteScaffold 56h + 구분선(`crew/CrewWriteScaffold.kt:68-74`) | 다름 |

## F-08 공동 목표 달성·접힘 (61770:4847)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 달성 배너 | 목표 카드 **위에** 접힌 띠(Section "공동 목표 달성 축하 카드", 노드 61770:4865): surfaceHigh 채움 + divider 2px, r28, 좌우18·상하14, 왼쪽 "10월 4주 목표 달성" Bold 12/18, 오른쪽 "자세히 보기" Regular 12/18. 누르면 F-08b 팝업 | 달성 주에는 목표 카드를 **축하 카드로 바꿔** 크게 보임(`crew/CrewRaceTab.kt:61-66,166-231`) | 다름(배너 없음) |
| 목표 카드 | 달성 주에도 그대로 보임. 마감 자리에 **"달성"** Bold 12/18 | 달성 주에는 목표 카드 숨김 | 누락 |
| 달성 % | "84.23 / 80 km" + **"105%"**(100 넘김) | goalProgressLabel이 100에서 자름(`crew/ContributionBar.kt` goalProgressLabel) | 다름(문서 충돌 참고) |
| 보드 머리 | "러닝 보드" + 오른쪽 "이번 주" Regular 12/18 | "기여도 보드" + "이번 주"(`res:267`) Caption | 다름(제목) |

## F-08b 축하 팝업 (61770:4682, 팝업 61770:4784)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 형태 | Dim 위 **가운데 카드 팝업**(좌우 20, 350 폭): surface + divider 2px, r28, padding 20, 간격 14, 가운데 정렬 | 레이스 탭 안의 인라인 카드(primaryContainer 바탕 + primary 테두리, r24, padding 20)(`crew/CrewRaceTab.kt:175-184`) | 다름(팝업 아님) |
| 주차 | "10월 4주" Bold 14/18 textPrimary | "10월 4주 공동 목표 달성"(`res:271`) 12 Bold primary | 다름 |
| 그림 | 크루 기본 이미지 80 **원형**(surfaceHigh, groups 44) | 64 원 primary 채움 + 트로피 아이콘 32 | 다름 |
| 문구 | "[크루명]이\n함께 목표를 **달성했어요**" Bold 22/30 가운데 | "%s이\n함께 목표를 **넘었어요**"(`res:272-273`) 20 Black | 다름(문구·크기) |
| 숫자 3칸 | 3칸 그리드(각 96.66, 간격 8), 칸 padding 좌우4·상하10, 값 Bold 18/22 + 이름 Regular 12/18 textPrimary: "84.23km 주간 합산", "80km 목표 거리", "7명 참여 인원" | `crew/CrewRaceTab.kt:210-223,235-249` 값 20 Black, 이름 Caption textSecondary, 문구 같음 | 다름(크기·색) |
| 단추 | Lime Medium 48h 알약 **"확인"**(닫기) | "축하 카드 공유" RunkkPrimaryButton 56h(`res:278`) | 다름(공유 대신 확인. SRS상 축하 카드 공유는 1차 배포 숨김 목록에 있음) |
| 열림 조건 | 배너 "자세히 보기"를 누르면 열림(첫 진입 자동 표시 여부는 확인 못 함) | — | 누락(상태 필요) |

> F-09~F-10b의 경로 약어: `chat/` = `android/feature/chat/src/main/java/com/a207/runkk/app/ui/chat/`. F-10 "채팅 E10"은 **남의 메시지를 길게 눌러 연 메시지 메뉴** 상태다(F-09는 내 메시지 메뉴). "E10"이라는 코드의 뜻은 문서에서 찾지 못했다.

## F-09 크루 채팅 (61770:4983)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 화면 구조 | 크루 홈의 세 번째 탭이다. 위에서부터 크루 머리글, 세그먼트 3탭(레이스/크루런/**채팅** 선택됨), 메시지 목록, 입력 줄 순서. 상단 바·뒤로가기·구분선이 없다 | 따로 뜨는 화면. `RunkkTopBar(title="크루 채팅", onBack)` 다음에 HorizontalDivider, 목록, HorizontalDivider, 입력 줄(`chat/ChatRoomScreen.kt:101-110`). 크루 홈의 "채팅" 칸은 탭이 아니라 `Routes.CrewChat`로 이동한다(`feature/crew/.../CrewStripItem.kt:9`, `CrewHomeScreens.kt:112,249-265`) | 다름(배치). 채팅을 크루 홈 탭 내용으로 넣을지, 따로 뜨는 화면에 크루 머리글과 탭 줄을 똑같이 그릴지 정해야 한다 |
| 크루 머리글 | pt12, px20, gap12. 크루 기본 이미지 50×50(radius 15, 배경 surfaceHigh #E9EDE0, groups 아이콘 28), 크루명 17sp Bold, 줄높이 20, textPrimary("한강러너스"), 오른쪽 ⋯ 버튼 44×44(more_horiz 24) | 채팅 화면에는 없다. 크루 홈 Header는 `CrewLogo(size=48)`, `TopBarTitle` 18sp Black, MoreMenu 48dp이고 탭 루트가 아니면 뒤로가기도 있다(`CrewHomeScreens.kt:368-399`) | 누락(채팅 화면 기준) |
| 세그먼트 탭 | margin pt14 px20. 높이 44, radius 22, 배경 surfaceHigh #E9EDE0, 안쪽 p4. 칸은 h36, radius 18. 선택 칸은 배경 primary #CAEB2E에 14sp Bold #171B12, 나머지는 14sp Regular textPrimary | 채팅 화면에는 없다. 크루 홈은 `RunkkTabRow`에 padding 16(`CrewHomeScreens.kt:252-264`) | 누락 |
| 목록 여백 | px20, pt11, pb12, 메시지 사이 간격 **17**. 목록과 입력 줄 사이 gap 12 | contentPadding h16·v12, `spacedBy(12.dp)`(`chat/ChatRoomScreen.kt:149-150`) | 다름 |
| 날짜 구분 | 가운데 알약: 배경 surfaceHigh #E9EDE0, radius 12, px10 py4, 11sp Regular, 줄높이 13, textPrimary. 문구는 "오늘"(상대 날짜) | 양옆 가는 선 사이에 "M월 d일 (E)", Caption 12sp, onSurfaceVariant(`chat/ChatRoomScreen.kt:169-187`, `strings.xml` chat_day_pattern) | 다름(모양, 문구). "어제"나 그 이전 날짜를 어떻게 적는지는 피그마에 없다(확인 못 함) |
| 남의 메시지 배치 | Row gap 8. 아바타 32 원 다음에 Body 열(gap 4): 이름 줄, 말풍선+시각 줄(gap 6, 아래 정렬), 반응 줄 | 같은 구조. Row 8, Column 4, 시각 6(`chat/ChatMessageRow.kt:313-327, 375-376`) | 같음 |
| 아바타 | 32×32 원, 단색 채움(에셋 a44d0.svg, surfaceHigh 톤). 글자 없음 | 32 원, 배경 surfaceVariant(=surface #FFFFFF), 이름 첫 글자 13sp Bold(`chat/ChatMessageRow.kt:331-340`) | 다름(배경색, 첫 글자 표시) |
| 작성자 이름 | 12sp Bold, 줄높이 14, textPrimary | `text(12, Bold)` onSurface(`chat/ChatMessageRow.kt:317-322`) | 같음(글꼴만 다름) |
| 직책 배지 | 이름 옆 gap 6. "크루장" 배지: 테두리 1 divider #E9EDE0, radius 8, px6 py1, 10sp Regular, 줄높이 12, textPrimary. 크루장 메시지에만 붙는다 | 없음 | 누락(데이터 필요: 작성자 직책) |
| 남의 말풍선 | 배경 surfaceHigh #E9EDE0, **테두리 없음**, radius 17, px12 py9, 13sp Regular, 줄높이 16, textPrimary | 배경 surface(#FFFFFF), 테두리 1 outlineVariant, radius 14, px12 py10, Body 14sp, 최대 폭 240(`chat/ChatMessageRow.kt:379-396`) | 다름(배경, 테두리, 모서리, 세로 안쪽 여백, 글자 크기). 피그마 말풍선은 252px까지 한 줄로 늘어난다. 최대 폭은 피그마에 정해져 있지 않다(확인 못 함) |
| 내 말풍선 | 컴포넌트 "RunKK / Chat / 내 말풍선"(62151:12329): 배경 chatmine/bg #FFFFFF, 테두리 투명, radius 17, px12 py9, 13sp Regular, 줄높이 16, textPrimary. 오른쪽 정렬, 시각이 왼쪽(gap 6, 아래 정렬) | 배경 primaryContainer(limeContainer #ECF5D0), 테두리 1 outline(#8A8A93), radius 14, 14sp(`chat/ChatMessageRow.kt:383-385`) | 다름(배경, 테두리, 모서리, 글자 크기) |
| 보낸 시각 | 10sp Regular, 줄높이 12, textSecondary #67705B. 형식 "오후 9:12" | Micro 11sp, onSurfaceVariant. 형식 `a h:mm`(`chat/ChatMessageRow.kt:378,397`) | 다름(글자 크기) |
| 반응 알약 | 컴포넌트 "RunKK / Chat / 반응"(61940:10080): h24, 폭 40(내용에 맞춤), radius 12, pl8 pr9, gap 4. 줄선 아이콘 12.6과 숫자 12sp Medium(줄높이 14). **비선택**: 배경 surface #FFFFFF, 테두리 1 divider #E9EDE0, 숫자 textPrimary. **선택(내가 단 반응)**: 컴포넌트 기본값은 배경·테두리 secondary #5C4BDB, 숫자 onSecondary #FFFFFF(설명 "보라"). 화면 인스턴스는 배경·테두리 textSecondary **#67705B**, 숫자 surfaceHigh **#E9EDE0**로 덮어 썼다. 알약 사이 gap 4. 종류는 Up(엄지)과 Check 두 가지가 보인다 | 이모지 글자 칩 "👍 3": 12sp(선택이면 Bold), 패딩 8/4, radius 14. 비선택은 배경 surface에 테두리 1 outline #8A8A93. 선택은 배경 primaryContainer, 테두리 **2dp** primary, 글자 primary. 칩 사이 6, 터치 높이 최소 48(`chat/ChatMessageRow.kt:476-483, 505-535`) | 다름(아이콘이냐 이모지냐, 크기, 색, 간격). 선택색이 컴포넌트(보라)와 화면(#67705B) 사이에서 서로 다르다. 어느 쪽을 따를지 디자이너 확인 필요 |
| 반응 종류 | 메뉴에 좋아요(엄지), 하트, **체크** 세 가지. 알약은 Up과 Check | LIKE(👍), HEART(❤️) 두 가지(`domain/chat/ReactionKind.kt`, 서버 `crew/chat/ReactionKind.java`) | 누락(데이터 필요: CHECK 종류) |
| 반응 줄 위치 | 말풍선 열 안에서 gap 4. 남의 메시지는 왼쪽, 내 메시지는 오른쪽 끝 | Column spacedBy 4(`chat/ChatMessageRow.kt:305-311`) | 같음 |
| 시스템 메시지 | "RunKK / Chat / 시스템 메시지"(61936:344): 배경·테두리·아이콘 없음. 가운데 정렬 두 줄(gap 2), px14 py12. 제목 "[닉네임]님이 러닝을 마쳤어요" 12sp Bold(줄높이 14), 부제 "[n.nn]km · [mm:ss]" 11sp Regular(줄높이 13). 생성 코드상 두 줄 모두 textPrimary이고, 컴포넌트 설명은 textPrimary/textSecondary로 적혀 있다 | 없음 | 누락(데이터 필요: 시스템 메시지 종류, 러닝 완료 이벤트) |
| 길게 누름 메뉴(내 메시지) | 컴포넌트 "RunKK / Chat / 메시지 메뉴"(61943:10719). 폭 160, 배경 surface, 테두리 1 menudivider #E9EDE0, radius **16**, p4, 그림자 0 4 12 rgba(0,0,0,0.18). 위 줄(px8 py6, gap 6, 아래 테두리 1 #E9EDE0): 40×40(radius 8) 칸 3개에 줄선 아이콘 18(좋아요, 하트, 체크). 아래 항목 h40, px12, 14sp Regular, 줄높이 17, textPrimary "삭제". 위치: 누른 말풍선 **바로 위**, 오른쪽 끝을 말풍선 오른쪽에 맞춤(left 190, 폭 160 → 오른쪽 350). 내 메시지에는 "신고"가 없다 | M3 `DropdownMenu`(앵커 아래로 펼침, 기본 모양 extraSmall 8dp, 기본 그림자). 리액션 칸은 48×48 **원**, 이모지 18sp, 선택이면 배경 primaryContainer. HorizontalDivider 뒤에 `DropdownMenuItem` "삭제"(canDelete), "신고"(남의 메시지)(`chat/ChatMessageRow.kt:403-460`) | 다름(모서리, 폭, 그림자, 테두리, 칸 모양·크기, 아이콘, 위치). 리액션 칸에 선택 상태를 어떻게 보이는지는 피그마에 없다(확인 못 함) |
| 운영진이 남의 메시지를 누른 경우 | 피그마에 없음(삭제와 신고가 함께 나오는지 확인 못 함) | canDelete와 남의 메시지가 겹치면 "삭제"와 "신고"가 둘 다 나온다(`chat/ChatMessageRow.kt:433-458`) | 확인 못 함 |
| 입력칸 | Row gap 8. 입력칸: 테두리 1 divider #E9EDE0, radius 12, min-h 48, px16 py14, **배경 없음**(화면 배경이 비침). 자리 문구 "[메시지 입력]"(실제 문구는 "메시지 입력"으로 보임), 13sp Regular, 줄높이 16, textSecondary | 배경 surface, 테두리 1 outline #8A8A93, radius 12, min-h 44, px12 py11. 자리 문구 "메시지 보내기" 15sp, 입력 글자 15sp(`chat/ChatComposer.kt:52-95`, chat_input_placeholder) | 다름(문구, 크기, 색, 높이, 여백) |
| 보내기 버튼 | 44×44 원(radius 22), 배경 **textPrimary #171B12**, 아이콘 20 **위쪽 화살표**(흰색) | 44 원(터치 48), 배경 colorScheme.primary(라이트에서 #3F6212), 아이콘 `RunkkIcons.ArrowRight`, onPrimary. 보낼 수 없으면 알파 0.4(`chat/ChatComposer.kt:111-139`) | 다름(색, 아이콘) |
| 글자 수 표시 | 없음 | 입력 줄 아래 "n/500", 넘치면 "500자까지 보낼 수 있어요", 오른쪽 끝에서 52 띄움(`chat/ChatComposer.kt:98-107`) | 앱에만 있음(FR-208 안내라 남길지 결정 필요) |
| 입력 줄 여백 | 목록 컨테이너 안에서 px20, pb12 | padding start12 end12 top10 bottom16, 위에 구분선(`chat/ChatComposer.kt:48`, `ChatRoomScreen.kt:106`) | 다름 |
| 이전 메시지 더 보기 | 없음 | 목록 맨 위 `RunkkPillButton("이전 메시지 더 보기")`(`chat/ChatRoomScreen.kt:159-165`) | 앱에만 있음(접근성 대체 수단) |
| 빈 상태, 보내는 중, 보내기 실패 | 피그마에 없음 | "아직 메시지가 없어요"(`ChatRoomScreen.kt:120-125`), "보내는 중"(`ChatMessageRow.kt:468-472`), "보내지 못했어요 · 다시 보내기"(`ChatMessageRow.kt:487-503`) | 앱에만 있음. 피그마 사양 확인 못 함 |
| 안내 줄(스낵바) | 없음 | `RunkkSnackbarHost`(아래 96 띄움)(`ChatRoomScreen.kt:109`) | 앱에만 있음 |

## F-09b 메시지 삭제 확인 (61770:5124)

F-09와 같은 화면 위에 가운데 대화상자를 띄운다. 달라진 부분만 적는다.

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 팝업 형태 | 가운데 **다이얼로그**. 스크림 rgba(23,27,18,0.45), 양옆 28 | `RunkkConfirmDialog` = M3 `AlertDialog`(`chat/ChatRoomScreen.kt:192-201`, `ds/RunkkFields.kt:364-396`) | 다름(구현 방식) |
| 카드 | 폭 334, 배경 surface, radius **28**, pt24 px20 pb20, 세로 gap 10 | AlertDialog 기본(radius 28, padding 24) | 다름(여백) |
| 제목 | "메시지를 삭제할까요?" 18sp Bold, 줄높이 26, textPrimary | "메시지를 지울까요?" CardTitle 16sp Black(chat_delete_title) | 다름(문구, 크기, 굵기) |
| 본문 | "삭제한 메시지는 되돌릴 수 없어요." 14sp Regular, 줄높이 21, **textPrimary** | "지운 메시지는 대화에 \"삭제된 메시지\"로 남아요." Body 14sp, TextSecondary(chat_delete_message) | 다름(문구, 색) |
| 버튼 줄 | pt10, gap 10, 너비를 채움. 왼쪽 "취소": Outline 버튼(배경 surface, 테두리 1 divider #E9EDE0, h48, radius 100, 15sp SemiBold textPrimary, 폭 143). 오른쪽 "삭제": Purple 버튼(배경 secondary #5C4BDB, h48, radius 100, 15sp SemiBold onSecondary #FFFFFF, 남은 폭) | 오른쪽 정렬 TextButton 두 개: "취소" BodyStrong TextSecondary, "삭제" BodyStrong Lavender 글자(`ds/RunkkFields.kt:381-394`) | 다름(버튼 모양, 색, 배치) |
| Purple 알약 버튼 컴포넌트 | "RunKK / Buttons / 기본" Style=Purple(61857:7657), Medium48 / Large52 | 앱에 보라 채움 알약 버튼이 없다(`ds/RunkkButtons.kt`에는 Lime 주 버튼, Secondary 외곽선, AccentOutlined만 있다) | 누락(공용 컴포넌트) |

## F-09c 삭제된 메시지 (61770:5275)

F-09에서 마지막 내 메시지 "넵 확인할게요!"가 삭제된 상태이고 메뉴는 닫혀 있다.

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 문구 | "삭제된 메시지입니다" | "삭제된 메시지"(chat_removed, `chat/ChatMessageRow.kt:348`) | 다름(문구) |
| 말풍선 모양 | 보통 내 말풍선과 똑같다(61770:5382): 배경 #FFFFFF, 테두리 없음, radius 17, px12 py9, 13sp **Regular(기울임 아님)**, textPrimary | 배경 surface에 테두리 1 outline, **기울임**, onSurfaceVariant 글자(`chat/ChatMessageRow.kt:383-395`) | 다름(기울임, 글자색, 테두리) |
| 시각 | 그대로 보임("오후 8:05" 10sp textSecondary, 말풍선 왼쪽) | 보임 | 같음(크기만 F-09와 같이 다름) |
| 반응, 메뉴 | 반응 줄 없음 | 반응 줄 없음, 메뉴 없음(`chat/ChatMessageRow.kt:351, 466`) | 같음 |
| 남의 메시지가 삭제된 경우 | 피그마에 없음(surfaceHigh 말풍선일 것으로 보이나 확인 못 함) | 남의 말풍선과 같은 surface 배경 | 확인 못 함 |

## F-09d 반응한 사람 (61770:5402)

**앱에 화면 없음.** 피그마 사양을 그대로 적는다. 무엇을 눌러 여는지는 피그마에 표시가 없다(반응 알약을 누르거나 길게 누르는 것으로 추정, 확인 못 함). 앱은 지금 알약을 누르면 그 반응을 달거나 뗀다(`chat/ChatMessageRow.kt:517`). 그래서 여는 동작을 무엇으로 할지 정해야 한다.

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 팝업 형태 | **바텀시트**. 스크림 rgba(23,27,18,0.45), 시트는 아래 붙임, 폭 390, 높이 내용만큼(288). 드래그 손잡이 **없음** | 없음 | 누락 |
| 시트 | 배경 surface, 위 두 모서리 radius 24, pt24 px20 pb28, 세로 gap 14 | 없음 | 누락 |
| 머리 줄 | 양 끝 정렬. 왼쪽 "반응" 17sp Bold, 줄높이 20, textPrimary. 오른쪽 닫기 "✕"(글자 기호) 17sp Regular, textPrimary | 없음 | 누락 |
| 탭(종류 필터) | gap 8, 각 h32, radius 16, px12. **선택** "전체 3": 배경 secondary #5C4BDB, 13sp Medium(줄높이 16), onSecondary #FFFFFF. **비선택**: 테두리 1 divider #E9EDE0, 배경 없음, 아이콘 14.04과 숫자 13sp Medium textPrimary(gap 4). 예: [전체 3] [✓ 2] [👍(줄선) 1]. 종류별 탭은 수가 있는 종류만 보이는 것으로 보인다 | 없음 | 누락 |
| 목록 행 | 높이 52, 아래 테두리 1 divider #E9EDE0, 양 끝 정렬. 왼쪽(gap 10): 아바타 32 원(a44d0) + 닉네임 15sp Regular, 줄높이 18, textPrimary. 오른쪽: 그 사람이 단 반응 종류 아이콘 18(체크 e707c, 엄지 bfba4) | 없음 | 누락 |
| 한 사람이 여러 종류를 단 경우 | 피그마에 없음(행을 나눌지 아이콘을 여러 개 둘지 확인 못 함) | - | 확인 못 함 |
| 데이터 | 메시지별 반응한 사람 목록(회원ID, 닉네임, 아바타, 종류) | API, 도메인, UiState 모두 없음. 응답에는 종류별 수와 내 반응만 있다(`data/api/chat/ReactionResponse.kt`, `ChatMessageItem.kt`, 서버 `ChatController.java:162-182`) | 누락(데이터 필요) |

## F-10 채팅 E10 = 남의 메시지 길게 눌러 연 메뉴 (61770:5565)

배경 화면은 F-09와 같다(위 표 그대로). 메뉴만 다르다.

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 메뉴 항목 | 위 줄에 반응 3종(좋아요, 하트, 체크), 아래 항목 **"신고" 하나**(Action=신고). 삭제 없음 | 리액션 2종, 구분선, "신고"(canDelete면 "삭제"도 함께)(`chat/ChatMessageRow.kt:433-458`) | 다름(반응 종류 수). 운영진의 경우는 확인 못 함 |
| 위치 | 왼쪽 끝을 남의 말풍선 시작(x=40, 아바타 32 + gap 8 다음)에 맞추고, 누른 말풍선 바로 위에 띄움(top 421, "다들 고생했어요!" 말풍선 위) | M3 DropdownMenu 기본 위치(앵커 Box 아래, 왼쪽 정렬) | 다름(위치) |
| 메뉴 모양 | F-09와 같다(폭 160, radius 16, 테두리 #E9EDE0, 그림자 0 4 12 rgba(0,0,0,.18), 반응 칸 40×40 radius 8, 아이콘 18, 항목 h40 14sp) | F-09 표와 같다 | 다름 |

## F-10b 메시지 신고 (61770:5706)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 팝업 형태 | **바텀시트**. 스크림 rgba(23,27,18,0.45), 시트 높이 545(내용만큼) | M3 `ModalBottomSheet`, containerColor surface(`ds/RunkkReportDialog.kt:37-48`, 호출 `chat/ChatRoomScreen.kt:202-222`) | 같음(형태). 세부는 아래처럼 다름 |
| 시트 | 배경 surface, 위 모서리 radius 24, pt12 px20 pb28, 세로 gap 10, 가운데 정렬 | 시트 모양은 M3 기본(테마 extraLarge 24). 내용 padding s20 e20 b24, spacedBy **16**(`ds/RunkkReportDialog.kt:60-67`) | 다름(간격, 아래 여백) |
| 손잡이 | 40×4, radius 2, 배경 surfaceHigh #E9EDE0 | M3 기본 DragHandle(32×4, onSurfaceVariant 계열) | 다름 |
| 제목 | "이 메시지를 신고할까요?" 18sp Bold, 줄높이 22, textPrimary | 문구 같음(chat_report_title). CardTitle **16sp Black**(`ds/RunkkReportDialog.kt:69-74`) | 다름(크기, 굵기) |
| 부제 | "[닉네임]님의 메시지" 12sp Regular, 줄높이 18, **textPrimary**. 제목과 gap 2 | 문구 같음(chat_report_subtitle). Caption 12sp, **onSurfaceVariant**, gap 4 | 다름(색, 간격) |
| 사유 묶음 이름 | "신고 사유" **13sp Medium**, 줄높이 16, textPrimary, pb2 | "신고 사유" Caption 12sp, onSurfaceVariant(`ds/RunkkReportDialog.kt:78`) | 다름 |
| 사유 행 | "RunKK / Control / 라디오 항목". min-h **44**, gap 12. 라디오 20×20 원: 비선택은 테두리 1 #E9EDE0, 선택은 바깥 테두리 1 **secondary #5C4BDB**와 안쪽 12 점(에셋 0836a.svg, 스크린샷상 어두운 채움). 라벨 15sp, 줄높이 18, textPrimary, 선택이면 Bold. 순서: 욕설, 비방 / 음란 / 광고, 스팸 / 개인정보 노출 / 기타 | M3 `RadioButton`(selectedColor = primary #3F6212, unselected = outline #8A8A93), 행 min-h 48, gap 8. 라벨 Body 14sp, 선택이면 BodyStrong(`ds/RunkkReportDialog.kt:113-134`). 순서와 문구는 같다(`core/ui/res/values/strings.xml:131-135`) | 다름(라디오 색·모양, 글자 크기, 행 높이, 간격) |
| 신고 내용 칸 | 라벨 "신고 내용" 13sp Medium textPrimary, 칸과 gap 9. 텍스트 영역 **h72**, 테두리 1 **#C7CCBF**, radius **16**, px12 py14. 자리 문구 "신고 사유를 작성해주세요." 14sp Regular, 줄높이 18, textSecondary | `RunkkTextField`(라벨 Caption 12sp TextSecondary, gap 8, 상자 min-h 48, radius 12, 테두리 InputBorder #71717A, padding 16/12, 입력 16sp). **자리 문구 없음**(`ds/RunkkReportDialog.kt:86-91`, `ds/RunkkFields.kt:61-120`) | 다름(높이, 모서리, 테두리색, 글자 크기). 자리 문구 누락 |
| 글자 수 | "0/200" 12sp Regular, 줄높이 18, **textPrimary**, 오른쪽 정렬 | Caption 12sp, onSurfaceVariant, 오른쪽(`ds/RunkkReportDialog.kt:92-98`) | 다름(색) |
| 버튼 | pt4, gap **10**. "취소": Outline(배경 surface, 테두리 1 divider #E9EDE0, **h52**, radius 100, 16sp SemiBold textPrimary, 폭 171). "신고하기": **Purple**(배경 #5C4BDB, h52, radius 100, 16sp SemiBold #FFFFFF, 남은 폭) | 간격 8. `RunkkSecondaryButton`(h56, radius 16, 테두리 BorderStrong #8A8A93), `RunkkPrimaryButton`(h56, radius 16, **라임 채움** #CBEA2B, 16sp Black, OnLimeFill)(`ds/RunkkReportDialog.kt:100-108`, `ds/RunkkButtons.kt:54-57,153-171`) | 다름(색, 높이, 모서리, 간격) |
| 기타 사유 상세 필수 안내 | 피그마에 없음 | 상태 `isDetailMissing`은 있다. 시트는 신고하기를 막기만 한다(`chat/ChatUiState.kt:35`) | 확인 못 함(피그마에 오류 표시 사양 없음) |
| 공용 영향 | - | 이 시트는 크루 콘텐츠 신고(`feature/crew/.../CrewReportDialog.kt:12-35`)와 같이 쓴다. 고치면 크루 게시글 신고 시트도 함께 바뀐다 | 참고 |


> F-11~F-13e의 경로 약어: `crew/`, `ds/`는 위와 같고 `strings` = `android/feature/crew/src/main/res/values/strings.xml`.

## F-11 크루 일정(제안·새 디자인) (61771:4397)

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

## F-11b 지난 일정 (61771:4543)

**화면 없음.** 앱은 같은 탭 목록 끝으로 스크롤한다(위 표). 피그마 사양:

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 헤더 | 공통 헤더, 제목 "지난 크루런", 오른쪽 동작 없음 | 없음 | 누락 |
| 본문 여백 | pt11 px20 pb16, 항목 간격 10 | 없음 | 누락 |
| 기간 필터 | 오른쪽 정렬. 바탕 surface, 테두리 1 divider, 높이 42, 모서리 21, pl18 pr16, 간격 8. 글자 "2026년 10월" 16 Medium lh19 textPrimary + 아래 꺾쇠(Chevron 12×6). 누르면 F-11c 시트 | 없음. 기간(월) 필터 개념 없음 | 누락(기능) |
| 줄 | 아래 구분선 divider, 최소 높이 45, pb6, 간격 12. 제목 "[화요일 인터벌]" 16 **Regular** lh19 textPrimary. 보조 "10.20(화) 19:00 · 5km · 6명" 12 Regular lh18 **textPrimary**, 제목과 간격 4. 오른쪽 35×16 빈 칸(용도 확인 못 함). 최신순 | 지금 앱의 지난 줄은 카드(RunkkCard, 테두리)에 BodyStrong 제목 + "크루런, 10.14, 5km, 6명" Caption textSecondary + 결과면 › (`CrewScheduleTab.kt:590-636`) | 다름 |
| 줄 누름 | F-11d 지난 일정 상세 | 끝난 크루런만 결과 화면(Routes.CrewRunResult)으로, 취소된 것은 누를 수 없음 (`CrewScheduleTab.kt:594, 610`) | 다름 |
| 취소된 일정 | 피그마 없음 | "취소됨" 문구 (`CrewScheduleTab.kt:600-604`) | 확인 못 함 |

## F-11c 기간 선택 (61771:4612)

**화면 없음(시트 없음).** 피그마 사양(노드 61771:4675 "Bottom Sheet - 기간 선택"):

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 형태 | **바텀시트**, 시트 위끝 y=506, 공통 시트 규격, 딤 0.45 | 없음 | 누락 |
| 제목 | "기간 선택" 18 Bold + "✕" | 없음 | 누락 |
| 휠 피커 | 높이 180, 열 2개(년·월) 간격 8. 항목 높이 36. 선택 띠: surfaceHigh `#e9ede0`, 높이 36, 모서리 12, top 72, 전체 폭. 고른 값 18 Bold lh22, 바로 옆 16 Regular opacity 0.45, 그 바깥 16 Regular opacity 0.20 | 앱에 휠 피커 컴포넌트가 없다(`ds/`에 없음) | 누락 |
| 값 | 년 "2026년"(아래 2027·2028), 월 "10월"(위 9월, 아래 11·12월). 위쪽 빈 칸 2개 | 없음 | 누락. 지난 일정인데 앞 연도가 보이는 범위는 확인 못 함 |
| 버튼 | "적용" Lime Medium h48 전체 폭 | 없음 | 누락 |

## F-11d 지난 일정 상세 (61771:4718)

**화면 없음.** 앱은 지난 줄을 누르면 크루런 결과 화면(F-16 계열, `crew/CrewRunResultScreen.kt`)으로 간다. 피그마 사양은 F-13 상세 틀과 같고 아래만 다르다(스크린샷 기준, 수치는 F-13 design context에서 가져옴):

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 헤더 | "크루런 상세", 오른쪽 ⋯ | 결과 화면 헤더 | 다름 |
| 작성 정보 | "[닉네임] · 종료된 일정" 12 Regular | 없음 | 누락 |
| 정보 카드 | 날짜 "10.13(화) 19:00"(반복 배지 없음), 거리 "5km", "참석 4명", 내용 상자 | 없음 | 누락 |
| 참석자 줄 | F-13 줄 + 오른쪽 끝에 달린 거리 "5.28km" Bold(약 16, 스크린샷 추정, 정확한 수치 확인 못 함) | 결과 화면의 참가자별 거리(CrewRunResult.participants) | 다름(데이터는 있음) |
| 하단 버튼 | 없음 | - | - |

## F-12 일정 작성 (61771:4799)

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

## F-12b 날짜 선택 팝업 (61771:4850)

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

## F-12c 날짜 팝업 11월 (61771:5007)

F-12b와 같은 시트에서 달만 바뀐 상태(스크린샷 기준).

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 이전 화살표 | 활성(textPrimary) | 없음 | 누락 |
| 다음 화살표 | 비활성(textSecondary) — 범위 끝 달 | 없음 | 누락 |
| 날짜 | 1~4일 Bold(고를 수 있음), 5~30일 흐림 | 없음 | 누락 |

## F-12d 시간 선택 (61771:5166)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 형태 | **바텀시트**(위끝 약 y=506), "시간 선택" + ✕, 휠 3열 + "확인" | 드롭다운 목록 (`CrewRunScreens.kt:223-230`) | 다름 |
| 열 | 오전/오후 · 시(두 자리 "07") · 분(두 자리 "00") | 한 열 "HH:mm" | 다름 |
| 분 단위 | **5분**(50, 55, 00, 05, 10) | 30분 (`CrewRunWriteUiState.kt:28-29`) | 다름(기능) |
| 시 범위 | 보이는 값 05~09(12시간제). 전체 범위 확인 못 함 | 05~23시 | 확인 못 함 |
| 휠 모양 | F-11c와 같은 휠(띠 surfaceHigh h36 모서리 12, 고른 값 18 Bold, 옆 0.45, 바깥 0.20) | 없음 | 누락 |
| 날짜 칸 값 표시 | "10.24 (토)" (요일 앞 공백) | dayLabel "10/24(토)", 오늘·내일은 "오늘 …"/"내일 …" (`CrewRunScreens.kt:235-244`) | 다름 |

## F-12e 거리 선택 (61771:5265)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 형태 | **바텀시트**, "거리 선택" + ✕, 휠 1열 + "확인" | 세그먼트 4칸 (`CrewRunScreens.kt:201-207`) | 다름 |
| 선택지 | **제한 없음**, 3km, 5km, 10km, **하프** (보이는 범위. 하프 아래 더 있는지는 확인 못 함). **1km 없음** | 1·3·5·10km (`crew/CrewSupport.kt:26-27`) | 다름(기능) |
| 시간 칸 값 | "오전 07:00" | "07:00" | 다름 |

## F-12f 반복 선택 (61771:5342)

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

## F-13 일정 상세 (61771:5416)

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

## F-13b 작성자 메뉴 (61771:5548)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 상태 | 내 글, 내가 참석 중: 작성 정보 "나 · 2시간 전", 참석자 첫 줄 "**나** 작성자", 참석 4명 | - | - |
| 하단 버튼(참석 중) | "참석 취소하기" White Medium: 높이 48, 알약, 배경 surface, 15 SemiBold textPrimary, 테두리 없음 | 참석 칩 해제 또는 시트의 "미정" (`CrewScheduleTab.kt:394-397`) | 다름 |
| ⋯ 메뉴 형태 | **드롭다운**(RunKK/Menu/모집글 드롭다운): 너비 90, 배경 surface, 테두리 1 `color/dropdown/stroke #c7ccbf`, 모서리 16, 그림자 0 4 12 rgba(0,0,0,0.18), 안쪽 py6. 위치 left 280 top 48.5(⋯ 바로 아래 오른쪽 정렬, 오른쪽 여백 20) | 상세 화면이 없어 없음. 크루 홈 ⋯는 M3 DropdownMenu 기본 모양(`crew/BoardParts.kt:80-95`) | 누락 |
| 메뉴 항목 | "수정하기" / 구분선 1px `color/menudivider #e9ede0` / "삭제하기", 각 px16 py13, 15 Regular lh18 textPrimary | 삭제는 시트 머리의 "삭제" 텍스트 링크(Caption, Lime), 수정 없음 (`CrewScheduleTab.kt:473-479`) | 다름 / 수정은 누락 |
| 메뉴 노출 조건 | 작성자 | canDelete(본인 또는 운영진 이상)면 삭제 링크 | 확인 못 함(운영진에게 피그마 메뉴가 보이는지) |

## F-13c 삭제 확인 (61771:5668)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 형태 | 가운데 **다이얼로그**, 딤 `rgba(23,27,18,0.45)`, 좌우 28 | RunkkConfirmDialog(M3 AlertDialog) (`CrewHomeScreens.kt:270-279`, `ds/RunkkFields.kt:364-397`) | 다름(모양) |
| 상자 | 너비 334, 배경 surface, 모서리 **28**, pt24 px20 pb20, 간격 10 | AlertDialog 기본(모서리는 테마 extraLarge 24, `ds/Theme.kt:84-90`) | 다름 |
| 제목 | "모집글을 삭제할까요?" 18 Bold lh26 textPrimary | "글을 지울까요?" CardTitle 16 Black (`strings:109`) | 다름 |
| 본문 | "삭제한 모집글은 되돌릴 수 없어요." 14 Regular lh21 **textPrimary** | "지운 글은 되돌릴 수 없어요." Body 14 TextSecondary (`strings:110`) | 다름 |
| 버튼 줄 | 위 10, 간격 10, 같은 높이 48. 왼쪽 "취소": 너비 143, 배경 surface, 테두리 1 divider, 알약, 15 SemiBold textPrimary. 오른쪽 "삭제": 나머지 폭, Purple(`#5c4bdb`) 채움, 15 SemiBold white | TextButton 두 개: "취소" BodyStrong TextSecondary, "삭제" BodyStrong Lavender 글자 | 다름 |
| 삭제 뒤 이동 | 확인 못 함(상세 화면이 닫히는지) | 크루 홈 목록에서 지움 | 확인 못 함 |

## F-13d 모집글 수정 (61771:5824)

**화면 없음.** 피그마 사양(스크린샷 기준, 틀은 F-12와 같음):

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 헤더 | "모집글 수정"(가운데), ⋯ 없음 | 없음 | 누락 |
| 칸 | F-12와 같은 6칸, 기존 값이 채워짐(제목, 내용, 10.24 (토), 오전 07:00, 5km, 매주 토요일), 값 글자 textPrimary | 없음 | 누락 |
| 날짜·시간 칸 | **수정 불가** 상태: 배경 surfaceHigh `#e9ede0`, 테두리 없음(스크린샷 기준), 글자 textSecondary | 없음 | 누락 |
| 버튼 | "저장" Lime Large h52 | 없음 | 누락 |

## F-13e 반복 선택 팝업(수정) (61771:5875)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 형태 | F-12f와 같은 반복 바텀시트(위끝 약 y=572) | 없음 | 누락 |
| 상태 | "반복 안 함" 고름 + 안내 "다음 모집글이 생성되지 않아요", "매주 토요일" 안 고름 | 없음 | 누락 |


## 요약 1. 기능·데이터가 필요한 차이 (서버 API·응답 필드·UiState)

### 찾기·상세·초대·만들기·크루 홈·목표 (F-01~F-08)

**기능·데이터가 필요한 차이**

1. **크루 검색 화면(F-01d·F-01e)**: 새 화면, 경로(예: `Routes.CrewSearch`), UiState(keyword, results, resultCount)를 추가한다. 검색 API(`GET /crews?keyword=`)는 이미 있다. "검색 결과 [n]개"의 n을 어디서 가져올지 정해야 한다. 지금 응답(`CrewList(crews, nextCursor)`)은 커서 방식이라 전체 개수가 없다. 서버에 totalCount를 더하거나, 앱이 읽은 개수로 보일지 결정이 필요하다.
2. **목록 카드의 인원 "4/10"과 한줄 소개**: 둘 다 이미 `CrewList.Item.headcount`·`intro`에 있으므로 화면만 고치면 된다. 다만 가입 방식 배지와 크루장·이번 주 거리 메타를 뺄지는 결정이 필요하다(문서 충돌 1 참고).
3. **새로고침 단추(F-01)**: 화면에 단추만 넣고 `CrewFindActions.onRetry`(= load)를 잇는다. 서버 변경은 없다.
4. **크루 상세 통계(F-02)**: 서버 `CrewDetailView`와 앱 `CrewDetail`에 세 가지 필드를 더한다.
   - 개설일 `createdAt`
   - 누적 크루런 횟수 `crewRunCount`
   - 최근 크루런 목록 3건 `recentCrewRuns`(시작 시각, 참여 인원, 총 거리)
   - `crew.created_at`은 설계서 자료사전에 생성일시로 있다. 크루 밖 사용자에게 열람을 허용하는 범위는 SRS를 바꿔야 한다(문서 충돌 2 참고).
5. **24시간 가입 제한 카운트다운(F-02c)**: 앱이 가입 가능 시각을 알아야 한다. `GET /crews/me` 응답 `MyCrewView` 또는 상세 응답에 `rejoinAvailableAt`(Instant)을 더하고, UiState에 그 값과 초 단위 남은 시간 갱신을 둔다. 지금은 가입을 눌러야 COOLDOWN 오류로 알 수 있다.
6. **초대 코드 2단계 흐름(F-03·F-03b·F-03c)**: 코드로 크루를 먼저 조회하는 API가 필요하다(예: `GET /crews/invite/{code}` → 크루 미리보기: 이름, 개설일, 인원, 소개, 태그). 지금은 `POST /crews/join`이 조회와 가입을 한 번에 한다.
   - 무작위 대입 방지: 조회 API에도 기존 `CrewJoinAttemptGuard`를 적용해야 한다.
   - UiState: `invitePreview: CrewPreview?`, `inviteError`(칸 안 오류 표시)를 더하고 "확인"과 "가입하기" 동작을 나눈다.
   - 초대 코드 입력을 전체 화면에서 바텀시트로 바꾼다.
7. **크루 로고 등록(F-04)**: 이미지 고르기, 업로드, `CrewForm.logoUrl`이 필요하다. 서버 `CrewCreateRequest`는 이미 `logoUrl`(https URL)을 받는다. 업로드 경로(사진 저장소)가 있는지는 확인하지 못했다.
8. **크루명 길이 2~12자(F-04 자리 문구)**: 서버 `CrewName.MAX_LENGTH = 20`, 앱 `CrewFormState.NAME_MAX = 20`, 설계서 FR-110은 2~20자다. 바꾸려면 서버·앱·문서를 함께 바꿔야 한다(문서 충돌 3 참고).
9. **레이스 카드의 "참여 [n]명 · 내 기여 nn%"와 내 기여 막대(F-05·F-06)**: 응답 필드는 있다.
   - 내 기여 %는 `RaceStatus.myShare`를 쓴다.
   - 참여 인원은 contributors 가운데 거리가 0보다 큰 사람 수로 계산할 수 있다(목표 화면이 이미 이렇게 센다).
   - 막대의 내 기여 구간 폭은 진행률 × 내 기여율이다.
   - 서버를 바꿀 필요는 없다. 참여 인원을 이렇게 정의해도 되는지는 확인이 필요하다.
10. **목표 화면의 지난 목표 3주(F-07)**: `crews.race(crewId, week)`를 지난 1·2·3주로 세 번 부르면 된다(API 있음). UiState의 `lastWeek: RaceStatus?`를 `pastWeeks: List<RaceStatus>`로 바꾸고 주차 이름("10월 3주차")을 계산한다.
11. **달성 배너와 축하 팝업(F-08·F-08b)**: UiState에 팝업 열림 여부(`isCelebrationOpen`)를 더한다. 데이터는 `race.celebration`에 있다. 같은 주에 처음 들어왔을 때 팝업을 자동으로 띄울지는 피그마에 없어 확인하지 못했다. 자동으로 띄운다면 "본 주"를 기기에 저장해야 한다.
12. **보드 행 원(32)에 프로필 이미지**: 피그마는 빈 원이라 무엇을 넣을지 확인하지 못했다. 이미지를 넣는다면 `RaceStatus.Contributor`와 `CrewRanking.Entry`에 아바타 필드가 필요하다.

### 채팅 (F-09~F-10b)


1. **반응 종류 CHECK 추가**: 피그마는 좋아요, 하트, 체크 세 종류다. 서버 `ReactionKind`(LIKE, HEART), 앱 `domain/chat/ReactionKind.kt`, 문자열(chat_reaction_*), SRS FR-210과 자료사전 "리액션종류 = [좋아요 | 하트]"에 모두 CHECK를 더해야 한다. DB 제약도 확인해야 한다.
2. **반응한 사람 목록(F-09d)**: 새 API가 필요하다. 예: `GET /chat-messages/{id}/reactions` → `[{memberId, nickname, avatar?, kind}]`. 그 밖에 `ChatRepository.reactors(messageId)`, 도메인 모델, ChatUiState(시트 대상 메시지, 고른 탭(전체 또는 종류), 목록, 로딩·오류), ChatActions(열기, 탭, 닫기)가 필요하다. 여는 제스처도 정해야 한다(알약 누름은 지금 달기·떼기다).
3. **작성자 직책 배지("크루장")**: `ChatMessageItem`과 `CrewChatPosted` 실시간 메시지에 작성자 직책(role)을 넣거나, 앱이 크루원 목록(CrewMembers, `CrewRole.labelRes()`가 "크루장" 문구를 이미 가짐)과 authorId를 맞춰 붙여야 한다. 채팅 모듈은 지금 크루원 목록을 모른다. 운영진도 배지를 붙이는지는 확인 못 함(피그마에는 크루장만 있다).
4. **시스템 메시지("[닉네임]님이 러닝을 마쳤어요 / n.nn km · mm:ss")**: 서버에 시스템 메시지 종류와 생성 시점(크루원 러닝 완료 이벤트), 응답 필드(type, distance, duration)가 필요하다. 앱에는 `ChatMessage` 종류 구분과 렌더러가 필요하다. SRS에 근거 요구가 없다(아래 문서 충돌 참고).
5. **채팅을 크루 홈 탭으로 넣기(F-09 구조)**: 크루 홈 `CrewTab`에 CHAT을 더하고 `CrewStripItem.CHAT.tab`을 null이 아니게 바꾼다. 화면이 보이는 동안만 방을 여는 규칙(`LifecycleStartEffect` → `ChatViewModel.onShown/onHidden`, D-103·D-130)을 탭 선택과 연결한다. 알림 진입(`CrewChatEntryRoute`, `Routes.CrewChat`, InboxDestination)도 크루 홈의 채팅 탭으로 보내도록 고친다. 서버 API는 바뀌지 않는다. 그래도 내비게이션과 생명주기를 바꾸는 일이라 화면만 고치는 범위를 넘는다.
6. (선택) **아바타 이미지**: 피그마 아바타는 단색 원이라 지금 데이터로 그릴 수 있다. 실제 프로필 이미지를 넣으려면 작성자 아바타 필드가 필요하다(확인 못 함).


### 일정 (F-11~F-13e)


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


## 요약 2. 화면만 고치면 되는 차이

### 찾기·상세·초대·만들기·크루 홈·목표 (F-01~F-08)


- 공통(0절)
  - 라이트 색 토큰을 피그마 값으로 맞추고 surfaceHigh, barCrew, tagPurple, LimeTint 토큰을 더한다.
  - 글꼴을 IBM Plex Sans KR로 바꾸고 Black 굵기를 Bold·SemiBold로 낮춘다.
  - 상단 바: 가운데 제목, 원 없는 arrow_back.
  - 버튼: 알약 모양, 높이 52/48/36.
  - 카드: radius 24·28, 패딩 12~14.
  - 바텀시트: 핸들 없음, 위 모서리 28.
  - 본문 좌우 여백 20, 하단 CTA 위 구분선 제거.
- 크루 기본 이미지: 글자 대신 groups 아이콘을 쓴다(48/50/74/80).
- 크루 태그: Outline Small 태그(목록), Purple·LimeTint Medium 태그(상세·조건).
- 시간대 칩: 32h이고, 선택하면 #67705B로 채우며 체크 표시는 없다.
- 세그먼트 Control: 크루 홈 3탭, 폼 2탭, 레이스 주간/월간 소형.
- F-01 문구
  - 검색 자리 문구: "크루명 검색"
  - 조건 안내: "나와 비슷한 시간대에 뛰는 크루를 찾아보세요"
  - 크루 만들기 단추: + 아이콘 제거
- F-01b: 팝업 헤더 ✕, 칩 간격 8, 적용하기 48h 알약.
- F-02
  - 가로 배치 프로필 줄
  - 소개 16
  - 2칸 통계 그리드: 누적 크루런 칸은 데이터가 생기면 채운다.
  - 단추 문구: "가입 신청하기" / "가입하기"(F-02b)
- F-04
  - 로고 칸: 자리만 먼저 둔다.
  - 문구: "한 줄 소개", "최대 40자", 태그 안내 문구
  - 라벨: Medium 13
- F-05·F-06
  - 머리글 크기, 탭 줄
  - 목표 카드
    - 문구: "이번 주 목표", 마감 글자 textPrimary
    - 큰 숫자: Bold 30, % 표시 textPrimary
    - 막대: 8h, barCrew + secondary
  - "목표 정하기": 밑줄 링크로 바꿔 제목 줄 오른쪽에 둔다.
  - "이번 주 공동 목표가 없어요"를 지운다.
  - "러닝 보드" 제목
  - 보드 행 크기·색: 막대 11.2h, 남 barCrew, 나 secondary, 이름 13
- F-07
  - 제목 "주간 목표", "10월 4주차" 제목
  - 알약 입력칸(값 Bold 20)
  - 빠른 선택 칩 제거
  - 지난 목표 카드 모양: 투명 바탕, r28
  - 문구: "달성 실패"
- F-08b
  - 문구: "함께 목표를 달성했어요"
  - 80 원형 크루 이미지
  - 숫자 Bold 18
  - 공유 대신 "확인" 단추

### 채팅 (F-09~F-10b)


- 말풍선: 내 것은 #FFFFFF에 테두리 없음, 남의 것은 surfaceHigh #E9EDE0에 테두리 없음. radius 17, px12 py9, 13sp/16. 최대 폭은 다시 정한다.
- 시각 10sp/12 textSecondary. 메시지 사이 간격 17, 좌우 20.
- 날짜 구분을 surfaceHigh 알약(radius 12, px10 py4, 11sp)으로 바꾸고, 문구를 "오늘" 같은 상대 날짜로 바꾼다(그 밖의 날짜 형식은 확인 필요).
- 아바타를 단색 surfaceHigh 원으로 바꾸고 첫 글자를 뺀다.
- 반응 알약: h24, radius 12, 줄선 아이콘 12.6과 12sp Medium. 비선택은 흰 바탕에 #E9EDE0 테두리, 선택은 #67705B 채움에 #E9EDE0 숫자(화면) 또는 #5C4BDB에 흰 숫자(컴포넌트). 어느 쪽인지 확인한다. 이모지를 아이콘으로 바꾼다. 터치 영역 48은 바깥 패딩으로 지킨다.
- 메시지 메뉴: M3 DropdownMenu 대신 직접 그린 Popup으로 바꾼다. 폭 160, radius 16, 테두리 #E9EDE0, 그림자 0 4 12 18%, 반응 칸 40×40 radius 8에 아이콘 18, 항목 h40 14sp. 누른 말풍선 바로 위에 띄우고, 내 것은 오른쪽 끝에, 남의 것은 말풍선 왼쪽 끝에 맞춘다.
- 삭제 확인 다이얼로그: 문구("메시지를 삭제할까요?", "삭제한 메시지는 되돌릴 수 없어요."), 18sp Bold 제목, 14sp textPrimary 본문, radius 28 카드, Outline "취소"와 Purple "삭제" 알약(h48, 15sp SemiBold), gap 10. 공용 Purple 알약 버튼과 Outline 알약 버튼(divider 테두리)을 새로 만들어야 한다.
- 삭제된 메시지: 문구 "삭제된 메시지입니다", 기울임 없음, textPrimary, 보통 말풍선 모양.
- 입력 줄: 배경 없음, 테두리 #E9EDE0, min-h 48, px16 py14, 자리 문구 "메시지 입력" 13sp. 보내기는 #171B12 원에 위쪽 화살표 아이콘. 글자 수 줄과 위아래 구분선은 뺄지 결정한다(FR-208 초과 안내는 다른 방식으로 남겨야 한다).
- 상단 바 "크루 채팅"과 뒤로가기를 크루 머리글과 세그먼트 탭으로 바꾼다(위 기능 5와 함께 정한다).
- 신고 시트(공용 `RunkkReportSheetContent`): 손잡이 40×4 #E9EDE0. 제목 18sp Bold, 부제 12sp textPrimary, 묶음 라벨 13sp Medium. 라디오 20(#5C4BDB 테두리와 점), 행 44, gap 12, 15sp. 텍스트 영역 h72, radius 16, #C7CCBF, 자리 문구 "신고 사유를 작성해주세요.". 글자 수 textPrimary. 버튼은 Outline/Purple h52 gap 10. 시트 간격 10, 아래 28. 크루 콘텐츠 신고 시트도 함께 바뀐다.
- 테마 토큰: 피그마 hex에 맞추려면 background, textPrimary, textSecondary, divider, surfaceHigh(새로 추가), secondary를 손봐야 한다. 앱 전체에 영향이 있다(0절 표).
- 테스트 영향: `feature/chat/src/test/.../ChatRoomScreenTest.kt`가 문구와 노드를 확인하므로 문구를 바꾸면 테스트도 고쳐야 한다.


### 일정 (F-11~F-13e)


- 공통 토큰: background `#f7f8f2`, textPrimary `#171b12`, textSecondary `#67705b`, divider `#e9ede0`, surfaceHigh `#e9ede0` 추가, dropdown stroke `#c7ccbf`, 딤 0.45. 글꼴 IBM Plex Sans KR(Bold 위주, Black 사용 중단).
- 공통 헤더: 가운데 19 Bold 제목, 원 없는 뒤로가기 40, ⋯ 44(more_horiz 아이콘).
- 공통 바텀시트(위 모서리 28, 제목+✕, Lime Medium 확인 버튼)와 휠 피커, 달력 시트, 라디오 목록 시트 컴포넌트 신설.
- F-11: 헤더 크루 기본 이미지·여백, 탭 줄 색·크기, "+ 크루런 모집하기" 칩(h28, 테두리 없음), "지난 크루런 ›" 글자 색·크기·늘 보이기, 히어로 날짜 문구 "오늘 · 10.21(수) 19:30", 줄 보조문구 구분자 " · ", 미참석 칩 14 Medium, 목록 끝 지난 크루런 묶음과 "이후" 묶음 제거 여부, 본문 여백 20.
- F-12: 칸 순서, 라벨 13 Medium textPrimary, 입력 상자 테두리 divider·안쪽 12/14·글자 15, 내용 높이 72·모서리 16, 자리문구 문구, 날짜·시간·거리 칸을 입력 상자 모양 + 시트로, 기본값 비우기, 올리기 버튼 h52 알약·구분선 제거.
- F-13c: 다이얼로그 문구("모집글을 삭제할까요?", "삭제한 모집글은 되돌릴 수 없어요."), 모서리 28, 취소(외곽선)·삭제(보라 채움) 두 알약 버튼.
- F-13b: 드롭다운 메뉴 모양(너비 90, 모서리 16, 테두리, 그림자, 구분선).


## 요약 3. 앱에 없는 피그마 에셋

### 찾기·상세·초대·만들기·크루 홈·목표 (F-01~F-08)


| 에셋(피그마 이름) | 쓰는 곳 | 노드 | 앱 상태 |
|---|---|---|---|
| Material `groups`(크루 기본 이미지 아이콘, 26/28/40/44) | 크루 카드·홈 머리글·상세·초대 미리보기·축하 팝업 | 컴포넌트 61869:4933(인스턴스 예 61869:4922, 61869:4928, 61869:4931) | 없음. `RunkkIcons.Users`(lucide 모양)만 있다 |
| Material `more_horiz` 24 | 크루 홈 더보기 | `RunKK / Buttons / 아이콘` 61865:12380(인스턴스 61865:12381) | 없음. 점 세 개를 직접 그린다(`crew/CrewMoreButton.kt`) |
| Material `arrow_back` 24 | 서브 화면 뒤로 | `RunKK / Buttons / 뒤로가기` 61831:9677 | 없음. 셰브론 `RunkkIcons.Back` |
| Material `add` 28 | 크루 만들기 로고 칸 | 61874:9526 | 없음. `RunkkIcons.Plus`(선 굵기 다를 수 있음) |
| 검색 돋보기 18 | 크루 검색 바 | 61943:10699 | `RunkkIcons.Search`가 있다(모양 일치는 확인 못 함) |
| `✕`, `‹`, `›`, `↻` | 시트 닫기, 검색 뒤로, 미리보기 이동, 새로고침 | 61769:4383, 61769:4717, 61769:5483, 61769:4142 | 피그마도 글자(문자)로 그렸다. 앱은 `RunkkIcons.Close`·`ChevronRight`가 있고, 새로고침 아이콘은 없다 |
| IBM Plex Sans KR 글꼴 | 전체 | — | 없음(`android/core/ui/src/main/res/font`에 Noto Sans KR·Barlow만 있다) |

### 채팅 (F-09~F-10b)


| 용도 | 피그마 노드 이름(id) | 에셋 파일 | 앱 상태 |
|---|---|---|---|
| 메뉴 좋아요(엄지, 줄선) | Button - 좋아요 달기 > Icon (61943:10708) | bfba4.svg | 없음(앱은 이모지 👍). RunkkIcons에 ThumbUp 없음 |
| 메뉴 하트(줄선) | Button - 하트 달기 > Icon (61943:10712) | d46c9.svg | `RunkkIcons.Heart` 있음(같은 모양인지 확인 못 함) |
| 메뉴 체크(줄선) | Button - 체크 달기 > Icon (61943:10715) | e707c.svg | `RunkkIcons.Check` 있음(같은 모양인지 확인 못 함) |
| 반응 알약 Up 선택 | RunKK / Chat / 반응 > Icon (61940:10059) | 8d950.svg (화면 덮어쓰기 89973.svg) | 없음 |
| 반응 알약 Up 비선택 | 반응 > Icon (61940:10065) | b65fb.svg | 없음 |
| 반응 알약 Check 비선택 | 반응 > Icon (61940:10071) | 6d62e.svg | 없음 |
| 반응 시트 탭 아이콘 Check / Up | Tab - 2 > Icon (61770:5530), Tab - 1 > Icon (61770:5534) | c6f9a.svg, 08b5b.svg | 없음 |
| 반응 시트 행 아이콘 Check / Up | Row > Icon (61770:5543), (61770:5555) | e707c.svg, bfba4.svg | 없음 |
| 보내기(위쪽 화살표) | Icon - 보내기 (61770:5114) | 2df83.svg | 없음(앱은 ArrowRight). RunkkIcons에 ArrowUp 없음(ChevronUp만 있음) |
| 크루 기본 이미지 groups | 크루 기본 이미지 > groups (61869:4925) | eba29.svg | 없음(앱 CrewLogo는 이름 글자. `RunkkIcons.Users`가 근사) |
| 더보기 가로 점 | 더보기 > more_horiz (I61865:12396;61865:12375) | df65f.svg | 앱은 Canvas로 점 세 개를 그린다(`feature/crew/.../BoardParts.kt:62-78`). 별도 에셋 없음 |
| 메시지 아바타 기본 원 | Avatar (61770:5012) | a44d0.svg | 단색 원이라 코드로 그릴 수 있음 |
| 라디오 선택 점 | 라디오 항목 > Icon (62151:12430) | 0836a.svg | M3 RadioButton을 쓴다. 에셋 없음 |
| 닫기 ✕ | Header 글자 "✕" (61770:5525) | 글자 기호 | `RunkkIcons.Close` 있음 |


### 일정 (F-11~F-13e)


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


## 요약 4. 문서(docs/sw)와 피그마 충돌

### 찾기·상세·초대·만들기·크루 홈·목표 (F-01~F-08)

| # | 내용 | 피그마 | 문서(docs/sw) | 앱 |
|---|---|---|---|---|
| 1 | 크루 목록 카드에 보이는 정보 | 이름·한줄 소개·태그·인원 "4/10" | srs 기능 표 F-07은 "목록·태그 필터·검색(이번 주 크루 거리 표시)". 권한 표(v0.29 CR-163)는 "크루 소개·태그·인원·지난 주 달린 거리 열람"이고 크루장·이번 주 거리는 뺐다 | 크루장·인원·이번 주 거리(`crewMeta`)와 가입 방식 배지. CR-163과도 어긋난다 |
| 2 | 크루 밖 상세 열람 범위 | 개설일, 누적 크루런 횟수, 최근 크루런 3건을 더 보인다 | 권한 표(CR-163): 소개·태그·인원·지난 주 달린 거리만 허용 | 문서대로(지난 주 거리만) |
| 3 | 크루명 길이 | 자리 문구 "2~12자" | design-oo FR-110 `CrewName` 2~20자, D-143 | 2~20자(서버·앱 모두) |
| 4 | 공동 목표 진행률 상한 | 105%·106%·101%처럼 100을 넘겨 보인다(F-07·F-08) | FR-140 / TF-VB-22: 진행률은 100%에서 자른다(앱 주석 근거) | 100에서 자른다(`goalProgressLabel`) |
| 5 | 축하 카드 공유 | 팝업에 공유 단추가 없고 "확인"만 있다 | FR-141은 외부 공유를 (제안)으로 두었다. 1차 배포 숨김 목록에 "축하 카드 공유(FR-141 일부)"가 있다 | "축하 카드 공유" 단추를 보인다 |
| 6 | 축하 카드 위치 | 레이스 탭은 접힌 배너만 두고, 자세히 보기를 누르면 팝업 | FR-141: "크루 홈에 그 주 축하 카드를 보여야 한다" | 탭 안에 큰 카드 |
| 7 | 목표 없는 주 | "목표 없음" 상태(F-06)와 "목표 정하기" | FR-138 (제안): "정하지 않은 주는 지난 주 목표를 이어 쓴다. 처음 만든 크루는 목표 없음" | 서버 응답을 따른다 |
| 8 | 초대 코드 흐름 | 코드 조회(확인) → 미리보기 → 가입 | FR-112: 초대 코드로 가입(조회 단계는 언급 없음). 조회 API는 설계에 없다 | 한 번에 가입 |
| 9 | 24시간 제한 표시 | 남은 시간 카운트다운 단추 | FR-124 퇴장 뒤 24시간 쿨타임(남은 시간 표시 방법은 정하지 않음) | 오류 안내 줄 |
| 10 | F-02c 통계 이름 | 이 프레임만 "이번 주 달린 거리"(다른 프레임은 "지난 주") | CR-163: 지난 주 달린 거리 | "지난 주 달린 거리" |

### 채팅 (F-09~F-10b)


1. **리액션 종류**: SRS FR-210(`docs/sw/srs/03-05-크루-관리.md`)과 용어집·자료사전은 "좋아요(👍)와 하트(❤️) 두 종류"로 정했다. 피그마는 좋아요, 하트, **체크** 세 종류이고 이모지가 아니라 줄선 아이콘이다. 요구사항 변경(CR)이 필요하다.
2. **삭제 확인 문구와 삭제 표시**: D-160(`docs/sw/design-oo/05-02d-계층-동시성-설계-결정.md:16`)은 삭제·숨김 메시지를 "삭제된 메시지" 자리표시로 남기도록 정했다. 피그마 문구는 "삭제된 메시지입니다"이고, 확인 본문도 앱의 "…\"삭제된 메시지\"로 남아요"와 다른 "삭제한 메시지는 되돌릴 수 없어요."다. 자리표시를 남기는 동작 자체는 같다. 문구만 다르다.
3. **시스템 메시지**: FR-208~FR-210과 자료사전 크루채팅목록(`메시지ID + 작성자표시명 + 메시지내용 + 작성일시 + 리액션`)에는 시스템 메시지(러닝 완료 안내)가 없다. 피그마는 채팅 안에 "[닉네임]님이 러닝을 마쳤어요"를 둔다. 새 요구가 필요하다.
4. **작성자 직책 배지**: FR-209는 "메시지마다 작성자 표시명, 작성일시, 리액션 종류별 수"를 보이도록 정했고 직책은 없다. 피그마는 "크루장" 배지를 보인다.
5. **반응한 사람 보기**: FR-209·FR-210과 자료사전에는 반응한 사람 목록이 없다(수와 본인 여부만). 피그마 F-09d는 누가 어떤 반응을 달았는지 보인다. 요구 추가와 개인정보(닉네임 노출) 검토가 필요하다.
6. **채팅 진입 구조**: 설계(6.1.14 ChatRoomScreen, `Routes.CrewChat`, D-150 알림 진입)는 채팅을 따로 뜨는 화면으로 둔다. 피그마는 크루 홈의 세 번째 탭으로 그린다. 코드 주석(`CrewTab.kt:6`, `CrewStripItem.kt:5`)은 "채팅 칸은 탭 내용이 아니라 채팅방을 연다"고 명시한다.
7. **글자 수 표시**: FR-208은 500자 초과를 막는다. 앱은 "n/500"과 초과 안내를 입력 줄 아래에 보인다. 피그마에는 표시가 없다. 지울 경우 초과 안내를 어디에 둘지 정해야 한다.


### 일정 (F-11~F-13e)


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

## 요약 5. 확인 못 함

### 찾기·상세·초대·만들기·크루 홈·목표 (F-01~F-08)

- 하단 내비(Floating) 차이: 앱 공용 하단 바라 이 비교에서 뺐다.
- 대기 중인 가입 신청, 정원 참, 크루 목록 빈 상태(조건 결과 없음)의 피그마 사양: 해당 프레임이 없다.
- F-08b 축하 팝업을 처음 들어왔을 때 자동으로 띄우는지 여부.
- 레이스 보드 행 32 원에 프로필 이미지를 넣는지 여부.
- F-03c 미리보기 카드의 `›`를 누르면 크루 상세(F-02)로 가는지 여부.
- 크루 로고 업로드 저장소(사진 업로드 API)가 있는지 여부.
- 다크 모드 값: 라이트 값만 봤다.
- 피그마 아이콘(groups, 돋보기 등)과 앱 RunkkIcons의 모양 비교: 에셋을 받아 비교하지 않았다.

### 채팅 (F-09~F-10b)

- "E10"의 뜻(docs에 F-10 E10 정의 없음).
- 반응 알약 선택색: 컴포넌트의 보라 #5C4BDB와 화면 인스턴스의 #67705B 가운데 어느 쪽인지.
- 반응한 사람 시트를 여는 제스처, 한 사람이 여러 종류를 단 경우의 행 모양.
- 메뉴의 반응 칸 선택 상태 모양. 운영진이 남의 메시지를 눌렀을 때 삭제와 신고가 함께 나오는지.
- 남의 메시지가 삭제된 모양, 빈 채팅, 보내는 중, 보내기 실패, 기타 사유 상세 누락 오류 표시(피그마 프레임에 없음).
- 날짜 알약의 "오늘" 말고 다른 날짜 형식. 말풍선 최대 폭.
- 다크 모드 값(이번에는 라이트 값만 확인함).
- 하트·체크 아이콘이 앱 `RunkkIcons.Heart`·`Check`와 같은 모양인지(에셋을 받아 비교하지 않음).

### 일정 (F-11~F-13e)


- F-12e: "하프" 아래에 더 있는 선택지. F-12d: 시(時) 범위. 날짜를 고른 상태의 모양.
- 남의 글에서 ⋯ 메뉴에 무엇이 있는지(신고가 있는지).
- 히어로 카드가 오늘이 아닌 일정일 때의 모양. 대기실이 열리기 전 버튼 상태.
- 앱 아이콘(Calendar, Route, Users)이 피그마 아이콘과 같은 모양인지.
- 서버에 날짜 범위(15일) 상한이나 시간 단위 제약이 있는지.
- 스크린샷으로만 본 프레임(F-11b, F-11d, F-12c, F-12d, F-12e, F-13d, F-13e)의 정확한 수치.

## 부록 A. 채팅 화면 공통 토큰 대응표 (채팅 비교에서 옮김)

피그마는 `Runkk · Theme` 변수를 쓴다. 앱 라이트 벌(`ds/RunkkColors.kt:128-162`)과 hex가 다른 것이 많다. 채팅 화면만 고칠지 테마 전체를 고칠지는 상위 결정이 필요하다.

| 피그마 토큰 | hex | 앱 가장 가까운 토큰 | 앱 hex | 비고 |
|---|---|---|---|---|
| runkk-color-background | #F7F8F2 | RunkkColors.Background / colorScheme.background | #FAFAFA | 다름 |
| runkk-color-surface | #FFFFFF | Surface / colorScheme.surface | #FFFFFF | 같음 |
| runkk-color-surfaceHigh | #E9EDE0 | 없음(runimalTile #E9EEDC가 근사) | - | 앱에 없음. 남의 말풍선·날짜 알약·크루 로고 배경·시트 손잡이에 쓴다 |
| runkk-color-divider | #E9EDE0 | Border / colorScheme.outlineVariant | #E4E4E7 | 다름 |
| color/menudivider | #E9EDE0 | Border | #E4E4E7 | 다름 |
| runkk-color-textPrimary | #171B12 | TextPrimary / onSurface | #18181B | 근사, 다름 |
| runkk-color-textSecondary | #67705B | TextSecondary / onSurfaceVariant | #52525B | 다름 |
| runkk-color-primary | #CAEB2E | LimeFill | #CBEA2B | 근사. 주의: 앱 `colorScheme.primary`는 라이트에서 Lime = **#3F6212(짙은 올리브)**다 |
| runkk-color-secondary | #5C4BDB | BackButton(#5C4BDB) / Violet·Lavender(#5B4FD9) | - | 앱 `colorScheme.secondary`는 Lavender #5B4FD9 |
| runkk-color-onSecondary | #FFFFFF | OnBackButton | #FFFFFF | 같음 |
| color/chatmine/bg | #FFFFFF | Surface | #FFFFFF | 내 말풍선 배경 |
| color/chatmine/stroke | rgba(69,74,61,0) | - | - | 투명이라 테두리가 없다 |
| color/tabselected/text | #171B12 | OnTabSelected | #171B12 | 같음 |
| color/radio/stroke, radio/selectedouter | #E9EDE0 | colorScheme.outline(BorderStrong) | #8A8A93 | 다름 |
| 신고 내용 입력칸 테두리(원시값) | #C7CCBF | InputBorder | #71717A | 다름 |
| 팝업 스크림 | rgba(23,27,18,0.45) | M3 기본 스크림 | - | 다름 |
| 글꼴 | IBM Plex Sans KR (Regular/Medium/SemiBold/Bold) | NotoSansKr 400/500/700/900 (`ds/RunkkType.kt:26`) | - | 앱 전체 문제다. 채팅만의 차이가 아니다 |


## 부록 B. 일정 화면 공통 차이 (일정 비교에서 옮김)


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

