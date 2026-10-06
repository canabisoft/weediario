package br.com.weediario.identity.application.port;

/**
 * Prova de trabalho do cadastro: barreira contra criação de contas em série, sem serviço de terceiros (nada de
 * reCAPTCHA), sem cookie e sem IP. O servidor emite um desafio assinado; o navegador gasta um pouco de CPU para
 * resolvê-lo; o cadastro só segue com a solução, que vale uma vez só. Para uma pessoa é um segundo; para quem quer
 * criar milhares de contas, é um custo que cresce com cada uma.
 * <p>
 * Protocolo do ALTCHA: {@code challenge = SHA-256(salt + number)}, com {@code number} entre 0 e {@code maxNumber};
 * {@code signature = HMAC(challenge)}; o prazo vai dentro do {@code salt}.
 */
public interface RegistrationChallenge {

    Challenge issue();

    /**
     * Confere a solução e a marca como usada.
     *
     * @throws br.com.weediario.shared.domain.DomainException se faltar, estiver errada, vencida ou já usada
     */
    void redeem(Solution solution);

    record Challenge(String algorithm, String challenge, String salt, long maxNumber, String signature) {
    }

    record Solution(String challenge, String salt, long number, String signature) {
    }
}
