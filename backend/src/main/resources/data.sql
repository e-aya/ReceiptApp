-- INSERT INTO users (id, email, password, plan_id, created_at)
-- VALUES (
--            'test-user-001',
--            'test@example.com',
--            'password123',
--            'free',
--            NOW()
--        );
-- プランの初期データ
INSERT INTO plans (id, name, monthly_limit, price_jpy)
VALUES
    ('free',     '無料プラン',     30,   0),
    ('pro',      'Proプラン',     300,  500),
    ('business', 'Businessプラン', NULL, 3000)
    ON CONFLICT (id) DO NOTHING;