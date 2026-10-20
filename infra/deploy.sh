#!/usr/bin/env bash
# Build the api image from ../backend and (re)start the whole stack.
# Run from the infra/ directory on the server:  ./deploy.sh
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f .env ]; then
  echo ".env not found. Copy .env.example to .env and fill in the values." >&2
  exit 1
fi

docker compose --env-file .env pull nginx postgres redis
docker compose --env-file .env build api
docker compose --env-file .env up -d --remove-orphans
docker compose --env-file .env ps

echo "Waiting for the api health check..."
for i in $(seq 1 30); do
  if docker compose --env-file .env ps api --format '{{.Health}}' | grep -q healthy; then
    echo "api is healthy"
    curl -fsS "http://localhost:${NGINX_HTTP_PORT:-80}/actuator/health" && echo
    exit 0
  fi
  sleep 5
done
echo "api did not become healthy. Logs:" >&2
docker compose --env-file .env logs --tail=100 api >&2
exit 1
