import { CoupleRoom } from "./room.js";

export { CoupleRoom };

const SESSION_DAYS = 30;
const PAIR_MINUTES = 10;
const PASSWORD_ITERATIONS = 100000;
const RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

const JSON_HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
};

function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: JSON_HEADERS });
}

function fail(error, message, status = 400) {
  return json({ ok: false, error, message }, status);
}

function now() {
  return Date.now();
}

function bytesToB64url(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function b64urlToBytes(value) {
  const pad = value.length % 4 === 0 ? "" : "=".repeat(4 - (value.length % 4));
  const binary = atob(value.replace(/-/g, "+").replace(/_/g, "/") + pad);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

function constantTimeEqual(a, b) {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
  return diff === 0;
}

async function sha256Bytes(text) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return new Uint8Array(digest);
}

async function hashToken(token) {
  return bytesToB64url(await sha256Bytes(token));
}

async function hashPassword(password) {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(password),
    "PBKDF2",
    false,
    ["deriveBits"],
  );
  const bits = await crypto.subtle.deriveBits(
    { name: "PBKDF2", salt, iterations: PASSWORD_ITERATIONS, hash: "SHA-256" },
    key,
    256,
  );
  return `v1$${PASSWORD_ITERATIONS}$${bytesToB64url(salt)}$${bytesToB64url(new Uint8Array(bits))}`;
}

async function verifyPassword(password, stored) {
  const parts = String(stored || "").split("$");
  if (parts.length !== 4 || parts[0] !== "v1") return false;
  const iterations = Number(parts[1]);
  if (!Number.isInteger(iterations) || iterations < 10000 || iterations > 500000) return false;
  let salt;
  let expected;
  try {
    salt = b64urlToBytes(parts[2]);
    expected = b64urlToBytes(parts[3]);
  } catch {
    return false;
  }
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(password),
    "PBKDF2",
    false,
    ["deriveBits"],
  );
  const bits = await crypto.subtle.deriveBits(
    { name: "PBKDF2", salt, iterations, hash: "SHA-256" },
    key,
    256,
  );
  return constantTimeEqual(new Uint8Array(bits), expected);
}

function randomToken() {
  return bytesToB64url(crypto.getRandomValues(new Uint8Array(32)));
}

function randomRecoveryCode() {
  const bytes = crypto.getRandomValues(new Uint8Array(10));
  let out = "";
  for (let i = 0; i < 10; i++) out += RECOVERY_ALPHABET[bytes[i] % RECOVERY_ALPHABET.length];
  return out;
}

function randomPairCode() {
  return String(crypto.getRandomValues(new Uint32Array(1))[0] % 1000000).padStart(6, "0");
}

function normalizeEmail(value) {
  return String(value || "").trim().toLowerCase();
}

