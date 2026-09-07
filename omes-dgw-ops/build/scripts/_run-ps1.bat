@echo off
setlocal
cd /d "%~dp0"
set "SCRIPT=%~1"
shift
chcp 65001 >nul
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0%SCRIPT%" %*
endlocal
exit /b %ERRORLEVEL%
