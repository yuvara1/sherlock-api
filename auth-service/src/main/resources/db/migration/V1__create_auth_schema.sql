CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(120) NOT NULL UNIQUE,
    plan VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL,
    password_hash VARCHAR(60) NOT NULL,
    role VARCHAR(20) NOT NULL,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_secret_encrypted VARCHAR(512),
    pending_mfa_secret_encrypted VARCHAR(512),
    avatar_url VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_token_hash VARCHAR(64) NOT NULL,
    device_info VARCHAR(500) NOT NULL,
    ip_address VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_auth_sessions_user ON auth_sessions(user_id);
CREATE INDEX idx_auth_sessions_expiry ON auth_sessions(expires_at);

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_password_reset_user ON password_reset_tokens(user_id);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    event VARCHAR(40) NOT NULL,
    ip_address VARCHAR(64) NOT NULL,
    user_agent VARCHAR(500) NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_audit_user_timestamp ON audit_logs(user_id, timestamp DESC);

CREATE TABLE login_throttles (
    key_hash VARCHAR(64) PRIMARY KEY,
    failures INTEGER NOT NULL,
    window_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_until TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_login_throttle_expiry ON login_throttles(locked_until);
