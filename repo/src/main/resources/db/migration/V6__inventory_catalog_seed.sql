INSERT INTO unit_conversions (from_unit_id, to_unit_id, factor, note)
SELECT u_case.id, u_ea.id, 24.000000, '1 case = 24 each'
FROM inventory_units u_case
JOIN inventory_units u_ea ON u_ea.unit_code = 'EA'
WHERE u_case.unit_code = 'CASE'
  AND NOT EXISTS (
    SELECT 1 FROM unit_conversions c
    WHERE c.from_unit_id = u_case.id AND c.to_unit_id = u_ea.id
  );

INSERT INTO unit_conversions (from_unit_id, to_unit_id, factor, note)
SELECT u_lb.id, u_oz.id, 16.000000, '1 lb = 16 oz'
FROM inventory_units u_lb
JOIN inventory_units u_oz ON u_oz.unit_code = 'OZ'
WHERE u_lb.unit_code = 'LB'
  AND NOT EXISTS (
    SELECT 1 FROM unit_conversions c
    WHERE c.from_unit_id = u_lb.id AND c.to_unit_id = u_oz.id
  );

INSERT INTO ingredients (ingredient_code, ingredient_name, default_unit_id, active)
SELECT 'TOMATO', 'Tomato', u.id, TRUE
FROM inventory_units u
WHERE u.unit_code = 'EA'
  AND NOT EXISTS (SELECT 1 FROM ingredients WHERE ingredient_code = 'TOMATO');

INSERT INTO ingredients (ingredient_code, ingredient_name, default_unit_id, active)
SELECT 'OLIVE_OIL', 'Olive Oil', u.id, TRUE
FROM inventory_units u
WHERE u.unit_code = 'OZ'
  AND NOT EXISTS (SELECT 1 FROM ingredients WHERE ingredient_code = 'OLIVE_OIL');

INSERT INTO inventory_batches (
    ingredient_id,
    batch_no,
    quantity_received,
    quantity_available,
    unit_id,
    unit_cost,
    received_at,
    expires_at,
    status
)
SELECT i.id, 'BATCH-TOMATO-001', 240.000, 240.000, i.default_unit_id, 0.3000, NOW(6), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'AVAILABLE'
FROM ingredients i
WHERE i.ingredient_code = 'TOMATO'
  AND NOT EXISTS (SELECT 1 FROM inventory_batches b WHERE b.ingredient_id = i.id AND b.batch_no = 'BATCH-TOMATO-001');

INSERT INTO inventory_batches (
    ingredient_id,
    batch_no,
    quantity_received,
    quantity_available,
    unit_id,
    unit_cost,
    received_at,
    expires_at,
    status
)
SELECT i.id, 'BATCH-OIL-001', 128.000, 128.000, i.default_unit_id, 0.4500, NOW(6), DATE_ADD(CURDATE(), INTERVAL 45 DAY), 'AVAILABLE'
FROM ingredients i
WHERE i.ingredient_code = 'OLIVE_OIL'
  AND NOT EXISTS (SELECT 1 FROM inventory_batches b WHERE b.ingredient_id = i.id AND b.batch_no = 'BATCH-OIL-001');

INSERT INTO spu_catalog (spu_code, name, description, active)
VALUES ('MAT-KIT-001', 'Program Material Kit', 'Starter material kit for on-site training', TRUE)
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description), active = VALUES(active);

INSERT INTO sku_catalog (spu_id, sku_code, name, specs, purchase_limit_per_student, active)
SELECT s.id, 'MAT-KIT-001-A', 'Material Kit Standard', JSON_OBJECT('version', 'A', 'bundle', 'standard'), 5, TRUE
FROM spu_catalog s
WHERE s.spu_code = 'MAT-KIT-001'
  AND NOT EXISTS (SELECT 1 FROM sku_catalog k WHERE k.sku_code = 'MAT-KIT-001-A');

INSERT INTO sku_pricing_tiers (sku_id, min_qty, unit_price, currency_code, valid_from)
SELECT k.id, 1, 29.00, 'USD', NOW(6)
FROM sku_catalog k
WHERE k.sku_code = 'MAT-KIT-001-A'
  AND NOT EXISTS (SELECT 1 FROM sku_pricing_tiers t WHERE t.sku_id = k.id AND t.min_qty = 1);

INSERT INTO sku_pricing_tiers (sku_id, min_qty, unit_price, currency_code, valid_from)
SELECT k.id, 3, 25.00, 'USD', NOW(6)
FROM sku_catalog k
WHERE k.sku_code = 'MAT-KIT-001-A'
  AND NOT EXISTS (SELECT 1 FROM sku_pricing_tiers t WHERE t.sku_id = k.id AND t.min_qty = 3);
