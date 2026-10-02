package br.com.weediario.diary.infrastructure.privacy;

import br.com.weediario.shared.application.PersonalDataSection;
import br.com.weediario.shared.infrastructure.persistence.PersonalDataRows;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Diários (inclusive privados), atualizações semanais, fotos vinculadas e colheitas da pessoa. */
@Component
public class DiaryPersonalData implements PersonalDataSection {

    private final NamedParameterJdbcTemplate jdbc;

    public DiaryPersonalData(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String name() {
        return "diarios";
    }

    @Override
    public Object export(UUID userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("diarios", PersonalDataRows.of(jdbc, """
                select * from grow_diaries where owner_id = :userId order by created_at""", userId));
        data.put("atualizacoes", PersonalDataRows.of(jdbc, """
                select u.* from diary_updates u join grow_diaries d on d.id = u.diary_id
                 where d.owner_id = :userId order by u.diary_id, u.created_at""", userId));
        data.put("fotos_das_atualizacoes", PersonalDataRows.of(jdbc, """
                select p.* from diary_update_photos p
                  join diary_updates u on u.id = p.update_id
                  join grow_diaries d on d.id = u.diary_id
                 where d.owner_id = :userId""", userId));
        return data;
    }

    /**
     * Apaga os diários antes da conta, na mesma transação. As fotos das atualizações apontam para {@code images} sem
     * cascade; se a conta fosse apagada primeiro, o banco tentaria remover as imagens enquanto o vínculo ainda existe.
     */
    @Override
    public Runnable prepareErasure(UUID userId) {
        jdbc.update("delete from grow_diaries where owner_id = :userId",
                new MapSqlParameterSource("userId", userId));
        return () -> {
        };
    }
}
