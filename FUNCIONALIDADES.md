# Funcionalidades do weediário

Inventário do que o app faz hoje (backend 0.29.0, frontend 0.22.0). Para instalar e rodar, veja o [README](README.md);
para contribuir, o [CONTRIBUTING](CONTRIBUTING.md).

O weediário é um diário de cultivo de cannabis com comunidade, catálogo aberto de genéticas e acompanhamento de
tratamento medicinal. Tudo foi feito para funcionar sem saber quem você é: sem e-mail, sem senha, sem IP e sem
rastreamento.

---

## Conta e acesso

| O quê | Onde |
|---|---|
| Criar conta só com um **apelido** (pode ser pseudônimo) e o aceite dos Termos de Uso | `/cadastro` |
| Entrar com **apelido + chave de acesso** (`wd_sec_…`) | `/entrar` |
| Recuperar a conta com a **frase de 12 palavras**, que gera uma chave nova | `/recuperar` |
| Gerar ou trocar a frase de recuperação (pede a chave atual) | `/frase-de-recuperacao` |
| Perfil: bio, foto, link para o perfil público | `/perfil` |

- **Sem senha e sem e-mail.** No cadastro, o servidor gera a chave de acesso (256 bits aleatórios), mostra uma única
  vez e guarda só o hash SHA-256 dela. A tela permite copiar a chave e salvar no gerenciador de senhas.
- **Frase de recuperação (BIP-39, lista em português).** É gerada no navegador e confirmada no cadastro (a pessoa
  digita 3 palavras sorteadas). O servidor nunca recebe as palavras, só um código derivado (PBKDF2), guardado como
  Argon2id. A frase aceita maiúsculas, vírgulas, acentos e as 4 primeiras letras de cada palavra, e avisa quando há
  palavra trocada ou fora de ordem.
- **Recuperar a conta** gera uma chave nova, invalida a antiga e encerra todas as sessões abertas.
- **Contas antigas** (de quando havia senha) entram uma última vez com a senha, recebem a chave e a senha é apagada.
  Quem ainda não tem frase é levado a gerar uma, e um aviso fica no topo até isso acontecer.
- **Limite de tentativas por apelido**, sem IP: bloqueio progressivo de 15 min até 6 h. Login e recuperação têm
  limites separados. Apelido inexistente e credencial errada recebem a mesma resposta.
- **Sessão:** token de acesso de 10 min só em memória e refresh token em cookie `HttpOnly`/`Secure`/`SameSite=Strict`,
  com rotação e tolerância a recarregar a página no meio de uma renovação.

## Diários de cultivo

| O quê | Onde |
|---|---|
| Lista e feed de diários públicos | `/diarios` |
| Iniciar um diário | `/diarios/novo` |
| Ver um diário (linha do tempo, fotos, comentários, reações) | `/diarios/:id` |
| Publicar um update | `/diarios/:id/update` |

- **Dados do diário:** strain e banco (escolhidos no catálogo, com "você quis dizer?"), tipo (fotoperíodo ou
  automática), sexo da semente (feminizada ou regular), meio (solo vivo, mineral ou inerte), data de início e
  visibilidade (público ou privado, mudável depois).
- **Setup:** o diário pode partir de um setup salvo, que leva junto o ambiente, a tenda e os produtos usados.
- **Updates:** fase (germinação, vegetativo, floração, secagem/cura), notas e fotos. A semana do cultivo é calculada.
- **Fotos:** o servidor remove todos os metadados (GPS, data, modelo do celular) e gera versões em WebP (400 e 1600 px).
  Há visualizador em tela cheia.
- **Encerrar o cultivo:** concluir ou cancelar. Ao concluir, dá para informar o peso seco e o número de plantas
  (gramas por planta), na hora ou depois da cura.
- **Avaliação pós-colheita:** o autor de um diário concluído avalia a strain (vigor, resistência, facilidade,
  rendimento, aroma/sabor), opcionalmente o banco de sementes (germinação, estabilidade, atendimento) e cada produto
  usado, com um relato do cultivo.
- **Receitas no diário:** o diário pode indicar as receitas de nutrição que usou.

## Equipamentos

| O quê | Onde |
|---|---|
| Setups reutilizáveis: ambiente (indoor, outdoor, estufa), medidas da tenda, iluminação e produtos | `/equipamentos` |

Os setups aparecem no perfil público do grower.

## Comunidade

| O quê | Onde |
|---|---|
| Ranking de growers (por diários, curtidas ou gramas por planta) e busca por apelido | `/growers` |
| Perfil público: bio, foto, diários, setups, receitas e avaliações | `/growers/:apelido` |
| Notificações | `/notificacoes` |

- **Comentários** nos diários e nas receitas. O autor pode apagar os próprios, e o dono do diário ou da receita pode
  apagar os dele.
- **Respostas em comentários:** botão "Responder" em cada comentário; a resposta aparece recuada sob o original (um
  nível só: responder uma resposta continua na mesma conversa). Mostra as 5 primeiras respostas e um "Ver mais".
  Apagar um comentário apaga as respostas dele. Quem teve o comentário respondido recebe uma notificação.
