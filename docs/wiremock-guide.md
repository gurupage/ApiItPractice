# WireMock使用ガイド

## WireMockとは

WireMockは、外部HTTP APIをモック化するためのライブラリです。統合テスト（IT）で外部APIへの依存を切り離し、安定したテストを実現します。

## 使用目的

### なぜWireMockが必要か

**問題点（WireMockなし）:**
- 外部APIに実際にアクセス → 遅い、不安定、課金の可能性
- テストデータが外部システムに影響
- 外部APIの障害でテストが失敗
- エラーケースのテストが困難

**解決策（WireMock）:**
- ✅ 外部APIをローカルでモック化
- ✅ 高速・安定
- ✅ エラーケースも自由にテスト可能
- ✅ 本番環境に影響しない

## 本プロジェクトでの使用例

### 2つの外部API依存

| API | Port | 用途 | WireMockで置き換え |
|-----|------|------|------------------|
| **通知API** | 8081 | タスク作成通知 | ✅ 常に成功レスポンス |
| **ユーザー検証API** | 8082 | ユーザー存在確認 | ✅ 200 OK or 404 Not Found |

### アーキテクチャ

```
[IT Test]
    ↓
[TaskService] ← ビジネスロジック
    ↓ ↓
    |  └→ [UserValidationClient (Port)]
    |         ↓
    |      [UserValidationClientAdapter] ← Infrastructure
    |         ↓
    |      HTTP GET /api/users/{userId}
    |         ↓
    |      [WireMock Server (port 8082)] ← テスト時のみ
    |
    └→ [NotificationClient (Port)]
           ↓
        [NotificationClientAdapter] ← Infrastructure
           ↓
        HTTP POST /notifications
           ↓
        [WireMock Server (port 8081)] ← テスト時のみ
```

## WireMock起動方法の選択

### 選択肢

| 方法 | メリット | デメリット | 推奨 |
|------|---------|-----------|------|
| **テスト内起動** | シンプル、高速 | Javaコード内で管理 | ✅ **推奨（教材向き）** |
| Testcontainers | 本番に近い | 起動遅い、Docker必要 | 大規模プロジェクト向け |
| 外部サーバー | 複数プロジェクト共有可能 | 管理が複雑 | 特殊ケースのみ |

### 推奨: テスト内起動

**選定理由:**

1. **最小構成**: 追加の依存がわずか
2. **高速**: Dockerコンテナ起動不要（数ms）
3. **理解しやすい**: Javaコード内で完結
4. **ポート管理が簡単**: 固定ポートで明確

## 実装方法

### 1. 依存関係の追加

`build.gradle`:

```gradle
dependencies {
    // WireMock（統合テスト用）
    testImplementation 'com.github.tomakehurst:wiremock-jre8-standalone:2.35.1'
}
```

**ポイント:**
- `wiremock-jre8-standalone`: スタンドアロン版（依存が少ない）
- テスト依存のみ（`testImplementation`）

### 2. Portインターフェースの定義

`src/main/java/usecase/port/UserValidationClient.java`:

```java
public interface UserValidationClient {
    /**
     * ユーザーの存在を確認
     * @param userId ユーザーID
     * @return ユーザーが存在する場合true
     */
    boolean existsUser(String userId);
}
```

**ポイント:**
- Usecase層に配置（ビジネスロジックの依存）
- 実装詳細（HTTP）を隠蔽

### 3. HTTPクライアント実装

`src/main/java/infrastructure/client/UserValidationClientAdapter.java`:

```java
@Component
public class UserValidationClientAdapter implements UserValidationClient {

    private final RestTemplate restTemplate;
    private final String userApiUrl;

    public UserValidationClientAdapter(
            RestTemplate restTemplate,
            @Value("${user.validation.api.url}") String userApiUrl) {
        this.restTemplate = restTemplate;
        this.userApiUrl = userApiUrl;
    }

    @Override
    public boolean existsUser(String userId) {
        try {
            String url = userApiUrl + "/" + userId;
            ResponseEntity<UserResponse> response =
                restTemplate.getForEntity(url, UserResponse.class);
            return response.getStatusCode() == HttpStatus.OK;

        } catch (HttpClientErrorException.NotFound e) {
            // 404: ユーザー不在
            return false;

        } catch (Exception e) {
            throw new UserValidationException("Failed to validate user", e);
        }
    }
}
```

**ポイント:**
- Infrastructure層に配置（技術詳細）
- `@Value`でURL外部化（テスト時に上書き可能）
- エラーハンドリング（404、その他）

