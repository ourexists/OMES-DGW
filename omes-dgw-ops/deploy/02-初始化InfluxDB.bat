@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo [init-influx] InfluxDB org + bucket + app token (interactive) ...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp002-初始化InfluxDB.ps1" %*
if errorlevel 1 pause & exit /b %ERRORLEVEL%
pause
