# AIDoc 项目

AIDoc 是一个集成 Flutter 前端和 Spring Boot 后端的智能现代化文档管理系统。

## 项目结构

```
AIDoc/
├── backend/                 # Spring Boot 后端服务
│   ├── src/
│   ├── pom.xml
│   └── ...
├── frontend/               # Flutter 前端应用
│   ├── lib/
│   ├── web/
│   ├── pubspec.yaml
│   └── ...
├── docs/                   # 项目文档
└── README.md
```

## 技术栈

- **前端**: Flutter
- **后端**: Spring Boot
- **数据库**: MongoDB
- **安全框架**: SaToken
- **构建工具**: Maven (后端), pub (前端)

## 环境要求

- Java 22+
- Maven 3.6+
- Flutter 3.0+
- MongoDB 4.0+

## 部署步骤

#### 1. 构建前端 Web 应用

在 `frontend` 目录下运行:

```bash
flutter clean
flutter pub get
flutter build web --no-wasm-dry-run
```

#### 2. 部署前端到后端

将 `frontend/build/web` 目录下的所有文件复制到 `backend/src/main/resources/static` 目录中:

**Windows:**

```cmd
xcopy /E /I /Y "frontend\build\web" "backend\src\main\resources\static"
```

或者直接运行copy_frontend_to_backend.bat

**Linux/macOS:**

```bash
cp -r frontend/build/web/* backend/src/main/resources/static/
```

#### 3. 启动后端服务

1. 确保 MongoDB 服务已启动
2. 修改 `backend/src/main/resources/application.yml` 中的数据库连接配置
3. 在 `backend` 目录下运行:

```bash
mvn clean compile spring-boot:run
```

后端服务默认将在 `http://localhost:8080` 启动，可以直接访问 Web 前端或使用 API。

#### 4. 桌面应用

如需运行桌面应用（Windows），在 `frontend` 目录下运行:

```bash
flutter build windows
```

构建完成后，在 `frontend\build\windows\x64\runner\Release` 目录中找到可执行文件。

## 开发模式

### 后端开发

1. 启动 MongoDB 服务
2. 修改 `backend/src/main/resources/application.yml` 中的数据库连接配置
3. 在 `backend` 目录下运行:

```bash
mvn spring-boot:run
```

### 前端开发

在 `frontend` 目录下运行:

```bash
flutter pub get
flutter run
```

## 配置说明

- 后端配置: `backend/src/main/resources/application.yml`
- 前端配置: `frontend/lib/config/`

## API 文档

后端 API 文档请参考 `docs/AIDOC-api-v1.md`

## 测试

使用 Apifox 进行 api 测试

## 贡献

欢迎提交 Issue 和 Pull Request。

## 许可证

[MIT](LICENSE)
