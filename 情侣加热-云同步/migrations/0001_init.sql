-- 账号、会话、配对关系。所有时间戳均为毫秒。
CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  email TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  recovery_hash TEXT NOT NULL,
  display_name TEXT NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS sessions (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  token_hash TEXT NOT NULL UNIQUE,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);

CREATE TABLE IF NOT EXISTS couples (
  id TEXT PRIMARY KEY,
  created_at INTEGER NOT NULL
);

-- user_id 唯一：一个人同一时间只能绑定一个伴侣。
CREATE TABLE IF NOT EXISTS memberships (
  user_id TEXT PRIMARY KEY,
  couple_id TEXT NOT NULL,
  joined_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_memberships_couple ON memberships(couple_id);

CREATE TABLE IF NOT EXISTS pair_codes (
  code TEXT PRIMARY KEY,
  owner_id TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  used_by TEXT
);

CREATE INDEX IF NOT EXISTS idx_pair_codes_owner ON pair_codes(owner_id);
