-- Cotas de uso por conta e por ação (denúncias, exportação de dados...). A chave é um HMAC de (ação, conta): sem a
-- chave do servidor, a tabela não diz quem fez o quê. Linhas vencidas são apagadas de hora em hora.
create table action_quotas (
    quota_key  varchar(64) primary key,
    used       integer     not null,
    expires_at timestamptz not null
);
create index ix_action_quotas_expires on action_quotas (expires_at);

-- Só denúncias de contas com histórico contam para tirar um conteúdo do ar sozinho; as demais vão para a fila da
-- moderação sem ocultar nada (defesa contra contas criadas em série para censurar alguém). As antigas continuam
-- contando, como antes.
alter table content_reports add column counts_toward_hide boolean not null default true;
