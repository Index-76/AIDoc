@echo off
setlocal enabledelayedexpansion

echo 开始部署 AIDoc 项目...

REM 检查必要工具
where java >nul 2>&1
if errorlevel 1 (
    echo 错误: Java 未安装，请先安装 Java 8+
    exit /b 1
)

where mvn >nul 2>&1
if errorlevel 1 (
    echo 错误: Maven 未安装，请先安装 Maven
    exit /b 1
)

where flutter >nul 2>&1
if errorlevel 1 (
    echo 错误: Flutter 未安装，请先安装 Flutter
    exit /b 1
)

REM 项目路径
set "PROJECT_ROOT=%~dp0.."
set "BACKEND_DIR=%PROJECT_ROOT%\backend"
set "FRONTEND_DIR=%PROJECT_ROOT%\frontend"

echo 构建后端...

cd /d "%BACKEND_DIR%"
call mvn clean package -DskipTests

echo 构建前端 Web 应用...

cd /d "%FRONTEND_DIR%"
call flutter clean
call flutter pub get
call flutter build web

echo 部署完成!

echo.
echo 启动后端服务，请在 backend 目录下运行:
echo   java -jar target/AIDoc-0.0.1-SNAPSHOT.jar
echo 或者在 IDE 中直接运行 AiDocApplication 类
echo.

pause