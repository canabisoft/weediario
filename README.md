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
- **Conta com apelido + senha, e-mail opcional:** a senha é guardada como Argon2id e segue o NIST SP 800-63B
  (mínimo de 10 caracteres, sem regra de símbolo, recusa as senhas vazadas mais comuns de uma lista embutida no app e
  o próprio apelido). Toda conta tem uma frase de 12 palavras (BIP-39, lista em português) para recuperar o acesso: a
  frase é criada no navegador e o servidor recebe só um derivado PBKDF2 dela, guardado como Argon2id. Recuperar com
  a frase define uma senha nova, troca a frase e encerra todas as sessões.
- **E-mail opcional e cifrado:** serve só para redefinir a senha (link de uso único, 30 min) e para avisos de
  segurança. Fica cifrado (AES-256-GCM, amarrado à conta), sem índice e sem unicidade: não existe login nem busca por
  e-mail, e o cadastro nunca revela se um endereço já tem conta. Só vale para recuperar depois de confirmado. O
  pedido de redefinição parte do apelido e a resposta é sempre a mesma.
- **Direitos do titular (LGPD):** no perfil, baixar todos os dados (.zip com `dados.json` e as fotos) e apagar a
  conta, os dois com a senha atual. Cada contexto entrega a sua parte por `PersonalDataSection`
  (`shared.application`); o banco apaga o resto por `on delete cascade`. Registros de moderação e itens do catálogo
  aberto ficam sem vínculo com a conta.
- **Histórico de segurança:** trocas de senha, e-mail e frase ficam 90 dias no perfil, só com tipo e data (sem IP nem
  aparelho), e o e-mail confirmado recebe um aviso. Mudanças com a sessão aberta exigem a senha atual.
- **Boas práticas OWASP:** Argon2id (senha e frase), tokens JWT de curta duração (10 min) com refresh token em cookie
  `HttpOnly`, `Secure` e `SameSite=Strict`, e payloads validados estritamente. Trocar ou redefinir a senha,
  recuperar a conta, suspender ou banir encerra também os access tokens já emitidos, na hora (geração de sessões no
  token, conferida a cada requisição).

### Privacidade na infraestrutura

- nginx e Caddy sem log de acesso e sem repassar o IP do visitante ao backend; o nginx só registra falhas que o
  derrubam, e o Postgres registra erros sem o comando SQL nem os valores (`log_error_verbosity=terse`).
- Logs do docker com rotação (`max-size: 10m`, 3 arquivos): nada acumula para sempre no disco. Erro inesperado vai
  para o log só com a classe, onde aconteceu no nosso código e um código de referência (que também volta na
  resposta), nunca com a mensagem, que pode trazer dado de usuário.
- Limite de tentativas por apelido (chave HMAC do apelido, sem IP) no login e na confirmação da senha atual: depois
  de 5 erros, espera progressiva de 30 s até no máximo 15 min (curta, para ninguém trancar a conta dos outros por
  horas). Cada tentativa é reservada antes do Argon2, com a linha do apelido travada no banco, então tentativas em
  paralelo também contam uma a uma. Frase (128 bits) e links de e-mail (256 bits) não têm bloqueio: adivinhar é
  inviável, e a recuperação nunca fica travada.
- **Banco com menor privilégio:** o backend conecta como `weediario_app`, que só lê e escreve linhas (sem DDL, sem
  `COPY` para arquivo ou programa, sem ler `pg_authid`) e tem tempo máximo por comando (30 s). As migrations rodam
  como `weediario_owner`, dono do schema e sem superusuário; o superusuário da imagem fica só para manutenção e
  backup. O serviço `db-roles` do compose (`deploy/postgres/roles.sql`) cria e confere os papéis a cada `up`, e corrige
  sozinho instalações antigas e bancos restaurados.
- **Cotas por conta** (nunca por IP) em ações que dão para abusar, numa tabela com incremento atômico e chave HMAC de
  (ação, conta): 429 com `Retry-After` ao passar do limite. Por dia: 10 denúncias, 3 pacotes de dados, 5 trocas de
  e-mail, 20 receitas, 30 diários, 60 updates e 200 fotos; por hora: 100 follows e 300 reações. Todos em
  `ActionLimits`. No máximo 2 pacotes de dados são montados ao mesmo tempo; os demais esperam a vez.
- **Cadastro com prova de trabalho** (protocolo do ALTCHA, sem terceiros, sem cookie e sem IP): o navegador resolve um
  desafio assinado (cerca de 1 s) e a solução vale para um cadastro só. Conferida antes do Argon2, encarece a criação
  de contas em série. Dificuldade em `WEEDIARIO_REGISTER_CHALLENGE_MAX`.
