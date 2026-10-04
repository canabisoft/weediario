package br.com.weediario.moderation.infrastructure.identity;

import br.com.weediario.moderation.application.port.ReporterStandings;
import br.com.weediario.moderation.domain.model.ReporterStanding;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** Leitura direta de contas, e-mails e updates de diário: só fatos, sem trazer o conteúdo de ninguém. */
@Component
public class JdbcReporterStandings implements ReporterStandings {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcReporterStandings(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ReporterStanding> of(UUID accountId) {
        return jdbc.query("""
                        select u.created_at,
                               exists (select 1 from account_emails e
                                        where e.user_id = u.id and e.verified_at is not null) as email_verified,
                               exists (select 1 from diary_updates du join grow_diaries d on d.id = du.diary_id
                                        where d.owner_id = u.id) as published_update
                          from users u
                         where u.id = :id""",
                new MapSqlParameterSource("id", accountId),
                (rs, n) -> new ReporterStanding(rs.getTimestamp("created_at").toInstant(),
                        rs.getBoolean("email_verified"), rs.getBoolean("published_update")))
                .stream().findFirst();
    }
}
