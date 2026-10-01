INSERT INTO order_data (user_name, product_name, category, amount, status, order_date)
SELECT
    'User ' || series,
    CASE series % 5
        WHEN 0 THEN 'Ski Lift Pass'
        WHEN 1 THEN 'Waterpark Day Pass'
        WHEN 2 THEN 'Hotel Stay'
        WHEN 3 THEN 'Camping Package'
        ELSE 'Theme Park Ticket'
    END,
    CASE series % 5
        WHEN 0 THEN 'ski'
        WHEN 1 THEN 'waterpark'
        WHEN 2 THEN 'accommodation'
        WHEN 3 THEN 'camping'
        ELSE 'themepark'
    END,
    (10000 + random() * 490000)::INTEGER,
    CASE series % 10
        WHEN 0 THEN 'cancelled'
        WHEN 1 THEN 'pending'
        ELSE 'confirmed'
    END,
    now() - (series || ' minutes')::interval
FROM generate_series(1, 100000) AS series;