### 4. ITでWireMockをセットアップ

`src/test/java/integration/api/TaskApiIntegrationTest.java`:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
class TaskApiIntegrationTest extends TestcontainersConfig {

    private static WireMockServer userValidationWireMock;
    private static WireMockServer notificationWireMock;

    @BeforeAll
    static void startWireMock() {
        // ユーザー検証API用WireMock（port 8082）
        userValidationWireMock = new WireMockServer(8082);
        userValidationWireMock.start();

        // 通知API用WireMock（port 8081）
        notificationWireMock = new WireMockServer(8081);
        notificationWireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        userValidationWireMock.stop();
        notificationWireMock.stop();
    }

    @BeforeEach
    void setupWireMock() {
        userValidationWireMock.resetAll();
        notificationWireMock.resetAll();

        // デフォルトのスタブ設定
        WireMock.configureFor("localhost", 8082);
        stubFor(get(urlMatching("/api/users/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"userId\":\"test-user\",\"active\":true}")));

        WireMock.configureFor("localhost", 8081);
        stubFor(post(urlEqualTo("/notifications"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\"status\":\"success\"}")));
    }

    @DynamicPropertySource
    static void setWireMockUrl(DynamicPropertyRegistry registry) {
        registry.add("user.validation.api.url",
            () -> "http://localhost:8082/api/users");
        registry.add("notification.api.url",
            () -> "http://localhost:8081/notifications");
    }
}
```

**ポイント:**
- `@BeforeAll`: テストクラス開始時に起動（1回）
- `@AfterAll`: テストクラス終了時に停止
- `@BeforeEach`: 各テスト前にスタブをリセット
- `@DynamicPropertySource`: Spring設定を上書き

### 5. スタブの設定

#### 基本パターン: 常に成功

```java
stubFor(get(urlEqualTo("/api/users/user123"))
        .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"userId\":\"user123\",\"active\":true}")));
```

#### パターンマッチング

```java
// 正規表現でURLマッチ
stubFor(get(urlMatching("/api/users/.*"))
        .willReturn(aResponse().withStatus(200)));

// パスパラメータを含む
stubFor(get(urlPathEqualTo("/api/users"))
        .withQueryParam("id", equalTo("123"))
        .willReturn(aResponse().withStatus(200)));
```

#### エラーケース

```java
// 404 Not Found
stubFor(get(urlEqualTo("/api/users/unknown"))
        .willReturn(aResponse().withStatus(404)));

// 500 Internal Server Error
stubFor(get(urlEqualTo("/api/users/error"))
        .willReturn(aResponse().withStatus(500)));

// タイムアウト
stubFor(get(urlEqualTo("/api/users/slow"))
        .willReturn(aResponse()
                .withFixedDelay(5000))); // 5秒遅延
```

#### リクエストボディの検証

```java
stubFor(post(urlEqualTo("/notifications"))
        .withRequestBody(containing("taskId"))
        .withRequestBody(matchingJsonPath("$.title"))
        .willReturn(aResponse().withStatus(200)));
```

### 6. 呼び出しの検証

```java
@Test
void タスク作成時にユーザー検証APIが呼ばれる() {
    // given
    CreateTaskRequest request = new CreateTaskRequest();
    request.setUserId("user123");
    request.setTitle("Test Task");

    // when
    restTemplate.postForEntity("/tasks", request, TaskResponse.class);

    // then: ユーザー検証APIが1回呼ばれた
    WireMock.configureFor("localhost", 8082);
    verify(1, getRequestedFor(urlEqualTo("/api/users/user123")));

    // then: 通知APIも1回呼ばれた
    WireMock.configureFor("localhost", 8081);
    verify(1, postRequestedFor(urlEqualTo("/notifications")));
}
```

**検証パターン:**

```java
// 呼ばれなかったことを確認
verify(0, getRequestedFor(urlEqualTo("/api/users/test")));

// 呼び出し回数の検証
verify(exactly(2), getRequestedFor(urlMatching("/api/.*")));

// リクエストボディの検証
verify(postRequestedFor(urlEqualTo("/notifications"))
        .withRequestBody(containing("taskId")));

// ヘッダーの検証
verify(postRequestedFor(urlEqualTo("/notifications"))
        .withHeader("Content-Type", equalTo("application/json")));
