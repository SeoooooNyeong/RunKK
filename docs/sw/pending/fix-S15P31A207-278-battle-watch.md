# 병합 대기: fix/S15P31A207-278-battle-watch

> 작성: 2026-11-08 | 작성자: 이성현 | 스킬: sw-maintain, sw-implement

대결·워치 결함 3건(S15P31A207-278) 수정. 공용 담당이 쓴 코드 브랜치지만 박재영 담당 조각(srs 03-02·03-04)을 고쳐야 하므로 요청으로 남기고, 공용 조각(design-oo 06-06c·06-06d·06-06e, test, 추적표)은 이 MR 병합 뒤 `/sw-sdlc merge`에서 반영한다.

## 고친 조각

- maintenance/2026-11-08_매칭이탈-대결복귀-워치동기화-성.md: 새 조각. 원인·결정·구현·남은 위험.

## 새 임시 번호

| 임시 번호 | 종류 | 한 줄 설명 | 위치 |
|-----------|------|------------|------|
| [임시:CR-성-278-1] | CR | 매칭 대기 중 앱이 백그라운드로 가면 매칭을 취소하고 홈으로 간다. 이미 짝이 지어졌으면 기권(출발 전 취소). 친구 초대 대기는 제외 | maintenance 조각 |
| [임시:CR-성-278-2] | CR | 프로세스가 살아 있는 채 화면만 닫힌 대결은 「대결로 돌아가기」 안내로 진행 화면에 돌아가고, 대결 진행 알림은 진행 화면을 연다 | maintenance 조각 |
| [임시:CR-성-278-3] | CR | 워치 앱 시작·화면 재개 때 폰에 표시 요청(DISPLAY_REQUEST), 폰은 노드 등록 뒤 마지막 표시를 다시 보낸다 | maintenance 조각 |

## 공용 파일 반영 (병합 뒤 sw-sdlc merge)

| 파일 | 넣을 내용 |
|------|-----------|
| srs 변경 요청 표(07-18 다음 조각) | [임시:CR-성-278-1]~[임시:CR-성-278-3] 행. 출처 2026-11-08 사용자 시험 보고와 결정 |
| design-oo/06-06c | MatchViewModel.onBackgrounded, BattleRunCoordinator.forfeitMatched(ALREADY_MATCHED 뒤 active 조회·재시도·forfeit), OpenBattleScreen(앱 범위 진행 화면 키), ActiveBattleViewModel 돌아가기 안내·알림 복귀 |
| design-oo/06-06d | ProgressNotice.battleId, 진행 알림 PendingIntent, MainActivity·BattleNoticeReader |
| design-oo/06-06e | PhoneLink.requestDisplay(2초 간격), WatchFlowViewModel.onScreenShown 요청, WatchLink.onDisplayRequested |
| design-oo/05-03 또는 wear-contract 절 | WearPaths.DISPLAY_REQUEST(워치 → 폰, 본문 없음), 경로 수 14 |
| test | 각 커밋의 S15P31A207-278 시험(매칭 14, 복귀 10, 워치 13)에 정식 TC 번호 |
| srs 추적표 | FR-085·FR-220·동행 모드 FR → 위 TC |

## 다른 담당에게 요청

- 박재영(srs/03-04 FR-085): 처리 끝에 "매칭 대기 중(요청 중 포함) 앱이 백그라운드로 가면 매칭을 취소하고 홈으로 간다. 취소가 이미 배정됨으로 거부되면 그 대결을 기권(출발 전 취소)한다. 친구 초대 대기는 이 규칙을 따르지 않는다 ([임시:CR-성-278-1])"를 더한다.
- 박재영(srs/03-04 FR-220): 「앱 종료 뒤 이어 달리기는 지원하지 않는다」를 "프로세스가 살아 있어 이 기기가 그 대결을 아직 측정 중이면(최근 앱 목록에서 밀어 닫은 경우) 그만두기 대신 「대결로 돌아가기」 안내로 진행 화면에 돌아가고, 대결 진행 알림을 누르면 바로 진행 화면을 연다. 프로세스가 끝난 뒤 이어 달리기는 지원하지 않는다 ([임시:CR-성-278-2])"로 바꾼다.
- 박재영(srs/03-02 동행 모드 FR): "워치 앱이 시작되거나 화면이 다시 보이면 폰에 현재 표시를 요청하고, 폰은 마지막 표시를 다시 보낸다 ([임시:CR-성-278-3])"를 더한다.
- 박재영(코드 검토): 대결(BattleRunCoordinator, MatchViewModel, ActiveBattleViewModel)과 워치 연동(wear-contract, PhoneLink, WatchLink)은 박재영 도메인이므로 MR에서 승인한다.
