-- QueryLog: PK = UUID(TEXT)
CREATE TABLE IF NOT EXISTS query_log (
  id           TEXT    PRIMARY KEY,         -- UUID(LogID)
  executed_at  TEXT    NOT NULL,            -- ISO8601
  user_id      TEXT    NOT NULL,
  sql_raw      TEXT    NOT NULL,
  sql_summary  TEXT,
  return_rows  INTEGER NOT NULL DEFAULT 0,
  status       TEXT    NOT NULL             -- 'SUCCESS' | 'FAILURE'
);

-- DetectionEvent: FK = query_log.id(UUID)
CREATE TABLE IF NOT EXISTS detection_event (
  id             TEXT    PRIMARY KEY,       -- UUID(EventID)
  log_id         TEXT    NOT NULL,          -- FK → query_log.id
  event_type     TEXT    NOT NULL,          -- 'PATTERN' | 'BEHAVIOR' | 'AUTHZ'
  severity       TEXT    NOT NULL,          -- 'LOW' | 'MEDIUM' | 'HIGH'
  occurred_at    TEXT    NOT NULL,          -- ISO8601
  sql_raw        TEXT    NOT NULL,          -- 마스킹/정규화된 SQL
  FOREIGN KEY (log_id) REFERENCES query_log(id)
);

-- NotificationLog (SDS 4.2.3)
CREATE TABLE IF NOT EXISTS notification_log (
  id             TEXT    PRIMARY KEY,         -- UUID (NotifyID)
  event_id       TEXT    NOT NULL,            -- FK -> detection_event.id
  channel        TEXT    NOT NULL,            -- 'SLACK' | 'EMAIL'
  status         TEXT    NOT NULL,            -- 'SENT'  | 'FAILED'
  error_code     TEXT,                         -- nullable
  error_message  TEXT,                         -- nullable
  sent_at        TEXT    NOT NULL,            -- ISO8601
  FOREIGN KEY (event_id) REFERENCES detection_event(id)
);

-- ========== FR-6: AdminUser ==========
CREATE TABLE IF NOT EXISTS admin_user (
  id             TEXT PRIMARY KEY,              -- AdminID(UUID)
  email          TEXT NOT NULL UNIQUE,          -- 관리자 이메일(고유)
  password_hash  TEXT NOT NULL,                 -- SHA-256 해시(hex)
  role           TEXT NOT NULL,                 -- READER | WRITER | DBA
  last_login     TEXT                           -- ISO-8601 문자열
);

-- === MIGRATION: add admin_id FK to query_log =========================
PRAGMA foreign_keys=OFF;

-- 새 스키마( admin_id + FK ) 테이블 생성
CREATE TABLE IF NOT EXISTS query_log_new (
  id            TEXT PRIMARY KEY,
  executed_at   TEXT NOT NULL,
  user_id       TEXT NOT NULL,   -- 이메일 문자열
  admin_id      TEXT,            -- ✅ AdminUser FK (nullable 가능)
  sql_raw       TEXT NOT NULL,
  sql_summary   TEXT,
  return_rows   INTEGER NOT NULL DEFAULT 0 CHECK (return_rows >= 0),
status        TEXT NOT NULL CHECK (status IN ('SUCCESS','FAILURE')),
FOREIGN KEY (admin_id) REFERENCES admin_user(id)
);

-- 기존 query_log 데이터를 이관 (기존엔 admin_id 없었으므로 NULL로)
INSERT INTO query_log_new (id, executed_at, user_id, admin_id, sql_raw, sql_summary, return_rows, status)
SELECT id, executed_at, user_id, NULL, sql_raw, sql_summary, return_rows, status
FROM query_log;

DROP TABLE IF EXISTS query_log;
ALTER TABLE query_log_new RENAME TO query_log;

-- 인덱스 권장
CREATE INDEX IF NOT EXISTS idx_query_log_admin ON query_log(admin_id);
CREATE INDEX IF NOT EXISTS idx_query_log_user  ON query_log(user_id);

PRAGMA foreign_keys=ON;
-- ====================================================================
