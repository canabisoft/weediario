package br.com.weediario.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.autoconfigure.flyway.FlywayDataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Banco dos testes com os mesmos papéis da produção (deploy/postgres/roles.sql): o Flyway roda como
 * {@code weediario_owner} e a aplicação como {@code weediario_app}, que só lê e escreve linhas. Uma migration nova que
 * a aplicação não consiga usar, ou um comando que precise de mais do que DML, quebra os testes.
 */
@TestConfiguration(proxyBeanMethods = false)
public class EmbeddedPostgresConfig {

    private static final String DATABASE = "weediario";
    private static final Path ROLES_SQL = Path.of("..", "deploy", "postgres", "roles.sql");

    @Bean(destroyMethod = "close")
    EmbeddedPostgres embeddedPostgres() throws IOException, SQLException {
        EmbeddedPostgres postgres = EmbeddedPostgres.start();
        try (Connection admin = postgres.getPostgresDatabase().getConnection(); Statement sql = admin.createStatement()) {
            sql.execute("create database " + DATABASE);
        }
        try (Connection admin = postgres.getDatabase("postgres", DATABASE).getConnection();
             Statement sql = admin.createStatement()) {
            sql.execute(Files.readString(ROLES_SQL));
        }
        return postgres;
    }

    @Bean
    @Primary
    DataSource dataSource(EmbeddedPostgres postgres) {
        return postgres.getDatabase("weediario_app", DATABASE);
    }

    @Bean
    @FlywayDataSource
    DataSource flywayDataSource(EmbeddedPostgres postgres) {
        return postgres.getDatabase("weediario_owner", DATABASE);
    }
}
