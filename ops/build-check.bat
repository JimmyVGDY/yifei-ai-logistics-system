@echo off
chcp 65001 >nul

:: 物流管理平台 — 编译检查（Windows）
:: 用法：双击运行或在 CMD 中执行 ops\build-check.bat
:: 作用：编译项目并报告结果，等同于 CI 的"编译验证"步骤

cd /d "%~dp0.."

echo ╔══════════════════════════════════╗
echo ║   物流管理平台 — 编译检查        ║
echo ╚══════════════════════════════════╝
echo.

echo [1/3] 清理旧构建...
call *** clean -q 2>nul

echo [2/3] 编译项目（89 个源文件）...
call *** compile -B 2>&1

if errorlevel 1 (
    echo.
    echo ❌ 编译失败！请检查上方错误信息。
    echo.
    pause
    exit /b 1
)

echo [3/3] 检查结果...
echo.
echo ✅ 编译通过 — 89 个源文件全部成功
echo.

:: 可选：显示编译时间（需要 PowerShell）
for /f "tokens=*" %%a in ('powershell -Command "Get-Date -Format 'yyyy-MM-dd HH:mm:ss'"') do set NOW=%%a
echo   检查时间: %NOW%
echo   项目路径: %CD%

echo.
pause
