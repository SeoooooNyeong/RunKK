# 병합 대기: fix/S15P31A207-300-crewrun-avatar-pace

> 작성: 2026-11-15 | 작성자: 이성현 | 스킬: sw-code-review(결함 처리 후속), 입력: pending/fix-S15P31A207-300-review-pl12.md(MR !162)의 「크루런은 PM 결정 뒤」 항목
>
> PM 결정(2026-11-15): 러닝 화면 3종(자유런·대결·크루런)의 내 아바타는 같은 규칙(D-856)으로 움직인다. MR !162에서 대결을 맞췄고, 이 브랜치는 크루런 진행 화면을 같은 방식으로 맞춘다. MR !162 위에 쌓였으므로 그 MR이 먼저 병합된다.

## 결정
| 항목 | 내용 | 근거 |
|------|------|------|
| 크루런 내 아바타 동작 | 크루런 측정 화면 내 아바타도 D-856 규칙: `CrewRunPhase.RUNNING`이고 움직이는 중(거리가 3초 안에 늘었고, 일시정지가 아니고, 현재 페이스가 있음)이면 RUN, 그 밖(일시정지·멈춤 3초·페이스 모름·준비·마감)은 STAND. 달리기 빠르기는 `runMotionSpeed(mine.currentPace)`(자유런·대결과 같음) | PM 2026-11-15 「러닝 화면 3종의 아바타가 같은 규칙으로 움직이게」 |
| 적용 범위 | 내 아바타(`RUN_AVATAR` 칸)만. 다른 크루원 그림(`RunkkRunningFigure`, 각자 상태 `MateState.RUNNING && !mine.isPaused`)은 지금 그대로 | 05-30 7절 「크루원 마커는 그대로」 |

## 고친 곳
| 파일 | 내용 | 시험 |
|------|------|------|
| `android/feature/crew/src/main/java/com/a207/runkk/app/ui/crew/CrewRunScreens.kt` `CrewRunMap`(596·597·600행) | 동작을 `RUNNING && !mine.isPaused`에서 `RUNNING && isMoving`(이미 계산하던 `rememberIsMoving(mine.distance) && !mine.isPaused && mine.currentPace != null`)으로, `RunAvatarSlot(pose, isLeft, runSpeed)`에 `runMotionSpeed(mine.currentPace)`를 넘긴다. 기존 부품만 재사용 | `CrewRunAvatarSlotTest` 3건(움직이면 RUN·빠르기 1.2·일시정지면 STAND, 3초 멈추면 STAND·다시 움직이면 RUN, 페이스 모르면 STAND·빠르기 1). 대결 `BattleAvatarSlotTest`와 같은 짜임 |

스냅샷: 바뀐 것 없음(측정 화면 스냅샷은 칸이 비어 `RunkkRunningFigure`를 그리고, 그 그림의 `isMoving`은 그대로다).

## 새 임시 번호
| 임시 번호 | 종류 | 한 줄 설명 | 위치 |
|-----------|------|------------|------|
| 없음 | | | |

## 공용 파일 반영
| 파일 | 넣을 내용 |
|------|-----------|
| design-oo/05-30-설계-아바타요구-성.md 7절 표 크루런 행 「동작」 열 | 지금 「`CrewRunPhase.RUNNING && !mine.isPaused` → RUN(진행 방향 뒤집기)」 → 「`CrewRunPhase.RUNNING`이고 움직이는 중(거리가 3초 안에 늘었고, 일시정지가 아니고, 현재 페이스가 있음)이면 RUN(진행 방향 뒤집기), 그 밖(일시정지·멈춤·페이스 모름·준비·마감)은 STAND. 달리기 빠르기는 `runMotionSpeed(내 현재 페이스)`(D-856, PM 2026-11-15). 크루원 그림은 그대로」 |
| design-oo/05-30 [임시:D-성-122] 동작 규칙 | 크루런 문장을 위 행과 같게 고친다(자유런·대결 문장은 !162 반영분과 함께 D-856으로) |
| pending/fix-S15P31A207-300-review-pl12.md 반영 시 | 「크루런 행은 PM이 정한 뒤 적는다」·「크루 담당 요청」은 이 파일로 끝난다(크루런도 D-856) |
| code-review/27 1절 D-856 행, 6절 U27-4 | 「크루런은 남음」 → 「크루런도 고쳤다(fix/S15P31A207-300-crewrun-avatar-pace)」 |
| srs FR-339 처리 칸 | 「측정 중이어도 멈추면 서기, 달리기 빠르기는 페이스 비례」 적용 화면에 크루런을 더한다(자유런·대결·크루런) |

## 다른 담당에게 요청
- 크루 담당: 이 MR 리뷰. 크루런 중 3초 멈추면 내 아바타가 서고, 페이스에 따라 빨라지는지 실기기 확인
