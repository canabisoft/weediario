package br.com.weediario.identity.infrastructure.security;

import br.com.weediario.identity.domain.model.TermsOfUse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * Quem ainda não aceitou a versão vigente dos Termos de Uso pode ler, sair e apagar coisas, mas não publica nem
 * altera nada até aceitar. A versão aceita vem no access token (claim {@value #TERMS_CLAIM}); depois do aceite o
 * cliente renova o token.
 * <p>
 * Ficam livres: leituras, {@code DELETE} (apagar os próprios dados é direito, nunca é bloqueado), autenticação
 * (login/refresh/logout) e o próprio aceite.
 */
@Component
public class TermsAcceptanceFilter extends OncePerRequestFilter {

    public static final String TERMS_CLAIM = "terms";

    private static final Set<String> READ_ONLY = Set.of(HttpMethod.GET.name(), HttpMethod.HEAD.name(),
            HttpMethod.OPTIONS.name(), HttpMethod.DELETE.name());
    private static final List<String> EXEMPT_PREFIXES = List.of("/api/v1/auth/", "/api/v1/me/terms",
            "/api/v1/me/notifications/");

    private static final String BODY = """
            {"status":403,"code":"TERMS_ACCEPTANCE_REQUIRED","detail":"Atualizamos os Termos de Uso. Leia e aceite a versão %s para continuar publicando."}"""
            .formatted(TermsOfUse.CURRENT_VERSION);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return READ_ONLY.contains(request.getMethod())
                || !path.startsWith("/api/")
                || EXEMPT_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt
                && !TermsOfUse.CURRENT_VERSION.equals(jwt.getToken().getClaimAsString(TERMS_CLAIM))) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(BODY);
            return;
        }
        chain.doFilter(request, response);
    }
}
