package br.com.weediario.shared.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    /**
     * Fuso único da plataforma, usado para datas de cultivo. Não é derivado do usuário (não coletamos localização).
     */
    @Bean
    Clock clock(@Value("${weediario.time-zone:America/Sao_Paulo}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
