package br.com.weediario.treatment.infrastructure.identity;

import br.com.weediario.treatment.application.port.TreatmentUsers;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class JdbcTreatmentUsers implements TreatmentUsers {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcTreatmentUsers(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> activeUserId(String username) {
        return jdbc.queryForList("select id from users where lower(username) = lower(:username) and status = 'ACTIVE'",
                new MapSqlParameterSource("username", username), UUID.class).stream().findFirst();
    }
}
