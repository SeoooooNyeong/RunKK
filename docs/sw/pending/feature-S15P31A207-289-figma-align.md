# 병합 대기: feature/S15P31A207-289-figma-align

> 작성: 2026-11-09 | 작성자: 이성현 | 스킬: sw-maintain, sw-srs, sw-design-oo(피그마 맞춤, 상위 S15P31A207-289, 묶음 -292~-299)

피그마 Light 화면과 앱의 차이를 사용자 결정(maintenance/2026-11-09_피그마맞춤-성/decisions.md)대로 맞추는 개선 보수의 문서 단계다. 이 브랜치는 문서만 바꾼다. 구현은 묶음 브랜치 여덟 개(W1 -292 ~ W8 -299)가 하고, W4~W8 서버 계약은 각 작업자가 `maintenance/2026-11-09_피그마맞춤-성/contract-W4.md`~`contract-W8.md`에 쓴다. 계약이 확정되면 아래 「공용 파일 반영」의 계약 관련 줄(05-03, 06-04, 06-05, 자료사전, ERD)을 그 파일에서 옮긴다.

## 고친 조각
- maintenance/2026-11-09_피그마맞춤-성.md: 새 유지보수 기록. 개선 보수, CR 묶음 여덟 개, 영향 범위(요구, 모듈, Fan-In, 회귀 TC), Flyway 번호 대역(W4 V50~V52, W5 V53~V55, W6 V56~V58, W7 V59~V61, W8 V62~V64)
- srs/03-05-크루-관리.md: FR-110, FR-112, FR-114, FR-117, FR-121, FR-124, FR-126, FR-127, FR-132, FR-133, FR-135, FR-138, FR-140, FR-141, FR-144, FR-145, FR-147, FR-191, FR-204, FR-209, FR-210, FR-299 고침(다음 판 변경), 새 절 3.5.0a([임시:FR-성-1]~[임시:FR-성-12]), 머리 문단 한 문장
- srs/03-06-캐릭터-관리.md: FR-160, FR-161, FR-163, FR-164, FR-202, FR-236 고침, 새 절 3.6.0a([임시:FR-성-13]~[임시:FR-성-16])
- srs/03-09-2차배포-런바디-측정-성.md: FR-302 주간/월간 토글 삭제
- srs/07-25-변경-요청-CR-피그마맞춤-성.md: 새 조각(7.13.28). 변경 요청 표, 도출 기록(충돌과 정한 값), 바뀐 요구 위치표, [임시:NFR-성-2]·[임시:NFR-성-3], 시나리오 S-FG-1~7, 확인 기준
- srs/07-24-변경-요청-CR-서버시간대-성.md: 첫 줄 「다음」 링크만
- design-oo/05-29-설계-피그마맞춤-성.md: 새 조각. 결정 [임시:D-성-7]~[임시:D-성-40], W1 토큰·공통 부품 이름·아이콘 메타 표기, W2·W3 클래스 변경과 계약 표본, W4~W8 틀(앱 경로·화면 상태·저장소 메소드 이름, 서버 바뀌는 클래스, 계약 파일 링크), 묶음 경계
- design-oo/05-25-설계-2차배포-성.md: 2.3 런바디 줄에 토글 삭제 표시
- design-oo/05-28-설계-서버시간대-성.md: 「다음」 링크만

