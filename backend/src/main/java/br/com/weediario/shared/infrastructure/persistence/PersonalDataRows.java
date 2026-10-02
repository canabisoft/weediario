package br.com.weediario.shared.infrastructure.persistence;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Linhas do banco para a exportação de dados pessoais: datas em ISO-8601, nenhuma coluna binária (cifrados e hashes
 * não fazem sentido fora do app) e as colunas na ordem da tabela.
 */
public final class PersonalDataRows {

    private PersonalDataRows() {
    }

    /** @param sql consulta com o parâmetro {@code :userId} */
    public static List<Map<String, Object>> of(NamedParameterJdbcTemplate jdbc, String sql, UUID userId) {
        return jdbc.queryForList(sql, new MapSqlParameterSource("userId", userId)).stream()
                .map(PersonalDataRows::readable)
                .toList();
    }

    private static Map<String, Object> readable(Map<String, Object> row) {
        Map<String, Object> clean = new LinkedHashMap<>();
        row.forEach((column, value) -> {
            if (value instanceof byte[]) {
                return;
            }
            clean.put(column, switch (value) {
                case Timestamp timestamp -> timestamp.toInstant().toString();
                case Date date -> date.toLocalDate().toString();
                case null -> null;
                default -> value instanceof Number || value instanceof Boolean || value instanceof UUID
                        ? value : value.toString();
            });
        });
        return clean;
    }
}
