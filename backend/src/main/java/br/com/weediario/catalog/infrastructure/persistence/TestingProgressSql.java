package br.com.weediario.catalog.infrastructure.persistence;

/**
 * Contagens do teste da comunidade, como subconsultas correlacionadas a uma strain {@code s}. Usadas tanto na
 * promoção quanto nas telas de andamento, para as duas nunca divergirem.
 * <p>
 * Contam só contas ativas e nunca os representantes aprovados do breeder da strain (o breeder não se auto-valida).
 * Diários retidos pela moderação não contam como teste.
 */
public final class TestingProgressSql {

    private static final String NOT_A_REPRESENTATIVE = """
            not exists (select 1 from breeder_representatives br
                         where br.breeder_id = s.breeder_id and br.user_id = %s and br.status = 'APPROVED')""";

    /** Growers distintos com diário da strain, em qualquer visibilidade. */
    public static final String TESTERS = """
            (select count(distinct td.owner_id)
               from grow_diaries td join users tu on tu.id = td.owner_id and tu.status = 'ACTIVE'
              where td.strain_id = s.id and not td.moderation_hold
                and %s)""".formatted(NOT_A_REPRESENTATIVE.formatted("td.owner_id"));

    /** Avaliações pós-colheita publicadas (não ocultas). */
    public static final String REVIEWS = """
            (select count(*)
               from reviews tr join users tru on tru.id = tr.author_id and tru.status = 'ACTIVE'
              where tr.strain_id = s.id and not tr.hidden
                and %s)""".formatted(NOT_A_REPRESENTATIVE.formatted("tr.author_id"));

    private TestingProgressSql() {
    }
}