## 새 임시 번호
| 임시 번호 | 종류 | 한 줄 설명 | 위치 |
|-----------|------|------------|------|
| [임시:CR-성-2] | CR | W1 앱 전체 테마·공통 부품·아이콘 메타 표기(Jira -292) | srs/07-25 |
| [임시:CR-성-3] | CR | W2 크루런 화면(대기실 얼굴, SRS에만 있던 표시 삭제)(-293) | srs/07-25 |
| [임시:CR-성-4] | CR | W3 런바디 주간/월간 토글 삭제(-294) | srs/07-25 |
| [임시:CR-성-5] | CR | W4 크루 찾기·검색·상세·초대·만들기(-295) | srs/07-25 |
| [임시:CR-성-6] | CR | W5 크루 홈·목표·채팅 탭(-296) | srs/07-25 |
| [임시:CR-성-7] | CR | W6 크루 일정(-297) | srs/07-25 |
| [임시:CR-성-8] | CR | W7 크루 정보·크루원 관리(-298) | srs/07-25 |
| [임시:CR-성-9] | CR | W8 아바타·상점(-299) | srs/07-25 |
| [임시:FR-성-1] | FR | 크루명 검색 화면 | srs/03-05 3.5.0a |
| [임시:FR-성-2] | FR | 재가입 제한 남은 시간 표시 | srs/03-05 3.5.0a |
| [임시:FR-성-3] | FR | 초대 코드 조회와 확인(2단계) | srs/03-05 3.5.0a |
| [임시:FR-성-4] | FR | 크루 로고 사진 등록 | srs/03-05 3.5.0a |
| [임시:FR-성-5] | FR | 반응한 사람 보기 | srs/03-05 3.5.0a |
| [임시:FR-성-6] | FR | 채팅 러닝 완료 시스템 메시지 | srs/03-05 3.5.0a |
| [임시:FR-성-7] | FR | 크루런 반복 일정 | srs/03-05 3.5.0a |
| [임시:FR-성-8] | FR | 크루런 일정 상세 | srs/03-05 3.5.0a |
| [임시:FR-성-9] | FR | 크루런 일정 수정 | srs/03-05 3.5.0a |
| [임시:FR-성-10] | FR | 지난 크루런 달력 | srs/03-05 3.5.0a |
| [임시:FR-성-11] | FR | 지난 공동 목표 목록 | srs/03-05 3.5.0a |
| [임시:FR-성-12] | FR | 크루원 정보 팝업(가입일, 넘기기·내보내기) | srs/03-05 3.5.0a |
| [임시:FR-성-13] | FR | 내 러너 화면 | srs/03-06 3.6.0a |
| [임시:FR-성-14] | FR | 잠긴 상점 아이템 | srs/03-06 3.6.0a |
| [임시:FR-성-15] | FR | 상점 아이템 그림 | srs/03-06 3.6.0a |
| [임시:FR-성-16] | FR | 배경 해금 팝업 | srs/03-06 3.6.0a |
| [임시:NFR-성-2] | NFR | 앱 전체 피그마 테마(색, 글꼴, 상단 바, 버튼, 대화상자, 시트) | srs/07-25 |
| [임시:NFR-성-3] | NFR | 가운뎃점 금지와 아이콘 메타 표기 | srs/07-25 |
| [임시:D-성-7]~[임시:D-성-40] | 설계 결정 | W1~W8 결정(묶음 경계 D-성-20·D-성-32 포함) | design-oo/05-29 |

번호 메모: [임시:CR-성-1], [임시:NFR-성-1], [임시:C-성-1], [임시:D-성-1]~[임시:D-성-6]은 병합 대기 `fix-S15P31A207-275-server-timezone.md`가 먼저 썼다. 이 파일은 겹치지 않게 그다음 번호부터 썼다.

정식 번호: CR은 서버 시간대 CR 다음 번호부터 여덟 개, FR은 마지막 FR(FR-321) 다음, NFR은 서버 시간대 NFR 다음, D는 마지막 D 다음부터 순서대로. 묶음 계약 파일(contract-Wn.md)에 임시 번호가 쓰였으면 같이 바꾼다.

