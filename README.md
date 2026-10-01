# Sherlock Backend Microservices

Maven multi-module Spring Boot 3.4 / Java 21 backend containing the Sherlock edge
gateway, authentication, and project microservices using Clean (hexagonal) Architecture.

## Modules

| Module | Port | Responsibility |
|---|---:|---|
| `api-gateway` | 8080 | Routing, RS256 JWT validation, tenant identity headers, Redis rate limits, correlation IDs and request logging |
| `auth-service` | 8081 | Registration, login, RS256 token issuance, MFA, sessions, password reset and audit history |
| `project-service` | 8082 | Workspaces, projects, teams, invitations, API keys, security settings and subscription reads |

The gateway validates access tokens from the auth service's public JWKS endpoint and forwards
trusted `X-User-Id`, `X-Org-Id`, and `X-Role` headers. Incoming values for those headers are
always removed before routing.

## Features

- Registration and BCrypt password hashing (cost 12)
- Sign-in with a durable five-attempt / five-minute throttle
- Short-lived JWT access tokens plus rotating, hashed, single-use refresh tokens
- TOTP MFA setup, verification, sign-in challenge, and protected removal (secrets encrypted with AES-256-GCM)
- Generic password-reset requests with single-use, hashed reset tokens
- Session listing and revocation, plus a per-user audit history
- Flyway migrations that run on both PostgreSQL and H2 (PostgreSQL mode), health probes, tests, and a non-root container

## Architecture

```
            ┌──────────────────────────── presentation.rest ────────────────────────────┐
 HTTP ───▶  │ AuthController · UserController · AuthDtos (request/response) · ApiExceptionHandler │
            └───────────────────────────────┬──────────────────────────────────────────┘
                                            │ depends on (inbound ports only)
            ┌───────────────────────────────▼─────────── application ───────────────────┐
            │ port.in   RegisterUseCase, LoginUseCase, TokenRefreshUseCase, SessionUseCase, │
            │           PasswordResetUseCase, MfaUseCase, UserQueryUseCase, AuditQueryUseCase│
            │ service   RegistrationService, LoginService, LoginThrottleService,             │
            │           TokenRefreshService, SessionService, PasswordResetService,           │
            │           MfaService, AccountQueryService (+ SessionIssuer, AuditRecorder)    │
            │ dto       commands / results / RequestContext                                  │
            │ port.out  *RepositoryPort, TokenProviderPort, PasswordHasherPort, TotpPort,    │
            │           EncryptionPort, SecureTokenPort, PasswordResetNotifierPort,          │
            │           AuthSettingsPort                                                     │
            └───────────────┬───────────────────────────────────────▲──────────────────────┘
                            │ uses                                  │ implements (adapters)
            ┌───────────────▼──────── domain ───────┐   ┌───────────┴──────── infrastructure ─────────┐
            │ model      UserAccount, Organization,  │   │ persistence  Spring Data repos + adapters  │
            │            AuthSession, AuditLog, …    │   │ security     JWT, filter, SecurityConfig,  │
            │ exception  AuthException               │   │              BCrypt                        │
            │ service    PasswordPolicy              │   │ crypto       AES-GCM, SHA-256 tokens, TOTP │
            └────────────────────────────────────────┘   │ mail         SMTP / logging notifier       │
                                                         │ config       AuthProperties, bean wiring   │
                                                         │ seed         DemoDataSeeder                │
                                                         └────────────────────────────────────────────┘
```

Dependencies point inward. The domain has no Spring dependencies; JPA annotations stay on
entities as a pragmatic choice. The application layer depends only on the domain and on its
own ports. Infrastructure implements the outbound ports.

### Auth package layout (`in.sherlock.auth`)

```
SherlockAuthApplication
domain/
  model/            UserAccount, Organization, AuthSession, AuditLog, AuditEvent, Role,
                    PasswordResetToken, LoginThrottle
  exception/        AuthException
  service/          PasswordPolicy
application/
  port/in/          *UseCase interfaces
  port/out/         persistence, token, hashing, TOTP, encryption, notifier, settings ports
  service/          use-case implementations
  dto/              commands, results, RequestContext, PageResult
infrastructure/
  persistence/      *JpaRepository + *PersistenceAdapter
  security/         SecurityConfig, JwtTokenProvider, JwtAuthenticationFilter,
                    AuthPrincipal, BCryptPasswordHasher
  crypto/           AesGcmEncryptionAdapter, SecureTokenAdapter, TotpAdapter
  mail/             SmtpPasswordResetNotifier, LoggingPasswordResetNotifier
  config/           AuthProperties, ApplicationConfig
  seed/             DemoDataSeeder
presentation/
  rest/             AuthController, UserController, AuthDtos, ApiExceptionHandler
```

