package br.com.weediario.review.domain.repository;

import br.com.weediario.review.domain.model.Seedbank;

import java.util.Optional;

public interface SeedbankRepository {

    Seedbank save(Seedbank seedbank);

    Optional<Seedbank> findByKey(String key);
}
