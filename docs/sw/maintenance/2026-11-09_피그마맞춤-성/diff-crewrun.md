# 크루런 화면 피그마 ↔ 앱 차이 (F-14 ~ F-16b)

- 피그마: fileKey `NMTtVMWIT22D0CwOU2y0I9`, 프레임 390×844, Light 값 기준(get_design_context·get_variable_defs로 읽음). Dark 값은 읽지 않았다(확인 못 함).
- 앱: 브랜치 develop `d3b9d4dd`. 경로 약칭
  - `S` = `android/feature/crew/src/main/java/com/a207/runkk/app/ui/crew/CrewRunScreens.kt`
  - `R` = `android/feature/crew/src/main/java/com/a207/runkk/app/ui/crew/CrewRunResultScreen.kt`
  - `Q` = `android/feature/crew/src/main/java/com/a207/runkk/app/ui/crew/CrewQuitDialog.kt`
  - `D` = `android/core/ui/src/main/java/com/a207/runkk/app/core/designsystem/RunResultDetail.kt` (자유런이 아니라 **대결 결과 2쪽과 크루런 F-16b가 함께 쓰는 공용 부품**이라 고치면 대결 결과 2쪽도 바뀐다)
  - `B` = `android/core/ui/src/main/java/com/a207/runkk/app/core/designsystem/RunkkButtons.kt`
  - `N` = `android/data/phone/src/main/kotlin/com/a207/runkk/app/data/device/` (진행 알림)
  - 문자열: `android/feature/crew/src/main/res/values/strings.xml`(이하 `str`), `android/data/phone/src/main/res/values/strings_phone.xml`
- 차이 종류: **누락**(피그마에 있고 앱에 없음), **다름**(둘 다 있으나 값이 다름), **앱에만 있음**.

## 0. 공통: 토큰·글꼴 대응

피그마 변수(Light)와 앱 토큰(`RunkkColors.light`, `RunkkColors.kt:128-163`)의 값이 다르다. 이미 정한 대응표가 설계서에 있다: design-oo 05-21 7.2절(D-496) "background→Background, surface→Surface, divider→Border, textPrimary→TextPrimary, textSecondary→TextSecondary, primary→LimeFill, onPrimary→OnLimeFill, secondary→Violet, onSecondary→White, surfaceHigh→RunimalTile, #D3D9C6→RunimalTileBorder, 덮개 0x73171A12" 그리고 D-497 "SemiBold→Medium, 큰 숫자도 Noto Sans KR Bold(Barlow 쓰지 않음)". 아래 표의 "→ 토큰"은 이 대응을 따른다.

| 피그마 변수 | 피그마 값 | 앱 토큰(라이트 값) | 비고 |
|---|---|---|---|
| runkk-color-background | #F7F8F2 | Background #FAFAFA | 값 다름(D-496에 따라 토큰 유지) |
| runkk-color-surface | #FFFFFF | Surface #FFFFFF | 같음 |
| runkk-color-surfaceHigh | #E9EDE0 | RunimalTile #E9EEDC (D-496) / Border #E4E4E7 (design-oo 05-26 이벤트 화면은 Border로 옮김) | **설계서 두 곳의 대응이 서로 다름 → 사용자 확인 필요** |
| runkk-color-divider | #E9EDE0 | Border #E4E4E7 | 값 다름 |
| runkk-color-textPrimary | #171B12 | TextPrimary #18181B | 근사 |
| runkk-color-textSecondary | #67705B | TextSecondary #52525B | 값 다름 |
| runkk-color-primary / onPrimary | #CAEB2E / #171B12 | LimeFill #CBEA2B / OnLimeFill #171B12 | 근사 |
| runkk-color-secondary / onSecondary | #5C4BDB / #FFFFFF | Violet #5B4FD9 / Color.White | 근사 |
| color/runEnd/icon | #171B12 | OnLimeFill | 같음 |
| 일시정지 버튼 테두리(변수 아님) | #C7CCBF | 대응 토큰 없음. 앱은 BorderStrong #8A8A93 | **토큰 없음** (RunimalTileBorder #D3D9C6이 가장 가깝다) |
| 잠금 화면 카드 테두리(변수 아님) | #D3D9C6 | RunimalTileBorder | 알림은 시스템이 그림(아래 F-15b) |
| 덮개(일시정지·그만두기) | rgba(23,27,18,0.45) | 앱 `Color.Black` 45% (`S:772`, `S:785`), 그만두기는 Dialog 기본 막 | 다름 |
| 글꼴 | IBM Plex Sans KR (Regular 400 / Medium 500 / SemiBold 600 / Bold 700) | Noto Sans KR 400·500·700·900(`RunkkType.kt:26`), 숫자는 Barlow Condensed Italic(`RunkkType.kt:29-32`) | D-497: SemiBold→Medium, Bold→Bold. 앱의 `display()`(Barlow 기울임)·`FontWeight.Black` 사용처는 모두 피그마와 다르다 |
| 공용 버튼 `RunKK / Buttons / 기본` | Large 52 / Medium 48, 모서리 100(완전 알약), 글자 16 SemiBold(Large)·15 SemiBold(Medium), Gray=surfaceHigh 바탕+textSecondary 글자(테두리 없음) | `RunkkPrimaryButton` 56, 모서리 16, 글자 16 Black(`B:53-57`,`B:85`,`RunkkType.kt:102`). `RunkkSecondaryButton` 56, 모서리 16, Surface+BorderStrong 테두리, 15 Bold(`B:153-171`,`B:208-233`) | 다름(높이·모서리·굵기). 공용 부품이라 바꾸면 앱 전체가 바뀐다 → 크루런 화면에서만 맞출지 결정 필요 |
| `RunKK / Run / 러닝 라벨`(칩) | 높이 28, 좌우 10, 모서리 14, 바탕 surfaceHigh, 글자 12 Regular textPrimary 줄높이 14, 글자 "● 크루런 · 5km"(점도 글자) | `RunkkStatusPill`(`RunkkChips.kt:265-290`): 최소 높이 32, 좌우 16, 모서리 16, 바탕 Surface(흰색), 글자 Caption 12 TextSecondary, 앞에 8dp 원(TextPrimary) + 간격 8 | 다름 |
| `RunKK / Run / 헤더 버튼`(나가기) | 높이 36, 너비 65, 좌우 16, 모서리 100, 바탕 surface, 테두리 1 divider, 글자 14 Medium textPrimary | `RunkkPillButton`(`B:239-286`): 높이 40, 모서리 20, 테두리 1 BorderStrong, 글자 Label 13 Regular, 터치 48 | 다름 |
| `RunKK / Buttons / 뒤로가기` | 40×40, 배경 원 없음, arrow_back(화살대 있는 ←) 24, 색 textPrimary | `RunkkBackButton`(`RunkkBars.kt:111-121`): 라이트는 보라 채움 원 + 흰 셰브런(`RunkkIcons.Back` = "<", `RunkkIcons.kt:16`) | 다름 |