### Gateway package layout (`in.sherlock.gateway`)

```
domain/                 GatewayPrincipal and domain errors (no Spring imports)
application/            ResolvePrincipalUseCase and framework-free implementation
infrastructure/config/  Spring wiring and validated gateway settings
infrastructure/security OAuth2 resource-server/JWKS configuration
interfaceadapter/web/   Correlation, identity, logging and problem-response filters
```

## Running locally

### 1. Dev profile (no external dependencies)

```sh
cd backend
mvn -pl auth-service spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile (`auth-service/src/main/resources/application-dev.yml`) does the following:

- Uses in-memory H2 in PostgreSQL mode. Flyway applies the same migrations used in production.
- Uses fixed, publicly committed JWT and MFA keys. They are for development only and must never be used in production.
- Disables SMTP. Password-reset links are printed to the console.
- Seeds the demo accounts listed below (`sherlock.seed.enabled=true`).

The auth API listens on `http://localhost:8081/api/v1`.

To run the gateway, start Redis and the auth service, then run:

```sh
cd backend
mvn -pl api-gateway spring-boot:run \
  -Dspring-boot.run.arguments="--sherlock.gateway.issuer=sherlock-dev"
```

Point the frontend at `VITE_API_BASE_URL=http://localhost:8080`.

### 2. Docker Compose with PostgreSQL

Copy `example.env` to `.env` and fill in the RSA key pair, MFA encryption key, and
database password. Then run:

```sh
docker compose --env-file .env up --build
```

To get the demo accounts against Postgres, set `SEED_DEMO_DATA=true`. Never do this in production.

## Demo credentials

These accounts exist only when `sherlock.seed.enabled=true`, which is on in `dev` and off by default.
The seeder is idempotent: it leaves existing accounts unchanged. All three accounts belong to the organisation **Sherlock Demo**.

| Email                | Password            | Role      | MFA |
|----------------------|---------------------|-----------|-----|
| `admin@sherlock.dev` | `Sherlock@Owner123` | OWNER     | No  |
| `demo@sherlock.dev`  | `Sherlock@Guest123` | DEVELOPER | No  |
| `mfa@sherlock.dev`   | `Sherlock@Mfa1234`  | DEVELOPER | TOTP, secret `JBSWY3DPEHPK3PXP` |

For the MFA account, add the secret `JBSWY3DPEHPK3PXP` to any authenticator app, or run
`oathtool --totp -b JBSWY3DPEHPK3PXP` to get the 6-digit code for `POST /api/v1/auth/login/mfa`.

The password policy rejects passwords that contain the email local part, so
`Sherlock@Admin123` and `Sherlock@Demo123` are not allowed for those accounts.

## Auth service configuration

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8081` | HTTP port |
| `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | — | JDBC connection (required outside `dev`) |
| `JWT_PRIVATE_KEY_BASE64` | — | Base64 PKCS#8 RSA private key (auth service only) |
| `JWT_PUBLIC_KEY_BASE64` | — | Base64 X.509 RSA public key |
| `JWT_KEY_ID` | `sherlock-auth-rs256-1` | Key identifier exposed in JWKS and JWT headers |
| `MFA_ENCRYPTION_KEY_BASE64` | — | exactly 32 random bytes, Base64 (required outside `dev`) |
| `ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:8443,https://sherlock-in.vercel.app` | Comma-separated CORS origins |
| `PASSWORD_RESET_URL` | `https://sherlock-in.vercel.app/reset-password` | Base URL put in reset links |
| `SEED_DEMO_DATA` | `false` | Create the demo accounts |
| `MAIL_ENABLED`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `MAIL_FROM` | mail off | SMTP delivery of reset emails |

