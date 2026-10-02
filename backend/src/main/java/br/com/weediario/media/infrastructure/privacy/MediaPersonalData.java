package br.com.weediario.media.infrastructure.privacy;

import br.com.weediario.media.application.port.ImageStorage;
import br.com.weediario.media.domain.model.ImageId;
import br.com.weediario.media.domain.model.ImageVariant;
import br.com.weediario.shared.application.PersonalDataSection;
import br.com.weediario.shared.infrastructure.persistence.PersonalDataRows;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Fotos enviadas pela pessoa (diários e avatar). A exportação leva a versão grande de cada uma (já sem metadados); ao
 * apagar a conta, o banco remove os registros e esta seção apaga os arquivos do disco depois do commit.
 */
@Component
public class MediaPersonalData implements PersonalDataSection {

    private final NamedParameterJdbcTemplate jdbc;
    private final ImageStorage storage;

    public MediaPersonalData(NamedParameterJdbcTemplate jdbc, ImageStorage storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    @Override
    public String name() {
        return "fotos";
    }

    @Override
    public Object export(UUID userId) {
        return PersonalDataRows.of(jdbc,
                "select id, width, height, status, created_at from images where owner_id = :userId order by created_at",
                userId);
    }

    @Override
    public void writeFiles(UUID userId, FileSink sink) {
        for (UUID id : ownedImages(userId)) {
            storage.load(new ImageId(id), ImageVariant.FULL).ifPresent(image ->
                    sink.put("fotos/" + id + "." + image.format().extension(), image.content()));
        }
    }

    @Override
    public Runnable prepareErasure(UUID userId) {
        List<UUID> images = ownedImages(userId);
        return () -> images.forEach(id -> storage.delete(new ImageId(id)));
    }

    private List<UUID> ownedImages(UUID userId) {
        return jdbc.queryForList("select id from images where owner_id = :userId order by created_at",
                new MapSqlParameterSource("userId", userId), UUID.class);
    }
}
