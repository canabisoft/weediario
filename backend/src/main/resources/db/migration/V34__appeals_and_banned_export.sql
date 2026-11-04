-- Política de privacidade e Termos 1.2 (cláusula 9, moderação com contraditório):
--  - quem teve o conteúdo retido é avisado e pode mandar a sua versão à moderação em até 5 dias;
--  - a conta banida ainda entra por 30 dias, só para exportar os próprios dados.

-- A versão do autor fica junto da retenção e some com ela quando a moderação decide (a decisão vai para o log).
alter table moderation_holds add column appeal varchar(2000);
alter table moderation_holds add column appealed_at timestamptz;
alter table moderation_holds add constraint ck_moderation_holds_appeal
    check ((appeal is null) = (appealed_at is null));

-- Aviso "seu conteúdo está aguardando a moderação": aponta para o item retido (sem FK: o item pode ser apagado e o
-- aviso só deixa de oferecer a manifestação).
alter table notifications add column held_type varchar(20);
alter table notifications add column held_id uuid;
alter table notifications drop constraint ck_notifications_type;
alter table notifications add constraint ck_notifications_type
    check (type in ('COMMENT', 'REACTION', 'NEW_FOLLOWER', 'FOLLOWED_UPDATE', 'REPLY', 'CONTENT_HELD'));
alter table notifications add constraint ck_notifications_held
    check ((type = 'CONTENT_HELD') = (held_type is not null and held_id is not null));

-- Quando o status da conta mudou: conta o prazo de exportação depois do banimento. Contas já banidas ganham o prazo
-- a partir da última alteração conhecida.
alter table users add column status_changed_at timestamptz;
update users set status_changed_at = updated_at where status <> 'ACTIVE';
