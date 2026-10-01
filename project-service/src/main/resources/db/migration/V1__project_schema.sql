CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(120) NOT NULL UNIQUE,
    plan VARCHAR(20) NOT NULL CHECK (plan IN ('FREE', 'PRO', 'ENTERPRISE')),
    data_retention_days INTEGER NOT NULL CHECK (data_retention_days BETWEEN 7 AND 365),
    timezone VARCHAR(100) NOT NULL,
    region VARCHAR(30) NOT NULL,
    default_environment VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE projects (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(120) NOT NULL,
    description VARCHAR(2000),
    language VARCHAR(50),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    environments VARCHAR(200) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (organization_id, slug)
);
CREATE INDEX idx_project_org_created ON projects(organization_id, created_at DESC, id);
CREATE TABLE team_members (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    email VARCHAR(254) NOT NULL,
    name VARCHAR(80) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'DEVELOPER', 'VIEWER')),
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (organization_id, user_id),
    UNIQUE (organization_id, email)
);
CREATE TABLE api_keys (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    prefix VARCHAR(20) NOT NULL,
    key_hash VARCHAR(64) NOT NULL UNIQUE,
    environment VARCHAR(20) NOT NULL CHECK (environment IN ('production', 'staging', 'development')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'REVOKED')),
    expires_at TIMESTAMP WITH TIME ZONE,
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_key_org_project ON api_keys(organization_id, project_id);
CREATE TABLE invitations (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    email VARCHAR(254) NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_invitation_email ON invitations(organization_id, email);
CREATE TABLE security_settings (
    organization_id UUID PRIMARY KEY REFERENCES organizations(id) ON DELETE CASCADE,
    saml_sso_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    saml_idp_metadata_url VARCHAR(2000),
    mfa_enforced BOOLEAN NOT NULL DEFAULT FALSE,
    ip_allowlist VARCHAR(10000) NOT NULL DEFAULT '[]',
    session_timeout VARCHAR(10) NOT NULL DEFAULT 'H24',
    audit_log_enabled BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE subscriptions (
    organization_id UUID PRIMARY KEY REFERENCES organizations(id) ON DELETE CASCADE,
    plan VARCHAR(20) NOT NULL,
    price_monthly INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    renews_at TIMESTAMP WITH TIME ZONE
);
CREATE TABLE project_audit_logs (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    action VARCHAR(200) NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_project_audit_org_time ON project_audit_logs(organization_id, timestamp DESC);