---

## 1. F-14 크루런 대기실 (61772:4615)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 화면 여백 | 머리 줄 위 24·좌우 24, 본문 좌우 20·위 3, 하단 단추 영역 위 12·아래 32·좌우 20 | 전체 좌우 16·위 16·아래 32 한 벌(`S:381`) | 다름 |
| 머리 줄 | 왼쪽 칩, 오른쪽 "나가기", 간격 8 | 같은 배치(`S:382-390`) | 같음(부품 모양은 0절 칩·헤더 버튼 차이) |
| 칩 | "● 크루런 · 5km" 0절 규격 | `CrewRunChip`(`S:365-370`), 문구 `str:212` "크루런 · %1$s" + 8dp 점 | 다름(0절) |
| 나가기 | 0절 헤더 버튼 | `RunkkPillButton`(`S:385-389`) | 다름(0절) |
| 본문 세로 간격 | 블록 사이 16 | `spacedBy(20)`(`S:394`) | 다름 |
| 시각 블록 위치·간격 | 위아래 패딩 8, 줄 사이 6 | 위 16 패딩, 줄 사이 2(`S:432`,`S:436`) | 다름 |
| "오늘 오후 9:00" | 12 Regular, 줄높이 18, textPrimary, 가운데 | Caption 12, TextPrimary(`S:438`), 문구 `str:214-215` | 거의 같음(줄높이 미지정) |
| 남은 시간 "12:34" | 48 **Bold IBM Plex**, 줄높이 48, textPrimary | `RunkkTextStyles.display(52)` Barlow Condensed Italic ExtraBold(`S:439`) | 다름(글꼴·크기·기울임) |
| "출발까지" | 13 Regular, 줄높이 16 | Caption 12(`S:441-444`) | 다름(크기) |
| 참가자 머리 줄 | 좌우 패딩 12, "참가자" 15 Bold 줄높이 18, 오른쪽 "준비 완료 3 / 5" 12 Regular, 간격 10 | 좌우 패딩 8(`S:496`), "참가자" CardTitle 16 Black(`S:498-501`), 수 Caption 12(`S:503-507`) | 다름(패딩·크기·굵기) |
| 머리 줄↔격자 간격 | 16(본문 간격) | 12(`S:495`) | 다름 |
| 격자 | 3열(칸 107.33), 칸 사이 가로·세로 14, 행 높이 114.5 | 3열 weight, 칸 사이 가로 간격 0, 행 사이 12(`S:510-515`) | 다름(간격) |
| 참가자 원 | 66×66 원, 바탕 surfaceHigh, **테두리 1 점선 divider**, 안은 비어 있음(자리 표시) | `CrewLogo` 64, 이름 첫 글자, 바탕 MaterialTheme primaryContainer/outlineVariant(나는 강조색)(`S:535`, `CrewComponents.kt:40-57`) | 다름. 피그마는 빈 자리 표시라 **무엇을 넣을지(아바타 얼굴? 프로필?) 확인 못 함** |
| 원↔이름↔상태 간격 | 6 | 4(`S:533`), 칸 위아래 패딩 4(`S:531`) | 다름 |
| 이름 | 13 Regular 줄높이 16, 나는 "나" | Body 14(`S:536`), "나"(`str:143`) | 다름(크기) |
| 상태 "준비 완료" | 12 **Bold** 줄높이 18 | text(13, Bold)(`S:539`) | 다름(크기 13→12) |
| 상태 "대기 중"·"미입장" | 12 Regular 줄높이 18 | Caption 12(`S:539`) | 같음 |
| 나를 첫 칸 | 첫 칸 "나" | `sortedByDescending { it.isMe }`(`S:510`) | 같음 |
| 준비 완료 단추(준비 전) | Large Lime: 높이 52, 모서리 100, 글자 16 SemiBold onPrimary, 전체 너비 | `RunkkPrimaryButton` 56, 모서리 16, 16 Black, 위 패딩 16(`S:481-487`,`S:469`) | 다름(0절) |
| 오류 문구 | 없음 | Lavender Caption(`S:397-404`) | 앱에만 있음(상태 분기, 유지) |
| 대기실 진입 중·오류 화면 | 프레임 없음 | `EnteringBody`(`S:326-347`) | 앱에만 있음 |
| 출발 카운트다운 화면 | 대상 프레임에 없음 | `CountdownBody` "READY"/숫자 160 Barlow + "다 같이 출발해요"(`S:546-563`, `str:205`) | 앱에만 있음(피그마 대상 밖, 확인 못 함) |

