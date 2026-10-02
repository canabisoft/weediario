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
- **Conta sem e-mail e sem senha:** o cadastro pede só um apelido. Para entrar, apelido + chave de acesso
  (`wd_sec_…`, 256 bits gerados pelo servidor e mostrados uma única vez); o banco guarda só o SHA-256 da chave, com
  índice único. Se a chave se perder, uma frase de 12 palavras (BIP-39, lista em português) gera outra. A frase é
  criada no navegador e o servidor recebe só um derivado PBKDF2 dela, guardado como Argon2id. Sem chave e sem frase,
  a conta não tem como ser recuperada.
- **Boas práticas OWASP:** hashes com SHA-256 (chave de acesso) e Argon2id (frase de recuperação), tokens JWT de curta duração (10 min) com refresh token em cookie
  `HttpOnly`, `Secure` e `SameSite=Strict`, e payloads validados estritamente.

### Privacidade na infraestrutura

- nginx e Caddy sem log de acesso e sem repassar o IP do visitante ao backend.
- Limite de tentativas de login e de recuperação por conta (chave HMAC do apelido), sem usar IP.
- **Proteção contra sobrecarga sem IP:** cadastro, recuperação de conta, último login de conta antiga e troca da
  frase usam Argon2id, que é pesado de propósito. Um teto global de cálculos Argon2 simultâneos (padrão
  `max(2, núcleos / 2)`) impede que alguém derrube o servidor variando apelidos: com o teto cheio, a requisição
  espera até 2 s e, sem vaga, recebe `429` com `Retry-After`. O teto é só um contador (sem IP nem apelido) e não
  conta como tentativa errada. O login com chave de acesso (SHA-256) não passa por ele. Ajuste com
  `WEEDIARIO_HASH_MAX_CONCURRENT` (0 = automático) e `WEEDIARIO_HASH_MAX_WAIT` (ex.: `2s`).
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

### Catálogo, avaliações e banco aberto

O catálogo (`/strains`) junta strains, bancos de sementes, marcas e produtos sem duplicar (chave normalizada,
apelidos e "você quis dizer?"). As notas vêm só de cultivos reais: o autor de um diário concluído avalia a strain e,
opcionalmente, o banco e cada produto usado no cultivo. As médias consideram só avaliações publicadas de contas ativas;
a nota de um produto não mostra quem avaliou e só leva ao diário quando ele é público (`/produtos/:id`).

- **Programa de breeders** (`/breeders/programa`): para **bancos de sementes** e para **breeders independentes**
  (quem cria genéticas por conta própria). Quem pede diz o que representa e manda uma nota de prova (só a moderação
  vê, na aba "Representantes" do painel): site oficial do banco ou, para independentes, diários de polinização,
  fotos, perfil onde divulga o trabalho. O breeder independente que ainda não está no catálogo cadastra o nome do
  projeto na hora (com "você quis dizer?"). Ao aprovar, o breeder fica marcado como banco ou independente. Aprovado,
  cadastra as sementes, com a **geração** opcional (F1, F2, F3+, S1, BX, IBL, poli-híbrido), que entram como
  **em teste**: aparecem no autocomplete e na aba "Em teste", mas ficam fora da listagem oficial, dos rankings e do
  banco aberto. A semente entra no banco oficial sozinha quando **3 cultivadores** diferentes abrem diário com ela e
  há **1 avaliação pós-colheita**, sem contar quem representa o breeder (checado a cada review e a cada 10 min).
  Moderadores não podem "verificar" uma semente em teste, só recusar se ninguém a usa. Quem representa um breeder
  não é exibido publicamente, a não ser que um breeder independente escolha aparecer como criador(a) das genéticas
  (opcional, desligado por padrão, reversível a qualquer momento).
- **Banco aberto de strains** (`/dados-abertos`): `GET /api/v1/open-data/strains` (JSON) e
  `GET /api/v1/open-data/strains.csv`, públicos e sem login, sob a licença
  [ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/). Só strains oficiais e só agregados (características,
  diários públicos, growers distintos, médias das avaliações e g/planta): nenhum apelido, id de diário ou de review.
  O CSV é protegido contra fórmulas de planilha e as respostas ficam em cache por 1 hora.

