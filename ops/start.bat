@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

:: 物流管理平台 — 一键启动（Windows）
set "PROJECT_DIR=%~dp0.."
cd /d "%PROJECT_DIR%"

echo 🚀 启动物流管理平台...
echo.

:: 自动检测并启动中间件（按需启动 Windows 服务）
:: MySQL / Redis / RabbitMQ / ES 需要已在 Windows 上安装并运行
echo 【检查中间件状态】

set ALL_RUNNING=1

:: 检查 MySQL
netstat -an | findstr ":3306.*LISTENING" >nul
if errorlevel 1 (
    echo   ❌ MySQL :3306 未运行 —— 请手动启动
    set ALL_RUNNING=0
) else (
    echo   ✅ MySQL :3306 运行中
)

:: 检查 Redis
netstat -an | findstr ":6379.*LISTENING" >nul
if errorlevel 1 (
    echo   ❌ Redis :6379 未运行 —— 请手动启动
    set ALL_RUNNING=0
) else (
    echo   ✅ Redis :6379 运行中
)

:: 检查 RabbitMQ
netstat -an | findstr ":5672.*LISTENING" >nul
if errorlevel 1 (
    echo   ❌ RabbitMQ :5672 未运行 —— 请手动启动
    set ALL_RUNNING=0
) else (
    echo   ✅ RabbitMQ :5672 运行中
)

:: 检查 ES
netstat -an | findstr ":9200.*LISTENING" >nul
if errorlevel 1 (
    echo   ❌ Elasticsearch :9200 未运行 —— 请手动启动
    set ALL_RUNNING=0
) else (
    echo   ✅ Elasticsearch :9200 运行中
)

if !ALL_RUNNING!==0 (
    echo.
    echo ⚠️  部分中间件未运行，请启动后再执行本脚本。
    pause
    exit /b 1
)

echo.
echo 【确保 JAR 包存在】
if not exist "target\demo-springboot-1.0-SNAPSHOT.jar" (
    echo 📦 未找到 JAR，正在构建...
    call mvn clean package -DskipTests
    if errorlevel 1 (
        echo ❌ 构建失败
        pause
        exit /b 1
    )
    echo   构建完成
)

echo.
echo 【启动应用】
set NACOS_REGISTER_ENABLED=false
set SPRING_SQL_INIT_MODE=never
set APP_ENCRYPT_ENABLED=false
set MANAGEMENT_HEALTH_ELASTICSEARCH_ENABLED=false
set LOCAL_FRONTEND_AUTO_START=false
set LOCAL_FRONTEND_AUTO_OPEN=false

start "物流管理平台" javaw -jar target\demo-springboot-1.0-SNAPSHOT.jar --xxl.job.enabled=false

echo.
echo ⏳ 等待应用就绪（最多 60 秒）...
for /L %%i in (1,1,12) do (
    timeout /t 5 /nobreak >nul
    curl -s http://localhost:8080/actuator/health 2>nul | findstr "UP" >nul
    if not errorlevel 1 (
        echo.
        echo ✅ 物流管理平台启动完成！
        echo.
        echo ══════════════════════════════════════
        echo   应用: http://localhost:8080
        echo   前端: http://localhost:5173
        echo ══════════════════════════════════════
        start http://localhost:5173
        exit /b 0
    )
)

echo ⚠️  等待超时，请检查控制台窗口。
pause
