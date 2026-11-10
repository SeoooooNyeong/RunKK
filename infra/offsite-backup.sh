#!/usr/bin/env bash
# 매일 덤프의 서버 밖 사본(CI backup:offsite, 00:00 스케줄). 환경마다 가장 최근 매일 덤프를 infra/offsite/로 꺼내
# GitLab 아티팩트(7일 만료, Developer 이상만 내려받음)로 올린다(sqa.md 8.1 필수 5, -209). EC2가 통째로 사라져도 하루 전 덤프가 남는다.
#   실패 조건  꺼낸 덤프가 하나도 없음, 꺼낸 덤프가 26시간보다 오래됨(매일 백업이 멈춤)
#   덤프가 아직 없는 환경(prod 첫 배포 전)은 경고만 한다
# 덤프 폴더(BACKUP_DIR/<환경>)는 0700·소유자 ubuntu라 러너 사용자가 직접 못 읽는다. 일회용 컨테이너에 읽기 전용으로 연결해 docker cp로 꺼낸다.
set -euo pipefail
cd "$(dirname "$0")"
# shellcheck source=lib.sh
source ./lib.sh

load_app_env
BACKUP_ROOT="$(env_value "$APP_ENV_FILE" BACKUP_DIR /var/backups/runkk)"
OUT="$INFRA_DIR/offsite"
COPY="runkk-offsite-copy"
MAX_AGE_HOURS="${OFFSITE_MAX_AGE_HOURS:-26}"

# -v: postgres 이미지가 만드는 익명 볼륨까지 지운다(매일 빈 볼륨이 쌓이던 문제)
cleanup() { docker rm -fv "$COPY" >/dev/null 2>&1 || true; }
trap cleanup EXIT
cleanup

docker run -d --name "$COPY" --network none --entrypoint sleep -v "$BACKUP_ROOT:/backups:ro" postgres:17-alpine 600 >/dev/null
rm -rf "$OUT"
# 개인정보가 든 덤프라 러너 사용자만 읽게 한다(작업 폴더는 다음 잡이 git clean으로 지울 때까지 남는다)
umask 077
mkdir "$OUT"

copied=0
stale=0
for env in prod develop; do
  dump="$(docker exec "$COPY" sh -c "ls -t /backups/$env/runkk-$env-daily-*.dump 2>/dev/null | head -n 1" || true)"
  if [ -z "$dump" ]; then
    echo "WARN: no daily dump for $env in $BACKUP_ROOT/$env" >&2
    continue
  fi
  age_hours=$(( ( $(date +%s) - $(docker exec "$COPY" stat -c %Y "$dump") ) / 3600 ))
  docker cp "$COPY:$dump" "$OUT/" >/dev/null
  chmod 600 "$OUT/$(basename "$dump")"
  copied=$((copied + 1))
  echo "$env: $(basename "$dump") (${age_hours}h old, $(du -h "$OUT/$(basename "$dump")" | cut -f1))"
  if [ "$age_hours" -gt "$MAX_AGE_HOURS" ]; then
    echo "error: $env newest daily dump is ${age_hours}h old (> ${MAX_AGE_HOURS}h). Check: docker logs runkk-$env-backup-1" >&2
    stale=1
  fi
done

[ "$copied" -gt 0 ] || die "no daily dump to copy"
# 오래된 덤프도 사본은 올린다(그것이 가장 최근 것이다). 잡은 실패로 끝내 알린다
[ "$stale" -eq 0 ] || exit 1
echo "offsite: $copied dump(s)"