### Moderação

Regras em `/termos` (Termos de Uso, versão atual 1.0). O cadastro exige o aceite, e o banco guarda só a versão e a
data/hora. Ao mudar o texto, suba `TermsOfUse.CURRENT_VERSION` (backend) e `TERMS_VERSION` (frontend): quem aceitou uma
versão antiga continua lendo, baixando e apagando dados, mas não publica até aceitar a nova.

- **Filtro anti-comércio** (`CommerceFilter`): bloqueia telefone (inclusive disfarçado: espaçado, com letras no
  lugar de dígitos, +55/0), CPF/CNPJ, chave PIX (e-mail, e-mail por extenso, chave aleatória ou telefone perto de
  "pix", mesmo escrito "p.i.x"/"piks") e links de WhatsApp/Telegram/Signal/Discord (inclusive "wa . me" e
  "wa ponto me"). Segura para revisão anúncios, "chama no privado/pv/dm", @perfil com contexto de negócio, links
  encurtados, pedido de PIX e telefone ambíguo. Medidas de cultivo, datas, modelos de LED e strains com número
  passam (mais de 80 frases reais de grower nos testes). Vale para comentários e respostas, diários e updates
  públicos, reviews, receitas e bio. Nomes do catálogo são só bloqueados.
- **Denúncias** em qualquer conteúdo público, com motivo e de forma anônima (pseudônimo HMAC por conteúdo). Com 3
  denúncias distintas o conteúdo sai do ar até a moderação decidir.
- **Painel `/moderacao`** (papel `MODERATOR` ou `ADMIN`): fila unificada, manter/remover, suspender ou banir, reativar
  conta. Cada decisão vai para `moderation_log` (só inserção, 5 anos), sem IP, sem quem denunciou e sem o conteúdo, só
  o SHA-256 do texto removido. `ADMIN` exporta o log em CSV para atender ordem judicial.

- **Hierarquia da equipe:** moderadores nunca suspendem, banem ou removem conteúdo de outra conta da equipe (403);
  só um admin remove conteúdo de alguém da equipe, e nem ele suspende ou bane uma conta da equipe sem antes tirá-la
  da equipe.
- **Corte imediato:** a cada requisição autenticada o backend confere status e papel da conta. Suspensa ou banida
  recebe 401 na hora (sem esperar o token de 10 min expirar); quem mudou de papel renova a sessão automaticamente.

### Conversas nos comentários

Comentários de diários e de receitas aceitam respostas (um nível só: responder uma resposta mantém a conversa no
mesmo comentário) e reações anônimas 🔥 🌿 👏. Como nas reações de diário, só existem contagens: o banco guarda um
pseudônimo HMAC diferente para cada comentário, nunca quem reagiu nem quando. Respostas passam pelo mesmo filtro
anti-comércio, limite de frequência e denúncia dos comentários; quem teve o comentário respondido recebe uma
notificação. Apagar um comentário apaga as respostas dele, e não dá para responder ou reagir a comentário de conta
suspensa ou banida.

### Painel do admin

Abas "Métricas" e "Equipe" em `/moderacao`, só para `ADMIN` (rota `/api/v1/admin/**` e checagem no caso de uso):

- **Métricas** (`GET /api/v1/admin/metrics`): contagens feitas no banco na hora (contas por status e papel, conteúdo
  publicado, atividade dos últimos 7 e 30 dias, fila da moderação). Nada por pessoa, nenhuma ferramenta externa e
  nenhum dado de tratamento.
- **Equipe** (`GET /api/v1/admin/team`, `POST /api/v1/admin/team/{apelido}/promote` e `/remove`): promover um grower
  ativo a moderador e tirar um moderador da equipe. Cada mudança vai para o log de moderação (`ROLE_GRANTED` /
  `ROLE_REVOKED`). Ninguém muda o próprio papel, e admins não são criados nem removidos pela API: o primeiro admin é
  definido no banco, e a pessoa entra de novo para o papel valer:

```sh
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "update users set role = '"'"'ADMIN'"'"' where username = '"'"'apelido'"'"'"'
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