function validEmail(email) {
  return email.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function validPassword(password) {
  return typeof password === "string" && password.length >= 8 && password.length <= 72;
}

function cleanName(value, fallback) {
  const name = String(value || "").trim().replace(/\s+/g, " ").slice(0, 20);
  return name || fallback;
}

async function readJson(request) {
  try {
    const body = await request.json();
    return body && typeof body === "object" ? body : {};
  } catch {
    return null;
  }
}

async function createSession(env, userId) {
  const token = randomToken();
  const tokenHash = await hashToken(token);
  const createdAt = now();
  const expiresAt = createdAt + SESSION_DAYS * 24 * 60 * 60 * 1000;
  await env.DB.prepare(
    "INSERT INTO sessions (id, user_id, token_hash, created_at, expires_at) VALUES (?, ?, ?, ?, ?)",
  )
    .bind(crypto.randomUUID(), userId, tokenHash, createdAt, expiresAt)
    .run();
  return { token, expiresAt };
}

async function requireUser(request, env) {
  const header = request.headers.get("Authorization") || "";
  if (!header.startsWith("Bearer ")) return null;
  const token = header.slice(7).trim();
  if (!token || token.length > 200) return null;
  const tokenHash = await hashToken(token);
  const row = await env.DB.prepare(
    `SELECT u.id, u.email, u.display_name, s.expires_at
       FROM sessions s JOIN users u ON u.id = s.user_id
      WHERE s.token_hash = ?`,
  )
    .bind(tokenHash)
    .first();
  if (!row) return null;
  if (Number(row.expires_at) <= now()) {
    await env.DB.prepare("DELETE FROM sessions WHERE token_hash = ?").bind(tokenHash).run();
    return null;
  }
  return {
    id: row.id,
    email: row.email,
    displayName: row.display_name,
    tokenHash,
  };
}

async function coupleInfo(env, userId) {
  const membership = await env.DB.prepare(
    "SELECT couple_id, joined_at FROM memberships WHERE user_id = ?",
  )
    .bind(userId)
    .first();
  if (!membership) return null;
  const partner = await env.DB.prepare(
    `SELECT m.user_id, u.display_name
       FROM memberships m JOIN users u ON u.id = m.user_id
      WHERE m.couple_id = ? AND m.user_id <> ?`,
  )
    .bind(membership.couple_id, userId)
    .first();
  if (!partner) return null;
  return {
    id: membership.couple_id,
    joinedAt: Number(membership.joined_at),
    partner: { id: partner.user_id, displayName: partner.display_name },
  };
}

async function checkLimit(env, request, bucket, limit, windowMs) {
  const ip = request.headers.get("cf-connecting-ip")
    || request.headers.get("x-real-ip")
    || "local";
  const key = bucket + ":" + (await hashToken(ip)).slice(0, 32);
  const at = now();
  const row = await env.DB.prepare(
    "SELECT count, reset_at FROM auth_limits WHERE key = ?",
  ).bind(key).first();
  if (row && Number(row.reset_at) > at && Number(row.count) >= limit) return false;
  if (!row || Number(row.reset_at) <= at) {
    await env.DB.prepare(
      "INSERT OR REPLACE INTO auth_limits (key, count, reset_at) VALUES (?, ?, ?)",
    ).bind(key, 1, at + windowMs).run();
  } else {
    await env.DB.prepare("UPDATE auth_limits SET count = count + 1 WHERE key = ?")
      .bind(key)
      .run();
  }
  return true;
}

async function clearLimit(env, request, bucket) {
  const ip = request.headers.get("cf-connecting-ip")
    || request.headers.get("x-real-ip")
    || "local";
  const key = bucket + ":" + (await hashToken(ip)).slice(0, 32);
  await env.DB.prepare("DELETE FROM auth_limits WHERE key = ?").bind(key).run();
}

async function handleRegister(request, env) {
  if (!(await checkLimit(env, request, "register", 20, 60 * 60 * 1000))) {
    return fail("too_many", "注册太频繁了，请稍后再试", 429);
  }
  const body = await readJson(request);
  if (!body) return fail("bad_json", "请求不是合法 JSON");
  const email = normalizeEmail(body.email);
  const password = body.password;
  if (!validEmail(email)) return fail("bad_email", "邮箱格式不正确");
  if (!validPassword(password)) return fail("bad_password", "密码需要 8–72 个字符");

  const exists = await env.DB.prepare("SELECT id FROM users WHERE email = ?").bind(email).first();
  if (exists) return fail("email_taken", "这个邮箱已经注册", 409);

  const displayName = cleanName(body.displayName, email.split("@")[0] || "我");
  const recoveryCode = randomRecoveryCode();
  const passwordHash = await hashPassword(password);
  const recoveryHash = await hashToken(recoveryCode);
  const userId = crypto.randomUUID();
  const createdAt = now();

  try {
    await env.DB.prepare(
      `INSERT INTO users (id, email, password_hash, recovery_hash, display_name, created_at)
       VALUES (?, ?, ?, ?, ?, ?)`,
    )
      .bind(userId, email, passwordHash, recoveryHash, displayName, createdAt)
      .run();
  } catch {
    return fail("email_taken", "这个邮箱已经注册", 409);
  }

  const session = await createSession(env, userId);
  return json({
    ok: true,
    token: session.token,
    tokenExpiresAt: session.expiresAt,
    recoveryCode,
    user: { id: userId, email, displayName },
  });
}

async function handleLogin(request, env) {
  if (!(await checkLimit(env, request, "login", 30, 10 * 60 * 1000))) {
    return fail("too_many", "尝试太频繁了，请稍后再试", 429);
  }
  const body = await readJson(request);
  if (!body) return fail("bad_json", "请求不是合法 JSON");
  const email = normalizeEmail(body.email);
  const password = body.password;
  if (!validEmail(email) || typeof password !== "string") {
    return fail("bad_credentials", "邮箱或密码不正确", 401);
  }

  const user = await env.DB.prepare(
    "SELECT id, email, display_name, password_hash FROM users WHERE email = ?",
  )
    .bind(email)
    .first();
  if (!user) return fail("bad_credentials", "邮箱或密码不正确", 401);
  const valid = await verifyPassword(password, user.password_hash);
  if (!valid) return fail("bad_credentials", "邮箱或密码不正确", 401);

  const session = await createSession(env, user.id);
  await clearLimit(env, request, "login").catch(() => {});
  return json({
    ok: true,
    token: session.token,
    tokenExpiresAt: session.expiresAt,
    user: { id: user.id, email: user.email, displayName: user.display_name },
  });
}

async function handleRecover(request, env) {
  if (!(await checkLimit(env, request, "recover", 10, 60 * 60 * 1000))) {
    return fail("too_many", "恢复尝试太频繁了，请稍后再试", 429);
  }
  const body = await readJson(request);
  if (!body) return fail("bad_json", "请求不是合法 JSON");
  const email = normalizeEmail(body.email);
  const code = String(body.recoveryCode || "").trim().toUpperCase();
  const password = body.newPassword;
  if (!validEmail(email) || !code) return fail("bad_recovery", "邮箱或恢复码不正确");
  if (!validPassword(password)) return fail("bad_password", "密码需要 8–72 个字符");

  const user = await env.DB.prepare(
    "SELECT id, recovery_hash FROM users WHERE email = ?",
  )
    .bind(email)
    .first();
  if (!user) return fail("bad_recovery", "邮箱或恢复码不正确");
  const hash = await hashToken(code);
  if (!constantTimeEqual(b64urlToBytes(hash), b64urlToBytes(user.recovery_hash))) {
    return fail("bad_recovery", "邮箱或恢复码不正确");
  }

  const newRecovery = randomRecoveryCode();
  const newPasswordHash = await hashPassword(password);
  const newRecoveryHash = await hashToken(newRecovery);
  await env.DB.batch([
    env.DB.prepare("UPDATE users SET password_hash = ?, recovery_hash = ? WHERE id = ?")
      .bind(newPasswordHash, newRecoveryHash, user.id),
    env.DB.prepare("DELETE FROM sessions WHERE user_id = ?").bind(user.id),
  ]);
  return json({ ok: true, recoveryCode: newRecovery });
}

async function handleMe(request, env) {
  const user = await requireUser(request, env);
  if (!user) return fail("unauthorized", "登录已过期，请重新登录", 401);
  const couple = await coupleInfo(env, user.id);
  return json({
    ok: true,
    user: { id: user.id, email: user.email, displayName: user.displayName },
    couple,
    serverTime: now(),
  });
}

async function handlePairCreate(request, env) {
  if (!(await checkLimit(env, request, "pair_create", 30, 60 * 60 * 1000))) {
    return fail("too_many", "配对码生成太频繁了", 429);
  }
  const user = await requireUser(request, env);
  if (!user) return fail("unauthorized", "登录已过期，请重新登录", 401);
  const existing = await coupleInfo(env, user.id);
  if (existing) return fail("already_paired", "你已经绑定了伴侣", 409);

  await env.DB.prepare("DELETE FROM pair_codes WHERE owner_id = ?").bind(user.id).run();
  const expiresAt = now() + PAIR_MINUTES * 60 * 1000;
  let code = "";
  for (let i = 0; i < 8; i++) {
    const candidate = randomPairCode();
    const result = await env.DB.prepare(
      "INSERT OR IGNORE INTO pair_codes (code, owner_id, created_at, expires_at, used_by) VALUES (?, ?, ?, ?, NULL)",
    )
      .bind(candidate, user.id, now(), expiresAt)
      .run();
    if (Number(result.meta.changes || 0) > 0) {
      code = candidate;
      break;
    }
  }
  if (!code) return fail("code_busy", "配对码生成失败，请稍后再试", 503);
  return json({ ok: true, code, expiresAt, expiresIn: PAIR_MINUTES * 60 });
}

async function handlePairJoin(request, env) {
  if (!(await checkLimit(env, request, "pair_join", 30, 10 * 60 * 1000))) {
    return fail("too_many", "尝试太频繁了，请稍后再试", 429);
  }
  const user = await requireUser(request, env);
  if (!user) return fail("unauthorized", "登录已过期，请重新登录", 401);
  const body = await readJson(request);
  if (!body) return fail("bad_json", "请求不是合法 JSON");
  const code = String(body.code || "").trim();
  if (!/^\d{6}$/.test(code)) return fail("bad_code", "配对码是 6 位数字");

  const existing = await coupleInfo(env, user.id);
  if (existing) return fail("already_paired", "你已经绑定了伴侣", 409);

  const pair = await env.DB.prepare(
    "SELECT code, owner_id, expires_at, used_by FROM pair_codes WHERE code = ?",
  )
    .bind(code)
    .first();
  if (!pair) return fail("bad_code", "配对码不存在或已过期", 404);
  if (pair.used_by) return fail("code_used", "这个配对码已经被使用", 409);
  if (Number(pair.expires_at) <= now()) return fail("code_expired", "配对码已过期，请让对方重新生成", 410);
  if (pair.owner_id === user.id) return fail("self_pair", "不能和自己配对");

  const ownerCouple = await coupleInfo(env, pair.owner_id);
  if (ownerCouple) return fail("owner_paired", "对方已经绑定了伴侣", 409);

  const coupleId = crypto.randomUUID();
  const joinedAt = now();

  // 先用一条条件 UPDATE 抢占配对码。D1 的 batch 不是“条件事务”，
  // 如果把它和插入放在同一个 batch，抢码失败时后面的插入仍会执行，
  // 留下没有成员的孤儿 couple。所以抢占必须单独一步。
  let claimed;
  try {
    claimed = await env.DB.prepare(
      "UPDATE pair_codes SET used_by = ? WHERE code = ? AND used_by IS NULL AND expires_at > ?",
    ).bind(user.id, code, joinedAt).run();
  } catch {
    return fail("pair_conflict", "配对冲突了，请刷新后重试", 409);
  }
  if (Number(claimed.meta.changes || 0) === 0) {
    return fail("code_used", "这个配对码刚刚被用掉了，请让对方重新生成", 409);
  }

  try {
    await env.DB.batch([
      env.DB.prepare("INSERT INTO couples (id, created_at) VALUES (?, ?)").bind(coupleId, joinedAt),
      env.DB.prepare("INSERT INTO memberships (user_id, couple_id, joined_at) VALUES (?, ?, ?)")
        .bind(pair.owner_id, coupleId, joinedAt),
      env.DB.prepare("INSERT INTO memberships (user_id, couple_id, joined_at) VALUES (?, ?, ?)")
        .bind(user.id, coupleId, joinedAt),
    ]);
  } catch {
    // 抢占成功但建关系失败：把码释放回可用，避免用户白白等 10 分钟。
    await env.DB.prepare("UPDATE pair_codes SET used_by = NULL WHERE code = ? AND used_by = ?")
      .bind(code, user.id).run().catch(() => {});
    return fail("pair_conflict", "配对冲突了，请刷新后重试", 409);
  }

  const owner = await env.DB.prepare(
    "SELECT id, display_name FROM users WHERE id = ?",
  )
    .bind(pair.owner_id)
    .first();
  return json({
    ok: true,
    couple: {
      id: coupleId,
      joinedAt,
      partner: { id: owner.id, displayName: owner.display_name },
    },
  });
}

async function handleUnpair(request, env) {
  const user = await requireUser(request, env);
  if (!user) return fail("unauthorized", "登录已过期，请重新登录", 401);
  const couple = await coupleInfo(env, user.id);
  if (!couple) return fail("no_pair", "还没有绑定伴侣", 404);

  await env.DB.batch([
    env.DB.prepare("DELETE FROM memberships WHERE couple_id = ?").bind(couple.id),
    env.DB.prepare("DELETE FROM couples WHERE id = ?").bind(couple.id),
    env.DB.prepare("DELETE FROM pair_codes WHERE owner_id = ?").bind(user.id),
  ]);

  try {
    const stub = env.COUPLE_ROOM.get(env.COUPLE_ROOM.idFromName(couple.id));
    await stub.fetch("https://room/internal/close", {
      method: "POST",
      headers: { "x-couple-id": couple.id },
    });
  } catch {
    // 房间已经关闭或暂时不可用时，不影响解绑结果。
  }
  return json({ ok: true });
}

async function handleWebSocket(request, env) {
  const user = await requireUser(request, env);
  if (!user) return fail("unauthorized", "登录已过期，请重新登录", 401);
  if (request.headers.get("Upgrade") !== "websocket") {
    return fail("upgrade_required", "这里只接受 WebSocket", 426);
  }
  const couple = await coupleInfo(env, user.id);
  if (!couple) return fail("no_pair", "先绑定伴侣再连接", 409);

  const headers = new Headers(request.headers);
  headers.set("x-user-id", user.id);
  headers.set("x-user-name", encodeURIComponent(user.displayName));
  headers.set("x-partner-id", couple.partner.id);
  headers.set("x-couple-id", couple.id);

  const stub = env.COUPLE_ROOM.get(env.COUPLE_ROOM.idFromName(couple.id));
  return stub.fetch(new Request(request, { headers }));
}

async function handleLogout(request, env) {
  const user = await requireUser(request, env);
  if (!user) return fail("unauthorized", "登录已过期", 401);
  await env.DB.prepare("DELETE FROM sessions WHERE token_hash = ?").bind(user.tokenHash).run();
  return json({ ok: true });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: {
          "access-control-allow-origin": "*",
          "access-control-allow-headers": "authorization, content-type",
          "access-control-allow-methods": "GET, POST, OPTIONS",
        },
      });
    }

    try {
      if (url.pathname === "/api/health" && request.method === "GET") {
        return json({ ok: true, time: now(), service: "hotwinter-cloud" });
      }
      if (url.pathname === "/api/register" && request.method === "POST") {
        return await handleRegister(request, env);
      }
      if (url.pathname === "/api/login" && request.method === "POST") {
        return await handleLogin(request, env);
      }
      if (url.pathname === "/api/recover" && request.method === "POST") {
        return await handleRecover(request, env);
      }
      if (url.pathname === "/api/logout" && request.method === "POST") {
        return await handleLogout(request, env);
      }
      if (url.pathname === "/api/me" && request.method === "GET") {
        return await handleMe(request, env);
      }
      if (url.pathname === "/api/pair/create" && request.method === "POST") {
        return await handlePairCreate(request, env);
      }
      if (url.pathname === "/api/pair/join" && request.method === "POST") {
        return await handlePairJoin(request, env);
      }
      if (url.pathname === "/api/pair/unpair" && request.method === "POST") {
        return await handleUnpair(request, env);
      }
      if (url.pathname === "/api/ws" && request.method === "GET") {
        return await handleWebSocket(request, env);
      }
      return fail("not_found", "接口不存在", 404);
    } catch (error) {
      console.error("worker_error", error);
      return fail("internal", "服务端开小差了，请稍后再试", 500);
    }
  },
};
