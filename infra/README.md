# 서버 인프라 (EC2 이관 가이드)

Docker Compose 한 벌로 nginx, api(Spring Boot 3.5, Java 21), PostgreSQL 17, Redis 8을 띄웁니다. 로컬 Docker에서 검증한 상태이며, EC2가 배정되면 아래 순서대로 옮기면 됩니다.

```
infra/
├─ docker-compose.yml        nginx · api · postgres · redis · ai · prometheus · grafana · node-exporter (+ certbot 프로필)
├─ nginx/http/default.conf.template        HTTP 리버스 프록시 (초기)
├─ nginx/https/default.conf.template       인증서 발급 후 쓰는 HTTPS 설정 (deploy.sh가 NGINX_MODE로 고름)
├─ nginx/https/wiki.conf.template          팀 위키(appflowy.kktrkkt.dev)를 같은 서버의 AppFlowy 스택으로 넘김. 인증서는 letsencrypt 볼륨 appflowy-interim/
├─ nginx/static/                           공개 홈(/)과 법정 고지 문서(/legal/privacy·terms·location·help), 두 설정이 모두 정적으로 보여 줌
├─ monitoring/                             Prometheus 수집 설정, Grafana 데이터소스·대시보드 provisioning (6절)
├─ .env.example              서버 환경 변수 목록 (.env는 커밋 금지)
├─ deploy.sh                 이미지 빌드 + 스택 재시작 + 헬스체크
└─ init-letsencrypt.sh       Let's Encrypt 인증서 1회 발급 + HTTPS 전환
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

git clone https://lab.ssafy.com/s15-final/S15P31A207.git && cd S15P31A207/infra
cp .env.example .env && nano .env     # SERVER_NAME, DB_PASSWORD, REDIS_PASSWORD, RUNKK_INTERNAL_TOKEN 반드시 변경(토큰 자리표시 값이면 ai가 뜨지 않음). DB_NAME·DB_USER·DB_PASSWORD·REDIS_PASSWORD가 비면 compose가 바로 멈춥니다. postgres·redis 포트는 내부 망 기본값(5432·6379)으로 고정입니다
./deploy.sh
curl http://localhost/actuator/health  # {"status":"UP"}
```

CI 러너(docker·deploy 태그 2개) 등록과 CI 변수는 루트 README "CI/CD" 절을 따릅니다. EC2 UFW는 22·80·443만 허용합니다.

Android 잡 이미지는 레지스트리가 없어 EC2 로컬 Docker에 둡니다. 러너 등록 뒤 한 번 만들고, docker 러너가 로컬 이미지를 쓰게 허용합니다.

```bash
cd ~/S15P31A207 && docker build -t runkk-android-sdk:37 ci/android   # 이후 ci/android가 바뀌면 android:ci-image 잡이 다시 만든다
# /etc/gitlab-runner/config.toml의 docker 러너 [runners.docker]를 아래처럼 둔다(러너가 자동으로 다시 읽는다)
#   allowed_pull_policies = ["always", "if-not-present"]
#   volumes = ["/var/run/docker.sock:/var/run/docker.sock", "/srv/gitlab-runner/cache:/cache"]
sudo mkdir -p /srv/gitlab-runner/cache
```

캐시는 호스트 폴더 `/srv/gitlab-runner/cache` 하나에 둡니다. 기본값(`"/cache"`)이면 동시 실행 슬롯(`concurrent`)마다 Docker 볼륨이 따로 생겨, 같은 브랜치라도 다른 슬롯에 걸린 잡은 빈 캐시(android 약 1.4GB)로 시작합니다.

보안 그룹은 22, 80, 443만 엽니다. 5432·6379·8080·8081(api 관리 포트)·9090(Prometheus)·9100(node-exporter)·3000(Grafana)은 외부에 열지 않습니다(compose 내부 네트워크만 사용).

## 2. HTTPS 전환

SSAFY 배정 도메인(예: `xxx.p.ssafy.io`)이 EC2를 가리키는지 확인한 뒤:

```bash
./init-letsencrypt.sh your@email.com
```

