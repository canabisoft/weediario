package br.com.weediario.shared.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * Duas cadeias de filtros:
 * <ol>
 *   <li>{@code /api/v1/auth/**}: pública, sem validação de Bearer (um access token expirado não pode impedir o refresh).</li>
 *   <li>Demais rotas: stateless, exigem JWT válido no header {@code Authorization: Bearer}, exceto os GETs de
 *   conteúdo público (feed, diários, strains), que aceitam visitantes.</li>
 * </ol>
 * <p>
 * CSRF desabilitado conscientemente: o access token trafega em header (não é enviado automaticamente pelo browser)
 * e o único cookie do sistema — o refresh token — é {@code SameSite=Strict}, com path restrito a {@code /api/v1/auth}
 * e só devolve tokens no corpo da resposta, ilegível para outra origem por causa do CORS.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    public static final String AUTH_PATH = "/api/v1/auth";
    public static final String ROLES_CLAIM = "roles";

    @Bean
    @Order(1)
    SecurityFilterChain authFilterChain(HttpSecurity http) throws Exception {
        applyCommonHardening(http.securityMatcher(AUTH_PATH + "/**"));
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST,
                        AUTH_PATH + "/register", AUTH_PATH + "/login", AUTH_PATH + "/refresh", AUTH_PATH + "/logout")
                .permitAll()
                .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        applyCommonHardening(http);
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/actuator/**").denyAll()
                        // Leitura de conteúdo público liberada para visitantes. A visibilidade de cada diário
                        // (público/privado) é decidida nos casos de uso, não aqui.
                        .requestMatchers(HttpMethod.GET, "/api/v1/feed/**", "/api/v1/diaries/**", "/api/v1/strains/**",
                                "/api/v1/seedbanks/**", "/api/v1/growers/**", "/api/v1/catalog/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    private void applyCommonHardening(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .preload(true)
                                .maxAgeInSeconds(Duration.ofDays(365).toSeconds()))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                                "geolocation=(), camera=(), microphone=(), interest-cohort=()"))
                        .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Opener-Policy", "same-origin"))
                        .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Resource-Policy", "same-origin")));
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(ROLES_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        config.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /** Impede o Spring Boot de criar o usuário padrão com senha gerada (a autenticação é feita pelos casos de uso). */
    @Bean
    UserDetailsService noOpUserDetailsService() {
        return new InMemoryUserDetailsManager();
    }
}
