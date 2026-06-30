@echo off
chcp 65001 >nul

:: 物流管理平台 - 本地开发启动脚本（Windows）
:: 会自动拉起 XXL-Job 调度中心，并在执行器端口冲突时切换备用端口。

setlocal
cd /d "%~dp0.."

echo 物流管理平台本地开发启动
echo.

echo [1/4] 检查 XXL-Job 调度中心
netstat -ano | findstr ":8081" | findstr "LISTENING" >nul
if errorlevel 1 (
    echo   调度中心未运行，尝试启动...
    powershell -NoProfile -ExecutionPolicy Bypass -File "F:\Development\Middleware\scripts\middleware-manager.ps1" -Service xxljob -Action start
) else (
    echo   调度中心已运行：http://127.0.0.1:8081/xxl-job-admin
)

echo.
echo [2/4] 检查后端端口和 XXL-Job 执行器端口
netstat -ano | findstr ":8080" | findstr "LISTENING" >nul
if not errorlevel 1 (
    echo   8080 端口已被占用，后端可能已经启动。请先关闭重复进程，或改用其他 APP_PORT。
    pause
    exit /b 1
)

set XXL_EXECUTOR_PORT=9999
netstat -ano | findstr ":9999" | findstr "LISTENING" >nul
if not errorlevel 1 (
    echo   9999 端口已被占用，改用备用端口 10099。
    set XXL_EXECUTOR_PORT=10099
)
netstat -ano | findstr ":%XXL_EXECUTOR_PORT%" | findstr "LISTENING" >nul
if not errorlevel 1 (
    echo   端口 %XXL_EXECUTOR_PORT% 仍被占用，请关闭重复启动的后端或手动指定其他端口。
    pause
    exit /b 1
)

echo.
echo [3/4] 检查应用 JAR
if not exist "target\demo-springboot-1.0-SNAPSHOT.jar" (
    echo   未找到 JAR，开始打包...
    call mvn clean package -DskipTests
    if errorlevel 1 (
        echo   打包失败，请先处理 Maven 错误。
        pause
        exit /b 1
    )
) else (
    echo   JAR 已存在。
)

echo.
echo [4/4] 启动后端应用
set NACOS_REGISTER_ENABLED=false
set SPRING_SQL_INIT_MODE=never
set MANAGEMENT_HEALTH_ELASTICSEARCH_ENABLED=false
set LOCAL_FRONTEND_AUTO_START=true
set LOCAL_FRONTEND_AUTO_OPEN=true
set AI_INTERNAL_SHARED_SECRET=local-dev-ai-secret-please-change

start "物流管理平台" javaw -jar target\demo-springboot-1.0-SNAPSHOT.jar ^
  --xxl.job.enabled=true ^
  --xxl.job.admin.addresses=http://127.0.0.1:8081/xxl-job-admin ^
  --xxl.job.executor.port=%XXL_EXECUTOR_PORT% ^
  --app.ai.python.enabled=true ^
  --app.encrypt.enabled=false

echo.
echo 后端启动命令已发送。
echo 应用地址：http://127.0.0.1:8080
echo XXL-Job：http://127.0.0.1:8081/xxl-job-admin
echo 执行器端口：%XXL_EXECUTOR_PORT%
echo Python AI 本地启动前请设置同值：set AI_INTERNAL_SHARED_SECRET=%AI_INTERNAL_SHARED_SECRET%