인증서를 받고 nginx 설정을 HTTPS 버전으로 바꿔 재시작합니다. 그 뒤로는 `deploy.sh`가 인증서가 있는지 보고 배포 때마다 HTTPS 설정을 고르므로, 저장소를 새로 받는 CI 배포에서도 HTTPS가 유지됩니다. 갱신은 스크립트가 출력하는 cron 한 줄을 등록하면 됩니다(작업 폴더와 무관하게 `runkk_*` 볼륨과 `runkk-nginx-1` 컨테이너를 씁니다). EC2 기본 호스트명(`*.amazonaws.com`)으로는 Let's Encrypt 발급이 안 되므로 도메인이 먼저 필요합니다.

## 3. 배포 (매번)

```bash
cd ~/S15P31A207 && git pull && cd infra && ./deploy.sh
```

- `deploy.sh`는 `../backend`·`../ai`를 빌드해 `runkk-api`·`runkk-ai` 이미지를 만들고 스택을 올린 뒤 api와 ai 헬스체크가 모두 통과할 때까지 기다립니다. 어느 하나라도 실패하면 로그를 출력하고 실패로 끝납니다.
- `.env`를 통째로 컨테이너에 넣지 않습니다. `docker-compose.yml`의 `environment:`가 컨테이너마다 필요한 값만 고릅니다(api는 DB·Redis·Spring 값과 `AI_SERVER_URL`·`RUNKK_INTERNAL_TOKEN`, ai는 `RUNKK_*`만). 새 환경 변수는 `.env.example`과 함께 해당 서비스의 `environment:`에도 추가합니다.
- GitLab CI의 `backend:deploy` 잡(EC2에 등록한 shell 러너, `deploy` 태그)이 CI File 변수 `INFRA_ENV`를 `infra/.env`로 복사한 뒤 이 스크립트를 실행합니다. 수동 배포도 같은 명령입니다(루트 README "CI/CD" 참고).
- develop 브랜치용 서버라면 `.env`의 `SPRING_PROFILES_ACTIVE=develop`으로 두면 Swagger(`/docs`)가 열리고 Flyway 마이그레이션(`backend/src/main/resources/db/migration`) 뒤 DDL validate로 동작합니다. prod도 같아서 스키마 변경은 마이그레이션으로만 반영됩니다.

## 4. 운영 명령

```bash
docker compose ps                       # 상태
docker compose logs -f api              # 로그
docker compose exec postgres psql -U runkk runkk   # DB 접속
docker compose down                     # 중지 (볼륨 유지)
docker compose down -v                  # 데이터까지 삭제 (주의)
```

## 5. 로컬에서 같은 스택 띄우기

PC에 Docker가 있으면 동일하게 검증할 수 있습니다. 80 포트가 쓰이고 있으면 `.env`에서 `NGINX_HTTP_PORT=8088`처럼 바꿉니다.

```bash
cd infra && cp .env.example .env && ./deploy.sh
curl http://localhost:8088/actuator/health
docker compose down -v
```

## 6. 모니터링 (Prometheus + Grafana)

같은 compose에 `prometheus`·`grafana`·`node-exporter`가 함께 뜹니다(`deploy.sh`가 이미지를 받고 올립니다). 셋 다 호스트 포트를 열지 않고, Grafana만 nginx를 거쳐 밖에서 보입니다.

```
브라우저 ─ nginx /grafana/ ─ grafana:3000 ─ prometheus:9090 ─┬─ api:8081/actuator/prometheus  (Spring Boot 관리 포트)
                                                            └─ node-exporter:9100            (EC2 호스트 CPU·메모리·디스크)
```

