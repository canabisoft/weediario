package br.com.weediario.shared.domain;

import br.com.weediario.shared.domain.CommerceFilter.Decision;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CommerceFilterTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "chama no zap 11 98765-4321",
            "contato (21) 3456-7890",
            "meu cpf 123.456.789-09",
            "cnpj 12.345.678/0001-90",
            "entra no grupo chat.whatsapp.com/AbCdEf",
            "segue lá t.me/lojinha",
            "pix: fulano@example.com",
            "chave pix 123e4567-e89b-12d3-a456-426614174000",
            "+55 11 912345678",
    })
    void blocksUnambiguousContactAndPaymentData(String text) {
        assertThat(CommerceFilter.check(text).decision()).isEqualTo(Decision.BLOCK);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "vendo 5g R$50",
            "V3ND0 prensado",
            "vende-se sementes, interessados",
            "chama no zap que eu passo",
            "faço entrega na zona sul",
            "aceito pix",
            "R$ 30 o grama, 10g por 250 reais",
            "troco clones de gelato",
            "me manda email: grower@example.com",
    })
    void holdsTypicalAdsForModeration(String text) {
        assertThat(CommerceFilter.check(text).decision()).isEqualTo(Decision.HOLD);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "pH 6,2 e EC 1.4, gastei R$ 300 no LED",
            "200 g por planta, colheita em 2026-09-30 12:00",
            "estou vendo as flores engordarem",
            "Rega com 1.5L, temperatura 25,4 e umidade 60%",
            "comprei sementes de um banco europeu",
            "dia 01/10/2026 fiz a poda",
            "tô vendo uns tricomas âmbar já",
    })
    void allowsEverydayGrowLanguage(String text) {
        assertThat(CommerceFilter.check(text).decision()).isEqualTo(Decision.ALLOW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"v3nd0", "VENDO", "véndo"})
    void normalizesAccentsCaseAndLeetspeak(String text) {
        assertThat(CommerceFilter.normalize(text.toLowerCase())).isEqualTo("vendo");
    }
}
