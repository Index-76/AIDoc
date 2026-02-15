---
title: AIDOC-api-v1
language_tabs:
  - shell: Shell
  - http: HTTP
  - javascript: JavaScript
  - ruby: Ruby
  - python: Python
  - php: PHP
  - java: Java
  - go: Go
toc_footers: []
includes: []
search: true
code_clipboard: true
highlight_theme: darkula
headingLevel: 2
generator: "@tarslib/widdershins v4.0.30"
---

# AIDOC-api-v1

Base URLs:

# Authentication

# 用户管理

## POST 登录系统

POST /api/v1/auth/login

> Body 请求参数

```json
{
  "username": "123",
  "password": "123"
}
```

### 请求参数

| 名称 | 位置 | 类型   | 必选 | 说明 |
| ---- | ---- | ------ | ---- | ---- |
| body | body | object | 是   | none |

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "token": "d47718e7-4b30-43f7-9cba-623464719db9",
    "user": {
      "userid": 8,
      "username": "123",
      "email": "123",
      "password": "$2a$10$a5Ecyl2DUI0UV7pzKcI/GubiI/H2N60wmxpZWHN8AgfTou93BWpaC",
      "createdAt": "2026-01-02T00:26:01",
      "updatedAt": "2026-01-02T00:26:01",
      "status": 1
    },
    "message": "登录成功"
  }
}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明 | 数据模型 |
| ------ | ------------------------------------------------------- | ---- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | none | Inline   |

### 返回数据结构

## POST 登出系统

POST /api/v1/auth/logout

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "msg": "success",
  "data": "退出成功"
}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明 | 数据模型 |
| ------ | ------------------------------------------------------- | ---- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | none | Inline   |

### 返回数据结构

## GET 获取用户信息

GET /api/v1/users/me

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "userid": 8,
    "username": "123",
    "email": "123",
    "password": "$2a$10$a5Ecyl2DUI0UV7pzKcI/GubiI/H2N60wmxpZWHN8AgfTou93BWpaC",
    "createdAt": "2026-01-02T00:26:01",
    "updatedAt": "2026-01-02T00:26:01",
    "status": 1
  }
}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明 | 数据模型 |
| ------ | ------------------------------------------------------- | ---- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | none | Inline   |

### 返回数据结构

## POST 注册账户

POST /api/v1/auth/register

> Body 请求参数

```json
{
  "username": "123456",
  "email": "123456@qq.com",
  "password": "123456"
}
```

### 请求参数

| 名称 | 位置 | 类型   | 必选 | 说明 |
| ---- | ---- | ------ | ---- | ---- |
| body | body | object | 是   | none |

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "msg": "success",
  "data": "注册成功"
}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明 | 数据模型 |
| ------ | ------------------------------------------------------- | ---- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | none | Inline   |

### 返回数据结构

## GET 查询配置

GET /api/v1/user-config

> 返回示例

> 200 Response

```json
{
  "code": 200,
  "msg": "success",
  "data": "{\r\n  \"siliconFlowApiKey\" : \"\",\r\n  \"siliconFlowBaseUrl\" : \"https://api.siliconflow.cn/v1/chat/completions\",\r\n  \"chatModelName\" : \"deepseek-ai/DeepSeek-V3.2\",\r\n  \"decisionModelName\" : \"Qwen/Qwen2.5-7B-Instruct\",\r\n  \"analysisModelName\" : \"deepseek-ai/DeepSeek-V3.2\",\r\n  \"databaseUrl\" : \"\",\r\n  \"databaseUsername\" : \"\",\r\n  \"databasePassword\" : \"\",\r\n  \"databaseName\" : \"\",\r\n  \"databaseTableName\" : \"\",\r\n  \"databaseType\" : \"mysql\"\r\n}"
}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明 | 数据模型 |
| ------ | ------------------------------------------------------- | ---- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | none | Inline   |

### 返回数据结构

## POST 更新配置

POST /api/v1/user-config

> Body 请求参数

```json
{
  "siliconFlowApiKey": "test",
  "siliconFlowBaseUrl": "https://api.siliconflow.cn/v1/chat/completions",
  "chatModelName": "deepseek-ai/DeepSeek-V3.2",
  "decisionModelName": "Qwen/Qwen2.5-7B-Instruct",
  "analysisModelName": "deepseek-ai/DeepSeek-V3.2"
}
```

### 请求参数

| 名称 | 位置 | 类型   | 必选 | 说明 |
| ---- | ---- | ------ | ---- | ---- |
| body | body | object | 是   | none |

> 返回示例

```json
{
  "code": 500,
  "msg": "服务器内部错误：Required request body is missing: public com.project.aidoc.common.Result<java.lang.String> com.project.aidoc.controller.SystemConfigController.updateUserConfig(com.project.aidoc.entity.UserConfig)",
  "data": null
}
```

```json
{
  "code": 500,
  "msg": "服务器内部错误：Required request body is missing: public com.project.aidoc.common.Result<java.lang.String> com.project.aidoc.controller.SystemConfigController.updateUserConfig(com.project.aidoc.entity.UserConfig)",
  "data": null
}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明 | 数据模型 |
| ------ | ------------------------------------------------------- | ---- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | none | Inline   |

