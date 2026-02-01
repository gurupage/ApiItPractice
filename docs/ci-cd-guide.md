# CI/CD ガイド（GitHub Actions）

## 概要

GitHub ActionsでCI/CDパイプラインを構築し、自動テスト・カバレッジ計測・レポート生成を実行します。

## パイプライン構成

### ワークフローファイル

`.github/workflows/ci.yml`

### トリガー条件

- **Push**: `main`, `develop`ブランチへのプッシュ
- **Pull Request**: `main`ブランチへのPR作成・更新
- **Manual**: 手動実行（`workflow_dispatch`）

### 実行内容

```
1. 環境セットアップ
   ├─ JDK 21 インストール
   ├─ Gradle キャッシュ
   └─ Docker 確認（Testcontainers用）

2. UT実行（Unit Tests）
   ├─ Mockitoで依存をモック
   ├─ 実行時間計測
   └─ 期待時間: < 5秒

3. IT実行（Integration Tests）
   ├─ Testcontainers（Oracle XE）起動
   ├─ Flyway Migration
   ├─ WireMock（外部API）
   ├─ 実行時間計測
   └─ 期待時間: 3-6分（改善前）

4. JaCoCoレポート生成
   ├─ カバレッジ計測
   └─ HTML/XMLレポート

5. アーティファクトアップロード
   ├─ テスト結果（XML/HTML）
   └─ JaCoCoレポート（HTML/XML）

6. サマリー出力
   └─ 実行時間・カバレッジ表示
```

## Oracle Testcontainers対応

### CI環境での課題

**問題点:**
- Dockerコンテナ起動に時間がかかる
- メモリ不足でコンテナ起動失敗
- タイムアウトエラー

**解決策:**

#### 1. タイムアウト設定

```yaml
jobs:
  test:
    timeout-minutes: 30  # 全体のタイムアウト

    steps:
      - name: Run Integration Tests
        timeout-minutes: 20  # IT専用のタイムアウト
```

**設定理由:**
- Oracle XE起動: 30-60秒
- Flyway Migration: 5-10秒
- テスト実行: 数分
- 合計: 3-6分 → 余裕を持って20分

#### 2. Docker設定

```yaml
env:
  TESTCONTAINERS_RYUK_DISABLED: false  # リソースクリーンアップ
  DOCKER_BUILDKIT: 1  # ビルド高速化
```

**ubuntu-latestの利点:**
- Dockerプリインストール
- 十分なメモリ（7GB）
- 2コアCPU

#### 3. Docker状態確認

```yaml
- name: Verify Docker
  run: |
    docker --version
    docker info
    free -h
```

**確認項目:**
- Dockerバージョン
- メモリ使用状況
- ストレージ

#### 4. コンテナクリーンアップ

```yaml
- name: Check Docker Containers
  if: always()
  run: |
    docker ps
    docker ps -a
    docker images
```

**確認内容:**
- 起動中のコンテナ
- 停止したコンテナ
- イメージ

### メモリ管理

**ubuntu-latestのメモリ:**
- 合計: ~7GB
- Oracle XE: ~2.5GB
- Testcontainers: ~500MB
- Gradle/JVM: ~1GB
- 残り: ~3GB（十分）

**最適化:**
```yaml
env:
  GRADLE_OPTS: -Dorg.gradle.daemon=false -Dorg.gradle.parallel=true
```

- `daemon=false`: CI環境ではデーモン不要
- `parallel=true`: 並列実行で高速化

## 実行時間の計測

### 計測方法

**Gradleビルドスキャンを使わない理由:**
- 外部サービス依存
- アカウント作成が必要
- プライバシー懸念

**採用した方法: シェルスクリプト**

```bash
UT_START=$(date +%s)
./gradlew unitTest --no-daemon
UT_END=$(date +%s)
UT_DURATION=$((UT_END - UT_START))
```

**利点:**
- 外部依存なし
- シンプル
- 秒単位で正確

### 計測結果の表示