- 접속: `https://<SERVER_NAME>/grafana/` (HTTP 모드면 `http://<SERVER_NAME>/grafana/`). `.env`의 `GRAFANA_ADMIN_USER`(기본 `admin`)·`GRAFANA_ADMIN_PASSWORD`로 로그인합니다. 익명 접근과 회원가입은 꺼져 있습니다.
- 대시보드: **런크크** 폴더의 `런크크 API`(요청 수·응답 시간 p95·오류율·JVM 메모리·GC·CPU·DB 연결 풀)와 `런크크 서버 호스트`(CPU·메모리·디스크·부하). `monitoring/grafana/dashboards/*.json`이 원본이라 화면에서 고친 내용은 저장되지 않습니다. 바꾸려면 JSON을 고쳐 배포합니다.
- api 지표: develop·prod 프로필은 actuator를 관리 포트 8081에 따로 둡니다(`application.yml`). 서비스 포트 8080에는 actuator가 없고, nginx는 바깥에 `/actuator/health` 하나만 8081로 넘기며 나머지 `/actuator/*`는 404입니다. compose healthcheck도 8081을 부릅니다.
- 보존: Prometheus는 15일 또는 2GB 중 먼저 닿는 쪽까지 둡니다(`prometheus-data` 볼륨). Grafana 설정·세션은 `grafana-data` 볼륨에 있습니다.
- 메모리 제한: prometheus 512m, grafana 512m, node-exporter 64m. EC2(15GiB, 4코어)에서 api·postgres·CI 빌드와 같은 서버의 AppFlowy 스택을 지키는 값입니다.
- ai 서버 지표는 아직 수집하지 않습니다. ai는 Prometheus 라이브러리가 없고 모든 경로에 내부 토큰을 요구하므로, 붙이려면 의존성·잠금 파일과 토큰 예외 경로를 함께 바꿔야 합니다.

### 새 환경 변수

| 이름 | 필수 | 설명 |
|---|---|---|
| `GRAFANA_ADMIN_PASSWORD` | 예 | Grafana 관리자 비밀번호. 비면 compose가 멈춥니다(`docker compose ps`도 실패하므로 CI 배포·스모크가 모두 멈춥니다). `$`가 들어가면 compose가 변수로 읽으니 `openssl rand -base64 24`처럼 만듭니다 |
| `GRAFANA_ADMIN_USER` | 아니요 | 관리자 계정 이름. 기본 `admin` |

`GRAFANA_ROOT_URL`·`GRAFANA_COOKIE_SECURE`는 `.env`에 넣지 않습니다. `deploy.sh`가 nginx 모드(HTTP·HTTPS)를 보고 정합니다.

관리자 비밀번호는 `grafana-data` 볼륨이 처음 만들어질 때만 반영됩니다. 그 뒤 바꾸려면 CI 변수(`.env`)를 고치고 서버에서 아래 명령을 한 번 실행합니다(CI 작업 폴더에는 `.env`가 남지 않으므로 compose 대신 컨테이너 이름을 씁니다).

```bash
docker exec runkk-grafana-1 grafana cli admin reset-admin-password '<새 비밀번호>'
```

### 배포 순서 (처음 붙일 때)

1. GitLab **Settings > CI/CD > Variables**의 `INFRA_ENV`(develop)와 `INFRA_ENV_PROD`(main) 파일 내용에 `GRAFANA_ADMIN_PASSWORD=...`(필요하면 `GRAFANA_ADMIN_USER=...`)를 먼저 더합니다. 빠진 채로 병합하면 `backend:deploy`가 compose 단계에서 실패합니다.
2. MR을 develop에 병합하면 `backend:deploy`가 `deploy.sh`로 api 이미지를 다시 만들고(관리 포트 8081) 모니터링 컨테이너 3개를 함께 올립니다. 수동이면 `cd infra && ./deploy.sh`.
3. 확인: `https://<SERVER_NAME>/actuator/health`가 `UP`, `https://<SERVER_NAME>/actuator/prometheus`가 404, `https://<SERVER_NAME>/grafana/`에서 로그인 뒤 **Connections > Data sources > Prometheus > Test**가 성공하고 두 대시보드에 값이 나오는지 봅니다. 서버에서 `docker ps --filter name=runkk-`로 `runkk-prometheus-1`·`runkk-grafana-1`이 `healthy`인지도 봅니다.

수집 대상 상태는 Grafana **Explore**에서 `up` 쿼리로 봅니다(`job` 값 `api`·`node`·`prometheus`가 1이면 정상).

