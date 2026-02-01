# 単体テスト（UT）サマリー

## テストクラス一覧

### 1. TaskServiceTest

**パス**: `src/test/java/com/example/apipractice/unit/usecase/TaskServiceTest.java`

**テスト対象**: `TaskService`（Usecase層）

**テストケース一覧**:

| # | テストメソッド名 | 分類 | 検証内容 |
|---|----------------|------|----------|
| 1 | タスク作成時にリポジトリ保存と通知が呼ばれる | 正常系 | `createTask()` の基本動作 |
| 2 | タスク取得時に存在しない場合は例外が発生する | 異常系 | 存在しないIDで `getTask()` |
| 3 | タスク完了時にステータスがDONEに変更される | 正常系 | `completeTask()` の基本動作 |
| 4 | 完了済みタスクを完了しようとすると例外が発生する | 仕様系 | 再完了の禁止ルール |
| 5 | 存在しないIDでタスク完了を試みると例外が発生する | 異常系 | 存在しないIDで `completeTask()` |
| 6 | タスク作成時にタイトルと説明が正しく保存される | 正常系 | データの正確性検証 |
| 7 | タスク取得時にIDが一致するタスクが返される | 正常系 | `getTask()` の戻り値検証 |
| 8 | タスク完了時にリポジトリが呼ばれる | 正常系 | `completeTask()` のモック呼び出し検証 |

**カバレッジ**:
- メソッドカバレッジ: 100% (3/3メソッド)
- 行カバレッジ: 90%以上

### 2. TaskControllerTest

**パス**: `src/test/java/com/example/apipractice/unit/interfaces/TaskControllerTest.java`

**テスト対象**: `TaskController`（Interface層）

**テストケース概要**:
- REST APIのリクエスト/レスポンス変換
- バリデーション
- HTTPステータスコード

## テストケース分類

### 正常系（Happy Path）

期待通りの動作を検証するテスト。

**目的**:
- 基本的な機能が動作することを確認
- 戻り値が正しいことを確認
- 依存コンポーネントが適切に呼ばれることを確認

**例**:
```java
@Test
void タスク作成時にタイトルと説明が正しく保存される() {
    // given
    String expectedTitle = "新規タスク";
    String expectedDescription = "タスクの詳細説明";
    Task savedTask = Task.create(expectedTitle, expectedDescription);
    savedTask.setId(10L);

    when(taskRepository.save(any(Task.class))).thenReturn(savedTask);

    // when
    Task result = taskService.createTask(expectedTitle, expectedDescription);

    // then
    assertThat(result.getTitle()).isEqualTo(expectedTitle);
    assertThat(result.getDescription()).isEqualTo(expectedDescription);
    assertThat(result.getStatus()).isEqualTo(TaskStatus.TODO);
}
```

### 異常系（Error Cases）

エラー条件での例外発生を検証するテスト。

**目的**:
- 不正な入力に対して適切なエラーハンドリングを確認
- 例外の型とメッセージを確認
- エラー時に副作用が発生しないことを確認

**例**:
```java
@Test
void 存在しないIDでタスク完了を試みると例外が発生する() {
    // given
    Long nonExistentId = 999L;
    when(taskRepository.findById(nonExistentId)).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> taskService.completeTask(nonExistentId))
            .isInstanceOf(TaskService.TaskNotFoundException.class)
            .hasMessageContaining("Task not found: id=999");

    // 副作用がないことを確認
    verify(taskRepository, never()).save(any(Task.class));
}
```

### 仕様系（Business Rules）

ビジネスルールの検証を行うテスト。

**目的**:
- ドメインモデルのルールが守られることを確認
- 状態遷移が正しいことを確認
- 制約条件が機能することを確認

**例**:
```java
@Test
void 完了済みタスクを完了しようとすると例外が発生する() {
    // given
    Task task = Task.create("Test Task", "Description");
    task.setId(1L);
    task.complete();  // 既に完了状態
    when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

    // when & then
    assertThatThrownBy(() -> taskService.completeTask(1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already completed");
}
```

## テスト実行結果（例）

