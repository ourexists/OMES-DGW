@echo off
chcp 65001 >nul
cd /d "%~dp0"

if exist ".env" (
    echo [01-初始化环境配置] .env 已存在，跳过复制。
    exit /b 0
)

if not exist ".env.example" (
    echo [01-初始化环境配置] 缺少 .env.example
    exit /b 1
)

copy /Y ".env.example" ".env" >nul
echo [01-初始化环境配置] 已从 .env.example 生成 .env，请编辑 MOUNT_PATH、SQLITE_PATH、Redis/Influx 等后运行 03-启动.bat。
exit /b 0
