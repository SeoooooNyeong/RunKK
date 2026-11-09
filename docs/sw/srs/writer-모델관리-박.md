# Writer 모델 관리 계약

> 작성 2026-11-09 | v0.31(2026-11-09 병합, 7.13.26) | 입력 S15P31A207-243·197, FR-268~271, 운영자 결정(CR-179)

- 모델 관리 대상은 런바디 분석 문구를 작성하는 writer다. 운영자는 기존 관리 인증으로 목록·등록·활성화·롤백을 요청한다.
- 첫 모델은 등록과 함께 활성화한다. 같은 ID를 다시 등록하면 주소·메모를 갱신하고 현재 활성 여부를 보존한다. 다른 새 모델은 활성 모델을 바꾸지 않는다.
- 목록은 모델 배열과 활성 모델 ID를 반환한다. 모델 값은 ID, 주소, 메모, 활성 여부, 등록·활성화 시각이다.
- 운영자 등록으로 전달할 주소는 배포 관리자가 설정한 writer URL로 제한한다. 활성화·롤백은 AI 서버에 이미 등록된 모델을 대상으로 한다.

| 관리 경로 | AI 경로 | 성공 |
|---|---|---|
| GET /api/v1/admin/models | GET /models | 200 |
| POST /api/v1/admin/models | POST /models | 201 |
| POST /api/v1/admin/models/{id}/activate | POST /models/{id}/activate | 200 |
| POST /api/v1/admin/models/rollback | POST /models/rollback | 200 |

- 등록 본문은 `modelId`, `baseUrl`, 선택 `note`다. 활성화 대상 없음은 404 MODEL_NOT_FOUND, 직전 모델 없음은 409 NO_PREVIOUS_MODEL, AI 통신·계약 오류는 503 MODEL_REGISTRY_UNAVAILABLE이다.
- 운영자와 변경 종류를 호출 전에 기록하고, 성공을 확인하면 대상 모델과 성공도 기록한다. 내부 토큰·writer URL·메모는 감사 기록에 담지 않는다.
