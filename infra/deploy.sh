#!/usr/bin/env bash
# 서버 배포. infra/ 폴더에서 실행한다(README 3절).
#   ./deploy.sh         .env의 RUNKK_ENV(develop|prod) 환경을 배포한다: 이미지 빌드 → 에지 맞추기 → 배포 직전 DB 덤프 → 환경 스택 재시작 → 헬스체크
#   ./deploy.sh edge    에지(nginx·모니터링)만 맞춘다. .env가 필요 없다
# 값: .env(환경 스택, CI INFRA_ENV / INFRA_ENV_PROD), edge.env(에지, CI INFRA_ENV_EDGE).
# 한 환경 배포는 다른 환경 컨테이너를 건드리지 않는다(프로젝트가 다르고 --remove-orphans도 자기 프로젝트만 본다).
# 에지는 어느 배포에서든 맞추되, 바뀐 컨테이너만 다시 만들고 nginx·Prometheus 설정은 nginx -t 검사를 통과한 뒤에만 다시 읽힌다.
# api나 ai가 healthy가 되지 않으면 실패(exit 1)한다. ai가 내려가도 api는 대체 문구로 버티므로 따로 확인하지 않으면 모른다.
set -euo pipefail
cd "$(dirname "$0")"
# shellcheck source=lib.sh
source ./lib.sh

TARGET="${1:-app}"
case "$TARGET" in app|edge) ;; *) die "usage: ./deploy.sh [edge]" ;; esac

load_edge_env
if [ "$TARGET" = app ]; then
  load_app_env
  SPRING_PROFILE="$(env_value "$APP_ENV_FILE" SPRING_PROFILES_ACTIVE prod)"
  # prod(베타)는 prod 프로필만, Swagger는 켜지 않는다(sqa.md 8.1 필수 4). 셀카는 서버 스위치로 꺼 둔다(2026-10-23 결정)
  if [ "$RUNKK_ENV" = prod ]; then
    [ "$SPRING_PROFILE" = prod ] || die "RUNKK_ENV=prod needs SPRING_PROFILES_ACTIVE=prod (got $SPRING_PROFILE)"
    [ "$(env_value "$APP_ENV_FILE" SWAGGER_ENABLED false)" = false ] || die "RUNKK_ENV=prod must not set SWAGGER_ENABLED=true"
    if [ "$(env_value "$APP_ENV_FILE" SELFIE_ENABLED false)" != false ]; then
      echo "warning: SELFIE_ENABLED is on for prod. The beta decision (2026-10-23) keeps selfie conversion off." >&2
    fi
  fi
fi

# 두 환경 배포가 동시에 에지를 만지지 않게 서버 안에서 한 번에 하나만 돈다(CI resource_group과 같은 뜻, 수동 실행까지 막는다)
mkdir -p "$RUNKK_HOME" 2>/dev/null || die "cannot create $RUNKK_HOME. Once on the server: sudo install -d -m 2775 -g docker $RUNKK_HOME"
if command -v flock >/dev/null 2>&1; then
  exec 9>"$RUNKK_HOME/.deploy.lock"
  flock -w 1800 9 || die "another deploy is still running ($RUNKK_HOME/.deploy.lock)"
fi

# Prints "healthy" only for a healthy container ("unhealthy" must not match).
wait_healthy() {  # <service> <tries, 5 s apart>
  for _ in $(seq 1 "$2"); do
    if [ "$(app_compose ps "$1" --format '{{.Health}}')" = "healthy" ]; then
      echo "$1 is healthy"
      return 0
    fi
    sleep 5
  done
  echo "$1 did not become healthy. Logs:" >&2
  app_compose logs --tail=100 "$1" >&2
  return 1
}

