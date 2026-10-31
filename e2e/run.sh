#!/usr/bin/env bash
# 런크크 에뮬레이터 스모크 E2E 한 번에 돌리기(Windows Git Bash·macOS·Linux).
#   카카오 스텁(18081) → 서버(local 프로필, 8090, 임베디드 Redis 6372) → E2E 앱 빌드·설치 → Maestro 흐름 7개를 단계마다
#   돌리고 단계별 결과·스크린샷·logcat을 e2e/out/에 남긴다. 끝나면 이 스크립트가 띄운 서버·스텁(·에뮬레이터)만 끈다.
# 사용: e2e/run.sh            (전부)
#       e2e/run.sh 03_free_run (한 단계만. 앞 단계 상태가 필요하면 앞에서부터)
# 설정은 아래 환경 변수로 덮는다. 자세한 준비는 e2e/README.md.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/e2e/out"
FLOWS="$ROOT/android/e2e/maestro"

SERIAL="${E2E_SERIAL:-emulator-5556}"            # 시험 전용 에뮬레이터. 다른 기기에는 adb를 보내지 않는다.
AVD="${E2E_AVD:-runkk_e2e}"
EMU_PORT="${SERIAL#emulator-}"
SERVER_PORT="${E2E_SERVER_PORT:-8090}"           # 개발용 8080과 겹치지 않게
REDIS_PORT="${E2E_REDIS_PORT:-6372}"             # local 프로필 기본 6370과 겹치지 않게
STUB_PORT="${E2E_STUB_PORT:-18081}"
KAKAO_APP_ID="${E2E_KAKAO_APP_ID:-900001}"
KAKAO_TOKEN="${E2E_KAKAO_TOKEN:-e2e-1001}"       # 앱 빌드 속성 RUNKK_E2E_KAKAO_TOKEN과 같아야 한다
APP_ID="com.a207.runkk.local"
NICKNAME="${E2E_NICKNAME:-e2erunner}"
CREW_NAME="${E2E_CREW_NAME:-E2ECrew}"
ANDROID_SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-C:/Users/SSAFY/tools/android-sdk}}"
ADB="$ANDROID_SDK/platform-tools/adb"
EMULATOR="$ANDROID_SDK/emulator/emulator"
MAESTRO="${MAESTRO:-$(command -v maestro || echo "$HOME/tools/maestro/bin/maestro")}"
# Windows의 python3는 스토어 바로가기일 수 있어 실제로 도는 것을 고른다.
if [ -z "${PYTHON:-}" ]; then
  for candidate in python3 python; do
    if "$candidate" --version >/dev/null 2>&1; then PYTHON="$candidate"; break; fi
  done
fi
# 앱 빌드·Maestro는 JDK 17, 서버 실행은 JDK 21(서버 툴체인). 없으면 Gradle foojay가 받은 21을 찾는다.
export JAVA_HOME="${JAVA_HOME:-C:/Users/SSAFY/tools/jdk-17.0.20+8}"
JAVA21="${E2E_JAVA21:-}"
if [ -z "$JAVA21" ]; then
  JAVA21="$(ls -d "$HOME"/.gradle/jdks/*21* 2>/dev/null | grep -v '\.zip\|\.lock' | head -1)/bin/java"
fi

STEPS=(01_signup 02_home 03_free_run 04_records 05_crew 06_settings 07_inbox)
if [ $# -gt 0 ]; then STEPS=("$@"); fi

is_windows() { case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) return 0 ;; *) return 1 ;; esac; }
native_path() { if is_windows; then cygpath -m "$1"; else echo "$1"; fi; }
adb() { "$ADB" -s "$SERIAL" "$@"; }
log() { printf '[e2e %s] %s\n' "$(date +%H:%M:%S)" "$*"; }

# 포트를 듣는 프로세스만 끈다(이 스크립트가 띄운 서버·스텁). 8080 등 다른 포트는 건드리지 않는다.
kill_port() {
  local port="$1"
  if is_windows; then
    netstat -ano | awk -v p=":$port" '$2 ~ p"$" && $4 == "LISTENING" {print $5}' | sort -u | while read -r pid; do
      [ -n "$pid" ] && [ "$pid" != "0" ] && taskkill //PID "$pid" //F >/dev/null 2>&1 || true
    done
  else
    (lsof -ti "tcp:$port" -sTCP:LISTEN 2>/dev/null || true) | xargs -r kill
  fi
}

port_busy() {
  if is_windows; then netstat -ano | awk -v p=":$1" '$2 ~ p"$" && $4 == "LISTENING"' | grep -q .
  else lsof -ti "tcp:$1" -sTCP:LISTEN >/dev/null 2>&1; fi
}

