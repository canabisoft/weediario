#!/usr/bin/env sh
# Backup do weediário: banco (pg_dump) e fotos, cifrados com age, num diretório com data.
#
#   scripts/backup.sh [diretório-de-destino]     (padrão: ./backups)
#
# Cifra: o backup traz apelidos, hashes de senha, diários e fotos PRIVADOS. Ele sai cifrado com a CHAVE PÚBLICA de
# WEEDIARIO_BACKUP_RECIPIENT (no .env): o servidor só consegue cifrar. A chave privada fica fora dele (no seu
# computador, no gerenciador de senhas); quem invadir o servidor ou o destino dos backups não lê nada. Os dados nunca
# passam pelo disco sem cifra: vão do pg_dump/tar direto para o age.
#   Gerar o par, no SEU computador:  age-keygen -o weediario-backup.key
#   Copie a linha "public key: age1..." para WEEDIARIO_BACKUP_RECIPIENT no .env do servidor.
#
# As CHAVES do .env (WEEDIARIO_HEALTH_KEY, WEEDIARIO_EMAIL_KEY, WEEDIARIO_JWT_SECRET, WEEDIARIO_PSEUDONYM_KEY) NÃO
# entram no backup de propósito: guarde o .env num lugar separado (gerenciador de senhas, pendrive cifrado). Backup do
# banco + chave juntos no mesmo lugar anularia a cifra dos dados de saúde e dos e-mails.
#
# Cópia fora do servidor (opcional): com WEEDIARIO_BACKUP_RCLONE=remoto:caminho no .env, cada backup também vai para
# lá com rclone. Prefira um bucket com retenção/object lock: quem tomar o servidor não consegue apagar as cópias.
#
# Mantém os últimos 14 backups locais. Para agendar todo dia às 4h: crontab -e
#   0 4 * * * cd /opt/weediario && scripts/backup.sh /var/backups/weediario >> /var/log/weediario-backup.log 2>&1
set -eu

cd "$(dirname "$0")/.."

# Do ambiente ou do .env (a última linha vale, como no docker compose).
setting() {
  eval "current=\${$1:-}"
  if [ -z "$current" ] && [ -f .env ]; then
    current="$(sed -n "s/^$1=//p" .env | tail -n 1)"
  fi
  printf '%s' "$current"
}

recipient="$(setting WEEDIARIO_BACKUP_RECIPIENT)"
rclone_remote="$(setting WEEDIARIO_BACKUP_RCLONE)"
if [ -z "$recipient" ]; then
  echo "WEEDIARIO_BACKUP_RECIPIENT não está no .env: sem a chave pública do age o backup não sai (ver o topo deste script)." >&2
  exit 1
fi
command -v age >/dev/null 2>&1 || { echo "Instale o age: sudo apt install age" >&2; exit 1; }

dest="${1:-./backups}"
stamp="$(date +%Y-%m-%d_%H%M)"
target="$dest/$stamp"
umask 077
mkdir -p "$target"
status="$(mktemp)"
trap 'rm -f "$status"' EXIT

# "sh" não tem pipefail: o código de saída de quem gera os dados vai para um arquivo e é conferido depois. Sem isso,
# um pg_dump que falhou geraria um backup vazio com cara de bom.
encrypt() {
  output="$1"
  shift
  { "$@"; echo "$?" > "$status"; } | age -r "$recipient" -o "$output"
  if [ "$(cat "$status")" != "0" ]; then
    echo "Falhou ao gerar $output" >&2
    rm -rf "$target"
    exit 1
  fi
}

# shellcheck disable=SC2016
encrypt "$target/banco.dump.age" \
  docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom'
encrypt "$target/fotos.tar.age" docker compose exec -T backend tar -C /app/data -cf - images
(cd "$target" && sha256sum banco.dump.age fotos.tar.age > SHA256SUMS)

echo "Backup cifrado em $target: $(du -sh "$target" | cut -f1)"

if [ -n "$rclone_remote" ]; then
  rclone copy "$target" "$rclone_remote/$stamp"
  echo "Cópia enviada para $rclone_remote/$stamp"
fi

# Guarda só os 14 mais recentes.
ls -1dt "$dest"/*/ 2>/dev/null | tail -n +15 | xargs -r rm -rf
