# 情侣加热 · Cloudflare 云端实时同步

这是「情侣加热」的云端后端原型：账号、情侣配对、状态同步、弹幕中继、轻提醒。

当前版本 **0.1.0**，已在本地 `wrangler dev --local` 和 Cloudflare 线上环境跑通端到端 HTTP + WebSocket 测试。

客户端使用地址（国内可达）：

```text
https://hotwinter-cloud.pages.dev
```

该地址是 Cloudflare Pages 上的反向代理，转到 Worker：

```text
https://hotwinter-cloud.wumisc.workers.dev
```

- Cloudflare 账号：`9e9db96a0aa466895dcaeb223d3176a7`
- D1 数据库：`hotwinter-cloud` / `d3c0ef63-cc3d-43dc-9b64-b664f71b4055`
- 线上验证：注册、登录、配对、WebSocket hello、状态同步、弹幕送达均通过
- `pages.dev` 实测在国内网络可解析并返回 200，WebSocket 也能正常升级

> 为什么不直接用 `workers.dev`：`*.workers.dev` 在中国大陆被 DNS 污染，实测解析到错误 IP，TLS 连接被重置；`pages.dev` 目前可达，所以用 Pages Function 做反向代理，保留 Workers + D1 + DO 的后端不变。

## 它同步什么

只同步 5 项状态：

- 电量百分比
- 是否在充电
- 网络类型（Wi-Fi / 移动网络 / 无）
- 是否正在加热
- 最后更新时间

以及：

- 弹幕文本（最多 200 字）
- “早点睡 / 喝口热水”轻提醒

**不采集 App 使用记录，不读取对方前台应用，不提供远程锁机。** 这是双方手动开启、可见、可随时关闭的状态共享，不做伴侣监控。

## 技术栈

- **Cloudflare Workers**：REST API、账号鉴权、WebSocket 升级
- **D1**：账号、会话、配对关系、配对码、限流
- **Durable Objects**：每个情侣一个实时房间，WebSocket 休眠 + 最近状态 + 最近弹幕
- **PBKDF2-SHA256**：密码哈希（100,000 次迭代）
- **Bearer Token**：会话令牌，数据库只存 SHA-256 哈希
- **恢复码**：注册时返回一次，用于无邮件验证的 MVP 账号找回

## 目录

```text
src/index.js              Worker 路由、账号、配对、WebSocket 升级
src/room.js               CoupleRoom Durable Object
migrations/0001_init.sql  账号 / 会话 / 配对 / 成员关系
migrations/0002_auth_limits.sql  登录限流
test/smoke.mjs            本地端到端测试（两个账号 + 两个 WebSocket）
test/remote-smoke.mjs     线上端到端测试（Cloudflare 网络访问 pages.dev / WebSocket）
pages-proxy/_worker.js    Pages Function 反向代理，国内客户端入口
wrangler.jsonc            Workers / D1 / DO 绑定
```

## 本地跑

```bash
npm install
npm run db:local
npm run dev
# 另开一个终端
npm test
```

`npm run dev` 默认监听 `http://127.0.0.1:8787`。`npm test` 会真实注册两个账号、配对、建立两条 WebSocket、互发状态/弹幕/提醒，然后解绑。

当前本地测试结果：

```text
1. 注册两个账号          ✓
2. 登录与恢复码          ✓
3. 配对                  ✓
4. WebSocket 实时同步    ✓
   - hello / 状态 / 弹幕 / ack / 轻提醒
5. 断线与解绑            ✓
6. 并发抢同一个配对码    ✓
   - 一人成功，另一人 409 code_used，不留下孤儿 couple
```

并发测试单独跑：

```bash
npm run test:race
```

## API

所有成功响应都是 `{ "ok": true, ... }`，失败是 `{ "ok": false, "error": "...", "message": "..." }`。

### 注册

`POST /api/register`

```json
{
  "email": "a@example.com",
  "password": "至少 8 个字符",
  "displayName": "小A"
}
```

返回：

```json
{
  "ok": true,
  "token": "...",
  "tokenExpiresAt": 1795...,
  "recoveryCode": "K7M4...",
  "user": { "id": "...", "email": "a@example.com", "displayName": "小A" }
}
```

`recoveryCode` 只返回这一次，客户端必须让用户保存。

