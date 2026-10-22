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

# 인증서는 letsencrypt 볼륨에 있고(init-letsencrypt.sh가 1회 발급) nginx 설정은 작업 폴더의 파일이다. CI는 배포마다 저장소를
# 새로 받아 HTTP 설정으로 돌아가므로, 인증서가 있으면 배포 때마다 HTTPS 설정을 다시 고른다.
if compose run --rm --no-deps --entrypoint test certbot -f "/etc/letsencrypt/live/${SERVER_NAME}/fullchain.pem" 2>/dev/null; then
  cp nginx/https.conf.template nginx/templates/default.conf.template
  # HTTPS면 80은 301만 돌려주므로 443을 인증서 이름으로 직접 부른다(--resolve로 이 서버에 붙고 인증서도 검증한다)
  HEALTH_CURL=(curl -fsS --resolve "${SERVER_NAME}:${NGINX_HTTPS_PORT}:127.0.0.1" "https://${SERVER_NAME}:${NGINX_HTTPS_PORT}/actuator/health")
else
  HEALTH_CURL=(curl -fsS "http://localhost:${NGINX_HTTP_PORT}/actuator/health")
fi

compose pull nginx postgres redis
compose build api ai
compose up -d --remove-orphans
compose ps

echo "Waiting for the api and ai health checks..."
wait_healthy api 30
"${HEALTH_CURL[@]}" && echo
wait_healthy ai 12
