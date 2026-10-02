#!/usr/bin/env sh
# Confere se o frontend servido por um site é o mesmo código deste repositório (build reproduzível).
#
#   scripts/verify-frontend.sh https://weediario.exemplo
#
# Compila o frontend a partir do commit atual e compara, arquivo por arquivo, o SHA-256 do que foi gerado com o que
# o site entrega. O ngsw.json é ignorado: ele traz o horário do build e muda a cada compilação.
set -eu

site="${1:?informe a URL do site, ex.: https://weediario.exemplo}"
site="${site%/}"
root="$(cd "$(dirname "$0")/.." && pwd)"

cd "$root/frontend"
npm ci --silent
npx ng build >/dev/null
dist="$(dirname "$(find dist -name index.html | head -n 1)")"

diferentes=0
total=0
cd "$dist"
for file in $(find . -type f ! -name ngsw.json | sed 's#^\./##' | sort); do
  total=$((total + 1))
  local_hash="$(sha256sum "$file" | cut -d ' ' -f 1)"
  remote_hash="$(curl -fsS "$site/$file" | sha256sum | cut -d ' ' -f 1)" || remote_hash="(não encontrado)"
  if [ "$local_hash" != "$remote_hash" ]; then
    echo "DIFERENTE: $file"
    diferentes=$((diferentes + 1))
  fi
done

if [ "$diferentes" -eq 0 ]; then
  echo "OK: os $total arquivos servidos por $site são idênticos ao build deste repositório."
else
  echo "ATENÇÃO: $diferentes de $total arquivos diferem do build deste repositório."
  exit 1
fi
