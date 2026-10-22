#!/usr/bin/env bash
# One-time certificate issue on the server. Needs the stack running on port 80 and
# SERVER_NAME (from .env) pointing at this machine. Usage: ./init-letsencrypt.sh you@example.com
set -euo pipefail
cd "$(dirname "$0")"
EMAIL="${1:?usage: ./init-letsencrypt.sh <email>}"
source .env

docker compose --env-file .env run --rm certbot certonly --webroot -w /var/www/certbot \
  -d "$SERVER_NAME" --email "$EMAIL" --agree-tos --no-eff-email

cp nginx/https.conf.template nginx/templates/default.conf.template
docker compose --env-file .env restart nginx
echo "HTTPS enabled for https://$SERVER_NAME"
# 갱신은 작업 폴더에 묶지 않는다(CI 작업 폴더는 배포마다 새로 받고 .env를 지운다). compose 프로젝트 이름(runkk)의 볼륨과 컨테이너를 직접 쓴다
echo "Renewal (add to crontab, weekly): docker run --rm -v runkk_certbot-www:/var/www/certbot -v runkk_letsencrypt:/etc/letsencrypt certbot/certbot renew -q && docker exec runkk-nginx-1 nginx -s reload"
