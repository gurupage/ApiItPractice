# 単体テスト（UT）作成ガイドライン

## 基本方針

### 1. Spring起動なし

UTはSpringコンテナを起動せず、Mockitoで依存をモック化して高速実行します。

```java
// ❌ NGパターン（Springを起動してしまう）
@SpringBootTest
class TaskServiceTest { ... }

// ✅ OKパターン（Mockitoのみ）
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class TaskServiceTest { ... }
```

**実行時間の目安:**
- UT: 数百ms（0.1-0.5秒）
- IT: 数十秒〜数分

### 2. テスト対象

- **Usecase層（Service）**: ビジネスロジックの中核
- **Interface層（Controller）**: リクエスト/レスポンス変換、バリデーション
- **Domain層（ドメインモデル）**: ドメインルール（必要に応じて）

### 3. モック対象

- **Port（インターフェース）**: Repository, Client など
- **外部依存**: データベース、外部API、ファイルシステム

## テスト構成

### テンプレート

```java
package com.example.apipractice.unit.usecase;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * [クラス名] の単体テスト
 * UT: Spring起動なし、Mockito使用
 *
 * テスト方針:
 * - [テスト方針を簡潔に記述]
 * - [対象となるビジネスルールを記述]
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class [クラス名]Test {

    @Mock
    private [依存クラス] [依存名];

    @InjectMocks
    private [テスト対象クラス] [テスト対象];

    @Test
    void [テストメソッド名]() {
        // given（前提条件）

        // when（実行）

        // then（検証）
    }
}
```

### アノテーション

| アノテーション | 用途 | 必須 |
|--------------|------|------|
| `@ExtendWith(MockitoExtension.class)` | Mockitoの初期化 | ✅ |
| `@Tag("unit")` | UTタグ付け（`./gradlew unitTest`で実行可能） | ✅ |
| `@Mock` | モックオブジェクトの生成 | ✅ |
| `@InjectMocks` | テスト対象にモックを注入 | ✅ |
| `@BeforeEach` | 各テスト前の初期化処理 | オプション |
| `@DisplayName` | テスト名の英語表記（オプション） | オプション |

## テスト名の付け方

### 基本ルール

**形式**: `[動作主体]_[条件]_[期待結果]` の日本語表記

```java
// ✅ 良い例
@Test
void タスク作成時にリポジトリ保存と通知が呼ばれる() { ... }

@Test
void 存在しないIDでタスク完了を試みると例外が発生する() { ... }

@Test
void 完了済みタスクを完了しようとすると例外が発生する() { ... }

// ❌ 悪い例（曖昧、何をテストしているか不明確）
@Test
void test1() { ... }

@Test
void taskTest() { ... }

@Test
void checkTask() { ... }
```

### テストケースの分類

#### 1. 正常系（Happy Path）

**目的**: 期待通りの動作を検証

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

#### 2. 異常系（Error Cases）

