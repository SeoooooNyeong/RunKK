#!/usr/bin/env bash
# Build the api (../backend) and ai (../ai) images and (re)start the whole stack.
# Run from the infra/ directory on the server:  ./deploy.sh
# Fails (exit 1) if api or ai does not become healthy. api keeps serving fallback text while ai is down,
# so an unhealthy ai would otherwise go unnoticed (e.g. a placeholder RUNKK_INTERNAL_TOKEN makes ai refuse to start).
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f .env ]; then
  echo ".env not found. Copy .env.example to .env and fill in the values." >&2
  exit 1
fi

compose() { docker compose --env-file .env "$@"; }

# .env는 compose만 읽는다. 스크립트가 치는 포트는 같은 파일에서 읽는다(값에 공백·괄호가 있어도 안전하게 이 줄만 뽑는다).
# 키는 선택값(compose는 ${NGINX_HTTP_PORT:-80})이다. 줄이 없으면 grep이 1을 돌려 set -euo pipefail 아래에서 스크립트가
# 메시지 없이 끝나므로 `|| true`로 빈 값을 받고 기본값을 쓴다(코드 리뷰 3차 I-M1).
env_value() {  # <key> <default>
  local value
  value="$(grep -E "^$1=" .env | tail -n 1 | cut -d= -f2- | sed 's/[[:space:]]*#.*//' | tr -d ' \r"' || true)"
  echo "${value:-$2}"
}
NGINX_HTTP_PORT="$(env_value NGINX_HTTP_PORT 80)"
NGINX_HTTPS_PORT="$(env_value NGINX_HTTPS_PORT 443)"
SERVER_NAME="$(env_value SERVER_NAME localhost)"

# Prints "healthy" only for a healthy container ("unhealthy" must not match).
wait_healthy() {  # <service> <tries, 5 s apart>
  for _ in $(seq 1 "$2"); do
    if [ "$(compose ps "$1" --format '{{.Health}}')" = "healthy" ]; then
      echo "$1 is healthy"
      return 0
    fi
    sleep 5
  done
  echo "$1 did not become healthy. Logs:" >&2
  compose logs --tail=100 "$1" >&2
  return 1
}

# 인증서는 letsencrypt 볼륨에 있다(init-letsencrypt.sh가 1회 발급). 인증서가 있으면 nginx/https 설정 폴더를 고른다(NGINX_MODE).
# 예전처럼 저장소 파일을 덮어쓰면 다음 CI 잡이 작업 폴더를 다시 받을 때 HTTP 설정으로 돌아가고, 그 뒤 nginx가 재시작되면 443이 닫혔다.
if compose run --rm --no-deps --entrypoint test certbot -f "/etc/letsencrypt/live/${SERVER_NAME}/fullchain.pem" 2>/dev/null; then
  export NGINX_MODE=https
  # HTTPS면 80은 301만 돌려주므로 443을 인증서 이름으로 직접 부른다(--resolve로 이 서버에 붙고 인증서도 검증한다)
  HEALTH_CURL=(curl -fsS --resolve "${SERVER_NAME}:${NGINX_HTTPS_PORT}:127.0.0.1" "https://${SERVER_NAME}:${NGINX_HTTPS_PORT}/actuator/health")
else
  export NGINX_MODE=http
  HEALTH_CURL=(curl -fsS "http://localhost:${NGINX_HTTP_PORT}/actuator/health")
fi

# 새 api가 Flyway로 되돌릴 수 없는 마이그레이션(예: V20의 DROP COLUMN member.tags·crew_membership.crew_tags)을 돌리기 전에
# postgres를 덤프해 둔다(BE5-A-15). 덤프가 실패하면 배포를 멈춘다.
# - 보관 위치는 CI 작업 폴더 밖이다(BE6-1). shell 러너는 다음 잡 시작 때 git clean으로 작업 폴더의 무시 파일까지 지우므로
#   infra/backups/에 두면 다음 파이프라인에서 사라진다. RUNKK_BACKUP_DIR(환경 변수 > .env > 기본 $HOME/runkk-backups,
#   절대 경로)에 쓰고 그 폴더 기준으로 최근 7개만 남긴다.
# - 덤프에는 위치·토큰 해시가 들어 있으므로 폴더 700, 파일 600으로 만든다(BE6-2).
# - postgres가 멈춰 있어도 데이터 볼륨이 있으면 postgres만 먼저 올려 덤프한다. 볼륨도 없을 때(처음 배포)만 건너뛴다(BE6-3).
# 복구: gunzip -c <RUNKK_BACKUP_DIR>/<파일> | docker compose exec -T postgres psql -U <DB_USER> <DB_NAME>
BACKUP_KEEP=7

