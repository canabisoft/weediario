package br.com.weediario.shared.domain;

import br.com.weediario.shared.domain.CommerceFilter.Decision;
import br.com.weediario.shared.domain.CommerceFilter.Verdict;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class CommerceFilterTest {

    @Nested
    class Bloqueia {

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
        void unambiguousContactAndPaymentData(String text) {
            assertDecision(text, Decision.BLOCK);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "liga 11 9 8765 4321",
                "11 98 7654321",
                "1 1 9 8 7 6 5 4 3 2 1",
                "1-1-9-8-7-6-5-4-3-2-1",
                "(11)98765.4321",
                "11.98765.4321",
                "011 98765-4321",
                "+55 (11) 9 8765-4321",
                "5511987654321",
                "11987654321 me chama",
                "fone (11) 98765-432l",
                "tel 11 9876S-4321 nao, 11 98765-432I",
                "contato: (2l) 34S6-789O, (21) 3456-789O",
                "１１ ９８７６５－４３２１",
                "11​98765​4321",
                "telefone 11 8765-4321",
        })
        void phonesWithStrangeSeparatorsOrLookalikeLetters(String text) {
            assertDecision(text, Decision.BLOCK);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "12345678909",
                "cpf 123 456 789 09",
                "cpf 1 2 3 4 5 6 7 8 9 0 9",
                "cnpj 12 345 678 0001 95",
        })
        void documentsWithoutTheUsualFormatting(String text) {
            assertDecision(text, Decision.BLOCK);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "zap: wa.me/5511987654321",
                "https://wa.me/5511987654321",
                "wa . me / 5511987654321",
                "wa ponto me/5511987654321",
                "wa(.)me/5511987654321",
                "wa[dot]me/5511987654321",
                "api.whatsapp.com/send?phone=5511987654321",
                "web.whatsapp.com/send?phone=5511987654321",
                "grupo: chat.whatsapp.com/AbCdEf",
                "telegram.me/lojinha",
                "telegram.dog/lojinha",
                "t . me / lojinha",
                "wa.link/abc123",
                "signal.me/#p/abc",
                "discord.gg/abc123",
        })
        void messengerLinksAndTheirVariants(String text) {
            assertDecision(text, Decision.BLOCK);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "p i x fulano@gmail.com",
                "p.i.x: fulano@gmail.com",
                "p-i-x fulano@gmail.com",
                "p_i_x fulano@gmail.com",
                "P1X fulano@gmail.com",
                "p!x fulano@gmail.com",
                "piiix fulano@gmail.com",
                "piks: fulano@gmail.com",
                "pyx fulano@gmail.com",
                "рix fulano@gmail.com",
                "p­ix fulano@gmail.com",
                "pix fulano arroba gmail ponto com",
                "pix: fulano [at] gmail [dot] com",
                "pix fulano @ gmail . com",
                "pix 123e4567e89b12d3a456426614174000",
                "pix 123E4567-E89B-12D3-A456-426614174000",
                "minha chave aleatória: 123e4567-e89b-12d3-a456-426614174000",
                "chave: fulano@gmail.com",
                "pix 98765-4321",
                "pix é o celular 9 8765 4321",
                "pix no fixo 3456-7890",
        })
        void maskedPixKeys(String text) {
            assertThat(CommerceFilter.check(text)).isEqualTo(new Verdict(Decision.BLOCK, "uma chave PIX"));
        }

        /** Achados da auditoria de segurança: emoji e marcadores como separador, letras soltas, número por extenso. */
        @ParameterizedTest
        @ValueSource(strings = {
                "1\uD83C\uDF3F1\uD83C\uDF3F9\uD83C\uDF3F8\uD83C\uDF3F7\uD83C\uDF3F6\uD83C\uDF3F5\uD83C\uDF3F4\uD83C\uDF3F3\uD83C\uDF3F2\uD83C\uDF3F1",
                "cpf 123\u2022456\u2022789\u202209",
                "pix: f u l a n o @ g m a i l . c o m",
                "me chama: onze nove oito sete seis cinco quatro tres dois um",
                "zap: onze, nove, oito, sete, seis, cinco, quatro, tr\u00eas, dois, um",
                "liga vinte e um nove nove oito sete seis cinco quatro tr\u00eas",
        })
        void evasionsFoundInTheSecurityAudit(String text) {
            assertDecision(text, Decision.BLOCK);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "zap 98765-4321",
                "z a p 98765 4321",
                "z4p 98765-4321",
                "wpp 9876-54321",
                "w.p.p: 98765 4321",
                "whats 98765-4321",
                "whatsapp: 3456-7890",
                "telegram 98765-4321",
        })
        void messengerNameWithLocalNumber(String text) {
            assertDecision(text, Decision.BLOCK);
        }
    }

    @Nested
    class Retem {

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
        void typicalAdsForModeration(String text) {
            assertDecision(text, Decision.HOLD);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "v e n d o 5g",
                "v.e.n.d.o prensado",
                "vendendo mudas",
                "veeendo sementes",
                "vendo 3,5 g",
                "vendemos clones",
                "R$ 30 o grama",
                "50 reais cada muda",
                "R$ 25/g",
                "valor a combinar",
                "aceito propostas",
                "chama no privado",
                "me chama no pv",
                "manda dm",
                "interessados chamar inbox",
                "chama no z a p",
                "chama no w h a t s",
                "fala no telegram",
                "me add no insta",
                "zap na bio",
                "zap final 4321",
                "manda um pix",
                "chave pix na bio",
                "só no pix",
                "aceito p.i.x",
                "envio pra todo brasil, frete gratis",
                "fulano arroba gmail ponto com",
                "contato: fulano [at] gmail [dot] com",
                "pedidos com o @lojinha420",
                "encomendas: @growshop_sp",
                "chama o @fulano no direct",
                "bit.ly/promo420",
                "linktr.ee/lojinha",
                "98765-4321",
                "11 98 765 43 21",
                "pics fulano@gmail.com",
        })
        void maskedOrAmbiguousAds(String text) {
            assertDecision(text, Decision.HOLD);
        }
    }

    @Nested
    class Permite {

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
        void everydayGrowLanguage(String text) {
            assertDecision(text, Decision.ALLOW);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "EC 1.8",
                "pH 6,2",
                "PPFD 900",
                "PPFD 900 a 1100 µmol/m²/s",
                "VPD 1.2 kPa",
                "fotoperíodo 18/6 e depois 12/12",
                "tenda 60x60x140",
                "LED 240W",
                "LED de 1000 watts",
                "3 g/L de nutriente",
                "colhi 120 g secos",
                "semana 7 de flora",
                "dia 45",
                "26 °C",
                "umidade 55%",
                "26 °C, umidade 55%, VPD 1.2 kPa, EC 1.8, pH 6.2, PPFD 900, 18/6",
                "pH 5.8 6.0 6.2 6.4 6.6",
                "EC 2.2 2.4 2.6 2.8 3.0",
                "temperaturas da semana: 24 25 26 24 23 25",
                "rega 1l 2l 3l 10l",
                "vasos de 11l, 20l e 30l",
                "datas: 2026-09-30, 2026-10-01 e 2026-10-15",
                "dia 21/09/2026 às 13h, dia 15/12/2025 10h",
                "germinou 21/09/2026 13 sementes",
                "às 08:30 liguei, às 20:30 desliguei",
                "Samsung LM301H",
                "Samsung LM301H EVO, driver Meanwell, placa QB288",
                "Gorilla Glue #4",
                "Gelato 41",
                "OG #18",
                "OG Kush e Gelato #33",
                "estou vendo",
                "tá vendo esses tricomas?",
                "vendo que as folhas amarelaram",
                "to só vendo se a EC sobe",
                "troca de vaso",
                "troquei o substrato",
                "troco a água do reservatório toda semana",
                "troco as mudas de vaso amanhã",
                "entregou bem o rendimento",
                "paguei caro na tenda",
                "paguei R$ 900 na tenda 60x60x140 e colhi 120 g secos",
                "custou R$ 45 o pacote de 5 sementes",
                "dica: compra no mercado livre",
                "a loja vende LED barato",
                "esse negócio de flor é demais",
                "pics da semana 5",
                "olha as pics: semana 7 de flora, dia 45",
                "paguei o LED no pix, R$ 800",
                "valeu @fulano",
                "segue o diário do @fulano, top",
                "falei com o @fulano sobre o PPFD",
                "o pH caiu pra 5.5 em 3 dias",
                "1o dia de flora",
                "comecei em 2024, terceiro cultivo",
                "veja o diário weediario.com.br/diarios/123e4567-e89b-12d3-a456-426614174000",
                "chave liga/desliga do timer quebrou",
                "versão 1.2.3 do app",
                "fiz LST no dia 12 e no dia 19",
                "3 plantas, 450 g no total, 150 g por planta",
                "regando 2x por dia, 500 ml cada",
                "Comprei o kit no mercado livre por R$ 450",
                "a planta mais alta tem 1,20 m e 98 cm de copa",
                "PPFD 600 800 1000 1200",
                "lux 35000 a 45000",
                "temp 24.5 26.1 25.8 23.9",
                "12/12 desde 01/09, colheita prevista 15/11",
                "não estou vendendo nada, só mostrando",
                "o pix do LED foi 1200 reais",
                "manda foto das flores",
                "chama a atenção o tamanho dos buds",
                "adiciona 2 ml de silício",
                "entra em flora semana que vem",
                "contato com a raiz",
                "tô trocando o substrato",
                "troco de lugar as plantas toda semana",
                "comprei 10 sementes por R$ 300 no banco",
                "colheita: 98,5 g, 2026-09-30",
                "dia 21 de setembro, 98 dias de vida",
                "o telegram tem um grupo bom de cultivo",
                "pics ou não aconteceu",
                "um dois tr\u00eas e j\u00e1 estava germinando",
                "semana um, dois e tr\u00eas sem rega",
                "vinte e cinco dias de flora, trinta e dois graus no pico",
                "\uD83D\uDD25\uD83D\uDD25 colheita linda \uD83C\uDF3F 26 \u00b0C e 55% de umidade",
                "notas \u2022 4 \u2022 5 \u2022 4 \u2022 5",
                "a e i o u do cultivo: \u00e1gua, espa\u00e7o, ilumina\u00e7\u00e3o, oxig\u00eanio, umidade",
        })
        void growVocabularyWithoutFalsePositives(String text) {
            assertDecision(text, Decision.ALLOW);
        }

        @Test
        void ignoresNullAndBlankFields() {
            assertThat(CommerceFilter.check(null, "  ", "")).isEqualTo(Verdict.ALLOWED);
        }

        @Test
        void diaryWithManyUpdatesStaysAllowed() {
            // Na mudança de visibilidade o título e todas as atualizações são analisados juntos.
            assertDecision(List.of("Gelato 41 indoor", "germinou 21/09/2026", "gastei R$ 300 no LED 240W",
                    "semana 7 de flora, EC 1.8, PPFD 900", "colhi 120 g secos").toArray(String[]::new),
                    Decision.ALLOW);
        }
    }

    @Nested
    class Mensagens {

        private static final List<String> CASES = List.of(
                "chama no zap 11 98765-4321", "t.me/lojinha", "pix: fulano@example.com", "vendo 5g R$50",
                "chama no privado", "bit.ly/promo420", "98765-4321", "pics fulano@gmail.com", "R$ 30 o grama");

        @Test
        void blockMessageIsFriendlyAndDoesNotEchoTheText() {
            for (String text : CASES) {
                Verdict verdict = CommerceFilter.check(text);
                String message = CommerceFilter.blockMessage(verdict);
                assertThat(verdict.decision()).as(text).isNotEqualTo(Decision.ALLOW);
                assertThat(message).as(text)
                        .startsWith("Isso parece ")
                        .endsWith("(veja os Termos de Uso).")
                        .doesNotContain("null")
                        .doesNotContain("4321")
                        .doesNotContain("fulano")
                        .hasSizeLessThan(200);
            }
        }

        @Test
        void requireAllowedOrHeldThrowsOnlyOnBlock() {
            assertThatThrownBy(() -> CommerceFilter.requireAllowedOrHeld("zap 98765-4321"))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("Termos de Uso");
            assertThat(CommerceFilter.requireAllowedOrHeld("vendo 5g").held()).isTrue();
            assertThat(CommerceFilter.requireAllowedOrHeld("EC 1.8").decision()).isEqualTo(Decision.ALLOW);
        }

        @Test
        void requireAllowedAlsoRejectsHeldContent() {
            assertThatThrownBy(() -> CommerceFilter.requireAllowed("Vendo sementes R$ 50"))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("anúncio de venda ou troca");
        }
    }

    @Nested
    class Desempenho {

        /** Textos de 10 mil caracteres feitos para forçar retrocesso nas expressões. */
        private static final List<String> ADVERSARIAL = List.of(
                "a".repeat(10_000),
                "a.".repeat(5_000),
                "1 ".repeat(5_000),
                "1.".repeat(5_000),
                "12-".repeat(3_333),
                "p i ".repeat(2_500),
                "pix ".repeat(2_500),
                "zap ".repeat(2_500),
                "vendo ".repeat(1_666),
                "@a".repeat(5_000),
                "a@a.".repeat(2_500),
                "a@".repeat(5_000),
                "r$ ".repeat(3_333),
                "chama ".repeat(1_666),
                "f ".repeat(5_000),
                "9".repeat(10_000),
                "pix 1 ".repeat(1_666),
                "abcdef0123".repeat(1_000),
                "pix 1 2 ".repeat(1_250),
                "zap 9 ".repeat(1_666),
                "R$ 1 g ".repeat(1_428),
                "@ab chama ".repeat(1_000),
                "semana 7 de flora, EC 1.8, pH 6,2, PPFD 900, 26 °C, umidade 55%, 18/6. ".repeat(140));

        /**
         * Medido numa máquina de desenvolvimento: 1 a 6 ms por texto depois do aquecimento. O limite folgado é para
         * não falhar em CI lento; retrocesso catastrófico levaria segundos.
         */
        @Test
        void analyzesTenThousandAdversarialCharactersInFewMilliseconds() {
            for (int i = 0; i < 20; i++) {
                ADVERSARIAL.forEach(CommerceFilter::check); // aquecimento do JIT
            }
            for (String text : ADVERSARIAL) {
                assertThat(text.length()).isBetween(9_000, 10_100);
                long best = Long.MAX_VALUE;
                for (int i = 0; i < 5; i++) {
                    long start = System.nanoTime();
                    CommerceFilter.check(text);
                    best = Math.min(best, System.nanoTime() - start);
                }
                assertThat(best / 1_000_000).as("%s...", text.substring(0, 12)).isLessThan(50);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"v3nd0", "VENDO", "véndo", "v3nd0", "ｖｅｎｄｏ", "vеndо"})
    void normalizesAccentsCaseAndLeetspeak(String text) {
        assertThat(CommerceFilter.normalize(text.toLowerCase())).isEqualTo("vendo");
    }

    private static void assertDecision(String text, Decision expected) {
        assertDecision(new String[] {text}, expected);
    }

    /**
     * O filtro roda sobre todo texto livre, então uma entrada maliciosa não pode prender a CPU (ReDoS). Entradas bem
     * maiores que o limite de qualquer campo, montadas para forçar backtracking nas regras de telefone, chave PIX e
     * números por extenso.
     */
    @ParameterizedTest
    @ValueSource(strings = {"1 ", "1-", "1 (", "9.", "0123456789abcdef-", "a1 ", "um dois tres ", "pix ", "@a. ",
            "wa.me/", "1\t", "((((1", "a ", "vinte e um ", "um,  dois - "})
    void adversarialInputDoesNotHangTheFilter(String unit) {
        String hostile = unit.repeat(50_000 / unit.length()) + "!x";
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> CommerceFilter.check(hostile));
    }

    private static void assertDecision(String[] texts, Decision expected) {
        Verdict verdict = CommerceFilter.check(texts);
        assertThat(verdict.decision()).as("%s -> %s", String.join(" | ", texts), verdict.reason()).isEqualTo(expected);
    }
}
