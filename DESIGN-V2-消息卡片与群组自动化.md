# FeishuSmartBot V2 功能设计：消息卡片升级 & 项目群自动化管理

## 1. 功能概述

本次优化包含三大改造方向：

| 改造 | 一句话描述 |
|------|-----------|
| **改造一** | 将纯文本欢迎消息升级为飞书 Interactive 消息卡片 |
| **改造二** | 新人群通知改用临时消息卡片（仅特定人可见） |
| **改造三** | 项目群自动创建 & 自动拉通知机器人入群 |

---

## 2. 改造一：欢迎消息升级为消息卡片

### 2.1 现状问题

当前 3 种欢迎消息（试标群、新人群、MCP 试标群）均使用 `msg_type: "text"` 纯文本发送：

- 视觉效果差，纯文字缺乏层次感，重要信息容易被忽略
- 链接以明文 URL 展示，不美观且不便点击
- 无法添加按钮、图片等交互元素
- 修改文案需要改代码、重新打包部署

### 2.2 目标方案

采用飞书消息卡片 JSON 2.0 + CardKit 模板方案：

```
┌──────────────────────────────────────────────────────┐
│                    改造前                             │
│                                                      │
│  代码硬编码文案 ──► msg_type: "text" ──► 纯文本消息   │
│                                                      │
├──────────────────────────────────────────────────────┤
│                    改造后                             │
│                                                      │
│  CardKit 搭建卡片 ──► 发布获得 template_id           │
│         │                                            │
│         ▼                                            │
│  代码传入 template_id + 变量                          │
│         │                                            │
│         ▼                                            │
│  msg_type: "interactive" ──► 富样式消息卡片           │
│   (带标题、按钮、分栏、颜色主题)                      │
└──────────────────────────────────────────────────────┘
```

### 2.3 卡片 JSON 2.0 结构规范

```json
{
    "schema": "2.0",
    "config": {
        "update_multi": true
    },
    "header": {
        "title": {
            "content": "卡片标题",
            "tag": "plain_text"
        },
        "template": "blue"
    },
    "body": {
        "elements": [
            {
                "tag": "markdown",
                "content": "正文内容，支持 **加粗**、[链接](url) 等"
            },
            {
                "tag": "action",
                "actions": [
                    {
                        "tag": "button",
                        "text": {
                            "tag": "plain_text",
                            "content": "按钮文字"
                        },
                        "type": "primary",
                        "multi_url": {
                            "url": "https://..."
                        }
                    }
                ]
            }
        ]
    }
}
```

### 2.4 三种欢迎卡片设计

#### A. 试标群欢迎卡片

| 属性 | 值 |
|------|-----|
| header.template | `blue`（蓝色主题） |
| header.title | "欢迎进入试标阶段" |
| 正文 | @用户 + 须知要点 |
| 按钮 | "查看《Hippo项目指南》" → 跳转文档链接 |

```json
{
    "schema": "2.0",
    "header": {
        "title": { "content": "🎉 欢迎进入试标阶段", "tag": "plain_text" },
        "template": "blue"
    },
    "body": {
        "elements": [
            {
                "tag": "markdown",
                "content": "<at id={{user_id}}></at> 恭喜通过面试，进入试标阶段！\n\n请务必完成以下步骤：\n1. 进入试标群并 **查看群公告**\n2. 仔细阅读《Hippo项目指南》"
            },
            { "tag": "hr" },
            {
                "tag": "action",
                "actions": [
                    {
                        "tag": "button",
                        "text": { "tag": "plain_text", "content": "📖 查看《Hippo项目指南》" },
                        "type": "primary",
                        "multi_url": {
                            "url": "https://meetchances.feishu.cn/docx/QJdZd5MjxoD2YJxOiZcc6tAFnBe"
                        }
                    }
                ]
            }
        ]
    }
}
```

#### B. 新人群欢迎卡片

| 属性 | 值 |
|------|-----|
| header.template | `green`（绿色主题） |
| header.title | "新人须知" |
| 正文 | 欢迎语 + 三步指引 |
| 按钮 | 无（引导查看群公告即可） |