## 2. F-14b 대기실 · 준비 상태 (61772:4676)

F-14와 같은 틀. 다른 점만 적는다.

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 내 칸 | "나" + "준비 완료"(Bold) | 같은 로직(`S:523-529`) | 같음 |
| 미입장 칸 | "미입장" 12 Regular | `str:216`, Caption(`S:527`) | 같음 |
| 준비 후 단추 | **Gray Large**: 바탕 surfaceHigh #E9EDE0, 글자 textSecondary #67705B 16 SemiBold, 테두리 없음, 높이 52, 모서리 100, 문구 "준비 완료" | `RunkkSecondaryButton`: 바탕 Surface(흰), 테두리 1 BorderStrong, 글자 TextPrimary 15 Bold, 높이 56, 모서리 16(`S:473-479`) | 다름(색·테두리·크기) |
| 준비 후 단추 동작 | 피그마는 "Gray(비활성)" 스타일로만 보임. 다시 눌러 푸는지 표시 없음 | 다시 누르면 풀림(스위치 역할, `S:469-472`). FR-147 "다시 누르면 풀린다"와 같음 | 동작은 SRS를 따른다. 회색은 모양만 맞추고 눌림은 유지할 것 |
| 프레임 이름 "알림 설정" | 이름에만 있고 화면에 알림 설정 요소 없음 | 없음 | 같음(아래 요구사항 충돌 5 참고) |

## 3. F-14c 크루런 취소 · 준비 2명 미만 (61772:4737)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 틀 | 대기실 틀 그대로(칩·나가기·시각·참가자 격자) | 대기실이 있으면 `LobbyContent` 재사용(`S:315-318`, `S:380`) | 같음 |
| 남은 시간 | "0:00" | `untilStart = Duration.ZERO`(`CrewRunViewModel.kt:223`) → `RunkkFormat.duration` "%02d:%02d" → **"00:00"**(`RunkkFormat.kt:38-48`) | 다름(앞자리 0) |
| 안내 문구 | "준비 완료한 참가자가 2명보다 적어 시작하지 않았어요" 13 Regular, 가운데 | 서버 사유 그대로(`S:422-424`, 서버 `CrewRunAnnouncer.java:30` "…%d명보다 적어 시작하지 않았어요"), Caption 12(`S:441-444`) | 문구는 같음(인원은 운영 기준), 크기 다름 13↔12 |
| 수 | "준비 완료 1 / 5" | 같은 로직 | 같음 |
| 하단 단추 | Lime Large "일정으로 돌아가기" | `RunkkPrimaryButton`(`S:406-411`, `str:192`) | 문구 같음, 모양 0절 차이 |
| 나가기 | 그대로 "나가기" | 취소면 일정으로 돌아감(`S:387`) | 같음 |
| 대기실 없이 받은 취소 | 프레임 없음 | `CancelledBody`: 상단 바 "크루런 대기실" + 경고 아이콘 + 사유 + 알약 "일정으로 돌아가기"(`S:351-361`) | 앱에만 있음 |

## 4. F-15 크루런 진행 (61772:4799)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 화면 여백 | 위 20, 좌우 24, 아래 36, 블록 간격 16 | 위 16, 좌우 16, 아래 32, 간격 12(`S:573-574`) | 다름 |
| 칩 영역 | 칩이 높이 64 칸 맨 위(칩 아래 36 빈 공간) | 칩 바로 아래 거리(간격 12)(`S:576`) | 다름 |
| 큰 거리 "3.22" | 96 **Bold IBM Plex**, 줄높이 72, 가운데 | `DisplayHuge` 136 Barlow Italic ExtraBold(`S:615`, `RunkkType.kt:105`) | 다름(크기·글꼴) |
| "km" | 20 Regular **textPrimary**, 숫자 오른쪽 아래(줄높이 24) | text(18) **TextSecondary**, 왼쪽 4·아래 12 패딩(`S:616-621`) | 다름(크기·색) |
| 거리 블록 높이 | 82(위 패딩 8) | 내용 높이 | 다름 |
| 세 수치 | 3등분(flex 1), 값 26 Bold 줄높이 31, 값↔라벨 2 | `SpaceEvenly`(`S:628`), 값 `display(28)` Barlow(`S:653`) | 다름(배치·글꼴·크기) |
| 수치 라벨 | 12 Regular **textPrimary**, 줄높이 14 | Caption 12 **TextSecondary**(`S:654`) | 다름(색) |
| 수치 문구 | "경과"·"현재 페이스"·"평균 페이스" | `str:217-219` | 같음 |
| 지도 영역 | 351×349.2, 좌 19.5(본문보다 바깥으로 4.5 넘침), **모서리 없음·테두리 없음, 사방 24px 알파 페이드(마스크)** | `weight(1f)` 가변 높이, 위 패딩 24(`S:579`), Kakao 지도 모서리 20 + 테두리 1 Border(`KakaoRunMap.kt:192`,`:241`) | 다름(크기·모서리·페이드 없음) |
| 지도 위 내 아바타 | "[내 아바타]" 자리 표시 50.4×57.6, 모서리 12.6, 이름표 없음 | `RunkkRunningFigure` 72 + 아래 이름표 "나"(11 Bold, Surface 알약)(`S:698`,`S:710-712`, `KakaoRunMap.kt:75`,`:212-225`) | 다름(크기·이름표). 아바타 그림 종류는 자리 표시라 확인 못 함 |
| 지도 위 크루원 | "[아바타]" 39.6×45 넷, 이름표 없음 | 달리는 그림(성별 캐릭터) 72 + 이름표(`S:674-688`) | 다름(크기 내 > 크루원 구분 없음, 이름표) |
| 경로선 | 짙은 회녹색 선(에셋 25794.svg) | Kakao 지도/RouteCanvas 경로선 색(확인 못 함) | 확인 못 함 |
| "Me - 내 위치" 점 | 경로 시작점 쪽 18px 흰 원(에셋 4a149.svg) | 없음 | 누락(의미가 "시작점"인지 "내 위치"인지 확인 못 함) |
| "함께 달리는 크루원 4명" | 14 Regular 줄높이 17, 위 패딩 40(블록 높이 75) | Body 14, 패딩 없음(`S:583-589`, `str:221`) | 다름(위 여백) |
| 하단 단추 배치 | 가운데 정렬, 두 단추 사이 **간격 56** | `SpaceEvenly`(`S:719-722`) | 다름 |
| 종료 단추 | 84 원, 바탕 primary(라임), 정지 사각 28 모서리 **7**, 색 #171B12 | `RunkkLongPressButton` 84, LimeFill, 사각 28 모서리 **8** OnLimeFill(`S:724-738`) | 다름(모서리 7↔8) |
| "길게 눌러 종료" | **14** Regular textSecondary, 단추 아래 **14** 간격 | Caption **12** TextSecondary, 간격 **8**(`B:430`,`B:436-443`) | 다름 |
| 일시정지 단추 | 84 원, 바탕 surface, 테두리 **2.33 #C7CCBF**, 아이콘: 28×28 안에 막대 2개(각 6.4×28, 모서리 1.4, 간격 5.25, textPrimary) | 84 원, Surface, 테두리 **2 BorderStrong(#8A8A93)**(`S:745`), 아이콘 `RunkkIcons.Pause`(24 격자에서 3×14 막대) 28dp(`S:755-760`, `RunkkIcons.kt:152`) → 막대가 피그마보다 가늘고 짧다 | 다름 |
| 탭 안내 힌트 | 없음 | 짧게 누르면 `RunkkHint` "길게 눌러 종료"(Lavender 13 Bold)가 지도 아래에 뜸(`S:581`) | 앱에만 있음(상태 분기, 유지 여부 결정) |
| 오류 문구 | 없음 | Lavender Caption(`S:590-597`) | 앱에만 있음 |

