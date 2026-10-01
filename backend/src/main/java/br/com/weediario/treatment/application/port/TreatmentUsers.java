package br.com.weediario.treatment.application.port;

import java.util.Optional;
import java.util.UUID;

/** Busca uma conta ativa pelo apelido, para convidar como cuidadora. */
public interface TreatmentUsers {

    Optional<UUID> activeUserId(String username);
}
