package br.com.weediario.nutrition.infrastructure.privacy;

import br.com.weediario.shared.application.PersonalDataSection;
import br.com.weediario.shared.infrastructure.persistence.PersonalDataRows;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Setups, produtos usados, receitas de nutrição (com ingredientes), notas e comentários em receitas. */
@Component
public class NutritionPersonalData implements PersonalDataSection {

    private final NamedParameterJdbcTemplate jdbc;

    public NutritionPersonalData(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String name() {
        return "cultivo_e_receitas";
    }

    @Override
    public Object export(UUID userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("setups", PersonalDataRows.of(jdbc, """
                select * from grow_setups where owner_id = :userId order by created_at""", userId));
        data.put("produtos_dos_setups", PersonalDataRows.of(jdbc, """
                select sp.* from grow_setup_products sp join grow_setups s on s.id = sp.setup_id
                 where s.owner_id = :userId""", userId));
        data.put("produtos_dos_diarios", PersonalDataRows.of(jdbc, """
                select dp.* from diary_products dp join grow_diaries d on d.id = dp.diary_id
                 where d.owner_id = :userId""", userId));
        data.put("receitas", PersonalDataRows.of(jdbc, """
                select * from recipes where owner_id = :userId order by created_at""", userId));
        data.put("ingredientes_das_receitas", PersonalDataRows.of(jdbc, """
                select i.* from recipe_ingredients i join recipes r on r.id = i.recipe_id
                 where r.owner_id = :userId""", userId));
        data.put("receitas_nos_diarios", PersonalDataRows.of(jdbc, """
                select dr.* from diary_recipes dr join grow_diaries d on d.id = dr.diary_id
                 where d.owner_id = :userId""", userId));
        data.put("notas_dadas_a_receitas", PersonalDataRows.of(jdbc, """
                select * from recipe_ratings where rater_id = :userId""", userId));
        data.put("comentarios_em_receitas", PersonalDataRows.of(jdbc, """
                select * from recipe_comments where author_id = :userId order by created_at""", userId));
        return data;
    }
}