### 登录

`POST /api/login`，body 为 `email` + `password`，返回 `token`。

### 当前账号 / 配对信息

`GET /api/me`，Header：`Authorization: Bearer <token>`。

```json
{
  "ok": true,
  "user": { "id": "...", "email": "...", "displayName": "小A" },
  "couple": {
    "id": "...",
    "joinedAt": 1795...,
    "partner": { "id": "...", "displayName": "小B" }
  }
}
```

未配对时 `couple` 为 `null`。

### 创建配对码

`POST /api/pair/create`，需要登录。返回 6 位数字，10 分钟有效：

```json
{ "ok": true, "code": "482913", "expiresAt": 1795..., "expiresIn": 600 }
```

### 输入配对码

`POST /api/pair/join`

```json
{ "code": "482913" }
```

配对成功后返回 `couple`。一个人同一时间只能绑定一个伴侣。

### 解绑

`POST /api/pair/unpair`，需要登录。会删除双方成员关系，并关闭对应 Durable Object 房间。

### 恢复码换密码

`POST /api/recover`

```json
{
  "email": "a@example.com",
  "recoveryCode": "K7M4...",
  "newPassword": "新的至少 8 位密码"
}
```

成功后旧会话全部失效，返回新的恢复码。

### WebSocket

`GET /api/ws`，Header：`Authorization: Bearer <token>`，必须带 `Upgrade: websocket`。

未配对返回 `409 no_pair`；未登录返回 `401`。

连接后服务端先发：

```json
{
  "type": "hello",
  "self": { "id": "...", "name": "小A" },
  "partner": {
    "id": "...",
    "online": true,
    "at": 1795...,
    "status": {
      "battery": 61,
      "charging": true,
      "network": "wifi",
      "heating": true,
      "at": 1795...,
      "userId": "...",
      "name": "小A"
    }
  },
  "recent": [],
  "serverTime": 1795...
}
```

客户端可以发：

```json
{ "type": "status", "battery": 61, "charging": true, "network": "wifi", "heating": true }
```

```json
{ "type": "dm", "id": "客户端消息 id", "text": "云端的弹幕也能到了" }
```

```json
{ "type": "nudge", "kind": "sleep" }
```

`kind` 只接受 `sleep` / `water`。

服务端会广播：

- `{ "type": "status", "status": {...} }`
- `{ "type": "dm", "message": {...} }`
- `{ "type": "nudge", "from": {...}, "kind": "sleep", "at": ... }`
- `{ "type": "presence", "userId": "...", "online": true|false, "at": ... }`
- `{ "type": "ack", "of": "消息 id", "at": ... }`
- `{ "type": "pong", "at": ... }`（客户端每 20–25 秒发 `{"type":"ping"}`）

服务端给发送方回 `ack`，但不把同一条弹幕再发回发送方；客户端本地先显示一份。

## 存储与生命周期

- 状态：Durable Object 存每个用户最后一条，新连接进入 `hello` 时带上
- 弹幕：最近 50 条，7 天过期，超出即裁剪
- 在线：`presence` 带时间戳；客户端以 45 秒未更新判定离线，不依赖某一帧 close 事件
- 解绑：删除 D1 成员关系 + 关闭 DO 里的所有 WebSocket

## 安全边界

- 密码不明文存储；会话 token 只存 SHA-256 哈希
- 所有接口有基础 IP 限流（哈希后的 IP，不存原始 IP）
- 弹幕上限 200 字，单条 WebSocket 消息上限 4 KB，单连接 10 秒最多 20 条
- 不记录 App 使用、不提供锁机、不读取通讯录/位置
- 当前是 **TLS 传输 + 服务端存储**；如果后续要“服务器也看不到内容”，需要在客户端加端到端加密（房间密钥配对时通过二维码带过去）

## 部署到 Cloudflare（已执行）

已使用的命令：

```bash
npx wrangler login --device
npx wrangler d1 create hotwinter-cloud
# 把输出的 database_id 填进 wrangler.jsonc
npx wrangler d1 migrations apply hotwinter-cloud --remote
npx wrangler deploy
```

线上 Worker：`hotwinter-cloud`，版本 `3bd7ed05-ac7a-42cc-ab63-7cc0e3019ee4`。