## Gateway configuration

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | Gateway HTTP port |
| `AUTH_SERVICE_URL` | `http://localhost:8081` | Auth route destination |
| `AUTH_JWKS_URI` | auth service JWKS URL | RS256 public-key discovery |
| `AUTH_ISSUER` | `sherlock-auth` | Required JWT issuer |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `localhost` / `6379` / empty | Distributed rate-limit store |
| `*_SERVICE_URL` | service-specific localhost port | Downstream route destinations |

## Endpoints

All endpoints are under `/api/v1`. Errors are returned as RFC 7807 problem JSON with a `code` field.

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/auth/register` | – | Create an account and organisation. Returns 201 with `{user, tokens, organization}` |
| POST | `/auth/login` | – | Returns 200 `{user, tokens, mfaPending:false}`, or 202 `{mfaPending:true, mfaToken}` |
| POST | `/auth/login/mfa` | – | `{mfaToken, code}`. Completes the MFA sign-in |
| POST | `/auth/refresh` | – | `{refreshToken}`. Rotates the token and returns `{accessToken, refreshToken, expiresIn}` |
| POST | `/auth/logout` | Bearer | Revokes the current session (204) |
| POST | `/auth/forgot-password` | – | `{email}`. Always returns a generic message |
| POST | `/auth/reset-password` | – | `{token, newPassword}`. Revokes all sessions |
| GET | `/auth/me` | Bearer | Current user |
| GET | `/users/me` | Bearer | Current user (alias) |
| GET | `/auth/sessions` | Bearer | Active sessions (`current` flag) |
| DELETE | `/auth/sessions/{id}` | Bearer | Revoke one session (204) |
| GET | `/auth/audit-log?page=0&pageSize=25` | Bearer | Paged audit history |
| POST | `/auth/mfa/enable` | Bearer | Start TOTP setup and return `{otpAuthUrl, secret}` |
| POST | `/auth/mfa/verify` | Bearer | `{code}`. Activates MFA (204) |
| DELETE | `/auth/mfa` | Bearer | `{password, code}`. Disables MFA and revokes sessions (204) |
| GET | `/actuator/health` | – | Liveness and readiness |
| GET | `/auth/.well-known/jwks.json` | – | Public RS256 JSON Web Key Set |

## Verification

```sh
cd backend
mvn -B -ntp verify
```

The tests run against H2 in PostgreSQL mode. `DevProfileSeedTest` boots the `dev` profile
and signs in with each seeded account.

## Deployment requirements

Deploy `backend/auth-service/Dockerfile`, `backend/project-service/Dockerfile`, and `backend/api-gateway/Dockerfile` with PostgreSQL
and Redis, and supply every variable in `example.env`, using
unique, cryptographically random secrets per environment. Do not enable the `dev` profile or
`SEED_DEMO_DATA` in production. Terminate TLS at the load balancer and restrict direct
container access to the trusted proxy or network. Back up the database. Use `/actuator/health`
for readiness and liveness probes.

## Project service

Implements `docs/backend-spec.md` §3.2 and the project-owned portions of §3.17.
The module separates framework-free domain records and application logic from outbound
ports, JDBC/HTTP/SMTP/security adapters, and REST controllers. Controllers provide the
transaction boundary; the application service has no Spring dependencies. PostgreSQL
tables and Flyway history live in the independent `project_service` schema. Auth-service
continues to own accounts, sessions, signing keys, and its registration-time organization
identity snapshot. No source code lives in `backend/src`.

### Startup

```sh
cd backend
cp example.env .env
# Generate RSA and MFA keys as described in example.env.
# Set DATABASE_PASSWORD and SERVICE_INTERNAL_TOKEN (openssl rand -hex 32).
docker compose --env-file .env up --build
```

Only the gateway is the public project-service entry point (`http://localhost:8080`).
Project-service is intentionally not host-published by Compose. Its internal validation
endpoint and auth-service membership endpoint require a shared, random
`SERVICE_INTERNAL_TOKEN` of at least 32 characters; never expose this token to the frontend.
Set `PROJECT_SERVICE_URL`, `PROJECT_DATABASE_URL`, `AUTH_SERVICE_URL`, `AUTH_JWKS_URI`,
and `AUTH_ISSUER` when running outside Compose. Run the project module directly with
`mvn -pl project-service spring-boot:run` after starting PostgreSQL and auth-service.

