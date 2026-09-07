@echo off
chcp 65001 >nul
cd /d "%~dp0"
call "scripts\_run-ps1.bat" "03-up.ps1" %*
if errorlevel 1 pause
exit /b %ERRORLEVEL%
