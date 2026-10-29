# 런크크 에뮬레이터 E2E(스모크)

실제 앱(local 플레이버 + debug)을 시험 전용 에뮬레이터에 띄우고, 로컬 서버(backend local 프로필)와 실제로 주고받는 사용자 흐름 7개를 Maestro로 확인한다.

| 단계 | 흐름 파일(`android/e2e/maestro/`) | 확인 |
|---|---|---|
| 1 | `01_signup.yaml` | 카카오 버튼 로그인 → 가입 동의 화면(만 14세·약관·개인정보·위치 필수 동의) → "시작하기" → 홈(첫 사용 가이드 건너뛰기) |
| 2 | `02_home.yaml` | 홈(v0.14)의 머리·리워드 잔액·이번 주 막대(0km)·자유런/배틀런 카드가 뜨고 "다시 시도"(오류 상태)가 없음 |
| 3 | `03_free_run.yaml` | 홈 자유런 카드 → (동의 없으면) 위치 동의 시트 → 측정 → GPS 경로 재생(1.16km, 4 m/s) → 길게 눌러 종료 → 러닝 결과 평균 페이스 3~7분대·서버 "구간 페이스" → 서버 `/dashboard` 누적 1회·1000m 이상 |
| 4 | `04_records.yaml` | 마이 > 러닝 기록 > 목록 탭 → 상세(코스 지도·구간 페이스) |
| 5 | `05_crew.yaml` | 크루 만들기 → 바로 내 크루 홈 → 크루 탭을 나갔다 다시 들어와도 내 크루 홈 → 서버 `/crews/me` |
| 6 | `06_settings.yaml` | 설정 "코스 지도 공유" 켬 → 화면 다시 열기·앱 다시 켜기 뒤에도 켜짐 → 서버 `/me/settings` |
| 7 | `07_inbox.yaml` | 알림함 빈 상태 → 서버 `/notifications` 0건 |

홈 대기와 첫 사용 가이드 닫기는 공용 하위 흐름 `android/e2e/maestro/common/home_ready.yaml`이 맡는다(각 단계가 `runFlow`로 부른다).

## 로그인 방식(인증 우회 없음)

- **서버**: 코드는 그대로이고 설정만 바꾼다. `KAKAO_API_BASE_URL`(= `runkk.auth.kakao.base-url`)을 카카오 스텁 `e2e/kakao_stub.py`로, `KAKAO_APP_ID`를 스텁이 돌려줄 app_id로 둔다. 서버의 `KakaoLoginGateway`는 실제와 같이 `GET /v1/user/access_token_info`를 불러 회원 ID와 앱 ID를 검증한다. 스텁은 `Bearer e2e-<숫자>`에만 `{"id", "app_id", "expires_in"}`을 주고 나머지는 401이다.
- **앱**: Kakao SDK 대신 빌드 속성 `RUNKK_E2E_KAKAO_TOKEN` 값을 돌려주는 시험용 창구(`android/app/src/localDebug/`)를 쓴다.
  - local 플레이버 + debug 빌드의 소스셋에만 있다. 값이 비면 창구가 꺼져 Kakao SDK 그대로다.
  - 출시 코드에는 "대체 창구 자리"(`KakaoTokenOverride` 멀티바인딩 집합, 늘 비어 있음)만 있다.
  - 출시 빌드 확인: `e2e/verify_release.sh`. 다른 플레이버 debug 단위 시험(`E2eLoginPathTest`)을 돌리고, release APK 3종(local·develop·prod)의 dex에서 시험용 클래스·토큰 이름을 찾는다. 토큰 속성을 일부러 켠 채 빌드해서 본다.

## 준비(한 번)

