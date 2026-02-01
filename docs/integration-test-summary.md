# 統合テスト（IT）サマリー

## テストクラス一覧

### 1. TaskApiIntegrationTest

**パス**: `src/test/java/com/example/apipractice/integration/api/TaskApiIntegrationTest.java`

**テスト対象**: Task REST API（E2Eフロー）

**構成**:
- Spring Boot起動: `@SpringBootTest(webEnvironment = RANDOM_PORT)`
- Oracle Database: Testcontainers（gvenzl/oracle-xe:21-slim）
- Flyway Migration: 自動適用
- 外部API: WireMock（通知API）
- HTTPクライアント: TestRestTemplate

**テストケース一覧**:

| # | テストメソッド名 | 分類 | 検証内容 |
|---|----------------|------|----------|
| 1 | タスク作成から完了までのE2Eフロー | E2E | POST→GET→完了の全フロー |
| 2 | 存在しないタスクを取得すると404が返る | エラーハンドリング | 404ステータスコード |
| 3 | 完了済みタスクを完了しようとすると400が返る | ビジネスルール | 再完了の禁止 |
| 4 | POSTで作成したタスクがGETで取得できる | データ永続化 | DB保存の検証 |
| 5 | タスク完了後の状態変更がDBに正しく反映される | 状態遷移 | 完了状態の永続化 |
| 6 | 複数のタスクを作成して個別に取得できる | データ分離 | 複数レコードの独立性 |

**カバレッジ**:
- APIエンドポイント: 3/3（100%）
  - `POST /tasks`
  - `GET /tasks/{id}`
  - `POST /tasks/{id}/complete`
- HTTPステータスコード: 4種類
  - 200 OK
  - 201 Created
  - 400 Bad Request
  - 404 Not Found

### 2. TaskRepositoryIntegrationTest

**パス**: `src/test/java/com/example/apipractice/integration/infrastructure/TaskRepositoryIntegrationTest.java`

**テスト対象**: TaskRepository（Repository層）

**構成**:
- Spring Boot起動: `@SpringBootTest`
- Oracle Database: Testcontainers
- `@Transactional`: テスト後に自動ロールバック

**テストケース概要**:
- CRUD操作の検証
- ドメインモデル⇔Entity変換の正確性

### 3. FlywayMigrationIntegrationTest

**パス**: `src/test/java/com/example/apipractice/integration/database/FlywayMigrationIntegrationTest.java`

**テスト対象**: Flywayマイグレーション

**テストケース概要**:
- マイグレーションの適用確認
- テーブル構造の検証
- インデックス、制約の確認

## HTTPテストツール: TestRestTemplate

### 選定理由

#### 1. 教材として最適

**シンプルで理解しやすいAPI:**

```java
// GET
ResponseEntity<TaskResponse> response =
    restTemplate.getForEntity("/tasks/1", TaskResponse.class);

// POST
ResponseEntity<TaskResponse> response =
    restTemplate.postForEntity("/tasks", request, TaskResponse.class);
```

**学習コストが低い:**
- Javaの標準的なメソッド呼び出し
- 特殊なDSLや構文なし
- Spring Bootの公式ドキュメントで推奨

#### 2. 実HTTPリクエスト

**本番環境に近い検証:**
- 実際にサーバーを起動（RANDOM_PORT）
- HTTPプロトコルレベルでの動作確認
- ネットワーク層、シリアライゼーション、HTTPヘッダーも含めて検証

#### 3. Spring Boot標準

**追加の依存不要:**
- `spring-boot-starter-test`に含まれる
- 別ライブラリのインストール不要
- Spring Bootとの統合が完璧

### 他のツールとの比較

| 特徴 | TestRestTemplate | MockMvc | RestAssured |
|------|-----------------|---------|-------------|
| サーバー起動 | ✅ 実HTTP | ❌ モック | ✅ 実HTTP |
| 学習コスト | ⭐ 低 | ⭐⭐ 低〜中 | ⭐⭐⭐ 中 |
| 記述量 | 少ない | 中程度 | 少ない（DSL） |
| 検証の柔軟性 | 中 | 高 | 高 |
| 追加依存 | 不要 | 不要 | 必要 |
| 推奨用途 | **E2E統合テスト** | Controller単体 | API仕様テスト |

