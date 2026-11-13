# 크루 채팅: 피그마와 앱 비교 (F-09, F-09b, F-09c, F-09d, F-10, F-10b)

- 피그마 fileKey `NMTtVMWIT22D0CwOU2y0I9`. 기본 화면 F-09·F-10은 get_design_context로, 변형은 스크린샷과 팝업 하위 노드(61770:5243, 61770:5521, 61770:5825, 61770:5382)의 get_design_context로 확인했다.
- 앱 코드는 develop 기준이다. 채팅 UI는 `android/feature/chat/src/main/java/com/a207/runkk/app/ui/chat/`에 있다(아래 표에서는 줄여서 `chat/파일:줄`로 적는다). `feature/crew/.../CrewChatEntryScreen.kt`는 알림으로 들어올 때 내 크루를 확인한 뒤 `Routes.CrewChat`로 넘기는 경유 화면일 뿐이다.
- 공용 컴포넌트와 토큰은 `android/core/ui/src/main/java/com/a207/runkk/app/core/designsystem/`에 있다(줄여서 `ds/파일:줄`).
- **F-10 "채팅 E10"이 어떤 상태인지:** F-09와 같은 대화 화면에서 **남의 메시지를 길게 눌러 연 메시지 메뉴**다. 메뉴 아래 항목이 "삭제"가 아니라 "신고"이고, 메뉴는 남의 말풍선 왼쪽 끝(x=40)에 붙어 있다. F-09는 **내 메시지**를 길게 누른 메뉴(아래 항목 "삭제")다. "E10"이라는 코드의 뜻은 docs에서 찾지 못했다(확인 못 함).

## 0. 공통 토큰 대응표(피그마 → 앱 라이트 벌)

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

## 화면별 비교

### F-09 크루 채팅 (61770:4983)

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

### F-09b 메시지 삭제 확인 (61770:5124)