```bash
$ ./gradlew unitTest

> Task :unitTest

TaskServiceTest
  ✓ タスク作成時にリポジトリ保存と通知が呼ばれる (12ms)
  ✓ タスク取得時に存在しない場合は例外が発生する (5ms)
  ✓ タスク完了時にステータスがDONEに変更される (8ms)
  ✓ 完了済みタスクを完了しようとすると例外が発生する (6ms)
  ✓ 存在しないIDでタスク完了を試みると例外が発生する (7ms)
  ✓ タスク作成時にタイトルと説明が正しく保存される (9ms)
  ✓ タスク取得時にIDが一致するタスクが返される (6ms)
  ✓ タスク完了時にリポジトリが呼ばれる (7ms)

TaskControllerTest
  ✓ POST /tasks でタスクが作成される (15ms)
  ✓ GET /tasks/{id} でタスクが取得される (10ms)
  ✓ POST /tasks/{id}/complete でタスクが完了する (12ms)

BUILD SUCCESSFUL in 2s
11 tests completed, 11 passed
```

**実行時間**: 約2秒（Spring起動なし）

## カバレッジレポート

### 生成方法

```bash
# テスト実行 + カバレッジレポート生成
./gradlew test jacocoTestReport

# レポート確認（ブラウザで開く）
open build/reports/jacoco/test/html/index.html
```

### カバレッジ目標

| パッケージ | 目標 | 除外対象 |
|-----------|------|----------|
| `core.domain` | 80%以上 | なし |
| `usecase.service` | 90%以上 | なし |
| `interfaces.rest` | 70%以上 | DTOクラス |
| `infrastructure` | 70%以上 | Entityクラス |
| **全体** | **70%以上** | Config, Application.class |

### カバレッジレポートの見方

```
Element         Coverage    Missed    Total
---------------------------------------------
Instructions    85.2%       148       1000
Branches        78.9%       19        90
Lines           87.5%       50        400
Methods         92.3%       5         65
Classes         100%        0         12
```

**重要な指標**:
- **Lines**: 行カバレッジ（最重要）
- **Branches**: 分岐カバレッジ（if/else, switch など）
- **Methods**: メソッドカバレッジ

## ベストプラクティス

### テスト作成時のチェックリスト

- [ ] `@ExtendWith(MockitoExtension.class)` と `@Tag("unit")` を付与
- [ ] テストメソッド名は日本語で明確に記述
- [ ] AAAパターン（given/when/then）を使用
- [ ] モックの振る舞いを `when()` で定義
- [ ] 戻り値を `assertThat()` で検証
- [ ] モックの呼び出し回数を `verify()` で検証
- [ ] 例外テストでは例外の型とメッセージを検証

### コードレビューチェックリスト

- [ ] 各メソッドに対して正常系・異常系のテストが存在する
- [ ] テスト名から何をテストしているか明確にわかる
- [ ] 1つのテストで1つのことだけをテストしている
- [ ] マジックナンバーを使わず、意味のある変数名を使用
- [ ] モックの設定漏れがない
- [ ] 不要なモック設定がない

## 次のステップ

### 1. カバレッジ向上

カバレッジレポートを確認して、未カバーの分岐を特定し、テストを追加します。

```bash
./gradlew test jacocoTestReport
open build/reports/jacoco/test/html/index.html
```

### 2. パラメータ化テスト

複数の入力パターンをテストする場合は、`@ParameterizedTest` を使用します。

```java
@ParameterizedTest
@CsvSource({
    "1, TODO",
    "2, IN_PROGRESS",
    "3, DONE"
})
void タスクステータスが正しく設定される(Long id, TaskStatus status) {
    // ...
}
```

### 3. テストの高速化

- 不要なモック設定を削減
- 共通のセットアップを `@BeforeEach` にまとめる
- テストデータのファクトリメソッドを作成

## 参考資料

- [単体テスト作成ガイドライン](./unit-test-guidelines.md)
- [統合テスト実行ガイド](./how-to-run-integration-tests.md)
- [Oracle Testcontainersセットアップ](./oracle-testcontainers-setup.md)
