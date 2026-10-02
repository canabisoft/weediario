package br.com.weediario.review.infrastructure.privacy;

import br.com.weediario.shared.application.PersonalDataSection;
import br.com.weediario.shared.infrastructure.persistence.PersonalDataRows;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Avaliações de genéticas escritas pela pessoa e os produtos citados nelas. */
@Component
public class ReviewPersonalData implements PersonalDataSection {

    private final NamedParameterJdbcTemplate jdbc;

    public ReviewPersonalData(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String name() {
        return "avaliacoes";
    }

    @Override
    public Object export(UUID userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("avaliacoes", PersonalDataRows.of(jdbc, """
                select * from reviews where author_id = :userId order by created_at""", userId));
        data.put("produtos_das_avaliacoes", PersonalDataRows.of(jdbc, """
                select rp.* from review_products rp join reviews r on r.id = rp.review_id
                 where r.author_id = :userId""", userId));
        return data;
    }
}
