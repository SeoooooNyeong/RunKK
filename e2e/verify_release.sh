#!/usr/bin/env bash
# E2E 시험 전용 경로(시험용 카카오 토큰 창구)가 localDebug 밖에 들어가지 않는지 확인한다.
#   1) 다른 플레이버의 debug 단위 시험(E2eLoginPathTest: 클래스·빌드 설정 값이 "없다")
#   2) release APK 3종(local·develop·prod)의 dex에 시험용 클래스 이름·토큰 이름이 없는지
# 토큰 속성을 일부러 켠 채(-PRUNKK_E2E_KAKAO_TOKEN) 빌드해 "속성이 있어도 출시 빌드에는 안 들어간다"를 본다.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/android"
./gradlew -q \
  :app:testDevelopDebugUnitTest --tests '*E2eLoginPathTest*' \
  :app:testProdDebugUnitTest --tests '*E2eLoginPathTest*' \
  :app:assembleLocalRelease :app:assembleDevelopRelease :app:assembleProdRelease \
  -PRUNKK_E2E_KAKAO_TOKEN=e2e-verify
PATTERN='E2eKakaoTokenSource|E2eLoginModule|E2eBuildConfigModule|runkk\.e2e\.kakaoToken|E2E_KAKAO_TOKEN'
status=0
for apk in app/build/outputs/apk/{local,develop,prod}/release/*.apk; do
  hits=$(unzip -p "$apk" 'classes*.dex' | grep -a -c -E "$PATTERN" || true)
  if [ "$hits" = 0 ]; then
    echo "OK   $(basename "$apk"): 시험 전용 코드 없음"
  else
    echo "FAIL $(basename "$apk"): 시험 전용 코드 흔적 $hits곳" >&2
    status=1
  fi
done
exit "$status"