### Pages 国内入口

`workers.dev` 在国内被污染，所以额外部署了一个 Pages Function 反向代理：

```bash
npx wrangler pages project create hotwinter-cloud --production-branch main
npx wrangler pages deploy pages-proxy --project-name hotwinter-cloud --branch main
```

代理源码 `pages-proxy/_worker.js` 只做一件事：把原始请求（含 `Authorization`、`Upgrade` 和请求体）转发到 Worker，并把响应原样返回。WebSocket 101 也走这条链路。

线上验证：

```bash
CF_TOKEN=... BASE=https://hotwinter-cloud.pages.dev node test/remote-smoke.mjs
```

该测试覆盖注册、登录、配对、WebSocket `hello`、状态同步、弹幕送达，全部通过。

### 可选：自定义域名

如果以后要换自己的域名，把域名接入 Cloudflare 并在 `wrangler.jsonc` 增加：

```jsonc
"routes": [
  { "pattern": "api.example.com", "custom_domain": true }
]
```

再 `npx wrangler deploy`，然后把 Android 地址改成 `https://api.example.com`。当前不绑定域名也能用，Pages 入口已经够 MVP。

### 费用提醒

Durable Objects 有配额和计费，长期在线 WebSocket 要开启 hibernation（本实现已经使用 `ctx.acceptWebSocket`，不靠常驻循环）。上线前先在 Cloudflare 控制台确认当前套餐的 DO / D1 限额。

## Android 客户端（1.6.0-cloud）

Android 端已接入，代码在 `情侣加热-1.5.8-fix` 的 `cloud` 包：

1. `CloudApi`：OkHttp REST；`CloudSync`：WebSocket + 20 秒心跳 + 断线重连
2. `CloudPrefs`：token 用 Android Keystore AES-GCM 加密；Keystore 不可用时回退应用私有目录
3. `CloudActivity`：登录/注册/恢复码、创建/输入配对码、TA 的状态、共享开关、轻提醒、云端弹幕
4. `CloudService`：共享开启时的前台服务，通知栏显示云端连接状态
5. 云端弹幕进入现有 `DmBus`，应用内弹幕列表和悬浮弹幕都能收到，并按消息 `id` 去重

客户端 API 地址可编辑。release 默认地址已改为 `https://hotwinter-cloud.pages.dev`，versionCode 20，可直接登录使用。

**当前未做真机安装验证**：本机没有 KVM/系统镜像，只做了编译、37 个 JVM 测试、线上 Pages 代理端到端测试。

## 登录方式说明

当前主登录仍是**邮箱 + 密码 + 一次性恢复码**。这是有意保留的兜底，不依赖任何第三方账号。

评估过 Cloudflare Zero Trust Access 和 Cloudflare OAuth：

- **Cloudflare 官方 OAuth（2026 自管理 OAuth client）**：支持 Authorization Code + PKCE（S256），移动端可以不放 client secret；但它的 scope 是 Cloudflare API 权限，用户必须有 Cloudflare 账号，定位是“第三方应用访问 Cloudflare 资源”，不适合当普通情侣用户的主登录。
- **Cloudflare Zero Trust Access**：新版本支持 Managed OAuth，非浏览器客户端可以用 PKCE + 自定义 scheme，所以 Android 技术上能接；但它本质是访问网关，不是用户账号/配对系统。配对、房间、弹幕、WebSocket 仍然要 Worker + D1 + DO；免费版有 50 用户上限，默认 Cloudflare 身份源要求用户有 CF 账号，OTP 邮件依赖 Cloudflare 发信，国内可达性和品牌跳转要实测。
- **`@cloudflare/workers-oauth-provider`**：是“把你的 Worker 做成 OAuth 2.1 授权服务器”的库，不等于免登录；仍然要实现 `/authorize` 的认证和同意页。

结论：公开开源的情侣 App 继续用邮箱密码做主登录；Access 更适合内测/管理后台，或作为以后可选的高级登录通道。

## 不做什么

- 不做“对方开了什么 App、用了多久”
- 不做远程锁机
- 不做隐藏运行、后台偷读、绕过系统权限
- 不在服务端保存 App 使用记录

这套后端只做“双方手动开启的状态共享 + 弹幕中继 + 轻提醒”。
