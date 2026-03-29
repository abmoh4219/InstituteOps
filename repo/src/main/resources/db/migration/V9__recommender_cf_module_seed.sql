CREATE INDEX idx_recommender_events_student_time ON recommender_events (student_id, occurred_at);
CREATE INDEX idx_recommender_events_item_time ON recommender_events (item_type, item_id, occurred_at);
CREATE INDEX idx_recommender_models_active ON recommender_models (active, model_code);
CREATE INDEX idx_recommender_model_versions_model_status ON recommender_model_versions (model_id, training_status, version_no);
CREATE INDEX idx_recommender_recommendations_student_rank ON recommender_recommendations (student_id, rank_no, generated_at);

INSERT INTO recommender_models (
    model_code,
    algorithm_family,
    similarity_metric,
    time_decay_half_life_days,
    popularity_penalty,
    active,
    created_by
)
SELECT 'USER_CF_COSINE', 'USER_CF', 'COSINE', 21, 0.0500, TRUE, u.id
FROM users u
WHERE u.username = 'sysadmin'
  AND NOT EXISTS (SELECT 1 FROM recommender_models WHERE model_code = 'USER_CF_COSINE');

INSERT INTO recommender_models (
    model_code,
    algorithm_family,
    similarity_metric,
    time_decay_half_life_days,
    popularity_penalty,
    active,
    created_by
)
SELECT 'USER_CF_JACCARD', 'USER_CF', 'JACCARD', 21, 0.0500, FALSE, u.id
FROM users u
WHERE u.username = 'sysadmin'
  AND NOT EXISTS (SELECT 1 FROM recommender_models WHERE model_code = 'USER_CF_JACCARD');

INSERT INTO recommender_models (
    model_code,
    algorithm_family,
    similarity_metric,
    time_decay_half_life_days,
    popularity_penalty,
    active,
    created_by
)
SELECT 'USER_CF_ADJUSTED', 'USER_CF', 'ADJUSTED_COSINE', 21, 0.0500, FALSE, u.id
FROM users u
WHERE u.username = 'sysadmin'
  AND NOT EXISTS (SELECT 1 FROM recommender_models WHERE model_code = 'USER_CF_ADJUSTED');

INSERT INTO recommender_models (
    model_code,
    algorithm_family,
    similarity_metric,
    time_decay_half_life_days,
    popularity_penalty,
    active,
    created_by
)
SELECT 'ITEM_CF_COSINE', 'ITEM_CF', 'COSINE', 21, 0.0500, FALSE, u.id
FROM users u
WHERE u.username = 'sysadmin'
  AND NOT EXISTS (SELECT 1 FROM recommender_models WHERE model_code = 'ITEM_CF_COSINE');

INSERT INTO recommender_models (
    model_code,
    algorithm_family,
    similarity_metric,
    time_decay_half_life_days,
    popularity_penalty,
    active,
    created_by
)
SELECT 'ITEM_CF_JACCARD', 'ITEM_CF', 'JACCARD', 21, 0.0500, FALSE, u.id
FROM users u
WHERE u.username = 'sysadmin'
  AND NOT EXISTS (SELECT 1 FROM recommender_models WHERE model_code = 'ITEM_CF_JACCARD');

INSERT INTO recommender_models (
    model_code,
    algorithm_family,
    similarity_metric,
    time_decay_half_life_days,
    popularity_penalty,
    active,
    created_by
)
SELECT 'ITEM_CF_ADJUSTED', 'ITEM_CF', 'ADJUSTED_COSINE', 21, 0.0500, FALSE, u.id
FROM users u
WHERE u.username = 'sysadmin'
  AND NOT EXISTS (SELECT 1 FROM recommender_models WHERE model_code = 'ITEM_CF_ADJUSTED');

INSERT INTO recommender_events (
    event_type,
    student_id,
    item_type,
    item_id,
    event_value,
    occurred_at,
    source,
    processed
)
SELECT
    'ORDER',
    s.id,
    'SKU',
    c.sku_id,
    o.quantity,
    o.placed_at,
    'GROUP_BUY_BOOTSTRAP',
    FALSE
FROM group_buy_orders o
JOIN group_buy_campaigns c ON c.id = o.campaign_id
JOIN students s ON s.id = o.student_id
WHERE o.order_status IN ('CONFIRMED', 'INVENTORY_LOCKED', 'PENDING_GROUP')
  AND NOT EXISTS (
      SELECT 1
      FROM recommender_events e
      WHERE e.source = 'GROUP_BUY_BOOTSTRAP'
        AND e.event_type = 'ORDER'
        AND e.student_id = o.student_id
        AND e.item_type = 'SKU'
        AND e.item_id = c.sku_id
        AND e.occurred_at = o.placed_at
  );

INSERT INTO recommender_events (
    event_type,
    student_id,
    item_type,
    item_id,
    event_value,
    occurred_at,
    source,
    processed
)
SELECT
    'ENROLLMENT',
    e.student_id,
    'CLASS',
    e.class_id,
    1,
    e.enrolled_at,
    'ENROLLMENT_BOOTSTRAP',
    FALSE
FROM enrollments e
WHERE e.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM recommender_events x
      WHERE x.source = 'ENROLLMENT_BOOTSTRAP'
        AND x.event_type = 'ENROLLMENT'
        AND x.student_id = e.student_id
        AND x.item_type = 'CLASS'
        AND x.item_id = e.class_id
        AND x.occurred_at = e.enrolled_at
  );
