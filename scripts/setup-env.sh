#!/usr/bin/env sh
# Cria o .env com senhas e chaves aleatórias. Não sobrescreve um .env existente.
set -eu

cd "$(dirname "$0")/.."

if [ -f .env ]; then
  echo ".env já existe; nada foi alterado."
  exit 0
fi

secret() {
  openssl rand -base64 48 | tr -d '\n'
}

umask 077
cat > .env <<EOF
WEEDIARIO_DB_USER=weediario
WEEDIARIO_DB_PASSWORD=$(openssl rand -hex 24)
WEEDIARIO_JWT_SECRET=$(secret)
WEEDIARIO_PSEUDONYM_KEY=$(secret)
WEEDIARIO_HTTP_PORT=8080
EOF

echo ".env criado. Suba com: docker compose up -d --build"