wait_http() {
  local url="$1" seconds="$2"
  for _ in $(seq 1 "$seconds"); do
    curl -fsS "$url" >/dev/null 2>&1 && return 0
    sleep 1
  done
  return 1
}

STARTED_EMULATOR=0
cleanup() {
  log "정리: 서버(:$SERVER_PORT)·임베디드 Redis(:$REDIS_PORT)·스텁(:$STUB_PORT) 종료"
  kill_port "$SERVER_PORT"
  # 서버를 강제 종료하면 임베디드 Redis 자식 프로세스가 남으므로 그 포트도 끈다.
  kill_port "$REDIS_PORT"
  kill_port "$STUB_PORT"
  if [ "$STARTED_EMULATOR" = 1 ] && [ "${E2E_KEEP_EMULATOR:-0}" != 1 ]; then
    log "정리: $SERIAL 에뮬레이터 종료"
    adb emu kill >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

mkdir -p "$OUT"
rm -rf "$OUT/maestro"
rm -f "$OUT"/*.png "$OUT"/*.maestro.txt "$OUT"/*.xml "$OUT"/*.logcat.txt "$OUT"/*.errors.txt "$OUT"/summary.txt

for port in "$SERVER_PORT" "$REDIS_PORT" "$STUB_PORT"; do
  if port_busy "$port"; then echo "포트 $port가 이미 쓰이고 있다. 앞선 실행이 남았는지 확인한다." >&2; exit 2; fi
done

# 1. 에뮬레이터(시험 전용 AVD)
if ! "$ADB" devices | grep -q "^$SERIAL[[:space:]]*device"; then
  log "에뮬레이터 $AVD 시작(포트 $EMU_PORT)"
  window_flag="-no-window"
  if [ "${E2E_EMULATOR_WINDOW:-0}" = 1 ]; then window_flag=""; fi
  # shellcheck disable=SC2086
  "$EMULATOR" -avd "$AVD" -port "$EMU_PORT" -no-snapshot-save -no-boot-anim $window_flag >"$OUT/emulator.log" 2>&1 &
  STARTED_EMULATOR=1
  "$ADB" -s "$SERIAL" wait-for-device
  until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do sleep 2; done
fi
log "기기 $SERIAL 준비됨"
# 에뮬레이터 이미지의 Bluetooth 서비스가 비정상 종료되면 시스템 창이 앱 화면을 가린다. 시험에 쓰지 않으므로 끈다.
adb shell cmd bluetooth_manager disable >/dev/null 2>&1 || true
adb shell pm disable-user --user 0 com.android.bluetooth >/dev/null 2>&1 || true

# 2. 카카오 스텁
log "카카오 스텁 :$STUB_PORT (app_id=$KAKAO_APP_ID)"
"$PYTHON" -I "$(native_path "$ROOT/e2e/kakao_stub.py")" --port "$STUB_PORT" --app-id "$KAKAO_APP_ID" >"$OUT/kakao_stub.log" 2>&1 &
wait_http "http://127.0.0.1:$STUB_PORT/health" 20 || { echo "스텁이 뜨지 않았다" >&2; exit 1; }

# 3. 서버(backend local 프로필, H2 메모리 DB라 실행마다 새 상태). 코드는 고치지 않고 설정만 바꾼다.
if [ "${E2E_SKIP_BACKEND_BUILD:-0}" != 1 ]; then
  log "서버 빌드(bootJar)"
  (cd "$ROOT/backend" && ./gradlew -q bootJar)
fi
JAR="$(ls "$ROOT"/backend/build/libs/*.jar | grep -v plain | head -1)"
log "서버 :$SERVER_PORT 시작($JAR)"
SERVER_PORT="$SERVER_PORT" EMBEDDED_REDIS_PORT="$REDIS_PORT" \
  KAKAO_API_BASE_URL="http://127.0.0.1:$STUB_PORT" KAKAO_APP_ID="$KAKAO_APP_ID" \
  "$JAVA21" -jar "$(native_path "$JAR")" --spring.profiles.active=local --spring.jpa.show-sql=false \
  >"$OUT/server.log" 2>&1 &
wait_http "http://localhost:$SERVER_PORT/actuator/health" 120 || { echo "서버가 뜨지 않았다(e2e/out/server.log)" >&2; exit 1; }

# 4. E2E 앱 빌드(local 플레이버 + debug, 시험용 카카오 창구 켬, 서버 주소 8090)·설치
if [ "${E2E_SKIP_APP_BUILD:-0}" != 1 ]; then
  log "앱 빌드(assembleLocalDebug, E2E 속성)"
  (cd "$ROOT/android" && ./gradlew -q :app:assembleLocalDebug \
    -PRUNKK_E2E_KAKAO_TOKEN="$KAKAO_TOKEN" \
    -PRUNKK_LOCAL_API_URL="http://10.0.2.2:$SERVER_PORT/api/v1/" \
    -PRUNKK_LOCAL_WS_URL="ws://10.0.2.2:$SERVER_PORT/ws")
fi
# 앱은 ABI별 APK로 나뉜다(android/app/build.gradle.kts splits). 기기 ABI에 맞는 것을 고른다.
ABI="$(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
APK="$ROOT/android/app/build/outputs/apk/local/debug/app-local-$ABI-debug.apk"
log "앱 설치"
adb install -r -g "$(native_path "$APK")" >/dev/null

# 5. 단계별 Maestro 흐름
FAILED=0
for step in "${STEPS[@]}"; do
  adb logcat -c || true
  attempt=1
  while :; do
    log "단계 $step (시도 $attempt)"
    adb wait-for-device
    set +e
    # Maestro 드라이버는 첫 단계에서만 (다시) 설치한다. 단계마다 다시 설치하면 Windows에서 adb 연결이 자주 끊겼다("device offline").
    driver_flag="--no-reinstall-driver"
    if [ "$step" = "${STEPS[0]}" ] && [ "$attempt" = 1 ]; then driver_flag="--reinstall-driver"; fi
    "$MAESTRO" --device "$SERIAL" test "$driver_flag" "$(native_path "$FLOWS/$step.yaml")" \
      -e APP_ID="$APP_ID" -e SERVER_URL="http://localhost:$SERVER_PORT" -e KAKAO_TOKEN="$KAKAO_TOKEN" \
      -e NICKNAME="$NICKNAME" -e CREW_NAME="$CREW_NAME" \
      --test-output-dir "$(native_path "$OUT/maestro/$step")" \
      --format junit --output "$(native_path "$OUT/$step.xml")" >"$OUT/$step.maestro.txt" 2>&1
    code=$?
    set -e
    # 단언 실패가 아니라 기기 연결이 잠깐 끊긴 경우(adb "device offline", Maestro 기기 서버 종료)만 두 번까지 다시 돈다.
    # 흐름은 이어 돌려도 되게(이미 가입·이미 크루 있음) 짜여 있다. 다시 돈 횟수는 summary에 남긴다.
    if [ "$code" != 0 ] && [ "$attempt" -lt 3 ] &&
      grep -rqE "device offline|DeviceServerDiedException" "$OUT/maestro/$step" 2>/dev/null; then
      echo "RETRY $step (기기 연결 끊김)" >>"$OUT/summary.txt"
      rm -rf "$OUT/maestro/$step"
      adb reconnect >/dev/null 2>&1 || true
      sleep 5
      attempt=$((attempt + 1))
      continue
    fi
    break
  done
  # takeScreenshot 결과(<단계>_*.png)를 e2e/out/ 바로 아래로 모은다.
  find "$OUT/maestro/$step" -name "${step}_*.png" -exec cp {} "$OUT/" \; 2>/dev/null || true
  adb logcat -d -v threadtime >"$OUT/$step.logcat.txt" 2>/dev/null || true
  adb logcat -d -b crash >>"$OUT/$step.logcat.txt" 2>/dev/null || true
  pids="$(adb shell pidof "$APP_ID" 2>/dev/null | tr -d '\r' || true)"
  # 앱 오류: 치명적 예외 + 앱 프로세스의 E 줄(시스템 hiddenapi 경고 등 잡음 제외)
  {
    grep -E "FATAL EXCEPTION| E AndroidRuntime" "$OUT/$step.logcat.txt" || true
    for pid in $pids; do
      awk -v p="$pid" '$3 == p && $5 == "E"' "$OUT/$step.logcat.txt" | grep -v -E "hiddenapi|ashmem|EGL|HWUI" || true
    done
  } >"$OUT/$step.errors.txt"
  errors=$(grep -c . "$OUT/$step.errors.txt" || true)
  if [ "$code" = 0 ] && [ "$errors" = 0 ]; then
    echo "PASS $step" | tee -a "$OUT/summary.txt"
  else
    echo "FAIL $step (maestro=$code, app_errors=$errors) → e2e/out/$step.maestro.txt, $step.errors.txt" | tee -a "$OUT/summary.txt"
    FAILED=1
    break
  fi
done

log "결과: e2e/out/summary.txt"
cat "$OUT/summary.txt"
exit "$FAILED"