## 공용 파일 반영
| 파일 | 넣을 내용 |
|------|-----------|
| srs.md | 머리 헤더 입력란(피그마 맞춤 7.13.28, CR 여덟 개, FR 16개, NFR 두 개), 목차 표에 07-25 행 |
| srs/04-비기능-요구사항.md | [임시:NFR-성-2](사용성, 외관 일관성)와 [임시:NFR-성-3](사용성, 표기 규칙) 행. 본문과 측정 방법은 07-25 7.13.28.2 표 그대로 |
| srs/03-00-작성-기준.md | 3.0.3 권한표: 운영진 열 머리에 「(다음 판 변경, [임시:CR-성-8]: 앱에서 지정할 수 없고 기존 운영진은 크루원으로 바뀐다)」. 「크루 정보·태그·공개 여부·가입 방식 수정, 초대 코드 재발급」 행의 재발급 뒤에 「(앱 화면 없음, 만료 코드는 읽을 때 자동 재발급, FR-112)」. 60행 「크루 소개·태그·인원·지난 주 달린 거리 열람」 행에 「만든 날, 누적 크루런 횟수, 최근 크루런 3건(크루 밖 허용, C9)」. 일정 행에 「일정 수정: 작성자만([임시:FR-성-9])」 새 행 |
| srs/02-전체-설명.md | 기능 요약에서 FR-132(거리 다섯), FR-145(참석/참석 취소), FR-147(대기실 표시), FR-161(악세서리 탭)을 언급한 문장이 있으면 같은 말로 고침 |
| srs/05-외부-인터페이스.md | 크루 찾기·상세·만들기 행에 크루 검색 화면, 초대 코드 조회, 로고 올리기. 크루 홈 행에 채팅 탭. 아바타 행에 내 러너 화면, 배경 해금 팝업 |
| srs/06-04-자료사전.md | 리액션종류 = [좋아요 \| 하트 \| 체크]. 크루채팅목록에 메시지종류([일반 \| 시스템]), 작성자직책, (러닝거리, 러닝경과). 반응한사람목록 = {회원ID + 표시명 + {리액션종류}}. 크루런거리 = [제한 없음 \| 3km \| 5km \| 10km \| 하프]. 일정요청에 제목·내용 분리, 반복여부. 반복규칙(새, 필드는 contract-W6). 크루상세에 만든날·누적거리·누적크루런횟수·최근크루런·가입가능일시(contract-W4). 크루원목록에 누적거리·가입일·레벨·나이대·성별(contract-W7). 상점목록에 잠김·해금조건문구·그림키(contract-W8). 아바타정보에 이번주러닝횟수·피부톤대표시각(contract-W8). 크루명 = 2{글자}12 |
| srs/06-07-ERD-상태전이도.md | 반복 규칙 엔터티와 일정의 관계, 채팅 메시지 종류(계약 확정 뒤 contract-W5·W6에서) |
| srs/07-01b-추적표-FR-130-이후.md | 바뀐 FR 행의 「변경 요청」 열에 해당 [임시:CR-성-n], 새 행 [임시:FR-성-1]~[임시:FR-성-16] → DFD(각 FR의 DFD 칸) → design-oo 05-29 결정 → 시험(`/sw-test` 뒤). 07-01(FR-110·FR-112·FR-114·FR-117·FR-121·FR-124·FR-126·FR-127)도 같은 열 |
| srs/03-05-크루-관리.md | 피그마 맞춤으로 96KB가 되었다(README 4절 60KB 기준 초과, 100KB 미만). 다음 공용 정리 때 3.5.0a 절을 따로 조각(예: 03-05b)으로 나눌지 정한다 |
| design-oo.md | 머리 헤더 입력란, 목차 표에 05-29 행 |
| design-oo/05-19-설계-결정-v0.26.md, 05-20-설계-결정-v0.27.md | 「가운뎃점을 넣지 않는다」 문장 뒤에 「(다음 판 보완: 여러 값을 한 줄에 늘어놓을 때는 아이콘 메타 표기 `MetaRow`, 05-29 [임시:D-성-10])」 |
| design-oo/05-03-API-실시간-메시지.md | W4~W8 REST와 실시간 메시지(CrewChatPosted 필드 추가)를 contract-W4~W8에서 옮긴다. 대기실·결과·일정 응답의 크루런 거리 nullable([임시:D-성-28]) |
| design-oo/06-04-클래스-crew.md | (이성현 담당 조각이지만 계약 확정 뒤 한 번에) crew·crew.chat·batch.crew 새 클래스(CrewRunRepeatJob, ChatRunFinishedListener, 반복 규칙, 로고 저장)와 바뀐 연산 |
| design-oo/06-05-클래스-runimal-policy-operator.md | (이성현 담당, 계약 확정 뒤) avatar 상점 아이템 해금 조건·그림 키, 아바타 응답 필드 |
| design-oo/06-06c-클래스-앱-도메인-동기화-대결-채팅.md | 채팅: ChatRoomScreen 단독 경로 삭제, ChatTabContent, ReactionKind 체크, ChatRepository.reactors, ChatUiState 반응한 사람 상태 |
| design-oo/06-06d-클래스-앱-데이터-표현.md | ui.crew(CrewManage 계열 삭제, CrewSearch·CrewRunDetail·CrewPastRuns·CrewPastGoals·CrewMemberDialog 새), ui.avatar(AvatarRunner 새), core designsystem 공통 부품(05-29 2.2), Routes 변경(05-29 각 절), 저장 CrewCelebrationStore·BackgroundUnlockSeenStore |
| design-oo/12-의존도.md | ~~앱 화면 모듈 허용 표에 `:feature:crew → :feature:chat`. 서버 허용 표에 `crew.chat → running`(사건)~~ (2026-11-10 정정) 화면 모듈 사이 새 의존 없음(칸 `SlotKey.CREW_CHAT_TAB`, [임시:D-성-42]), 서버 새 화살표 없음(crew.chat은 crew 사건 `RaceContributionAdded`, [임시:D-성-43]). 새 모듈 `:core:avatar`와 `:feature:avatar → :core:avatar`([임시:D-성-75]) |
| design-oo/A-추적표.md | 런바디 RunBodySummaryTab 행에 토글 삭제, 새 FR 행 → 05-29 결정과 클래스 |
| test.md, test/06-추적표.md | TF-VB-22(진행률 100% 자르기) 기대 결과를 「넘은 값 그대로, 막대만 꽉 참」으로 바꿈. 바뀐 FR의 원인 확인 TC는 `/sw-test`가 [임시:TC-성-n]으로 |
| README.md | 1절 유지보수 줄에 피그마 맞춤 기록(maintenance 조각 수) |
| design-review | 계약 확정 뒤 05-29 검증(묶음 경계와 계약 파일 대조) |

