package br.com.weediario.treatment.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Ao subir, regrava com a chave atual e no formato atual (contexto da linha como AAD) todo texto de saúde que ainda
 * esteja no formato antigo ou cifrado com uma chave antiga. Assim a troca de chave é: pôr a nova em
 * {@code encryption-key}, a velha em {@code previous-keys}, subir, e depois tirar a velha.
 * <p>
 * Não registra nenhum texto nem id: só quantos valores foram regravados.
 */
@Component
public class TreatmentRecipher implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TreatmentRecipher.class);

    /** Colunas cifradas: escolhidas aqui, nunca por texto de fora. */
    private static final List<String[]> COLUMNS = List.of(
            new String[]{"treatment_patients", "name_enc"},
            new String[]{"treatment_patients", "notes_enc"},
            new String[]{"treatment_symptoms", "name_enc"},
            new String[]{"treatment_batches", "name_enc"},
            new String[]{"treatment_batches", "notes_enc"},
            new String[]{"treatment_doses", "notes_enc"});

    private final NamedParameterJdbcTemplate jdbc;
    private final FieldCipher cipher;
    private final TransactionTemplate transactions;

    public TreatmentRecipher(NamedParameterJdbcTemplate jdbc, FieldCipher cipher, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.transactions = transactions;
    }

    @Override
    public void run(ApplicationArguments args) {
        int total = 0;
        for (String[] column : COLUMNS) {
            total += recipher(column[0], column[1]);
        }
        if (total > 0) {
            log.info("{} valor(es) de saúde regravados com a chave e o formato atuais", total);
        }
    }

    int recipher(String table, String column) {
        Integer changed = transactions.execute(status -> {
            int count = 0;
            List<Row> rows = jdbc.query("select id, " + column + " as value from " + table
                            + " where " + column + " is not null for update",
                    new MapSqlParameterSource(), (rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getBytes("value")));
            for (Row row : rows) {
                if (!cipher.needsRecipher(row.value())) {
                    continue;
                }
                String context = FieldCipher.context(table, column, row.id());
                byte[] sealed = cipher.encrypt(cipher.decrypt(row.value(), context), context);
                jdbc.update("update " + table + " set " + column + " = :value where id = :id",
                        new MapSqlParameterSource("value", sealed).addValue("id", row.id()));
                count++;
            }
            return count;
        });
        return changed == null ? 0 : changed;
    }

    private record Row(UUID id, byte[] value) {
    }
}