#### 1. ステップごとの時間（GitHub Actions標準）

GitHubが自動で各ステップの実行時間を表示:

```
✓ Run Unit Tests (5s)
✓ Run Integration Tests (4m 23s)
✓ Generate JaCoCo Report (12s)
```

#### 2. ログ内の詳細表示

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ Unit Tests Completed
⏱️  Duration: 5s (00:05)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

#### 3. サマリー表示

GitHub Actionsの`$GITHUB_STEP_SUMMARY`を使用:

```yaml
- name: Test Execution Summary
  run: |
    echo "## 📊 Test Execution Summary" >> $GITHUB_STEP_SUMMARY
    echo "| Test Type | Duration | Status |" >> $GITHUB_STEP_SUMMARY
    echo "| Unit Tests | 5s (00:05) | ✅ |" >> $GITHUB_STEP_SUMMARY
```

**表示例:**

| Test Type | Duration | Status |
|-----------|----------|--------|
| 🧪 Unit Tests | 5s (00:05) | ✅ |
| 🔬 Integration Tests | 263s (04:23) | ✅ |
| **📦 Total** | **268s** | **✅** |

### Performance Notes
- UT expected: < 5s
- IT expected: 3-6 minutes (includes Oracle container startup)
- **Improvement**: Container reuse can reduce IT time by 70-80%

## JaCoCoレポート

### レポート生成

```yaml
- name: Generate JaCoCo Coverage Report
  run: ./gradlew jacocoTestReport --no-daemon
```

**生成されるファイル:**
- `build/reports/jacoco/test/html/index.html` - HTMLレポート
- `build/reports/jacoco/test/jacocoTestReport.xml` - XMLレポート

### カバレッジ目標

| パッケージ | 目標 |
|-----------|------|
| 全体 | 70%以上 |
| core.domain | 80%以上 |
| usecase.service | 90%以上 |
| interfaces.rest | 70%以上 |

### PRコメント自動投稿

```yaml
- name: Add Coverage Comment to PR
  uses: madrapps/jacoco-report@v1.6.1
  if: github.event_name == 'pull_request'
  with:
    paths: build/reports/jacoco/test/jacocoTestReport.xml
    min-coverage-overall: 70
    min-coverage-changed-files: 70
```

**表示内容:**
- 全体カバレッジ
- 変更ファイルのカバレッジ
- カバレッジの増減
- 目標達成状況

## アーティファクト

### 1. テスト結果

```yaml
- name: Upload Test Results
  uses: actions/upload-artifact@v4
  with:
    name: test-results
    path: |
      build/test-results/**/*.xml
      build/reports/tests/**
    retention-days: 30
```

**含まれるファイル:**
- JUnit XMLレポート
- HTMLテストレポート

**保持期間:** 30日

### 2. JaCoCoカバレッジレポート

```yaml
- name: Upload JaCoCo Coverage Report
  uses: actions/upload-artifact@v4
  with:
    name: jacoco-coverage-report
    path: |
      build/reports/jacoco/test/html/
      build/reports/jacoco/test/jacocoTestReport.xml
    retention-days: 30
```

**含まれるファイル:**
- HTMLレポート（ブラウザで閲覧可能）
- XMLレポート（解析用）

### ダウンロード方法

1. GitHub ActionsのWorkflow実行ページを開く
2. 下にスクロールして「Artifacts」セクションを見つける
3. `test-results`または`jacoco-coverage-report`をクリック
4. ZIPファイルがダウンロードされる
5. 解凍して`index.html`を開く

## エラーハンドリング

### テスト失敗時

```yaml
- name: Print Test Failure Details
  if: failure()
  run: |
    find build/reports/tests/unitTest -name "*.html"
    find build/reports/tests/integrationTest -name "*.html"
```

**表示内容:**
- 失敗したテストのHTMLレポートパス
- エラーメッセージ
- スタックトレース

### タイムアウト時

**症状:**
```
Error: The operation was canceled.
```

