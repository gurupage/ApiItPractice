# 統合テスト（IT）作成ガイドライン

## 基本方針

### 1. Springコンテキスト起動

ITは実際のSpringアプリケーションコンテキストを起動して、本番に近い環境でテストします。

```java
// ✅ 統合テストの基本構成
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
class TaskApiIntegrationTest extends TestcontainersConfig {
    // ...
}
```

**実行時間の目安:**
- UT: 数百ms（Spring起動なし）
- IT: 数十秒〜数分（Spring起動 + Testcontainers）

### 2. テスト対象

- **E2Eフロー**: Controller → Service → Repository → Database
- **HTTP層**: リクエスト/レスポンスの変換
- **外部連携**: WireMockで外部APIをモック化
- **DB永続化**: Testcontainers（Oracle）+ Flyway

### 3. 実環境に近い構成

- Oracle Database（Testcontainers）
- Flyway Migration（自動適用）
- 外部API（WireMock）
- 実HTTPリクエスト（TestRestTemplate）

## HTTPテストツールの選定

### 選択肢の比較

| ツール | サーバー起動 | HTTPリクエスト | 学習コスト | 検証の柔軟性 | 推奨用途 |
|--------|------------|---------------|-----------|------------|---------|
| **MockMvc** | ❌ | モック | 低 | 高 | Controller層のみのテスト |
| **TestRestTemplate** | ✅ | 実HTTP | **低** | 中 | **E2E統合テスト（推奨）** |
| **RestAssured** | ✅ | 実HTTP | 中 | 高 | API仕様テスト、複雑な検証 |

### 推奨: TestRestTemplate

**選定理由:**

#### 1. 教材として理解しやすい

```java
// シンプルで直感的なAPI
ResponseEntity<TaskResponse> response = restTemplate.postForEntity(
    "/tasks",
    request,
    TaskResponse.class
);

assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
assertThat(response.getBody().getTitle()).isEqualTo("Test Task");
```

#### 2. Spring Boot標準

- 追加の依存関係不要
- Spring Bootの公式ドキュメントで推奨
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` と相性が良い

#### 3. 実HTTPリクエスト

- 実際にサーバーを起動してテスト
- 本番環境に近い動作を検証
- HTTPステータスコード、ヘッダー、ボディを検証可能

#### 4. シンプルな学習曲線

```java
// GET
ResponseEntity<TaskResponse> response = restTemplate.getForEntity("/tasks/1", TaskResponse.class);

// POST
ResponseEntity<TaskResponse> response = restTemplate.postForEntity("/tasks", request, TaskResponse.class);

// PUT
restTemplate.put("/tasks/1", updateRequest);

// DELETE
restTemplate.delete("/tasks/1");
```

### 他のツールとの比較

#### MockMvc（Controller層テスト向き）

**メリット:**
- 高速（サーバー起動不要）
- 詳細なHTTP検証（ヘッダー、ステータス、JSON構造）

**デメリット:**
- 実HTTPリクエストではない
- E2Eテストには不向き

**使用例:**
```java
mockMvc.perform(post("/tasks")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"title\":\"Test\"}"))
    .andExpect(status().isCreated())
    .andExpect(jsonPath("$.title").value("Test"));
```

**推奨用途**: Controller層の単体テスト（UTとITの中間）

#### RestAssured（複雑なAPI検証向き）

**メリット:**
- BDDスタイル（given/when/then）
- JSONPath、XPathで柔軟な検証
- DSLが読みやすい

**デメリット:**
- 別ライブラリ（追加依存）
- 学習コスト

**使用例:**
```java
given()
    .contentType(ContentType.JSON)
    .body(request)
.when()
    .post("/tasks")
.then()
    .statusCode(201)
    .body("title", equalTo("Test Task"));
```

**推奨用途**: APIコントラクトテスト、複雑なJSON検証

## テスト構成

### テンプレート

```java
package com.example.apipractice.integration.api;

import com.example.apipractice.integration.config.TestcontainersConfig;
import com.example.apipractice.interfaces.rest.dto.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

