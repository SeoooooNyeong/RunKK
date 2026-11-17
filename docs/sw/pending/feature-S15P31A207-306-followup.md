# 병합 대기: feature/S15P31A207-306-followup

> 작성: 2026-11-16 | 작성자: 이성현(성) | 스킬: sw-code-review, sw-test, sw-sqa | 기준: f856903f 작업 트리

## 고친 조각

- `code-review/28-실기기수정후속-성.md`: 끝의 「수정 확인」에 AR-322~326의 파일과 시험을 연결했다.
- `test/실기기수정-성.md`: 기존 TC-374/375와 TC-11000~11008 대응표, 후속 검사 기준과 실행 결과, NFR-성-7 기기 측정 절차를 갱신했다.
- `sqa/실기기수정-성.md`: DF-95/96을 부분 수정, 열림으로 유지했다. 측면 내부 투명 픽셀 검사 실패와 기기 지연 미측정을 구별한다.

## 새 임시 번호

없음. 기존 AR/DF/TC/FR/NFR을 재사용하고 정식 번호와 문서 버전은 바꾸지 않았다.

## 공용 파일 반영

| 파일 | 넣을 내용 |
|------|-----------|
| 요구/설계/시험 추적표 | AR-322~326의 인터페이스와 시험 보강, TC-11002/11003/11004/11005/11007/11008의 직접 시험을 연결. 측면 픽셀 실패를 FR-성-349 합격으로 표시하지 않음 |
| SRS의 NFR-성-8 배경 수 | SRS에는 27장, 실제 선택 가능한 무대는 도시 22+단계 3+기본 1=26종이다. 새 시험은 낮/밤 52리소스와 폭 3개를 검사한다. 조정자가 개수 기준을 정한 뒤 반영 |
| 계획의 R-089 | 그림에서 잰 새 HeadTarget ±3px 회귀 추가. 실제 생성 여자 셀카 통합과 기기 인수는 남아 있어 위험 종료로 반영하지 않음 |

## 다른 담당에게 요청

- 조정자: NFR-성-7은 시험 문서 11.3절에 따라 Galaxy S22 실제 랜덤 대결 20회를 측정한다. **조정자 측정 예정**이며 현재 측정값은 없다.
- 조정자: 여자 측면 접합부의 완전 투명 픽셀 검사 실패를 확인한다. 이번 지시대로 그림을 수정하지 않았다. 상세 좌표와 개수는 시험 11.2절에 기록한다.
- 조정자: 실제 생성 셀카 TC-11015, 전체 렌더 캡처와 기존 L-18 인수 범위는 별도로 확인한다.

## 조정자가 git add할 파일

아래 18개만 변경 대상이다. `.gradle/followup/`은 로컬 검증 설정과 로그이며 추가 대상이 아니다. 커밋, push, MR은 수행하지 않았다.

```text
android/core/ui/src/main/java/com/a207/runkk/app/core/designsystem/RunkkTrackCard.kt
android/core/ui/src/main/java/com/a207/runkk/app/core/designsystem/TrackRunnerGeometry.kt
android/core/ui/src/main/java/com/a207/runkk/app/core/designsystem/TrackRunnerShadow.kt
android/core/ui/src/main/java/com/a207/runkk/app/core/designsystem/TrackRunnerLabel.kt
android/feature/avatar/src/main/java/com/a207/runkk/app/ui/avatar/RunAvatarSlotContent.kt
android/feature/avatar/src/main/java/com/a207/runkk/app/ui/avatar/TrackRunAvatarFigure.kt
android/core/avatar/src/test/kotlin/com/a207/runkk/app/core/avatar/BuiltInHeadPixels.kt
android/core/avatar/src/test/kotlin/com/a207/runkk/app/core/avatar/FemaleBuiltInFaceTest.kt
android/core/ui/src/test/kotlin/com/a207/runkk/app/core/designsystem/TrackCardContractTest.kt
android/core/ui/src/test/kotlin/com/a207/runkk/app/core/designsystem/ResultStageBackdropFilesTest.kt
android/core/ui/src/test/kotlin/com/a207/runkk/app/core/designsystem/TrackSceneTest.kt
android/feature/avatar/src/test/kotlin/com/a207/runkk/app/ui/avatar/AvatarSlotsTest.kt
android/feature/avatar/src/test/kotlin/com/a207/runkk/app/ui/avatar/TrackRunAvatarFrameTest.kt
android/feature/chat/src/test/kotlin/com/a207/runkk/app/ui/chat/ReactorGenderTest.kt
docs/sw/code-review/28-실기기수정후속-성.md
docs/sw/test/실기기수정-성.md
docs/sw/sqa/실기기수정-성.md
docs/sw/pending/feature-S15P31A207-306-followup.md
```

## 검증

최종 명령, 건수와 실패 사유는 `test/실기기수정-성.md` 11.2절에 둔다. Gradle은 `--max-workers=2 --no-parallel`로 하나씩 실행한다. 공용 Maven 잠금 파일 쓰기 실패 뒤 캐시된 Robolectric SDK 35/36 jar를 작업 폴더 `.gradle/followup/robolectric`로 복사해 오프라인 실행했다. Kotlin 컴파일도 `-Pkotlin.compiler.execution.strategy=in-process`로 실행해 공용 daemon 임시 파일 쓰기를 피했다.

최종 포맷/ktlint/lint 통과. 20모듈 시험 3,647개 중 통과 3,645, 실패 2, 오류/건너뜀 0. 실패는 측면 내부 투명 0 요구이며 볼 0/1/2에서 원본 합성 14/12/9개, 실제 변환 피부 범위의 목 등록 합성 8/6/3개다. 외곽 분류 픽스처, 반응자 8조건, 목표 ±3px와 순수 좌표 전수는 통과했다. 마지막 `./gradlew.bat --stop` exit 0. 그림/스냅숏 변경, 커밋/push/MR 없음.
