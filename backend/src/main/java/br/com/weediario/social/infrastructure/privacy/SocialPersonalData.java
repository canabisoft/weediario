package br.com.weediario.social.infrastructure.privacy;

import br.com.weediario.shared.application.PersonalDataSection;
import br.com.weediario.shared.infrastructure.persistence.PersonalDataRows;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Comentários escritos em diários e quem a pessoa segue. Reações e denúncias não entram: são guardadas só com
 * pseudônimo (HMAC por alvo), sem vínculo recuperável com a conta.
 */
@Component
public class SocialPersonalData implements PersonalDataSection {

    private final NamedParameterJdbcTemplate jdbc;

    public SocialPersonalData(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String name() {
        return "comunidade";
    }

    @Override
    public Object export(UUID userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("comentarios_em_diarios", PersonalDataRows.of(jdbc, """
                select * from diary_comments where author_id = :userId order by created_at""", userId));
        data.put("quem_eu_sigo", PersonalDataRows.of(jdbc, """
                select u.username, f.created_at from grower_follows f join users u on u.id = f.followee_id
                 where f.follower_id = :userId order by f.created_at""", userId));
        return data;
    }
}
