#!/usr/bin/env bash
# 매일 덤프의 서버 밖 사본(CI backup:offsite, 00:00 스케줄). 환경마다 가장 최근 매일 덤프와 아바타 볼륨 묶음(-avatar-daily-*.tar.gz)을
# infra/offsite/로 꺼내 GitLab 아티팩트(7일 만료, Developer 이상만 내려받음)로 올린다(sqa.md 8.1 필수 5, -209). EC2가 통째로 사라져도 하루 전 백업이 남는다.
#   실패 조건  꺼낸 덤프가 하나도 없음, 꺼낸 덤프나 아바타 묶음이 26시간보다 오래됨(매일 백업이 멈춤)
#   덤프가 아직 없는 환경(prod 첫 배포 전)은 경고만 한다. 아바타 묶음이 없는 환경(avatar 프로필 꺼짐·빈 볼륨)은 건너뛴다
#   아바타 묶음이 OFFSITE_AVATAR_MAX_MB(기본 50)보다 크면 아티팩트 크기 제한에 걸려 덤프 사본까지 잃지 않게 경고만 하고 뺀다
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
AVATAR_MAX_MB="${OFFSITE_AVATAR_MAX_MB:-50}"

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
avatars=0
stale=0
# 가장 최근 파일 하나를 꺼낸다. 없으면 FOUND=0. 26시간보다 오래되면 stale=1(사본은 그래도 올린다).
# set -e가 함수 안에서도 듣도록 `||`로 부르지 않고 결과는 FOUND로 받는다
copy_newest() {  # <env> <파일 이름 패턴> <종류> [최대 MB]
  local env="$1" pattern="$2" kind="$3" max_mb="${4:-}" file age_hours size_mb
  FOUND=0
  file="$(docker exec "$COPY" sh -c "ls -t /backups/$env/$pattern 2>/dev/null | head -n 1" || true)"
  [ -n "$file" ] || return 0
  FOUND=1
  age_hours=$(( ( $(date +%s) - $(docker exec "$COPY" stat -c %Y "$file") ) / 3600 ))
  if [ "$age_hours" -gt "$MAX_AGE_HOURS" ]; then
    echo "error: $env newest daily $kind $(basename "$file") is ${age_hours}h old (> ${MAX_AGE_HOURS}h). Check: docker logs runkk-$env-backup-1" >&2
    stale=1
  fi
  if [ -n "$max_mb" ]; then
    size_mb=$(( $(docker exec "$COPY" stat -c %s "$file") / 1048576 ))
    if [ "$size_mb" -gt "$max_mb" ]; then
      echo "WARN: $env: $(basename "$file") is ${size_mb}MB (> ${max_mb}MB, OFFSITE_AVATAR_MAX_MB); not copied offsite" >&2
      FOUND=0
      return 0
    fi
  fi
  docker cp "$COPY:$file" "$OUT/" >/dev/null
  chmod 600 "$OUT/$(basename "$file")"
  echo "$env: $(basename "$file") (${age_hours}h old, $(du -h "$OUT/$(basename "$file")" | cut -f1))"
}

for env in prod develop; do
  copy_newest "$env" "runkk-$env-daily-*.dump" dump
  if [ "$FOUND" -eq 1 ]; then
    copied=$((copied + 1))
  else
    echo "WARN: no daily dump for $env in $BACKUP_ROOT/$env" >&2
  fi
  copy_newest "$env" "runkk-$env-avatar-daily-*.tar.gz" "avatar archive" "$AVATAR_MAX_MB"
  if [ "$FOUND" -eq 1 ]; then
    avatars=$((avatars + 1))
  fi
done

[ "$copied" -gt 0 ] || die "no daily dump to copy"
# 오래된 덤프도 사본은 올린다(그것이 가장 최근 것이다). 잡은 실패로 끝내 알린다
[ "$stale" -eq 0 ] || exit 1
echo "offsite: $copied dump(s), $avatars avatar archive(s)"
