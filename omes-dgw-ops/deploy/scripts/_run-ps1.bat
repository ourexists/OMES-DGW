@echo off
cd /d "%~dp0"
set "SCRIPT=%~1"
shift
chcp 65001 >nul
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0%SCRIPT%" %*
exit /b %ERRORLEVEL%
