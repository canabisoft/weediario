#!/usr/bin/env sh
# Cria/atualiza os papéis do banco (ver roles.sql) e define as senhas. Roda no serviço db-roles do compose, conectado
# como superusuário (PGHOST, PGUSER, PGPASSWORD e PGDATABASE vêm do ambiente).
set -eu

: "${WEEDIARIO_DB_OWNER_PASSWORD:?rode scripts/setup-env para acrescentar a senha ao .env}"
: "${WEEDIARIO_DB_APP_PASSWORD:?rode scripts/setup-env para acrescentar a senha ao .env}"

psql -X -q -v ON_ERROR_STOP=1 -f /roles/roles.sql

# As senhas entram por variável do psql (com aspas tratadas por ele), nunca montadas no texto do comando.
psql -X -q -v ON_ERROR_STOP=1 \
  -v owner_password="$WEEDIARIO_DB_OWNER_PASSWORD" \
  -v app_password="$WEEDIARIO_DB_APP_PASSWORD" <<'SQL'
ALTER ROLE weediario_owner PASSWORD :'owner_password';
ALTER ROLE weediario_app PASSWORD :'app_password';
SQL

echo "Papéis do banco conferidos."
