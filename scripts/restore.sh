#!/usr/bin/env sh
# Restaura um backup feito por scripts/backup.sh. APAGA o banco e as fotos atuais.
#
#   scripts/restore.sh ./backups/2026-11-01_0400
#
# Precisa do MESMO .env (as mesmas chaves) da época do backup: sem elas, textos de saúde e e-mails ficam ilegíveis.
set -eu

cd "$(dirname "$0")/.."
source_dir="${1:?informe o diretório do backup, ex.: ./backups/2026-11-01_0400}"
[ -f "$source_dir/banco.dump" ] || { echo "Não achei $source_dir/banco.dump"; exit 1; }

printf 'Isto apaga o banco e as fotos atuais e restaura %s. Digite RESTAURAR para continuar: ' "$source_dir"
read -r answer
[ "$answer" = "RESTAURAR" ] || { echo "Cancelado."; exit 1; }

docker compose stop backend frontend
# shellcheck disable=SC2016
docker compose exec -T postgres sh -c \
  'dropdb -U "$POSTGRES_USER" --if-exists "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$POSTGRES_DB"'
# shellcheck disable=SC2016
docker compose exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --no-privileges' < "$source_dir/banco.dump"

if [ -f "$source_dir/fotos.tar" ]; then
  docker compose run --rm --no-deps -T --entrypoint sh backend -c \
    'rm -rf /app/data/images && tar -C /app/data -xf -' < "$source_dir/fotos.tar"
fi

# O serviço db-roles devolve a posse das tabelas ao dono do schema e refaz as permissões do backend.
docker compose up -d
echo "Restaurado."
