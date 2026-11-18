-- v0.14 [임시:D-성-972] 재평가가 바꾸기 전 티어. TierApplier가 평가 표시와 같은 조건부 갱신으로 한 번 쓴다.
-- 열을 더하기 전 기록은 NULL(앱은 결과 문장을 "UNKNOWN"으로 고른다). 값은 common.Tier 이름(evaluation_tier와 같은 길이).
ALTER TABLE run_record ADD COLUMN evaluation_previous_tier VARCHAR(10);
