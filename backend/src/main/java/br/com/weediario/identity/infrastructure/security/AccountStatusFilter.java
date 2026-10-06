package br.com.weediario.identity.infrastructure.security;

import br.com.weediario.shared.infrastructure.observability.SecuritySignals;
import br.com.weediario.shared.infrastructure.security.SecurityConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Confere a conta a cada requisição autenticada, para que suspensão, banimento e mudança de papel valham na hora, e
 * não só quando o access token (10 min) expirar.
 * <ul>
 *   <li>Conta suspensa, banida ou apagada: 401 {@code ACCOUNT_DISABLED}. As sessões já foram revogadas, então a
 *   renovação também falha e o app sai da conta.</li>
 *   <li>Papel diferente do que está no token (promovido ou tirado da equipe): 401 {@code SESSION_OUTDATED}; o app
 *   renova a sessão e recebe um token com o papel atual.</li>
 *   <li>Token de uma geração de sessões já encerrada (troca ou redefinição de senha, recuperação): 401
 *   {@code SESSION_REVOKED}. A renovação também falha, porque as sessões foram revogadas, e o app sai da conta;
 *   quem roubou o token perde o acesso na hora, não dez minutos depois.</li>
 * </ul>
 * Custa uma consulta pela chave primária por requisição. Antes do filtro de Termos de Uso.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class AccountStatusFilter extends OncePerRequestFilter {

    private static final String DISABLED = """
            {"status":401,"code":"ACCOUNT_DISABLED","detail":"Esta conta está suspensa ou banida."}""";
    private static final String REVOKED = """
            {"status":401,"code":"SESSION_REVOKED","detail":"A sessão foi encerrada. Entre de novo."}""";
    private static final String OUTDATED = """
            {"status":401,"code":"SESSION_OUTDATED","detail":"As permissões da conta mudaram. Renove a sessão."}""";

    private final NamedParameterJdbcTemplate jdbc;
    private final SecuritySignals signals;

    public AccountStatusFilter(NamedParameterJdbcTemplate jdbc, SecuritySignals signals) {
        this.jdbc = jdbc;
        this.signals = signals;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/") || path.startsWith(SecurityConfig.AUTH_PATH + "/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();
            Optional<Current> current = current(jwt.getSubject());
            if (current.isEmpty() || !"ACTIVE".equals(current.get().status())) {
                reject(response, DISABLED);
                return;
            }
            if (epochOf(jwt) < current.get().sessionEpoch()) {
                signals.record(SecuritySignals.Signal.SESSION_REVOKED);
                reject(response, REVOKED);
                return;
            }
            List<String> roles = jwt.getClaimAsStringList(SecurityConfig.ROLES_CLAIM);
            if (roles == null || !roles.equals(List.of(current.get().role()))) {
                reject(response, OUTDATED);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private Optional<Current> current(String subject) {
        UUID id;
        try {
            id = UUID.fromString(subject);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            return Optional.empty();
        }
        return jdbc.query("select status, role, session_epoch from users where id = :id",
                new MapSqlParameterSource("id", id),
                (rs, n) -> new Current(rs.getString("status"), rs.getString("role"), rs.getInt("session_epoch")))
                .stream().findFirst();
    }

    /** Tokens emitidos antes da geração existir valem como geração 0. */
    private static long epochOf(Jwt jwt) {
        Object epoch = jwt.getClaim(JwtAccessTokenIssuer.SESSION_EPOCH_CLAIM);
        return epoch instanceof Number number ? number.longValue() : 0;
    }

    private static void reject(HttpServletResponse response, String body) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(body);
    }

    private record Current(String status, String role, int sessionEpoch) {
    }
}
