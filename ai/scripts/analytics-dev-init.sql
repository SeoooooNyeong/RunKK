-- 로컬 개발용: analytics 스키마와 runkk_ai 계정을 만든다(EC2에서는 백엔드 Flyway가 같은 일을 한다).
-- DB 관리자(로컬 compose의 runkk 계정)로 한 번 실행한다. 다시 실행해도 된다.
--   docker compose -f backend/compose.yaml up -d postgres
--   docker compose -f backend/compose.yaml exec -T postgres psql -U runkk -d runkk < ai/scripts/analytics-dev-init.sql
-- 비밀번호 runkk_ai는 로컬 전용이다. 운영 비밀번호는 .env의 RUNKK_AI_DB_PASSWORD로만 넣는다.
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'runkk_ai') THEN
        CREATE ROLE runkk_ai LOGIN PASSWORD 'runkk_ai';
    END IF;
END
$$;

CREATE SCHEMA IF NOT EXISTS analytics AUTHORIZATION runkk_ai;
GRANT CONNECT ON DATABASE runkk TO runkk_ai;

-- 백엔드 테이블(public)은 읽기만 한다
GRANT USAGE ON SCHEMA public TO runkk_ai;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO runkk_ai;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO runkk_ai;
REVOKE CREATE ON SCHEMA public FROM runkk_ai;
