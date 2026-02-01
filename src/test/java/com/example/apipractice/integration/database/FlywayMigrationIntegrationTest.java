package com.example.apipractice.integration.database;

import com.example.apipractice.integration.config.TestcontainersConfig;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Flyway Migration の統合テスト
 * IT: Oracle Testcontainers + Flyway
 *
 * テスト方針:
 * - Flywayマイグレーションが正しく適用されているか検証
 * - テーブル構造、インデックス、制約の確認
 * - Oracle固有の機能（IDENTITY列、COMMENTなど）の動作確認
 *
 * 注意: このテストクラスは TaskRepositoryIntegrationTest とは
 *       別のクラスなので、わざとコンテナが再起動されます（改善前の設計）
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class FlywayMigrationIntegrationTest extends TestcontainersConfig {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void Flywayマイグレーションが適用されている() {
        // when
        var info = flyway.info();
        var migrations = info.all();

        // then
        assertThat(migrations).isNotEmpty();
        assertThat(migrations[0].getVersion().getVersion()).isEqualTo("1");
        assertThat(migrations[0].getDescription()).isEqualTo("create task table");
        assertThat(migrations[0].getState().isApplied()).isTrue();
    }

    @Test
    void tasksテーブルが存在する() {
        // when
        String sql = """
            SELECT table_name
            FROM user_tables
            WHERE table_name = 'TASKS'
            """;

        List<String> tables = jdbcTemplate.queryForList(sql, String.class);

        // then
        assertThat(tables).containsExactly("TASKS");
    }

    @Test
    void tasksテーブルに必要なカラムが存在する() {
        // when
        String sql = """
            SELECT column_name, data_type, nullable
            FROM user_tab_columns
            WHERE table_name = 'TASKS'
            ORDER BY column_id
            """;

        List<Map<String, Object>> columns = jdbcTemplate.queryForList(sql);

        // then
        assertThat(columns).hasSize(6);

        // ID列の検証
        Map<String, Object> idColumn = columns.get(0);
        assertThat(idColumn.get("COLUMN_NAME")).isEqualTo("ID");
        assertThat(idColumn.get("DATA_TYPE")).isEqualTo("NUMBER");
        assertThat(idColumn.get("NULLABLE")).isEqualTo("N");

        // TITLE列の検証
        Map<String, Object> titleColumn = columns.get(1);
        assertThat(titleColumn.get("COLUMN_NAME")).isEqualTo("TITLE");
        assertThat(titleColumn.get("DATA_TYPE")).isEqualTo("VARCHAR2");
        assertThat(titleColumn.get("NULLABLE")).isEqualTo("N");

        // STATUS列の検証
        Map<String, Object> statusColumn = columns.get(3);
        assertThat(statusColumn.get("COLUMN_NAME")).isEqualTo("STATUS");
        assertThat(statusColumn.get("DATA_TYPE")).isEqualTo("VARCHAR2");
    }

    @Test
    void tasksテーブルのインデックスが作成されている() {
        // when
        String sql = """
            SELECT index_name
            FROM user_indexes
            WHERE table_name = 'TASKS'
            AND index_name IN ('IDX_TASKS_STATUS', 'IDX_TASKS_CREATED_AT')
            ORDER BY index_name
            """;

        List<String> indexes = jdbcTemplate.queryForList(sql, String.class);

        // then
        assertThat(indexes).containsExactlyInAnyOrder(
            "IDX_TASKS_STATUS",
            "IDX_TASKS_CREATED_AT"
        );
    }

    @Test
    void tasksテーブルの主キー制約が存在する() {
        // when
        String sql = """
            SELECT constraint_name, constraint_type
            FROM user_constraints
            WHERE table_name = 'TASKS'
            AND constraint_type = 'P'
            """;

        List<Map<String, Object>> constraints = jdbcTemplate.queryForList(sql);

        // then
        assertThat(constraints).hasSize(1);
        assertThat(constraints.get(0).get("CONSTRAINT_TYPE")).isEqualTo("P");
    }

    @Test
    void IDENTITY列でIDが自動採番される() {
        // given & when
        String insertSql = """
            INSERT INTO tasks (title, description, status)
            VALUES (?, ?, ?)
            """;

        jdbcTemplate.update(insertSql, "Test Task 1", "Description 1", "TODO");
        jdbcTemplate.update(insertSql, "Test Task 2", "Description 2", "TODO");

        String selectSql = "SELECT id FROM tasks ORDER BY id";
        List<Long> ids = jdbcTemplate.queryForList(selectSql, Long.class);

        // then
        assertThat(ids).hasSize(2);
        assertThat(ids.get(0)).isGreaterThan(0);
        assertThat(ids.get(1)).isEqualTo(ids.get(0) + 1);
    }

    @Test
    void デフォルト値が設定されている() {
        // given & when
        String insertSql = """
            INSERT INTO tasks (title)
            VALUES (?)
            """;

        jdbcTemplate.update(insertSql, "Minimal Task");

        String selectSql = """
            SELECT status, created_at, updated_at
            FROM tasks
            WHERE title = 'Minimal Task'
            """;

        Map<String, Object> result = jdbcTemplate.queryForMap(selectSql);

        // then
        assertThat(result.get("STATUS")).isEqualTo("TODO");
        assertThat(result.get("CREATED_AT")).isNotNull();
        assertThat(result.get("UPDATED_AT")).isNotNull();
    }

    @Test
    void テーブルコメントが設定されている() {
        // when
        String sql = """
            SELECT comments
            FROM user_tab_comments
            WHERE table_name = 'TASKS'
            """;

        String comment = jdbcTemplate.queryForObject(sql, String.class);

        // then
        assertThat(comment).isEqualTo("タスク管理テーブル");
    }
}
