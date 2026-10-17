# Weediario

Plataforma colaborativa e open-source de diários de cultivo e avaliações de genéticas, construída com foco em
privacidade, anonimato e segurança.

A nota de uma genética vem só de cultivos reais: quem conclui um diário avalia a strain e, se quiser, o banco de
sementes e os produtos usados. O catálogo nasce desses diários e de um banco de strains aberto (licença ODbL).

---

## Privacidade e segurança

Os princípios que guiam a arquitetura. Os detalhes de implementação estão no código, para quem quiser conferir.

- **Sem IP e sem localização.** Nenhuma tabela guarda geolocalização; os proxies não registram log de acesso nem
  repassam o IP do visitante ao backend.
- **Fotos sem metadados.** Todo upload é reprocessado a partir dos pixels: EXIF (GPS, data, aparelho) é descartado e
  a imagem sai recodificada.
- **Conta anônima.** Apelido e senha (Argon2id), e-mail opcional e cifrado. Toda conta tem uma frase de recuperação
  gerada no navegador — o servidor nunca vê as palavras, só um derivado.
- **Anonimato nas interações.** Reações e denúncias usam pseudônimos derivados por conteúdo, não relacionáveis entre
  si; o banco guarda contagens, não quem reagiu.
- **Direitos do titular (LGPD).** No perfil, exportar todos os dados e apagar a conta por completo.


Dados de **cannabis medicinal** (área de acompanhamento de tratamento) são tratados como dado sensível: consentimento
específico, textos cifrados em repouso, e nunca aparecem em feed, ranking ou notificações. O app apenas **registra** o
que a pessoa informa — não sugere dose, produto ou ajuste.

---

## Stack

- **Backend:** Java 21, Spring Boot, Spring Security, Spring Data JPA, PostgreSQL (Clean Architecture + DDD).
- **Frontend:** Angular, Tailwind CSS, PWA com ferramentas offline.
- **Infra:** Docker e Docker Compose, atrás de reverse proxy com HTTPS automático.

---

## Rodar localmente

Pré-requisito: Docker com Compose v2.

```sh
git clone https://github.com/canabisoft/weediario.git
cd weediario
sh scripts/setup-env.sh          # gera o .env com segredos aleatórios (PowerShell: .\scripts\setup-env.ps1)
docker compose up -d --build
```

Acesse `http://localhost:8080`. Só o frontend fica exposto (em `127.0.0.1`); backend e banco ficam na rede interna do
Compose, e a API é servida em `/api`.

```
navegador ──► nginx (frontend) ──/api──► backend ──► postgres
```

| Comando | O que faz |
| --- | --- |
| `docker compose ps` | Estado e saúde dos serviços |
| `docker compose logs -f backend` | Logs do backend |
| `docker compose down` | Para tudo (dados preservados) |
| `docker compose down -v` | Para tudo e **apaga** banco e fotos |

### Desenvolvimento

```sh
docker compose --profile dev up -d postgres postgres-port   # banco em localhost:5432
cd backend && mvn spring-boot:run                           # API
cd frontend && npm install && npm start                     # http://localhost:4200, proxy de /api
```

Testes: `mvn test` no `backend/` (PostgreSQL embarcado, sem Docker) e `npm test` no `frontend/`.

---

## Banco aberto de strains

Dados agregados do catálogo, públicos e sem login, sob a licença
[ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/):

- `GET /api/v1/open-data/strains` (JSON) e `GET /api/v1/open-data/strains.csv`.

Só strains oficiais e só agregados — nenhum apelido, id de diário ou de avaliação.

---

## Contribuir

Veja o [CONTRIBUTING.md](CONTRIBUTING.md) para as diretrizes de código, o fluxo de commits e como enviar pull requests.

## Licença

[AGPL-3.0](LICENSE).
