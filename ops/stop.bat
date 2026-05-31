@echo off
chcp 65001 >nul

:: 物流管理平台 — 停止（Windows）
echo 🛑 停止物流管理平台...

:: 查找并关闭 Java 进程
for /f "tokens=2" %%a in ('tasklist ^| findstr "javaw.exe" 2^>nul') do (
    taskkill /pid %%a /f >nul 2>nul
)
for /f "tokens=2" %%a in ('tasklist ^| findstr "java.exe" 2^>nul ^| findstr "demo-springboot" 2^>nul') do (
    taskkill /pid %%a /f >nul 2>nul
)

echo ✅ 应用已停止
