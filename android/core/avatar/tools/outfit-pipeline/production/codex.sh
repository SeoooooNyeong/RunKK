#!/bin/bash
# 로컬 Codex CLI(image_generation 켜짐)로 그림 하나를 만든다. 작업 폴더는 저장소 밖(빈 폴더)이어야 한다.
#   codex.sh <작업 폴더> <실행 이름> <지시문 파일> [참고 그림...]
#   결과: <작업 폴더>/runs/<실행 이름>/ (지시문이 정한 파일 이름, summary.txt, log.txt)
#   Codex가 실패하면 codex.log에 "failed <이름>: exit <값>"을 적고 그 값으로 끝난다.
# 의상 묶음 하나:  codex.sh <작업> item <assetKey> <상점 썸네일 폴더>
#   - 지시문은 prompts/items/<assetKey>.txt, 참고 그림은 in/의 기본 텍스처와 상점 썸네일 <폴더>/<assetKey>.png(있으면)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
CODEX="${CODEX:-codex}"  # 시험이 가짜 실행 파일로 바꾼다
WORK="$1"; shift
run() {
  local name="$1" prompt="$2"; shift 2
  local out="$WORK/runs/$name"
  rm -rf "$out"; mkdir -p "$out"
  local refs=(); for f in "$@"; do refs+=(-i "$f"); done
  echo "$(date +%T) start $name" >> "$WORK/codex.log"
  local status=0
  "$CODEX" exec -C "$out" -s workspace-write --skip-git-repo-check -c model_reasoning_effort="low" "${refs[@]}" \
    -o "$out/summary.txt" "$(cat "$prompt")" > "$out/log.txt" 2>&1 || status=$?
  if [ "$status" -ne 0 ]; then
    # 실패를 기록하고 그 값으로 끝낸다(여러 묶음을 돌리는 쪽이 실패를 알아채게). 자세한 내용은 runs/<이름>/log.txt
    echo "$(date +%T) failed $name: exit $status" >> "$WORK/codex.log"
    exit "$status"
  fi
  echo "$(date +%T) done $name: $(ls "$out" | tr '\n' ' ')" >> "$WORK/codex.log"
}
if [ "$1" = "item" ]; then
  key="$2"; thumbs="$3"; in="$WORK/in"
  case "$key" in
    top_*) refs=("$in/stand_default.png" "$in/run_body_default.png" "$in/run_arm_default.png")
           case "$key" in top_finisher_tee|top_winter_hoodie|top_dawn_windbreaker|top_cherry_vest) refs+=("$in/stand_coral_hoodie.png");; esac;;
    bottom_*) refs=("$in/stand_default.png" "$in/run_leg_default.png");;
    shoes_*) refs=("$in/stand_default.png" "$in/run_shoe_default.png");;
  esac
  [ -f "$thumbs/$key.png" ] && refs+=("$thumbs/$key.png")
  run "$key" "$HERE/prompts/items/$key.txt" "${refs[@]}"
else
  run "$@"
fi
