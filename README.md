# weediário

Diários de cultivo e reviews de genéticas com foco em privacidade: sem IP, sem localização e fotos sem metadados.

- `backend/`: Java 21, Spring Boot, PostgreSQL (Clean Architecture + DDD)
- `frontend/`: Angular 22, Tailwind CSS, PWA

## Subindo com Docker

Requisitos: Docker com Compose v2.

```sh
# 1. Gera o .env com senhas e chaves aleatórias (só na primeira vez)
sh scripts/setup-env.sh          # Linux/macOS/Git Bash
.\scripts\setup-env.ps1          # PowerShell

# 2. Sobe banco, backend e frontend
docker compose up -d --build
```

Acesse **http://localhost:8080**. Para usar outra porta, mude `WEEDIARIO_HTTP_PORT` no `.env`.

| Comando | O que faz |
| --- | --- |
| `docker compose ps` | Estado e saúde dos serviços |
| `docker compose logs -f backend` | Logs do backend |
| `docker compose down` | Para tudo (dados preservados) |
| `docker compose down -v` | Para tudo e **apaga** banco e fotos |

### Como os serviços se conectam

```
navegador ──► nginx (frontend, porta publicada) ──/api──► backend ──► postgres
```

Só o nginx fica exposto, e apenas em `127.0.0.1`. Backend e banco ficam na rede interna do Compose. Como o app e
a API estão na mesma origem, o cookie de sessão (`SameSite=Strict`) funciona sem CORS.

Para publicar na internet, coloque um proxy com HTTPS na frente do nginx (Caddy, Traefik ou o do seu provedor).
O cookie de sessão é `Secure` e só trafega em HTTPS (ou em `localhost`).

### Privacidade na infraestrutura

- nginx sem log de acesso e sem repassar o IP do visitante ao backend.
- Containers sem root, com sistema de arquivos somente leitura e sem capabilities.
- Segredos só no `.env` (fora do git), gerados aleatoriamente pelo script.

## Desenvolvimento local

```sh
# Banco no Docker, acessível em localhost:5432
docker compose --profile dev up -d postgres postgres-port

# Backend (usa as variáveis do .env)
cd backend && mvn spring-boot:run

# Frontend com recarga automática em http://localhost:4200 (proxy de /api para localhost:8080)
cd frontend && npm install && npm start
```

Testes: `mvn test` no `backend/` (PostgreSQL embarcado, sem Docker) e `npm test` no `frontend/`.
