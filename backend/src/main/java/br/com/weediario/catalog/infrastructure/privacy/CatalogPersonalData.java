package br.com.weediario.catalog.infrastructure.privacy;

import br.com.weediario.shared.application.PersonalDataSection;
import br.com.weediario.shared.infrastructure.persistence.PersonalDataRows;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Itens do catálogo aberto cadastrados pela pessoa e pedidos do programa de breeders. Ao apagar a conta, os itens
 * ficam no catálogo (dado aberto, ODbL) sem autor.
 */
@Component
public class CatalogPersonalData implements PersonalDataSection {

    private final NamedParameterJdbcTemplate jdbc;

    public CatalogPersonalData(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String name() {
        return "catalogo";
    }

    @Override
    public Object export(UUID userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("geneticas_cadastradas", PersonalDataRows.of(jdbc, """
                select id, name, created_at from strains where created_by = :userId""", userId));
        data.put("breeders_cadastrados", PersonalDataRows.of(jdbc, """
                select id, name, created_at from breeders where created_by = :userId""", userId));
        data.put("marcas_cadastradas", PersonalDataRows.of(jdbc, """
                select id, name, created_at from brands where created_by = :userId""", userId));
        data.put("produtos_cadastrados", PersonalDataRows.of(jdbc, """
                select id, name, created_at from products where created_by = :userId""", userId));
        data.put("programa_de_breeders", PersonalDataRows.of(jdbc, """
                select * from breeder_representatives where user_id = :userId""", userId));
        return data;
    }
}
