#!/bin/bash

# テスト実行時間計測スクリプト
# Usage: ./scripts/measure-test-time.sh [ut|it|all]

set -e

# 色の定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# ヘッダー表示
print_header() {
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo -e "${BLUE}$1${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
}

# 時間をフォーマット（秒 → mm:ss）
format_time() {
    local seconds=$1
    printf "%02d:%02d" $((seconds/60)) $((seconds%60))
}

# UT実行
run_unit_tests() {
    print_header "🧪 Running Unit Tests (UT)"

    UT_START=$(date +%s)
    ./gradlew unitTest --no-daemon --console=plain
    UT_EXIT_CODE=$?
    UT_END=$(date +%s)
    UT_DURATION=$((UT_END - UT_START))

    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    if [ $UT_EXIT_CODE -eq 0 ]; then
        echo -e "${GREEN}✅ Unit Tests Completed${NC}"
    else
        echo -e "${RED}❌ Unit Tests Failed${NC}"
    fi
    echo -e "⏱️  Duration: ${UT_DURATION}s ($(format_time $UT_DURATION))"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""

    return $UT_EXIT_CODE
}

# IT実行
run_integration_tests() {
    print_header "🔬 Running Integration Tests (IT)"
    echo "📦 Using Testcontainers + Oracle XE"
    echo "⚠️  This may take 3-6 minutes (includes Oracle startup)"
    echo ""

    IT_START=$(date +%s)
    ./gradlew integrationTest --no-daemon --console=plain
    IT_EXIT_CODE=$?
    IT_END=$(date +%s)
    IT_DURATION=$((IT_END - IT_START))

    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    if [ $IT_EXIT_CODE -eq 0 ]; then
        echo -e "${GREEN}✅ Integration Tests Completed${NC}"
    else
        echo -e "${RED}❌ Integration Tests Failed${NC}"
    fi
    echo -e "⏱️  Duration: ${IT_DURATION}s ($(format_time $IT_DURATION))"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""

    return $IT_EXIT_CODE
}

# サマリー表示
print_summary() {
    echo ""
    echo "╔════════════════════════════════════════════════════╗"
    echo "║          📊 Test Execution Summary                 ║"
    echo "╠════════════════════════════════════════════════════╣"
    printf "║ 🧪 Unit Tests         │ %5ds (%s)      ║\n" ${UT_DURATION:-0} "$(format_time ${UT_DURATION:-0})"
    printf "║ 🔬 Integration Tests  │ %5ds (%s)      ║\n" ${IT_DURATION:-0} "$(format_time ${IT_DURATION:-0})"
    echo "╠════════════════════════════════════════════════════╣"
    printf "║ 📦 Total              │ %5ds (%s)      ║\n" $((${UT_DURATION:-0} + ${IT_DURATION:-0})) "$(format_time $((${UT_DURATION:-0} + ${IT_DURATION:-0})))"
    echo "╚════════════════════════════════════════════════════╝"
    echo ""

    echo "🎯 Performance Notes:"
    echo "  - UT expected: < 5s"
    echo "  - IT expected: 3-6 minutes (includes Oracle container startup)"
    echo "  - Improvement: Container reuse can reduce IT time by 70-80%"
    echo ""
}

# メイン処理
main() {
    local test_type="${1:-all}"

    echo ""
    echo "╔════════════════════════════════════════════════════╗"
    echo "║       🚀 Test Execution Time Measurement           ║"
    echo "╚════════════════════════════════════════════════════╝"

    case "$test_type" in
        ut|unit)
            run_unit_tests
            UT_RESULT=$?
            print_summary
            exit $UT_RESULT
            ;;
        it|integration)
            run_integration_tests
            IT_RESULT=$?
            print_summary
            exit $IT_RESULT
            ;;
        all)
            run_unit_tests
            UT_RESULT=$?

            run_integration_tests
            IT_RESULT=$?

            print_summary

            # どちらかが失敗したら非ゼロを返す
            if [ $UT_RESULT -ne 0 ] || [ $IT_RESULT -ne 0 ]; then
                exit 1
            fi
            exit 0
            ;;
        *)
            echo -e "${RED}❌ Invalid argument: $test_type${NC}"
            echo ""
            echo "Usage: $0 [ut|it|all]"
            echo ""
            echo "  ut, unit         Run Unit Tests only"
            echo "  it, integration  Run Integration Tests only"
            echo "  all              Run all tests (default)"
            echo ""
            exit 1
            ;;
    esac
}

# スクリプト実行
main "$@"