## 5. F-15b 크루런 잠금 화면 · Live Updates (62048:7061)

알림은 Android 시스템이 그린다. 앱 코드는 `N/ProgressNoticeText.kt:102-110`(크루런 문구), `N/ProgressNotificationStyle.kt:210-244`(공통 바탕), `N/LiveUpdateStyle.kt:15-28`(Android 16 승격), `N/OngoingProgressStyle.kt`(15 이하).

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 상태 표시줄 칩 | 흰 알약 "● 2.84 km" 11 Bold | `setShortCriticalText("2.84 km")`(`LiveUpdateStyle.kt:26`, 문구 `strings_phone.xml:10`). 점·모양은 시스템이 그림 | 같음(시스템 렌더) |
| 카드 앱 아이콘 | 22px 앱 아이콘(에셋 80227.png) | small icon = `android.R.drawable.ic_menu_mylocation`(시스템 위치 아이콘)(`ProgressNotificationStyle.kt:221`). 폰 앱 모듈에 알림용 단색 아이콘(ic_stat_*) 없음(워치에만 `wear/.../ic_stat_run.xml`) | 다름(아이콘 누락) |
| 첫 줄 | "크루런 · 15:40 경과" 12 Regular textSecondary | 제목 "크루런 · %1$s 경과"(`strings_phone.xml:8`), 경과 "mm:ss"(`ProgressNoticeText.kt:154-164`) | 문구 같음. 크기·색은 시스템 템플릿(제목이 굵게 크게 보임) |
| 거리 | "2.84"(34 Bold) + "km"(16 Bold) 큰 글자 | 본문 "2.84 km"(contentText) 일반 크기 | 다름. **플랫폼 제약**: 승격 알림은 사용자 지정 뷰를 쓸 수 없어(`LiveUpdateStyle.kt:12-13`) 큰 숫자 배치를 그대로 만들 수 없다 |
| 일시정지 단추 | 오른쪽 72 원, 흰 바탕, 테두리 2 #C7CCBF, 막대 아이콘 24 | 알림 동작 버튼 "일시정지"/"재개" 글자(`ProgressNotificationStyle.kt:229-242`, `strings_phone.xml:17-18`) | 다름. **플랫폼 제약**(시스템 동작 버튼 모양) |
| 진행 막대 | 없음 | 크루런은 progressMax=null이라 막대 없음(`ProgressNoticeText.kt:108-109`) | 같음 |
| 카드 모양 | 흰 카드 358×119, 모서리 24, 테두리 #D3D9C6 | 시스템 | 플랫폼 제약 |
| 알림 누르면 | 피그마 없음 | 앱 실행 인텐트만 연다. 대결은 battleId를 싣지만 크루런 postId는 싣지 않는다(`ProgressNotificationStyle.kt:250-251`) | 피그마 범위 밖(참고) |
| 잠금 아이콘·시계·손전등·카메라·"위로 밀어 잠금 해제"·[다른 알림] | 시스템 잠금 화면 요소 | 해당 없음 | 대상 아님 |

## 6. F-15c 크루런 일시정지 (61772:4871)

