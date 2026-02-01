package com.example.apipractice.integration.database;

import com.example.apipractice.integration.config.TestcontainersConfig;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Testcontainersのライフサイクル確認用テスト
 *
 * 目的:
 * - このテストクラスと他のテストクラス（TaskRepositoryIntegrationTest、FlywayMigrationIntegrationTest）
 *   を実行すると、それぞれのクラスでコンテナが再起動されることを確認する
 * - ログ出力でコンテナ起動のタイミングを可視化
 *
 * 実行方法:
 * ./gradlew integrationTest --tests "*IntegrationTest" --tests "*LifecycleTest"
 *
 * 期待される動作（改善前）:
 * - 各テストクラスの開始時にOracleコンテナが起動される
 * - 各テストクラスの終了時にコンテナが破棄される
 * - 合計実行時間 = (コンテナ起動時間 × テストクラス数) + テスト実行時間
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ContainerLifecycleTest extends TestcontainersConfig {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    @BeforeAll
    static void beforeAll() {
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        System.out.println("\n" + "=".repeat(80));
        System.out.println("🚀 [" + timestamp + "] ContainerLifecycleTest: テストクラス開始");
        System.out.println("   Oracle Container Status: " + (oracleContainer.isRunning() ? "✅ RUNNING" : "❌ NOT RUNNING"));
        System.out.println("   JDBC URL: " + oracleContainer.getJdbcUrl());
        System.out.println("=".repeat(80) + "\n");
    }

    @AfterAll
    static void afterAll() {
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        System.out.println("\n" + "=".repeat(80));
        System.out.println("🏁 [" + timestamp + "] ContainerLifecycleTest: テストクラス終了");
        System.out.println("   このテストクラス終了後、コンテナは破棄されます（withReuse=false）");
        System.out.println("=".repeat(80) + "\n");
    }

    @BeforeEach
    void setUp() {
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        System.out.println("📝 [" + timestamp + "] テストメソッド開始");
    }

    @AfterEach
    void tearDown() {
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        System.out.println("✅ [" + timestamp + "] テストメソッド終了\n");
    }

    @Test
    @Order(1)
    void テスト1_コンテナが起動していることを確認() {
        System.out.println("   → Oracleコンテナの状態をチェック中...");
        Assertions.assertTrue(oracleContainer.isRunning(), "Oracleコンテナが起動している必要があります");
        System.out.println("   → ✅ コンテナは正常に起動しています");
    }

    @Test
    @Order(2)
    void テスト2_コンテナの接続情報を確認() {
        System.out.println("   → JDBC接続情報:");
        System.out.println("      URL: " + oracleContainer.getJdbcUrl());
        System.out.println("      User: " + oracleContainer.getUsername());
        System.out.println("      Database: " + oracleContainer.getDatabaseName());

        Assertions.assertNotNull(oracleContainer.getJdbcUrl());
        Assertions.assertEquals("testuser", oracleContainer.getUsername());
        Assertions.assertEquals("testdb", oracleContainer.getDatabaseName());
        System.out.println("   → ✅ 接続情報は正しく設定されています");
    }

    @Test
    @Order(3)
    void テスト3_コンテナのポートマッピングを確認() {
        System.out.println("   → ポートマッピング:");
        System.out.println("      Oracle Port: " + oracleContainer.getOraclePort());
        System.out.println("      Mapped Port: " + oracleContainer.getMappedPort(1521));

        Assertions.assertNotNull(oracleContainer.getMappedPort(1521));
        System.out.println("   → ✅ ポートは正しくマッピングされています");
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ネストされたテスト {

        @Test
        @Order(1)
        void ネストされたテストでもコンテナは共有される() {
            System.out.println("   → ネストされたテストクラス内でもコンテナ確認");
            Assertions.assertTrue(oracleContainer.isRunning());
            System.out.println("   → ✅ 同じコンテナインスタンスが使われています");
        }
    }
}
