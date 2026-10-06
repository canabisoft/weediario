#!/usr/bin/env sh
# Restaura um backup feito por scripts/backup.sh. APAGA o banco e as fotos atuais.
#
#   scripts/restore.sh ./backups/2026-11-01_0400 ~/weediario-backup.key
#
# O segundo argumento é a CHAVE PRIVADA do age (a de age-keygen, que não fica no servidor): copie para cá só durante o
# restore e apague depois. Backups antigos, sem cifra (banco.dump), não pedem chave.
#
# Precisa do MESMO .env (as mesmas chaves) da época do backup: sem elas, textos de saúde e e-mails ficam ilegíveis.
set -eu

cd "$(dirname "$0")/.."
source_dir="${1:?informe o diretório do backup, ex.: ./backups/2026-11-01_0400}"
identity="${2:-}"

if [ -f "$source_dir/banco.dump.age" ]; then
  encrypted=1
  [ -n "$identity" ] || { echo "Backup cifrado: informe o arquivo da chave privada do age como segundo argumento." >&2; exit 1; }
  [ -f "$identity" ] || { echo "Não achei a chave $identity" >&2; exit 1; }
  command -v age >/dev/null 2>&1 || { echo "Instale o age: sudo apt install age" >&2; exit 1; }
  # Arquivo corrompido ou trocado é recusado antes de apagar qualquer coisa.
  (cd "$source_dir" && sha256sum -c --quiet SHA256SUMS) || { echo "Checksum não confere: backup corrompido?" >&2; exit 1; }
  # Também antes de apagar: a chave abre mesmo este backup?
  age -d -i "$identity" "$source_dir/banco.dump.age" | head -c 5 | grep -q PGDMP \
    || { echo "A chave não abre este backup." >&2; exit 1; }
elif [ -f "$source_dir/banco.dump" ]; then
  encrypted=0
else
  echo "Não achei banco.dump.age nem banco.dump em $source_dir" >&2
  exit 1
fi

printf 'Isto apaga o banco e as fotos atuais e restaura %s. Digite RESTAURAR para continuar: ' "$source_dir"
read -r answer
[ "$answer" = "RESTAURAR" ] || { echo "Cancelado."; exit 1; }

# Conteúdo de um arquivo do backup na saída padrão, decifrado se for o caso.
open_part() {
  if [ "$encrypted" = 1 ]; then
    age -d -i "$identity" "$source_dir/$1.age"
  else
    cat "$source_dir/$1"
  fi
}

has_part() {
  [ -f "$source_dir/$1.age" ] || [ -f "$source_dir/$1" ]
}

docker compose stop backend frontend
# shellcheck disable=SC2016
docker compose exec -T postgres sh -c \
  'dropdb -U "$POSTGRES_USER" --if-exists "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$POSTGRES_DB"'
# shellcheck disable=SC2016
open_part banco.dump | docker compose exec -T postgres sh -c \
  'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --no-privileges'

if has_part fotos.tar; then
  open_part fotos.tar | docker compose run --rm --no-deps -T --entrypoint sh backend -c \
    'rm -rf /app/data/images && tar -C /app/data -xf -'
fi

# O serviço db-roles devolve a posse das tabelas ao dono do schema e refaz as permissões do backend.
docker compose up -d
echo "Restaurado."