**原因:**
- Oracle起動に時間がかかりすぎ
- メモリ不足
- ネットワーク遅延

**対策:**
```yaml
timeout-minutes: 30  # タイムアウトを延長
```

### Docker起動失敗時

**症状:**
```
Could not find a valid Docker environment
```

**対策:**
- ubuntu-latestを使用（Dockerプリインストール）
- `docker info`で状態確認

## ローカルでの実行

### 同じ環境で実行

```bash
# 環境変数を設定
export JAVA_VERSION=21
export GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.parallel=true"

# UT実行 + 時間計測
UT_START=$(date +%s)
./gradlew unitTest --no-daemon --console=plain
UT_END=$(date +%s)
echo "UT Duration: $((UT_END - UT_START))s"

# IT実行 + 時間計測
IT_START=$(date +%s)
./gradlew integrationTest --no-daemon --console=plain
IT_END=$(date +%s)
echo "IT Duration: $((IT_END - IT_START))s"

# JaCoCo
./gradlew jacocoTestReport
```

### レポート確認

```bash
# Linuxの場合
xdg-open build/reports/jacoco/test/html/index.html

# Macの場合
open build/reports/jacoco/test/html/index.html

# Windowsの場合
start build/reports/jacoco/test/html/index.html
```

## 最適化案

### 現在の構成（改善前）

**問題点:**
- テストクラスごとにOracleコンテナ再起動
- IT実行時間: 3-6分

**IT実行時間の内訳:**
```
Oracle起動:        30-60秒 × テストクラス数
Spring起動:        10-20秒 × テストクラス数
Flyway Migration:  5-10秒 × テストクラス数
テスト実行:        数秒
```

### 改善案（次のステップ）

#### 1. コンテナ再利用

```java
@Container
static OracleContainer oracleContainer =
    new OracleContainer("gvenzl/oracle-xe:21-slim")
        .withReuse(true);  // 再利用を有効化
```

**効果:** IT時間を70-80%削減（3-6分 → 1分以内）

#### 2. Spring Context共有

```java
@DirtiesContext を最小化
```

**効果:** さらに20-30%削減

#### 3. パラレル実行

```bash
./gradlew test --parallel --max-workers=4
```

**効果:** 複数テストクラスを並列実行

## トラブルシューティング

### 問題1: "Gradle cache not found"

**原因:** Gradleキャッシュが無効

**解決:**
```yaml
- uses: actions/setup-java@v4
  with:
    cache: 'gradle'  # キャッシュを有効化
```

### 問題2: "Permission denied: ./gradlew"

**原因:** 実行権限がない

**解決:**
```yaml
- name: Grant execute permission
  run: chmod +x gradlew
```

### 問題3: Oracle起動タイムアウト

**原因:** メモリ不足、ネットワーク遅延

**解決:**
```yaml
timeout-minutes: 20  # タイムアウトを延長
```

または

```java
oracleContainer.withStartupTimeout(Duration.ofMinutes(5));
```

### 問題4: "Out of memory"

**原因:** JVMメモリ不足

**解決:**
```yaml
env:
  GRADLE_OPTS: -Xmx2g -XX:MaxMetaspaceSize=512m
```

## バッジの追加

READMEにCIステータスバッジを追加:

```markdown
[![CI Pipeline](https://github.com/USERNAME/REPO/actions/workflows/ci.yml/badge.svg)](https://github.com/USERNAME/REPO/actions/workflows/ci.yml)
```

**表示例:**
![CI Pipeline](https://img.shields.io/badge/CI-passing-brightgreen)

## 参考資料

- [GitHub Actions公式ドキュメント](https://docs.github.com/en/actions)
- [actions/setup-java](https://github.com/actions/setup-java)
- [actions/upload-artifact](https://github.com/actions/upload-artifact)
- [madrapps/jacoco-report](https://github.com/madrapps/jacoco-report)
- [Testcontainers on CI](https://www.testcontainers.org/supported_docker_environment/continuous_integration/github_actions/)