```

## UTではMockitoでモック

ITでは実HTTPリクエストをテストしますが、UTではPortをMockitoでモック化します。

```java
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class TaskServiceTest {

    @Mock
    private UserValidationClient userValidationClient;

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskService taskService;

    @Test
    void ユーザー存在時にタスクが作成される() {
        // given
        when(userValidationClient.existsUser("user123")).thenReturn(true);
        when(taskRepository.save(any(Task.class))).thenReturn(task);

        // when
        Task result = taskService.createTask("user123", "Title", "Desc");

        // then
        verify(userValidationClient).existsUser("user123");
        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void ユーザー不在時に例外が発生する() {
        // given
        when(userValidationClient.existsUser("unknown")).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> taskService.createTask("unknown", "Title", "Desc"))
                .isInstanceOf(UserNotFoundException.class);

        // ユーザー検証で失敗したので、保存は呼ばれない
        verify(taskRepository, never()).save(any(Task.class));
    }
}
```

## テストシナリオ例

### シナリオ1: ユーザー存在確認成功

```java
@Test
void 正常なユーザーIDでタスク作成できる() {
    // WireMockのデフォルト設定（200 OK）を使用
    CreateTaskRequest request = new CreateTaskRequest();
    request.setUserId("user123");
    request.setTitle("Task");

    ResponseEntity<TaskResponse> response =
        restTemplate.postForEntity("/tasks", request, TaskResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    verify(1, getRequestedFor(urlEqualTo("/api/users/user123")));
}
```

### シナリオ2: ユーザー不在

```java
@Test
void 存在しないユーザーでタスク作成すると400が返る() {
    // given: 特定のユーザーIDで404を返す
    WireMock.configureFor("localhost", 8082);
    stubFor(get(urlEqualTo("/api/users/unknown"))
            .willReturn(aResponse().withStatus(404)));

    CreateTaskRequest request = new CreateTaskRequest();
    request.setUserId("unknown");
    request.setTitle("Task");

    // when
    ResponseEntity<String> response =
        restTemplate.postForEntity("/tasks", request, String.class);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    verify(1, getRequestedFor(urlEqualTo("/api/users/unknown")));
}
```

### シナリオ3: 外部API障害

```java
@Test
void ユーザー検証API障害時にエラーハンドリングされる() {
    // given: 500エラーを返す
    WireMock.configureFor("localhost", 8082);
    stubFor(get(urlEqualTo("/api/users/error"))
            .willReturn(aResponse().withStatus(500)));

    CreateTaskRequest request = new CreateTaskRequest();
    request.setUserId("error");
    request.setTitle("Task");

    // when
    ResponseEntity<String> response =
        restTemplate.postForEntity("/tasks", request, String.class);

    // then: アプリケーションのエラーハンドリングによる
    assertThat(response.getStatusCode().is5xxServerError()).isTrue();
}
```

## ベストプラクティス

### ✅ DO（推奨）

1. **各テスト前にリセット**: `@BeforeEach`で`resetAll()`
2. **明確なポート番号**: 8081, 8082など固定
3. **デフォルトのスタブ**: よくあるケースを`@BeforeEach`で設定
4. **特殊ケースは個別設定**: テストメソッド内で`stubFor()`
5. **呼び出し検証**: `verify()`で外部API呼び出しを確認
6. **複数WireMock**: `configureFor()`でポート切り替え

### ❌ DON'T（非推奨）

1. **実際の外部APIにアクセスしない**: WireMockで置き換える
2. **動的ポート避ける**: テストごとにポート変わると混乱
3. **グローバルな状態共有しない**: 各テストで独立
4. **過度な検証しない**: 必要な検証のみ

## トラブルシューティング

### ポート競合エラー

```
Address already in use: bind
```

**解決策:**
```bash
# ポート8082を使用しているプロセスを確認
netstat -ano | findstr :8082  # Windows
lsof -i :8082                  # Mac/Linux

# プロセスを停止
taskkill /PID <PID> /F         # Windows
kill -9 <PID>                  # Mac/Linux
```

### WireMockが応答しない

```
Connection refused: localhost:8082
```

**チェックポイント:**
1. `@BeforeAll`でWireMockが起動しているか
2. ポート番号が正しいか（8082）
3. `configureFor()`でポート指定しているか

### スタブが機能しない

**チェックポイント:**
1. URLパスが完全一致しているか
2. `configureFor()`で正しいポートを指定しているか
3. `resetAll()`が呼ばれていないか確認

## 参考資料

- [WireMock公式ドキュメント](http://wiremock.org/docs/)
- [統合テスト作成ガイドライン](./integration-test-guidelines.md)
- [統合テストサマリー](./integration-test-summary.md)
