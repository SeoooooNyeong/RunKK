-- Grafana 이벤트 현황 대시보드용 읽기 전용 DB 계정 runkk_grafana_ro(S15P31A207-275, infra/README.md 6절 "이벤트 현황").
-- deploy.sh가 환경 배포마다 api가 healthy가 된 뒤(Flyway가 끝난 뒤) 그 환경 postgres 컨테이너 안에서 psql로 돌린다. 몇 번 돌려도 결과가 같다.
-- 비밀번호는 환경 변수 GRAFANA_DB_READONLY_PASSWORD(CI masked 변수 → edge.env)에서 \getenv로 읽는다. 이 파일과 명령줄에는 값이 없다.
-- deploy.sh가 영숫자 16자 이상인지 먼저 확인하므로 따옴표가 들어오지 않는다.
--
-- 권한은 대시보드 쿼리가 읽는 표와 열만 SELECT로 준다. 회원은 ID, 닉네임, 상태만, 러닝 기록은 경로와 심박 없이 집계에 쓰는 열만 연다.
-- 기본 권한(ALTER DEFAULT PRIVILEGES)은 주지 않는다. 앞으로 생길 표가 저절로 열리지 않게 하고, 배포마다 아래 목록으로 다시 맞춘다
-- (마이그레이션이 표를 다시 만들어도 같은 배포에서 권한이 돌아온다). 표나 열을 더 읽어야 하면 이 파일에 적고 설계(design-oo 05-29)를 고친다.
\set ON_ERROR_STOP on
\getenv ro_password GRAFANA_DB_READONLY_PASSWORD

SELECT 'CREATE ROLE runkk_grafana_ro'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'runkk_grafana_ro') \gexec

-- 로그인만 하고 아무 특권이 없다. 연결은 3개까지(Grafana 데이터소스 최대 2개 + 여유)
ALTER ROLE runkk_grafana_ro WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS
    CONNECTION LIMIT 3 PASSWORD :'ro_password';
-- 계정 기본 설정: 읽기 전용 트랜잭션, 쿼리 15초, 트랜잭션 안 대기 30초. 세션에서 바꿀 수는 있으므로 실제 보호는 아래 권한이 한다
ALTER ROLE runkk_grafana_ro SET default_transaction_read_only = on;
ALTER ROLE runkk_grafana_ro SET statement_timeout = '15s';
ALTER ROLE runkk_grafana_ro SET idle_in_transaction_session_timeout = '30s';

BEGIN;
-- 전에 준 표·열 권한을 모두 거두고(표 권한을 거두면 그 표의 열 권한도 거둬진다) 목록대로 다시 준다
SELECT format('REVOKE ALL ON TABLE %I.%I FROM runkk_grafana_ro', table_schema, table_name)
FROM information_schema.role_table_grants WHERE grantee = 'runkk_grafana_ro'
UNION
SELECT format('REVOKE ALL ON TABLE %I.%I FROM runkk_grafana_ro', table_schema, table_name)
FROM information_schema.role_column_grants WHERE grantee = 'runkk_grafana_ro' \gexec

SELECT format('GRANT CONNECT ON DATABASE %I TO runkk_grafana_ro', current_database()) \gexec
GRANT USAGE ON SCHEMA public TO runkk_grafana_ro;

-- 이벤트 정의와 포인트 지급 이력(개인정보 없음)
GRANT SELECT ON TABLE event, event_point_grant TO runkk_grafana_ro;
-- 셀럽 대결: 판정에 쓰는 열만
GRANT SELECT (id, type, status, opponent_kind, opponent_record_id, judged_at, winner_member_id) ON TABLE battle TO runkk_grafana_ro;
-- 1km 응모: 경로(route_data)와 심박은 열지 않는다
GRANT SELECT (record_id, member_id, started_at, distance_meters, valid) ON TABLE run_record TO runkk_grafana_ro;
-- 회원: 닉네임까지만(탈퇴 회원을 빼려고 상태도 읽는다)
GRANT SELECT (id, nickname, status) ON TABLE member TO runkk_grafana_ro;
-- 코치(셀럽) 이름: 가림 이름 열만
GRANT SELECT (id, anonymous_name) ON TABLE celebrity_record TO runkk_grafana_ro;
-- 시각 자동 보정: 서버 시간대 수정 V48이 적용된 DB인지만 본다
GRANT SELECT (version, success) ON TABLE flyway_schema_history TO runkk_grafana_ro;
COMMIT;
