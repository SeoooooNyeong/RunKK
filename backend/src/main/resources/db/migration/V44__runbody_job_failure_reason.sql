-- 런바디 작업 거부 사유(MR !91 e2e 1). 출생연도가 없어 요청을 만들지 못한 작업(BIRTH_YEAR_MISSING)을 앱이 안내하고,
-- 회원이 출생연도를 넣으면 그 작업만 다시 낸다. 그 밖의 거부(ai 409·413·422 등)도 사유만 남긴다. 기존 행은 NULL이다.
ALTER TABLE runbody_job ADD COLUMN failure_reason VARCHAR(40);
