# Sherlock — Spring Boot Microservices Backend Specification

## Architecture Overview

**Pattern:** Clean Architecture (Domain → Application → Infrastructure → Interface layers)  
**Style:** Microservices via Spring Boot 3.x, Spring Cloud, Docker Compose (dev) / Kubernetes (prod)  
**Communication:** REST (sync) + Apache Kafka (async events)  
**API Gateway:** Spring Cloud Gateway  
**Service Discovery:** Eureka (dev) / Kubernetes DNS (prod)  
**Auth:** Spring Security + JWT (Access 15m / Refresh 7d) + optional MFA  

---

## 1. Service Decomposition

| Service | Port | Responsibility |
|---|---|---|
| `api-gateway` | 8080 | Route, rate-limit, auth pre-filter |
| `auth-service` | 8081 | Login, register, JWT, MFA, sessions |
| `project-service` | 8082 | Organizations, projects, API keys, teams |
| `ingest-service` | 8083 | Receive logs/spans/metrics from SDKs (write-heavy) |
| `log-service` | 8084 | Query & stream logs |
| `trace-service` | 8085 | Distributed traces & span waterfalls |
| `error-service` | 8086 | Error groups, fingerprinting, occurrence counts |
| `incident-service` | 8087 | Incident lifecycle, timeline events |
| `metric-service` | 8088 | Time-series metrics (KPIs, infra, application) |
| `service-registry` | 8089 | Service catalog, health, dependency graph, API endpoint catalog |
| `alert-service` | 8090 | Alert rules, firing evaluation, notifications |
| `ai-service` | 8091 | LLM-backed analysis, chat, root cause |
| `integration-service` | 8092 | Third-party connectors (Slack, PagerDuty, GitHub…) |
| `deployment-service` | 8093 | CI/CD deployment lifecycle, release tracking, rollbacks |
| `dependency-service` | 8094 | Dependency inventory + SCA vulnerability (CVE) scanning |
| `notification-service` | 8095 | Per-user in-app notifications, marketing contact & newsletter |

---

## 2. Shared Domain Model

All services share these base value objects via a `sherlock-common` library published to a private Maven registry.

```
sherlock-common/
  domain/
    Severity         ENUM  INFO | LOW | MEDIUM | HIGH | CRITICAL
    Environment      ENUM  development | staging | production   -- serialized lowercase (frontend contract)
    ServiceHealth    ENUM  HEALTHY | DEGRADED | CRITICAL | UNKNOWN
    LogLevel         ENUM  TRACE | DEBUG | INFO | WARN | ERROR | FATAL
    SpanStatus       ENUM  OK | ERROR | UNSET
    IncidentStatus   ENUM  DETECTED | INVESTIGATING | IDENTIFIED
                           MITIGATING | RESOLVED | CLOSED
    Role             ENUM  OWNER | ADMIN | DEVELOPER | VIEWER
                           -- NOTE: frontend Settings/Team UI uses DEVELOPER (not MEMBER);
                           --       accept MEMBER as a legacy alias mapping → DEVELOPER
    DeployStatus     ENUM  SUCCESS | FAILED | IN_PROGRESS | CANCELLED | ROLLED_BACK
  events/
    LogIngestedEvent
    SpanIngestedEvent
    MetricIngestedEvent
    ErrorGroupedEvent
    IncidentCreatedEvent
    AlertFiredEvent
  pagination/
    PageRequest<T>
    PageResponse<T>  { data, total, page, pageSize, hasMore }
```

---

## 3. Service Specifications

### 3.1 `auth-service`

**Domain entities**

```
User
  id: UUID
  email: String (unique)
  name: String
  passwordHash: String (bcrypt, cost=12)
  role: Role
  organizationId: UUID
  mfaEnabled: Boolean
  mfaSecret: String?          -- TOTP (Google Authenticator)
  avatarUrl: String?
  createdAt: Instant

Session
  id: UUID
  userId: UUID
  refreshTokenHash: String
  deviceInfo: String
  ipAddress: String
  createdAt: Instant
  expiresAt: Instant
  revokedAt: Instant?

AuditLog
  id: UUID
  userId: UUID
  event: String               -- LOGIN | LOGOUT | PASSWORD_RESET | MFA_ENABLED
  ipAddress: String
  userAgent: String
  timestamp: Instant
```

**REST endpoints** — all under `/api/v1/auth`

```
POST   /register              Body: RegisterRequest { name, email, password, organizationName }
                              → 201 { user, tokens, organization }

POST   /login                 Body: LoginRequest { email, password }
                              → 200 { user, tokens } | 202 { mfaPending: true, mfaToken }

POST   /login/mfa             Body: { mfaToken, code }
                              → 200 { user, tokens }

POST   /logout                Header: Authorization Bearer <access>
                              Body: { refreshToken }

POST   /refresh               Body: { refreshToken }
                              → 200 { accessToken, refreshToken, expiresIn }

POST   /forgot-password       Body: { email }
POST   /reset-password        Body: { token, newPassword }

GET    /me                    → User

GET    /sessions              → Session[]
DELETE /sessions/{sessionId}  → 204

GET    /audit-log             → PageResponse<AuditLog>

POST   /mfa/enable            → { qrCodeUrl, secret }
POST   /mfa/verify            Body: { code } → enables MFA
DELETE /mfa                   Disables MFA
```

