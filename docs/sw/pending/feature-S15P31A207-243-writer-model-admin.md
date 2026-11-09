# 병합 대기: feature/S15P31A207-243-writer-model-admin

> 작성: 2026-11-09 | 작성자: 박재영 | 스킬: sw-srs·sw-design-oo·sw-test·sw-code-review

## 고친 조각

- [요구사항](../srs/writer-모델관리-박.md), [설계](../design-oo/writer-모델관리-박.md), [시험](../test/writer-모델관리-박.md), [코드 리뷰](../code-review/writer-모델관리-박.md)

## 새 임시 번호

- [임시:CR-박-1]은 #243 사용자 확인으로 정한 writer 관리 범위와 등록 동작에 대응한다.

## 공용 파일 반영

| 위치 | 반영 요청 |
|---|---|
| srs/03-07 FR-268~271 | [임시:CR-박-1]을 연결한다. 런바디 분석 문구용 writer의 관리 API로 범위를 적고, 첫 등록 자동 활성화와 같은 ID 재등록 시 주소·메모 갱신을 적는다. |
| 설계 D-446, 관리 API와 서버 패키지 표 | `/api/v1/admin/models` 네 연산, `aimodel`·`web.admin.model`과 `infra.integration.ai` 연결, 배포 허용 URL을 반영한다. |
| 감사 자료사전·추적표 | V47의 WRITER_MODEL_REGISTER·ACTIVATE·ROLLBACK 및 REQUESTED·SUCCEEDED를 반영한다. |

## 다른 담당에게 요청

- 운영 담당은 두 번째 writer를 운영할 때 그 주소를 `RUNKK_WRITER_MODEL_URLS`에 추가한다. 이 설정은 관리 API 등록 시 적용한다.
