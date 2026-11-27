import WebSocket from "ws";

const BASE = process.env.BASE || "http://127.0.0.1:8787";
const WS_BASE = BASE.replace(/^http/, "ws");

function assert(condition, message) {
  if (!condition) throw new Error(message);
  console.log("  ✓ " + message);
}

async function api(path, body, token, method = "POST") {
  const response = await fetch(BASE + path, {
    method,
    headers: {
      "content-type": "application/json",
      ...(token ? { authorization: "Bearer " + token } : {}),
    },
    body: method === "GET" ? undefined : JSON.stringify(body || {}),
  });
  const json = await response.json();
  if (!response.ok) {
    throw new Error(`${path} ${response.status}: ${JSON.stringify(json)}`);
  }
  return json;
}

function connect(token) {
  return new Promise((resolve, reject) => {
    const socket = new WebSocket(`${WS_BASE}/api/ws`, {
      headers: { authorization: "Bearer " + token },
    });
    const messages = [];
    const waiters = [];
    socket.on("message", (data) => {
      const message = JSON.parse(String(data));
      if (process.env.TRACE) console.log("RECV", token.slice(0, 6), JSON.stringify(message));
      const waiter = waiters.shift();
      if (waiter) waiter.resolve(message);
      else messages.push(message);
    });
    socket.on("open", () => {
      resolve({
        socket,
        messages,
        next(predicate, timeoutMs = 8000) {
          const ready = messages.find(predicate);
          if (ready) {
            messages.splice(messages.indexOf(ready), 1);
            return Promise.resolve(ready);
          }
          return new Promise((res, rej) => {
            const timer = setTimeout(() => {
              const index = waiters.indexOf(entry);
              if (index >= 0) waiters.splice(index, 1);
              rej(new Error("timeout waiting for websocket message"));
            }, timeoutMs);
            const entry = {
              resolve(message) {
                clearTimeout(timer);
                res(message);
              },
            };
            waiters.push(entry);
          }).then((message) => (predicate(message) ? message : this.next(predicate, timeoutMs)));
        },
        close() {
          socket.close();
        },
      });
    });
    socket.on("error", reject);
  });
}

async function waitFor(predicate, timeoutMs = 15000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    if (await predicate()) return true;
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  return false;
}

async function main() {
  const stamp = Date.now();
  const emailA = `a-${stamp}@example.com`;
  const emailB = `b-${stamp}@example.com`;

  console.log("1. 注册两个账号");
  const a = await api("/api/register", {
    email: emailA,
    password: "correct horse battery",
    displayName: "小A",
  });
  const b = await api("/api/register", {
    email: emailB,
    password: "correct horse battery",
    displayName: "小B",
  });
  assert(a.token && a.recoveryCode && b.token && b.recoveryCode, "账号、Token、恢复码都已返回");

  console.log("2. 登录与恢复码");
  const login = await api("/api/login", { email: emailA, password: "correct horse battery" });
  assert(login.user.id === a.user.id, "邮箱密码可以登录");
  const recovered = await api("/api/recover", {
    email: emailB,
    recoveryCode: b.recoveryCode,
    newPassword: "new password 123",
  });
  assert(recovered.recoveryCode && recovered.recoveryCode !== b.recoveryCode, "恢复码可换新密码并轮换");
  const loginB = await api("/api/login", { email: emailB, password: "new password 123" });
  b.token = loginB.token;

  console.log("3. 配对");
  const pair = await api("/api/pair/create", {}, a.token);
  assert(/^\d{6}$/.test(pair.code), "生成 6 位配对码");
  const joined = await api("/api/pair/join", { code: pair.code }, b.token);
  assert(joined.couple.partner.displayName === "小A", "B 看到伴侣是 A");
  const meA = await api("/api/me", null, a.token, "GET");
  const meB = await api("/api/me", null, b.token, "GET");
  assert(meA.couple.partner.displayName === "小B" && meB.couple.partner.displayName === "小A", "双方配对关系一致");

  console.log("4. WebSocket 实时同步");
  const wa = await connect(a.token);
  const wb = await connect(b.token);
  const helloA = await wa.next((m) => m.type === "hello");
  const helloB = await wb.next((m) => m.type === "hello");
  assert(helloA.self.name === "小A" && helloB.self.name === "小B", "双方收到 hello");

  wa.socket.send(JSON.stringify({
    type: "status",
    battery: 61,
    charging: true,
    network: "wifi",
    heating: true,
  }));
  const status = await wb.next((m) => m.type === "status");
  assert(status.status.battery === 61 && status.status.charging === true, "B 收到 A 的状态");
  assert(status.status.heating === true && status.status.network === "wifi", "状态字段完整");
  assert(!("app" in status.status) && !("foregroundApp" in status.status), "状态里没有 App 使用字段");

  wb.socket.send(JSON.stringify({
    type: "dm",
    id: "dm-test-1",
    text: "云端的弹幕也能到了",
  }));
  const dm = await wa.next((m) => m.type === "dm");
  assert(dm.message.id === "dm-test-1" && dm.message.name === "小B", "A 收到 B 的弹幕并带上发送者");
  const ack = await wb.next((m) => m.type === "ack" && m.of === "dm-test-1");
  assert(Boolean(ack), "发送方收到 ack");

  wb.socket.send(JSON.stringify({ type: "nudge", kind: "sleep" }));
  const nudge = await wa.next((m) => m.type === "nudge");
  assert(nudge.from.name === "小B" && nudge.kind === "sleep", "轻提醒可以送达");

  console.log("5. 断线与解绑");
  wb.socket.terminate();
  wa.socket.send(JSON.stringify({ type: "ping" }));
  const pong = await wa.next((m) => m.type === "pong");
  assert(Boolean(pong), "B 断线后 A 的连接仍然可用");

  const unpair = await api("/api/pair/unpair", {}, a.token);
  assert(unpair.ok, "可以解绑");
  const meAfter = await api("/api/me", null, a.token, "GET");
  assert(meAfter.couple === null, "解绑后关系清空");

  console.log("\n全部通过。");
}

main().catch((error) => {
  console.error("\n测试失败：", error.message);
  process.exit(1);
});
