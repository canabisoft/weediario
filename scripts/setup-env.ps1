# Cria o .env com senhas e chaves aleatórias. Não sobrescreve um .env existente.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root '.env'

function New-RandomBytes([int] $count) {
    $bytes = New-Object byte[] $count
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    return $bytes
}

if (Test-Path $envFile) {
    # .env antigo: só acrescenta chaves que surgiram depois, sem mexer nas existentes.
    $added = $false
    foreach ($name in @('WEEDIARIO_HEALTH_KEY', 'WEEDIARIO_EMAIL_KEY')) {
        if (-not (Select-String -Path $envFile -Pattern "^$name=" -Quiet)) {
            $line = "$name=$([Convert]::ToBase64String((New-RandomBytes 32)))"
            [System.IO.File]::AppendAllText($envFile, $line + "`n", (New-Object System.Text.UTF8Encoding $false))
            Write-Output "$name acrescentada ao .env (guarde uma cópia segura dela)."
            $added = $true
        }
    }
    foreach ($name in @('WEEDIARIO_DB_OWNER_PASSWORD', 'WEEDIARIO_DB_APP_PASSWORD')) {
        if (-not (Select-String -Path $envFile -Pattern "^$name=" -Quiet)) {
            $line = "$name=$(([System.BitConverter]::ToString((New-RandomBytes 24))) -replace '-', '')"
            [System.IO.File]::AppendAllText($envFile, $line + "`n", (New-Object System.Text.UTF8Encoding $false))
            Write-Output "$name acrescentada ao .env."
            $added = $true
        }
    }
    if (-not $added) {
        Write-Output '.env já existe; nada foi alterado.'
    }
    exit 0
}

$lines = @(
    'WEEDIARIO_DB_USER=weediario'
    "WEEDIARIO_DB_PASSWORD=$(([System.BitConverter]::ToString((New-RandomBytes 24))) -replace '-', '')"
    "WEEDIARIO_DB_OWNER_PASSWORD=$(([System.BitConverter]::ToString((New-RandomBytes 24))) -replace '-', '')"
    "WEEDIARIO_DB_APP_PASSWORD=$(([System.BitConverter]::ToString((New-RandomBytes 24))) -replace '-', '')"
    "WEEDIARIO_JWT_SECRET=$([Convert]::ToBase64String((New-RandomBytes 48)))"
    "WEEDIARIO_PSEUDONYM_KEY=$([Convert]::ToBase64String((New-RandomBytes 48)))"
    "WEEDIARIO_HEALTH_KEY=$([Convert]::ToBase64String((New-RandomBytes 32)))"
    "WEEDIARIO_EMAIL_KEY=$([Convert]::ToBase64String((New-RandomBytes 32)))"
    'WEEDIARIO_HTTP_PORT=8080'
)
# UTF-8 sem BOM: o docker compose lê o arquivo literalmente.
[System.IO.File]::WriteAllLines($envFile, $lines, (New-Object System.Text.UTF8Encoding $false))

Write-Output '.env criado. Suba com: docker compose up -d --build'