**Rate limiting:** 5 failed logins → 5-minute lockout per IP+email (match frontend `authStore` logic). The auth service persists hashed throttle keys in PostgreSQL so lockouts remain consistent across instances without retaining raw email/IP combinations.

**JWT claims:** `{ sub: userId, orgId, role, env, iat, exp }`  
**Token storage contract:** Return tokens in response body. The frontend `apiClient` (`src/api/client.ts`) reads/writes `localStorage` keys `elora_access_token` / `elora_refresh_token` and the Zustand persisted stores `elora-auth` / `elora-app-store` (internal legacy naming — the product is "Sherlock"; do **not** rename these keys without a coordinated frontend change). Refresh is `POST /auth/refresh { refreshToken } → { accessToken }`. In production, move tokens to httpOnly `Secure; SameSite=Strict` cookies via the gateway.

---

### 3.2 `project-service`

**Domain entities**

```
Organization
  id: UUID
  name: String
  slug: String (unique)
  plan: Enum FREE | PRO | ENTERPRISE
  dataRetentionDays: Int        -- 7–365, maps to Settings "Data Retention" slider
  timezone: String              -- IANA e.g. "America/New_York"
  createdAt: Instant

Project
  id: UUID
  organizationId: UUID
  name: String
  slug: String
  description: String?
  language: String?             -- Go | Java | Node.js | Python | Scala
  status: Enum ACTIVE | ARCHIVED
  environments: String[]        -- ["production","staging","development"]
  createdAt: Instant

TeamMember
  id: UUID
  organizationId: UUID
  userId: UUID
  role: Role
  joinedAt: Instant

ApiKey
  id: UUID
  projectId: UUID
  organizationId: UUID
  name: String
  prefix: String                -- "demo_live_" or "demo_test_"
  keyHash: String               -- SHA-256 of full key; never stored plaintext
  environment: Environment
  status: Enum ACTIVE | REVOKED
  expiresAt: Instant?
  createdBy: UUID
  createdAt: Instant
  lastUsedAt: Instant?
```

**REST endpoints** — all under `/api/v1`

```
-- Organizations
GET    /org                           → Organization
PATCH  /org                           Body: { name?, timezone?, dataRetentionDays? }
DELETE /org                           → 204 (cascades all data)

-- Projects
GET    /projects                      → PageResponse<ProjectSummary>
POST   /projects                      Body: { name, environments, description?, language? }
GET    /projects/{projectId}          → Project
PATCH  /projects/{projectId}
DELETE /projects/{projectId}

-- Team
GET    /team                          → TeamMember[]
POST   /team/invite                   Body: { email, role } → sends invite email
PATCH  /team/{memberId}/role          Body: { role }
DELETE /team/{memberId}

-- API Keys
GET    /projects/{projectId}/api-keys → ApiKey[]
POST   /projects/{projectId}/api-keys Body: { name, environment, expiresAt? }
                                      → { apiKey: ApiKey, plaintext: String }
DELETE /projects/{projectId}/api-keys/{keyId}   → 204 (revoke)
```

**ApiKey validation** is a shared library method used by `api-gateway` to authenticate ingest SDKs: hash incoming key, look up by hash, check status & expiry.

---

### 3.3 `ingest-service`

Write-optimized, no read path. Accepts data from customer SDKs. Validates API key via `project-service` gRPC call (cached 60 s in Caffeine). Publishes to Kafka.

```
POST /api/v1/ingest/logs       Body: LogIngestRequest[]
POST /api/v1/ingest/spans      Body: SpanIngestRequest[]
POST /api/v1/ingest/metrics    Body: MetricIngestRequest[]
POST /api/v1/ingest/errors     Body: ErrorIngestRequest[]
```

**Kafka topics produced:**
- `sherlock.logs.raw`
- `sherlock.spans.raw`
- `sherlock.metrics.raw`
- `sherlock.errors.raw`

**Payload schemas** (Avro / JSON Schema registered in Confluent Schema Registry):