# 분리 전(한 프로젝트 runkk에 nginx와 api·ai·postgres·redis가 함께 있던 때) 컨테이너를 내린다. 볼륨은 지우지 않는다.
# postgres는 바로 죽이지 않고 60초까지 정상 종료를 기다린다. 이 뒤 develop 스택이 같은 볼륨(runkk_postgres-data)으로 뜬다
retire_legacy_app_containers() {
  local legacy
  legacy="$(docker ps -a --filter label=com.docker.compose.project=runkk --format '{{.Label "com.docker.compose.service"}} {{.Names}}' \
    | awk '$1=="api"||$1=="ai"||$1=="postgres"||$1=="redis" {print $2}' || true)"
  [ -n "$legacy" ] || return 0
  echo "Retiring pre-split containers (volumes are kept): $(echo "$legacy" | tr '\n' ' ')"
  # api·ai를 먼저 내려 DB 쓰기를 멈춘 뒤 postgres·redis를 내린다
  for service in api ai postgres redis; do
    docker ps -a --filter label=com.docker.compose.project=runkk --filter "label=com.docker.compose.service=$service" -q \
      | xargs -r docker stop -t 60 >/dev/null
  done
  echo "$legacy" | xargs -r docker rm >/dev/null
}

deploy_edge() {
  sync_bundle "$EDGE_DIR" docker-compose.edge.yml nginx monitoring
  detect_nginx_mode
  if [ "$NGINX_MODE" = https ]; then
    # Grafana가 만드는 링크 주소와 로그인 쿠키 Secure를 nginx 모드에 맞춘다(compose 기본값은 http). Grafana는 prod 도메인(443)에 둔다
    if [ "$NGINX_HTTPS_PORT" = 443 ]; then
      export GRAFANA_ROOT_URL="https://${SERVER_NAME}/grafana/"
    else
      export GRAFANA_ROOT_URL="https://${SERVER_NAME}:${NGINX_HTTPS_PORT}/grafana/"
    fi
    export GRAFANA_COOKIE_SECURE=true
  else
    export GRAFANA_ROOT_URL="http://${SERVER_NAME}:${NGINX_HTTP_PORT}/grafana/" GRAFANA_COOKIE_SECURE=false
  fi
  echo "Edge: nginx mode $NGINX_MODE"

  edge_compose pull -q nginx prometheus node-exporter grafana
  # 새 설정을 일회용 컨테이너에서 먼저 검사한다(같은 템플릿·인증서·망). 실패하면 돌고 있는 nginx는 건드리지 않고 멈춘다
  edge_compose run --rm --no-deps -T -e NGINX_ENTRYPOINT_QUIET_LOGS=1 nginx nginx -t -q || die "nginx config test failed; the running nginx was not touched"

  retire_legacy_app_containers
  edge_compose up -d --remove-orphans nginx prometheus node-exporter grafana

  # 연결한 파일이 바뀌어도 compose는 컨테이너를 다시 만들지 않는다. 템플릿을 다시 그리고 검사한 뒤 무중단으로 다시 읽힌다
  edge_compose exec -T nginx sh -c '/docker-entrypoint.d/20-envsubst-on-templates.sh >/dev/null && nginx -t -q && nginx -s reload'
  wait_edge_healthy prometheus
  edge_compose exec -T prometheus wget -q -O /dev/null --post-data= http://127.0.0.1:9090/-/reload
  # 알림 규칙·연락처(monitoring/grafana/provisioning/alerting)도 파일만 바뀌면 다시 읽히지 않는다. 관리자 API로 다시 읽힌다.
  # 관리자 비밀번호를 reset-admin-password로 바꿔 edge.env와 어긋나면 실패하므로 배포는 멈추지 않고 경고만 남긴다(그때는 grafana 재시작)
  wait_edge_healthy grafana
  edge_compose exec -T grafana sh -c 'wget -q -O /dev/null --post-data= \
    --header "Authorization: Basic $(printf "%s:%s" "$GF_SECURITY_ADMIN_USER" "$GF_SECURITY_ADMIN_PASSWORD" | base64 | tr -d "\n")" \
    http://127.0.0.1:3000/api/admin/provisioning/alerting/reload' \
    || echo "WARN: grafana alerting reload failed; run: docker compose -p runkk restart grafana" >&2
  edge_compose ps
}

wait_edge_healthy() {  # <service>
  for _ in $(seq 1 24); do
    [ "$(edge_compose ps "$1" --format '{{.Health}}')" = healthy ] && return 0
    sleep 5
  done
  edge_compose logs --tail=50 "$1" >&2
  die "$1 did not become healthy"
}