### 返回数据结构

# 文件管理

## GET 获取文件列表

GET /api/v1/files

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明 | 数据模型 |
| ------ | ------------------------------------------------------- | ---- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | none | Inline   |

### 返回数据结构

## POST 上传文件

POST /api/v1/files/upload

> Body 请求参数

```yaml
section: read
file: file://D:\下载\默认模块.md
```

### 请求参数

| 名称      | 位置 | 类型           | 必选 | 说明 |
| --------- | ---- | -------------- | ---- | ---- |
| body      | body | object         | 是   | none |
| » section | body | string         | 否   | none |
| » file    | body | string(binary) | 否   | none |

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明 | 数据模型 |
| ------ | ------------------------------------------------------- | ---- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | none | Inline   |

### 返回数据结构

## GET 下载文件

GET /api/v1/files/{fileId}/download

### 请求参数

| 名称   | 位置 | 类型   | 必选 | 说明 |
| ------ | ---- | ------ | ---- | ---- |
| fileId | path | string | 是   | none |

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明 | 数据模型 |
| ------ | ------------------------------------------------------- | ---- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | none | Inline   |

### 返回数据结构

## DELETE 删除文件

DELETE /api/v1/files/{fileId}/delete

### 请求参数

| 名称   | 位置 | 类型   | 必选 | 说明 |
| ------ | ---- | ------ | ---- | ---- |
| fileId | path | string | 是   | none |

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明 | 数据模型 |
| ------ | ------------------------------------------------------- | ---- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | none | Inline   |

### 返回数据结构

## POST 移动文件

POST /api/v1/files/{fileId}/move

> Body 请求参数

```json
{
  "destinationSectionId": "read"
}
```

### 请求参数

| 名称   | 位置 | 类型   | 必选 | 说明 |
| ------ | ---- | ------ | ---- | ---- |
| fileId | path | string | 是   | none |
| body   | body | object | 是   | none |

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明 | 数据模型 |
| ------ | ------------------------------------------------------- | ---- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | none | Inline   |

### 返回数据结构

## POST 重命名文件

POST /api/v1/files/{fileId}/rename

> Body 请求参数

```json
{
  "newName": "text.md"
}
```

### 请求参数

| 名称   | 位置 | 类型   | 必选 | 说明 |
| ------ | ---- | ------ | ---- | ---- |
| fileId | path | string | 是   | none |
| body   | body | object | 是   | none |

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明 | 数据模型 |
| ------ | ------------------------------------------------------- | ---- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | none | Inline   |

### 返回数据结构

# AI对话

## POST 发送消息

POST /api/v1/chat/message

> Body 请求参数

```json
{
  "message": "test",
  "sessionId": "{{sessionId}}"
}
```

### 请求参数

| 名称 | 位置 | 类型   | 必选 | 说明 |
| ---- | ---- | ------ | ---- | ---- |
| body | body | object | 是   | none |

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明 | 数据模型 |
| ------ | ------------------------------------------------------- | ---- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | none | Inline   |

