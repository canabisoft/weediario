package br.com.weediario.identity.infrastructure.persistence;

import br.com.weediario.identity.domain.model.AccountEmail;
import br.com.weediario.identity.domain.model.EmailAddress;
import br.com.weediario.identity.domain.model.UserId;
import br.com.weediario.identity.domain.repository.AccountEmailRepository;
import br.com.weediario.identity.infrastructure.security.EmailCipher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;

@Repository
public class JdbcAccountEmailRepository implements AccountEmailRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final EmailCipher cipher;

    public JdbcAccountEmailRepository(NamedParameterJdbcTemplate jdbc, EmailCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    @Override
    public Optional<AccountEmail> find(UserId userId) {
        return jdbc.query("select email_enc, verified_at from account_emails where user_id = :id",
                new MapSqlParameterSource("id", userId.value()),
                (rs, rowNum) -> AccountEmail.restore(userId,
                        new EmailAddress(cipher.decrypt(rs.getBytes("email_enc"), userId.value())),
                        rs.getTimestamp("verified_at") == null ? null : rs.getTimestamp("verified_at").toInstant()))
                .stream().findFirst();
    }

    @Override
    public void save(AccountEmail email) {
        jdbc.update("""
                insert into account_emails (user_id, email_enc, verified_at, updated_at)
                values (:id, :email, :verifiedAt, now())
                on conflict (user_id) do update set
                    email_enc = excluded.email_enc,
                    verified_at = excluded.verified_at,
                    updated_at = excluded.updated_at""",
                new MapSqlParameterSource("id", email.userId().value())
                        .addValue("email", cipher.encrypt(email.address().value(), email.userId().value()))
                        .addValue("verifiedAt", email.verifiedAt().map(Timestamp::from).orElse(null)));
    }

    @Override
    public void delete(UserId userId) {
        jdbc.update("delete from account_emails where user_id = :id", new MapSqlParameterSource("id", userId.value()));
    }
}
