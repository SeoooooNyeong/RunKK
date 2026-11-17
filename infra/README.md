# 서버 인프라

EC2 한 대(15GiB, 4코어)에 **prod**(베타, main 브랜치)와 **develop**(develop 브랜치) 두 환경을 따로 띄웁니다. 두 환경은 DB·Redis·api·ai·백업을 각자 가지고, nginx와 모니터링은 한 벌을 함께 씁니다.

```
                       ┌──────────── 에지 스택 (compose 프로젝트 runkk, docker-compose.edge.yml, 값 edge.env) ────────────┐
 앱 prod 플레이버 ──443──▶│ nginx ─ /grafana/ ─▶ grafana ─▶ prometheus ─┬─ api-prod:8081    (env=prod)                 │
 앱 develop 플레이버 ─8443─▶│   │                                          ├─ api-develop:8081 (env=develop)              │
 80: ACME + https 301 ──▶│   │  wiki.conf: appflowy.kktrkkt.dev ─▶ appflowy-nginx(같은 EC2의 AppFlowy 스택)             │
                       │   │                                          └─ node-exporter:9100                         │
                       └───┼──────────────── 공유 망 runkk_default ────────────────────────────────────────────────┘
               443 → api-prod │                                    8443 → api-develop
   ┌── 프로젝트 runkk-prod (값 .env, RUNKK_ENV=prod) ──┐    ┌── 프로젝트 runkk-develop (값 .env, RUNKK_ENV=develop) ──┐
   │ api(prod 프로필) ─ postgres ─ redis ─ ai ─ backup │    │ api(develop 프로필) ─ postgres ─ redis ─ ai ─ backup    │
   │ 볼륨 runkk-prod_postgres-data·redis-data          │    │ 볼륨 runkk_postgres-data·runkk_redis-data(분리 전 것)   │
   └───────────────────────────────────────────────────┘    └─────────────────────────────────────────────────────────┘
```

- api만 공유 망 `runkk_default`에 `api-<환경>` 별칭으로 붙습니다. postgres·redis·ai·backup은 자기 프로젝트 망에만 있어 다른 환경이 닿지 않습니다. 예외로 postgres는 Grafana만 함께 붙는 DB 전용 망 `runkk_db-<환경>`에도 있습니다(6절 "이벤트 현황", 읽기 전용 계정).
- 에지 프로젝트 이름은 분리 전과 같은 `runkk`입니다. 그래서 망 `runkk_default`(AppFlowy nginx가 붙어 있음), 인증서 볼륨 `runkk_letsencrypt`, 컨테이너 이름 `runkk-nginx-1`·`runkk-grafana-1`이 그대로 이어집니다.
- develop 환경은 분리 전 볼륨(`runkk_postgres-data`·`runkk_redis-data`)을 이어 씁니다. prod는 빈 DB에서 시작합니다.

```
infra/
├─ docker-compose.yml          환경 스택: api · ai · postgres · redis · backup (RUNKK_ENV마다 한 프로젝트)
├─ docker-compose.edge.yml     에지 스택: nginx · prometheus · grafana · node-exporter (+ certbot 프로필)
├─ .env.example                환경 스택 값 (develop·prod 각 한 벌, CI INFRA_ENV / INFRA_ENV_PROD)
├─ edge.env.example            에지 값 (서버에 한 벌, CI INFRA_ENV_EDGE)
├─ deploy.sh                   배포: 이미지 빌드 → 에지 맞추기 → 배포 직전 덤프 → 환경 스택 재시작 → 헬스체크
├─ smoke.sh                    배포 뒤 확인 (CI backend:smoke)
├─ restore-check.sh            매일 덤프 복구 시험 (CI backup:restore-check, 7절)
├─ offsite-backup.sh           매일 덤프 서버 밖 사본 (CI backup:offsite, 7절)
├─ dev-seed.sh, dev-seed.sql   develop DB 더미 데이터 (CI dev:seed, 수동, develop 전용, 4절)
├─ lib.sh                      위 스크립트가 함께 쓰는 함수
├─ init-letsencrypt.sh         Let's Encrypt 인증서 1회 발급 + HTTPS 전환
├─ backup/pg-backup.sh         backup 컨테이너가 도는 매일 pg_dump (7절)
├─ nginx/http/                 HTTP 설정(인증서 전·로컬): 80 → prod, 8443 → develop(평문)
├─ nginx/https/                HTTPS 설정: 80 → 301, 443 → prod, 8443 → develop. wiki.conf.template은 팀 위키 프록시
├─ nginx/common/               두 모드 공통 location 조각(api 경로, /grafana/)
├─ nginx/static/               공개 홈(/), 법정 고지 문서(/legal/*), 친구 대결 초대 안내(/invite/battle/*), App Links 검증(/.well-known/assetlinks.json)
└─ monitoring/                 Prometheus 수집 설정, Grafana 데이터소스·대시보드, 이벤트 현황 DB 읽기 전용 계정(grafana/runkk-db, 6절)
```

## 1. EC2 최초 세팅 (1회)

```bash
# Ubuntu 기준
sudo apt-get update && sudo apt-get install -y ca-certificates curl git
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER && newgrp docker
# Docker 29는 최소 API를 1.40으로 올려 Testcontainers(docker-java 1.32)를 거부한다. 그대로 두면 backend:test의
# 통합 시험(PostgreSQL·Redis)이 "Docker 없음"으로 조용히 건너뛰어진다. 러너와 같은 서버면 한 번 낮춰 둔다
echo '{"min-api-version": "1.24"}' | sudo tee /etc/docker/daemon.json && sudo systemctl restart docker
# UFW(기본 들어옴 거부)가 docker0로 들어오는 연결도 막아, CI 잡 컨테이너가 호스트에 연 Testcontainers 포트(Ryuk·PostgreSQL·Redis)에
# 못 붙는다. 외부가 아닌 docker0 브리지만 허용한다(22·80·443 규칙과 SSH는 그대로)
sudo ufw allow in on docker0 comment "CI job containers to host published ports (Testcontainers)"
# 배포 스크립트가 실행 중인 컨테이너가 연결할 설정 파일을 복사해 두는 고정 폴더(3절, RUNKK_HOME, 기본 /srv/runkk). docker 그룹이면 누구나 쓴다.
# 지금 서버는 sudo 없이 CI 변수 RUNKK_HOME=/home/gitlab-runner/runkk를 쓴다(러너 사용자 홈이라 따로 만들 필요 없음).
# /srv/runkk로 옮기려면 아래를 한 번 실행하고, 기존 폴더 내용을 복사한 뒤 CI 변수 RUNKK_HOME을 지운다
sudo install -d -m 2775 -g docker /srv/runkk

git clone https://lab.ssafy.com/s15-final/S15P31A207.git && cd S15P31A207/infra
cp edge.env.example edge.env && nano edge.env   # SERVER_NAME, GRAFANA_ADMIN_PASSWORD
cp .env.example .env && nano .env               # RUNKK_ENV, 볼륨 이름, DB_PASSWORD, REDIS_PASSWORD, JWT 키, RUNKK_INTERNAL_TOKEN
./deploy.sh
```

CI 러너(docker·deploy 태그 2개) 등록과 CI 변수는 `.gitlab-ci.yml` 머리 주석을 따릅니다.

Android 잡 이미지는 레지스트리가 없어 EC2 로컬 Docker에 둡니다. 러너 등록 뒤 한 번 만들고, docker 러너가 로컬 이미지를 쓰게 허용합니다.

```bash
cd ~/S15P31A207 && docker build -t runkk-android-sdk:37 ci/android   # 이후 ci/android가 바뀌면 android:ci-image 잡이 다시 만든다
# /etc/gitlab-runner/config.toml의 docker 러너 [runners.docker]를 아래처럼 둔다(러너가 자동으로 다시 읽는다)
#   allowed_pull_policies = ["always", "if-not-present"]
#   volumes = ["/var/run/docker.sock:/var/run/docker.sock", "/srv/gitlab-runner/cache:/cache"]
sudo mkdir -p /srv/gitlab-runner/cache
```

