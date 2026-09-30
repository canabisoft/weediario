# Contribuindo para o Weediario

Obrigado por seu interesse em contribuir para o Weediario! Como este é um projeto focado em privacidade, segurança e
ferramentas para growers, seguimos padrões rigorosos de engenharia e boas práticas.

## Diretrizes de Segurança (Crítico)

- **Nunca comite segredos:** o repositório usa variáveis de ambiente estritas. Segredos ficam só no `.env` (fora do
  git), gerado por `scripts/setup-env.sh` ou `scripts/setup-env.ps1`. Nunca coloque chaves reais, senhas ou tokens no
  código-fonte, nos testes ou no `.env.example`.
- **Respeite o anonimato:** nenhuma contribuição pode introduzir telemetria, rastreadores de terceiros ou coleta de
  dados sensíveis, IPs ou localização dos usuários. Isso vale também para logs e para dependências novas.

## Fluxo de Contribuição

1. Faça um fork do projeto.
2. Crie uma branch para a sua feature (`git checkout -b feature/minha-feature`).
3. Siga os padrões da arquitetura existente:
   - **Backend:** Clean Architecture + Domain-Driven Design (DDD), mantendo a cobertura de testes unitários e de
     integração.
   - **Frontend:** componentes Angular estilizados com Tailwind CSS, mantendo a responsividade mobile-first.
4. Garanta que todos os testes locais passem:

   ```sh
   cd backend && mvn test      # PostgreSQL embarcado, sem Docker
   cd frontend && npm test
   ```

5. Escreva as mensagens de commit no padrão [Conventional Commits](https://www.conventionalcommits.org/)
   (`feat:`, `fix:`, `docs:`...), como no histórico do repositório.
6. Envie um pull request detalhando as alterações e o problema que ele resolve.

## Relatando Vulnerabilidades

Se você encontrou uma falha de segurança ou uma vulnerabilidade crítica, **não abra uma issue pública**. Use o canal
de [Private Vulnerability Reporting](https://github.com/canabisoft/weediario/security/advisories/new) do GitHub ou
entre em contato em particular, para que possamos corrigir antes da exposição pública.