```
LogIngestRequest
  traceId: String?
  spanId: String?
  requestId: String?
  service: String
  host: String
  environment: Environment
  level: LogLevel
  message: String
  timestamp: Instant
  metadata: Map<String,String>?
  stackTrace: String?

SpanIngestRequest
  traceId: String
  spanId: String
  parentSpanId: String?
  service: String
  operation: String
  startTime: Instant
  duration: Long              -- nanoseconds
  status: SpanStatus
  httpMethod: String?
  httpUrl: String?
  httpStatusCode: Int?
  attributes: Map<String,String>
  events: SpanEvent[]
  logs: String[]
  error: String?

MetricIngestRequest
  service: String
  environment: Environment
  name: String                -- e.g. "cpu.usage", "http.request.duration"
  value: Double
  unit: String
  timestamp: Instant
  tags: Map<String,String>

ErrorIngestRequest
  service: String
  environment: Environment
  type: String                -- exception class name
  message: String
  stackTrace: String
  timestamp: Instant
  traceId: String?
  severity: Severity
```

---

### 3.4 `log-service`

Consumes `sherlock.logs.raw`, persists to OpenSearch (or ClickHouse for analytics), serves query API.

**Domain entity**

```
LogEntry
  id: UUID
  projectId: UUID
  timestamp: Instant
  level: LogLevel
  service: String
  environment: Environment
  message: String
  traceId: String?
  spanId: String?
  requestId: String?
  host: String?
  metadata: Map<String,String>?
  stackTrace: String?
```

**REST endpoints**

```
GET  /api/v1/projects/{projectId}/logs
     QueryParams: search?, level[]?, service[]?, environment?, traceId?,
                  requestId?, from?, to?, page=0, pageSize=50
     → PageResponse<LogEntry>

GET  /api/v1/projects/{projectId}/logs/{logId}  → LogEntry

GET  /api/v1/projects/{projectId}/logs/stream   → SSE stream (Server-Sent Events)
     QueryParams: same filters as list

GET  /api/v1/projects/{projectId}/logs/services → String[]  (distinct service names)
```

**Retention:** A scheduled job (`@Scheduled`) deletes entries older than `organization.dataRetentionDays`.

---

### 3.5 `trace-service`

Consumes `sherlock.spans.raw`. Groups spans by `traceId`. Reconstructs trace trees.

**Domain entities**

```
Span
  spanId: String (PK part)
  traceId: String (PK part)
  parentSpanId: String?
  projectId: UUID
  service: String
  operation: String
  startTime: Instant
  duration: Long            -- nanoseconds
  status: SpanStatus
  httpMethod: String?
  httpUrl: String?
  httpStatusCode: Int?
  attributes: Map<String,String>
  events: SpanEvent[]
  logs: String[]
  error: String?

Trace (materialized view / aggregated)
  traceId: String
  projectId: UUID
  rootService: String
  rootOperation: String
  totalDuration: Long
  startTime: Instant
  status: SpanStatus
  spanCount: Int
  errorCount: Int
```

**REST endpoints**

```
GET  /api/v1/projects/{projectId}/traces
     QueryParams: service?, status?, from?, to?, page, pageSize
     → PageResponse<TraceSummary>

GET  /api/v1/projects/{projectId}/traces/{traceId}  → Trace (with full spans[])
```

---

### 3.6 `error-service`

Consumes `sherlock.errors.raw`. Fingerprints each error (stack frame normalization), groups into `ErrorGroup`.

**Domain entities**

```
ErrorGroup
  id: UUID
  projectId: UUID
  fingerprint: String         -- SHA-256 of normalized stack top 5 frames
  type: String
  message: String
  service: String
  environment: Environment
  severity: Severity
  occurrences: Long
  firstSeenAt: Instant
  lastSeenAt: Instant
  stackTrace: String
  resolved: Boolean
  assigneeId: UUID?
  incidentId: UUID?

ErrorOccurrence
  id: UUID
  errorGroupId: UUID
  timestamp: Instant
  traceId: String?
  metadata: Map<String,String>
```

**REST endpoints**

```
GET   /api/v1/projects/{projectId}/errors
      QueryParams: service?, severity?, resolved?, page, pageSize, sort(lastSeen|occurrences)
      → PageResponse<ErrorGroup>

GET   /api/v1/projects/{projectId}/errors/{groupId}  → ErrorGroupDetail
      (includes trend[], affectedServices[], relatedTraces[], aiExplanation?)

PATCH /api/v1/projects/{projectId}/errors/{groupId}
      Body: { resolved?, assigneeId?, severity? }

GET   /api/v1/projects/{projectId}/errors/{groupId}/occurrences → PageResponse<ErrorOccurrence>
```

Publishes `ErrorGroupedEvent` to `sherlock.errors.grouped` when an error group's occurrence count crosses thresholds, triggering `alert-service`.

---

### 3.7 `incident-service`

**Domain entities**

```
Incident
  id: UUID
  shortId: String             -- "INC-0042", project-scoped sequential
  projectId: UUID
  title: String
  severity: Severity
  status: IncidentStatus
  affectedServices: String[]
  startedAt: Instant
  resolvedAt: Instant?
  duration: Long?             -- seconds
  confidence: Int             -- 0-100, set by AI analysis
  rootCause: String?

IncidentEvent
  id: UUID
  incidentId: UUID
  timestamp: Instant
  type: Enum DETECTED | STATUS_CHANGE | COMMENT | EVIDENCE | RESOLVED
  title: String
  description: String?
  service: String?
  authorId: UUID?
```

