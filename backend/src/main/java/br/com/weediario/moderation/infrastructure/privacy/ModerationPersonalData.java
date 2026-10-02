package br.com.weediario.moderation.infrastructure.privacy;

import br.com.weediario.moderation.domain.repository.ModerationRepository;
import br.com.weediario.shared.application.PersonalDataSection;
import br.com.weediario.shared.infrastructure.persistence.PersonalDataRows;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Decisões de moderação sobre a pessoa (o que foi removido, por quê e quando), sem dizer quem moderou nem quem
 * denunciou. Ao apagar a conta, o registro de moderação <b>fica</b> (guardado 5 anos para atender ordem judicial), só
 * perde o vínculo com a conta; as denúncias e retenções do conteúdo apagado somem logo, sem esperar a faxina periódica.
 */
@Component
public class ModerationPersonalData implements PersonalDataSection {

    private final NamedParameterJdbcTemplate jdbc;
    private final ModerationRepository moderation;

    public ModerationPersonalData(NamedParameterJdbcTemplate jdbc, ModerationRepository moderation) {
        this.jdbc = jdbc;
        this.moderation = moderation;
    }

    @Override
    public String name() {
        return "moderacao";
    }

    @Override
    public Object export(UUID userId) {
        return PersonalDataRows.of(jdbc, """
                select action, reason, target_type, created_at from moderation_log
                 where reported_user_id = :userId order by created_at""", userId);
    }

    @Override
    public Runnable prepareErasure(UUID userId) {
        return moderation::deleteOrphans;
    }
}
