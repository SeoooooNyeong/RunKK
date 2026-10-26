#!/usr/bin/env bash
# 배포 뒤 스모크(CI backend:smoke). infra/ 폴더의 .env(RUNKK_ENV)·edge.env로 그 환경 주소를 정해 확인한다.
#   prod    https://<SERVER_NAME>/actuator/health = UP, ai healthy, Swagger(/v3/api-docs) 닫힘
#   develop https://<SERVER_NAME>:8443/actuator/health = UP, ai healthy, Swagger(/docs) 열림
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

if [ "$RUNKK_ENV" = prod ]; then
  # prod는 API 문서를 열지 않는다(sqa.md 8.1 필수 4)
  if health_curl prod /v3/api-docs >/dev/null 2>&1; then
    die "prod serves /v3/api-docs (Swagger must be off)"
  fi
  echo "prod swagger: closed"
else
  # springdoc이 /docs를 swagger-ui 주소로 돌려보낸다
  health_curl develop /docs -L >/dev/null
  echo "develop swagger: open"
fi
