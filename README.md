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

### Publicando com HTTPS

O compose tem um perfil `https` com o Caddy na frente do nginx, que obtém e renova o certificado Let's Encrypt
sozinho. Aponte o DNS do domínio para o servidor, libere as portas 80 e 443 e rode:

```sh
WEEDIARIO_DOMAIN=weediario.exemplo.com docker compose --profile https up -d --build
```

Com `WEEDIARIO_DOMAIN=localhost` o Caddy usa uma CA local, útil para testar HTTPS na máquina. O Caddy não grava log
de acesso e não repassa o IP do visitante. O cookie de sessão é `Secure` e só trafega em HTTPS (ou em `localhost`).

### Privacidade na infraestrutura

- nginx e Caddy sem log de acesso e sem repassar o IP do visitante ao backend.
- Limite de tentativas de login por conta (chave HMAC do e-mail), sem usar IP.
- Containers somente leitura e sem capabilities; app, API e nginx rodam sem root (o Caddy só tem a permissão de
  abrir as portas 80/443).
- Segredos só no `.env` (fora do git), gerados aleatoriamente pelo script.
- Notificações guardam só ids (nunca quem reagiu ou quem seguiu) e são apagadas depois de 90 dias. O canal em
  tempo real (Server-Sent Events) só transmite o contador de não lidas.

### Moderação

O painel `/moderacao` (denúncias de comentários e cadastros da comunidade no catálogo) aparece para contas com papel
`MODERATOR` ou `ADMIN`. Ainda não há tela para promover alguém; use o banco e peça para a pessoa entrar de novo
(o papel vai no token de acesso):

```sh
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "update users set role = '"'"'MODERATOR'"'"' where username = '"'"'apelido'"'"'"'
```

O canal de notificações guarda as conexões abertas em memória: com mais de uma instância do backend, cada uma só
avisa quem está conectado nela (para escalar, trocar o `SseNotificationHub` por um pub/sub, como o LISTEN/NOTIFY
do Postgres).

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