```json
{
    "schema": "2.0",
    "header": {
        "title": { "content": "📋 新人须知", "tag": "plain_text" },
        "template": "green"
    },
    "body": {
        "elements": [
            {
                "tag": "markdown",
                "content": "你好 **{{user_name}}**，欢迎加入新人群！\n\n请按以下步骤操作：\n1. 查看 **群公告**\n2. 阅读群公告中的《Hippo项目新人指南》\n3. 有问题可 @群管理员 咨询"
            }
        ]
    }
}
```

#### C. MCP 试标群欢迎卡片

| 属性 | 值 |
|------|-----|
| header.template | `orange`（橙色主题） |
| header.title | "欢迎加入 MCP 试标群" |
| 正文 | 快速上手指南 + 收益说明 |
| 按钮 | "查看《MCP 培训文档》" → 跳转文档链接 |

```json
{
    "schema": "2.0",
    "header": {
        "title": { "content": "🚀 欢迎加入 MCP 试标群", "tag": "plain_text" },
        "template": "orange"
    },
    "body": {
        "elements": [
            {
                "tag": "markdown",
                "content": "<at id={{user_id}}></at> 欢迎！请按以下步骤快速上手：\n\n**第一步：** 查看群公告 → 了解群规与注意事项\n**第二步：** 阅读《MCP 培训文档》→ 掌握考核与操作流程\n\n---\n💰 **收益说明**\n- 每完成一道题，可获得 **300~500 元** 收益\n- 多劳多得，还有额外内推奖励！\n\n有任何问题，随时 @群管理员 为你解答"
            },
            { "tag": "hr" },
            {
                "tag": "action",
                "actions": [
                    {
                        "tag": "button",
                        "text": { "tag": "plain_text", "content": "📖 查看《MCP 培训文档》" },
                        "type": "primary",
                        "multi_url": {
                            "url": "https://meetchances.feishu.cn/wiki/XgJHwAGqkiAk6MkH0eXcpMX0nXg"
                        }
                    }
                ]
            }
        ]
    }
}
```

### 2.5 发送方式对比

| 方式 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| **内嵌卡片 JSON** | 无需额外操作，代码即可控制 | 修改样式需改代码重新部署 | 简单卡片、变动少 |
| **CardKit 模板** | 可视化搭建，修改无需改代码 | 需要去 CardKit 管理模板 | 复杂卡片、需频繁调整 |

**推荐方案：短期用内嵌 JSON 快速上线，后续迁移到 CardKit 模板。**

使用模板时发送方式：

```json
{
    "receive_id": "oc_xxx",
    "msg_type": "interactive",
    "content": "{\"type\":\"template\",\"data\":{\"template_id\":\"ctp_xxxx\",\"template_variable\":{\"user_id\":\"ou_xxx\",\"user_name\":\"张三\"}}}"
}
```

### 2.6 代码改造点

| 改造文件 | 改造内容 |
|----------|----------|
| `FeishuBotController.java` | 新增 `sendCardMessage()` 通用方法，支持发送 interactive 类型消息 |
| `sendTrialGroupWelcome()` | 将纯文本替换为试标群卡片 JSON |
| `sendNewbieNotice()` | 将纯文本替换为新人群卡片 JSON |
| `sendMcpTrialGroupWelcome()` | 将纯文本替换为 MCP 试标群卡片 JSON |
| `application.properties` | 新增 `feishu.card.template-id.*` 配置项（若使用模板方案） |

新增通用发送卡片方法签名：

```java
private void sendCardMessage(String receiveId, String receiveIdType, 
                              String cardJson, String token)
```

---

## 3. 改造二：新人群通知改用临时消息卡片

### 3.1 现状问题

新人入群通知采用「私聊优先 → 群内 @ 降级」的策略：

```
尝试私聊发送新人须知
    │
    ├── 成功 → 结束
    │
    └── 失败（用户未与机器人建立会话）
            │
            └── 降级为群内 @ 消息（全员可见，产生噪声）
```

**问题：** 降级的群内 @ 消息对其他群成员是噪声。

### 3.2 目标方案

使用飞书临时消息卡片 API（`POST /open-apis/ephemeral/v1/send`），在群聊中发送 **仅该新人可见** 的欢迎卡片：