캐시는 호스트 폴더 `/srv/gitlab-runner/cache` 하나에 둡니다. 기본값(`"/cache"`)이면 동시 실행 슬롯(`concurrent`)마다 Docker 볼륨이 따로 생겨, 같은 브랜치라도 다른 슬롯에 걸린 잡은 빈 캐시(android 약 1.4GB)로 시작합니다.

**포트**: 밖에서 들어오는 포트는 22, 80, 443, 8443입니다. SSAFY 보안 그룹은 8443을 허용합니다(2026-10-24 Docker 임시 컨테이너로 확인). EC2 UFW는 22·80·443만 허용하지만, Docker가 게시한 포트(nginx 80·443·8443)는 UFW 규칙을 거치지 않으므로 UFW에 8443을 더하지 않아도 됩니다. 5432·6379·8080·8081(api 관리 포트)·9090(Prometheus)·9100(node-exporter)·3000(Grafana)은 호스트에 게시하지 않습니다(compose 망 안에서만 씀).

## 2. HTTPS 전환

SSAFY 배정 도메인(`k15a207.p.ssafy.io`)이 EC2를 가리키는지 확인한 뒤:

```bash
./init-letsencrypt.sh your@email.com
```

인증서를 받고 nginx 설정을 HTTPS 폴더로 바꿔 재시작합니다. 같은 인증서를 443(prod)과 8443(develop)이 함께 씁니다. 그 뒤로는 `deploy.sh`가 인증서가 있는지 보고 배포 때마다 HTTPS 설정을 고릅니다. 갱신은 스크립트가 출력하는 cron 한 줄을 등록합니다(`runkk_*` 볼륨과 `runkk-nginx-1` 컨테이너를 씁니다. 에지 프로젝트 이름이 분리 전과 같아 기존 cron을 그대로 둡니다).

## 3. 배포

| 브랜치 | 환경 | 주소 | CI 변수 | 배포 |
|---|---|---|---|---|
| develop | develop (`runkk-develop`, develop 프로필, Swagger 열림) | `https://k15a207.p.ssafy.io:8443` | `INFRA_ENV` | 병합하면 자동 |
| main | prod (`runkk-prod`, prod 프로필, Swagger 닫힘, 셀카 꺼짐) | `https://k15a207.p.ssafy.io` | `INFRA_ENV_PROD` | 파이프라인에서 수동 승인 |
| 둘 다 | 에지 (nginx·모니터링) | 위 두 주소, `/grafana/` | `INFRA_ENV_EDGE` | 두 배포가 모두 맞춤 |

`deploy.sh`가 하는 일(`./deploy.sh edge`는 2번만):

1. `../backend`·`../ai`로 이미지를 만듭니다. 태그는 환경별(`runkk-api:develop`·`runkk-api:prod`)이라 서로 덮어쓰지 않고, 직전 이미지는 `runkk-api:<환경>-prev`로 남깁니다.
2. 에지를 맞춥니다. 설정 파일을 `$RUNKK_HOME/edge/`에 복사하고(기본 `/srv/runkk`, 지금 서버는 `/home/gitlab-runner/runkk`), 새 nginx 설정을 일회용 컨테이너에서 `nginx -t`로 검사한 뒤 `up -d`로 바뀐 컨테이너만 다시 만들고, 돌고 있는 nginx는 템플릿을 다시 그려 `nginx -s reload`, Prometheus는 `/-/reload`로 다시 읽힙니다. 검사가 실패하면 돌고 있는 nginx를 건드리지 않고 멈춥니다. 분리 전 컨테이너(프로젝트 `runkk`의 api·ai·postgres·redis)가 있으면 여기서 정상 종료시켜 지웁니다(볼륨은 남김, 9절).
3. 배포 직전 덤프를 하나 뜹니다(Flyway가 되돌릴 수 없는 마이그레이션을 돌리기 전, 7절). 이 환경 postgres 데이터 볼륨이 있으면 postgres·backup 컨테이너만 먼저 올려(분리 후 첫 배포나 `down` 뒤에도) `pg-backup.sh now predeploy`로 덤프하고, 올리지 못하거나 덤프가 실패하면 배포를 멈춥니다. 볼륨이 없을 때(처음 배포)만 건너뜁니다. 덤프는 CI 작업 폴더 밖 `/var/backups/runkk/<환경>/`에 폴더 700·파일 600으로 남습니다.
4. 환경 스택 설정을 `$RUNKK_HOME/<환경>/`에 복사하고, 이 환경 postgres 볼륨을 다른 프로젝트 컨테이너가 쓰고 있으면 멈춥니다(같은 데이터 폴더에 postgres 두 개가 붙으면 DB가 깨짐). 그 뒤 `up -d --remove-orphans`(자기 프로젝트만)로 올리고 api·ai healthy와 nginx를 거친 `/actuator/health`를 기다립니다. api가 healthy가 되면(Flyway 뒤) Grafana 이벤트 현황용 읽기 전용 계정을 이 환경 DB에 맞춥니다(6절 "이벤트 현황", 실패해도 경고만).

- 한 환경 배포는 다른 환경 컨테이너를 다시 만들지 않습니다. 두 배포가 겹치지 않게 CI는 `resource_group: runkk-server`, 스크립트는 `$RUNKK_HOME/.deploy.lock`(flock)을 씁니다.
- 실행 중인 컨테이너는 CI 작업 폴더가 아니라 `$RUNKK_HOME/<스택>/`의 파일을 연결합니다. CI 작업 폴더는 다른 브랜치·MR 잡이 다시 받으므로(git checkout·clean) 거기를 연결하면 한 배포가 다른 환경의 설정 파일을 바꿔 버립니다. 저장소에서 파일을 지우면 `$RUNKK_HOME/`에서도 지웁니다(복사는 덮어쓰기만 함).
- 에지 설정은 마지막으로 배포한 브랜치의 것이 반영됩니다. nginx·모니터링 설정을 바꾸면 develop에서 확인한 뒤 main에도 곧 반영합니다(main 배포가 옛 설정으로 되돌리지 않게).
- `.env`를 통째로 컨테이너에 넣지 않습니다. `docker-compose.yml`의 `environment:`가 컨테이너마다 필요한 값만 고릅니다. 새 환경 변수는 `.env.example`과 함께 해당 서비스의 `environment:`에도 추가합니다.
- 스케줄러 스레드 `SCHEDULER_POOL_SIZE`(api, `application.yml spring.task.scheduling.pool.size`, 기본 27)는 넣을 때 27 이상으로 둡니다. api의 주기 작업이 27개(코치 대화 색인 만료, 크루런 매주 반복 회차 생성 `CrewRunRepeatJob` 포함)이고, 스레드가 작업 수보다 적으면 0시·3시처럼 같은 시각 작업과 1초 시계가 스레드를 서로 기다립니다. 주기 작업을 더하면 `application.yml` 기본값, compose 기본값, `.env`의 값을 함께 늘립니다(`BootstrapConfigTest`가 등록 작업 수와 기본값을 비교합니다).
- prod 환경 확인: `deploy.sh`는 `RUNKK_ENV=prod`인데 `SPRING_PROFILES_ACTIVE`가 prod가 아니거나 `SWAGGER_ENABLED=true`면 멈춥니다. `smoke.sh`는 prod에서 `/v3/api-docs`가 열려 있으면 실패합니다.
- 아바타 서버 확인: `.env`의 `COMPOSE_PROFILES`에 `avatar`가 있으면 `smoke.sh`가 `avatar` 컨테이너 헬스 상태가 healthy인지(starting이면 1분까지 기다림), ai 컨테이너에서 `http://avatar:3000/api/health`가 응답하는지 보고, 아니면 실패합니다. 프로필이 없으면 건너뜁니다. avatar 포트는 밖으로 열지 않습니다.

### 셀카 변환 스위치