- **Sinais de segurança agregados:** contadores em memória por tipo de evento (senha errada, bloqueios, cadastros,
  cotas estouradas, denúncias, ocultações, tokens de sessão encerrada...), sem IP nem conta. Um resumo por hora vai
  para o log e vira aviso quando algum sinal passa do normal: `docker compose logs backend | grep "Sinais de segurança"`.
- **Backup cifrado** com a chave pública do age: o servidor só cifra; a chave privada fica fora dele. Checksums,
  cópia opcional para fora (rclone) e restore que confere checksum e chave antes de apagar qualquer coisa.
- **CI de segurança** (GitHub Actions, actions e imagens fixadas por SHA/digest): testes + FindSecBugs no
  `mvn verify`, `npm audit`, gitleaks, Semgrep, OSV-Scanner e Trivy; Dependabot com espera de 5 dias por versão nova.
- Limite por IP **só em memória** no nginx para entrar, cadastrar e recuperar (20/min por IP, com folga). O IP vira
  chave de um contador que some sozinho: não vai para log nem para o backend. Atrás do Caddy, o IP chega ao nginx num
  cabeçalho próprio que o Caddy sobrescreve.
- Contas da época da chave de acesso (`wd_sec_…`) entram uma última vez com a chave e criam uma senha; a chave deixa
  de valer.
- **Integridade do frontend:** cada script sai com SRI (`integrity="sha384-…"`) e o build é reproduzível.
  `scripts/verify-frontend.sh https://site` compila o repositório e confere, arquivo por arquivo, se o site serve o
  mesmo código.
- **Proteção contra sobrecarga sem IP:** cadastro, login, recuperação de conta e troca de senha ou de frase usam
  Argon2id, que é pesado de propósito. Um teto global de cálculos Argon2 simultâneos (padrão
  `max(2, núcleos / 2)`) impede que alguém derrube o servidor variando apelidos: com o teto cheio, a requisição
  espera até 2 s e, sem vaga, recebe `429` com `Retry-After`. O teto é só um contador (sem IP nem apelido) e não
  conta como tentativa errada. Ajuste com
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
- **Nomes limpos:** nome de genética não pode ter preço ou símbolo de moeda, anúncio ("SALE"), brinde de loja, pacote,
  mistura ou produto que não é semente (`CatalogNames`, aplicado a todo cadastro novo e à importação do dataset).
  A importação também tira o ruído de vitrine ("SALE - X - Banco" vira "X" do banco; "Banco - X" sem breeder vira "X"
  do Banco). A migração V24 limpou o dataset já importado: anúncios juntados com a strain verdadeira (o nome antigo
  vira apelido, para não voltar a duplicar), banco e linha do banco tirados do nome, e brindes, pacotes e produtos de
  loja apagados (só o que ninguém usava).
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
  denúncias distintas de contas com histórico (7 dias de conta e um update publicado ou e-mail confirmado) o conteúdo
  sai do ar até a moderação decidir. Denúncia de conta nova vai para a fila do mesmo jeito, mas não oculta nada:
  contas criadas em série não censuram ninguém. Até 10 denúncias por conta por dia.
- **Painel `/moderacao`** (papel `MODERATOR` ou `ADMIN`): fila unificada, manter/remover, suspender ou banir, reativar
  conta. Cada decisão vai para `moderation_log` (só inserção, 5 anos), sem IP, sem quem denunciou e sem o conteúdo, só
  o SHA-256 do texto removido. `ADMIN` exporta o log em CSV para atender ordem judicial.

- **Hierarquia da equipe:** moderadores nunca suspendem, banem ou removem conteúdo de outra conta da equipe (403);
  só um admin remove conteúdo de alguém da equipe, e nem ele suspende ou bane uma conta da equipe sem antes tirá-la
  da equipe. Ninguém decide sobre o próprio conteúdo nem aprova o próprio pedido no programa de breeders, e só admin
  desfaz banimento. Comentários só são apagados diretamente pelo autor ou pelo dono do diário/receita; a moderação
  remove pela decisão, que fica no log.
- **Log do catálogo:** corrigir, verificar, juntar, recusar e aprovar/revogar representantes ficam em `catalog_audit`
  (quem, o quê, quando; 5 anos).
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
  cópia segura dela, separada dos backups do banco**: sem a chave, esses textos ficam ilegíveis.
  - Cada valor é amarrado à sua linha e coluna (dado autenticado do GCM): copiar o texto cifrado de um paciente para
    outro faz a leitura falhar.
  - A chave pode vir de arquivo (`WEEDIARIO_HEALTH_KEY_FILE`, ex. um docker secret), que não aparece em
    `docker inspect`.
  - **Trocar a chave sem parar:** ponha a nova em `WEEDIARIO_HEALTH_KEY`, a antiga em
    `WEEDIARIO_HEALTH_PREVIOUS_KEYS` e suba o backend: ele regrava tudo com a nova ao iniciar. Depois remova a
    antiga.

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
