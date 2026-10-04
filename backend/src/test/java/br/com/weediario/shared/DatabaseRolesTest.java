package br.com.weediario.shared;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Os papéis de deploy/postgres/roles.sql, num Postgres próprio (não o compartilhado dos testes de integração): o
 * backend conecta como {@code weediario_app}, que só lê e escreve linhas. Simula uma instalação antiga, com tabela
 * criada pelo superusuário, e roda o script duas vezes, como acontece a cada {@code docker compose up}.
 */
class DatabaseRolesTest {

    private static final Path ROLES_SQL = Path.of("..", "deploy", "postgres", "roles.sql");

    private static EmbeddedPostgres postgres;

    @BeforeAll
    static void setUp() throws Exception {
        postgres = EmbeddedPostgres.start();
        try (Connection admin = postgres.getPostgresDatabase().getConnection(); Statement sql = admin.createStatement()) {
            sql.execute("create database weediario");
        }
        try (Connection admin = connect("postgres"); Statement sql = admin.createStatement()) {
            sql.execute("create table antiga (id int primary key, nome text)");
            sql.execute("insert into antiga values (1, 'da época do superusuário')");
            String roles = Files.readString(ROLES_SQL);
            sql.execute(roles);
            sql.execute(roles);
        }
        // O que uma migration futura cria, rodando como dono do schema.
        try (Connection owner = connect("weediario_owner"); Statement sql = owner.createStatement()) {
            sql.execute("create table nova (id int primary key)");
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        postgres.close();
    }

    @Test
    void appReadsAndWritesRowsOfOldAndNewTables() throws Exception {
        try (Connection app = connect("weediario_app"); Statement sql = app.createStatement()) {
            sql.execute("insert into antiga values (2, 'nova linha')");
            sql.execute("update antiga set nome = 'editada' where id = 2");
            sql.execute("insert into nova values (1)");
            sql.execute("delete from nova where id = 1");
            try (ResultSet rows = sql.executeQuery("select count(*) from antiga")) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(2);
            }
        }
    }

    @Test
    void appCannotChangeTheSchema() throws Exception {
        try (Connection app = connect("weediario_app"); Statement sql = app.createStatement()) {
            assertDenied(sql, "create table invasora (id int)");
            assertDenied(sql, "drop table antiga");
            assertDenied(sql, "alter table antiga add column extra text");
            assertDenied(sql, "truncate antiga");
            assertDenied(sql, "create function f() returns int language sql as 'select 1'");
        }
    }

    @Test
    void appCannotReachServerFilesProgramsOrPasswordHashes() throws Exception {
        try (Connection app = connect("weediario_app"); Statement sql = app.createStatement()) {
            assertDenied(sql, "copy (select 1) to program 'id'");
            assertDenied(sql, "copy antiga to '/tmp/vazamento.csv'");
            assertDenied(sql, "select pg_read_file('postgresql.conf')");
            assertDenied(sql, "select rolpassword from pg_authid");
            assertDenied(sql, "create extension if not exists dblink");
        }
    }

    @Test
    void appCommandsHaveATimeLimit() throws Exception {
        try (Connection app = connect("weediario_app"); Statement sql = app.createStatement();
             ResultSet timeout = sql.executeQuery("show statement_timeout")) {
            timeout.next();
            assertThat(timeout.getString(1)).isEqualTo("30s");
        }
    }

    @Test
    void ownerIsNotASuperuserAndOwnsTheOldTables() throws Exception {
        try (Connection owner = connect("weediario_owner"); Statement sql = owner.createStatement()) {
            assertDenied(sql, "copy (select 1) to program 'id'");
            try (ResultSet rows = sql.executeQuery("select tableowner from pg_tables where tablename = 'antiga'")) {
                rows.next();
                assertThat(rows.getString(1)).isEqualTo("weediario_owner");
            }
        }
    }

    private static Connection connect(String user) throws SQLException {
        return postgres.getDatabase(user, "weediario").getConnection();
    }

    private static void assertDenied(Statement sql, String command) {
        assertThatThrownBy(() -> sql.execute(command)).as(command).isInstanceOf(SQLException.class);
    }
}
