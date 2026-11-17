# 병합 대기: impl/S15P31A207-306-review

> 작성: 2026-11-15 | 작성자: 이성현(성) | 스킬: sw-code-review | 다음 판 변경

## 고친 조각

- `code-review/26-실기기수정-성.md` 끝에 「수정 확인」을 추가했다. 결함별 운영 파일, 시험 이름, 실제 실패 재현과 검증 결과, 제외 파일 때문에 남긴 연결을 기록했다. 기존 리뷰 당시의 판정과 파일 식별 이력은 유지했다.
- [임시:AR-성-315], [임시:AR-성-317]~[임시:AR-성-321]은 고침, [임시:AR-성-316]은 공통 카드와 결과 화면을 고치고 실제 카운트다운 호출 연결을 남김이다. 새 임시 번호는 발급하지 않았다.

## 공용 파일 반영

- 요구/설계/시험 추적표에는 위 여섯 건의 수정과 회귀 시험, 316의 부분 수정을 연결한다. 파일과 시험 이름은 리뷰 26의 「수정 확인」을 기준으로 한다. 공용 목차, 버전, 정식 번호는 이 작업에서 변경하지 않았다.
- 설계서 `design-oo/05-31-설계-실기기수정-성.md`: 실제 성별 읽기의 별도 트랜잭션과 기존 동시 읽기 상한, 얼굴 키의 scalar 일괄 조회, `RunRecord.LookColumns`, `RunAvatarFigure` 상태/방향 인자, 준비 신호 이후의 pending 대체 그림 유지, fontScale 1.3 확대 시 카드 여백/간격 조정을 반영한다. 외부 REST/WS DTO와 DB 열은 바꾸지 않았다.
- 카드 기본 배치, 334:383 비율, 그림 60%, 글자 크기와 fontScale은 유지한다. 공유 캡처 검증은 크기와 6개 픽셀 표본 비교이며 전수 픽셀 비교는 아니다.

## 다른 담당에게 요청

- 대결 화면 작업자: 사용자 편집 제외인 `android/feature/battle/src/main/java/com/a207/runkk/app/ui/battle/BattleRunningScreen.kt`의 카운트다운 상대 `Contender`에 `look = state.opponentLook`을 전달한다. 공통 `PastSelfStill`과 결과 화면의 전달은 이 작업에서 수정했다. 연결 후 현재 의상과 저장 의상이 다른 실제 카운트다운 전체 시험을 추가 확인한다.
- `RunkkTrackCard.kt`, `TrackScene.kt`, `RunkkTrackProgressBar.kt`, `AvatarStageBackdrop.kt`, `BattleRunningScreen.kt`, `CrewRunScreens.kt`와 스냅숏 PNG는 이 작업의 편집 제외다.

## 검증

- backend `spotlessApply`, 관련 140시험, 최종 전체 `build` 통과. 전체 303클래스 2,417시험, 실패/오류/건너뜀 0. 실제 PostgreSQL 통합 시험을 포함하며 전체 build는 14분 22초에 한 번 실행했다.
- Android 관련 9클래스 61시험, 실패/오류/건너뜀 0. `ktlintFormat`은 제외 파일 보호 필터로 통과했다. Kotlin daemon의 AppData 쓰기 접근 거부는 `'-Pkotlin.compiler.execution.strategy=in-process'`로 보정했다.
- Android 최종 `./gradlew.bat ktlintCheck lintLocalDebug testLocalDebugUnitTest '-Pkotlin.compiler.execution.strategy=in-process' --max-workers=2`는 보호 필터 없이 통과했다. 13분, 1,161태스크(실행 465, 캐시 295, 최신 401), 실제 실행한 시험 태스크의 XML 합계는 20모듈 392클래스 3,554시험, 실패/오류/건너뜀 0이다.
- 확대 배치 시험은 실제 fontScale=1.3과 거리 40sp, 심박값 16sp를 확인한다. 모든 날짜, 라벨과 값의 글리프/한 줄 경계를 확인하고, 공유 화면과 캡처는 크기와 6개 픽셀 표본을 비교했다.
- Java, Kotlin, 접근성 검토에서 추가 결함은 발견하지 못했다. 검토 결과는 사용자 승인으로 취급하지 않는다.
- 최종 앱 검증 뒤 기존 보호 소스 다섯 파일의 SHA256을 경로별로 비교해 모두 동일함을 확인했다. `TrackScene.kt`는 이 작업 트리에 없다. Git diff의 PNG 변경과 추적하지 않는 PNG 추가는 모두 0이며, `git diff --check`도 통과했다.
- 로컬 실행 로그 17개는 모든 원본/목적지 절대 경로를 작업 폴더 안으로 검증한 뒤 기존 파일을 덮어쓰지 않고 `backend/build/reports/review-306/`, `android/build/reports/review-306/`에 보존했다. 보호 포맷 스크립트 사본은 `android/build/reports/review-306/protected-format.init.gradle`이며 원본/사본 SHA256 일치를 확인한 뒤 임시 원본을 삭제했다.
- 브랜치를 유지하고 커밋, push, MR은 수행하지 않았다. 조정자가 작업 트리 변경을 확인한 뒤 커밋한다.
- 중단 후 재개에서 diff, 성공 로그와 시험 XML을 다시 대조했다. 운영 코드와 시험의 추가 변경 없이 기존 결과를 확인했으며 전체 검증은 반복하지 않았다. backend `./gradlew.bat --stop`으로 데몬 2개를 종료하고 android `./gradlew.bat --stop`으로 실행 중 데몬이 없음을 확인했다(모두 종료 코드 0). 316의 카운트다운 호출 연결은 계속 다른 담당에게 남긴다.
