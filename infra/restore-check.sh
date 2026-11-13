#!/usr/bin/env bash
# 복구 시험(CI backup:restore-check, 수동). infra/ 폴더의 .env(RUNKK_ENV)로 그 환경의 가장 최근 매일 덤프를 골라
# 일회용 postgres 컨테이너에 복원하고, 운영 DB와 테이블 목록·행 수를 비교한다(sqa.md 8.1 필수 5, NFR-018·019).
#   통과 조건  매일 덤프가 26시간 안에 있음, pg_restore 오류 없음, 운영 DB와 테이블 목록이 같음, 복원한 행이 1개 이상
#   운영 DB    읽기만 한다(테이블마다 count(*)). 일회용 컨테이너는 망 없이 띄우고 끝나면(실패해도) 지운다
# 덤프 폴더(BACKUP_DIR/<환경>)는 0700·소유자 ubuntu라 러너 사용자가 직접 못 읽는다. 일회용 컨테이너에 읽기 전용으로 연결해 root로 읽는다.
set -euo pipefail
cd "$(dirname "$0")"
# shellcheck source=lib.sh
source ./lib.sh

load_app_env
BACKUP_HOST_DIR="$(env_value "$APP_ENV_FILE" BACKUP_DIR /var/backups/runkk)/$RUNKK_ENV"
CHECK="$APP_PROJECT-restore-check"
# 매일 덤프가 하루 한 번 뜨므로 2시간 여유를 둔다. 이보다 오래됐으면 매일 백업이 멈춘 것이다
MAX_AGE_HOURS="${RESTORE_CHECK_MAX_AGE_HOURS:-26}"

# -v: postgres 이미지가 만드는 익명 데이터 볼륨(복원한 운영 백업 사본)까지 지운다
cleanup() { docker rm -fv "$CHECK" >/dev/null 2>&1 || true; }
trap cleanup EXIT
cleanup

docker run -d --name "$CHECK" --network none \
  -e POSTGRES_PASSWORD="$(head -c 18 /dev/urandom | base64 | tr -d '/+=')" \
  -v "$BACKUP_HOST_DIR:/backups:ro" postgres:17-alpine >/dev/null
for _ in $(seq 60); do
  docker exec "$CHECK" pg_isready -U postgres >/dev/null 2>&1 && break
  sleep 1
done
docker exec "$CHECK" pg_isready -U postgres >/dev/null || die "restore-check postgres did not start"

# 배포 직전 덤프(-predeploy-)가 아니라 매일 덤프를 고른다. 매일 백업이 도는지도 함께 확인하는 시험이다
DUMP="$(docker exec "$CHECK" sh -c "ls -t /backups/$APP_PROJECT-daily-*.dump 2>/dev/null | head -n 1" || true)"
[ -n "$DUMP" ] || die "no daily dump in $BACKUP_HOST_DIR (is $APP_PROJECT-backup-1 running?)"
AGE_HOURS=$(( ( $(date +%s) - $(docker exec "$CHECK" stat -c %Y "$DUMP") ) / 3600 ))
echo "dump: $(basename "$DUMP") (${AGE_HOURS}h old, $(docker exec "$CHECK" du -h "$DUMP" | cut -f1))"
[ "$AGE_HOURS" -le "$MAX_AGE_HOURS" ] || die "newest daily dump is ${AGE_HOURS}h old (> ${MAX_AGE_HOURS}h). Check: docker logs $APP_PROJECT-backup-1"

docker exec "$CHECK" createdb -U postgres runkk
# 덤프에는 앱 계정(운영 DB의 POSTGRES_USER, 보통 runkk)의 ALTER DEFAULT PRIVILEGES와 ai 전용 계정 runkk_ai(V29, Flyway가 만듦),
# Grafana 이벤트 현황 읽기 전용 계정 runkk_grafana_ro(deploy.sh가 만듦, README 6절)에게 준 GRANT가 들어 있다. 빈 시험 컨테이너에는
# 이 계정들이 없어 pg_restore가 role ... does not exist로 멈추므로, 복원 전에 로그인 불가 계정으로 먼저 만든다
# (권한 복원까지 함께 확인한다). 앱 계정 이름은 운영 DB 컨테이너에서 읽는다
APP_DB_ROLE="$(docker exec "$APP_PROJECT-postgres-1" sh -c 'printf %s "$POSTGRES_USER"')"
[ -n "$APP_DB_ROLE" ] || die "cannot read POSTGRES_USER from $APP_PROJECT-postgres-1"
for role in "$APP_DB_ROLE" runkk_ai runkk_grafana_ro; do
  [ "$role" = postgres ] && continue
  docker exec "$CHECK" psql -U postgres -v ON_ERROR_STOP=1 -qc "CREATE ROLE \"$role\" NOLOGIN" >/dev/null
done
docker exec "$CHECK" pg_restore -U postgres -d runkk --no-owner --exit-on-error "$DUMP" || die "pg_restore failed for $DUMP"
echo "restore: ok"

# public 스키마 테이블마다 정확한 행 수(통계 추정치가 아니라 count(*))
COUNT_SQL="SELECT table_name, (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM public.%I', table_name), false, true, '')))[1]::text
FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY 1"
VERSION_SQL="SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"
restored_psql() { docker exec "$CHECK" psql -U postgres -d runkk -AtF ' ' -c "$1"; }
live_psql() { docker exec "$APP_PROJECT-postgres-1" sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -AtF " " -c "$1"' sh "$1"; }

# join이 읽도록 바이트 순서로 다시 정렬한다(DB 정렬 규칙은 밑줄을 다르게 다룬다)
RESTORED="$(restored_psql "$COUNT_SQL" | LC_ALL=C sort)"
LIVE="$(live_psql "$COUNT_SQL" | LC_ALL=C sort)"
RESTORED_VERSION="$(restored_psql "$VERSION_SQL")"
LIVE_VERSION="$(live_psql "$VERSION_SQL")"

# 덤프 뒤에 쌓인 자료가 있으므로 행 수는 운영 쪽이 같거나 많을 수 있다. 비교표만 남기고 판정에는 쓰지 않는다
printf '%-40s %10s %10s\n' table restored live
LC_ALL=C join -a 1 -a 2 -e '-' -o '0,1.2,2.2' <(echo "$RESTORED") <(echo "$LIVE") | while read -r table restored live; do
  printf '%-40s %10s %10s\n' "$table" "$restored" "$live"
done
TOTAL="$(echo "$RESTORED" | awk '{ s += $2 } END { print s + 0 }')"
echo "tables: restored $(echo "$RESTORED" | wc -l), live $(echo "$LIVE" | wc -l); rows restored $TOTAL; flyway restored $RESTORED_VERSION, live $LIVE_VERSION"

[ "$TOTAL" -gt 0 ] || die "restored database has no rows"
if [ "$RESTORED_VERSION" = "$LIVE_VERSION" ]; then
  [ "$(echo "$RESTORED" | cut -d' ' -f1)" = "$(echo "$LIVE" | cut -d' ' -f1)" ] || die "restored tables differ from live tables"
else
  # 덤프 뒤에 마이그레이션이 있는 배포가 있었다. 테이블 목록 차이는 그 때문일 수 있어 경고만 한다
  echo "WARN: flyway version differs (dump before a migration deploy); table list not compared" >&2
fi
echo "$RUNKK_ENV restore-check: ok"
