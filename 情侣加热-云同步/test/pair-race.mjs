import { randomUUID } from "node:crypto";

const BASE = process.env.BASE || "http://127.0.0.1:8787";

function assert(condition, message) {
  if (!condition) throw new Error(message);
  console.log("  ✓ " + message);
}

async function raw(path, body, token, method = "POST") {
  const response = await fetch(BASE + path, {
    method,
    headers: {
      "content-type": "application/json",
      ...(token ? { authorization: "Bearer " + token } : {}),
    },
    body: method === "GET" ? undefined : JSON.stringify(body || {}),
  });
  let json = {};
  try {
    json = await response.json();
  } catch {
    // 空响应
  }
  return { status: response.status, json };
}

async function register(name) {
  const email = `${name}-${randomUUID()}@example.com`;
  const result = await raw("/api/register", {
    email,
    password: "correct horse battery",
    displayName: name,
  });
  if (result.status !== 200) throw new Error("register: " + JSON.stringify(result.json));
  return result.json;
}

async function main() {
  console.log("并发抢同一个配对码");
  const owner = await register("owner");
  const first = await register("first");
  const second = await register("second");

  const pair = await raw("/api/pair/create", {}, owner.token);
  assert(pair.status === 200 && /^\d{6}$/.test(pair.json.code), "房主生成 6 位配对码");

  const [a, b] = await Promise.all([
    raw("/api/pair/join", { code: pair.json.code }, first.token),
    raw("/api/pair/join", { code: pair.json.code }, second.token),
  ]);
  const success = [a, b].filter((item) => item.status === 200);
  const failed = [a, b].filter((item) => item.status !== 200);
  assert(success.length === 1, "只有一个人抢到配对码");
  assert(failed.length === 1 && failed[0].status === 409, "另一个人收到 409");
  assert(
    failed[0].json.error === "code_used" || failed[0].json.error === "pair_conflict",
    "失败原因是配对码已被用掉或冲突",
  );

  const ownerMe = await raw("/api/me", null, owner.token, "GET");
  const winnerMe = await raw("/api/me", null, success[0] === a ? first.token : second.token, "GET");
  const loserMe = await raw("/api/me", null, success[0] === a ? second.token : first.token, "GET");
  assert(ownerMe.json.couple !== null, "房主只有一段配对关系");
  assert(winnerMe.json.couple !== null, "抢到的人有配对关系");
  assert(loserMe.json.couple === null, "没抢到的人没有残留关系");

  console.log("并发抢码测试通过。");
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
