const STATUS_KEY_PREFIX = "status:";
const PRESENCE_KEY_PREFIX = "presence:";
const RECENT_KEY = "recent";
const MAX_RECENT = 50;
const RETENTION_MS = 7 * 24 * 60 * 60 * 1000;
const MAX_TEXT = 200;
const MAX_MESSAGE_BYTES = 4096;
const PRESENCE_TTL_MS = 45_000;
const OFFLINE_GRACE_MS = 4_000;

/**
 * 一个情侣一个房间。只保存：
 * - 双方最后一条五项状态（电量 / 充电 / 网络 / 是否加热 / 时间）
 * - 最近 50 条弹幕，7 天过期
 * - 在线标记
 *
 * 不保存 App 使用记录、不提供远程锁机。
 */
export class CoupleRoom {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
    this.rate = new Map();
  }

  async fetch(request) {
    const url = new URL(request.url);
    if (url.pathname.endsWith("/internal/close")) {
      return this.closeAll();
    }

    if (request.headers.get("Upgrade") !== "websocket") {
      return new Response("expected websocket", { status: 426 });
    }

    const userId = request.headers.get("x-user-id") || "";
    const partnerId = request.headers.get("x-partner-id") || "";
    const name = decodeURIComponent(request.headers.get("x-user-name") || "TA");
    if (!userId || !partnerId) {
      return new Response("missing identity", { status: 400 });
    }

    const { 0: client, 1: server } = new WebSocketPair();
    this.ctx.acceptWebSocket(server);
    server.serializeAttachment({ userId, partnerId, name, joinedAt: Date.now() });

    await this.rememberMember(userId);
    await this.markPresence(userId, true, name);
    await this.sendHello(server, userId, partnerId, name);
    this.broadcastPresence(userId, name, true, server);
    return new Response(null, { status: 101, webSocket: client });
  }

  async webSocketMessage(ws, message) {
    const attachment = ws.deserializeAttachment() || {};
    const userId = attachment.userId;
    const name = attachment.name || "TA";
    if (!userId) {
      this.safeSend(ws, { type: "error", error: "bad_identity", message: "连接身份丢失，请重连" });
      return;
    }

    if (typeof message !== "string") {
      this.safeSend(ws, { type: "error", error: "bad_type", message: "只接受文本 JSON" });
      return;
    }
    if (message.length > MAX_MESSAGE_BYTES) {
      this.safeSend(ws, { type: "error", error: "too_large", message: "消息太长了" });
      return;
    }
    if (!this.allow(userId)) {
      this.safeSend(ws, { type: "error", error: "rate_limited", message: "慢一点，消息太密了" });
      return;
    }

    let payload;
    try {
      payload = JSON.parse(message);
    } catch {
      this.safeSend(ws, { type: "error", error: "bad_json", message: "消息不是合法 JSON" });
      return;
    }
    if (!payload || typeof payload !== "object") {
      this.safeSend(ws, { type: "error", error: "bad_payload", message: "消息格式不对" });
      return;
    }

    switch (payload.type) {
      case "status":
        await this.onStatus(ws, userId, name, payload);
        return;
      case "dm":
        await this.onDanmaku(ws, userId, name, payload);
        return;
      case "nudge":
        this.onNudge(ws, userId, name, payload);
        return;
      case "ping":
        this.markPresence(userId, true, name).catch(() => {});
        this.safeSend(ws, { type: "pong", at: Date.now() });
        return;
      default:
        this.safeSend(ws, { type: "error", error: "unknown_type", message: "未知消息类型" });
    }
  }

  async webSocketClose(ws, code, reason, wasClean) {
    const attachment = ws.deserializeAttachment() || {};
    if (attachment.userId) {
      // 先尽力即时广播；有些运行时在 close 阶段会丢弃发送，所以再用 alarm 兜底。
      this.broadcastPresence(attachment.userId, attachment.name || "TA", false, ws);
      await this.ctx.storage.setAlarm(Date.now() + OFFLINE_GRACE_MS).catch(() => {});
    }
  }

  async webSocketError(ws, error) {
    const attachment = ws.deserializeAttachment() || {};
    if (attachment.userId) {
      this.broadcastPresence(attachment.userId, attachment.name || "TA", false, ws);
      await this.ctx.storage.setAlarm(Date.now() + OFFLINE_GRACE_MS).catch(() => {});
    }
    try {
      ws.close(1011, "socket error");
    } catch {
      // 已经关闭
    }
  }

  async alarm() {
    const members = (await this.ctx.storage.get("members")) || [];
    if (!Array.isArray(members)) return;
    for (const userId of members) {
      if (this.socketsFor(userId).length > 0) continue;
      const presence = await this.ctx.storage.get(PRESENCE_KEY_PREFIX + userId);
      if (presence && presence.online) {
        const name = presence.name || "TA";
        await this.markPresence(userId, false, name);
        this.broadcastPresence(userId, name, false);
      }
    }
  }

  async onStatus(ws, userId, name, payload) {
    const status = normalizeStatus(payload);
    if (!status) {
      this.safeSend(ws, { type: "error", error: "bad_status", message: "状态字段不合法" });
      return;
    }
    const record = { ...status, userId, name, at: Date.now() };
    await this.ctx.storage.put(STATUS_KEY_PREFIX + userId, record);
    await this.markPresence(userId, true, name);
    this.broadcastExcept(userId, { type: "status", status: record });
  }

  async onDanmaku(ws, userId, name, payload) {
    let text = String(payload.text || "").replace(/\s+/g, " ").trim();
    if (!text) return;
    if (text.length > MAX_TEXT) text = text.slice(0, MAX_TEXT);
    const id = typeof payload.id === "string" && payload.id.length <= 64
      ? payload.id
      : crypto.randomUUID();
    const record = { id, userId, name, text, at: Date.now() };

    let recent = (await this.ctx.storage.get(RECENT_KEY)) || [];
    if (!Array.isArray(recent)) recent = [];
    const cutoff = Date.now() - RETENTION_MS;
    recent = recent.filter((item) => item && Number(item.at) > cutoff);
    recent.push(record);
    if (recent.length > MAX_RECENT) recent = recent.slice(recent.length - MAX_RECENT);
    await this.ctx.storage.put(RECENT_KEY, recent);

    this.broadcastExcept(userId, { type: "dm", message: record });
    this.safeSend(ws, { type: "ack", of: id, at: record.at });
  }

  onNudge(ws, userId, name, payload) {
    const kind = payload.kind === "water" ? "water" : payload.kind === "sleep" ? "sleep" : "";
    if (!kind) {
      this.safeSend(ws, { type: "error", error: "bad_nudge", message: "提醒类型不对" });
      return;
    }
    this.broadcastExcept(userId, {
      type: "nudge",
      from: { id: userId, name },
      kind,
      at: Date.now(),
    });
    this.safeSend(ws, { type: "ack", of: "nudge:" + kind, at: Date.now() });
  }

  async sendHello(ws, userId, partnerId, name) {
    const partnerStatus = await this.ctx.storage.get(STATUS_KEY_PREFIX + partnerId);
    const partnerPresence = await this.ctx.storage.get(PRESENCE_KEY_PREFIX + partnerId);
    let recent = (await this.ctx.storage.get(RECENT_KEY)) || [];
    if (!Array.isArray(recent)) recent = [];
    const cutoff = Date.now() - RETENTION_MS;
    recent = recent.filter((item) => item && Number(item.at) > cutoff);
    const presenceAt = partnerPresence ? Number(partnerPresence.at) || 0 : 0;
    const online = Boolean(partnerPresence && partnerPresence.online)
      && Date.now() - presenceAt < PRESENCE_TTL_MS;
    this.safeSend(ws, {
      type: "hello",
      self: { id: userId, name },
      partner: {
        id: partnerId,
        online,
        at: presenceAt,
        status: partnerStatus || null,
      },
      recent,
      serverTime: Date.now(),
    });
  }

  broadcastPresence(userId, name, online, except) {
    const payload = { type: "presence", userId, name, online, at: Date.now() };
    for (const socket of this.ctx.getWebSockets()) {
      if (socket === except) continue;
      let attachment;
      try {
        attachment = socket.deserializeAttachment() || {};
      } catch {
        continue;
      }
      if (attachment.userId !== userId) this.safeSend(socket, payload);
    }
  }

  broadcastExcept(userId, payload) {
    for (const socket of this.ctx.getWebSockets()) {
      let attachment;
      try {
        attachment = socket.deserializeAttachment() || {};
      } catch {
        continue;
      }
      if (attachment.userId !== userId) this.safeSend(socket, payload);
    }
  }

  safeSend(socket, payload) {
    try {
      socket.send(JSON.stringify(payload));
    } catch {
      // 断开中的 socket 不重复报错
    }
  }

  async markPresence(userId, online, name) {
    try {
      await this.ctx.storage.put(PRESENCE_KEY_PREFIX + userId, {
        online,
        name: name || "TA",
        at: Date.now(),
      });
    } catch {
      // presence 写失败不影响消息
    }
  }

  async rememberMember(userId) {
    try {
      const members = (await this.ctx.storage.get("members")) || [];
      const list = Array.isArray(members) ? members : [];
      if (!list.includes(userId)) {
        list.push(userId);
        await this.ctx.storage.put("members", list);
      }
    } catch {
      // 成员列表写失败不阻塞连接
    }
  }

  socketsFor(userId) {
    const list = [];
    for (const socket of this.ctx.getWebSockets()) {
      let attachment;
      try {
        attachment = socket.deserializeAttachment() || {};
      } catch {
        continue;
      }
      if (attachment.userId === userId) list.push(socket);
    }
    return list;
  }

  allow(userId) {
    const current = Date.now();
    const state = this.rate.get(userId) || { started: current, count: 0 };
    if (current - state.started > 10_000) {
      state.started = current;
      state.count = 0;
    }
    state.count += 1;
    this.rate.set(userId, state);
    return state.count <= 20;
  }

  closeAll() {
    for (const socket of this.ctx.getWebSockets()) {
      try {
        socket.close(1000, "unpaired");
      } catch {
        // 已经关闭
      }
    }
    return new Response(JSON.stringify({ ok: true }), {
      headers: { "content-type": "application/json" },
    });
  }
}

function normalizeStatus(input) {
  const battery = Number(input.battery);
  if (!Number.isFinite(battery)) return null;
  const network = input.network === "wifi" || input.network === "mobile" ? input.network : "none";
  return {
    battery: Math.max(0, Math.min(100, Math.round(battery))),
    charging: input.charging === true,
    network,
    heating: input.heating === true,
    at: Date.now(),
  };
}