- **Reações** 🔥 🌿 👏 nos diários e em qualquer comentário ou resposta, anônimas: cada reação é gravada com um
  pseudônimo que não liga a pessoa ao conteúdo (nos comentários, nem a data).
- **Seguir growers.** O número de seguidores aparece, mas quem segue quem não fica público.
- **Notificações em tempo real** de comentário, reação, novo seguidor e update de quem você segue. Reações e
  seguidores chegam sem dizer quem foi. Ficam guardadas por 90 dias.

## Catálogo e avaliações

| O quê | Onde |
|---|---|
| Catálogo com abas Strains, Bancos de sementes, Produtos e Em teste | `/strains` |
| Página da strain: características, médias, g/planta, avaliações | `/strains/:id` |
| Página do banco de sementes: strains, notas e sementes em teste | `/bancos/:id` |
| Página do produto: marca, categoria, uso, receitas, notas | `/produtos/:id` |
| Mais usados: ranking de strains, bancos, marcas e produtos | `/mais-usados` |

- **Só genéticas:** o catálogo recusa nome com preço ou moeda, anúncio ("SALE"), brinde, pacote, mistura ou produto
  de loja, e a importação limpa o nome que vem com o banco ou a linha do banco embutidos.
- **Sem duplicatas:** os nomes são normalizados, a busca tolera erro de digitação e, antes de cadastrar algo novo,
  o app pergunta "você quis dizer?".
- **A comunidade completa** as características que faltam numa strain (floração, dominância, tempo de flora,
  sabores, efeitos, genética). Só moderadores corrigem o que já foi preenchido.
- **Notas só de cultivos reais:** a média vem apenas de avaliações pós-colheita de contas ativas que não estejam
  em análise.

## Programa de breeders

| O quê | Onde |
|---|---|
| Entrar no programa como banco de sementes ou breeder independente, cadastrar sementes, acompanhar o teste | `/breeders/programa` |

- **Para bancos e para breeders independentes** (quem cria genéticas em casa ou em pequenos projetos). Quem pede
  diz o que representa e manda uma justificativa que só a moderação vê: site do banco ou, para independentes,
  diários de polinização, fotos, perfil onde divulga o trabalho. O independente que ainda não está no catálogo
  cadastra o nome do projeto na hora.
- Sementes cadastradas entram **em teste**, com a **geração** opcional (F1, F2, F3+, S1, BX, IBL, poli-híbrido),
  que ajuda quem testa a saber o quanto as plantas podem variar. Dá para escolher a semente ao abrir um diário, mas
  ela fica fora do catálogo oficial, dos rankings e do banco aberto.
- A semente entra no **banco oficial** sozinha com **3 cultivadores diferentes + 1 avaliação pós-colheita**.
  Quem representa o breeder não conta, e a moderação não consegue pular essa regra.
- Quem representa um breeder não aparece publicamente. Breeder independente pode **escolher aparecer como
  criador(a)** das genéticas na página do breeder (desligado por padrão, dá para desligar quando quiser).
- O catálogo mostra se o breeder é banco de sementes ou independente, e o banco aberto traz o tipo e a geração.

## Dados abertos

| O quê | Onde |
|---|---|
| Página com explicação, campos e downloads | `/dados-abertos` |
| Strains oficiais em JSON e CSV, sem login | `GET /api/v1/open-data/strains`, `/strains.csv` |

Licença **ODbL 1.0**. A exportação traz só agregados (características, diários públicos, growers distintos, médias
das avaliações e g/planta), sem nenhum apelido ou id de diário.

## Receitas de nutrição

| O quê | Onde |
|---|---|
| Lista, busca e receitas recomendadas | `/receitas` |
| Criar e editar uma receita | `/receitas/nova`, `/receitas/:id/editar` |
| Ver uma receita, com nota e comentários | `/receitas/:id` |

- Ingredientes vindos do catálogo de produtos, com autocomplete.
- Receita pública ou privada. Dá para **replicar** a receita de outra pessoa para a sua conta.
- Nota de 1 a 5 e comentários.
- O perfil do grower mostra as receitas públicas dele, e a página do produto mostra as receitas que o usam.

## Tratamento (cannabis medicinal)

| O quê | Onde |
|---|---|
| Perfis acompanhados (o próprio ou de dependentes) | `/tratamento` |
| Painel do paciente: hoje, remédios, histórico, ajustes | `/tratamento/:id` |

- **Remédios e lotes:** forma (óleo, flor, pomada, gomas, extrato…), origem, via, concentração do rótulo, estoque
  e ajustes de estoque.
- **Registro de doses**, com mg calculados pela concentração e a avaliação "depois" da dose.
- **Sintomas** com notas antes e depois, check-ins e **efeitos indesejados** com intensidade.
- **Histórico em gráficos e relatório em PDF** para levar à consulta.
- **Cuidadores:** convite por apelido. A pessoa convidada aceita ou recusa, e o acesso pode ser revogado.
- **Privacidade:**
  - Exige consentimento específico (dado sensível, LGPD art. 11).
  - Nunca aparece em feed, ranking, perfil ou notificação.
  - Os textos ficam cifrados no banco (AES-256-GCM).
  - Dá para exportar em JSON e apagar tudo a qualquer momento.
