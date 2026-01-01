# 多阶段构建 AIDoc 项目

# 第一阶段：构建后端 JAR
FROM maven:3.9.4-openjdk-17 AS backend-builder

WORKDIR /app

# 复制 POM 文件并下载依赖
COPY backend/pom.xml .
RUN mvn dependency:go-offline -q

# 复制源代码并构建
COPY backend/src ./src
RUN mvn package -DskipTests -q

# 第二阶段：构建前端
FROM flutter:3.13.0 AS frontend-builder

WORKDIR /app

# 复制前端代码并构建
COPY frontend/ ./
RUN flutter pub get
RUN flutter build web

# 第三阶段：运行后端服务并集成前端
FROM openjdk:17-jre-slim

WORKDIR /app

# 安装必要的工具
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# 从构建阶段复制 JAR 文件和构建的前端文件
COPY --from=backend-builder /app/target/AIDoc-*.jar app.jar
COPY --from=frontend-builder /app/build/web ./frontend/build/web

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]