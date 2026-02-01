# 統合テスト実行ガイド

## 前提条件

- Docker がインストールされ、起動していること
- Java 21 以上
- Gradle 8.x

## テスト実行コマンド

### 1. すべての統合テストを実行

```bash
./gradlew integrationTest
```

**実行されるテスト:**
- `TaskRepositoryIntegrationTest` (Repositoryの動作確認)
- `FlywayMigrationIntegrationTest` (マイグレーション確認)
- `ContainerLifecycleTest` (コンテナライフサイクル確認)
- `TaskApiIntegrationTest` (API統合テスト)

**期待される動作（改善前の構成）:**

```
1. TaskRepositoryIntegrationTest 実行
   ├─ 🚀 Oracleコンテナ起動（30-60秒）
   ├─ 📦 Spring Context構築（10-20秒）
   ├─ 🔄 Flyway Migration実行（5-10秒）
   ├─ ✅ テスト実行（数秒）
   └─ 🛑 コンテナ停止・破棄

2. FlywayMigrationIntegrationTest 実行
   ├─ 🚀 Oracleコンテナ起動（30-60秒）← 再起動！
   ├─ 📦 Spring Context構築（10-20秒）
   ├─ 🔄 Flyway Migration実行（5-10秒）
   ├─ ✅ テスト実行（数秒）
   └─ 🛑 コンテナ停止・破棄

3. ContainerLifecycleTest 実行
   ├─ 🚀 Oracleコンテナ起動（30-60秒）← また再起動！
   └─ ... (以下同様)
```

**合計実行時間の目安:**
- テストクラス数: 4クラス
- 1クラスあたり: 50-90秒（起動 + マイグレーション + テスト）
- **合計: 3-6分**

### 2. 特定のテストクラスのみ実行

```bash
# Repositoryテストのみ
./gradlew integrationTest --tests TaskRepositoryIntegrationTest

# Flywayテストのみ
./gradlew integrationTest --tests FlywayMigrationIntegrationTest

# ライフサイクルテストのみ
./gradlew integrationTest --tests ContainerLifecycleTest
```

### 3. 複数のテストクラスを指定して実行

```bash
./gradlew integrationTest \
  --tests TaskRepositoryIntegrationTest \
  --tests FlywayMigrationIntegrationTest
```

### 4. 単体テストのみ実行（高速）

```bash
./gradlew unitTest
```

統合テストは実行されず、Testcontainersも起動しません。

### 5. すべてのテスト実行（UT + IT）

```bash
./gradlew test
```

または

```bash
./gradlew ci
```

## コンテナ再起動の確認方法

### 方法1: ログ出力で確認

テスト実行中のログに以下のような出力が表示されます:

```
================================================================================
🚀 [14:23:15.123] ContainerLifecycleTest: テストクラス開始
   Oracle Container Status: ✅ RUNNING
   JDBC URL: jdbc:oracle:thin:@localhost:xxxxx/testdb
================================================================================

... テスト実行 ...

================================================================================
🏁 [14:23:45.456] ContainerLifecycleTest: テストクラス終了
   このテストクラス終了後、コンテナは破棄されます（withReuse=false）
================================================================================
```

### 方法2: Docker コンテナの確認

別のターミナルで以下のコマンドを実行:

```bash
# テスト実行中にコンテナを監視
watch -n 1 'docker ps --filter "name=oracle" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"'
```

または

```bash
# 定期的に確認
while true; do
  clear
  date
  docker ps --filter "ancestor=gvenzl/oracle-xe:21-slim" --format "table {{.ID}}\t{{.Status}}\t{{.CreatedAt}}"
  sleep 2
done
```

**期待される動作:**
- テストクラス実行中: コンテナが1つ起動している
- テストクラス終了時: コンテナが停止・削除される
- 次のテストクラス開始時: 新しいコンテナIDで再起動される

### 方法3: 実行時間を計測

```bash
time ./gradlew integrationTest
```

**改善前の実行時間の目安:**
```
real    3m30s
user    0m15s
sys     0m5s
```

## トラブルシューティング

### エラー: "Could not find or load main class"

Gradle キャッシュをクリア:

```bash
./gradlew clean build
```

### エラー: "Container startup failed"

Docker が起動しているか確認:

```bash
docker info
```

Docker のメモリ設定を確認（最低4GB推奨）:

```bash
# Docker Desktop の場合
# Settings → Resources → Memory を確認
```

### エラー: "Port already in use"

既存のOracleコンテナを停止:

```bash
docker ps -a | grep oracle
docker stop $(docker ps -aq --filter "ancestor=gvenzl/oracle-xe:21-slim")
docker rm $(docker ps -aq --filter "ancestor=gvenzl/oracle-xe:21-slim")
```

### テストが遅すぎる

これは**意図的な設計**です（改善前の状態）。

最適化方法については `oracle-testcontainers-optimization.md` を参照してください。

## 次のステップ

現在の構成の問題点と改善方法:

1. **問題**: テストクラスごとにコンテナ再起動（3-6分）
2. **改善**: コンテナを全テストで共有（1分以内）
3. **効果**: 70-80%の時間削減

詳細は以下のドキュメントを参照:
- [oracle-testcontainers-optimization.md](./oracle-testcontainers-optimization.md)
- [oracle-testcontainers-setup.md](./oracle-testcontainers-setup.md)

## 補足: CI/CD での実行

### GitHub Actions の例

```yaml
name: Integration Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Run Integration Tests
        run: ./gradlew integrationTest

      - name: Upload Test Report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-results
          path: build/reports/tests/
```

**注意**: CI環境では改善前の構成だと非常に時間がかかるため、最適化を推奨します。
