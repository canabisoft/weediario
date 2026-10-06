-- Geração das sessões da conta. Toda revogação geral (troca ou redefinição de senha, recuperação pela frase,
-- suspensão, banimento) incrementa o número; o access token carrega a geração em que foi emitido e os de geração
-- anterior deixam de valer na hora, sem esperar os minutos que ainda teriam de vida.
alter table users add column session_epoch integer not null default 0;
