package br.com.weediario.shared.application;

import java.util.UUID;

/**
 * Parte dos dados pessoais de uma conta que mora num contexto (diários, fotos, receitas, tratamento...). Cada contexto
 * implementa a sua na própria infraestrutura; o contexto de identidade junta todas para atender os direitos do titular
 * (LGPD, art. 18): baixar os dados e apagar a conta.
 * <p>
 * O banco apaga quase tudo sozinho quando a conta some ({@code on delete cascade}); esta interface cobre o que fica
 * fora dele, como os arquivos das fotos em disco.
 */
public interface PersonalDataSection {

    /** Nome da seção no {@code dados.json} da exportação. */
    String name();

    /** Dados da pessoa prontos para JSON (records, listas, mapas). {@code null} omite a seção. */
    Object export(UUID userId);

    /** Arquivos que acompanham a exportação (ex.: as fotos), entregues um por vez para não juntar tudo na memória. */
    default void writeFiles(UUID userId, FileSink sink) {
    }

    /**
     * Chamado dentro da transação que apaga a conta, antes do {@code delete}: junta o que precisa sumir fora do banco e
     * devolve o que fazer <b>depois</b> do commit (se a exclusão for desfeita, nada é apagado do disco).
     */
    default Runnable prepareErasure(UUID userId) {
        return () -> {
        };
    }

    @FunctionalInterface
    interface FileSink {

        /** @param path caminho dentro do pacote, ex.: {@code fotos/<id>.webp} */
        void put(String path, byte[] content);
    }
}
