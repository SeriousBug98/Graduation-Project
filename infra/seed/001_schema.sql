-- 대상 DB에 간단한 테이블 (행동/권한/패턴 탐지 테스트용)
CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  email VARCHAR(255) UNIQUE,
  name VARCHAR(100),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO users(email,name) VALUES
('normal@example.com','Normal'),
('admin@example.com','Admin');

-- 읽기 전용 계정 예시(권한 탐지 시나리오)
-- 실제 접근은 애플리케이션에서 관리하되, 시나리오 검증용으로 참고