```
新人入群事件
    │
    ├── 尝试私聊发送新人须知卡片
    │       │
    │       ├── 成功 → 结束
    │       │
    │       └── 失败
    │               │
    │               ▼
    │       发送临时消息卡片（仅新人可见）
    │       POST /open-apis/ephemeral/v1/send
    │       {
    │           "chat_id": "oc_xxx",
    │           "open_id": "ou_xxx",
    │           "msg_type": "interactive",
    │           "card": { ... 新人须知卡片 ... }
    │       }
    │
    └── 同时发群内欢迎（简短版，全员可见，如"欢迎 @xxx 加入"）
```

**优势：**
- 新人须知只有新人自己看得到，不打扰其他人
- 不依赖私聊权限（不需要用户先与机器人建立对话）
- 卡片上显示「仅对你可见」标识，用户体验清晰

**限制需注意：**
- 仅用户在线时可见（离线时看不到）
- 不支持转发
- 只能在群聊中使用

### 3.3 API 调用规范

```
POST https://open.feishu.cn/open-apis/ephemeral/v1/send
Authorization: Bearer {tenant_access_token}
Content-Type: application/json

{
    "chat_id": "oc_xxx",
    "open_id": "ou_xxx",
    "msg_type": "interactive",
    "card": {
        "schema": "2.0",
        "header": {
            "title": { "content": "📋 新人须知", "tag": "plain_text" },
            "template": "green"
        },
        "body": {
            "elements": [
                {
                    "tag": "markdown",
                    "content": "你好 **{{user_name}}**，欢迎加入！\n\n1. 查看群公告\n2. 阅读《Hippo项目新人指南》\n3. 有问题 @群管理员"
                }
            ]
        }
    }
}
```

---

## 4. 改造三：项目群自动创建 & 拉机器人入群

### 4.1 需求场景

当有新项目启动时，运营需要：

1. 手动创建飞书群
2. 手动将通知机器人拉入群
3. 手动在 `application.properties` 中配置新群的 `chat_id`
4. 重新部署服务

**目标：** 通过 API 自动完成步骤 1~3，减少人工操作。

### 4.2 涉及的飞书 API

| API | 端点 | 用途 |
|-----|------|------|
| 创建群聊 | `POST /open-apis/im/v1/chats` | 创建项目群 |
| 拉人入群 | `POST /open-apis/im/v1/chats/{chat_id}/members` | 向已有群中添加成员/机器人 |
| 获取群信息 | `GET /open-apis/im/v1/chats/{chat_id}` | 查询群详情 |

### 4.3 系统设计

```
┌──────────────────────────────────────────────────────────┐
│                  群组自动化管理流程                         │
│                                                          │
│  管理员触发（私聊机器人指令 / 卡片按钮）                    │
│       │                                                  │
│       ▼                                                  │
│  解析指令参数                                             │
│  - 群名称                                                │
│  - 群类型（试标/新人/MCP）                                │
│  - 初始成员列表（可选）                                   │
│       │                                                  │
│       ▼                                                  │
│  POST /open-apis/im/v1/chats                             │
│  {                                                       │
│      "name": "XXX 项目试标群",                            │
│      "chat_type": "group",                               │
│      "user_id_list": ["ou_xxx", "ou_yyy"],               │
│      "bot_id_list": ["cli_a909be3353badbc6"]              │
│  }                                                       │
│       │                                                  │
│       ▼                                                  │
│  返回 chat_id ──► 动态注册到内存映射表                     │
│       │                                                  │
│       ▼                                                  │
│  发送群初始化卡片（群公告模板）                             │
│       │                                                  │
│       ▼                                                  │
│  记录群信息到多维表格（群名、chat_id、创建时间、类型）      │
└──────────────────────────────────────────────────────────┘
```

### 4.4 动态群组映射

为了避免每次新增群组都要修改 `application.properties` 并重启服务，引入 **动态群组映射表**：

```java
public enum ChatGroupType {
    TRIAL,      // 试标群
    NEWBIE,     // 新人群
    MCP_TRIAL   // MCP 试标群
}

public class ChatGroupRegistry {
    private final Map<String, ChatGroupType> chatGroupMap = new ConcurrentHashMap<>();
    
    // 启动时从配置文件加载初始映射
    public void loadFromConfig() { ... }
    
    // 运行时动态注册新群
    public void register(String chatId, ChatGroupType type) { ... }
    
    // 根据 chat_id 查找群类型
    public ChatGroupType resolve(String chatId) { ... }
}
```

