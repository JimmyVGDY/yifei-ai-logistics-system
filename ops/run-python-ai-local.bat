@echo off
setlocal

set PROJECT_DIR=%~dp0..
cd /d "%PROJECT_DIR%\ai-service"

if "%AI_INTERNAL_SHARED_SECRET%"=="" set AI_INTERNAL_SHARED_SECRET=local-dev-ai-secret-please-change
if "%JAVA_INTERNAL_URL%"=="" set JAVA_INTERNAL_URL=http://127.0.0.1:8080

echo Starting Python AI service from %CD%
echo JAVA_INTERNAL_URL=%JAVA_INTERNAL_URL%
echo AI_INTERNAL_SHARED_SECRET is set for local Java internal callbacks.
echo.

uv run python -m uvicorn ai_service.main:app --host 127.0.0.1 --port 8001 --reload
