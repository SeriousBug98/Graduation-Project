DROP TABLE IF EXISTS query_log;

CREATE TABLE IF NOT EXISTS query_log (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,          -- LogID
  executed_at    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 실행 시간
  user_id        VARCHAR(128)   NOT NULL,                    -- UserID (DB 사용자명 등)
  sql_raw        TEXT           NOT NULL,                    -- SQL 원문
  sql_summary    VARCHAR(512)   NULL,                        -- SQL 요약(옵션)
  return_rows    INT            NOT NULL DEFAULT 0 CHECK (return_rows >= 0), -- 반환 행 수
  status         ENUM('SUCCESS','FAILURE') NOT NULL,         -- 실행 상태
  INDEX idx_exec_time (executed_at)
);