### コード比較

同じテストを3つのツールで実装した場合の比較：

#### TestRestTemplate（採用）

```java
@Test
void タスクを作成できる() {
    CreateTaskRequest request = new CreateTaskRequest();
    request.setTitle("Test Task");

    ResponseEntity<TaskResponse> response = restTemplate.postForEntity(
        "/tasks",
        request,
        TaskResponse.class
    );

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().getTitle()).isEqualTo("Test Task");
}
```

**メリット**: 直感的、読みやすい、追加依存なし

#### MockMvc

```java
@Test
void タスクを作成できる() throws Exception {
    mockMvc.perform(post("/tasks")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"Test Task\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("Test Task"));
}
```

**メリット**: 高速、詳細なHTTP検証
**デメリット**: 実HTTPではない、E2Eには不向き

#### RestAssured

```java
@Test
void タスクを作成できる() {
    CreateTaskRequest request = new CreateTaskRequest();
    request.setTitle("Test Task");

    given()
        .contentType(ContentType.JSON)
        .body(request)
    .when()
        .post("/tasks")
    .then()
        .statusCode(201)
        .body("title", equalTo("Test Task"));
}
```

**メリット**: BDDスタイル、読みやすい
**デメリット**: 追加依存、学習コスト

## テストケースの詳細

### 1. E2Eフロー（正常系）

**テスト名**: `タスク作成から完了までのE2Eフロー`

**シナリオ**:
1. `POST /tasks` でタスク作成 → 201 Created
2. WireMockが呼ばれたことを確認
3. `GET /tasks/{id}` でタスク取得 → 200 OK
4. `POST /tasks/{id}/complete` でタスク完了 → 200 OK
5. ステータスが `DONE` に変更されたことを確認

**検証ポイント**:
- 全エンドポイントの動作
- HTTPステータスコード
- リクエスト/レスポンスの変換
- DB永続化
- 外部API連携（WireMock）

### 2. データ永続化検証

**テスト名**: `POSTで作成したタスクがGETで取得できる`

**シナリオ**:
1. `POST /tasks` でタスク作成
2. `GET /tasks/{id}` で同じタスクを取得
3. タイトル、説明、ステータスが一致することを確認

**検証ポイント**:
- DB保存の正確性
- データの完全性
- タイムスタンプの自動設定

### 3. 状態遷移検証

**テスト名**: `タスク完了後の状態変更がDBに正しく反映される`

**シナリオ**:
1. タスク作成（ステータス: TODO）
2. タスク完了（ステータス: DONE）
3. GETで再取得して完了状態が永続化されていることを確認

**検証ポイント**:
- ステータス遷移の正確性
- 更新日時の変更
- DB更新の永続化

### 4. エラーハンドリング

**テスト名**: `存在しないタスクを取得すると404が返る`

**シナリオ**:
1. 存在しないID（9999）で `GET /tasks/9999`
2. 404 Not Found が返ることを確認

**検証ポイント**:
- 例外ハンドリング（`@ExceptionHandler`）
- 適切なHTTPステータスコード

### 5. ビジネスルール検証

**テスト名**: `完了済みタスクを完了しようとすると400が返る`

**シナリオ**:
1. タスク作成
2. タスク完了
3. 再度完了を試みる
4. 400 Bad Request が返ることを確認

**検証ポイント**:
- ドメインルールの適用
- 不正な操作の拒否
- エラーレスポンス

### 6. データ分離検証

**テスト名**: `複数のタスクを作成して個別に取得できる`

**シナリオ**:
1. 3つのタスクを作成
2. 各タスクを個別に取得
3. IDが異なることを確認
4. データが混在しないことを確認

**検証ポイント**:
- 複数レコードの独立性
- ID採番の正確性
- データの分離

## テスト実行フロー

### 起動シーケンス

