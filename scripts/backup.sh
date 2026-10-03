#!/usr/bin/env sh
# Backup do weediário: banco (pg_dump) e fotos, num diretório com data.
#
#   scripts/backup.sh [diretório-de-destino]     (padrão: ./backups)
#
# As CHAVES do .env (WEEDIARIO_HEALTH_KEY, WEEDIARIO_EMAIL_KEY, WEEDIARIO_JWT_SECRET, WEEDIARIO_PSEUDONYM_KEY) NÃO
# entram no backup de propósito: guarde o .env num lugar separado (gerenciador de senhas, pendrive cifrado). Backup do
# banco + chave juntos no mesmo lugar anularia a cifra dos dados de saúde e dos e-mails.
#
# Mantém os últimos 14 backups. Para agendar todo dia às 4h: crontab -e
#   0 4 * * * cd /opt/weediario && scripts/backup.sh /var/backups/weediario >> /var/log/weediario-backup.log 2>&1
set -eu

cd "$(dirname "$0")/.."
dest="${1:-./backups}"
stamp="$(date +%Y-%m-%d_%H%M)"
target="$dest/$stamp"
umask 077
mkdir -p "$target"

# shellcheck disable=SC2016
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom' > "$target/banco.dump"
docker compose exec -T backend tar -C /app/data -cf - images > "$target/fotos.tar"

echo "Backup em $target: $(du -sh "$target" | cut -f1)"

# Guarda só os 14 mais recentes.
ls -1dt "$dest"/*/ 2>/dev/null | tail -n +15 | xargs -r rm -rf