F-09와 같은 화면 위에 가운데 대화상자를 띄운다. 달라진 부분만 적는다.

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 팝업 형태 | 가운데 **다이얼로그**. 스크림 rgba(23,27,18,0.45), 양옆 28 | `RunkkConfirmDialog` = M3 `AlertDialog`(`chat/ChatRoomScreen.kt:192-201`, `ds/RunkkFields.kt:364-396`) | 다름(구현 방식) |
| 카드 | 폭 334, 배경 surface, radius **28**, pt24 px20 pb20, 세로 gap 10 | AlertDialog 기본(radius 28, padding 24) | 다름(여백) |
| 제목 | "메시지를 삭제할까요?" 18sp Bold, 줄높이 26, textPrimary | "메시지를 지울까요?" CardTitle 16sp Black(chat_delete_title) | 다름(문구, 크기, 굵기) |
| 본문 | "삭제한 메시지는 되돌릴 수 없어요." 14sp Regular, 줄높이 21, **textPrimary** | "지운 메시지는 대화에 \"삭제된 메시지\"로 남아요." Body 14sp, TextSecondary(chat_delete_message) | 다름(문구, 색) |
| 버튼 줄 | pt10, gap 10, 너비를 채움. 왼쪽 "취소": Outline 버튼(배경 surface, 테두리 1 divider #E9EDE0, h48, radius 100, 15sp SemiBold textPrimary, 폭 143). 오른쪽 "삭제": Purple 버튼(배경 secondary #5C4BDB, h48, radius 100, 15sp SemiBold onSecondary #FFFFFF, 남은 폭) | 오른쪽 정렬 TextButton 두 개: "취소" BodyStrong TextSecondary, "삭제" BodyStrong Lavender 글자(`ds/RunkkFields.kt:381-394`) | 다름(버튼 모양, 색, 배치) |
| Purple 알약 버튼 컴포넌트 | "RunKK / Buttons / 기본" Style=Purple(61857:7657), Medium48 / Large52 | 앱에 보라 채움 알약 버튼이 없다(`ds/RunkkButtons.kt`에는 Lime 주 버튼, Secondary 외곽선, AccentOutlined만 있다) | 누락(공용 컴포넌트) |

### F-09c 삭제된 메시지 (61770:5275)

F-09에서 마지막 내 메시지 "넵 확인할게요!"가 삭제된 상태이고 메뉴는 닫혀 있다.

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 문구 | "삭제된 메시지입니다" | "삭제된 메시지"(chat_removed, `chat/ChatMessageRow.kt:348`) | 다름(문구) |
| 말풍선 모양 | 보통 내 말풍선과 똑같다(61770:5382): 배경 #FFFFFF, 테두리 없음, radius 17, px12 py9, 13sp **Regular(기울임 아님)**, textPrimary | 배경 surface에 테두리 1 outline, **기울임**, onSurfaceVariant 글자(`chat/ChatMessageRow.kt:383-395`) | 다름(기울임, 글자색, 테두리) |
| 시각 | 그대로 보임("오후 8:05" 10sp textSecondary, 말풍선 왼쪽) | 보임 | 같음(크기만 F-09와 같이 다름) |
| 반응, 메뉴 | 반응 줄 없음 | 반응 줄 없음, 메뉴 없음(`chat/ChatMessageRow.kt:351, 466`) | 같음 |
| 남의 메시지가 삭제된 경우 | 피그마에 없음(surfaceHigh 말풍선일 것으로 보이나 확인 못 함) | 남의 말풍선과 같은 surface 배경 | 확인 못 함 |

### F-09d 반응한 사람 (61770:5402)

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

### F-10 채팅 E10 = 남의 메시지 길게 눌러 연 메뉴 (61770:5565)

배경 화면은 F-09와 같다(위 표 그대로). 메뉴만 다르다.

| 항목 | 피그마 | 앱(파일:줄) | 차이 종류 |
|---|---|---|---|
| 메뉴 항목 | 위 줄에 반응 3종(좋아요, 하트, 체크), 아래 항목 **"신고" 하나**(Action=신고). 삭제 없음 | 리액션 2종, 구분선, "신고"(canDelete면 "삭제"도 함께)(`chat/ChatMessageRow.kt:433-458`) | 다름(반응 종류 수). 운영진의 경우는 확인 못 함 |
| 위치 | 왼쪽 끝을 남의 말풍선 시작(x=40, 아바타 32 + gap 8 다음)에 맞추고, 누른 말풍선 바로 위에 띄움(top 421, "다들 고생했어요!" 말풍선 위) | M3 DropdownMenu 기본 위치(앵커 Box 아래, 왼쪽 정렬) | 다름(위치) |
| 메뉴 모양 | F-09와 같다(폭 160, radius 16, 테두리 #E9EDE0, 그림자 0 4 12 rgba(0,0,0,.18), 반응 칸 40×40 radius 8, 아이콘 18, 항목 h40 14sp) | F-09 표와 같다 | 다름 |

### F-10b 메시지 신고 (61770:5706)

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

## 기능·데이터가 필요한 차이(서버 API, 응답 필드, UiState 추가)

1. **반응 종류 CHECK 추가**: 피그마는 좋아요, 하트, 체크 세 종류다. 서버 `ReactionKind`(LIKE, HEART), 앱 `domain/chat/ReactionKind.kt`, 문자열(chat_reaction_*), SRS FR-210과 자료사전 "리액션종류 = [좋아요 | 하트]"에 모두 CHECK를 더해야 한다. DB 제약도 확인해야 한다.
2. **반응한 사람 목록(F-09d)**: 새 API가 필요하다. 예: `GET /chat-messages/{id}/reactions` → `[{memberId, nickname, avatar?, kind}]`. 그 밖에 `ChatRepository.reactors(messageId)`, 도메인 모델, ChatUiState(시트 대상 메시지, 고른 탭(전체 또는 종류), 목록, 로딩·오류), ChatActions(열기, 탭, 닫기)가 필요하다. 여는 제스처도 정해야 한다(알약 누름은 지금 달기·떼기다).
3. **작성자 직책 배지("크루장")**: `ChatMessageItem`과 `CrewChatPosted` 실시간 메시지에 작성자 직책(role)을 넣거나, 앱이 크루원 목록(CrewMembers, `CrewRole.labelRes()`가 "크루장" 문구를 이미 가짐)과 authorId를 맞춰 붙여야 한다. 채팅 모듈은 지금 크루원 목록을 모른다. 운영진도 배지를 붙이는지는 확인 못 함(피그마에는 크루장만 있다).
4. **시스템 메시지("[닉네임]님이 러닝을 마쳤어요 / n.nn km · mm:ss")**: 서버에 시스템 메시지 종류와 생성 시점(크루원 러닝 완료 이벤트), 응답 필드(type, distance, duration)가 필요하다. 앱에는 `ChatMessage` 종류 구분과 렌더러가 필요하다. SRS에 근거 요구가 없다(아래 문서 충돌 참고).
5. **채팅을 크루 홈 탭으로 넣기(F-09 구조)**: 크루 홈 `CrewTab`에 CHAT을 더하고 `CrewStripItem.CHAT.tab`을 null이 아니게 바꾼다. 화면이 보이는 동안만 방을 여는 규칙(`LifecycleStartEffect` → `ChatViewModel.onShown/onHidden`, D-103·D-130)을 탭 선택과 연결한다. 알림 진입(`CrewChatEntryRoute`, `Routes.CrewChat`, InboxDestination)도 크루 홈의 채팅 탭으로 보내도록 고친다. 서버 API는 바뀌지 않는다. 그래도 내비게이션과 생명주기를 바꾸는 일이라 화면만 고치는 범위를 넘는다.
6. (선택) **아바타 이미지**: 피그마 아바타는 단색 원이라 지금 데이터로 그릴 수 있다. 실제 프로필 이미지를 넣으려면 작성자 아바타 필드가 필요하다(확인 못 함).

## 화면만 고치면 되는 차이

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

## 앱에 없는 피그마 에셋(아이콘·이미지)

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

## 문서 충돌

1. **리액션 종류**: SRS FR-210(`docs/sw/srs/03-05-크루-관리.md`)과 용어집·자료사전은 "좋아요(👍)와 하트(❤️) 두 종류"로 정했다. 피그마는 좋아요, 하트, **체크** 세 종류이고 이모지가 아니라 줄선 아이콘이다. 요구사항 변경(CR)이 필요하다.
2. **삭제 확인 문구와 삭제 표시**: D-160(`docs/sw/design-oo/05-02d-계층-동시성-설계-결정.md:16`)은 삭제·숨김 메시지를 "삭제된 메시지" 자리표시로 남기도록 정했다. 피그마 문구는 "삭제된 메시지입니다"이고, 확인 본문도 앱의 "…\"삭제된 메시지\"로 남아요"와 다른 "삭제한 메시지는 되돌릴 수 없어요."다. 자리표시를 남기는 동작 자체는 같다. 문구만 다르다.
3. **시스템 메시지**: FR-208~FR-210과 자료사전 크루채팅목록(`메시지ID + 작성자표시명 + 메시지내용 + 작성일시 + 리액션`)에는 시스템 메시지(러닝 완료 안내)가 없다. 피그마는 채팅 안에 "[닉네임]님이 러닝을 마쳤어요"를 둔다. 새 요구가 필요하다.
4. **작성자 직책 배지**: FR-209는 "메시지마다 작성자 표시명, 작성일시, 리액션 종류별 수"를 보이도록 정했고 직책은 없다. 피그마는 "크루장" 배지를 보인다.
5. **반응한 사람 보기**: FR-209·FR-210과 자료사전에는 반응한 사람 목록이 없다(수와 본인 여부만). 피그마 F-09d는 누가 어떤 반응을 달았는지 보인다. 요구 추가와 개인정보(닉네임 노출) 검토가 필요하다.
6. **채팅 진입 구조**: 설계(6.1.14 ChatRoomScreen, `Routes.CrewChat`, D-150 알림 진입)는 채팅을 따로 뜨는 화면으로 둔다. 피그마는 크루 홈의 세 번째 탭으로 그린다. 코드 주석(`CrewTab.kt:6`, `CrewStripItem.kt:5`)은 "채팅 칸은 탭 내용이 아니라 채팅방을 연다"고 명시한다.
7. **글자 수 표시**: FR-208은 500자 초과를 막는다. 앱은 "n/500"과 초과 안내를 입력 줄 아래에 보인다. 피그마에는 표시가 없다. 지울 경우 초과 안내를 어디에 둘지 정해야 한다.

## 확인 못 함

- "E10"의 뜻(docs에 F-10 E10 정의 없음).
- 반응 알약 선택색: 컴포넌트의 보라 #5C4BDB와 화면 인스턴스의 #67705B 가운데 어느 쪽인지.
- 반응한 사람 시트를 여는 제스처, 한 사람이 여러 종류를 단 경우의 행 모양.
- 메뉴의 반응 칸 선택 상태 모양. 운영진이 남의 메시지를 눌렀을 때 삭제와 신고가 함께 나오는지.
- 남의 메시지가 삭제된 모양, 빈 채팅, 보내는 중, 보내기 실패, 기타 사유 상세 누락 오류 표시(피그마 프레임에 없음).
- 날짜 알약의 "오늘" 말고 다른 날짜 형식. 말풍선 최대 폭.
- 다크 모드 값(이번에는 라이트 값만 확인함).
- 하트·체크 아이콘이 앱 `RunkkIcons.Heart`·`Check`와 같은 모양인지(에셋을 받아 비교하지 않음).
