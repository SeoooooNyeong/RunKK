# 병합 대기: feature/S15P31A207-247-ai-coach
> 작성: 2026-11-03 | 작성자: 박재영 | 스킬: sw-design review·sw-code-review·sw-test

## 고친 조각

- 신규 design-review/ai-coach-consent-박.md: 코치 하위 패키지 구조 검토.
- 신규 implementation/ai-coach-consent-박.md: 구현·검증·미연결 범위.
- 신규 test/ai-coach-consent-박.md: 동의·상태·화면 시험.
- 신규 code-review/ai-coach-consent-박.md: 설계·코드·취소·동의 검토.

## 새 임시 번호

| 임시 번호 | 종류 | 설명 | 위치 |
|---|---|---|---|
| [임시:D-박-1] | D | ui.record.coach 하위 패키지로 Fan-Out 7을 지킨다 | design-review/ai-coach-consent-박.md |
| [임시:TC-박-1]~[임시:TC-박-14] | TC | 동의·코치 시험 | test/ai-coach-consent-박.md |

## 공용 파일 반영

| 파일 | 요청 |
|---|---|
| design-oo 05-19 D-453·D-455, 5.1.2, 12.4 | ui.record.coach → domain.coach·core.designsystem·core.model 3쌍, domain.coach → core.model 1쌍. 폰 허용 표 150→154, 기존 ui.record 7 유지. 같은 feature/record의 생성 R 참조는 부모 코드 의존에서 제외 |
| design-oo 05-19 CoachFailure | CONSENT_REQUIRED·FEATURE_CLOSED 추가: 설계 3.1의 요청 전 거부 전달. 실제 HTTP/SSE 매핑은 #241 후속 |
| implementation.md·test.md·code-review.md·design-review.md | 새 조각의 목차 링크와 수행 결과. 전체 문서 버전·기존 판정은 여기서 변경하지 않음 |

## 다른 담당에게 요청

- 이성현: 회원 문서 FR-015는 이미 질문 원문까지 포함한다. 이번 앱 동의 안내를 확인하고 RM-20-3의 재동의 판을 확정할 때 서버와 앱 계약을 함께 정할 것.
- 공용 담당: 위키 SRS v0.22·설계 v0.27이 원격 문서 브랜치보다 앞선 상태다. 기존 공용 헤더를 이 브랜치에서 덮어쓰지 않았음.
- #241 연동 시: 실제 요청·SSE·목록 JSON 표본을 앱 계약 시험으로 고정한 뒤 Hilt·경로 연결. 이 브랜치에는 실제 어댑터와 제품 진입점이 없음.