`SELFIE_ENABLED`(api 환경 변수, `application.yml runkk.features.selfie-enabled`)는 서버 스위치입니다. false면 관리툴의 운영 기준값 "셀카생성사용"이 켜져 있어도 셀카 변환을 막고(SELFIE_DISABLED) 앱에는 셀카 단추가 보이지 않습니다. compose 기본값은 꺼짐입니다. develop `.env`와, 2차 배포(11/13)부터는 prod `.env`에도 `SELFIE_ENABLED=true`를 둡니다(2026-11-09 결정). 로컬 실행(local 프로필)과 시험은 켜진 채입니다.

### 2차 배포 기능 공개 스위치

크루, 아바타 꾸미기(체형·배경), 런바디 ai 분석, AI 코치는 관리툴 운영 기준값으로 엽니다. 처음 값만은 환경 변수 `RUNKK_BOOTSTRAP_SETTINGS`로 넣을 수 있습니다(아래).

| 운영 기준값 | 기본값 | 꺼져 있을 때 |
|-------------|--------|--------------|
| `CREW_RELEASED` | false | Runimal 동행 축이 크루런을 세지 않고, 크루 알림 16종은 알림함에만 저장하고 FCM 푸시를 보내지 않습니다 |
| `AVATAR_RELEASED` | false | 체형 변화(`BODY_SHAPE_CHANGED`)·배경 해금(`BACKGROUND_UNLOCKED`) 알림을 알림함에만 저장하고 FCM 푸시를 보내지 않습니다 |
| `BODY_SHAPE_RELEASED` | false | 2차 배포에서 뺀 체형 변화입니다. 0시 외형 재계산이 모든 회원을 기본 몸(`STAGE4`)으로 두고 체형 변화 알림을 만들지 않습니다. 피부색 계산은 그대로입니다. 2차 배포에서는 켜지 않습니다 |
| `RUNBODY_ENABLED` | false | 러닝 저장 뒤 런바디 분석 작업을 만들지 않고 ai에 제출·상태 조회하지 않습니다. `/api/v1/runbody/**`는 403 `FEATURE_CLOSED`입니다. 켜기 전에 저장한 러닝은 분석하지 않습니다 |
| `COACH_ENABLED` | false | AI 코치 질문과 대화 목록·대화 내용 조회가 403 `FEATURE_CLOSED`(「아직 열리지 않은 기능입니다」)입니다. 켜져 있어도 회원의 최신 LLM 전송 동의가 없으면 403 `CONSENT_REQUIRED`입니다(설계 D-440) |

DB에 행이 없으면 기본값(false)을 쓰므로 1차 배포(develop·prod)는 따로 넣지 않아도 닫혀 있습니다. 2차 배포에서 기능을 열 때 관리툴에서 true로 바꿉니다. 앱의 1차 배포 잠금 빌드(`LOCK_UNRELEASED`)도 같은 알림 종류를 숨기며, 두 분류는 계약 파일 `policy/GatedNotification.txt`로 대조합니다.

develop은 GitLab CI 변수 `INFRA_ENV`에 `RUNKK_BOOTSTRAP_SETTINGS=CREW_RELEASED=true,AVATAR_RELEASED=true,RUNBODY_ENABLED=true`를 넣고 배포하면 시작 때 그 키의 행이 없을 때만 한 번 넣습니다(감사 기록 행위자 `bootstrap:RUNKK_BOOTSTRAP_SETTINGS`). 이미 행이 있으면(관리툴로 바꾼 값) 덮지 않으므로 이후 변경은 관리툴로 합니다. 모르는 키나 형식이 틀린 값은 WARN 로그만 남기고 건너뜁니다.

### 롤백

- 보통은 GitLab에서 이전 성공 파이프라인의 `backend:deploy`를 다시 실행합니다(그 커밋으로 다시 빌드해 같은 환경에 올림).
- 빌드 없이 직전 이미지로 돌리려면 서버에서 `docker tag runkk-api:prod-prev runkk-api:prod`(ai도 같게) 뒤 `backend:deploy`를 다시 실행하지 않고 `docker compose -f $RUNKK_HOME/prod/docker-compose.yml --env-file <prod .env> up -d --no-deps api ai`를 씁니다. CI 작업 폴더에는 `.env`가 남지 않으므로 이 경우 `INFRA_ENV_PROD` 내용을 임시 파일로 두고 끝나면 지웁니다.
- DB 스키마가 바뀐 배포(Flyway)를 되돌릴 때는 배포 직전 덤프(`runkk-prod-predeploy-*.dump`)로 복구합니다(7절).
- 0.11.4(운영자 공지 알림, V35_3) 뒤에 덤프 복구 없이 0.11.3 이하 서버 이미지로만 되돌리면, 옛 코드가 `NOTICE` 알림 종류를 몰라 공지를 받은 회원의 알림함 조회가 500이 됩니다. 이미지를 되돌리기 전에 공지 행을 지웁니다(감사 기록 `SETTING_CHANGE` 대상 `NOTICE:제목`에 보낸 내용이 남습니다).

  ```sql
  BEGIN;
  SELECT count(*) FROM notification WHERE type = 'NOTICE';
  DELETE FROM notification WHERE type = 'NOTICE';
  COMMIT;
  ```

- 서버 시간대 수정(V48, DF-073)이 적용된 환경은 V48이 없는 서버 이미지(옛 시간대 설정: JVM UTC, `hibernate.jdbc.time_zone`)로 되돌리지 않습니다. 오류 없이 뜨지만(Flyway는 DB에만 있는 V48, V49를 문제 삼지 않습니다) 그 뒤 서버가 쓰는 시각이 다시 9시간 앞서 저장되어 V48로 고친 값과 섞입니다. 되돌려야 하면 V48 직전 덤프(`before-tz` 또는 그 배포의 `predeploy`)로 DB를 먼저 복구한 뒤 이미지를 되돌립니다. V48 적용 뒤에는 `-prev` 이미지 태그로 되돌리지 않고, 이전 파이프라인의 `backend:deploy`를 다시 실행하지 않으며, V48 이전에 develop에서 갈라진 `release/*` 브랜치에 push하지 않습니다(8443 자동 배포로 옛 이미지가 뜹니다).

### 서버 시간대 마이그레이션 V48 (DF-073, 설계 05-28)

`V48__server_timezone_kst.sql`은 서버 시간대 수정과 한 묶음입니다. 같은 이미지가 JVM 시간대를 Asia/Seoul로 맞추고(`RunkkApplication.main`, Dockerfile, compose) `hibernate.jdbc.time_zone`을 지우며, V48은 그동안 9시간 앞서 저장된 "서버가 쓴 시각"을 한 트랜잭션에서 9시간 뺍니다.

- 바꾸는 것: 서버가 JPA로 쓰는 `timestamp without time zone` 열 전부(V47 기준, 열 이름을 나열). `audit_record`는 추가 전용 트리거를 V48 안에서만 껐다가 다시 켭니다. `celebrity_record`는 관리 API로 등록한 행(감사 `CELEBRITY_REGISTER`가 있는 행)만, `event`는 기간을 모든 행에서 빼고 시드 두 행(`coach-challenge`, `beta-1km`)의 만든 시각은 9시간 더합니다.
- 바꾸지 않는 것: `timestamp with time zone` 열, `match_ticket` 대기열 시각, `flyway_schema_history`. 운영자가 SQL로 9시간 더해 넣은 값은 V48이 다루는 표면 함께 되돌아가지만, 다루지 않는 행(SQL로 넣은 유명인 기록)은 9시간 앞선 채 남으므로 적용 전에 찾아 따로 고칩니다.
- 후속 V49(`V49__event_seed_fresh_db.sql`): 임시 보정이 없던 새 DB(시험 컨테이너, 새 환경)에서 V48이 틀어 놓은 이벤트 시드 기간과 만든 시각만 되돌립니다. develop과 운영은 보정 상태라 V49가 바꾸는 행이 없습니다. V48은 develop에 이미 적용되어(2026-11-09) 체크섬 때문에 고치지 않습니다.
- 배포 때 주의
  - V48과 새 시간대 설정은 한 이미지로만 배포합니다. 옛 설정 api가 V48 뒤에 쓰면 그 값이 다시 9시간 앞섭니다(되돌릴 때는 위 "롤백").
  - 운영은 2차 배포에서 V36~V49를 차례로 받습니다. V48을 다른 번호(예: 핫픽스 V35_4)로 main에 먼저 넣지 않습니다. 역병합 뒤 develop이 순서 밖으로 다시 적용해 두 번 뺍니다.
  - 적용 전에 덤프를 하나 더 뜹니다(`pg-backup.sh now before-tz`, `deploy.sh`의 `predeploy` 덤프와 별도). 되돌림은 V48 역 SQL이 아니라 이 덤프 복구를 기본으로 합니다.
  - 적용 뒤 확인: `flyway_schema_history`의 V48·V49가 success, 미래 시각 행 0건(`member.last_access_at`, `run_record.ended_at`, `audit_record.recorded_at` > `now()`), `docker exec runkk-<환경>-api-1 date`가 KST, 감사 트리거 `trg_audit_record_reject_update`가 다시 켜짐(`tgenabled = 'O'`). 쿼리 전체는 설계 05-28 7절에 있습니다.
  - 운영 적용을 확인하면 4절 운영 주의(9시간 규칙)를 「DB 시각은 한국 시각 그대로 저장한다」로 바꾸고 DF-073을 닫습니다.

