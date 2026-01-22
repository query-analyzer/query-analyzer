-- ============================================
-- Create SLEEP function alias for H2
-- This allows us to simulate slow queries
-- ============================================
CREATE ALIAS IF NOT EXISTS SLEEP FOR "java.lang.Thread.sleep(long)";

-- ============================================
-- Users (10 users for better N+1 demonstration)
-- ============================================
INSERT INTO users (name, email) VALUES ('Alice Smith', 'alice@example.com');
INSERT INTO users (name, email) VALUES ('Bob Johnson', 'bob@example.com');
INSERT INTO users (name, email) VALUES ('Charlie Brown', 'charlie@example.com');
INSERT INTO users (name, email) VALUES ('Diana Prince', 'diana@example.com');
INSERT INTO users (name, email) VALUES ('Eve Davis', 'eve@example.com');
INSERT INTO users (name, email) VALUES ('Frank Wilson', 'frank@example.com');
INSERT INTO users (name, email) VALUES ('Grace Lee', 'grace@example.com');
INSERT INTO users (name, email) VALUES ('Henry Miller', 'henry@example.com');
INSERT INTO users (name, email) VALUES ('Ivy Chen', 'ivy@example.com');
INSERT INTO users (name, email) VALUES ('Jack Taylor', 'jack@example.com');

-- ============================================
-- Orders for Alice (user_id = 1) - 3 orders
-- ============================================
INSERT INTO orders (product_name, amount, order_date, user_id) VALUES ('Laptop', 1200.00, CURRENT_TIMESTAMP, 1);
INSERT INTO orders (product_name, amount, order_date, user_id) VALUES ('Mouse', 25.00, CURRENT_TIMESTAMP, 1);
INSERT INTO orders (product_name, amount, order_date, user_id) VALUES ('Keyboard', 75.00, CURRENT_TIMESTAMP, 1);

-- ============================================
-- Orders for Bob (user_id = 2) - 2 orders
-- ============================================
INSERT INTO orders (product_name, amount, order_date, user_id) VALUES ('Monitor', 350.00, CURRENT_TIMESTAMP, 2);
INSERT INTO orders (product_name, amount, order_date, user_id) VALUES ('Webcam', 120.00, CURRENT_TIMESTAMP, 2);

-- ============================================
-- Orders for Charlie (user_id = 3) - 4 orders
-- ============================================
INSERT INTO orders (product_name, amount, order_date, user_id) VALUES ('Headphones', 200.00, CURRENT_TIMESTAMP, 3);
INSERT INTO orders (product_name, amount, order_date, user_id) VALUES ('Microphone', 150.00, CURRENT_TIMESTAMP, 3);
INSERT INTO orders (product_name, amount, order_date, user_id) VALUES ('Desk Lamp', 45.00, CURRENT_TIMESTAMP, 3);
INSERT INTO orders (product_name, amount, order_date, user_id) VALUES ('Office Chair', 400.00, CURRENT_TIMESTAMP, 3);

-- ============================================
-- Orders for Diana (user_id = 4) - 1 order
-- ============================================
INSERT INTO orders (product_name, amount, order_date, user_id) VALUES ('Tablet', 500.00, CURRENT_TIMESTAMP, 4);

-- ============================================
-- Orders for Eve (user_id = 5) - 2 orders
-- ============================================
INSERT INTO orders (product_name, amount, order_date, user_id) VALUES ('Smartphone', 800.00, CURRENT_TIMESTAMP, 5);
INSERT INTO orders (product_name, amount, order_date, user_id) VALUES ('Smartwatch', 300.00, CURRENT_TIMESTAMP, 5);

-- ============================================
-- Orders for Frank (user_id = 6) - 3 orders
-- ============================================
INSERT INTO orders (product_name, amount, order_date, user_id) VALUES ('External SSD', 150.00, CURRENT_TIMESTAMP, 6);
INSERT INTO orders (product_name, amount, order_date, user_id) VALUES ('USB Hub', 35.00, CURRENT_TIMESTAMP, 6);
INSERT INTO orders (product_name, amount, order_date, user_id) VALUES ('Laptop Stand', 80.00, CURRENT_TIMESTAMP, 6);

-- ============================================
-- Orders for Grace (user_id = 7) - 2 orders
-- ============================================
INSERT INTO orders (product_name, amount, order_date, user_id) VALUES ('Drawing Tablet', 250.00, CURRENT_TIMESTAMP, 7);
INSERT INTO orders (product_name, amount, order_date, user_id) VALUES ('Stylus Pen', 50.00, CURRENT_TIMESTAMP, 7);

-- ============================================
-- Orders for Henry (user_id = 8) - 0 orders
-- (User with no orders - edge case)
-- ============================================

-- ============================================
-- Orders for Ivy (user_id = 9) - 5 orders
-- ============================================
INSERT INTO orders (product_name, amount, order_date, user_id) VALUES ('Printer', 200.00, CURRENT_TIMESTAMP, 9);
INSERT INTO orders (product_name, amount, order_date, user_id) VALUES ('Scanner', 150.00, CURRENT_TIMESTAMP, 9);
INSERT INTO orders (product_name, amount, order_date, user_id) VALUES ('Paper Shredder', 100.00, CURRENT_TIMESTAMP, 9);
INSERT INTO orders (product_name, amount, order_date, user_id) VALUES ('Filing Cabinet', 120.00, CURRENT_TIMESTAMP, 9);
INSERT INTO orders (product_name, amount, order_date, user_id) VALUES ('Label Maker', 40.00, CURRENT_TIMESTAMP, 9);

-- ============================================
-- Orders for Jack (user_id = 10) - 1 order
-- ============================================
INSERT INTO orders (product_name, amount, order_date, user_id) VALUES ('Gaming Console', 500.00, CURRENT_TIMESTAMP, 10);
