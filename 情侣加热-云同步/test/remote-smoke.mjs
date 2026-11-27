import puppeteer from "puppeteer-core";

const ACCOUNT_ID = "9e9db96a0aa466895dcaeb223d3176a7";
const TOKEN = process.env.CF_TOKEN;
const BASE = process.env.BASE || "https://hotwinter-cloud.wumisc.workers.dev";

if (!TOKEN) throw new Error("CF_TOKEN missing");

function assert(condition, message) {
  if (!condition) throw new Error(message);
  console.log("  ✓ " + message);
}

const browser = await puppeteer.connect({
  browserWSEndpoint: `wss://api.cloudflare.com/client/v4/accounts/${ACCOUNT_ID}/browser-rendering/devtools/browser?keep_alive=120000`,
  headers: { Authorization: `Bearer ${TOKEN}` },
});

try {
  const page = await browser.newPage();
  await page.goto(BASE + "/api/health", { waitUntil: "domcontentloaded", timeout: 30000 });
  assert((await page.content()).includes('"ok":true'), "线上 /api/health 返回 200");

  const result = await page.evaluate(async (base) => {
    const stamp = Date.now();
    async function api(path, body, token, method = "POST") {
      const response = await fetch(base + path, {
        method,
        headers: {
          "content-type": "application/json",
          ...(token ? { authorization: "Bearer " + token } : {}),
        },
        body: method === "GET" ? undefined : JSON.stringify(body || {}),
      });
      const json = await response.json();
      return { status: response.status, json };
    }
    const a = await api("/api/register", { email: `ra-${stamp}@example.com`, password: "correct horse battery", displayName: "远程A" });
    const b = await api("/api/register", { email: `rb-${stamp}@example.com`, password: "correct horse battery", displayName: "远程B" });
    const login = await api("/api/login", { email: `ra-${stamp}@example.com`, password: "correct horse battery" });
    const pair = await api("/api/pair/create", {}, a.json.token);
    const join = await api("/api/pair/join", { code: pair.json.code }, b.json.token);
    const me = await api("/api/me", null, a.json.token, "GET");
    return {
      registerA: a.status, registerB: b.status, login: login.status,
      pair: pair.status, join: join.status,
      code: pair.json.code,
      partnerA: me.json.couple?.partner?.displayName,
      partnerB: join.json.couple?.partner?.displayName,
      tokenA: a.json.token, tokenB: b.json.token,
    };
  }, BASE);

  assert(result.registerA === 200 && result.registerB === 200, "线上注册可用");
  assert(result.login === 200, "线上登录可用");
  assert(result.pair === 200 && /^\d{6}$/.test(result.code), "线上生成配对码");
  assert(result.join === 200 && result.partnerB === "远程A", "线上配对成功");
  assert(result.partnerA === "远程B", "线上关系双向一致");

  const pageB = await browser.newPage();
  await pageB.goto(BASE + "/api/health", { waitUntil: "domcontentloaded", timeout: 30000 });
  await page.setExtraHTTPHeaders({ Authorization: `Bearer ${result.tokenA}` });
  await pageB.setExtraHTTPHeaders({ Authorization: `Bearer ${result.tokenB}` });

  async function openSocket(p, slot) {
    return p.evaluate((base, slot) => new Promise((resolve, reject) => {
      const socket = new WebSocket(base.replace(/^https/, "wss") + "/api/ws");
      window[slot + "Sock"] = socket;
      window[slot + "Msgs"] = [];
      socket.onmessage = (event) => window[slot + "Msgs"].push(JSON.parse(event.data));
      socket.onopen = () => setTimeout(() => resolve(true), 500);
      socket.onerror = () => reject(new Error("websocket error"));
      setTimeout(() => reject(new Error("websocket timeout")), 8000);
    }), BASE, slot);
  }

  await openSocket(page, "a");
  await openSocket(pageB, "b");
  const helloA = await page.evaluate(() => window.aMsgs.find((m) => m.type === "hello"));
  const helloB = await pageB.evaluate(() => window.bMsgs.find((m) => m.type === "hello"));
  assert(helloA?.self?.name === "远程A" && helloB?.self?.name === "远程B", "线上 WebSocket 收到 hello");

  await page.evaluate(() => window.aSock.send(JSON.stringify({
    type: "status", battery: 61, charging: true, network: "wifi", heating: true,
  })));

  let status = null;
  const started = Date.now();
  while (!status && Date.now() - started < 8000) {
    status = await pageB.evaluate(() => window.bMsgs.find((m) => m.type === "status"));
    if (!status) await new Promise((resolve) => setTimeout(resolve, 100));
  }
  assert(status?.status?.battery === 61 && status.status.charging === true, "线上状态同步成功");
  assert(status.status.heating === true && status.status.network === "wifi", "线上状态字段完整");

  await pageB.evaluate(() => window.bSock.send(JSON.stringify({ type: "dm", id: "remote-dm-1", text: "远程弹幕测试" })));
  let dm = null;
  const dmStart = Date.now();
  while (!dm && Date.now() - dmStart < 8000) {
    dm = await page.evaluate(() => window.aMsgs.find((m) => m.type === "dm"));
    if (!dm) await new Promise((resolve) => setTimeout(resolve, 100));
  }
  assert(dm?.message?.id === "remote-dm-1" && dm.message?.name === "远程B", "线上弹幕送达");

  console.log("\n远程线上 smoke 通过。");
} finally {
  await browser.close();
}