- **Só registro, nunca orientação:** o app não sugere dose, horário, produto ou ajuste.

## Ferramentas (funcionam offline)

| O quê | Onde |
|---|---|
| Calculadora de VPD | `/ferramentas/vpd` |
| Conversor EC / PPM | `/ferramentas/ec-ppm` |

## Moderação e regras

| O quê | Onde |
|---|---|
| Termos de Uso, com aceite registrado (versão e data/hora) | `/termos` |
| Manifesto: o que o projeto acredita e garante | `/manifesto` |
| Painel de moderação (moderador e admin) | `/moderacao` |
| Métricas da plataforma e gestão da equipe (só admin) | `/moderacao?aba=metricas`, `/moderacao?aba=equipe` |

- **Filtro anti-comércio:**
  - **Bloqueia na hora** telefone, CPF/CNPJ, chave PIX e links de WhatsApp, Telegram, Signal e Discord, inclusive
    disfarçados ("p.i.x", "piks", número espaçado ou com letras no lugar de dígitos, "wa ponto me").
  - **Segura para análise** anúncios, "chama no privado", @perfil com contexto de negócio, links encurtados e
    telefone ambíguo. O conteúdo fica visível só para o autor, com o selo "em análise".
  - **Não atrapalha o cultivo:** medidas (EC, pH, PPFD, VPD), datas, modelos de LED e strains com número passam.
  - **Vale para** comentários, diários, updates, avaliações, receitas, bio e nomes do catálogo.
- **Denúncias** em qualquer conteúdo público ou perfil, com motivo e anônimas. Com 3 denúncias o conteúdo sai do
  ar até a moderação decidir.
- **Painel com abas:**
  - Denúncias e retidos: manter, remover, remover e suspender, remover e banir.
  - Catálogo, por tipo: verificar, juntar duplicatas, recusar.
  - Representantes de breeders.
  - Contas e log.
- **Log de moderação:** só aceita inclusões e é guardado por 5 anos. Guarda o hash do conteúdo removido, nunca o
  texto nem quem denunciou. O admin exporta em CSV para atender ordem judicial.
- **Hierarquia:** moderador nunca suspende, bane ou remove conteúdo de outra conta da equipe (403). Só admin remove
  conteúdo de alguém da equipe, e ninguém suspende uma conta da equipe sem antes tirá-la da equipe.
- **Contas suspensas ou banidas perdem o acesso na hora** (o backend confere a conta a cada requisição) e podem ser
  reativadas.
- **Painel do admin:**
  - Métricas só por contagem no banco: contas por status, conteúdo publicado, atividade de 7 e 30 dias e fila da
    moderação. Nada por pessoa, nada de tratamento, nenhuma ferramenta externa.
  - Equipe: promover grower a moderador e tirar moderador da equipe, com registro no log. Admins são definidos só no
    banco.
- Quem não aceitou a versão atual dos Termos continua lendo, baixando e apagando dados, mas não publica.

## App e interface

- **PWA instalável.** Quando sai versão nova, aparece o aviso "Tem versão nova".
- **Tema claro/escuro** e **modo noturno de cultivo** (`/ajustes`): só verde escuro sobre preto e fotos escurecidas,
  para não estressar as plantas no período de escuro.
- **Avisos de privacidade** que se alternam pelo app.
- Feito para celular primeiro (funciona a partir de 390 px de largura).

## Privacidade e segurança (resumo)

- **Coletamos só:** apelido; o hash da chave de acesso; o hash do derivado da frase de recuperação; e a versão e a
  data/hora do aceite dos Termos.
- **Não guardamos:** e-mail, senha, nome, CPF, telefone, IP, localização, user-agent nem logs de acesso.
- **Fotos:** sem EXIF.
- **Reações e denúncias:** usam pseudônimos HMAC.
- **Dados de saúde:** cifrados.
- **Infraestrutura:** containers sem root e somente leitura; CSP estrita, sem estilos inline; teto de cálculos
  pesados (Argon2) para o servidor não cair sob ataque, sem usar IP.
- **Projeto open-source** (código AGPL-3.0, dados abertos ODbL 1.0): qualquer pessoa pode auditar e contribuir.

## Limitações conhecidas

- O primeiro admin é definido no banco (veja o README); depois disso, moderadores são geridos pelo painel.
- O app não avisa quando um pedido de representante é aprovado nem quando uma semente em teste vira oficial.
- A avaliação feita pelo próprio representante de um banco entra na média pública da strain, embora não conte para
  a promoção.
- O banco aberto não libera CORS: download e uso por servidor funcionam, leitura direta por outro site não.
- As notificações em tempo real funcionam com uma instância do backend. Para escalar, é preciso um pub/sub.