| 항목 | 값 |
|---|---|
| AVD | `runkk_e2e`(API 36). 개발용 `runkk_api36`(보통 `emulator-5554`)과 따로 쓴다. 스크립트는 `emulator-5556`에만 adb를 보낸다 |
| JDK | 앱 빌드·Maestro는 17(`JAVA_HOME`), 서버 실행은 21(없으면 `~/.gradle/jdks/*21*`을 찾는다. `E2E_JAVA21`로 지정 가능) |
| Maestro | `curl -Ls "https://get.maestro.mobile.dev" \| bash`. Windows는 [릴리스 zip](https://github.com/mobile-dev-inc/maestro/releases)을 풀고 `MAESTRO=<경로>/bin/maestro.bat`로 지정한다. 2.11.0에서 확인했다 |
| Python | 3.9 이상, 표준 라이브러리만 씀(스텁) |
| 포트 | 서버 8090, 임베디드 Redis 6372, 스텁 18081. 개발 서버 8080은 건드리지 않는다 |

빌드 속성(local.properties·`-P`·환경 변수, `secretProperty()` 순서). `run.sh`가 `-P`로 넣으므로 직접 넣을 필요는 없다.

| 속성 | E2E 값 | 없을 때 |
|---|---|---|
| `RUNKK_E2E_KAKAO_TOKEN` | `e2e-1001` | 시험용 창구 꺼짐(Kakao SDK) |
| `RUNKK_LOCAL_API_URL` | `http://10.0.2.2:8090/api/v1/` | `http://10.0.2.2:8080/api/v1/` |
| `RUNKK_LOCAL_WS_URL` | `ws://10.0.2.2:8090/ws` | `ws://10.0.2.2:8080/ws` |

## 실행

```bash
MAESTRO=/c/Users/SSAFY/tools/maestro/bin/maestro.bat e2e/run.sh   # Windows Git Bash
e2e/run.sh                                                          # maestro가 PATH에 있으면
```

`run.sh` 순서:

1. 에뮬레이터가 없으면 `runkk_e2e`를 `-port 5556 -no-window`로 띄운다. 이 스크립트가 띄웠을 때만 끝나고 끈다(`E2E_KEEP_EMULATOR=1`이면 둔다).
2. 스텁을 띄운다.
3. 서버를 `bootJar` 후 `java -jar`로 띄운다(H2 메모리 DB라 실행마다 새 상태).
4. E2E 앱을 빌드하고 기기 ABI(`ro.product.cpu.abi`)에 맞는 분할 APK(`app-local-<ABI>-debug.apk`)를 `adb install -r -g`로 설치한다. 기기가 준비되면 Bluetooth를 끈다(에뮬레이터 이미지의 Bluetooth 비정상 종료 창이 화면을 가린다).
5. 흐름을 단계마다 실행한다.
6. 끝나면 이 스크립트가 띄운 서버·Redis·스텁 포트의 프로세스만 끈다.

조절: `E2E_SKIP_BACKEND_BUILD=1`, `E2E_SKIP_APP_BUILD=1`, `E2E_EMULATOR_WINDOW=1`(창 보기), `E2E_SERIAL`, `E2E_SERVER_PORT` 등(파일 머리 참고). 한 단계만: `e2e/run.sh 04_records`(앞 단계가 만든 상태가 필요하다).

## 결과(`e2e/out/`, git 제외)

| 파일 | 내용 |
|---|---|
| `summary.txt` | 단계별 `PASS`/`FAIL`, 연결 끊김으로 다시 돈 단계는 `RETRY` |
| `<단계>_*.png` | 단계 스크린샷(9장) |
| `<단계>.logcat.txt` | 단계 동안의 logcat(crash 버퍼 포함) |
| `<단계>.errors.txt` | 앱 프로세스의 E 줄과 FATAL EXCEPTION. 하나라도 있으면 그 단계는 실패 |
| `<단계>.xml` | JUnit 보고서 |
| `<단계>.maestro.txt` | Maestro 출력 |
| `maestro/<단계>/` | Maestro 산출물(commands.json, maestro.log, device-logcat) |
| `server.log`, `kakao_stub.log` | 서버·스텁 로그 |

## 알려진 한계

- 랜덤 대결·크루런 실시간·응원처럼 상대가 필요한 흐름은 넣지 않았다. 계정이 둘 필요하다(스텁 토큰 `e2e-<다른 숫자>`로 두 번째 기기·두 번째 빌드를 쓰면 확장할 수 있다).
- 워치 연동·푸시(FCM)·카카오맵 키가 필요한 지도 타일은 확인하지 않는다(키 없이 경로 선만 그린다).
- 가입 3단계는 앱 설계상 건너뛴다(1·2·4·5단계).
- GPS 경로:
  - Maestro `travel`은 속도가 지정 값과 맞지 않아(4 m/s로 주면 실제 약 16 m/s) 쓰지 않는다.
  - 대신 `scripts/route_next.js`가 벽시계 기준으로 지점을 계산하고, 약 1초마다 `setLocation`을 보낸다.
  - 직선 경로라 구간 페이스는 1km 구간 하나만 나온다.
- Windows에서 Maestro를 단계마다 새로 띄우면 adb가 잠깐 `device offline`이 될 때가 있다.
  - 드라이버는 첫 단계에서만 다시 설치한다(`--no-reinstall-driver`).
  - 연결 끊김으로 실패한 단계는 두 번까지 다시 돈다. 단언 실패는 다시 돌지 않는다.
  - 흐름은 다시 돌아도 되게 짰다(이미 가입·이미 크루·이미 켜진 설정이면 건너뛴다).
- 앱 오류 판정은 앱 프로세스의 `E` 로그 전체다. 의도된 오류 로그가 생기면 `run.sh`의 제외 목록(`hiddenapi|ashmem|EGL|HWUI`)에 더한다.
