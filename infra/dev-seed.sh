#!/usr/bin/env bash
# develop 더미 데이터 시드(CI dev:seed, 수동, S15P31A207-222). infra/ 폴더의 .env(RUNKK_ENV)가 develop일 때만
# develop DB 컨테이너(runkk-develop-postgres-1)에 dev-seed.sql을 한 트랜잭션으로 넣는다. 무엇을 넣는지는 dev-seed.sql 머리말에 있다.
#   운영 보호  RUNKK_ENV가 develop이 아니거나 컨테이너가 runkk-develop-postgres-1이 아니면 아무것도 하지 않고 멈춘다
#   멱등      다시 돌려도 이미 있는 더미 회원·크루·기록은 건너뛴다(dev-seed.sql)
#   대상 계정  DEV_SEED_TARGETS(쉼표 구분 닉네임, 기본 "러너4171,러너1699")의 회원마다 더미 크루 가입·러닝 22회·런바디 작업을 넣는다
set -euo pipefail
cd "$(dirname "$0")"
# shellcheck source=lib.sh
source ./lib.sh

load_app_env
[ "$RUNKK_ENV" = develop ] || die "dev-seed runs only on develop (RUNKK_ENV=$RUNKK_ENV)"
DB_CONTAINER="$APP_PROJECT-postgres-1"
[ "$DB_CONTAINER" = runkk-develop-postgres-1 ] || die "unexpected DB container $DB_CONTAINER"
docker inspect "$DB_CONTAINER" >/dev/null 2>&1 || die "$DB_CONTAINER is not running"

# 앱 계정·DB 이름은 컨테이너 환경 변수에서 읽는다(restore-check.sh live_psql과 같은 방식). 경고·안내는 psql이 stderr로 찍는다
DEV_SEED_TARGETS="${DEV_SEED_TARGETS:-러너4171,러너1699}"
echo "develop dev-seed targets: $DEV_SEED_TARGETS"
docker exec -i -e DEV_SEED_TARGETS="$DEV_SEED_TARGETS" "$DB_CONTAINER" \
  sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -X -q -v ON_ERROR_STOP=1 -v dev_seed_targets="$DEV_SEED_TARGETS"' < dev-seed.sql \
  || die "dev-seed.sql failed (rolled back)"
echo "develop dev-seed: ok"
