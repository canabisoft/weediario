package br.com.weediario.identity.infrastructure.security;

import br.com.weediario.identity.application.port.AccessTokenIssuer;
import br.com.weediario.identity.domain.model.User;
import br.com.weediario.shared.infrastructure.security.JwtConfig;
import br.com.weediario.shared.infrastructure.security.SecurityConfig;
import br.com.weediario.shared.infrastructure.security.SecurityProperties;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Minimização de dados: o token carrega apenas id opaco e papel. Nada de e-mail, username ou dados de dispositivo.
 */
@Component
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

    /** Geração das sessões da conta quando o token saiu (ver {@link AccountStatusFilter}). */
    public static final String SESSION_EPOCH_CLAIM = "sep";

    private final JwtEncoder encoder;
    private final NamedParameterJdbcTemplate jdbc;
    private final SecurityProperties.Jwt properties;
    private final Clock clock;

    public JwtAccessTokenIssuer(JwtEncoder encoder, NamedParameterJdbcTemplate jdbc, SecurityProperties properties,
                                Clock clock) {
        this.encoder = encoder;
        this.jdbc = jdbc;
        this.properties = properties.jwt();
        this.clock = clock;
    }

    @Override
    public IssuedAccessToken issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .issuer(properties.issuer())
                .subject(user.id().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(SecurityConfig.ROLES_CLAIM, List.of(user.role().name()))
                // Versão dos Termos de Uso aceita: sem a vigente, a API não aceita publicações.
                .claim(TermsAcceptanceFilter.TERMS_CLAIM, user.termsVersion().orElse(""))
                .claim(SESSION_EPOCH_CLAIM, sessionEpoch(user))
                .build();
        JwsHeader header = JwsHeader.with(JwtConfig.ALGORITHM).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(token, expiresAt);
    }

    /** Lido na mesma transação que abre a sessão: logo depois de uma revogação, já vem a geração nova. */
    private int sessionEpoch(User user) {
        return jdbc.query("select session_epoch from users where id = :id",
                        new MapSqlParameterSource("id", user.id().value()), (rs, n) -> rs.getInt(1))
                .stream().findFirst().orElse(0);
    }
}
