@echo off
chcp 65001 >nul

:: 物流管理平台 — 日志查看（Windows）
echo 📋 物流管理平台日志

if exist "logs\logistics-management.log" (
    echo === 最近 50 行 ===
    powershell -Command "Get-Content logs\logistics-management.log -Tail 50"
) else if exist "%USERPROFILE%\logs\logistics-management.log" (
    echo === 最近 50 行 ===
    powershell -Command "Get-Content %USERPROFILE%\logs\logistics-management.log -Tail 50"
) else (
    echo ❌ 未找到日志文件。请检查 LOG_FILE 配置。
)

echo.
pause
