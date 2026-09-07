@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo ========================================
echo  Build release package (4 Docker image tars)
echo ========================================
echo.

call "build\scripts\_run-ps1.bat" "test-image-tars.ps1"
if errorlevel 1 (
    echo [hint] Missing images; running build-all ...
    call "build\scripts\_run-ps1.bat" "build-all.ps1"
    if errorlevel 1 goto :fail
)

call "build\scripts\_run-ps1.bat" "publish.ps1"
if errorlevel 1 goto :fail

echo.
echo Release folder: release\omes-dgw-deploy
pause
exit /b 0

:fail
echo [failed]
pause
exit /b 1