## 다른 담당에게 요청
- 박재영: 런바디 화면(W3, -294)은 결정 B3에 따라 이성현이 코드를 고치고 박재영이 리뷰한다. 바뀌는 클래스는 design-oo/05-29 4절(RunBodyPeriod, RunBodyUiState.period, RunBodyActions.onPeriod, RunBodyViewModel.onPeriod 삭제, FrequencyRow는 runsPerWeek만). 리뷰 요청.
- 박재영: srs/03-03-기록-분석.md FR-067 처리 칸의 「코멘트가 아직 없으면 체력 요약만 보여 주…」 문장을 앱 동작(811eef40에서 「이번 주 요약」 카드를 뺐다)에 맞춰 고쳐 주세요. 제안 문안: 「코멘트가 아직 없으면 러닝 코멘트 카드에 「코멘트 준비 중」을 보이고 나머지 카드(최근 10회 변화, 주간 러닝 기록, 최근 10회 평균)는 그대로 보인다 (다음 판 변경, [임시:CR-성-4], 피그마 G-33)」.
- 박재영: srs/03-03-기록-분석.md FR-267 처리 칸의 빈 달 문구 「이번 달 분석된 러닝이 없어요」를 피그마와 앱 문구 「이번 달의 러닝을 아직 분석하지 않았어요」로 바꿔 주세요(피그마 G-32, 앱은 이미 이 문구다).
- 박재영: core designsystem `RunResultDetail`을 피그마 F-16b 값으로 고치면 대결 결과 2쪽도 같이 바뀐다(design-oo/05-29 [임시:D-성-15]). W2(-293) MR에서 대결 결과 2쪽 스냅샷 변경 확인 요청.
- 박재영: W1(-292) 테마 변경으로 러닝 측정·대결·런바디·코치 화면의 색, 글꼴(IBM Plex Sans KR), 상단 바(원 없는 ←)가 바뀐다. 담당 화면의 스냅샷 기준 이미지 갱신을 W1 MR에서 확인 요청.