backup_dir() {
  local dir="${RUNKK_BACKUP_DIR:-}"
  [ -n "$dir" ] || dir="$(env_value RUNKK_BACKUP_DIR "")"
  [ -n "$dir" ] || dir="${HOME:?HOME is not set; set RUNKK_BACKUP_DIR}/runkk-backups"
  case "$dir" in
    /*) echo "$dir" ;;
    *)
      echo "RUNKK_BACKUP_DIR must be an absolute path outside the CI work folder: $dir" >&2
      return 1
      ;;
  esac
}

# compose 프로젝트 이름(docker-compose.yml의 name, COMPOSE_PROJECT_NAME이 있으면 그 값)의 postgres 데이터 볼륨이 있는가.
# 0 있음, 1 없음, 2 확인 실패(Docker에 물을 수 없음). 확인 실패를 "없음"으로 보면 백업 없이 마이그레이션이 돌 수 있어 나눈다.
postgres_volume_exists() {
  local project volumes
  project="$(compose config | sed -n 's/^name: *//p' | head -n 1)" || return 2
  [ -n "$project" ] || return 2
  volumes="$(docker volume ls -q)" || return 2
  grep -Fxq "${project}_postgres-data" <<< "$volumes"
}

backup_database() {
  local dir db_user db_name revision file
  dir="$(backup_dir)"
  if [ -z "$(compose ps --status running --quiet postgres)" ]; then
    local volume_status=0
    postgres_volume_exists || volume_status=$?
    if [ "$volume_status" -eq 1 ]; then
      echo "No postgres data volume yet (first deploy); skipping the pre-deploy backup."
      return 0
    elif [ "$volume_status" -ne 0 ]; then
      echo "Could not check the postgres data volume; aborting the deploy before the migrations run." >&2
      return 1
    fi
    echo "postgres is stopped but its data volume exists; starting postgres alone for the pre-deploy backup."
    if ! compose up -d --no-deps postgres; then
      echo "Could not start postgres for the backup; aborting the deploy before the migrations run." >&2
      return 1
    fi
  fi
  if ! wait_healthy postgres 12; then
    echo "postgres is not healthy; aborting the deploy before the migrations run." >&2
    return 1
  fi

  db_user="$(env_value DB_USER runkk)"
  db_name="$(env_value DB_NAME runkk)"
  revision="${CI_COMMIT_SHORT_SHA:-$(git rev-parse --short HEAD 2>/dev/null || echo unknown)}"
  mkdir -p "$dir"
  chmod 700 "$dir"
  file="$dir/pre-deploy-$(date +%Y%m%d-%H%M%S)-${revision}.sql.gz"
  # umask 077이라 gzip이 만드는 파일은 처음부터 600이다(다른 계정이 읽을 틈이 없다)
  if ! (umask 077 && compose exec -T postgres pg_dump -U "$db_user" "$db_name" | gzip > "$file"); then
    rm -f "$file"
    echo "pg_dump failed; aborting the deploy before the migrations run." >&2
    return 1
  fi
  chmod 600 "$file"
  echo "Database backup written to $file"
  local old=()
  # 이름은 pre-deploy-<시각>-<sha>.sql.gz로 고정이라 줄 단위로 읽어도 안전하다(폴더 경로에 공백이 있어도 된다)
  # shellcheck disable=SC2012
  mapfile -t old < <(ls -1t "$dir"/pre-deploy-*.sql.gz | tail -n +$((BACKUP_KEEP + 1)))
  if [ "${#old[@]}" -gt 0 ]; then
    rm -f -- "${old[@]}"
  fi
}

backup_database
compose pull nginx postgres redis
compose build api ai
compose up -d --remove-orphans
compose ps

echo "Waiting for the api and ai health checks..."
wait_healthy api 30
"${HEALTH_CURL[@]}" && echo
wait_healthy ai 12
