package br.com.weediario.identity.domain.repository;

import br.com.weediario.identity.domain.model.SecurityEvent;
import br.com.weediario.identity.domain.model.UserId;

import java.time.Instant;
import java.util.List;

public interface SecurityEventRepository {

    void record(SecurityEvent event);

    /** Mais recentes primeiro. */
    List<SecurityEvent> since(UserId userId, Instant since);

    void deleteBefore(Instant instant);
}