## 구현 뒤 추가 (2026-11-10, 브랜치 `docs/S15P31A207-289-figma-docs`, 통합 브랜치 363f0992 기준)

스킬: sw-design-oo, sw-implement(8. 기록), sw-test, sw-srs(보정). 코드는 고치지 않았다(읽기만).

### 고친 조각
- design-oo/05-29-설계-피그마맞춤-성.md: 5절 W4~W8에 확정 계약(엔드포인트 표, 오류 코드, Flyway V50·V51·V53·V56·V57·V59·V62, 앱 Route·UiState·Repository 구현 이름) 옮김, 5.6 W9(`:core:avatar` `RunkkRiveAvatar` 공개 API, Rive 파일 4개와 상태 머신 입력, 합성 클래스, avatar-ai 포팅 3모듈, 셀카 응답 8 PNG·mobile nullable), 1.3절 결정 [임시:D-성-41]~[임시:D-성-77], 1.2의 D-성-21·22·26·27·31·32·33·36·38·39 행에 정정·확정 표시, 2.2·3·4절 구현 메모, 6절 의존 정정, 7절 남은 확인, 8절 실제 병합 순서
- srs/03-05-크루-관리.md: FR-132(제목 30자·내용 200자), FR-135(누적 거리 정의), [임시:FR-성-2]·[임시:FR-성-3]·[임시:FR-성-5]·[임시:FR-성-6]·[임시:FR-성-7]·[임시:FR-성-10] 보정
- srs/03-06-캐릭터-관리.md: FR-160(잠긴·보유 칸 고르지 않음), FR-163(게이지 세 칸, 최근 7일), FR-164(Rive 피부 밝기), [임시:FR-성-13]·[임시:FR-성-14](해금 조건 세 종류)·[임시:FR-성-16](내 러너 화면, 확인한 배경 목록) 보정
- srs/07-25-변경-요청-CR-피그마맞춤-성.md: S-FG-4·S-FG-7 정정, 새 절 7.13.28.5(구현 뒤 요구 보정 표, 가운뎃점 정적 검사 불합격)
- implementation/18-피그마맞춤-성.md: 새 조각(묶음·브랜치·Jira, 병렬 방식, 병합 17개와 충돌 해소, 검증 게이트, 멈춤과 재개, 결정 I18-1~I18-5, 남은 일)
- test/피그마맞춤-성.md: 새 조각([임시:TC-성-18]~[임시:TC-성-81] 64개, 판정 조건부)
- maintenance/2026-11-09_피그마맞춤-성.md: 실행 이력, 부작용 점검 결과, 체크리스트, 상태 표

### 새 임시 번호 (추가)
| 임시 번호 | 종류 | 한 줄 설명 | 위치 |
|-----------|------|------------|------|
| [임시:D-성-41]~[임시:D-성-43] | 설계 결정(정정) | 지난 크루런 API는 W6, 채팅 탭은 칸 장치, 시스템 메시지는 레이스 합산 사건 경로 | design-oo/05-29 1.3 |
| [임시:D-성-44]~[임시:D-성-56] | 설계 결정 | W4 초대 미리보기·시도 제한·로고 BYTEA, W5 시스템 메시지 문장·대상·알림, W6 반복 저장·참석 취소 DELETE·일시 검증 위치·제목 30자, W7 누적 거리·팝업 목록 응답·초대 코드 자동 재발급 | design-oo/05-29 1.3 |
| [임시:D-성-57]~[임시:D-성-63] | 설계 결정 | W8 GET /avatar/runner, 체형 4→3, runAmount 서버 계산, 해금 팝업 기기 저장, 잠김 계산, 피부톤 두 규칙, 홈 아바타 그대로·팝업은 내 러너 | design-oo/05-29 1.3 |
| [임시:D-성-64]~[임시:D-성-74] | 설계 결정 | W1 상단 바 투명·세그먼트 두 종·고른 칩 흰 글자·확인 단추 보라·글꼴 번들·DisplayHuge 96/72, W2 칩 문구·지도 페이드 띠·일시정지 캡션 흰색, W3 「다시 확인」 유지·「구간과 피드백 자세히 보기」 | design-oo/05-29 1.3 |
| [임시:D-성-75]~[임시:D-성-77] | 설계 결정 | W9 `:core:avatar` 경계, 의상 대응표 앱 소유·모자 미표시, 셀카 응답 8 PNG·mobile nullable | design-oo/05-29 1.3 |
| [임시:TC-성-18]~[임시:TC-성-68] | 시험 케이스 | 묶음별 자동 시험(새 시험 파일 39개, 메소드 201개)과 계약·구조·게이트 | test/피그마맞춤-성 1절 |
| [임시:TC-성-69] | 시험 케이스 | 가운뎃점 정적 검사(불합격 17개) | test/피그마맞춤-성 2절 |
| [임시:TC-성-70]~[임시:TC-성-81] | 시험 케이스 | 에뮬레이터 수동 확인 9개, 실기기 Rive 검수, 새 셀카 무개입 회귀, APK 크기 | test/피그마맞춤-성 4·5절 |