/**
 * [API名] の統合テスト（E2E）
 * IT: Spring起動 + Testcontainers（Oracle XE）+ WireMock（外部API）
 *
 * テスト方針:
 * - Controller → Service → Repository → DB の全体フロー検証
 * - 実HTTPリクエストでの動作確認
 * - [追加の方針]
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
class [クラス名]IntegrationTest extends TestcontainersConfig {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void [テストメソッド名]() {
        // given（前提条件）

        // when（実行）

        // then（検証）
    }
}
```

### アノテーション

| アノテーション | 用途 | 設定値 |
|--------------|------|--------|
| `@SpringBootTest` | Springコンテキスト起動 | `webEnvironment = RANDOM_PORT` |
| `@ActiveProfiles("test")` | テストプロファイル | application-test.yml使用 |
| `@Tag("integration")` | ITタグ付け | `./gradlew integrationTest`で実行 |
| `extends TestcontainersConfig` | Testcontainers設定継承 | Oracle起動、接続情報設定 |

## テスト名の付け方

### 基本ルール

**形式**: `[HTTPメソッド]_[エンドポイント]_[条件]_[期待結果]` または自然な日本語

```java
// ✅ 良い例（自然な日本語）
@Test
void タスク作成から完了までのE2Eフロー() { ... }

@Test
void POSTでタスクを作成してGETで取得できる() { ... }

@Test
void 存在しないタスクを取得すると404が返る() { ... }

// ✅ 良い例（英語風）
@Test
void POST_tasks_creates_task_and_returns_201() { ... }

// ❌ 悪い例
@Test
void test1() { ... }

@Test
void testTaskCreation() { ... }
```

### テストケースの分類

#### 1. E2Eフロー（正常系）

**目的**: ユーザーシナリオ全体の動作検証

```java
@Test
void タスク作成から完了までのE2Eフロー() {
    // 1. POST /tasks - タスク作成
    CreateTaskRequest request = new CreateTaskRequest();
    request.setTitle("E2E Test Task");
    request.setDescription("End-to-End test");

    ResponseEntity<TaskResponse> createResponse = restTemplate.postForEntity(
        "/tasks",
        request,
        TaskResponse.class
    );

    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(createResponse.getBody()).isNotNull();
    assertThat(createResponse.getBody().getTitle()).isEqualTo("E2E Test Task");

    Long taskId = createResponse.getBody().getId();

    // 2. GET /tasks/{id} - タスク取得
    ResponseEntity<TaskResponse> getResponse = restTemplate.getForEntity(
        "/tasks/" + taskId,
        TaskResponse.class
    );

    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(getResponse.getBody().getId()).isEqualTo(taskId);

    // 3. POST /tasks/{id}/complete - タスク完了
    ResponseEntity<TaskResponse> completeResponse = restTemplate.postForEntity(
        "/tasks/" + taskId + "/complete",
        null,
        TaskResponse.class
    );

    assertThat(completeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(completeResponse.getBody().getStatus().toString()).isEqualTo("DONE");
}
```

#### 2. HTTPステータスコード検証

**目的**: エラーハンドリングの検証

```java
@Test
void 存在しないタスクを取得すると404が返る() {
    // when
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/tasks/9999",
        String.class
    );

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
}

@Test
void 完了済みタスクを完了しようとすると400が返る() {
    // given: タスク作成と完了
    CreateTaskRequest request = new CreateTaskRequest();
    request.setTitle("Test Task");

    ResponseEntity<TaskResponse> createResponse = restTemplate.postForEntity(
        "/tasks",
        request,
        TaskResponse.class
    );

    Long taskId = createResponse.getBody().getId();
    restTemplate.postForEntity("/tasks/" + taskId + "/complete", null, TaskResponse.class);

    // when: 再度完了を試みる
    ResponseEntity<String> response = restTemplate.postForEntity(
        "/tasks/" + taskId + "/complete",
        null,
        String.class
    );

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
}
```

#### 3. データ永続化検証

**目的**: DBへの保存・更新が正しく反映されることを検証

```java
@Test
void POSTで作成したタスクがGETで取得できる() {
    // given
    CreateTaskRequest request = new CreateTaskRequest();
    request.setTitle("Persistence Test");
    request.setDescription("DB永続化の検証");

    // when: POST
    ResponseEntity<TaskResponse> postResponse = restTemplate.postForEntity(
        "/tasks",
        request,
        TaskResponse.class
    );

    Long taskId = postResponse.getBody().getId();

    // then: GET
    ResponseEntity<TaskResponse> getResponse = restTemplate.getForEntity(
        "/tasks/" + taskId,
        TaskResponse.class
    );

    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(getResponse.getBody().getTitle()).isEqualTo("Persistence Test");
    assertThat(getResponse.getBody().getDescription()).isEqualTo("DB永続化の検証");
    assertThat(getResponse.getBody().getStatus().toString()).isEqualTo("TODO");
}
```

## AAAパターン

統合テストでも **AAA（Arrange-Act-Assert）パターン** を適用します。

### 1. Given（Arrange）: 前提条件の準備

- テストデータの作成
- 事前状態のセットアップ
- WireMockのスタブ設定

### 2. When（Act）: 実行

- HTTPリクエストの実行
- 複数のリクエストをシーケンシャルに実行（E2Eフロー）

### 3. Then（Assert）: 検証

- HTTPステータスコードの検証
- レスポンスボディの検証
- DB状態の検証（必要に応じて）

## TestRestTemplateの使い方

### GETリクエスト

```java
// 単純なGET
ResponseEntity<TaskResponse> response = restTemplate.getForEntity(
    "/tasks/1",
    TaskResponse.class
);

// URLパラメータ付き
ResponseEntity<TaskResponse> response = restTemplate.getForEntity(
    "/tasks?status={status}",
    TaskResponse.class,
    "TODO"
);
```

### POSTリクエスト

```java
// リクエストボディ付き
CreateTaskRequest request = new CreateTaskRequest();
request.setTitle("Title");

ResponseEntity<TaskResponse> response = restTemplate.postForEntity(
    "/tasks",
    request,
    TaskResponse.class
);

// リクエストボディなし
ResponseEntity<TaskResponse> response = restTemplate.postForEntity(
    "/tasks/1/complete",
    null,
    TaskResponse.class
);
```

### カスタムヘッダー

```java
HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_JSON);
headers.set("Authorization", "Bearer token");

HttpEntity<CreateTaskRequest> entity = new HttpEntity<>(request, headers);

ResponseEntity<TaskResponse> response = restTemplate.exchange(
    "/tasks",
    HttpMethod.POST,
    entity,
    TaskResponse.class
);
```

## WireMockの使い方

### セットアップ

```java
private static WireMockServer wireMockServer;

@BeforeAll
static void startWireMock() {
    wireMockServer = new WireMockServer(8081);
    wireMockServer.start();
    WireMock.configureFor("localhost", 8081);
}

@AfterAll
static void stopWireMock() {
    wireMockServer.stop();
}

@BeforeEach
void setupWireMock() {
    wireMockServer.resetAll();

    // スタブ設定
    stubFor(post(urlEqualTo("/notifications"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"status\":\"success\"}")));
}
```

### 呼び出し検証

```java
// 1回呼ばれたことを検証
verify(1, postRequestedFor(urlEqualTo("/notifications")));

// 呼ばれなかったことを検証
verify(0, postRequestedFor(urlEqualTo("/notifications")));

// リクエストボディの検証
verify(postRequestedFor(urlEqualTo("/notifications"))
    .withRequestBody(containing("taskId")));
```

## ベストプラクティス

### ✅ DO（推奨）

- E2Eフローを中心にテスト
- HTTPステータスコードを必ず検証
- レスポンスボディの主要フィールドを検証
- 外部APIはWireMockでモック化
- `@Transactional`でテストデータをクリーンアップ（Repository層のみ）

### ❌ DON'T（非推奨）

- ITでビジネスロジックの細かい分岐を検証しない（UTの役割）
- DBの全フィールドを検証しない（必要な項目のみ）
- 複数のテストケースで状態を共有しない
- ハードコーディングされたポート番号（RANDOM_PORT推奨）

## テスト実行

```bash
# ITのみ実行
./gradlew integrationTest

# 特定のテストクラスのみ
./gradlew integrationTest --tests TaskApiIntegrationTest

# 全テスト（UT + IT）
./gradlew test
```

## トラブルシューティング

### Dockerが起動していない

```
Caused by: Could not find a valid Docker environment
```

→ Docker Desktopを起動してください

### ポート競合

```
Address already in use: bind
```

→ `RANDOM_PORT`を使用しているか確認、または既存のコンテナを停止

```bash
docker ps -a
docker stop $(docker ps -q)
```

### WireMock接続エラー

```
Connection refused: localhost:8081
```

→ `@BeforeAll`でWireMockが起動しているか確認

## 参考資料

- [Spring Boot Testing](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
- [TestRestTemplate](https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/test/web/client/TestRestTemplate.html)
- [WireMock](http://wiremock.org/docs/)
- [Testcontainers](https://www.testcontainers.org/)