## 4. 운영 명령

CI 작업 폴더에는 `.env`가 남지 않으므로 서버에서는 컨테이너 이름으로 다룹니다(`runkk-<환경>-<서비스>-1`, 에지는 `runkk-<서비스>-1`).

```bash
docker ps --filter name=runkk-                       # 전체 상태
docker logs -f --tail 100 runkk-prod-api-1           # prod api 로그 (develop은 runkk-develop-api-1)
docker exec -it runkk-prod-postgres-1 psql -U runkk runkk   # prod DB 접속
docker exec runkk-nginx-1 nginx -t && docker exec runkk-nginx-1 nginx -s reload   # nginx 설정 다시 읽기
```

운영 주의(결함 DF-073, 서버 시간대): develop은 2026-11-09 V48 적용 뒤 서버가 쓴 시각과 SQL로 넣는 시각 모두 한국 시각 그대로 저장합니다(SQL로 넣을 때 9시간을 더하지 않습니다). 운영은 2차 배포(V36~V49, 서버 시간대 수정 V48 포함) 전까지 옛 규칙입니다: API 컨테이너 JVM이 UTC인데 Clock과 `hibernate.jdbc.time_zone`이 Asia/Seoul이라 서버가 쓴 시각은 DB에 9시간 앞서 저장되므로 DB에서 읽을 때 9시간을 빼고, SQL로 시각을 넣을 때 의도한 한국 시각에 9시간을 더합니다. 운영 event 기간은 +9시간 임시 보정해 두었고 2차 배포의 V48이 되돌립니다(docs/sw/sqa/이벤트-공지-성.md 3절, docs/sw/design-oo/05-28-설계-서버시간대-성.md 6절).

### develop 더미 데이터 (dev:seed)

develop 파이프라인의 수동 잡 **dev:seed**를 누르면 `dev-seed.sh`가 develop DB(`runkk-develop-postgres-1`)에 `dev-seed.sql`을 한 트랜잭션으로 넣습니다. 잡은 develop 브랜치 파이프라인에만 생기고, 스크립트도 `.env`의 `RUNKK_ENV`가 develop이 아니면 멈춥니다.

- 넣는 것: 게스트형 더미 회원 18명(`더미러너01`~`18`, 로그인 수단 없음), 공개 크루 5개(`더미 …`, 크루원·다가오는/지난 크루런·채팅·주간 레이스), 더미 회원마다 최근 6주 유효 러닝 10~25회(경로 포함)와 그 기록으로 센 티어·레벨·Runimal.
- 대상 계정: 잡 변수 `DEV_SEED_TARGETS`(쉼표 구분 닉네임, 비우면 `러너4171,러너1699`)의 닉네임마다 그 회원이 있으면 `더미 새벽한강 크루`에 넣고(활성 크루가 없을 때만), 출생연도가 비었으면 1998로 채우고, 최근 6주 러닝 22회(경로·심박·움직임 표본)와 런바디 작업(PENDING)을 넣습니다. 없는 닉네임은 경고만 남깁니다. 기록 ID는 회원 ID와 순번으로 만들어 다시 돌려도 중복되지 않습니다.
- 다시 눌러도 이미 있는 더미 회원·크루·기록은 건너뜁니다.
- 랭킹 스냅숏은 0시 집계(또는 관리 API `POST /api/v1/admin/rankings/recompute`) 뒤에, 대상 계정의 티어 누계는 0시 10분 티어 보정 뒤에 반영됩니다. 런바디 작업은 서버가 5초 안에 ai로 보냅니다.

## 5. 로컬에서 같은 구성 띄우기

PC의 Docker에서도 같은 스크립트로 띄웁니다. 포트가 겹치면 `edge.env`의 `NGINX_*_PORT`를, 설정 복사 폴더는 `RUNKK_HOME`을 바꿉니다. HTTP 모드라 prod는 `NGINX_HTTP_PORT`, develop은 `NGINX_DEVELOP_PORT`(평문)입니다.

```bash
cd infra && cp edge.env.example edge.env && cp .env.example .env    # 값 채우기, BACKUP_DIR도 PC 경로로
RUNKK_HOME=$HOME/runkk-stacks ./deploy.sh
curl http://localhost:8443/actuator/health                         # develop (.env RUNKK_ENV=develop)
docker compose -p runkk-develop down -v && docker compose -p runkk down -v   # 정리(데이터까지 삭제)
```

- Docker Desktop은 node-exporter가 VM 루트(`/`)를 연결하지 못하므로 로컬 `edge.env`에만 `NODE_EXPORTER_HOST_ROOT=/tmp`, `NODE_EXPORTER_ROOT_PROPAGATION=rprivate`를 둡니다.
- Windows Git Bash에서는 `MSYS_NO_PATHCONV=1 RUNKK_HOME=C:/... ./deploy.sh`처럼 경로 변환을 끄고 `RUNKK_HOME`을 `C:/` 형식으로 줍니다.
- 로컬은 인증서가 없어 HTTP 모드입니다. HTTPS 모드를 시험하려면 `runkk_letsencrypt` 볼륨에 자체 서명 인증서(`live/<SERVER_NAME>/`, `appflowy-interim/`)를 넣고 `./deploy.sh edge`를 돌립니다. 이때 `deploy.sh`의 nginx 경유 헬스체크는 인증서 검증에 실패하므로 `curl -k`로 따로 확인합니다.

## 6. 모니터링 (Prometheus + Grafana)

에지 스택에 `prometheus`·`grafana`·`node-exporter`가 한 벌 있고 두 환경 api를 함께 읽습니다. 셋 다 호스트 포트를 열지 않고, Grafana만 nginx를 거쳐 prod 도메인(443)의 `/grafana/`에서 보입니다.

```
브라우저 ─ nginx :443 /grafana/ ─ grafana:3000 ─ prometheus:9090 ─┬─ api-prod:8081/actuator/prometheus     (env="prod")
                                                                 ├─ api-develop:8081/actuator/prometheus  (env="develop")
                                                                 └─ node-exporter:9100                    (EC2 호스트)
```

- 접속: `https://<SERVER_NAME>/grafana/`. `edge.env`의 `GRAFANA_ADMIN_USER`(기본 `admin`)·`GRAFANA_ADMIN_PASSWORD`로 로그인합니다. 익명 접근과 회원가입은 꺼져 있습니다.
- 대시보드: **런크크** 폴더의 `런크크 API`(위쪽 **환경** 선택: prod·develop), `런크크 서버 호스트`, `런크크 이벤트 현황`(아래 "이벤트 현황"). `monitoring/grafana/dashboards/*.json`이 원본이라 화면에서 고친 내용은 저장되지 않습니다.
- api 지표: develop·prod 프로필은 actuator를 관리 포트 8081에 따로 둡니다. 서비스 포트 8080에는 actuator가 없고, nginx는 바깥에 `/actuator/health` 하나만 8081로 넘기며 나머지 `/actuator/*`는 404입니다.
- 보존: Prometheus는 15일 또는 2GB 중 먼저 닿는 쪽까지 둡니다(`runkk_prometheus-data`). Grafana 설정·세션은 `runkk_grafana-data`에 있습니다.
- 메모리 제한: prometheus 512m, grafana 512m, node-exporter 64m.
- ai 서버 지표는 아직 수집하지 않습니다. ai는 Prometheus 라이브러리가 없고 모든 경로에 내부 토큰을 요구하므로, 붙이려면 의존성·잠금 파일과 토큰 예외 경로를 함께 바꿔야 합니다.
- 수집 대상 상태는 Grafana **Explore**에서 `up` 쿼리로 봅니다(`job="api"`의 `env="prod"`·`env="develop"`, `node`, `prometheus`가 1이면 정상). 한 환경이 아직 없으면 그 대상만 0입니다.

