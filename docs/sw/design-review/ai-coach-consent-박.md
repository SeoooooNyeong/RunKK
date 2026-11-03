# AI 코치 화면 변경 범위 설계 검토 (다음 판 변경)
> 작성: 2026-11-03 | 작성자: 박재영 | 입력: 위키 SRS v0.22·설계 v0.27, 05-19 D-434~D-455, 저장소 c0e0eb3f | 스킬: sw-design review

## 범위와 기준

검토 범위는 #247 동의 안내와 #246 앱 대화 상태·화면 준비다. 서버 #241, 실제 SSE 데이터 어댑터, 운영 공개는 포함하지 않는다. 기존 설계 검증 v0.19의 전체 판정을 바꾸지 않는다.

기준: Fan-Out 7 이하, 순환 0, 계층 역행 0, 결합도 스탬프 이하, 응집도 통신적 이상. 같은 Gradle 모듈의 생성 R 참조는 코드 패키지 의존과 구분한다.

## 변경 전후

05-19 D-455대로 domain.coach를 ui.record에 직접 더하면 기존 CORE_UI 3 + 업무 의존 4 + coach 1 = 8이다. 따라서 ui.record.coach 하위 패키지로 분리한다. 같은 feature/record 모듈 안이며 클래스 이름은 D-453과 같다.

| 패키지 | 책임 | 변경 후 Fan-Out | 응집 |
|---|---|---:|---|
| ui.record | 기존 기록·분석·계획 화면 | 7, 변화 없음 | 통신적, 기존 판정 유지 |
| ui.record.coach | AI 대화 상태와 표시 | 3 | 통신적 |
| domain.coach | 질문·스트림·대화 색인 계약 | 1 | 통신적 |

| 화살표 | 주고받는 것 | 결합도 | 판정 |
|---|---|---|---|
| ui.record.coach → domain.coach | CoachEvent, CoachConversation, ask(id?, text), conversations() | 스탬프 | 허용 |
| ui.record.coach → core.designsystem | 화면 컴포넌트·UiText | 스탬프 | 허용 |
| ui.record.coach → core.model | Outcome 성공·실패 | 스탬프 | 허용 |
| domain.coach → core.model | Outcome | 스탬프 | 허용 |

새 화살표 4, 폰 허용 표 150→154, 워치 5 유지. 순환·공통 가변 상태·도메인의 Android 의존은 없다. 상위 ui.record가 코치의 형식이나 상태를 import하지 않는다. ui.record.coach가 ui.record.R을 읽는 것은 같은 Android 모듈의 생성 리소스이며 부모 코드 접근은 허용하지 않는다.

## 결정과 남은 경계

- [임시:D-박-1] D-453의 앱 배치를 ui.record 하위 coach 패키지로 좁힌다. D-455·5.1.2·12.4의 공용 반영은 pending으로 요청한다.
- CoachFailure에 CONSENT_REQUIRED·FEATURE_CLOSED를 추가해 설계 3.1의 HTTP 거부를 화면에 전달할 자리를 마련했다. RETRY·QUOTA·EXPIRED·TOO_LONG은 그대로다. JSON·HTTP 매핑은 아직 구현하지 않았다.
- 질문 상한은 FR-261의 제안 500 코드 포인트를 화면 초안 검증값으로 사용한다. 계약 시험에서 서버와 맞춰야 한다.
- D-438 재동의 버전은 제안이므로 저장·조회·강제 재동의를 추가하지 않는다.
- D-451 제품 경로·RUNBODY 진입점과 실제 DI는 데이터 어댑터 및 #241 계약 검증 뒤 연결한다. 공개되는 가짜 저장소는 없다.

판정: 이 변경 범위의 구조안은 허용. 실제 ArchitectureTest 18건, 전체 포맷·린트·단위 게이트가 통과했다. 최대 Fan-Out 7, 허용 표 폰 154·워치 5, 표 밖 import 0 조건을 시험으로 확인했다. 전체 설계 v0.27의 합격 판정이 아니다.
