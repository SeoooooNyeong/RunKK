# 병합 대기: fix/S15P31A207-300-review-pl12

> 작성: 2026-11-15 | 작성자: 이성현 | 스킬: sw-code-review(결함 처리), 입력: code-review/27-기록대결코치-리뷰-박대행(MR !161 브랜치 docs/S15P31A207-300-sw-merge-1114)
>
> 리뷰 27의 중 결함 4건(PL12-A1~A4)을 PM 결정(2026-11-15 「지금 고친다」)으로 고친다. 리뷰 27 조각은 다른 브랜치(MR !161)에 있어 이 브랜치에서 직접 고치지 않는다. 병합 뒤 공용 담당이 아래 「공용 파일 반영」대로 결함 표의 처리 현황을 적는다. 경 결함(PL12-A5~A23, PL12-B*)은 이 브랜치 범위가 아니다.

## 고친 조각
- 없음(문서 조각은 고치지 않음, 결함 처리 현황과 설계 어긋남만 이 파일에 적는다)

## 결함 처리 현황
| 결함 | 등급 | 처리 | 바뀐 곳 | 시험 |
|------|------|------|---------|------|
| PL12-A1 | 중 | 해결. 대결 측정 화면도 자유런과 같은 D-856 규칙: `RUNNING`이어도 멈춰 있으면(거리가 3초 늘지 않음, 페이스 모름) STAND, 달리기 빠르기는 `runMotionSpeed(state.currentPace)`를 `RunAvatarSlot`에 넘긴다(기존 부품 재사용). 마감(CLOSING)·카운트다운은 그대로 STAND. 크루런 `CrewRunScreens.kt`는 크루 도메인이라 고치지 않았다(리뷰도 「참고」) | `android/feature/battle/.../BattleRunningScreen.kt` `BattleRoute` | `BattleAvatarSlotTest` 3건(움직이면 RUN·빠르기 1.2, 3초 멈추면 STAND·다시 움직이면 RUN, 페이스 모르면 STAND·빠르기 1) |
| PL12-A2 | 중 | 해결. 결과 상세 구간 막대 채움을 `splitBarFill(colors) = textSecondary`로 바꾼다(라이트 #52525B, 트랙 surfaceHigh #E4E4E7 대비 약 6.1:1 / 다크 #A1A1AA로 이전 barCrew 다크 값과 같아 다크 화면은 그대로). 리뷰 교정 예시 `BorderStrong`(#8A8A93)은 트랙 surfaceHigh 대비 약 2.7:1이라 3:1에 못 미쳐 쓰지 않았다. 크루 화면의 `BarCrew`(크루 도메인)는 그대로 | `android/core/ui/.../RunResultDetail.kt` | `ColorContrastTest` 1건(두 벌 모두 트랙·카드 바탕 대비 3 이상), D4 라이트 스냅샷에 구간 페이스를 넣음 |
| PL12-A3 | 중 | 해결. `record_chat_continue_meta`를 `%1$s, %2$s`(쉼표)로. 첫 질문은 길 수 있어 한 줄 자르기(`maxLines = 1`)인 MetaRow 대신 쉼표를 골랐다(D-642·D-713 「나머지는 쉼표·띄어쓰기」, c6570511 방식) | `android/feature/record/src/main/res/values/chat_strings.xml` | `CoachChatScreensTest` 1건(「어제 러닝 어땠어?, 11월 2일」, 화면에 「·」 0개) |
| PL12-A4 | 중 | 해결. 구간 기록 줄에 `spokenValue`(「1km, 1킬로미터당 5분 42초, 148 bpm」), 코칭 근거에 읽기 문구(「확인 근거 전반 1킬로미터당 5분 44초, 후반 1킬로미터당 5분 52초」)를 붙인다. `RunkkFormat.paceSpoken` 재사용, 새 문자열 `record_runbody_coaching_split_spoken` | `android/feature/record/.../runbody/RunBodyDetailPage.kt`, `values/strings.xml` | `RunBodyScreensTest` 상세 시험에 contentDescription 3건 |

스냅샷 다시 기록: `feature/battle/src/test/snapshots/D4_battle_result_detail_light.png`(구간 막대가 보이게 픽스처에 구간 페이스를 더함, 막대 #52525B), `feature/record/src/test/snapshots/record_ai_coach_continue.png`(가운뎃점 → 쉼표). core:ui·battle·crew·share·record·run 모듈을 다시 기록해 이 둘만 바뀌었다.

## 새 임시 번호
| 임시 번호 | 종류 | 한 줄 설명 | 위치 |
|-----------|------|------------|------|
| 없음 | | | |

## 공용 파일 반영
| 파일 | 넣을 내용 |
|------|-----------|
| code-review/27-기록대결코치-리뷰-박대행.md 2절 결함 표 | PL12-A1~A4 처리 칸에 「해결(fix/S15P31A207-300-review-pl12)」과 위 「결함 처리 현황」 요약. 판정 줄 「중 4」 → 「남은 중 0」 |
| code-review.md v0.30 줄 | 남은 결함 수 (0 / 4 / 28) → (0 / 0 / 28) |
| code-review/27 1절 D-856 행, 6절 U27-4 | 대결 측정 화면은 고쳤다(크루런은 남음) |
| design-oo/05-30-설계-아바타요구-성.md 7절 표 대결 행 「동작」 열 (U27-4) | 지금 「RUNNING이면 RUN」 → 「RUNNING이고 움직이는 중(거리가 3초 안에 늘었고 현재 페이스가 있음)이면 RUN, 그 밖(멈춤·마감·카운트다운)은 STAND. 달리기 빠르기는 runMotionSpeed(현재 페이스)」. D-856과 맞춘다. 크루런 행은 아직 옛 동작(크루 도메인 미수정)이라 D-856 적용 범위(자유런·대결만인지, 크루런까지인지)를 PM이 정한 뒤 적는다 |
| srs FR-339 처리 칸 (U27-4) | 「측정 중이어도 멈추면 서기, 달리기 빠르기는 페이스 비례」(자유런·대결) |
| design-oo/05-29 2.1절 색 토큰(또는 D-647 행) | 결과 상세 구간 막대 채움은 barCrew가 아니라 textSecondary(비텍스트 대비 3:1, PL12-A2) |

## 다른 담당에게 요청
- 박재영(러닝 측정·기록·대결 담당): 이 MR 리뷰. 특히 대결 측정 화면 아바타가 멈춤 3초 뒤 서고 페이스에 따라 빨라지는지 실기기 확인
- 크루 담당: 크루런 `CrewRunScreens.kt:594·597`도 같은 규칙(D-856)을 따를지 PM 결정 뒤 반영(리뷰 PL12-A1 참고 항목)
