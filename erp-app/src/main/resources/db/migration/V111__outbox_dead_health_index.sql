CREATE INDEX idx_erp_outbox_dead_health ON erp_outbox_message(id) WHERE status='DEAD';
