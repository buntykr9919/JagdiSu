CREATE TABLE IF NOT EXISTS users (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  email VARCHAR(190) NOT NULL,
  password VARCHAR(255) NOT NULL,
  plan VARCHAR(32) NOT NULL DEFAULT 'FREE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email),
  KEY idx_users_name (name)
);

ALTER TABLE users ADD COLUMN mobile VARCHAR(30) NULL;
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN mobile_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN locked_until TIMESTAMP NULL;
ALTER TABLE users ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS user_feedback (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NULL,
  user_name VARCHAR(120) NULL,
  user_email VARCHAR(190) NULL,
  category VARCHAR(60) NOT NULL,
  rating INT NOT NULL,
  message TEXT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'NEW',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_feedback_status (status),
  KEY idx_feedback_created_at (created_at),
  CONSTRAINT fk_feedback_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS community_questions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NULL,
  user_name VARCHAR(120) NOT NULL,
  title VARCHAR(255) NOT NULL,
  body TEXT NOT NULL,
  exam_type VARCHAR(80) NOT NULL,
  subject VARCHAR(120) NOT NULL,
  topic VARCHAR(160) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_community_questions_created_at (created_at),
  KEY idx_community_questions_exam_subject (exam_type, subject),
  CONSTRAINT fk_community_question_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS community_answers (
  id BIGINT NOT NULL AUTO_INCREMENT,
  question_id BIGINT NOT NULL,
  user_id BIGINT NULL,
  user_name VARCHAR(120) NOT NULL,
  body TEXT NOT NULL,
  upvotes INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_community_answers_question (question_id),
  CONSTRAINT fk_community_answer_question FOREIGN KEY (question_id) REFERENCES community_questions(id) ON DELETE CASCADE,
  CONSTRAINT fk_community_answer_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS roles (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(50) NOT NULL,
  name VARCHAR(80) NOT NULL,
  description VARCHAR(255) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_roles_code (code)
);

CREATE TABLE IF NOT EXISTS permissions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(100) NOT NULL,
  description VARCHAR(255) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_permissions_code (code)
);

CREATE TABLE IF NOT EXISTS user_roles (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS role_permissions (
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, permission_id),
  CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
  CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS auth_sessions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  device_id VARCHAR(120) NOT NULL,
  device_name VARCHAR(160) NULL,
  ip_address VARCHAR(64) NULL,
  user_agent VARCHAR(512) NULL,
  refresh_token_hash VARCHAR(255) NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  revoked_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_auth_sessions_user_active (user_id, revoked_at, expires_at),
  UNIQUE KEY uk_auth_sessions_refresh_token (refresh_token_hash),
  CONSTRAINT fk_auth_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS verification_tokens (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NULL,
  channel VARCHAR(20) NOT NULL,
  target VARCHAR(190) NOT NULL,
  purpose VARCHAR(40) NOT NULL,
  token_hash VARCHAR(255) NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  consumed_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_verification_target_purpose (target, purpose, expires_at),
  CONSTRAINT fk_verification_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS subscription_plans (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(40) NOT NULL,
  name VARCHAR(80) NOT NULL,
  billing_cycle VARCHAR(30) NOT NULL,
  price_inr DECIMAL(10,2) NOT NULL DEFAULT 0,
  ai_quota INT NOT NULL DEFAULT 0,
  quiz_quota INT NOT NULL DEFAULT 0,
  notes_quota INT NOT NULL DEFAULT 0,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_subscription_plans_code (code)
);

ALTER TABLE subscription_plans ADD COLUMN quiz_quota INT NOT NULL DEFAULT 0;
ALTER TABLE subscription_plans ADD COLUMN notes_quota INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS user_subscriptions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  plan_id BIGINT NOT NULL,
  status VARCHAR(30) NOT NULL,
  starts_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ends_at TIMESTAMP NULL,
  canceled_at TIMESTAMP NULL,
  reactivated_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_subscriptions_user_status (user_id, status),
  CONSTRAINT fk_user_subscriptions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_user_subscriptions_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans(id)
);

ALTER TABLE user_subscriptions ADD COLUMN canceled_at TIMESTAMP NULL;
ALTER TABLE user_subscriptions ADD COLUMN reactivated_at TIMESTAMP NULL;
ALTER TABLE user_subscriptions ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS user_plan_entitlements (
  id BIGINT NOT NULL AUTO_INCREMENT,
  plan_code VARCHAR(40) NOT NULL,
  feature VARCHAR(80) NOT NULL,
  limit_value INT NOT NULL,
  period VARCHAR(30) NOT NULL DEFAULT 'MONTHLY',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_entitlements_plan_feature (plan_code, feature)
);

CREATE TABLE IF NOT EXISTS user_usage_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  feature VARCHAR(80) NOT NULL,
  units INT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_usage_events_user_feature_created (user_id, feature, created_at),
  CONSTRAINT fk_usage_events_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS payments (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  subscription_id BIGINT NULL,
  provider VARCHAR(30) NOT NULL,
  provider_order_id VARCHAR(120) NULL,
  provider_payment_id VARCHAR(120) NULL,
  amount DECIMAL(10,2) NOT NULL,
  currency VARCHAR(10) NOT NULL DEFAULT 'INR',
  status VARCHAR(30) NOT NULL,
  invoice_number VARCHAR(80) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_payments_user_status (user_id, status),
  UNIQUE KEY uk_payments_provider_payment (provider, provider_payment_id),
  CONSTRAINT fk_payments_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_payments_subscription FOREIGN KEY (subscription_id) REFERENCES user_subscriptions(id) ON DELETE SET NULL
);

ALTER TABLE payments ADD COLUMN provider_signature VARCHAR(255) NULL;
ALTER TABLE payments ADD COLUMN webhook_event_id VARCHAR(160) NULL;
ALTER TABLE payments ADD COLUMN receipt_url VARCHAR(600) NULL;
ALTER TABLE payments ADD COLUMN metadata_json JSON NULL;
ALTER TABLE payments ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

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

CREATE TABLE IF NOT EXISTS coupons (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(60) NOT NULL,
  discount_type VARCHAR(20) NOT NULL,
  discount_value DECIMAL(10,2) NOT NULL,
  starts_at TIMESTAMP NULL,
  ends_at TIMESTAMP NULL,
  max_redemptions INT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_coupons_code (code)
);

CREATE TABLE IF NOT EXISTS wallets (
  user_id BIGINT NOT NULL,
  balance DECIMAL(10,2) NOT NULL DEFAULT 0,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_wallets_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS courses (
  id BIGINT NOT NULL AUTO_INCREMENT,
  title VARCHAR(180) NOT NULL,
  slug VARCHAR(190) NOT NULL,
  description TEXT NULL,
  teacher_id BIGINT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_courses_slug (slug),
  KEY idx_courses_status (status),
  CONSTRAINT fk_courses_teacher FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS exams (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(80) NOT NULL,
  name VARCHAR(160) NOT NULL,
  description TEXT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_exams_code (code)
);

CREATE TABLE IF NOT EXISTS questions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  exam_id BIGINT NULL,
  course_id BIGINT NULL,
  subject VARCHAR(120) NOT NULL,
  topic VARCHAR(160) NULL,
  difficulty VARCHAR(30) NOT NULL,
  question_type VARCHAR(40) NOT NULL,
  prompt TEXT NOT NULL,
  options_json JSON NULL,
  answer_json JSON NULL,
  explanation TEXT NULL,
  created_by BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_questions_exam_subject (exam_id, subject),
  KEY idx_questions_course_topic (course_id, topic),
  CONSTRAINT fk_questions_exam FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE SET NULL,
  CONSTRAINT fk_questions_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE SET NULL,
  CONSTRAINT fk_questions_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS test_attempts (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  exam_id BIGINT NULL,
  score DECIMAL(6,2) NOT NULL DEFAULT 0,
  max_score DECIMAL(6,2) NOT NULL DEFAULT 0,
  time_spent_seconds INT NOT NULL DEFAULT 0,
  completed_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_test_attempts_user_created (user_id, created_at),
  CONSTRAINT fk_test_attempts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_test_attempts_exam FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS ai_models (
  id BIGINT NOT NULL AUTO_INCREMENT,
  provider VARCHAR(60) NOT NULL,
  model_name VARCHAR(120) NOT NULL,
  use_case VARCHAR(80) NOT NULL,
  is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_models_provider_model_use_case (provider, model_name, use_case)
);

CREATE TABLE IF NOT EXISTS ai_usage_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NULL,
  model_id BIGINT NULL,
  feature VARCHAR(80) NOT NULL,
  prompt_tokens INT NOT NULL DEFAULT 0,
  completion_tokens INT NOT NULL DEFAULT 0,
  cost_usd DECIMAL(12,6) NOT NULL DEFAULT 0,
  latency_ms INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ai_usage_user_created (user_id, created_at),
  KEY idx_ai_usage_feature_created (feature, created_at),
  CONSTRAINT fk_ai_usage_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT fk_ai_usage_model FOREIGN KEY (model_id) REFERENCES ai_models(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS notifications (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NULL,
  channel VARCHAR(30) NOT NULL,
  title VARCHAR(160) NOT NULL,
  body TEXT NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  sent_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_notifications_user_status (user_id, status),
  CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS audit_logs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  actor_user_id BIGINT NULL,
  action VARCHAR(120) NOT NULL,
  entity_type VARCHAR(80) NOT NULL,
  entity_id VARCHAR(80) NULL,
  ip_address VARCHAR(64) NULL,
  metadata_json JSON NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_audit_logs_actor_created (actor_user_id, created_at),
  KEY idx_audit_logs_entity (entity_type, entity_id),
  CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS support_tickets (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NULL,
  subject VARCHAR(180) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
  priority VARCHAR(30) NOT NULL DEFAULT 'NORMAL',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_support_tickets_status_priority (status, priority),
  CONSTRAINT fk_support_tickets_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS analytics_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NULL,
  event_type VARCHAR(80) NOT NULL,
  entity_type VARCHAR(80) NULL,
  entity_id VARCHAR(80) NULL,
  duration_seconds INT NULL,
  metadata_json JSON NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_analytics_user_created (user_id, created_at),
  KEY idx_analytics_event_created (event_type, created_at),
  CONSTRAINT fk_analytics_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS community_votes (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  entity_type VARCHAR(30) NOT NULL,
  entity_id BIGINT NOT NULL,
  vote_value TINYINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_community_votes_user_entity (user_id, entity_type, entity_id),
  CONSTRAINT fk_community_votes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_followers (
  follower_user_id BIGINT NOT NULL,
  followed_user_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (follower_user_id, followed_user_id),
  CONSTRAINT fk_user_followers_follower FOREIGN KEY (follower_user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_user_followers_followed FOREIGN KEY (followed_user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS ai_requests (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NULL,
  provider VARCHAR(60) NOT NULL,
  model_name VARCHAR(120) NOT NULL,
  feature VARCHAR(80) NOT NULL,
  prompt_tokens INT NOT NULL DEFAULT 0,
  completion_tokens INT NOT NULL DEFAULT 0,
  latency_ms INT NOT NULL DEFAULT 0,
  estimated_cost_usd DECIMAL(12,6) NOT NULL DEFAULT 0,
  status VARCHAR(30) NOT NULL,
  error_message VARCHAR(600) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ai_requests_feature_created (feature, created_at),
  KEY idx_ai_requests_provider_created (provider, created_at),
  KEY idx_ai_requests_user_created (user_id, created_at),
  CONSTRAINT fk_ai_requests_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS ai_cost_tracking (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NULL,
  provider VARCHAR(60) NOT NULL,
  model_name VARCHAR(120) NOT NULL,
  feature VARCHAR(80) NOT NULL,
  prompt_tokens INT NOT NULL DEFAULT 0,
  completion_tokens INT NOT NULL DEFAULT 0,
  cost_usd DECIMAL(12,6) NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ai_cost_provider_created (provider, created_at),
  KEY idx_ai_cost_feature_created (feature, created_at),
  CONSTRAINT fk_ai_cost_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS exam_patterns (
  id BIGINT NOT NULL AUTO_INCREMENT,
  exam_id BIGINT NULL,
  exam_code VARCHAR(80) NOT NULL,
  pattern_json JSON NOT NULL,
  source_url VARCHAR(600) NULL,
  version INT NOT NULL DEFAULT 1,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_exam_patterns_exam_active (exam_code, is_active),
  CONSTRAINT fk_exam_patterns_exam FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS study_plans (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  exam_id BIGINT NULL,
  title VARCHAR(180) NOT NULL,
  target_date DATE NULL,
  plan_json JSON NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_study_plans_user_status (user_id, status),
  CONSTRAINT fk_study_plans_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_study_plans_exam FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS user_progress (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  entity_type VARCHAR(60) NOT NULL,
  entity_id BIGINT NOT NULL,
  progress_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
  time_spent_seconds INT NOT NULL DEFAULT 0,
  last_activity_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_progress_entity (user_id, entity_type, entity_id),
  KEY idx_user_progress_activity (user_id, last_activity_at),
  CONSTRAINT fk_user_progress_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS revision_plans (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  study_plan_id BIGINT NULL,
  title VARCHAR(180) NOT NULL,
  schedule_json JSON NOT NULL,
  next_revision_at TIMESTAMP NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_revision_plans_user_next (user_id, next_revision_at),
  CONSTRAINT fk_revision_plans_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_revision_plans_study_plan FOREIGN KEY (study_plan_id) REFERENCES study_plans(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS news_sources (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(140) NOT NULL,
  source_url VARCHAR(600) NOT NULL,
  source_type VARCHAR(40) NOT NULL DEFAULT 'WEB',
  trust_score DECIMAL(4,2) NOT NULL DEFAULT 0,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_news_sources_url (source_url)
);

CREATE TABLE IF NOT EXISTS current_affairs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  source_id BIGINT NULL,
  title VARCHAR(220) NOT NULL,
  slug VARCHAR(240) NOT NULL,
  summary TEXT NOT NULL,
  exam_relevance TEXT NULL,
  tags_json JSON NULL,
  published_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_current_affairs_slug (slug),
  KEY idx_current_affairs_published (published_at),
  CONSTRAINT fk_current_affairs_source FOREIGN KEY (source_id) REFERENCES news_sources(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS editorials (
  id BIGINT NOT NULL AUTO_INCREMENT,
  source_id BIGINT NULL,
  title VARCHAR(220) NOT NULL,
  slug VARCHAR(240) NOT NULL,
  body TEXT NOT NULL,
  analysis TEXT NULL,
  published_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_editorials_slug (slug),
  KEY idx_editorials_published (published_at),
  CONSTRAINT fk_editorials_source FOREIGN KEY (source_id) REFERENCES news_sources(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS mind_maps (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NULL,
  title VARCHAR(180) NOT NULL,
  source_type VARCHAR(60) NOT NULL,
  source_id BIGINT NULL,
  map_json JSON NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_mind_maps_user_created (user_id, created_at),
  CONSTRAINT fk_mind_maps_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS flashcards (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NULL,
  deck_title VARCHAR(180) NOT NULL,
  front TEXT NOT NULL,
  back TEXT NOT NULL,
  difficulty VARCHAR(30) NOT NULL DEFAULT 'MEDIUM',
  next_review_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_flashcards_user_review (user_id, next_review_at),
  CONSTRAINT fk_flashcards_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS ocr_jobs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NULL,
  file_name VARCHAR(255) NOT NULL,
  file_type VARCHAR(80) NOT NULL,
  storage_url VARCHAR(600) NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'QUEUED',
  extracted_text MEDIUMTEXT NULL,
  evaluation_json JSON NULL,
  error_message VARCHAR(600) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at TIMESTAMP NULL,
  PRIMARY KEY (id),
  KEY idx_ocr_jobs_user_created (user_id, created_at),
  KEY idx_ocr_jobs_status_created (status, created_at),
  CONSTRAINT fk_ocr_jobs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS community_reports (
  id BIGINT NOT NULL AUTO_INCREMENT,
  reporter_user_id BIGINT NULL,
  entity_type VARCHAR(40) NOT NULL,
  entity_id BIGINT NOT NULL,
  reason VARCHAR(120) NOT NULL,
  details TEXT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_community_reports_status (status, created_at),
  CONSTRAINT fk_community_reports_reporter FOREIGN KEY (reporter_user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS device_sessions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  device_fingerprint VARCHAR(160) NOT NULL,
  device_name VARCHAR(160) NULL,
  ip_address VARCHAR(64) NULL,
  last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  revoked_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_device_sessions_user_fingerprint (user_id, device_fingerprint),
  KEY idx_device_sessions_active (user_id, revoked_at, last_seen_at),
  CONSTRAINT fk_device_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS system_settings (
  setting_key VARCHAR(120) NOT NULL,
  setting_value JSON NOT NULL,
  is_secret BOOLEAN NOT NULL DEFAULT FALSE,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (setting_key)
);

CREATE TABLE IF NOT EXISTS feature_flags (
  flag_key VARCHAR(120) NOT NULL,
  description VARCHAR(255) NULL,
  is_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  rollout_percent INT NOT NULL DEFAULT 0,
  rules_json JSON NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (flag_key)
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

CREATE TABLE IF NOT EXISTS domain_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  event_id VARCHAR(80) NOT NULL,
  event_type VARCHAR(80) NOT NULL,
  payload_json JSON NOT NULL,
  published_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_domain_events_event_id (event_id),
  KEY idx_domain_events_type_published (event_type, published_at)
);

CREATE TABLE IF NOT EXISTS event_consumption_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  stream_key VARCHAR(120) NOT NULL,
  stream_record_id VARCHAR(120) NOT NULL,
  event_type VARCHAR(80) NOT NULL,
  consumer_name VARCHAR(120) NOT NULL,
  consumed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_event_consumption_record (stream_key, stream_record_id, consumer_name),
  KEY idx_event_consumption_type (event_type, consumed_at)
);

CREATE TABLE IF NOT EXISTS search_index_queue (
  id BIGINT NOT NULL AUTO_INCREMENT,
  entity_type VARCHAR(60) NOT NULL,
  entity_id BIGINT NOT NULL,
  operation VARCHAR(20) NOT NULL DEFAULT 'UPSERT',
  status VARCHAR(30) NOT NULL DEFAULT 'QUEUED',
  attempts INT NOT NULL DEFAULT 0,
  available_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  error_message VARCHAR(600) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_search_index_entity (entity_type, entity_id, operation),
  KEY idx_search_index_claim (status, available_at, attempts)
);

CREATE TABLE IF NOT EXISTS learning_sessions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  subject VARCHAR(120) NOT NULL,
  topic VARCHAR(160) NOT NULL,
  activity_type VARCHAR(50) NOT NULL,
  duration_seconds INT NOT NULL DEFAULT 0,
  completed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_learning_sessions_user_completed (user_id, completed_at),
  KEY idx_learning_sessions_topic (user_id, subject, topic),
  CONSTRAINT fk_learning_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS study_streaks (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  streak_date DATE NOT NULL,
  current_streak_days INT NOT NULL DEFAULT 1,
  longest_streak_days INT NOT NULL DEFAULT 1,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_study_streak_user_date (user_id, streak_date),
  KEY idx_study_streaks_user_updated (user_id, updated_at),
  CONSTRAINT fk_study_streaks_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS topic_mastery (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  subject VARCHAR(120) NOT NULL,
  topic VARCHAR(160) NOT NULL,
  mastery_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
  attempts INT NOT NULL DEFAULT 0,
  last_activity_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_topic_mastery_user_topic (user_id, subject, topic),
  KEY idx_topic_mastery_weak (user_id, mastery_percent, last_activity_at),
  CONSTRAINT fk_topic_mastery_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS daily_targets (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  target_date DATE NOT NULL,
  target_minutes INT NOT NULL DEFAULT 60,
  completed_minutes INT NOT NULL DEFAULT 0,
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_daily_targets_user_date (user_id, target_date),
  KEY idx_daily_targets_status (user_id, status, target_date),
  CONSTRAINT fk_daily_targets_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS revision_history (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  subject VARCHAR(120) NOT NULL,
  topic VARCHAR(160) NOT NULL,
  revised_on DATE NOT NULL,
  next_revision_on DATE NOT NULL,
  retention_score DECIMAL(5,2) NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_revision_history_user_next (user_id, next_revision_on),
  KEY idx_revision_history_topic (user_id, subject, topic),
  CONSTRAINT fk_revision_history_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);


INSERT INTO roles (code, name, description) VALUES
  ('STUDENT', 'Student', 'Learner account'),
  ('MENTOR', 'Mentor', 'Mentor account'),
  ('TEACHER', 'Teacher', 'Teacher account'),
  ('CONTENT_CREATOR', 'Content Creator', 'Creates learning content'),
  ('MODERATOR', 'Moderator', 'Community and content moderation'),
  ('ADMIN', 'Admin', 'Operational administrator'),
  ('SUPER_ADMIN', 'Super Admin', 'Platform owner with full access')
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

INSERT INTO subscription_plans (code, name, billing_cycle, price_inr, ai_quota) VALUES
  ('FREE', 'Free', 'FREE', 0, 10),
  ('MONTHLY', 'Monthly', 'MONTHLY', 199, 1000),
  ('QUARTERLY', 'Quarterly', 'QUARTERLY', 499, 3500),
  ('HALF_YEARLY', 'Half Yearly', 'HALF_YEARLY', 899, 8000),
  ('YEARLY', 'Yearly', 'YEARLY', 1499, 20000)
ON DUPLICATE KEY UPDATE name = VALUES(name), billing_cycle = VALUES(billing_cycle), price_inr = VALUES(price_inr), ai_quota = VALUES(ai_quota);

INSERT INTO subscription_plans (code, name, billing_cycle, price_inr, ai_quota, quiz_quota, notes_quota) VALUES
  ('PRO', 'Pro', 'MONTHLY', 20, 50000, 1000, 300),
  ('ADVANCED', 'Advanced', 'MONTHLY', 49, 200000, 5000, 1500)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  billing_cycle = VALUES(billing_cycle),
  price_inr = VALUES(price_inr),
  ai_quota = VALUES(ai_quota),
  quiz_quota = VALUES(quiz_quota),
  notes_quota = VALUES(notes_quota);

INSERT INTO user_plan_entitlements (plan_code, feature, limit_value, period) VALUES
  ('FREE', 'QUIZ', 10, 'DAILY'),
  ('FREE', 'NOTES', 5, 'DAILY'),
  ('FREE', 'AI_TOKENS', 10000, 'MONTHLY'),
  ('PRO', 'QUIZ', 1000, 'MONTHLY'),
  ('PRO', 'NOTES', 300, 'MONTHLY'),
  ('PRO', 'AI_TOKENS', 50000, 'MONTHLY'),
  ('ADVANCED', 'QUIZ', 5000, 'MONTHLY'),
  ('ADVANCED', 'NOTES', 1500, 'MONTHLY'),
  ('ADVANCED', 'AI_TOKENS', 200000, 'MONTHLY')
ON DUPLICATE KEY UPDATE limit_value = VALUES(limit_value), period = VALUES(period);
