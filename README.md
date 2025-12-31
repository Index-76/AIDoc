# AIDoc 项目

AIDoc 是一个集成 Flutter 前端和 Spring Boot 后端的现代化文档管理系统。

## 项目结构

```
AIDoc/
├── backend/                 # Spring Boot 后端服务
│   ├── src/
│   ├── pom.xml
│   └── ...
├── frontend/               # Flutter 前端应用
│   ├── lib/
│   ├── android/
│   ├── ios/
│   ├── web/
│   ├── windows/
│   ├── macos/
│   ├── linux/
│   ├── pubspec.yaml
│   └── ...
├── scripts/                # 部署脚本
├── docs/                   # 项目文档
└── README.md
```

## 技术栈

- **前端**: Flutter
- **后端**: Spring Boot + MyBatis
- **数据库**: MongoDB + MySQL
- **安全框架**: Spring Security
- **构建工具**: Maven (后端), pub (前端)

## 环境要求

- Java 8+
- Maven 3.6+
- Flutter 3.0+
- MongoDB 4.0+
- MySQL 5.7+ (用于 MyBatis)

## 快速开始

### 后端设置

1. 启动 MongoDB 和 MySQL 服务
2. 修改 `backend/src/main/resources/application.yml` 中的数据库连接配置
3. 在 `backend` 目录下运行:

```bash
mvn clean install
mvn spring-boot:run
```

### 前端设置

1. 在 `frontend` 目录下运行:

```bash
flutter pub get
flutter run
```

## 配置说明

- 后端配置: `backend/src/main/resources/application.yml`
- 前端配置: `frontend/lib/config/`

## 部署

部署脚本位于 `scripts/` 目录下:

- `scripts/deploy.sh` - Linux/macOS 部署脚本
- `scripts/deploy.bat` - Windows 部署脚本

## API 文档

后端 API 文档请参考 `docs/api.md`

## 贡献

欢迎提交 Issue 和 Pull Request。

## 许可证

[MIT](LICENSE)