관리자 비밀번호는 `grafana-data` 볼륨이 처음 만들어질 때만 반영됩니다. 그 뒤 바꾸려면 CI 변수 `GRAFANA_ADMIN_PASSWORD`를 고치고 서버에서 한 번 실행합니다.

```bash
docker exec runkk-grafana-1 grafana cli admin reset-admin-password '<새 비밀번호>'
```

### 장애 알림

장애는 두 갈래로 알립니다. 둘 다 Mattermost **15기 자율 서울2반 › runkk-alerts**(비공개) 채널로 옵니다.

| 감시 | 무엇을 | 언제 울리나 | 못 잡는 것 |
|---|---|---|---|
| Grafana 알림 (`monitoring/grafana/provisioning/alerting/`) | prod api `up`, prod 5xx 비율, 호스트 디스크·메모리 | api 응답 없음 2분, 5xx 5% 초과 5분(분당 3건 이상일 때), 디스크·메모리 90% 초과 10분 | EC2 자체가 멈추거나 에지 스택이 죽은 경우 |
| 외부 가동 감시 (UptimeRobot 등, 팀 계정) | `https://<SERVER_NAME>/actuator/health` | 5분 간격 확인이 연속 실패 | 서버 안쪽 원인(로그로 본다) |

- 웹훅 주소는 GitLab CI 변수 `GRAFANA_ALERT_WEBHOOK_URL`(masked·protected)에 둡니다. 배포가 `edge.env` 끝에 붙이고, Grafana는 환경 변수로 읽습니다. 없으면 Grafana는 뜨고 알림 전송만 실패합니다.
- 규칙 파일을 고치면 배포가 Grafana 관리자 API로 다시 읽힙니다. 화면에서 고친 규칙은 저장되지 않습니다.
- 대응: 알림 스레드에 확인 시각과 복구 시각을 남깁니다. NFR-016(장애 확인 뒤 1시간 안 복구)의 기록이 됩니다.
- 시험: Grafana **Alerting › Contact points › mattermost-runkk-alerts › Test**로 채널에 시험 메시지를 보냅니다.
- 앱 비정상 종료는 Play Console **Android vitals**(비정상 종료·ANR)에서 봅니다. 앱에 크래시 수집 SDK를 넣지 않아 알림이 없으므로 베타 기간에는 담당이 매일 확인합니다. 집계가 하루쯤 늦고, Play 밖에서 설치한 APK의 비정상 종료는 잡지 못합니다.

### 이벤트 현황 (DB 읽기 전용)

`런크크 이벤트 현황` 대시보드는 Prometheus가 아니라 두 환경 DB를 직접 읽습니다(S15P31A207-275, 설계 `docs/sw/design-oo/05-29-설계-이벤트현황-성.md`). 이벤트별 응모자 수, 코치(셀럽)별 대결과 승리 수, 거리 이벤트(베타는 1km) 응모자 수, 이벤트 포인트 지급 건수와 합계, 일별 추이(새 응모자, 코치 대결, 지급), 응모자 닉네임 명단을 봅니다. 응모 판정은 서버 `EventEntrantService`와 같습니다(셀럽마다 첫 승리, 기준 거리 이상 유효 기록, 기간 양 끝 포함, 탈퇴 회원 제외).

```
grafana ─┬─ 망 runkk_db-prod (internal)    ─ postgres-prod:5432     (runkk-prod의 postgres)
         └─ 망 runkk_db-develop (internal) ─ postgres-develop:5432  (runkk-develop의 postgres)
           계정 runkk_grafana_ro: 읽기 전용 기본값, 연결 3개, 쿼리 15초, 아래 표와 열만 SELECT
```

