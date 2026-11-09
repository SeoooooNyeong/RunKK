# 병합 대기: feature/S15P31A207-275-event-ui-align

> 작성: 2026-11-09 | 작성자: 이성현 | 스킬: sw-design-oo(앱 화면 절 보충), 구현, sw-code-review

이벤트 관련 앱 화면(이벤트 목록 I-3b, 알림함 이벤트 행과 공지 행 I-3, 홈 알림 점 A-4, 셀럽런 목록 코치 두 줄 E-1)을 피그마 정식 화면과 맞췄다. 피그마 이벤트 컴포넌트(섹션 62268:2782)에 해당하는 작은 부품은 core.designsystem으로 옮겼다. 상세 두 화면(I-3c, I-3d)은 서버 정적 웹 페이지라 범위 밖이다. 코드는 같은 브랜치의 코드 커밋이다.

## 고친 조각
- design-oo/05-26-설계-이벤트-성.md: 새 2.6 「앱 화면 피그마 정식 디자인 반영」(차이 표, 피그마와 여전히 다른 점, 요구와 결정 대응 행, 결정 [임시:D-성-27]~[임시:D-성-30], 2.5에 더할 클래스 행), 9절 시험, 11절 요청. 모두 「(다음 판 변경)」 표시. 머리 헤더, 머리말, 1.1, 1.2, 2.5 표는 고치지 않았다(MR !100 정식 번호 병합과 충돌하지 않게, 코드 리뷰 [임시:EV-성-18])
- code-review/이벤트-화면-디자인-성.md(새 조각): MR !104 코드 리뷰, 결함 [임시:EV-성-18]~[임시:EV-성-21], 판정 합격

## 새 임시 번호
| 임시 번호 | 종류 | 한 줄 설명 | 위치 |
|-----------|------|------------|------|
| [임시:D-성-27] | D | 이벤트 화면 작은 부품(상태 칩, D-day 배지, 밑줄 탭, 넘김 점)을 core.designsystem으로, 결과 화면 쪽 점도 같은 부품 | design-oo/05-26 2.6 |
| [임시:D-성-28] | D | 알림 점 지름 12와 바깥 2dp 테두리, 홈 점 자리, RunkkIconButton 배지 통일 | design-oo/05-26 2.6 |
| [임시:D-성-29] | D | 알림함 운영자 공지 전용 행(피그마 공지 행, 본문 줄 유지, 링크 있을 때만 바깥 열기 표시) | design-oo/05-26 2.6 |
| [임시:D-성-30] | D | 셀럽런 목록 코치 얼굴(CelebrityCharacter.face, CelebrityPortrait)과 셀럽 카드 수치 | design-oo/05-26 2.6 |
| [임시:EV-성-18] | 결함(주요, 수정) | 05-26 보충이 MR !100 정식 번호 병합과 다섯 곳 충돌 → 2.6절로 모음 | code-review/이벤트-화면-디자인-성 |
| [임시:EV-성-19] | 결함(경미, 남김) | RunkkIconButton(hasBadge) 점 테두리 색을 부르는 쪽이 못 정함 | code-review/이벤트-화면-디자인-성 |
| [임시:EV-성-20] | 결함(제안, 남김) | RunkkPageDots 꺼진 점 기본색 Border가 다크에서 대비 낮음 | code-review/이벤트-화면-디자인-성 |
| [임시:EV-성-21] | 결함(제안, 남김) | 홈 알림 점 자리가 기준 이미지에 없음 | code-review/이벤트-화면-디자인-성 |

번호 메모: 지시는 「[임시:D-성-1]부터」였으나 05-26이 이미 [임시:D-성-1]~[임시:D-성-18], 05-27이 [임시:D-성-19]~[임시:D-성-26]을 써서 같은 파일 안에서 겹치지 않게 [임시:D-성-27]부터 이었다. 정식 번호는 docs-S15P31A207-275-event-docs 병합 대기 파일의 시작점(D-603부터) 뒤에 이어 매긴다.

## 공용 파일 반영
| 파일 | 넣을 내용 |
|------|-----------|
| design-oo/06-06 계열(앱 데이터, 표현 요약) | core.designsystem 새 부품 `RunkkStatusChip`(+`RunkkStatusChipTone`), `RunkkDdayBadge`, `RunkkUnderlineTabs`, `RunkkPageDots`, `RunkkNotificationDot`, `CelebrityPortrait`, `CelebrityCharacter.face`, `RunkkIcons.OpenInNew`. ui.notification `InboxNoticeRow`. ui.notification `NewEventDot`, `StatusChip`, `BannerDots` 삭제 |
| design-oo/A-추적표.md | [임시:FR-성-2], [임시:FR-성-3], [임시:FR-성-16] 행에 [임시:D-성-27]~[임시:D-성-29], FR-100 행에 [임시:D-성-30] |
| design-oo/B-E-변경-이력-색인-가정.md | D 색인에 [임시:D-성-27]~[임시:D-성-30](정식 번호로) |
| design-oo/12-의존도.md | 변경 없음(새 모듈 의존 없음, 앱 ArchitectureTest 허용 쌍 수 그대로) |
| design-oo/05-26-설계-이벤트-성.md | 정식 번호를 매길 때 2.6의 「요구와 결정 대응」 행은 1.1 표 끝, 「결정」 4행은 1.2 표 끝, 「클래스 명세 보충」 3행은 2.5 표 끝으로 옮기고(EventInboxRow 행은 기존 행에 합침) 2.6에는 차이 표와 다른 점만 남긴다. 머리 헤더 입력란에 「(다음 판 변경, 2026-11-09 브랜치 feature/S15P31A207-275-event-ui-align) 피그마 정식 화면 I-3b 62271:6986(Dark 62274:6818), I-3 61969:894, A-4 62273:7142, E-1 60865:7403, 이벤트 컴포넌트 62268:2782」 |
| code-review.md | 머리 헤더 입력란과 최신 판정, 목차에 [이벤트-화면-디자인-성](code-review/이벤트-화면-디자인-성.md) 행(MR !104, 0 / 1 / 1, 제안 2, 합격), 끝에 「리뷰 2026-11-09 (이벤트 화면 디자인 반영)」 절 |
| design-oo.md | 머리 헤더 입력란에 「피그마 정식 화면 2026-11-09(I-3b, I-3, A-4, E-1, 이벤트 컴포넌트 62268:2782)」 |

## 다른 담당에게 요청
- 박재영: 코드 MR에서 ui.battle `RecordRunScreen.CelebCandidateCard`(셀럽런 카드 수치와 코치 얼굴, 고르기 흐름은 그대로)와 `BattleResultScreen.PageDots`(RunkkPageDots로 바꿈, 겉모습 같음), 셀럽런 스크린샷 기준 이미지 변경을 확인한다. 문서 06-03은 고칠 것이 없다.