**REST endpoints**

```
GET    /api/v1/projects/{projectId}/incidents
       QueryParams: severity?, status?, service?, page, pageSize
       → PageResponse<Incident>

POST   /api/v1/projects/{projectId}/incidents
       Body: { title, severity, affectedServices }

GET    /api/v1/projects/{projectId}/incidents/{incidentId}  → IncidentDetail

PATCH  /api/v1/projects/{projectId}/incidents/{incidentId}
       Body: { status?, title?, severity?, affectedServices? }

GET    /api/v1/projects/{projectId}/incidents/{incidentId}/timeline
       → IncidentEvent[]

POST   /api/v1/projects/{projectId}/incidents/{incidentId}/comments
       Body: { content }

DELETE /api/v1/projects/{projectId}/incidents/{incidentId}   → 204
```

---

### 3.8 `metric-service`

Consumes `sherlock.metrics.raw`. Stores in TimescaleDB (PostgreSQL extension) or InfluxDB. Exposes aggregated KPI and chart series.

**Stored model**

```
MetricPoint
  timestamp: Instant (partition key)
  projectId: UUID
  service: String
  environment: Environment
  name: String
  value: Double
  unit: String
  tags: Map<String,String>    -- JSONB
```

**REST endpoints**

```
GET /api/v1/projects/{projectId}/metrics/kpi
    QueryParams: environment, from, to
    → ProjectMetrics {
        totalRequests, requestsDelta,
        errorRate, errorRateDelta,
        p95Latency, p95LatencyDelta,
        p99Latency, p99LatencyDelta,
        activeIncidents, servicesDown,
        requestSeries: MetricPoint[],
        errorSeries:   MetricPoint[],
        latencySeries: MetricPoint[]
      }

GET /api/v1/projects/{projectId}/metrics
    QueryParams: name[], service[], environment, from, to, step, aggregate(avg|p95|p99|max|sum)
    → Map<String, MetricPoint[]>

GET /api/v1/projects/{projectId}/metrics/names  → String[]
```

**Predefined metric names the frontend expects:**

| Category | Names |
|---|---|
| Infrastructure | `cpu.usage`, `memory.usage`, `disk.io`, `network.in`, `network.out`, `load.average` |
| Application | `http.request.duration`, `http.request.rate`, `http.error.rate`, `db.query.duration`, `cache.hit.rate`, `thread.pool.usage` |

---

### 3.9 `service-registry`

Maintains the catalog of services and their dependency graph. Updated from span data by a consumer on `sherlock.spans.raw`.

**Domain entities**

```
ServiceRecord
  id: UUID
  projectId: UUID
  name: String
  environment: Environment
  health: ServiceHealth       -- recomputed every 60 s from recent error rate + latency
  requestCount: Long
  errorRate: Double
  p95Latency: Double
  p99Latency: Double
  lastSeenAt: Instant
  language: String?
  version: String?

DependencyEdge
  sourceService: String
  targetService: String
  projectId: UUID
  environment: Environment
  requestCount: Long
  errorCount: Long
  avgLatency: Double
  updatedAt: Instant
```

**REST endpoints**

```
GET /api/v1/projects/{projectId}/services
    QueryParams: health?, language?, environment?
    → Service[]

GET /api/v1/projects/{projectId}/services/{serviceName}  → ServiceRecord

GET /api/v1/projects/{projectId}/dependencies
    → DependencyGraph { nodes: Service[], edges: DependencyEdge[] }

GET /api/v1/projects/{projectId}/endpoints
    QueryParams: service?, status?, sort, page, pageSize
    → PageResponse<ApiEndpoint>
```

**Health computation rule:**
- `errorRate > 5% || p95Latency > 2000ms` → `CRITICAL`
- `errorRate > 1% || p95Latency > 1000ms` → `DEGRADED`
- otherwise → `HEALTHY`

---

### 3.10 `alert-service`

**Domain entities**

```
AlertRule
  id: UUID
  projectId: UUID
  name: String
  event: String               -- "ERROR_RATE_SPIKE" | "LATENCY_HIGH" | "SERVICE_DOWN" etc.
  severityThreshold: Severity
  enabled: Boolean
  channels: NotificationChannel[]

NotificationChannel
  type: Enum EMAIL | WEBHOOK | SLACK
  enabled: Boolean
  config: Map<String,String>  -- webhookUrl, slackChannel, emails[]

FiringAlert
  id: UUID
  ruleId: UUID
  projectId: UUID
  firedAt: Instant
  resolvedAt: Instant?
  message: String
  severity: Severity
  service: String?
```

**REST endpoints**