**目的**: エラー条件での例外発生を検証

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

    // リポジトリのsaveは呼ばれないことを確認
    verify(taskRepository, never()).save(any(Task.class));
}
```

#### 3. 仕様系（Business Rules）

**目的**: ビジネスルールの検証

```java
@Test
void 完了済みタスクを完了しようとすると例外が発生する() {
    // given
    Task task = Task.create("Test Task", "Description");
    task.setId(1L);
    task.complete();  // 既に完了状態にする
    when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

    // when & then
    assertThatThrownBy(() -> taskService.completeTask(1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already completed");
}
```

## AAAパターン

すべてのテストで **AAA（Arrange-Act-Assert）パターン** を適用します。

### 1. Given（Arrange）: 前提条件の準備

- モックの振る舞いを定義
- テストデータを準備
- 初期状態を設定

```java
// given
Task task = Task.create("Test Task", "Description");
task.setId(1L);
when(taskRepository.save(any(Task.class))).thenReturn(task);
```

### 2. When（Act）: 実行

- テスト対象のメソッドを実行
- 1つのテストで1つのアクションのみ

```java
// when
Task result = taskService.createTask("Test Task", "Description");
```

### 3. Then（Assert）: 検証

- 戻り値の検証
- モックの呼び出し回数の検証
- 例外の検証

```java
// then
assertThat(result.getId()).isEqualTo(1L);
assertThat(result.getTitle()).isEqualTo("Test Task");
verify(taskRepository, times(1)).save(any(Task.class));
verify(notificationClient, times(1)).notifyTaskCreated(1L, "Test Task");
```

### when & then（例外テストの場合）

例外発生のテストでは、when と then をまとめて記述します。

```java
// when & then
assertThatThrownBy(() -> taskService.getTask(999L))
        .isInstanceOf(TaskService.TaskNotFoundException.class)
        .hasMessageContaining("Task not found: id=999");
```

## Mockitoの使い方

### モックの振る舞い定義

```java
// 単純な戻り値
when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

// 引数によらず常に同じ値を返す
when(taskRepository.save(any(Task.class))).thenReturn(savedTask);

// 渡された引数をそのまま返す
when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

// 例外をスロー
when(taskRepository.findById(999L)).thenThrow(new RuntimeException("DB Error"));
```

### モック呼び出しの検証

```java
// 1回だけ呼ばれたことを検証
verify(taskRepository, times(1)).save(any(Task.class));

// 呼ばれなかったことを検証
verify(taskRepository, never()).save(any(Task.class));

// 引数の検証
verify(notificationClient).notifyTaskCreated(eq(1L), eq("Test Task"));

// 任意の引数（引数の値を問わない）
verify(taskRepository).save(any(Task.class));
```

## AssertJの使い方

### 基本的なアサーション

```java
// 等価性
assertThat(result.getId()).isEqualTo(1L);

// null チェック
assertThat(result).isNotNull();
assertThat(result.getDescription()).isNull();

// 文字列の部分一致
assertThat(result.getTitle()).contains("Task");
assertThat(result.getTitle()).startsWith("Test");

// 真偽値
assertThat(task.canComplete()).isTrue();
assertThat(task.canComplete()).isFalse();
```

### 例外のアサーション

```java
// 例外型と メッセージの検証
assertThatThrownBy(() -> taskService.getTask(999L))
        .isInstanceOf(TaskService.TaskNotFoundException.class)
        .hasMessageContaining("Task not found");

// 例外が発生しないことを検証
assertThatCode(() -> taskService.createTask("Title", "Desc"))
        .doesNotThrowAnyException();
```

### コレクションのアサーション

```java
// サイズ
assertThat(tasks).hasSize(3);
assertThat(tasks).isEmpty();

// 要素の検証
assertThat(tasks).contains(task1, task2);
assertThat(tasks).containsExactly(task1, task2);
assertThat(tasks).containsExactlyInAnyOrder(task2, task1);

// 条件による検証
assertThat(tasks)
        .allMatch(task -> task.getStatus() == TaskStatus.TODO)
        .anyMatch(task -> task.getTitle().contains("Important"));
```

## テストケースの網羅性

### 最小限のテストセット

各メソッドに対して最低限以下をカバーします：

1. **正常系**: 期待通りの動作（1本）
2. **異常系**: エラー条件（1-2本）
3. **境界値**: 境界条件（必要に応じて）
4. **ビジネスルール**: ドメインルールの検証（必要に応じて）

### 例: `createTask()` メソッド

```java
// 1. 正常系
@Test
void タスク作成時にタイトルと説明が正しく保存される() { ... }

// 2. 異常系（例: タイトルがnullの場合）
@Test
void タイトルがnullの場合に例外が発生する() { ... }

// 3. ビジネスルール
@Test
void タスク作成後に通知が送信される() { ... }
```

## ベストプラクティス

### ✅ DO（推奨）

- テストメソッド名は日本語で明確に
- AAAパターンを必ず使用
- 1テスト1検証（Single Responsibility）
- モックの呼び出し回数を検証
- 例外メッセージも検証

### ❌ DON'T（非推奨）

- 複数のアクションを1つのテストに詰め込まない
- テスト間で状態を共有しない
- モックの設定漏れ（`when()`なし）
- アサーション漏れ（検証なし）
- 魔法の数値（マジックナンバー）の使用

## 実行方法

```bash
# UTのみ実行
./gradlew unitTest

# 特定のテストクラスのみ
./gradlew unitTest --tests TaskServiceTest

# 特定のテストメソッドのみ
./gradlew unitTest --tests TaskServiceTest.タスク作成時にリポジトリ保存と通知が呼ばれる

# カバレッジレポート生成
./gradlew test jacocoTestReport
```

## 参考

- [Mockito公式ドキュメント](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [AssertJ公式ドキュメント](https://assertj.github.io/doc/)
- [JUnit 5 公式ドキュメント](https://junit.org/junit5/docs/current/user-guide/)