번호 메모: [임시:TC-성-1]~[임시:TC-성-17]은 서버 시간대(`fix-S15P31A207-275-server-timezone.md`)가 썼다. 구현 기록의 결정 I18-1~I18-5는 implementation 조각 안 번호다(기존 I17-n과 같은 방식).

### 공용 파일 반영 (추가)
| 파일 | 넣을 내용 |
|------|-----------|
| srs/07-01b-추적표-FR-130-이후.md, srs/07-01-추적표.md | 시험 열: FR-110·FR-117·FR-121·FR-124·[임시:FR-성-1]~[임시:FR-성-4] → TC-성-26~30·70·71, FR-138·FR-140·FR-141·FR-209·FR-210·FR-299·[임시:FR-성-5]·[임시:FR-성-6] → TC-성-31~36·72, FR-132·FR-133·FR-145·[임시:FR-성-7]~[임시:FR-성-9] → TC-성-37~42·73, FR-112·FR-114·FR-135·FR-204·[임시:FR-성-10]~[임시:FR-성-12] → TC-성-43~48·74, FR-160~FR-164·FR-202·FR-236·[임시:FR-성-13]~[임시:FR-성-16] → TC-성-49~56·75~77, FR-147·FR-191·FR-300 → TC-성-22·23·78, FR-302 → TC-성-24·25, [임시:NFR-성-2] → TC-성-18~21, [임시:NFR-성-3] → TC-성-18·69. 설계 열에 [임시:D-성-41]~[임시:D-성-77]의 해당 번호(05-29 1.1 표) |
| srs/06-04-자료사전.md | 위 표의 「(contract-Wn)」 자리를 확정 값으로 바꾼다. 리액션종류 = [LIKE, HEART, CHECK]. 메시지종류(ChatMessageKind) = [TEXT, RUN_FINISHED]. 크루채팅메시지 += 메시지종류 + 작성자역할(LEADER, MANAGER, MEMBER 중 지금 역할) + (러닝거리m). 반응한사람 = 회원ID + 표시명 + 리액션종류 + 반응일시 + 본인여부(한 건 한 줄). 크루런반복(CrewRunRepeat) = [NONE, WEEKLY]. 크루런거리 = [제한 없음(null), 3000, 5000, 10000, 21097]. 일정요청 += 제목(1~30자) + 반복. 일정상세(CrewRunDetailResponse) = 게시글ID + 크루ID + 제목 + 내용 + 작성자 + 작성일시 + 일시 + 응답마감 + (거리) + 상태 + 참석인원 + (내응답) + 수정가능 + 삭제가능 + 반복 + (반복요일) + {참석자}. 일정수정요청(CrewRunEditRequest) = 제목 + 내용 + (거리) + 반복. 지난크루런(PastCrewRunsResponse) = 월 + {날짜} + {게시글ID + 제목 + 일시 + (거리) + 상태 + 참석인원 + 달린인원} + (가장이른월). 크루상세 += 개설일시 + 누적거리m + 누적크루런횟수 + {최근크루런: 게시글ID + 일시 + 참여인원 + 총거리m}(최대 3) + (재가입가능일시). 크루목록 += 전체수. 내크루 += (재가입가능일시). 초대코드미리보기(InvitePreviewRequest·Response) = 초대코드 → 크루ID + 크루명 + (소개) + 태그 + (로고) + 공개여부 + 가입방식 + 인원 + 개설일시. 크루로고(CrewLogoResponse) = 로고ID + 로고주소. 크루원(Member) += 누적거리m + 가입일시 + (레벨) + (나이대) + (성별) + (본인만: 나이대공개 + 성별공개). 가입신청 += (레벨) + (나이대) + (성별). 지난목표(GoalHistoryResponse) = {주시작일 + 월 + 월주차 + 목표m + 합계m + 달성여부 + 진행률 + 참여인원} + (다음커서). 내러너(RunnerResponse) = 체형(STAGE1~4) + 체형3단계(0~2) + 피부톤 + (대표시각) + 최근7일횟수 + 최근7일거리m + 러닝량(0~100) + 쉼일수. 배경목록 += 누적거리m, 배경 += 남은거리m. 상점아이템 += (그림키) + 잠김 + (해금조건문구). 해금조건종류(UnlockKind) = [TOTAL_DISTANCE, LONGEST_RUN, RUN_COUNT]. 셀카머리(GeneratedHead) += (frontImage) + (rightImage) + (rightFaceImage) + (rightEyesImage) + (rightHairImage) + (rightLayerLayout). 사유코드 += CREW_LOGO_NOT_FOUND(404), ITEM_LOCKED(409). 크루명 = 2{글자}12(입력 때만, 저장 값 읽기는 2~20) |
| srs/06-07-ERD-상태전이도.md | 새 엔터티 크루런반복(`crew_run_series`: 크루, 작성자, 최신 회차 글, 최신 일시, 진행 여부) 1 : N 크루게시(`crew_post.series_id`, 고유 (series_id, scheduled_at)), 크루게시 += 제목(`title`). 새 엔터티 크루로고(`crew_logo`, BYTEA). 크루채팅메시지 += 종류(`kind`), 러닝거리(`run_distance_meters`). 크루멤버십 역할의 운영진은 V59 뒤 쓰지 않음(값은 남음) |
| design-oo/05-03-API-실시간-메시지.md | 05-29 5.1~5.5의 엔드포인트 표를 옮긴다(새 API: POST /crews/invite-preview, POST·GET /crew-logos, GET /chat-messages/{id}/reactions, GET·PATCH /crew-runs/{id}, DELETE /posts/{id}/attendance, GET /crews/{id}/crew-runs/past, GET /crews/{id}/goals/history, GET /avatar/runner). 실시간 `CrewChatPosted` += messageKind·authorRole·runDistanceMeters, 표본 `CrewChatPosted-runFinished.json` |
| design-oo/06-04-클래스-crew.md | (이성현 담당, 다음 병합 때) 새 클래스: crew `CrewLogo`, `CrewRunSeries`, `InviteCodeRenewal`, 사건 `RaceContributionAdded`, 열거 `CrewRunRepeat`; crew.chat `ChatMessageKind`, `ChatRunFinishedListener`, `CrewChatService.postRunFinished`; batch.crew `CrewRunRepeatJob`; web.crew `CrewLogoController`, `CrewScheduleController`·`CrewScheduleDtos`, `CrewGoalHistoryController`, `CrewMembersAssembler` 확장 |
| design-oo/06-05-클래스-runimal-policy-operator.md | (이성현 담당, 다음 병합 때) avatar `RunnerService`, `AppearanceCalculator`(bodyVariant), policy `ShopUnlock`·`UnlockKind`, reward `ShopService` 잠김 계산, web.avatar `RunnerResponse` |
| design-oo/06-06c-클래스-앱-도메인-동기화-대결-채팅.md | 채팅: `ChatMessage.kind`·`authorRole`·`runDistance`, `Reactor`, `ChatRepository.reactors`, `ChatUiState` 반응한 사람 상태, `ChatTabContent`, `ChatFeatureEntry`의 칸 `SlotKey.CREW_CHAT_TAB` |
| design-oo/06-06d-클래스-앱-데이터-표현.md | 위 기존 줄에 더해: 새 모듈 `:core:avatar`(05-29 5.6 공개 API와 합성 클래스), ui.avatar `AvatarRunnerUiState`·`BackgroundUnlock`·`AvatarLook`, ui.crew `CrewRunWriteMode`·`CrewRunWriteSheet`·`CrewRunDistanceChoice`·`InviteStep`·`InviteSheetState`, core/navigation `SlotKey.CREW_CHAT_TAB`·`LocalSlotCrewId`, core designsystem `RunkkButtonSize`·`RunkkButtonStyle`·`RunkkSegmentedSelection`·`RunkkDialog`·`RunkkBottomSheet`·`MetaRow` |
| design-oo/A-추적표.md | [임시:D-성-41]~[임시:D-성-77] 행(05-29 1.3 출처 칸) |
| design-oo.md | 머리 헤더 입력란에 05-29 보완(확정 계약, W9, D-성-41~77) |
| implementation.md | 목차 표에 [18-피그마맞춤-성](implementation/18-피그마맞춤-성.md) 행(「18. 피그마 맞춤 반복 (2026-11-09~10): 묶음 W1~W9, A단계 서버 → B단계 앱, 병합 17개와 충돌 해소, 서버 통합 빌드 2,298건 중 실패 1 수정, W7 앱·전체 게이트 남음」). 0절 문서 상태 「현재 단계」 칸: 「2026-11-10: 피그마 맞춤 통합 브랜치 363f0992(W7 앱 통합 전, 앱 전체 게이트·에뮬레이터 확인 남음, 원격 미반영)」. 머리 헤더 입력란 |
| implementation/17-2차배포-통합.md | 첫 줄 「다음」 링크를 18-피그마맞춤-성으로 |
| test.md, test/06-추적표.md | 목차 표에 [피그마맞춤-성](test/피그마맞춤-성.md) 행(「피그마 맞춤 시험 (이성현, -289·-292~-300, 2026-11-10, 363f0992)」, [임시:TC-성-18]~[임시:TC-성-81] (64), 단위·통합·검증·시스템). test/서버시간대-성 「다음」 링크. 추적표 06: 위 srs 추적표와 같은 FR → TC. 기존 케이스 기대 결과 변경: TF-VB-22(진행률 100% 초과 그대로), FR-132 1km 케이스(새로 쓰기 거부, 기존 글 유지), FR-163 화면 케이스(게이지 세 칸, 최근 7일), TC-6535(Routes 목록에 COACH_CHAT과 새 경로) |
| README.md | 1절 문서 지도: implementation 18장, test 조각 하나, design-oo 05-29 보완, srs 7.13.28.5. 유지보수 줄의 피그마 맞춤 기록 상태(테스트 중) |
| srs.md | 머리 헤더 입력란에 7.13.28.5(구현 뒤 보정) |

### 다른 담당에게 요청 (추가)
- 박재영: 기록 화면 문자열 11개에 가운뎃점이 남아 있다(`record_coach_title`, `record_coach_goal_saved`, `record_day_row_pattern`, `record_row_stats`, `record_list_row_caption`, `record_tag_battle_win`·`_lose`·`_draw`, `record_tag_friend_win`·`_lose`·`_draw`). [임시:NFR-성-3]에 맞춰 쉼표·띄어쓰기 또는 `MetaRow`로 바꿀지 결정 요청(test/피그마맞춤-성 [임시:TC-성-69]).
- 박재영: W3 런바디(-294) 7커밋과 「다시 확인」 단추 유지([임시:D-성-73]), 「구간과 피드백 자세히 보기」([임시:D-성-74]) 리뷰. 대결 결과 2쪽을 포함한 battle 스냅샷 56장 변경 확인.
