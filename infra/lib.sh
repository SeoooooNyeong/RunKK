#!/usr/bin/env bash
# deploy.sh·smoke.sh·init-letsencrypt.sh가 source하는 공통 함수. 직접 실행하지 않는다.
# 호출하는 쪽이 infra/ 폴더에서 set -euo pipefail로 source한다.

die() { echo "error: $*" >&2; exit 1; }

# env 파일에서 한 줄만 뽑는다(값에 공백·괄호가 있어도 안전). 줄이 없으면 기본값. 줄 끝 주석과 따옴표·CR은 지운다.
# 키가 없을 때 grep이 1을 돌려 set -euo pipefail 아래에서 조용히 끝나지 않게 `|| true`로 받는다(코드 리뷰 3차 I-M1).
env_value() {  # <file> <key> <default>
  local value
  value="$(grep -E "^$2=" "$1" | tail -n 1 | cut -d= -f2- | sed 's/[[:space:]]*#.*//' | tr -d ' \r"' || true)"
  echo "${value:-$3}"
}

# Git Bash(Windows)에서는 docker compose가 읽을 수 있게 C:/ 형식으로 받는다. Linux에는 pwd -W가 없어 pwd를 쓴다
INFRA_DIR="$(pwd -W 2>/dev/null || pwd)"
EDGE_ENV_FILE="$INFRA_DIR/edge.env"
APP_ENV_FILE="$INFRA_DIR/.env"
# 실행 중인 컨테이너가 연결하는 설정 파일을 둘 곳. CI 작업 폴더는 다른 브랜치·MR 잡이 다시 받으므로(git checkout·clean)
# 거기를 연결하면 한 환경 배포나 MR 잡이 다른 환경 컨테이너의 설정 파일을 바꿔 버린다. 스택마다 고정 폴더에 복사해 연결한다.
RUNKK_HOME="${RUNKK_HOME:-/srv/runkk}"
EDGE_DIR="$RUNKK_HOME/edge"

load_edge_env() {
  [ -f "$EDGE_ENV_FILE" ] || die "edge.env not found. Copy edge.env.example to edge.env (CI: INFRA_ENV_EDGE)."
  SERVER_NAME="$(env_value "$EDGE_ENV_FILE" SERVER_NAME localhost)"
  NGINX_HTTP_PORT="$(env_value "$EDGE_ENV_FILE" NGINX_HTTP_PORT 80)"
  NGINX_HTTPS_PORT="$(env_value "$EDGE_ENV_FILE" NGINX_HTTPS_PORT 443)"
  NGINX_DEVELOP_PORT="$(env_value "$EDGE_ENV_FILE" NGINX_DEVELOP_PORT 8443)"
}

edge_compose() { docker compose -f "$EDGE_DIR/docker-compose.edge.yml" --env-file "$EDGE_ENV_FILE" "$@"; }

load_app_env() {
  [ -f "$APP_ENV_FILE" ] || die ".env not found. Copy .env.example to .env (CI: INFRA_ENV / INFRA_ENV_PROD)."
  RUNKK_ENV="$(env_value "$APP_ENV_FILE" RUNKK_ENV "")"
  case "$RUNKK_ENV" in
    develop|prod) ;;
    *) die "RUNKK_ENV in .env must be develop or prod (got '${RUNKK_ENV}')" ;;
  esac
  APP_DIR="$RUNKK_HOME/$RUNKK_ENV"
  APP_PROJECT="runkk-$RUNKK_ENV"
}

app_compose() { docker compose -f "$APP_DIR/docker-compose.yml" --env-file "$APP_ENV_FILE" "$@"; }

# 인증서는 에지의 letsencrypt 볼륨에 있다(init-letsencrypt.sh가 1회 발급). 있으면 https, 없으면 http 모드다.
# 저장소 파일을 바꾸지 않고 폴더(nginx/http|https)를 골라 연결하므로 작업 폴더를 다시 받아도 모드가 돌아가지 않는다.
detect_nginx_mode() {
  if edge_compose run --rm --no-deps --entrypoint test certbot -f "/etc/letsencrypt/live/${SERVER_NAME}/fullchain.pem" 2>/dev/null; then
    NGINX_MODE=https
  else
    NGINX_MODE=http
  fi
  export NGINX_MODE
}

# nginx를 거친 /actuator/health 주소. HTTPS면 인증서 이름으로 부르고 --resolve로 이 서버에 붙는다(인증서도 검증한다)
health_curl() {  # <develop|prod> <path> [curl options...]
  local env="$1" path="$2" port
  shift 2
  if [ "$NGINX_MODE" = https ]; then
    if [ "$env" = prod ]; then port="$NGINX_HTTPS_PORT"; else port="$NGINX_DEVELOP_PORT"; fi
    curl -fsS --max-time 10 --resolve "${SERVER_NAME}:${port}:127.0.0.1" "$@" "https://${SERVER_NAME}:${port}${path}"
  else
    if [ "$env" = prod ]; then port="$NGINX_HTTP_PORT"; else port="$NGINX_DEVELOP_PORT"; fi
    curl -fsS --max-time 10 "$@" "http://localhost:${port}${path}"
  fi
}

# 저장소 파일을 고정 폴더로 복사한다. 이미 있는 파일은 제자리에 덮어써(같은 inode) 연결된 폴더가 끊기지 않는다.
# 저장소에서 지운 파일은 남으므로 지울 때는 고정 폴더에서도 지운다(README 3절)
sync_bundle() {  # <dest> <path...>
  local dest="$1"; shift
  mkdir -p "$dest" 2>/dev/null || die "cannot create $dest. Once on the server: sudo install -d -m 2775 -g docker $RUNKK_HOME"
  local path
  for path in "$@"; do
    if [ -d "$path" ]; then
      mkdir -p "$dest/$path"
      cp -R "$path/." "$dest/$path/"
    elif [ -e "$path" ]; then
      cp "$path" "$dest/$path"
    fi
  done
}
