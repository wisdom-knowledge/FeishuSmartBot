# FeishuSmartBot 项目设计文档

## 1. 项目概述

### 1.1 项目简介

FeishuSmartBot 是一个基于 Spring Boot 的飞书（Feishu/Lark）智能机器人服务，用于自动化处理飞书群组中的成员管理和消息响应。系统通过订阅飞书开放平台事件回调，实现新成员入群自动欢迎、入群记录自动写入多维表格（Bitable）、以及用户私聊自动回复引导等功能。

### 1.2 项目目标

- **自动化入群管理**：新成员加入指定群聊时，自动发送欢迎消息并记录入群信息
- **多场景支持**：支持试标群、新人群、MCP 试标群三种业务场景，各自有独立的欢迎文案和记录表格
- **私聊自动回复**：用户向机器人发送私信时，自动回复新人指引信息
- **数据记录**：将入群人员信息自动写入飞书多维表格，便于运营统计

### 1.3 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 编程语言 | Java | 21 |
| 框架 | Spring Boot | 4.0.2 |
| 构建工具 | Maven | 3.9.12（Wrapper） |
| JSON 处理 | Fastjson2 | 2.0.60 |
| Web 服务器 | 内嵌 Tomcat | 由 Spring Boot 管理 |
| 反向代理 | Nginx | 系统包管理 |
| 部署方式 | systemd 服务 | - |

---

## 2. 系统架构

### 2.1 整体架构图

```
┌─────────────────────┐
│   飞书开放平台        │
│  (事件订阅 & API)    │
└─────────┬───────────┘
          │  HTTPS 事件推送
          ▼
┌─────────────────────┐
│       Nginx          │
│   :80 反向代理       │
│  /feishu_callback    │
└─────────┬───────────┘
          │  HTTP 转发
          ▼
┌──────────────────────────────────────────────┐
│          Spring Boot 应用 (:8080)             │
│                                              │
│  ┌────────────────────────────────────────┐  │
│  │        FeishuBotController             │  │
│  │                                        │  │
│  │  POST /feishu_callback                 │  │
│  │    ├─ URL 验证 (challenge)             │  │
│  │    ├─ 入群事件处理 (异步)              │  │
│  │    └─ 私聊消息处理 (异步)              │  │
│  └────────────────────────────────────────┘  │
│                                              │
│  ┌──────────────┐  ┌──────────────────────┐  │
│  │ RestTemplate  │  │ ExecutorService      │  │
│  │ (HTTP 客户端) │  │ (15 线程异步池)      │  │
│  └──────────────┘  └──────────────────────┘  │
└──────────┬───────────────────────────────────┘
           │  HTTPS API 调用
           ▼
┌──────────────────────────────────────┐
│       飞书开放平台 API                │
│                                      │
│  ├─ 认证: tenant_access_token        │
│  ├─ 消息: im/v1/messages             │
│  ├─ 群组: im/v1/chats/members        │
│  ├─ 通讯录: contact/v3/users         │
│  └─ 多维表格: bitable/v1/apps/...    │
└──────────────────────────────────────┘
```

### 2.2 项目结构

```
FeishuSmartBot/
├── pom.xml                          # Maven 项目配置
├── mvnw / mvnw.cmd                  # Maven Wrapper 脚本
├── .mvn/wrapper/                    # Maven Wrapper 配置
├── deploy/
│   ├── deploy.sh                    # 服务器部署脚本
│   └── setup-nginx-https.sh         # Nginx 反向代理配置脚本
└── src/
    ├── main/
    │   ├── java/com/smartknowledgetechnology/feishusmartbot/
    │   │   ├── FeishuSmartBotApplication.java    # Spring Boot 入口
    │   │   └── FeishuBotController.java          # 核心业务控制器
    │   └── resources/
    │       └── application.properties            # 应用配置
    └── test/
        └── java/com/smartknowledgetechnology/feishusmartbot/
            └── FeishuSmartBotApplicationTests.java  # 上下文测试
```

---

