# 병합 대기 — feature/S15P31A207-244-ai-privacy

> 작성: 2026-11-04 | 작성자: 박재영 | 스킬: sw-maintain, sw-srs, sw-design-oo, sw-code-review, sw-test

## 변경 조각

- maintenance/2026-11-04_AI파기연결-박.md
- srs/ai-자료파기-박.md
- design-oo/ai-자료파기-박.md
- implementation/ai-자료파기-박.md
- code-review/ai-자료파기-박.md
- test/ai-자료파기-박.md

## 공용 반영 요청

FR-021·FR-272 추적과 D-447에 탈퇴 직후·실패 매일·7일 재확인·경과 실패 감사·기존 탈퇴자 누락 보정을 반영한다. D-449의 privacy 읽기 제한은 AI 10초보다 긴 15초로 보정한다. 감사 자료사전에 AI_DATA_PURGE_OVERDUE를 더한다.

패키지 목록과 의존 표에는 aiprivacy → member·audit·common, infra.aiprivacy → aiprivacy, infra.integration.ai → aiprivacy, batch.aiprivacy → aiprivacy를 반영한다. 새 TC 임시 번호를 정식으로 매기고 공용 목차·버전을 갱신한다.

## 후속 확인

운영 배치와 실제 AI·Node 삭제는 MR 승인 뒤 확인한다. #257의 Node 삭제 미지원 기록은 AI !74 및 이 연결의 검증 결과에 맞춰 공용 담당이 갱신한다.

## 최종 검증 조각

- design-review/ai-자료파기-박.md
- sqa/ai-자료파기-박.md

backend build 1,808건 중 실행 1,653건 통과·Docker 의존 155건 건너뜀. 기존 감사 행위 고정 목록은 새 행위를 포함한 20개로 갱신했다.