```
GET    /api/v1/projects/{projectId}/alerts/rules             → AlertRule[]
POST   /api/v1/projects/{projectId}/alerts/rules
PATCH  /api/v1/projects/{projectId}/alerts/rules/{ruleId}
DELETE /api/v1/projects/{projectId}/alerts/rules/{ruleId}

GET    /api/v1/projects/{projectId}/alerts/firing            → FiringAlert[]
```

**Evaluation:** Kafka consumer on `sherlock.errors.grouped` and a scheduled metric-threshold evaluator (default: every 60 s). On threshold breach, publishes `AlertFiredEvent` to `sherlock.alerts.fired`, then dispatches notifications via `integration-service`.

---

### 3.11 `ai-service`

Wraps an LLM (OpenAI / Anthropic Claude via API). Performs root-cause analysis and supports the chat UI.

**REST endpoints**

```
POST /api/v1/projects/{projectId}/ai/chat
     Body: { message: String, history: AiMessage[] }
     → AiMessage { id, role: "assistant", content, evidence?, confidence?, recommendedActions? }

POST /api/v1/projects/{projectId}/ai/analyze
     Body: { incidentId: UUID }
     → AiAnalysis {
         summary, evidence: String[], rootCause: RootCause,
         confidence: Int, recommendedActions: String[], disclaimer
       }

GET  /api/v1/projects/{projectId}/ai/sample
     → AiMessage[]           -- seeded demo conversation for empty state
```

**Context building:** Before calling LLM, fetch recent logs, error groups, spans, and incidents from the respective services. Summarize into a system prompt. Keep context under token budget.

**Frontend contract note:** the current `src/api/ai.ts` posts *both* free-form chat turns and structured incident analysis to `POST /ai/analyze`. The backend must discriminate on the request body: `{ message, history }` → chat completion (returns `AiMessage`); `{ incidentId }` → structured RCA (returns `AiAnalysis`). The AIAnalysis page renders `structured = { rootCause, evidence[], actions[] }` and an interactive suggested-action checklist (client-side completion tracking only — no persistence required). Keep `/ai/chat` as an explicit alias for forward compatibility.

---

### 3.12 `integration-service`

**Domain entities**

```
Integration
  id: UUID
  organizationId: UUID
  category: Enum ALERTING | CI_CD | APM | LOGGING | COMMUNICATION | TICKETING
  name: String                -- "PagerDuty" | "Slack" | "GitHub Actions" | ...
  status: Enum CONNECTED | SYNCING | ERROR | DISCONNECTED
                              -- frontend renders connected | syncing | error
  account: String?            -- linked account/workspace label (e.g. "acme-org", "#incidents")
  config: Map<String,String>  -- API keys, webhook URLs, channel IDs (encrypted at rest)
  installedAt: Instant
  lastSyncAt: Instant?
  lastUsedAt: Instant?
```

**REST endpoints**

```
GET    /api/v1/integrations           → Integration[] (catalog + installed status)
POST   /api/v1/integrations/{name}/connect  Body: { config } → OAuth/connect flow
PATCH  /api/v1/integrations/{id}      Body: { config?, status? }
DELETE /api/v1/integrations/{id}      → 204 (disconnect)
POST   /api/v1/integrations/{id}/sync → { status, lastSyncAt }  (frontend "Sync now")
POST   /api/v1/integrations/{id}/test → { success, message }
```

**Dispatchers** (Kafka consumer on `sherlock.alerts.fired`):
- `SlackDispatcher` — POST to Slack Incoming Webhook
- `EmailDispatcher` — Spring Mail / SendGrid
- `WebhookDispatcher` — generic HTTP POST
- `PagerDutyDispatcher` — PagerDuty Events API v2

---

### 3.13 `deployment-service`

Tracks CI/CD releases per service/environment (Deployments page). Fed by `integration-service` webhooks (GitHub/GitLab/Jenkins) and/or the ingest SDK. Correlates deploys with incidents (AIAnalysis links "deploy v2.14.1 at 14:01 UTC" as evidence).

**Domain entity**

```
Deployment
  id: UUID
  projectId: UUID
  service: String
  environment: Environment
  version: String              -- release tag, e.g. "v2.14.1"
  branch: String               -- e.g. "main"
  commitSha: String
  commitMessage: String?
  buildId: String?             -- CI build/run identifier
  status: DeployStatus         -- SUCCESS | FAILED | IN_PROGRESS | CANCELLED | ROLLED_BACK
  author: String               -- git author / triggering user
  durationSeconds: Long?       -- build+deploy duration
  startedAt: Instant
  finishedAt: Instant?
  rolledBackToId: UUID?        -- target deployment when status = ROLLED_BACK
```

**REST endpoints**