## 3. 核心模块设计

### 3.1 事件回调入口 (`POST /feishu_callback`)

飞书开放平台通过 HTTP POST 将事件推送到此端点，控制器负责解析并分发处理。

**请求处理流程：**

```
收到 POST 请求
    │
    ├── 包含 challenge 字段？ ──是──► 原样返回 challenge（URL 验证）
    │
    ├── 解析 header.event_type
    │
    ├── im.chat.member.user.added_v1  ──► 异步提交 handleJoinLogic()
    │
    ├── im.message.receive_v1 (p2p)   ──► 异步提交 replyGuideToUser()
    │
    └── 立即返回 {"msg": "ok"}
```

**设计要点：**

- **同步响应，异步处理**：回调接口立即返回 `200 OK`，业务逻辑通过 `ExecutorService`（固定 15 线程池）异步执行，避免飞书超时重试
- **Challenge 验证**：飞书注册事件订阅 URL 时，会发送包含 `challenge` 字段的验证请求，需原样返回

### 3.2 入群事件处理 (`handleJoinLogic`)

当监听到 `im.chat.member.user.added_v1` 事件时，根据群 ID 匹配不同的业务场景：

| 群组类型 | 群 ID 配置项 | 处理动作 |
|----------|-------------|----------|
| 试标群 | `feishu.chat.target-id.trial` | 写入试标表格 + 发送试标群欢迎消息 |
| 新人群 | `feishu.chat.target-id.newbie` | 写入新人表格 + 发送新人须知（优先私聊，降级群内 @） |
| MCP 试标群 | `feishu.chat.target-id.mcp.trial` | 写入 MCP 表格 + 发送 MCP 群欢迎消息 |

### 3.3 用户名获取策略 (`getUserName`)

获取用户真实姓名采用**三级降级策略**：

```
1. 等待 2 秒（飞书服务端同步延迟）
        │
        ▼
2. 遍历群成员列表（分页，每页 100）
   匹配 open_id → 返回 name
        │ 失败
        ▼
3. 调用通讯录接口（仅内部用户有效）
   GET /contact/v3/users/{open_id}
        │ 失败
        ▼
4. 返回兜底值："未获取到用户名"
```

**设计考量：**

- 群成员列表优先：可获取外部协作者名称
- 通讯录接口补充：覆盖内部人员场景
- 2 秒延迟：解决飞书后端成员数据同步时延问题

### 3.4 消息发送

系统支持两种消息发送方式：

| 方式 | 接收方标识 | 使用场景 |
|------|-----------|----------|
| 群消息 | `receive_id_type=chat_id` | 试标群欢迎、MCP 群欢迎、新人群降级欢迎 |
| 私聊消息 | `receive_id_type=open_id` | 新人须知私聊、私信自动回复 |

**降级机制（新人群）**：优先通过私聊发送新人须知，若私聊失败（用户未与机器人建立会话），则降级为群内 @ 消息。

### 3.5 多维表格记录 (`writeToBitable`)

每次入群事件触发后，将以下字段写入对应的多维表格：

| 字段名 | 值 | 说明 |
|--------|-----|------|
| 人员名称 | `getUserName()` 返回值 | 用户真实姓名 |
| OpenID | 用户 open_id | 飞书用户唯一标识 |
| 入群时间 | `System.currentTimeMillis()` | 毫秒时间戳 |

三个业务场景各自写入独立的表格（通过不同的 `table-id` 区分），共享同一个多维表格应用（`app-token`）。

### 3.6 认证机制

使用飞书 **自建应用** 的 `tenant_access_token`（租户令牌）模式：

```
POST https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal
Body: { "app_id": "...", "app_secret": "..." }
Response: { "tenant_access_token": "..." }
```

- 每次业务处理前动态获取 Token
- Token 有效期由飞书控制（通常 2 小时），当前实现不做缓存

---

## 4. 飞书 API 调用清单

