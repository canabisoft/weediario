#!/usr/bin/env sh
# Cria o .env com senhas e chaves aleatórias. Não sobrescreve um .env existente.
set -eu

cd "$(dirname "$0")/.."

if [ -f .env ]; then
  # .env antigo: só acrescenta chaves que surgiram depois, sem mexer nas existentes.
  if ! grep -q '^WEEDIARIO_HEALTH_KEY=' .env; then
    printf 'WEEDIARIO_HEALTH_KEY=%s
' "$(openssl rand -base64 32 | tr -d '
')" >> .env
    echo "WEEDIARIO_HEALTH_KEY acrescentada ao .env (guarde uma cópia segura dela)."
  else
    echo ".env já existe; nada foi alterado."
  fi
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
WEEDIARIO_HEALTH_KEY=$(openssl rand -base64 32 | tr -d '
')
WEEDIARIO_HTTP_PORT=8080
EOF

echo ".env criado. Suba com: docker compose up -d --build"