```
GET    /api/v1/projects/{projectId}/deployments
       QueryParams: service?, environment?, status?, branch?, author?, from?, to?, page, pageSize
       → PageResponse<Deployment>

GET    /api/v1/projects/{projectId}/deployments/{deploymentId}   → Deployment
POST   /api/v1/projects/{projectId}/deployments                  Body: { service, environment, version, branch, commitSha, ... }
POST   /api/v1/projects/{projectId}/deployments/{deploymentId}/rollback   → Deployment (new ROLLED_BACK entry)
GET    /api/v1/projects/{projectId}/deployments/stats            → { total, successRate, avgDurationSeconds, failed, frequency }
```

Publishes `DeploymentCreatedEvent` to `sherlock.deployments.created`; consumed by `ai-service` (deploy-correlation) and `incident-service` (change-cause candidates).

---

### 3.14 `dependency-service`

Powers the Dependencies page beyond the runtime call graph in `service-registry`. Maintains the software dependency inventory (packages per service) and runs **SCA vulnerability scanning** (CVE tracking, à la Dependabot/Snyk).

**Domain entities**

```
DependencyPackage
  id: UUID
  projectId: UUID
  service: String
  name: String                 -- package/artifact name
  ecosystem: String            -- maven | npm | pypi | go | cargo ...
  currentVersion: String
  latestVersion: String?
  license: String?
  direct: Boolean              -- direct vs transitive
  updatedAt: Instant

Vulnerability
  id: UUID
  packageId: UUID
  cveId: String                -- e.g. "CVE-2024-1234"
  severity: Severity           -- CRITICAL | HIGH | MEDIUM | LOW | INFO
  title: String
  description: String?
  vulnerableRange: String      -- affected semver range
  fixedVersion: String?        -- fix version, null if none
  cvssScore: Double?
  publishedAt: Instant
  status: Enum OPEN | FIXED | IGNORED | DISMISSED
```

**REST endpoints**

```
GET   /api/v1/projects/{projectId}/dependencies/packages
      QueryParams: service?, ecosystem?, direct?, search?, page, pageSize
      → PageResponse<DependencyPackage>

GET   /api/v1/projects/{projectId}/dependencies/vulnerabilities
      QueryParams: service?, severity?, status?, hasFix?, page, pageSize
      → PageResponse<Vulnerability>

GET   /api/v1/projects/{projectId}/dependencies/stats
      → { totalPackages, vulnerable, critical, high, medium, low, outdated }

PATCH /api/v1/projects/{projectId}/dependencies/vulnerabilities/{id}
      Body: { status }         -- ignore / dismiss / mark fixed

POST  /api/v1/projects/{projectId}/dependencies/scan   → triggers a fresh SCA scan
```

> The runtime service-to-service call graph (`DependencyGraph`, `DependencyEdge`) stays in `service-registry` §3.9. This service owns the *package/SCA* dimension only.

---

### 3.15 `notification-service`

In-app notification feed for the authenticated user plus public marketing endpoints (Contact page, newsletter). Consumes `sherlock.alerts.fired`, `sherlock.incidents.created`, and `sherlock.errors.grouped` to build the feed.

**Domain entities**

```
Notification
  id: UUID
  userId: UUID
  organizationId: UUID
  type: Enum INCIDENT_CREATED | CRITICAL_INCIDENT | ROOT_CAUSE_DETECTED
             | INCIDENT_RESOLVED | AI_ANALYSIS_COMPLETED | ALERT_FIRED | DEPLOY_FAILED
  title: String
  body: String?
  link: String?                -- deep link into the app
  read: Boolean
  createdAt: Instant

ContactRequest
  id: UUID
  name: String
  email: String
  company: String?
  message: String
  inquiryType: Enum GENERAL | SALES | SUPPORT | PARTNERSHIP
  createdAt: Instant
```

**REST endpoints**

```
-- Authenticated in-app feed
GET    /api/v1/notifications              QueryParams: read?, page, pageSize → PageResponse<Notification>
GET    /api/v1/notifications/unread-count → { count }
POST   /api/v1/notifications/{id}/read
POST   /api/v1/notifications/read-all
DELETE /api/v1/notifications/{id}

-- Public (no auth) — marketing
POST   /api/v1/public/contact       Body: { name, email, company?, message, inquiryType } → 202
POST   /api/v1/public/newsletter    Body: { email } → 202
```

---

### 3.16 Auth additions (email verification & MFA lifecycle)

Extends §3.1 to match the frontend auth flows (`authStore` MFA/verification state, Register → check-email, resend).

```
POST   /api/v1/auth/check-email          Body: { email } → { available: Boolean }   -- Register field-level check
POST   /api/v1/auth/verify-email         Body: { token } → 200
POST   /api/v1/auth/resend-verification  Body: { email } → 202
POST   /api/v1/auth/mfa/resend           Body: { mfaToken } → 202                     -- re-issue MFA challenge
```

`User` gains `emailVerified: Boolean`. `login` returns `403 { emailVerified: false }` when verification is required and pending.

---

### 3.17 Settings, Security & Billing (project-service extensions)

