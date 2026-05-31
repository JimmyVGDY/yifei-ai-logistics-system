@echo off
chcp 65001 >nul

:: 物流管理平台 — 服务状态检查（Windows）
echo.
echo ╔══════════════════════════════════════════╗
echo ║     物流管理平台  服务状态               ║
echo ╚══════════════════════════════════════════╝
echo.

:: 检查端口
setlocal enabledelayedexpansion
set PORTS=3306:MySQL 6379:Redis 5672:RabbitMQ 9200:ES 8080:应用
for %%p in (%PORTS%) do (
    for /f "tokens=1,2 delims=:" %%a in ("%%p") do (
        set PORT=%%a
        set NAME=%%b
        netstat -an | findstr ":!PORT! .*LISTENING" >nul
        if errorlevel 1 (
            echo   ❌ !NAME! :!PORT! 未运行
        ) else (
            echo   ✅ !NAME! :!PORT! 运行中
        )
    )
)

echo.
echo 【应用健康检查】
curl -s --max-time 3 http://localhost:8080/actuator/health 2>nul | findstr "UP" >nul
if errorlevel 1 (
    echo   ❌ 应用无响应
) else (
    echo   ✅ 应用健康
)

echo.
pause