# 이 환경의 postgres 볼륨을 다른 프로젝트 컨테이너가 쓰고 있으면 멈춘다. 같은 데이터 폴더에 postgres 두 개가 붙으면 DB가 깨진다
guard_postgres_volume() {
  local volume users
  volume="$(env_value "$APP_ENV_FILE" POSTGRES_VOLUME "")"
  [ -n "$volume" ] || die "POSTGRES_VOLUME is not set in .env"
  users="$(docker ps --filter "volume=$volume" --format '{{.Label "com.docker.compose.project"}} {{.Names}}' | awk -v p="$APP_PROJECT" '$1!=p' || true)"
  [ -z "$users" ] || die "volume $volume is in use by another stack: $users"
}

# 배포 직전 덤프(Flyway가 되돌릴 수 없는 마이그레이션을 돌리기 전, BE5-A-15). 이 환경 postgres 데이터 볼륨이 있으면
# postgres·backup만 먼저 올려 덤프한다(분리 후 첫 배포나 down 뒤에도, BE6-3). 볼륨이 없을 때(처음 배포)만 건너뛴다.
# 덤프는 백업 컨테이너가 CI 작업 폴더 밖 BACKUP_DIR/<환경>에 폴더 700·파일 600으로 남긴다(BE6-1·BE6-2, backup/pg-backup.sh).
predeploy_backup() {
  local volume
  volume="$(env_value "$APP_ENV_FILE" POSTGRES_VOLUME "")"
  if ! docker volume inspect "$volume" >/dev/null 2>&1; then
    echo "No postgres data volume $volume yet (first deploy); skipping the pre-deploy backup."
    return 0
  fi
  app_compose up -d --no-deps postgres backup || die "could not start postgres/backup for the pre-deploy backup"
  wait_healthy postgres 12 || die "postgres is not healthy; aborting the deploy before the migrations run"
  docker exec "$APP_PROJECT-backup-1" sh /opt/runkk-backup/pg-backup.sh now predeploy     || die "pre-deploy backup failed; aborting the deploy before the migrations run"
}

deploy_app() {
  sync_bundle "$APP_DIR" docker-compose.yml backup secrets
  guard_postgres_volume
  app_compose pull -q postgres redis
  predeploy_backup
  app_compose up -d --remove-orphans
  app_compose ps

  echo "Waiting for the api and ai health checks..."
  wait_healthy api 30
  # nginx를 거친 주소까지 확인한다. `cmd && echo` 꼴은 set -e에서 실패해도 넘어가므로 따로 부른다
  health_curl "$RUNKK_ENV" /actuator/health || die "$RUNKK_ENV api is not reachable through nginx"
  echo
  wait_healthy ai 12
}

if [ "$TARGET" = app ]; then
  # 빌드는 내려가는 시간이 없으니 먼저 한다. 환경별 태그라 다른 환경 이미지를 덮어쓰지 않는다. 직전 이미지는 -prev로 남긴다(README 롤백)
  for image in api ai; do
    if docker image inspect "runkk-$image:$RUNKK_ENV" >/dev/null 2>&1; then
      docker tag "runkk-$image:$RUNKK_ENV" "runkk-$image:$RUNKK_ENV-prev"
    fi
  done
  docker build -t "runkk-api:$RUNKK_ENV" ../backend
  docker build -t "runkk-ai:$RUNKK_ENV" ../ai
  # 아바타(Node)는 avatar 프로필을 켠 환경에서만 빌드한다. 폴더가 없으면 프로필을 켰어도 멈춰서 옛 이미지로 조용히 뜨지 않게 한다
  case ",$(env_value "$APP_ENV_FILE" COMPOSE_PROFILES "")," in
    *,avatar,*)
      [ -f ../avatar-ai/Dockerfile ] || die "COMPOSE_PROFILES has avatar but ../avatar-ai/Dockerfile is missing"
      if docker image inspect "runkk-avatar:$RUNKK_ENV" >/dev/null 2>&1; then
        docker tag "runkk-avatar:$RUNKK_ENV" "runkk-avatar:$RUNKK_ENV-prev"
      fi
      docker build -t "runkk-avatar:$RUNKK_ENV" ../avatar-ai
      ;;
  esac
fi

deploy_edge
if [ "$TARGET" = app ]; then
  deploy_app
fi
