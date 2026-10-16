# 서버 인프라 (EC2 이관 가이드)

Docker Compose 한 벌로 nginx, api(Spring Boot), MySQL 8.4, Redis 8을 띄웁니다. 로컬 Docker에서 검증한 상태이며, EC2가 배정되면 아래 순서대로 옮기면 됩니다.

```
infra/
├─ docker-compose.yml        nginx · api · mysql · redis (+ certbot 프로필)
├─ nginx/templates/default.conf.template   HTTP 리버스 프록시 (초기)
├─ nginx/https.conf.template               인증서 발급 후 교체할 HTTPS 설정
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

git clone https://lab.ssafy.com/s15-final/S15P31A207.git && cd S15P31A207/infra
cp .env.example .env && nano .env     # SERVER_NAME, DB_PASSWORD, DB_ROOT_PASSWORD 반드시 변경
./deploy.sh
curl http://localhost/actuator/health  # {"status":"UP"}
```

보안 그룹은 22, 80, 443만 엽니다. 3306·6379·8080은 외부에 열지 않습니다(compose 내부 네트워크만 사용).

## 2. HTTPS 전환

SSAFY 배정 도메인(예: `xxx.p.ssafy.io`)이 EC2를 가리키는지 확인한 뒤:

```bash
./init-letsencrypt.sh your@email.com
```

인증서를 받고 nginx 설정을 HTTPS 버전으로 바꿔 재시작합니다. 갱신은 스크립트가 출력하는 cron 한 줄을 등록하면 됩니다. EC2 기본 호스트명(`*.amazonaws.com`)으로는 Let's Encrypt 발급이 안 되므로 도메인이 먼저 필요합니다.

## 3. 배포 (매번)

```bash
cd ~/S15P31A207 && git pull && cd infra && ./deploy.sh
```

- `deploy.sh`는 `../backend`를 Docker 멀티스테이지로 빌드해 `runkk-api` 이미지를 만들고 스택을 올린 뒤 api 헬스체크가 통과할 때까지 기다립니다.
- CI/CD(1.5-E)가 붙으면 이 스크립트를 GitLab CI의 deploy 단계에서 SSH로 호출합니다.
- develop 브랜치용 서버라면 `.env`의 `SPRING_PROFILES_ACTIVE=develop`으로 두면 Swagger(`/docs`)가 열리고 DDL이 update로 동작합니다. prod는 DDL validate라 스키마 변경은 마이그레이션으로만 반영됩니다.

## 4. 운영 명령

```bash
docker compose ps                       # 상태
docker compose logs -f api              # 로그
docker compose exec mysql mysql -uroot -p runkk   # DB 접속
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
