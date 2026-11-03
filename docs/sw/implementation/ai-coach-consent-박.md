# LLM 동의·AI 코치 화면 구현 기록 (다음 판 변경)
> 작성: 2026-11-03 | 작성자: 박재영 | 입력: 위키 SRS v0.22·설계 v0.27, Jira #247·#246 | 스킬: sw-code-review·sw-test·sw-design review

## 상태

작업 트리: Codex 관리 ai-coach-consent/runkk. 브랜치 feature/S15P31A207-247-ai-coach, 병합 기준 develop c29df9fa. 문서 참고는 c0e0eb3f(origin/chore/sw-docs-skills)이지만 문서 이전 커밋은 이번 MR 이력에서 제외한다. D:/runkk의 feature/character-preview 변경은 가져오거나 수정하지 않는다.

이 기록은 #247 구현과 #246의 계약·상태·화면 준비를 다룬다. #246 전체 완료나 서비스 공개를 뜻하지 않는다. 사용자 지시 ‘위키 기준으로 진행할 수 있는 것’을 범위로 삼았다. 제품 코드에 가짜 응답·인증 우회를 넣지 않았다.

## 구현

- 가입·게스트 화면에 집계값과 질문 원문 외부 전송, 처리위탁·국외 이전, 미동의 시 규칙 코멘트·코치 사용 불가를 안내한다.
- 동의 설정에서 켤 때 같은 고지를 확인하고 확정 뒤 저장한다. 취소는 저장하지 않고, 철회는 기존 저장 계약을 쓴다.
- 동의 키·기본 정책·서버 모델은 바꾸지 않는다. 재동의 버전은 RM-20-3 확정 전이라 추가하지 않았다.
- domain.coach의 CoachRepository·CoachEvent·CoachConversation·CoachFailure 계약을 추가한다.
- CoachChatViewModel은 답 조각 연결, 완료·실패, 빈 답 거부, 재시도, 전송 중 연타 방지, 새 대화·이전 대화 선택, 연결 취소를 처리한다.
- CoachChatScreen은 공용 디자인 시스템을 사용한 상태 없는 화면이다. 실제 API 어댑터·Hilt 바인딩·런바디 진입 경로는 연결하지 않았다.
- ui.record.coach 하위 패키지로 Fan-Out 상한을 지킨다. 근거는 design-review/ai-coach-consent-박.md와 pending에 있다.

## 시험·검증 이력

1. 동의 UI 시험을 먼저 추가하고 기존 구현에서 4건 실패를 확인했다(전체 119건 중 신규 4건).
2. 코치 상태 시험을 먼저 추가하고 미구현 클래스·계약으로 컴파일 실패를 확인했다. 이는 동작 시험 실패와 구분한다.
3. 단위·Compose·구조·포맷·린트 게이트 결과는 아래에 실행 후 기록한다.

최종 검증: Android 전체 게이트 통과.

| 실행 | 결과 |
|---|---|
| `./gradlew.bat ktlintFormat` | 통과 |
| `./gradlew.bat ktlintCheck lintLocalDebug testLocalDebugUnitTest` | 통과, 6분 44초 |
| 전체 JUnit XML 집계 | 1,877건, 실패 0·오류 0·건너뜀 0 |
| ArchitectureTest | 18건 통과, Fan-Out 7·표 밖 import 0 조건 유지 |
| 동의·기록 모듈 회귀 | 119건·106건 통과 |
| 신규 시험 | 동의 4건, 코치 상태 8건, 코치 UI·스크린샷 7건 |

환경은 JDK 21(D:/java/jdk-21.0.12.1+1), 기존 Android SDK의 Platform 37.0이다. 환경 변수는 검증 프로세스에만 지정했고 local.properties는 만들지 않았다. 실제 기기·TalkBack·서버 SSE·NFR 성능은 미실행이다.

Roborazzi 기록: 기존 가입(다크·라이트)·게스트·동의 설정 4장 갱신, 신규 record_ai_coach.png 1장 추가. 코치·가입 화면을 이미지로 열어 기본 글자 크기에서 잘림 없이 표시되는 것을 확인했다. 새 코치 화면은 디자인 #265 확정본과 대조한 결과가 아니라 기존 컴포넌트로 만든 초안이다.

기록 뒤 같은 5개 화면을 verifyRoborazziDebug로 다시 비교했고 통과했다(25초). 전체 게이트 이후 제품 코드는 바꾸지 않았으며, 검증 기록 문서만 보완했다.

## 다음 연결

#241: POST /api/v1/coach/questions의 요청·SSE 자료와 GET /api/v1/coach/conversations의 실제 JSON 표본을 고정하고 데이터 어댑터를 구현한다. Failed의 CONSENT_REQUIRED·FEATURE_CLOSED는 요청 전 HTTP 거부를 전달한다. 스트림 tool은 표시하지 않는다. 서버가 끝난 뒤에도 앱이 끊으면 요청 취소가 전파되는지 계약 시험이 필요하다.

#246 후속: Hilt 저장소 바인딩, 런바디 안 진입·기존 RUNBODY 잠금, 화면 수명과 전송 취소 연결, 디자인 #265 대조. 이전 대화 본문 조회 계약은 없어 목록 선택 시 기존 문답을 복원하지 않고 ID로 다음 질문만 이어 간다.

추가 환경 변수·비밀 파일·의존성은 없다. 2026-11-03 사용자 후속 요청으로 이슈별 커밋과 develop 대상 Draft MR 등록을 진행한다. 병합·배포는 범위에 넣지 않는다. MR의 자동 종료 대상은 #247만이고, 실제 연동이 남은 #246은 관련 이슈로 연결한다. 두 Jira 이슈는 진행 중이다.
