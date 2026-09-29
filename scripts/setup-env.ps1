# Cria o .env com senhas e chaves aleatórias. Não sobrescreve um .env existente.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root '.env'

if (Test-Path $envFile) {
    Write-Output '.env já existe; nada foi alterado.'
    exit 0
}

function New-RandomBytes([int] $count) {
    $bytes = New-Object byte[] $count
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    return $bytes
}

$lines = @(
    'WEEDIARIO_DB_USER=weediario'
    "WEEDIARIO_DB_PASSWORD=$(([System.BitConverter]::ToString((New-RandomBytes 24))) -replace '-', '')"
    "WEEDIARIO_JWT_SECRET=$([Convert]::ToBase64String((New-RandomBytes 48)))"
    "WEEDIARIO_PSEUDONYM_KEY=$([Convert]::ToBase64String((New-RandomBytes 48)))"
    'WEEDIARIO_HTTP_PORT=8080'
)
# UTF-8 sem BOM: o docker compose lê o arquivo literalmente.
[System.IO.File]::WriteAllLines($envFile, $lines, (New-Object System.Text.UTF8Encoding $false))

Write-Output '.env criado. Suba com: docker compose up -d --build'
