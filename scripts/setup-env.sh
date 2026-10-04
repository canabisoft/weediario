#!/usr/bin/env sh
# Cria o .env com senhas e chaves aleatórias. Não sobrescreve um .env existente.
set -eu

cd "$(dirname "$0")/.."

if [ -f .env ]; then
  # .env antigo: só acrescenta chaves que surgiram depois, sem mexer nas existentes.
  added=0
  for name in WEEDIARIO_HEALTH_KEY WEEDIARIO_EMAIL_KEY; do
    if ! grep -q "^$name=" .env; then
      printf '%s=%s\n' "$name" "$(openssl rand -base64 32 | tr -d '\n')" >> .env
      echo "$name acrescentada ao .env (guarde uma cópia segura dela)."
      added=1
    fi
  done
  for name in WEEDIARIO_DB_OWNER_PASSWORD WEEDIARIO_DB_APP_PASSWORD; do
    if ! grep -q "^$name=" .env; then
      printf '%s=%s
' "$name" "$(openssl rand -hex 24)" >> .env
      echo "$name acrescentada ao .env."
      added=1
    fi
  done
  if [ "$added" = 0 ]; then
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
WEEDIARIO_DB_OWNER_PASSWORD=$(openssl rand -hex 24)
WEEDIARIO_DB_APP_PASSWORD=$(openssl rand -hex 24)
WEEDIARIO_JWT_SECRET=$(secret)
WEEDIARIO_PSEUDONYM_KEY=$(secret)
WEEDIARIO_HEALTH_KEY=$(openssl rand -base64 32 | tr -d '
')
WEEDIARIO_EMAIL_KEY=$(openssl rand -base64 32 | tr -d '\n')
WEEDIARIO_HTTP_PORT=8080
EOF

echo ".env criado. Suba com: docker compose up -d --build"
