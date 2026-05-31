@echo off
chcp 65001 >nul

:: 物流管理平台 — 本地开发启动（Windows）
:: 自动启动 XXL-Job 调度中心 + 应用

setlocal
cd /d "%~dp0.."

echo 🚀 物流管理平台 本地开发启动
echo.

:: 启动 XXL-Job 调度中心（如果没在运行）
echo [1/3] XXL-Job 调度中心
netstat -an | findstr ":8081.*LISTENING" >nul
if errorlevel 1 (
    echo   启动中...
    powershell -NoProfile -ExecutionPolicy Bypass -File "F:\Development\Middleware\scripts\middleware-manager.ps1" -Service xxljob -Action start
) else (
    echo   ✅ 已在运行 :8081
)

:: 构建（如需要）
echo.
echo [2/3] 构建项目
if not exist "target\demo-springboot-1.0-SNAPSHOT.jar" (
    echo   📦 首次构建中...
    call *** clean package -DskipTests
    if errorlevel 1 (
        echo   ❌ 构建失败
        pause
        exit /b 1
    )
) else (
    echo   ✅ JAR 已就绪
)

:: 启动应用（连接 XXL-Job）
echo.
echo [3/3] 启动应用...
set NACOS_REGISTER_ENABLED=false
set SPRING_SQL_INIT_MODE=never
set LOCAL_FRONTEND_AUTO_START=true
set LOCAL_FRONTEND_AUTO_OPEN=true

start "物流管理平台" javaw -jar target\demo-springboot-1.0-SNAPSHOT.jar ^
  --xxl.job.admin.addresses=http://127.0.0.1:8081/xxl-job-admin ^
  --xxl.job.executor.port=9999

echo   应用已启动，XXL-Job 调度中心: http://127.0.0.1:8081/xxl-job-admin