这样 `handleJoinLogic` 中的 `if-else` 链可以替换为：

```java
ChatGroupType type = chatGroupRegistry.resolve(chatId);
switch (type) {
    case TRIAL     -> { writeToBitable(...); sendTrialCardWelcome(...); }
    case NEWBIE    -> { writeToBitable(...); sendNewbieCard(...); }
    case MCP_TRIAL -> { writeToBitable(...); sendMcpTrialCardWelcome(...); }
}
```

### 4.5 权限要求

| 权限 | 说明 | 状态 |
|------|------|------|
| `im:chat` | 创建群聊、获取群信息 | **需新增** |
| `im:chat.members:write_only` | 添加/移除群成员 | **需新增** |
| `im:message:send_as_bot` | 以机器人身份发消息 | 已有 |
| `contact:user.id:readonly` | 按 open_id 查用户信息 | 已有 |

### 4.6 频率限制

| API | 限制 |
|-----|------|
| 创建群聊 | 具体见飞书文档，通常有每分钟上限 |
| 拉人入群 | 1000 次/分钟，50 次/秒 |
| 每次拉人数 | 最多 50 个用户 + 5 个机器人 |
| 群内机器人上限 | 15 个 |

---

## 5. 改造优先级与排期建议

| 阶段 | 改造内容 | 预计工作量 | 依赖项 |
|------|----------|-----------|--------|
| **P0** | 欢迎消息升级为消息卡片（内嵌 JSON 方式） | 0.5 天 | 无 |
| **P1** | 新人群通知改用临时消息卡片 | 0.5 天 | P0 完成 |
| **P2** | 迁移到 CardKit 模板方案 | 1 天 | 需在 CardKit 搭建并发布模板 |
| **P3** | 项目群自动创建 & 拉机器人 | 1.5 天 | 需申请 `im:chat` 权限 |
| **P4** | 动态群组映射表 + 多维表格持久化 | 1 天 | P3 完成 |

---

## 6. 新增 API 调用清单

| API | 方法 | 用途 | 阶段 |
|-----|------|------|------|
| `/open-apis/im/v1/messages` | POST | 发送消息卡片（`msg_type: interactive`） | P0 |
| `/open-apis/ephemeral/v1/send` | POST | 发送仅特定人可见的临时消息卡片 | P1 |
| `/open-apis/im/v1/chats` | POST | 创建群聊 | P3 |
| `/open-apis/im/v1/chats/{chat_id}/members` | POST | 拉人/机器人入群 | P3 |
| `/open-apis/im/v1/chats/{chat_id}` | GET | 获取群聊详情 | P3 |

---

## 7. 新增配置项

| 配置项 | 说明 | 阶段 |
|--------|------|------|
| `feishu.card.template-id.trial` | 试标群欢迎卡片模板 ID | P2 |
| `feishu.card.template-id.newbie` | 新人群欢迎卡片模板 ID | P2 |
| `feishu.card.template-id.mcp.trial` | MCP 试标群欢迎卡片模板 ID | P2 |
| `feishu.bot.app-id` | 机器人自身的 App ID（用于拉机器人入群） | P3 |

---

## 8. 参考资料

| 资料 | 链接 |
|------|------|
| 飞书卡片搭建工具 CardKit | https://open.feishu.cn/cardkit |
| 卡片 JSON 2.0 结构 | https://open.feishu.cn/document/uAjLw4CM/ukzMukzMukzM/feishu-cards/card-json-v2-structure |
| 发送消息卡片 | https://open.feishu.cn/document/ukTMukTMukTM/uYzM3QjL2MzN04iNzcDN/send-message-card/send-messages-using-card-json-data |
| 发送仅特定人可见的消息卡片 | https://feishu.apifox.cn/api-9020986 |
| 配置卡片交互 | https://open.feishu.cn/document/feishu-cards/configuring-card-interactions |
| 创建群聊 API | https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/reference/im-v1/chat/create |
| 拉人入群 API | https://open.feishu.cn/document/server-docs/group/chat-member/create |