### 返回数据结构

## POST 创建新对话

POST /api/v1/chat/new

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明 | 数据模型 |
| ------ | ------------------------------------------------------- | ---- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | none | Inline   |

### 返回数据结构

## GET 获取对话列表

GET /api/v1/chat/sessionList

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明 | 数据模型 |
| ------ | ------------------------------------------------------- | ---- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | none | Inline   |

### 返回数据结构

## GET 获取对话历史

GET /api/v1/chat/session/{sessionId}/history

### 请求参数

| 名称      | 位置 | 类型   | 必选 | 说明 |
| --------- | ---- | ------ | ---- | ---- |
| sessionId | path | string | 是   | none |

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明 | 数据模型 |
| ------ | ------------------------------------------------------- | ---- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | none | Inline   |

### 返回数据结构

## DELETE 删除对话

DELETE /api/v1/chat/session/{sessionId}/delete

### 请求参数

| 名称      | 位置 | 类型   | 必选 | 说明 |
| --------- | ---- | ------ | ---- | ---- |
| sessionId | path | string | 是   | none |

> 返回示例

> 200 Response

```json
{}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明 | 数据模型 |
| ------ | ------------------------------------------------------- | ---- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | none | Inline   |

### 返回数据结构

# 其他

## GET 服务器健康检查

GET /api/v1/health

> 返回示例

> 200 Response

```json
{
  "details": {
    "database": "UP",
    "mongodb": "UP"
  },
  "status": "UP",
  "timestamp": 1768740187202
}
```

### 返回结果

| 状态码 | 状态码含义                                              | 说明 | 数据模型 |
| ------ | ------------------------------------------------------- | ---- | -------- |
| 200    | [OK](https://tools.ietf.org/html/rfc7231#section-6.3.1) | none | Inline   |

### 返回数据结构

# 数据模型

<h2 id="tocS_Pet">Pet</h2>

<a id="schemapet"></a>
<a id="schema_Pet"></a>
<a id="tocSpet"></a>
<a id="tocspet"></a>

```json
{
  "id": 1,
  "category": {
    "id": 1,
    "name": "string"
  },
  "name": "doggie",
  "photoUrls": ["string"],
  "tags": [
    {
      "id": 1,
      "name": "string"
    }
  ],
  "status": "available"
}
```

### 属性

| 名称      | 类型                        | 必选 | 约束 | 中文名 | 说明         |
| --------- | --------------------------- | ---- | ---- | ------ | ------------ |
| id        | integer(int64)              | true | none |        | 宠物ID编号   |
| category  | [Category](#schemacategory) | true | none |        | 分组         |
| name      | string                      | true | none |        | 名称         |
| photoUrls | [string]                    | true | none |        | 照片URL      |
| tags      | [[Tag](#schematag)]         | true | none |        | 标签         |
| status    | string                      | true | none |        | 宠物销售状态 |

#### 枚举值

| 属性   | 值        |
| ------ | --------- |
| status | available |
| status | pending   |
| status | sold      |

<h2 id="tocS_Category">Category</h2>

<a id="schemacategory"></a>
<a id="schema_Category"></a>
<a id="tocScategory"></a>
<a id="tocscategory"></a>

```json
{
  "id": 1,
  "name": "string"
}
```

### 属性

| 名称 | 类型           | 必选  | 约束 | 中文名 | 说明       |
| ---- | -------------- | ----- | ---- | ------ | ---------- |
| id   | integer(int64) | false | none |        | 分组ID编号 |
| name | string         | false | none |        | 分组名称   |

<h2 id="tocS_Tag">Tag</h2>

<a id="schematag"></a>
<a id="schema_Tag"></a>
<a id="tocStag"></a>
<a id="tocstag"></a>

```json
{
  "id": 1,
  "name": "string"
}
```

### 属性

| 名称 | 类型           | 必选  | 约束 | 中文名 | 说明       |
| ---- | -------------- | ----- | ---- | ------ | ---------- |
| id   | integer(int64) | false | none |        | 标签ID编号 |
| name | string         | false | none |        | 标签名称   |