```
1. テストクラス開始
   ↓
2. @BeforeAll: WireMockサーバー起動
   ↓
3. Testcontainers: Oracleコンテナ起動（30-60秒）
   ↓
4. Spring Boot起動（10-20秒）
   ↓
5. Flyway Migration実行（5-10秒）
   ↓
6. @BeforeEach: WireMockスタブ設定
   ↓
7. テストメソッド実行
   ↓
8. @AfterAll: WireMockサーバー停止
   ↓
9. Testcontainers: コンテナ停止・破棄
```

**合計**: 約50-90秒/テストクラス（改善前の構成）

## 実行方法

```bash
# ITのみ実行
./gradlew integrationTest

# 特定のテストクラスのみ
./gradlew integrationTest --tests TaskApiIntegrationTest

# 特定のテストメソッドのみ
./gradlew integrationTest --tests TaskApiIntegrationTest.タスク作成から完了までのE2Eフロー

# 詳細ログ付き
./gradlew integrationTest --info
```

## 実行結果（例）

```bash
$ ./gradlew integrationTest

> Task :integrationTest

TaskApiIntegrationTest
  ✓ タスク作成から完了までのE2Eフロー (523ms)
  ✓ 存在しないタスクを取得すると404が返る (145ms)
  ✓ 完了済みタスクを完了しようとすると400が返る (312ms)
  ✓ POSTで作成したタスクがGETで取得できる (234ms)
  ✓ タスク完了後の状態変更がDBに正しく反映される (289ms)
  ✓ 複数のタスクを作成して個別に取得できる (456ms)

TaskRepositoryIntegrationTest
  ✓ タスクを保存して取得できる (178ms)
  ✓ タスクを更新できる (156ms)
  ✓ 存在しないIDで検索すると空のOptionalが返る (89ms)

FlywayMigrationIntegrationTest
  ✓ Flywayマイグレーションが適用されている (98ms)
  ✓ tasksテーブルが存在する (76ms)
  ... (省略)

BUILD SUCCESSFUL in 3m 45s
15 tests completed, 15 passed
```

## ベストプラクティス

### テスト作成時のチェックリスト

- [ ] `@SpringBootTest(webEnvironment = RANDOM_PORT)` を使用
- [ ] `@ActiveProfiles("test")` でテストプロファイル適用
- [ ] `@Tag("integration")` でITタグ付け
- [ ] `extends TestcontainersConfig` でOracle起動
- [ ] HTTPステータスコードを検証
- [ ] レスポンスボディの主要フィールドを検証
- [ ] 外部APIはWireMockでモック化
- [ ] テストメソッド名は日本語で明確に

### コードレビューチェックリスト

- [ ] E2Eフローのテストが存在する
- [ ] エラーケースのテストが存在する
- [ ] HTTPステータスコードを検証している
- [ ] DB永続化を検証している
- [ ] 外部API呼び出しを検証している（WireMock）
- [ ] テストデータのクリーンアップが適切

## 改善前の課題

### 現在の問題点

**実行時間:**
- テストクラスごとにOracleコンテナ再起動
- 合計: 3-6分（テストクラス数 × 起動時間）

**リソース:**
- メモリ使用量が大きい
- CI/CDパイプラインの時間が長い

### 次のステップ（改善案）

1. **コンテナの共有化**
   - `@Container static` → Singletonパターン
   - `withReuse(true)` で再利用
   - 期待効果: 70-80%の時間削減

2. **Spring Contextの共有**
   - `@DirtiesContext` を最小化
   - 期待効果: さらに20-30%削減

3. **パラレル実行**
   - `./gradlew test --parallel`
   - 期待効果: CI時間の大幅短縮

詳細は [oracle-testcontainers-optimization.md](./oracle-testcontainers-optimization.md) を参照（今後作成予定）

## 参考資料

- [統合テスト作成ガイドライン](./integration-test-guidelines.md)
- [Oracle Testcontainersセットアップ](./oracle-testcontainers-setup.md)
- [統合テスト実行ガイド](./how-to-run-integration-tests.md)
- [単体テスト作成ガイドライン](./unit-test-guidelines.md)
