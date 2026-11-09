# 병합 대기: feature/S15P31A207-275-event-ui-align

> 작성: 2026-11-09 | 작성자: 이성현 | 스킬: sw-design-oo(앱 화면 절 보충), 구현

이벤트 관련 앱 화면(이벤트 목록 I-3b, 알림함 이벤트 행과 공지 행 I-3, 홈 알림 점 A-4, 셀럽런 목록 코치 두 줄 E-1)을 피그마 정식 화면과 맞췄다. 피그마 이벤트 컴포넌트(섹션 62268:2782)에 해당하는 작은 부품은 core.designsystem으로 옮겼다. 상세 두 화면(I-3c, I-3d)은 서버 정적 웹 페이지라 범위 밖이다. 코드는 같은 브랜치의 코드 커밋이다.

## 고친 조각
- design-oo/05-26-설계-이벤트-성.md: 머리 헤더 입력란 보충, 1.1 대응 행, 1.2 결정 [임시:D-성-27]~[임시:D-성-30], 2.5 앱 클래스 행 보충, 새 2.6 「앱 화면 피그마 정식 디자인 반영」(차이 표, 피그마와 여전히 다른 점), 9절 시험, 11절 요청. 모두 「(다음 판 변경)」 표시

## 새 임시 번호
| 임시 번호 | 종류 | 한 줄 설명 | 위치 |
|-----------|------|------------|------|
| [임시:D-성-27] | D | 이벤트 화면 작은 부품(상태 칩, D-day 배지, 밑줄 탭, 넘김 점)을 core.designsystem으로, 결과 화면 쪽 점도 같은 부품 | design-oo/05-26 1.2, 2.6 |
| [임시:D-성-28] | D | 알림 점 지름 12와 바깥 2dp 테두리, 홈 점 자리, RunkkIconButton 배지 통일 | design-oo/05-26 1.2, 2.6 |
| [임시:D-성-29] | D | 알림함 운영자 공지 전용 행(피그마 공지 행, 본문 줄 유지, 링크 있을 때만 바깥 열기 표시) | design-oo/05-26 1.2, 2.6 |
| [임시:D-성-30] | D | 셀럽런 목록 코치 얼굴(CelebrityCharacter.face, CelebrityPortrait)과 셀럽 카드 수치 | design-oo/05-26 1.2, 2.6 |

번호 메모: 지시는 「[임시:D-성-1]부터」였으나 05-26이 이미 [임시:D-성-1]~[임시:D-성-18], 05-27이 [임시:D-성-19]~[임시:D-성-26]을 써서 같은 파일 안에서 겹치지 않게 [임시:D-성-27]부터 이었다. 정식 번호는 docs-S15P31A207-275-event-docs 병합 대기 파일의 시작점(D-603부터) 뒤에 이어 매긴다.

## 공용 파일 반영
| 파일 | 넣을 내용 |
|------|-----------|
| design-oo/06-06 계열(앱 데이터, 표현 요약) | core.designsystem 새 부품 `RunkkStatusChip`(+`RunkkStatusChipTone`), `RunkkDdayBadge`, `RunkkUnderlineTabs`, `RunkkPageDots`, `RunkkNotificationDot`, `CelebrityPortrait`, `CelebrityCharacter.face`, `RunkkIcons.OpenInNew`. ui.notification `InboxNoticeRow`. ui.notification `NewEventDot`, `StatusChip`, `BannerDots` 삭제 |
| design-oo/A-추적표.md | [임시:FR-성-2], [임시:FR-성-3], [임시:FR-성-16] 행에 [임시:D-성-27]~[임시:D-성-29], FR-100 행에 [임시:D-성-30] |
| design-oo/B-E-변경-이력-색인-가정.md | D 색인에 [임시:D-성-27]~[임시:D-성-30](정식 번호로) |
| design-oo/12-의존도.md | 변경 없음(새 모듈 의존 없음, 앱 ArchitectureTest 허용 쌍 수 그대로) |
| design-oo.md | 머리 헤더 입력란에 「피그마 정식 화면 2026-11-09(I-3b, I-3, A-4, E-1, 이벤트 컴포넌트 62268:2782)」 |

## 다른 담당에게 요청
- 박재영: 코드 MR에서 ui.battle `RecordRunScreen.CelebCandidateCard`(셀럽런 카드 수치와 코치 얼굴, 고르기 흐름은 그대로)와 `BattleResultScreen.PageDots`(RunkkPageDots로 바꿈, 겉모습 같음), 셀럽런 스크린샷 기준 이미지 변경을 확인한다. 문서 06-03은 고칠 것이 없다.