| API | 方法 | 用途 |
|-----|------|------|
| `/open-apis/auth/v3/tenant_access_token/internal` | POST | 获取租户访问令牌 |
| `/open-apis/im/v1/chats/{chat_id}/members` | GET | 分页获取群成员列表（含外部人员） |
| `/open-apis/contact/v3/users/{user_id}` | GET | 获取通讯录用户信息 |
| `/open-apis/im/v1/messages` | POST | 发送消息（群消息/私聊） |
| `/open-apis/bitable/v1/apps/{app_token}/tables/{table_id}/records` | POST | 新增多维表格记录 |

---

## 5. 配置项说明

| 配置项 | 说明 | 示例 |
|--------|------|------|
| `spring.application.name` | 应用名称 | `FeishuSmartBot` |
| `feishu.app.id` | 飞书应用 App ID | `cli_xxx` |
| `feishu.app.secret` | 飞书应用密钥 | `***` |
| `feishu.bitable.app-token` | 多维表格应用 Token | `AMy0bxxx` |
| `feishu.bitable.table-id.trial` | 试标群对应表格 ID | `tblxxx` |
| `feishu.bitable.table-id.newbie` | 新人群对应表格 ID | `tblxxx` |
| `feishu.bitable.table-id.mcp.trial` | MCP 试标群对应表格 ID | `tblxxx` |
| `feishu.chat.target-id.trial` | 试标群群 ID | `oc_xxx` |
| `feishu.chat.target-id.newbie` | 新人群群 ID | `oc_xxx` |
| `feishu.chat.target-id.mcp.trial` | MCP 试标群群 ID | `oc_xxx` |

---

## 6. 部署架构

### 6.1 部署拓扑

```
┌──────────────────────────────────────────────┐
│              Linux 服务器                     │
│              115.191.36.7                     │
│                                              │
│  ┌──────────────┐     ┌───────────────────┐  │
│  │    Nginx      │────►│  Spring Boot App  │  │
│  │   :80         │     │  :8080            │  │
│  │               │     │                   │  │
│  │  /feishu_     │     │  feishu-smart-    │  │
│  │  callback     │     │  bot.jar          │  │
│  │  (反向代理)   │     │  (systemd 管理)   │  │
│  └──────────────┘     └───────────────────┘  │
│                                              │
│  JAR 路径: /opt/feishu-bot/feishu-smart-bot.jar  │
│  服务名称: feishu-bot.service                │
│  JDK: Adoptium Temurin 21                    │
└──────────────────────────────────────────────┘
```

### 6.2 部署步骤

1. **构建 JAR**：本地执行 `mvnw.cmd package`（Windows）或 `./mvnw package`（Linux/Mac）
2. **上传 JAR**：将 `target/feishu-smart-bot-0.0.1-SNAPSHOT.jar` 上传至服务器 `/opt/feishu-bot/feishu-smart-bot.jar`
3. **执行部署脚本**：`bash deploy/deploy.sh`（自动安装 Java 21、配置 systemd 服务）
4. **配置 Nginx**：`bash deploy/setup-nginx-https.sh`（配置反向代理）
5. **飞书平台配置**：将事件回调 URL 设置为 `http://115.191.36.7/feishu_callback`

### 6.3 运维命令

```bash
# 查看服务状态
systemctl status feishu-bot

# 重启服务
systemctl restart feishu-bot

# 查看运行日志
journalctl -u feishu-bot -f

# 停止服务
systemctl stop feishu-bot
```

---

## 7. 数据流设计

### 7.1 入群事件数据流

```
飞书用户加入群聊
       │
       ▼
飞书平台推送事件 ──────────────────────────────────────────────┐
  {                                                           │
    "header": {                                               │
      "event_type": "im.chat.member.user.added_v1"            │
    },                                                        │
    "event": {                                                │
      "chat_id": "oc_xxx",                                    │
      "users": [{ "user_id": { "open_id": "ou_xxx" } }]      │
    }                                                         │
  }                                                           │
       │                                                      │
       ▼                                                      │
  Controller 接收 & 立即返回 200                               │
       │                                                      │
       ▼  (异步线程)                                           │
  获取 tenant_access_token                                    │
       │                                                      │
       ▼                                                      │
  获取用户名（群成员列表 → 通讯录 → 兜底）                    │
       │                                                      │
       ├──► 写入多维表格（人员名称 + OpenID + 入群时间）       │
       │                                                      │
       └──► 发送欢迎消息（按群类型选择文案和发送方式）         │
                                                              │
```

