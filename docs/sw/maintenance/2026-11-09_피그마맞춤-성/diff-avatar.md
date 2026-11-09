# 아바타·상점 화면 피그마 대조 (H-1 ~ H-6)

- 피그마: fileKey `NMTtVMWIT22D0CwOU2y0I9`, H-1 `62025:6568`, H-2 `62025:6668`, H-3 `62025:6731`, H-4 `62025:6826`, H-5 `62025:6959`, H-6 `62025:7065` (모두 390x844, Light 값 기준).
- 앱 기준: **origin/develop(7ff05509, #276 셀카 비동기 병합 포함)**. 로컬 develop(d3b9d4dd)은 이 병합보다 뒤처져 있어 `AvatarSetupScreen.kt`·`AvatarSetupUiState.kt`·`FaceConsent.kt`·`strings.xml`이 다르다. 아래 줄 번호는 origin/develop 기준이다(AvatarScreens.kt·ShopScreen.kt·AvatarBackgroundTab.kt·core/ui 컴포넌트는 두 브랜치가 같다).
- 경로 약어: `avatar/` = `android/feature/avatar/src/main/java/com/a207/runkk/app/ui/avatar/`, `ds/` = `android/core/ui/src/main/java/com/a207/runkk/app/core/designsystem/`.
- 다크 모드 피그마 값은 확인 못 함(get_variable_defs가 Light 값만 돌려줌).

## 0. 공통 (모든 H 화면에 걸리는 차이)

### 0-1. 색 토큰 대응

| 피그마 변수 | 피그마 값(Light) | 가장 가까운 앱 토큰 (ds/RunkkColors.kt light 128~163) | 차이 |
|---|---|---|---|
| runkk-color-background | #F7F8F2 | `RunkkColors.Background` #FAFAFA | 다름 |
| runkk-color-surface | #FFFFFF | `RunkkColors.Surface` #FFFFFF | 같음 |
| runkk-color-divider | #E9EDE0 | `RunkkColors.Border` #E4E4E7 | 다름 |
| runkk-color-surfaceHigh | #E9EDE0 | 대응 토큰 없음(`runimalTile` #E9EEDC가 가장 가깝다) | 누락 |
| runkk-color-textPrimary | #171B12 | `RunkkColors.TextPrimary` #18181B (`OnLimeFill`이 #171B12) | 다름(미세) |
| runkk-color-textSecondary | #67705B | `RunkkColors.TextSecondary` #52525B | 다름 |
| runkk-color-primary | #CAEB2E | `RunkkColors.LimeFill`/`SelectedBorder`/`TabSelected` #CBEA2B | 같음(1단계 차) |
| runkk-color-onPrimary | #171B12 | `RunkkColors.OnLimeFill` #171B12 | 같음 |
| runkk-color-secondary (Purple 버튼) | #5C4BDB | 버튼용 토큰 없음. `BackButton` #5C4BDB, `Violet`/`Lavender`(light) #5B4FD9 | 누락(보라 채움 버튼 컴포넌트 없음) |
| runkk-color-onSecondary | #FFFFFF | `OnBackButton` #FFFFFF | 버튼용 토큰 없음 |
| 대화상자 scrim | rgba(31,31,31,0.5) H-5 / 0.55 H-6 | Material 기본 scrim(AlertDialog) | 다름 |

주의: 앱 `RunkkColors.Lime`은 라이트에서 짙은 올리브 #3F6212다. 피그마의 "라임 테두리/강조"(primary #CAEB2E)를 그대로 옮기려면 `Lime`이 아니라 `LimeFill`/`SelectedBorder`를 써야 한다. 현재 아바타 화면은 선택 테두리·잔액 글자·선택 라벨에 `Lime`을 써서 라이트에서 올리브로 보인다.

### 0-2. 글꼴·글자 스타일

| 피그마 스타일 | 값 | 앱 대응 (ds/RunkkType.kt) | 차이 |
|---|---|---|---|
| 글꼴 | IBM Plex Sans KR | Noto Sans KR(400·500·700·900만 선언, 26행) | 다름(글꼴 자원 없음, SemiBold 600 없음) |
| RunKK/Settings/Title | Bold 20 / 30 | `TopBarTitle` 18 Black(78행), `Headline` 24/32 Black(75행) | 다름 |
| RunKK/Settings/Label | Medium 14 / 22 | 없음(`Label`은 13 Regular, 93행) → `text(14, Medium, 22)` | 누락 |
| RunKK/Settings/Body | Regular 14 / 22 | `Body` 14(줄높이 없음, 87행) | 줄높이 다름 |
| RunKK/Settings/Caption | Regular 12 / 18 | `Caption` 12(줄높이 없음, 96행) | 줄높이 다름 |
| 버튼 라벨 | SemiBold 15 | `Button` 16 Black(102행), 보조 버튼 15 Bold(RunkkButtons.kt 233행) | 다름 |
| 캐릭터 칸 라벨 | Medium 10 | 없음 | 누락 |

### 0-3. 공통 컴포넌트

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 상단 바 높이·배경 | 62, 흰 표면(surface), 아래 1px divider | `RunkkTopBar` 최소 64, 배경 없음, 아래 선 없음 (ds/RunkkBars.kt:77-97) | 다름 |
| 상단 바 좌우 여백 | H-1·3·5: px12 / H-2·4: px16, 3칸 그리드 76/206/76 | px16 (RunkkBars.kt:86) | 다름 |
| 제목 위치·스타일 | 가운데 정렬, Bold 20/30 textPrimary | 왼쪽 정렬(isTitleCentered 기본 false), 18 Black (RunkkBars.kt:81,99-106) | 다름(아바타 화면은 `isTitleCentered=true` 안 씀) |
| 뒤로 버튼 | "RunKK/Buttons/뒤로가기": 배경 원 없음, arrow_back(←) 24, 색 textPrimary, 44 칸(40 터치) | `RunkkBackButton`: 라이트는 보라 채움 원 + 흰 셰브런(<) (RunkkBars.kt:111-121, RunkkIcons `Back`="M15 18L9 12L15 6") | 다름(아이콘 모양·원) |
| 기본 버튼 모양 | 알약(rounded 100), Medium=48, Large=52 | `RunkkPrimaryButton` rounded 16, 높이 56 (RunkkButtons.kt:54-56,74-) | 다름 |
| Lime 버튼 | primary 채움, SemiBold 15 onPrimary | LimeFill 채움, 16 Black (RunkkButtons.kt:117-126) | 다름(글자) |
| White 버튼 | 흰 채움, **테두리 없음**, SemiBold 15 textPrimary | `RunkkSecondaryButton` 흰 채움 + BorderStrong 테두리, rounded 16, 56, 15 Bold (RunkkButtons.kt:153-170,196-235) | 다름 |
| Outline 버튼 | 흰 채움 + divider 1px 테두리, 48, 알약 | 위와 같음(BorderStrong #8A8A93 테두리, rounded 16, 56) | 다름 |
| Purple 버튼 | secondary #5C4BDB 채움, 흰 SemiBold 15, 알약 | 없음 | 누락 |
| 하단 고정 영역 | 흰 표면, 위 1px divider, pt12 pb32 px20, 버튼 사이 10 | `HorizontalDivider` + 배경 없음(Background), 16/12/32 또는 16/16/32, 간격 8 | 다름 |

---

## H-1 얼굴 만들기 (62025:6568) ↔ `AvatarSetupScreen` (avatar/AvatarSetupScreen.kt, origin/develop)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 상단 바 | "얼굴 만들기" 가운데 Bold 20, 흰 바+divider, ← 아이콘 | `RunkkTopBar(title, onBack)` 왼쪽 18 Black, 보라 원 뒤로 (AvatarSetupScreen.kt:134) | 다름(0-3 참고) |
| 본문 여백·간격 | px24 py16, 세로 간격 16 | padding 16, spacedBy 20 (136-137) | 다름 |
| 제목 문구 | "아바타 얼굴을 만들어 볼까요?" Bold 20/30 | 같은 문구, `Headline` 24/32 Black -0.03em (139-144) | 다름(크기·굵기) |
| 미리보기 카드 | 흰 카드 높이 200, rounded 20, 가운데 **146x176 세로 직사각형**(surfaceHigh, rounded 16) "RunKK/Avatar/아바타 미리보기"(사람 아이콘 24 + "아바타 미리보기" 12 Medium textSecondary는 자리표시) | 흰 카드 rounded 24, 세로 패딩 16, **112dp 원형 얼굴**(Border 배경) (174-210) | 다름(모양·크기: 원형 얼굴 → 세로 직사각형 전신/상반신 자리) |
| 만드는 중 표시 | 없음 | 진행 원 124dp + "얼굴을 만들고 있어요"(192-208) | 앱에만 있음(유지 권장, 피그마에 상태 없음) |
| 셀카 카드 틀 | 흰 표면, **divider 1px** 테두리, rounded 20, padding 16, 간격 12 | `RunkkCard(borderColor = Lime 또는 Border)` 만들 수 있으면 라임(라이트 올리브) 테두리, 간격 8 (225) | 다름(테두리 색, 간격) |
| 카드 제목 | "셀카로 만들기" Label 14 Medium | `CardTitle` 16 Black (227-232) | 다름 |
| "하루 1번" 꼬리표 | surfaceHigh 배경, rounded 6, px8 py3, Caption 12 textSecondary | `RunkkBadge` Lime 톤(LimeContainer 배경, Lime 글자 12 Bold, 높이 28, rounded 8), 못 만들면 Muted (233-236, ds/RunkkChips.kt:303-322) | 다름 |
| 동의 체크 | M3 체크박스 18(테두리 2px #49454F, rounded 2), 글자 Body 14 두 줄 "얼굴 변환 서비스로 셀카를 보내는 데 / 동의해요 (선택)", 간격 10 | `RunkkCheckRow`(M3 Checkbox, unchecked=InputBorder #71717A, 간격 12, Body) 문구 같음 (238-242) | 다름(미세: 색·간격). 로컬 develop에는 이 체크가 없음 |
| 셀카 버튼 2개 | Outline 알약 48, 각 149 폭, 간격 10, 아이콘 없음, "셀카 찍기"/"앨범에서 고르기" | `RunkkSecondaryButton` 56, rounded 16, 간격 8, "셀카 찍기"에 카메라 아이콘 (243-257) | 다름(높이·모서리·아이콘·간격) |
| 구역 제목 | "기본 캐릭터 얼굴 고르기" Label 14 Medium textPrimary | `RunkkSectionTitle` 13 Bold **textSecondary** (147, ds/RunkkBars.kt:476-485) | 다름 |
| 캐릭터 칸 배치 | 4칸 한 줄, 각 76.5x**80**(정사각 아님), 간격 12, rounded 16 | 정사각(aspectRatio 1), 간격 8, rounded 16 (263-311) | 다름 |
| 캐릭터 칸 선택 상태 | 선택: surfaceHigh 배경 + primary(#CAEB2E) 2px / 비선택: 흰 배경 + divider 1px | 선택: LimeContainer 배경 + Lime(라이트 올리브) 2dp / 비선택: Surface + Border 1dp (289-294) | 다름(색) |
| 캐릭터 칸 내용 | 캐릭터 그림 자리(아이콘 24) + 아래 라벨 "기본 1"~"기본 4" Medium 10 textSecondary | 원형 캐릭터 그림만, 라벨 없음(이름은 contentDescription, 그림 못 그릴 때만 이름 글자) (303-309) | 누락(보이는 라벨). 문구도 "기본 n" vs 앱 이름(토끼·곰·여우·고양이) |
| 오류 문구 | 없음 | Lavender 13 Bold 문구(149-156) | 앱에만 있음 |
| 저장 버튼 | 하단 흰 영역(위 divider, pt12 pb32 px20), Lime 알약 52 x 350, "저장" | `HorizontalDivider` + `RunkkPrimaryButton` 56 rounded 16, padding 16/16/32 (158-165) | 다름 |
| 저장 활성 조건 | 상태 없음(확인 못 함) | 캐릭터를 골라야 켜짐, 저장 중 진행 원 (162-163) | 앱에만 있음 |

## H-2 아바타 "내 러너" (62025:6668) ↔ **앱에 화면 없음**

앱은 홈 아바타를 누르면 곧바로 꾸미기(`Routes.AVATAR`)로 가고(android/feature/home/.../HomeScreen.kt:529), 홈 잔액 알약은 상점으로 간다(HomeScreen.kt:337). "내 러너" 화면과 그 경로가 없다.

| 항목 | 피그마 | 앱 | 차이 종류 |
|---|---|---|---|
| 화면 자체 | 상단 바 "내 러너"(3칸 그리드, 가운데 Bold 20) + 오른쪽 잔액 알약 "리워드 1,240"(surfaceHigh, rounded 8, 높이 30, px10, Medium 14) | 없음 | 누락 |
| 본문 | 배경 Background, pt16 pb22 px20, 간격 12, 세로 스크롤 | 없음 | 누락 |
| 아바타 카드 | 350x390, rounded 24, 랜드마크/기본 배경 그림(img b8bbe.png 공원 장면) 전체 채움, 아래쪽에 165x330 "내 3D 아바타" 자리(top 54) | 없음(꾸미기 `AvatarFigure`는 150x210 상자, AvatarScreens.kt:332-354) | 누락 |
| 왼쪽 위 칩 | "근육 Lv.3" (흰 배경, divider 1px, rounded 999, 높이 24, px10, Caption 12 textSecondary, left 14 top 14) | 없음(체형 단계는 `bodyStage()`로 계산 가능, AvatarSupport.kt) | 누락 |
| 오른쪽 위 칩 | "아침 피부톤" (같은 스타일, 오른쪽 위) | 없음(`skinLabel()`은 "아침형" 문구, AvatarSupport.kt) | 누락(문구 형식 "아침 피부톤" 새로 필요) |
| 체형 변화 카드 | 흰 카드 divider 1px, rounded 20, px18 py16, 간격 8. 제목 "체형 변화" Label 14 | 없음 | 누락 |
| 체형 게이지 | "배가 볼록" Caption + 트랙(surfaceHigh, 높이 12, rounded 999) + 흰 원 손잡이 22 + "근육 탄탄" Caption | 없음 | 누락 |
| 체형 안내 | "이번 주 3번 달렸어요. 4일 넘게 쉬면 배가 나오기 시작해요." Caption textSecondary | 없음(FR-163 요구) | 누락 + **데이터 필요**(이번 주/최근 7일 러닝 횟수) |
| 피부톤 카드 | 흰 카드 divider, rounded 20, px18 py12, 간격 2. "피부톤은 달리는 시간을 따라가요" Label 14 + "주로 오전 7시에 달려서 아침 톤이에요" Caption | 없음(FR-164 요구) | 누락 + **데이터 필요**(주 러닝 시각) |
| 하단 버튼 | 본문 끝(빈 공간 채움 뒤) Lime "꾸미기" 56 x 169 + White "상점 가기" 56 x 171, 간격 10, 알약 | 없음 | 누락 |
| 진입 경로 | 홈 아바타 → 내 러너 → 꾸미기/상점 (추정, 피그마 프로토타입 연결은 확인 못 함) | 홈 아바타 → 꾸미기 | 다름 |

## H-3 꾸미기 (62025:6731) ↔ `AvatarScreen` (avatar/AvatarScreens.kt) + `AvatarBackgroundTab` (avatar/AvatarBackgroundTab.kt)

피그마 H-3은 "배경" 탭을 고른 상태만 그린다. 상의·하의·신발 탭의 보유 아이템 칸 모양은 피그마에 없어 확인 못 함.

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 상단 바 제목 | "아바타 꾸미기" 가운데 Bold 20 | 왼쪽 18 Black (AvatarScreens.kt:151) | 다름 |
| 잔액 표시 | "[1,240] P" Medium 14 **textPrimary**, 꾸밈 없음 | `Balance()` "1,240 P" 14 Bold **Lime(라이트 올리브)**, end 8 (AvatarScreens.kt:294-303) | 다름(색·굵기) |
| 본문 여백 | px20 py16, 간격 12 | padding 16, 간격 12 (198-199) | 다름(좌우 20) |
| 미리보기 영역 | **전체 폭 흰 카드**(rounded 20) 가운데 152x200 "RunKK/Avatar/내 러너" 자리(surfaceHigh, rounded 16). 배경 그림 없음 | 흰 카드 없음. 150x210 상자, rounded 24, Lime 2dp 테두리, 고른 배경 그림 위에 사각 몸·원형 머리 그림 (AvatarScreens.kt:332-385) | 다름(카드 유무·크기·테두리·배경) |
| 미리보기 배경 | 없음(자리표시만) | 고른 랜드마크 배경을 그린다(202-208) | 앱에만 있음. FR-202 "고른 배경은 아바타 화면(FR-162)에 함께 보인다"와 피그마가 어긋날 수 있어 확인 필요 |
| 체형 칩 | "체형 [보통]" 흰 배경, divider 1px, **rounded 16**, 높이 32, px12, Body 14 Regular textPrimary | `RunkkInfoChip` 투명 배경, BorderStrong 테두리, rounded 16, px16, Caption 12 textSecondary (AvatarScreens.kt:242-244, ds/RunkkChips.kt:224-258) | 다름(배경·테두리·글자) |
| 피부색 칩 | "피부색 [저녁형]" 같은 스타일, **rounded 12** | 위와 같음(rounded 16) (245) | 다름 |
| 얼굴 바꾸기 | 흰 배경, divider 1px, 높이 34, rounded 18, px12, Body 14, **아이콘 없음** | `RunkkPillButton` 높이 40, rounded 20, BorderStrong, Label 13, **카메라 아이콘** (246-250, ds/RunkkButtons.kt:239-287) | 다름 |
| 칩 줄 배치 | 칩 2개 묶음(간격 8) + 얼굴 바꾸기, 묶음 사이 10, 가운데 정렬, 한 줄 | FlowRow 간격 8 가운데 정렬(236-241) | 다름(미세) |
| 탭 줄 컨테이너 | surfaceHigh(#E9EDE0) 배경, rounded 12, padding 4 | `RunkkSegmentedTabs` Background 배경, rounded 16, padding 4 (211-223, ds/RunkkChips.kt:330-378) | 다름 |
| 탭 칸 | 높이 36, rounded 9, 고른 탭 **흰 표면** + Medium 14 textPrimary, 나머지 Regular 14 **textPrimary** | 최소 48(안쪽 40), rounded 12, 고른 탭 TabSelected(라이트 라임 채움) 15 Black, 나머지 15 Regular textSecondary | 다름 |
| 탭 문구 | 상의·하의·신발·**악세사리**·배경 | 상의·하의·신발·**모자**·배경 (strings avatar_slot_hat) | 다름 + **요구사항 충돌**(아래 7절) |
| 구역 제목 | "랜드마크 배경" Label 14 Medium | 13 Bold (AvatarBackgroundTab.kt:60-65) | 다름 |
| 해금 수 | "해금 [3] / [12]" Caption 12 textSecondary | "해금 %d / %d" Caption textSecondary (66-74) | 같음 |
| 격자 | 3열, 칸 간격 8(가로·세로 12 묶음 간격), 칸 111x**190** 고정 높이 | FlowRow 3열, 간격 8, 높이는 내용에 따름 (113-122) | 다름(높이) |
| 칸 틀 | 흰 배경, divider 1px, rounded 16, padding 8, 간격 8 | Surface, Border 1dp, rounded 16, padding 8, 간격 **6** (144-161) | 다름(간격) |
| 칸 그림 | 95x**126 세로**(rounded 12, surfaceHigh 바탕) | `aspectRatio(1.4f)` **가로로 넓은** 그림, rounded 12 (162-166, 188) | 다름(비율) |
| 칸 라벨 | Caption 12 textSecondary 가운데 | Caption 가운데(176-183) | 같음 |
| 기본 배경 라벨 | "기본" | "기본 배경" (strings avatar_background_default) | 다름(문구) |
| 사용 중 칸 | 테두리 **primary #CAEB2E 2px**, 라벨 "서울, 사용 중" **textSecondary** | 테두리 `Lime`(라이트 올리브) 2dp, 라벨 색 `Lime` (149-150, 179) | 다름(색) |
| 잠긴 칸 배경 | 칸 전체 **surfaceHigh**, divider 1px | Background(#FAFAFA) (147) | 다름 |
| 잠긴 칸 그림 | 그림 없이 surfaceHigh 바탕 + 자물쇠 16 가운데 | 배경 그림을 알파 0.35로 흐리게 + 자물쇠 16 TextPrimary (166-174, 189) | 다름 |
| 잠긴 칸 문구 | "[전주], [113]km 더 달리면 해금" (남은 거리, 폭 94 줄바꿈) | "전주, 누적 1,000km에 해금" (해금 기준 누적 거리, strings avatar_background_locked) | 다름 + **데이터 필요**(남은 거리 = 기준 − 내 누적 거리) |
| 잠긴 칸 누름 | 확인 못 함 | 스낵바로 해금 거리 안내 (AvatarBackgroundViewModel.onChoose) | 앱에만 있음(유지) |
| 하단 버튼 | 흰 영역(위 divider, pt12 pb32 px20, 간격 10): White "상점 가기" 54 x 165(테두리 없음) + Lime "저장" 52 x 175, 알약 | Divider + `RunkkSecondaryButton`(테두리) + `RunkkPrimaryButton`, 각 weight 1, 56, rounded 16, 간격 8, padding 16 (AvatarScreens.kt:166-182) | 다름 |
| 랜드마크별 그림 | 서울·대전·부산·전주·제주 등 랜드마크마다 다른 배경 그림 자리 | 단계별 그림 3장(domestic·east_asia·intercontinental)만 (ds/AvatarBackdrop.kt) | 누락(그림 자원) |
| 보유 아이템 탭 칸 | 피그마에 없음 | `ItemTile`(카드 + "입는 중"/부위 꼬리표) (AvatarScreens.kt:305-325) | 확인 못 함 |
| 아바타 그림 | "내 러너" 자리(실제 그림 삽입 예정) | 사각·원 도형 임시 그림(피부색 원, 몸 막대, 다리) (356-440) | 다름(그림 자원 없음) |

## H-4 상점 (62025:6826) ↔ `ShopScreen` (avatar/ShopScreen.kt)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 상단 바 | 3칸 그리드(76/206/76), px16, 가운데 "상점" Bold 20 | 왼쪽 18 Black (ShopScreen.kt:87) | 다름 |
| 잔액 | 알약 "1,240"(surfaceHigh 배경, rounded 8, 높이 30, px10, Medium 14 textPrimary, **P 없음**) | "1,240 P" Bold 14 Lime 글자만 (ShopScreen.kt:88 → AvatarScreens.kt:294-303) | 다름 |
| 부위 선택 모양 | **필터 칩 줄**: 칩 높이 40, rounded 10, px16, 간격 8, 내용 폭(가로 스크롤/왼쪽 정렬). 고른 칩 primary 채움 + onPrimary Regular 14, 나머지 흰 표면(테두리 없음) textPrimary Regular 14 | `RunkkSegmentedTabs` 같은 폭 4칸, Background 컨테이너 (90-95) | 다름 |
| 부위 문구 | 상의·하의·신발·모자·**소품**(5개) | 상의·하의·신발·모자(4개, `ItemSlot` 4값) | 누락(소품) + **요구사항 충돌** |
| 본문 여백 | px20, pt16, **pb100**(하단 구매 바 자리), 간격 12 | padding 16 (181) | 다름 |
| 격자 | 2열, 칸 169x**176 고정**, 가로·세로 간격 12 | FlowRow 2열, 간격 8, 높이 내용에 따름 (180-188) | 다름 |
| 칸 틀 | 흰 표면, divider 1px, rounded 20, padding **12**, 간격 **4** | `RunkkCard` padding 16, 간격 8, rounded 20, Border (202-206) | 다름 |
| 칸 그림 | 높이 96, rounded 12, surfaceHigh 바탕 + 가운데 **아이템 그림 66x66**(티셔츠 아이콘) | `ItemPicture` 높이 72, rounded 16, Border 바탕 + 부위 이름 글자 (149-157) | 다름 + **그림 자원/데이터 필요** |
| 이름 | Body 14 **Regular** textPrimary | `BodyStrong` 14 Bold (208) | 다름 |
| 가격 | "300" Label 14 Medium **textPrimary**, **P 없음**, 단독 | "300 P" 14 Bold Lime + 오른쪽 라임 알약 "구매" (214-222) | 다름 |
| 칸별 구매 버튼 | **없음**(칸을 눌러 고른 뒤 하단 바에서 산다) | 칸마다 `RunkkLimePillButton("구매")` (221) | 앱에만 있음 |
| 선택 상태 | 고른 칸: primary #CAEB2E 2px 테두리 | 없음(칸 선택 개념 없음, `ShopUiState`에 선택 필드 없음) | 누락 |
| 보유 상태 | 가격 자리 "**소지 중**" Label 14 Medium textPrimary(꼬리표 아님) | `RunkkBadge("보유 중", Lime)` (210) | 다름(문구·모양) |
| 잠김 상태 | 칸 opacity 0.45, 가격 자리 "하프 완주하면 열려요" | 없음 | 누락 + **데이터 필요** + **요구사항 충돌**(N-17) |
| 판매 종료 상태 | 피그마에 없음 | `RunkkBadge("판매 종료", Muted)` (212) | 앱에만 있음 |
| 하단 구매 바 | 화면 아래 떠 있는 카드: left 14, 폭 362, top 760(아래 여백 약 14), 흰 표면, divider 1px, rounded 20, pl18 pr10 py10, 간격 10. 왼쪽 이름 Label 14 + "사면 940 남아요" Caption textSecondary, 오른쪽 **Purple 알약 50 x 110 "300에 사기"** | 없음 | 누락 |
| 구매 흐름 | 칸 선택 → 하단 바 "n에 사기" → 확인(H-5) | 칸의 "구매" → 바로 확인 대화상자 (ShopViewModel.onPick) | 다름 |
| 잔액 부족 | 피그마에 상태 없음(확인 못 함) | 스낵바 "리워드가 모자라요…"(ShopViewModel.onPick, FR-160) | 앱에만 있음(유지, FR-160) |
| 처음 탭 | 상의 | 상의 (ShopUiState.slot 기본 TOP) | 같음 |

## H-5 아이템 구매 확인 (62025:6959) ↔ `BuyConfirmDialog` (avatar/ShopScreen.kt:103-146)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 대화상자 틀 | 직접 그린 카드: 폭 334(좌우 28), 흰 표면, rounded **28**, pt28 pb24 px24, 간격 16, scrim rgba(31,31,31,0.5) | Material `AlertDialog`(containerColor Surface) (106-110) | 다름 |
| 제목 | "[아이템]을 살까요?" Bold 20/30 textPrimary 왼쪽 | `CardTitle` 16 Black, "%1$s을(를) 살까요?" (111-113) | 다름(크기·조사 표기) |
| 아이템 그림 | **112x112** 정사각, surfaceHigh, rounded 20, 가운데 정렬, 아이템 그림 | `ItemPicture` 전체 폭 x 72, Border 바탕, 부위 이름 (116, 149-157) | 다름 |
| 가격·잔액 문구 | 가운데 정렬 Body 14 **textPrimary** 두 줄: "[500] P," / "구매 후 잔액 [740] P" | 한 줄 문자열 "%1$s P, 구매 후 잔액 %2$s P", Body **TextSecondary**, 왼쪽 (117-124) | 다름(줄바꿈·정렬·색) |
| 내용 묶음 간격 | 그림·문구 간격 16, 위아래 6 | spacedBy 12 (115) | 다름 |
| 버튼 | 위 여백 10, 가로 간격 10: **White "취소" 48 x 139 + Purple "구매" 48 x 137** 알약 | `TextButton` 두 개: "구매" Lime(라이트 올리브) BodyStrong, "취소" TextSecondary (127-144) | 다름 |
| 배경 상점 화면 | H-5 뒤 상점은 **H-4와 다른 모양**: 탭 줄(surfaceHigh 컨테이너, 상의·하의·신발·모자 4개), 2열 170x178(간격 10, padding 10, 간격 6), 그림 94, "[300] P" + 칸마다 "구매" 외곽선 알약(높이 30, rounded 18), 보유는 오른쪽 "보유 중" Caption, 잔액 "[1,240] P" 글자 | 앱 현재 상점과 더 비슷함 | **피그마 내부 불일치**(아래 7절) |

## H-6 배경 해금 (62025:7065) ↔ **앱에 없음**

앱은 배경 해금을 알림(BACKGROUND_UNLOCKED)으로만 알리고, 알림을 누르면 꾸미기 배경 탭(`Routes.AVATAR_BACKGROUND`)으로 간다(android/feature/notification/.../InboxDestination.kt:19). 홈 위에 뜨는 해금 대화상자는 없다.

| 항목 | 피그마 | 앱 | 차이 종류 |
|---|---|---|---|
| 뜨는 자리 | 홈 화면(A5) 위 scrim rgba(31,31,31,0.55) | 없음 | 누락 |
| 대화상자 틀 | 폭 342(좌우 24), 흰 표면, rounded 28, pt24 pb20 px20, 간격 14, 가운데 정렬 | 없음 | 누락 |
| 미리보기 | 280x**500** 세로, surfaceHigh, rounded 16, "[대전 랜드마크 배경 미리보기]"(랜드마크 배경 그림 자리) | 없음(랜드마크별 그림도 없음) | 누락 + 그림 자원 |
| 제목 | "[대전] 배경을 열었어요" Bold 20/30 왼쪽 | 없음 | 누락 |
| 함께 연 배경 상자 | surfaceHigh 배경 + divider 1px, rounded 16, px12 py10, 간격 4: "이번 러닝으로 함께 연 배경" Caption textSecondary + 배경 이름 줄마다 Body 14("[대전]", "[세종]") | 없음 | 누락 + **데이터 필요**(이번 러닝에 연 배경 목록) |
| 버튼 | White "닫기" 52 x 153 + Lime "배경 바꾸기" 52 x 139, 간격 10, 알약 | 없음 | 누락 |
| "배경 바꾸기" 동작 | 제목의 배경으로 바꾼다고 보임(여러 개일 때 어느 것인지 확인 못 함) | `chooseBackground(key)` API는 있음 | 화면·흐름 누락 |
| 1개만 열었을 때 | "함께 연 배경" 상자 표시 여부 확인 못 함 | - | 확인 못 함 |

---

## 7. 피그마와 요구사항(docs/sw)·피그마 내부가 충돌하는 곳 (사용자 확인 필요)

| # | 충돌 | 피그마 | 요구사항·앱 | 근거 |
|---|---|---|---|---|
| C-1 | 상점 "소품" 부위 | H-4 탭에 "소품" | 4부위(상의·하의·신발·모자) 유지, 소품 제외 | srs/07-03-미결-사항-결정.md:159 (N-17 "기존 유지(4부위)"), srs/07-05-변경-요청-CR.md:147 "상점 소품 부위는 계속 제외", FR-161 |
| C-2 | 잠긴 상점 아이템 | H-4 "완주 기념 티 — 하프 완주하면 열려요"(Locked) | 잠긴 아이템 없음 | N-17 "상점 소품 부위, 잠긴 아이템 → 기존 유지", FR-201 출처 "N-22 업적 해금 아이템 제외" |
| C-3 | 꾸미기 탭 "악세사리" | H-3 "악세사리" | FR-161 "상의·하의·신발·모자", H-4·H-5는 "모자" | srs/03-06-캐릭터-관리.md FR-161 |
| C-4 | 꾸미기 미리보기 배경 | H-3 미리보기에 배경 없음 | FR-202 "고른 배경은 아바타가 보이는 화면(FR-162)에 함께 보인다" | srs/03-06-캐릭터-관리.md FR-202 (H-2에는 배경이 있음) |
| C-5 | 상점 화면 두 가지 | H-4: 필터 칩 + 칸 선택 + 하단 구매 바, "소지 중", 가격 "300" | H-5 뒤 상점: 탭 줄 + 칸별 "구매" 버튼, "보유 중", "[300] P" | 피그마 내부 불일치. H-4를 기준으로 할지 확인 필요 |
| C-6 | 잔액 표기 | H-2 "리워드 1,240"(알약), H-3·H-5 "[1,240] P"(글자), H-4 "1,240"(알약) | 앱은 모두 "1,240 P" 라임 글자 | 피그마 내부 불일치 |
| C-7 | 기본 캐릭터 라벨 | "기본 1"~"기본 4" | 앱 이름 토끼·곰·여우·고양이(FR-158 "기본 캐릭터 얼굴 4종") | 피그마가 자리표시 문구인지 확인 필요 |

FR-163(체형 게이지와 "4일 넘게 쉬면 배가 나오기 시작해요")과 FR-164("주로 오전 7시에 달려서 아침 톤이에요" 근거 문구)는 피그마 H-2와 맞고 앱이 빠뜨린 것이다(충돌 아님). FR-155 앞 흐름("아바타 화면에서 얼굴 변환 전송에 동의", srs/02-전체-설명.md:314)은 H-1 체크와 맞고 origin/develop에 이미 들어갔다.

---

## 8. 기능·데이터가 필요한 차이 (서버 API·응답 필드·UiState 추가)

| # | 화면 | 필요한 것 | 현재 상태 | 비고 |
|---|---|---|---|---|
| F-1 | H-2 | "내 러너" 화면 신규: 경로(Routes), ViewModel, UiState(아바타·잔액·배경·체형·피부톤) | 없음 | 홈 아바타 진입을 H-2로 바꿀지 결정 필요(HomeScreen.kt:529) |
| F-2 | H-2 | 체형 안내용 "이번 주 n번 달렸어요" 횟수 | AvatarResponse에 없음 | FR-163은 "최근 7일 유효 러닝 횟수". `Dashboard.weekly`(이번 주 실제 값)로 대신할지, 서버 AvatarResponse에 필드(예: recentRunCount)를 더할지 결정 필요 |
| F-3 | H-2 | 피부톤 근거 "주로 오전 7시에" 대표 시각 | AvatarResponse에 없음(서버 AppearanceCalculator는 recentStartTimes를 씀) | 서버 응답 필드 추가 필요 |
| F-4 | H-2 | 체형 게이지 손잡이 위치 | bodyShape STAGE1~4만 있음 | 단계 4칸으로 놓을지 연속값인지 확인 못 함(피그마 손잡이 x 137.58 / 트랙 200.79) |
| F-5 | H-3 | 잠긴 배경 "n km 더 달리면 해금"(남은 거리) | 배경 응답에 해금 기준 거리(distanceMeters)만 있음 | 서버에 남은 거리/내 누적 거리 필드 추가, 또는 `Dashboard.total`(RecordRepository)로 앱에서 계산 |
| F-6 | H-3·H-6 | 랜드마크별 배경 그림 | 단계 3종 그림만 | 그림 자원(또는 서버 이미지 URL) 필요 |
| F-7 | H-4 | 칸 선택 상태와 하단 구매 바 | `ShopUiState`에 선택 아이템 없음 | UiState에 `selected: Shop.Item?` 추가, 구매 흐름(onPick → 선택, 바 버튼 → confirming) 변경. 서버 변경 없음 |
| F-8 | H-4·H-5 | 아이템 그림 | `Shop.Item`·서버 `ShopItem(id,name,slot,price,onSale)`에 그림 키 없음 | 아이템 그림 키/URL 필드 또는 앱 내 itemId→그림 대응표 필요 |
| F-9 | H-4 | 잠긴 아이템과 해금 조건 문구 | 없음 | C-2 결정 뒤. 채택하면 서버 필드(locked, unlockCondition) 추가 필요 |
| F-10 | H-4·H-3 | "소품"/"악세사리" 부위 | `ItemSlot` 4값(도메인·서버 common.ItemSlot) | C-1·C-3 결정 뒤. 채택하면 서버 enum·DB·앱 enum 모두 변경 |
| F-11 | H-6 | 이번 러닝으로 연 배경 목록과 대화상자 띄울 시점 | 알림 BACKGROUND_UNLOCKED만 있음. `AvatarBackgrounds.Item.unlockedAt`은 있음 | 앱이 "마지막으로 본 해금 시각"을 저장해 비교하거나, 러닝 저장 응답/홈 대시보드에 새 해금 목록 필드를 서버가 주거나 결정 필요. 홈(A5) 기능 모듈에 띄우는 진입점도 필요 |
| F-12 | H-6 | "배경 바꾸기"로 고를 배경(여러 개 열었을 때) | API `chooseBackground(key)`는 있음 | 어느 배경인지 확인 못 함 |
| F-13 | 공통 | 피그마 테마 값(background #F7F8F2, divider/surfaceHigh #E9EDE0, textSecondary #67705B, IBM Plex Sans KR) | 앱 라이트 벌 값이 다름, surfaceHigh·Purple 버튼 토큰 없음 | core/ui 테마 변경은 모든 화면에 퍼진다. 아바타 화면만 맞출지, 테마 토큰을 바꿀지 결정 필요 |

## 9. 화면만 고치면 되는 차이 (기존 데이터로 가능)

- 공통: 상단 바(흰 바+divider, 가운데 Bold 20, 원 없는 ← 뒤로), 알약 버튼(48/52, rounded 100), White(테두리 없음)·Outline(divider)·Purple 버튼 모양, 하단 고정 흰 영역(위 divider, pt12 pb32 px20, 간격 10), 선택 테두리를 `Lime` → primary(`LimeFill`/`SelectedBorder`)로.
- H-1: 본문 px24·간격 16, 제목 Bold 20, 미리보기 146x176 세로 자리, 셀카 카드 테두리 divider·제목 Label 14·"하루 1번" 꼬리표 surfaceHigh rounded 6, 셀카 버튼 알약 48·아이콘 제거·간격 10, 구역 제목 Label 14 textPrimary, 캐릭터 칸 76.5x80·간격 12·선택 surfaceHigh+primary 2px·아래 라벨(Medium 10), 저장 Lime 알약 52.
- H-3: 미리보기를 전체 폭 흰 카드(rounded 20) 안 152x200으로, 칩 2개(흰 배경·divider·rounded 16/12·px12·Body 14 textPrimary), 얼굴 바꾸기(높이 34·rounded 18·아이콘 없음), 탭 줄(surfaceHigh 컨테이너 rounded 12, 탭 36·rounded 9, 고른 탭 흰 표면 Medium 14, 나머지 Regular 14 textPrimary), "랜드마크 배경" Label 14, 배경 칸 111x190 고정·그림 95x126 세로·칸 간격 8·라벨 간격 8, 사용 중 테두리 primary·라벨 textSecondary, 잠긴 칸 surfaceHigh 바탕에 자물쇠만, 기본 배경 라벨 "기본", 잔액 "1,240 P" Medium 14 textPrimary, 하단 버튼 White+Lime 알약.
- H-4: 가운데 제목, 잔액 알약(surfaceHigh·rounded 8·높이 30·P 없음), 필터 칩 줄(높이 40·rounded 10·px16·간격 8, 고른 칩 primary), 본문 px20 pb100, 칸 169x176·padding 12·간격 4·그림 영역 96(surfaceHigh·rounded 12), 이름 Regular 14, 가격 Medium 14 textPrimary "300", 보유 "소지 중"(문구는 C-5 결정 뒤), 칸별 구매 버튼 제거(F-7과 함께).
- H-5: 커스텀 대화상자(폭 334, rounded 28, 28/24/24 padding, scrim 0.5), 제목 Bold 20, 그림 112x112 rounded 20, 문구 가운데 두 줄 textPrimary("%1$s P," + 줄바꿈 + "구매 후 잔액 %2$s P"), White "취소" + Purple "구매" 알약 48.

## 10. 피그마 에셋 중 앱에 없는 것

| 에셋 | 피그마 위치 | 앱 상황 |
|---|---|---|
| arrow_back(←) 아이콘 24 (`35749.svg`, H-3은 `511c3.svg`) | 모든 상단 바 뒤로가기 | 없음. 앱 `RunkkIcons.Back`은 셰브런(<), ArrowLeft 없음(ArrowRight만 있음) |
| 아이템 그림: 티셔츠 아이콘 66x66 라임(`79115.svg`, 고른 칸)·보라(`a4ed0.svg`, 나머지) | H-4 상품 칸 | 없음(부위 이름 글자로 대신) |
| 아이템별 그림(라임 싱글렛·새벽 바람막이·크루 티셔츠·겨울 후디·체리 조끼 등) | H-4·H-5 | 없음(피그마도 같은 티셔츠 아이콘 재사용, 실제 그림은 확인 못 함) |
| 공원 장면 배경 그림(`b8bbe.png`) | H-2 아바타 카드 | `avatar_backdrop_default.webp`가 같은 그림인지 확인 못 함 |
| 랜드마크별 배경 그림(서울·대전·부산·전주·제주·세종…) | H-3 칸, H-6 미리보기(280x500) | 없음(단계 3종 `avatar_backdrop_domestic/east_asia/intercontinental`만) |
| "내 3D 아바타"/"내 러너"/"아바타 미리보기" 전신 그림(자리표시 컴포넌트 `Runkk/Placeholder/Character`) | H-1·H-2·H-3 | 피그마도 자리표시(사람 아이콘 `56d95.svg`). 앱에는 성별 기본 전신 그림(`avatar_default_stand.webp` 등)과 도형 임시 몸만 있고 착용 아이템 반영 전신 그림은 없음 |
| 자물쇠 아이콘 16 (`c7a32.svg`) | H-3 잠긴 칸 | `RunkkIcons.Lock` 있음(모양 동일 여부 확인 못 함) |
| IBM Plex Sans KR 글꼴 | 모든 글자 | 없음(Noto Sans KR만) |
