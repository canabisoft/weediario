# Weediario (Plataforma Open-Source para Growers)

Plataforma colaborativa de diários de cultivo e reviews de genéticas voltada para a comunidade, construída com foco
absoluto em privacidade, anonimato e segurança.

Inspirada nas melhores plataformas do ecossistema, mas adaptada para garantir que os dados dos cultivadores estejam
blindados.

---

## Garantias de Privacidade e Segurança

Este projeto foi arquitetado sob premissas rígidas de segurança digital:

- **Zero log de localização e IP:** nenhuma tabela do banco armazena geolocalização, e os logs de acesso (nginx e
  Caddy) são desabilitados, sem repassar o IP do visitante ao backend.
- **Sanitização de EXIF:** o backend intercepta todo upload de foto, remove os metadados (GPS, data, modelo do
  celular) e gera variantes otimizadas em WebP (400px e 1600px).
- **Reações anônimas:** cada reação é gravada com um pseudônimo derivado por HMAC do par (usuário, alvo). O mesmo
  usuário em alvos diferentes gera pseudônimos diferentes e não relacionáveis.
- **Boas práticas OWASP:** senhas com Argon2id, tokens JWT de curta duração (10 min) com refresh token em cookie
  `HttpOnly`, `Secure` e `SameSite=Strict`, e payloads validados estritamente.

### Privacidade na infraestrutura

- nginx e Caddy sem log de acesso e sem repassar o IP do visitante ao backend.
- Limite de tentativas de login por conta (chave HMAC do e-mail), sem usar IP.
- Containers somente leitura e sem capabilities; app, API e nginx rodam sem root (o Caddy só tem a permissão de
  abrir as portas 80/443).
- Segredos só no `.env` (fora do git), gerados aleatoriamente pelo script.
- Notificações guardam só ids (nunca quem reagiu ou quem seguiu) e são apagadas depois de 90 dias. O canal em
  tempo real (Server-Sent Events) só transmite o contador de não lidas.

---

## Stack Tecnológica

- **Backend:** Java 21, Spring Boot, Spring Security, Spring Data JPA, PostgreSQL, Clean Architecture + DDD.
- **Frontend:** Angular, Tailwind CSS, PWA (ferramentas offline como calculadora de VPD e conversores).
- **Infraestrutura:** Docker e Docker Compose (containers sem root, somente leitura, isolados em rede interna).

---

## Como Rodar o Projeto Localmente

### Pré-requisitos

- Docker com Compose v2.

### Passo a passo

1. Clone o repositório:

   ```sh
   git clone https://github.com/canabisoft/weediario.git
   cd weediario
   ```

2. Gere o `.env` com senhas e chaves aleatórias (só na primeira vez):

   ```sh
   sh scripts/setup-env.sh          # Linux/macOS/Git Bash
   .\scripts\setup-env.ps1          # PowerShell
   ```

3. Suba banco, backend e frontend:

   ```sh
   docker compose up -d --build
   ```

4. Acesse a aplicação:
   - **Frontend:** http://localhost:8080 (nginx; para outra porta, mude `WEEDIARIO_HTTP_PORT` no `.env`).
   - **Backend API:** fica na rede interna do Docker, exposto só via reverse proxy em `/api`.

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

### Moderação

Regras em `/termos` (Termos de Uso, versão atual 1.0). O cadastro exige o aceite, e o banco guarda só a versão e a
data/hora. Ao mudar o texto, suba `TermsOfUse.CURRENT_VERSION` (backend) e `TERMS_VERSION` (frontend): quem aceitou uma
versão antiga continua lendo, baixando e apagando dados, mas não publica até aceitar a nova.

- **Filtro anti-comércio** (`CommerceFilter`): bloqueia telefone, CPF/CNPJ, chave PIX e links de WhatsApp/Telegram;
  segura para revisão textos com cara de anúncio ("vendo 5g", "chama no zap", "faço entrega"). Vale para comentários,
  diários e updates públicos, reviews, receitas, comentários de receita e bio. Nomes do catálogo são só bloqueados.
- **Denúncias** em qualquer conteúdo público, com motivo e de forma anônima (pseudônimo HMAC por conteúdo). Com 3
  denúncias distintas o conteúdo sai do ar até a moderação decidir.
- **Painel `/moderacao`** (papel `MODERATOR` ou `ADMIN`): fila unificada, manter/remover, suspender ou banir, reativar
  conta. Cada decisão vai para `moderation_log` (só inserção, 5 anos), sem IP, sem quem denunciou e sem o conteúdo, só
  o SHA-256 do texto removido. `ADMIN` exporta o log em CSV para atender ordem judicial.

Ainda não há tela para promover moderadores; use o banco e peça para a pessoa entrar de novo (o papel vai no token de
acesso):

```sh
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "update users set role = '"'"'MODERATOR'"'"' where username = '"'"'apelido'"'"'"'
```

O canal de notificações guarda as conexões abertas em memória: com mais de uma instância do backend, cada uma só
avisa quem está conectado nela (para escalar, trocar o `SseNotificationHub` por um pub/sub, como o LISTEN/NOTIFY
do Postgres).

### Acompanhamento de tratamento (cannabis medicinal)

Área privada em `/tratamento` para registrar o uso de remédios prontos (óleo, flor, pomada, gomas, extrato...),
as notas de sintomas antes e depois, os efeitos indesejados e o estoque de cada lote, com um relatório em PDF para
levar à consulta. Cada conta pode acompanhar perfis próprios ou de dependentes e convidar cuidadores.

- **Só registro, nunca orientação.** O app não sugere dose, horário, produto ou ajuste (ver RDC 657/2022 da ANVISA
  sobre software como dispositivo médico). Os mg são aritmética sobre a concentração do rótulo, e o relatório traz
  somas e médias do que foi registrado, com o aviso em todas as páginas.
- **Dado sensível (LGPD, art. 11).** Exige consentimento específico por conta, nunca aparece em feed, ranking,
  perfil ou notificações, pode ser exportado em JSON e apagado por completo a qualquer momento.
- **Textos cifrados.** Nomes e observações vão para o banco cifrados com AES-256-GCM. A chave é
  `WEEDIARIO_HEALTH_KEY` no `.env`: `scripts/setup-env` cria e acrescenta a chave a um `.env` antigo. **Guarde uma
  cópia segura dela**: sem a chave, esses textos ficam ilegíveis (inclusive em backups).

---

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

---

## Como Contribuir

Consulte o [CONTRIBUTING.md](CONTRIBUTING.md) para entender as diretrizes de código, o fluxo de commits e como enviar
pull requests.

## Licença

Este projeto está sob a licença AGPL-3.0. Veja o arquivo [LICENSE](LICENSE) para mais detalhes.
