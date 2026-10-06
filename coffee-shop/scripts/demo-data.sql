-- 애플리케이션이 테이블을 생성한 후, 과제 DB에서 실행합니다.
-- 기존 사용자 지갑 잔액은 변경하지 않습니다.
INSERT INTO users (email, created_at)
SELECT 'demo@example.com', UTC_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'demo@example.com');

INSERT INTO point_wallets (user_id, balance, updated_at)
SELECT u.id, 0, UTC_TIMESTAMP(6)
FROM users u
WHERE u.email = 'demo@example.com'
  AND NOT EXISTS (SELECT 1 FROM point_wallets w WHERE w.user_id = u.id);

INSERT INTO coffee_menus (name, price, active)
SELECT '아메리카노', 4500, TRUE
WHERE NOT EXISTS (SELECT 1 FROM coffee_menus WHERE name = '아메리카노');

INSERT INTO coffee_menus (name, price, active)
SELECT '카페라떼', 5000, TRUE
WHERE NOT EXISTS (SELECT 1 FROM coffee_menus WHERE name = '카페라떼');

INSERT INTO coffee_menus (name, price, active)
SELECT '카페모카', 5500, TRUE
WHERE NOT EXISTS (SELECT 1 FROM coffee_menus WHERE name = '카페모카');

SELECT id AS user_id, email FROM users WHERE email = 'demo@example.com';
