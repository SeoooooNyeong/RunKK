#!/usr/bin/env bash
# 배포 뒤 스모크(CI backend:smoke). infra/ 폴더의 .env(RUNKK_ENV)·edge.env로 그 환경 주소를 정해 확인한다.
#   prod    https://<SERVER_NAME>/actuator/health = UP, ai healthy, Swagger(/v3/api-docs) 닫힘
#   develop https://<SERVER_NAME>:8443/actuator/health = UP, ai healthy, Swagger는 밖에서 닫힘(nginx)·컨테이너 안에서 열림
#   공통    .env COMPOSE_PROFILES에 avatar가 있으면 avatar healthy + ai에서 http://avatar:3000/api/health 응답(없으면 건너뜀)
# 서버(배포 러너)에서 127.0.0.1로 붙고 인증서 이름으로 검증한다(--resolve).
set -euo pipefail
cd "$(dirname "$0")"
# shellcheck source=lib.sh
source ./lib.sh

load_edge_env
load_app_env
detect_nginx_mode

health_curl "$RUNKK_ENV" /actuator/health | grep -q '"UP"' || die "$RUNKK_ENV api health is not UP"
echo "$RUNKK_ENV api: UP ($NGINX_MODE)"

# ai는 외부 포트가 없어 컨테이너 헬스 상태로 본다("unhealthy"가 걸리지 않게 정확히 비교)
[ "$(docker inspect -f '{{.State.Health.Status}}' "$APP_PROJECT-ai-1")" = healthy ] || die "$APP_PROJECT-ai-1 is not healthy"
echo "$RUNKK_ENV ai: healthy"

# avatar(Node)는 프로필로 켠 환경에서만 본다. 외부 포트가 없어 compose 헬스 상태(컨테이너 안 /api/health)로 보고,
# ai가 실제로 부르는 길(전용 망 avatar)로도 한 번 부른다. 배포가 막 다시 만든 컨테이너는 starting일 수 있어 1분까지 기다린다
if app_profile_enabled avatar; then
  avatar_health=""
  for _ in $(seq 1 12); do
    avatar_health="$(docker inspect -f '{{.State.Health.Status}}' "$APP_PROJECT-avatar-1" 2>/dev/null || true)"
    [ "$avatar_health" = starting ] || break
    sleep 5
  done
  [ "$avatar_health" = healthy ] || die "$APP_PROJECT-avatar-1 is not healthy (${avatar_health:-no container})"
  docker exec "$APP_PROJECT-ai-1" python -c "import urllib.request; urllib.request.urlopen('http://avatar:3000/api/health', timeout=5)" \
    || die "$APP_PROJECT-ai-1 cannot reach http://avatar:3000/api/health"
  echo "$RUNKK_ENV avatar: healthy"
else
  echo "$RUNKK_ENV avatar: skipped (COMPOSE_PROFILES has no avatar)"
fi

if [ "$RUNKK_ENV" = prod ]; then
  # prod는 API 문서를 열지 않는다(sqa.md 8.1 필수 4)
  if health_curl prod /v3/api-docs >/dev/null 2>&1; then
    die "prod serves /v3/api-docs (Swagger must be off)"
  fi
  echo "prod swagger: closed"
else
  # develop은 문서를 켜 두되 nginx가 밖에서 막는다(결정 D80). 밖은 닫힘, api 컨테이너 안은 열림을 함께 본다
  if health_curl develop /v3/api-docs >/dev/null 2>&1; then
    die "develop nginx serves /v3/api-docs (must be blocked outside)"
  fi
  docker exec "$APP_PROJECT-api-1" curl -fsS --max-time 10 http://localhost:8080/v3/api-docs >/dev/null     || die "develop api docs are off inside the container"
  echo "develop swagger: closed outside, open inside"
fi