The first authenticated owner request initializes the workspace and imports existing
auth accounts in that workspace (including seeded demo teammates). Initialization is
idempotent. Sign in as the demo owner once before using demo developer accounts. After
initialization, project-service membership is authoritative: removed members cannot be
recreated by simply reusing a JWT. Every project API request independently verifies the
RS256 JWT, checks the live auth session and current organization, then checks local
membership/role. The service does not trust client-supplied identity headers.

### Frontend feature coverage

All public paths below are prefixed with `/api/v1` and require a bearer access token.

| Frontend surface | Endpoints / behavior |
|---|---|
| Workspace settings | `GET/PATCH/DELETE /org`; name, timezone, 7–365-day retention, region, default environment |
| Projects | `GET/POST /projects`, `GET/PATCH/DELETE /projects/{id}`; description, language, environments, archive status |
| Project search/list | `GET /projects?page=1&pageSize=20&search=&status=`; `{data,total,page,pageSize,hasMore}`, stable ordering, 1-based pages, pageSize ≤ 100 |
| Settings API Keys | `GET /api-keys` lists keys across the current workspace |
| Project API Keys | `GET/POST /projects/{id}/api-keys`, `DELETE /projects/{id}/api-keys/{keyId}` |
| Team | `GET /team`, `POST /team/invite`, `PATCH /team/{memberId}/role`, `DELETE /team/{memberId}` |
| Invitation acceptance | `POST /team/invitations/accept {token}`; verified account email must match; seven-day, one-use invitations |
| Security | `GET/PATCH /org/security`; IP allowlist, MFA enrollment requirement, session timeout, audit toggle |
| Audit export | `GET /org/audit-log/export`; CSV of workspace/project/key/team actions in the last 90 days |
| Subscription | `GET /org/subscription`; FREE = $0, PRO = $49/month, ENTERPRISE uses negotiated pricing |
| Upgrade | `POST /org/subscription/upgrade {plan}` returns `501 BILLING_NOT_CONFIGURED` until a real checkout provider is added |

Mutation permissions: OWNER/ADMIN manage workspace, teams and project deletion;
DEVELOPER can create/update projects and create/revoke keys; VIEWER is read-only.
Only OWNER manages security/billing or deletes the workspace. Owners cannot be removed,
demoted, or invited through the ordinary role endpoint. Only owners can manage admins;
self-removal and self-role changes are rejected.

`POST /team/invite` sends real SMTP mail only when `MAIL_ENABLED=true`; without SMTP,
it returns `503 MAIL_NOT_CONFIGURED`, not a false success. Mail readiness checks are
enabled only with `MAIL_ENABLED=true`, so intentionally disabled SMTP does not make
auth/project-service health checks fail. Configure `TEAM_INVITE_URL` to the invitation
acceptance screen in your deployed frontend. Acceptance requires an
authenticated account: a newly registered owner may join if their previous workspace has
no projects or other members. The auth-service updates their organization/role and
revokes all sessions; sign in again after accepting or changing roles. The same session
revocation happens on removal. Reissuing an invitation revokes earlier pending links.

API keys contain 256 random bits, retain `demo_live_` (production) or `demo_test_`
(staging/development), and are stored only as SHA-256 hashes. Creation returns
`{apiKey,plaintext}` exactly once. List responses contain metadata, **never plaintext or
hashes**. Existing keys cannot be copied/revealed later; create a replacement instead.
Revocation is idempotent. Expired/revoked keys and archived/deleted projects cannot ingest.
`lastUsedAt` updates on successful validation. Remove keys before removing their environment.

### Gateway SDK authentication

SDKs send `X-API-Key` on ingest requests. The gateway calls the private
`POST /internal/api-keys/validate {apiKey}` with service authentication and forwards only
`X-Org-Id`, `X-Project-Id`, and `X-Environment`; it strips SDK keys and user bearer
credentials before forwarding. SDK organization IDs also drive the Redis rate limiter.
Authentication failures fail closed, including project-service outages.

This initial implementation uses a bounded-timeout **HTTP validation adapter**, rather
than the spec's future gRPC/60-second cache. There is deliberately no key cache, so
revocations are not delayed by a stale cached validation. The ingest-service itself is
not yet implemented. Move the validation transport behind a gRPC adapter when ingest
is built; no raw keys should be logged or passed through to ingest.

