package com.example.apipractice.integration.config;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testcontainers設定（共通ベースクラス）
 * IT: Oracle XEコンテナの起動と接続情報の設定
 *
 * 教材用の設計:
 * - わざと @Container static で「テストクラスごとに起動」
 * - 後で「全テスト共有」に改善することで、最適化の価値を実感できる
 */
@Testcontainers
public abstract class TestcontainersConfig {

    /**
     * Oracle XE コンテナ
     * gvenzl/oracle-xe:21-slim を使用（無料、軽量）
     *
     * わざと static にしてテストクラスごとに起動させる（教材用）
     */
    @Container
    protected static OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-xe:21-slim")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass")
            .withReuse(false);  // わざと再利用しない（教材用）

    /**
     * Spring の datasource プロパティを動的に設定
     */
    @DynamicPropertySource
    static void setDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", oracleContainer::getJdbcUrl);
        registry.add("spring.datasource.username", oracleContainer::getUsername);
        registry.add("spring.datasource.password", oracleContainer::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "oracle.jdbc.OracleDriver");
    }
}
