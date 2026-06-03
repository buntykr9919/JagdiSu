ALTER TABLE users ADD COLUMN IF NOT EXISTS mobile VARCHAR(30) NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS mobile_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE payments ADD COLUMN IF NOT EXISTS provider_signature VARCHAR(255) NULL;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS webhook_event_id VARCHAR(160) NULL;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS receipt_url VARCHAR(600) NULL;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS metadata_json JSON NULL;

CREATE TABLE IF NOT EXISTS invoices (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  payment_id BIGINT NOT NULL,
  invoice_number VARCHAR(80) NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  currency VARCHAR(10) NOT NULL DEFAULT 'INR',
  status VARCHAR(30) NOT NULL DEFAULT 'ISSUED',
  invoice_json JSON NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_invoices_number (invoice_number),
  KEY idx_invoices_user_created (user_id, created_at),
  CONSTRAINT fk_invoices_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_invoices_payment FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS uploaded_files (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NULL,
  bucket VARCHAR(120) NOT NULL,
  object_key VARCHAR(500) NOT NULL,
  original_file_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(120) NOT NULL,
  size_bytes BIGINT NOT NULL,
  checksum_sha256 VARCHAR(80) NULL,
  storage_provider VARCHAR(40) NOT NULL DEFAULT 'MINIO',
  signed_url_expires_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_uploaded_files_object (bucket, object_key),
  KEY idx_uploaded_files_user_created (user_id, created_at),
  CONSTRAINT fk_uploaded_files_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS async_jobs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  job_type VARCHAR(80) NOT NULL,
  payload_json JSON NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'QUEUED',
  attempts INT NOT NULL DEFAULT 0,
  max_attempts INT NOT NULL DEFAULT 3,
  available_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  locked_at TIMESTAMP NULL,
  locked_by VARCHAR(120) NULL,
  error_message VARCHAR(600) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_async_jobs_claim (status, available_at, attempts),
  KEY idx_async_jobs_type_status (job_type, status)
);

CREATE TABLE IF NOT EXISTS ai_budget_limits (
  id BIGINT NOT NULL AUTO_INCREMENT,
  scope_type VARCHAR(40) NOT NULL,
  scope_key VARCHAR(120) NOT NULL,
  daily_cost_usd DECIMAL(12,6) NOT NULL DEFAULT 0,
  daily_token_limit INT NOT NULL DEFAULT 0,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_budget_scope (scope_type, scope_key)
);

INSERT INTO ai_budget_limits (scope_type, scope_key, daily_cost_usd, daily_token_limit)
VALUES ('GLOBAL', 'GLOBAL', 25, 500000)
ON DUPLICATE KEY UPDATE daily_cost_usd = daily_cost_usd;

CREATE INDEX idx_ai_cost_created_cost ON ai_cost_tracking (created_at, cost_usd);
CREATE INDEX idx_ai_requests_status_created ON ai_requests (status, created_at);
CREATE INDEX idx_payments_order_user ON payments (provider_order_id, user_id);
CREATE INDEX idx_user_subscriptions_ends ON user_subscriptions (status, ends_at);
CREATE INDEX idx_notifications_status_created ON notifications (status, created_at);
