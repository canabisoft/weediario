package br.com.weediario.identity.domain.repository;

import br.com.weediario.identity.domain.model.AccountEmail;
import br.com.weediario.identity.domain.model.UserId;

import java.util.Optional;

/** Não existe busca por endereço: o e-mail nunca identifica nem localiza uma conta. */
public interface AccountEmailRepository {

    Optional<AccountEmail> find(UserId userId);

    void save(AccountEmail email);

    void delete(UserId userId);
}