Backs the Settings page tabs (General, API Keys, Team, Alerts, Security). Extends §3.2.

```
Organization (additional fields)
  region: Enum US_EAST_1 | US_WEST_2 | EU_WEST_1 | AP_SOUTHEAST_1   -- data residency
  defaultEnvironment: Environment

SecuritySettings                 -- one row per organization
  organizationId: UUID
  samlSsoEnabled: Boolean
  samlIdpMetadataUrl: String?
  mfaEnforced: Boolean           -- org-wide MFA requirement (distinct from per-user enrollment)
  ipAllowlist: String[]          -- CIDR blocks
  sessionTimeout: Enum H1 | H8 | H24 | D7
  auditLogEnabled: Boolean       -- 90-day retention when enabled

Subscription                     -- billing surface (General tab "Plan / Upgrade")
  organizationId: UUID
  plan: Enum FREE | PRO | ENTERPRISE
  priceMonthly: Int              -- canonical source of truth for the displayed price;
                                 --   reconcile Landing ($49) vs Settings ($99) to ONE value
  status: Enum ACTIVE | PAST_DUE | CANCELLED
  renewsAt: Instant?
```

**REST endpoints**

```
-- Team role management (Team tab)
PATCH  /api/v1/team/{memberId}/role      Body: { role }   -- OWNER|ADMIN|DEVELOPER|VIEWER
DELETE /api/v1/team/{memberId}

-- Security tab
GET    /api/v1/org/security              → SecuritySettings
PATCH  /api/v1/org/security              Body: { samlSsoEnabled?, samlIdpMetadataUrl?, mfaEnforced?, ipAllowlist?, sessionTimeout?, auditLogEnabled? }
GET    /api/v1/org/audit-log/export      → text/csv  (90-day audit export)

-- Billing (General tab)
GET    /api/v1/org/subscription          → Subscription
POST   /api/v1/org/subscription/upgrade  Body: { plan } → checkout/session
```

> **Alert-rule schemas differ across surfaces.** `Alerts.tsx` uses `{ metric, condition, threshold, severity, channel }`; `Settings.tsx` AlertsTab uses `{ trigger, threshold, severity, channel(free-text) }`. Back both with the single `AlertRule` model in §3.10 — treat Settings' `trigger` as `metric` and normalize the create-dialog severity vocabulary (`high/medium/low`) onto the shared `Severity` enum.

---

### 3.18 API endpoint catalog & samples (service-registry extension)

Backs the "API Debugger" endpoint catalog / API samples surface. Extends §3.9.

```
ApiEndpoint
  id: UUID
  projectId: UUID
  service: String
  method: String               -- GET | POST | ...
  path: String                 -- route template, e.g. "/v1/orders/{id}"
  requestCount: Long
  errorRate: Double
  p95Latency: Double
  lastSeenAt: Instant

ApiSample                      -- captured request/response exemplar for an endpoint
  id: UUID
  endpointId: UUID
  traceId: String?
  statusCode: Int
  latencyMs: Double
  requestHeaders: Map<String,String>
  requestBody: String?
  responseBody: String?
  capturedAt: Instant
```

```
GET /api/v1/projects/{projectId}/endpoints/{endpointId}/samples → PageResponse<ApiSample>
```

> The interactive HTTP client in `pages/ApiDebugger.tsx` (tabs, collections, environments, cURL import/export, scripted tests, GraphQL, file uploads) is a **fully client-side tool** — it issues live `fetch` calls to arbitrary third-party URLs and persists to `localStorage` (`sherlock.api.*.v2`). It requires **no backend**; only the endpoint catalog above is server-backed.

---

## 4. Cross-Cutting Concerns

### 4.1 API Gateway (`api-gateway`)

```yaml
Routes:
  /api/v1/auth/**      → auth-service          (no auth required)
  /api/v1/ingest/**    → ingest-service         (API key auth)
  /api/v1/**           → service-by-path        (JWT auth)

Filters:
  JwtAuthFilter          -- validate JWT, forward X-User-Id, X-Org-Id, X-Role headers
  RateLimitFilter        -- 1000 req/min per org (Redis token bucket)
  CorrelationIdFilter    -- generate/pass X-Correlation-ID
  RequestLoggingFilter
```

### 4.2 Security

- **JWT**: `RS256`, asymmetric keys; `auth-service` holds private key, all others verify with public key (served at `/api/v1/auth/.well-known/jwks.json`).
- **API key ingest auth**: Gateway strips SDK key header, calls `project-service` gRPC `ValidateApiKey` (Caffeine cache, 60 s TTL).
- **Row-level isolation**: Every service query must AND `projectId` (or `organizationId`) against the JWT claim. Never expose cross-tenant data.
- **Secrets**: Integration configs encrypted at rest using AES-256-GCM with per-row IV; key stored in Vault / AWS KMS.
- **Audit**: Auth events, API key generation/revocation, and team changes written to `audit_log` in `auth-service`.

