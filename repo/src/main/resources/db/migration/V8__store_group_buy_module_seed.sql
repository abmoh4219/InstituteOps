CREATE INDEX idx_group_buy_campaigns_status_window ON group_buy_campaigns (status, starts_at, ends_at);
CREATE INDEX idx_group_buy_groups_campaign_status ON group_buy_groups (campaign_id, status, expires_at);
CREATE INDEX idx_group_buy_members_group_status ON group_buy_group_members (group_id, status);
CREATE INDEX idx_group_buy_orders_group_status ON group_buy_orders (group_id, order_status);
CREATE INDEX idx_group_buy_orders_student_campaign ON group_buy_orders (student_id, campaign_id, order_status);
CREATE INDEX idx_inventory_locks_order_status ON inventory_locks (group_buy_order_id, lock_status);

INSERT INTO students (
    student_no,
    first_name,
    last_name,
    date_of_birth,
    status,
    masked_email,
    masked_phone
)
SELECT 'student1', 'Student', 'One', '2004-01-01', 'ACTIVE', 'st***@local', '***-***-0001'
WHERE NOT EXISTS (SELECT 1 FROM students WHERE UPPER(student_no) = 'STUDENT1');

INSERT INTO group_buy_campaigns (
    campaign_code,
    sku_id,
    title,
    starts_at,
    ends_at,
    cutoff_time,
    required_participants,
    formation_window_hours,
    status,
    created_by
)
SELECT 'CMP-KIT-APR', s.id, 'Material Kit April Group Buy', DATE_SUB(NOW(6), INTERVAL 1 DAY), DATE_ADD(NOW(6), INTERVAL 20 DAY), '21:00:00', 3, 72, 'ACTIVE', u.id
FROM sku_catalog s
JOIN users u ON u.username = 'store'
WHERE s.sku_code = 'MAT-KIT-001-A'
  AND NOT EXISTS (SELECT 1 FROM group_buy_campaigns WHERE campaign_code = 'CMP-KIT-APR');
