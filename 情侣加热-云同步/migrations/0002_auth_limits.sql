-- 登录/注册/配对接口的粗粒度限流。key 是“桶 + IP 哈希”，不存原始 IP。
CREATE TABLE IF NOT EXISTS auth_limits (
  key TEXT PRIMARY KEY,
  count INTEGER NOT NULL,
  reset_at INTEGER NOT NULL
);
