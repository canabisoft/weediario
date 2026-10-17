package br.com.weediario.identity.domain.model;

import br.com.weediario.shared.domain.DomainException;


/**
 * Versão vigente dos Termos de Uso. O texto de cada versão fica no código (página /termos), então o
 * histórico do repositório prova exatamente o que cada pessoa aceitou; no banco vai só a versão e a data/hora.
 * Mudou o texto, mudou a versão: todo mundo aceita de novo antes de publicar.
 */
public final class TermsOfUse {

    /** A primeira versão pública: a contagem começa no lançamento em produção. */
    public static final String CURRENT_VERSION = "1.1";

    private TermsOfUse() {
    }

    public static String requireCurrent(String acceptedVersion) {
        if (!CURRENT_VERSION.equals(acceptedVersion)) {
            throw new DomainException("Leia e aceite a versão atual dos Termos de Uso");
        }
        return acceptedVersion;
    }
}
