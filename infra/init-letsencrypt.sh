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
echo "Renewal (add to crontab, weekly): docker compose -f $(pwd)/docker-compose.yml --env-file $(pwd)/.env run --rm certbot renew && docker compose -f $(pwd)/docker-compose.yml restart nginx"