The gateway strips all inbound user/org/project/environment/internal-token/client-IP
headers. It stamps the direct peer IP and an authenticated gateway marker. Project-service
only trusts that IP when the marker matches; arbitrary forwarded IP headers are ignored.
Behind a load balancer, configure a trusted-proxy-only client-IP strategy before enabling
an allowlist; the default sees the direct load-balancer peer, not arbitrary X-Forwarded-For.

### Integration boundaries and remaining work

- The frontend `Projects.tsx`/`Settings.tsx` now use live project/workspace APIs, with
  pagination handled in `src/api/projects.ts`. Configure `VITE_API_BASE_URL` to the gateway
  origin (without `/api/v1`). The development proxy uses `API_GATEWAY_URL` (default 8080),
  not a direct auth-service URL. Do not confuse project `status`
  (`ACTIVE|ARCHIVED`) with telemetry health (`HEALTHY|DEGRADED|CRITICAL|UNKNOWN`).
- Service health/counts, request rates, error rates, incidents, deployments, and last-event
  timestamps belong to future registry/metric/incident/deployment services. This service
  does not fabricate telemetry for frontend cards. Settings alert rules belong to alert-service.
- Frontend region IDs such as `us-east-1` and timeout IDs `1h|8h|24h|7d` are accepted and
  normalized to spec enums. Lowercase team roles and legacy `MEMBER` are accepted.
  Environment names remain lowercase. Workspace/project slugs are server-owned.
- Frontend billing prices come from subscription responses, not the old hardcoded `$99`.
  Existing token storage keys and Geist/Geist Mono fonts are preserved.
- Enabling SAML returns `501 SAML_NOT_CONFIGURED`; metadata may be saved, but there is
  no pretend SAML authentication. MFA enrollment, IP, and actual-session-age policies
  are enforced on **project-service APIs**; cross-service enforcement is still a gateway/auth
  rollout task. Data residency/retention settings are stored, not yet applied to future telemetry stores.
- Subscription reads work; paid checkout/webhooks are not implemented. No fake checkout
  URL or automatic paid entitlement is returned.
- Workspace deletion cascades project-service data and revokes/demotes auth members;
  it does not delete auth account records or future telemetry stores. Auth snapshots of
  workspace name/plan are not synchronized after settings edits.
- Audit export is project-service audit only; auth login/MFA/session events remain at
  `/auth/audit-log`. Export filters to 90 days; old rows are pruned on subsequent mutations.
- Membership synchronization is synchronous and idempotent, but not a distributed
  transaction. A remote success followed by a local rollback requires reconciliation/retry.
  Identity/role mismatches fail closed. Add a durable outbox/saga before production-scale
  independent database deployment.

### Repository ownership

`backend/` is the exportable root for `https://github.com/yuvara1/sherlock-api`.
Keep these modules in that backend repository; do not ship them in a frontend-only export.
The corrupted-history/orphan-branch procedure applies to `sherlock-ui` only, not to the
backend repository. Publish both repositories to `staging`, leaving `main` untouched;
the backend uses a normal push that preserves its history. Authenticate with a fresh secure GitHub CLI or credential-manager
session; do not reuse previously exposed credentials or paste tokens into chat.

### Project tests

`mvn -B -ntp verify` runs auth, gateway, and project tests. Project tests boot the full
Spring MVC/JDBC/Flyway/security stack with H2 PostgreSQL mode while replacing external
auth/SMTP/JWKS dependencies. They cover CRUD, tenant isolation, role restrictions,
hash-only keys, prefixes, expiry/revocation, cascades, invitations, security policies,
validation, and unconfigured providers. Gateway tests cover SDK header stripping and
fail-closed validation. Auth tests cover workspace identity and membership session revocation.
No TLS certificate validation is disabled during verification.

All 31 Maven tests pass across the three modules. A separate Chromium integration run
against production-profile jars, actual PostgreSQL and Redis, and a local SMTP capture
server verifies the frontend registration/login, project creation/editing, key creation/revocation,
gateway SDK validation, settings, invitation acceptance, role/removal session revocation,
audit export, concurrent refresh, MFA enrollment/login/disable, logout, and mobile layout.
PostgreSQL persistence and revoked-session records are checked after the browser run.
This local verification does not deploy the services or configure Vercel's gateway URL.
