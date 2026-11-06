# 병합 대기: hotfix/S15P31A207-275-v0111-feedback
> 작성: 2026-11-04 | 작성자: 이성현 | 스킬: sw-implement(핫픽스)

v0.11.1 핫픽스(main). 2026-11-03 컨설턴트 피드백과 사용자 지적 가운데 사용자가 2026-11-04에 핫픽스로 고른 4건이다. 코드는 main에 먼저 들어가고, 문서는 main → develop 역병합 뒤 `/sw-sdlc merge`로 정식 번호를 매긴다.

## 고친 조각

없음(코드만). 아래 요청을 반영해야 문서와 코드가 맞는다.

## 새 임시 번호

| 임시 번호 | 종류 | 설명 | 위치 |
|---|---|---|---|
| [임시:CR-성-1] | CR | 러닝 종료 짧게 누름·측정 중 뒤로 가기는 안내를 더 띄우지 않는다. "길게 눌러 종료"는 버튼 아래 캡션 하나로만 보인다(같은 문구가 캡션, 안내 줄, 안내 띠 세 군데에 겹쳤다) | 이 파일 |
| [임시:CR-성-2] | CR | 코치 오늘 계획에서 이미 오늘 계획인 거리를 다시 누르면 오늘 계획을 취소한다. 서버 `DELETE /api/v1/plans/{date}`(204, 계획 없어도 204, 지난 날짜 409 PAST_DATE), 앱 `RecordRepository.deletePlan(date)`, 문구 "오늘 계획을 지웠어요" | 이 파일 |
| [임시:CR-성-3] | CR | 홈 러닝 설정 아이콘을 해 모양에서 톱니바퀴(RunkkIcons.Settings)로 | 이 파일 |
| [임시:CR-성-4] | CR | 여자 캐릭터 얼굴 그림(avatar_female_face.webp)의 흰 배경 조각을 투명 처리(요구 변경 없음, 결함 수정) | 이 파일 |

## 공용 파일 반영

| 파일 | 요청 |
|---|---|
| srs/07-1x 변경 요청 표 | [임시:CR-성-1]~[임시:CR-성-4] 행. 출처: 2026-11-03 컨설턴트 피드백(위키), 2026-11-04 사용자 핫픽스 선정 |
| design-oo 5.7.1(API)·05-19 또는 새 조각 | `CoachController DELETE /plans/{date}` → `CoachService.deletePlan(memberId, date)` → `RunningGoal.deletePlan(date, today)`. 204·멱등·PAST_DATE. 앱 포트 `RecordRepository.deletePlan(date): Outcome<Unit>`, `CoachViewModel.onTodayPlan`은 같은 거리면 지우고 `TodayPlanCache`를 비운다 |
| test.md 다음 판 | 각 CR의 시험: RunViewModelTest(짧게 누름 안내 없음), AnalysisDomainTest·AnalysisServicesTest·RecordControllerTest(deletePlan), AnalysisViewModelsTest 3건, RecordRepositoryImplTest 1건, 홈 스크린샷 9장 |
| implementation.md | 핫픽스 v0.11.1 기록(커밋 ad75029e, ac501cff, f67345ad, 70f01073, b269788e, c314118a) |

## 다른 담당에게 요청

- 박재영: srs/03-02-러닝-측정.md FR-181 "측정 중 뒤로 가기는 종료가 아니라 '길게 눌러 종료' 안내"를 "버튼 아래 '길게 눌러 종료' 캡션이 늘 보이고, 짧게 누름·뒤로 가기는 측정을 이어 간다"로 바꾼다([임시:CR-성-1]).
- 박재영: srs/03-03(코치 계획) FR-073 처리에 "이미 오늘 계획인 거리를 다시 누르면 오늘 계획을 취소한다"를 더한다([임시:CR-성-2]).
- 박재영: 코치 오늘 계획 거리 칸은 접근성 역할이 RadioButton이라 화면 낭독기가 "다시 누르면 취소"를 알 수 없다. 후속으로 안내를 더할지 정한다.
