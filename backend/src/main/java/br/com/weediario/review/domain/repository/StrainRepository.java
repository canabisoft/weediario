package br.com.weediario.review.domain.repository;

import br.com.weediario.review.domain.model.Strain;
import br.com.weediario.review.domain.model.StrainKey;

import java.util.Optional;

public interface StrainRepository {

    Strain save(Strain strain);

    Optional<Strain> findByKey(StrainKey key);
}
