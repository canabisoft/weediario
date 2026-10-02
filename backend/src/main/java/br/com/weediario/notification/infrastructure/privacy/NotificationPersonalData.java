package br.com.weediario.notification.infrastructure.privacy;

import br.com.weediario.shared.application.PersonalDataSection;
import br.com.weediario.shared.infrastructure.persistence.PersonalDataRows;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Notificações recebidas pela pessoa. */
@Component
public class NotificationPersonalData implements PersonalDataSection {

    private final NamedParameterJdbcTemplate jdbc;

    public NotificationPersonalData(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String name() {
        return "notificacoes";
    }

    @Override
    public Object export(UUID userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recebidas", PersonalDataRows.of(jdbc, """
                select * from notifications where recipient_id = :userId order by created_at""", userId));
        return data;
    }
}
