INSERT INTO order_data (id, customer_name, email, amount, ordered_at)
SELECT
    series,
    'Customer ' || series,
    'customer' || series || '@example.local',
    round((10 + random() * 990)::numeric, 2),
    now() - (series || ' minutes')::interval
FROM generate_series(1, 100000) AS series;
