#!/bin/bash

# AIDoc 项目部署脚本 (Linux/macOS)

set -e

echo "开始部署 AIDoc 项目..."

# 检查是否安装了必要的工具
command -v java >/dev/null 2>&1 || { echo >&2 "Java 未安装，请先安装 Java 8+"; exit 1; }
command -v mvn >/dev/null 2>&1 || { echo >&2 "Maven 未安装，请先安装 Maven"; exit 1; }
command -v flutter >/dev/null 2>&1 || { echo >&2 "Flutter 未安装，请先安装 Flutter"; exit 1; }

# 项目路径
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$PROJECT_ROOT/backend"
FRONTEND_DIR="$PROJECT_ROOT/frontend"

echo "构建后端..."

cd "$BACKEND_DIR"
mvn clean package -DskipTests

echo "构建前端 Web 应用..."

cd "$FRONTEND_DIR"
flutter clean
flutter pub get
flutter build web

echo "部署完成!"

echo "启动后端服务，请在 backend 目录下运行:"
echo "  java -jar target/AIDoc-0.0.1-SNAPSHOT.jar"
echo ""
echo "或者在 IDE 中直接运行 AiDocApplication 类"