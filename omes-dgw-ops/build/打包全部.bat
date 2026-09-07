@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo [build-all] Backend jar + frontend dist -^> images ...
call "scripts\_run-ps1.bat" "build-all.ps1" %*
if errorlevel 1 pause
exit /b %ERRORLEVEL%