- 망: DB 전용 망 두 개는 에지 스택이 만들고(internal, 밖으로 나가지 못함), 각 환경 postgres만 `postgres-<환경>` 별칭으로 붙습니다. api·redis·ai는 이 망에 없어 Grafana는 그 환경의 postgres 말고 닿지 않습니다.
- 계정: `monitoring/grafana/runkk-db/readonly-role.sql`. `deploy.sh`가 환경 배포마다 api healthy 뒤(Flyway 뒤) 그 환경 DB에만 돌려 계정을 만들거나 비밀번호를 맞추고, 권한을 목록대로 다시 맞춥니다. 읽는 것: `event`, `event_point_grant` 전체, `battle`(판정 열), `run_record`(회원, 시작 시각, 거리, 유효만. 경로·심박 없음), `member`(ID, 닉네임, 상태만), `celebrity_record`(ID, 가림 이름), `flyway_schema_history`(버전, 성공). 대시보드가 다른 표나 열을 읽어야 하면 이 파일과 설계를 함께 고칩니다.
- 비밀번호: GitLab CI 변수 `GRAFANA_DB_READONLY_PASSWORD`(masked·protected, 영숫자 16자 이상, 예 `openssl rand -hex 24`). 배포가 `edge.env` 끝에 붙이고, Grafana는 환경 변수로 읽어 데이터소스(`monitoring/grafana/runkk-db/datasources.yml`)에 씁니다. 두 환경이 같은 값을 씁니다. 값이 없거나 형식이 틀리면 데이터소스를 넣지 않고(이미 있으면 지움) 계정도 건드리지 않으며 배포는 계속됩니다. 계정 맞추기가 실패해도 경고만 남깁니다.
- 배포 순서 주의: 에지와 환경 스택은 마지막에 배포한 브랜치의 설정을 따릅니다. release/* 브랜치는 이 변경(S15P31A207-275 Grafana 이벤트 현황)이 develop에 병합된 뒤에 자릅니다. 병합 전에 갈라진 release/* 에 push하면 develop 환경(8443) 자동 배포가 옛 설정으로 develop postgres를 다시 만들어 DB 전용 망에서 떼고, grafana도 DB 망에서 떨어집니다(다음 develop 배포가 또 다시 만듭니다). 그 파이프라인의 옛 복구 시험도 이 계정 권한에서 멈춥니다. main 배포(핫픽스)도 이 변경이 main에 들어가기 전에는 grafana를 DB 망에서 뗍니다(대시보드만 영향).
- 처음 켜기와 비밀번호 바꾸기: CI 변수를 넣거나 바꾼 뒤 develop 배포(파이프라인 `backend:deploy` 재실행)와 prod 배포를 한 번씩 돌립니다. 에지(데이터소스)는 어느 배포든 맞추지만 계정은 그 배포의 환경 DB에만 맞추므로, 두 배포가 모두 끝나야 두 데이터소스가 붙습니다. 한쪽만 돌면 다른 쪽 데이터소스는 인증 오류로 보입니다.
- 처음 배포 한 번은 postgres 컨테이너가 다시 만들어집니다(DB 전용 망이 붙으므로, 볼륨은 그대로). 배포 직전 덤프 단계에서 몇 초 끊깁니다. 이때 아직 옛 api가 돌고 있어 그 사이 요청은 실패하고 연결 풀이 다시 붙습니다. 정상 종료는 60초까지 기다립니다(compose `stop_grace_period`). 복구 시험(7절)은 이 계정도 미리 만들어 복원합니다.
- 시각: 운영 DB는 2차 배포(V48) 전까지 서버가 쓴 시각이 9시간 앞서 저장됩니다(3절 운영 주의). 응모 판정과 개수는 저장 값끼리 비교하므로 V48 전후 모두 맞습니다. 화면에 보이는 시각과 일별 날짜만 대시보드 위쪽 **시각 보정**으로 맞춥니다. 기본 `자동`은 V48이 없는 DB만 9시간 빼서 보여 줍니다. 대시보드 시간대는 Asia/Seoul입니다.
- 보안 영향: Grafana가 털리면 두 환경 DB를 위 표와 열만큼 읽을 수 있습니다(쓰기 불가, 닉네임까지, 경로·이메일 없음). Grafana 편집 권한이 있는 사람은 Explore로 같은 범위의 SQL을 돌릴 수 있습니다. 그래서 Grafana 계정은 지금처럼 관리자 하나만 두고, 계정을 늘릴 때는 Viewer로 줍니다. DB 전용 망에서 postgres 포트에 닿으므로 다른 DB 계정 비밀번호도 길게 둡니다.
- 확인: Grafana **Connections › Data sources › runkk-db-prod / runkk-db-develop › Save & test**가 `Database Connection OK`. DB에서는 `docker exec runkk-prod-postgres-1 sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "\du runkk_grafana_ro"'`.
- 끄기: CI 변수를 지우고 배포하면 데이터소스가 빠집니다. 계정까지 지우려면 각 환경 DB에서 `DROP OWNED BY runkk_grafana_ro; DROP ROLE runkk_grafana_ro;`를 운영자가 직접 돌립니다.

### 메모리 배분 (15GiB, 4코어)

| 대상 | 제한 | 비고 |
|---|---|---|
| api ×2 | 1536m씩(`API_MEM_LIMIT`) | 힙은 제한의 60%(`-XX:MaxRAMPercentage=60`, 약 0.9GiB), 메모리 고갈 때 종료 후 재시작(`ExitOnOutOfMemoryError`) |
| ai ×2 | 512m씩 | 외부 API 중계 |
| redis ×2 | maxmemory 256mb씩 | |
| postgres ×2 | 제한 없음 | 베타 규모에서 수백 MiB |
| backup ×2 | 256m씩 | 하루 한 번 pg_dump |
| 에지 | prometheus 512m, grafana 512m, node-exporter 64m, nginx | |

최악 합계는 약 6GiB이고, 분리 전 측정 사용량은 1.9GiB였습니다. 나머지는 CI 빌드(Gradle·Android)와 AppFlowy 스택 몫입니다.

## 7. 백업과 복구 (NFR-018·019·037)

각 환경의 `backup` 컨테이너가 매일 `BACKUP_TIME`(기본 04:30, 3시 파기 배치 뒤)에 `pg_dump -Fc`를 뜨고, `pg_restore --list`로 읽히는지 확인한 뒤 `/var/backups/runkk/<환경>/runkk-<환경>-daily-YYYYMMDD-HHMMSS.dump`로 둡니다. 7일(`BACKUP_KEEP_DAYS`)이 지난 덤프는 지워 "오늘 + 지난 6일" 7개만 남습니다(처리방침 "하루 1회 이상 백업, 백업본 7일 보존"). 배포 직전 덤프(`-predeploy-`)도 같은 폴더·같은 보존 기간입니다. 폴더는 0700, 덤프는 0600이고 소유자는 `BACKUP_OWNER`(기본 1000:1000 = ubuntu)입니다.

같은 시각에 아바타 결과 볼륨(`avatar-data`, 사용자별 머리 그림)도 `runkk-<환경>-avatar-daily-YYYYMMDD-HHMMSS.tar.gz`로 같은 폴더에 남습니다. 보존 기간·권한·소유자는 덤프와 같고, `tar -t`로 읽히는지 확인한 뒤에만 이름을 바꿉니다. backup 컨테이너는 볼륨을 읽기 전용(`/avatar-data`)으로만 붙이고, avatar 프로필이 꺼진 환경은 볼륨이 비어 있어 건너뜁니다. 배포 직전 덤프(`now predeploy`)는 DB만 뜨고, 수동 `now manual`·`now before-restore`는 아바타 묶음도 남깁니다.

```bash
ls -l /var/backups/runkk/prod/                                        # 덤프·아바타 묶음 목록
docker logs --tail 20 runkk-prod-backup-1                             # "ok ..." 또는 "FAILED ..." 기록
docker exec runkk-prod-backup-1 sh /opt/runkk-backup/pg-backup.sh now manual   # 지금 하나 뜨기
```

### 서버 밖 사본

00:00 스케줄 파이프라인의 **backup:offsite** 잡이 prod·develop의 가장 최근 매일 덤프를 GitLab 아티팩트로 올립니다(`offsite-backup.sh`). EC2가 통째로 사라져도 GitLab(lab.ssafy.com)에 하루 전 덤프가 남습니다.

- 한 번만 돌게 release 기간(`RELEASE_FREEZE=1`)에는 release 스케줄, 아니면 develop 스케줄에서 돕니다. 스케줄이 04:30 덤프보다 먼저 돌아 아티팩트는 전날 04:30 덤프입니다.
- 아티팩트는 7일 뒤 만료되고 Developer 이상만 내려받습니다(처리방침 "백업본 7일 보존"). **Settings › CI/CD › Artifacts › Keep artifacts from most recent successful jobs**를 꺼 둬야 마지막 사본도 7일 뒤 지워집니다.
- 덤프가 26시간보다 오래됐으면 잡이 실패합니다(매일 백업이 멈춤). 아직 덤프가 없는 환경(prod 첫 배포 전)은 경고만 남깁니다.
- 가장 최근 아바타 묶음(`-avatar-daily-`)도 같은 규칙으로 올립니다(26시간보다 오래되면 실패, 없는 환경은 건너뜀). 아티팩트 크기 제한에 걸려 덤프 사본까지 잃지 않게 `OFFSITE_AVATAR_MAX_MB`(기본 50)보다 큰 묶음은 경고만 남기고 뺍니다. 그때 아바타는 서버 안 7일치만 남습니다.
- 서버가 사라졌을 때: 마지막 성공한 backup:offsite 잡의 아티팩트를 내려받아 새 서버에서 아래 "실제 복구" 절차로 넣습니다.

### 복구 리허설 (베타 기간 1회, 운영 DB를 건드리지 않음)

GitLab 파이프라인의 수동 잡 **backup:restore-check**를 누릅니다(develop·release/* 파이프라인은 develop 환경, main 파이프라인은 prod 환경). `restore-check.sh`가 가장 최근 매일 덤프(`-daily-`)를 일회용 postgres 컨테이너(망 없음)에 복원하고, 운영 DB와 테이블별 행 수를 나란히 찍은 뒤 컨테이너를 지웁니다. 운영 DB는 `count(*)`로 읽기만 합니다.

- 통과: 매일 덤프가 26시간 안에 있음(NFR-019), `pg_restore` 오류 없음, 테이블 목록이 운영과 같음, 복원한 행이 1개 이상.
- 덤프 뒤에 마이그레이션이 든 배포가 있었으면 Flyway 버전이 달라 테이블 목록은 비교하지 않고 경고만 남깁니다. 행 수는 덤프 뒤 쌓인 자료 때문에 운영 쪽이 많을 수 있어 판정에 쓰지 않습니다.
- 결과(잡 번호, 덤프 이름, 통과 여부)는 test.md 8장에 한 줄 남깁니다.
- 익명 볼륨 정리: `restore-check.sh`와 `offsite-backup.sh`는 끝나면 일회용 postgres 컨테이너를 `docker rm -fv`로 지웁니다. postgres 이미지가 컨테이너마다 익명 데이터 볼륨을 만들어 `docker rm -f`로는 볼륨이 남기 때문입니다(2026-11-09 서버에서 복원한 운영 백업 사본 10개와 빈 볼륨 12개를 찾아 지운 뒤 고침). 복원 사본이 남으면 백업 7일 보존(NFR-037)과 탈퇴 자료 파기(NFR-026)를 어길 수 있으므로, 아래처럼 손으로 리허설할 때도 `-v`를 붙여 지웁니다. 남은 것은 `docker volume ls -qf dangling=true`로 봅니다.

서버에서 손으로 할 때는 아래처럼 합니다(ubuntu 사용자). 최신 기록 시각과 덤프 시각의 차이가 24시간 안이면 NFR-019를 만족합니다.

```bash
F=$(ls -t /var/backups/runkk/prod/runkk-prod-*.dump | head -n 1); echo "$F"
docker run -d --name runkk-restore-rehearsal -e POSTGRES_PASSWORD=rehearsal postgres:17-alpine
until docker exec runkk-restore-rehearsal pg_isready -U postgres >/dev/null 2>&1; do sleep 1; done
docker exec runkk-restore-rehearsal createdb -U postgres runkk
for r in runkk runkk_ai runkk_grafana_ro; do docker exec runkk-restore-rehearsal psql -U postgres -qc "CREATE ROLE $r NOLOGIN"; done   # 덤프의 GRANT 대상(앱 계정 이름이 runkk가 아니면 바꾼다)
docker exec -i runkk-restore-rehearsal pg_restore -U postgres -d runkk --no-owner --exit-on-error < "$F"
docker exec runkk-restore-rehearsal psql -U postgres -d runkk -c "ANALYZE" \
  -c "SELECT relname, n_live_tup FROM pg_stat_user_tables ORDER BY n_live_tup DESC LIMIT 10" \
  -c "SELECT max(installed_rank) AS flyway_version_rank, max(installed_on) AS last_migration FROM flyway_schema_history"
docker rm -fv runkk-restore-rehearsal                                # -v: 복원한 사본이 든 익명 볼륨까지 지운다
```

### 실제 복구 (장애 때)

```bash
F=/var/backups/runkk/prod/<고른 덤프>.dump
docker stop runkk-prod-api-1                                          # 쓰기를 멈춘다
docker exec runkk-prod-backup-1 sh /opt/runkk-backup/pg-backup.sh now before-restore   # 지금 상태도 남긴다
docker exec -i runkk-prod-postgres-1 sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner --single-transaction --exit-on-error' < "$F"
docker start runkk-prod-api-1 && docker logs -f --tail 50 runkk-prod-api-1   # Flyway validate 뒤 기동
```

덤프보다 뒤의 자료는 잃습니다. 기기에 남은 미전송 기록(FR-039)은 앱이 다시 보냅니다.

아바타 볼륨은 avatar를 멈추고 볼륨을 비운 뒤 묶음을 풀어 넣습니다(파일 소유자 node 1000은 묶음에 남아 그대로 돌아옵니다). 볼륨 이름은 `runkk-<환경>_avatar-data`입니다.

```bash
A=<고른 묶음>.tar.gz   # 예: runkk-prod-avatar-daily-20261114-043000.tar.gz
docker stop runkk-prod-avatar-1
docker run --rm --network none -v runkk-prod_avatar-data:/data -v /var/backups/runkk/prod:/backups:ro --entrypoint sh postgres:17-alpine -c "find /data -mindepth 1 -delete && tar -xzf /backups/$A -C /data"
docker start runkk-prod-avatar-1
```

## 8. 로그 보관 (NFR-020)

- **api 파일 로그**: 요청 오류·장애 기록을 `api-logs` 볼륨(`runkk-<환경>_api-logs`)의 `/var/log/runkk/api.log`에 남기고 날마다 `api.YYYY-MM-DD.N.log.gz`로 돌립니다. 30일이 지난 파일은 지웁니다(NFR-020 14일 이상, 처리방침 "접속·요청 오류·장애 기록 30일"). 파일 하나 50MB, 환경당 합계 2GB 상한. 배포로 컨테이너를 다시 만들어도 볼륨이라 남습니다.
- **컨테이너 로그**: 모든 서비스가 json-file 드라이버로 20MB × 5개까지 돌립니다(디스크 보호). 컨테이너를 다시 만들면 사라지므로 오래 둘 기록은 위 파일 로그를 봅니다. nginx 접속 기록은 이 컨테이너 로그에만 있어 nginx가 다시 만들어지면 사라집니다.

```bash
docker exec runkk-prod-api-1 ls -l /var/log/runkk/
docker exec runkk-prod-api-1 sh -c 'zcat /var/log/runkk/api.2026-11-03.*.log.gz | grep -E " (ERROR|WARN) "'
```

## 9. 서버 분리 이관 (develop 단일 스택 → develop + prod, 1회)

분리 전 서버는 프로젝트 `runkk` 하나에 nginx·api·ai·postgres·redis가 있고 develop 프로필로 443에서 돌았습니다. 이관 뒤 develop은 8443으로 옮기고, 443은 main 배포(prod)가 차지합니다.

**준비 (병합 전)**

1. 현재 develop DB 덤프를 따로 떠 PC로 내립니다(이관 실패 대비):
   `docker exec runkk-postgres-1 pg_dump -U runkk -Fc runkk > ~/runkk-before-split.dump && scp`로 PC에 복사.
2. 서버에 고정 폴더를 만듭니다: `sudo install -d -m 2775 -g docker /srv/runkk`. sudo를 쓸 수 없으면 CI 변수 `RUNKK_HOME`에 러너가 쓸 수 있는 폴더(지금 서버는 `/home/gitlab-runner/runkk`)를 넣습니다
3. GitLab **Settings > CI/CD > Variables**:
   - `INFRA_ENV_EDGE`(File) 새로 추가: `SERVER_NAME`, `NGINX_HTTP_PORT`, `NGINX_HTTPS_PORT`, `NGINX_DEVELOP_PORT`, `GRAFANA_ADMIN_USER`. 비밀번호는 masked 변수 `GRAFANA_ADMIN_PASSWORD`, 알림 웹훅은 `GRAFANA_ALERT_WEBHOOK_URL`로 따로 둔다.
   - `INFRA_ENV`(develop)에 `RUNKK_ENV=develop`, `POSTGRES_VOLUME=runkk_postgres-data`, `REDIS_VOLUME=runkk_redis-data`, `SELFIE_ENABLED=true`(셀카 시험 시)를 더합니다. `RUNKK_AI_DB_PASSWORD`(ai 전용 DB 계정 비밀번호, 아래 prod 목록과 같은 규칙)도 필수입니다. `SERVER_NAME`·`NGINX_*`·`GRAFANA_*`는 이제 쓰지 않으니 지워도 됩니다.
   - `INFRA_ENV_PROD`(prod)를 새 값으로 채웁니다(아래 "prod 키 목록"). 다른 비밀(DB·Redis 비밀번호, JWT 키, 내부 토큰)은 develop과 다른 값으로 만듭니다.
4. 앱: develop APK를 새로 받기 전까지 기존 develop APK(443을 봄)는 이관 뒤 prod 서버에 붙습니다. 테스터에게 이관 시각과 새 develop APK(8443)를 알립니다.

**이관 (develop 병합)**

5. 이 MR을 develop에 병합하면 `backend:deploy`(develop)가 돕니다. `deploy.sh`가 에지를 새 설정으로 맞추면서 분리 전 api·ai·postgres·redis 컨테이너를 정상 종료해 지우고(볼륨 유지), 프로젝트 `runkk-develop`을 같은 볼륨으로 올립니다. 이 사이 1~2분 develop이 멈춥니다. 443은 prod가 뜰 때까지 api 경로가 502입니다(`/legal/*`·`/grafana/`는 됨).
6. 확인:
   - `curl https://k15a207.p.ssafy.io:8443/actuator/health` → UP
   - `docker exec runkk-develop-postgres-1 psql -U runkk runkk -c "select count(*) from flyway_schema_history"`가 이관 전과 같고, 회원·기록 수가 그대로인지 봅니다(자료 보존 확인).
   - `docker volume ls | grep runkk`에 `runkk_postgres-data`가 그대로 있고 `runkk-develop_postgres-data` 같은 새 develop DB 볼륨이 생기지 않았는지 봅니다.
   - 위키 `https://appflowy.kktrkkt.dev`가 열리는지 봅니다(망 `runkk_default`가 그대로라 AppFlowy nginx 연결이 유지됨).
   - Grafana `https://k15a207.p.ssafy.io/grafana/` → Explore `up{job="api"}`: develop 1, prod 0(아직 없음).

**prod 첫 배포 (main 생성)**

7. main 브랜치를 develop에서 만들고(아래 "main 만드는 시점") 파이프라인의 `backend:deploy`를 수동 승인하면 `runkk-prod`가 빈 DB로 뜹니다(Flyway가 스키마 생성).
8. 확인: `https://k15a207.p.ssafy.io/actuator/health` UP, `https://k15a207.p.ssafy.io/v3/api-docs` 404(smoke가 확인), prod 앱 플레이버로 게스트 시작·로그인, Grafana에서 prod 1, `docker logs runkk-prod-backup-1`에 "daily backup at 04:30".
9. 관리툴 첫 운영자는 prod `.env`의 `RUNKK_BOOTSTRAP_OPERATORS`로 넣습니다(prod DB는 새것).

**롤백 (이관을 되돌릴 때)**

- 새 develop 스택이 뜨지 않으면: develop 브랜치에서 이 MR을 되돌리는(revert) 커밋을 병합하기 전에, 서버에서 바로 옛 구성으로 돌립니다.
  ```bash
  docker compose -p runkk-develop down          # -v 금지. 볼륨 runkk_postgres-data는 남는다
  cd ~/S15P31A207 && git checkout 4100cf88 -- infra   # 또는 분리 전 커밋의 infra 폴더
  cd infra && cp <분리 전 develop .env> .env && ./deploy.sh   # 옛 deploy.sh가 프로젝트 runkk로 같은 볼륨을 다시 쓴다
  ```
  옛 구성은 nginx 443 → develop이므로 기존 develop APK가 다시 붙습니다. 그다음 MR을 revert합니다.
- DB가 손상됐으면 준비 1의 덤프로 복구합니다(7절 "실제 복구"의 명령에서 컨테이너 이름만 바꿈).

**main 만드는 시점**

- MR !14(v0.10, V20 DROP COLUMN 포함)를 develop에 병합하고 실기기 확인(sqa.md 8.1 필수 1·2)을 마친 develop 커밋에서 main을 만듭니다. 그 전에 만들면 베타 서버가 v0.10 이전 스키마로 시작했다가 곧 V20 마이그레이션을 다시 겪습니다.
- 순서: (1) 이 분리 MR과 모니터링 MR !17을 develop에 병합 → develop(8443) 안정 확인, (2) MR !14 병합 → develop 확인, (3) 그 커밋에서 `main` 생성(GitLab **Repository > Branches > New branch**, 보호 브랜치로 지정, 이후 develop → main MR로만 반영), (4) main 파이프라인의 `backend:deploy` 수동 승인, (5) `android:release`가 만든 prodRelease AAB를 Play 비공개 테스트에 올림. 11/02 전에 (4)까지 끝내고 하루 이상 prod를 지켜봅니다.

### prod 키 목록 (`INFRA_ENV_PROD`)

필수: `RUNKK_ENV=prod`, `POSTGRES_VOLUME=runkk-prod_postgres-data`, `REDIS_VOLUME=runkk-prod_redis-data`, `SPRING_PROFILES_ACTIVE=prod`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`, `ADMIN_JWT_SECRET`, `RUNKK_INTERNAL_TOKEN`, `RUNKK_AI_DB_PASSWORD`(영숫자, ai 전용 DB 계정 runkk_ai의 비밀번호로 Flyway V29가 첫 배포 때 계정을 만든다. ai `RUNKK_DB_URL`의 비밀번호와 같은 값. 빠지면 compose가 멈춘다), `FRIEND_INVITE_LINK_BASE=https://k15a207.p.ssafy.io/invite/battle`.
연동(비우면 그 연동만 꺼짐): `KAKAO_APP_ID`(prod 패키지 com.a207.runkk 앱), `GOOGLE_CLIENT_IDS`, `FCM_PROJECT_ID`, `FCM_CREDENTIALS_FILE=/run/secrets/fcm-service-account.json`, `KMA_SERVICE_KEY`, `RUNKK_BOOTSTRAP_OPERATORS`, ai의 `RUNKK_LLM_*`·`RUNKK_ANTHROPIC_*`·`RUNKK_FACE_*`.
넣지 않음: `SWAGGER_ENABLED`(넣으면 false만), `SERVER_NAME`·`NGINX_*`·`GRAFANA_*`(에지 값). 2차 배포(11/13)부터 `SELFIE_ENABLED=true`는 넣습니다.
선택: `DB_POOL_SIZE`, `SCHEDULER_POOL_SIZE`, `AUTH_CLIENT_REQUESTS_PER_MINUTE`, `RUNKK_QUEUE`, `RUNKK_LIVE`, `API_MEM_LIMIT`, `API_JAVA_OPTS`, `BACKUP_TIME`, `BACKUP_KEEP_DAYS`, `BACKUP_DIR`, `BACKUP_OWNER`, `LEGAL_*`.

## 10. 친구 대결 초대 링크 (v0.14, 설계 05-06 D-219, 05-08 D-293·6절)

초대 링크는 `https://k15a207.p.ssafy.io/invite/battle/{token}`이고 앞부분은 api 설정 `runkk.battle.friend-invite.link-base`(환경 변수 `FRIEND_INVITE_LINK_BASE`)다. 기본값이 없는 필수 값이라 환경마다 `.env`(CI `INFRA_ENV`, `INFRA_ENV_PROD`)에 넣는다. develop·release(8443)는 `https://k15a207.p.ssafy.io:8443/invite/battle`, prod(443)는 `https://k15a207.p.ssafy.io/invite/battle`이고, 비면 compose가 막고 api도 기동하지 않는다. 로컬(local 프로필)과 시험만 기본값이 있다. nginx 접근 로그는 `/invite/battle/{token}`과 by-token 경로의 토큰을 `***`로 가려 남긴다. nginx는 아래 세 경로를 정적 파일로 준다(`nginx/common/app-locations.conf`, api로 넘기지 않는다).

| 경로 | 파일 | 용도 |
|------|------|------|
| `/.well-known/assetlinks.json` | `nginx/static/.well-known/assetlinks.json` | Android App Links 도메인 검증(앱이 있으면 링크가 앱으로 열린다) |
| `/invite/card.png` | `nginx/static/invite/card.png` | 카카오톡 공유 카드 이미지(앱 링크 경로 `/invite/battle/` 밖에 둔다. 안에 두면 앱 링크가 그림 주소를 가로챈다. 피그마 C-11 카카오톡 초대 카드 그림(61002:2174), 800×400) |
| `/invite/battle/{token}` | `nginx/static/invite/battle/index.html` | 앱이 없을 때 보이는 스토어 안내 페이지(Android에서는 intent로 앱 열기를 다시 시도) |

**배포 전에 할 일**: `assetlinks.json`에는 운영 앱과 베타 앱만 둔다(결정 D66, 디버그 키와 `.local`은 넣지 않는다). Play 앱 서명 키 지문 3개와 업로드 키 지문을 채웠다(2026-10-30 Play Console 앱 서명 화면). 키를 바꾸면 이 파일도 바꾼다. 8443 링크도 포트를 구분하지 않으므로 이 파일로 검증된다.

- `com.a207.runkk`(prod): Play Console > 앱 무결성 > 앱 서명의 **앱 서명 키** 지문과 **업로드 키** 지문
- `com.a207.runkk.dev`: 업로드 키 지문(CI가 업로드 키로 서명한다)
- `com.a207.runkk.local`: 넣지 않는다. 로컬 빌드에서 초대 링크는 브라우저 안내 페이지로 열린다

확인: `curl -s https://k15a207.p.ssafy.io/.well-known/assetlinks.json`이 JSON(`Content-Type: application/json`)을 주고, 리다이렉트가 없어야 한다. 기기에서는 `adb shell pm get-app-links com.a207.runkk`로 `verified`를 본다. 카드 이미지가 더 큰 해상도로 필요하면 같은 이름으로 바꾸면 된다(앱은 주소만 쓴다).
