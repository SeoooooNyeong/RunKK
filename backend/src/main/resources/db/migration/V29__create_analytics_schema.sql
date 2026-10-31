-- ai 분석 결과 저장소(S15P31A207-172, 상위 -163). analytics 스키마와 ai 전용 계정 runkk_ai만 만든다.
-- analytics 안의 테이블(session_result·analysis_event·v_session_result·coach_message·llm_model)은 ai가 기동 때
-- Alembic(alembic upgrade head)으로 직접 관리한다. 같은 테이블을 양쪽에서 건드리지 않도록 Flyway는 테이블을 만들지 않는다.
-- 비밀번호는 Flyway 플레이스홀더 runkk_ai_password(application.yml spring.flyway.placeholders, 환경 변수 RUNKK_AI_DB_PASSWORD)로 받는다.
-- 따옴표가 들어가면 SQL이 깨지므로 영숫자 값(예: openssl rand -hex 24)을 쓴다. 계정이 이미 있으면 비밀번호를 바꾸지 않는다.
-- CREATE ROLE은 Flyway를 돌리는 계정(DB_USER, postgres 이미지의 POSTGRES_USER라 슈퍼유저)이 한다.
CREATE SCHEMA IF NOT EXISTS analytics;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'runkk_ai') THEN
    CREATE ROLE runkk_ai LOGIN PASSWORD '${runkk_ai_password}';
  END IF;
END
$$;

-- analytics는 runkk_ai가 소유하고 모든 작업을 한다(Alembic 버전 표 alembic_version도 여기에 둔다)
ALTER SCHEMA analytics OWNER TO runkk_ai;
GRANT ALL ON SCHEMA analytics TO runkk_ai;

-- public은 읽기만 한다(2026-10-29 결정: 이후 run_record 등을 ai가 직접 참조할 수 있게 전체 SELECT).
-- 기본 권한은 이 마이그레이션을 돌리는 계정(DB_USER)이 앞으로 만드는 public 테이블에 적용된다
GRANT USAGE ON SCHEMA public TO runkk_ai;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO runkk_ai;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO runkk_ai;
