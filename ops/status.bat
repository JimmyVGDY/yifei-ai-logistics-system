@echo off
setlocal enabledelayedexpansion

echo.
echo ================================================
echo   Logistics Platform - Local Service Status
echo ================================================
echo.

set PORTS=3306:MySQL 6379:Redis 5672:RabbitMQ 9200:Elasticsearch 6333:Qdrant 8001:PythonAI 8080:JavaApp
for %%p in (%PORTS%) do (
    for /f "tokens=1,2 delims=:" %%a in ("%%p") do (
        set PORT=%%a
        set NAME=%%b
        netstat -an | findstr ":!PORT! .*LISTENING" >nul
        if errorlevel 1 (
            echo   [DOWN] !NAME! :!PORT!
        ) else (
            echo   [ OK ] !NAME! :!PORT!
        )
    )
)

echo.
echo [Java application health]
curl -s --max-time 3 http://localhost:8080/actuator/health 2>nul | findstr "UP" >nul
if errorlevel 1 (
    echo   [DOWN] Java application health endpoint is not responding
) else (
    echo   [ OK ] Java application is healthy
)

echo.
echo [Python AI health - optional]
curl -s --max-time 3 http://localhost:8001/health 2>nul | findstr "status" >nul
if errorlevel 1 (
    echo   [WARN] Python AI is not running or not responding; required only when APP_AI_PYTHON_ENABLED=true
) else (
    echo   [ OK ] Python AI is responding
)

echo.
echo [Qdrant health - optional]
curl -s --max-time 3 http://localhost:6333/readyz 2>nul | findstr "ready" >nul
if errorlevel 1 (
    echo   [WARN] Qdrant is not running or not responding; AI RAG/memory will degrade
) else (
    echo   [ OK ] Qdrant is ready
)

echo.
