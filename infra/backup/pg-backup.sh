#!/bin/sh
# 런크크 DB 매일 백업(NFR-018·019·037). docker-compose.yml backup 서비스(postgres:17-alpine)가 실행한다.
#   인자 없음  매일 BACKUP_TIME(Asia/Seoul)에 덤프 + 보존 기간 지난 덤프 삭제를 되풀이한다(컨테이너 주 프로세스)
#   now        지금 한 번 덤프하고 정리한 뒤 끝낸다(deploy.sh 배포 직전 덤프, 수동 백업)
# 덤프는 pg_dump 사용자 정의 형식(-Fc)이다. pg_restore --list로 읽히는지 확인한 뒤에만 .dump 이름으로 바꾼다.
# 접속 정보는 libpq 환경 변수(PGHOST·PGDATABASE·PGUSER·PGPASSWORD)로 받는다.
set -eu

: "${BACKUP_PREFIX:?}" "${BACKUP_TIME:=04:30}" "${BACKUP_KEEP_DAYS:=7}" "${BACKUP_OWNER:=1000:1000}"
DIR=/backups
umask 077

log() { echo "$(date '+%Y-%m-%d %H:%M:%S %Z') pg-backup: $*"; }

# 백업 폴더는 BACKUP_OWNER만 읽는다(개인정보가 든 덤프). 호스트에서 docker가 root로 만든 폴더를 넘겨준다
prepare_dir() {
  chown "$BACKUP_OWNER" "$DIR" 2>/dev/null || log "warning: chown $BACKUP_OWNER $DIR failed"
  chmod 700 "$DIR"
}

dump() {  # <label>
  stamp="$(date +%Y%m%d-%H%M%S)"
  name="$BACKUP_PREFIX-$1-$stamp.dump"
  tmp="$DIR/.$name.partial"
  if pg_dump -Fc -f "$tmp" && pg_restore --list "$tmp" >/dev/null; then
    mv "$tmp" "$DIR/$name"
    chown "$BACKUP_OWNER" "$DIR/$name" 2>/dev/null || true
    log "ok $name $(du -h "$DIR/$name" | cut -f1)"
    return 0
  fi
  rm -f "$tmp"
  log "FAILED $name"
  return 1
}

# 보존 기간(일)이 지난 덤프를 지운다. 매일 같은 시각에 뜨므로 1시간 여유를 빼 "오늘 + 지난 6일" 7개만 남긴다(NFR-037: 8일 넘은 백업 0개)
prune() {
  minutes=$((BACKUP_KEEP_DAYS * 1440 - 60))
  find "$DIR" -maxdepth 1 -type f -name "$BACKUP_PREFIX-*.dump" -mmin +"$minutes" -print -delete | sed 's/^/pruned /'
  find "$DIR" -maxdepth 1 -type f -name ".$BACKUP_PREFIX-*.partial" -mmin +60 -delete
}

# 다음 BACKUP_TIME(오늘 그 시각이 지났으면 내일)까지 남은 초
seconds_until_next() {
  now="$(date +%s)"
  target="$(date -d "$(date +%Y-%m-%d) $BACKUP_TIME" +%s)"
  if [ "$target" -le "$now" ]; then
    target=$((target + 86400))
  fi
  echo $((target - now))
}

prepare_dir
if [ "${1:-}" = "now" ]; then
  dump "${2:-manual}"
  prune
  exit 0
fi

log "daily backup at $BACKUP_TIME, keep $BACKUP_KEEP_DAYS days, dir $DIR"
prune
while true; do
  wait_s="$(seconds_until_next)"
  log "next backup in ${wait_s}s"
  sleep "$wait_s"
  # 실패해도 컨테이너를 끝내지 않는다(로그에 FAILED가 남고 다음 날 다시 뜬다). 10분 뒤 한 번 더 시도한다
  dump daily || { sleep 600; dump daily || true; }
  prune
  # 같은 분 안에서 두 번 뜨지 않게 1분 쉰다
  sleep 60
done
