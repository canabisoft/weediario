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

    /** Avaliações pós-colheita publicadas (não ocultas) de fora do breeder. */
    public static final String REVIEWS = """
            (select count(*)
               from reviews tr join users tru on tru.id = tr.author_id and tru.status = 'ACTIVE'
              where tr.strain_id = s.id and not tr.hidden
                and %s)""".formatted(NOT_A_REPRESENTATIVE.formatted("tr.author_id"));

    /**
     * O próprio breeder (um representante aprovado) já publicou a avaliação pós-colheita da genética: é a primeira
     * nota do "que esperar", e só existe depois de ele ter cultivado e colhido a strain.
     */
    public static final String BREEDER_REVIEWED = """
            exists (select 1 from reviews brv
                      join users bru on bru.id = brv.author_id and bru.status = 'ACTIVE'
                      join breeder_representatives brr on brr.breeder_id = s.breeder_id
                           and brr.user_id = brv.author_id and brr.status = 'APPROVED'
                     where brv.strain_id = s.id and not brv.hidden)""";

    private TestingProgressSql() {
    }
}
