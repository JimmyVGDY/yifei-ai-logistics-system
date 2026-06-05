@echo off
setlocal
set QDRANT_HOME=%QDRANT_HOME%
if "%QDRANT_HOME%"=="" set QDRANT_HOME=F:\Development\Middleware\qdrant\qdrant-1.18.2
set QDRANT_STORAGE=%QDRANT_STORAGE%
if "%QDRANT_STORAGE%"=="" set QDRANT_STORAGE=F:\Development\Middleware\qdrant\data
if not exist "%QDRANT_STORAGE%" mkdir "%QDRANT_STORAGE%"
copy /Y "%QDRANT_HOME%\qdrant.exe" "%QDRANT_STORAGE%\qdrant.exe" >nul
start "Qdrant Vector DB" /D "%QDRANT_STORAGE%" qdrant.exe
echo Qdrant started, api: http://127.0.0.1:6333
endlocal