F-15 화면 위에 덮개. 다른 점만 적는다.

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 덮개 | 화면 전체 rgba(23,27,18,0.45) | `Color.Black` 45%(`S:772`,`S:785`) | 다름(색: 검정 ↔ #171B12) |
| 덮개 위 단추 자리 | 원래 단추 자리(top 686.5)에 그대로 다시 그림, 아래 원 단추는 opacity 0 | 같은 방식: 원래 단추 투명 + 덮개 위 하단 정렬(아래 32)(`S:599-600`,`S:767-779`) | 같음(정렬은 F-15 차이를 따름) |
| 종료 단추 아래 문구 | "길게 눌러 종료" **흰색(surface)** 14 | TextSecondary Caption 12(`B:437-442`) — 덮개 위에서 회색 | 다름(색·크기) |
| 재개 단추 | 84 흰 원, **테두리 없음**, 재생 아이콘(에셋 4d6d0.svg) 28 | 84 Surface, 테두리 Surface색(보이지 않음)(`S:745`), `RunkkIcons.Play` 28(`S:756`, `RunkkIcons.kt:155`) | 아이콘 모양 확인 필요(에셋 대비), 나머지 같음 |
| 지도 아바타 이름 | 내 아바타·크루원 아바타 **모두** "[… · 멈춤]" | 내 이름표만 "나 · 멈춤"(`S:672`). 크루원은 그 사람 상태가 PAUSED일 때만 "· 멈춤"(`S:677-680`), 움직임은 내가 멈추면 같이 멈춤(`S:681`) | 다름. 피그마 의도가 "내가 멈추면 모두 멈춤 표시"인지 확인 못 함 |
| 내 아바타 자리 표시 크기 | 57.6×57.6(F-15는 50.4×57.6) | 72 | 다름 |

## 7. F-15d 크루런 그만두기 (61772:4956)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 덮개 | rgba(23,27,18,0.45) | `Dialog` 기본 덮개(`Q:39`) — 색·불투명도 확인 못 함(플랫폼 기본) | 다름 가능 |
| 대화 상자 | 너비 334(좌우 28), **모서리 28**, 위 24·아래 20·좌우 20, 줄 간격 10 | 좌우 28, **모서리 24**, 같은 패딩, 간격 10(`Q:42-48`) | 다름(모서리) |
| 제목 | "크루런을 그만둘까요?" **18 Bold** 줄높이 26 | CardTitle **16 Black**(`Q:50-55`, `str:187`) | 다름(크기·굵기) |
| 본문 | "지금까지 달린 기록은 저장돼요." 14 Regular 줄높이 21 textPrimary | Body 14(`Q:56-60`, `str:188`) | 같음(줄높이 미지정) |
| 단추 줄 | 위 10, 간격 10 | 같음(`Q:61`) | 같음 |
| "계속 달리기" | **고정 너비 143**, 높이 48, 모서리 100, 바탕 surface, 테두리 1 **divider**, 15 SemiBold textPrimary | weight 1(반반), 48, 모서리 24, 테두리 1 **BorderStrong**, 15 **Bold**(`Q:62-69`,`Q:93-103`) | 다름(너비 비율·테두리색·굵기) |
| "그만두기" | 나머지 너비(약 171), 48, Purple #5C4BDB, 15 SemiBold 흰색 | weight 1, Violet, 흰 15 Bold(`Q:70-77`) | 다름(너비 비율·굵기) |
| 뒤 화면 | 진행 화면 그대로(일시정지 단추 보임) | 같음 | 같음 |

## 8. F-16 크루런 결과 (61772:5051)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 배경 | 화면 전체(머리 포함) 풍경 그림(에셋 b8bbe.png) + scrim 변수(투명) | `AvatarBackdrop`(기본 `avatar_backdrop_default.webp` 또는 내 배경 키), 상세 쪽으로 넘기면 흐려짐(`R:118-121`, `AvatarBackdrop.kt:43-48`) | 같은 그림인지 확인 못 함(눈으로 비교 필요) |
| 머리 줄 | 높이 57, 좌우 12, 왼쪽 뒤로 **화살표(원 없음)**, 제목 "크루런 결과" **19 Bold 가운데** | `RunkkTopBar`: 높이 64, 좌우 16, 보라 원 뒤로 버튼, 제목 18 Black **왼쪽 정렬**(`R:124`, `RunkkBars.kt:77-107`) | 다름 |
| 본문 여백 | 좌우 20, 위 5, 위 묶음 위 10, 묶음 안 간격 16, 묶음↔카드 24 | 좌우 20, 간격 12 한 벌(`R:199-201`) | 다름 |
| 모집글 제목 | "[오늘 저녁 한강 5km]" 13 Regular 줄높이 16 | Body 14(`R:203-210`) | 다름(크기) |
| 아바타 줄 | 크루원 50×62 ×4, 나 104×140, 간격 8, 나 가운데 | 같은 크기·간격·배치(`R:229-263`) | 같음 |
| 합산 거리 "23.28km" | 44 **Bold IBM Plex** 줄높이 44 | `display(44)` Barlow Italic ExtraBold(`R:214-219`) | 다름(글꼴) |
| 참가자 카드 | 바탕 surface, **테두리 1 divider**, **모서리 26**, 좌우 19·위아래 5 | 테두리 없음, 모서리 24, 좌우 20·위아래 8(`R:268-274`) | 다름 |
| 카드 줄 | 위아래 14, 최소 높이 49, 간격 12, 줄 사이 **점선** 1 divider | 위아래 12, 실선 `HorizontalDivider(Border)`(`R:276`,`R:286`) | 다름(점선·간격) |
| 줄 원 | 36 원, 바탕 surfaceHigh, 비어 있음(자리 표시) | `CrewLogo` 36 첫 글자, 나는 강조색(`R:291`) | 다름(무엇을 넣을지 확인 못 함) |
| 이름 | 15 Bold 줄높이 18, 나는 "나" | text(15, Bold)(`R:292-297`) | 같음 |
| 거리 | **17 Bold** 줄높이 20 | text(**16**, Bold)(`R:298-302`) | 다름(크기) |
| 쪽 넘김 글자 | "밀어서 상세 결과 보기 ›" **11** Regular textPrimary, 점 위 약 10 | Caption **12**, 클릭 가능(`R:181-193`, `str:228`) | 다름(크기) |
| 쪽 점 | 켜진 점 18×6 textPrimary, 꺼진 점 6×6 **surfaceHigh**, 간격 6 | 간격 6, 꺼진 점 **BorderStrong**(`R:348-357`) | 다름(꺼진 점 색) |
| 하단 단추 영역 | 위 12, 아래 32, 좌우 20, 간격 10 | 위 8, 아래 24, 좌우 20, 간격 10(`R:137-140`) | 다름 |
| "공유" | 높이 **54**, 모서리 27, 바탕 surface, **테두리 없음**, 16 **Medium** onPrimary, flex(163) | `RunkkSecondaryButton` 56, 모서리 16, BorderStrong 테두리, 15 Bold, weight 1(`R:141-145`) | 다름 |
| "확인" | 높이 52, 모서리 100, Lime, **테두리 1 divider**, 16 SemiBold, 고정 너비 169 | `RunkkPrimaryButton` 56, 모서리 16, 16 Black, weight 1(`R:146-150`) | 다름 |
| 첫 크루런 표시·크루 주간 거리 반영 | 없음 | 없음(도메인에는 `isFirstCrewRun`·`raceContribution` 있음, `CrewRunResult.kt`) | 요구사항 충돌 4 참고 |
| 로딩·오류 | 프레임 없음 | `RunkkLoading`/`RunkkErrorState`(`R:127-132`) | 앱에만 있음 |

## 9. F-16b 크루런 결과 상세 · 스와이프 (61772:5131)

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 배경 | 단색 background, 그림 없음 | 결과 쪽 그림이 넘김 비율만큼 사라지고 상세 쪽 바탕 Background(`R:120`,`R:314`) | 같음 |
| 머리 줄 | 높이 57, **뒤로 버튼 없음**, 제목 "크루런 결과" 19 Bold 가운데 | 두 쪽이 같은 `RunkkTopBar`라 상세 쪽에도 보라 원 뒤로 버튼 + 왼쪽 제목(`R:124`) | 다름(뒤로 버튼 앱에만 있음, 제목 정렬) |
| 본문 여백 | 좌우 20, 위 11, 블록 간격 14 | 좌우 20, 위아래 12(`R:316`), 블록 간격 16(`D:92`) | 다름 |
| 큰 거리 | "4.87" **64 Bold** 자간 -0.5 + " km" **22 Bold**, 아래 정렬 | `DisplayLarge` 104 Barlow + "km" 20 Bold, 아래 패딩 10(`D:110-120`, `RunkkType.kt:108`) | 다름 |
| 아바타 | 오른쪽 110×110 "기뻐하는 내 아바타" | `CheeringAvatar` 110 뛰기 반복(`D:124-125`,`D:143-168`) | 같음(움직임은 앱 추가) |
| 날짜 줄 | "[MM.DD 요일] · [오전 h:mm] · 크루런" 12 Regular **textPrimary** | "M.dd E · a h:mm · 크루런"(`R:340-344`, `str:231-232`), Caption **TextSecondary**(`D:122`) | 다름(색; 월 두 자리 여부는 자리 표시라 확인 못 함) |
| 통계 세 칸 | 칸 사이 8, 바탕 surface, **테두리 1 divider**, **모서리 24**, 좌우 12·위아래 14, 줄 간격 **4** | 테두리 없음, 모서리 **16**, 같은 패딩, 간격 **6**(`D:206-227`) | 다름 |
| 칸 라벨 | **11** Regular **textPrimary** 줄높이 13 | Caption **12 TextSecondary**(`D:223`) | 다름 |
| 칸 값 | 18 **Bold** 줄높이 22 | 18 **Black**(`D:224`) | 다름(굵기) |
| 평균 심박 값 | "[bpm]" 자리 표시 | 숫자만 또는 "–"(`D:192-196`) | 단위 "bpm" 붙일지 확인 못 함 |
| 구간·심박 카드 | 바탕 surface, **테두리 1 divider**, **모서리 26**, 좌우 18·위아래 14, 줄 간격 **7** | 테두리 없음, 모서리 **20**, 패딩 **20**, 간격 **10**(`D:231-253`) | 다름 |
| "구간 페이스" 제목 | **14 Bold** 줄높이 21 | CardTitle **16 Black**(`D:241-245`) | 다름 |
| 구간 줄 | 열 34 · 220 · 44, 간격 8, 줄 높이 18. "1km" 12 Regular, 막대 높이 **9** 모서리 999, 값 "6'20"" **12 Bold** 오른쪽 정렬 | 라벨 Label **13** 너비 **44**, 막대 높이 **8**, 값 **14 Black** 왼쪽 패딩 12(`D:256-287`) | 다름 |
| 구간 막대 색 | 바탕·채움 모두 surfaceHigh(#E9EDE0)로 그려져 있음 | 바탕 Border, 채움 **Lime**(라이트 #3F6212)(`D:270`,`D:277`) | 피그마 채움색이 바탕과 같아 **의도 확인 못 함**(자리 표시로 보임) |
| "심박 구간" 줄 | 제목 14 Bold, "워치" 알약 높이 24·좌우 10·**테두리 1 divider**·바탕 없음·11 Regular, 줄 위 패딩 4 | 제목 CardTitle 16 Black, "워치" Caption 12 바탕 Background 테두리 없음, 위 패딩 6(`D:296-312`) | 다름 |
| 심박 막대 | 높이 **16**, 모서리 999, 조각 간격 2, 첫 조각 투명·나머지 surfaceHigh | `RunkkSegmentBar` 기본 높이 **12**, 간격 2, 구간 색 5단(`D:315-321`,`D:358-364`, `RunkkCards.kt:260-266`) | 다름(높이; 색은 피그마가 자리 표시라 확인 못 함) |
| 하이라이트 묶음 | 좌우 4, 줄 간격 6 | 패딩 없음, 간격 10(`D:328`) | 다름 |
| ★ 신기록 줄 | 원 26 바탕 surfaceHigh 안에 "★" 글자 12, 간격 8, 문구 "[n] km 신기록" 13 Regular | 원 28 바탕 **Surface** + Star 아이콘 14, 간격 10, Body 14, 문구는 서버 newRecords 그대로(`D:329`,`D:336-346`) | 다름 |
| + 리워드 줄 | 원 배경 없음, "+" 아이콘 24(에셋 03664.svg), "리워드 +[n]" 13 | 원 28 Surface + Plus 14, "리워드 +%1$d"(`D:331`, `core/ui/.../strings.xml:162`) | 다름 |
| 쪽 넘김 글자 | "‹ 크루런 결과로" 11 | Caption 12(`str:229`) | 다름 |
| 쪽 점 | 꺼진 점 surfaceHigh, 켜진 점 둘째 | 꺼진 점 BorderStrong(`R:355`) | 다름 |
| 하단 단추 | "공유" **고정 171**, 52, 모서리 100, surface, 테두리 없음, 16 SemiBold / "확인" flex, Lime, 테두리 1 divider | F-16과 같은 단추(`R:137-151`) | 다름(F-16 차이 + F-16과 F-16b의 공유 단추 높이 54↔52, 너비 고정 위치가 서로 다름) |
| 기록 대기 문구 | 프레임 없음 | "상세 기록을 불러오고 있어요"(`R:327-333`, `str:230`) | 앱에만 있음 |

---

## 10. 요구사항(SRS·설계서)과 피그마 충돌

1. **FR-147 "위치 대신 달린 거리만 공유된다는 안내를 보인다"** — 피그마 F-14·F-14b·F-14c에 이 안내가 없다. 앱에도 없다. → 사용자 확인 필요.
2. **FR-147 "준비 완료를 누르면 '시작을 기다리는 중'이 되고"** — 피그마 F-14b는 회색 "준비 완료" 단추와 내 칸 "준비 완료"만 보이고 "시작을 기다리는 중" 문구가 없다. 앱은 피그마와 같다(스위치 상태 설명만 `str:203`). → SRS 문구를 고칠지 확인 필요.
3. **FR-148 취소 안내 n = 운영 기준** — 피그마는 "2명"으로 고정. 앱은 서버 문구(`CrewRunAnnouncer.CANCELLED_REASON`, %d)를 그대로 보이므로 충돌 없음.
4. **FR-191 결과 화면에 "첫 크루런 표시, 크루 주간 거리에 반영된 거리 합(FR-139)"** — 피그마 F-16에 둘 다 없다. 앱 화면에도 없다(도메인 `CrewRunResult.isFirstCrewRun`·`raceContribution`은 있다). → 피그마를 따를지 SRS를 따를지 확인 필요.
5. **F-14b 프레임 이름 "준비 상태·알림 설정"** — FR-147(v0.29 CR-164)은 "순위 알림 설정은 두지 않는다". 피그마 화면에도 설정 요소가 없어 프레임 이름만 옛 것이다. 구현 영향 없음.
6. **FR-300 잠금 화면 진행 알림 "일시정지·재개 단추"** — 피그마는 둥근 아이콘 단추, 앱은 시스템 글자 동작 버튼. 승격 조건(사용자 지정 뷰 금지, `LiveUpdateStyle.kt:12-13`) 때문에 모양을 똑같이 만들 수 없다.
7. **FR-149 "연결 확인 중" 상태** — 피그마 F-15에 표현이 없다. 앱은 CHECKING을 달리는 중처럼 그린다(`S:677-681`). 피그마에 없어 충돌은 아니나 표시 방식 미정.
8. **토큰 대응 불일치** — surfaceHigh를 design-oo 05-21(D-496)은 `RunimalTile`로, 05-26(이벤트)은 `Border`로 옮겼다. 크루런에 어느 쪽을 쓸지 확인 필요.
9. **D-497(큰 숫자는 Noto Sans KR Bold, Barlow 쓰지 않음)** — 현재 크루런 화면은 Barlow `display()`를 쓴다(`S:439`,`S:615`,`S:653`,`R:216`,`D:112`). 피그마·D-497 쪽으로 바꾸면 된다(충돌 아님, 미반영).

## 11. 기능·데이터가 필요한 차이 (서버 API 또는 UiState 추가)

| 항목 | 화면 | 필요한 것 |
|---|---|---|
| 대기실 참가자 원에 아바타 얼굴을 넣는다면 | F-14·F-14b·F-14c | `Lobby.Participant.faceKey`·`shownGender`는 이미 있다. 셀카 얼굴이면 부위 그림이 필요해 `CrewRunUiState`에 `mateFaces: Map<String, List<FaceLayer>>`(결과 화면 `CrewRunResultUiState.mateFaces`와 같은 방식)와 VM 읽기가 필요. **피그마는 빈 자리 표시라 무엇을 넣을지 먼저 확인** |
| 결과 참가자 줄 원(36)에 아바타 얼굴을 넣는다면 | F-16 | 데이터는 이미 있다(`CrewRunResult.Participant.faceKey`, `state.mateFaces`). 화면만 고치면 됨. 역시 자리 표시라 확인 필요 |
| 일시정지 때 모든 아바타 "· 멈춤" | F-15c | 의도가 "내가 멈추면 모두 멈춤 표시"면 화면 로직만, "크루원 각자 상태"면 지금 그대로. 확인 필요(서버 변경 없음) |
| 알림 앱 아이콘 | F-15b | 폰 앱용 단색 알림 아이콘 drawable 추가 + `setSmallIcon` 교체(`ProgressNotificationStyle.kt:221`). 서버 변경 없음 |
| 첫 크루런·크루 주간 반영 표시 (SRS만 요구) | F-16 | 도메인 값은 있음. 피그마에 없어 넣을지 결정 필요(충돌 4) |
| 거리 공유 안내 (SRS만 요구) | F-14 | 문구 리소스 추가만. 피그마에 없어 결정 필요(충돌 1) |

서버 API 추가가 꼭 필요한 차이는 찾지 못했다.

## 12. 화면만 고치면 되는 차이 (요약)

- **공통**: 칩(28 높이·surfaceHigh·글자 textPrimary), 헤더 버튼 "나가기"(36·divider 테두리·14 Medium), 기본 버튼(52/48 높이·완전 알약·SemiBold→Medium), Gray 버튼(surfaceHigh 바탕·textSecondary), 뒤로 버튼(원 없는 ← 화살표), 덮개 색 #171B12 45%, 큰 숫자 Barlow → Noto Sans KR Bold(D-497), `FontWeight.Black` → Bold.
- **F-14/14b/14c**: 여백(24/20/32), 간격 16, 남은 시간 48 Bold, "출발까지" 13, 참가자 제목 15 Bold·패딩 12, 격자 간격 14, 원 66(점선 테두리 자리 표시), 이름 13·상태 12, 취소 "0:00"(앞자리 0 없는 형식).
- **F-15**: 여백 20/24/36, 칩 칸 64, 거리 96 Bold + km 20 textPrimary, 수치 3등분·26 Bold·라벨 textPrimary, 지도 351×349 사방 24px 페이드·모서리 없음·테두리 없음, 아바타 크기(내 50×58, 크루원 40×45)·이름표 없음, "함께 달리는…" 위 40, 단추 간격 56 가운데, 정지 사각 모서리 7, 캡션 14·간격 14, 일시정지 테두리 #C7CCBF 2.33·굵은 막대 아이콘.
- **F-15c**: 덮개 색, 캡션 흰색, 재생 아이콘 에셋 대조.
- **F-15d**: 모서리 28, 제목 18 Bold, "계속 달리기" 143 고정·divider 테두리, 글자 SemiBold(→Medium), 덮개.
- **F-16**: 머리 줄(57, 원 없는 화살표, 19 Bold 가운데), 간격 10/16/24, 제목 13, 합산 44 Bold, 카드 테두리·모서리 26·점선 구분선·줄 높이 49, 거리 17, 쪽 글자 11, 꺼진 점 surfaceHigh, 단추(공유 54 테두리 없음 Medium / 확인 52 divider 테두리), 하단 여백 12/32.
- **F-16b**: 상세 쪽 머리 줄 뒤로 버튼 없음(두 쪽이 한 상단 바를 쓰므로 쪽마다 다르게 그려야 함), 큰 거리 64 Bold + km 22, 날짜 줄 textPrimary, 통계 칸·구간 카드 테두리·모서리(24/26)·간격, 글자 크기(11/12/14), 막대 높이 9·16, "워치" 알약 테두리, 하이라이트 원 26·간격 8·글자 13, "+" 아이콘 원 없음. **`RunResultDetail`은 대결 결과 2쪽과 공용**이므로 크루런 전용 변형을 둘지(파라미터·별도 부품) 결정이 필요하다.
- **F-15b**: 알림 아이콘 외에는 시스템 렌더라 화면으로 맞출 수 있는 부분이 거의 없다(플랫폼 제약).

## 13. 앱에 없는 피그마 에셋

| 에셋(피그마 MCP 파일명) | 쓰는 곳 | 앱 상태 |
|---|---|---|
| arrow_back 아이콘 `35749.svg` (24, 원 없음) | F-16 머리 줄 뒤로 | 없음. `RunkkIcons`에는 셰브런 `Back`(`RunkkIcons.kt:16`)과 `ArrowRight`(`:22`)만 있다 |
| 재생(재개) 아이콘 `4d6d0.svg` (28) | F-15c 재개 단추 | `RunkkIcons.Play`(`:155`)가 있으나 같은 모양인지 확인 못 함 |
| 일시정지 아이콘(막대 2개, 컴포넌트 `RunKK / Icon / 일시정지` 62076:7026) | F-15·F-15b | `RunkkIcons.Pause`(`:152`)는 막대가 더 가늘고 짧다. 컴포넌트 비율(24 기준 막대 5.5×24, 모서리 1.2)로 새로 그려야 같다 |
| "+" 리워드 아이콘 `03664.svg` | F-16b | `RunkkIcons.Plus`(`:135`) 있음. 같은 모양인지 확인 못 함 |
| 지도 가장자리 알파 페이드 마스크 `df22d.svg` | F-15·F-15c·F-15d | 없음(효과로 구현하면 에셋 불필요) |
| 경로선 `25794.svg`, 내 위치 점 `4a149.svg` | F-15 지도 | 경로선은 앱이 실제 경로로 그림. 시작/내 위치 흰 점은 없음 |
| 지도 그림 `86478.png` | F-15 지도 | 자리 표시용 정지 지도 그림. 앱은 Kakao 지도라 넣지 않는다 |
| 결과 풍경 배경 `b8bbe.png` | F-16 | `avatar_backdrop_default.webp`가 있음. 같은 그림인지 확인 못 함 |
| 앱 아이콘 `80227.png` (22) | F-15b 알림 | 알림용 단색 아이콘 없음(`setSmallIcon(android.R.drawable.ic_menu_mylocation)`). 런처 로고 `app/src/main/res/drawable/ic_launcher_logo.webp`만 있음 |
| 손전등·카메라·잠금 아이콘 | F-15b 잠금 화면 | 시스템 요소라 대상 아님 |
