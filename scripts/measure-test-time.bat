@echo off
REM テスト実行時間計測スクリプト（Windows版）
REM Usage: scripts\measure-test-time.bat [ut|it|all]

setlocal enabledelayedexpansion

set TEST_TYPE=%1
if "%TEST_TYPE%"=="" set TEST_TYPE=all

echo.
echo ╔════════════════════════════════════════════════════╗
echo ║       🚀 Test Execution Time Measurement           ║
echo ╚════════════════════════════════════════════════════╝
echo.

if /i "%TEST_TYPE%"=="ut" goto run_ut
if /i "%TEST_TYPE%"=="unit" goto run_ut
if /i "%TEST_TYPE%"=="it" goto run_it
if /i "%TEST_TYPE%"=="integration" goto run_it
if /i "%TEST_TYPE%"=="all" goto run_all
goto invalid_arg

:run_ut
call :print_header "🧪 Running Unit Tests (UT)"

set UT_START=%time%
call gradlew.bat unitTest --no-daemon --console=plain
set UT_EXIT_CODE=%ERRORLEVEL%
set UT_END=%time%

call :calc_duration UT_DURATION %UT_START% %UT_END%

echo.
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
if %UT_EXIT_CODE% equ 0 (
    echo ✅ Unit Tests Completed
) else (
    echo ❌ Unit Tests Failed
)
echo ⏱️  Duration: !UT_DURATION!s
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo.

call :print_summary
exit /b %UT_EXIT_CODE%

:run_it
call :print_header "🔬 Running Integration Tests (IT)"
echo 📦 Using Testcontainers + Oracle XE
echo ⚠️  This may take 3-6 minutes (includes Oracle startup)
echo.

set IT_START=%time%
call gradlew.bat integrationTest --no-daemon --console=plain
set IT_EXIT_CODE=%ERRORLEVEL%
set IT_END=%time%

call :calc_duration IT_DURATION %IT_START% %IT_END%

echo.
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
if %IT_EXIT_CODE% equ 0 (
    echo ✅ Integration Tests Completed
) else (
    echo ❌ Integration Tests Failed
)
echo ⏱️  Duration: !IT_DURATION!s
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo.

call :print_summary
exit /b %IT_EXIT_CODE%

:run_all
call :print_header "🧪 Running Unit Tests (UT)"

set UT_START=%time%
call gradlew.bat unitTest --no-daemon --console=plain
set UT_EXIT_CODE=%ERRORLEVEL%
set UT_END=%time%

call :calc_duration UT_DURATION %UT_START% %UT_END%

echo.
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
if %UT_EXIT_CODE% equ 0 (
    echo ✅ Unit Tests Completed
) else (
    echo ❌ Unit Tests Failed
)
echo ⏱️  Duration: !UT_DURATION!s
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo.

call :print_header "🔬 Running Integration Tests (IT)"
echo 📦 Using Testcontainers + Oracle XE
echo.

set IT_START=%time%
call gradlew.bat integrationTest --no-daemon --console=plain
set IT_EXIT_CODE=%ERRORLEVEL%
set IT_END=%time%

call :calc_duration IT_DURATION %IT_START% %IT_END%

echo.
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
if %IT_EXIT_CODE% equ 0 (
    echo ✅ Integration Tests Completed
) else (
    echo ❌ Integration Tests Failed
)
echo ⏱️  Duration: !IT_DURATION!s
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo.

call :print_summary

if %UT_EXIT_CODE% neq 0 exit /b %UT_EXIT_CODE%
if %IT_EXIT_CODE% neq 0 exit /b %IT_EXIT_CODE%
exit /b 0

:invalid_arg
echo ❌ Invalid argument: %TEST_TYPE%
echo.
echo Usage: %0 [ut^|it^|all]
echo.
echo   ut, unit         Run Unit Tests only
echo   it, integration  Run Integration Tests only
echo   all              Run all tests (default)
echo.
exit /b 1

:print_header
echo.
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo %~1
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo.
exit /b 0

:calc_duration
set start_time=%~2
set end_time=%~3

REM 時間を秒に変換（簡易版）
for /f "tokens=1-3 delims=:." %%a in ("%start_time%") do (
    set /a start_sec=%%a*3600 + %%b*60 + %%c
)
for /f "tokens=1-3 delims=:." %%a in ("%end_time%") do (
    set /a end_sec=%%a*3600 + %%b*60 + %%c
)

set /a duration=end_sec - start_sec
if !duration! lss 0 set /a duration=duration + 86400

set %~1=!duration!
exit /b 0

:print_summary
echo.
echo ╔════════════════════════════════════════════════════╗
echo ║          📊 Test Execution Summary                 ║
echo ╠════════════════════════════════════════════════════╣
echo ║ 🧪 Unit Tests         │ !UT_DURATION!s               ║
echo ║ 🔬 Integration Tests  │ !IT_DURATION!s               ║
echo ╠════════════════════════════════════════════════════╣
set /a TOTAL_DURATION=!UT_DURATION! + !IT_DURATION!
echo ║ 📦 Total              │ !TOTAL_DURATION!s               ║
echo ╚════════════════════════════════════════════════════╝
echo.
echo 🎯 Performance Notes:
echo   - UT expected: ^< 5s
echo   - IT expected: 3-6 minutes (includes Oracle container startup)
echo   - Improvement: Container reuse can reduce IT time by 70-80%%
echo.
exit /b 0