### 4.3 Data Storage Summary

| Service | Primary Store | Reason |
|---|---|---|
| auth-service | PostgreSQL | Relational, ACID |
| project-service | PostgreSQL | Relational |
| log-service | OpenSearch / ClickHouse | Full-text search, analytics |
| trace-service | ClickHouse | Column-store, fast span queries |
| error-service | PostgreSQL + Redis | Relational + occurrence counter |
| incident-service | PostgreSQL | Relational, timeline events |
| metric-service | TimescaleDB | Time-series partitioning |
| service-registry | PostgreSQL | Relational + scheduled refresh |
| alert-service | PostgreSQL | Relational |
| ai-service | Redis | Conversation session cache |
| integration-service | PostgreSQL | Relational, encrypted config |
| deployment-service | PostgreSQL | Relational, release history |
| dependency-service | PostgreSQL | Relational, package + CVE inventory |
| notification-service | PostgreSQL | Relational feed + contact requests |

### 4.4 Kafka Topic Map

| Topic | Producer | Consumers |
|---|---|---|
| `sherlock.logs.raw` | ingest-service | log-service |
| `sherlock.spans.raw` | ingest-service | trace-service, service-registry |
| `sherlock.metrics.raw` | ingest-service | metric-service |
| `sherlock.errors.raw` | ingest-service | error-service |
| `sherlock.errors.grouped` | error-service | alert-service, incident-service |
| `sherlock.alerts.fired` | alert-service | integration-service, notification-service |
| `sherlock.incidents.created` | incident-service | ai-service (auto-analyze), notification-service |
| `sherlock.deployments.created` | deployment-service | ai-service, incident-service |

### 4.5 Pagination Convention

All list endpoints follow the frontend `PaginatedResponse<T>` contract:

```json
{
  "data": [],
  "total": 0,
  "page": 0,
  "pageSize": 50,
  "hasMore": false
}
```

---

## 5. Clean Architecture Layer Structure (per service)

```
service-name/
├── domain/
│   ├── model/          Entity, ValueObject, Aggregate
│   ├── port/
│   │   ├── in/         UseCase interfaces (application entry points)
│   │   └── out/        Repository & external service interfaces
│   └── exception/      Domain exceptions
│
├── application/
│   └── usecase/        Implements domain/port/in interfaces
│                       Orchestrates domain + ports; no framework imports
│
├── infrastructure/
│   ├── persistence/    JPA entities, repositories, mappers
│   ├── messaging/      Kafka producers & consumers
│   ├── external/       HTTP clients to other services (Feign/WebClient)
│   └── config/         Spring beans, security, datasource
│
└── interface/
    ├── rest/           @RestController, DTOs, mappers
    └── grpc/           gRPC stubs (where applicable, e.g. API key validation)
```

**Rules enforced:**
- `domain` has zero Spring imports.
- `application` depends only on `domain`.
- `infrastructure` and `interface` depend on `application` and `domain`.
- DTOs never leak into `application` or `domain` layers.
- Mappers (`*Mapper`) live at the boundary (infrastructure persistence mapper, interface rest mapper).

---

## 6. Tech Stack Versions

```
Java 21 (LTS, virtual threads enabled)
Spring Boot 3.3.x
Spring Cloud 2023.0.x
  - Spring Cloud Gateway
  - Spring Cloud Netflix Eureka
  - Spring Cloud OpenFeign
Spring Security 6.x (OAuth2 Resource Server for JWT)
Spring Data JPA + Hibernate 6
Spring Kafka
Spring WebFlux (for SSE log streaming endpoint)
Flyway (schema migrations per service)
MapStruct (DTO <-> domain mapping)
Lombok
OpenAPI 3 / Springdoc
Testcontainers (integration tests)
JUnit 5 + Mockito
Docker Compose (local dev)
```

---

## 7. Implementation Priority Order

1. **`auth-service`** — unblocks everything (JWT issuance)
2. **`project-service`** — organizations, projects, API keys (all other services gate on `projectId`)
3. **`api-gateway`** — route + JWT filter (connects frontend to real APIs)
4. **`ingest-service` + Kafka** — data pipeline backbone
5. **`log-service`** — highest frontend usage, simplest query model
6. **`trace-service`** + **`error-service`** — core observability value
7. **`metric-service`** + **`service-registry`** — Dashboard KPIs and dependency graph
8. **`incident-service`** — ties errors/traces together
9. **`alert-service`** + **`integration-service`** + **`notification-service`** — notifications
10. **`deployment-service`** + **`dependency-service`** — release tracking & SCA scanning
11. **`ai-service`** — LLM layer, depends on all read services

---

> Removing `USE_MOCK = true` from each `src/api/*.ts` module and pointing `VITE_API_BASE_URL` at the gateway is the only frontend change needed once the backend is live.
