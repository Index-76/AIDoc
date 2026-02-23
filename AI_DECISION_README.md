# AI决策系统说明文档

## 功能概述

本系统实现了基于AI的智能决策功能，能够自动分析用户输入并决定是否需要调用特定工具来处理请求。

## 📝 重要说明

**用户只需调用一个接口即可完成所有操作：**

```
POST /api/v1/chat/message
```

系统会自动处理：

1. 消息存储
2. AI决策分析
3. 工具调用（如需要）
4. AI回复生成
5. 实时状态通知（通过SSE）

## 核心组件

### 1. 决策算法

决策流程如下：

1. 检查用户消息中是否包含 `<tools=?>` 格式的工具标记
2. 检测是否包含疑问词或否定词（如"不"、"？"等）
3. 检测是否包含工具关键词（如"自动填表"、"目录查看"等）
4. 如以上都不满足，则调用决策AI进行智能分析

### 2. 工具类型定义

| 工具代码 | 工具名称   | 功能描述         |
| -------- | ---------- | ---------------- |
| 0        | 不使用工具 | 直接进行AI对话   |
| 1        | 目录查看   | 查看文件目录结构 |
| 2        | 内容总结   | 总结文档内容     |
| 3        | 格式转换   | 转换文件格式     |
| 4        | 智能填表   | 自动填写表格     |
| 5        | 智能修改   | 智能编辑文档     |

## API接口

### 用户聊天接口（推荐使用）

```
POST /api/v1/chat/message
```

**请求体：**

```json
{
  "userId": 123,
  "sessionId": "session123",
  "message": "帮我查看当前目录的文件"
}
```

**响应：**

```json
{
  "code": 200,
  "msg": "消息发送成功",
  "data": null
}
```

### SSE连接管理（可选，用于实时状态更新）

#### 建立SSE连接

```
GET /api/v1/sse/connect/{sessionId}
```

#### 断开SSE连接

```
POST /api/v1/sse/disconnect/{sessionId}
```

#### 获取连接数

```
GET /api/v1/sse/connections
```

## 工作流程

### 用户完整交互流程：

1. **用户发送消息**

   ```
   POST /api/v1/chat/message
   {
     "message": "帮我查看文件",
     "sessionId": "session123"
   }
   ```

2. **后台自动处理**
   - 消息入库（senderType="USER"）
   - 触发AI决策分析
   - 如需工具则自动调用相应工具
   - 生成AI回复
   - 回复入库（senderType="AI"）

3. **实时状态通知**（通过SSE，可选）
   - 工具开始执行通知
   - AI回复完成通知

4. **获取结果**
   ```
   GET /api/v1/chat/session/{sessionId}/history
   ```

## SSE事件格式

### 工具开始执行事件

```json
{
  "eventType": "tool_begin",
  "sessionId": "session123",
  "data": 1,
  "timestamp": 1708672800000
}
```

### AI回复完成事件

```json
{
  "eventType": "ai_reply_finish",
  "sessionId": "session123",
  "data": "success",
  "timestamp": 1708672800000
}
```

## 使用示例

### 1. 直接对话

```
POST /api/v1/chat/message
{
  "message": "你好，今天天气怎么样？",
  "sessionId": "session123"
}
```

- 决策结果：工具代码 0
- 流程：直接调用对话AI生成回复

### 2. 工具调用

```
POST /api/v1/chat/message
{
  "message": "帮我查看当前目录的文件",
  "sessionId": "session123"
}
```

- 决策结果：工具代码 1
- 流程：调用目录查看工具 → 结果反馈给对话AI → 生成回复

### 3. 显式工具指定

```
POST /api/v1/chat/message
{
  "message": "<tools=4>帮我填表",
  "sessionId": "session123"
}
```

- 决策结果：工具代码 4
- 流程：直接调用智能填表工具 → 结果反馈给对话AI → 生成回复

## 注意事项

1. **简化使用**：用户只需调用 `/api/v1/chat/message` 接口，其余全部自动处理
2. **异步处理**：消息处理完全异步，接口立即返回成功
3. **实时反馈**：如需实时状态可建立SSE连接
4. **防幻觉机制**：工具执行完成后再生成AI回复
5. **错误处理**：完善的异常捕获和错误通知机制

## 前端集成建议

1. 调用 `/api/v1/chat/message` 发送用户消息
2. （可选）建立SSE连接监听处理状态
3. 调用 `/api/v1/chat/session/{sessionId}/history` 获取对话历史
4. 根据工具代码显示相应的用户界面反馈
