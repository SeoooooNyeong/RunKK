#!/usr/bin/env bash
# 인증서 1회 발급(서버). 에지 스택이 80 포트로 떠 있고 edge.env의 SERVER_NAME이 이 서버를 가리켜야 한다.
# 사용: ./init-letsencrypt.sh you@example.com
# 같은 인증서를 443(prod)과 8443(develop)이 함께 쓴다(포트는 인증서와 무관하다).
set -euo pipefail
cd "$(dirname "$0")"
EMAIL="${1:?usage: ./init-letsencrypt.sh <email>}"
# shellcheck source=lib.sh
source ./lib.sh
load_edge_env
sync_bundle "$EDGE_DIR" docker-compose.edge.yml nginx monitoring

edge_compose run --rm certbot certonly --webroot -w /var/www/certbot \
  -d "$SERVER_NAME" --email "$EMAIL" --agree-tos --no-eff-email

# HTTPS 설정 폴더(nginx/https)로 nginx를 다시 만든다. 이후 배포는 deploy.sh가 인증서를 보고 같은 폴더를 고른다
NGINX_MODE=https edge_compose up -d --no-deps --force-recreate nginx
echo "HTTPS enabled for https://$SERVER_NAME (prod :443, develop :8443)"
# 갱신은 작업 폴더에 묶지 않는다(CI 작업 폴더는 배포마다 새로 받고 .env를 지운다). 에지 프로젝트 이름(runkk)의 볼륨과 컨테이너를 직접 쓴다.
# nginx -s reload 한 번으로 443·8443이 함께 새 인증서를 읽는다
echo "Renewal (add to crontab, weekly): docker run --rm -v runkk_certbot-www:/var/www/certbot -v runkk_letsencrypt:/etc/letsencrypt certbot/certbot renew -q && docker exec runkk-nginx-1 nginx -s reload"
