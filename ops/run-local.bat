@echo off
chcp 65001 >nul

:: 物流管理平台 — 本地开发启动（Windows）
:: 前提：MySQL/Redis/RabbitMQ/ES 已在 Windows 上运行
setlocal

cd /d "%~dp0.."

:: 如果 JAR 还没构建
if not exist "target\demo-springboot-1.0-SNAPSHOT.jar" (
    echo 📦 构建中...
    call mvn clean package -DskipTests
    if errorlevel 1 (
        echo ❌ 构建失败
        pause
        exit /b 1
    )
)

echo 🚀 启动应用...
set NACOS_REGISTER_ENABLED=false
set SPRING_SQL_INIT_MODE=never
set LOCAL_FRONTEND_AUTO_START=true
set LOCAL_FRONTEND_AUTO_OPEN=true

start "物流管理平台" javaw -jar target\demo-springboot-1.0-SNAPSHOT.jar --xxl.job.enabled=false

echo   应用将在后台启动，浏览器将自动打开前端页面
