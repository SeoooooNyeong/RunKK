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
NGINX_HTTP_PORT="$(grep -E '^NGINX_HTTP_PORT=' .env | tail -n 1 | cut -d= -f2- | sed 's/[[:space:]]*#.*//' | tr -d ' \r"')"
NGINX_HTTP_PORT="${NGINX_HTTP_PORT:-80}"

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

compose pull nginx postgres redis
compose build api ai
compose up -d --remove-orphans
compose ps

echo "Waiting for the api and ai health checks..."
wait_healthy api 30
curl -fsS "http://localhost:${NGINX_HTTP_PORT}/actuator/health" && echo
wait_healthy ai 12