### 7.2 私聊消息数据流

```
用户发送私信给机器人
       │
       ▼
飞书平台推送事件
  event_type: "im.message.receive_v1"
  chat_type: "p2p"
       │
       ▼
  Controller 接收 & 立即返回 200
       │
       ▼  (异步线程)
  获取 token + 用户名
       │
       ▼
  回复新人须知引导消息
```

---

## 8. 业务文案

### 8.1 试标群欢迎消息

> @用户 欢迎通过面试，进入试标阶段，请务必阅读以下内容：
> 1. 进入试标群并查看群公告
> 2. 仔细阅读《Hippo项目指南》：[链接]

### 8.2 新人群通知

**私聊版本：**

> 你好 {用户名}，欢迎加入新人群！
> 新人须知：
> 1. 请收看群公告
> 2. 查阅群公告中的《Hippo项目新人指南》
> 3. 有问题可@群管理员咨询

**群内 @ 降级版本：**

> @用户 你好，欢迎加入 Hippo 新人考试群。
> 请优先查阅群公告，知悉群内相关要求...

### 8.3 MCP 试标群欢迎消息

> @用户 欢迎加入 MCP 试标群！
> 快速上手指南：
> 先看群公告 → 了解群规与注意事项
> 再读《MCP 培训文档》：[链接] → 掌握考核与操作流程
> ...

### 8.4 私信自动回复

> 你好 {用户名}，欢迎加入新人群！
> 新人须知：
> 1. 请收看群公告
> 2. 查阅群公告中的《Hippo项目新人指南》
> 3. 根据指南参加考试，通过进入试标
> 4. 有问题可@管理员

---

## 9. 安全注意事项

1. **密钥管理**：当前 `application.properties` 中包含明文的飞书应用密钥（`app.secret`），建议：
   - 使用环境变量注入敏感配置
   - 使用 Spring Cloud Config 或 Vault 等密钥管理方案
   - 确保 `.gitignore` 排除含密钥的配置文件

2. **事件验证**：当前未实现飞书事件签名验证（`Encrypt Key` / `Verification Token`），存在被伪造回调的风险，建议添加签名校验逻辑

3. **Token 缓存**：`tenant_access_token` 每次业务调用都重新获取，建议增加缓存（有效期内复用），减少 API 调用频率

4. **HTTPS**：部署脚本中 Nginx 仅配置了 HTTP 80 端口，飞书事件订阅要求回调地址支持 HTTPS，建议配置 SSL 证书

---

## 10. 可能的优化方向

| 优化项 | 说明 | 优先级 |
|--------|------|--------|
| 事件去重 | 飞书可能重复推送事件，需根据 `event_id` 做幂等处理 | 高 |
| Token 缓存 | 缓存 `tenant_access_token`，避免频繁请求认证接口 | 高 |
| 事件签名校验 | 验证回调请求来源合法性，防止伪造攻击 | 高 |
| HTTPS 支持 | 配置 SSL/TLS 证书，满足飞书安全要求 | 高 |
| 日志框架 | 用 SLF4J/Logback 替代 `System.out/err.println` | 中 |
| 分层架构 | 将业务逻辑从 Controller 拆分到 Service 层 | 中 |
| 异常处理 | 完善全局异常处理与重试机制 | 中 |
| 配置外部化 | 敏感配置通过环境变量或配置中心注入 | 中 |
| 健康检查 | 添加 Spring Boot Actuator 监控端点 | 低 |
| 容器化 | 添加 Dockerfile，支持 Docker/K8s 部署 | 低 |
